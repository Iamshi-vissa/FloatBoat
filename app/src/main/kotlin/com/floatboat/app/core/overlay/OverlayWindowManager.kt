package com.floatboat.app.core.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import com.floatboat.app.island.ui.PillDefaults
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * The only place that talks to [WindowManager] for the island overlay.
 *
 * Owns a single overlay window containing a [ComposeOverlayHost]. All methods must be called
 * on the main thread and are safe to call in any order (show twice, hide when not shown).
 */
@Singleton
class OverlayWindowManager @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) {
    private val windowContext: Context = createWindowContext()
    private val windowManager: WindowManager =
        windowContext.getSystemService(WindowManager::class.java)

    private var host: ComposeOverlayHost? = null

    val isShowing: Boolean get() = host != null

    /**
     * Adds the overlay window with [content]. Returns false if the system refused the window,
     * which happens when the overlay permission was revoked while the service was running.
     */
    @MainThread
    fun show(content: @Composable () -> Unit): Boolean {
        if (host != null) return true
        val newHost = ComposeOverlayHost(windowContext).apply { setContent(content) }
        return try {
            windowManager.addView(newHost.view, buildLayoutParams())
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
     * Re-applies layout params after a configuration change. Gravity keeps the pill centred
     * by itself; the top offset has to be recomputed because the status bar height differs
     * between portrait and landscape and across fold states.
     */
    @MainThread
    fun onConfigurationChanged() {
        val current = host ?: return
        try {
            windowManager.updateViewLayout(current.view, buildLayoutParams())
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

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val density = windowContext.resources.displayMetrics.density
        val topOffset = PillPlacement.topOffsetPx(
            statusBarHeightPx = statusBarHeightPx(),
            pillHeightPx = (PillDefaults.Height.value * density).roundToInt(),
            minMarginPx = (PillDefaults.MinTopMargin.value * density).roundToInt(),
        )
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
            y = topOffset
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

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    private fun statusBarHeightPx(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
                .top
        }
        // Before API 30 there is no public way to read insets without an attached view.
        val resources = windowContext.resources
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id != 0) resources.getDimensionPixelSize(id) else 0
    }

    /**
     * From Android 11 a WindowManager must come from a context associated with a display and a
     * window type; the application context is neither, and using it reports the wrong metrics
     * after rotation. Older versions have no such notion, so the app context is correct there.
     */
    private fun createWindowContext(): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return appContext
        val display = appContext.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)
        return appContext.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }

    private companion object {
        const val TAG = "OverlayWindowManager"
    }
}
