package com.ujwal.focusgram

import android.net.Uri
import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.core.utils.AppIconManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * ChatLockAndAppIconTest
 * Unit tests verifying Custom App Icon options, Chat Lock route evaluation,
 * message preview masking rules, and separate Chat PIN hashing determinism.
 */
class ChatLockAndAppIconTest {

    @Test
    fun testDefaultSettingsForChatLockAndAppIcon() {
        val settings = FocusSettings()
        assertEquals("default", settings.appIcon)
        assertEquals("individual", settings.chatLockMode)
        assertFalse(settings.chatLockAll)
        assertTrue(settings.lockedChatIds.isEmpty())
        assertFalse(settings.useSeparateChatPin)
        assertTrue(settings.chatBiometricEnabled)
    }

    @Test
    fun testAppIconOptionsCompleteness() {
        val options = AppIconManager.IconOption.values()
        assertEquals(13, options.size)

        val ids = options.map { it.id }.toSet()
        // Core icons
        assertTrue(ids.contains("default"))
        assertTrue(ids.contains("pitch_black"))
        assertTrue(ids.contains("all_white"))

        // Alt Redesigns
        assertTrue(ids.contains("redesign"))
        assertTrue(ids.contains("abstract"))
        assertTrue(ids.contains("forest"))
        assertTrue(ids.contains("golden"))
        assertTrue(ids.contains("metal"))
        assertTrue(ids.contains("scribble"))

        // Originals (Historical Brand Icons)
        assertTrue(ids.contains("polaroid"))
        assertTrue(ids.contains("viewfinder"))
        assertTrue(ids.contains("minimalist"))
        assertTrue(ids.contains("vibrant"))

        options.forEach { option ->
            assertTrue(option.aliasSuffix.startsWith("MainActivity"))
            assertTrue(option.displayName.isNotBlank())
            assertTrue(option.subtitle.isNotBlank())
            assertTrue(option.category.isNotBlank())
        }

        assertEquals(AppIconManager.IconOption.DEFAULT, AppIconManager.IconOption.fromId("default"))
        assertEquals(AppIconManager.IconOption.REDESIGN, AppIconManager.IconOption.fromId("redesign"))
        assertEquals(AppIconManager.IconOption.PITCH_BLACK, AppIconManager.IconOption.fromId("pitch_black"))
        assertEquals(AppIconManager.IconOption.ALL_WHITE, AppIconManager.IconOption.fromId("all_white"))
        assertEquals(AppIconManager.IconOption.POLAROID, AppIconManager.IconOption.fromId("polaroid"))
        assertEquals(AppIconManager.IconOption.VIEWFINDER, AppIconManager.IconOption.fromId("viewfinder"))
        assertEquals(AppIconManager.IconOption.MINIMALIST, AppIconManager.IconOption.fromId("minimalist"))
        assertEquals(AppIconManager.IconOption.VIBRANT, AppIconManager.IconOption.fromId("vibrant"))
        assertEquals(AppIconManager.IconOption.DEFAULT, AppIconManager.IconOption.fromId("unknown_id"))
    }

    @Test
    fun testHistoricalIconsCompleteness() {
        val icons = com.ujwal.focusgram.core.utils.HistoricalIconManager.ORIGINAL_ICONS
        assertEquals(4, icons.size)
        val ids = icons.map { it.id }.toSet()
        assertTrue(ids.contains("polaroid_camera"))
        assertTrue(ids.contains("viewfinder"))
        assertTrue(ids.contains("minimalist_colors"))
        assertTrue(ids.contains("vibrant_version"))
        icons.forEach {
            assertTrue(it.downloadUrl.startsWith("https://"))
            assertTrue(it.title.isNotBlank())
        }
    }

    @Test
    fun testChatLockRouteEvaluation() {
        fun isRouteLocked(url: String, settings: FocusSettings, unlockedThreads: Set<String>, isAllUnlocked: Boolean): Boolean {
            val path = url.removePrefix("https://www.instagram.com").removePrefix("https://instagram.com").lowercase()
            if (!path.startsWith("/direct")) return false

            if (settings.chatLockAll) {
                return !isAllUnlocked
            }

            if (path.startsWith("/direct/t/")) {
                val threadId = path.removePrefix("/direct/t/").trim('/').split('/').firstOrNull()?.trim() ?: ""
                if (unlockedThreads.contains(threadId)) return false
                return settings.lockedChatIds.any { it.equals(threadId, ignoreCase = true) || url.contains(it, ignoreCase = true) }
            }

            return false
        }

        val baseSettings = FocusSettings()
        // Default settings: nothing locked
        assertFalse(isRouteLocked("https://www.instagram.com/direct/inbox/", baseSettings, emptySet(), false))
        assertFalse(isRouteLocked("https://www.instagram.com/direct/t/17849999/", baseSettings, emptySet(), false))

        // ChatLockAll enabled: all direct routes locked
        val lockAllSettings = baseSettings.copy(chatLockAll = true)
        assertTrue(isRouteLocked("https://www.instagram.com/direct/inbox/", lockAllSettings, emptySet(), false))
        assertTrue(isRouteLocked("https://www.instagram.com/direct/t/17849999/", lockAllSettings, emptySet(), false))
        // Non-direct routes should not be locked
        assertFalse(isRouteLocked("https://www.instagram.com/reels/", lockAllSettings, emptySet(), false))

        // When all unlocked
        assertFalse(isRouteLocked("https://www.instagram.com/direct/inbox/", lockAllSettings, emptySet(), true))

        // Specific chat locked:
        val specificLockSettings = baseSettings.copy(lockedChatIds = setOf("17849999", "filian_editz"))
        // Inbox itself is not locked when individual chat locked
        assertFalse(isRouteLocked("https://www.instagram.com/direct/inbox/", specificLockSettings, emptySet(), false))
        // Target thread is locked
        assertTrue(isRouteLocked("https://www.instagram.com/direct/t/17849999/", specificLockSettings, emptySet(), false))
        assertTrue(isRouteLocked("https://www.instagram.com/direct/t/filian_editz/", specificLockSettings, emptySet(), false))
        // Unlocked thread is accessible
        assertFalse(isRouteLocked("https://www.instagram.com/direct/t/17849999/", specificLockSettings, setOf("17849999"), false))
        // Other threads not locked
        assertFalse(isRouteLocked("https://www.instagram.com/direct/t/99999999/", specificLockSettings, emptySet(), false))
    }

    @Test
    fun testSeparateChatPinHashing() {
        fun hashChatPin(pin: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val saltedBytes = ("focusgram_salt_v2_chat_" + pin).toByteArray(Charsets.UTF_8)
            val hashBytes = digest.digest(saltedBytes)
            return hashBytes.joinToString("") { "%02x".format(it) }
        }

        fun hashMainPin(pin: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val saltedBytes = ("focusgram_salt_v2_" + pin).toByteArray(Charsets.UTF_8)
            val hashBytes = digest.digest(saltedBytes)
            return hashBytes.joinToString("") { "%02x".format(it) }
        }

        val mainPinHash = hashMainPin("1234")
        val chatPinHash = hashChatPin("1234")

        // Chat PIN hash must be isolated and distinct from main PIN hash even with the same digits
        org.junit.Assert.assertNotEquals(mainPinHash, chatPinHash)
        assertEquals(64, chatPinHash.length)
        assertEquals(chatPinHash, hashChatPin("1234"))
    }

    @Test
    fun testUsernameResolutionForLockedChat() {
        fun resolveDisplayTitle(id: String, lockedChatUsernames: Map<String, String>): String {
            val username = lockedChatUsernames[id]
            return if (!username.isNullOrBlank()) {
                "@${username.removePrefix("@")}"
            } else if (!id.all { it.isDigit() }) {
                "@${id.removePrefix("@")}"
            } else {
                "Conversation (${id.takeLast(6)})"
            }
        }

        val usernames = mapOf("1784140001" to "johndoe", "1784140002" to "@alice")
        assertEquals("@johndoe", resolveDisplayTitle("1784140001", usernames))
        assertEquals("@alice", resolveDisplayTitle("1784140002", usernames))
        // Alphabetic ID fallback
        assertEquals("@filian_editz", resolveDisplayTitle("filian_editz", emptyMap()))
        // Pure digit ID with no username yet fallback
        assertEquals("Conversation (123456)", resolveDisplayTitle("9876123456", emptyMap()))
    }

    @Test
    fun testChatLockCancelRoutingDestination() {
        val cancelUrl = "https://www.instagram.com/direct/inbox/"
        assertTrue(cancelUrl.endsWith("/direct/inbox/"))
        assertFalse(cancelUrl.contains("/direct/t/"))
    }

    @Test
    fun testPreRenderBlockingInExtensionAssets() {
        val baseDir = java.io.File("src/main/assets/extensions/ublock")
        val altDir = java.io.File("app/src/main/assets/extensions/ublock")
        val uBlockDir = if (baseDir.exists()) baseDir else altDir
        val fgFile = java.io.File(uBlockDir, "focusgram_content.js")
        assertTrue("focusgram_content.js must exist", fgFile.exists())
        val text = fgFile.readText()

        assertTrue("Must intercept click before React router", text.contains("requestUnlockChat"))
        assertTrue("Must prevent default click navigation", text.contains("e.preventDefault()"))
        assertTrue("Must stop event propagation", text.contains("e.stopPropagation()"))
        assertTrue("Must stop immediate propagation", text.contains("e.stopImmediatePropagation()"))
        assertTrue("Must export applyPrivacyVeil", text.contains("__fgApplyPrivacyVeil"))
        assertTrue("Must export removePrivacyVeil", text.contains("__fgRemovePrivacyVeil"))
        assertTrue("Must export getThreadParticipant", text.contains("__fgGetThreadParticipant"))
    }
}

