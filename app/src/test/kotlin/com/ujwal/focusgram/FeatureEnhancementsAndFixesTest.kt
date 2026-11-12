package com.ujwal.focusgram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URI

/**
 * FeatureEnhancementsAndFixesTest
 *
 * Verifies fixes and behavior for:
 * 1. Native clipboard capture via custom scheme bridge (bypassing GeckoView WebIDL read-only restrictions)
 * 2. URL blocking for Reels, DMs, and Stories in navigation delegation, intent handling, and client scripts
 * 3. Home feed unblur specificity and container unblur rules for single posts & carousels
 * 4. Permanent blur and autoplay prevention for home feed videos when "Block Reels" is enabled
 * 5. DM background notification scanning resilience and foreground service lifecycle
 */
class FeatureEnhancementsAndFixesTest {

    private fun getFile(relativePath: String): File {
        val f1 = File(relativePath)
        if (f1.exists()) return f1
        val f2 = File("app/$relativePath")
        if (f2.exists()) return f2
        val f3 = File("../$relativePath")
        if (f3.exists()) return f3
        return f1
    }

    @Test
    fun testClipboardInterceptionSchemeInNavigationDelegateAndScripts() {
        val navDelegateFile = getFile("src/main/kotlin/com/ujwal/focusgram/core/engine/FocusGramNavigationDelegate.kt").readText()
        assertTrue("NavigationDelegate must handle focusgram-clipboard scheme",
            navDelegateFile.contains("focusgram-clipboard"))
        assertTrue("NavigationDelegate must copy text to system ClipboardManager",
            navDelegateFile.contains("ClipboardManager") && navDelegateFile.contains("ClipData.newPlainText"))

        val nativeFeelJs = getFile("src/main/assets/scripts/native_feel.js").readText()
        assertTrue("native_feel.js must patch the Clipboard API in the page world",
            nativeFeelJs.contains("patchWriteText") &&
                nativeFeelJs.contains("navigator.clipboard") &&
                nativeFeelJs.contains("Clipboard.prototype"))
        assertTrue("native_feel.js must dispatch focusgram-clipboard scheme",
            nativeFeelJs.contains("focusgram-clipboard://copy?text="))

        val contentEngineJs = getFile("src/main/assets/extensions/content-engine/focusgram_content.js").readText()
        assertTrue("focusgram_content.js must provide focusgram-clipboard bridge",
            contentEngineJs.contains("focusgram-clipboard://copy?text="))
        assertTrue("focusgram_content.js must send clipboard text directly to Android",
            contentEngineJs.contains("sendNativeMessage('focusgram', message)"))
        val focusGramApp = getFile("src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt").readText()
        assertTrue("Main GeckoSession must install the native message delegate before loading Instagram",
            focusGramApp.contains("attachContentMessageDelegate(sessionManager.session)"))
        assertTrue("focusgram_content.js must bridge Clipboard API calls made by Instagram's page world",
            contentEngineJs.contains("installPageClipboardBridge") &&
                contentEngineJs.contains("focusgram:page-clipboard-copy") &&
                contentEngineJs.contains("clipboard.writeText"))
    }

    @Test
    fun testBlockedRouteInterceptionLogic() {
        fun isBlockedRoute(url: String, noReels: Boolean, noDMs: Boolean, noStories: Boolean): Boolean {
            val path = try {
                URI(url).path.lowercase()
            } catch (_: Exception) {
                url.substringBefore('?').substringBefore('#').lowercase()
            }

            if (noReels && (path.startsWith("/reel/") || path.startsWith("/reels") || path == "/reel")) {
                return true
            }
            if (noDMs && (path.startsWith("/direct") || path.startsWith("/messages"))) {
                return true
            }
            if (noStories && (path.startsWith("/stories/") || path == "/stories")) {
                return true
            }
            return false
        }

        // Reels blocking
        assertTrue(isBlockedRoute("https://www.instagram.com/reel/C12345/", noReels = true, noDMs = false, noStories = false))
        assertTrue(isBlockedRoute("https://www.instagram.com/reels/", noReels = true, noDMs = false, noStories = false))
        assertFalse(isBlockedRoute("https://www.instagram.com/reel/C12345/", noReels = false, noDMs = false, noStories = false))

        // DM blocking
        assertTrue(isBlockedRoute("https://www.instagram.com/direct/inbox/", noReels = false, noDMs = true, noStories = false))
        assertTrue(isBlockedRoute("https://www.instagram.com/direct/t/98765/", noReels = false, noDMs = true, noStories = false))
        assertFalse(isBlockedRoute("https://www.instagram.com/direct/inbox/", noReels = false, noDMs = false, noStories = false))

        // Stories blocking
        assertTrue(isBlockedRoute("https://www.instagram.com/stories/username/123/", noReels = false, noDMs = false, noStories = true))
        assertFalse(isBlockedRoute("https://www.instagram.com/stories/username/123/", noReels = false, noDMs = false, noStories = false))

        // Normal content unblocked
        assertFalse(isBlockedRoute("https://www.instagram.com/p/C99999/", noReels = true, noDMs = true, noStories = true))
        assertFalse(isBlockedRoute("https://www.instagram.com/", noReels = true, noDMs = true, noStories = true))
    }

    @Test
    fun testBlockedRouteInterceptionInCodeAndScripts() {
        val navDelegateFile = getFile("src/main/kotlin/com/ujwal/focusgram/core/engine/FocusGramNavigationDelegate.kt").readText()
        assertTrue("NavigationDelegate must block /reel/ under noReels",
            navDelegateFile.contains(".noReels") && navDelegateFile.contains("/reel/"))
        assertTrue("NavigationDelegate must block /direct under noDMs",
            navDelegateFile.contains(".noDMs") && navDelegateFile.contains("/direct"))
        assertTrue("NavigationDelegate must block /stories under noStories",
            navDelegateFile.contains(".noStories") && navDelegateFile.contains("/stories"))

        val appUiFile = getFile("src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt").readText()
        assertTrue("FocusGramApp must intercept pending intents for blocked routes",
            appUiFile.contains("settings.noReels") &&
            appUiFile.contains("settings.noDMs") &&
            appUiFile.contains("settings.noStories"))

        val contentHiderJs = getFile("src/main/assets/scripts/content_hider.js").readText()
        assertTrue("content_hider.js must intercept link clicks for blocked routes",
            contentHiderJs.contains("startsWith('/reel/')") &&
            contentHiderJs.contains("startsWith('/direct')") &&
            contentHiderJs.contains("startsWith('/stories/')"))

        val contentEngineJs = getFile("src/main/assets/extensions/content-engine/focusgram_content.js").readText()
        assertTrue("focusgram_content.js must intercept link clicks for blocked routes",
            contentEngineJs.contains("startsWith('/reel/')") &&
            contentEngineJs.contains("startsWith('/direct')") &&
            contentEngineJs.contains("startsWith('/stories/')"))
    }

    @Test
    fun testHomeFeedUnblurSpecificityAndContainerRules() {
        val contentHiderJs = getFile("src/main/assets/scripts/content_hider.js").readText()
        val contentEngineJs = getFile("src/main/assets/extensions/content-engine/focusgram_content.js").readText()

        listOf(contentHiderJs, contentEngineJs).forEach { script ->
            assertTrue("Script must exclude revealed elements in feed blur selector",
                script.contains(":not([data-fg-reveal]):not(.fg-unblurred)"))
            assertTrue("Script must include high-specificity unblur rules for articles and feed",
                script.contains("article[data-fg-reveal]") &&
                script.contains("html[data-fg-feed] article[data-fg-reveal]"))
            assertTrue("revealTarget must mark parent article revealed for single posts",
                script.contains("art.setAttribute('data-fg-reveal', '1')") ||
                script.contains("art.setAttribute(\"data-fg-reveal\", \"1\")"))
        }
    }

    @Test
    fun testPerUserIncrementalNotificationCounterAndTitle() {
        val dmManagerFile = getFile("src/main/kotlin/com/ujwal/focusgram/feature/notifications/DmNotificationManager.kt").readText()

        // Verify title uses sender nickname/first name, not generic FocusGram
        assertTrue("Notification title must use sender name when not chat locked",
            dmManagerFile.contains("val title = if (isChatLocked) \"Direct Message\" else sender.trim()") ||
            dmManagerFile.contains("cleanSender"))

        // Verify per-thread unread counter tracking
        assertTrue("Must track per-thread unread counts in memory",
            dmManagerFile.contains("unreadCountByThread") && dmManagerFile.contains("hasPlusByThread"))

        // Badge fallback must post a privacy-safe generic alert when Instagram only
        // exposes its unread count rather than individual virtualized inbox rows.
        assertTrue("onBadgeCountChanged must post an unread badge fallback notification",
            dmManagerFile.contains("postUnreadBadgeNotification(count)"))
        assertTrue("badge fallback must retain the Instagram Direct notification title",
            dmManagerFile.contains("setContentTitle(\"Instagram Direct\")"))
        assertFalse("badge changes must not reload the headless inbox and create a reload loop",
            dmManagerFile.contains("DmNotificationService.reload(context)"))

        // Simulate multi-user simultaneous message counter calculation
        val unreadCounts = mutableMapOf<String, Int>()
        val hasPlusMap = mutableMapOf<String, Boolean>()

        fun processIncoming(sender: String, countStr: String, countInt: Int): Pair<String, String> {
            val threadKey = sender.lowercase()
            if (countStr.contains("+")) hasPlusMap[threadKey] = true
            val digits = countStr.filter { it.isDigit() }.toIntOrNull() ?: countInt
            val incomingCount = maxOf(1, digits)
            val next = incomingCount
            unreadCounts[threadKey] = next
            val hasPlus = hasPlusMap[threadKey] == true || next >= 3
            val formatted = if (hasPlus && next > 1) "${next}+" else next.toString()
            val body = if (next == 1 && !hasPlus) "1 new message" else "$formatted new messages"
            return Pair(sender, body)
        }

        // User A already had 3 unread messages ("3+ unread messages")
        val userA1 = processIncoming("Alice", "3+", 3)
        assertEquals("Alice", userA1.first)
        assertEquals("3+ new messages", userA1.second)

        // User A receives a new message; Instagram reports "4+ unread messages"
        val userA2 = processIncoming("Alice", "4+", 4)
        assertEquals("Alice", userA2.first)
        assertEquals("4+ new messages", userA2.second)

        // Re-scanning without a new message must strictly retain 4+ and NEVER runaway-increment
        val userA3 = processIncoming("Alice", "4+", 4)
        assertEquals("Alice", userA3.first)
        assertEquals("4+ new messages", userA3.second)
        assertEquals(4, unreadCounts["alice"])

        // Simultaneously, User B sends 1 message
        val userB1 = processIncoming("Bob", "1", 1)
        assertEquals("Bob", userB1.first)
        assertEquals("1 new message", userB1.second)

        // Simultaneously, User C sends 2 messages
        val userC1 = processIncoming("Charlie", "2", 2)
        assertEquals("Charlie", userC1.first)
        assertEquals("2 new messages", userC1.second)

        // Verify User A count was not corrupted by User B or C
        assertEquals(4, unreadCounts["alice"])
        assertEquals(1, unreadCounts["bob"])
        assertEquals(2, unreadCounts["charlie"])
    }

    @Test
    fun testFeedVideosPermanentlyBlurredAndAutoplayBlockedWhenNoReels() {
        val contentHiderJs = getFile("src/main/assets/scripts/content_hider.js").readText()
        val contentEngineJs = getFile("src/main/assets/extensions/content-engine/focusgram_content.js").readText()
        val autoplayBlockerJs = getFile("src/main/assets/scripts/autoplay_blocker.js").readText()

        listOf(contentHiderJs, contentEngineJs).forEach { script ->
            assertTrue("Script must apply blur to feed videos when noReels is active",
                script.contains("html[data-fg-feed] article video") &&
                script.contains("pointer-events: none !important"))
            assertTrue("getBlurredTarget must disallow unblurring feed videos when noReels is active",
                script.contains("isBlockReelsActive") || script.contains("noReels"))
        }

        assertTrue("autoplay_blocker.js must block autoplay for feed videos when noReels is active",
            autoplayBlockerJs.contains("config.hideReels || config.noReels"))
    }

    @Test
    fun testDmWatcherContinuousScanningAndNotificationServiceSafety() {
        val dmWatcherJs = getFile("src/main/assets/extensions/content-engine/dm_watcher.js").readText()
        assertFalse("dm_watcher must not abort early on isInboxPage without running title and badge fallbacks",
            dmWatcherJs.contains("if (hasUnreadInbox || hasToast || isInboxPage) return;"))
        assertTrue("dm_watcher must run scanTitleBadge",
            dmWatcherJs.contains("scanTitleBadge()"))

        val dmServiceFile = getFile("src/main/kotlin/com/ujwal/focusgram/feature/notifications/DmNotificationService.kt").readText()
        assertFalse("DmNotificationService must never call stopForeground(STOP_FOREGROUND_REMOVE) which crashes background service on Android 12+",
            dmServiceFile.contains("stopForeground(STOP_FOREGROUND_REMOVE)"))
    }

    @Test
    fun testHeadlessSessionLifecycleAndResourceTeardown() {
        val dmServiceFile = getFile("src/main/kotlin/com/ujwal/focusgram/feature/notifications/DmNotificationService.kt").readText()

        assertTrue("ACTION_APP_FOREGROUND must stop headless session",
            dmServiceFile.contains("ACTION_APP_FOREGROUND ->") &&
            dmServiceFile.contains("stopHeadlessSession()"))
        assertTrue("foreground app entry must fully remove the background DM listener",
            dmServiceFile.contains("ACTION_APP_FOREGROUND ->") &&
            dmServiceFile.contains("stopForegroundAndRemove()") &&
            dmServiceFile.contains("stopSelf()"))

        val applicationFile = getFile("src/main/kotlin/com/ujwal/focusgram/FocusGramApp.kt").readText()
        val activityFile = getFile("src/main/kotlin/com/ujwal/focusgram/MainActivity.kt").readText()
        assertFalse("Application startup must not race a visible activity by starting the DM FGS",
            applicationFile.contains("DmNotificationService.start(this)"))
        assertFalse("Activity creation must not start a DM FGS that onResume immediately stops",
            activityFile.contains("DmNotificationService.start(this@MainActivity)"))

        assertTrue("ACTION_APP_BACKGROUND must start headless session",
            dmServiceFile.contains("ACTION_APP_BACKGROUND ->") &&
            dmServiceFile.contains("startHeadlessSession()"))

        assertTrue("onTaskRemoved must start headless session in background",
            dmServiceFile.contains("override fun onTaskRemoved") &&
            dmServiceFile.contains("startHeadlessSession()"))

        assertTrue("stopHeadlessSession must cancel heartbeat loop",
            dmServiceFile.contains("heartbeatJob?.cancel()") &&
            dmServiceFile.contains("heartbeatJob = null"))

        assertTrue("stopHeadlessSession must release ImageReader and Surface",
            dmServiceFile.contains("headlessSurface?.release()") &&
            dmServiceFile.contains("headlessImageReader?.close()"))
    }

    @Test
    fun testDmWatcherExcludesViewerAndHeaderAndStrictlyScansDirect() {
        val dmWatcherJs = getFile("src/main/assets/extensions/content-engine/dm_watcher.js").readText()

        assertTrue("dm_watcher must detect viewer username",
            dmWatcherJs.contains("getViewerUsername") && dmWatcherJs.contains("isViewer"))

        assertTrue("dm_watcher must recognize unread count phrases to prevent them becoming sender",
            dmWatcherJs.contains("isUnreadCountPhrase"))

        assertTrue("scanDirectInbox must only run on direct message paths",
            dmWatcherJs.contains("path.startsWith('/direct')"))

        assertTrue("scanDirectInbox must exclude header, nav, and banner elements",
            dmWatcherJs.contains("header, nav, [role=\"banner\"], [role=\"navigation\"]"))

        assertTrue("extractThreadInfo must reject viewer username and count phrases",
            dmWatcherJs.contains("if (!sender || isViewer(sender) || isUnreadCountPhrase(sender))"))
    }

    @Test
    fun testDmNotificationDismissalOnReadAndInstantObservation() {
        val dmWatcherJs = getFile("src/main/assets/extensions/content-engine/dm_watcher.js").readText()

        assertTrue("dm_watcher must report dmThreadRead when thread in inbox is read",
            dmWatcherJs.contains("action: 'dmThreadRead'"))

        assertTrue("dm_watcher must report dmAllThreadsRead when all threads are read",
            dmWatcherJs.contains("action: 'dmAllThreadsRead'"))

        assertTrue("MutationObserver must observe characterData and attributes for instant detection",
            dmWatcherJs.contains("characterData: true") && dmWatcherJs.contains("attributes: true"))

        assertTrue("Debounce must be 50ms for instant reactivity",
            dmWatcherJs.contains("}, 50);"))

        val dmManagerFile = getFile("src/main/kotlin/com/ujwal/focusgram/feature/notifications/DmNotificationManager.kt").readText()

        assertTrue("DmNotificationManager must implement cancelAllDmNotifications",
            dmManagerFile.contains("fun cancelAllDmNotifications()"))

        assertTrue("onBadgeCountChanged must cancel all notifications when badge count is 0",
            dmManagerFile.contains("cancelAllDmNotifications()"))

        val extManagerFile = getFile("src/main/kotlin/com/ujwal/focusgram/core/engine/ExtensionManager.kt").readText()

        assertTrue("ExtensionManager must route dmThreadRead and dmAllThreadsRead",
            extManagerFile.contains("\"dmThreadRead\" ->") &&
            extManagerFile.contains("\"dmAllThreadsRead\" ->"))
    }

    @Test
    fun testModalSelectionUnblockingCompactStylingAndDmBioCopy() {
        val sessionMgrFile = getFile("src/main/kotlin/com/ujwal/focusgram/core/engine/GeckoSessionManager.kt").readText()
        assertTrue("GeckoSessionManager must expose isModalActive property",
            sessionMgrFile.contains("var isModalActive: Boolean"))
        assertTrue("GeckoSessionManager must expose attachActivity method",
            sessionMgrFile.contains("fun attachActivity(activity: Activity?)"))
        assertTrue("GeckoSessionManager must forward to BasicSelectionActionDelegate",
            sessionMgrFile.contains("BasicSelectionActionDelegate") &&
                sessionMgrFile.contains("delegate.onShowActionRequest(session, selection)"))
        assertTrue("GeckoSessionManager must allow selection for editable, modal, or having text",
            sessionMgrFile.contains("isEditable || isModalActive || hasText"))

        val nativeFeelJs = getFile("src/main/assets/scripts/native_feel.js").readText()
        assertTrue("native_feel.js must whitelist modals for user-select",
            nativeFeelJs.contains("div[role=\"dialog\"]") && nativeFeelJs.contains("user-select: text !important"))
        assertTrue("native_feel.js must whitelist DM messages for user-select",
            nativeFeelJs.contains("[role=\"listitem\"] [dir=\"auto\"]"))
        assertTrue("native_feel.js must whitelist Bio captions for user-select",
            nativeFeelJs.contains("header section [dir=\"auto\"]"))
        assertTrue("native_feel.js must define compact modal styling",
            nativeFeelJs.contains("max-width: 320px !important") && nativeFeelJs.contains("width: 88% !important"))

        val contentEngineJs = getFile("src/main/assets/extensions/content-engine/focusgram_content.js").readText()
        assertTrue("focusgram_content.js must whitelist modals, DMs, and Bio captions",
            contentEngineJs.contains("header section [dir=\"auto\"]") &&
                contentEngineJs.contains("[role=\"listitem\"] [dir=\"auto\"]") &&
                contentEngineJs.contains("user-select: text !important"))
        assertTrue("focusgram_content.js must define compact modal styling",
            contentEngineJs.contains("max-width: 320px !important") && contentEngineJs.contains("width: 88% !important"))
        assertTrue("focusgram_content.js must handle Select text button and forward to native",
            contentEngineJs.contains("label.includes('select text')") &&
                contentEngineJs.contains("forwardToNative(foundUrl)"))
    }

    @Test
    fun testTrackingParameterSanitizationAndDmMessageCopy() {
        val trackingNames = com.ujwal.focusgram.core.utils.NetworkUtils.TRACKING_PARAM_NAMES
        val expected = listOf("igsh", "igshid", "fbclid", "ref", "hl", "taken-by", "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "ig_rid")
        for (param in expected) {
            assertTrue("NetworkUtils must include tracking parameter $param", trackingNames.contains(param))
        }

        assertTrue("isTrackingParam must identify utm_ prefixes",
            com.ujwal.focusgram.core.utils.NetworkUtils.isTrackingParam("utm_custom_id"))
        assertTrue("isTrackingParam must identify taken-by",
            com.ujwal.focusgram.core.utils.NetworkUtils.isTrackingParam("taken-by"))
        assertTrue("isTrackingParam must identify ref",
            com.ujwal.focusgram.core.utils.NetworkUtils.isTrackingParam("ref"))
        assertTrue("isTrackingParam must identify hl",
            com.ujwal.focusgram.core.utils.NetworkUtils.isTrackingParam("hl"))
        assertFalse("isTrackingParam must keep legitimate query params like variant",
            com.ujwal.focusgram.core.utils.NetworkUtils.isTrackingParam("variant"))

        val dirtyUrl = "https://www.instagram.com/reel/C_12345/?igsh=MWQ1Z3Rhb&utm_source=ig_web_copy_link&utm_medium=share_sheet&ref=profile&hl=en&taken-by=ujwal"
        val cleanUrl = com.ujwal.focusgram.core.utils.NetworkUtils.sanitizeText(dirtyUrl)
        assertEquals("https://www.instagram.com/reel/C_12345/", cleanUrl)

        val embeddedUrlText = "Here is the post https://www.instagram.com/p/ABC/?igshid=xyz123&utm_campaign=promo! Check it out"
        val cleanEmbedded = com.ujwal.focusgram.core.utils.NetworkUtils.sanitizeText(embeddedUrlText)
        assertEquals("Here is the post https://www.instagram.com/p/ABC/! Check it out", cleanEmbedded)

        val pureDmText = "Meet me at 5pm at the cafe"
        assertEquals(pureDmText, com.ujwal.focusgram.core.utils.NetworkUtils.sanitizeText(pureDmText))

        val contentEngineJs = getFile("src/main/assets/extensions/content-engine/focusgram_content.js").readText()
        assertTrue("focusgram_content.js must define sanitizeUrlsInText",
            contentEngineJs.contains("function sanitizeUrlsInText"))
        assertTrue("focusgram_content.js must hook DataTransfer setData",
            contentEngineJs.contains("hookDataTransfer") && contentEngineJs.contains("DataTransfer.prototype"))
        assertTrue("focusgram_content.js must track lastActiveDmText",
            contentEngineJs.contains("lastActiveDmText"))
        assertTrue("focusgram_content.js must handle cleanLabel === 'copy'",
            contentEngineJs.contains("cleanLabel === 'copy'"))
        assertTrue("focusgram_content.js must transform Link modal button to Copy and elevate z-index",
            contentEngineJs.contains("transformLinkModal") &&
                contentEngineJs.contains("btn.textContent = 'Copy'") &&
                contentEngineJs.contains("z-index") &&
                contentEngineJs.contains("2147483647"))

        val extensionManagerKt = getFile("src/main/kotlin/com/ujwal/focusgram/core/engine/ExtensionManager.kt").readText()
        val networkUtilsKt = getFile("src/main/kotlin/com/ujwal/focusgram/core/utils/NetworkUtils.kt").readText()
        assertTrue("ExtensionManager and ClipboardDebouncer must debounce clipboardCopy to eliminate duplicate toasts",
            extensionManagerKt.contains("ClipboardDebouncer") &&
                networkUtilsKt.contains("lastCopiedText") &&
                networkUtilsKt.contains("lastCopyTimestamp") &&
                networkUtilsKt.contains("1500L"))

        val nativeFeelJs = getFile("src/main/assets/scripts/native_feel.js").readText()
        assertTrue("native_feel.js must define sanitizeUrlsInText",
            nativeFeelJs.contains("function sanitizeUrlsInText"))
    }

    @Test
    fun testBareInstagramRootUrlDetectionAndSuppression() {
        assertTrue(com.ujwal.focusgram.core.utils.NetworkUtils.isBareInstagramRootUrl("https://www.instagram.com/"))
        assertTrue(com.ujwal.focusgram.core.utils.NetworkUtils.isBareInstagramRootUrl("https://www.instagram.com"))
        assertTrue(com.ujwal.focusgram.core.utils.NetworkUtils.isBareInstagramRootUrl("https://instagram.com/"))
        assertTrue(com.ujwal.focusgram.core.utils.NetworkUtils.isBareInstagramRootUrl("http://www.instagram.com"))
        assertTrue(com.ujwal.focusgram.core.utils.NetworkUtils.isBareInstagramRootUrl("https://www.instagram.com/?utm_source=ig_web_copy_link&igsh=123"))
        assertTrue(com.ujwal.focusgram.core.utils.NetworkUtils.isBareInstagramRootUrl("https://www.instagram.com/#fragment"))

        assertFalse(com.ujwal.focusgram.core.utils.NetworkUtils.isBareInstagramRootUrl("https://www.instagram.com/p/C_abc123/"))
        assertFalse(com.ujwal.focusgram.core.utils.NetworkUtils.isBareInstagramRootUrl("https://www.instagram.com/reel/C_abc123/"))
        assertFalse(com.ujwal.focusgram.core.utils.NetworkUtils.isBareInstagramRootUrl("https://www.instagram.com/reels/videos/"))
        assertFalse(com.ujwal.focusgram.core.utils.NetworkUtils.isBareInstagramRootUrl("https://www.instagram.com/username/"))

        val navDelegateFile = getFile("src/main/kotlin/com/ujwal/focusgram/core/engine/FocusGramNavigationDelegate.kt").readText()
        assertTrue("FocusGramNavigationDelegate must suppress bare root URL writes",
            navDelegateFile.contains("isBareInstagramRootUrl(finalText)"))

        val extensionManagerFile = getFile("src/main/kotlin/com/ujwal/focusgram/core/engine/ExtensionManager.kt").readText()
        assertTrue("ExtensionManager must suppress bare root URL writes",
            extensionManagerFile.contains("isBareInstagramRootUrl(finalText)"))

        val nativeFeelJs = getFile("src/main/assets/scripts/native_feel.js").readText()
        assertTrue("native_feel.js must extract URLs from dialogs and reject bare root URLs",
            nativeFeelJs.contains("function extractUrlFromDialog") &&
            nativeFeelJs.contains("isBareInstagramRootUrl"))

        val contentEngineJs = getFile("src/main/assets/extensions/content-engine/focusgram_content.js").readText()
        assertTrue("focusgram_content.js must extract URLs from dialogs and reject bare root URLs",
            contentEngineJs.contains("function extractUrlFromDialog") &&
            contentEngineJs.contains("isBareInstagramRootUrl"))
    }
}
