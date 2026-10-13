package com.ujwal.focusgram.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ScreenTimeRecord
 * Tracks total on-device Instagram usage seconds per calendar date.
 */
@Entity(tableName = "screen_time_records")
data class ScreenTimeRecord(
    @PrimaryKey
    val date: String, // Format: YYYY-MM-DD
    val seconds: Int,
    val reelsSeconds: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * ReelsHistoryItem
 * Local client-side history of reels viewed by the user.
 * Zero video media downloaded or cached to disk.
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
 * SessionRecord
 * Detailed session records for mindful digital wellness analytics.
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
 * SnapshotItem
 * Saved offline DOM bookmarks and page state for offline viewing.
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
