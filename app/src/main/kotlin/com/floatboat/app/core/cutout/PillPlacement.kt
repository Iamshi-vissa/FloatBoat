package com.floatboat.app.core.cutout

import com.floatboat.app.settings.PlacementSettings
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Bounding box of a display cutout in screen pixels, for the current rotation. */
data class CutoutBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Everything about the screen that pill placement depends on. */
data class ScreenGeometry(
    val widthPx: Int,
    val statusBarHeightPx: Int,
    val density: Float,
    /** Cutout on the top edge in the current rotation, or null if there is none there. */
    val topCutout: CutoutBounds?,
)

/** Resting pill dimensions before any cutout or user adjustment. */
data class PillMetrics(
    val widthDp: Float,
    val heightDp: Float,
    /** Gap kept from the top edge when there is no cutout and no room in the status bar. */
    val minTopMarginDp: Float,
    /** How far the pill extends past the camera hole on each side. */
    val cutoutPaddingDp: Float,
    /** Widest the expanded island gets; it is narrower on small screens. */
    val expandedWidthDp: Float,
    /** Room for details below the compact row when expanded. */
    val expandedExtraHeightDp: Float,
    /** Gap the expanded island keeps from the screen's side edges. */
    val screenMarginDp: Float,
)

/** Where the pill goes and how big it is, in pixels. */
data class PillLayout(
    val widthPx: Int,
    val heightPx: Int,
    /** Horizontal distance of the pill's centre from the screen's centre; negative is left. */
    val xFromCentrePx: Int,
    /** Distance from the top of the screen to the top of the pill. */
    val yPx: Int,
    val expandedWidthPx: Int,
    val expandedHeightPx: Int,
    /**
     * Like [xFromCentrePx] but for the expanded island, which is wider and therefore cannot
     * follow an off-centre camera hole as far before it would leave the screen.
     */
    val expandedXFromCentrePx: Int,
)

/** Pure placement maths, free of Android types so it is unit-testable. */
object PillPlacement {

    /**
     * With a top cutout, the pill is centred on the camera hole and enlarged if needed so the
     * hole is fully covered. Without one, it is centred horizontally and vertically centred in
     * the status bar. The user's offsets and scale are applied last, then the result is clamped
     * so the pill stays on screen.
     */
    fun compute(
        screen: ScreenGeometry,
        settings: PlacementSettings,
        metrics: PillMetrics,
    ): PillLayout {
        fun px(dp: Float) = (dp * screen.density).roundToInt()

        val cutout = screen.topCutout
        var width = px(metrics.widthDp)
        var height = px(metrics.heightDp)

        // Some devices report a box that starts at the screen edge and runs down to the bottom
        // of the hole, others a tight box around the hole. Either way the hole itself is about
        // as tall as the box's shorter side and sits at the bottom of the box.
        val holeSize = cutout?.let { min(it.width, it.height) } ?: 0
        if (cutout != null) {
            val padding = px(metrics.cutoutPaddingDp)
            width = max(width, cutout.width + 2 * padding)
            height = max(height, holeSize + 2 * padding)
        }
        width = (width * settings.sizeScale).roundToInt()
        height = (height * settings.sizeScale).roundToInt()

        var x: Int
        var y: Int
        if (cutout != null) {
            val holeCentreY = cutout.bottom - holeSize / 2
            x = (cutout.left + cutout.right) / 2 - screen.widthPx / 2
            y = holeCentreY - height / 2
        } else {
            x = 0
            y = max((screen.statusBarHeightPx - height) / 2, px(metrics.minTopMarginDp))
        }
        x += px(settings.offsetXDp)
        y += px(settings.offsetYDp)

        val maxX = max((screen.widthPx - width) / 2, 0)
        val margin = px(metrics.screenMarginDp)
        val expandedWidth = max(
            min(px(metrics.expandedWidthDp), screen.widthPx - 2 * margin),
            width,
        )
        val maxExpandedX = max((screen.widthPx - expandedWidth) / 2 - margin, 0)
        return PillLayout(
            widthPx = width,
            heightPx = height,
            xFromCentrePx = x.coerceIn(-maxX, maxX),
            yPx = max(y, 0),
            expandedWidthPx = expandedWidth,
            expandedHeightPx = height + px(metrics.expandedExtraHeightDp),
            expandedXFromCentrePx = x.coerceIn(-maxExpandedX, maxExpandedX),
        )
    }
}
