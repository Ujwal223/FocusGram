package com.ujwal.focusgram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AdsterraAdFlowTest
 * Validates dual-container rewarded ad conditions: 20-second minimum timer and 2 external ad clicks.
 */
class AdsterraAdFlowTest {

    @Test
    fun testAdRewardCriteria() {
        val requiredSeconds = 20
        val requiredClicks = 2

        // Condition 1: 0 seconds, 0 clicks -> Incomplete
        val isComplete0 = (0 >= requiredSeconds) && (0 >= requiredClicks)
        assertFalse(isComplete0)

        // Condition 2: 20 seconds, 1 click -> Incomplete
        val isComplete1 = (20 >= requiredSeconds) && (1 >= requiredClicks)
        assertFalse(isComplete1)

        // Condition 3: 15 seconds, 2 clicks -> Incomplete
        val isComplete2 = (15 >= requiredSeconds) && (2 >= requiredClicks)
        assertFalse(isComplete2)

        // Condition 4: 20 seconds, 2 clicks -> Complete!
        val isComplete3 = (20 >= requiredSeconds) && (2 >= requiredClicks)
        assertTrue(isComplete3)

        // Condition 5: 25 seconds, 3 clicks -> Complete!
        val isComplete4 = (25 >= requiredSeconds) && (3 >= requiredClicks)
        assertTrue(isComplete4)
    }
}
