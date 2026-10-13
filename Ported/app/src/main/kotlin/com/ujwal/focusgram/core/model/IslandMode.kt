package com.ujwal.focusgram.core.model

/**
 * IslandMode
 * Defines the presentation state of FocusGram's native navigation island
 * and the corresponding content-script body padding for Instagram web.
 *
 * Implements Senior Architecture Blueprint:
 * - Edge-to-edge full-screen GeckoView.
 * - Floating native OLED island pill overlay.
 * - Route-driven visibility and padding states:
 *   - NORMAL: Feed, Explore, User Profile, Direct Inbox (island shown, 96px body padding)
 *   - FLOATING: Reels (full bleed, 0px body padding, translucent island)
 *   - HIDDEN: Direct conversation threads, Comments, Stories, Create post (0px body padding)
 */
enum class IslandMode {
    /**
     * Standard browsing view (Feed, Search/Explore, User Profile, Direct Inbox).
     * Island is shown with frosted glass elevation.
     * Content script applies bottom padding (96px) so content scrolls behind the island.
     */
    NORMAL,

    /**
     * Reels view (/reels, /reel/...).
     * Video container is full-bleed 100vh with 0px body padding to avoid distortion or gaps.
     * Island floats translucently and automatically accommodates full-screen gestures.
     */
    FLOATING,

    /**
     * Hidden state (DM thread /direct/t/, Comments /comments or modal, Stories /stories/, Create /create, Soft keyboard IME).
     * Island is completely animated offscreen so bottom input composers sit completely flush.
     */
    HIDDEN;

    companion object {
        /**
         * Resolves the IslandMode from a given URL path.
         * Uses pure string parsing for cross-platform reliability on both Android runtime and JVM JUnit tests.
         */
        fun fromUrl(url: String?): IslandMode {
            if (url.isNullOrBlank()) return NORMAL
            val path = extractPath(url)

            return when {
                path.startsWith("/accounts/") || path.startsWith("/login") || path.startsWith("/signup") -> HIDDEN
                path.startsWith("/direct") -> HIDDEN
                path.contains("/comments") -> HIDDEN
                path.startsWith("/stories/") -> HIDDEN
                path.startsWith("/create") -> HIDDEN
                path.startsWith("/reels") || path.startsWith("/reel/") -> FLOATING
                else -> NORMAL
            }
        }

        /**
         * Extracts the URL path segment cleanly from absolute or relative URLs.
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
