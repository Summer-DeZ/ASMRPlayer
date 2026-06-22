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
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

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

class LibraryViewModel(
    application: Application,
    private val libraryRepository: LibraryRepository,
) : AndroidViewModel(application) {
    private val librarySelection = LibrarySelectionState()
    private val pendingLibrarySelection = PendingLibrarySelection()
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        observeLibrary()
        syncStateFromRepository()
        refreshMissingDurations()
    }

    private fun syncStateFromRepository() {
        val playlists = libraryRepository.getPlaylists()
        val selectedId = libraryRepository.getSelectedPlaylistId()
        applyLibrarySnapshot(playlists, selectedId)
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
            libraryRepository.setSelectedPlaylistId(selectedPlaylist.id)
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

    fun ensureSelectedPlaylist(): Playlist {
        selectedPlaylist()?.let { return it }
        val playlist = libraryRepository.createPlaylist(DEFAULT_PLAYLIST_NAME)
        librarySelection.selectPlaylist(playlist)
        syncStateFromRepository()
        return selectedPlaylist() ?: playlist
    }

    fun handlePlaylistClick(playlist: Playlist) {
        librarySelection.handlePlaylistClick(playlist)
        librarySelection.selectedPlaylist()?.let {
            libraryRepository.setSelectedPlaylistId(it.id)
        }
        syncStateFromRepository()
    }

    fun selectPlaylist(playlist: Playlist) {
        librarySelection.selectPlaylist(playlist)
        libraryRepository.setSelectedPlaylistId(playlist.id)
        syncStateFromRepository()
    }

    fun selectPlaylistById(playlistId: String?): Playlist? {
        val playlist = PlaylistQueries.findById(_state.value.playlists, playlistId)
        if (playlist != null) {
            selectPlaylist(playlist)
        }
        return playlist
    }

    fun createPlaylist(name: String): Playlist {
        val playlist = libraryRepository.createPlaylist(name)
        librarySelection.selectPlaylist(playlist)
        syncStateFromRepository()
        return playlist
    }

    fun renamePlaylist(playlist: Playlist, name: String) {
        libraryRepository.renamePlaylist(playlist.id, name)
        syncStateFromRepository()
        selectPlaylistById(playlist.id)
    }

    fun deletePlaylist(playlist: Playlist) {
        libraryRepository.deletePlaylist(playlist.id)
        syncStateFromRepository()
    }

    fun renameTrack(playlist: Playlist, track: TrackItem, title: String) {
        libraryRepository.renameTrack(playlist.id, track.id, title)
        syncStateFromRepository()
        selectPlaylistById(playlist.id)
    }

    fun removeTrack(playlist: Playlist, track: TrackItem) {
        libraryRepository.removeTrack(playlist.id, track.id)
        syncStateFromRepository()
        selectPlaylistById(playlist.id)
    }

    fun moveTrack(sourcePlaylist: Playlist, track: TrackItem, targetPlaylist: Playlist): MoveTrackResult {
        val sourceIndex = PlaylistQueries.indexOfTrack(sourcePlaylist, track.id)
        libraryRepository.moveTrack(sourcePlaylist.id, targetPlaylist.id, track.id)
        syncStateFromRepository()
        selectPlaylistById(targetPlaylist.id)
        val movedTarget = PlaylistQueries.findById(_state.value.playlists, targetPlaylist.id)
        return MoveTrackResult(
            sourcePlaylistId = sourcePlaylist.id,
            sourceIndex = sourceIndex,
            targetPlaylistId = targetPlaylist.id,
            targetIndex = PlaylistQueries.indexOfTrack(movedTarget, track.id),
            trackId = track.id,
        )
    }

    fun addAudioUris(context: Context, uris: List<Uri>): Int {
        if (uris.isEmpty()) {
            return 0
        }
        val playlist = ensureSelectedPlaylist()
        uris.forEach { uri ->
            DocumentFiles.persistReadPermission(context, uri)
            libraryRepository.addTrack(
                playlist.id,
                TrackItem(
                    UUID.randomUUID().toString(),
                    DocumentFiles.displayName(context, uri),
                    uri.toString(),
                    "",
                    "",
                    DocumentFiles.audioDurationMs(context, uri),
                ),
            )
        }
        syncStateFromRepository()
        selectPlaylistById(playlist.id)
        return uris.size
    }

    fun importFolder(context: Context, data: android.content.Intent?, folderUri: Uri?): FolderImportResult {
        if (folderUri == null) {
            return FolderImportResult(0, 0)
        }
        val playlist = ensureSelectedPlaylist()
        DocumentFiles.persistTreeReadPermission(context, data, folderUri)
        val imports = DocumentFiles.folderAudioImports(context, folderUri)
        var subtitleCount = 0
        imports.forEach { item ->
            if (item.hasSubtitle()) {
                subtitleCount++
            }
            libraryRepository.addTrack(
                playlist.id,
                TrackItem(
                    UUID.randomUUID().toString(),
                    item.audioName,
                    item.audioUri.toString(),
                    if (item.hasSubtitle()) item.subtitleUri.toString() else "",
                    if (item.hasSubtitle()) item.subtitleName else "",
                    DocumentFiles.audioDurationMs(context, item.audioUri),
                ),
            )
        }
        syncStateFromRepository()
        selectPlaylistById(playlist.id)
        return FolderImportResult(imports.size, subtitleCount)
    }

    fun startCoverPicker(playlist: Playlist) {
        pendingLibrarySelection.startCoverPicker(playlist.id)
    }

    fun clearCoverPicker() {
        pendingLibrarySelection.clearCoverPicker()
    }

    fun handleCoverUri(uri: Uri?) {
        val playlistId = pendingLibrarySelection.consumeCoverPlaylistId()
        if (playlistId.isEmpty() || uri == null) {
            return
        }
        val selectedId = selectedPlaylist()?.id ?: libraryRepository.getSelectedPlaylistId()
        libraryRepository.setPlaylistCover(playlistId, uri.toString())
        syncStateFromRepository()
        selectPlaylistById(selectedId)
    }

    fun startSubtitlePicker(playlist: Playlist, track: TrackItem) {
        pendingLibrarySelection.startSubtitlePicker(playlist.id, track.id)
    }

    fun clearSubtitlePicker() {
        pendingLibrarySelection.clearSubtitlePicker()
    }

    fun handleSubtitleUri(context: Context, uri: Uri?): SubtitleBindingResult? {
        val target = pendingLibrarySelection.consumeSubtitleTarget()
        if (target.playlistId.isEmpty() || target.trackId.isEmpty() || uri == null) {
            return null
        }
        DocumentFiles.persistReadPermission(context, uri)
        val name = DocumentFiles.displayName(context, uri)
        libraryRepository.setTrackSubtitle(target.playlistId, target.trackId, uri.toString(), name)
        syncStateFromRepository()
        selectPlaylistById(target.playlistId)
        return SubtitleBindingResult(target.playlistId, target.trackId, uri, name)
    }

    private companion object {
        const val DEFAULT_PLAYLIST_NAME = "默认播放列表"
    }

    private fun refreshMissingDurations() {
        viewModelScope.launch(Dispatchers.IO) {
            val changed = libraryRepository.refreshMissingTrackDurations()
            if (changed) {
                withContext(Dispatchers.Main) {
                    syncStateFromRepository()
                }
            }
        }
    }
}
