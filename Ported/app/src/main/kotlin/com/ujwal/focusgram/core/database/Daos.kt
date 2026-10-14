package com.ujwal.focusgram.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenTimeDao {
    @Query("SELECT * FROM screen_time_records WHERE date = :date LIMIT 1")
    fun getForDate(date: String): Flow<ScreenTimeRecord?>

    @Query("SELECT * FROM screen_time_records WHERE date = :date LIMIT 1")
    suspend fun getForDateSync(date: String): ScreenTimeRecord?

    @Query("SELECT * FROM screen_time_records ORDER BY date DESC")
    fun getAll(): Flow<List<ScreenTimeRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: ScreenTimeRecord)

    @Query("UPDATE screen_time_records SET seconds = seconds + :deltaSeconds, reelsSeconds = reelsSeconds + :deltaReelsSeconds, lastUpdated = :timestamp WHERE date = :date")
    suspend fun incrementSeconds(date: String, deltaSeconds: Int, deltaReelsSeconds: Int = 0, timestamp: Long = System.currentTimeMillis())

    suspend fun addSeconds(date: String, deltaSeconds: Int, deltaReelsSeconds: Int = 0) {
        val existing = getForDateSync(date)
        if (existing == null) {
            insertOrUpdate(ScreenTimeRecord(date = date, seconds = deltaSeconds, reelsSeconds = deltaReelsSeconds))
        } else {
            incrementSeconds(date = date, deltaSeconds = deltaSeconds, deltaReelsSeconds = deltaReelsSeconds)
        }
    }

    @Query("UPDATE screen_time_records SET reelsWatched = reelsWatched + :delta, lastUpdated = :timestamp WHERE date = :date")
    suspend fun incrementReelsWatched(date: String, delta: Int = 1, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE screen_time_records SET reelsWatched = :count, lastUpdated = :timestamp WHERE date = :date")
    suspend fun setReelsWatched(date: String, count: Int, timestamp: Long = System.currentTimeMillis())

    suspend fun recordReelWatched(date: String) {
        val existing = getForDateSync(date)
        if (existing == null) {
            insertOrUpdate(ScreenTimeRecord(date = date, seconds = 0, reelsSeconds = 0, reelsWatched = 1))
        } else {
            incrementReelsWatched(date = date, delta = 1)
        }
    }

    suspend fun syncReelsWatched(date: String, count: Int) {
        val existing = getForDateSync(date)
        if (existing == null) {
            insertOrUpdate(ScreenTimeRecord(date = date, seconds = 0, reelsSeconds = 0, reelsWatched = count))
        } else {
            setReelsWatched(date = date, count = count)
        }
    }

    @Query("DELETE FROM screen_time_records WHERE date < :cutoffDate")
    suspend fun pruneOlderThan(cutoffDate: String)
}

@Dao
interface ReelsHistoryDao {
    @Query("SELECT * FROM reels_history ORDER BY visitedAt DESC")
    fun getAll(): Flow<List<ReelsHistoryItem>>

    @Query("SELECT * FROM reels_history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY visitedAt DESC")
    fun search(query: String): Flow<List<ReelsHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ReelsHistoryItem)

    @Query("DELETE FROM reels_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM reels_history")
    suspend fun clearAll()

    @Query("DELETE FROM reels_history WHERE id NOT IN (SELECT id FROM reels_history ORDER BY visitedAt DESC LIMIT :limit)")
    suspend fun trimToLimit(limit: Int = 200)
}

@Dao
interface SessionRecordDao {
    @Query("SELECT * FROM session_records ORDER BY startTime DESC")
    fun getAll(): Flow<List<SessionRecord>>

    @Query("SELECT * FROM session_records ORDER BY startTime DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<SessionRecord>>

    @Insert
    suspend fun insert(record: SessionRecord): Long

    @Update
    suspend fun update(record: SessionRecord)
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM saved_snapshots ORDER BY savedAt DESC")
    fun getAll(): Flow<List<SnapshotItem>>

    @Query("SELECT * FROM saved_snapshots WHERE id = :id LIMIT 1")
    fun getById(id: String): Flow<SnapshotItem?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SnapshotItem)

    @Query("DELETE FROM saved_snapshots WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM saved_snapshots")
    suspend fun clearAll()
}
