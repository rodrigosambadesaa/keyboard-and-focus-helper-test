package dev.rodrigosambade.keyboardtest

import KeyboardAndFocusHelper
import android.app.Activity
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardDeviceTest {
    @Test fun subscriptionCanBeRemovedTwiceOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var removed = false
        instrumentation.runOnMainSync {
            val activity = Activity()
            val input = EditText(instrumentation.targetContext)
            val registration = KeyboardAndFocusHelper.setOnKeyboardVisibilityListener(input) { _, _ -> }
            registration.remove()
            registration.remove()
            removed = registration.isRemoved()
        }
        assertTrue(removed)
    }

    @Test fun invalidDelayRejectedOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse(KeyboardAndFocusHelper.showKeyboard(EditText(context), -1))
    }
}
