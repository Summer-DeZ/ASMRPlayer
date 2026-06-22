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
