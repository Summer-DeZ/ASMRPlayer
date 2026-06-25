package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.domain.PendingLibrarySelection

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingLibrarySelectionTest {
    @Test
    fun coverPickerTargetIsConsumedOnce() {
        val pending = PendingLibrarySelection()

        pending.startCoverPicker("playlist-1")

        assertEquals("playlist-1", pending.consumeCoverPlaylistId())
        assertEquals("", pending.consumeCoverPlaylistId())
    }

    @Test
    fun subtitlePickerTargetIsConsumedOnce() {
        val pending = PendingLibrarySelection()

        pending.startSubtitlePicker("playlist-1", "track-1")

        val target = pending.consumeSubtitleTarget()
        assertEquals("playlist-1", target.playlistId)
        assertEquals("track-1", target.trackId)

        val emptyTarget = pending.consumeSubtitleTarget()
        assertEquals("", emptyTarget.playlistId)
        assertEquals("", emptyTarget.trackId)
    }

    @Test
    fun nullTargetsAreNormalizedToEmptyStrings() {
        val pending = PendingLibrarySelection()

        pending.startCoverPicker(null)
        pending.startSubtitlePicker(null, null)

        assertEquals("", pending.consumeCoverPlaylistId())
        val target = pending.consumeSubtitleTarget()
        assertEquals("", target.playlistId)
        assertEquals("", target.trackId)
    }
}
