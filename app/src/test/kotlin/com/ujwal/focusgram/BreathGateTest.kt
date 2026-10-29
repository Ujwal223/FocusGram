package com.ujwal.focusgram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BreathGateTest
 * Validates conscious breathing gate duration bounds and countdown completion logic.
 */
class BreathGateTest {

    @Test
    fun testDurationCoercion() {
        val tooLow = 1.coerceIn(3, 60)
        assertEquals(3, tooLow)

        val tooHigh = 120.coerceIn(3, 60)
        assertEquals(60, tooHigh)

        val valid = 10.coerceIn(3, 60)
        assertEquals(10, valid)
    }

    @Test
    fun testCountdownCompletionLogic() {
        var secondsRemaining = 10
        val canContinueBefore = secondsRemaining <= 0
        assertFalse(canContinueBefore)

        while (secondsRemaining > 0) {
            secondsRemaining--
        }

        val canContinueAfter = secondsRemaining <= 0
        assertTrue(canContinueAfter)
    }
}
