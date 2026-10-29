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

        assertTrue(settings.noAds)
        assertTrue(settings.noAutoplay)
        assertTrue(settings.blurExplore)
        assertFalse(settings.blurReels)
        assertFalse(settings.blurFeed)
        assertEquals("tap", settings.unblurMode)
        assertTrue(settings.tapToUnblur)
        assertFalse(settings.requireLongPress)
        // Hiding suggested posts blanks the feed after ~9 items; keep disabled by default.
        assertFalse(settings.hideSuggestedPosts)
        assertTrue(settings.showBreathGate)
        assertEquals(10, settings.breathGateSeconds)
        assertTrue(settings.blockDmReelsSwiping)
        assertEquals(5, settings.cooldownMinutes)
        assertTrue(settings.showSessionTimeInBottomBar)
        assertTrue(settings.portraitOnly)
        assertTrue(settings.notifyPersistent)
        assertTrue(settings.notifyEvery5Minutes)
        assertTrue(settings.sanitizeLinks)
        assertFalse("Ghost Mode main toggle must default to OFF", settings.ghostModeEnabled)
        assertTrue(settings.ghostHideStorySeen)
        assertTrue(settings.ghostHideDirectSeen)
        assertTrue(settings.ghostHideTypingIndicator)
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
