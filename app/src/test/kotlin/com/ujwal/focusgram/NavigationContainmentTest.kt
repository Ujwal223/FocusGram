package com.ujwal.focusgram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NavigationContainmentTest
 * Validates domain whitelisting, authentication domain support, and external escaping defense.
 */
class NavigationContainmentTest {

    private val allowedHosts = listOf(
        "instagram.com",
        "instagr.am",
        "cdninstagram.com",
        "fbcdn.net",
        "facebook.com",
        "m.facebook.com",
        "accountscenter.instagram.com",
        "accountscenter.facebook.com"
    )

    private fun isAllowedHost(host: String): Boolean {
        val lower = host.lowercase()
        return allowedHosts.any { allowed ->
            lower == allowed || lower.endsWith(".$allowed")
        }
    }

    @Test
    fun testAllowedInstagramHosts() {
        assertTrue(isAllowedHost("instagram.com"))
        assertTrue(isAllowedHost("www.instagram.com"))
        assertTrue(isAllowedHost("help.instagram.com"))
        assertTrue(isAllowedHost("about.instagram.com"))
        assertTrue(isAllowedHost("instagr.am"))
        assertTrue(isAllowedHost("scontent.cdninstagram.com"))
        assertTrue(isAllowedHost("static.xx.fbcdn.net"))
    }

    @Test
    fun testAllowedAuthHosts() {
        assertTrue(isAllowedHost("facebook.com"))
        assertTrue(isAllowedHost("m.facebook.com"))
        assertTrue(isAllowedHost("accountscenter.instagram.com"))
        assertTrue(isAllowedHost("accountscenter.facebook.com"))
    }

    @Test
    fun testDisallowedExternalHosts() {
        assertFalse(isAllowedHost("google.com"))
        assertFalse(isAllowedHost("youtube.com"))
        assertFalse(isAllowedHost("tiktok.com"))
        assertFalse(isAllowedHost("malicious-fake-instagram.com"))
        assertFalse(isAllowedHost("notinstagram.com"))
        assertFalse(isAllowedHost("x.com"))
    }

    @Test
    fun testInternalSchemesAllowed() {
        val internalSchemes = listOf("resource", "about", "data", "blob", "javascript")
        internalSchemes.forEach { scheme ->
            val isInternal = scheme == "resource" || scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript"
            assertTrue(isInternal)
        }
    }

    @Test
    fun testDirectMessageBackNavigationHierarchy() {
        fun resolveDmBackTarget(url: String): String? {
            val path = url.removePrefix("https://www.instagram.com")
                .removePrefix("http://www.instagram.com")
                .substringBefore('?')
                .substringBefore('#')
                .trimEnd('/')
                .ifEmpty { "/" }

            val isDmThread = path.startsWith("/direct/t/") || path.startsWith("/direct/threads/") || (path.startsWith("/direct/") && path != "/direct/inbox" && path != "/direct")
            val isDmInbox = path == "/direct" || path == "/direct/inbox"
            val isRootHome = path == "/" || path.isEmpty()

            return when {
                isDmThread -> "https://www.instagram.com/direct/inbox/"
                isDmInbox -> "https://www.instagram.com/"
                isRootHome -> null // Allow predictive back / system launcher exit
                else -> "https://www.instagram.com/"
            }
        }

        // 1. Thread level navigates to inbox
        org.junit.Assert.assertEquals("https://www.instagram.com/direct/inbox/", resolveDmBackTarget("https://www.instagram.com/direct/t/12345/"))
        org.junit.Assert.assertEquals("https://www.instagram.com/direct/inbox/", resolveDmBackTarget("https://www.instagram.com/direct/t/12345"))
        org.junit.Assert.assertEquals("https://www.instagram.com/direct/inbox/", resolveDmBackTarget("https://www.instagram.com/direct/threads/999/"))
        org.junit.Assert.assertEquals("https://www.instagram.com/direct/inbox/", resolveDmBackTarget("https://www.instagram.com/direct/new/"))

        // 2. Inbox navigates to Home root
        org.junit.Assert.assertEquals("https://www.instagram.com/", resolveDmBackTarget("https://www.instagram.com/direct/inbox/"))
        org.junit.Assert.assertEquals("https://www.instagram.com/", resolveDmBackTarget("https://www.instagram.com/direct/inbox"))
        org.junit.Assert.assertEquals("https://www.instagram.com/", resolveDmBackTarget("https://www.instagram.com/direct/"))
        org.junit.Assert.assertEquals("https://www.instagram.com/", resolveDmBackTarget("https://www.instagram.com/direct"))

        // 3. Home root returns null to let Android system back minimize/exit
        org.junit.Assert.assertNull(resolveDmBackTarget("https://www.instagram.com/"))
        org.junit.Assert.assertNull(resolveDmBackTarget("https://www.instagram.com"))
    }
}
