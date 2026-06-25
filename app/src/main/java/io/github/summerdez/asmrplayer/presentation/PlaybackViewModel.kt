package io.github.summerdez.asmrplayer.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.summerdez.asmrplayer.data.LibraryRepository
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.playback.PlaybackCommandClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PlaybackViewModel(
    application: Application,
    private val libraryRepository: LibraryRepository,
    private val playbackCommands: PlaybackCommandClient,
) : AndroidViewModel(application) {
    private val presentationState = PlaybackPresentationState()
    private val _state = MutableStateFlow(presentationState.uiState())
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
        if (!presentationState.hasAudio()) {
            val request = presentationState.selectFirstTrackFromSelectedPlaylist()
            if (request != null) {
                emitState()
                startPlayback(request)
                return null
            }
            return "请先选择音频"
        }

        if (presentationState.shouldToggleCurrentSelection()) {
            playbackCommands.togglePlayback(presentationState.currentIsPlaying())
            return null
        }

        presentationState.currentPlaybackRequest()?.let(::startPlayback)
        return null
    }

    fun playPlaylistTrack(playlist: Playlist?, index: Int) {
        val request = presentationState.selectPlaylistTrack(playlist, index) ?: return
        emitState()
        startPlayback(request)
    }

    fun playRelativeTrack(delta: Int): String? {
        return when (val navigation = presentationState.selectRelativeTrack(delta)) {
            PlaybackTrackNavigation.Empty -> "当前播放列表为空"
            PlaybackTrackNavigation.BeforeStart -> "已经是第一首"
            PlaybackTrackNavigation.AfterEnd -> "已经是最后一首"
            is PlaybackTrackNavigation.Ready -> {
                emitState()
                startPlayback(navigation.request)
                null
            }
        }
    }

    fun seekTo(positionMs: Int) {
        playbackCommands.seekTo(positionMs)
    }

    fun setSubtitleForCurrentTrack(uri: Uri, title: String, trackId: String) {
        if (presentationState.updateSubtitleForCurrentTrack(uri, title, trackId)) {
            playbackCommands.setSubtitle(uri)
            emitState()
        }
    }

    fun onPlaylistDeleted(playlistId: String) {
        presentationState.onPlaylistDeleted(playlistId)
        emitState()
    }

    fun onTrackRemoved(playlistId: String, index: Int) {
        presentationState.onTrackRemoved(playlistId, index)
        emitState()
    }

    fun onTrackRenamed(trackId: String, title: String) {
        if (presentationState.renameCurrentTrack(trackId, title)) {
            emitState()
        }
    }

    fun onTrackMoved(result: MoveTrackResult) {
        presentationState.onTrackMoved(result)
        emitState()
    }

    fun currentTrackId(): String {
        return presentationState.currentTrackId()
    }

    private fun observeLibrary() {
        viewModelScope.launch {
            combine(
                libraryRepository.playlistsFlow,
                libraryRepository.selectedPlaylistIdFlow,
            ) { playlists, selectedId ->
                playlists to selectedId
            }.collect { (playlists, selectedId) ->
                _state.value = presentationState.updateLibrary(playlists, selectedId)
            }
        }
    }

    private fun observePlaybackController() {
        viewModelScope.launch {
            playbackCommands.snapshots.collect { snapshot ->
                _state.value = presentationState.updateController(snapshot)
            }
        }
    }

    private fun startPlayback(request: PlaybackStartRequest) {
        playbackCommands.playMedia(
            request.audioUri,
            request.title,
            request.subtitleUri,
            request.playlistId,
            request.playlistIndex,
        )
    }

    private fun emitState() {
        _state.value = presentationState.uiState()
    }
}
