package io.github.summerdez.asmrplayer.domain

import io.github.summerdez.asmrplayer.R
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
class PendingLibrarySelection {
    private var coverPlaylistId: String = ""
    private var subtitlePlaylistId: String = ""
    private var subtitleTrackId: String = ""

    fun startCoverPicker(playlistId: String?) {
        coverPlaylistId = playlistId.orEmpty()
    }

    fun consumeCoverPlaylistId(): String {
        val playlistId = coverPlaylistId
        clearCoverPicker()
        return playlistId
    }

    fun clearCoverPicker() {
        coverPlaylistId = ""
    }

    fun startSubtitlePicker(playlistId: String?, trackId: String?) {
        subtitlePlaylistId = playlistId.orEmpty()
        subtitleTrackId = trackId.orEmpty()
    }

    fun consumeSubtitleTarget(): TrackSubtitleTarget {
        val target = TrackSubtitleTarget(subtitlePlaylistId, subtitleTrackId)
        clearSubtitlePicker()
        return target
    }

    fun clearSubtitlePicker() {
        subtitlePlaylistId = ""
        subtitleTrackId = ""
    }

    data class TrackSubtitleTarget(
        @JvmField val playlistId: String,
        @JvmField val trackId: String,
    )
}
