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
import org.junit.Test

class PlaybackNavigationTest {
    private val firstTrack = TrackItem("track-1", "First", "content://first")
    private val secondTrack = TrackItem("track-2", "Second", "content://second")
    private val playlist = Playlist("playlist-1", "Playlist", listOf(firstTrack, secondTrack))

    @Test
    fun emptyPlaylistCannotNavigate() {
        val selection = PlaybackSelection("No audio", "No subtitle")

        val result = PlaybackNavigation.relative(selection, Playlist("empty", "Empty"), 1)

        assertEquals(PlaybackNavigation.Result.Status.EMPTY, result.status)
        assertEquals(-1, result.targetIndex)
    }

    @Test
    fun missingPlaylistCannotNavigate() {
        val selection = PlaybackSelection("No audio", "No subtitle")

        val result = PlaybackNavigation.relative(selection, null, 1)

        assertEquals(PlaybackNavigation.Result.Status.EMPTY, result.status)
        assertEquals(-1, result.targetIndex)
    }

    @Test
    fun nextStartsAtFirstTrackWhenSelectionIsOutsidePlaylist() {
        val selection = PlaybackSelection("No audio", "No subtitle")

        val result = PlaybackNavigation.relative(selection, playlist, 1)

        assertEquals(PlaybackNavigation.Result.Status.READY, result.status)
        assertEquals(0, result.targetIndex)
    }

    @Test
    fun previousBeforeKnownTrackStopsAtStartBoundary() {
        val selection = PlaybackSelection("No audio", "No subtitle")
        selection.moveToPlaylist("playlist-1", 0)

        val result = PlaybackNavigation.relative(selection, playlist, -1)

        assertEquals(PlaybackNavigation.Result.Status.BEFORE_START, result.status)
        assertEquals(-1, result.targetIndex)
    }

    @Test
    fun nextAfterLastTrackStopsAtEndBoundary() {
        val selection = PlaybackSelection("No audio", "No subtitle")
        selection.moveToPlaylist("playlist-1", 1)

        val result = PlaybackNavigation.relative(selection, playlist, 1)

        assertEquals(PlaybackNavigation.Result.Status.AFTER_END, result.status)
        assertEquals(-1, result.targetIndex)
    }

    @Test
    fun nextAndPreviousReturnTargetIndexesWithinPlaylist() {
        val selection = PlaybackSelection("No audio", "No subtitle")
        selection.moveToPlaylist("playlist-1", 0)

        val next = PlaybackNavigation.relative(selection, playlist, 1)
        assertEquals(PlaybackNavigation.Result.Status.READY, next.status)
        assertEquals(1, next.targetIndex)

        selection.moveToPlaylist("playlist-1", 1)
        val previous = PlaybackNavigation.relative(selection, playlist, -1)
        assertEquals(PlaybackNavigation.Result.Status.READY, previous.status)
        assertEquals(0, previous.targetIndex)
    }
}
