package io.github.summerdez.asmrplayer.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import io.github.summerdez.asmrplayer.data.files.DocumentFiles
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.util.UUID

interface LibraryRepository {
    val playlistsFlow: Flow<List<Playlist>>
    val selectedPlaylistIdFlow: Flow<String>

    suspend fun getPlaylist(playlistId: String?): Playlist?
    suspend fun createPlaylist(name: String?): Playlist
    suspend fun renamePlaylist(playlistId: String?, name: String?)
    suspend fun setPlaylistCover(playlistId: String?, coverUri: String?)
    suspend fun deletePlaylist(playlistId: String?)
    suspend fun addTrack(playlistId: String?, track: TrackItem?)
    suspend fun renameTrack(playlistId: String?, trackId: String?, title: String?)
    suspend fun setTrackSubtitle(playlistId: String?, trackId: String?, subtitleUri: String?, subtitleTitle: String?): Boolean
    suspend fun removeTrack(playlistId: String?, trackId: String?)
    suspend fun moveTrack(fromPlaylistId: String?, toPlaylistId: String?, trackId: String?): Boolean
    suspend fun refreshMissingTrackDurations(): Boolean
    suspend fun setSelectedPlaylistId(playlistId: String?)
}

class RoomLibraryRepository(
    context: Context,
    private val database: AsrmDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LibraryRepository {
    private val appContext = context.applicationContext
    private val playlistDao = database.playlistDao()
    private val settingsDao = database.appSettingsDao()

    override val playlistsFlow: Flow<List<Playlist>> =
        combine(playlistDao.playlistFlow(), playlistDao.tracksFlow(), ::playlistsWithTracks)
            .onStart {
                withContext(ioDispatcher) {
                    ensureDefaultPlaylist()
                }
            }

    override val selectedPlaylistIdFlow: Flow<String> =
        settingsDao.valueFlow(KEY_SELECTED_PLAYLIST_ID).map { it.orEmpty() }

    override suspend fun getPlaylist(playlistId: String?): Playlist? {
        if (playlistId.isNullOrEmpty()) {
            return null
        }
        return withContext(ioDispatcher) {
            val entity = playlistDao.playlistById(playlistId) ?: return@withContext null
            entity.toPlaylist(playlistDao.tracksForPlaylist(playlistId))
        }
    }

    override suspend fun createPlaylist(name: String?): Playlist {
        return withContext(ioDispatcher) {
            val normalizedName = name.orEmpty().trim().ifEmpty { DEFAULT_PLAYLIST_NAME }
            val playlist = Playlist(UUID.randomUUID().toString(), normalizedName)
            database.withTransaction {
                playlistDao.insertPlaylist(
                    PlaylistEntity(
                        id = playlist.id,
                        name = playlist.name,
                        coverUri = playlist.coverUri,
                        sortOrder = playlistDao.playlistCount(),
                    ),
                )
                settingsDao.put(AppSettingEntity(KEY_SELECTED_PLAYLIST_ID, playlist.id))
            }
            playlist
        }
    }

    override suspend fun renamePlaylist(playlistId: String?, name: String?) {
        val trimmedName = name.orEmpty().trim()
        if (playlistId.isNullOrEmpty() || trimmedName.isEmpty()) {
            return
        }
        withContext(ioDispatcher) {
            val playlist = playlistDao.playlistById(playlistId) ?: return@withContext
            playlistDao.updatePlaylist(playlist.copy(name = trimmedName))
        }
    }

    override suspend fun setPlaylistCover(playlistId: String?, coverUri: String?) {
        if (playlistId.isNullOrEmpty()) {
            return
        }
        withContext(ioDispatcher) {
            val playlist = playlistDao.playlistById(playlistId) ?: return@withContext
            playlistDao.updatePlaylist(playlist.copy(coverUri = coverUri.orEmpty()))
        }
    }

    override suspend fun deletePlaylist(playlistId: String?) {
        if (playlistId.isNullOrEmpty()) {
            return
        }
        withContext(ioDispatcher) {
            database.withTransaction {
                playlistDao.deletePlaylist(playlistId)
                val playlists = playlistDao.playlists()
                if (playlistId == settingsDao.value(KEY_SELECTED_PLAYLIST_ID).orEmpty()) {
                    settingsDao.put(AppSettingEntity(KEY_SELECTED_PLAYLIST_ID, playlists.firstOrNull()?.id.orEmpty()))
                }
            }
            ensureDefaultPlaylist()
        }
    }

    override suspend fun addTrack(playlistId: String?, track: TrackItem?) {
        if (playlistId.isNullOrEmpty() || track == null) {
            return
        }
        withContext(ioDispatcher) {
            playlistDao.playlistById(playlistId) ?: return@withContext
            playlistDao.insertTrack(track.toEntity(playlistId, playlistDao.trackCount(playlistId)))
        }
    }

    override suspend fun renameTrack(playlistId: String?, trackId: String?, title: String?) {
        val trimmedTitle = title.orEmpty().trim()
        if (playlistId.isNullOrEmpty() || trackId.isNullOrEmpty() || trimmedTitle.isEmpty()) {
            return
        }
        withContext(ioDispatcher) {
            val track = playlistDao.trackById(trackId) ?: return@withContext
            if (track.playlistId != playlistId) {
                return@withContext
            }
            playlistDao.updateTrack(track.copy(title = trimmedTitle))
        }
    }

    override suspend fun setTrackSubtitle(
        playlistId: String?,
        trackId: String?,
        subtitleUri: String?,
        subtitleTitle: String?,
    ): Boolean {
        if (playlistId.isNullOrEmpty() || trackId.isNullOrEmpty() || subtitleUri.isNullOrEmpty()) {
            return false
        }
        return withContext(ioDispatcher) {
            val track = playlistDao.trackById(trackId) ?: return@withContext false
            if (track.playlistId != playlistId) {
                return@withContext false
            }
            playlistDao.updateTrack(
                track.copy(
                    subtitleUri = subtitleUri,
                    subtitleTitle = subtitleTitle.orEmpty(),
                ),
            )
            true
        }
    }

    override suspend fun removeTrack(playlistId: String?, trackId: String?) {
        if (playlistId.isNullOrEmpty() || trackId.isNullOrEmpty()) {
            return
        }
        withContext(ioDispatcher) {
            playlistDao.deleteTrack(playlistId, trackId)
        }
    }

    override suspend fun moveTrack(fromPlaylistId: String?, toPlaylistId: String?, trackId: String?): Boolean {
        if (fromPlaylistId.isNullOrEmpty()
            || toPlaylistId.isNullOrEmpty()
            || trackId.isNullOrEmpty()
            || fromPlaylistId == toPlaylistId
        ) {
            return false
        }
        return withContext(ioDispatcher) {
            var moved = false
            database.withTransaction {
                val track = playlistDao.trackById(trackId) ?: return@withTransaction
                if (track.playlistId != fromPlaylistId || playlistDao.playlistById(toPlaylistId) == null) {
                    return@withTransaction
                }
                playlistDao.updateTrack(
                    track.copy(
                        playlistId = toPlaylistId,
                        sortOrder = playlistDao.trackCount(toPlaylistId),
                    ),
                )
                moved = true
            }
            moved
        }
    }

    override suspend fun refreshMissingTrackDurations(): Boolean {
        return withContext(ioDispatcher) {
            var changed = false
            playlistDao.tracksMissingDuration().forEach { track ->
                val durationMs = DocumentFiles.audioDurationMs(appContext, Uri.parse(track.audioUri))
                if (durationMs > 0L) {
                    playlistDao.updateTrack(track.copy(durationMs = durationMs))
                    changed = true
                }
            }
            changed
        }
    }

    override suspend fun setSelectedPlaylistId(playlistId: String?) {
        withContext(ioDispatcher) {
            settingsDao.put(AppSettingEntity(KEY_SELECTED_PLAYLIST_ID, playlistId.orEmpty()))
        }
    }

    private suspend fun ensureDefaultPlaylist() {
        if (playlistDao.playlistCount() > 0) {
            migrateDefaultPlaylistName()
            return
        }
        val playlist = Playlist(UUID.randomUUID().toString(), DEFAULT_PLAYLIST_NAME)
        playlistDao.insertPlaylist(
            PlaylistEntity(
                id = playlist.id,
                name = playlist.name,
                coverUri = "",
                sortOrder = 0,
            ),
        )
        settingsDao.put(AppSettingEntity(KEY_SELECTED_PLAYLIST_ID, playlist.id))
    }

    private suspend fun migrateDefaultPlaylistName() {
        playlistDao.playlists().forEach { playlist ->
            if (playlist.name == "默认列表" || playlist.name == "默认文件夹") {
                playlistDao.updatePlaylist(playlist.copy(name = DEFAULT_PLAYLIST_NAME))
            }
        }
    }

    private fun playlistsWithTracks(
        playlists: List<PlaylistEntity>,
        tracks: List<TrackEntity>,
    ): List<Playlist> {
        val tracksByPlaylist = tracks.groupBy { it.playlistId }
        return playlists.map { playlist ->
            playlist.toPlaylist(tracksByPlaylist[playlist.id].orEmpty())
        }
    }

    private companion object {
        const val KEY_SELECTED_PLAYLIST_ID = "selected_playlist_id"
        const val DEFAULT_PLAYLIST_NAME = "默认播放列表"
    }
}
