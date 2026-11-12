package com.ujwal.focusgram

import com.ujwal.focusgram.core.model.FocusSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Developer Options and Ad Reward disabling contracts.
 */
class DeveloperOptionsTest {

    @Test
    fun testDeveloperOptionsDefaultState() {
        val settings = FocusSettings()
        assertFalse("developerOptionsEnabled should default to false", settings.developerOptionsEnabled)
        assertFalse("enableDebugMenu should default to false", settings.enableDebugMenu)
        assertFalse("hideSupportBanner should default to false", settings.hideSupportBanner)
        assertFalse("disableAutoUpdateCheck should default to false", settings.disableAutoUpdateCheck)
        assertTrue("adRewardsEnabled should default to true", settings.adRewardsEnabled)
    }

    @Test
    fun testDeveloperOptionsMutation() {
        val settings = FocusSettings()
        val updated = settings.copy(
            developerOptionsEnabled = true,
            enableDebugMenu = true,
            hideSupportBanner = true,
            disableAutoUpdateCheck = true,
            adRewardsEnabled = false
        )

        assertTrue(updated.developerOptionsEnabled)
        assertTrue(updated.enableDebugMenu)
        assertTrue(updated.hideSupportBanner)
        assertTrue(updated.disableAutoUpdateCheck)
        assertFalse(updated.adRewardsEnabled)
    }

    @Test
    fun testAdDisablingSuppressesAdExtensions() {
        val disabledSettings = FocusSettings(adRewardsEnabled = false)
        assertFalse("Ad rewards must be disabled when toggled off", disabledSettings.adRewardsEnabled)
    }
}
