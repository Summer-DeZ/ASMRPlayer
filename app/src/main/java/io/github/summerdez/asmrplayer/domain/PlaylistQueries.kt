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
object PlaylistQueries {
    @JvmStatic
    fun findById(playlists: List<Playlist>?, playlistId: String?): Playlist? {
        if (playlists.isNullOrEmpty() || playlistId.isNullOrEmpty()) {
            return null
        }
        return playlists.firstOrNull { it.id == playlistId }
    }

    @JvmStatic
    fun indexOfTrack(playlist: Playlist?, trackId: String?): Int {
        if (playlist == null || trackId.isNullOrEmpty()) {
            return -1
        }
        return playlist.tracks.indexOfFirst { it.id == trackId }
    }

    @JvmStatic
    fun trackAt(playlist: Playlist?, index: Int): TrackItem? {
        if (playlist == null || index < 0 || index >= playlist.tracks.size) {
            return null
        }
        return playlist.tracks[index]
    }
}
