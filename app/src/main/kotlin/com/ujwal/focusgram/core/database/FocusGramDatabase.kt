package com.ujwal.focusgram.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room SQLite database for screen time intervals, reels history, and snapshots.
 */
@Database(
    entities = [
        ScreenTimeRecord::class,
        ReelsHistoryItem::class,
        SessionRecord::class,
        SnapshotItem::class
    ],
    version = 3,
    exportSchema = false
)
abstract class FocusGramDatabase : RoomDatabase() {

    abstract fun screenTimeDao(): ScreenTimeDao
    abstract fun reelsHistoryDao(): ReelsHistoryDao
    abstract fun sessionRecordDao(): SessionRecordDao
    abstract fun snapshotDao(): SnapshotDao

    companion object {
        private const val DB_NAME = "focusgram_master.db"

        @Volatile
        private var instance: FocusGramDatabase? = null

        fun getInstance(context: Context): FocusGramDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusGramDatabase::class.java,
                    DB_NAME
                )
                    // Drops and recreates tables on version bump; historical tables are ephemeral local caches
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
