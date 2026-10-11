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
        assertTrue("manifest.json must declare name uBlock Origin", manifestContent.contains("uBlock Origin"))

        val bgFile = File(uBlockDir, "background.js")
        assertTrue("background.js must exist", bgFile.exists())

        val contentFile = File(uBlockDir, "content.js")
        assertTrue("content.js must exist", contentFile.exists())
    }
}
