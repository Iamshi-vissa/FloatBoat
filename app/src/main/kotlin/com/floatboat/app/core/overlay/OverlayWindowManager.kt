package com.floatboat.app.core.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the overlay window sits: [xFromCentrePx] is the horizontal distance of its centre from
 * the screen centre (negative is left), [yPx] the distance of its top from the top of the screen.
 */
data class OverlayPosition(val xFromCentrePx: Int, val yPx: Int)

/**
 * The only place that talks to [WindowManager] for the island overlay.
 *
 * Owns a single overlay window containing a [ComposeOverlayHost]. All methods must be called
 * on the main thread and are safe to call in any order (show twice, hide when not shown).
 */
@Singleton
class OverlayWindowManager @Inject constructor(
    @param:OverlayWindowContext private val windowContext: Context,
) {
    private val windowManager: WindowManager =
        windowContext.getSystemService(WindowManager::class.java)

    private var host: ComposeOverlayHost? = null

    val isShowing: Boolean get() = host != null

    /**
     * Adds the overlay window at [position] with [content]. Returns false if the system refused the window,
     * which happens when the overlay permission was revoked while the service was running.
     */
    @MainThread
    fun show(position: OverlayPosition, content: @Composable () -> Unit): Boolean {
        if (host != null) return true
        val newHost = ComposeOverlayHost(windowContext).apply { setContent(content) }
        return try {
            windowManager.addView(newHost.view, buildLayoutParams(position))
            newHost.onShown()
            host = newHost
            true
        } catch (e: WindowManager.BadTokenException) {
            // "permission denied for window type": SYSTEM_ALERT_WINDOW is not (or no longer) held.
            Log.w(TAG, "Overlay window rejected", e)
            newHost.destroy()
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Overlay window rejected", e)
            newHost.destroy()
            false
        }
    }

    /**
     * Moves the window. The window is anchored by its centre, so it stays centred on
     * [position] when its content changes size.
     */
    @MainThread
    fun updatePosition(position: OverlayPosition) {
        val current = host ?: return
        try {
            windowManager.updateViewLayout(current.view, buildLayoutParams(position))
        } catch (e: IllegalArgumentException) {
            // The system already dropped the view (e.g. permission revoked). Forget it.
            Log.w(TAG, "Overlay view no longer attached", e)
            host = null
            current.destroy()
        }
    }

    @MainThread
    fun hide() {
        val current = host ?: return
        host = null
        try {
            // Synchronous removal so the window is gone before the service finishes dying;
            // a leaked overlay window is the classic WindowLeaked crash.
            windowManager.removeViewImmediate(current.view)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Overlay view was not attached", e)
        } finally {
            current.destroy()
        }
    }

    private fun buildLayoutParams(position: OverlayPosition): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_FOCUSABLE: never steal key input / the IME from the app underneath.
            // Because the window is WRAP_CONTENT, touches outside the pill already go through.
            // LAYOUT_IN_SCREEN + NO_LIMITS: position relative to the physical screen so the
            // pill can sit inside the status bar area.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = position.xFromCentrePx
            y = position.yPx
            title = "Floatboat"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private companion object {
        const val TAG = "OverlayWindowManager"
    }
}
