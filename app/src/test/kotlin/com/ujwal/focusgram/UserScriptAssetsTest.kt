package com.ujwal.focusgram

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * UserScriptAssetsTest
 * Verifies that all required client-side injection scripts exist in assets/scripts/
 * and contain valid code structure.
 */
class UserScriptAssetsTest {

    private val expectedScripts = listOf(
        "content_hider.js",
        "autoplay_blocker.js",
        "grayscale.js",
        "spa_navigation_monitor.js",
        "native_feel.js",
        "reel_metadata_extractor.js",
        "fetch_interceptor.js",
        "media_downloader.js",
        "stealth_instagram.js"
    )

    @Test
    fun testAllUserScriptsExistAndNonEmpty() {
        // Path relative to execution root or app directory
        val baseAssetsDir = File("src/main/assets/scripts")
        val altAssetsDir = File("app/src/main/assets/scripts")
        val assetsDir = if (baseAssetsDir.exists()) baseAssetsDir else altAssetsDir

        assertTrue("assets/scripts directory must exist", assetsDir.exists())

        expectedScripts.forEach { scriptName ->
            val scriptFile = File(assetsDir, scriptName)
            assertTrue("Script file $scriptName must exist", scriptFile.exists())
            assertTrue("Script file $scriptName must not be empty", scriptFile.length() > 0)

            val content = scriptFile.readText()
            assertTrue("$scriptName should contain function or IIFE", content.contains("function") || content.contains("=>"))
        }
    }

    @Test
    fun testStealthInstagramContainsGhostModeInterceptors() {
        val baseAssetsDir = File("src/main/assets/scripts")
        val altAssetsDir = File("app/src/main/assets/scripts")
        val assetsDir = if (baseAssetsDir.exists()) baseAssetsDir else altAssetsDir
        val stealthScript = File(assetsDir, "stealth_instagram.js")

        assertTrue("stealth_instagram.js must exist", stealthScript.exists())
        val content = stealthScript.readText()

        assertTrue("stealth_instagram.js must intercept fetch", content.contains("window.fetch"))
        assertTrue("stealth_instagram.js must intercept XHR", content.contains("XMLHttpRequest.prototype.send"))
        assertTrue("stealth_instagram.js must intercept WebSocket", content.contains("WebSocket.prototype.send"))
        assertTrue("stealth_instagram.js must spoof hasFocus", content.contains("document.hasFocus"))
        assertTrue("stealth_instagram.js must contain story seen mutation patterns", content.contains("xdt_mark_story_reel_seen"))
        assertTrue("stealth_instagram.js must contain direct typing patterns", content.contains("direct_v2/threads/broadcast/typing"))
        assertTrue("stealth_instagram.js must contain direct seen patterns", content.contains("directmarkasseen"))
    }
}
