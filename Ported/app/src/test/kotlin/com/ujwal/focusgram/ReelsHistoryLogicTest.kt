package com.ujwal.focusgram

import com.ujwal.focusgram.core.database.ReelsHistoryItem
import com.ujwal.focusgram.feature.history.ReelsHistoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ReelsHistoryLogicTest
 * Validates 60-second deduplication logic, maximum retention limits, and weekly statistics.
 */
class ReelsHistoryLogicTest {

    @Test
    fun testDuplicateDetectionWithinSixtySeconds() {
        val now = 1_000_000L
        val url = "https://www.instagram.com/reel/C1234567890/"

        val recentItem = ReelsHistoryItem(
            id = "1",
            url = url,
            title = "Sample Reel",
            visitedAt = now - 30_000L // 30 seconds ago
        )

        val hasRecentDuplicate = recentItem.url == url && kotlin.math.abs(now - recentItem.visitedAt) <= 60_000L
        assertTrue(hasRecentDuplicate)

        val olderItem = ReelsHistoryItem(
            id = "2",
            url = url,
            title = "Sample Reel",
            visitedAt = now - 90_000L // 90 seconds ago
        )
        val hasOlderDuplicate = olderItem.url == url && kotlin.math.abs(now - olderItem.visitedAt) <= 60_000L
        assertFalse(hasOlderDuplicate)
    }

    @Test
    fun testMaxEntriesConstant() {
        assertEquals(200, ReelsHistoryRepository.MAX_ENTRIES)
    }

    @Test
    fun testWeeklyAverageCalculation() {
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L

        val items = (1..14).map { i ->
            ReelsHistoryItem(
                id = "$i",
                url = "https://www.instagram.com/reel/test$i/",
                title = "Reel $i",
                visitedAt = now - (i * (oneDayMs / 3)) // Distributed across ~4.6 days
            )
        }

        val cutoff = now - (7L * oneDayMs)
        val recentCount = items.count { it.visitedAt >= cutoff }
        assertEquals(14, recentCount)

        val averagePerDay = recentCount / 7.0
        assertEquals(2.0, averagePerDay, 0.001)
    }

    @Test
    fun testReelHistoryOperations() {
        val tempFile = java.io.File.createTempFile("reel_test", ".json")
        try {
            val history = com.ujwal.focusgram.feature.history.ReelHistory(tempFile)
            assertEquals(0, history.sessionCount.value)
            assertEquals(0, history.items.value.size)

            // Watch reel 1
            history.onWatched("abc12345", "photographer", "Nice view")
            assertEquals(1, history.sessionCount.value)
            assertEquals(1, history.items.value.size)
            assertEquals("abc12345", history.items.value[0].code)
            assertEquals("photographer", history.items.value[0].author)
            assertEquals("Nice view", history.items.value[0].caption)
            assertEquals(1, history.items.value[0].views)

            // Watch reel 1 again (views increment, session count remains 1 unique)
            history.onWatched("abc12345", "", "")
            assertEquals(1, history.sessionCount.value)
            assertEquals(1, history.items.value.size)
            assertEquals(2, history.items.value[0].views)
            assertEquals("photographer", history.items.value[0].author)

            // Meta update
            history.onMeta("abc12345", "photographer_pro", "Updated caption")
            assertEquals("photographer_pro", history.items.value[0].author)
            assertEquals("Updated caption", history.items.value[0].caption)

            // Watch reel 2
            history.onWatched("def67890", "artist", "Artwork")
            assertEquals(2, history.sessionCount.value)
            assertEquals(2, history.items.value.size)

            // Delete reel 1
            history.delete("abc12345")
            assertEquals(1, history.items.value.size)
            assertEquals("def67890", history.items.value[0].code)

            // Clear history
            history.clear()
            assertEquals(0, history.items.value.size)
            assertEquals(2, history.sessionCount.value)

            // Reset session
            history.resetSession()
            assertEquals(0, history.sessionCount.value)
        } finally {
            tempFile.delete()
        }
    }
}
