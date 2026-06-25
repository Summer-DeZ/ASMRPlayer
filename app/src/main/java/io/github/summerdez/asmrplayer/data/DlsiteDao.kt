package io.github.summerdez.asmrplayer.data

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

@Entity(
    tableName = "dlsite_download_queue",
    indices = [
        Index("workId"),
        Index(value = ["status", "queueOrder"]),
    ],
)
data class DlsiteDownloadQueueEntity(
    @PrimaryKey val taskId: String,
    val workId: String,
    val optionIds: String,
    val status: String,
    val queueOrder: Long,
    val createdAt: Long,
    val startedAt: Long?,
    val updatedAt: Long,
    val finishedAt: Long?,
    val errorMessage: String?,
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

    @Query("SELECT * FROM dlsite_download_queue WHERE workId = :workId AND status IN ('pending', 'running') ORDER BY queueOrder ASC, createdAt ASC LIMIT 1")
    suspend fun activeDownloadQueueByWorkId(workId: String): DlsiteDownloadQueueEntity?

    @Query("SELECT * FROM dlsite_download_queue WHERE taskId = :taskId LIMIT 1")
    suspend fun downloadQueueByTaskId(taskId: String): DlsiteDownloadQueueEntity?

    @Query("SELECT * FROM dlsite_download_queue WHERE status = 'pending' ORDER BY queueOrder ASC, createdAt ASC LIMIT :limit")
    suspend fun pendingDownloadQueue(limit: Int): List<DlsiteDownloadQueueEntity>

    @Query("SELECT * FROM dlsite_download_queue WHERE status IN ('pending', 'running') ORDER BY queueOrder ASC, createdAt ASC")
    suspend fun activeDownloadQueue(): List<DlsiteDownloadQueueEntity>

    @Query("SELECT COALESCE(MAX(queueOrder), 0) + 1 FROM dlsite_download_queue")
    suspend fun nextDownloadQueueOrder(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(work: DlsiteWorkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(works: List<DlsiteWorkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContent(content: DlsiteContentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContents(contents: List<DlsiteContentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownloadQueue(task: DlsiteDownloadQueueEntity)

    @Query("UPDATE dlsite_download_queue SET status = 'pending', startedAt = NULL, updatedAt = :now, finishedAt = NULL, errorMessage = NULL WHERE status = 'running'")
    suspend fun resetRunningDownloadQueue(now: Long): Int

    @Query("UPDATE dlsite_download_queue SET status = 'running', startedAt = :now, updatedAt = :now, finishedAt = NULL, errorMessage = NULL WHERE taskId = :taskId AND status = 'pending'")
    suspend fun markDownloadQueueRunning(taskId: String, now: Long): Int

    @Query("UPDATE dlsite_download_queue SET status = :status, updatedAt = :now, finishedAt = :now, errorMessage = :errorMessage WHERE taskId = :taskId")
    suspend fun finishDownloadQueue(taskId: String, status: String, now: Long, errorMessage: String?): Int

    @Query("UPDATE dlsite_download_queue SET status = 'pending', startedAt = NULL, updatedAt = :now, finishedAt = NULL, errorMessage = NULL WHERE taskId = :taskId")
    suspend fun markDownloadQueuePending(taskId: String, now: Long): Int
}
