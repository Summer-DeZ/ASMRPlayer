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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "dlsite_works")
data class DlsiteWorkEntity(
    @PrimaryKey val workId: String,
    val title: String,
    val detailUrl: String,
    val downloadUrl: String,
    val coverUrl: String,
    val coverUri: String,
    val status: String,
    val playlistId: String,
    val localPath: String,
    val error: String,
    val downloadOptionId: String,
    val downloadOptionTitle: String,
    val updatedAt: Long,
    val trackCount: Int,
)

@Dao
interface DlsiteDao {
    @Query("SELECT * FROM dlsite_works ORDER BY updatedAt DESC, workId ASC")
    suspend fun works(): List<DlsiteWorkEntity>

    @Query("SELECT * FROM dlsite_works ORDER BY updatedAt DESC, workId ASC")
    fun workFlow(): Flow<List<DlsiteWorkEntity>>

    @Query("SELECT * FROM dlsite_works WHERE workId = :workId LIMIT 1")
    suspend fun workById(workId: String): DlsiteWorkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(work: DlsiteWorkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(works: List<DlsiteWorkEntity>)
}
