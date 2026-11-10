package com.floatboat.app.settings

/** The user's manual adjustments to where the pill sits and how big it is. */
data class PlacementSettings(
    /** Shift right (positive) or left (negative) from the detected position. */
    val offsetXDp: Float = 0f,
    /** Shift down (positive) or up (negative) from the detected position. */
    val offsetYDp: Float = 0f,
    /** Multiplier on the pill's width and height. */
    val sizeScale: Float = 1f,
) {
    fun coerced() = copy(
        offsetXDp = offsetXDp.coerceIn(OffsetXRange),
        offsetYDp = offsetYDp.coerceIn(OffsetYRange),
        sizeScale = sizeScale.coerceIn(SizeScaleRange),
    )

    companion object {
        val Default = PlacementSettings()
        val OffsetXRange = -160f..160f
        val OffsetYRange = -24f..96f
        val SizeScaleRange = 0.6f..1.8f
    }
}
