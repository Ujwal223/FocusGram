package com.ujwal.focusgram

import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.core.utils.TimeFormatters
import com.ujwal.focusgram.feature.history.ReelEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TwoTierLimitsAndEnhancementsTest {

    private fun getFile(relPath: String): File {
        val root = File(".").canonicalFile
        return if (root.name == "app") File(root.parentFile, "app/$relPath") else File(root, "app/$relPath")
    }

    @Test
    fun testTimeFormattersNeverShowsRawMinutesOver59() {
        assertEquals("4h 30m", TimeFormatters.formatMinutes(270))
        assertEquals("1h", TimeFormatters.formatMinutes(60))
        assertEquals("1h 15m", TimeFormatters.formatMinutes(75))
        assertEquals("45m", TimeFormatters.formatMinutes(45))
        assertEquals("0m", TimeFormatters.formatMinutes(0))
        assertEquals("0m", TimeFormatters.formatMinutes(-5))
    }

    @Test
    fun testTimeFormattersSecondsFormatting() {
        assertEquals("00:00", TimeFormatters.formatSeconds(0))
        assertEquals("00:45", TimeFormatters.formatSeconds(45))
        assertEquals("05:30", TimeFormatters.formatSeconds(330))
        assertEquals("1:05:30", TimeFormatters.formatSeconds(3930))
    }

    @Test
    fun testReelsWatchedCounterStartsAtZeroAndUpdatesInstantlyOnScroll() {
        val contentScript = getFile("src/main/assets/extensions/ublock/focusgram_content.js").readText()
        val reelsScript = getFile("src/main/assets/reelhistory/reels.js").readText()

        assertTrue("focusgram_content.js must track isInitialReelOnTabEntry",
            contentScript.contains("let isInitialReelOnTabEntry = true;"))
        assertTrue("focusgram_content.js must post type: 'meta' for initial reel to avoid starting at 1",
            contentScript.contains("isInitialReelOnTabEntry = false;") &&
            contentScript.contains("action: 'reelWatched'") &&
            contentScript.contains("type: 'meta'"))
        assertFalse("focusgram_content.js must NOT have dwell timer delay for reel scroll updates",
            contentScript.contains("reelWatchedDwellTimer"))

        assertTrue("reels.js must use responsive DWELL_MS (200ms)",
            reelsScript.contains("const DWELL_MS = 200;"))
        assertTrue("reels.js must skip initial mount reel",
            reelsScript.contains("isInitialReelMount"))
    }

    @Test
    fun testReelsAdBlockingSelectorsAndRules() {
        val contentJs = getFile("src/main/assets/extensions/ublock/content.js").readText()
        val focusgramJs = getFile("src/main/assets/extensions/ublock/focusgram_content.js").readText()

        assertTrue("content.js must check strict ad CTAs including 'shop now', 'install now', 'learn more'",
            contentJs.contains("shop now") && contentJs.contains("install now") && contentJs.contains("learn more"))
        assertTrue("content.js must detect sponsored keywords and ads",
            contentJs.contains("data-fg-reel-ad") && contentJs.contains("findReelCardForVideo"))
        assertTrue("focusgram_content.js must check STRICT ad CTAs and mute/pause/skip ad reels",
            focusgramJs.contains("strictAdCtas") && focusgramJs.contains("data-fg-reel-ad"))
    }

    @Test
    fun testScreenTimeServicePauseAndResumeSupport() {
        val serviceSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/screentime/ScreenTimeService.kt").readText()
        assertTrue("ScreenTimeService must define ACTION_PAUSE",
            serviceSource.contains("ACTION_PAUSE = \"com.ujwal.focusgram.action.PAUSE_SESSION\""))
        assertTrue("ScreenTimeService must define ACTION_RESUME",
            serviceSource.contains("ACTION_RESUME = \"com.ujwal.focusgram.action.RESUME_SESSION\""))
        assertTrue("ScreenTimeService must pause tracking and update notification when paused",
            serviceSource.contains("pauseTracking()") && serviceSource.contains("(Paused)"))
    }

    @Test
    fun testFocusGramAppPausesTimerOutsideMainWebview() {
        val appSource = getFile("src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt").readText()
        assertTrue("FocusGramApp must pause ticker when destination != MAIN_WEBVIEW",
            appSource.contains("destination == AppDestination.MAIN_WEBVIEW"))
        assertTrue("FocusGramApp must send pauseSession when destination != MAIN_WEBVIEW",
            appSource.contains("ScreenTimeService.pauseSession(context"))
    }

    @Test
    fun testReelsHistoryClientSideDeduplication() {
        fun extractReelCode(entry: ReelEntry): String {
            val cleanCode = entry.code.trim()
                .removePrefix("reel_")
                .removePrefix("/")
                .removeSuffix("/")
                .substringAfterLast("/reel/")
                .substringAfterLast("/reels/")
                .substringBefore("?")
                .substringBefore("/")
                .trim()
                .lowercase()

            val ignoredCodes = setOf("reels", "reel", "videos", "audio", "clips", "home", "explore", "direct", "stories", "")
            return if (cleanCode.length >= 4 && cleanCode !in ignoredCodes) {
                cleanCode
            } else {
                val cleanAuthor = entry.author.trim().lowercase().removePrefix("@")
                val cleanCaption = entry.caption.trim().lowercase()
                if (cleanAuthor.isNotBlank() || cleanCaption.isNotBlank()) {
                    "${cleanAuthor}___${cleanCaption.take(60)}"
                } else {
                    entry.url.trim().substringBefore("?").trimEnd('/')
                }
            }
        }

        val entries = listOf(
            ReelEntry("C9abc123", "alice", "Cool post", 1000L, 1),
            ReelEntry("reel_C9abc123", "alice", "Cool post", 1005L, 2),
            ReelEntry("/C9abc123/", "alice", "Cool post", 1010L, 1),
            ReelEntry("", "bob", "Morning coffee", 1020L, 1),
            ReelEntry("reels", "bob", "Morning coffee", 1030L, 2),
            ReelEntry("D4xyz999", "charlie", "Different post", 1040L, 1)
        )

        val deduplicated = entries.distinctBy { extractReelCode(it) }
        assertEquals(3, deduplicated.size)
        assertEquals("C9abc123", deduplicated[0].code)
        assertEquals("bob", deduplicated[1].author)
        assertEquals("D4xyz999", deduplicated[2].code)
    }

    @Test
    fun testDailyLimitSettingsLockoutRules() {
        // Rule: Locked if limit is already hit
        fun isDailyLimitLocked(dailyLimitMinutes: Int, todaySeconds: Int): Boolean {
            val baseDailyLimitSec = dailyLimitMinutes * 60
            val isDailyLimitHit = dailyLimitMinutes > 0 && todaySeconds >= baseDailyLimitSec
            // Rule: Locked if within 5m (300s), but ONLY if total app limit is ABOVE 15 minutes
            val isNearDailyLimit = dailyLimitMinutes > 15 && (baseDailyLimitSec - todaySeconds) in 0..300
            return dailyLimitMinutes > 0 && (isDailyLimitHit || isNearDailyLimit)
        }

        // Limit is 60m (3600s). Today usage is 3400s (56m40s) -> within 5m -> locked
        assertTrue(isDailyLimitLocked(dailyLimitMinutes = 60, todaySeconds = 3400))

        // Limit is 60m (3600s). Today usage is 3000s (50m) -> remaining 10m -> NOT locked
        assertFalse(isDailyLimitLocked(dailyLimitMinutes = 60, todaySeconds = 3000))

        // Limit is 60m (3600s). Today usage is 3700s (hit) -> locked
        assertTrue(isDailyLimitLocked(dailyLimitMinutes = 60, todaySeconds = 3700))

        // Limit is 15m (900s). Rule says proximity ONLY activates if total limit > 15 min!
        // At 14m (840s, remaining 60s <= 300s), proximity rule must NOT activate because limit <= 15
        assertFalse(isDailyLimitLocked(dailyLimitMinutes = 15, todaySeconds = 840))

        // At 15m (900s), hit rule DOES activate
        assertTrue(isDailyLimitLocked(dailyLimitMinutes = 15, todaySeconds = 900))

        // Off (0m) -> never locked
        assertFalse(isDailyLimitLocked(dailyLimitMinutes = 0, todaySeconds = 5000))
    }

    @Test
    fun testTwoTierExtensionsArchitectureSeparation() {
        val appSource = getFile("src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt").readText()

        // Daily limit gate must be separate from Session Exhausted gate
        assertTrue("FocusGramApp must render DailyLimitGateScreen",
            appSource.contains("DailyLimitGateScreen("))
        assertTrue("FocusGramApp must render SessionExhaustedScreen",
            appSource.contains("SessionExhaustedScreen("))

        // Session extensions: 3 extensions of 3 min
        assertTrue("Session extensions must allow 3 extensions",
            appSource.contains("sessionExtensionsRemaining = (3 - sessionExtensionsUsed).coerceAtLeast(0)"))
        assertTrue("Session extension must add 3 minutes",
            appSource.contains("sessionRemainingSeconds += 3 * 60"))

        // Daily limit extensions: 2 extensions of 5 min
        assertTrue("Daily extensions must allow 2 extensions",
            appSource.contains("dailyExtensionsRemaining = (2 - dailyExtensionsUsed).coerceAtLeast(0)"))
        assertTrue("Daily limit extension must add 5 minutes",
            appSource.contains("extendDailyLimit5Minutes"))
    }

    @Test
    fun testReelsAdBlockingDoesNotTargetFeedContainersOrSetZeroHeight() {
        val contentJs = getFile("src/main/assets/extensions/ublock/content.js").readText()
        val focusgramJs = getFile("src/main/assets/extensions/ublock/focusgram_content.js").readText()

        // Card should NOT target section, main, or parent divs > 0.4
        assertFalse("content.js findReelCardForVideo must NOT select section or container div",
            contentJs.contains("article, section, [role=\"region\"]"))
        assertFalse("content.js must NOT set height 0px on reel card",
            contentJs.contains("card.style.setProperty('height', '0px'"))

        // focusgram_content.js must NOT have stray closing brace
        assertFalse("focusgram_content.js must not contain stray unmatched closing brace in CSS",
            focusgramJs.contains("text-shadow: 0 1px 3px rgba(0, 0, 0, 0.85) !important;\n            }\n\n            }"))
    }

    @Test
    fun testDebugBypassOptionsForCooldownAndDailyLimit() {
        val dailyGateSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/session/DailyLimitGateScreen.kt").readText()
        val sessionGateSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/session/SessionExhaustedScreen.kt").readText()
        val cooldownGateSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/wellness/CooldownGateScreen.kt").readText()
        val appSource = getFile("src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt").readText()

        assertTrue("DailyLimitGateScreen must define onDebugBypass parameter",
            dailyGateSource.contains("onDebugBypass: (() -> Unit)? = null"))
        assertTrue("DailyLimitGateScreen must show Skip Daily Limit (Debug) button",
            dailyGateSource.contains("Skip Daily Limit (Debug)"))

        assertTrue("SessionExhaustedScreen must define onDebugBypass parameter",
            sessionGateSource.contains("onDebugBypass: (() -> Unit)? = null"))
        assertTrue("SessionExhaustedScreen must show Skip Session Limit (Debug) button",
            sessionGateSource.contains("Skip Session Limit (Debug)"))

        assertTrue("CooldownGateScreen must support skipping cooldown",
            cooldownGateSource.contains("Skip Cooldown"))

        assertTrue("FocusGramApp must track isDebugDailyLimitBypassed",
            appSource.contains("isDebugDailyLimitBypassed"))
    }

    @Test
    fun testAboutDialogSafeIconRasterization() {
        val settingsSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/settings/SettingsScreen.kt").readText()
        assertFalse("SettingsScreen must not pass R.mipmap.ic_launcher to painterResource (causes crash on adaptive icon XML)",
            settingsSource.contains("painterResource(R.mipmap.ic_launcher)") ||
            settingsSource.contains("painterResource(id = R.mipmap.ic_launcher)"))
        assertTrue("SettingsScreen must use AppIconManager.getIconBitmap for the About dialog logo",
            settingsSource.contains("AppIconManager.getIconBitmap(context, R.mipmap.ic_launcher)"))
    }

    @Test
    fun testDebugMenuInSettingsScreen() {
        val settingsSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/settings/SettingsScreen.kt").readText()
        val appSource = getFile("src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt").readText()
        assertTrue("SettingsScreen must have DEVELOPER DIAGNOSTICS section",
            settingsSource.contains("DEVELOPER DIAGNOSTICS"))
        assertTrue("SettingsScreen must contain Debug Diagnostics Menu row",
            settingsSource.contains("Debug Diagnostics Menu"))
        assertTrue("FocusGramApp must route onNavigateDebugMenu to AppDestination.DEBUG_MENU",
            appSource.contains("onNavigateDebugMenu = { destination = AppDestination.DEBUG_MENU }"))
    }

    @Test
    fun testExtrasSubpageCategorization() {
        val settingsSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/settings/SettingsScreen.kt").readText()
        assertTrue("SettingsScreen ExtrasSubpage must have OTHERS section title",
            settingsSource.contains("IOSSectionTitle(title = \"OTHERS\")"))
        assertFalse("SettingsScreen ExtrasSubpage must no longer have INSTAGRAM section title",
            settingsSource.contains("IOSSectionTitle(title = \"INSTAGRAM\")"))

        val othersIndex = settingsSource.indexOf("IOSSectionTitle(title = \"OTHERS\")")
        val portraitIndex = settingsSource.indexOf("title = \"Portrait Only\"", othersIndex)
        val igSettingsIndex = settingsSource.indexOf("title = \"Instagram Account Settings\"", othersIndex)

        assertTrue("Portrait Only must be in OTHERS section", portraitIndex > othersIndex)
        assertTrue("Portrait Only must be placed above Instagram Account Settings", portraitIndex < igSettingsIndex)
    }

    @Test
    fun testCupertinoWheelPickerAlignmentAndPrecision() {
        val onboardingSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/onboarding/OnboardingScreen.kt").readText()
        val pickerSource = getFile("src/main/kotlin/com/ujwal/focusgram/core/ui/components/CupertinoWheelPicker.kt").readText()

        assertFalse("OnboardingScreen must not force fixed height(160.dp) on CupertinoWheelPicker",
            onboardingSource.contains(".height(160.dp)"))
        assertTrue("CupertinoWheelPicker must compute centered index by finding visible item closest to viewport center",
            pickerSource.contains("abs((item.offset + item.size / 2) - viewportCenter)"))
    }

    @Test
    fun testScriptsStrictFeatureDeactivation() {
        val fetchJs = getFile("src/main/assets/scripts/fetch_interceptor.js").readText()
        val nativeFeelJs = getFile("src/main/assets/scripts/native_feel.js").readText()
        val contentJs = getFile("src/main/assets/extensions/ublock/focusgram_content.js").readText()

        assertTrue("fetch_interceptor.js must define cleanupStorySearch",
            fetchJs.contains("function cleanupStorySearch()"))
        assertTrue("fetch_interceptor.js must listen for FOCUSGRAM_FILTER_UPDATE",
            fetchJs.contains("FOCUSGRAM_FILTER_UPDATE"))

        assertTrue("native_feel.js must define cleanupBioStylist",
            nativeFeelJs.contains("function cleanupBioStylist()"))
        assertTrue("native_feel.js must listen for FOCUSGRAM_FILTER_UPDATE",
            nativeFeelJs.contains("FOCUSGRAM_FILTER_UPDATE"))

        assertTrue("focusgram_content.js must clean up fg-story-search-card when storySearchEnabled is false",
            contentJs.contains("currentSettings.storySearchEnabled === false") &&
            contentJs.contains("existing.remove()"))
        assertTrue("focusgram_content.js must clean up bioToolbarEl when customBioFontsEnabled is false",
            contentJs.contains("currentSettings.customBioFontsEnabled === false") &&
            contentJs.contains("bioToolbarEl.remove()"))
    }

    @Test
    fun testReducedLiquidGlassSpecularEffect() {
        val iosComponents = getFile("src/main/kotlin/com/ujwal/focusgram/core/ui/components/IOSSettingsComponents.kt").readText()
        val focusComponents = getFile("src/main/kotlin/com/ujwal/focusgram/core/ui/components/FocusGramComponents.kt").readText()

        assertTrue("IOSGroupedCard must use 20% reduced specular border brush Color(0x2DFFFFFF)",
            iosComponents.contains("Color(0x2DFFFFFF)"))
        assertTrue("FocusGramCard must use 20% reduced specular border brush Color(0x2DFFFFFF)",
            focusComponents.contains("Color(0x2DFFFFFF)"))
        assertTrue("IOSSettingsRow must use 15.dp start padding for isDependent",
            iosComponents.contains("if (isDependent) 15.dp else 16.dp"))
    }
}
