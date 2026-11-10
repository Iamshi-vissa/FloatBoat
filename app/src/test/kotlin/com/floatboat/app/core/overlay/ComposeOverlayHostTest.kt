package com.floatboat.app.core.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ComposeOverlayHostTest {

    private val host = ComposeOverlayHost(RuntimeEnvironment.getApplication())

    @Test
    fun `installs itself as all three view tree owners`() {
        assertSame(host, host.view.findViewTreeLifecycleOwner())
        assertSame(host, host.view.findViewTreeViewModelStoreOwner())
        assertSame(host, host.view.findViewTreeSavedStateRegistryOwner())
    }

    @Test
    fun `lifecycle goes created, resumed, destroyed`() {
        assertEquals(Lifecycle.State.CREATED, host.lifecycle.currentState)

        host.onShown()
        assertEquals(Lifecycle.State.RESUMED, host.lifecycle.currentState)

        host.destroy()
        assertEquals(Lifecycle.State.DESTROYED, host.lifecycle.currentState)
    }

    @Test
    fun `destroy is idempotent`() {
        host.onShown()
        host.destroy()
        host.destroy()

        assertEquals(Lifecycle.State.DESTROYED, host.lifecycle.currentState)
    }
}
