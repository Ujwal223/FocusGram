package com.ujwal.focusgram.core.model

/**
 * User-configurable toggles and operational thresholds stored on-device.
 *
 * All state is persisted via DataStore Preferences. Defaults prioritize distraction
 * elimination (ad blocking, autoplay suppression, explore blurring).
 */
data class FocusSettings(
    val noAds: Boolean = true,
    val noStories: Boolean = false,
    val noReels: Boolean = false,
    val noAutoplay: Boolean = true,
    val noDMs: Boolean = false,
    val blurExplore: Boolean = true,
    val blurReels: Boolean = false,
    val blurFeed: Boolean = false,
    val unblurMode: String = "tap", // "tap" reveals on click; "hold" requires continuous touch
    val tapToUnblur: Boolean = true,
    val requireLongPress: Boolean = false,
    val hideSuggestedPosts: Boolean = false,
    val hideLikeCounts: Boolean = false,
    val hideFollowerCounts: Boolean = false,
    val hideShopTab: Boolean = true,
    val hideNotesTray: Boolean = false,
    val minimalModeEnabled: Boolean = false,
    val hideFeedPosts: Boolean = false,
    val blockDmReelsSwiping: Boolean = true,

    val showBreathGate: Boolean = true,
    val breathGateSeconds: Int = 10,
    val requireWordChallenge: Boolean = true,
    val wordChallengeCount: Int = 10,
    val effortFrictionEnabled: Boolean = true,
    val cooldownMinutes: Int = 5,
    val dailyLimitMinutes: Int = 60,
    val dailyLimitAdClaimedDate: String = "",
    val dailyLimitExtensionsCount: Int = 0,

    val grayscaleEnabled: Boolean = false,
    val smoothScroll120Hz: Boolean = true,
    val reelsHistoryEnabled: Boolean = true,

    val adsWatchedAllTime: Int = 0,
    val lastAdDate: String = "",

    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockOnBackground: Boolean = true,
    val isFirstRun: Boolean = true,

    val startupPage: String = "home",
    val videoDownloadEnabled: Boolean = false,
    val sanitizeLinks: Boolean = true,
    val notifySessionEnd: Boolean = false,
    val notifyPersistent: Boolean = true,
    val notifyEvery5Minutes: Boolean = true,
    val portraitOnly: Boolean = true,
    val showSessionTimeInBottomBar: Boolean = true,
    val appIcon: String = "default",
    val customIconsEnabled: Boolean = false,
    val storySearchEnabled: Boolean = true,
    val customBioFontsEnabled: Boolean = true,

    val chatLockMode: String = "individual", // "off", "individual", "whole_dm"
    val chatLockAll: Boolean = false,
    val lockedChatIds: Set<String> = emptySet(),
    val lockedChatUsernames: Map<String, String> = emptyMap(),
    val useSeparateChatPin: Boolean = false,
    val chatBiometricEnabled: Boolean = true,

    val ghostModeEnabled: Boolean = false,
    val ghostHideStorySeen: Boolean = true,
    val ghostHideDirectSeen: Boolean = true,
    val ghostHideTypingIndicator: Boolean = true,

    val activeSessionDurationMinutes: Int = 0,
    val activeSessionRemainingSeconds: Int = 0,
    val activeSessionElapsedSeconds: Int = 0,
    val activeSessionReelsWatched: Int = 0,
    val activeSessionReelCodes: Set<String> = emptySet(),
    val activeSessionIsActive: Boolean = false,

    val hasSeenGestureGuide: Boolean = false,
    val isMigrationCompleted: Boolean = false,
    val isMigrationDeclined: Boolean = false,
    val grayscaleSchedules: String = "[]",
    val lastKnownVersionCode: Int = 0,

    val dmNotificationsEnabled: Boolean = false,
    val dmNotificationPreview: Boolean = false,
    val dmRecentMessageHashes: String = "[]",

    val developerOptionsEnabled: Boolean = false,
    val enableDebugMenu: Boolean = false,
    val hideSupportBanner: Boolean = false,
    val disableAutoUpdateCheck: Boolean = false,
    val adRewardsEnabled: Boolean = true
) {
    /**
     * Evaluates whether grayscale is currently active, considering the manual toggle
     * and any active time-based schedules (e.g. overnight or daytime intervals).
     */
    fun isGrayscaleActive(
        currentHour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
        currentMinute: Int = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)
    ): Boolean {
        if (grayscaleEnabled) return true
        if (grayscaleSchedules.isBlank() || grayscaleSchedules == "[]") return false
        try {
            val array = org.json.JSONArray(grayscaleSchedules)
            val currentMinutes = currentHour * 60 + currentMinute
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                if (!item.optBoolean("enabled", true)) continue
                val startMinutes = parseTimeToMinutes(item.optString("startTime", "")) ?: continue
                val endMinutes = parseTimeToMinutes(item.optString("endTime", "")) ?: continue
                if (endMinutes < startMinutes) {
                    // Overnight schedule (e.g., 21:00 to 06:00 or 9:00 PM to 6:00 AM)
                    if (currentMinutes >= startMinutes || currentMinutes < endMinutes) {
                        return true
                    }
                } else {
                    // Same day schedule (e.g., 09:00 to 17:00 or 9:00 AM to 5:00 PM)
                    if (currentMinutes in startMinutes until endMinutes) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    companion object {
        fun parseTimeToMinutes(time: String): Int? {
            val trimmed = time.trim()
            val isPm = trimmed.endsWith("PM", ignoreCase = true)
            val isAm = trimmed.endsWith("AM", ignoreCase = true)
            val clean = trimmed.replace("AM", "", ignoreCase = true).replace("PM", "", ignoreCase = true).trim()
            val parts = clean.split(":")
            if (parts.size != 2) return null
            var hour = parts[0].trim().toIntOrNull() ?: return null
            val minute = parts[1].trim().toIntOrNull() ?: return null
            if (isPm && hour < 12) hour += 12
            if (isAm && hour == 12) hour = 0
            return hour * 60 + minute
        }

        fun formatTo12Hour(time: String): String {
            val upper = time.trim().uppercase(java.util.Locale.US)
            if (upper.contains("AM") || upper.contains("PM")) {
                return time.trim()
            }
            val parts = upper.split(":")
            if (parts.size != 2) return time.trim()
            val hour = parts[0].trim().toIntOrNull() ?: return time.trim()
            val minute = parts[1].trim().toIntOrNull() ?: return time.trim()
            val amPm = if (hour >= 12) "PM" else "AM"
            val hour12 = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            return String.format(java.util.Locale.US, "%d:%02d %s", hour12, minute, amPm)
        }
    }
}
