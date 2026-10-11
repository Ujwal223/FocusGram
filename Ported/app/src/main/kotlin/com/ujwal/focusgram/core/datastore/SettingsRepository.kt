package com.ujwal.focusgram.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ujwal.focusgram.core.model.FocusSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focusgram_settings")

/**
 * SettingsRepository
 * Reactive Jetpack DataStore repository managing all focus toggles,
 * mindful gate configurations, and rewarded credit balances on-device.
 */
class SettingsRepository(private val context: Context) {

    val settingsFlow: Flow<FocusSettings> = context.dataStore.data.map { prefs ->
        FocusSettings(
            noAds = prefs[KEY_NO_ADS] ?: true,
            noStories = prefs[KEY_NO_STORIES] ?: false,
            noReels = prefs[KEY_NO_REELS] ?: false,
            noAutoplay = prefs[KEY_NO_AUTOPLAY] ?: true,
            noDMs = prefs[KEY_NO_DMS] ?: false,
            blurExplore = prefs[KEY_BLUR_EXPLORE] ?: true,
            blurReels = prefs[KEY_BLUR_REELS] ?: false,
            tapToUnblur = prefs[KEY_TAP_TO_UNBLUR] ?: true,
            requireLongPress = prefs[KEY_REQUIRE_LONG_PRESS] ?: true,
            hideSuggestedPosts = prefs[KEY_HIDE_SUGGESTED_POSTS] ?: true,
            hideLikeCounts = prefs[KEY_HIDE_LIKE_COUNTS] ?: false,
            hideFollowerCounts = prefs[KEY_HIDE_FOLLOWER_COUNTS] ?: false,
            hideShopTab = prefs[KEY_HIDE_SHOP_TAB] ?: true,
            minimalModeEnabled = prefs[KEY_MINIMAL_MODE_ENABLED] ?: false,

            showBreathGate = prefs[KEY_SHOW_BREATH_GATE] ?: true,
            breathGateSeconds = prefs[KEY_BREATH_GATE_SECONDS] ?: 10,
            requireWordChallenge = prefs[KEY_REQUIRE_WORD_CHALLENGE] ?: true,
            wordChallengeCount = prefs[KEY_WORD_CHALLENGE_COUNT] ?: 30,
            effortFrictionEnabled = prefs[KEY_EFFORT_FRICTION_ENABLED] ?: true,
            cooldownMinutes = prefs[KEY_COOLDOWN_MINUTES] ?: 15,
            dailyLimitMinutes = prefs[KEY_DAILY_LIMIT_MINUTES] ?: 60,

            grayscaleEnabled = prefs[KEY_GRAYSCALE_ENABLED] ?: false,
            smoothScroll120Hz = prefs[KEY_SMOOTH_SCROLL_120HZ] ?: true,
            reelsHistoryEnabled = prefs[KEY_REELS_HISTORY_ENABLED] ?: true,

            reelsMinutes = prefs[KEY_REELS_MINUTES] ?: 0,
            instaMinutes = prefs[KEY_INSTA_MINUTES] ?: 0,
            adsWatchedToday = prefs[KEY_ADS_WATCHED_TODAY] ?: 0,
            adsWatchedAllTime = prefs[KEY_ADS_WATCHED_ALL_TIME] ?: 0,
            lastAdDate = prefs[KEY_LAST_AD_DATE] ?: "",
            userLevel = prefs[KEY_USER_LEVEL] ?: 1,
            userXP = prefs[KEY_USER_XP] ?: 0,

            appLockEnabled = prefs[KEY_APP_LOCK_ENABLED] ?: false,
            biometricEnabled = prefs[KEY_BIOMETRIC_ENABLED] ?: false,
            autoLockOnBackground = prefs[KEY_AUTO_LOCK_BACKGROUND] ?: true,
            isFirstRun = prefs[KEY_IS_FIRST_RUN] ?: false,

            ghostModeEnabled = prefs[KEY_GHOST_MODE_ENABLED] ?: false,
            ghostHideStorySeen = prefs[KEY_GHOST_HIDE_STORY_SEEN] ?: true,
            ghostHideDirectSeen = prefs[KEY_GHOST_HIDE_DIRECT_SEEN] ?: true,
            ghostHideTypingIndicator = prefs[KEY_GHOST_HIDE_TYPING] ?: true
        )
    }

    suspend fun setNoAds(enabled: Boolean) = setBoolean(KEY_NO_ADS, enabled)
    suspend fun setNoStories(enabled: Boolean) = setBoolean(KEY_NO_STORIES, enabled)
    suspend fun setNoReels(enabled: Boolean) = setBoolean(KEY_NO_REELS, enabled)
    suspend fun setNoAutoplay(enabled: Boolean) = setBoolean(KEY_NO_AUTOPLAY, enabled)
    suspend fun setNoDMs(enabled: Boolean) = setBoolean(KEY_NO_DMS, enabled)
    suspend fun setBlurExplore(enabled: Boolean) = setBoolean(KEY_BLUR_EXPLORE, enabled)
    suspend fun setBlurReels(enabled: Boolean) = setBoolean(KEY_BLUR_REELS, enabled)
    suspend fun setTapToUnblur(enabled: Boolean) = setBoolean(KEY_TAP_TO_UNBLUR, enabled)
    suspend fun setHideSuggestedPosts(enabled: Boolean) = setBoolean(KEY_HIDE_SUGGESTED_POSTS, enabled)
    suspend fun setHideLikeCounts(enabled: Boolean) = setBoolean(KEY_HIDE_LIKE_COUNTS, enabled)
    suspend fun setHideFollowerCounts(enabled: Boolean) = setBoolean(KEY_HIDE_FOLLOWER_COUNTS, enabled)
    suspend fun setHideShopTab(enabled: Boolean) = setBoolean(KEY_HIDE_SHOP_TAB, enabled)
    suspend fun setMinimalMode(enabled: Boolean) = setBoolean(KEY_MINIMAL_MODE_ENABLED, enabled)

    suspend fun setBreathGate(enabled: Boolean, seconds: Int = 10) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHOW_BREATH_GATE] = enabled
            prefs[KEY_BREATH_GATE_SECONDS] = seconds
        }
    }

    suspend fun setEffortFriction(enabled: Boolean, wordCount: Int = 30) {
        context.dataStore.edit { prefs ->
            prefs[KEY_EFFORT_FRICTION_ENABLED] = enabled
            prefs[KEY_REQUIRE_WORD_CHALLENGE] = enabled
            prefs[KEY_WORD_CHALLENGE_COUNT] = wordCount
        }
    }

    suspend fun setGrayscale(enabled: Boolean) = setBoolean(KEY_GRAYSCALE_ENABLED, enabled)
    suspend fun setCooldownMinutes(minutes: Int) = setInt(KEY_COOLDOWN_MINUTES, minutes)
    suspend fun setDailyLimitMinutes(minutes: Int) = setInt(KEY_DAILY_LIMIT_MINUTES, minutes)

    suspend fun addInstaMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_INSTA_MINUTES] ?: 0
            prefs[KEY_INSTA_MINUTES] = (current + minutes).coerceAtLeast(0)
        }
    }

    suspend fun deductInstaMinute() {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_INSTA_MINUTES] ?: 0
            if (current > 0) prefs[KEY_INSTA_MINUTES] = current - 1
        }
    }

    suspend fun addReelsMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_REELS_MINUTES] ?: 0
            prefs[KEY_REELS_MINUTES] = (current + minutes).coerceAtLeast(0)
        }
    }

    suspend fun deductReelsMinute() {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_REELS_MINUTES] ?: 0
            if (current > 0) prefs[KEY_REELS_MINUTES] = current - 1
        }
    }

    suspend fun recordRewardedAd(todayKey: String, minutesEarned: Int = 2) {
        context.dataStore.edit { prefs ->
            val lastDate = prefs[KEY_LAST_AD_DATE] ?: ""
            val todayCount = if (lastDate == todayKey) (prefs[KEY_ADS_WATCHED_TODAY] ?: 0) else 0
            val allTime = (prefs[KEY_ADS_WATCHED_ALL_TIME] ?: 0) + 1

            prefs[KEY_LAST_AD_DATE] = todayKey
            prefs[KEY_ADS_WATCHED_TODAY] = todayCount + 1
            prefs[KEY_ADS_WATCHED_ALL_TIME] = allTime

            // Credit the minutes
            val currentInsta = prefs[KEY_INSTA_MINUTES] ?: 0
            prefs[KEY_INSTA_MINUTES] = currentInsta + minutesEarned
        }
    }

    suspend fun addXP(amount: Int) {
        context.dataStore.edit { prefs ->
            val currentXP = prefs[KEY_USER_XP] ?: 0
            val newXP = currentXP + amount
            prefs[KEY_USER_XP] = newXP
            // 100 XP per level
            prefs[KEY_USER_LEVEL] = (newXP / 100) + 1
        }
    }

    suspend fun setAppLock(enabled: Boolean, biometric: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_LOCK_ENABLED] = enabled
            prefs[KEY_BIOMETRIC_ENABLED] = biometric
        }
    }

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

    companion object {
        val KEY_NO_ADS = booleanPreferencesKey("no_ads")
        val KEY_NO_STORIES = booleanPreferencesKey("no_stories")
        val KEY_NO_REELS = booleanPreferencesKey("no_reels")
        val KEY_NO_AUTOPLAY = booleanPreferencesKey("no_autoplay")
        val KEY_NO_DMS = booleanPreferencesKey("no_dms")
        val KEY_BLUR_EXPLORE = booleanPreferencesKey("set_blur_explore")
        val KEY_BLUR_REELS = booleanPreferencesKey("set_blur_reels")
        val KEY_TAP_TO_UNBLUR = booleanPreferencesKey("set_tap_to_unblur")
        val KEY_REQUIRE_LONG_PRESS = booleanPreferencesKey("set_require_long_press")
        val KEY_HIDE_SUGGESTED_POSTS = booleanPreferencesKey("hide_suggested_posts")
        val KEY_HIDE_LIKE_COUNTS = booleanPreferencesKey("hide_like_counts")
        val KEY_HIDE_FOLLOWER_COUNTS = booleanPreferencesKey("hide_follower_counts")
        val KEY_HIDE_SHOP_TAB = booleanPreferencesKey("hide_shop_tab")
        val KEY_MINIMAL_MODE_ENABLED = booleanPreferencesKey("minimal_mode_enabled")

        val KEY_SHOW_BREATH_GATE = booleanPreferencesKey("set_show_breath_gate")
        val KEY_BREATH_GATE_SECONDS = intPreferencesKey("breath_gate_seconds")
        val KEY_REQUIRE_WORD_CHALLENGE = booleanPreferencesKey("set_require_word_challenge")
        val KEY_WORD_CHALLENGE_COUNT = intPreferencesKey("word_challenge_count")
        val KEY_EFFORT_FRICTION_ENABLED = booleanPreferencesKey("effort_friction_enabled")
        val KEY_COOLDOWN_MINUTES = intPreferencesKey("cooldown_minutes")
        val KEY_DAILY_LIMIT_MINUTES = intPreferencesKey("daily_limit_minutes")

        val KEY_GRAYSCALE_ENABLED = booleanPreferencesKey("grayscale_enabled")
        val KEY_SMOOTH_SCROLL_120HZ = booleanPreferencesKey("smooth_scroll_120hz")
        val KEY_REELS_HISTORY_ENABLED = booleanPreferencesKey("reels_history_enabled")

        val KEY_REELS_MINUTES = intPreferencesKey("reels_min")
        val KEY_INSTA_MINUTES = intPreferencesKey("insta_min")
        val KEY_ADS_WATCHED_TODAY = intPreferencesKey("ads_today_count")
        val KEY_ADS_WATCHED_ALL_TIME = intPreferencesKey("ads_all_time")
        val KEY_LAST_AD_DATE = stringPreferencesKey("ads_today_date")
        val KEY_USER_LEVEL = intPreferencesKey("user_level")
        val KEY_USER_XP = intPreferencesKey("user_xp")

        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val KEY_AUTO_LOCK_BACKGROUND = booleanPreferencesKey("auto_lock_background")
        val KEY_IS_FIRST_RUN = booleanPreferencesKey("set_is_first_run")

        val KEY_GHOST_MODE_ENABLED = booleanPreferencesKey("ghost_mode_enabled")
        val KEY_GHOST_HIDE_STORY_SEEN = booleanPreferencesKey("ghost_hide_story_seen")
        val KEY_GHOST_HIDE_DIRECT_SEEN = booleanPreferencesKey("ghost_hide_direct_seen")
        val KEY_GHOST_HIDE_TYPING = booleanPreferencesKey("ghost_hide_typing")
    }
}
