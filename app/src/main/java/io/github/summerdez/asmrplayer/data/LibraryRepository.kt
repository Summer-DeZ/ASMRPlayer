package io.github.summerdez.asmrplayer.data

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
import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

interface LibraryRepository {
    val playlistsFlow: Flow<List<Playlist>>
    val selectedPlaylistIdFlow: Flow<String>

    fun getPlaylists(): List<Playlist>
    fun getPlaylist(playlistId: String?): Playlist?
    fun createPlaylist(name: String?): Playlist
    fun renamePlaylist(playlistId: String?, name: String?)
    fun setPlaylistCover(playlistId: String?, coverUri: String?)
    fun deletePlaylist(playlistId: String?)
    fun addTrack(playlistId: String?, track: TrackItem?)
    fun renameTrack(playlistId: String?, trackId: String?, title: String?)
    fun setTrackSubtitle(playlistId: String?, trackId: String?, subtitleUri: String?, subtitleTitle: String?)
    fun removeTrack(playlistId: String?, trackId: String?)
    fun moveTrack(fromPlaylistId: String?, toPlaylistId: String?, trackId: String?)
    fun refreshMissingTrackDurations(): Boolean
    fun getSelectedPlaylistId(): String
    fun setSelectedPlaylistId(playlistId: String?)
}

class RoomLibraryRepository(
    context: Context,
    private val database: AsrmDatabase,
) : LibraryRepository {
    private val appContext = context.applicationContext
    private val playlistDao = database.playlistDao()
    private val settingsDao = database.appSettingsDao()

    override val playlistsFlow: Flow<List<Playlist>> =
        combine(playlistDao.playlistFlow(), playlistDao.tracksFlow(), ::playlistsWithTracks)

    override val selectedPlaylistIdFlow: Flow<String> =
        settingsDao.valueFlow(KEY_SELECTED_PLAYLIST_ID).map { it.orEmpty() }

    init {
        DbIo.run { ensureDefaultPlaylist() }
    }

    override fun getPlaylists(): List<Playlist> {
        return DbIo.run {
            ensureDefaultPlaylist()
            readPlaylists()
        }
    }

    override fun getPlaylist(playlistId: String?): Playlist? {
        if (playlistId.isNullOrEmpty()) {
            return null
        }
        return DbIo.run {
            val entity = playlistDao.playlistById(playlistId) ?: return@run null
            entity.toPlaylist(playlistDao.tracksForPlaylist(playlistId))
        }
    }

    override fun createPlaylist(name: String?): Playlist {
        return DbIo.run {
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

    override fun renamePlaylist(playlistId: String?, name: String?) {
        val trimmedName = name.orEmpty().trim()
        if (playlistId.isNullOrEmpty() || trimmedName.isEmpty()) {
            return
        }
        DbIo.run {
            val playlist = playlistDao.playlistById(playlistId) ?: return@run
            playlistDao.updatePlaylist(playlist.copy(name = trimmedName))
        }
    }

    override fun setPlaylistCover(playlistId: String?, coverUri: String?) {
        if (playlistId.isNullOrEmpty()) {
            return
        }
        DbIo.run {
            val playlist = playlistDao.playlistById(playlistId) ?: return@run
            playlistDao.updatePlaylist(playlist.copy(coverUri = coverUri.orEmpty()))
        }
    }

    override fun deletePlaylist(playlistId: String?) {
        if (playlistId.isNullOrEmpty()) {
            return
        }
        DbIo.run {
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

    override fun addTrack(playlistId: String?, track: TrackItem?) {
        if (playlistId.isNullOrEmpty() || track == null) {
            return
        }
        DbIo.run {
            playlistDao.playlistById(playlistId) ?: return@run
            playlistDao.insertTrack(track.toEntity(playlistId, playlistDao.trackCount(playlistId)))
        }
    }

    override fun renameTrack(playlistId: String?, trackId: String?, title: String?) {
        val trimmedTitle = title.orEmpty().trim()
        if (playlistId.isNullOrEmpty() || trackId.isNullOrEmpty() || trimmedTitle.isEmpty()) {
            return
        }
        DbIo.run {
            val track = playlistDao.trackById(trackId) ?: return@run
            if (track.playlistId != playlistId) {
                return@run
            }
            playlistDao.updateTrack(track.copy(title = trimmedTitle))
        }
    }

    override fun setTrackSubtitle(playlistId: String?, trackId: String?, subtitleUri: String?, subtitleTitle: String?) {
        if (playlistId.isNullOrEmpty() || trackId.isNullOrEmpty() || subtitleUri.isNullOrEmpty()) {
            return
        }
        DbIo.run {
            val track = playlistDao.trackById(trackId) ?: return@run
            if (track.playlistId != playlistId) {
                return@run
            }
            playlistDao.updateTrack(
                track.copy(
                    subtitleUri = subtitleUri,
                    subtitleTitle = subtitleTitle.orEmpty(),
                ),
            )
        }
    }

    override fun removeTrack(playlistId: String?, trackId: String?) {
        if (playlistId.isNullOrEmpty() || trackId.isNullOrEmpty()) {
            return
        }
        DbIo.run {
            playlistDao.deleteTrack(playlistId, trackId)
        }
    }

    override fun moveTrack(fromPlaylistId: String?, toPlaylistId: String?, trackId: String?) {
        if (fromPlaylistId.isNullOrEmpty()
            || toPlaylistId.isNullOrEmpty()
            || trackId.isNullOrEmpty()
            || fromPlaylistId == toPlaylistId
        ) {
            return
        }
        DbIo.run {
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
            }
        }
    }

    override fun refreshMissingTrackDurations(): Boolean {
        return DbIo.run {
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

    override fun getSelectedPlaylistId(): String {
        return DbIo.run {
            settingsDao.value(KEY_SELECTED_PLAYLIST_ID).orEmpty()
        }
    }

    override fun setSelectedPlaylistId(playlistId: String?) {
        DbIo.run {
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

    private suspend fun readPlaylists(): List<Playlist> {
        return playlistsWithTracks(playlistDao.playlists(), playlistDao.tracks())
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
