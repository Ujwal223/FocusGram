package com.ujwal.focusgram

import com.ujwal.focusgram.feature.wellness.DisciplineMantraRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EffortFrictionGateTest
 * Validates typing discipline challenge mechanics, word counting, and completion criteria.
 */
class EffortFrictionGateTest {

    private val sampleMantra =
        "I choose to control my focus and not let algorithmic feeds govern my attention. Real life happens outside this screen, and my precious time deserves deliberate purpose and unwavering disciplined presence."

    @Test
    fun testSampleMantraWordCount() {
        val words = sampleMantra.trim().split("\\s+".toRegex())
        assertTrue("Mantra should have at least 25 words", words.size >= 25)
    }

    @Test
    fun testWordCountProgressCalculation() {
        val targetCount = 30

        val input10Words = "one two three four five six seven eight nine ten"
        val words10 = input10Words.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        assertEquals(10, words10.size)

        val progress = (words10.size.toFloat() / targetCount).coerceIn(0f, 1f)
        assertEquals(10f / 30f, progress, 0.001f)

        val isTargetReached = words10.size >= targetCount
        assertFalse(isTargetReached)
    }

    @Test
    fun testCompletionCondition() {
        val targetCount = 30
        val fullInput = (1..35).joinToString(" ") { "word$it" }
        val words = fullInput.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }

        assertTrue(words.size >= targetCount)
    }

    @Test
    fun testMantraRepositoryWordCountsAndQuantities() {
        val supportedWordCounts = listOf(10, 15, 20, 30, 50)
        for (count in supportedWordCounts) {
            val list = DisciplineMantraRepository.mantrasByWordCount[count]
            assertTrue("Expected mantras pool for $count words", list != null)
            assertTrue("Expected at least 30 mantras for $count words, found ${list?.size}", (list?.size ?: 0) >= 30)

            list?.forEachIndexed { index, sentence ->
                val words = sentence.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                assertEquals(
                    "Sentence at index $index for word count $count must have exactly $count words: '$sentence'",
                    count,
                    words.size
                )
            }
        }
    }

    @Test
    fun testMantraExhaustionWithoutRepetition() {
        val supportedWordCounts = listOf(10, 15, 20, 30, 50)
        for (count in supportedWordCounts) {
            val totalAvailable = DisciplineMantraRepository.mantrasByWordCount[count]?.size ?: 0
            val retrieved = mutableSetOf<String>()

            // Retrieve all items in the first full pass
            repeat(totalAvailable) {
                val mantra = DisciplineMantraRepository.getNextMantra(count)
                val isNew = retrieved.add(mantra)
                assertTrue("Mantra should not repeat until pool of $totalAvailable is exhausted: '$mantra'", isNew)
            }

            assertEquals(totalAvailable, retrieved.size)
        }
    }
}
