package dev.rodrigosambade.keyboardtest

import KeyboardAndFocusHelper
import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.test.core.app.ApplicationProvider
import dev.rodrigosambade.keyboardtest.fixed.FixedKeyboardAndFocusHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LegacyGeometryRegressionTest {
    /**
     * Reproduces the coordinate-space bug in the exact Gist:
     * View.height is local, while getWindowVisibleDisplayFrame().bottom is in
     * screen coordinates. With a root whose screen Y is non-zero, subtracting
     * the latter directly from height underestimates the obscured bottom area.
     */
    @Test
    fun originalUnderestimatesKeyboardWhenRootStartsBelowScreenOrigin() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = SyntheticGeometryView(context)
        view.layout(0, 0, 1000, 1800)

        val density = view.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        val thresholdDp = 250f / density

        assertFalse(
            "Exact Gist should reproduce the local-vs-screen coordinate bug",
            KeyboardAndFocusHelper.isKeyboardVisible(view, thresholdDp)
        )
        assertEquals(0, KeyboardAndFocusHelper.getKeyboardHeightPx(view, thresholdDp))

        assertTrue(
            "Corrected helper should detect the 300px obscured bottom region",
            FixedKeyboardAndFocusHelper.isKeyboardVisible(view, thresholdDp)
        )
        assertEquals(
            300,
            FixedKeyboardAndFocusHelper.getKeyboardHeightPx(view, thresholdDp)
        )
    }

    private class SyntheticGeometryView(context: Context) : View(context) {
        override fun getWindowVisibleDisplayFrame(outRect: Rect) {
            // Screen coordinates: visible window spans y=100..1600.
            outRect.set(0, 100, 1000, 1600)
        }

        override fun getLocationOnScreen(outLocation: IntArray) {
            // Root itself starts 100px below the screen origin.
            outLocation[0] = 0
            outLocation[1] = 100
        }
    }
}
