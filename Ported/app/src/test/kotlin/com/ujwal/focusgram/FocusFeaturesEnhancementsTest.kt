package com.ujwal.focusgram

import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.feature.settings.SettingsSubpage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * FocusFeaturesEnhancementsTest
 * Validates fixes for:
 * 1. Autoplay manual user play support
 * 2. Blur feed & carousel blurring
 * 3. Blur reels unblur persistence (tap & hold)
 * 4. Hide like counts selector coverage
 * 5. Licenses subpage availability
 */
class FocusFeaturesEnhancementsTest {

    private fun getFile(relativePath: String): File {
        val f1 = File(relativePath)
        if (f1.exists()) return f1
        val f2 = File("app/$relativePath")
        if (f2.exists()) return f2
        val f3 = File("Ported/app/$relativePath")
        if (f3.exists()) return f3
        throw AssertionError("File not found: $relativePath")
    }

    @Test
    fun testFocusSettingsDefaultsAndToggles() {
        val settings = FocusSettings()
        assertFalse("blurFeed should default to false", settings.blurFeed)
        assertFalse("blurReels should default to false", settings.blurReels)
        assertTrue("blurExplore should default to true", settings.blurExplore)
        assertEquals("tap", settings.unblurMode)
        assertTrue(settings.tapToUnblur)
        assertFalse(settings.requireLongPress)

        val updated = settings.copy(blurFeed = true, blurReels = true, unblurMode = "hold", requireLongPress = true, tapToUnblur = false)
        assertTrue(updated.blurFeed)
        assertTrue(updated.blurReels)
        assertEquals("hold", updated.unblurMode)
        assertTrue(updated.requireLongPress)
        assertFalse(updated.tapToUnblur)
    }

    @Test
    fun testSettingsSubpageIncludesLicenses() {
        val subpages = SettingsSubpage.values().map { it.name }
        assertTrue("SettingsSubpage must include LICENSES", subpages.contains("LICENSES"))
        assertTrue("SettingsSubpage must include GUARDRAILS", subpages.contains("GUARDRAILS"))
        assertTrue("SettingsSubpage must include FOCUS", subpages.contains("FOCUS"))
    }

    @Test
    fun testAutoplayBlockerSupportsManualPlayOnUserGesture() {
        val script = getFile("src/main/assets/scripts/autoplay_blocker.js").readText()
        assertTrue("Must track allowed videos via WeakSet for per-post tap-to-play permission",
            script.contains("allowedVideos") || script.contains("WeakSet"))
        assertTrue("Must allow playback on user gesture", script.contains("allowedVideos.has") || script.contains("allowedVideos.add"))
        assertTrue("Must listen on pointer and click events", (script.contains("pointerdown") || script.contains("pointerup")) && script.contains("click"))
    }

    @Test
    fun testContentHiderBlurAndLikeCountRules() {
        val script = getFile("src/main/assets/scripts/content_hider.js").readText()
        assertTrue("Must support blurFeed", script.contains("config.blurFeed"))
        assertTrue("Must blur home feed media elements", script.contains("html[data-fg-feed] article img") || script.contains("article img"))
        assertTrue("Must support unblur reveal classes and attributes", script.contains("[data-fg-reveal]") && script.contains(".fg-unblurred"))
        assertTrue("Must have getBlurredTarget helper for tap/hold gestures", script.contains("getBlurredTarget"))
        assertTrue("Must have revealTarget helper", script.contains("revealTarget"))
        assertTrue("Must hide modern like counts via CSS", script.contains("a[href*=\"/liked_by/\"]"))
        assertTrue("Must scan dynamic like counts and hide them", script.contains("liked by") || script.contains("likes") || script.contains("hideLikeCounts"))
    }

    @Test
    fun testCarouselSlideIndependentBlurAndUnblurSelectors() {
        val hiderScript = getFile("src/main/assets/scripts/content_hider.js").readText()
        val extensionScript = getFile("src/main/assets/extensions/ublock/focusgram_content.js").readText()

        for (script in listOf(hiderScript, extensionScript)) {
            // Must blur individual carousel slides and media
            assertTrue("Must blur media elements individually without blurring parent aspect-ratio containers",
                script.contains("article img:not(header img)") || script.contains("article ul li"))
            // Must preserve post header avatars
            assertTrue("Must keep post header avatars unblurred",
                script.contains("article header img") && script.contains("filter: none !important"))
            // getBlurredTarget must detect carousel slides or media
            assertTrue("Must detect individual carousel slide or media in getBlurredTarget",
                script.contains("closest('li") || script.contains("carouselSlide") || script.contains("closest('article"))
            // Must ignore carousel arrow navigation buttons
            assertTrue("Must ignore carousel navigation buttons",
                script.contains("button[aria-label=\"Next\"]"))
            // revealTarget must clear inline filter permanently
            assertTrue("revealTarget must set filter: none !important",
                script.contains("target.style.setProperty('filter', 'none', 'important')"))
        }
    }

    @Test
    fun testCupertinoWheelPickerUsesModernNonDeprecatedVibrator() {
        val source = getFile("src/main/kotlin/com/ujwal/focusgram/core/ui/components/CupertinoWheelPicker.kt").readText()
        assertFalse("Must not use deprecated Context.VIBRATOR_SERVICE", source.contains("Context.VIBRATOR_SERVICE"))
        assertTrue("Must use VibratorManager on API 31+", source.contains("VibratorManager::class.java"))
        assertTrue("Must use Vibrator class on pre-31", source.contains("context.getSystemService(Vibrator::class.java)"))
    }

    @Test
    fun testAdsterraDirectContainerAndPreloader() {
        val source = getFile("src/main/kotlin/com/ujwal/focusgram/feature/wellness/AdsterraAdScreen.kt").readText()
        // Container element before script for reliable script invocation
        val ad1DivIdx = source.indexOf("container-e8a9b107824c939fb63d96c218c1336a")
        val ad1ScriptIdx = source.indexOf("https://pl18364273.effectivecpmnetwork.com/e8a9b107824c939fb63d96c218c1336a/invoke.js")
        assertTrue("Ad 1 container div must appear before the invoke script", ad1DivIdx != -1 && ad1ScriptIdx != -1 && ad1DivIdx < ad1ScriptIdx)
        // Ad 2 isolated atOptions
        assertTrue("Ad 2 must have atOptions", source.contains("atOptions = {"))
        // WebView settings
        assertTrue("Must allow mixed content", source.contains("WebSettings.MIXED_CONTENT_ALWAYS_ALLOW"))
        assertTrue("Must enable cacheMode LOAD_DEFAULT", source.contains("WebSettings.LOAD_DEFAULT"))
        assertTrue("Must sanitize User-Agent to remove ; wv", source.contains("replace(\"; wv\", \"\")"))
        assertTrue("Must handle window creation in WebChromeClient", source.contains("onCreateWindow"))
        // Timer cycle restart support
        assertTrue("Must restart countdown on reload", source.contains("timerCycle"))
        // AdPreloader
        assertTrue("Must provide AdPreloader with prewarm", source.contains("object AdPreloader") && source.contains("fun prewarm"))
    }

    @Test
    fun testStrictGatePrioritySequenceInFocusGramApp() {
        val source = getFile("src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt").readText()
        // Priority: AppLOCK -> Time limit reached -> Cooldown -> Breathe Gate -> Effort Friction challenge -> Set your intention
        val lockIdx = source.indexOf("if (shouldShowLock)")
        val timeLimitIdx = source.indexOf("if ((isDailyLimitExhausted || isSessionExhausted)")
        val cooldownIdx = source.indexOf("if (remainingCooldownSeconds > 0 && !hasPassedCooldownGate)")
        val breathGateIdx = source.indexOf("if (settings.showBreathGate && settings.breathGateSeconds > 0 && !hasPassedBreathGate)")
        val frictionIdx = source.indexOf("if (settings.effortFrictionEnabled && settings.wordChallengeCount > 0 && !hasPassedEffortFriction)")
        val sessionPickerIdx = source.indexOf("if (!hasPassedSessionPicker)")

        assertTrue("AppLock check must exist", lockIdx != -1)
        assertTrue("Time limit exhausted check must exist", timeLimitIdx != -1)
        assertTrue("Cooldown check must exist", cooldownIdx != -1)
        assertTrue("Breath gate check must exist", breathGateIdx != -1)
        assertTrue("Effort friction check must exist", frictionIdx != -1)
        assertTrue("Session picker check must exist", sessionPickerIdx != -1)

        assertTrue("AppLock must be evaluated before Time Limit", lockIdx < timeLimitIdx)
        assertTrue("Time Limit must be evaluated before Cooldown", timeLimitIdx < cooldownIdx)
        assertTrue("Cooldown must be evaluated before Breath Gate", cooldownIdx < breathGateIdx)
        assertTrue("Breath Gate must be evaluated before Effort Friction", breathGateIdx < frictionIdx)
        assertTrue("Effort Friction must be evaluated before Session Picker", frictionIdx < sessionPickerIdx)
    }

    @Test
    fun testSubpageBackNavigationAlwaysReturnsToSettings() {
        val source = getFile("src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt").readText()
        assertTrue("BackHandler must route subpages to SETTINGS",
            source.contains("destination in listOf(") &&
            source.contains("AppDestination.APP_LOCK_SETTINGS") &&
            source.contains("AppDestination.SCREEN_TIME") &&
            source.contains("AppDestination.REELS_HISTORY") &&
            source.contains("AppDestination.SNAPSHOTS") &&
            source.contains("AppDestination.GUARDRAILS") &&
            source.contains("destination = AppDestination.SETTINGS")
        )
    }

    @Test
    fun testDailyLimitExtensionModelAndRepository() {
        val settings = FocusSettings(
            dailyLimitMinutes = 60,
            dailyLimitAdClaimedDate = "2026-10-13"
        )
        assertEquals(60, settings.dailyLimitMinutes)
        assertEquals("2026-10-13", settings.dailyLimitAdClaimedDate)

        val repoSource = getFile("src/main/kotlin/com/ujwal/focusgram/core/datastore/SettingsRepository.kt").readText()
        assertTrue("SettingsRepository must provide extendDailyLimit5Minutes",
            repoSource.contains("suspend fun extendDailyLimit5Minutes(today: String)"))
    }

    @Test
    fun testMinimalModeRestrictsNavigationToDMsAndProfile() {
        val delegateSource = getFile("src/main/kotlin/com/ujwal/focusgram/core/engine/FocusGramNavigationDelegate.kt").readText()
        assertTrue("NavigationDelegate must redirect feed to direct inbox in minimal mode",
            delegateSource.contains("minimalModeEnabled") &&
            delegateSource.contains("https://www.instagram.com/direct/inbox/"))

        val navBarSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/navigation/NativeBottomNavBar.kt").readText()
        assertTrue("Native bottom nav bar in minimal mode must only display MESSAGES and PROFILE",
            navBarSource.contains("listOf(NavTab.MESSAGES, NavTab.PROFILE)"))
    }
}
