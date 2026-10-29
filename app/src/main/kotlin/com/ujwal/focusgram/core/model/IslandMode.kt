package com.ujwal.focusgram.core.model

/**
 * Presentation states for the native navigation island overlay and corresponding
 * page bottom padding in Instagram web.
 *
 * Instagram web layouts rely on CSS viewport heights and sticky footers. Injecting
 * 96px bottom padding prevents the floating island from obscuring buttons in feed
 * and explore, while full-bleed views (Reels) need 0px padding to avoid letterboxing.
 */
enum class IslandMode {
    /**
     * Standard browsing view (Feed, Search/Explore, User Profile, Direct Inbox).
     * The native island is displayed and web content receives bottom padding (96px).
     */
    NORMAL,

    /**
     * Reels view (/reels, /reel/...).
     * Full-screen 100vh layout with 0px padding to prevent video letterboxing or scroll glitches.
     * The island is rendered with reduced opacity.
     */
    FLOATING,

    /**
     * Active interaction view (DM conversations, comment sheets, story viewer, create composer).
     * The island is translated offscreen so input fields align directly with the software keyboard.
     */
    HIDDEN;

    companion object {
        /**
         * Resolves the navigation mode for a given URL path.
         *
         * Uses string splitting rather than android.net.Uri to allow direct execution
         * in host JVM unit tests without Android mock stubs.
         */
        fun fromUrl(url: String?): IslandMode {
            if (url.isNullOrBlank()) return NORMAL
            val path = extractPath(url)

            return when {
                (path.startsWith("/accounts/") && !path.startsWith("/accounts/edit")) || path.startsWith("/login") || path.startsWith("/signup") -> HIDDEN
                path.startsWith("/direct") -> HIDDEN
                path.contains("/comments") -> HIDDEN
                path.startsWith("/stories/") -> HIDDEN
                path.startsWith("/create") -> HIDDEN
                path.startsWith("/reels") || path.startsWith("/reel/") -> FLOATING
                else -> NORMAL
            }
        }

        /**
         * Returns the path portion of a URL, defaulting to "/" if no path exists.
         */
        fun extractPath(url: String): String {
            val clean = url.trim().lowercase()
            val withoutScheme = if (clean.contains("://")) {
                clean.substringAfter("://")
            } else {
                clean
            }
            val pathPart = if (withoutScheme.contains("/")) {
                withoutScheme.substringAfter("/")
            } else {
                ""
            }
            return if (pathPart.isNotEmpty()) "/$pathPart" else "/"
        }
    }
}
