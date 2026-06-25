package io.github.summerdez.asmrplayer.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.github.summerdez.asmrplayer.domain.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class PlaybackTrackSynchronizer(
    private val player: Player,
    private val scope: CoroutineScope,
    private val loadPlaylist: suspend (String) -> Playlist?,
    private val applyPlaylistTrack: (PlaybackTrackSnapshot) -> Unit,
    private val applyFallback: (PlaybackTrackMetadataSnapshot) -> Unit,
    private val publishState: () -> Unit,
) {
    private var syncJob: Job? = null

    fun sync(mediaItem: MediaItem?) {
        val identity = PlaybackMediaQueue.parseMediaId(mediaItem?.mediaId)
        if (identity != null) {
            val mediaId = mediaItem?.mediaId.orEmpty()
            syncJob?.cancel()
            syncJob = scope.launch {
                val playlist = loadPlaylist(identity.playlistId)
                if (player.currentMediaItem?.mediaId != mediaId) {
                    return@launch
                }
                val snapshot = playlist.toPlaybackTrackSnapshot(identity)
                if (snapshot != null) {
                    applyPlaylistTrack(snapshot)
                    publishState()
                    return@launch
                }
                applyFallback(mediaItem.toPlaybackTrackMetadataSnapshot())
                publishState()
            }
            return
        }
        applyFallback(mediaItem.toPlaybackTrackMetadataSnapshot())
    }
}

internal data class PlaybackTrackSnapshot(
    val playlistId: String,
    val playlistIndex: Int,
    val audioUri: Uri,
    val subtitleUri: Uri?,
    val title: String,
)

internal data class PlaybackTrackMetadataSnapshot(
    val audioUri: Uri?,
    val title: String?,
)

private fun Playlist?.toPlaybackTrackSnapshot(identity: PlaybackMediaQueue.PlaylistIdentity): PlaybackTrackSnapshot? {
    val track = this?.tracks?.getOrNull(identity.index) ?: return null
    return PlaybackTrackSnapshot(
        playlistId = identity.playlistId,
        playlistIndex = identity.index,
        audioUri = track.audioUri(),
        subtitleUri = track.subtitleUriOrNull(),
        title = track.title,
    )
}

private fun MediaItem?.toPlaybackTrackMetadataSnapshot(): PlaybackTrackMetadataSnapshot {
    return PlaybackTrackMetadataSnapshot(
        audioUri = this?.localConfiguration?.uri,
        title = this?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() },
    )
}
