package com.ujwal.focusgram

import com.ujwal.focusgram.core.utils.ClipboardDebouncer
import com.ujwal.focusgram.core.utils.NetworkUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Dedicated unit tests for [NetworkUtils] and [ClipboardDebouncer].
 * Covers pure-JVM algorithmic paths, URL sanitization edge cases, and clipboard debouncing contracts.
 */
class NetworkUtilsTest {

    @Before
    fun resetDebouncer() {
        ClipboardDebouncer.lastCopiedText = ""
        ClipboardDebouncer.lastCopyTimestamp = 0L
    }

    // ── sanitizeUrl ────────────────────────────────────────────────────────────

    @Test
    fun testSanitizeUrlWithNoQueryStringIsUnchanged() {
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
        val url = "https://www.instagram.com/explore/search/?q=cats"
        assertEquals(url, NetworkUtils.sanitizeUrl(url))
    }

    @Test
    fun testSanitizeUrlStripsOnlyTrackingKeepingOthers() {
        val dirty = "https://www.instagram.com/explore/?igshid=123&q=dogs&hl=en"
        val clean = NetworkUtils.sanitizeUrl(dirty)
        assertEquals("https://www.instagram.com/explore/?q=dogs", clean)
    }

    @Test
    fun testSanitizeUrlPreservesFragment() {
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

    // ── isBareInstagramRootUrl ─────────────────────────────────────────────────

    @Test
    fun testBareInstagramRootUrlDetection() {
        assertTrue(NetworkUtils.isBareInstagramRootUrl("https://www.instagram.com/"))
        assertTrue(NetworkUtils.isBareInstagramRootUrl("https://www.instagram.com"))
        assertTrue(NetworkUtils.isBareInstagramRootUrl("https://instagram.com/"))
        assertTrue(NetworkUtils.isBareInstagramRootUrl("http://www.instagram.com"))
        assertTrue(NetworkUtils.isBareInstagramRootUrl("https://www.instagram.com/?utm_source=ig_web_copy_link&igsh=123"))
        assertTrue(NetworkUtils.isBareInstagramRootUrl("https://www.instagram.com/#fragment"))

        assertFalse(NetworkUtils.isBareInstagramRootUrl("https://www.instagram.com/p/C_abc123/"))
        assertFalse(NetworkUtils.isBareInstagramRootUrl("https://www.instagram.com/reel/C_abc123/"))
        assertFalse(NetworkUtils.isBareInstagramRootUrl("https://www.instagram.com/username/"))
        assertFalse(NetworkUtils.isBareInstagramRootUrl(""))
    }

    // ── isTrackingParam ────────────────────────────────────────────────────────

    @Test
    fun testIsTrackingParam() {
        assertTrue(NetworkUtils.isTrackingParam("igsh"))
        assertTrue(NetworkUtils.isTrackingParam("igshid"))
        assertTrue(NetworkUtils.isTrackingParam("fbclid"))
        assertTrue(NetworkUtils.isTrackingParam("utm_source"))
        assertTrue(NetworkUtils.isTrackingParam("utm_campaign"))
        assertTrue(NetworkUtils.isTrackingParam("ref"))
        assertTrue(NetworkUtils.isTrackingParam("hl"))
        assertTrue(NetworkUtils.isTrackingParam("taken-by"))

        assertFalse(NetworkUtils.isTrackingParam("q"))
        assertFalse(NetworkUtils.isTrackingParam("variant"))
        assertFalse(NetworkUtils.isTrackingParam("id"))
    }

    // ── ClipboardDebouncer ─────────────────────────────────────────────────────

    @Test
    fun testDebouncerAllowsFirstCopy() {
        assertTrue(ClipboardDebouncer.shouldCopy("https://www.instagram.com/p/ABC/", now = 1000L))
    }

    @Test
    fun testDebouncerSuppressesDuplicateWithin1500ms() {
        val url = "https://www.instagram.com/p/ABC/"
        assertTrue(ClipboardDebouncer.shouldCopy(url, now = 1000L))
        assertFalse(ClipboardDebouncer.shouldCopy(url, now = 2000L))
    }

    @Test
    fun testDebouncerAllowsSameCopyAfter1500ms() {
        val url = "https://www.instagram.com/p/ABC/"
        assertTrue(ClipboardDebouncer.shouldCopy(url, now = 1000L))
        assertTrue(ClipboardDebouncer.shouldCopy(url, now = 2500L))
    }

    @Test
    fun testDebouncerAllowsDifferentTextImmediately() {
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
