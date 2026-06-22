package io.github.summerdez.asmrplayer.presentation

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
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val audioTitle: String = "未选择音频",
    val subtitleTitle: String = "未选择字幕",
    val contextTitle: String = "未选择字幕",
    val coverUri: String = "",
    val playlistId: String = "",
    val playlistIndex: Int = -1,
    val hasAudio: Boolean = false,
    val isPlaying: Boolean = false,
    val canPlayNext: Boolean = false,
    val durationMs: Int = 0,
    val positionMs: Int = 0,
    val previousSubtitle: String = "",
    val currentSubtitle: String = "",
    val nextSubtitle: String = "",
    val subtitleLines: List<String> = emptyList(),
    val subtitleIndex: Int = -1,
    val subtitleEmptyText: String = "未载入字幕",
    val overlayRequested: Boolean = false,
    val overlayLocked: Boolean = false,
    val error: PlaybackError = PlaybackError.None,
)

class PlaybackViewModel(
    application: Application,
    private val libraryRepository: LibraryRepository,
    private val playbackCommands: PlaybackCommandClient,
) : AndroidViewModel(application) {
    private val playbackSelection = PlaybackSelection(DEFAULT_AUDIO_TITLE, DEFAULT_SUBTITLE_TITLE)
    private var lastPlaylists: List<Playlist> = emptyList()
    private var lastSelectedPlaylist: Playlist? = null
    private var serviceSnapshot = PlaybackServiceSnapshot()
    private var controllerSnapshot = PlaybackControllerSnapshot()
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(PlaybackUiState())
    val state: kotlinx.coroutines.flow.StateFlow<PlaybackUiState> = _state

    init {
        playbackCommands.connect()
        observeLibrary()
        observePlaybackController()
        observePlaybackService()
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
        if ((serviceSnapshot.connected || controllerSnapshot.connected)
            && playbackDurationMs > 0
            && audioUri != null
            && serviceSnapshot.audioUri == audioUri.toString()
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
                syncPlaylistStateFromSnapshot(serviceSnapshot)
                emitState()
            }
        }
    }

    private fun observePlaybackService() {
        viewModelScope.launch {
            PlaybackServiceState.snapshots.collect { snapshot ->
                serviceSnapshot = snapshot
                syncPlaylistStateFromSnapshot(snapshot)
                emitState()
            }
        }
    }

    private fun observePlaybackController() {
        viewModelScope.launch {
            playbackCommands.snapshots.collect { snapshot ->
                controllerSnapshot = snapshot
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
        val snapshot = serviceSnapshot
        val connected = snapshot.connected || controllerSnapshot.connected
        _state.value = PlaybackUiState(
            audioTitle = playbackSelection.audioTitle(),
            subtitleTitle = playbackSelection.subtitleTitle(),
            contextTitle = playbackContextTitle(),
            coverUri = playbackContextCoverUri(),
            playlistId = playbackSelection.playlistId(),
            playlistIndex = playbackSelection.playlistIndex(),
            hasAudio = playbackSelection.hasAudio(),
            isPlaying = connected && currentIsPlaying(),
            canPlayNext = controllerSnapshot.hasNextMediaItem || playbackSelection.hasNextIn(activePlaylistForNavigation()),
            durationMs = if (connected) currentDurationMs() else 0,
            positionMs = if (connected) currentPositionMs() else 0,
            previousSubtitle = if (connected) snapshot.previousSubtitle else "",
            currentSubtitle = if (connected) snapshot.currentSubtitle else "",
            nextSubtitle = if (connected) snapshot.nextSubtitle else "",
            subtitleLines = if (connected) snapshot.subtitleLines else emptyList(),
            subtitleIndex = if (connected) snapshot.subtitleIndex else -1,
            subtitleEmptyText = if (snapshot.subtitleCount > 0) "等待字幕" else "未载入字幕",
            overlayRequested = connected && snapshot.overlayRequested,
            overlayLocked = connected && snapshot.overlayLocked,
            error = if (connected) snapshot.error else PlaybackError.None,
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
        return if (controllerSnapshot.connected) controllerSnapshot.isPlaying else serviceSnapshot.isPlaying
    }

    private fun currentDurationMs(): Int {
        return controllerSnapshot.durationMs.takeIf { controllerSnapshot.connected && it > 0 }
            ?: serviceSnapshot.durationMs
    }

    private fun currentPositionMs(): Int {
        return if (controllerSnapshot.connected) controllerSnapshot.positionMs else serviceSnapshot.positionMs
    }

    private companion object {
        const val DEFAULT_AUDIO_TITLE = "未选择音频"
        const val DEFAULT_SUBTITLE_TITLE = "未选择字幕"
    }
}
