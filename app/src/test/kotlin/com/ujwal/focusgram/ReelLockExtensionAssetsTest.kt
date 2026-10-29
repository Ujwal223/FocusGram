package com.ujwal.focusgram

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ReelLockExtensionAssetsTest
 * Verifies that the built-in Reel Lock GeckoView WebExtension in assets/reellock/
 * contains valid manifest.json, background script, and content script.
 */
class ReelLockExtensionAssetsTest {

    @Test
    fun testReelLockExtensionIntegrity() {
        val baseDir = File("src/main/assets/reellock")
        val altDir = File("app/src/main/assets/reellock")
        val lockDir = if (baseDir.exists()) baseDir else altDir

        assertTrue("assets/reellock directory must exist", lockDir.exists())

        val manifestFile = File(lockDir, "manifest.json")
        assertTrue("manifest.json must exist", manifestFile.exists())
        val manifestContent = manifestFile.readText()
        assertTrue("manifest.json must declare manifest_version 2", manifestContent.contains("\"manifest_version\": 2"))
        assertTrue("manifest.json must declare gecko id", manifestContent.contains("reellock@focusgram.app"))

        val bgFile = File(lockDir, "bg.js")
        assertTrue("bg.js must exist", bgFile.exists())
        val bgContent = bgFile.readText()
        assertTrue("bg.js must contain NAME_RE regex", bgContent.contains("NAME_RE"))
        assertTrue("bg.js must contain DOC_IDS", bgContent.contains("DOC_IDS"))
        assertTrue("bg.js must handle fg-reel heartbeat", bgContent.contains("fg-reel"))

        val contentFile = File(lockDir, "reellock.js")
        assertTrue("reellock.js must exist", contentFile.exists())
        val content = contentFile.readText()
        assertTrue("reellock.js must check /direct/t/", content.contains("/direct/t/"))
        assertTrue("reellock.js must lock container scroll", content.contains("data-fg-lock"))
        assertTrue("reellock.js must observe data-fg-block-dm-reels toggle", content.contains("data-fg-block-dm-reels"))
    }
}
