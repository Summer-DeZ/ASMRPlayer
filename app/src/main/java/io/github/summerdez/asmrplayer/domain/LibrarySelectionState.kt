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
class LibrarySelectionState {
    private var selectedPlaylist: Playlist? = null
    private var collapsedSelectedPlaylistId: String = ""
    private var animatingExpandPlaylistId: String = ""
    private var animatingCollapsePlaylistId: String = ""

    fun sync(playlists: List<Playlist>?, storedSelectedPlaylistId: String?) {
        selectedPlaylist = PlaylistQueries.findById(playlists, storedSelectedPlaylistId)
        if (selectedPlaylist == null && !playlists.isNullOrEmpty()) {
            selectedPlaylist = playlists[0]
        }
        if (selectedPlaylist == null || selectedPlaylist?.id != collapsedSelectedPlaylistId) {
            collapsedSelectedPlaylistId = ""
        }
    }

    fun handlePlaylistClick(playlist: Playlist?) {
        if (playlist == null) {
            return
        }
        val previousPlaylistId = selectedPlaylistId()
        val wasExpanded = playlist.id == previousPlaylistId && playlist.id != collapsedSelectedPlaylistId
        if (playlist.id == previousPlaylistId) {
            if (wasExpanded) {
                animatingExpandPlaylistId = ""
                animatingCollapsePlaylistId = playlist.id
                collapsedSelectedPlaylistId = playlist.id
            } else {
                animatingExpandPlaylistId = playlist.id
                animatingCollapsePlaylistId = ""
                collapsedSelectedPlaylistId = ""
            }
            return
        }

        animatingExpandPlaylistId = playlist.id
        animatingCollapsePlaylistId = if (previousPlaylistId.isEmpty() ||
            previousPlaylistId == collapsedSelectedPlaylistId
        ) {
            ""
        } else {
            previousPlaylistId
        }
        collapsedSelectedPlaylistId = ""
        selectedPlaylist = playlist
    }

    fun selectPlaylist(playlist: Playlist?) {
        selectedPlaylist = playlist
        collapsedSelectedPlaylistId = ""
        animatingExpandPlaylistId = ""
        animatingCollapsePlaylistId = ""
    }

    fun clearPlaylistAnimationState(playlistId: String, expanded: Boolean) {
        if (expanded && playlistId == animatingExpandPlaylistId) {
            animatingExpandPlaylistId = ""
        } else if (!expanded && playlistId == animatingCollapsePlaylistId) {
            animatingCollapsePlaylistId = ""
        }
    }

    fun selectedPlaylist(): Playlist? = selectedPlaylist

    fun selectedPlaylistId(): String = selectedPlaylist?.id.orEmpty()

    fun collapsedSelectedPlaylistId(): String = collapsedSelectedPlaylistId

    fun animatingExpandPlaylistId(): String = animatingExpandPlaylistId

    fun animatingCollapsePlaylistId(): String = animatingCollapsePlaylistId
}
