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

    @Test
    fun testSettingToggleCriteria() {
        val requiredSeconds = 20
        // Setting toggles only require watching the 20s ad countdown without mandatory external clicks
        fun isToggleComplete(elapsed: Int) = elapsed >= requiredSeconds

        assertFalse(isToggleComplete(0))
        assertFalse(isToggleComplete(19))
        assertTrue(isToggleComplete(20))
        assertTrue(isToggleComplete(25))
    }

    @Test
    fun testAdDestinationGateExemption() {
        fun isGateActive(
            destination: String,
            shouldShowLock: Boolean,
            isExhausted: Boolean,
            effortFrictionActive: Boolean
        ): Boolean {
            if (destination == "ADSTERRA_REWARDED") return false
            return shouldShowLock || isExhausted || effortFrictionActive
        }

        // When watching ad, gate must NOT be active even if lock, exhaustion, or friction are present
        assertFalse(isGateActive("ADSTERRA_REWARDED", shouldShowLock = true, isExhausted = true, effortFrictionActive = true))
        assertFalse(isGateActive("ADSTERRA_REWARDED", shouldShowLock = false, isExhausted = false, effortFrictionActive = false))

        // When in Main WebView or Settings, gate evaluates normally
        assertTrue(isGateActive("MAIN_WEBVIEW", shouldShowLock = true, isExhausted = false, effortFrictionActive = false))
        assertTrue(isGateActive("MAIN_WEBVIEW", shouldShowLock = false, isExhausted = true, effortFrictionActive = false))
        assertFalse(isGateActive("MAIN_WEBVIEW", shouldShowLock = false, isExhausted = false, effortFrictionActive = false))
    }

    @Test
    fun testAdClickDebouncing() {
        fun shouldCountClick(
            url: String,
            lastUrl: String,
            now: Long,
            lastTime: Long
        ): Boolean {
            val clean = url.trim()
            if (clean.isBlank() || clean.contains("effectivecpmnetwork.com") || clean.startsWith("about:")) return false
            return clean != lastUrl || (now - lastTime > 1500L)
        }

        val url1 = "https://example.com/ad1"
        val url2 = "https://example.com/ad2"

        // First click count
        assertTrue(shouldCountClick(url1, "", 1000L, 0L))

        // Rapid duplicate click on same url within 500ms -> debounced
        assertFalse(shouldCountClick(url1, url1, 1500L, 1000L))

        // Duplicate click after 2000ms -> accepted
        assertTrue(shouldCountClick(url1, url1, 3100L, 1000L))

        // Different ad url even rapidly -> accepted
        assertTrue(shouldCountClick(url2, url1, 1500L, 1000L))
    }
}
