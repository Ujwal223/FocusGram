package com.ujwal.focusgram.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ujwal.focusgram.core.model.FocusSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focusgram_settings")

/**
 * Persists toggles, cooldown thresholds, and focus metrics via Preferences DataStore.
 */
class SettingsRepository(private val context: Context) {

    fun getSettingsSync(): FocusSettings = runBlocking { settingsFlow.first() }

    val settingsFlow: Flow<FocusSettings> = context.dataStore.data.map { prefs ->
        FocusSettings(
            noAds = prefs[KEY_NO_ADS] ?: true,
            noStories = prefs[KEY_NO_STORIES] ?: false,
            noReels = prefs[KEY_NO_REELS] ?: false,
            noAutoplay = prefs[KEY_NO_AUTOPLAY] ?: true,
            noDMs = prefs[KEY_NO_DMS] ?: false,
            blurExplore = prefs[KEY_BLUR_EXPLORE] ?: true,
            blurReels = prefs[KEY_BLUR_REELS] ?: false,
            blurFeed = prefs[KEY_BLUR_FEED] ?: false,
            unblurMode = prefs[KEY_UNBLUR_MODE] ?: if (prefs[KEY_REQUIRE_LONG_PRESS] == true && prefs[KEY_TAP_TO_UNBLUR] == false) "hold" else "tap",
            tapToUnblur = (prefs[KEY_UNBLUR_MODE] ?: if (prefs[KEY_REQUIRE_LONG_PRESS] == true && prefs[KEY_TAP_TO_UNBLUR] == false) "hold" else "tap") == "tap",
            requireLongPress = (prefs[KEY_UNBLUR_MODE] ?: if (prefs[KEY_REQUIRE_LONG_PRESS] == true && prefs[KEY_TAP_TO_UNBLUR] == false) "hold" else "tap") == "hold",
            hideSuggestedPosts = prefs[KEY_HIDE_SUGGESTED_POSTS] ?: false,
            hideLikeCounts = prefs[KEY_HIDE_LIKE_COUNTS] ?: false,
            hideFollowerCounts = prefs[KEY_HIDE_FOLLOWER_COUNTS] ?: false,
            hideShopTab = prefs[KEY_HIDE_SHOP_TAB] ?: true,
            minimalModeEnabled = prefs[KEY_MINIMAL_MODE_ENABLED] ?: false,
            hideFeedPosts = prefs[KEY_HIDE_FEED_POSTS] ?: false,
            blockDmReelsSwiping = prefs[KEY_BLOCK_DM_REELS_SWIPING] ?: true,

            showBreathGate = prefs[KEY_SHOW_BREATH_GATE] ?: true,
            breathGateSeconds = prefs[KEY_BREATH_GATE_SECONDS] ?: 10,
            requireWordChallenge = prefs[KEY_REQUIRE_WORD_CHALLENGE] ?: true,
            wordChallengeCount = prefs[KEY_WORD_CHALLENGE_COUNT] ?: 30,
            effortFrictionEnabled = prefs[KEY_EFFORT_FRICTION_ENABLED] ?: true,
            cooldownMinutes = prefs[KEY_COOLDOWN_MINUTES] ?: 5,
            dailyLimitMinutes = prefs[KEY_DAILY_LIMIT_MINUTES] ?: 60,
            dailyLimitAdClaimedDate = prefs[KEY_DAILY_LIMIT_AD_DATE] ?: "",
            dailyLimitExtensionsCount = prefs[KEY_DAILY_LIMIT_EXTENSIONS_COUNT] ?: 0,

            grayscaleEnabled = prefs[KEY_GRAYSCALE_ENABLED] ?: false,
            smoothScroll120Hz = prefs[KEY_SMOOTH_SCROLL_120HZ] ?: true,
            reelsHistoryEnabled = prefs[KEY_REELS_HISTORY_ENABLED] ?: true,

            adsWatchedAllTime = prefs[KEY_ADS_WATCHED_ALL_TIME] ?: 0,
            lastAdDate = prefs[KEY_LAST_AD_DATE] ?: "",

            appLockEnabled = prefs[KEY_APP_LOCK_ENABLED] ?: false,
            biometricEnabled = prefs[KEY_BIOMETRIC_ENABLED] ?: false,
            autoLockOnBackground = prefs[KEY_AUTO_LOCK_BACKGROUND] ?: true,
            isFirstRun = prefs[KEY_IS_FIRST_RUN] ?: true,

            startupPage = prefs[KEY_STARTUP_PAGE] ?: "home",
            videoDownloadEnabled = prefs[KEY_VIDEO_DOWNLOAD_ENABLED] ?: false,
            sanitizeLinks = prefs[KEY_SANITIZE_LINKS] ?: true,
            notifySessionEnd = prefs[KEY_NOTIFY_SESSION_END] ?: false,
            notifyPersistent = prefs[KEY_NOTIFY_PERSISTENT] ?: true,
            notifyEvery5Minutes = prefs[KEY_NOTIFY_EVERY_5_MINUTES] ?: true,
            portraitOnly = prefs[KEY_PORTRAIT_ONLY] ?: true,
            showSessionTimeInBottomBar = prefs[KEY_SHOW_SESSION_TIME_IN_BOTTOM_BAR] ?: true,
            appIcon = prefs[KEY_APP_ICON] ?: "default",
            customIconsEnabled = prefs[KEY_CUSTOM_ICONS_ENABLED] ?: false,
            storySearchEnabled = prefs[KEY_STORY_SEARCH_ENABLED] ?: true,
            customBioFontsEnabled = prefs[KEY_CUSTOM_BIO_FONTS_ENABLED] ?: true,

            chatLockMode = prefs[KEY_CHAT_LOCK_MODE] ?: if (prefs[KEY_CHAT_LOCK_ALL] == true) "whole_dm" else "individual",
            chatLockAll = (prefs[KEY_CHAT_LOCK_MODE] ?: if (prefs[KEY_CHAT_LOCK_ALL] == true) "whole_dm" else "individual") == "whole_dm",
            lockedChatIds = prefs[KEY_LOCKED_CHAT_IDS] ?: emptySet(),
            lockedChatUsernames = parseUsernamesMap(prefs[KEY_LOCKED_CHAT_USERNAMES]),
            useSeparateChatPin = prefs[KEY_USE_SEPARATE_CHAT_PIN] ?: false,
            chatBiometricEnabled = prefs[KEY_CHAT_BIOMETRIC_ENABLED] ?: true,

            ghostModeEnabled = prefs[KEY_GHOST_MODE_ENABLED] ?: false,
            ghostHideStorySeen = prefs[KEY_GHOST_HIDE_STORY_SEEN] ?: true,
            ghostHideDirectSeen = prefs[KEY_GHOST_HIDE_DIRECT_SEEN] ?: true,
            ghostHideTypingIndicator = prefs[KEY_GHOST_HIDE_TYPING] ?: true,

            activeSessionDurationMinutes = prefs[KEY_ACTIVE_SESSION_DURATION_MINUTES] ?: 0,
            activeSessionRemainingSeconds = prefs[KEY_ACTIVE_SESSION_REMAINING_SECONDS] ?: 0,
            activeSessionElapsedSeconds = prefs[KEY_ACTIVE_SESSION_ELAPSED_SECONDS] ?: 0,
            activeSessionReelsWatched = prefs[KEY_ACTIVE_SESSION_REELS_WATCHED] ?: 0,
            activeSessionReelCodes = prefs[KEY_ACTIVE_SESSION_REEL_CODES] ?: emptySet(),
            activeSessionIsActive = prefs[KEY_ACTIVE_SESSION_IS_ACTIVE] ?: false
        )
    }

    private fun parseUsernamesMap(jsonStr: String?): Map<String, String> {
        if (jsonStr.isNullOrBlank()) return emptyMap()
        return try {
            val json = JSONObject(jsonStr)
            val map = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = json.optString(k)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    val lastSessionEndTimestampFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_SESSION_END_TIMESTAMP] ?: 0L
    }

    suspend fun recordSessionEnd(timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SESSION_END_TIMESTAMP] = timestamp
        }
    }

    suspend fun setNoAds(enabled: Boolean) = setBoolean(KEY_NO_ADS, enabled)
    suspend fun setNoStories(enabled: Boolean) = setBoolean(KEY_NO_STORIES, enabled)
    suspend fun setNoReels(enabled: Boolean) = setBoolean(KEY_NO_REELS, enabled)
    suspend fun setNoAutoplay(enabled: Boolean) = setBoolean(KEY_NO_AUTOPLAY, enabled)
    suspend fun setNoDMs(enabled: Boolean) = setBoolean(KEY_NO_DMS, enabled)
    suspend fun setBlurExplore(enabled: Boolean) = setBoolean(KEY_BLUR_EXPLORE, enabled)
    suspend fun setBlurReels(enabled: Boolean) = setBoolean(KEY_BLUR_REELS, enabled)
    suspend fun setBlurFeed(enabled: Boolean) = setBoolean(KEY_BLUR_FEED, enabled)
    suspend fun setTapToUnblur(enabled: Boolean) = setBoolean(KEY_TAP_TO_UNBLUR, enabled)
    suspend fun setRequireLongPress(enabled: Boolean) = setBoolean(KEY_REQUIRE_LONG_PRESS, enabled)
    suspend fun setUnblurMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_UNBLUR_MODE] = mode
            prefs[KEY_TAP_TO_UNBLUR] = (mode == "tap")
            prefs[KEY_REQUIRE_LONG_PRESS] = (mode == "hold")
        }
    }
    suspend fun setHideSuggestedPosts(enabled: Boolean) = setBoolean(KEY_HIDE_SUGGESTED_POSTS, enabled)
    suspend fun setHideLikeCounts(enabled: Boolean) = setBoolean(KEY_HIDE_LIKE_COUNTS, enabled)
    suspend fun setHideFollowerCounts(enabled: Boolean) = setBoolean(KEY_HIDE_FOLLOWER_COUNTS, enabled)
    suspend fun setHideShopTab(enabled: Boolean) = setBoolean(KEY_HIDE_SHOP_TAB, enabled)
    suspend fun setMinimalMode(enabled: Boolean) = setBoolean(KEY_MINIMAL_MODE_ENABLED, enabled)
    suspend fun setHideFeedPosts(enabled: Boolean) = setBoolean(KEY_HIDE_FEED_POSTS, enabled)
    suspend fun setBlockDmReelsSwiping(enabled: Boolean) = setBoolean(KEY_BLOCK_DM_REELS_SWIPING, enabled)

    suspend fun setBreathGate(enabled: Boolean, seconds: Int = 10) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHOW_BREATH_GATE] = enabled
            prefs[KEY_BREATH_GATE_SECONDS] = seconds
        }
    }
    suspend fun setShowBreathGate(enabled: Boolean) = setBreathGate(enabled)

    suspend fun setEffortFriction(enabled: Boolean, wordCount: Int = 30) {
        context.dataStore.edit { prefs ->
            prefs[KEY_EFFORT_FRICTION_ENABLED] = enabled
            prefs[KEY_REQUIRE_WORD_CHALLENGE] = enabled
            prefs[KEY_WORD_CHALLENGE_COUNT] = wordCount
        }
    }

    suspend fun setGrayscale(enabled: Boolean) = setBoolean(KEY_GRAYSCALE_ENABLED, enabled)
    suspend fun setSmoothScroll120Hz(enabled: Boolean) = setBoolean(KEY_SMOOTH_SCROLL_120HZ, enabled)
    suspend fun setCooldownMinutes(minutes: Int) = setInt(KEY_COOLDOWN_MINUTES, minutes)
    suspend fun setDailyLimitMinutes(minutes: Int) = setInt(KEY_DAILY_LIMIT_MINUTES, minutes)

    suspend fun extendDailyLimit5Minutes(today: String) {
        context.dataStore.edit { prefs ->
            val currentDate = prefs[KEY_DAILY_LIMIT_AD_DATE] ?: ""
            val currentCount = if (currentDate == today) prefs[KEY_DAILY_LIMIT_EXTENSIONS_COUNT] ?: 0 else 0
            prefs[KEY_DAILY_LIMIT_AD_DATE] = today
            prefs[KEY_DAILY_LIMIT_EXTENSIONS_COUNT] = (currentCount + 1).coerceAtMost(2)
        }
    }

    suspend fun setStartupPage(page: String) = setString(KEY_STARTUP_PAGE, page)
    suspend fun setVideoDownloadEnabled(enabled: Boolean) = setBoolean(KEY_VIDEO_DOWNLOAD_ENABLED, enabled)
    suspend fun setSanitizeLinks(enabled: Boolean) = setBoolean(KEY_SANITIZE_LINKS, enabled)
    suspend fun setNotifySessionEnd(enabled: Boolean) = setBoolean(KEY_NOTIFY_SESSION_END, enabled)
    suspend fun setNotifyPersistent(enabled: Boolean) = setBoolean(KEY_NOTIFY_PERSISTENT, enabled)
    suspend fun setNotifyEvery5Minutes(enabled: Boolean) = setBoolean(KEY_NOTIFY_EVERY_5_MINUTES, enabled)
    suspend fun setPortraitOnly(enabled: Boolean) = setBoolean(KEY_PORTRAIT_ONLY, enabled)
    suspend fun setShowSessionTimeInBottomBar(enabled: Boolean) = setBoolean(KEY_SHOW_SESSION_TIME_IN_BOTTOM_BAR, enabled)

    suspend fun saveActiveSession(
        durationMinutes: Int,
        remainingSeconds: Int,
        elapsedSeconds: Int,
        reelsWatched: Int = 0,
        reelCodes: Set<String> = emptySet(),
        isActive: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_SESSION_DURATION_MINUTES] = durationMinutes
            prefs[KEY_ACTIVE_SESSION_REMAINING_SECONDS] = remainingSeconds
            prefs[KEY_ACTIVE_SESSION_ELAPSED_SECONDS] = elapsedSeconds
            prefs[KEY_ACTIVE_SESSION_REELS_WATCHED] = reelsWatched
            prefs[KEY_ACTIVE_SESSION_REEL_CODES] = reelCodes
            prefs[KEY_ACTIVE_SESSION_IS_ACTIVE] = isActive
        }
    }

    suspend fun clearActiveSession() {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_SESSION_IS_ACTIVE] = false
            prefs[KEY_ACTIVE_SESSION_REMAINING_SECONDS] = 0
            prefs[KEY_ACTIVE_SESSION_ELAPSED_SECONDS] = 0
            prefs[KEY_ACTIVE_SESSION_REELS_WATCHED] = 0
            prefs[KEY_ACTIVE_SESSION_REEL_CODES] = emptySet()
        }
    }

    suspend fun setAppLock(enabled: Boolean, biometric: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_LOCK_ENABLED] = enabled
            prefs[KEY_BIOMETRIC_ENABLED] = biometric
        }
    }

    suspend fun setAutoLockBackground(enabled: Boolean) = setBoolean(KEY_AUTO_LOCK_BACKGROUND, enabled)

    suspend fun setGhostMode(enabled: Boolean) = setBoolean(KEY_GHOST_MODE_ENABLED, enabled)
    suspend fun setGhostHideStorySeen(enabled: Boolean) = setBoolean(KEY_GHOST_HIDE_STORY_SEEN, enabled)
    suspend fun setGhostHideDirectSeen(enabled: Boolean) = setBoolean(KEY_GHOST_HIDE_DIRECT_SEEN, enabled)
    suspend fun setGhostHideTypingIndicator(enabled: Boolean) = setBoolean(KEY_GHOST_HIDE_TYPING, enabled)

    private suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    private suspend fun setInt(key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { it[key] = value }
    }

    private suspend fun setString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun setAppIcon(icon: String) = setString(KEY_APP_ICON, icon)
    suspend fun setCustomIconsEnabled(enabled: Boolean) = setBoolean(KEY_CUSTOM_ICONS_ENABLED, enabled)
    suspend fun setChatLockMode(mode: String) {
        val cleanMode = when (mode.lowercase().trim()) {
            "whole_dm", "whole", "all" -> "whole_dm"
            "individual", "individual_dms", "single" -> "individual"
            else -> "off"
        }
        context.dataStore.edit { prefs ->
            prefs[KEY_CHAT_LOCK_MODE] = cleanMode
            prefs[KEY_CHAT_LOCK_ALL] = (cleanMode == "whole_dm")
        }
    }
    suspend fun setChatLockAll(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CHAT_LOCK_ALL] = enabled
            prefs[KEY_CHAT_LOCK_MODE] = if (enabled) "whole_dm" else "individual"
        }
    }
    suspend fun setLockedChatIds(ids: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCKED_CHAT_IDS] = ids
        }
    }
    suspend fun addLockedChatId(id: String) {
        val clean = id.trim().lowercase().removePrefix("@")
        if (clean.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_LOCKED_CHAT_IDS] ?: emptySet()
            prefs[KEY_LOCKED_CHAT_IDS] = current + clean
        }
    }
    suspend fun removeLockedChatId(id: String) {
        val clean = id.trim().lowercase().removePrefix("@")
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_LOCKED_CHAT_IDS] ?: emptySet()
            val usernamesMap = parseUsernamesMap(prefs[KEY_LOCKED_CHAT_USERNAMES]).toMutableMap()
            val mappedUser = usernamesMap[clean]
            val toRemove = buildSet {
                add(clean)
                if (!mappedUser.isNullOrBlank()) add(mappedUser)
            }
            prefs[KEY_LOCKED_CHAT_IDS] = current - toRemove
            usernamesMap.remove(clean)
            if (!mappedUser.isNullOrBlank()) usernamesMap.remove(mappedUser)
            prefs[KEY_LOCKED_CHAT_USERNAMES] = JSONObject(usernamesMap as Map<*, *>).toString()
        }
    }
    suspend fun toggleLockedChat(threadId: String, username: String? = null): Boolean {
        val cleanId = threadId.trim().lowercase()
        if (cleanId.isBlank()) return false
        val cleanUser = username?.trim()?.lowercase()?.removePrefix("@")
        var isNowLocked = false
        context.dataStore.edit { prefs ->
            val currentIds = prefs[KEY_LOCKED_CHAT_IDS] ?: emptySet()
            val usernamesMap = parseUsernamesMap(prefs[KEY_LOCKED_CHAT_USERNAMES]).toMutableMap()
            val existingUser = usernamesMap[cleanId] ?: cleanUser
            val matchesExisting = currentIds.contains(cleanId) ||
                    (!cleanUser.isNullOrBlank() && currentIds.contains(cleanUser)) ||
                    (!existingUser.isNullOrBlank() && currentIds.contains(existingUser))

            if (matchesExisting) {
                val toRemove = buildSet {
                    add(cleanId)
                    if (!cleanUser.isNullOrBlank()) add(cleanUser)
                    if (!existingUser.isNullOrBlank()) add(existingUser)
                }
                prefs[KEY_LOCKED_CHAT_IDS] = currentIds - toRemove
                usernamesMap.remove(cleanId)
                if (!cleanUser.isNullOrBlank()) usernamesMap.remove(cleanUser)
                if (!existingUser.isNullOrBlank()) usernamesMap.remove(existingUser)
                isNowLocked = false
            } else {
                prefs[KEY_LOCKED_CHAT_IDS] = currentIds + cleanId
                val effectiveUser = if (!cleanUser.isNullOrBlank()) cleanUser else existingUser
                if (!effectiveUser.isNullOrBlank()) {
                    usernamesMap[cleanId] = effectiveUser
                    usernamesMap[effectiveUser] = cleanId
                }
                isNowLocked = true
            }
            prefs[KEY_LOCKED_CHAT_USERNAMES] = JSONObject(usernamesMap as Map<*, *>).toString()
        }
        return isNowLocked
    }
    suspend fun mapThreadUsername(threadId: String, username: String) {
        val cleanId = threadId.trim().lowercase()
        val cleanUser = username.trim().lowercase().removePrefix("@")
        if (cleanId.isBlank() || cleanUser.isBlank()) return
        context.dataStore.edit { prefs ->
            val usernamesMap = parseUsernamesMap(prefs[KEY_LOCKED_CHAT_USERNAMES]).toMutableMap()
            usernamesMap[cleanId] = cleanUser
            usernamesMap[cleanUser] = cleanId
            prefs[KEY_LOCKED_CHAT_USERNAMES] = JSONObject(usernamesMap as Map<*, *>).toString()
        }
    }
    suspend fun setUseSeparateChatPin(enabled: Boolean) = setBoolean(KEY_USE_SEPARATE_CHAT_PIN, enabled)
    suspend fun setChatBiometricEnabled(enabled: Boolean) = setBoolean(KEY_CHAT_BIOMETRIC_ENABLED, enabled)

    suspend fun setFirstRunCompleted() {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_FIRST_RUN] = false
        }
    }

    suspend fun resetFirstRun() {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_FIRST_RUN] = true
        }
    }

    suspend fun setDismissedUpdateVersion(version: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_DISMISSED_UPDATE] = version
        }
    }

    suspend fun setStorySearchEnabled(enabled: Boolean) = setBoolean(KEY_STORY_SEARCH_ENABLED, enabled)
    suspend fun setCustomBioFontsEnabled(enabled: Boolean) = setBoolean(KEY_CUSTOM_BIO_FONTS_ENABLED, enabled)

    suspend fun getDismissedUpdateVersion(): String? {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_LAST_DISMISSED_UPDATE]
    }

    companion object {
        val KEY_NO_ADS = booleanPreferencesKey("no_ads")
        val KEY_NO_STORIES = booleanPreferencesKey("no_stories")
        val KEY_NO_REELS = booleanPreferencesKey("no_reels")
        val KEY_NO_AUTOPLAY = booleanPreferencesKey("no_autoplay")
        val KEY_NO_DMS = booleanPreferencesKey("no_dms")
        val KEY_BLUR_EXPLORE = booleanPreferencesKey("set_blur_explore")
        val KEY_BLUR_REELS = booleanPreferencesKey("set_blur_reels")
        val KEY_BLUR_FEED = booleanPreferencesKey("set_blur_feed")
        val KEY_TAP_TO_UNBLUR = booleanPreferencesKey("set_tap_to_unblur")
        val KEY_REQUIRE_LONG_PRESS = booleanPreferencesKey("set_require_long_press")
        val KEY_UNBLUR_MODE = stringPreferencesKey("unblur_mode")
        val KEY_HIDE_SUGGESTED_POSTS = booleanPreferencesKey("hide_suggested_posts")
        val KEY_HIDE_LIKE_COUNTS = booleanPreferencesKey("hide_like_counts")
        val KEY_HIDE_FOLLOWER_COUNTS = booleanPreferencesKey("hide_follower_counts")
        val KEY_HIDE_SHOP_TAB = booleanPreferencesKey("hide_shop_tab")
        val KEY_MINIMAL_MODE_ENABLED = booleanPreferencesKey("minimal_mode_enabled")
        val KEY_HIDE_FEED_POSTS = booleanPreferencesKey("content_posts")
        val KEY_BLOCK_DM_REELS_SWIPING = booleanPreferencesKey("block_dm_reels_swiping")

        val KEY_SHOW_BREATH_GATE = booleanPreferencesKey("set_show_breath_gate")
        val KEY_BREATH_GATE_SECONDS = intPreferencesKey("breath_gate_seconds")
        val KEY_REQUIRE_WORD_CHALLENGE = booleanPreferencesKey("set_require_word_challenge")
        val KEY_WORD_CHALLENGE_COUNT = intPreferencesKey("word_challenge_count")
        val KEY_EFFORT_FRICTION_ENABLED = booleanPreferencesKey("effort_friction_enabled")
        val KEY_COOLDOWN_MINUTES = intPreferencesKey("cooldown_minutes")
        val KEY_DAILY_LIMIT_MINUTES = intPreferencesKey("daily_limit_minutes")
        val KEY_DAILY_LIMIT_AD_DATE = stringPreferencesKey("daily_limit_ad_date")
        val KEY_DAILY_LIMIT_EXTENSIONS_COUNT = intPreferencesKey("daily_limit_extensions_count")

        val KEY_GRAYSCALE_ENABLED = booleanPreferencesKey("grayscale_enabled")
        val KEY_SMOOTH_SCROLL_120HZ = booleanPreferencesKey("smooth_scroll_120hz")
        val KEY_REELS_HISTORY_ENABLED = booleanPreferencesKey("reels_history_enabled")

        val KEY_ADS_WATCHED_ALL_TIME = intPreferencesKey("ads_all_time")
        val KEY_LAST_AD_DATE = stringPreferencesKey("ads_today_date")

        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val KEY_AUTO_LOCK_BACKGROUND = booleanPreferencesKey("auto_lock_background")
        val KEY_IS_FIRST_RUN = booleanPreferencesKey("set_is_first_run")
        val KEY_LAST_DISMISSED_UPDATE = stringPreferencesKey("last_dismissed_update_version")

        val KEY_STARTUP_PAGE = stringPreferencesKey("startup_page")
        val KEY_VIDEO_DOWNLOAD_ENABLED = booleanPreferencesKey("video_download_enabled")
        val KEY_SANITIZE_LINKS = booleanPreferencesKey("set_sanitize_links")
        val KEY_NOTIFY_SESSION_END = booleanPreferencesKey("set_notify_session_end")
        val KEY_NOTIFY_PERSISTENT = booleanPreferencesKey("set_notify_persistent")
        val KEY_NOTIFY_EVERY_5_MINUTES = booleanPreferencesKey("set_notify_every_5_minutes")
        val KEY_PORTRAIT_ONLY = booleanPreferencesKey("portrait_only")
        val KEY_SHOW_SESSION_TIME_IN_BOTTOM_BAR = booleanPreferencesKey("show_session_time_in_bottom_bar")
        val KEY_STORY_SEARCH_ENABLED = booleanPreferencesKey("story_search_enabled")
        val KEY_CUSTOM_BIO_FONTS_ENABLED = booleanPreferencesKey("custom_bio_fonts_enabled")

        val KEY_GHOST_MODE_ENABLED = booleanPreferencesKey("ghost_mode_enabled")
        val KEY_GHOST_HIDE_STORY_SEEN = booleanPreferencesKey("ghost_hide_story_seen")
        val KEY_GHOST_HIDE_DIRECT_SEEN = booleanPreferencesKey("ghost_hide_direct_seen")
        val KEY_GHOST_HIDE_TYPING = booleanPreferencesKey("ghost_hide_typing")
        val KEY_LAST_SESSION_END_TIMESTAMP = longPreferencesKey("last_session_end_timestamp")

        val KEY_ACTIVE_SESSION_DURATION_MINUTES = intPreferencesKey("active_session_duration_minutes")
        val KEY_ACTIVE_SESSION_REMAINING_SECONDS = intPreferencesKey("active_session_remaining_seconds")
        val KEY_ACTIVE_SESSION_ELAPSED_SECONDS = intPreferencesKey("active_session_elapsed_seconds")
        val KEY_ACTIVE_SESSION_REELS_WATCHED = intPreferencesKey("active_session_reels_watched")
        val KEY_ACTIVE_SESSION_REEL_CODES = stringSetPreferencesKey("active_session_reel_codes")
        val KEY_ACTIVE_SESSION_IS_ACTIVE = booleanPreferencesKey("active_session_is_active")

        val KEY_APP_ICON = stringPreferencesKey("app_icon")
        val KEY_CUSTOM_ICONS_ENABLED = booleanPreferencesKey("custom_icons_enabled")
        val KEY_CHAT_LOCK_MODE = stringPreferencesKey("chat_lock_mode")
        val KEY_CHAT_LOCK_ALL = booleanPreferencesKey("chat_lock_all")
        val KEY_LOCKED_CHAT_IDS = stringSetPreferencesKey("locked_chat_ids")
        val KEY_LOCKED_CHAT_USERNAMES = stringPreferencesKey("locked_chat_usernames")
        val KEY_USE_SEPARATE_CHAT_PIN = booleanPreferencesKey("use_separate_chat_pin")
        val KEY_CHAT_BIOMETRIC_ENABLED = booleanPreferencesKey("chat_biometric_enabled")
    }
}
