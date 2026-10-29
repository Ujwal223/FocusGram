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
        val stripped = relativePath.removePrefix("../")
        val f3 = File(stripped)
        if (f3.exists()) return f3
        val f4 = File("app/$stripped")
        if (f4.exists()) return f4
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
            assertTrue("Must blur media elements individually without blurring parent aspect-ratio containers",
                script.contains("article img:not(header img)") || script.contains("article ul li"))
            assertTrue("Must keep post header avatars unblurred",
                script.contains("article header img") && script.contains("filter: none !important"))
            assertTrue("Must detect individual carousel slide or media in getBlurredTarget",
                script.contains("closest('li") || script.contains("carouselSlide") || script.contains("closest('article"))
            assertTrue("Must ignore carousel navigation buttons",
                script.contains("button[aria-label=\"Next\"]"))
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
    fun testHapticHelperImplementsUniversalDirectHardwareVibration() {
        val source = getFile("src/main/kotlin/com/ujwal/focusgram/core/utils/HapticHelper.kt").readText()
        assertFalse("Must not use deprecated Context.VIBRATOR_SERVICE", source.contains("Context.VIBRATOR_SERVICE"))
        assertTrue("Must use VibratorManager on API 31+", source.contains("VibratorManager::class.java"))
        assertTrue("Must use Vibrator class on pre-31", source.contains("context.getSystemService(Vibrator::class.java)"))
        assertTrue("Must pulse physical vibrator directly with >= 30ms duration to overcome ERM motor inertia",
            source.contains("createOneShot(35L") || source.contains("createOneShot(30L"))
        assertTrue("Must provide session completion waveform pattern", source.contains("createWaveform(pattern"))

        val sessionExhaustedSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/session/SessionExhaustedScreen.kt").readText()
        assertTrue("SessionExhaustedScreen must trigger session completion alert vibration",
            sessionExhaustedSource.contains("HapticHelper.performSessionCompletionAlert"))
    }

    @Test
    fun testAdsterraDirectContainerAndPreloader() {
        val source = getFile("src/main/kotlin/com/ujwal/focusgram/feature/wellness/AdsterraAdScreen.kt").readText()
        val ad1DivIdx = source.indexOf("container-e8a9b107824c939fb63d96c218c1336a")
        val ad1ScriptIdx = source.indexOf("https://pl18364273.effectivecpmnetwork.com/e8a9b107824c939fb63d96c218c1336a/invoke.js")
        assertTrue("Ad 1 container div must appear before the invoke script", ad1DivIdx != -1 && ad1ScriptIdx != -1 && ad1DivIdx < ad1ScriptIdx)
        assertTrue("Ad 2 must have atOptions", source.contains("atOptions = {"))
        assertTrue("Must allow mixed content", source.contains("WebSettings.MIXED_CONTENT_ALWAYS_ALLOW"))
        assertTrue("Must enable cacheMode LOAD_DEFAULT", source.contains("WebSettings.LOAD_DEFAULT"))
        assertTrue("Must sanitize User-Agent to remove ; wv", source.contains("replace(\"; wv\", \"\")"))
        assertTrue("Must handle window creation in WebChromeClient", source.contains("onCreateWindow"))
        assertTrue("Must restart countdown on reload", source.contains("timerCycle"))
        assertTrue("Must provide AdPreloader with prewarm", source.contains("object AdPreloader") && source.contains("fun prewarm"))
    }

    @Test
    fun testStrictGatePrioritySequenceInFocusGramApp() {
        val source = getFile("src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt").readText()
        // Priority: AppLOCK -> Daily limit reached -> Session Exhausted -> Cooldown -> Breathe Gate -> Effort Friction challenge -> Set your intention
        val lockIdx = source.indexOf("if (shouldShowLock)")
        val dailyLimitIdx = source.indexOf("if (isDailyLimitExhausted && destination != AppDestination.ADSTERRA_REWARDED)")
        val sessionExhaustedIdx = source.indexOf("if (isSessionExhausted && destination != AppDestination.ADSTERRA_REWARDED)")
        val cooldownIdx = source.indexOf("if (remainingCooldownSeconds > 0 && !hasPassedCooldownGate)")
        val breathGateIdx = source.indexOf("if (settings.showBreathGate && settings.breathGateSeconds > 0 && !hasPassedBreathGate)")
        val frictionIdx = source.indexOf("if (settings.effortFrictionEnabled && settings.wordChallengeCount > 0 && !hasPassedEffortFriction)")
        val sessionPickerIdx = source.indexOf("if (!hasPassedSessionPicker)")

        assertTrue("AppLock check must exist", lockIdx != -1)
        assertTrue("Daily limit check must exist", dailyLimitIdx != -1)
        assertTrue("Session exhausted check must exist", sessionExhaustedIdx != -1)
        assertTrue("Cooldown check must exist", cooldownIdx != -1)
        assertTrue("Breath gate check must exist", breathGateIdx != -1)
        assertTrue("Effort friction check must exist", frictionIdx != -1)
        assertTrue("Session picker check must exist", sessionPickerIdx != -1)

        assertTrue("AppLock must be evaluated before Daily Limit", lockIdx < dailyLimitIdx)
        assertTrue("Daily Limit must be evaluated before Session Exhausted", dailyLimitIdx < sessionExhaustedIdx)
        assertTrue("Session Exhausted must be evaluated before Cooldown", sessionExhaustedIdx < cooldownIdx)
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

    @Test
    fun testFilePickerManifestAndProviderConfiguration() {
        val manifestSource = getFile("src/main/AndroidManifest.xml").readText()
        assertTrue("Manifest must declare FileProvider",
            manifestSource.contains("androidx.core.content.FileProvider"))
        assertTrue("Manifest must declare intent queries for PICK_IMAGES",
            manifestSource.contains("android.provider.action.PICK_IMAGES"))
        assertTrue("Manifest must declare intent queries for GET_CONTENT",
            manifestSource.contains("android.intent.action.GET_CONTENT"))
        assertTrue("Manifest must declare camera capture intents in queries",
            manifestSource.contains("android.media.action.IMAGE_CAPTURE"))

        val pathsSource = getFile("src/main/res/xml/file_paths.xml").readText()
        assertTrue("file_paths.xml must configure cache-path for camera",
            pathsSource.contains("cache-path name=\"camera_photos\" path=\"camera/\""))
    }

    @Test
    fun testNativeBottomNavBarBlurAndGlassStyling() {
        val navBarSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/navigation/NativeBottomNavBar.kt").readText()
        assertTrue("NavBar must include frosted glass blur layer",
            navBarSource.contains(".blur(20.dp)"))
        assertTrue("NavBar must include glassBorderBrush",
            navBarSource.contains("glassBorderBrush"))
        assertTrue("NavBar must integrate HapticHelper for device-compatible vibrations",
            navBarSource.contains("HapticHelper.performHapticFeedback") || navBarSource.contains("HapticHelper"))
    }

    @Test
    fun testFragment186VersionEnforcement() {
        val buildGradle = getFile("build.gradle.kts").readText()
        assertTrue("build.gradle.kts must force androidx.fragment:fragment:1.8.6",
            buildGradle.contains("androidx.fragment:fragment:1.8.6"))
        assertTrue("build.gradle.kts must force androidx.fragment:fragment-ktx:1.8.6",
            buildGradle.contains("androidx.fragment:fragment-ktx:1.8.6"))

        val libsToml = getFile("../gradle/libs.versions.toml").readText()
        assertTrue("libs.versions.toml must declare fragment = \"1.8.6\"",
            libsToml.contains("fragment = \"1.8.6\""))
    }

    @Test
    fun testFilePickerMimeParsingAndMediaRouting() {
        fun resolveMediaTypes(mimes: Array<String>, isProfilePictureEdit: Boolean = false): Triple<Boolean, Boolean, String> {
            val cleanMimes = mimes
                .flatMap { it.split(",") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { token ->
                    if (token.startsWith(".jpg") || token.startsWith(".jpeg")) "image/jpeg"
                    else if (token.startsWith(".png")) "image/png"
                    else if (token.startsWith(".mp4")) "video/mp4"
                    else if (token.contains("/")) token
                    else null
                }
                .distinct()
                .toTypedArray()

            val isAnyMedia = cleanMimes.isEmpty() || cleanMimes.contains("*/*")
            val hasImages = isAnyMedia || cleanMimes.any { it.startsWith("image/") }
            val hasVideos = isAnyMedia || cleanMimes.any { it.startsWith("video/") }

            val allowsVideos = hasVideos || (!isProfilePictureEdit && hasImages)
            val allowsImages = hasImages || isAnyMedia

            val visualMediaType = when {
                allowsImages && !allowsVideos -> "ImageOnly"
                allowsVideos && !allowsImages -> "VideoOnly"
                else -> "ImageAndVideo"
            }
            return Triple(allowsImages, allowsVideos, visualMediaType)
        }

        // 1. Profile Picture: /accounts/edit screen strictly image only
        val (profileImg, profileVid, profileType) = resolveMediaTypes(arrayOf("image/*"), isProfilePictureEdit = true)
        assertTrue(profileImg)
        assertFalse(profileVid)
        assertEquals("ImageOnly", profileType)

        // 2. DM / Story upload: Instagram sends only image MIME types, but we must allow both Images and Videos!
        val (dmImg, dmVid, dmType) = resolveMediaTypes(arrayOf("image/avif, image/jpeg, image/png"), isProfilePictureEdit = false)
        assertTrue(dmImg)
        assertTrue(dmVid)
        assertEquals("ImageAndVideo", dmType)

        val (storyImg, storyVid, storyType) = resolveMediaTypes(arrayOf("image/png, image/jpeg"), isProfilePictureEdit = false)
        assertTrue(storyImg)
        assertTrue(storyVid)
        assertEquals("ImageAndVideo", storyType)

        // 3. Reels: Video only
        val (reelsImg, reelsVid, reelsType) = resolveMediaTypes(arrayOf("video/mp4, video/quicktime"), isProfilePictureEdit = false)
        assertFalse(reelsImg)
        assertTrue(reelsVid)
        assertEquals("VideoOnly", reelsType)

        // 4. Fallback: Empty array or */*
        val (emptyImg, emptyVid, emptyType) = resolveMediaTypes(emptyArray(), isProfilePictureEdit = false)
        assertTrue(emptyImg)
        assertTrue(emptyVid)
        assertEquals("ImageAndVideo", emptyType)

        val (starImg, starVid, starType) = resolveMediaTypes(arrayOf("*/*"), isProfilePictureEdit = false)
        assertTrue(starImg)
        assertTrue(starVid)
        assertEquals("ImageAndVideo", starType)
    }

    @Test
    fun testNativeFeelIncludesVideoSupportBridge() {
        val script = getFile("src/main/assets/scripts/native_feel.js").readText()
        assertTrue("native_feel.js must include enhanceFileInputsForVideoSupport",
            script.contains("enhanceFileInputsForVideoSupport"))
        assertTrue("native_feel.js must broaden accept attribute to include video formats",
            script.contains("video/mp4,video/quicktime,video/webm,video/*"))
        assertTrue("native_feel.js must exclude /accounts/edit from video patching",
            script.contains("/accounts/edit"))
    }

    @Test
    fun testReelsTopBarWatchedCount() {
        val appSource = getFile("src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt").readText()
        assertTrue("FocusGramApp must pass reelsWatchedSessionCount directly to ReelsTopBar",
            appSource.contains("watchedCount = reelsWatchedSessionCount"))

        val topBarSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/navigation/ReelsTopBar.kt").readText()
        assertTrue("ReelsTopBar must default watchedCount to 0",
            topBarSource.contains("watchedCount: Int = 0"))
    }

    @Test
    fun testDownloadProgressStateAndIndeterminateProgress() {
        val defaultState = com.ujwal.focusgram.core.utils.DownloadProgressState()
        assertFalse(defaultState.isDownloading)
        assertFalse(defaultState.isCompleted)
        assertEquals(0f, defaultState.progress, 0.001f)

        com.ujwal.focusgram.core.utils.MediaDownloadHelper.startIndeterminateProgress("test_reel")
        val activeState = com.ujwal.focusgram.core.utils.MediaDownloadHelper.downloadProgressFlow.value
        assertTrue("isDownloading must be true after startIndeterminateProgress", activeState.isDownloading)
        assertFalse("isCompleted must be false while downloading", activeState.isCompleted)
        assertTrue("progress should be >= 0.05f", activeState.progress >= 0.05f)
        assertEquals("test_reel", activeState.filename)
    }

    @Test
    fun testHomeVsProfileTabResolutionWithQueryParams() {
        fun getCleanPath(url: String): String {
            val afterScheme = if (url.contains("://")) url.substringAfter("://") else url
            val pathPart = if (afterScheme.contains('/')) afterScheme.substringAfter('/') else ""
            return pathPart.substringBefore('?').substringBefore('#').trim('/')
        }

        fun resolveIsHome(url: String): Boolean {
            val cleanPath = getCleanPath(url)
            val lowerUrl = url.lowercase()
            val isHome = cleanPath.isEmpty()
            return isHome || lowerUrl.startsWith("https://www.instagram.com/?") || lowerUrl.startsWith("https://instagram.com/?") || lowerUrl == "/"
        }

        fun resolveIsProfile(url: String): Boolean {
            val cleanPath = getCleanPath(url)
            val lowerUrl = url.lowercase()
            val isHome = cleanPath.isEmpty()
            val isInstagram = lowerUrl.contains("instagram.com") || lowerUrl.startsWith("/")
            val isExcluded = isHome ||
                    lowerUrl.contains("/explore") ||
                    lowerUrl.contains("/reel") ||
                    lowerUrl.contains("/direct") ||
                    lowerUrl.contains("/inbox") ||
                    lowerUrl.contains("/p/") ||
                    lowerUrl.contains("/stories/") ||
                    lowerUrl.startsWith("focusgram-download:") ||
                    lowerUrl.startsWith("javascript:")

            val segments = cleanPath.trim('/').split('/').filter { it.isNotEmpty() }
            val isSingleUserSegment = segments.size == 1

            return !isHome && isInstagram && !isExcluded &&
                    (lowerUrl.contains("/profile") || isSingleUserSegment)
        }

        // Query parameters on the root URL should never misclassify home feed as a profile page.
        assertTrue(resolveIsHome("https://www.instagram.com/"))
        assertFalse(resolveIsProfile("https://www.instagram.com/"))

        assertTrue(resolveIsHome("https://www.instagram.com/?variant=home"))
        assertFalse(resolveIsProfile("https://www.instagram.com/?variant=home"))

        assertTrue(resolveIsHome("https://www.instagram.com/?theme=dark"))
        assertFalse(resolveIsProfile("https://www.instagram.com/?theme=dark"))

        assertFalse(resolveIsHome("https://www.instagram.com/zuck/"))
        assertTrue(resolveIsProfile("https://www.instagram.com/zuck/"))

        assertFalse(resolveIsHome("https://www.instagram.com/profile"))
        assertTrue(resolveIsProfile("https://www.instagram.com/profile"))

        assertFalse(resolveIsProfile("https://www.instagram.com/explore/"))
        assertFalse(resolveIsProfile("https://www.instagram.com/reel/C7x90/"))
        assertFalse(resolveIsProfile("https://www.instagram.com/p/C7x90/"))
        assertFalse(resolveIsProfile("https://www.instagram.com/direct/inbox/"))
    }

    @Test
    fun testFocusGramContentAntiFlashDoesNotCollapseFeed() {
        val script = getFile("src/main/assets/extensions/ublock/focusgram_content.js").readText()
        assertFalse(
            "focusgram_content.js must NOT have overly broad div:has(> a[href='/']) that caused feed blanking",
            script.contains("div:has(> a[href=\"/\"]):has(a[href*=\"/explore/\"])")
        )
        assertFalse(
            "focusgram_content.js must NOT have div:has(> div > a[href='/'])",
            script.contains("div:has(> div > a[href=\"/\"]):has(a[href*=\"/explore/\"])")
        )
        assertTrue(
            "focusgram_content.js must restrict anti-flash hiding to navigation/footer roles",
            script.contains("nav[role=\"navigation\"]:has(a[href*=\"/explore/\"])")
        )
    }

    @Test
    fun testMediaDownloaderDoesNotUseIframeToPreventCspViolations() {
        val script = getFile("src/main/assets/scripts/media_downloader.js").readText()
        assertFalse(
            "media_downloader.js must not create iframes with custom schemes which violate Instagram frame-src CSP",
            script.contains("document.createElement('iframe')")
        )
    }

    @Test
    fun testMediaDownloaderAccidentalScrollGuard() {
        val scriptUser = getFile("src/main/assets/scripts/media_downloader.js").readText()
        assertTrue(
            "media_downloader.js must guard against accidental download triggers during feed scrolling with hold timer",
            scriptUser.contains("isGlobalScrolling") && scriptUser.contains("touchmove") && scriptUser.contains("holdThresholdPassed")
        )
    }

    @Test
    fun testMediaDownloaderPreventsFalseVideoHijackingOnPhotos() {
        val scriptUser = getFile("src/main/assets/scripts/media_downloader.js").readText()
        assertTrue(
            "media_downloader.js must check hasVideoElement before inspecting video streams from performance timings",
            scriptUser.contains("hasVideoElement")
        )
    }

    @Test
    fun testMediaDownloadHelperPhotoVsVideoDetectionAndFilename() {
        val helper = com.ujwal.focusgram.core.utils.MediaDownloadHelper
        val photoUrl = "https://instagram.fktm1-1.fna.fbcdn.net/v/t51.82787-15/841260402_18131269318760808_4130815482121662762_n.jpg?stp=dst-jpg_e15_tt6&_nc_cat=107"
        val videoUrl = "https://instagram.fktm1-1.fna.fbcdn.net/v/t50.28834-16/123456_n.mp4"

        assertFalse("Photo URL with /v/t51 and dst-jpg must NOT be detected as video", helper.detectIsVideo(photoUrl, "instagram_xyz.mp4"))
        assertTrue("Video URL with /v/t50 and .mp4 must be detected as video", helper.detectIsVideo(videoUrl, "instagram_xyz.mp4"))

        val formattedPhotoName = helper.formatTargetFilename("@ujwal - Focusgram.mp4", false)
        assertEquals("@ujwal - Focusgram.jpg", formattedPhotoName)

        val formattedVideoName = helper.formatTargetFilename("@ujwal - Focusgram.jpg", true)
        assertEquals("@ujwal - Focusgram.mp4", formattedVideoName)

        val fallbackFormatted = helper.formatTargetFilename("instagram_DehM0IhkVm0.jpg", false)
        assertEquals("@Focusgram - Focusgram.jpg", fallbackFormatted)

        val normalAuthorFormatted = helper.formatTargetFilename("@ujwal", true)
        assertEquals("@ujwal - Focusgram.mp4", normalAuthorFormatted)

        val longAuthorFormatted = helper.formatTargetFilename("ujwaldeveloper12345", true)
        assertEquals("@ujwaldevelope - Focusgram.mp4", longAuthorFormatted)
    }

    @Test
    fun testCommentGifsExclusionFromExploreBlur() {
        val ublockScript = getFile("src/main/assets/extensions/ublock/focusgram_content.js").readText()
        val contentHiderScript = getFile("src/main/assets/scripts/content_hider.js").readText()

        assertFalse("Explore blur must NOT blindly target all div[style*=\"grid\"]",
            ublockScript.contains("div[style*=\"grid\"]:not([data-fg-reveal]):not(.fg-unblurred) img"))
        assertFalse("Explore blur in content_hider must NOT blindly target all div[style*=\"grid\"]",
            contentHiderScript.contains("div[style*=\"grid\"]:not([data-fg-reveal]):not(.fg-unblurred) img"))

        assertTrue("Must include rule preserving Giphy and comment GIFs unblurred",
            ublockScript.contains("img[src*=\"giphy\"]") && ublockScript.contains("filter: none !important;"))
        assertTrue("content_hider must include rule preserving Giphy and comment GIFs unblurred",
            contentHiderScript.contains("img[src*=\"giphy\"]") && contentHiderScript.contains("filter: none !important;"))

        assertTrue("getBlurredTarget must ignore comment GIFs and comment inputs",
            ublockScript.contains("el.closest('img[src*=\"giphy\"]"))
        assertTrue("getBlurredTarget in content_hider must ignore comment GIFs and comment inputs",
            contentHiderScript.contains("el.closest('img[src*=\"giphy\"]"))
    }

    @Test
    fun testReelsCounterStartsAtZeroWithInstantScrollUpdate() {
        val ublockScript = getFile("src/main/assets/extensions/ublock/focusgram_content.js").readText()
        val reelsScript = getFile("src/main/assets/reelhistory/reels.js").readText()

        assertTrue("focusgram_content.js must track isInitialReelOnTabEntry",
            ublockScript.contains("isInitialReelOnTabEntry"))
        assertTrue("focusgram_content.js must not count initial landing reel as watched",
            ublockScript.contains("action: 'reelWatched'") && ublockScript.contains("type: 'meta'"))
        assertTrue("reels.js must use responsive DWELL_MS <= 200ms",
            reelsScript.contains("const DWELL_MS = 200;"))
    }

    @Test
    fun testPersistentNotificationTitleUsesSessionTimer() {
        val serviceSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/screentime/ScreenTimeService.kt").readText()
        assertTrue("ScreenTimeService must format title as 'Session Timer: ...'",
            serviceSource.contains("\"Session Timer: \$timeStr\""))
        assertFalse("ScreenTimeService must NOT toggle between Reels Focus and Instagram Focus in title",
            serviceSource.contains("if (isReelsSession) \"Reels Focus: \$timeStr\" else \"Instagram Focus: \$timeStr\""))
    }

    @Test
    fun testWallClockCooldownCalculationAcrossTimeJumps() {
        val cooldownMinutes = 15
        val now = 1_000_000_000L
        val lastSessionEndTimestamp = now - (12 * 60 * 1000L) // 12 minutes elapsed

        val targetExpiryMillis = lastSessionEndTimestamp + (cooldownMinutes * 60 * 1000L)
        val remainingMillis = targetExpiryMillis - now
        val remainingSeconds = if (remainingMillis > 0) ((remainingMillis + 999) / 1000).toInt() else 0

        assertEquals("After 12 minutes of a 15-minute cooldown, exactly 3 minutes (180s) must remain",
            180, remainingSeconds)

        // After full 15 minutes elapsed
        val pastTargetNow = lastSessionEndTimestamp + (16 * 60 * 1000L)
        val expiredMillis = targetExpiryMillis - pastTargetNow
        val expiredSeconds = if (expiredMillis > 0) ((expiredMillis + 999) / 1000).toInt() else 0
        assertEquals(0, expiredSeconds)
    }

    @Test
    fun testFocusGramNavigationDelegateWhitelistsFileSchemeForAppFiles() {
        val navDelegateSource = getFile("src/main/kotlin/com/ujwal/focusgram/core/engine/FocusGramNavigationDelegate.kt").readText()
        assertTrue("FocusGramNavigationDelegate must allow scheme == 'file' for internal files",
            navDelegateSource.contains("scheme == \"file\"") && navDelegateSource.contains("appFilesPath"))
    }

    @Test
    fun testGhostModeMainToggleRequiresAdReward() {
        val settingsSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/settings/SettingsScreen.kt").readText()

        assertTrue(
            "GhostModeSubpage must accept onRequestAdReward parameter",
            settingsSource.contains("private fun GhostModeSubpage(") &&
                settingsSource.contains("onRequestAdReward: (action: suspend () -> Unit, reason: String) -> Unit")
        )

        assertTrue(
            "Main Ghost Mode toggle must request ad reward to enable or disable",
            settingsSource.contains("repository.setGhostMode(newState)") &&
                settingsSource.contains("Please watch an ad in order to \$actionDesc Ghost Mode.")
        )

        assertTrue(
            "Sub-toggles must update repository directly via coroutine scope without ad prompts",
            settingsSource.contains("onCheckedChange = { scope.launch { repository.setGhostHideStorySeen(it) } }") &&
                settingsSource.contains("onCheckedChange = { scope.launch { repository.setGhostHideDirectSeen(it) } }") &&
                settingsSource.contains("onCheckedChange = { scope.launch { repository.setGhostHideTypingIndicator(it) } }")
        )
    }

    @Test
    fun testScreenTimeServiceShowsSelectedSessionTimeInSubtitle() {
        val serviceSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/screentime/ScreenTimeService.kt").readText()
        assertTrue(
            "ScreenTimeService must accept EXTRA_SESSION_DURATION_MINUTES",
            serviceSource.contains("EXTRA_SESSION_DURATION_MINUTES = \"extra_session_duration_minutes\"")
        )
        assertTrue(
            "ScreenTimeService subtitle must include selected session duration",
            serviceSource.contains("\"Session in progress • \$durationPart\"")
        )
        assertTrue(
            "ScreenTimeService pause tracking must format elapsed seconds accurately",
            serviceSource.contains("\"Session Timer: \$timeStr (Paused)\"")
        )
    }

    @Test
    fun testBottomNavSessionProgressAnimationWiring() {
        val navSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/navigation/NativeBottomNavBar.kt").readText()
        assertTrue(
            "NativeBottomNavBar must support sessionProgress parameter",
            navSource.contains("sessionProgress: Float = 0f")
        )
        assertTrue(
            "NativeBottomNavBar must respect showSessionTimeInBottomBar toggle and draw perimeter path",
            navSource.contains("settings.showSessionTimeInBottomBar") &&
                navSource.contains("currentSessionProgress > 0f") &&
                navSource.contains("Color(0xFFFFD700)")
        )

        val settingsSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/settings/SettingsScreen.kt").readText()
        assertTrue(
            "Settings Extras subpage must have 'Show session time in bottom bar' toggle",
            settingsSource.contains("Show session time in bottom bar") &&
                settingsSource.contains("repository.setShowSessionTimeInBottomBar")
        )
    }

    @Test
    fun testActiveSessionPersistenceWiring() {
        val appSource = getFile("src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt").readText()
        assertTrue(
            "FocusGramApp must restore hasPassedSessionPicker from active session state",
            appSource.contains("initialSettings.activeSessionIsActive && initialSettings.activeSessionRemainingSeconds > 0")
        )
        assertTrue(
            "FocusGramApp must save active session state to DataStore on session selection",
            appSource.contains("settingsRepository.saveActiveSession(")
        )
        assertTrue(
            "FocusGramApp must clear active session state on session completion",
            appSource.contains("settingsRepository.clearActiveSession()")
        )
        assertTrue(
            "FocusGramApp must restore reel history session count on startup if active session exists",
            appSource.contains("restoreSession(initialSettings.activeSessionReelCodes, initialSettings.activeSessionReelsWatched)")
        )
    }

    @Test
    fun testReelsHistorySessionCountPersistenceAndDeduplication() {
        val tempFile = File.createTempFile("reel_history_test", ".json")
        tempFile.deleteOnExit()

        val history = com.ujwal.focusgram.feature.history.ReelHistory(tempFile)
        assertEquals(0, history.sessionCount.value)

        // Simulate watching 3 unique reels
        history.onWatched("code_reel_1001", "creator1", "Caption 1")
        history.onWatched("code_reel_1002", "creator2", "Caption 2")
        history.onWatched("code_reel_1003", "creator3", "Caption 3")
        assertEquals(3, history.sessionCount.value)

        val savedCodes = history.getSessionCodes()
        assertEquals(3, savedCodes.size)

        // Simulate app restart / new ReelHistory instance restoring session
        val restoredHistory = com.ujwal.focusgram.feature.history.ReelHistory(tempFile)
        assertEquals(0, restoredHistory.sessionCount.value)

        restoredHistory.restoreSession(savedCodes, 3)
        assertEquals(3, restoredHistory.sessionCount.value)

        // Watching already watched reel should NOT increment session count
        restoredHistory.onWatched("code_reel_1001", "creator1", "Caption 1")
        assertEquals(3, restoredHistory.sessionCount.value)

        // Watching a new reel SHOULD increment session count
        restoredHistory.onWatched("code_reel_1004", "creator4", "Caption 4")
        assertEquals(4, restoredHistory.sessionCount.value)

        // Resetting session resets to 0
        restoredHistory.resetSession()
        assertEquals(0, restoredHistory.sessionCount.value)
    }

    @Test
    fun testBottomNavBarSunsetColorsAndTransition() {
        val navSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/navigation/NativeBottomNavBar.kt").readText()
        assertTrue(
            "NativeBottomNavBar must lerp golden colors to reddish-orange sunset colors as session progress increases",
            navSource.contains("sunsetStart = lerp(Color(0xFFFFE57F)") &&
                navSource.contains("sunsetMid1 = lerp(Color(0xFFFFD700)") &&
                navSource.contains("sunsetMid2 = lerp(Color(0xFFFFB300)") &&
                navSource.contains("sunsetEnd = lerp(Color(0xFFFFA000)")
        )
    }

    @Test
    fun testCooldownGateOnlyShowsSkipOnDebugBuild() {
        val cooldownSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/wellness/CooldownGateScreen.kt").readText()
        assertTrue(
            "CooldownGateScreen must guard skip button with BuildConfig.DEBUG",
            cooldownSource.contains("if (com.ujwal.focusgram.BuildConfig.DEBUG)") &&
                cooldownSource.contains("Skip Cooldown (Debug)")
        )
    }

    @Test
    fun testDebugMenuControllerAndSkipToggle() {
        val debugSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/diagnostics/DebugMenuScreen.kt").readText()
        assertTrue(
            "DebugMenuScreen must toggle between 'Skip Cooldowns & Daily Limits' and 'Don't Skip Cooldowns & Daily Limits'",
            debugSource.contains("Don't Skip Cooldowns & Daily Limits") &&
                debugSource.contains("Skip Cooldowns & Daily Limits") &&
                debugSource.contains("isLimitsBypassed")
        )
        assertTrue(
            "DebugMenuScreen must support onAdjustSessionTime and session time adjustment buttons",
            debugSource.contains("onAdjustSessionTime") &&
                debugSource.contains("Set Total Duration") &&
                debugSource.contains("Set Remaining")
        )
    }

    @Test
    fun testReelsHistoryClearDialogOptions() {
        val historyScreenSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/history/ReelsHistoryScreen.kt").readText()
        assertTrue(
            "ReelsHistoryScreen must present clear options for Today, 3 Days, Last Week, This Month, and All History",
            historyScreenSource.contains("ClearOptionRow(\n                        label = \"Today\"") ||
                historyScreenSource.contains("label = \"Today\"")
        )
        assertTrue(
            "ReelsHistoryScreen must offer 3 Days timeframe",
            historyScreenSource.contains("label = \"3 Days\"") &&
                historyScreenSource.contains("reelHistory.clearPastDays(3)")
        )
        assertTrue(
            "ReelsHistoryScreen must offer Last Week timeframe",
            historyScreenSource.contains("label = \"Last Week\"") &&
                historyScreenSource.contains("reelHistory.clearPastDays(7)")
        )
        assertTrue(
            "ReelsHistoryScreen must offer This Month timeframe",
            historyScreenSource.contains("label = \"This Month\"") &&
                historyScreenSource.contains("reelHistory.clearThisMonth()")
        )
        assertTrue(
            "ReelsHistoryScreen must offer All History clear",
            historyScreenSource.contains("label = \"All History\"") &&
                historyScreenSource.contains("reelHistory.clear()")
        )
    }

    @Test
    fun testScreenTimeScreenZeroDailyLimitHandling() {
        val screenTimeSource = getFile("src/main/kotlin/com/ujwal/focusgram/feature/screentime/ScreenTimeScreen.kt").readText()
        assertTrue(
            "ScreenTimeScreen must display 'Daily Limit (Off)' when daily limit is 0",
            screenTimeSource.contains("if (dailyLimitMinutes > 0) \"Daily Limit (\${TimeFormatters.formatMinutes(dailyLimitMinutes)})\" else \"Daily Limit (Off)\"")
        )
        assertTrue(
            "ScreenTimeScreen must compute limitFraction as 0f when dailyLimitMinutes is 0 to avoid false 100% red bar",
            screenTimeSource.contains("val limitFraction = if (dailyLimitMinutes > 0)")
        )
        assertTrue(
            "ScreenTimeScreen Mindful Insights must provide friendly message when daily limit is off",
            screenTimeSource.contains("dailyLimitMinutes <= 0 -> \"You have no daily limit set.")
        )
    }
}

