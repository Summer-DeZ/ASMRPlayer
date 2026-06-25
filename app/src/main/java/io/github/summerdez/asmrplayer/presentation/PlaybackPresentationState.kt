package io.github.summerdez.asmrplayer.presentation

import android.net.Uri
import io.github.summerdez.asmrplayer.domain.PlaybackNavigation
import io.github.summerdez.asmrplayer.domain.PlaybackSelection
import io.github.summerdez.asmrplayer.domain.PlaylistQueries
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.playback.PlaybackControllerSnapshot
import io.github.summerdez.asmrplayer.playback.PlaybackServiceSnapshot
import io.github.summerdez.asmrplayer.playback.activeServiceSnapshotOrNull

data class PlaybackUiState(
    val audioTitle: String = "未选择音频",
    val subtitleTitle: String = "未选择字幕",
    val contextTitle: String = "未选择字幕",
    val coverUri: String = "",
    val playlistId: String = "",
    val playlistIndex: Int = -1,
    val isPlaying: Boolean = false,
    val durationMs: Int = 0,
    val positionMs: Int = 0,
    val subtitleLines: List<String> = emptyList(),
    val subtitleIndex: Int = -1,
    val subtitleEmptyText: String = "未载入字幕",
    val overlayRequested: Boolean = false,
)

internal data class PlaybackStartRequest(
    val audioUri: Uri,
    val title: String,
    val subtitleUri: Uri?,
    val playlistId: String,
    val playlistIndex: Int,
)

internal sealed interface PlaybackTrackNavigation {
    data class Ready(val request: PlaybackStartRequest) : PlaybackTrackNavigation
    data object Empty : PlaybackTrackNavigation
    data object BeforeStart : PlaybackTrackNavigation
    data object AfterEnd : PlaybackTrackNavigation
}

internal class PlaybackPresentationState(
    defaultAudioTitle: String = DEFAULT_AUDIO_TITLE,
    defaultSubtitleTitle: String = DEFAULT_SUBTITLE_TITLE,
) {
    private val playbackSelection = PlaybackSelection(defaultAudioTitle, defaultSubtitleTitle)
    private var lastPlaylists: List<Playlist> = emptyList()
    private var lastSelectedPlaylist: Playlist? = null
    private var controllerSnapshot = PlaybackControllerSnapshot()

    fun updateLibrary(playlists: List<Playlist>, selectedPlaylistId: String): PlaybackUiState {
        lastPlaylists = playlists
        lastSelectedPlaylist = PlaylistQueries.findById(playlists, selectedPlaylistId) ?: playlists.firstOrNull()
        syncPlaylistStateFromSnapshot(activeServiceSnapshot())
        return uiState()
    }

    fun updateController(snapshot: PlaybackControllerSnapshot): PlaybackUiState {
        controllerSnapshot = snapshot
        syncPlaylistStateFromSnapshot(activeServiceSnapshot())
        return uiState()
    }

    fun uiState(): PlaybackUiState {
        val serviceSnapshot = activeServiceSnapshot()
        val connected = serviceSnapshot.connected || controllerSnapshot.connected
        return PlaybackUiState(
            audioTitle = playbackSelection.audioTitle(),
            subtitleTitle = playbackSelection.subtitleTitle(),
            contextTitle = playbackContextTitle(),
            coverUri = playbackContextCoverUri(),
            playlistId = playbackSelection.playlistId(),
            playlistIndex = playbackSelection.playlistIndex(),
            isPlaying = connected && currentIsPlaying(),
            durationMs = if (connected) currentDurationMs() else 0,
            positionMs = if (connected) currentPositionMs() else 0,
            subtitleLines = if (connected) serviceSnapshot.subtitleLines else emptyList(),
            subtitleIndex = if (connected) serviceSnapshot.subtitleIndex else -1,
            subtitleEmptyText = if (serviceSnapshot.subtitleCount > 0) "等待字幕" else "未载入字幕",
            overlayRequested = connected && serviceSnapshot.overlayRequested,
        )
    }

    fun hasAudio(): Boolean = playbackSelection.hasAudio()

    fun shouldToggleCurrentSelection(): Boolean {
        val audioUri = playbackSelection.audioUriString()
        if (audioUri.isEmpty()) {
            return false
        }
        val playbackDurationMs = currentDurationMs()
        val serviceSnapshot = activeServiceSnapshot()
        return (serviceSnapshot.connected || controllerSnapshot.connected) &&
            playbackDurationMs > 0 &&
            serviceSnapshot.audioUri == audioUri
    }

    fun currentIsPlaying(): Boolean {
        return controllerSnapshot.connected && controllerSnapshot.isPlaying
    }

    fun currentPlaybackRequest(): PlaybackStartRequest? {
        val audioUri = playbackSelection.audioUri() ?: return null
        return PlaybackStartRequest(
            audioUri = audioUri,
            title = playbackSelection.audioTitle(),
            subtitleUri = playbackSelection.subtitleUri(),
            playlistId = playbackSelection.playlistId(),
            playlistIndex = playbackSelection.playlistIndex(),
        )
    }

    fun selectFirstTrackFromSelectedPlaylist(): PlaybackStartRequest? {
        val playlist = lastSelectedPlaylist
        if (playlist == null || playlist.tracks.isEmpty()) {
            return null
        }
        return selectPlaylistTrack(playlist, 0)
    }

    fun selectPlaylistTrack(playlist: Playlist?, index: Int): PlaybackStartRequest? {
        if (playlist == null || index < 0 || index >= playlist.tracks.size) {
            return null
        }
        val track = playlist.tracks[index]
        playbackSelection.selectTrack(playlist, index, track)
        return currentPlaybackRequest()
    }

    fun selectRelativeTrack(delta: Int): PlaybackTrackNavigation {
        val playlist = activePlaylistForNavigation()
        val result = PlaybackNavigation.relative(playbackSelection, playlist, delta)
        return when (result.status) {
            PlaybackNavigation.Result.Status.EMPTY -> PlaybackTrackNavigation.Empty
            PlaybackNavigation.Result.Status.BEFORE_START -> PlaybackTrackNavigation.BeforeStart
            PlaybackNavigation.Result.Status.AFTER_END -> PlaybackTrackNavigation.AfterEnd
            PlaybackNavigation.Result.Status.READY -> {
                val request = selectPlaylistTrack(playlist, result.targetIndex)
                if (request == null) {
                    PlaybackTrackNavigation.Empty
                } else {
                    PlaybackTrackNavigation.Ready(request)
                }
            }
        }
    }

    fun updateSubtitleForCurrentTrack(uri: Uri, title: String, trackId: String): Boolean {
        if (trackId != currentTrackId()) {
            return false
        }
        playbackSelection.updateSubtitle(uri, title)
        return true
    }

    fun onPlaylistDeleted(playlistId: String) {
        playbackSelection.onPlaylistDeleted(playlistId)
    }

    fun onTrackRemoved(playlistId: String, index: Int) {
        playbackSelection.onTrackRemoved(playlistId, index)
    }

    fun renameCurrentTrack(trackId: String, title: String): Boolean {
        if (trackId != currentTrackId()) {
            return false
        }
        playbackSelection.updateAudioTitle(title)
        return true
    }

    fun onTrackMoved(result: MoveTrackResult) {
        val currentTrackId = currentTrackId()
        playbackSelection.onTrackMoved(
            result.sourcePlaylistId,
            result.sourceIndex,
            result.targetPlaylistId,
            result.targetIndex,
            result.trackId,
            currentTrackId,
        )
    }

    fun currentTrackId(): String {
        return playbackSelection.currentTrackId(lastPlaylists)
    }

    private fun syncPlaylistStateFromSnapshot(snapshot: PlaybackServiceSnapshot) {
        if (!snapshot.connected || snapshot.playlistId.isEmpty() || snapshot.playlistIndex < 0) {
            return
        }
        val servicePlaylist = PlaylistQueries.findById(lastPlaylists, snapshot.playlistId)
        val serviceTrack = PlaylistQueries.trackAt(servicePlaylist, snapshot.playlistIndex)
        if (playbackSelection.matchesPlaylistPosition(snapshot.playlistId, snapshot.playlistIndex)) {
            if (!playbackSelection.hasAudio() && servicePlaylist != null && serviceTrack != null) {
                playbackSelection.selectTrack(servicePlaylist, snapshot.playlistIndex, serviceTrack)
            }
            return
        }
        playbackSelection.moveToPlaylist(snapshot.playlistId, snapshot.playlistIndex)
        if (servicePlaylist != null && serviceTrack != null) {
            playbackSelection.selectTrack(servicePlaylist, snapshot.playlistIndex, serviceTrack)
        }
    }

    private fun playbackContextTitle(): String {
        val playlist = PlaylistQueries.findById(lastPlaylists, playbackSelection.playlistId())
        return playlist?.name ?: playbackSelection.subtitleTitle()
    }

    private fun playbackContextCoverUri(): String {
        val playlist = PlaylistQueries.findById(lastPlaylists, playbackSelection.playlistId())
        return playlist?.coverUri ?: ""
    }

    private fun activePlaylistForNavigation(): Playlist? {
        return playbackSelection.activePlaylist(lastPlaylists, lastSelectedPlaylist)
    }

    private fun currentDurationMs(): Int {
        return if (controllerSnapshot.connected) controllerSnapshot.durationMs else 0
    }

    private fun currentPositionMs(): Int {
        return if (controllerSnapshot.connected) controllerSnapshot.positionMs else 0
    }

    private fun activeServiceSnapshot(): PlaybackServiceSnapshot {
        return controllerSnapshot.activeServiceSnapshotOrNull() ?: PlaybackServiceSnapshot()
    }

    private companion object {
        const val DEFAULT_AUDIO_TITLE = "未选择音频"
        const val DEFAULT_SUBTITLE_TITLE = "未选择字幕"
    }
}
