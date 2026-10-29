package com.ujwal.focusgram.feature.update

import android.content.Context
import android.util.Log
import com.ujwal.focusgram.core.datastore.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for new FocusGram versions and exposes update state.
 *
 * All network requests are read-only HTTP GET calls targeting the public GitHub API,
 * keeping FocusGram fully independent of any third-party app store or proprietary telemetry.
 */
class UpdateCheckerService(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "UpdateChecker"
        private const val GITHUB_LATEST_RELEASE_URL =
            "https://api.github.com/repos/Ujwal223/FocusGram/releases/latest"
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 5000

        /**
         * Compares semver version strings (e.g., "v2.1.0" or "2.0.0").
         * Ignores leading 'v' and trailing prerelease qualifiers for numeric segments.
         */
        fun isNewerVersion(remoteTag: String, localVersion: String): Boolean {
            val cleanRemote = remoteTag.trim().removePrefix("v").substringBefore("-")
            val cleanLocal = localVersion.trim().removePrefix("v").substringBefore("-")

            val remoteParts = cleanRemote.split(".").map { it.toIntOrNull() ?: 0 }
            val localParts = cleanLocal.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
            return false
        }

        fun getMockUpdateInfo(): UpdateInfo {
            return UpdateInfo(
                latestVersion = "3.1.0",
                releaseUrl = "https://github.com/Ujwal223/FocusGram/releases",
                downloadUrl = "https://github.com/Ujwal223/FocusGram/releases/latest",
                whatsNew = "FocusGram v3.1.0 (Simulation Test)\n• Verified update notification & dialog rendering\n• Liquid glass components styling\n• Client-side reels deduplication and rewatch count",
                isUpdateAvailable = true
            )
        }
    }

    /**
     * Queries GitHub for the latest release and checks if it exceeds the running app version.
     *
     * @param ignoreDismissed If true, returns [UpdateCheckResult.Available] even if the user
     *                        previously dismissed this version banner (useful for manual settings check).
     */
    suspend fun checkForUpdates(ignoreDismissed: Boolean = false): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(GITHUB_LATEST_RELEASE_URL)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "FocusGram-Android-Client")
                }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext UpdateCheckResult.Failed("Server returned HTTP $responseCode")
                }

                val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(responseText)

                val tagName = json.optString("tag_name", "").trim()
                val htmlUrl = json.optString("html_url", "").trim()
                val rawBody = json.optString("body", "").trim()

                if (tagName.isEmpty() || htmlUrl.isEmpty()) {
                    return@withContext UpdateCheckResult.Failed("Malformed release payload from GitHub")
                }

                val currentVersion = getCurrentVersionName()
                if (!isNewerVersion(tagName, currentVersion)) {
                    return@withContext UpdateCheckResult.UpToDate
                }

                val cleanVersion = tagName.removePrefix("v")
                if (!ignoreDismissed) {
                    val dismissedVersion = settingsRepository.getDismissedUpdateVersion()
                    if (dismissedVersion == cleanVersion || dismissedVersion == tagName) {
                        return@withContext UpdateCheckResult.UpToDate
                    }
                }

                var downloadUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            val assetUrl = asset.optString("browser_download_url", "")
                            if (assetUrl.isNotEmpty()) {
                                if (name.contains("universal", ignoreCase = true) || downloadUrl == null) {
                                    downloadUrl = assetUrl
                                }
                            }
                        }
                    }
                }

                val trimmedNotes = if (rawBody.length > 2000) {
                    rawBody.substring(0, 2000).trim() + "..."
                } else rawBody

                val info = UpdateInfo(
                    latestVersion = cleanVersion,
                    releaseUrl = htmlUrl,
                    downloadUrl = downloadUrl ?: htmlUrl,
                    whatsNew = trimmedNotes,
                    isUpdateAvailable = true
                )

                UpdateCheckResult.Available(info)
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
                UpdateCheckResult.Failed(e.message ?: "Network error checking updates")
            }
        }

    suspend fun dismissVersion(version: String) {
        settingsRepository.setDismissedUpdateVersion(version)
    }

    private fun getCurrentVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "3.0.0"
        } catch (_: Exception) {
            "3.0.0"
        }
    }
}
