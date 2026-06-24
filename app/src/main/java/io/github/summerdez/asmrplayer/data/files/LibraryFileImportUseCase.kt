package io.github.summerdez.asmrplayer.data.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.summerdez.asmrplayer.data.LibraryRepository
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LibraryFolderAudioImport<ImportUri>(
    val audioName: String,
    val audioUri: ImportUri,
    val subtitleName: String = "",
    val subtitleUri: ImportUri? = null,
) {
    fun hasSubtitle(): Boolean = subtitleUri != null
}

data class LibraryFolderImportResult(
    val audioCount: Int,
    val subtitleCount: Int,
)

data class LibrarySubtitleBindingResult<ImportUri>(
    val playlistId: String,
    val trackId: String,
    val subtitleUri: ImportUri,
    val subtitleTitle: String,
)

interface LibraryImportFiles<ImportUri> {
    fun persistReadPermission(context: Context?, uri: ImportUri)
    fun persistTreeReadPermission(context: Context?, data: Intent?, uri: ImportUri)
    fun displayName(context: Context?, uri: ImportUri): String
    fun audioDurationMs(context: Context?, uri: ImportUri): Long
    fun folderAudioImports(context: Context?, folderUri: ImportUri): List<LibraryFolderAudioImport<ImportUri>>
    fun uriString(uri: ImportUri): String
}

class DocumentLibraryImportFiles : LibraryImportFiles<Uri> {
    override fun persistReadPermission(context: Context?, uri: Uri) {
        context ?: return
        DocumentFiles.persistReadPermission(context, uri)
    }

    override fun persistTreeReadPermission(context: Context?, data: Intent?, uri: Uri) {
        context ?: return
        DocumentFiles.persistTreeReadPermission(context, data, uri)
    }

    override fun displayName(context: Context?, uri: Uri): String {
        return if (context == null) {
            uri.lastPathSegment ?: uri.toString()
        } else {
            DocumentFiles.displayName(context, uri)
        }
    }

    override fun audioDurationMs(context: Context?, uri: Uri): Long {
        return DocumentFiles.audioDurationMs(context, uri)
    }

    override fun folderAudioImports(context: Context?, folderUri: Uri): List<LibraryFolderAudioImport<Uri>> {
        context ?: return emptyList()
        return DocumentFiles.folderAudioImports(context, folderUri).map { item ->
            LibraryFolderAudioImport(
                audioName = item.audioName,
                audioUri = item.audioUri,
                subtitleName = item.subtitleName,
                subtitleUri = item.subtitleUri,
            )
        }
    }

    override fun uriString(uri: Uri): String = uri.toString()
}

class LibraryFileImportUseCase<ImportUri>(
    private val libraryRepository: LibraryRepository,
    private val files: LibraryImportFiles<ImportUri>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) {
    @Suppress("UNCHECKED_CAST")
    constructor(
        libraryRepository: LibraryRepository,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        idProvider: () -> String = { UUID.randomUUID().toString() },
    ) : this(
        libraryRepository = libraryRepository,
        files = DocumentLibraryImportFiles() as LibraryImportFiles<ImportUri>,
        ioDispatcher = ioDispatcher,
        idProvider = idProvider,
    )

    suspend fun addAudioUris(
        context: Context?,
        selectedPlaylist: Playlist?,
        uris: List<ImportUri>,
    ): Int {
        if (uris.isEmpty()) {
            return 0
        }
        return withContext(ioDispatcher) {
            val playlist = selectedPlaylist ?: libraryRepository.createPlaylist(DEFAULT_PLAYLIST_NAME)
            uris.forEach { uri ->
                files.persistReadPermission(context, uri)
                libraryRepository.addTrack(
                    playlist.id,
                    TrackItem(
                        idProvider(),
                        files.displayName(context, uri),
                        files.uriString(uri),
                        "",
                        "",
                        files.audioDurationMs(context, uri),
                    ),
                )
            }
            libraryRepository.setSelectedPlaylistId(playlist.id)
            uris.size
        }
    }

    suspend fun importFolder(
        context: Context?,
        data: Intent?,
        folderUri: ImportUri?,
        selectedPlaylist: Playlist?,
    ): LibraryFolderImportResult {
        if (folderUri == null) {
            return LibraryFolderImportResult(0, 0)
        }
        return withContext(ioDispatcher) {
            val playlist = selectedPlaylist ?: libraryRepository.createPlaylist(DEFAULT_PLAYLIST_NAME)
            files.persistTreeReadPermission(context, data, folderUri)
            val imports = files.folderAudioImports(context, folderUri)
            var subtitleCount = 0
            imports.forEach { item ->
                if (item.hasSubtitle()) {
                    subtitleCount++
                }
                libraryRepository.addTrack(
                    playlist.id,
                    TrackItem(
                        idProvider(),
                        item.audioName,
                        files.uriString(item.audioUri),
                        item.subtitleUri?.let { files.uriString(it) }.orEmpty(),
                        if (item.hasSubtitle()) item.subtitleName else "",
                        files.audioDurationMs(context, item.audioUri),
                    ),
                )
            }
            libraryRepository.setSelectedPlaylistId(playlist.id)
            LibraryFolderImportResult(imports.size, subtitleCount)
        }
    }

    suspend fun bindSubtitle(
        context: Context?,
        playlistId: String,
        trackId: String,
        subtitleUri: ImportUri?,
    ): LibrarySubtitleBindingResult<ImportUri>? {
        if (playlistId.isEmpty() || trackId.isEmpty() || subtitleUri == null) {
            return null
        }
        return withContext(ioDispatcher) {
            files.persistReadPermission(context, subtitleUri)
            val name = files.displayName(context, subtitleUri)
            val updated = libraryRepository.setTrackSubtitle(
                playlistId,
                trackId,
                files.uriString(subtitleUri),
                name,
            )
            if (!updated) {
                return@withContext null
            }
            LibrarySubtitleBindingResult(
                playlistId = playlistId,
                trackId = trackId,
                subtitleUri = subtitleUri,
                subtitleTitle = name,
            )
        }
    }

    suspend fun setPlaylistCover(
        context: Context?,
        playlistId: String,
        coverUri: ImportUri?,
    ): Boolean {
        if (playlistId.isEmpty() || coverUri == null) {
            return false
        }
        return withContext(ioDispatcher) {
            files.persistReadPermission(context, coverUri)
            libraryRepository.setPlaylistCover(playlistId, files.uriString(coverUri))
            true
        }
    }

    private companion object {
        const val DEFAULT_PLAYLIST_NAME = "默认播放列表"
    }
}
