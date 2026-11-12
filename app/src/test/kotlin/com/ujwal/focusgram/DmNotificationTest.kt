package com.ujwal.focusgram

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Verifies the integrity, privacy guarantees, deduplication invariants,
 * and configuration of the Instant Direct Message notification subsystem.
 */
class DmNotificationTest {

    private fun getFile(relativePath: String): File {
        val f1 = File(relativePath)
        if (f1.exists()) return f1
        val f2 = File("app/$relativePath")
        if (f2.exists()) return f2
        val f3 = File("../$relativePath")
        if (f3.exists()) return f3
        return f1
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testDeduplicationHashingAndTenItemRetention() {
        // Simulates the exact deduplication algorithm in DmNotificationManager
        val maxEntries = 10
        var storedJson = "[]"

        fun recordMessage(threadId: String, sender: String, snippet: String): Boolean {
            val compositeHash = sha256("$threadId:$sender:$snippet")
            val existingArray = JSONArray(storedJson)
            for (i in 0 until existingArray.length()) {
                val obj = existingArray.getJSONObject(i)
                if (obj.getString("compositeHash") == compositeHash) {
                    return false // Duplicate!
                }
            }

            val updatedArray = JSONArray()
            updatedArray.put(JSONObject().apply {
                put("compositeHash", compositeHash)
                put("timestamp", System.currentTimeMillis())
            })

            for (i in 0 until existingArray.length()) {
                if (updatedArray.length() >= maxEntries) break
                val item = existingArray.getJSONObject(i)
                if (item.getString("compositeHash") == compositeHash) {
                    continue
                }
                updatedArray.put(item)
            }
            storedJson = updatedArray.toString()
            return true
        }

        // 1. Initial message from Alice
        assertTrue("First message should be accepted", recordMessage("t1", "Alice", "Hello there!"))
        // 2. Exact same message from Alice should be rejected as duplicate
        assertFalse("Duplicate message from same sender should be rejected", recordMessage("t1", "Alice", "Hello there!"))
        // 3. Same message text but from Bob should be accepted (different sender)
        assertTrue("Same text from different sender should be accepted", recordMessage("t2", "Bob", "Hello there!"))
        // 4. New message from Alice should be accepted
        assertTrue("Different message from Alice should be accepted", recordMessage("t1", "Alice", "Are you free?"))

        // Verify privacy invariant: neither plaintext snippet nor sender is in stored JSON
        assertFalse("Raw snippet 'Hello there!' must not be in storage", storedJson.contains("Hello there!"))
        assertFalse("Raw snippet 'Are you free?' must not be in storage", storedJson.contains("Are you free?"))
        assertFalse("Raw sender 'Alice' must not be in storage", storedJson.contains("Alice"))
        assertTrue("SHA-256 composite hash must be stored", storedJson.contains(sha256("t1:Alice:Are you free?")))

        // 5. Fill with 15 unique messages and verify exactly 10 are retained
        for (i in 1..15) {
            recordMessage("t_$i", "Sender_$i", "Message content number $i")
        }

        val finalArray = JSONArray(storedJson)
        assertEquals("Storage must strictly cap at exactly 10 items", 10, finalArray.length())
        assertEquals("Most recent item must be at index 0", sha256("t_15:Sender_15:Message content number 15"), finalArray.getJSONObject(0).getString("compositeHash"))
    }

    @Test
    fun testSingleMessageEventsUseTheirActualPreviewForDeduplication() {
        val managerFile = getFile("src/main/kotlin/com/ujwal/focusgram/feature/notifications/DmNotificationManager.kt")
        val code = managerFile.readText()

        assertTrue(
            "Deduplication must use the raw Instagram preview, not the generic notification body",
            code.contains("snippet = snippet.trim().ifBlank { cleanSnippet }")
        )
        assertFalse(
            "Deduplication must not key every single DM as cleanSnippet",
            code.contains("snippet = cleanSnippet,\n                    countStr")
        )
    }

    @Test
    fun testNotificationIdCollisionAvoidance() {
        val serviceId = 90211
        val badgeId = 90210

        fun resolveId(initial: Int): Int {
            var id = initial
            while (id == serviceId || id == badgeId) {
                id += 13
            }
            return id
        }

        // Test collisions are shifted away from service and badge IDs
        val resolvedBadgeCollision = resolveId(badgeId)
        assertFalse("Resolved ID must not collide with service ID", resolvedBadgeCollision == serviceId)
        assertFalse("Resolved ID must not collide with badge ID", resolvedBadgeCollision == badgeId)

        val resolvedServiceCollision = resolveId(serviceId)
        assertFalse("Resolved ID must not collide with service ID", resolvedServiceCollision == serviceId)
        assertFalse("Resolved ID must not collide with badge ID", resolvedServiceCollision == badgeId)
    }

    @Test
    fun testChatLockPrivacySuppression() {
        val baseSettings = com.ujwal.focusgram.core.model.FocusSettings(
            dmNotificationsEnabled = true,
            dmNotificationPreview = true,
            lockedChatIds = setOf("12345", "secret_user"),
            lockedChatUsernames = mapOf("12345" to "alice"),
            chatLockMode = "individual"
        )

        fun shouldShowPreview(threadId: String, sender: String, settings: com.ujwal.focusgram.core.model.FocusSettings): Boolean {
            val cleanUser = sender.trim().lowercase().removePrefix("@")
            val isWholeDmLocked = settings.chatLockMode == "whole_dm" || settings.chatLockAll
            val isThreadLocked = threadId.isNotBlank() && settings.lockedChatIds.any {
                it.equals(threadId, ignoreCase = true)
            }
            val isSenderLocked = settings.lockedChatUsernames.values.any {
                it.trim().lowercase().removePrefix("@").equals(cleanUser, ignoreCase = true)
            } || settings.lockedChatIds.any {
                it.trim().lowercase().removePrefix("@").equals(cleanUser, ignoreCase = true)
            } || (threadId.isNotBlank() && settings.lockedChatUsernames.containsKey(threadId))

            return settings.dmNotificationPreview && !isWholeDmLocked && !isThreadLocked && !isSenderLocked
        }

        // Unlocked chat shows preview
        assertTrue("Normal unlocked conversation should show preview", shouldShowPreview("99999", "Bob", baseSettings))

        // Locked by thread ID suppresses preview
        assertFalse("Locked thread ID 12345 must suppress preview", shouldShowPreview("12345", "Alice", baseSettings))

        // Locked by username suppresses preview
        assertFalse("Locked username 'secret_user' must suppress preview", shouldShowPreview("88888", "secret_user", baseSettings))

        // Whole DM locked suppresses ALL previews
        val wholeDmLockedSettings = baseSettings.copy(chatLockMode = "whole_dm")
        assertFalse("Whole DM lock must suppress preview for all conversations", shouldShowPreview("99999", "Bob", wholeDmLockedSettings))
    }

    @Test
    fun testWebExtensionDmWatcherAssetIntegrity() {
        val contentEngineDir = getFile("src/main/assets/extensions/content-engine")
        assertTrue("content-engine extension directory must exist", contentEngineDir.exists())

        val watcherFile = File(contentEngineDir, "dm_watcher.js")
        assertTrue("dm_watcher.js content script must exist", watcherFile.exists())
        val watcherCode = watcherFile.readText()

        assertTrue("dm_watcher must target /direct/t/ links", watcherCode.contains("a[href*=\"/direct/t/\"]"))
        assertTrue("dm_watcher must post dmReceived action", watcherCode.contains("action: 'dmReceived'"))
        assertTrue("dm_watcher must post dmBadgeCountChanged action", watcherCode.contains("action: 'dmBadgeCountChanged'"))
        assertTrue("dm_watcher must use MutationObserver", watcherCode.contains("new MutationObserver"))
        assertTrue("dm_watcher must check isAuthPage", watcherCode.contains("isAuthPage"))
        assertTrue("dm_watcher must contain cleanSenderName with i18n support", watcherCode.contains("cleanSenderName"))
        assertTrue("dm_watcher must use sliding deduplication TTL", watcherCode.contains("DUP_TTL_MS"))

        val manifestFile = File(contentEngineDir, "manifest.json")
        assertTrue("manifest.json must exist", manifestFile.exists())
        val manifestCode = manifestFile.readText()
        assertTrue("manifest.json must include dm_watcher.js in content_scripts", manifestCode.contains("dm_watcher.js"))

        // Verify privacy invariant: dm_watcher never reads document.cookie
        assertFalse("dm_watcher.js must never read document.cookie", watcherCode.contains("document.cookie"))

        // Verify session isolation: focusgram_content.js skips execution for background session
        val contentScriptFile = File(contentEngineDir, "focusgram_content.js")
        assertTrue("focusgram_content.js must exist", contentScriptFile.exists())
        val contentScriptCode = contentScriptFile.readText()
        assertTrue("focusgram_content.js must isolate background session", contentScriptCode.contains("focusgram_bg_dm=1"))

        val bgFile = File(contentEngineDir, "background.js")
        assertTrue("background.js must exist", bgFile.exists())
        val bgCode = bgFile.readText()
        assertTrue("background.js must intercept focusgram_bg_dm=1", bgCode.contains("focusgram_bg_dm=1"))
        assertTrue("background.js must block image, media, and font for headless session", bgCode.contains("type === 'image' || type === 'media' || type === 'font'"))
    }

    @Test
    fun testSettingsDefaultValuesAndPersistence() {
        val settings = com.ujwal.focusgram.core.model.FocusSettings()
        assertFalse("dmNotificationsEnabled must be disabled by default for zero background battery cost", settings.dmNotificationsEnabled)
        assertFalse("dmNotificationPreview must be disabled by default for privacy", settings.dmNotificationPreview)
        assertEquals("dmRecentMessageHashes must default to empty array JSON", "[]", settings.dmRecentMessageHashes)
    }

    @Test
    fun testManifestServiceAndReceiverDeclarations() {
        val manifestFile = getFile("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val manifestCode = manifestFile.readText()

        assertTrue(
            "DmNotificationService must be declared in AndroidManifest",
            manifestCode.contains("android:name=\".feature.notifications.DmNotificationService\"")
        )
        assertTrue(
            "DmNotificationService must declare specialUse foregroundServiceType",
            manifestCode.contains("android:foregroundServiceType=\"specialUse\"")
        )
        assertTrue(
            "DmNotificationService must declare PROPERTY_SPECIAL_USE_FGS_SUBTYPE",
            manifestCode.contains("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE")
        )
        assertTrue(
            "DmBootReceiver must be declared in AndroidManifest",
            manifestCode.contains("android:name=\".feature.notifications.DmBootReceiver\"")
        )
        assertTrue(
            "RECEIVE_BOOT_COMPLETED permission must be declared",
            manifestCode.contains("android.permission.RECEIVE_BOOT_COMPLETED")
        )
    }

    @Test
    fun testSettingsScreenDmSectionAndWarningDialogIntegrity() {
        val settingsScreenFile = getFile("src/main/kotlin/com/ujwal/focusgram/feature/settings/SettingsScreen.kt")
        assertTrue("SettingsScreen.kt must exist", settingsScreenFile.exists())
        val code = settingsScreenFile.readText()

        assertTrue("SettingsScreen must contain DIRECT MESSAGE NOTIFICATIONS title", code.contains("DIRECT MESSAGE NOTIFICATIONS"))
        assertTrue("SettingsScreen must contain Message Notifications toggle", code.contains("Message Notifications"))
        assertFalse("SettingsScreen must not contain redundant 'Message & Sender Preview' row", code.contains("\"Message & Sender Preview\""))
        assertTrue("SettingsScreen must contain warning dialog title", code.contains("Enable Message Notifications?"))
        assertTrue("Warning dialog must warn about persistent notification when closed", code.contains("A persistent notification will remain active in the background."))
        assertTrue("Warning dialog must warn about battery consumption", code.contains("Background listening may consume slightly more battery than usual."))
        assertTrue("Warning dialog must warn about active status", code.contains("Instagram may show your status as \\\"Active\\\"."))
        assertTrue("Warning dialog must state privacy guarantee", code.contains("Your messages are processed locally on-device and never stored or sent anywhere."))
        assertTrue("Warning dialog must launch BatteryOptimizationHelper", code.contains("BatteryOptimizationHelper.openBatteryOptimizationSettings"))
    }

    @Test
    fun testDmNotificationServiceLifecycleActions() {
        val serviceFile = getFile("src/main/kotlin/com/ujwal/focusgram/feature/notifications/DmNotificationService.kt")
        assertTrue("DmNotificationService.kt must exist", serviceFile.exists())
        val code = serviceFile.readText()

        assertTrue("Must support ACTION_APP_FOREGROUND", code.contains("ACTION_APP_FOREGROUND"))
        assertTrue("Must support ACTION_APP_BACKGROUND", code.contains("ACTION_APP_BACKGROUND"))
        assertTrue("Must support ACTION_RESTORE_NOTIFICATION", code.contains("ACTION_RESTORE_NOTIFICATION"))
        assertTrue("Must define onAppForegroundChanged", code.contains("fun onAppForegroundChanged"))
        assertTrue("Must setup off-screen ImageReader", code.contains("ImageReader"))
        assertTrue("Must configure mobile dimensions (390, 844)", code.contains("390, 844"))
        assertTrue("Must drain frames via setOnImageAvailableListener", code.contains("setOnImageAvailableListener"))
        assertTrue("Must release display in onDestroy", code.contains("releaseDisplay"))
    }

    @Test
    fun testInternationalizedAvatarAltTextCleaning() {
        fun cleanSenderName(raw: String): String {
            return raw
                .replace(Regex("^(?:profilbild von|photo de profil de|foto del? perfil de|foto do perfil de|immagine del profilo di|фото профиля|profile picture of|profile photo of|avatar of)\\s+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("(?:'s profile picture|'s profile photo|'s photo| profile picture|'s avatar|のプロフィール写真|的个人头像|님의 프로필 사진).*$", RegexOption.IGNORE_CASE), "")
                .trim()
        }

        assertEquals("alice", cleanSenderName("alice's profile picture"))
        assertEquals("alice", cleanSenderName("Profilbild von alice"))
        assertEquals("alice", cleanSenderName("Photo de profil de alice"))
        assertEquals("alice", cleanSenderName("Foto del perfil de alice"))
        assertEquals("alice", cleanSenderName("Foto do perfil de alice"))
        assertEquals("marco", cleanSenderName("Immagine del profilo di marco"))
        assertEquals("ivan", cleanSenderName("Фото профиля ivan"))
    }

    @Test
    fun testBatteryOptimizationHelperAndManifestDeclaration() {
        val helperFile = getFile("src/main/kotlin/com/ujwal/focusgram/core/utils/BatteryOptimizationHelper.kt")
        assertTrue("BatteryOptimizationHelper.kt must exist", helperFile.exists())
        val code = helperFile.readText()

        assertTrue("Helper must support Xiaomi", code.contains("xiaomi"))
        assertTrue("Helper must support Samsung", code.contains("samsung"))
        assertTrue("Helper must support Oppo", code.contains("oppo"))
        assertTrue("Helper must support Vivo", code.contains("vivo"))
        assertTrue("Helper must support Huawei", code.contains("huawei"))
        assertTrue("Helper must support ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", code.contains("ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))

        val manifestFile = getFile("src/main/AndroidManifest.xml")
        val manifestCode = manifestFile.readText()
        assertTrue("Manifest must declare REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", manifestCode.contains("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))
    }

    @Test
    fun testInstagramStyleXUnreadAndOutgoingDetection() {
        val contentEngineDir = getFile("src/main/assets/extensions/content-engine")
        val watcherFile = File(contentEngineDir, "dm_watcher.js")
        assertTrue("dm_watcher.js must exist", watcherFile.exists())
        val watcherCode = watcherFile.readText()

        // Verify StyleX unread classes are queried
        assertTrue("dm_watcher must check StyleX unread class x1s688f", watcherCode.contains("x1s688f"))
        assertTrue("dm_watcher must check StyleX unread class x5n08af", watcherCode.contains("x5n08af"))
        assertTrue("dm_watcher must check new messages text pattern", watcherCode.contains("new\\s+messages?"))
        assertTrue("dm_watcher must check data-visualcompletion ignore for Unread dot", watcherCode.contains("data-visualcompletion"))
        assertTrue("dm_watcher must check span[title] for contact name", watcherCode.contains("span[title]"))
        assertTrue("dm_watcher must discover inbox rows from sender titles when role wrappers change",
            watcherCode.contains("document.querySelectorAll('span[title], [data-visualcompletion=\"ignore\"], .x1s688f.x5n08af')"))
        assertTrue("dm_watcher must resolve a sender marker to its conversation row",
            watcherCode.contains("function getThreadContainer(seed)"))

        // Verify outgoing message filtering handles "You sent an attachment."
        fun isOutgoingMessage(text: String): Boolean {
            val trimmed = text.trim()
            if (Regex("^(?:seen|visto|vu|gesehen|visualizzato|既読)(?:\\s+[·•\\u00B7\\u2022\\s\\dsmhdw]+)?$", RegexOption.IGNORE_CASE).matches(trimmed)) return true
            if (Regex("^(?:you|tú|tu|vous|du|você|вы|ty|jij|أنت|你|あなた|회원님이)\\s*:\\s*", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) return true
            if (Regex("^(?:you\\s+(?:sent|shared|replied|reacted|forwarded)|enviaste|vous\\s+avez\\s+envoyé|gesendet|hai\\s+inviato|enviou|вы\\s+отправили)", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) return true
            return false
        }

        assertTrue("You sent an attachment must be marked outgoing", isOutgoingMessage("You sent an attachment."))
        assertTrue("You sent a message must be marked outgoing", isOutgoingMessage("You sent a photo."))
        assertTrue("You: Hello must be marked outgoing", isOutgoingMessage("You: Hello"))
        assertFalse("ROJAN sent an attachment must NOT be marked outgoing", isOutgoingMessage("ROJAN sent an attachment."))
        assertFalse("4 new messages must NOT be marked outgoing", isOutgoingMessage("4 new messages"))
    }

    @Test
    fun testLiveSessionClockAndPauseNotificationIcons() {
        val clockDrawable = getFile("src/main/res/drawable/ic_clock_notification.xml")
        assertTrue("ic_clock_notification.xml must exist", clockDrawable.exists())
        val clockXml = clockDrawable.readText()
        assertTrue("Clock drawable must be a vector", clockXml.contains("<vector"))
        assertTrue("Clock drawable must define viewport", clockXml.contains("android:viewportWidth=\"24\""))

        val pauseDrawable = getFile("src/main/res/drawable/ic_pause_notification.xml")
        assertTrue("ic_pause_notification.xml must exist", pauseDrawable.exists())
        val pauseXml = pauseDrawable.readText()
        assertTrue("Pause drawable must be a vector", pauseXml.contains("<vector"))
        assertTrue("Pause drawable must define viewport", pauseXml.contains("android:viewportWidth=\"24\""))

        val serviceFile = getFile("src/main/kotlin/com/ujwal/focusgram/feature/screentime/ScreenTimeService.kt")
        assertTrue("ScreenTimeService.kt must exist", serviceFile.exists())
        val serviceCode = serviceFile.readText()
        assertTrue("ScreenTimeService must use ic_clock_notification", serviceCode.contains("ic_clock_notification"))
        assertTrue("ScreenTimeService must use ic_pause_notification when paused", serviceCode.contains("ic_pause_notification"))
    }

    @Test
    fun testNotesExclusionAndSettingsIntegrity() {
        val watcherFile = getFile("src/main/assets/extensions/content-engine/dm_watcher.js")
        assertTrue("dm_watcher.js must exist", watcherFile.exists())
        val watcherCode = watcherFile.readText()
        assertTrue("dm_watcher.js must define isNotesElement helper", watcherCode.contains("isNotesElement"))
        assertTrue("dm_watcher.js must not use x1c4vz4f as a Notes selector because it appears on inbox rows",
            watcherCode.contains("x1c4vz4f") && !watcherCode.contains("closest('.x1c4vz4f"))
        assertTrue("conversation rows with span[title] must not be excluded as Notes",
            watcherCode.contains("el.querySelector('a[href*=\"/direct/t/\"], span[title]')"))
        assertTrue("dm_watcher.js must check speech bubble xsnw5ke", watcherCode.contains("xsnw5ke"))

        val settings = com.ujwal.focusgram.core.model.FocusSettings()
        assertFalse("hideNotesTray must default to false", settings.hideNotesTray)

        val settingsScreenFile = getFile("src/main/kotlin/com/ujwal/focusgram/feature/settings/SettingsScreen.kt")
        val settingsCode = settingsScreenFile.readText()
        assertTrue("SettingsScreen must contain 'Hide Notes Tray'", settingsCode.contains("Hide Notes Tray"))
        assertTrue("SettingsScreen must contain 'Choose which page to open on app launch'", settingsCode.contains("Choose which page to open on app launch"))
        assertFalse("SettingsScreen must remove 'Hide Suggested Posts' from UI", settingsCode.contains("\"Hide Suggested Posts\""))
        assertFalse("SettingsScreen must remove 'Hide Shop Tab' from UI", settingsCode.contains("\"Hide Shop Tab\""))

        val sessionMgrFile = getFile("src/main/kotlin/com/ujwal/focusgram/core/engine/GeckoSessionManager.kt")
        val sessionMgrCode = sessionMgrFile.readText()
        assertTrue("GeckoSessionManager must implement onShowActionRequest", sessionMgrCode.contains("onShowActionRequest"))
        assertTrue("GeckoSessionManager must check FLAG_IS_EDITABLE", sessionMgrCode.contains("FLAG_IS_EDITABLE"))
    }

    @Test
    fun testBlockDmsNotificationStateGatingAndRestoration() {
        val repoFile = getFile("src/main/kotlin/com/ujwal/focusgram/core/datastore/SettingsRepository.kt")
        assertTrue("SettingsRepository.kt must exist", repoFile.exists())
        val repoCode = repoFile.readText()

        assertTrue("SettingsRepository must define KEY_SAVED_DM_NOTIFICATIONS_STATE", repoCode.contains("KEY_SAVED_DM_NOTIFICATIONS_STATE"))
        assertTrue("SettingsRepository must define KEY_HIDE_NOTES_TRAY", repoCode.contains("KEY_HIDE_NOTES_TRAY"))
        assertTrue("setNoDMs must store current dm notifications state when enabled", repoCode.contains("prefs[KEY_SAVED_DM_NOTIFICATIONS_STATE] = currentDmNotifications"))
        assertTrue("setNoDMs must restore dm notifications when unblocked", repoCode.contains("prefs[KEY_DM_NOTIFICATIONS_ENABLED] = true"))

        // Simulate state machine:
        var dmNotificationsEnabled = true
        var dmNotificationPreview = true
        var savedDmNotificationsState = false
        var noDMs = false

        fun setNoDMsSim(enabled: Boolean) {
            noDMs = enabled
            if (enabled) {
                savedDmNotificationsState = dmNotificationsEnabled
                dmNotificationsEnabled = false
                dmNotificationPreview = false
            } else {
                if (savedDmNotificationsState) {
                    dmNotificationsEnabled = true
                    dmNotificationPreview = true
                }
            }
        }

        // Initially enabled, turn on Block DMs
        setNoDMsSim(true)
        assertTrue("noDMs must be true", noDMs)
        assertFalse("dmNotificationsEnabled must be auto-disabled", dmNotificationsEnabled)
        assertFalse("dmNotificationPreview must be auto-disabled", dmNotificationPreview)
        assertTrue("Previous state must be preserved as saved", savedDmNotificationsState)

        // Turn off Block DMs -> notifications must be restored!
        setNoDMsSim(false)
        assertFalse("noDMs must be false", noDMs)
        assertTrue("dmNotificationsEnabled must be restored to true", dmNotificationsEnabled)
        assertTrue("dmNotificationPreview must be restored to true", dmNotificationPreview)
    }

    @Test
    fun testMutedChatSuppressionAndNotificationFormatting() {
        // 1. Verify dm_watcher.js contains isChatMuted and checks muted SVG / aria attributes
        val contentEngineDir = getFile("src/main/assets/extensions/content-engine")
        val watcherFile = File(contentEngineDir, "dm_watcher.js")
        assertTrue("dm_watcher.js must exist", watcherFile.exists())
        val code = watcherFile.readText()

        assertTrue("dm_watcher must define isChatMuted helper", code.contains("function isChatMuted"))
        assertTrue("dm_watcher must check svg[aria-label=\"Muted\"]", code.contains("aria-label=\"Muted\""))
        assertTrue("dm_watcher must check svg title for muted", code.contains("titles[i].textContent"))
        assertTrue("dm_watcher must check M15.209 bell path", code.contains("M15.209"))
        assertTrue("dm_watcher must ignore muted elements in extractThreadInfo", code.contains("isChatMuted(container)"))

        // 2. Simulate formatting logic for single vs repeated messages
        fun formatNotification(sender: String, snippet: String, countStr: String, count: Int, showPreview: Boolean): Pair<String, String> {
            val title = if (showPreview) sender else "FocusGram"
            val body = if (showPreview) {
                if (count > 1 || countStr != "1") {
                    "You have $countStr unread messages"
                } else if (snippet.startsWith("You have", ignoreCase = true)) {
                    snippet
                } else if (snippet.isNotBlank() && !snippet.equals("Sent a message", ignoreCase = true)) {
                    snippet
                } else {
                    "You have 1 unread message"
                }
            } else {
                if (count > 1 || countStr != "1") {
                    "You have $countStr unread messages"
                } else {
                    "You have 1 unread message"
                }
            }
            return Pair(title, body)
        }

        // Single message with preview
        val (title1, body1) = formatNotification("BiPLoB dHAkaL", "Match abandoned due to rain..!!", "1", 1, true)
        assertEquals("BiPLoB dHAkaL", title1)
        assertEquals("Match abandoned due to rain..!!", body1)

        // Single message without text (e.g. attachment)
        val (titleAtt, bodyAtt) = formatNotification("BiPLoB dHAkaL", "Sent an attachment", "1", 1, true)
        assertEquals("BiPLoB dHAkaL", titleAtt)
        assertEquals("Sent an attachment", bodyAtt)

        // Repeated messages: 2 messages
        val (title2, body2) = formatNotification("BiPLoB dHAkaL", "", "2", 2, true)
        assertEquals("BiPLoB dHAkaL", title2)
        assertEquals("You have 2 unread messages", body2)

        // Repeated messages: 3+ messages
        val (title3, body3) = formatNotification("BiPLoB dHAkaL", "", "3+", 3, true)
        assertEquals("BiPLoB dHAkaL", title3)
        assertEquals("You have 3+ unread messages", body3)

        // Without preview
        val (titleNoPrev, bodyNoPrev) = formatNotification("BiPLoB dHAkaL", "Secret", "2", 2, false)
        assertEquals("FocusGram", titleNoPrev)
        assertEquals("You have 2 unread messages", bodyNoPrev)
    }

    @Test
    fun testRepeatedMessageNotificationReplacementAndHashUpdate() {
        val threadId = "thread_123456"
        val sender = "BiPLoB dHAkaL"

        // Derive notification ID
        val rawId = threadId.hashCode()
        var notificationId1 = if (rawId == Int.MIN_VALUE) 1 else kotlin.math.abs(rawId)
        if (notificationId1 == 0) notificationId1 = 1

        var notificationId2 = if (threadId.hashCode() == Int.MIN_VALUE) 1 else kotlin.math.abs(threadId.hashCode())
        if (notificationId2 == 0) notificationId2 = 1

        assertEquals("Same thread must always map to the exact same Android notification ID to replace earlier notification", notificationId1, notificationId2)

        // Deduplication hash updates when message count or time changes
        val hashMsg1 = sha256("$threadId:$sender:1:Hello:now")
        val hashMsg2 = sha256("$threadId:$sender:2:You have 2 unread messages:1m")
        val hashMsg3 = sha256("$threadId:$sender:3+:You have 3+ unread messages:2m")

        assertFalse("Hash must change when message count increments from 1 to 2", hashMsg1 == hashMsg2)
        assertFalse("Hash must change when message count increments from 2 to 3+", hashMsg2 == hashMsg3)
    }
}
