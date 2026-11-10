package com.floatboat.app.core.cutout

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import com.floatboat.app.core.overlay.OverlayWindowContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the current screen size, status bar height and top display cutout.
 *
 * Values are read fresh on every call because they change with rotation and fold state.
 */
@Singleton
class CutoutDetector @Inject constructor(
    @param:OverlayWindowContext private val context: Context,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    fun detect(): ScreenGeometry {
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        return ScreenGeometry(
            widthPx = screenWidthPx(display),
            statusBarHeightPx = statusBarHeightPx(),
            density = context.resources.displayMetrics.density,
            topCutout = topCutout(display),
        )
    }

    /**
     * The cutout on the top edge for the current rotation. In landscape a phone's camera hole
     * is on a side edge, so this is null and the pill falls back to the status bar.
     *
     * `Display.getCutout()` only exists from Android 10. On Android 8 and 9 the cutout can only
     * be read from an attached view's insets, so those versions rely on the manual offset.
     */
    private fun topCutout(display: Display): CutoutBounds? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val rect = display.cutout?.boundingRectTop ?: return null
        if (rect.isEmpty) return null
        return CutoutBounds(rect.left, rect.top, rect.right, rect.bottom)
    }

    private fun screenWidthPx(display: Display): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.maximumWindowMetrics.bounds.width()
        }
        val size = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(size)
        return size.x
    }

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    private fun statusBarHeightPx(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
                .top
        }
        // Before API 30 there is no public way to read insets without an attached view.
        val resources = context.resources
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id != 0) resources.getDimensionPixelSize(id) else 0
    }
}
