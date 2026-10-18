package com.ujwal.focusgram

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * UBlockExtensionAssetsTest
 * Verifies that the embedded uBlock Origin WebExtension contains manifest.json,
 * background script, and content script.
 */
class UBlockExtensionAssetsTest {

    @Test
    fun testUBlockExtensionIntegrity() {
        val baseDir = File("src/main/assets/extensions/ublock")
        val altDir = File("app/src/main/assets/extensions/ublock")
        val uBlockDir = if (baseDir.exists()) baseDir else altDir

        assertTrue("assets/extensions/ublock directory must exist", uBlockDir.exists())

        val manifestFile = File(uBlockDir, "manifest.json")
        assertTrue("manifest.json must exist", manifestFile.exists())
        val manifestContent = manifestFile.readText()
        assertTrue("manifest.json must declare manifest_version", manifestContent.contains("manifest_version"))
        assertTrue("manifest.json must declare name", manifestContent.contains("\"name\":"))

        val bgFile = File(uBlockDir, "background.js")
        assertTrue("background.js must exist", bgFile.exists())

        val contentFile = File(uBlockDir, "content.js")
        assertTrue("content.js must exist", contentFile.exists())

        val focusgramContentFile = File(uBlockDir, "focusgram_content.js")
        assertTrue("focusgram_content.js must exist", focusgramContentFile.exists())

        val ghostFile = File(uBlockDir, "ghost.js")
        assertTrue("ghost.js must exist", ghostFile.exists())
    }

    @Test
    fun testReelsOverlayAndLayoutIntegrity() {
        val baseDir = File("src/main/assets/extensions/ublock")
        val altDir = File("app/src/main/assets/extensions/ublock")
        val uBlockDir = if (baseDir.exists()) baseDir else altDir
        val fgFile = File(uBlockDir, "focusgram_content.js")
        assertTrue("focusgram_content.js must exist", fgFile.exists())
        val text = fgFile.readText()

        assertTrue("Must define --fg-reel-bottom-offset", text.contains("--fg-reel-bottom-offset"))
        assertTrue("Must define --fg-reel-scrubber-offset", text.contains("--fg-reel-scrubber-offset"))
        assertTrue("Must contain [data-fg-reel-overlay]", text.contains("[data-fg-reel-overlay]"))
        assertTrue("Must contain [data-fg-reel-actions]", text.contains("[data-fg-reel-actions]"))
        assertTrue("Must contain [data-fg-reel-card]", text.contains("[data-fg-reel-card]"))
        assertTrue("Must contain a[href*=\"/audio/\"] styling", text.contains("a[href*=\"/audio/\"]"))
    }

    @Test
    fun testLiveBioFontStylistIntegrity() {
        val baseDir = File("src/main/assets/extensions/ublock")
        val altDir = File("app/src/main/assets/extensions/ublock")
        val uBlockDir = if (baseDir.exists()) baseDir else altDir
        val fgFile = File(uBlockDir, "focusgram_content.js")
        assertTrue("focusgram_content.js must exist", fgFile.exists())
        val text = fgFile.readText()

        assertTrue("Must define fg-bio-font-toolbar", text.contains("fg-bio-font-toolbar"))
        assertTrue("Must intercept beforeinput event", text.contains("beforeinput"))
        assertTrue("Must support mathematical bold glyphs (0x1D400)", text.contains("0x1D400"))
        assertTrue("Must support mathematical monospace glyphs (0x1D670)", text.contains("0x1D670"))
        assertTrue("Must include surrogate pair counter (150 limit)", text.contains("150"))

        val scriptBase = File("src/main/assets/scripts")
        val scriptAlt = File("app/src/main/assets/scripts")
        val scriptDir = if (scriptBase.exists()) scriptBase else scriptAlt
        val nativeFeelFile = File(scriptDir, "native_feel.js")
        assertTrue("native_feel.js must exist", nativeFeelFile.exists())
        val nativeFeelText = nativeFeelFile.readText()
        assertTrue("native_feel.js must target pepBio", nativeFeelText.contains("pepBio"))
        assertTrue("native_feel.js must define fg-bio-inline-bar", nativeFeelText.contains("fg-bio-inline-bar"))
        assertTrue("native_feel.js must support beforeinput live typing", nativeFeelText.contains("beforeinput"))
    }

    @Test
    fun testStoryViewerSearchIntegrity() {
        val baseDir = File("src/main/assets/extensions/ublock")
        val altDir = File("app/src/main/assets/extensions/ublock")
        val uBlockDir = if (baseDir.exists()) baseDir else altDir

        val bgFile = File(uBlockDir, "background.js")
        val bgText = bgFile.readText()
        assertTrue("background.js must capture list_reel_media_viewer", bgText.contains("list_reel_media_viewer"))
        assertTrue("background.js must manage storyViewersCache", bgText.contains("storyViewersCache"))

        val fgFile = File(uBlockDir, "focusgram_content.js")
        val fgText = fgFile.readText()
        assertTrue("focusgram_content.js must define fg-story-search-card", fgText.contains("fg-story-search-card"))
        assertTrue("focusgram_content.js must define fg-story-search-input", fgText.contains("fg-story-search-input"))
        assertTrue("focusgram_content.js must handle story-viewers-detected", fgText.contains("story-viewers-detected"))

        val scriptBase = File("src/main/assets/scripts")
        val scriptAlt = File("app/src/main/assets/scripts")
        val scriptDir = if (scriptBase.exists()) scriptBase else scriptAlt
        val fetchInterceptorFile = File(scriptDir, "fetch_interceptor.js")
        assertTrue("fetch_interceptor.js must exist", fetchInterceptorFile.exists())
        val fetchText = fetchInterceptorFile.readText()
        assertTrue("fetch_interceptor.js must detect list_reel_media_viewer", fetchText.contains("list_reel_media_viewer"))
        assertTrue("fetch_interceptor.js must define fg-story-search-card", fetchText.contains("fg-story-search-card"))
        assertTrue("fetch_interceptor.js must define fg-story-search-input", fetchText.contains("fg-story-search-input"))
    }

    @Test
    fun testChatLockAndPreviewMaskingIntegrity() {
        val baseDir = File("src/main/assets/extensions/ublock")
        val altDir = File("app/src/main/assets/extensions/ublock")
        val uBlockDir = if (baseDir.exists()) baseDir else altDir

        val fgFile = File(uBlockDir, "focusgram_content.js")
        assertTrue("focusgram_content.js must exist", fgFile.exists())
        val fgText = fgFile.readText()
        assertTrue("focusgram_content.js must define focusgram-chat-privacy-veil", fgText.contains("focusgram-chat-privacy-veil"))
        assertTrue("focusgram_content.js must define syncChatLockSettings", fgText.contains("syncChatLockSettings"))
        assertTrue("focusgram_content.js must define maskInboxChatPreviews", fgText.contains("maskInboxChatPreviews"))
        assertTrue("focusgram_content.js must handle unlockChat action", fgText.contains("unlockChat"))

        val scriptBase = File("src/main/assets/scripts")
        val scriptAlt = File("app/src/main/assets/scripts")
        val scriptDir = if (scriptBase.exists()) scriptBase else scriptAlt
        val fetchInterceptorFile = File(scriptDir, "fetch_interceptor.js")
        assertTrue("fetch_interceptor.js must exist", fetchInterceptorFile.exists())
        val fetchText = fetchInterceptorFile.readText()
        assertTrue("fetch_interceptor.js must NOT intercept direct_v2/inbox (client-side DOM only)", !fetchText.contains("direct_v2/inbox"))
    }
}
