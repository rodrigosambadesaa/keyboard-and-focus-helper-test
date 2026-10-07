@file:Suppress("unused")

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.lang.ref.WeakReference
import kotlin.math.max

/**
 * Safe soft-keyboard, focus, and IME-visibility utilities for Android API 16+.
 *
 * This file depends only on the Android SDK and `androidx.core`. On API 30 and
 * newer, keyboard commands use [androidx.core.view.WindowInsetsControllerCompat]
 * and visibility/height are read from IME window insets. Earlier releases use
 * [InputMethodManager] and the visible-window-frame layout signal. Consequently,
 * the reported API 30+ height is the exact IME bottom inset, while legacy Android
 * returns the best measurable obscured height after subtracting system-bar insets.
 *
 * The visibility subscription removes itself when its observed Activity or
 * Fragment view is detached. Call [KeyboardVisibilitySubscription.remove] from
 * `onDestroy()` or `onDestroyView()` as well; explicit cleanup makes the ownership
 * unambiguous and immediately releases the callback.
 *
 * ## Dockerized emulator test matrix
 *
 * Build an instrumentation APK with `minSdk 16`, the current installed compile
 * SDK, and an `androidx.core` version compatible with that minimum. Wait for each
 * emulator's `sys.boot_completed` property before installing the APK. Headless
 * images commonly expose a hardware keyboard, so enable the software IME when
 * necessary with `adb shell settings put secure show_ime_with_hard_keyboard 1`.
 * Test both `adjustResize` and `adjustPan`, portrait/landscape rotation, and an
 * Activity plus a Fragment whose view is repeatedly created and destroyed.
 *
 * - **API 19:** exercises the `InputMethodManager` and global-layout fallbacks.
 *   Verify calls made before attachment are deferred, focus is cleared safely,
 *   and listener removal survives Activity/Fragment teardown.
 * - **API 28:** repeats the legacy path with modern navigation/status-bar layouts.
 *   Verify the cursor moves to the end of an `EditText`, height never becomes
 *   negative, and changing orientation produces a fresh height callback.
 * - **API 30+:** exercises `WindowInsetsControllerCompat` and IME insets. Verify
 *   open/closed callbacks, the IME bottom-inset height, rotation, multi-window,
 *   gesture navigation, and behavior with a physical keyboard connected.
 *
 * Vendor-ROM testing should additionally cover MIUI, One UI, EMUI, and ColorOS,
 * because their input-method services may throw unexpected runtime exceptions.
 */
object KeyboardAndFocusHelper {

    /** Receives IME visibility changes and the current keyboard height in pixels. */
    fun interface OnKeyboardVisibilityListener {
        /**
         * @param isVisible `true` when the software IME is visible.
         * @param keyboardHeightPx IME height in pixels, or `0` when it is hidden.
         */
        fun onKeyboardVisibilityChanged(isVisible: Boolean, keyboardHeightPx: Int)
    }

    /**
     * Removable registration returned by [setOnKeyboardVisibilityListener].
     *
     * [remove] is idempotent and may be called from any thread. The observed view
     * is held weakly, and the client callback is cleared as soon as removal runs.
     */
    class KeyboardVisibilitySubscription internal constructor(
        observedView: View,
        listener: OnKeyboardVisibilityListener,
        minimumKeyboardHeightDp: Float
    ) {
        private val observedViewReference = WeakReference(observedView)
        private val mainHandler = Handler(Looper.getMainLooper())
        private val thresholdDp = minimumKeyboardHeightDp

        @Volatile
        private var removed = false

        private var callback: OnKeyboardVisibilityListener? = listener
        private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
        private var attachStateListener: View.OnAttachStateChangeListener? = null
        private var lastVisible: Boolean? = null
        private var lastHeightPx = -1

        internal fun install(): Boolean {
            if (removed) return false
            val observedView = observedViewReference.get() ?: return false

            val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
                dispatchCurrentState()
            }
            val attachmentListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    dispatchCurrentState()
                }

                override fun onViewDetachedFromWindow(view: View) {
                    remove()
                }
            }

            globalLayoutListener = layoutListener
            attachStateListener = attachmentListener

            return try {
                observedView.addOnAttachStateChangeListener(attachmentListener)
                observedView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
                observedView.post { dispatchCurrentState() }
                true
            } catch (_: RuntimeException) {
                remove()
                false
            }
        }

        /** Removes the listener. Repeated calls are safe. */
        fun remove() {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                removeOnMainThread()
            } else {
                mainHandler.post { removeOnMainThread() }
            }
        }

        /** Returns whether this registration has been removed. */
        fun isRemoved(): Boolean = removed

        private fun dispatchCurrentState() {
            if (removed) return
            val observedView = observedViewReference.get() ?: run {
                remove()
                return
            }
            val state = keyboardState(observedView, thresholdDp)
            if (lastVisible == state.visible && lastHeightPx == state.heightPx) return

            lastVisible = state.visible
            lastHeightPx = state.heightPx
            callback?.onKeyboardVisibilityChanged(state.visible, state.heightPx)
        }

        @Synchronized
        private fun removeOnMainThread() {
            if (removed) return
            removed = true

            val observedView = observedViewReference.get()
            val layoutListener = globalLayoutListener
            val attachmentListener = attachStateListener
            try {
                if (observedView != null && layoutListener != null) {
                    val observer = observedView.viewTreeObserver
                    if (observer.isAlive) {
                        observer.removeOnGlobalLayoutListener(layoutListener)
                    }
                }
                if (observedView != null && attachmentListener != null) {
                    observedView.removeOnAttachStateChangeListener(attachmentListener)
                }
            } catch (_: RuntimeException) {
                // Some vendor ViewTreeObserver implementations throw during teardown.
            } finally {
                callback = null
                globalLayoutListener = null
                attachStateListener = null
                observedViewReference.clear()
            }
        }
    }

    /**
     * Hides the software keyboard associated with [activity].
     *
     * @return `true` when the operation was dispatched or safely scheduled.
     */
    @JvmStatic
    fun hideKeyboard(activity: Activity): Boolean {
        if (!isActivityUsable(activity)) return false
        val target = activity.currentFocus ?: activity.window.decorView
        return executeOnView(target) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hideWithWindowInsets(activity.window, target) || hideWithInputMethodManager(target)
            } else {
                hideWithInputMethodManager(target)
            }
        }
    }

    /**
     * Hides the software keyboard associated with [view]. Calls made before the
     * view is attached are queued through [View.post].
     *
     * @return `true` when the operation was dispatched or safely scheduled.
     */
    @JvmStatic
    fun hideKeyboard(view: View): Boolean = executeOnView(view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hideWithWindowInsets(view) || hideWithInputMethodManager(view)
        } else {
            hideWithInputMethodManager(view)
        }
    }

    /**
     * Requests focus and shows the software keyboard for [view].
     *
     * Calls made off the main thread or before attachment are posted safely. The
     * optional delay is useful immediately after Fragment transactions or dialog
     * presentation.
     *
     * @param delayMillis non-negative delay before requesting the IME.
     * @return `true` when the operation was dispatched or safely scheduled.
     */
    @JvmStatic
    @JvmOverloads
    fun showKeyboard(view: View, delayMillis: Long = 0L): Boolean {
        if (delayMillis < 0L) return false
        return executeOnView(view, delayMillis) {
            if (!view.hasFocus()) view.requestFocus()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                showWithWindowInsets(view) || showWithInputMethodManager(view)
            } else {
                showWithInputMethodManager(view)
            }
        }
    }

    /** Clears the Activity's current focus and hides its software keyboard. */
    @JvmStatic
    fun clearFocusAndHideKeyboard(activity: Activity): Boolean {
        if (!isActivityUsable(activity)) return false
        val focusedView = activity.currentFocus
        try {
            focusedView?.clearFocus()
        } catch (_: RuntimeException) {
            // Focus cleanup must not prevent the keyboard from being hidden.
        }
        return if (focusedView != null) hideKeyboard(focusedView) else hideKeyboard(activity)
    }

    /**
     * Requests focus, moves an [EditText] cursor to the end, and shows the IME.
     *
     * @param delayMillis non-negative delay before the final IME show request.
     * @return `true` when the operation was dispatched or safely scheduled.
     */
    @JvmStatic
    @JvmOverloads
    fun requestFocusAndShowKeyboard(view: View, delayMillis: Long = DEFAULT_SHOW_DELAY_MS): Boolean {
        if (delayMillis < 0L) return false
        return executeOnView(view) {
            view.isFocusableInTouchMode = true
            view.requestFocus()
            if (view is EditText) {
                try {
                    view.setSelection(view.text?.length ?: 0)
                } catch (_: RuntimeException) {
                    // Custom editable implementations can reject selection changes.
                }
            }
            showKeyboard(view, delayMillis)
        }
    }

    /**
     * Observes keyboard visibility for an Activity's decor view.
     *
     * Keep the returned handle and call `remove()` in `Activity.onDestroy()`.
     */
    @JvmStatic
    @JvmOverloads
    fun setOnKeyboardVisibilityListener(
        activity: Activity,
        minimumKeyboardHeightDp: Float = DEFAULT_MINIMUM_KEYBOARD_HEIGHT_DP,
        listener: OnKeyboardVisibilityListener
    ): KeyboardVisibilitySubscription {
        return setOnKeyboardVisibilityListener(
            activity.window.decorView,
            minimumKeyboardHeightDp,
            listener
        )
    }

    /**
     * Observes keyboard visibility for an Activity or Fragment [rootView].
     *
     * No existing window-insets listener is replaced. A global-layout observer is
     * used only as a signal; API 30+ state and height are sourced from IME insets.
     * The registration automatically removes itself when [rootView] detaches.
     * Keep the returned handle and also call `remove()` in `onDestroyView()`.
     *
     * @param minimumKeyboardHeightDp legacy-only visibility threshold. Values less
     * than zero are treated as zero.
     */
    @JvmStatic
    @JvmOverloads
    fun setOnKeyboardVisibilityListener(
        rootView: View,
        minimumKeyboardHeightDp: Float = DEFAULT_MINIMUM_KEYBOARD_HEIGHT_DP,
        listener: OnKeyboardVisibilityListener
    ): KeyboardVisibilitySubscription {
        val subscription = KeyboardVisibilitySubscription(
            rootView,
            listener,
            max(0f, minimumKeyboardHeightDp)
        )
        if (Looper.myLooper() == Looper.getMainLooper()) {
            subscription.install()
        } else {
            rootView.post { subscription.install() }
        }
        return subscription
    }

    /** Returns the currently measurable keyboard height in pixels. */
    @JvmStatic
    @JvmOverloads
    fun getKeyboardHeightPx(
        view: View,
        minimumKeyboardHeightDp: Float = DEFAULT_MINIMUM_KEYBOARD_HEIGHT_DP
    ): Int = keyboardState(view, max(0f, minimumKeyboardHeightDp)).heightPx

    /** Returns whether the software keyboard is currently visible. */
    @JvmStatic
    @JvmOverloads
    fun isKeyboardVisible(
        view: View,
        minimumKeyboardHeightDp: Float = DEFAULT_MINIMUM_KEYBOARD_HEIGHT_DP
    ): Boolean = keyboardState(view, max(0f, minimumKeyboardHeightDp)).visible

    private data class KeyboardState(val visible: Boolean, val heightPx: Int)

    private fun keyboardState(observedView: View, minimumKeyboardHeightDp: Float): KeyboardState {
        val measurementView = try {
            observedView.rootView ?: observedView
        } catch (_: RuntimeException) {
            observedView
        }

        val insets = try {
            ViewCompat.getRootWindowInsets(measurementView)
                ?: ViewCompat.getRootWindowInsets(observedView)
        } catch (_: RuntimeException) {
            null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && insets != null) {
            try {
                val visible = insets.isVisible(WindowInsetsCompat.Type.ime())
                val height = if (visible) {
                    max(0, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
                } else {
                    0
                }
                return KeyboardState(visible, height)
            } catch (_: RuntimeException) {
                // Fall through to the visible-frame calculation on broken ROMs.
            }
        }

        return legacyKeyboardState(measurementView, insets, minimumKeyboardHeightDp)
    }

    private fun legacyKeyboardState(
        view: View,
        insets: WindowInsetsCompat?,
        minimumKeyboardHeightDp: Float
    ): KeyboardState {
        return try {
            val visibleFrame = Rect()
            view.getWindowVisibleDisplayFrame(visibleFrame)
            val rootHeight = max(view.height, visibleFrame.bottom - visibleFrame.top)
            val obscuredBottom = max(0, rootHeight - visibleFrame.bottom)
            val systemBarBottom = try {
                insets?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
            } catch (_: RuntimeException) {
                0
            }
            val keyboardHeight = max(0, obscuredBottom - systemBarBottom)
            val density = view.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
            val thresholdPx = (minimumKeyboardHeightDp * density).toInt()
            val visible = keyboardHeight > thresholdPx
            KeyboardState(visible, if (visible) keyboardHeight else 0)
        } catch (_: RuntimeException) {
            KeyboardState(false, 0)
        }
    }

    private fun executeOnView(view: View, delayMillis: Long = 0L, action: () -> Unit): Boolean {
        return try {
            if (
                delayMillis == 0L &&
                Looper.myLooper() == Looper.getMainLooper() &&
                ViewCompat.isAttachedToWindow(view)
            ) {
                action()
                true
            } else if (delayMillis > 0L) {
                view.postDelayed(action, delayMillis)
            } else {
                view.post(action)
            }
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun hideWithWindowInsets(window: Window, view: View): Boolean {
        return try {
            WindowCompat.getInsetsController(window, view)
                .hide(WindowInsetsCompat.Type.ime())
            true
        } catch (_: RuntimeException) {
            false
        } catch (_: LinkageError) {
            false
        }
    }

    private fun hideWithWindowInsets(view: View): Boolean {
        return try {
            val controller = ViewCompat.getWindowInsetsController(view) ?: return false
            controller.hide(WindowInsetsCompat.Type.ime())
            true
        } catch (_: RuntimeException) {
            false
        } catch (_: LinkageError) {
            false
        }
    }

    private fun showWithWindowInsets(view: View): Boolean {
        return try {
            val controller = ViewCompat.getWindowInsetsController(view) ?: return false
            controller.show(WindowInsetsCompat.Type.ime())
            true
        } catch (_: RuntimeException) {
            false
        } catch (_: LinkageError) {
            false
        }
    }

    private fun hideWithInputMethodManager(view: View): Boolean {
        return try {
            val manager = inputMethodManager(view.context) ?: return false
            manager.hideSoftInputFromWindow(view.windowToken, 0)
        } catch (_: RuntimeException) {
            false
        } catch (_: LinkageError) {
            false
        }
    }

    private fun showWithInputMethodManager(view: View): Boolean {
        return try {
            val manager = inputMethodManager(view.context) ?: return false
            manager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        } catch (_: RuntimeException) {
            false
        } catch (_: LinkageError) {
            false
        }
    }

    private fun inputMethodManager(context: Context): InputMethodManager? {
        return try {
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun isActivityUsable(activity: Activity): Boolean {
        return try {
            !activity.isFinishing &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed)
        } catch (_: RuntimeException) {
            false
        }
    }

    private const val DEFAULT_SHOW_DELAY_MS = 100L
    private const val DEFAULT_MINIMUM_KEYBOARD_HEIGHT_DP = 100f
}

/*
 * USAGE EXAMPLES
 *
 * Kotlin Activity / Fragment
 * --------------------------
 *
 * private var keyboardSubscription:
 *     KeyboardAndFocusHelper.KeyboardVisibilitySubscription? = null
 *
 * override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
 *     val input = view.findViewById<EditText>(R.id.messageInput)
 *     KeyboardAndFocusHelper.requestFocusAndShowKeyboard(input)
 *
 *     keyboardSubscription = KeyboardAndFocusHelper.setOnKeyboardVisibilityListener(view) {
 *             visible, heightPx ->
 *         binding.keyboardStatus.text = "visible=$visible, height=$heightPx px"
 *     }
 * }
 *
 * fun dismissKeyboard() {
 *     KeyboardAndFocusHelper.clearFocusAndHideKeyboard(requireActivity())
 * }
 *
 * override fun onDestroyView() {
 *     keyboardSubscription?.remove()
 *     keyboardSubscription = null
 *     super.onDestroyView()
 * }
 *
 * Java Activity / Fragment
 * ------------------------
 *
 * private KeyboardAndFocusHelper.KeyboardVisibilitySubscription keyboardSubscription;
 *
 * @Override public void onViewCreated(@NonNull View view, Bundle state) {
 *     EditText input = view.findViewById(R.id.messageInput);
 *     KeyboardAndFocusHelper.requestFocusAndShowKeyboard(input);
 *
 *     keyboardSubscription = KeyboardAndFocusHelper.setOnKeyboardVisibilityListener(
 *         view,
 *         new KeyboardAndFocusHelper.OnKeyboardVisibilityListener() {
 *             @Override public void onKeyboardVisibilityChanged(
 *                     boolean visible, int heightPx) {
 *                 Log.d("Keyboard", "visible=" + visible + ", height=" + heightPx);
 *             }
 *         }
 *     );
 * }
 *
 * public void dismissKeyboard(Activity activity) {
 *     KeyboardAndFocusHelper.clearFocusAndHideKeyboard(activity);
 * }
 *
 * @Override public void onDestroyView() {
 *     if (keyboardSubscription != null) keyboardSubscription.remove();
 *     keyboardSubscription = null;
 *     super.onDestroyView();
 * }
 */
