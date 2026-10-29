package com.ujwal.focusgram.feature.history

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Persisted record for a watched Reel item.
 *
 * [author] and [caption] are populated asynchronously as DOM scrapers find them.
 */
data class ReelEntry(
    val code: String,
    val author: String,
    val caption: String,
    val lastSeen: Long,
    val views: Int,
) {
    val url: String get() = "https://www.instagram.com/reel/${code.removePrefix("reel_")}/"
}

/**
 * Manages locally-cached history of viewed Reels, persisted to a JSON file.
 *
 * Keeps up to 500 entries, newest first. Thread-safe via synchronized lock and single-thread executor.
 */
class ReelHistory(private val file: File) {
    private val lock = Any()
    private val io = Executors.newSingleThreadExecutor()
    private val sessionCodes = HashSet<String>()
    private var entries: List<ReelEntry> = load()

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
    val sessionCount: StateFlow<Int> = _sessionCount

    private val _todayCount = MutableStateFlow(calculateTodayCount(entries))
    val todayCount: StateFlow<Int> = _todayCount

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

        fun normalizeCaption(caption: String): String {
            return caption.lowercase()
                .replace(Regex("\\s+"), " ")
                .replace(Regex("[^a-z0-9 ]"), "")
                .trim()
        }

        fun isMatchingReel(
            codeA: String, authorA: String, captionA: String,
            codeB: String, authorB: String, captionB: String
        ): Boolean {
            val cleanCodeA = if (codeA.startsWith("reel_")) codeA.removePrefix("reel_") else codeA
            val cleanCodeB = if (codeB.startsWith("reel_")) codeB.removePrefix("reel_") else codeB
            val cleanAuthorA = authorA.trim().removePrefix("@").lowercase()
            val cleanAuthorB = authorB.trim().removePrefix("@").lowercase()

            val validCodeA = cleanCodeA.length >= 4 && cleanCodeA !in IGNORED_CODES && cleanCodeA != cleanAuthorA
            val validCodeB = cleanCodeB.length >= 4 && cleanCodeB !in IGNORED_CODES && cleanCodeB != cleanAuthorB

            // 1. Direct shortcode match
            if (validCodeA && validCodeB && cleanCodeA.equals(cleanCodeB, ignoreCase = true)) {
                return true
            }

            // 2. Same author matching
            if (cleanAuthorA.isNotBlank() && cleanAuthorA == cleanAuthorB) {
                val normA = normalizeCaption(captionA)
                val normB = normalizeCaption(captionB)
                if (normA.isNotBlank() && normB.isNotBlank()) {
                    val prefixLen = minOf(normA.length, normB.length, 30)
                    if (prefixLen >= 8 && normA.take(prefixLen) == normB.take(prefixLen)) {
                        return true
                    }
                }
                // Author placeholder matching (only when captions match, never blindly merge different reels)
                val isAuthorCodeA = cleanCodeA == cleanAuthorA || !validCodeA
                val isAuthorCodeB = cleanCodeB == cleanAuthorB || !validCodeB
                if (isAuthorCodeA && isAuthorCodeB) {
                    if (normA.isNotBlank() && normB.isNotBlank() && normA.take(15) == normB.take(15)) {
                        return true
                    }
                }
            }

            return false
        }
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
        val matchingEntries = entries.filter { existing ->
            isMatchingReel(
                existing.code, existing.author, existing.caption,
                finalCode, sanitizedAuthor, sanitizedCaption
            ) || existing.code.equals(finalCode, ignoreCase = true)
        }

        val resolvedAuthor = sanitizedAuthor.ifEmpty { matchingEntries.firstOrNull { it.author.isNotBlank() }?.author ?: "" }
        val resolvedCaption = sanitizedCaption.ifEmpty {
            val oldCaption = matchingEntries.firstOrNull { it.caption.isNotBlank() }?.caption ?: ""
            if (!isCaptionInvalid(oldCaption, resolvedAuthor)) oldCaption else ""
        }

        val bestCode = if (finalCode.equals(sanitizedAuthor, ignoreCase = true) && matchingEntries.any { !it.code.equals(sanitizedAuthor, ignoreCase = true) && it.code.length >= 4 }) {
            matchingEntries.first { !it.code.equals(sanitizedAuthor, ignoreCase = true) && it.code.length >= 4 }.code
        } else {
            finalCode
        }

        val maxPriorViews = matchingEntries.maxOfOrNull { it.views } ?: 0
        val newViews = maxOf(maxPriorViews, matchingEntries.size) + 1

        val updated = ReelEntry(
            code = bestCode,
            author = resolvedAuthor,
            caption = resolvedCaption,
            lastSeen = System.currentTimeMillis(),
            views = newViews,
        )

        val remaining = entries.filter { existing ->
            !isMatchingReel(
                existing.code, existing.author, existing.caption,
                finalCode, sanitizedAuthor, sanitizedCaption
            ) && !existing.code.equals(finalCode, ignoreCase = true) &&
               !existing.code.equals(bestCode, ignoreCase = true)
        }

        commit(listOf(updated) + remaining)
        _sessionCount.value = sessionCodes.size
        if (isNew) {
            onNewReelWatched?.invoke(finalCode)
        }
        sessionCodes.size
    }

    fun onMeta(code: String, author: String, caption: String) {
        synchronized(lock) {
            val cleanCode = if (code.startsWith("reel_")) code.removePrefix("reel_") else code
            val cleanAuthor = author.trim().removePrefix("@")
            val lowerAuthor = cleanAuthor.lowercase()
            val isGenericAuthor = lowerAuthor in GENERIC_AUTHORS || lowerAuthor.startsWith("instagram")
            val sanitizedAuthor = if (isGenericAuthor) "" else cleanAuthor
            val sanitizedCaption = sanitizeCaption(caption, sanitizedAuthor)

            if (entries.none { existing ->
                isMatchingReel(
                    existing.code, existing.author, existing.caption,
                    cleanCode, sanitizedAuthor, sanitizedCaption
                ) || existing.code.equals(cleanCode, ignoreCase = true)
            }) return

            commit(entries.map {
                if (isMatchingReel(it.code, it.author, it.caption, cleanCode, sanitizedAuthor, sanitizedCaption) ||
                    it.code.equals(cleanCode, ignoreCase = true)) {
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
                        code = if (cleanCode.length >= 5 && !cleanCode.equals(sanitizedAuthor, ignoreCase = true)) cleanCode else it.code,
                        author = resolvedAuthor,
                        caption = resolvedCaption
                    )
                } else it
            })
        }
    }

    fun delete(code: String) = synchronized(lock) { commit(entries.filter { it.code != code }) }

    fun deleteMatching(code: String, author: String, caption: String) = synchronized(lock) {
        val cleanCode = if (code.startsWith("reel_")) code.removePrefix("reel_") else code
        val cleanAuthor = author.trim().removePrefix("@")
        val cleanCaption = caption.trim()
        commit(entries.filter { existing ->
            !isMatchingReel(
                existing.code, existing.author, existing.caption,
                cleanCode, cleanAuthor, cleanCaption
            ) && !existing.code.equals(cleanCode, ignoreCase = true)
        })
    }

    fun clear() = synchronized(lock) { commit(emptyList()) }

    fun clearToday() = synchronized(lock) {
        val todayStart = getTodayStartMillis()
        commit(entries.filter { it.lastSeen < todayStart })
    }

    fun clearPastDays(days: Int) = synchronized(lock) {
        val cal = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, -(days - 1))
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val cutoff = cal.timeInMillis
        commit(entries.filter { it.lastSeen < cutoff })
    }

    fun clearThisMonth() = synchronized(lock) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val cutoff = cal.timeInMillis
        commit(entries.filter { it.lastSeen < cutoff })
    }

    fun resetSession() = synchronized(lock) {
        sessionCodes.clear()
        _sessionCount.value = 0
    }

    /**
     * Restores in-memory session reel state across app restarts for an ongoing session.
     * Guarantees sessionCount will never regress below fallbackCount even if individual codes
     * were partially persisted.
     */
    fun restoreSession(codes: Set<String>, fallbackCount: Int = 0) = synchronized(lock) {
        sessionCodes.clear()
        sessionCodes.addAll(codes)
        if (sessionCodes.size < fallbackCount) {
            for (i in sessionCodes.size until fallbackCount) {
                sessionCodes.add("restored_reel_$i")
            }
        }
        _sessionCount.value = sessionCodes.size
    }

    fun getSessionCodes(): Set<String> = synchronized(lock) {
        sessionCodes.toSet()
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
            // Writes to a temporary file before renaming to prevent corruption if killed mid-write
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) {
                if (file.exists()) file.delete()
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
            }
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
