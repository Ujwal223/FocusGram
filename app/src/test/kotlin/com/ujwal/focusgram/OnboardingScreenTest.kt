package com.ujwal.focusgram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * OnboardingScreenTest
 * Validates Onboarding screen implementation integrity, resource safety,
 * and page layout structure.
 */
class OnboardingScreenTest {

    private fun getOnboardingSource(): String {
        val file = File("src/main/kotlin/com/ujwal/focusgram/feature/onboarding/OnboardingScreen.kt")
        if (file.exists()) return file.readText()
        val altFile = File("app/src/main/kotlin/com/ujwal/focusgram/feature/onboarding/OnboardingScreen.kt")
        if (altFile.exists()) return altFile.readText()
        error("Could not find OnboardingScreen.kt at ${file.absolutePath} or ${altFile.absolutePath}")
    }

    @Test
    fun testNoAdaptiveIconInPainterResource() {
        val source = getOnboardingSource()
        // Compose painterResource throws IllegalArgumentException when passed XML adaptive icons.
        // It must NOT be called on R.mipmap.ic_launcher.
        assertFalse(
            "OnboardingScreen must not pass R.mipmap.ic_launcher to painterResource (causes crash on adaptive icon XML)",
            source.contains("painterResource(id = R.mipmap.ic_launcher)") ||
                source.contains("painterResource(R.mipmap.ic_launcher)")
        )
        assertTrue(
            "OnboardingScreen must use safe rasterization via AppIconManager.getIconBitmap",
            source.contains("AppIconManager.getIconBitmap(context, R.mipmap.ic_launcher)")
        )
    }

    @Test
    fun testOnboardingPageStructure() {
        val source = getOnboardingSource()
        assertTrue("Total pages must be 6", source.contains("val totalPages = 6"))
        assertTrue("WelcomeSlide must be page 0", source.contains("0 -> WelcomeSlide()"))
        assertTrue("LoginSecuritySlide must be last page (5)", source.contains("5 -> LoginSecuritySlide()"))
    }
}
