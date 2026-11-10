package com.floatboat.app.core.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class PillPlacementTest {

    @Test
    fun `centres the pill inside a tall status bar`() {
        val offset = PillPlacement.topOffsetPx(
            statusBarHeightPx = 140,
            pillHeightPx = 96,
            minMarginPx = 18,
        )
        assertEquals(22, offset)
    }

    @Test
    fun `uses the minimum margin when the status bar is shorter than the pill`() {
        val offset = PillPlacement.topOffsetPx(
            statusBarHeightPx = 72,
            pillHeightPx = 96,
            minMarginPx = 18,
        )
        assertEquals(18, offset)
    }

    @Test
    fun `uses the minimum margin when centring would leave less than the margin`() {
        val offset = PillPlacement.topOffsetPx(
            statusBarHeightPx = 100,
            pillHeightPx = 96,
            minMarginPx = 18,
        )
        assertEquals(18, offset)
    }

    @Test
    fun `uses the minimum margin when there is no status bar`() {
        val offset = PillPlacement.topOffsetPx(
            statusBarHeightPx = 0,
            pillHeightPx = 96,
            minMarginPx = 18,
        )
        assertEquals(18, offset)
    }
}
