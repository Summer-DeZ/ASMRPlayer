package io.github.summerdez.asmrplayer.domain

import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem
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
