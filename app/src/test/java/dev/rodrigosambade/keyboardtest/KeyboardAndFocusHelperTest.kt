package dev.rodrigosambade.keyboardtest

import KeyboardAndFocusHelper
import android.app.Activity
import android.os.Looper
import android.widget.EditText
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [19, 28, 30, 34])
class KeyboardAndFocusHelperTest {
    @Test fun negativeDelayIsRejected() {
        val view = EditText(ApplicationProvider.getApplicationContext())
        assertFalse(KeyboardAndFocusHelper.showKeyboard(view, -1))
        assertFalse(KeyboardAndFocusHelper.requestFocusAndShowKeyboard(view, -1))
    }

    @Test fun removedSubscriptionIsIdempotent() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        val registration = KeyboardAndFocusHelper.setOnKeyboardVisibilityListener(root) { _, _ -> }
        registration.remove()
        registration.remove()
        assertTrue(registration.isRemoved())
    }

    @Test fun listenerAutomaticallyRemovedOnDetach() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        val child = FrameLayout(activity)
        root.addView(child)
        val registration = KeyboardAndFocusHelper.setOnKeyboardVisibilityListener(child) { _, _ -> }
        root.removeView(child)
        assertTrue(registration.isRemoved())
    }

    @Test fun heightCannotBeNegativeWhenKeyboardHidden() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val input = EditText(activity)
        activity.setContentView(input)
        assertTrue(KeyboardAndFocusHelper.getKeyboardHeightPx(input) >= 0)
    }

    @Test fun requestFocusMovesCursorToEnd() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val input = EditText(activity)
        activity.setContentView(input)
        input.setText("abcd")
        input.setSelection(0)
        assertTrue(KeyboardAndFocusHelper.requestFocusAndShowKeyboard(input, 0))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(input.hasFocus())
        assertEquals(4, input.selectionStart)
    }
}
