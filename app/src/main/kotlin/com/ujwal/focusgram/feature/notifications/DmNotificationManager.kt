package com.ujwal.focusgram.feature.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ujwal.focusgram.FocusGramApp
import com.ujwal.focusgram.MainActivity
import com.ujwal.focusgram.R
import com.ujwal.focusgram.core.datastore.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Coordinates on-device incoming direct message notifications, privacy-preserving
 * cryptographic deduplication, and notification channel provisioning.
 *
 * All deduplication operates on one-way SHA-256 hashes of the message preview text
 * alongside the sender identifier. Plaintext message contents are never persisted to disk.
 */
class DmNotificationManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val deduplicationMutex = Mutex()

    @Volatile
    var activeChatThreadId: String? = null

    @Volatile
    var isAppInForeground: Boolean = false

    @Volatile
    private var lastReportedBadgeCount: Int = 0

    @Volatile
    private var lastDmNotificationTime: Long = 0L

    private val unreadCountByThread = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val hasPlusByThread = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    init {
        createNotificationChannels()
    }

    /**
     * Initializes notification channels for the persistent background listener
     * service and high-priority heads-up direct message alerts.
     */
    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Channel for high-priority incoming message heads-up notifications
            val messagesChannel = NotificationChannel(
                CHANNEL_ID_MESSAGES,
                "Direct Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for incoming Instagram direct messages"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(messagesChannel)

            // Channel for low-priority persistent background service indicator
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "Direct Message Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background notification service for direct messages"
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Processes an incoming message event captured from Instagram web.
     * Computes a SHA-256 digest of the message snippet, checks against the sliding
     * deduplication window, and posts a notification if deduplication passes.
     */
    fun onDmReceived(
        threadId: String,
        sender: String,
        snippet: String,
        messageCount: Int = 1,
        messageCountStr: String = "",
        messageTime: String = ""
    ) {
        val cleanSender = sender.trim().ifBlank { "Someone" }
        val cleanThreadId = threadId.trim()
        val threadKey = cleanThreadId.ifBlank { cleanSender.lowercase() }

        val digits = messageCountStr.filter { it.isDigit() }.toIntOrNull()
            ?: snippet.filter { it.isDigit() }.toIntOrNull()
            ?: messageCount
        val incomingCount = maxOf(1, digits)
        val incomingPlus = messageCountStr.contains("+") || snippet.contains("+") || incomingCount >= 3

        if (incomingPlus) {
            hasPlusByThread[threadKey] = true
        }

        // Strictly respect incoming count from Instagram : never self-increment on re-scans!
        val newCount = incomingCount
        unreadCountByThread[threadKey] = newCount

        val hasPlus = hasPlusByThread[threadKey] == true || newCount >= 3
        val countFormatted = if (hasPlus && newCount > 1) "${newCount}+" else newCount.toString()
        val countStr = countFormatted
        val cleanSnippet = if (newCount == 1 && !hasPlus) "1 new message" else "$countFormatted new messages"

        scope.launch {
            val settings = FocusGramApp.instance.settingsRepository.settingsFlow.first()
            if (!settings.dmNotificationsEnabled || settings.noDMs) {
                Log.d(TAG, "DM notifications are disabled or DMs are blocked by user toggle; skipping.")
                return@launch
            }

            // Suppress notification if user is currently looking at this active conversation
            if (isAppInForeground && cleanThreadId.isNotBlank() &&
                activeChatThreadId?.equals(cleanThreadId, ignoreCase = true) == true
            ) {
                Log.d(TAG, "User is currently viewing active thread in foreground; notification suppressed.")
                return@launch
            }

            // Verify if deduplication allows posting notification
            val isNewMessage = deduplicationMutex.withLock {
                checkAndRecordMessageHash(
                    repository = FocusGramApp.instance.settingsRepository,
                    threadId = cleanThreadId,
                    sender = cleanSender,
                    // Use Instagram's actual row preview to identify the event. The
                    // notification body remains `cleanSnippet` below, but using that
                    // generic text here made every one-message event in a thread look
                    // identical ("1 new message") and permanently suppressed later DMs.
                    snippet = snippet.trim().ifBlank { cleanSnippet },
                    countStr = countStr,
                    time = messageTime
                )
            }

            if (!isNewMessage) {
                Log.d(TAG, "Duplicate DM detected within deduplication window. Notification suppressed.")
                return@launch
            }

            // Enforce Chat Lock privacy invariant: if thread, contact, or all direct messages are locked, force preview off
            val cleanUser = cleanSender.trim().lowercase().removePrefix("@")
            val isWholeDmLocked = settings.chatLockMode == "whole_dm" || settings.chatLockAll
            val hasAnyChatLock = isWholeDmLocked || settings.lockedChatIds.isNotEmpty() || settings.lockedChatUsernames.isNotEmpty()

            val isThreadLocked = cleanThreadId.isNotBlank() && settings.lockedChatIds.any {
                it.equals(cleanThreadId, ignoreCase = true)
            }
            val isSenderLocked = settings.lockedChatUsernames.values.any {
                it.trim().lowercase().removePrefix("@").equals(cleanUser, ignoreCase = true)
            } || settings.lockedChatIds.any {
                it.trim().lowercase().removePrefix("@").equals(cleanUser, ignoreCase = true)
            } || (cleanThreadId.isNotBlank() && settings.lockedChatUsernames.containsKey(cleanThreadId))

            // Fail closed: if user has configured any chat lock and we cannot verify this is unlocked (e.g. blank threadId), lock it
            val isChatLocked = isWholeDmLocked || isThreadLocked || isSenderLocked || (hasAnyChatLock && cleanThreadId.isBlank())
            val effectiveShowPreview = settings.dmNotificationPreview && !isChatLocked

            postDmNotification(
                threadId = cleanThreadId,
                sender = cleanSender,
                snippet = cleanSnippet,
                countStr = countStr,
                messageCount = newCount,
                showPreview = effectiveShowPreview,
                isChatLocked = isChatLocked
            )
        }
    }

    /**
     * Handles badge count increments reported from the global navigation bar
     * when the full inbox thread list is not directly loaded in the foreground.
     */
    fun onBadgeCountChanged(count: Int) {
        if (count <= 0) {
            lastReportedBadgeCount = 0
            // All direct messages read: dismiss all active DM notifications
            cancelAllDmNotifications()
            return
        }

        val previousCount = lastReportedBadgeCount
        lastReportedBadgeCount = count

        // Current mobile Instagram sometimes exposes only the navigation badge in
        // a headless session, not the virtualized conversation rows. The badge is
        // still an authoritative unread signal, so use it as a privacy-safe
        // fallback. Repeated scans with the same count are ignored.
        if (count <= previousCount) return

        scope.launch {
            val settings = FocusGramApp.instance.settingsRepository.settingsFlow.first()
            if (!settings.dmNotificationsEnabled || settings.noDMs) return@launch
            postUnreadBadgeNotification(count)
        }
    }

    private fun postUnreadBadgeNotification(count: Int) {
        val targetUrl = "https://www.instagram.com/direct/inbox/"
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(targetUrl)
            putExtra(EXTRA_TARGET_URL, targetUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_GENERIC_BADGE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = if (count == 1) "1 new message" else "$count new messages"
        try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_GENERIC_BADGE,
                NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Instagram Direct")
                    .setContentText(body)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .build()
            )
            Log.i(TAG, "Dispatched unread DM badge notification: count=$count")
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed posting unread DM badge notification: ${e.message}", e)
        }
    }

    /**
     * Verifies if a message is already in recent records via one-way SHA-256 composite digest.
     * The raw snippet and raw sender name are strictly never saved to disk.
     */
    private suspend fun checkAndRecordMessageHash(
        repository: SettingsRepository,
        threadId: String,
        sender: String,
        snippet: String,
        countStr: String,
        time: String
    ): Boolean {
        // `time` is a relative web label (for example, "2m"), not a message ID. It
        // changes while an unread message is sitting in the inbox, so including it
        // would re-notify the user every minute on each watcher scan.
        val compositeHash = computeSha256("$threadId:$sender:$countStr:$snippet")
        val now = System.currentTimeMillis()
        val settings = repository.settingsFlow.first()
        val existingList = parseHashRecords(settings.dmRecentMessageHashes)

        // Check if an entry with the exact same composite hash exists in recent records
        val isDuplicate = existingList.any { record ->
            record.compositeHash == compositeHash
        }

        if (isDuplicate) {
            return false
        }

        // Prepend the new hash record and retain at most 10 active items
        val updatedList = mutableListOf(
            HashRecord(
                compositeHash = compositeHash,
                timestamp = now
            )
        )
        for (item in existingList) {
            if (updatedList.size >= MAX_RECENT_HASH_ENTRIES) break
            if (item.compositeHash == compositeHash) continue
            updatedList.add(item)
        }

        repository.setDmRecentMessageHashes(serializeHashRecords(updatedList))
        return true
    }

    /**
     * Dismisses any active direct message notification posted for the given thread ID or sender.
     */
    fun cancelNotificationForThread(threadIdOrSender: String) {
        if (threadIdOrSender.isBlank()) return
        val key = threadIdOrSender.trim()
        val lowerKey = key.lowercase()

        val keysToRemove = unreadCountByThread.keys.filter {
            it.equals(key, ignoreCase = true) || it.equals(lowerKey, ignoreCase = true) ||
            it.contains(lowerKey) || lowerKey.contains(it)
        }
        keysToRemove.forEach {
            unreadCountByThread.remove(it)
            hasPlusByThread.remove(it)
        }

        // Cancel notification by key hash
        val rawId = key.hashCode()
        var notificationId = if (rawId == Int.MIN_VALUE) 1 else kotlin.math.abs(rawId)
        if (notificationId == 0) notificationId = 1
        while (notificationId == NOTIFICATION_ID_SERVICE || notificationId == NOTIFICATION_ID_GENERIC_BADGE) {
            notificationId += 13
        }
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (_: Exception) {}

        // Also cancel by lowerKey hash if distinct
        val rawLowerId = lowerKey.hashCode()
        var lowerNotificationId = if (rawLowerId == Int.MIN_VALUE) 1 else kotlin.math.abs(rawLowerId)
        if (lowerNotificationId == 0) lowerNotificationId = 1
        while (lowerNotificationId == NOTIFICATION_ID_SERVICE || lowerNotificationId == NOTIFICATION_ID_GENERIC_BADGE) {
            lowerNotificationId += 13
        }
        try {
            NotificationManagerCompat.from(context).cancel(lowerNotificationId)
        } catch (_: Exception) {}
    }

    /**
     * Dismisses all active direct message notifications and clears all unread counters.
     */
    fun cancelAllDmNotifications() {
        unreadCountByThread.clear()
        hasPlusByThread.clear()
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.let { manager ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val activeNotifications = manager.activeNotifications
                    for (notification in activeNotifications) {
                        val id = notification.id
                        if (id != NOTIFICATION_ID_SERVICE && id != NOTIFICATION_ID_GENERIC_BADGE) {
                            manager.cancel(id)
                        }
                    }
                } else {
                    for (i in 1..200) {
                        if (i != NOTIFICATION_ID_SERVICE && i != NOTIFICATION_ID_GENERIC_BADGE) {
                            manager.cancel(i)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun postDmNotification(
        threadId: String,
        sender: String,
        snippet: String,
        countStr: String,
        messageCount: Int,
        showPreview: Boolean,
        isChatLocked: Boolean
    ) {
        // Enforce Chat Lock safety on deep link: locked chats must direct to the inbox challenge
        val targetUrl = if (threadId.isNotBlank() && !isChatLocked && !threadId.startsWith("thread_")) {
            "https://www.instagram.com/direct/t/$threadId/"
        } else {
            "https://www.instagram.com/direct/inbox/"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(targetUrl)
            putExtra(EXTRA_TARGET_URL, targetUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val threadKey = if (threadId.isNotBlank()) threadId.trim() else sender.lowercase().trim()
        val rawId = threadKey.hashCode()
        var notificationId = if (rawId == Int.MIN_VALUE) 1 else kotlin.math.abs(rawId)
        if (notificationId == 0) notificationId = 1
        // Avoid collision with service notification ID or generic badge notification ID
        while (notificationId == NOTIFICATION_ID_SERVICE || notificationId == NOTIFICATION_ID_GENERIC_BADGE) {
            notificationId += 13
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Title is the contact's nickname or first name / display name (or generic if locked by Chat Lock)
        val title = if (isChatLocked) "Direct Message" else sender.trim()
        // Body is the formatted counter (e.g. "4+ new messages", "1 new message")
        val body = if (isChatLocked) "New message" else snippet.trim()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(if (isChatLocked) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)

        if (body.length > 40) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            lastDmNotificationTime = System.currentTimeMillis()
            // Cancel generic badge notification since we dispatched a specific DM notification
            try {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_GENERIC_BADGE)
            } catch (_: Exception) {}
            Log.i(TAG, "Dispatched DM notification (id=$notificationId)")
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post DM notification: ${e.message}", e)
        }
    }

    private fun parseHashRecords(jsonStr: String): List<HashRecord> {
        if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<HashRecord>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val hash = obj.optString("compositeHash").ifBlank { obj.optString("hash") }
                val time = obj.optLong("timestamp", System.currentTimeMillis())
                if (hash.isNotBlank()) {
                    list.add(HashRecord(compositeHash = hash, timestamp = time))
                }
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing hash records: ${e.message}")
            emptyList()
        }
    }

    private fun serializeHashRecords(records: List<HashRecord>): String {
        return try {
            val array = JSONArray()
            for (r in records) {
                val obj = JSONObject().apply {
                    put("compositeHash", r.compositeHash)
                    put("timestamp", r.timestamp)
                }
                array.put(obj)
            }
            array.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Error serializing hash records: ${e.message}")
            "[]"
        }
    }

    private fun computeSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private data class HashRecord(
        val compositeHash: String,
        val timestamp: Long
    )

    companion object {
        private const val TAG = "DmNotificationManager"
        const val CHANNEL_ID_MESSAGES = "focusgram_dm_notifications"
        const val CHANNEL_ID_SERVICE = "focusgram_dm_service_channel"
        const val EXTRA_TARGET_URL = "com.ujwal.focusgram.EXTRA_TARGET_URL"
        const val MAX_RECENT_HASH_ENTRIES = 10
        private const val NOTIFICATION_ID_GENERIC_BADGE = 90210
        private const val NOTIFICATION_ID_SERVICE = 90211
        private const val DEDUPLICATION_WINDOW_MS = 60_000L

        @Volatile
        private var instance: DmNotificationManager? = null

        fun getInstance(context: Context): DmNotificationManager {
            return instance ?: synchronized(this) {
                instance ?: DmNotificationManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
