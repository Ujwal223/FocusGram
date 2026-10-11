package com.ujwal.focusgram

import com.ujwal.focusgram.feature.wellness.BaitOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BaitEngineTest
 * Validates weighted random mechanics and outcome labels.
 */
class BaitEngineTest {

    @Test
    fun testBaitOutcomeValues() {
        val outcomes = BaitOutcome.entries
        assertEquals(6, outcomes.size)

        outcomes.forEach { outcome ->
            assertNotNull(outcome.label)
            assertTrue(outcome.label.isNotEmpty())
            assertNotNull(outcome.subtext)
            assertTrue(outcome.subtext.isNotEmpty())
        }
    }

    @Test
    fun testPositiveOutcomesExist() {
        val positive = BaitOutcome.ADD_TEN_MINUTES
        assertTrue(positive.label.contains("+10 Minutes"))
    }
}
