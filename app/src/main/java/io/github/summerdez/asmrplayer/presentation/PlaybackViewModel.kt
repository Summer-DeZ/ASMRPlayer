package io.github.summerdez.asmrplayer.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.summerdez.asmrplayer.data.LibraryRepository
import io.github.summerdez.asmrplayer.domain.PlaybackNavigation
import io.github.summerdez.asmrplayer.domain.PlaybackSelection
import io.github.summerdez.asmrplayer.domain.PlaylistQueries
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.playback.PlaybackCommandClient
import io.github.summerdez.asmrplayer.playback.PlaybackControllerSnapshot
import io.github.summerdez.asmrplayer.playback.PlaybackServiceSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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

class PlaybackViewModel(
    application: Application,
    private val libraryRepository: LibraryRepository,
    private val playbackCommands: PlaybackCommandClient,
) : AndroidViewModel(application) {
    private val playbackSelection = PlaybackSelection(DEFAULT_AUDIO_TITLE, DEFAULT_SUBTITLE_TITLE)
    private var lastPlaylists: List<Playlist> = emptyList()
    private var lastSelectedPlaylist: Playlist? = null
    private var controllerSnapshot = PlaybackControllerSnapshot()
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state

    init {
        playbackCommands.connect()
        observeLibrary()
        observePlaybackController()
    }

    fun refresh() {
        emitState()
    }

    fun onPlayClicked(): String? {
        if (!playbackSelection.hasAudio()) {
            val playlist = lastSelectedPlaylist
            if (playlist != null && playlist.tracks.isNotEmpty()) {
                playPlaylistTrack(playlist, 0)
                return null
            }
            return "请先选择音频"
        }

        val audioUri = playbackSelection.audioUri()
        val playbackDurationMs = currentDurationMs()
        val activeSnapshot = activeServiceSnapshot()
        if ((activeSnapshot.connected || controllerSnapshot.connected)
            && playbackDurationMs > 0
            && audioUri != null
            && activeSnapshot.audioUri == audioUri.toString()
        ) {
            playbackCommands.togglePlayback(currentIsPlaying())
            return null
        }

        startPlayback(
            playbackSelection.audioUri(),
            playbackSelection.audioTitle(),
            playbackSelection.subtitleUri(),
            playbackSelection.playlistId(),
            playbackSelection.playlistIndex(),
        )
        return null
    }

    fun playPlaylistTrack(playlist: Playlist?, index: Int) {
        if (playlist == null || index < 0 || index >= playlist.tracks.size) {
            return
        }
        val track = playlist.tracks[index]
        playbackSelection.selectTrack(playlist, index, track)
        emitState()
        startPlayback(
            playbackSelection.audioUri(),
            playbackSelection.audioTitle(),
            playbackSelection.subtitleUri(),
            playlist.id,
            index,
        )
    }

    fun playRelativeTrack(delta: Int): String? {
        val playlist = activePlaylistForNavigation()
        val result = PlaybackNavigation.relative(playbackSelection, playlist, delta)
        return when (result.status) {
            PlaybackNavigation.Result.Status.EMPTY -> "当前播放列表为空"
            PlaybackNavigation.Result.Status.BEFORE_START -> "已经是第一首"
            PlaybackNavigation.Result.Status.AFTER_END -> "已经是最后一首"
            PlaybackNavigation.Result.Status.READY -> {
                playPlaylistTrack(playlist, result.targetIndex)
                null
            }
        }
    }

    fun seekTo(positionMs: Int) {
        playbackCommands.seekTo(positionMs)
    }

    fun setSubtitleForCurrentTrack(uri: Uri, title: String, trackId: String) {
        if (trackId == currentTrackId()) {
            playbackSelection.updateSubtitle(uri, title)
            playbackCommands.setSubtitle(uri)
            emitState()
        }
    }

    fun onPlaylistDeleted(playlistId: String) {
        playbackSelection.onPlaylistDeleted(playlistId)
        emitState()
    }

    fun onTrackRemoved(playlistId: String, index: Int) {
        playbackSelection.onTrackRemoved(playlistId, index)
        emitState()
    }

    fun onTrackRenamed(trackId: String, title: String) {
        if (trackId == currentTrackId()) {
            playbackSelection.updateAudioTitle(title)
            emitState()
        }
    }

    fun onTrackMoved(result: MoveTrackResult) {
        playbackSelection.onTrackMoved(
            result.sourcePlaylistId,
            result.sourceIndex,
            result.targetPlaylistId,
            result.targetIndex,
            result.trackId,
            currentTrackId(),
        )
        emitState()
    }

    fun currentTrackId(): String {
        return playbackSelection.currentTrackId(lastPlaylists)
    }

    private fun observeLibrary() {
        viewModelScope.launch {
            combine(
                libraryRepository.playlistsFlow,
                libraryRepository.selectedPlaylistIdFlow,
            ) { playlists, selectedId ->
                playlists to selectedId
            }.collect { (playlists, selectedId) ->
                lastPlaylists = playlists
                lastSelectedPlaylist = PlaylistQueries.findById(playlists, selectedId) ?: playlists.firstOrNull()
                syncPlaylistStateFromSnapshot(activeServiceSnapshot())
                emitState()
            }
        }
    }

    private fun observePlaybackController() {
        viewModelScope.launch {
            playbackCommands.snapshots.collect { snapshot ->
                controllerSnapshot = snapshot
                syncPlaylistStateFromSnapshot(activeServiceSnapshot())
                emitState()
            }
        }
    }

    private fun startPlayback(
        uri: Uri?,
        title: String,
        subtitle: Uri?,
        playlistId: String,
        playlistIndex: Int,
    ) {
        if (uri == null) {
            return
        }
        playbackCommands.playMedia(uri, title, subtitle, playlistId, playlistIndex)
    }

    private fun syncPlaylistStateFromSnapshot(snapshot: PlaybackServiceSnapshot) {
        if (!snapshot.connected || snapshot.playlistId.isEmpty() || snapshot.playlistIndex < 0) {
            return
        }
        if (playbackSelection.matchesPlaylistPosition(snapshot.playlistId, snapshot.playlistIndex)) {
            return
        }
        playbackSelection.moveToPlaylist(snapshot.playlistId, snapshot.playlistIndex)
        val servicePlaylist = PlaylistQueries.findById(lastPlaylists, snapshot.playlistId)
        if (servicePlaylist != null && snapshot.playlistIndex >= 0 && snapshot.playlistIndex < servicePlaylist.tracks.size) {
            playbackSelection.selectTrack(
                servicePlaylist,
                snapshot.playlistIndex,
                servicePlaylist.tracks[snapshot.playlistIndex],
            )
        }
    }

    private fun emitState() {
        val snapshot = activeServiceSnapshot()
        val connected = snapshot.connected || controllerSnapshot.connected
        _state.value = PlaybackUiState(
            audioTitle = playbackSelection.audioTitle(),
            subtitleTitle = playbackSelection.subtitleTitle(),
            contextTitle = playbackContextTitle(),
            coverUri = playbackContextCoverUri(),
            playlistId = playbackSelection.playlistId(),
            playlistIndex = playbackSelection.playlistIndex(),
            isPlaying = connected && currentIsPlaying(),
            durationMs = if (connected) currentDurationMs() else 0,
            positionMs = if (connected) currentPositionMs() else 0,
            subtitleLines = if (connected) snapshot.subtitleLines else emptyList(),
            subtitleIndex = if (connected) snapshot.subtitleIndex else -1,
            subtitleEmptyText = if (snapshot.subtitleCount > 0) "等待字幕" else "未载入字幕",
            overlayRequested = connected && snapshot.overlayRequested,
        )
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

    private fun currentIsPlaying(): Boolean {
        return controllerSnapshot.connected && controllerSnapshot.isPlaying
    }

    private fun currentDurationMs(): Int {
        return if (controllerSnapshot.connected) controllerSnapshot.durationMs else 0
    }

    private fun currentPositionMs(): Int {
        return if (controllerSnapshot.connected) controllerSnapshot.positionMs else 0
    }

    private fun activeServiceSnapshot(): PlaybackServiceSnapshot {
        val controllerServiceSnapshot = controllerSnapshot.serviceSnapshot
        return if (controllerSnapshot.connected && controllerServiceSnapshot.connected) {
            controllerServiceSnapshot
        } else {
            PlaybackServiceSnapshot()
        }
    }

    private companion object {
        const val DEFAULT_AUDIO_TITLE = "未选择音频"
        const val DEFAULT_SUBTITLE_TITLE = "未选择字幕"
    }
}
