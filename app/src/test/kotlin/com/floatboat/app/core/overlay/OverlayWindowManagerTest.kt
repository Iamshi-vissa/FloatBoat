package com.floatboat.app.core.overlay

import android.app.Application
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

/**
 * Runs the real overlay code against Robolectric's Android framework: a ComposeView hosted
 * outside an Activity, added to and removed from a WindowManager.
 */
@RunWith(RobolectricTestRunner::class)
class OverlayWindowManagerTest {

    private val app: Application = RuntimeEnvironment.getApplication()
    private lateinit var overlay: OverlayWindowManager

    @Before
    fun setUp() {
        ShadowSettings.setCanDrawOverlays(true)
        overlay = OverlayWindowManager(app)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun overlayViews(): List<View> =
        Shadow.extract<ShadowWindowManagerImpl>(app.getSystemService(WindowManager::class.java))
            .views

    @Test
    fun `show attaches a composing view without an Activity`() {
        var composed = false

        val shown = overlay.show {
            // rememberSaveable needs the SavedStateRegistryOwner the host provides.
            rememberSaveable { 1 }
            SideEffect { composed = true }
        }
        idle()

        assertTrue(shown)
        assertTrue(overlay.isShowing)
        assertTrue("content should have been composed", composed)
        val view = overlayViews().single()
        assertEquals(
            Lifecycle.State.RESUMED,
            view.findViewTreeLifecycleOwner()!!.lifecycle.currentState,
        )
    }

    @Test
    fun `window is a non-focusable overlay anchored top centre`() {
        overlay.show {}
        idle()

        val params = overlayViews().single().layoutParams as WindowManager.LayoutParams
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, params.gravity)
        assertNotEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, params.width)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, params.height)
        assertTrue("pill must keep a gap from the top edge", params.y > 0)
    }

    @Test
    fun `show is idempotent`() {
        overlay.show {}
        overlay.show {}
        idle()

        assertEquals(1, overlayViews().size)
    }

    @Test
    fun `hide removes the window and disposes the composition`() {
        var disposed = false
        overlay.show {
            DisposableEffect(Unit) { onDispose { disposed = true } }
        }
        idle()
        val owner = overlayViews().single().findViewTreeLifecycleOwner()!!

        overlay.hide()
        idle()

        assertFalse(overlay.isShowing)
        assertTrue(overlayViews().isEmpty())
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        assertTrue("composition should be disposed", disposed)
    }

    @Test
    fun `hide and configuration change are safe when nothing is shown`() {
        overlay.hide()
        overlay.onConfigurationChanged()

        assertFalse(overlay.isShowing)
    }

    @Test
    fun `survives a configuration change and can be shown again after hide`() {
        overlay.show {}
        idle()

        overlay.onConfigurationChanged()
        idle()
        assertEquals(1, overlayViews().size)

        overlay.hide()
        assertTrue(overlay.show {})
        idle()
        assertEquals(1, overlayViews().size)
    }
}
