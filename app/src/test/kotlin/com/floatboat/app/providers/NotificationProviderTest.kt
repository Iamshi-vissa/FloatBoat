package com.floatboat.app.providers

import app.cash.turbine.test
import com.floatboat.app.island.IslandEvent
import com.floatboat.app.island.IslandSignal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationProviderTest {

    private val own = "com.floatboat.app"

    private fun info(
        packageName: String = "com.chat",
        title: String = "Ravi",
        text: String = "Hello",
        category: String? = "msg",
        isOngoing: Boolean = false,
        isGroupSummary: Boolean = false,
    ) = NotificationInfo("key", packageName, "Chat", title, text, category, isOngoing, isGroupSummary)

    @Test
    fun `an ordinary notification becomes a notification event`() {
        assertEquals(
            IslandEvent.Notification("key", "com.chat", "Chat", "Ravi", "Hello"),
            NotificationProvider.toEvent(info(), own),
        )
    }

    @Test
    fun `a call notification becomes a call event even though it is ongoing`() {
        assertEquals(
            IslandEvent.Call("key", "com.chat", "Chat", "Ravi"),
            NotificationProvider.toEvent(info(category = "call", isOngoing = true), own),
        )
    }

    @Test
    fun `a call without a title uses its text as the caller`() {
        val event = NotificationProvider.toEvent(info(category = "call", title = ""), own)

        assertEquals("Hello", (event as IslandEvent.Call).caller)
    }

    @Test
    fun `our own, ongoing, summary and empty notifications are ignored`() {
        assertNull(NotificationProvider.toEvent(info(packageName = own), own))
        assertNull(NotificationProvider.toEvent(info(isOngoing = true), own))
        assertNull(NotificationProvider.toEvent(info(isGroupSummary = true), own))
        assertNull(NotificationProvider.toEvent(info(title = " ", text = ""), own))
    }

    @Test
    fun `call actions offered by the calling app are carried on the event`() {
        val event = NotificationProvider.toEvent(
            info(category = "call").copy(hasAnswerAction = true, hasDeclineAction = true),
            own,
        ) as IslandEvent.Call

        assertTrue(event.canAnswer)
        assertTrue(event.canDecline)
    }

    @Test
    fun `posted and removed notifications are emitted as signals`() = runTest {
        val provider = NotificationProvider()

        provider.signals.test {
            provider.onPosted(info(), own, actions = null, nowMs = 0)
            assertEquals(
                IslandSignal.Show(IslandEvent.Notification("key", "com.chat", "Chat", "Ravi", "Hello")),
                awaitItem(),
            )

            provider.onPosted(info(isOngoing = true), own, actions = null, nowMs = 10_000)
            provider.onRemoved("key")
            assertEquals(IslandSignal.End("key"), awaitItem())
        }
    }

    @Test
    fun `a burst from one app pops the island once, other apps are unaffected`() = runTest {
        val provider = NotificationProvider()

        provider.signals.test {
            provider.onPosted(info(), own, null, nowMs = 1_000)
            provider.onPosted(info(), own, null, nowMs = 1_200)
            provider.onPosted(info(packageName = "com.mail"), own, null, nowMs = 1_300)
            provider.onPosted(info(), own, null, nowMs = 1_000 + NotificationProvider.MIN_INTERVAL_MS)

            assertEquals("com.chat", (awaitItem().event()).packageName)
            assertEquals("com.mail", (awaitItem().event()).packageName)
            assertEquals("com.chat", (awaitItem().event()).packageName)
        }
    }

    @Test
    fun `calls are never rate limited`() = runTest {
        val provider = NotificationProvider()

        provider.signals.test {
            provider.onPosted(info(category = "call"), own, null, nowMs = 0)
            provider.onPosted(info(category = "call"), own, null, nowMs = 1)

            awaitItem()
            awaitItem()
        }
    }

    @Test
    fun `apps that posted are remembered for the mute list`() {
        val provider = NotificationProvider()

        provider.onPosted(info(), own, null, nowMs = 0)
        provider.onPosted(info(packageName = own), own, null, nowMs = 0)
        provider.onPosted(info(category = "call", packageName = "com.dialer"), own, null, nowMs = 0)

        assertEquals(mapOf("com.chat" to "Chat"), provider.recentApps.value)
    }

    private fun IslandSignal.event() = (this as IslandSignal.Show).event as IslandEvent.Notification
}
