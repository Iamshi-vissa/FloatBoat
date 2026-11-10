package com.floatboat.app.core.cutout

import com.floatboat.app.settings.PlacementSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PillPlacementTest {

    // density 3: 112x32 dp pill = 336x96 px, 6 dp margin = 18 px, 4 dp padding = 12 px.
    private val metrics = PillMetrics(
        widthDp = 112f,
        heightDp = 32f,
        minTopMarginDp = 6f,
        cutoutPaddingDp = 4f,
    )

    private fun screen(statusBar: Int = 140, cutout: CutoutBounds? = null) = ScreenGeometry(
        widthPx = 1080,
        statusBarHeightPx = statusBar,
        density = 3f,
        topCutout = cutout,
    )

    private fun compute(
        screen: ScreenGeometry,
        settings: PlacementSettings = PlacementSettings.Default,
    ) = PillPlacement.compute(screen, settings, metrics)

    @Test
    fun `without a cutout the pill is centred in the status bar`() {
        assertEquals(PillLayout(336, 96, 0, 22), compute(screen(statusBar = 140)))
    }

    @Test
    fun `without a cutout a short or missing status bar falls back to the minimum margin`() {
        assertEquals(18, compute(screen(statusBar = 72)).yPx)
        assertEquals(18, compute(screen(statusBar = 100)).yPx)
        assertEquals(18, compute(screen(statusBar = 0)).yPx)
    }

    @Test
    fun `pill is centred on a centred camera hole`() {
        // Tight 80 px box around a hole whose centre is at (540, 90).
        val layout = compute(screen(cutout = CutoutBounds(500, 50, 580, 130)))

        assertEquals(0, layout.xFromCentrePx)
        assertEquals(90, layout.yPx + layout.heightPx / 2)
    }

    @Test
    fun `pill grows to cover a hole taller than the default pill`() {
        // 120 px hole + 12 px padding each side = 144 px, more than the 96 px default.
        val layout = compute(screen(cutout = CutoutBounds(480, 30, 600, 150)))

        assertEquals(144, layout.heightPx)
        assertTrue("top above hole", layout.yPx <= 30)
        assertTrue("bottom below hole", layout.yPx + layout.heightPx >= 150)
    }

    @Test
    fun `a cutout box that starts at the screen edge is treated as a hole at its bottom`() {
        // 80 px wide, running from the edge down to 130: the hole is the bottom 80 px.
        val layout = compute(screen(cutout = CutoutBounds(500, 0, 580, 130)))

        assertEquals(90, layout.yPx + layout.heightPx / 2)
    }

    @Test
    fun `pill follows an off-centre camera hole`() {
        // Hole centred at x = 140 on a 1080 px screen: 400 px left of centre, but the
        // 336 px pill can only move 372 px before leaving the screen.
        val layout = compute(screen(cutout = CutoutBounds(100, 50, 180, 130)))

        assertEquals(-372, layout.xFromCentrePx)
    }

    @Test
    fun `pill widens to cover a wide notch and never goes above the screen`() {
        val layout = compute(screen(cutout = CutoutBounds(300, 0, 780, 90)))

        assertEquals(480 + 24, layout.widthPx)
        assertEquals(0, layout.yPx)
        assertTrue(layout.heightPx >= 90)
    }

    @Test
    fun `user offsets shift the detected position`() {
        val base = compute(screen(cutout = CutoutBounds(500, 50, 580, 130)))
        val moved = compute(
            screen(cutout = CutoutBounds(500, 50, 580, 130)),
            PlacementSettings(offsetXDp = 10f, offsetYDp = 5f),
        )

        assertEquals(base.xFromCentrePx + 30, moved.xFromCentrePx)
        assertEquals(base.yPx + 15, moved.yPx)
    }

    @Test
    fun `size scale multiplies both dimensions and keeps the pill centred on the hole`() {
        val layout = compute(
            screen(cutout = CutoutBounds(500, 50, 580, 130)),
            PlacementSettings(sizeScale = 1.5f),
        )

        assertEquals(504, layout.widthPx)
        assertEquals(156, layout.heightPx)
        assertEquals(90, layout.yPx + layout.heightPx / 2)
    }

    @Test
    fun `offsets cannot push the pill off screen`() {
        val layout = compute(screen(), PlacementSettings(offsetXDp = 160f, offsetYDp = -24f))

        assertEquals((1080 - 336) / 2, layout.xFromCentrePx)
        assertEquals(0, layout.yPx)
    }
}
