package com.ujwal.focusgram.feature.history

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class ReelEntry(
    val code: String,
    val author: String,    // may be empty if no source had it
    val caption: String,   // empty is normal, many reels have no caption
    val lastSeen: Long,    // epoch millis
    val views: Int,
) {
    val url: String get() = "https://www.instagram.com/reel/${code.removePrefix("reel_")}/"
}

class ReelHistory(private val file: File) {
    private val lock = Any()
    private val io = Executors.newSingleThreadExecutor()
    private val sessionCodes = HashSet<String>()
    private var entries: List<ReelEntry> = load()   // newest first

    var onNewReelWatched: ((String) -> Unit)? = null

    private fun getTodayStartMillis(): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun calculateTodayCount(list: List<ReelEntry>): Int {
        val start = getTodayStartMillis()
        return list.count { it.lastSeen >= start }
    }

    private val _items = MutableStateFlow(entries)
    val items: StateFlow<List<ReelEntry>> = _items

    private val _sessionCount = MutableStateFlow(0)
    val sessionCount: StateFlow<Int> = _sessionCount   // unique reels since app start

    private val _todayCount = MutableStateFlow(calculateTodayCount(entries))
    val todayCount: StateFlow<Int> = _todayCount       // unique reels watched today

    companion object {
        private val IGNORED_CODES = setOf(
            "home", "explore", "reels", "reel", "direct", "accounts",
            "stories", "p", "about", "legal", "videos", "audio", "clips",
            "trending", "foryou", "following", "live"
        )
        private val GENERIC_AUTHORS = setOf(
            "home", "explore", "reels", "reel", "direct", "instagram",
            "instagram creator", "instagram user", "instagram video", "creator", "user", ""
        )
        private val GENERIC_CAPTIONS = setOf(
            "instagram reel", "reel", "reels", "instagram", "instagram video", "video", ""
        )
    }

    private fun sanitizeCaption(rawCaption: String, cleanAuthor: String): String {
        val clean = rawCaption.trim()
        val lower = clean.lowercase()
        val isGeneric = lower.isBlank() ||
            lower in GENERIC_CAPTIONS ||
            lower.startsWith("instagram reel") ||
            lower.startsWith("reel by @") ||
            lower.startsWith("reel by ") ||
            (cleanAuthor.isNotBlank() && lower == cleanAuthor.lowercase()) ||
            lower.endsWith("· original audio") ||
            lower.endsWith("original audio")
        return if (isGeneric) "" else clean
    }

    private fun isCaptionInvalid(caption: String, author: String): Boolean {
        return sanitizeCaption(caption, author).isBlank()
    }

    fun onWatched(code: String, author: String, caption: String): Int = synchronized(lock) {
        val cleanCode = code.trim().removePrefix("/").removeSuffix("/")
        val lowerCode = cleanCode.lowercase()
        if (cleanCode.length < 5 || lowerCode in IGNORED_CODES) {
            return _sessionCount.value
        }
        val finalCode = if (cleanCode.startsWith("reel_")) cleanCode.removePrefix("reel_") else cleanCode
        if (finalCode.length < 5 || finalCode.lowercase() in IGNORED_CODES) {
            return _sessionCount.value
        }

        val cleanAuthor = author.trim().removePrefix("@")
        val lowerAuthor = cleanAuthor.lowercase()
        val isGenericAuthor = lowerAuthor in GENERIC_AUTHORS || lowerAuthor.startsWith("instagram")
        val sanitizedAuthor = if (isGenericAuthor) "" else cleanAuthor

        val sanitizedCaption = sanitizeCaption(caption, sanitizedAuthor)

        val isNew = sessionCodes.add(finalCode)
        val old = entries.firstOrNull { it.code == finalCode }

        val resolvedAuthor = sanitizedAuthor.ifEmpty { old?.author ?: "" }
        val resolvedCaption = sanitizedCaption.ifEmpty {
            val oldCaption = old?.caption ?: ""
            if (!isCaptionInvalid(oldCaption, resolvedAuthor)) oldCaption else ""
        }

        val updated = ReelEntry(
            code = finalCode,
            author = resolvedAuthor,
            caption = resolvedCaption,
            lastSeen = System.currentTimeMillis(),
            views = (old?.views ?: 0) + 1,
        )
        commit(listOf(updated) + entries.filter { it.code != finalCode })
        _sessionCount.value = sessionCodes.size
        if (isNew) {
            onNewReelWatched?.invoke(finalCode)
        }
        sessionCodes.size
    }

    fun onMeta(code: String, author: String, caption: String) {
        synchronized(lock) {
            val cleanCode = if (code.startsWith("reel_")) code.removePrefix("reel_") else code
            if (entries.none { it.code == cleanCode }) return

            val cleanAuthor = author.trim().removePrefix("@")
            val lowerAuthor = cleanAuthor.lowercase()
            val isGenericAuthor = lowerAuthor in GENERIC_AUTHORS || lowerAuthor.startsWith("instagram")
            val sanitizedAuthor = if (isGenericAuthor) "" else cleanAuthor

            val sanitizedCaption = sanitizeCaption(caption, sanitizedAuthor)

            commit(entries.map {
                if (it.code == cleanCode) {
                    val resolvedAuthor = sanitizedAuthor.ifEmpty { it.author }
                    val currentCaptionInvalid = isCaptionInvalid(it.caption, resolvedAuthor)
                    val resolvedCaption = if (sanitizedCaption.isNotEmpty()) {
                        sanitizedCaption
                    } else if (!currentCaptionInvalid) {
                        it.caption
                    } else {
                        ""
                    }
                    it.copy(
                        author = resolvedAuthor,
                        caption = resolvedCaption
                    )
                } else it
            })
        }
    }

    fun delete(code: String) = synchronized(lock) { commit(entries.filter { it.code != code }) }

    fun clear() = synchronized(lock) { commit(emptyList()) }

    fun resetSession() = synchronized(lock) {
        sessionCodes.clear()
        _sessionCount.value = 0
    }

    private fun commit(list: List<ReelEntry>) {
        entries = list.take(500)
        _items.value = entries
        _todayCount.value = calculateTodayCount(entries)
        val snapshot = entries
        io.execute { save(snapshot) }
    }

    private fun save(list: List<ReelEntry>) {
        try {
            val arr = JSONArray()
            list.forEach {
                arr.put(
                    JSONObject()
                        .put("code", it.code)
                        .put("author", it.author)
                        .put("caption", it.caption)
                        .put("lastSeen", it.lastSeen)
                        .put("views", it.views)
                )
            }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        } catch (_: Exception) {}
    }

    private fun load(): List<ReelEntry> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ReelEntry(
                    o.getString("code"),
                    o.optString("author"),
                    o.optString("caption"),
                    o.optLong("lastSeen"),
                    o.optInt("views", 1)
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
