package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import io.github.summerdez.asmrplayer.playback.PlaybackControllerSnapshot
import io.github.summerdez.asmrplayer.playback.PlaybackServiceSnapshot
import io.github.summerdez.asmrplayer.presentation.PlaybackPresentationState
import io.github.summerdez.asmrplayer.presentation.PlaybackTrackNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPresentationStateTest {
    private val firstTrack = TrackItem(
        "track-1",
        "First",
        "content://first",
        "content://first-subtitle",
        "first.vtt",
    )
    private val secondTrack = TrackItem(
        "track-2",
        "Second",
        "content://second",
        "content://second-subtitle",
        "second.vtt",
    )
    private val playlist = Playlist(
        "playlist-1",
        "Playlist",
        "content://cover",
        listOf(firstTrack, secondTrack),
    )

    @Test
    fun disconnectedControllerIgnoresServiceSnapshotExtras() {
        val presentationState = PlaybackPresentationState()

        val state = presentationState.updateController(
            PlaybackControllerSnapshot(
                connected = false,
                isPlaying = true,
                durationMs = 1200,
                positionMs = 300,
                serviceSnapshot = PlaybackServiceSnapshot(
                    connected = true,
                    playlistId = "playlist-1",
                    playlistIndex = 0,
                    audioUri = "content://first",
                    subtitleLines = listOf("line"),
                    subtitleIndex = 0,
                    subtitleCount = 1,
                    overlayRequested = true,
                ),
            ),
        )

        assertFalse(state.isPlaying)
        assertEquals(0, state.durationMs)
        assertEquals(0, state.positionMs)
        assertEquals(emptyList<String>(), state.subtitleLines)
        assertEquals(-1, state.subtitleIndex)
        assertEquals("未载入字幕", state.subtitleEmptyText)
        assertFalse(state.overlayRequested)
        assertEquals("", state.playlistId)
        assertEquals(-1, state.playlistIndex)
    }

    @Test
    fun connectedControllerProjectsPlaybackAndServiceExtras() {
        val presentationState = PlaybackPresentationState()

        val state = presentationState.updateController(
            PlaybackControllerSnapshot(
                connected = true,
                isPlaying = true,
                durationMs = 5000,
                positionMs = 1200,
                serviceSnapshot = PlaybackServiceSnapshot(
                    connected = true,
                    playlistId = "playlist-1",
                    playlistIndex = 1,
                    audioUri = "content://second",
                    subtitleLines = listOf("first line", "second line"),
                    subtitleIndex = 1,
                    subtitleCount = 2,
                    overlayRequested = true,
                ),
            ),
        )

        assertEquals("未选择音频", state.audioTitle)
        assertEquals("未选择字幕", state.subtitleTitle)
        assertEquals("未选择字幕", state.contextTitle)
        assertEquals("", state.coverUri)
        assertEquals("playlist-1", state.playlistId)
        assertEquals(1, state.playlistIndex)
        assertTrue(state.isPlaying)
        assertEquals(5000, state.durationMs)
        assertEquals(1200, state.positionMs)
        assertEquals(listOf("first line", "second line"), state.subtitleLines)
        assertEquals(1, state.subtitleIndex)
        assertEquals("等待字幕", state.subtitleEmptyText)
        assertTrue(state.overlayRequested)
    }

    @Test
    fun serviceSnapshotIdentityUsesLibraryContextWhenLibraryArrives() {
        val presentationState = PlaybackPresentationState()
        presentationState.updateController(
            PlaybackControllerSnapshot(
                connected = true,
                serviceSnapshot = PlaybackServiceSnapshot(
                    connected = true,
                    playlistId = "playlist-1",
                    playlistIndex = 1,
                ),
            ),
        )

        val state = presentationState.updateLibrary(listOf(playlist), "playlist-1")

        assertEquals("Playlist", state.contextTitle)
        assertEquals("content://cover", state.coverUri)
        assertEquals("playlist-1", state.playlistId)
        assertEquals(1, state.playlistIndex)
        assertEquals("Second", state.audioTitle)
        assertEquals("second.vtt", state.subtitleTitle)
        assertEquals("track-2", presentationState.currentTrackId())
    }

    @Test
    fun emptySelectedPlaylistDoesNotCreatePlaybackRequest() {
        val presentationState = PlaybackPresentationState()
        val emptyPlaylist = Playlist("empty", "Empty", emptyList())
        presentationState.updateLibrary(listOf(emptyPlaylist), "empty")

        assertNull(presentationState.selectFirstTrackFromSelectedPlaylist())
        assertSame(PlaybackTrackNavigation.Empty, presentationState.selectRelativeTrack(1))
    }

    @Test
    fun selectionWithoutAudioNeverTogglesCurrentMedia() {
        val presentationState = PlaybackPresentationState()
        presentationState.updateController(
            PlaybackControllerSnapshot(
                connected = true,
                isPlaying = true,
                durationMs = 5000,
                serviceSnapshot = PlaybackServiceSnapshot(
                    connected = true,
                    audioUri = "content://other",
                ),
            ),
        )
        assertFalse(presentationState.shouldToggleCurrentSelection())
        assertTrue(presentationState.currentIsPlaying())
    }
}
