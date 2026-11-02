package com.ujwal.focusgram.core.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri

/**
 * Utility for verifying real network connectivity state and URL link sanitation.
 */
object NetworkUtils {

    val TRACKING_PARAM_NAMES = setOf(
        "igsh", "igshid", "fbclid", "ref", "hl", "taken-by",
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "source_ve_path", "ig_rid"
    )

    private val URL_REGEX = Regex("""https?://[^\s<>"']+[^\s<>"'.,!?;:]""", RegexOption.IGNORE_CASE)

    fun isTrackingParam(name: String): Boolean {
        val lower = name.lowercase().trim()
        if (lower.startsWith("utm_")) return true
        return lower in TRACKING_PARAM_NAMES
    }

    /**
     * Sanitizes a raw string URL, removing tracking tokens if valid.
     * Pure string parsing avoids Android framework stub limitations on JVM and handles all URL encodings safely.
     */
    fun sanitizeUrl(urlStr: String): String {
        val trimmed = urlStr.trim()
        if (!trimmed.contains('?')) return trimmed

        val fragment = if (trimmed.contains('#')) "#" + trimmed.substringAfter('#') else ""
        val withoutFragment = trimmed.substringBefore('#')
        if (!withoutFragment.contains('?')) return trimmed

        val base = withoutFragment.substringBefore('?')
        val queryString = withoutFragment.substringAfter('?')

        val remainingParams = queryString.split('&').filter { param ->
            if (param.isBlank()) return@filter false
            val key = param.substringBefore('=').trim()
            !isTrackingParam(key)
        }

        return if (remainingParams.isEmpty()) {
            base + fragment
        } else {
            base + "?" + remainingParams.joinToString("&") + fragment
        }
    }

    /**
     * Strips intrusive tracking parameters (igsh, utm_*, fbclid, ref, hl, taken-by, etc.) from an Android Uri.
     */
    fun sanitizeUri(uri: Uri): Uri {
        val sanitized = sanitizeUrl(uri.toString())
        return try {
            Uri.parse(sanitized)
        } catch (_: Exception) {
            uri
        }
    }

    /**
     * Sanitizes standalone URLs or URLs embedded within arbitrary text (e.g. shared messages or links).
     */
    fun sanitizeText(text: String): String {
        if (!text.contains("http://", ignoreCase = true) && !text.contains("https://", ignoreCase = true)) {
            return text
        }
        val trimmed = text.trim()
        if ((trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) &&
            !trimmed.contains('\n') && !trimmed.contains(' ')
        ) {
            return sanitizeUrl(trimmed)
        }
        return URL_REGEX.replace(text) { matchResult ->
            sanitizeUrl(matchResult.value)
        }
    }

    /**
     * Checks if the device currently has an active network connection with internet capability.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Detects bare Instagram home/root URLs (e.g. `https://www.instagram.com/` or with tracking query params).
     * When copying post links on the home feed, race conditions in the web layer can fall back to the
     * page URL if modal parsing is premature. Rejecting bare root URLs here prevents Android's clipboard
     * and debouncer from getting locked with the home feed address instead of the actual post/reel link.
     */
    fun isBareInstagramRootUrl(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val base = trimmed.substringBefore('?').substringBefore('#').trimEnd('/')
        return base.equals("https://www.instagram.com", ignoreCase = true) ||
               base.equals("http://www.instagram.com", ignoreCase = true) ||
               base.equals("https://instagram.com", ignoreCase = true) ||
               base.equals("http://instagram.com", ignoreCase = true)
    }
}

/**
 * Shared thread-safe clipboard debouncer to eliminate duplicate copies and toasts across
 * WebExtension native messaging and FocusGramNavigationDelegate custom URI scheme.
 */
object ClipboardDebouncer {
    @Volatile
    var lastCopiedText: String = ""
    @Volatile
    var lastCopyTimestamp: Long = 0L

    @Synchronized
    fun shouldCopy(text: String, now: Long = System.currentTimeMillis()): Boolean {
        if (text == lastCopiedText && (now - lastCopyTimestamp) < 1500L) {
            return false
        }
        lastCopiedText = text
        lastCopyTimestamp = now
        return true
    }
}
