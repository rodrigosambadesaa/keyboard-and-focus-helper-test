package dev.rodrigosambade.keyboardtest

import android.app.Activity
import android.os.Looper
import android.widget.FrameLayout
import dev.rodrigosambade.keyboardtest.fixed.FixedKeyboardAndFocusHelper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FixedKeyboardAndFocusHelperTest {
    @Test
    fun backgroundRemovalInvalidatesBeforeQueuedInstallRuns() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        activity.setContentView(root)

        val callbacks = AtomicInteger(0)
        val registration =
            AtomicReference<FixedKeyboardAndFocusHelper.KeyboardVisibilitySubscription>()
        val removedImmediately = AtomicBoolean(false)

        val worker = Thread {
            val current = FixedKeyboardAndFocusHelper.setOnKeyboardVisibilityListener(root) { _, _ ->
                callbacks.incrementAndGet()
            }
            registration.set(current)
            current.remove()
            removedImmediately.set(current.isRemoved())
        }

        worker.start()
        worker.join()

        assertTrue(
            "remove() must invalidate the subscription synchronously even off main thread",
            removedImmediately.get()
        )

        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(registration.get().isRemoved())
        assertEquals(
            "A queued install must not dispatch after background removal",
            0,
            callbacks.get()
        )
    }
}
