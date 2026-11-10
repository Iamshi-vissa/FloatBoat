package com.floatboat.app.island.ui

import android.app.Application
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.floatboat.app.core.overlay.OverlayPosition
import com.floatboat.app.core.overlay.OverlayWindowManager
import com.floatboat.app.core.overlay.createOverlayWindowContext
import com.floatboat.app.island.IslandEvent
import com.floatboat.app.island.IslandState
import org.junit.Assert.assertEquals
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
import java.time.Duration
import kotlin.math.roundToInt

/** The overlay window wraps the island, so its size is what the user can see and touch. */
@RunWith(RobolectricTestRunner::class)
class IslandTest {

    private val app: Application = RuntimeEnvironment.getApplication()
    private val density = app.resources.displayMetrics.density
    private val compact = DpSize(112.dp, 32.dp)
    private val expanded = DpSize(300.dp, 88.dp)
    private var state by mutableStateOf<IslandState>(IslandState.Hidden)

    @Before
    fun setUp() {
        ShadowSettings.setCanDrawOverlays(true)
        OverlayWindowManager(createOverlayWindowContext(app)).show(OverlayPosition(0, 0)) {
            Island(state, compact, expanded, onTap = {}, onLongPress = {}, onDismiss = {})
        }
        settle()
    }

    /** Lets animations run to completion. */
    private fun settle() {
        repeat(120) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)) }
    }

    private fun window(): View =
        Shadow.extract<ShadowWindowManagerImpl>(app.getSystemService(WindowManager::class.java))
            .views.single()

    private fun px(dp: Float) = (dp * density).roundToInt()

    @Test
    fun `hidden island takes up next to nothing`() {
        assertTrue("width ${window().width}", window().width <= px(1f) + 1)
        assertTrue("height ${window().height}", window().height <= px(1f) + 1)
    }

    @Test
    fun `compact island is exactly the compact size`() {
        state = IslandState.Compact(IslandEvent.Torch)
        settle()

        assertEquals(px(112f), window().width)
        assertEquals(px(32f), window().height)
    }

    @Test
    fun `expanded island has room for the expanded size and shrinks back afterwards`() {
        state = IslandState.Expanded(IslandEvent.Charging(50))
        settle()
        assertTrue(window().width >= px(300f))
        assertTrue(window().height >= px(88f))

        state = IslandState.Compact(IslandEvent.Charging(50))
        settle()
        assertEquals(px(112f), window().width)
        assertEquals(px(32f), window().height)
    }

    @Test
    fun `island disappears again when the event ends`() {
        state = IslandState.Compact(IslandEvent.Torch)
        settle()

        state = IslandState.Hidden
        settle()

        assertTrue(window().width <= px(1f) + 1)
    }
}
