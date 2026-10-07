@file:Suppress("unused")

package dev.rodrigosambade.keyboardtest

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

/** Keyboard and focus utilities for API 16+, adapted from Rodrigo Sambade's public Gist. */
object KeyboardAndFocusHelper {
    fun interface OnKeyboardVisibilityListener {
        fun onKeyboardVisibilityChanged(isVisible: Boolean, keyboardHeightPx: Int)
    }

    class KeyboardVisibilitySubscription internal constructor(
        observedView: View,
        listener: OnKeyboardVisibilityListener,
        private val thresholdDp: Float
    ) {
        private val viewRef = WeakReference(observedView)
        private val handler = Handler(Looper.getMainLooper())
        @Volatile private var removed = false
        private var callback: OnKeyboardVisibilityListener? = listener
        private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
        private var attachListener: View.OnAttachStateChangeListener? = null
        private var observer: ViewTreeObserver? = null
        private var lastVisible: Boolean? = null
        private var lastHeight = -1

        internal fun install() {
            if (removed) return
            val view = viewRef.get() ?: return
            if (layoutListener != null) return
            val listener = ViewTreeObserver.OnGlobalLayoutListener { dispatch() }
            val attachment = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) { dispatch() }
                override fun onViewDetachedFromWindow(v: View) { remove() }
            }
            try {
                view.addOnAttachStateChangeListener(attachment)
                view.viewTreeObserver.addOnGlobalLayoutListener(listener)
                observer = view.viewTreeObserver
                layoutListener = listener
                attachListener = attachment
                if (ViewCompat.isAttachedToWindow(view)) dispatch()
            } catch (_: RuntimeException) {
                remove()
            }
        }

        private fun dispatch() {
            if (removed) return
            val view = viewRef.get() ?: return
            val state = keyboardState(view, thresholdDp)
            if (lastVisible == state.first && lastHeight == state.second) return
            lastVisible = state.first
            lastHeight = state.second
            callback?.onKeyboardVisibilityChanged(state.first, state.second)
        }

        fun remove() {
            // Invalidate immediately so queued installation and delivery cannot revive subscription.
            removed = true
            if (Looper.myLooper() == Looper.getMainLooper()) cleanup()
            else handler.post { cleanup() }
        }

        fun isRemoved(): Boolean = removed

        private fun cleanup() {
            val view = viewRef.get()
            try {
                val current = observer
                val listener = layoutListener
                if (current?.isAlive == true && listener != null) {
                    current.removeOnGlobalLayoutListener(listener)
                }
                val attachment = attachListener
                if (view != null && attachment != null) view.removeOnAttachStateChangeListener(attachment)
            } catch (_: RuntimeException) {
                // Vendor implementations may throw during window teardown.
            } finally {
                callback = null
                layoutListener = null
                attachListener = null
                observer = null
                viewRef.clear()
            }
        }
    }

    @JvmStatic fun hideKeyboard(activity: Activity): Boolean {
        if (!usable(activity)) return false
        val view = activity.currentFocus ?: activity.window.decorView
        return execute(view) {
            if (Build.VERSION.SDK_INT >= 30) hideWithInsets(activity.window, view)
                || hideWithImm(view)
            else hideWithImm(view)
        }
    }

    @JvmStatic fun hideKeyboard(view: View): Boolean = execute(view) {
        if (Build.VERSION.SDK_INT >= 30) hideWithInsets(view) || hideWithImm(view)
        else hideWithImm(view)
    }

    @JvmStatic @JvmOverloads
    fun showKeyboard(view: View, delayMillis: Long = 0): Boolean {
        if (delayMillis < 0) return false
        return execute(view, delayMillis) {
            if (!view.hasFocus()) view.requestFocus()
            if (Build.VERSION.SDK_INT >= 30) showWithInsets(view) || showWithImm(view)
            else showWithImm(view)
        }
    }

    @JvmStatic fun clearFocusAndHideKeyboard(activity: Activity): Boolean {
        if (!usable(activity)) return false
        val focused = activity.currentFocus
        return execute(focused ?: activity.window.decorView) {
            try { focused?.clearFocus() } catch (_: RuntimeException) {}
            hideKeyboard(activity)
        }
    }

    @JvmStatic @JvmOverloads
    fun requestFocusAndShowKeyboard(view: View, delayMillis: Long = 100): Boolean {
        if (delayMillis < 0) return false
        return execute(view) {
            view.isFocusableInTouchMode = true
            view.requestFocus()
            if (view is EditText) {
                try { view.setSelection(view.text?.length ?: 0) } catch (_: RuntimeException) {}
            }
            showKeyboard(view, delayMillis)
        }
    }

    @JvmStatic @JvmOverloads
    fun setOnKeyboardVisibilityListener(
        activity: Activity,
        minimumKeyboardHeightDp: Float = 100f,
        listener: OnKeyboardVisibilityListener
    ): KeyboardVisibilitySubscription =
        setOnKeyboardVisibilityListener(activity.window.decorView, minimumKeyboardHeightDp, listener)

    @JvmStatic @JvmOverloads
    fun setOnKeyboardVisibilityListener(
        rootView: View,
        minimumKeyboardHeightDp: Float = 100f,
        listener: OnKeyboardVisibilityListener
    ): KeyboardVisibilitySubscription {
        val threshold = if (minimumKeyboardHeightDp.isFinite()) max(0f, minimumKeyboardHeightDp) else 100f
        val subscription = KeyboardVisibilitySubscription(rootView, listener, threshold)
        if (Looper.myLooper() == Looper.getMainLooper()) subscription.install()
        else Handler(Looper.getMainLooper()).post { subscription.install() }
        return subscription
    }

    @JvmStatic @JvmOverloads
    fun getKeyboardHeightPx(view: View, minimumKeyboardHeightDp: Float = 100f): Int =
        keyboardState(view, minimumKeyboardHeightDp).second

    @JvmStatic @JvmOverloads
    fun isKeyboardVisible(view: View, minimumKeyboardHeightDp: Float = 100f): Boolean =
        keyboardState(view, minimumKeyboardHeightDp).first

    private fun keyboardState(view: View, thresholdDp: Float): Pair<Boolean, Int> {
        val root = view.rootView ?: view
        val insets = try { ViewCompat.getRootWindowInsets(root) } catch (_: RuntimeException) { null }
        if (Build.VERSION.SDK_INT >= 30 && insets != null) {
            try {
                val visible = insets.isVisible(WindowInsetsCompat.Type.ime())
                return Pair(visible, if (visible) max(0, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom) else 0)
            } catch (_: RuntimeException) {}
        }
        return try {
            val rect = Rect()
            root.getWindowVisibleDisplayFrame(rect)
            // The visible rect uses screen coordinates; subtract root's screen-space bottom.
            val position = IntArray(2)
            root.getLocationOnScreen(position)
            val rootBottom = position[1].toLong() + root.height.toLong()
            val obstruction = max(0L, rootBottom - rect.bottom.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val bars = try { insets?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0 }
                catch (_: RuntimeException) { 0 }
            val height = max(0, obstruction - bars)
            val density = root.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
            val threshold = if (thresholdDp.isFinite()) max(0f, thresholdDp) else 100f
            val visible = height.toFloat() > threshold * density
            Pair(visible, if (visible) height else 0)
        } catch (_: RuntimeException) { Pair(false, 0) }
    }

    private fun execute(view: View, delay: Long = 0, action: () -> Unit): Boolean =
        try {
            if (delay == 0L && Looper.myLooper() == Looper.getMainLooper()
                && ViewCompat.isAttachedToWindow(view)) {
                action()
                true
            } else if (delay > 0L) view.postDelayed(action, delay)
            else view.post(action)
        } catch (_: RuntimeException) { false }

    private fun showWithInsets(view: View): Boolean = try {
        val controller = ViewCompat.getWindowInsetsController(view) ?: return false
        controller.show(WindowInsetsCompat.Type.ime())
        true
    } catch (_: RuntimeException) { false } catch (_: LinkageError) { false }

    private fun hideWithInsets(view: View): Boolean = try {
        val controller = ViewCompat.getWindowInsetsController(view) ?: return false
        controller.hide(WindowInsetsCompat.Type.ime())
        true
    } catch (_: RuntimeException) { false } catch (_: LinkageError) { false }

    private fun hideWithInsets(window: Window, view: View): Boolean = try {
        WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.ime())
        true
    } catch (_: RuntimeException) { false } catch (_: LinkageError) { false }

    private fun imm(view: View): InputMethodManager? =
        try { view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager }
        catch (_: RuntimeException) { null }

    private fun showWithImm(view: View): Boolean = try {
        imm(view)?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT) ?: false
    } catch (_: RuntimeException) { false } catch (_: LinkageError) { false }

    private fun hideWithImm(view: View): Boolean = try {
        imm(view)?.hideSoftInputFromWindow(view.windowToken, 0) ?: false
    } catch (_: RuntimeException) { false } catch (_: LinkageError) { false }

    private fun usable(activity: Activity): Boolean = try {
        !activity.isFinishing && (Build.VERSION.SDK_INT < 17 || !activity.isDestroyed)
    } catch (_: RuntimeException) { false }
}
