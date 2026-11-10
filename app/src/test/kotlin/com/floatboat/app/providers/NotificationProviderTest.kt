package com.floatboat.app.providers

import app.cash.turbine.test
import com.floatboat.app.island.IslandEvent
import com.floatboat.app.island.IslandSignal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `posted and removed notifications are emitted as signals`() = runTest {
        val provider = NotificationProvider()

        provider.signals.test {
            provider.onPosted(info(), own)
            assertEquals(
                IslandSignal.Show(IslandEvent.Notification("key", "com.chat", "Chat", "Ravi", "Hello")),
                awaitItem(),
            )

            provider.onPosted(info(isOngoing = true), own)
            provider.onRemoved("key")
            assertEquals(IslandSignal.End("key"), awaitItem())
        }
    }
}
