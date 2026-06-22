package io.github.summerdez.asmrplayer.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.summerdez.asmrplayer.domain.model.Playlist

internal object PlaybackMediaQueue {
    fun buildPlaylistQueue(playlist: Playlist): List<MediaItem> {
        return playlist.tracks.mapIndexedNotNull { index, track ->
            if (!track.hasAudioUri()) {
                null
            } else {
                buildMediaItem(track.audioUri(), track.title, playlist.id, index, playlist.coverUri)
            }
        }
    }

    fun buildMediaItem(
        audioUri: Uri,
        title: String,
        playlistId: String,
        playlistIndex: Int,
        coverUri: String?,
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .apply {
                if (!coverUri.isNullOrEmpty()) {
                    setArtworkUri(Uri.parse(coverUri))
                }
            }
            .build()
        return MediaItem.Builder()
            .setUri(audioUri)
            .setMediaId(mediaId(playlistId, playlistIndex, audioUri.toString()))
            .setMediaMetadata(metadata)
            .build()
    }

    fun mediaId(playlistId: String, index: Int, fallback: String = ""): String {
        return if (playlistId.isEmpty() || index < 0) {
            fallback
        } else {
            "$playlistId#$index"
        }
    }

    fun parseMediaId(mediaId: String?): QueueIdentity? {
        if (mediaId.isNullOrEmpty()) {
            return null
        }
        val separator = mediaId.lastIndexOf('#')
        if (separator <= 0 || separator == mediaId.lastIndex) {
            return null
        }
        val index = mediaId.substring(separator + 1).toIntOrNull() ?: return null
        return QueueIdentity(mediaId.substring(0, separator), index)
    }

    data class QueueIdentity(val playlistId: String, val index: Int)
}
