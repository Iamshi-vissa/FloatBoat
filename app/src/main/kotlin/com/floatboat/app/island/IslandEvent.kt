package com.floatboat.app.island

/**
 * Something the island can show.
 *
 * [key] identifies the event: showing an event with a key that is already active replaces it.
 * [priority] decides which event owns the island when several are active (higher wins).
 * [durationMs] is how long a momentary event stays up; null means it stays until its source
 * ends it (for example the torch while it is on).
 */
sealed interface IslandEvent {
    val key: String
    val priority: Int
    val durationMs: Long?

    data object Torch : IslandEvent {
        override val key = "torch"
        override val priority = 20
        override val durationMs: Long? = null
    }

    data class Charging(val percent: Int) : IslandEvent {
        override val key = "charging"
        override val priority = 30
        override val durationMs: Long = 4_000
    }

    data class Notification(
        override val key: String,
        val packageName: String,
        val appName: String,
        val title: String,
        val text: String,
    ) : IslandEvent {
        override val priority = 40
        override val durationMs: Long = 5_000
    }

    data class Call(
        override val key: String,
        val packageName: String,
        val appName: String,
        val caller: String,
    ) : IslandEvent {
        override val priority = 100
        override val durationMs: Long? = null
    }
}

/** What an event source tells the island. */
sealed interface IslandSignal {
    data class Show(val event: IslandEvent) : IslandSignal
    data class End(val key: String) : IslandSignal
}

/** What the island is doing right now. The UI is a pure function of this. */
sealed interface IslandState {
    /** Nothing to show: the island is not on screen at all. */
    data object Hidden : IslandState

    /** Small pill around the camera with an icon and a short status. */
    data class Compact(val event: IslandEvent) : IslandState

    /** Enlarged pill with details, after a tap. */
    data class Expanded(val event: IslandEvent) : IslandState

    val eventOrNull: IslandEvent?
        get() = when (this) {
            Hidden -> null
            is Compact -> event
            is Expanded -> event
        }
}
