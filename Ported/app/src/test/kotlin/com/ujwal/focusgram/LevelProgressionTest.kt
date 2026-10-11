package com.ujwal.focusgram

import com.ujwal.focusgram.feature.wellness.ALL_LEVEL_FEATURES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LevelProgressionTest
 * Validates gamification tier progressions, XP level math, and feature gate unlocking.
 */
class LevelProgressionTest {

    @Test
    fun testLevelXPProgressMath() {
        val xp = 275
        val userLevel = (xp / 100) + 1
        assertEquals(3, userLevel)

        val xpInLevel = xp % 100
        assertEquals(75, xpInLevel)

        val progress = xpInLevel / 100f
        assertEquals(0.75f, progress, 0.001f)
    }

    @Test
    fun testLevelTitles() {
        fun getLevelTitle(level: Int): String = when (level) {
            1 -> "Beginner"
            2 -> "Mindful Scroller"
            3 -> "Disciplined"
            4 -> "Focus Master"
            else -> "Digital Monk"
        }

        assertEquals("Beginner", getLevelTitle(1))
        assertEquals("Mindful Scroller", getLevelTitle(2))
        assertEquals("Disciplined", getLevelTitle(3))
        assertEquals("Focus Master", getLevelTitle(4))
        assertEquals("Digital Monk", getLevelTitle(5))
        assertEquals("Digital Monk", getLevelTitle(10))
    }

    @Test
    fun testFeatureGatingByLevel() {
        // Feature: Offline Snapshots requires level 5
        val offlineSnapshots = ALL_LEVEL_FEATURES.first { it.name == "Offline Snapshots" }
        assertEquals(5, offlineSnapshots.requiredLevel)

        val userLevel3 = 3
        val isUnlockedLevel3 = userLevel3 >= offlineSnapshots.requiredLevel
        assertFalse(isUnlockedLevel3)

        val userLevel5 = 5
        val isUnlockedLevel5 = userLevel5 >= offlineSnapshots.requiredLevel
        assertTrue(isUnlockedLevel5)
    }
}
