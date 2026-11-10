package com.floatboat.app.core.overlay

/**
 * Pure placement maths for the pill window, kept free of Android types so it is unit-testable.
 */
object PillPlacement {

    /**
     * Distance from the top of the screen to the top of the pill.
     *
     * The pill is vertically centred inside the status bar when it fits, so it lines up with
     * the clock and system icons. If the status bar is shorter than the pill (or absent, as in
     * some landscape layouts) it falls back to [minMarginPx] so it never touches the edge.
     */
    fun topOffsetPx(statusBarHeightPx: Int, pillHeightPx: Int, minMarginPx: Int): Int {
        val centred = (statusBarHeightPx - pillHeightPx) / 2
        return maxOf(centred, minMarginPx)
    }
}
