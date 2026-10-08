package dev.rodrigosambade.keyboardtest

import android.os.SystemClock
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.rodrigosambade.keyboardtest.fixed.FixedKeyboardAndFocusHelper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FixedKeyboardDeviceTest {
    @Test
    fun correctedImplementationShowsMeasuresAndHidesIme() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var input: EditText

            scenario.onActivity { activity ->
                input = EditText(activity).apply {
                    setText("Corrected API 30 IME regression")
                    isFocusableInTouchMode = true
                }
                activity.setContentView(input)
                assertTrue(FixedKeyboardAndFocusHelper.requestFocusAndShowKeyboard(input, 100))
            }

            assertTrue(
                "Corrected implementation did not make IME visible within timeout",
                waitUntil(scenario) {
                    FixedKeyboardAndFocusHelper.isKeyboardVisible(input)
                }
            )

            val height = AtomicInteger()
            scenario.onActivity {
                assertTrue(FixedKeyboardAndFocusHelper.isKeyboardVisible(input))
                height.set(FixedKeyboardAndFocusHelper.getKeyboardHeightPx(input))
            }
            assertTrue(
                "IME height must never be negative; a floating/undocked visible IME may report 0",
                height.get() >= 0
            )

            scenario.onActivity {
                assertTrue(FixedKeyboardAndFocusHelper.hideKeyboard(input))
            }

            assertTrue(
                "Corrected implementation did not hide IME within timeout",
                waitUntil(scenario) {
                    !FixedKeyboardAndFocusHelper.isKeyboardVisible(input) &&
                        FixedKeyboardAndFocusHelper.getKeyboardHeightPx(input) == 0
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
