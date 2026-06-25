package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.data.LibraryRepository
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import io.github.summerdez.asmrplayer.playback.PlaybackPlaylistResolveResult
import io.github.summerdez.asmrplayer.playback.PlaybackPlaylistResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaybackPlaylistResolverTest {
    @Test
    fun emptyPlaylistIdDoesNotCallRepository() = runBlocking {
        val repository = FakeLibraryRepository()
        val resolver = PlaybackPlaylistResolver(repository)

        val result = resolver.resolve("")

        assertSame(PlaybackPlaylistResolveResult.None, result)
        assertEquals(0, repository.getPlaylistCallCount)
    }

    @Test
    fun returnsLoadedPlaylistFromRepository() = runBlocking {
        val playlist = Playlist("playlist-1", "One")
        val repository = FakeLibraryRepository(playlist = playlist)
        val resolver = PlaybackPlaylistResolver(repository)

        val result = resolver.resolve("playlist-1")

        result as PlaybackPlaylistResolveResult.Loaded
        assertSame(playlist, result.playlist)
        assertEquals(listOf("playlist-1"), repository.requestedPlaylistIds)
    }

    @Test
    fun ordinaryExceptionReturnsFailedResult() = runBlocking {
        val failure = IllegalStateException("db unavailable")
        val repository = FakeLibraryRepository(failure = failure)
        val resolver = PlaybackPlaylistResolver(repository)

        val result = resolver.resolve("playlist-1")

        result as PlaybackPlaylistResolveResult.Failed
        assertSame(failure, result.error)
        assertEquals(listOf("playlist-1"), repository.requestedPlaylistIds)
    }

    @Test
    fun cancellationExceptionIsRethrown() {
        val cancellation = CancellationException("stale request")
        val repository = FakeLibraryRepository(failure = cancellation)
        val resolver = PlaybackPlaylistResolver(repository)

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                resolver.resolve("playlist-1")
            }
        }

        assertSame(cancellation, thrown)
        assertEquals(listOf("playlist-1"), repository.requestedPlaylistIds)
    }

    private class FakeLibraryRepository(
        private val playlist: Playlist? = null,
        private val failure: Exception? = null,
    ) : LibraryRepository {
        override val playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
        override val selectedPlaylistIdFlow = MutableStateFlow("")
        val requestedPlaylistIds = mutableListOf<String>()
        var getPlaylistCallCount = 0
            private set

        override suspend fun getPlaylist(playlistId: String): Playlist? {
            getPlaylistCallCount += 1
            requestedPlaylistIds += playlistId
            failure?.let { throw it }
            return playlist
        }

        override suspend fun createPlaylist(name: String): Playlist = unused()
        override suspend fun renamePlaylist(playlistId: String, name: String) = unused()
        override suspend fun setPlaylistCover(playlistId: String, coverUri: String) = unused()
        override suspend fun deletePlaylist(playlistId: String) = unused()
        override suspend fun addTrack(playlistId: String, track: TrackItem) = unused()
        override suspend fun renameTrack(playlistId: String, trackId: String, title: String) = unused()
        override suspend fun setTrackSubtitle(
            playlistId: String,
            trackId: String,
            subtitleUri: String,
            subtitleTitle: String,
        ): Boolean = unused()
        override suspend fun removeTrack(playlistId: String, trackId: String) = unused()
        override suspend fun moveTrack(fromPlaylistId: String, toPlaylistId: String, trackId: String): Boolean =
            unused()
        override suspend fun refreshMissingTrackDurations(): Boolean = unused()
        override suspend fun setSelectedPlaylistId(playlistId: String) = unused()

        private fun unused(): Nothing = error("Unexpected repository call")
    }
}
