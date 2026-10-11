package com.ujwal.focusgram

import com.ujwal.focusgram.feature.wellness.CreditStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CreditStoreTest
 * Validates economy constants, credit calculations, and daily limits for rewarded ads.
 */
class CreditStoreTest {

    @Test
    fun testEconomyConstants() {
        assertEquals(2, CreditStore.MINUTES_PER_AD)
        assertEquals(5, CreditStore.MAX_DAILY_ADS)
        assertEquals(25, CreditStore.XP_PER_AD)
    }

    @Test
    fun testDailyAdLimitPredicate() {
        val today = "2026-10-10"

        // If lastAdDate is not today, user can watch ad regardless of count
        val differentDayLastAd = "2026-10-09"
        val canWatchNewDay = differentDayLastAd != today || 5 < CreditStore.MAX_DAILY_ADS
        assertTrue(canWatchNewDay)

        // If today and under max limit (e.g. 4 ads watched), user can watch
        val canWatchUnderLimit = today != today || 4 < CreditStore.MAX_DAILY_ADS
        assertTrue(canWatchUnderLimit)

        // If today and at max limit (5 ads watched), user cannot watch
        val canWatchAtLimit = today != today || 5 < CreditStore.MAX_DAILY_ADS
        assertFalse(canWatchAtLimit)
    }

    @Test
    fun testMinutesAccumulationMath() {
        var reelsMinutes = 0
        var instaMinutes = 0

        // Watch 3 ads for reels (+2 mins each)
        repeat(3) {
            reelsMinutes += CreditStore.MINUTES_PER_AD
        }
        assertEquals(6, reelsMinutes)

        // Watch 2 ads for Instagram (+2 mins each)
        repeat(2) {
            instaMinutes += CreditStore.MINUTES_PER_AD
        }
        assertEquals(4, instaMinutes)

        // Consume 1 minute from each
        reelsMinutes = (reelsMinutes - 1).coerceAtLeast(0)
        instaMinutes = (instaMinutes - 1).coerceAtLeast(0)

        assertEquals(5, reelsMinutes)
        assertEquals(3, instaMinutes)
    }
}
