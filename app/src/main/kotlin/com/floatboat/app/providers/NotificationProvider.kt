package com.floatboat.app.providers

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
)

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

    fun onPosted(info: NotificationInfo, ownPackage: String) {
        toEvent(info, ownPackage)?.let { bus.tryEmit(IslandSignal.Show(it)) }
    }

    fun onRemoved(key: String) {
        bus.tryEmit(IslandSignal.End(key))
    }

    companion object {
        private const val CATEGORY_CALL = "call"

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
}
