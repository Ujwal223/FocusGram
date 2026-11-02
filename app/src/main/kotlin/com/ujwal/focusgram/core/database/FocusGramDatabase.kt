package com.ujwal.focusgram.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room SQLite database for screen time intervals, reels history, and sessions.
 */
@Database(
    entities = [
        ScreenTimeRecord::class,
        ReelsHistoryItem::class,
        SessionRecord::class
    ],
    version = 4,
    exportSchema = false
)
abstract class FocusGramDatabase : RoomDatabase() {

    abstract fun screenTimeDao(): ScreenTimeDao
    abstract fun reelsHistoryDao(): ReelsHistoryDao
    abstract fun sessionRecordDao(): SessionRecordDao

    companion object {
        private const val DB_NAME = "focusgram_master.db"

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS saved_snapshots")
            }
        }

        @Volatile
        private var instance: FocusGramDatabase? = null

        fun getInstance(context: Context): FocusGramDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusGramDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_3_4)
                    // Drops and recreates tables on version bump if no migration matches; historical tables are ephemeral local caches
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
