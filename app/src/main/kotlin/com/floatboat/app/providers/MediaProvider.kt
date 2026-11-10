package com.floatboat.app.providers

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.scale
import com.floatboat.app.island.IslandEvent
import com.floatboat.app.island.IslandSignal
import com.floatboat.app.services.FloatboatNotificationListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** What a media session reports, reduced to what the island shows. */
data class MediaSnapshot(
    val packageName: String,
    val title: String,
    val artist: String,
    /** A `PlaybackState.STATE_*` value. */
    val state: Int,
    val positionMs: Long,
    val positionAtElapsedMs: Long,
    val speed: Float,
    val lengthMs: Long,
    val art: Bitmap?,
)

/**
 * Turns successive [MediaSnapshot]s into island signals.
 *
 * Playing media shows. A pause shows once more (the event then lingers briefly and expires)
 * but only right after playing: a session that was already paused, or that keeps sending
 * updates while paused, must not keep popping the island.
 */
class MediaSignalMapper {
    private var wasPlaying = false

    fun map(snapshot: MediaSnapshot?): IslandSignal {
        val end = IslandSignal.End(IslandEvent.Media.KEY)
        if (snapshot == null || snapshot.title.isBlank()) {
            wasPlaying = false
            return end
        }
        val playing = snapshot.state == PlaybackState.STATE_PLAYING ||
            snapshot.state == PlaybackState.STATE_BUFFERING
        val justPaused = !playing && wasPlaying && snapshot.state == PlaybackState.STATE_PAUSED
        wasPlaying = playing
        if (!playing && !justPaused) return end
        return IslandSignal.Show(
            IslandEvent.Media(
                packageName = snapshot.packageName,
                title = snapshot.title,
                artist = snapshot.artist,
                isPlaying = playing,
                positionMs = snapshot.positionMs,
                positionAtElapsedMs = snapshot.positionAtElapsedMs,
                speed = snapshot.speed,
                lengthMs = snapshot.lengthMs,
                art = snapshot.art,
            ),
        )
    }
}

/**
 * Now-playing media from whichever app holds the active media session, with real transport
 * controls.
 *
 * Reading other apps' media sessions is only allowed for an enabled notification listener, so
 * this waits for [NotificationAccessState] and uses the listener's component as its credential.
 * Everything is callback-driven: playback position is never polled here.
 */
@Singleton
class MediaProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    accessState: NotificationAccessState,
) : EventProvider {

    private var controller: MediaController? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override val signals: Flow<IslandSignal> = accessState.connected.flatMapLatest { connected ->
        if (connected) sessionSignals() else flowOf(IslandSignal.End(IslandEvent.Media.KEY))
    }

    fun playPause() {
        val current = controller ?: return
        if (current.playbackState?.state == PlaybackState.STATE_PLAYING) {
            current.transportControls.pause()
        } else {
            current.transportControls.play()
        }
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    /** The player's own "open me" intent, if it published one. */
    fun sessionActivity(): PendingIntent? = controller?.sessionActivity

    private fun sessionSignals(): Flow<IslandSignal> = callbackFlow {
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val listenerComponent = ComponentName(context, FloatboatNotificationListener::class.java)
        val handler = Handler(Looper.getMainLooper())
        val mapper = MediaSignalMapper()

        fun publish() {
            trySend(mapper.map(controller?.let(::snapshot)))
        }

        lateinit var select: (List<MediaController>?) -> Unit
        val controllerCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
            override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
            override fun onSessionDestroyed() {
                select(activeSessions(manager, listenerComponent))
            }
        }
        select = { sessions ->
            // Prefer whatever is actually playing; otherwise the system's top session.
            val next = sessions.orEmpty().firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: sessions.orEmpty().firstOrNull()
            if (next?.sessionToken != controller?.sessionToken) {
                controller?.unregisterCallback(controllerCallback)
                controller = next
                next?.registerCallback(controllerCallback, handler)
            }
            publish()
        }
        val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { select(it) }
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            select(activeSessions(manager, listenerComponent))
        } catch (_: SecurityException) {
            // Access was revoked between the listener connecting and us getting here.
            trySend(IslandSignal.End(IslandEvent.Media.KEY))
        }
        awaitClose {
            manager.removeOnActiveSessionsChangedListener(sessionsListener)
            controller?.unregisterCallback(controllerCallback)
            controller = null
        }
    }

    private fun activeSessions(
        manager: MediaSessionManager,
        component: ComponentName,
    ): List<MediaController> = try {
        manager.getActiveSessions(component)
    } catch (_: SecurityException) {
        emptyList()
    }

    private fun snapshot(controller: MediaController): MediaSnapshot {
        val metadata = controller.metadata
        val state = controller.playbackState
        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        return MediaSnapshot(
            packageName = controller.packageName,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: "",
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: "",
            state = state?.state ?: PlaybackState.STATE_NONE,
            positionMs = state?.position ?: 0L,
            positionAtElapsedMs = state?.lastPositionUpdateTime ?: 0L,
            speed = state?.playbackSpeed ?: 1f,
            lengthMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0)
                ?: 0L,
            art = art?.let(::thumbnail),
        )
    }

    /** Album art can be full-screen sized; the island only ever draws it small. */
    private fun thumbnail(source: Bitmap): Bitmap {
        if (source.width <= ART_SIZE_PX && source.height <= ART_SIZE_PX) return source
        return source.scale(ART_SIZE_PX, ART_SIZE_PX)
    }

    private companion object {
        const val ART_SIZE_PX = 160
    }
}
