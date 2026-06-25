package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.domain.LibrarySelectionState
import io.github.summerdez.asmrplayer.domain.model.Playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test

class LibrarySelectionStateTest {
    private val first = Playlist("playlist-1", "First")
    private val second = Playlist("playlist-2", "Second")

    @Test
    fun syncUsesStoredSelectionWhenPresent() {
        val state = LibrarySelectionState()

        state.sync(listOf(first, second), "playlist-2")

        assertSame(second, state.selectedPlaylist())
        assertEquals("playlist-2", state.selectedPlaylistId())
    }

    @Test
    fun syncFallsBackToFirstPlaylistWhenStoredSelectionIsMissing() {
        val state = LibrarySelectionState()

        state.sync(listOf(first, second), "missing")

        assertSame(first, state.selectedPlaylist())
        assertEquals("playlist-1", state.selectedPlaylistId())
    }

    @Test
    fun syncClearsSelectionWhenNoPlaylistsExist() {
        val state = LibrarySelectionState()
        state.sync(listOf(first), "playlist-1")

        state.sync(emptyList(), "playlist-1")

        assertNull(state.selectedPlaylist())
        assertEquals("", state.selectedPlaylistId())
        assertEquals("", state.collapsedSelectedPlaylistId())
    }

    @Test
    fun clickingSelectedPlaylistCollapsesThenExpandsIt() {
        val state = LibrarySelectionState()
        state.sync(listOf(first), "playlist-1")

        state.handlePlaylistClick(first)

        assertEquals("playlist-1", state.collapsedSelectedPlaylistId())
        assertEquals("", state.animatingExpandPlaylistId())
        assertEquals("playlist-1", state.animatingCollapsePlaylistId())

        state.handlePlaylistClick(first)

        assertEquals("", state.collapsedSelectedPlaylistId())
        assertEquals("playlist-1", state.animatingExpandPlaylistId())
        assertEquals("", state.animatingCollapsePlaylistId())
    }

    @Test
    fun clickingDifferentPlaylistAnimatesPreviousCollapseWhenPreviousWasExpanded() {
        val state = LibrarySelectionState()
        state.sync(listOf(first, second), "playlist-1")

        state.handlePlaylistClick(second)

        assertSame(second, state.selectedPlaylist())
        assertEquals("", state.collapsedSelectedPlaylistId())
        assertEquals("playlist-2", state.animatingExpandPlaylistId())
        assertEquals("playlist-1", state.animatingCollapsePlaylistId())
    }

    @Test
    fun clickingDifferentPlaylistDoesNotCollapseAlreadyCollapsedPreviousPlaylist() {
        val state = LibrarySelectionState()
        state.sync(listOf(first, second), "playlist-1")
        state.handlePlaylistClick(first)

        state.handlePlaylistClick(second)

        assertSame(second, state.selectedPlaylist())
        assertEquals("", state.collapsedSelectedPlaylistId())
        assertEquals("playlist-2", state.animatingExpandPlaylistId())
        assertEquals("", state.animatingCollapsePlaylistId())
    }

    @Test
    fun clearingAnimationOnlyClearsMatchingAnimationSlot() {
        val state = LibrarySelectionState()
        state.sync(listOf(first, second), "playlist-1")
        state.handlePlaylistClick(second)

        state.clearPlaylistAnimationState("playlist-1", false)
        state.clearPlaylistAnimationState("playlist-2", true)

        assertEquals("", state.animatingExpandPlaylistId())
        assertEquals("", state.animatingCollapsePlaylistId())
    }
}
