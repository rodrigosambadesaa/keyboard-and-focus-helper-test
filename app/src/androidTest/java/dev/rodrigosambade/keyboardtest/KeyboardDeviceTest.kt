package dev.rodrigosambade.keyboardtest

import KeyboardAndFocusHelper
import android.os.SystemClock
import android.widget.EditText
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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

    @Test
    fun softwareImeCanBeShownMeasuredAndHidden() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var input: EditText

            scenario.onActivity { activity ->
                input = EditText(activity).apply {
                    setText("IME integration test")
                    isFocusableInTouchMode = true
                }
                activity.setContentView(input)
                assertTrue(KeyboardAndFocusHelper.requestFocusAndShowKeyboard(input, 100))
            }

            assertTrue(
                "IME did not become visible within timeout",
                waitUntil(scenario) {
                    KeyboardAndFocusHelper.isKeyboardVisible(input)
                }
            )

            val height = AtomicInteger()
            scenario.onActivity {
                assertTrue(KeyboardAndFocusHelper.isKeyboardVisible(input))
                height.set(KeyboardAndFocusHelper.getKeyboardHeightPx(input))
            }
            assertTrue("Visible IME must have a positive measured height", height.get() > 0)

            scenario.onActivity {
                assertTrue(KeyboardAndFocusHelper.hideKeyboard(input))
            }

            assertTrue(
                "IME did not become hidden within timeout",
                waitUntil(scenario) {
                    !KeyboardAndFocusHelper.isKeyboardVisible(input) &&
                        KeyboardAndFocusHelper.getKeyboardHeightPx(input) == 0
                }
            )
        }
    }

    private fun waitUntil(
        scenario: ActivityScenario<MainActivity>,
        timeoutMs: Long = 8_000,
        condition: () -> Boolean
    ): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + timeoutMs

        do {
            val matched = AtomicBoolean(false)
            scenario.onActivity {
                matched.set(condition())
            }
            if (matched.get()) return true

            instrumentation.waitForIdleSync()
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)

        return false
    }
}
