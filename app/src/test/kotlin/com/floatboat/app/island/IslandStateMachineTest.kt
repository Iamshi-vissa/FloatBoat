package com.floatboat.app.island

import com.floatboat.app.island.IslandSignal.End
import com.floatboat.app.island.IslandSignal.Show
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IslandStateMachineTest {

    private val torch = IslandEvent.Torch
    private val charging = IslandEvent.Charging(percent = 80)
    private val call = IslandEvent.Call("call-1", "com.dialer", "Phone", "Asha")

    private fun notification(key: String = "n-1") =
        IslandEvent.Notification(key, "com.chat", "Chat", "Ravi", "Hello")

    private fun TestScope.machine() = IslandStateMachine(backgroundScope, expandedTimeoutMs = 6_000)

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `starts hidden`() = runTest {
        assertEquals(IslandState.Hidden, machine().state.value)
    }

    @Test
    fun `an event shows the compact island`() = runTest {
        val machine = machine()

        machine.onSignal(Show(torch))

        assertEquals(IslandState.Compact(torch), machine.state.value)
    }

    @Test
    fun `an ongoing event stays until its source ends it`() = runTest {
        val machine = machine()
        machine.onSignal(Show(torch))

        advance(60_000)
        assertEquals(IslandState.Compact(torch), machine.state.value)

        machine.onSignal(End(torch.key))
        assertEquals(IslandState.Hidden, machine.state.value)
    }

    @Test
    fun `a momentary event hides itself after its duration`() = runTest {
        val machine = machine()
        machine.onSignal(Show(charging))

        advance(charging.durationMs - 1)
        assertEquals(IslandState.Compact(charging), machine.state.value)

        advance(1)
        assertEquals(IslandState.Hidden, machine.state.value)
    }

    @Test
    fun `showing the same event again restarts its countdown`() = runTest {
        val machine = machine()
        machine.onSignal(Show(charging))
        advance(3_000)

        machine.onSignal(Show(charging.copy(percent = 81)))
        advance(3_000)

        assertEquals(IslandState.Compact(charging.copy(percent = 81)), machine.state.value)
        advance(1_000)
        assertEquals(IslandState.Hidden, machine.state.value)
    }

    @Test
    fun `higher priority event takes over and the lower one returns afterwards`() = runTest {
        val machine = machine()
        machine.onSignal(Show(torch))

        machine.onSignal(Show(notification()))
        assertEquals(IslandState.Compact(notification()), machine.state.value)

        advance(notification().durationMs)
        assertEquals(IslandState.Compact(torch), machine.state.value)
    }

    @Test
    fun `lower priority event does not interrupt a call`() = runTest {
        val machine = machine()
        machine.onSignal(Show(call))

        machine.onSignal(Show(notification()))

        assertEquals(IslandState.Compact(call), machine.state.value)
    }

    @Test
    fun `newest wins between events of equal priority`() = runTest {
        val machine = machine()
        machine.onSignal(Show(notification("a")))
        machine.onSignal(Show(notification("b")))

        assertEquals(IslandState.Compact(notification("b")), machine.state.value)
    }

    @Test
    fun `tap expands and a second tap collapses`() = runTest {
        val machine = machine()
        machine.onSignal(Show(torch))

        machine.onTap()
        assertEquals(IslandState.Expanded(torch), machine.state.value)

        machine.onTap()
        assertEquals(IslandState.Compact(torch), machine.state.value)
    }

    @Test
    fun `tap does nothing while hidden`() = runTest {
        val machine = machine()

        machine.onTap()
        machine.onDismiss()

        assertEquals(IslandState.Hidden, machine.state.value)
    }

    @Test
    fun `expanded island collapses by itself`() = runTest {
        val machine = machine()
        machine.onSignal(Show(torch))
        machine.onTap()

        advance(6_000)

        assertEquals(IslandState.Compact(torch), machine.state.value)
    }

    @Test
    fun `countdown is paused while expanded and restarts on collapse`() = runTest {
        val machine = machine()
        machine.onSignal(Show(charging))
        advance(3_000)
        machine.onTap()

        // Well past the event's own 4 s: still up because it is being looked at.
        advance(5_000)
        assertEquals(IslandState.Expanded(charging), machine.state.value)

        machine.onTap()
        advance(charging.durationMs - 1)
        assertEquals(IslandState.Compact(charging), machine.state.value)
        advance(1)
        assertEquals(IslandState.Hidden, machine.state.value)
    }

    @Test
    fun `an update to the expanded event keeps it expanded`() = runTest {
        val machine = machine()
        machine.onSignal(Show(charging))
        machine.onTap()

        machine.onSignal(Show(charging.copy(percent = 90)))

        assertEquals(IslandState.Expanded(charging.copy(percent = 90)), machine.state.value)
    }

    @Test
    fun `a more important event collapses an expanded one and takes over`() = runTest {
        val machine = machine()
        machine.onSignal(Show(torch))
        machine.onTap()

        machine.onSignal(Show(call))
        assertEquals(IslandState.Compact(call), machine.state.value)

        machine.onSignal(End(call.key))
        assertEquals(IslandState.Compact(torch), machine.state.value)
    }

    @Test
    fun `expanded island hides when its event ends`() = runTest {
        val machine = machine()
        machine.onSignal(Show(torch))
        machine.onTap()

        machine.onSignal(End(torch.key))

        assertEquals(IslandState.Hidden, machine.state.value)
        // The stale collapse timer must not resurrect anything.
        advance(10_000)
        assertEquals(IslandState.Hidden, machine.state.value)
    }

    @Test
    fun `dismiss removes the current event and reveals the next`() = runTest {
        val machine = machine()
        machine.onSignal(Show(torch))
        machine.onSignal(Show(call))

        machine.onDismiss()
        assertEquals(IslandState.Compact(torch), machine.state.value)

        machine.onDismiss()
        assertEquals(IslandState.Hidden, machine.state.value)
    }

    @Test
    fun `a dismissed ongoing event comes back when its source shows it again`() = runTest {
        val machine = machine()
        machine.onSignal(Show(torch))
        machine.onDismiss()

        machine.onSignal(End(torch.key))
        machine.onSignal(Show(torch))

        assertEquals(IslandState.Compact(torch), machine.state.value)
    }

    @Test
    fun `clear hides everything and cancels timers`() = runTest {
        val machine = machine()
        machine.onSignal(Show(torch))
        machine.onSignal(Show(charging))
        machine.onTap()

        machine.clear()
        advance(60_000)

        assertEquals(IslandState.Hidden, machine.state.value)
    }

    @Test
    fun `ending an unknown event is harmless`() = runTest {
        val machine = machine()
        machine.onSignal(Show(torch))

        machine.onSignal(End("nope"))

        assertEquals(IslandState.Compact(torch), machine.state.value)
    }
}
