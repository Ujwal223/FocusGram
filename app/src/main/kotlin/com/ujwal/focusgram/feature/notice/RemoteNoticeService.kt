package com.ujwal.focusgram.feature.notice

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Data payload representing a broadcast announcement or critical notice fetched from GitHub.
 */
data class RemoteNotice(
    val show: Boolean,
    val id: String,
    val header: String,
    val body: String,
    val maxShows: Int = 1,
    val buttonText: String = "OK"
)

/**
 * Service that queries GitHub for remote notice/alert configurations (android/popup.json).
 *
 * Allows maintainers to broadcast critical notices or updates to all clients via a GitHub commit.
 * All requests are read-only HTTP GET calls targeting raw GitHub contents.
 */
class RemoteNoticeService(private val context: Context) {

    companion object {
        private const val TAG = "RemoteNoticeService"
        const val POPUP_URL =
            "https://raw.githubusercontent.com/Ujwal223/FocusGram/refs/heads/main/android/popup.json"
        private const val PREFS_NAME = "focusgram_remote_notices"
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 5000

        fun getMockNotice(): RemoteNotice {
            return RemoteNotice(
                show = true,
                id = "test_notice_preview",
                header = "Test Broadcast Notice",
                body = "This is a simulated broadcast notice to verify the dialog rendering, liquid glass styling, and dismiss actions.",
                maxShows = 99,
                buttonText = "Understood"
            )
        }
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Silently fetches the latest notice from GitHub.
     */
    suspend fun fetchNotice(): RemoteNotice? = withContext(Dispatchers.IO) {
        try {
            val urlString = "$POPUP_URL?t=${System.currentTimeMillis()}"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "FocusGram-Android-Client")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val text = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val json = JSONObject(text)

            val show = json.optBoolean("show", false)
            val id = json.optString("id", "").trim()
            val header = json.optString("header", json.optString("title", "Notice")).trim()
            val body = json.optString("body", "").trim()
            val maxShows = json.optInt("max_shows", 1)
            val buttonText = json.optString("button_text", "OK").trim()

            RemoteNotice(
                show = show,
                id = id,
                header = header,
                body = body,
                maxShows = maxShows,
                buttonText = buttonText
            )
        } catch (e: Exception) {
            Log.d(TAG, "Notice fetch silent skip: ${e.message}")
            null
        }
    }

    fun shouldShow(notice: RemoteNotice): Boolean {
        if (!notice.show || notice.id.isBlank()) return false
        val key = "notice_count_${notice.id}"
        val count = prefs.getInt(key, 0)
        return count < notice.maxShows
    }

    fun markShown(noticeId: String) {
        if (noticeId.isBlank()) return
        val key = "notice_count_$noticeId"
        val count = prefs.getInt(key, 0)
        prefs.edit().putInt(key, count + 1).apply()
    }
}
