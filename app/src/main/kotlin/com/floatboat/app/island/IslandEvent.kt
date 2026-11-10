package com.floatboat.app.island

import android.graphics.Bitmap

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

    /**
     * Music or other media. Stays up while playing; after a pause it lingers briefly and then
     * goes away. Position is a snapshot: the UI extrapolates from [positionAtElapsedMs].
     */
    data class Media(
        val packageName: String,
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val positionMs: Long,
        /** `SystemClock.elapsedRealtime()` at which [positionMs] was correct. */
        val positionAtElapsedMs: Long,
        val speed: Float,
        /** Track length, or 0 if unknown (live streams). */
        val lengthMs: Long,
        val art: Bitmap?,
    ) : IslandEvent {
        override val key = KEY
        override val priority = 25
        override val durationMs: Long? get() = if (isPlaying) null else 4_000

        companion object {
            const val KEY = "media"
        }
    }

    data class Bluetooth(val deviceName: String) : IslandEvent {
        override val key = KEY
        override val priority = 35
        override val durationMs: Long = 4_000

        companion object {
            const val KEY = "bluetooth"
        }
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
        /** Whether the calling app offers these actions on its notification. */
        val canAnswer: Boolean = false,
        val canDecline: Boolean = false,
    ) : IslandEvent {
        override val priority = 100
        override val durationMs: Long? = null
    }
}

/** Buttons the expanded island can offer. */
enum class IslandAction { PlayPause, Next, Previous, Answer, Decline }

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
