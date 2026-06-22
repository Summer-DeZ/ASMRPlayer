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
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val coverUri: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId")],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val title: String,
    val audioUri: String,
    val subtitleUri: String,
    val subtitleTitle: String,
    val durationMs: Long,
    val sortOrder: Int,
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    suspend fun playlists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun playlistFlow(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun playlistById(playlistId: String): PlaylistEntity?

    @Query("SELECT * FROM tracks WHERE playlistId = :playlistId ORDER BY sortOrder ASC, title COLLATE NOCASE ASC")
    suspend fun tracksForPlaylist(playlistId: String): List<TrackEntity>

    @Query("SELECT * FROM tracks ORDER BY playlistId ASC, sortOrder ASC, title COLLATE NOCASE ASC")
    suspend fun tracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks ORDER BY playlistId ASC, sortOrder ASC, title COLLATE NOCASE ASC")
    fun tracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    suspend fun trackById(trackId: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE durationMs <= 0 ORDER BY playlistId ASC, sortOrder ASC")
    suspend fun tracksMissingDuration(): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun playlistCount(): Int

    @Query("SELECT COUNT(*) FROM tracks WHERE playlistId = :playlistId")
    suspend fun trackCount(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE playlistId = :playlistId AND id = :trackId")
    suspend fun deleteTrack(playlistId: String, trackId: String)
}
