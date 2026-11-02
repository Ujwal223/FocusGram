package com.ujwal.focusgram.core.datastore

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Detects whether the app was updated from a legacy version (v2.1.0 or earlier)
 * and cleans up remaining legacy files once the user acknowledges the update.
 */
object LegacyUpdateDetector {

    private const val TAG = "LegacyUpdateDetector"
    const val FLUTTER_SHARED_PREFS_NAME = "FlutterSharedPreferences"
    const val KEY_LEGACY_MARKER = "flutter_migrated_v2"

    /**
     * Inspects local disk storage on-device to determine if old Flutter data exists
     * from an update from version 2.1.0 or earlier.
     * Pure filesystem check : avoids loading Chromium WebView or extra engines on startup.
     *
     * Returns TRUE only if:
     * 1. The legacy FlutterSharedPreferences file exists on disk.
     * 2. It contains legacy `flutter.*` keys.
     * 3. It has not already been marked as acknowledged/cleaned up.
     */
    fun hasLegacyData(context: Context): Boolean {
        val dataDir = context.dataDir
        val prefsFile = File(dataDir, "shared_prefs/$FLUTTER_SHARED_PREFS_NAME.xml")
        if (!prefsFile.exists() || prefsFile.length() == 0L) {
            return false
        }

        try {
            val sharedPrefs = context.getSharedPreferences(FLUTTER_SHARED_PREFS_NAME, Context.MODE_PRIVATE)
            val isAlreadyHandled = sharedPrefs.getBoolean(KEY_LEGACY_MARKER, false) ||
                    sharedPrefs.getBoolean("flutter.$KEY_LEGACY_MARKER", false)
            if (isAlreadyHandled) {
                return false
            }
            val hasFlutterKeys = sharedPrefs.all.keys.any { key ->
                key.startsWith("flutter.") &&
                        key != "flutter.$KEY_LEGACY_MARKER" &&
                        key != KEY_LEGACY_MARKER
            }
            return hasFlutterKeys
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Purges legacy Flutter files, SharedPreferences XML, and cache directories.
     */
    fun purgeLegacyFiles(context: Context) {
        try {
            val dataDir = context.dataDir

            val sharedPrefs = context.getSharedPreferences(FLUTTER_SHARED_PREFS_NAME, Context.MODE_PRIVATE)
            sharedPrefs.edit().clear()
                .putBoolean(KEY_LEGACY_MARKER, true)
                .putBoolean("flutter.$KEY_LEGACY_MARKER", true)
                .commit()

            val prefsFile = File(dataDir, "shared_prefs/$FLUTTER_SHARED_PREFS_NAME.xml")
            if (prefsFile.exists()) {
                prefsFile.delete()
            }
            val prefsBak = File(dataDir, "shared_prefs/${FLUTTER_SHARED_PREFS_NAME}.bak")
            if (prefsBak.exists()) prefsBak.delete()

            val appFlutterDir = File(dataDir, "app_flutter")
            if (appFlutterDir.exists()) {
                appFlutterDir.deleteRecursively()
            }

            val appWebViewDir = File(dataDir, "app_webview")
            if (appWebViewDir.exists()) {
                appWebViewDir.deleteRecursively()
            }
            Log.i(TAG, "✓ Successfully cleaned up legacy files.")
        } catch (e: Exception) {
            Log.w(TAG, "Error during legacy file purge: ${e.message}")
        }
    }
}
