package com.ujwal.focusgram.core.datastore

import android.content.Context
import android.util.Log
import com.ujwal.focusgram.core.database.FocusGramDatabase
import com.ujwal.focusgram.core.database.ReelsHistoryItem
import com.ujwal.focusgram.core.database.ScreenTimeRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * LegacyFlutterDataMigrator
 * Reads FlutterSharedPreferences.xml on first launch of FocusGram Native Kotlin
 * and migrates all existing toggles, screen time data, reels history, and credit balances into
 * Jetpack DataStore and Room Database.
 */
class LegacyFlutterDataMigrator(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val database: FocusGramDatabase
) {

    suspend fun checkAndMigrate() = withContext(Dispatchers.IO) {
        val sharedPrefs = context.getSharedPreferences(FLUTTER_SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val allEntries = sharedPrefs.all

        if (allEntries.isEmpty()) {
            Log.d(TAG, "No legacy FlutterSharedPreferences found. Fresh install or already migrated.")
            return@withContext
        }

        val alreadyMigrated = sharedPrefs.getBoolean(KEY_MIGRATED_MARKER, false)
        if (alreadyMigrated) {
            Log.d(TAG, "Legacy data already migrated previously.")
            return@withContext
        }

        Log.i(TAG, "Found ${allEntries.size} entries in FlutterSharedPreferences. Beginning migration...")

        try {
            // 1. Migrate Focus & Content Toggles
            allEntries[key("no_ads")]?.let { if (it is Boolean) settingsRepository.setNoAds(it) }
            allEntries[key("no_stories")]?.let { if (it is Boolean) settingsRepository.setNoStories(it) }
            allEntries[key("no_reels")]?.let { if (it is Boolean) settingsRepository.setNoReels(it) }
            allEntries[key("no_autoplay")]?.let { if (it is Boolean) settingsRepository.setNoAutoplay(it) }
            allEntries[key("no_dms")]?.let { if (it is Boolean) settingsRepository.setNoDMs(it) }
            allEntries[key("set_blur_explore")]?.let { if (it is Boolean) settingsRepository.setBlurExplore(it) }
            allEntries[key("set_blur_reels")]?.let { if (it is Boolean) settingsRepository.setBlurReels(it) }
            allEntries[key("set_tap_to_unblur")]?.let { if (it is Boolean) settingsRepository.setTapToUnblur(it) }
            allEntries[key("hide_suggested_posts")]?.let { if (it is Boolean) settingsRepository.setHideSuggestedPosts(it) }
            allEntries[key("hide_like_counts")]?.let { if (it is Boolean) settingsRepository.setHideLikeCounts(it) }
            allEntries[key("hide_follower_counts")]?.let { if (it is Boolean) settingsRepository.setHideFollowerCounts(it) }
            allEntries[key("hide_shop_tab")]?.let { if (it is Boolean) settingsRepository.setHideShopTab(it) }
            allEntries[key("minimal_mode_enabled")]?.let { if (it is Boolean) settingsRepository.setMinimalMode(it) }

            // 2. Migrate Mindful Gates & Wellness
            val breathGateEnabled = (allEntries[key("set_show_breath_gate")] as? Boolean) ?: true
            val breathGateSeconds = (allEntries[key("breath_gate_seconds")] as? Number)?.toInt() ?: 10
            settingsRepository.setBreathGate(breathGateEnabled, breathGateSeconds)

            val effortFrictionEnabled = (allEntries[key("effort_friction_enabled")] as? Boolean) ?: true
            val wordChallengeCount = (allEntries[key("word_challenge_count")] as? Number)?.toInt() ?: 30
            settingsRepository.setEffortFriction(effortFrictionEnabled, wordChallengeCount)

            allEntries[key("grayscale_enabled")]?.let { if (it is Boolean) settingsRepository.setGrayscale(it) }

            // 3. Migrate Credits & Ad Balances
            val instaMin = (allEntries[key("insta_min")] as? Number)?.toInt() ?: 0
            if (instaMin > 0) settingsRepository.addInstaMinutes(instaMin)

            val reelsMin = (allEntries[key("reels_min")] as? Number)?.toInt() ?: 0
            if (reelsMin > 0) settingsRepository.addReelsMinutes(reelsMin)

            // 4. Migrate Screen Time Records from JSON string
            val rawScreenTime = allEntries[key("screen_time_data")] as? String
            if (!rawScreenTime.isNullOrBlank()) {
                migrateScreenTime(rawScreenTime)
            }

            // 5. Migrate Reels History from JSON string
            val rawReelsHistory = allEntries[key("reels_history")] as? String
            if (!rawReelsHistory.isNullOrBlank()) {
                migrateReelsHistory(rawReelsHistory)
            }

            // Mark migration complete in legacy prefs
            sharedPrefs.edit().putBoolean(KEY_MIGRATED_MARKER, true).apply()
            Log.i(TAG, "✓ FlutterSharedPreferences data migration completed successfully!")

        } catch (e: Exception) {
            Log.e(TAG, "Error migrating legacy Flutter data: ${e.message}", e)
        }
    }

    private suspend fun migrateScreenTime(rawJson: String) {
        try {
            val json = JSONObject(rawJson)
            val dao = database.screenTimeDao()
            val keys = json.keys()
            while (keys.hasNext()) {
                val date = keys.next()
                val seconds = json.optInt(date, 0)
                if (seconds > 0) {
                    dao.insertOrUpdate(ScreenTimeRecord(date = date, seconds = seconds))
                }
            }
            Log.d(TAG, "Migrated legacy screen time records into Room.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse legacy screen_time_data JSON: ${e.message}")
        }
    }

    private suspend fun migrateReelsHistory(rawJson: String) {
        try {
            val array = JSONArray(rawJson)
            val dao = database.reelsHistoryDao()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val historyItem = ReelsHistoryItem(
                    id = item.optString("id", System.currentTimeMillis().toString() + i),
                    url = item.optString("url", ""),
                    title = item.optString("title", "Instagram Reel"),
                    thumbnailUrl = item.optString("thumbnailUrl", ""),
                    durationSeconds = item.optInt("durationSeconds", 0),
                    adsWatchedInSession = item.optInt("adsWatchedInSession", 0)
                )
                if (historyItem.url.isNotBlank()) {
                    dao.insert(historyItem)
                }
            }
            Log.d(TAG, "Migrated ${array.length()} legacy reels history items into Room.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse legacy reels_history JSON: ${e.message}")
        }
    }

    private fun key(flutterKey: String) = "flutter.$flutterKey"

    companion object {
        private const val TAG = "FlutterDataMigrator"
        private const val FLUTTER_SHARED_PREFS_NAME = "FlutterSharedPreferences"
        private const val KEY_MIGRATED_MARKER = "flutter_migrated_v2"
    }
}
