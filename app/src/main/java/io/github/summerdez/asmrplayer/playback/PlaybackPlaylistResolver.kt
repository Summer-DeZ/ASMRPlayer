package io.github.summerdez.asmrplayer.playback

import io.github.summerdez.asmrplayer.data.LibraryRepository
import io.github.summerdez.asmrplayer.domain.model.Playlist
import kotlinx.coroutines.CancellationException

sealed interface PlaybackPlaylistResolveResult {
    object None : PlaybackPlaylistResolveResult
    data class Loaded(val playlist: Playlist?) : PlaybackPlaylistResolveResult
    data class Failed(val error: Exception) : PlaybackPlaylistResolveResult
}

class PlaybackPlaylistResolver(
    private val libraryRepository: LibraryRepository,
) {
    suspend fun resolve(playlistId: String): PlaybackPlaylistResolveResult {
        if (playlistId.isEmpty()) {
            return PlaybackPlaylistResolveResult.None
        }
        return try {
            PlaybackPlaylistResolveResult.Loaded(libraryRepository.getPlaylist(playlistId))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            PlaybackPlaylistResolveResult.Failed(error)
        }
    }
}
