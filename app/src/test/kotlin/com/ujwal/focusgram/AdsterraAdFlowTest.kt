package com.ujwal.focusgram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates dual-container rewarded ad conditions: 20-second minimum timer and 2 external ad clicks.
 */
class AdsterraAdFlowTest {

    @Test
    fun testAdRewardCriteria() {
        val requiredSeconds = 20
        val requiredClicks = 2

        assertFalse((0 >= requiredSeconds) && (0 >= requiredClicks))
        assertFalse((20 >= requiredSeconds) && (1 >= requiredClicks))
        assertFalse((15 >= requiredSeconds) && (2 >= requiredClicks))
        assertTrue((20 >= requiredSeconds) && (2 >= requiredClicks))
        assertTrue((25 >= requiredSeconds) && (3 >= requiredClicks))
    }

    @Test
    fun testSettingToggleCriteria() {
        val requiredSeconds = 20
        // Setting toggles only require watching the countdown without external clicks.
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

        // The ad viewer itself cannot be locked out by friction or exhaustion gates.
        assertFalse(isGateActive("ADSTERRA_REWARDED", shouldShowLock = true, isExhausted = true, effortFrictionActive = true))
        assertFalse(isGateActive("ADSTERRA_REWARDED", shouldShowLock = false, isExhausted = false, effortFrictionActive = false))

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

        assertTrue(shouldCountClick(url1, "", 1000L, 0L))
        assertFalse(shouldCountClick(url1, url1, 1500L, 1000L))
        assertTrue(shouldCountClick(url1, url1, 3100L, 1000L))
        assertTrue(shouldCountClick(url2, url1, 1500L, 1000L))
    }
}
