package com.floatboat.app.providers

import android.media.session.PlaybackState
import com.floatboat.app.island.IslandEvent
import com.floatboat.app.island.IslandSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSignalMapperTest {

    private val end = IslandSignal.End("media")
    private val mapper = MediaSignalMapper()

    private fun snapshot(state: Int, title: String = "Song") = MediaSnapshot(
        packageName = "com.player",
        title = title,
        artist = "Artist",
        state = state,
        positionMs = 5_000,
        positionAtElapsedMs = 100,
        speed = 1f,
        lengthMs = 200_000,
        art = null,
    )

    private fun IslandSignal.media() = (this as IslandSignal.Show).event as IslandEvent.Media

    @Test
    fun `no session means no media`() {
        assertEquals(end, mapper.map(null))
    }

    @Test
    fun `playing media is shown with its details`() {
        val media = mapper.map(snapshot(PlaybackState.STATE_PLAYING)).media()

        assertTrue(media.isPlaying)
        assertEquals("Song", media.title)
        assertEquals("Artist", media.artist)
        assertEquals(5_000, media.positionMs)
        assertEquals(200_000, media.lengthMs)
    }

    @Test
    fun `buffering counts as playing`() {
        assertTrue(mapper.map(snapshot(PlaybackState.STATE_BUFFERING)).media().isPlaying)
    }

    @Test
    fun `a session that is already paused does not pop the island`() {
        assertEquals(end, mapper.map(snapshot(PlaybackState.STATE_PAUSED)))
    }

    @Test
    fun `pausing shows once and later paused updates do not pop it again`() {
        mapper.map(snapshot(PlaybackState.STATE_PLAYING))

        assertFalse(mapper.map(snapshot(PlaybackState.STATE_PAUSED)).media().isPlaying)
        assertEquals(end, mapper.map(snapshot(PlaybackState.STATE_PAUSED)))
    }

    @Test
    fun `stopping ends media`() {
        mapper.map(snapshot(PlaybackState.STATE_PLAYING))

        assertEquals(end, mapper.map(snapshot(PlaybackState.STATE_STOPPED)))
    }

    @Test
    fun `media without a title is ignored`() {
        assertEquals(end, mapper.map(snapshot(PlaybackState.STATE_PLAYING, title = " ")))
    }
}
