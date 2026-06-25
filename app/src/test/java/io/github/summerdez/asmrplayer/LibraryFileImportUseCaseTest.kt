package io.github.summerdez.asmrplayer

import android.content.Context
import android.content.Intent
import io.github.summerdez.asmrplayer.data.LibraryRepository
import io.github.summerdez.asmrplayer.data.files.LibraryFileImportUseCase
import io.github.summerdez.asmrplayer.data.files.LibraryFolderAudioImport
import io.github.summerdez.asmrplayer.data.files.LibraryImportFiles
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFileImportUseCaseTest {
    @Test
    fun emptyAudioUrisDoNotCreatePlaylistOrReadFiles() = runBlocking {
        val repository = FakeLibraryRepository()
        val files = FakeLibraryImportFiles()
        val useCase = useCase(repository, files)

        val count = useCase.addAudioUris(context = null, selectedPlaylist = null, uris = emptyList())

        assertEquals(0, count)
        assertEquals(emptyList<String>(), repository.createdPlaylistNames)
        assertEquals(emptyList<String>(), files.persistedReadUris)
        assertEquals(emptyList<String>(), files.displayNameUris)
        assertEquals(emptyList<AddedTrack>(), repository.addedTracks)
        assertEquals(emptyList<String>(), repository.selectedPlaylistIds)
    }

    @Test
    fun audioImportCreatesDefaultPlaylistWhenSelectionIsEmpty() = runBlocking {
        val repository = FakeLibraryRepository()
        val files = FakeLibraryImportFiles()
        val first = "content://audio/one"
        val second = "content://audio/two"
        files.displayNames[first] = "one.mp3"
        files.displayNames[second] = "two.wav"
        files.durations[first] = 1_000L
        files.durations[second] = 2_000L
        val useCase = useCase(repository, files, ids = listOf("track-1", "track-2"))

        val count = useCase.addAudioUris(context = null, selectedPlaylist = null, uris = listOf(first, second))

        assertEquals(2, count)
        assertEquals(listOf("默认播放列表"), repository.createdPlaylistNames)
        assertEquals(listOf(first, second), files.persistedReadUris)
        assertEquals(listOf(first, second), files.displayNameUris)
        assertEquals(listOf(first, second), files.durationUris)
        assertEquals(listOf("created-1", "created-1"), repository.addedTracks.map { it.playlistId })
        assertEquals(listOf("track-1", "track-2"), repository.addedTracks.map { it.track.id })
        assertEquals(listOf("one.mp3", "two.wav"), repository.addedTracks.map { it.track.title })
        assertEquals(listOf(first, second), repository.addedTracks.map { it.track.uri })
        assertEquals(listOf(1_000L, 2_000L), repository.addedTracks.map { it.track.durationMs })
        assertEquals(listOf("created-1"), repository.selectedPlaylistIds)
    }

    @Test
    fun audioImportUsesExistingSelectedPlaylist() = runBlocking {
        val repository = FakeLibraryRepository()
        val files = FakeLibraryImportFiles()
        val selected = Playlist("playlist-1", "Selected")
        val audio = "content://audio/existing"
        files.displayNames[audio] = "existing.flac"
        val useCase = useCase(repository, files, ids = listOf("track-existing"))

        val count = useCase.addAudioUris(context = null, selectedPlaylist = selected, uris = listOf(audio))

        assertEquals(1, count)
        assertEquals(emptyList<String>(), repository.createdPlaylistNames)
        assertEquals(listOf("playlist-1"), repository.addedTracks.map { it.playlistId })
        assertEquals("track-existing", repository.addedTracks.single().track.id)
        assertEquals("existing.flac", repository.addedTracks.single().track.title)
        assertEquals(listOf("playlist-1"), repository.selectedPlaylistIds)
    }

    @Test
    fun nullFolderUriDoesNotCreatePlaylist() = runBlocking {
        val repository = FakeLibraryRepository()
        val files = FakeLibraryImportFiles()
        val useCase = useCase(repository, files)

        val result = useCase.importFolder(context = null, data = null, folderUri = null, selectedPlaylist = null)

        assertEquals(0, result.audioCount)
        assertEquals(0, result.subtitleCount)
        assertEquals(emptyList<String>(), repository.createdPlaylistNames)
        assertEquals(emptyList<String>(), files.persistedTreeUris)
        assertEquals(emptyList<String>(), files.folderImportUris)
        assertEquals(emptyList<AddedTrack>(), repository.addedTracks)
    }

    @Test
    fun folderImportCountsSubtitlesAndPreservesTrackSubtitleFields() = runBlocking {
        val repository = FakeLibraryRepository()
        val files = FakeLibraryImportFiles()
        val folderUri = "content://tree/folder"
        val firstAudio = "content://tree/folder/one"
        val firstSubtitle = "content://tree/folder/one-subtitle"
        val secondAudio = "content://tree/folder/two"
        files.folderImports[folderUri] = listOf(
            LibraryFolderAudioImport(
                audioName = "one.mp3",
                audioUri = firstAudio,
                subtitleName = "one.mp3.vtt",
                subtitleUri = firstSubtitle,
            ),
            LibraryFolderAudioImport(
                audioName = "two.wav",
                audioUri = secondAudio,
            ),
        )
        files.durations[firstAudio] = 3_000L
        files.durations[secondAudio] = 4_000L
        val useCase = useCase(repository, files, ids = listOf("track-1", "track-2"))

        val result = useCase.importFolder(
            context = null,
            data = null,
            folderUri = folderUri,
            selectedPlaylist = Playlist("playlist-1", "Selected"),
        )

        assertEquals(2, result.audioCount)
        assertEquals(1, result.subtitleCount)
        assertEquals(emptyList<String>(), repository.createdPlaylistNames)
        assertEquals(listOf(folderUri), files.persistedTreeUris)
        assertEquals(listOf(folderUri), files.folderImportUris)
        assertEquals(listOf("playlist-1", "playlist-1"), repository.addedTracks.map { it.playlistId })
        assertEquals(listOf("one.mp3", "two.wav"), repository.addedTracks.map { it.track.title })
        assertEquals(listOf(firstAudio, secondAudio), repository.addedTracks.map { it.track.uri })
        assertEquals(firstSubtitle, repository.addedTracks[0].track.subtitleUri)
        assertEquals("one.mp3.vtt", repository.addedTracks[0].track.subtitleTitle)
        assertEquals("", repository.addedTracks[1].track.subtitleUri)
        assertEquals("", repository.addedTracks[1].track.subtitleTitle)
        assertEquals(listOf(3_000L, 4_000L), repository.addedTracks.map { it.track.durationMs })
        assertEquals(listOf("playlist-1"), repository.selectedPlaylistIds)
    }

    @Test
    fun subtitleBindingWithoutTargetOrUriDoesNotReadFilesOrUpdateRepository() = runBlocking {
        val repository = FakeLibraryRepository()
        val files = FakeLibraryImportFiles()
        val useCase = useCase(repository, files)

        assertNull(
            useCase.bindSubtitle(
                context = null,
                playlistId = "",
                trackId = "track-1",
                subtitleUri = "content://subtitle/one",
            ),
        )
        assertNull(
            useCase.bindSubtitle(
                context = null,
                playlistId = "playlist-1",
                trackId = "",
                subtitleUri = "content://subtitle/one",
            ),
        )
        assertNull(
            useCase.bindSubtitle(
                context = null,
                playlistId = "playlist-1",
                trackId = "track-1",
                subtitleUri = null,
            ),
        )

        assertEquals(emptyList<String>(), files.persistedReadUris)
        assertEquals(emptyList<String>(), files.displayNameUris)
        assertEquals(emptyList<SubtitleUpdate>(), repository.subtitleUpdates)
    }

    @Test
    fun subtitleBindingPersistsPermissionReadsNameAndUpdatesRepository() = runBlocking {
        val repository = FakeLibraryRepository()
        val files = FakeLibraryImportFiles()
        val subtitle = "content://subtitle/one"
        files.displayNames[subtitle] = "episode-one.vtt"
        val useCase = useCase(repository, files)

        val result = useCase.bindSubtitle(
            context = null,
            playlistId = "playlist-1",
            trackId = "track-1",
            subtitleUri = subtitle,
        ) ?: error("Expected subtitle binding result")

        assertEquals(listOf(subtitle), files.persistedReadUris)
        assertEquals(listOf(subtitle), files.displayNameUris)
        assertEquals(
            listOf(
                SubtitleUpdate(
                    playlistId = "playlist-1",
                    trackId = "track-1",
                    subtitleUri = subtitle,
                    subtitleTitle = "episode-one.vtt",
                ),
            ),
            repository.subtitleUpdates,
        )
        assertEquals("playlist-1", result.playlistId)
        assertEquals("track-1", result.trackId)
        assertEquals(subtitle, result.subtitleUri)
        assertEquals("episode-one.vtt", result.subtitleTitle)
    }

    @Test
    fun subtitleBindingReturnsNullWhenRepositoryRejectsUpdate() = runBlocking {
        val repository = FakeLibraryRepository()
        val files = FakeLibraryImportFiles()
        val subtitle = "content://subtitle/rejected"
        repository.setTrackSubtitleResult = false
        files.displayNames[subtitle] = "rejected.vtt"
        val useCase = useCase(repository, files)

        val result = useCase.bindSubtitle(
            context = null,
            playlistId = "playlist-1",
            trackId = "track-1",
            subtitleUri = subtitle,
        )

        assertNull(result)
        assertEquals(listOf(subtitle), files.persistedReadUris)
        assertEquals(listOf(subtitle), files.displayNameUris)
        assertEquals(
            listOf(
                SubtitleUpdate(
                    playlistId = "playlist-1",
                    trackId = "track-1",
                    subtitleUri = subtitle,
                    subtitleTitle = "rejected.vtt",
                ),
            ),
            repository.subtitleUpdates,
        )
    }

    @Test
    fun playlistCoverWithoutTargetOrUriDoesNotReadFilesOrUpdateRepository() = runBlocking {
        val repository = FakeLibraryRepository()
        val files = FakeLibraryImportFiles()
        val useCase = useCase(repository, files)

        assertFalse(
            useCase.setPlaylistCover(
                context = null,
                playlistId = "",
                coverUri = "content://cover/one",
            ),
        )
        assertFalse(
            useCase.setPlaylistCover(
                context = null,
                playlistId = "playlist-1",
                coverUri = null,
            ),
        )

        assertEquals(emptyList<String>(), files.persistedReadUris)
        assertEquals(emptyList<String>(), files.uriStringUris)
        assertEquals(emptyList<CoverUpdate>(), repository.coverUpdates)
    }

    @Test
    fun playlistCoverPersistsPermissionAndUpdatesRepository() = runBlocking {
        val repository = FakeLibraryRepository()
        val files = FakeLibraryImportFiles()
        val cover = "content://cover/one"
        val useCase = useCase(repository, files)

        val result = useCase.setPlaylistCover(
            context = null,
            playlistId = "playlist-1",
            coverUri = cover,
        )

        assertTrue(result)
        assertEquals(listOf(cover), files.persistedReadUris)
        assertEquals(listOf<Context?>(null), files.persistedReadContexts)
        assertEquals(listOf(cover), files.uriStringUris)
        assertEquals(
            listOf(
                CoverUpdate(
                    playlistId = "playlist-1",
                    coverUri = cover,
                ),
            ),
            repository.coverUpdates,
        )
    }

    private fun useCase(
        repository: FakeLibraryRepository,
        files: FakeLibraryImportFiles,
        ids: List<String> = emptyList(),
    ): LibraryFileImportUseCase<String> {
        val remainingIds = ids.toMutableList()
        return LibraryFileImportUseCase(
            libraryRepository = repository,
            files = files,
            ioDispatcher = Dispatchers.Unconfined,
            idProvider = {
                if (remainingIds.isEmpty()) {
                    "track-${repository.addedTracks.size + 1}"
                } else {
                    remainingIds.removeAt(0)
                }
            },
        )
    }

}

private data class AddedTrack(
    val playlistId: String,
    val track: TrackItem,
)

private data class SubtitleUpdate(
    val playlistId: String,
    val trackId: String,
    val subtitleUri: String,
    val subtitleTitle: String,
)

private data class CoverUpdate(
    val playlistId: String,
    val coverUri: String,
)

private class FakeLibraryImportFiles : LibraryImportFiles<String> {
    val persistedReadUris = mutableListOf<String>()
    val persistedReadContexts = mutableListOf<Context?>()
    val persistedTreeUris = mutableListOf<String>()
    val displayNameUris = mutableListOf<String>()
    val durationUris = mutableListOf<String>()
    val folderImportUris = mutableListOf<String>()
    val uriStringUris = mutableListOf<String>()
    val displayNames = mutableMapOf<String, String>()
    val durations = mutableMapOf<String, Long>()
    val folderImports = mutableMapOf<String, List<LibraryFolderAudioImport<String>>>()

    override fun persistReadPermission(context: Context?, uri: String) {
        persistedReadContexts += context
        persistedReadUris += uri
    }

    override fun persistTreeReadPermission(context: Context?, data: Intent?, uri: String) {
        persistedTreeUris += uri
    }

    override fun displayName(context: Context?, uri: String): String {
        displayNameUris += uri
        return displayNames[uri] ?: uri
    }

    override fun audioDurationMs(context: Context?, uri: String): Long {
        durationUris += uri
        return durations[uri] ?: 0L
    }

    override fun folderAudioImports(context: Context?, folderUri: String): List<LibraryFolderAudioImport<String>> {
        folderImportUris += folderUri
        return folderImports[folderUri].orEmpty()
    }

    override fun uriString(uri: String): String {
        uriStringUris += uri
        return uri
    }
}

private class FakeLibraryRepository : LibraryRepository {
    override val playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
    override val selectedPlaylistIdFlow = MutableStateFlow("")
    val createdPlaylistNames = mutableListOf<String>()
    val addedTracks = mutableListOf<AddedTrack>()
    val selectedPlaylistIds = mutableListOf<String>()
    val subtitleUpdates = mutableListOf<SubtitleUpdate>()
    val coverUpdates = mutableListOf<CoverUpdate>()
    var setTrackSubtitleResult = true

    override suspend fun createPlaylist(name: String): Playlist {
        createdPlaylistNames += name
        return Playlist("created-${createdPlaylistNames.size}", name)
    }

    override suspend fun addTrack(playlistId: String, track: TrackItem) {
        addedTracks += AddedTrack(playlistId, track)
    }

    override suspend fun setSelectedPlaylistId(playlistId: String) {
        selectedPlaylistIds += playlistId
    }

    override suspend fun getPlaylist(playlistId: String): Playlist? = unused()
    override suspend fun renamePlaylist(playlistId: String, name: String) = unused()
    override suspend fun setPlaylistCover(playlistId: String, coverUri: String) {
        coverUpdates += CoverUpdate(
            playlistId = playlistId,
            coverUri = coverUri,
        )
    }
    override suspend fun deletePlaylist(playlistId: String) = unused()
    override suspend fun renameTrack(playlistId: String, trackId: String, title: String) = unused()
    override suspend fun setTrackSubtitle(
        playlistId: String,
        trackId: String,
        subtitleUri: String,
        subtitleTitle: String,
    ): Boolean {
        subtitleUpdates += SubtitleUpdate(
            playlistId = playlistId,
            trackId = trackId,
            subtitleUri = subtitleUri,
            subtitleTitle = subtitleTitle,
        )
        return setTrackSubtitleResult
    }
    override suspend fun removeTrack(playlistId: String, trackId: String) = unused()
    override suspend fun moveTrack(fromPlaylistId: String, toPlaylistId: String, trackId: String): Boolean =
        unused()
    override suspend fun refreshMissingTrackDurations(): Boolean = unused()

    private fun unused(): Nothing = error("Unexpected repository call")
}
