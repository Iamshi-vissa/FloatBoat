package com.floatboat.app.providers

import android.app.PendingIntent
import com.floatboat.app.island.IslandEvent
import com.floatboat.app.island.IslandSignal
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** The fields of a status bar notification that the island cares about. */
data class NotificationInfo(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val category: String?,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    val hasAnswerAction: Boolean = false,
    val hasDeclineAction: Boolean = false,
)

/** What tapping the island can trigger for a notification. Any of them may be missing. */
class NotificationIntents(
    val content: PendingIntent?,
    val answer: PendingIntent?,
    val decline: PendingIntent?,
)

/** Whether the system has connected the notification listener (i.e. access is granted). */
@Singleton
class NotificationAccessState @Inject constructor() {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun setConnected(connected: Boolean) {
        _connected.value = connected
    }
}

/**
 * Notifications and calls, fed by `FloatboatNotificationListener`.
 *
 * The system binds the listener service on its own schedule, independent of the island
 * service, so the listener publishes into this singleton and the island reads from it.
 * Signals posted while nobody is collecting (island switched off) are dropped.
 */
@Singleton
class NotificationProvider @Inject constructor() : EventProvider {

    private val bus = MutableSharedFlow<IslandSignal>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val signals: Flow<IslandSignal> = bus

    private val intents = object : LinkedHashMap<String, NotificationIntents>() {
        override fun removeEldestEntry(eldest: Map.Entry<String, NotificationIntents>) =
            size > MAX_TRACKED
    }
    private val lastShownAt = HashMap<String, Long>()

    private val _recentApps = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Package name to label of apps that recently posted, for the per-app mute list. */
    val recentApps: StateFlow<Map<String, String>> = _recentApps.asStateFlow()

    /**
     * @param nowMs a monotonic clock reading, used to drop bursts from the same app.
     */
    @Synchronized
    fun onPosted(
        info: NotificationInfo,
        ownPackage: String,
        actions: NotificationIntents?,
        nowMs: Long,
    ) {
        val event = toEvent(info, ownPackage) ?: return
        if (event is IslandEvent.Notification) {
            _recentApps.update { apps ->
                (apps - info.packageName + (info.packageName to info.appName))
                    .entries.toList().takeLast(MAX_RECENT_APPS).associate { it.toPair() }
            }
            val previous = lastShownAt[info.packageName]
            if (previous != null && nowMs - previous < MIN_INTERVAL_MS) return
            lastShownAt[info.packageName] = nowMs
        }
        if (actions != null) intents[info.key] = actions
        bus.tryEmit(IslandSignal.Show(event))
    }

    @Synchronized
    fun onRemoved(key: String) {
        intents.remove(key)
        bus.tryEmit(IslandSignal.End(key))
    }

    @Synchronized
    fun intentsFor(key: String): NotificationIntents? = intents[key]

    companion object {
        private const val CATEGORY_CALL = "call"
        private const val MAX_TRACKED = 50
        private const val MAX_RECENT_APPS = 30

        /** A chat app firing several notifications at once should pop the island once. */
        const val MIN_INTERVAL_MS = 1_500L

        /**
         * Decides whether a notification should reach the island, and as what.
         *
         * Calls stay up until their notification goes away. Everything else is a momentary
         * pop-up, except ongoing notifications (downloads, music, navigation...), group
         * summaries and empty notifications, which would only be noise here.
         */
        fun toEvent(info: NotificationInfo, ownPackage: String): IslandEvent? {
            if (info.packageName == ownPackage || info.isGroupSummary) return null
            if (info.category == CATEGORY_CALL) {
                return IslandEvent.Call(
                    key = info.key,
                    packageName = info.packageName,
                    appName = info.appName,
                    caller = info.title.ifBlank { info.text },
                    canAnswer = info.hasAnswerAction,
                    canDecline = info.hasDeclineAction,
                )
            }
            if (info.isOngoing) return null
            if (info.title.isBlank() && info.text.isBlank()) return null
            return IslandEvent.Notification(
                key = info.key,
                packageName = info.packageName,
                appName = info.appName,
                title = info.title,
                text = info.text,
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ProvidersModule {
    @Binds @IntoSet
    abstract fun torch(provider: TorchProvider): EventProvider

    @Binds @IntoSet
    abstract fun charging(provider: ChargingProvider): EventProvider

    @Binds @IntoSet
    abstract fun notifications(provider: NotificationProvider): EventProvider

    @Binds @IntoSet
    abstract fun media(provider: MediaProvider): EventProvider

    @Binds @IntoSet
    abstract fun bluetooth(provider: BluetoothProvider): EventProvider
}
