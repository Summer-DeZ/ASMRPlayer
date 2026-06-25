package io.github.summerdez.asmrplayer.playback

import android.net.Uri
import androidx.media3.common.Player
import io.github.summerdez.asmrplayer.domain.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class PlaybackPlaylistLoadGate {
    private var loadJob: Job? = null
    private var loadVersion: Long = 0L

    fun nextRequest(): Long {
        loadJob?.cancel()
        loadVersion += 1L
        return loadVersion
    }

    fun setJob(job: Job) {
        loadJob = job
    }

    fun isLatest(requestVersion: Long): Boolean {
        return requestVersion == loadVersion
    }
}

internal class PlaybackPlaylistNavigator(
    private val player: Player,
    private val scope: CoroutineScope,
    private val loadGate: PlaybackPlaylistLoadGate,
    private val loadPlaylist: suspend (String) -> Playlist?,
    private val playMedia: (Uri?, Uri?, String?, String?, Int) -> Unit,
) {
    fun playRelative(delta: Int, currentPlaylistId: String, currentPlaylistIndex: Int) {
        if (delta > 0 && player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.play()
            return
        }
        if (delta < 0 && player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            player.play()
            return
        }
        if (currentPlaylistId.isEmpty() || currentPlaylistIndex < 0) {
            return
        }

        val requestVersion = loadGate.nextRequest()
        val requestedPlaylistId = currentPlaylistId
        val requestedPlaylistIndex = currentPlaylistIndex
        loadGate.setJob(
            scope.launch {
                val playlist = loadPlaylist(requestedPlaylistId) ?: return@launch
                if (!loadGate.isLatest(requestVersion)) {
                    return@launch
                }
                val targetIndex = requestedPlaylistIndex + delta
                val track = playlist.tracks.getOrNull(targetIndex) ?: return@launch
                if (!track.hasAudioUri()) {
                    return@launch
                }
                playMedia(track.audioUri(), track.subtitleUriOrNull(), track.title, playlist.id, targetIndex)
            },
        )
    }
}
