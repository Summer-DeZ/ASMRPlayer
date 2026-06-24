package io.github.summerdez.asmrplayer.presentation

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.summerdez.asmrplayer.data.LibraryRepository
import io.github.summerdez.asmrplayer.data.files.LibraryFileImportUseCase
import io.github.summerdez.asmrplayer.domain.LibrarySelectionState
import io.github.summerdez.asmrplayer.domain.PendingLibrarySelection
import io.github.summerdez.asmrplayer.domain.PlaylistQueries
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryUiState(
    val playlists: List<Playlist> = emptyList(),
    val selectedPlaylist: Playlist? = null,
    val collapsedSelectedPlaylistId: String = "",
    val animatingExpandPlaylistId: String = "",
    val animatingCollapsePlaylistId: String = "",
)

data class FolderImportResult(
    val audioCount: Int,
    val subtitleCount: Int,
)

data class SubtitleBindingResult(
    val playlistId: String,
    val trackId: String,
    val subtitleUri: Uri,
    val subtitleTitle: String,
)

data class MoveTrackResult(
    val sourcePlaylistId: String,
    val sourceIndex: Int,
    val targetPlaylistId: String,
    val targetIndex: Int,
    val trackId: String,
)

sealed interface LibraryEvent {
    data class AudioUrisImported(val count: Int) : LibraryEvent
    data class FolderImported(val result: FolderImportResult) : LibraryEvent
    data class SubtitleBound(val binding: SubtitleBindingResult) : LibraryEvent
    data class TrackMoved(val result: MoveTrackResult, val targetPlaylistName: String) : LibraryEvent
}

class LibraryViewModel(
    application: Application,
    private val libraryRepository: LibraryRepository,
    private val fileImportUseCase: LibraryFileImportUseCase<Uri>,
) : AndroidViewModel(application) {
    private val librarySelection = LibrarySelectionState()
    private val pendingLibrarySelection = PendingLibrarySelection()
    private val _state = MutableStateFlow(LibraryUiState())
    private val _events = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 8)
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    init {
        observeLibrary()
        refreshMissingDurations()
    }

    private fun observeLibrary() {
        viewModelScope.launch {
            combine(
                libraryRepository.playlistsFlow,
                libraryRepository.selectedPlaylistIdFlow,
            ) { playlists, selectedId ->
                playlists to selectedId
            }.collect { (playlists, selectedId) ->
                applyLibrarySnapshot(playlists, selectedId)
            }
        }
    }

    private fun applyLibrarySnapshot(playlists: List<Playlist>, selectedId: String) {
        librarySelection.sync(playlists, selectedId)
        val selectedPlaylist = librarySelection.selectedPlaylist()
        if (selectedPlaylist != null && selectedPlaylist.id != selectedId) {
            viewModelScope.launch {
                libraryRepository.setSelectedPlaylistId(selectedPlaylist.id)
            }
        }
        _state.value = LibraryUiState(
            playlists = playlists,
            selectedPlaylist = selectedPlaylist,
            collapsedSelectedPlaylistId = librarySelection.collapsedSelectedPlaylistId(),
            animatingExpandPlaylistId = librarySelection.animatingExpandPlaylistId(),
            animatingCollapsePlaylistId = librarySelection.animatingCollapsePlaylistId(),
        )
    }

    fun selectedPlaylist(): Playlist? = _state.value.selectedPlaylist

    fun handlePlaylistClick(playlist: Playlist) {
        librarySelection.handlePlaylistClick(playlist)
        librarySelection.selectedPlaylist()?.let {
            viewModelScope.launch {
                libraryRepository.setSelectedPlaylistId(it.id)
            }
        }
        applySelectionState()
    }

    fun selectPlaylist(playlist: Playlist) {
        librarySelection.selectPlaylist(playlist)
        viewModelScope.launch {
            libraryRepository.setSelectedPlaylistId(playlist.id)
        }
        applySelectionState()
    }

    fun selectPlaylistById(playlistId: String?): Playlist? {
        val playlist = PlaylistQueries.findById(_state.value.playlists, playlistId)
        if (playlist != null) {
            selectPlaylist(playlist)
        }
        return playlist
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val playlist = libraryRepository.createPlaylist(name)
            librarySelection.selectPlaylist(playlist)
            applySelectionState()
        }
    }

    fun renamePlaylist(playlist: Playlist, name: String) {
        viewModelScope.launch {
            libraryRepository.renamePlaylist(playlist.id, name)
            selectPlaylistById(playlist.id)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            libraryRepository.deletePlaylist(playlist.id)
        }
    }

    fun renameTrack(playlist: Playlist, track: TrackItem, title: String) {
        viewModelScope.launch {
            libraryRepository.renameTrack(playlist.id, track.id, title)
            selectPlaylistById(playlist.id)
        }
    }

    fun removeTrack(playlist: Playlist, track: TrackItem) {
        viewModelScope.launch {
            libraryRepository.removeTrack(playlist.id, track.id)
            selectPlaylistById(playlist.id)
        }
    }

    fun moveTrack(sourcePlaylist: Playlist, track: TrackItem, targetPlaylist: Playlist) {
        val sourceIndex = PlaylistQueries.indexOfTrack(sourcePlaylist, track.id)
        viewModelScope.launch {
            val moved = libraryRepository.moveTrack(sourcePlaylist.id, targetPlaylist.id, track.id)
            if (!moved) {
                return@launch
            }
            selectPlaylistById(targetPlaylist.id)
            val targetIndex = if (sourcePlaylist.id == targetPlaylist.id) {
                PlaylistQueries.indexOfTrack(targetPlaylist, track.id)
            } else {
                targetPlaylist.tracks.size
            }
            _events.emit(
                LibraryEvent.TrackMoved(
                    MoveTrackResult(
                        sourcePlaylistId = sourcePlaylist.id,
                        sourceIndex = sourceIndex,
                        targetPlaylistId = targetPlaylist.id,
                        targetIndex = targetIndex,
                        trackId = track.id,
                    ),
                    targetPlaylist.name,
                ),
            )
        }
    }

    fun addAudioUris(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            val count = fileImportUseCase.addAudioUris(context, selectedPlaylist(), uris)
            _events.emit(LibraryEvent.AudioUrisImported(count))
        }
    }

    fun importFolder(context: Context, data: Intent?, folderUri: Uri?) {
        viewModelScope.launch {
            val result = fileImportUseCase.importFolder(context, data, folderUri, selectedPlaylist())
            _events.emit(
                LibraryEvent.FolderImported(
                    FolderImportResult(
                        audioCount = result.audioCount,
                        subtitleCount = result.subtitleCount,
                    ),
                ),
            )
        }
    }

    fun startCoverPicker(playlist: Playlist) {
        pendingLibrarySelection.startCoverPicker(playlist.id)
    }

    fun clearCoverPicker() {
        pendingLibrarySelection.clearCoverPicker()
    }

    fun handleCoverUri(context: Context, uri: Uri?) {
        val playlistId = pendingLibrarySelection.consumeCoverPlaylistId()
        if (playlistId.isEmpty() || uri == null) {
            return
        }
        val selectedId = selectedPlaylist()?.id.orEmpty()
        viewModelScope.launch {
            val updated = fileImportUseCase.setPlaylistCover(
                context = context,
                playlistId = playlistId,
                coverUri = uri,
            )
            if (!updated) {
                return@launch
            }
            if (selectedId.isNotEmpty()) {
                libraryRepository.setSelectedPlaylistId(selectedId)
            }
        }
    }

    fun startSubtitlePicker(playlist: Playlist, track: TrackItem) {
        pendingLibrarySelection.startSubtitlePicker(playlist.id, track.id)
    }

    fun clearSubtitlePicker() {
        pendingLibrarySelection.clearSubtitlePicker()
    }

    fun handleSubtitleUri(context: Context, uri: Uri?) {
        val target = pendingLibrarySelection.consumeSubtitleTarget()
        if (target.playlistId.isEmpty() || target.trackId.isEmpty() || uri == null) {
            return
        }
        viewModelScope.launch {
            val binding = fileImportUseCase.bindSubtitle(
                context = context,
                playlistId = target.playlistId,
                trackId = target.trackId,
                subtitleUri = uri,
            ) ?: return@launch
            selectPlaylistById(binding.playlistId)
            _events.emit(
                LibraryEvent.SubtitleBound(
                    SubtitleBindingResult(
                        playlistId = binding.playlistId,
                        trackId = binding.trackId,
                        subtitleUri = binding.subtitleUri,
                        subtitleTitle = binding.subtitleTitle,
                    ),
                ),
            )
        }
    }

    private fun refreshMissingDurations() {
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepository.refreshMissingTrackDurations()
        }
    }

    private fun applySelectionState() {
        val playlists = _state.value.playlists
        _state.value = _state.value.copy(
            selectedPlaylist = librarySelection.selectedPlaylist(),
            collapsedSelectedPlaylistId = librarySelection.collapsedSelectedPlaylistId(),
            animatingExpandPlaylistId = librarySelection.animatingExpandPlaylistId(),
            animatingCollapsePlaylistId = librarySelection.animatingCollapsePlaylistId(),
            playlists = playlists,
        )
    }
}
