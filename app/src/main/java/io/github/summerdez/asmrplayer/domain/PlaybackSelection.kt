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
import android.net.Uri

class PlaybackSelection(
    private val defaultAudioTitle: String,
    private val defaultSubtitleTitle: String,
) {
    private var audioUri: Uri? = null
    private var subtitleUri: Uri? = null
    private var audioTitle: String = defaultAudioTitle
    private var subtitleTitle: String = defaultSubtitleTitle
    private var playlistId: String = ""
    private var playlistIndex: Int = -1

    fun audioUri(): Uri? = audioUri

    fun subtitleUri(): Uri? = subtitleUri

    fun audioTitle(): String = audioTitle

    fun subtitleTitle(): String = subtitleTitle

    fun playlistId(): String = playlistId

    fun playlistIndex(): Int = playlistIndex

    fun hasAudio(): Boolean = audioUri != null

    fun matchesPlaylistPosition(otherPlaylistId: String?, otherPlaylistIndex: Int): Boolean {
        return playlistId == otherPlaylistId.orEmpty() && playlistIndex == otherPlaylistIndex
    }

    fun selectTrack(playlist: Playlist, index: Int, track: TrackItem) {
        audioUri = track.audioUri()
        subtitleUri = track.subtitleUriOrNull()
        audioTitle = track.title
        subtitleTitle = track.subtitleTitleOr(defaultSubtitleTitle)
        playlistId = playlist.id
        playlistIndex = index
    }

    fun updateSubtitle(uri: Uri?, title: String?) {
        subtitleUri = uri
        subtitleTitle = if (title.isNullOrEmpty()) defaultSubtitleTitle else title
    }

    fun updateAudioTitle(title: String?) {
        audioTitle = if (title.isNullOrEmpty()) defaultAudioTitle else title
    }

    fun moveToPlaylist(targetPlaylistId: String?, targetIndex: Int) {
        playlistId = targetPlaylistId.orEmpty()
        playlistIndex = targetIndex
    }

    fun shiftPlaylistIndex(delta: Int) {
        playlistIndex += delta
    }

    fun clearPlaylistPosition() {
        playlistId = ""
        playlistIndex = -1
    }

    fun onPlaylistDeleted(deletedPlaylistId: String?) {
        if (playlistId == deletedPlaylistId.orEmpty()) {
            clearPlaylistPosition()
        }
    }

    fun onTrackRemoved(removedPlaylistId: String?, removedIndex: Int) {
        if (playlistId != removedPlaylistId.orEmpty() || removedIndex < 0) {
            return
        }
        if (removedIndex < playlistIndex) {
            shiftPlaylistIndex(-1)
        } else if (removedIndex == playlistIndex) {
            clearPlaylistPosition()
        }
    }

    fun onTrackMoved(
        sourcePlaylistId: String?,
        sourceIndex: Int,
        targetPlaylistId: String?,
        targetIndex: Int,
        movedTrackId: String?,
        currentTrackId: String?,
    ) {
        if (!movedTrackId.isNullOrEmpty() && movedTrackId == currentTrackId) {
            moveToPlaylist(targetPlaylistId, targetIndex)
        } else if (playlistId == sourcePlaylistId.orEmpty() && sourceIndex >= 0 && sourceIndex < playlistIndex) {
            shiftPlaylistIndex(-1)
        }
    }

    fun currentTrackId(playlists: List<Playlist>?): String {
        val track = PlaylistQueries.trackAt(
            PlaylistQueries.findById(playlists, playlistId),
            playlistIndex,
        )
        return track?.id.orEmpty()
    }

    fun activePlaylist(playlists: List<Playlist>?, selectedPlaylist: Playlist?): Playlist? {
        return PlaylistQueries.findById(playlists, playlistId) ?: selectedPlaylist
    }

    fun baseIndexFor(playlist: Playlist?): Int {
        return if (playlist != null && playlist.id == playlistId) playlistIndex else -1
    }

    fun hasNextIn(playlist: Playlist?): Boolean {
        return playlist != null &&
            playlist.tracks.isNotEmpty() &&
            (playlist.id != playlistId || playlistIndex < playlist.tracks.size - 1)
    }
}
