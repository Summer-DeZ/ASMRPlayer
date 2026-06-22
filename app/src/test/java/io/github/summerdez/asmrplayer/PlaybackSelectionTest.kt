package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.data.*
import io.github.summerdez.asmrplayer.data.remote.*
import io.github.summerdez.asmrplayer.data.download.*
import io.github.summerdez.asmrplayer.data.files.*
import io.github.summerdez.asmrplayer.domain.*
import io.github.summerdez.asmrplayer.domain.model.*
import io.github.summerdez.asmrplayer.playback.*
import io.github.summerdez.asmrplayer.presentation.*
import io.github.summerdez.asmrplayer.ui.*
import io.github.summerdez.asmrplayer.ui.activity.*
import io.github.summerdez.asmrplayer.ui.components.*
import io.github.summerdez.asmrplayer.ui.screens.*
import io.github.summerdez.asmrplayer.ui.theme.*
import io.github.summerdez.asmrplayer.ui.util.*
import io.github.summerdez.asmrplayer.di.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSelectionTest {
    private val firstTrack = TrackItem("track-1", "First", "content://first")
    private val secondTrack = TrackItem("track-2", "Second", "content://second")
    private val playlist = Playlist("playlist-1", "Playlist", listOf(firstTrack, secondTrack))
    private val fallbackPlaylist = Playlist("fallback", "Fallback")

    @Test
    fun startsWithDefaultLabelsAndNoPlaylistPosition() {
        val selection = PlaybackSelection("No audio", "No subtitle")

        assertFalse(selection.hasAudio())
        assertEquals("No audio", selection.audioTitle())
        assertEquals("No subtitle", selection.subtitleTitle())
        assertEquals("", selection.playlistId())
        assertEquals(-1, selection.playlistIndex())
        assertEquals("", selection.currentTrackId(listOf(playlist)))
    }

    @Test
    fun playlistPositionCanMoveShiftAndClear() {
        val selection = PlaybackSelection("No audio", "No subtitle")

        selection.moveToPlaylist("playlist-1", 0)
        assertTrue(selection.matchesPlaylistPosition("playlist-1", 0))
        assertEquals("track-1", selection.currentTrackId(listOf(playlist)))
        assertEquals(0, selection.baseIndexFor(playlist))
        assertTrue(selection.hasNextIn(playlist))

        selection.shiftPlaylistIndex(1)
        assertEquals("track-2", selection.currentTrackId(listOf(playlist)))
        assertFalse(selection.hasNextIn(playlist))

        selection.clearPlaylistPosition()
        assertEquals("", selection.playlistId())
        assertEquals(-1, selection.playlistIndex())
        assertEquals(-1, selection.baseIndexFor(playlist))
    }

    @Test
    fun activePlaylistPrefersCurrentPlaylistThenSelectedPlaylist() {
        val selection = PlaybackSelection("No audio", "No subtitle")

        assertSame(fallbackPlaylist, selection.activePlaylist(listOf(playlist), fallbackPlaylist))

        selection.moveToPlaylist("playlist-1", 0)
        assertSame(playlist, selection.activePlaylist(listOf(playlist), fallbackPlaylist))
    }

    @Test
    fun titleUpdatesFallBackWhenEmpty() {
        val selection = PlaybackSelection("No audio", "No subtitle")

        selection.updateAudioTitle("Renamed")
        selection.updateSubtitle(null, "subtitle.srt")
        assertEquals("Renamed", selection.audioTitle())
        assertEquals("subtitle.srt", selection.subtitleTitle())

        selection.updateAudioTitle("")
        selection.updateSubtitle(null, "")
        assertEquals("No audio", selection.audioTitle())
        assertEquals("No subtitle", selection.subtitleTitle())
    }

    @Test
    fun deletingCurrentPlaylistClearsPlaylistPosition() {
        val selection = PlaybackSelection("No audio", "No subtitle")
        selection.moveToPlaylist("playlist-1", 1)

        selection.onPlaylistDeleted("playlist-1")

        assertEquals("", selection.playlistId())
        assertEquals(-1, selection.playlistIndex())
    }

    @Test
    fun deletingDifferentPlaylistKeepsPlaylistPosition() {
        val selection = PlaybackSelection("No audio", "No subtitle")
        selection.moveToPlaylist("playlist-1", 1)

        selection.onPlaylistDeleted("other")

        assertEquals("playlist-1", selection.playlistId())
        assertEquals(1, selection.playlistIndex())
    }

    @Test
    fun removingEarlierTrackShiftsCurrentIndexBack() {
        val selection = PlaybackSelection("No audio", "No subtitle")
        selection.moveToPlaylist("playlist-1", 1)

        selection.onTrackRemoved("playlist-1", 0)

        assertEquals("playlist-1", selection.playlistId())
        assertEquals(0, selection.playlistIndex())
    }

    @Test
    fun removingCurrentTrackClearsPlaylistPosition() {
        val selection = PlaybackSelection("No audio", "No subtitle")
        selection.moveToPlaylist("playlist-1", 1)

        selection.onTrackRemoved("playlist-1", 1)

        assertEquals("", selection.playlistId())
        assertEquals(-1, selection.playlistIndex())
    }

    @Test
    fun removingLaterOrDifferentPlaylistTrackKeepsPlaylistPosition() {
        val selection = PlaybackSelection("No audio", "No subtitle")
        selection.moveToPlaylist("playlist-1", 0)

        selection.onTrackRemoved("playlist-1", 1)
        selection.onTrackRemoved("other", 0)

        assertEquals("playlist-1", selection.playlistId())
        assertEquals(0, selection.playlistIndex())
    }

    @Test
    fun movingCurrentTrackMovesPlaylistPositionToTarget() {
        val selection = PlaybackSelection("No audio", "No subtitle")
        selection.moveToPlaylist("playlist-1", 1)

        selection.onTrackMoved(
                "playlist-1",
                1,
                "playlist-2",
                3,
                "track-2",
                "track-2")

        assertEquals("playlist-2", selection.playlistId())
        assertEquals(3, selection.playlistIndex())
    }

    @Test
    fun movingEarlierTrackFromCurrentPlaylistShiftsIndexBack() {
        val selection = PlaybackSelection("No audio", "No subtitle")
        selection.moveToPlaylist("playlist-1", 2)

        selection.onTrackMoved(
                "playlist-1",
                0,
                "playlist-2",
                0,
                "track-1",
                "track-3")

        assertEquals("playlist-1", selection.playlistId())
        assertEquals(1, selection.playlistIndex())
    }

    @Test
    fun movingOtherTrackOutsideCurrentPositionKeepsPlaylistPosition() {
        val selection = PlaybackSelection("No audio", "No subtitle")
        selection.moveToPlaylist("playlist-1", 1)

        selection.onTrackMoved(
                "playlist-1",
                2,
                "playlist-2",
                0,
                "track-3",
                "track-2")

        assertEquals("playlist-1", selection.playlistId())
        assertEquals(1, selection.playlistIndex())
    }
}
