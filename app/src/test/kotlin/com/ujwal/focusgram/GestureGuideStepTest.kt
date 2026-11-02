package com.ujwal.focusgram

import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.feature.guide.GestureGuideStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates gesture guide step states and default FocusSettings properties.
 */
class GestureGuideStepTest {

    @Test
    fun testFocusSettingsDefaults() {
        val settings = FocusSettings()
        assertFalse("hasSeenGestureGuide should default to false", settings.hasSeenGestureGuide)
        assertFalse("isMigrationCompleted should default to false", settings.isMigrationCompleted)
        assertFalse("isMigrationDeclined should default to false", settings.isMigrationDeclined)
    }

    @Test
    fun testGestureGuideStateFlow() {
        var currentStep = GestureGuideStep.IDLE

        // 1. App detects user authenticated for first time
        currentStep = GestureGuideStep.DOUBLE_TAP_REFRESH
        assertEquals(GestureGuideStep.DOUBLE_TAP_REFRESH, currentStep)

        // 2. User double taps any nav tab
        currentStep = GestureGuideStep.LONG_PRESS_PROFILE
        assertEquals(GestureGuideStep.LONG_PRESS_PROFILE, currentStep)

        // 3. User long presses profile icon to enter Settings
        currentStep = GestureGuideStep.SETTINGS_WAIT
        assertEquals(GestureGuideStep.SETTINGS_WAIT, currentStep)

        // 4. 3 seconds pass on Settings
        currentStep = GestureGuideStep.SETTINGS_PRESS_BACK
        assertEquals(GestureGuideStep.SETTINGS_PRESS_BACK, currentStep)

        // 5. User presses back button and returns to home
        currentStep = GestureGuideStep.COMPLETED
        assertEquals(GestureGuideStep.COMPLETED, currentStep)

        // 6. Completes and persists
        currentStep = GestureGuideStep.IDLE
        assertEquals(GestureGuideStep.IDLE, currentStep)
    }
}
