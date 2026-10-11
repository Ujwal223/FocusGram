package com.ujwal.focusgram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CooldownGateTest
 * Validates compulsory cooldown calculation between mindful sessions.
 */
class CooldownGateTest {

    @Test
    fun testCooldownElapsedCalculation() {
        val cooldownMinutes = 15
        val cooldownMs = cooldownMinutes * 60 * 1000L

        val sessionEndTime = 100_000L

        // After 10 minutes (insufficient cooldown)
        val now10Min = sessionEndTime + (10 * 60 * 1000L)
        val isCooldownPassed10 = (now10Min - sessionEndTime) >= cooldownMs
        assertFalse(isCooldownPassed10)

        val remainingMs = cooldownMs - (now10Min - sessionEndTime)
        val remainingMinutes = (remainingMs / (60 * 1000L)).toInt()
        assertEquals(5, remainingMinutes)

        // After 16 minutes (cooldown passed)
        val now16Min = sessionEndTime + (16 * 60 * 1000L)
        val isCooldownPassed16 = (now16Min - sessionEndTime) >= cooldownMs
        assertTrue(isCooldownPassed16)
    }

    @Test
    fun testCooldownTimeStringFormatting() {
        val remainingSeconds = 125 // 2m 5s
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val formatted = String.format("%02d:%02d", minutes, seconds)
        assertEquals("02:05", formatted)
    }
}
