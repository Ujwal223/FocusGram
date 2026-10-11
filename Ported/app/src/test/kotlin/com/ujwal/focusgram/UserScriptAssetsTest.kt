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
        "reel_metadata_extractor.js"
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
}
