package com.floatboat.app.core.cutout

import android.graphics.Insets
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.DisplayCutout
import com.floatboat.app.core.overlay.createOverlayWindowContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class CutoutDetectorTest {

    private val app = RuntimeEnvironment.getApplication()
    private val detector = CutoutDetector(createOverlayWindowContext(app))

    private val display: Display
        get() = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

    @Test
    fun `reports screen width and density`() {
        val geometry = detector.detect()

        assertEquals(app.resources.displayMetrics.widthPixels, geometry.widthPx)
        assertEquals(app.resources.displayMetrics.density, geometry.density)
        assertTrue(geometry.statusBarHeightPx >= 0)
    }

    @Test
    fun `no cutout is reported on a display without one`() {
        assertNull(detector.detect().topCutout)
    }

    @Test
    @Config(sdk = [35])
    fun `top cutout bounds are read from the display`() {
        val hole = Rect(140, 20, 180, 60)
        shadowOf(display).setDisplayCutout(
            DisplayCutout(Insets.of(0, 60, 0, 0), null, hole, null, null),
        )

        assertEquals(CutoutBounds(140, 20, 180, 60), detector.detect().topCutout)
    }

    @Test
    @Config(sdk = [35])
    fun `a cutout on a side edge is not a top cutout`() {
        val hole = Rect(20, 140, 60, 180)
        shadowOf(display).setDisplayCutout(
            DisplayCutout(Insets.of(60, 0, 0, 0), hole, null, null, null),
        )

        assertNull(detector.detect().topCutout)
    }
}
