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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PlaylistQueriesTest {
    private val firstTrack = TrackItem("track-1", "First", "content://first")
    private val secondTrack = TrackItem("track-2", "Second", "content://second")
    private val playlist = Playlist("playlist-1", "Playlist", listOf(firstTrack, secondTrack))

    @Test
    fun findByIdReturnsMatchingPlaylist() {
        val playlists = listOf(Playlist("other", "Other"), playlist)

        assertSame(playlist, PlaylistQueries.findById(playlists, "playlist-1"))
        assertNull(PlaylistQueries.findById(playlists, "missing"))
        assertNull(PlaylistQueries.findById(playlists, ""))
    }

    @Test
    fun indexOfTrackReturnsTrackPosition() {
        assertEquals(0, PlaylistQueries.indexOfTrack(playlist, "track-1"))
        assertEquals(1, PlaylistQueries.indexOfTrack(playlist, "track-2"))
        assertEquals(-1, PlaylistQueries.indexOfTrack(playlist, "missing"))
        assertEquals(-1, PlaylistQueries.indexOfTrack(null, "track-1"))
    }

    @Test
    fun trackAtGuardsOutOfRangeIndexes() {
        assertSame(firstTrack, PlaylistQueries.trackAt(playlist, 0))
        assertSame(secondTrack, PlaylistQueries.trackAt(playlist, 1))
        assertNull(PlaylistQueries.trackAt(playlist, -1))
        assertNull(PlaylistQueries.trackAt(playlist, 2))
        assertNull(PlaylistQueries.trackAt(null, 0))
    }
}
