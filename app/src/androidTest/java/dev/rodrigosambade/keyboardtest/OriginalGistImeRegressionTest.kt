package dev.rodrigosambade.keyboardtest

import KeyboardAndFocusHelper
import android.os.SystemClock
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OriginalGistImeRegressionTest {
    @Test
    fun exactGistShowsMeasuresAndHidesIme() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var input: EditText

            scenario.onActivity { activity ->
                input = EditText(activity).apply {
                    setText("Exact Gist IME regression")
                    isFocusableInTouchMode = true
                }
                activity.setContentView(input)
                assertTrue(KeyboardAndFocusHelper.requestFocusAndShowKeyboard(input, 100))
            }

            assertTrue(
                "Exact Gist did not make IME visible within timeout",
                waitUntil(scenario) {
                    KeyboardAndFocusHelper.isKeyboardVisible(input)
                }
            )

            val height = AtomicInteger()
            scenario.onActivity {
                assertTrue(KeyboardAndFocusHelper.isKeyboardVisible(input))
                height.set(KeyboardAndFocusHelper.getKeyboardHeightPx(input))
            }
            assertTrue("Visible IME must have positive measured height", height.get() > 0)

            scenario.onActivity {
                assertTrue(KeyboardAndFocusHelper.hideKeyboard(input))
            }

            assertTrue(
                "Exact Gist did not hide IME within timeout",
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
