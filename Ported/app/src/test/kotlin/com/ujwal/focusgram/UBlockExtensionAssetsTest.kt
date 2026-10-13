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
}
