package io.github.summerdez.asmrplayer.domain

import android.net.Uri
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem

class PlaybackSelection(
    private val defaultAudioTitle: String,
    private val defaultSubtitleTitle: String,
) {
    private var audioUri: String = ""
    private var subtitleUri: String = ""
    private var audioTitle: String = defaultAudioTitle
    private var subtitleTitle: String = defaultSubtitleTitle
    private var playlistId: String = ""
    private var playlistIndex: Int = -1

    fun audioUri(): Uri? = audioUri.takeIf { it.isNotEmpty() }?.let(Uri::parse)

    fun audioUriString(): String = audioUri

    fun subtitleUri(): Uri? = subtitleUri.takeIf { it.isNotEmpty() }?.let(Uri::parse)

    fun audioTitle(): String = audioTitle

    fun subtitleTitle(): String = subtitleTitle

    fun playlistId(): String = playlistId

    fun playlistIndex(): Int = playlistIndex

    fun hasAudio(): Boolean = audioUri.isNotEmpty()

    fun matchesPlaylistPosition(otherPlaylistId: String?, otherPlaylistIndex: Int): Boolean {
        return playlistId == otherPlaylistId.orEmpty() && playlistIndex == otherPlaylistIndex
    }

    fun selectTrack(playlist: Playlist, index: Int, track: TrackItem) {
        audioUri = track.uri
        subtitleUri = track.subtitleUri
        audioTitle = track.title
        subtitleTitle = track.subtitleTitleOr(defaultSubtitleTitle)
        playlistId = playlist.id
        playlistIndex = index
    }

    fun updateSubtitle(uri: Uri?, title: String?) {
        subtitleUri = uri?.toString().orEmpty()
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
