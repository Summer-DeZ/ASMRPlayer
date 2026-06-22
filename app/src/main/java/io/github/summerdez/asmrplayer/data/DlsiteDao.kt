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
import androidx.room.ForeignKey
import androidx.room.Index
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

@Entity(
    tableName = "dlsite_contents",
    primaryKeys = ["workId", "optionId"],
    foreignKeys = [
        ForeignKey(
            entity = DlsiteWorkEntity::class,
            parentColumns = ["workId"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workId")],
)
data class DlsiteContentEntity(
    val workId: String,
    val optionId: String,
    val title: String,
    val status: String,
    val localPath: String,
    val trackIds: String,
    val trackCount: Int,
    val error: String,
    val updatedAt: Long,
)

@Dao
interface DlsiteDao {
    @Query("SELECT * FROM dlsite_works ORDER BY updatedAt DESC, workId ASC")
    suspend fun works(): List<DlsiteWorkEntity>

    @Query("SELECT * FROM dlsite_works ORDER BY updatedAt DESC, workId ASC")
    fun workFlow(): Flow<List<DlsiteWorkEntity>>

    @Query("SELECT * FROM dlsite_works WHERE workId = :workId LIMIT 1")
    suspend fun workById(workId: String): DlsiteWorkEntity?

    @Query("SELECT * FROM dlsite_contents ORDER BY updatedAt DESC, workId ASC, title COLLATE NOCASE ASC")
    suspend fun contents(): List<DlsiteContentEntity>

    @Query("SELECT * FROM dlsite_contents ORDER BY updatedAt DESC, workId ASC, title COLLATE NOCASE ASC")
    fun contentFlow(): Flow<List<DlsiteContentEntity>>

    @Query("SELECT * FROM dlsite_contents WHERE workId = :workId ORDER BY title COLLATE NOCASE ASC, optionId ASC")
    suspend fun contentsForWork(workId: String): List<DlsiteContentEntity>

    @Query("SELECT * FROM dlsite_contents WHERE workId = :workId AND optionId = :optionId LIMIT 1")
    suspend fun contentById(workId: String, optionId: String): DlsiteContentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(work: DlsiteWorkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(works: List<DlsiteWorkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContent(content: DlsiteContentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContents(contents: List<DlsiteContentEntity>)
}
