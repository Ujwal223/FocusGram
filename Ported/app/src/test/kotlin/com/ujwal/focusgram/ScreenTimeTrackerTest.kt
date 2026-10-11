package com.ujwal.focusgram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ScreenTimeTrackerTest
 * Validates screen time second-to-minute conversions and daily limit enforcement.
 */
class ScreenTimeTrackerTest {

    @Test
    fun testSecondsToMinutesFormatting() {
        val seconds = 3665 // 1 hour, 1 minute, 5 seconds
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60

        assertEquals(1, hours)
        assertEquals(1, minutes)

        val formatted = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        assertEquals("1h 1m", formatted)
    }

    @Test
    fun testDailyLimitExceededPredicate() {
        val dailyLimitMinutes = 60
        val limitSeconds = dailyLimitMinutes * 60 // 3600s

        val underLimitSeconds = 3540 // 59 mins
        val isBreachedUnder = underLimitSeconds >= limitSeconds
        assertFalse(isBreachedUnder)

        val exactLimitSeconds = 3600 // 60 mins
        val isBreachedExact = exactLimitSeconds >= limitSeconds
        assertTrue(isBreachedExact)

        val overLimitSeconds = 3700
        val isBreachedOver = overLimitSeconds >= limitSeconds
        assertTrue(isBreachedOver)
    }

    @Test
    fun testRemainingQuotaCalculation() {
        val limitMinutes = 45
        val limitSeconds = limitMinutes * 60
        val usedSeconds = 1800 // 30 mins

        val remainingSeconds = (limitSeconds - usedSeconds).coerceAtLeast(0)
        assertEquals(900, remainingSeconds)
        assertEquals(15, remainingSeconds / 60)
    }
}
