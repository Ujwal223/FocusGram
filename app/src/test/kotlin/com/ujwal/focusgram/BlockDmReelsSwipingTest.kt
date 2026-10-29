package com.ujwal.focusgram

import com.ujwal.focusgram.core.model.FocusSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * BlockDmReelsSwipingTest
 * Verifies data model, default state, and WebExtension asset integrity
 * for the "Block Reels Swiping in DMs" feature.
 */
class BlockDmReelsSwipingTest {

    @Test
    fun testDefaultSettingsValue() {
        val settings = FocusSettings()
        assertTrue("blockDmReelsSwiping must default to true", settings.blockDmReelsSwiping)
    }

    @Test
    fun testCopySettingsToggle() {
        val original = FocusSettings(blockDmReelsSwiping = true)
        val toggled = original.copy(blockDmReelsSwiping = false)
        assertFalse("Toggled setting must be false", toggled.blockDmReelsSwiping)

        val restored = toggled.copy(blockDmReelsSwiping = true)
        assertTrue("Restored setting must be true", restored.blockDmReelsSwiping)
    }

    @Test
    fun testReelLockExtensionAssetsRules() {
        val baseDir = File("src/main/assets/reellock")
        val altDir = File("app/src/main/assets/reellock")
        val dir = if (baseDir.exists()) baseDir else altDir

        assertTrue("assets/reellock directory must exist", dir.exists())

        val manifest = File(dir, "manifest.json").readText()
        assertTrue("Manifest must declare reellock@focusgram.app", manifest.contains("reellock@focusgram.app"))
        assertTrue("Manifest must run at document_start", manifest.contains("\"run_at\": \"document_start\""))

        val bg = File(dir, "bg.js").readText()
        assertTrue("bg.js must cancel feed GraphQL queries", bg.contains("cancel: true"))
        assertTrue("bg.js must check fb_api_req_friendly_name or doc_id", bg.contains("NAME_RE.test(name)") || bg.contains("DOC_IDS.has(doc)"))
        assertTrue("bg.js must target graphql/query endpoints", bg.contains("graphql/query"))

        val script = File(dir, "reellock.js").readText()
        assertTrue("Script must lock container", script.contains("[data-fg-lock]"))
        assertTrue("Script must check /direct/t/", script.contains("/direct/t/"))
        assertTrue("Script must listen to touchmove and wheel with passive: false", script.contains("passive: false"))
        assertTrue("Script must check toggle attribute data-fg-block-dm-reels", script.contains("data-fg-block-dm-reels"))
        // Sent reel video in direct messages must play normally; only continuous feed scrolling is blocked.
        assertFalse("Script must NOT pause sent reel video", script.contains("v.pause()"))
        assertFalse("Script must NOT mute sent reel video", script.contains("v.muted = true"))
    }
}
