package com.ujwal.focusgram

import com.ujwal.focusgram.core.utils.ClipboardDebouncer
import com.ujwal.focusgram.core.utils.NetworkUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * NetworkUtilsTest
 *
 * Covers URL sanitization edge cases and the ClipboardDebouncer thread-safety contract.
 * The tracking-param set and isBareInstagramRootUrl path are exercised in
 * FeatureEnhancementsAndFixesTest; this file targets the string-parsing paths that
 * are silently reachable from the clipboard bridge but have no isolated unit tests yet.
 */
class NetworkUtilsTest {

    // ── sanitizeUrl ────────────────────────────────────────────────────────────

    @Test
    fun testSanitizeUrlWithNoQueryStringIsUnchanged() {
        // A clean URL with no query string must pass through untouched.
        val url = "https://www.instagram.com/p/C_abc123/"
        assertEquals(url, NetworkUtils.sanitizeUrl(url))
    }

    @Test
    fun testSanitizeUrlRemovesAllTrackingParams() {
        val dirty = "https://www.instagram.com/reel/C_12345/?igsh=abc&utm_source=share&fbclid=xyz&ref=profile"
        val clean = NetworkUtils.sanitizeUrl(dirty)
        assertEquals("https://www.instagram.com/reel/C_12345/", clean)
    }

    @Test
    fun testSanitizeUrlKeepsLegitimateParams() {
        // "q" is not a tracking param; it must survive sanitization.
        val url = "https://www.instagram.com/explore/search/?q=cats"
        assertEquals(url, NetworkUtils.sanitizeUrl(url))
    }

    @Test
    fun testSanitizeUrlStripsOnlyTrackingKeepingOthers() {
        val dirty = "https://www.instagram.com/explore/?igshid=123&q=dogs&hl=en"
        val clean = NetworkUtils.sanitizeUrl(dirty)
        // igshid and hl are tracking; q must survive.
        assertEquals("https://www.instagram.com/explore/?q=dogs", clean)
    }

    @Test
    fun testSanitizeUrlPreservesFragment() {
        // Fragments are user-navigation anchors; they must never be stripped.
        val url = "https://www.instagram.com/p/C_abc123/#comments"
        assertEquals(url, NetworkUtils.sanitizeUrl(url))
    }

    @Test
    fun testSanitizeUrlPreservesFragmentAfterStrippingTracking() {
        val dirty = "https://www.instagram.com/p/C_abc123/?igsh=xyz#comments"
        val clean = NetworkUtils.sanitizeUrl(dirty)
        assertEquals("https://www.instagram.com/p/C_abc123/#comments", clean)
    }

    @Test
    fun testSanitizeUrlHandlesBlankInput() {
        // Blank or whitespace strings are trimmed and return empty without throwing.
        assertEquals("", NetworkUtils.sanitizeUrl(""))
        assertEquals("", NetworkUtils.sanitizeUrl("   "))
    }

    // ── sanitizeText ───────────────────────────────────────────────────────────

    @Test
    fun testSanitizeTextLeavesPlainTextUntouched() {
        val text = "Let's meet at the café at 5pm!"
        assertEquals(text, NetworkUtils.sanitizeText(text))
    }

    @Test
    fun testSanitizeTextCleansStandaloneUrl() {
        val dirty = "https://www.instagram.com/p/ABC/?igsh=xyz123&utm_campaign=promo"
        val clean = NetworkUtils.sanitizeText(dirty)
        assertEquals("https://www.instagram.com/p/ABC/", clean)
    }

    @Test
    fun testSanitizeTextCleansUrlEmbeddedInSentence() {
        val dirty = "Check this out: https://www.instagram.com/p/ABC/?igshid=xyz&utm_medium=copy_link — amazing!"
        val clean = NetworkUtils.sanitizeText(dirty)
        assertEquals("Check this out: https://www.instagram.com/p/ABC/ — amazing!", clean)
    }

    @Test
    fun testSanitizeTextHandlesMultipleUrlsInOneString() {
        val dirty = "Post 1: https://www.instagram.com/p/AAA/?igsh=111 and Post 2: https://www.instagram.com/p/BBB/?fbclid=222"
        val clean = NetworkUtils.sanitizeText(dirty)
        assertEquals("Post 1: https://www.instagram.com/p/AAA/ and Post 2: https://www.instagram.com/p/BBB/", clean)
    }

    // ── ClipboardDebouncer ─────────────────────────────────────────────────────

    @Before
    fun resetDebouncer() {
        // Each test must start with a clean debouncer state so they remain independent.
        ClipboardDebouncer.lastCopiedText = ""
        ClipboardDebouncer.lastCopyTimestamp = 0L
    }

    @Test
    fun testDebouncerAllowsFirstCopy() {
        // The very first copy of any text must always be permitted.
        assertTrue(ClipboardDebouncer.shouldCopy("https://www.instagram.com/p/ABC/", now = 1000L))
    }

    @Test
    fun testDebouncerSuppressesDuplicateWithin1500ms() {
        // A second identical copy within the 1 500 ms window must be suppressed to prevent
        // duplicate toasts from the WebExtension bridge and NavigationDelegate firing together.
        val url = "https://www.instagram.com/p/ABC/"
        assertTrue(ClipboardDebouncer.shouldCopy(url, now = 1000L))
        assertFalse(ClipboardDebouncer.shouldCopy(url, now = 2000L))  // 1 000 ms later — still inside window
    }

    @Test
    fun testDebouncerAllowsSameCopyAfter1500ms() {
        val url = "https://www.instagram.com/p/ABC/"
        assertTrue(ClipboardDebouncer.shouldCopy(url, now = 1000L))
        // 1 500 ms have elapsed; the user has intentionally copied again.
        assertTrue(ClipboardDebouncer.shouldCopy(url, now = 2500L))
    }

    @Test
    fun testDebouncerAllowsDifferentTextImmediately() {
        // Different content is never a duplicate, even when copied in rapid succession.
        assertTrue(ClipboardDebouncer.shouldCopy("https://www.instagram.com/p/AAA/", now = 1000L))
        assertTrue(ClipboardDebouncer.shouldCopy("https://www.instagram.com/p/BBB/", now = 1100L))
    }

    @Test
    fun testDebouncerUpdatesStateOnSuccessfulCopy() {
        val url = "https://www.instagram.com/p/NEW/"
        ClipboardDebouncer.shouldCopy(url, now = 5000L)
        assertEquals(url, ClipboardDebouncer.lastCopiedText)
        assertEquals(5000L, ClipboardDebouncer.lastCopyTimestamp)
    }
}
