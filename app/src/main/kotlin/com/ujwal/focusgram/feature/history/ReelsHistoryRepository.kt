package com.ujwal.focusgram.feature.history

import com.ujwal.focusgram.core.database.ReelsHistoryDao
import com.ujwal.focusgram.core.database.ReelsHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Repository for Room-backed Reels viewing history.
 *
 * Keeps up to [MAX_ENTRIES] records. Only stores metadata (URLs, timestamps, titles);
 * no media is cached or transmitted off-device.
 */
class ReelsHistoryRepository(
    private val reelsHistoryDao: ReelsHistoryDao
) {

    val allReels: Flow<List<ReelsHistoryItem>> = reelsHistoryDao.getAll()

    fun searchReels(query: String): Flow<List<ReelsHistoryItem>> {
        return reelsHistoryDao.search(query)
    }

    suspend fun addReel(
        url: String,
        title: String,
        thumbnailUrl: String = "",
        durationSeconds: Int = 0,
        adsWatchedInSession: Int = 0
    ) {
        if (url.isBlank()) return

        val now = System.currentTimeMillis()
        val currentList = reelsHistoryDao.getAll().first()

        // Deduplicate visits to the same URL within 60 seconds
        val hasRecentDuplicate = currentList.any { item ->
            item.url == url && kotlin.math.abs(now - item.visitedAt) <= 60_000L
        }
        if (hasRecentDuplicate) return

        val id = UUID.randomUUID().toString()
        val item = ReelsHistoryItem(
            id = id,
            url = url,
            title = if (title.isBlank()) "Instagram Reel" else title,
            thumbnailUrl = thumbnailUrl,
            visitedAt = now,
            durationSeconds = durationSeconds,
            adsWatchedInSession = adsWatchedInSession
        )

        reelsHistoryDao.insert(item)
        reelsHistoryDao.trimToLimit(MAX_ENTRIES)
    }

    suspend fun deleteReel(id: String) {
        reelsHistoryDao.deleteById(id)
    }

    suspend fun clearHistory() {
        reelsHistoryDao.clearAll()
    }

    suspend fun getWeeklyAverageReels(): Double {
        val currentList = reelsHistoryDao.getAll().first()
        if (currentList.isEmpty()) return 0.0

        val cutoff = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        val recentCount = currentList.count { it.visitedAt >= cutoff }
        return recentCount / 7.0
    }

    suspend fun getRecentReelCount(days: Int = 7): Int {
        val currentList = reelsHistoryDao.getAll().first()
        val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        return currentList.count { it.visitedAt >= cutoff }
    }

    suspend fun getDailyReelCounts(days: Int = 30): Map<String, Int> {
        val currentList = reelsHistoryDao.getAll().first()
        val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        val recent = currentList.filter { it.visitedAt >= cutoff }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val counts = mutableMapOf<String, Int>()

        for (item in recent) {
            val dayKey = dateFormat.format(Date(item.visitedAt))
            counts[dayKey] = (counts[dayKey] ?: 0) + 1
        }
        return counts
    }

    companion object {
        const val MAX_ENTRIES = 200
    }
}
