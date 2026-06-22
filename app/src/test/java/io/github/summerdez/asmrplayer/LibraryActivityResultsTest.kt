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

class LibraryActivityResultsTest {
    @Test
    fun successfulAudioResultImportsEvenWithoutPrimaryUri() {
        val action = LibraryActivityResults.resolve(
                LibraryActivityResults.REQUEST_PLAYLIST_AUDIO,
                true,
                true,
                false)

        assertEquals(LibraryActivityResults.Action.IMPORT_PLAYLIST_AUDIO, action)
    }

    @Test
    fun successfulFolderResultWithUriImportsFolder() {
        val action = LibraryActivityResults.resolve(
                LibraryActivityResults.REQUEST_PLAYLIST_FOLDER,
                true,
                true,
                true)

        assertEquals(LibraryActivityResults.Action.IMPORT_PLAYLIST_FOLDER, action)
    }

    @Test
    fun missingFolderUriIsIgnored() {
        val action = LibraryActivityResults.resolve(
                LibraryActivityResults.REQUEST_PLAYLIST_FOLDER,
                true,
                true,
                false)

        assertEquals(LibraryActivityResults.Action.IGNORE, action)
    }

    @Test
    fun successfulSubtitleResultWithUriImportsSubtitle() {
        val action = LibraryActivityResults.resolve(
                LibraryActivityResults.REQUEST_TRACK_SUBTITLE,
                true,
                true,
                true)

        assertEquals(LibraryActivityResults.Action.IMPORT_TRACK_SUBTITLE, action)
    }

    @Test
    fun successfulCoverResultWithUriImportsCover() {
        val action = LibraryActivityResults.resolve(
                LibraryActivityResults.REQUEST_PLAYLIST_COVER,
                true,
                true,
                true)

        assertEquals(LibraryActivityResults.Action.IMPORT_PLAYLIST_COVER, action)
    }

    @Test
    fun cancelledSubtitleResultClearsPendingSubtitlePicker() {
        val action = LibraryActivityResults.resolve(
                LibraryActivityResults.REQUEST_TRACK_SUBTITLE,
                false,
                false,
                false)

        assertEquals(LibraryActivityResults.Action.CLEAR_TRACK_SUBTITLE_PICKER, action)
    }

    @Test
    fun missingSubtitleUriClearsPendingSubtitlePicker() {
        val action = LibraryActivityResults.resolve(
                LibraryActivityResults.REQUEST_TRACK_SUBTITLE,
                true,
                true,
                false)

        assertEquals(LibraryActivityResults.Action.CLEAR_TRACK_SUBTITLE_PICKER, action)
    }

    @Test
    fun cancelledCoverResultClearsPendingCoverPicker() {
        val action = LibraryActivityResults.resolve(
                LibraryActivityResults.REQUEST_PLAYLIST_COVER,
                false,
                false,
                false)

        assertEquals(LibraryActivityResults.Action.CLEAR_PLAYLIST_COVER_PICKER, action)
    }

    @Test
    fun missingCoverUriClearsPendingCoverPicker() {
        val action = LibraryActivityResults.resolve(
                LibraryActivityResults.REQUEST_PLAYLIST_COVER,
                true,
                true,
                false)

        assertEquals(LibraryActivityResults.Action.CLEAR_PLAYLIST_COVER_PICKER, action)
    }

    @Test
    fun unknownResultIsIgnored() {
        val action = LibraryActivityResults.resolve(999, true, true, true)

        assertEquals(LibraryActivityResults.Action.IGNORE, action)
    }
}
