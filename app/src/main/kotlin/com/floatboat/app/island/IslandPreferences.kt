package com.floatboat.app.island

/** The user's choices about when and how the island appears. */
data class IslandPreferences(
    /** The user wants the island on. Used to restore it after a reboot. */
    val enabled: Boolean = false,
    val startOnBoot: Boolean = true,
    val haptics: Boolean = true,
    /** Stay out of the way while a fullscreen app or game hides the status bar. */
    val hideInFullscreen: Boolean = false,
    val showTorch: Boolean = true,
    val showCharging: Boolean = true,
    val showMedia: Boolean = true,
    val showBluetooth: Boolean = true,
    val showNotifications: Boolean = true,
    val showCalls: Boolean = true,
    /** Apps whose notifications never pop the island. */
    val mutedPackages: Set<String> = emptySet(),
) {
    /** Whether this event is allowed to reach the island. */
    fun allows(event: IslandEvent): Boolean = when (event) {
        IslandEvent.Torch -> showTorch
        is IslandEvent.Charging -> showCharging
        is IslandEvent.Media -> showMedia
        is IslandEvent.Bluetooth -> showBluetooth
        is IslandEvent.Call -> showCalls
        is IslandEvent.Notification -> showNotifications && event.packageName !in mutedPackages
    }
}
