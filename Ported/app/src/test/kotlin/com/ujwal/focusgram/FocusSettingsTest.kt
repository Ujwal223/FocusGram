package com.ujwal.focusgram

import com.ujwal.focusgram.core.model.FocusSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FocusSettingsTest
 * Validates default digital wellness configurations and immutability.
 */
class FocusSettingsTest {

    @Test
    fun testDefaultSettingsIntegrity() {
        val settings = FocusSettings()

        // Ads should default to true (blocked by uBlock)
        assertTrue(settings.noAds)
        // Video autoplay default blocked
        assertTrue(settings.noAutoplay)
        // Explore blurred by default, Feed and Reels unblurred by default
        assertTrue(settings.blurExplore)
        assertFalse(settings.blurReels)
        assertFalse(settings.blurFeed)
        assertEquals("tap", settings.unblurMode)
        assertTrue(settings.tapToUnblur)
        assertFalse(settings.requireLongPress)
        // Suggested posts false by default to prevent feed blanking after ~9 posts
        assertFalse(settings.hideSuggestedPosts)
        // Breathing gate enabled by default
        assertTrue(settings.showBreathGate)
        assertEquals(10, settings.breathGateSeconds)
        // Block Reels swiping in DMs enabled by default
        assertTrue(settings.blockDmReelsSwiping)
        // Initial user level
        assertEquals(1, settings.userLevel)
        assertEquals(0, settings.userXP)
        // Portrait only enabled by default
        assertTrue(settings.portraitOnly)
    }

    @Test
    fun testMinimalModeCopy() {
        val base = FocusSettings()
        assertFalse(base.minimalModeEnabled)

        val updated = base.copy(minimalModeEnabled = true, noStories = true, noReels = true)
        assertTrue(updated.minimalModeEnabled)
        assertTrue(updated.noStories)
        assertTrue(updated.noReels)
    }
}
