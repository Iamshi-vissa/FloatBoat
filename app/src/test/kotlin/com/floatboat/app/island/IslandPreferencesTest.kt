package com.floatboat.app.island

import com.floatboat.app.island.ui.formatTime
import com.floatboat.app.island.ui.mediaPositionAt
import com.floatboat.app.services.BootReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandPreferencesTest {

    private val media = IslandEvent.Media("com.player", "Song", "Artist", true, 10_000, 1_000, 1f, 60_000, null)
    private val notification = IslandEvent.Notification("k", "com.chat", "Chat", "Ravi", "Hi")
    private val call = IslandEvent.Call("c", "com.dialer", "Phone", "Asha")

    @Test
    fun `everything is allowed by default`() {
        val prefs = IslandPreferences()

        listOf(
            IslandEvent.Torch,
            IslandEvent.Charging(50),
            IslandEvent.Bluetooth("Buds"),
            media,
            notification,
            call,
        ).forEach { assertTrue("$it", prefs.allows(it)) }
    }

    @Test
    fun `each switch blocks only its own kind of event`() {
        assertFalse(IslandPreferences(showTorch = false).allows(IslandEvent.Torch))
        assertFalse(IslandPreferences(showCharging = false).allows(IslandEvent.Charging(50)))
        assertFalse(IslandPreferences(showBluetooth = false).allows(IslandEvent.Bluetooth("Buds")))
        assertFalse(IslandPreferences(showMedia = false).allows(media))
        assertFalse(IslandPreferences(showCalls = false).allows(call))
        assertFalse(IslandPreferences(showNotifications = false).allows(notification))

        assertTrue(IslandPreferences(showNotifications = false).allows(call))
        assertTrue(IslandPreferences(showTorch = false).allows(media))
    }

    @Test
    fun `a muted app's notifications are blocked but its calls are not`() {
        val prefs = IslandPreferences(mutedPackages = setOf("com.chat", "com.dialer"))

        assertFalse(prefs.allows(notification))
        assertTrue(prefs.allows(notification.copy(packageName = "com.mail")))
        assertTrue(prefs.allows(call))
    }

    @Test
    fun `media position advances with time while playing and is clamped to the track`() {
        assertEquals(10_000, mediaPositionAt(media, nowElapsedMs = 1_000))
        assertEquals(15_000, mediaPositionAt(media, nowElapsedMs = 6_000))
        assertEquals(20_000, mediaPositionAt(media.copy(speed = 2f), nowElapsedMs = 6_000))
        assertEquals(60_000, mediaPositionAt(media, nowElapsedMs = 600_000))
    }

    @Test
    fun `media position stands still while paused`() {
        assertEquals(10_000, mediaPositionAt(media.copy(isPlaying = false), nowElapsedMs = 9_000))
    }

    @Test
    fun `times are formatted for display`() {
        assertEquals("0:00", formatTime(0))
        assertEquals("0:59", formatTime(59_999))
        assertEquals("3:05", formatTime(185_000))
        assertEquals("1:02:03", formatTime(3_723_000))
    }

    @Test
    fun `island restarts on boot only if it was on, allowed and still permitted`() {
        assertTrue(BootReceiver.shouldStart(enabled = true, startOnBoot = true, canDrawOverlays = true))
        assertFalse(BootReceiver.shouldStart(enabled = false, startOnBoot = true, canDrawOverlays = true))
        assertFalse(BootReceiver.shouldStart(enabled = true, startOnBoot = false, canDrawOverlays = true))
        assertFalse(BootReceiver.shouldStart(enabled = true, startOnBoot = true, canDrawOverlays = false))
    }
}
