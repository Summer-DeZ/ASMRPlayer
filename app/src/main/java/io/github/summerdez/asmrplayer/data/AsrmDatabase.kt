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
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        TrackEntity::class,
        DlsiteWorkEntity::class,
        DlsiteContentEntity::class,
        DlsiteDownloadQueueEntity::class,
        AppSettingEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AsrmDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun dlsiteDao(): DlsiteDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        @Volatile
        private var instance: AsrmDatabase? = null

        fun get(context: Context): AsrmDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AsrmDatabase::class.java,
                    "asrmplayer.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dlsite_works ADD COLUMN coverUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dlsite_works ADD COLUMN coverUri TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dlsite_contents (
                        workId TEXT NOT NULL,
                        optionId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        status TEXT NOT NULL,
                        localPath TEXT NOT NULL,
                        trackIds TEXT NOT NULL,
                        trackCount INTEGER NOT NULL,
                        error TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(workId, optionId),
                        FOREIGN KEY(workId) REFERENCES dlsite_works(workId) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dlsite_contents_workId ON dlsite_contents(workId)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dlsite_download_queue (
                        taskId TEXT NOT NULL,
                        workId TEXT NOT NULL,
                        optionIds TEXT NOT NULL,
                        status TEXT NOT NULL,
                        queueOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        startedAt INTEGER,
                        updatedAt INTEGER NOT NULL,
                        finishedAt INTEGER,
                        errorMessage TEXT,
                        PRIMARY KEY(taskId)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dlsite_download_queue_workId ON dlsite_download_queue(workId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dlsite_download_queue_status_queueOrder ON dlsite_download_queue(status, queueOrder)")
            }
        }
    }
}
