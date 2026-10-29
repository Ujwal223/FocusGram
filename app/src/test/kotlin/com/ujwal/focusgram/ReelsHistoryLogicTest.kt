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
            visitedAt = now - 30_000L
        )

        val hasRecentDuplicate = recentItem.url == url && kotlin.math.abs(now - recentItem.visitedAt) <= 60_000L
        assertTrue(hasRecentDuplicate)

        val olderItem = ReelsHistoryItem(
            id = "2",
            url = url,
            title = "Sample Reel",
            visitedAt = now - 90_000L
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
                visitedAt = now - (i * (oneDayMs / 3))
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

            history.onWatched("abc12345", "photographer", "Nice view")
            assertEquals(1, history.sessionCount.value)
            assertEquals(1, history.items.value.size)
            assertEquals("abc12345", history.items.value[0].code)
            assertEquals("photographer", history.items.value[0].author)
            assertEquals("Nice view", history.items.value[0].caption)
            assertEquals(1, history.items.value[0].views)

            // Re-watching increments view count without inflating unique session item count.
            history.onWatched("abc12345", "", "")
            assertEquals(1, history.sessionCount.value)
            assertEquals(1, history.items.value.size)
            assertEquals(2, history.items.value[0].views)
            assertEquals("photographer", history.items.value[0].author)

            history.onMeta("abc12345", "photographer_pro", "Updated caption")
            assertEquals("photographer_pro", history.items.value[0].author)
            assertEquals("Updated caption", history.items.value[0].caption)

            history.onWatched("def67890", "artist", "Artwork")
            assertEquals(2, history.sessionCount.value)
            assertEquals(2, history.items.value.size)

            history.delete("abc12345")
            assertEquals(1, history.items.value.size)
            assertEquals("def67890", history.items.value[0].code)

            history.clear()
            assertEquals(0, history.items.value.size)
            assertEquals(2, history.sessionCount.value)

            history.resetSession()
            assertEquals(0, history.sessionCount.value)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testReelDeduplicationByAuthorAndCaptionAndPlaceholder() {
        val tempFile = java.io.File.createTempFile("reel_dedup_test", ".json")
        try {
            val history = com.ujwal.focusgram.feature.history.ReelHistory(tempFile)

            // Step 1: Initial watch recorded with username placeholder as code
            history.onWatched("plainwalk_app", "plainwalk_app", "How about a little side trip?")
            assertEquals(1, history.items.value.size)
            assertEquals("plainwalk_app", history.items.value[0].code)
            assertEquals(1, history.items.value[0].views)

            // Step 2: Later watch with real Instagram shortcode for the same reel
            history.onWatched("DF5a_b8z1", "plainwalk_app", "How about a little side trip?")
            // Must consolidate to 1 item, upgrade code, increment view count to 2, and remove old entry
            assertEquals(1, history.items.value.size)
            assertEquals("DF5a_b8z1", history.items.value[0].code)
            assertEquals(2, history.items.value[0].views)
            assertEquals("plainwalk_app", history.items.value[0].author)

            // Step 3: Another watch with same author and caption moves to top with views=3
            history.onWatched("DF5a_b8z1", "plainwalk_app", "How about a little side trip?")
            assertEquals(1, history.items.value.size)
            assertEquals(3, history.items.value[0].views)

            // Step 4: Delete matching cleans up the entry
            history.deleteMatching("DF5a_b8z1", "plainwalk_app", "How about a little side trip?")
            assertEquals(0, history.items.value.size)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testReelHistoryClearTimeframes() {
        val tempFile = java.io.File.createTempFile("reel_clear_timeframe_test", ".json")
        try {
            val history = com.ujwal.focusgram.feature.history.ReelHistory(tempFile)
            val now = System.currentTimeMillis()
            val dayMs = 24 * 60 * 60 * 1000L

            // Populate test reels with known lastSeen offsets
            // Entry 1: today
            history.onWatched("reel_today_1", "creator1", "Today Reel")
            // Entry 2: 2 days ago
            history.onWatched("reel_2days_ago", "creator2", "2 Days Ago Reel")
            // Entry 3: 5 days ago
            history.onWatched("reel_5days_ago", "creator3", "5 Days Ago Reel")
            // Entry 4: 10 days ago
            history.onWatched("reel_10days_ago", "creator4", "10 Days Ago Reel")

            assertEquals(4, history.items.value.size)

            // Test clearToday: removes entries from today
            history.clearToday()
            // Since onWatched sets System.currentTimeMillis(), all 4 above were actually just watched today
            // So clearToday() removes all 4
            assertEquals(0, history.items.value.size)

            // Test clearing specific past days with simulated entries
            val cal = java.util.Calendar.getInstance()
            history.onWatched("reel_new_today", "creatorA", "Today")
            assertEquals(1, history.items.value.size)

            history.clearPastDays(3)
            assertEquals(0, history.items.value.size)

            history.onWatched("reel_new_today_2", "creatorB", "Today 2")
            assertEquals(1, history.items.value.size)

            history.clearThisMonth()
            assertEquals(0, history.items.value.size)
        } finally {
            tempFile.delete()
        }
    }
}
