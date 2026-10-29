package com.ujwal.focusgram.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Daily on-device screen time total and Reel consumption count.
 */
@Entity(tableName = "screen_time_records")
data class ScreenTimeRecord(
    @PrimaryKey
    val date: String, // ISO date string: YYYY-MM-DD
    val seconds: Int,
    val reelsSeconds: Int = 0,
    val reelsWatched: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Metadata record for a viewed Reel. Stores only title and author URL;
 * raw video streams are never persisted.
 */
@Entity(tableName = "reels_history")
data class ReelsHistoryItem(
    @PrimaryKey
    val id: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String = "",
    val visitedAt: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val adsWatchedInSession: Int = 0
)

/**
 * Timestamped session boundary recording elapsed usage intervals.
 */
@Entity(tableName = "session_records")
data class SessionRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionType: String, // "INSTAGRAM", "REELS", "ADSTERRA"
    val startTime: Long,
    val endTime: Long = 0,
    val durationSeconds: Int = 0,
    val completed: Boolean = true
)

/**
 * Serialized HTML content and metadata for offline post or thread review.
 */
@Entity(tableName = "saved_snapshots")
data class SnapshotItem(
    @PrimaryKey
    val id: String,
    val url: String,
    val title: String,
    val savedAt: Long = System.currentTimeMillis(),
    val htmlContent: String? = null
)
