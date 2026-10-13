package com.ujwal.focusgram.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * FocusGramDatabase
 * 100% on-device SQLite database managed by Room.
 * Stores screen time metrics, reels history, focus sessions, and saved offline snapshots.
 */
@Database(
    entities = [
        ScreenTimeRecord::class,
        ReelsHistoryItem::class,
        SessionRecord::class,
        SnapshotItem::class
    ],
    version = 2,
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
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
