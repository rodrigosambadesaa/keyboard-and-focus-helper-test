package dev.rodrigosambade.keyboardtest

import KeyboardAndFocusHelper
import android.content.Context
import android.widget.EditText
import android.widget.FrameLayout
import android.view.inputmethod.InputMethodManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardDeviceTest {
    @Test
    fun invalidDelayRejectedOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse(KeyboardAndFocusHelper.showKeyboard(EditText(context), -1))
        assertFalse(KeyboardAndFocusHelper.requestFocusAndShowKeyboard(EditText(context), -1))
    }

    @Test
    fun requestFocusMovesCursorToEndOnDevice() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val input = EditText(activity).apply {
                    setText("abcd")
                    setSelection(0)
                }
                activity.setContentView(input)

                assertTrue(KeyboardAndFocusHelper.requestFocusAndShowKeyboard(input, 0))
                assertTrue(input.hasFocus())
                assertEquals(4, input.selectionStart)
            }
        }
    }

    @Test
    fun subscriptionCanBeRemovedTwiceOnDevice() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val input = EditText(activity)
                activity.setContentView(input)
                val registration =
                    KeyboardAndFocusHelper.setOnKeyboardVisibilityListener(input) { _, _ -> }

                registration.remove()
                registration.remove()

                assertTrue(registration.isRemoved())
            }
        }
    }

    @Test
    fun subscriptionAutomaticallyRemovesWhenObservedViewDetaches() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = FrameLayout(activity)
                val child = EditText(activity)
                activity.setContentView(root)
                root.addView(child)

                val registration =
                    KeyboardAndFocusHelper.setOnKeyboardVisibilityListener(child) { _, _ -> }
                assertFalse(registration.isRemoved())

                root.removeView(child)

                assertTrue(registration.isRemoved())
            }
        }
    }


}
