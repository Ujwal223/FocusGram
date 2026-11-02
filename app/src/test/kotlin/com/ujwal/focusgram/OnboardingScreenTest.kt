package com.ujwal.focusgram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates OnboardingScreen flow, page count, and routing to Instagram login.
 */
class OnboardingScreenTest {

    private fun getOnboardingSource(): String {
        val direct = File("src/main/kotlin/com/ujwal/focusgram/feature/onboarding/OnboardingScreen.kt")
        val nested = File("app/src/main/kotlin/com/ujwal/focusgram/feature/onboarding/OnboardingScreen.kt")
        return when {
            direct.exists() -> direct.readText()
            nested.exists() -> nested.readText()
            else -> throw IllegalStateException("Could not find OnboardingScreen.kt")
        }
    }

    @Test
    fun testOnboardingPageStructure() {
        val source = getOnboardingSource()
        assertTrue("Total pages must be 6", source.contains("val totalPages = 6"))
        assertTrue("WelcomeSlide must be page 0", source.contains("0 -> WelcomeSlide()"))
        assertTrue("LoginSecuritySlide must be last page (5)", source.contains("5 -> LoginSecuritySlide()"))
    }

    @Test
    fun testOnboardingCompletionRoutesUsersToLogin() {
        val onboardingSource = getOnboardingSource()
        assertTrue(
            "OnboardingScreen onComplete must be a clean callback",
            onboardingSource.contains("onComplete: () -> Unit")
        )

        val appFile = File("src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt").let {
            if (it.exists()) it else File("app/src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt")
        }
        val appSource = appFile.readText()

        assertTrue(
            "FocusGramApp must route onboarding users to login with ?mtn",
            appSource.contains("\"https://www.instagram.com/accounts/login/?mtn\"")
        )
    }

    @Test
    fun testUpdateNoticeScreenPresentsReloginNoticeAndOkButton() {
        val noticeFile = File("src/main/kotlin/com/ujwal/focusgram/feature/update/UpdateNoticeScreen.kt").let {
            if (it.exists()) it else File("app/src/main/kotlin/com/ujwal/focusgram/feature/update/UpdateNoticeScreen.kt")
        }
        val noticeSource = noticeFile.readText()

        assertTrue(
            "UpdateNoticeScreen must state app is shifted to new infra",
            noticeSource.contains("App shifted to new infra")
        )
        assertTrue(
            "UpdateNoticeScreen must explain why login could not be ported",
            noticeSource.contains("Focusgram doesnt have access to your data")
        )
        assertTrue(
            "UpdateNoticeScreen must include 'Ok, fine' button",
            noticeSource.contains("\"Ok, fine\"")
        )

        val appFile = File("src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt").let {
            if (it.exists()) it else File("app/src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt")
        }
        val appSource = appFile.readText()
        assertTrue(
            "FocusGramApp must display UpdateNoticeScreen when update notice is pending",
            appSource.contains("isUpdateNoticePending") && appSource.contains("UpdateNoticeScreen")
        )
    }

    @Test
    fun testGestureGuideGuaranteedOnFirstRunAndUpdated() {
        val appFile = File("src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt").let {
            if (it.exists()) it else File("app/src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt")
        }
        val appSource = appFile.readText()

        assertTrue(
            "FocusGramApp must reset hasSeenGestureGuide to false when update notice is dismissed",
            appSource.contains("settingsRepository.setHasSeenGestureGuide(false)")
        )
        assertTrue(
            "FocusGramApp must overlay GestureGuideOverlay when active",
            appSource.contains("GestureGuideOverlay")
        )
    }

    @Test
    fun testReelHistoryWebExtensionIsEnabledInGeckoRuntimeManager() {
        val runtimeMgrFile = File("src/main/kotlin/com/ujwal/focusgram/core/engine/GeckoRuntimeManager.kt").let {
            if (it.exists()) it else File("app/src/main/kotlin/com/ujwal/focusgram/core/engine/GeckoRuntimeManager.kt")
        }
        val runtimeSource = runtimeMgrFile.readText()

        assertTrue(
            "reelhistory WebExtension must be explicitly enabled on installation",
            runtimeSource.contains("reelhistory@focusgram.app") &&
                runtimeSource.contains("webExtensionController.enable(ext,")
        )
    }
}
