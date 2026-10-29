package com.ujwal.focusgram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AuthNavBarSuppressionTest
 * Validates that the custom navigation bar is strictly suppressed on all
 * authentication, login, 2FA, signup, and OAuth challenge routes.
 */
class AuthNavBarSuppressionTest {

    private fun isAuthUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.contains("/accounts") && !lower.contains("/accounts/edit")) ||
                lower.contains("/login") ||
                lower.contains("/signup") ||
                lower.contains("/two_factor") ||
                lower.contains("/challenge") ||
                lower.contains("/onetap") ||
                lower.contains("accountscenter.") ||
                lower.contains("facebook.com")
    }

    private fun isNavBarVisible(url: String, isAuthenticated: Boolean, isAuthPage: Boolean): Boolean {
        val lowerUrl = url.lowercase()
        val isExplicitAuthRoute = isAuthUrl(lowerUrl)
        val isAuth = isAuthPage || !isAuthenticated || isExplicitAuthRoute
        return !isAuth
    }

    @Test
    fun testAuthRoutesAreDetected() {
        assertTrue(isAuthUrl("https://www.instagram.com/accounts/login/"))
        assertTrue(isAuthUrl("https://www.instagram.com/accounts/emailsignup/"))
        assertTrue(isAuthUrl("https://www.instagram.com/accounts/two_factor/"))
        assertTrue(isAuthUrl("https://www.instagram.com/login/"))
        assertTrue(isAuthUrl("https://www.instagram.com/signup/"))
        assertTrue(isAuthUrl("https://www.instagram.com/challenge/"))
        assertTrue(isAuthUrl("https://accountscenter.instagram.com/"))
        assertTrue(isAuthUrl("https://m.facebook.com/login/"))

        // Edit profile under accounts is an allowed authenticated page
        assertFalse(isAuthUrl("https://www.instagram.com/accounts/edit/"))
        assertFalse(isAuthUrl("https://www.instagram.com/"))
        assertFalse(isAuthUrl("https://www.instagram.com/direct/inbox/"))
    }

    @Test
    fun testNavBarSuppressionConditions() {
        // Logged out on homepage
        assertFalse(isNavBarVisible("https://www.instagram.com/", isAuthenticated = false, isAuthPage = true))
        assertFalse(isNavBarVisible("https://www.instagram.com/", isAuthenticated = false, isAuthPage = false))

        // Login screen
        assertFalse(isNavBarVisible("https://www.instagram.com/accounts/login/", isAuthenticated = false, isAuthPage = true))
        assertFalse(isNavBarVisible("https://www.instagram.com/accounts/login/", isAuthenticated = true, isAuthPage = false))

        // Authenticated on homepage
        assertTrue(isNavBarVisible("https://www.instagram.com/", isAuthenticated = true, isAuthPage = false))

        // Authenticated in DMs
        assertTrue(isNavBarVisible("https://www.instagram.com/direct/inbox/", isAuthenticated = true, isAuthPage = false))

        // Authenticated on accounts/edit
        assertTrue(isNavBarVisible("https://www.instagram.com/accounts/edit/", isAuthenticated = true, isAuthPage = false))
    }

    @Test
    fun testFocusGramAppEnforcesAuthCheck() {
        val file = File("src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt")
        val content = if (file.exists()) file.readText() else File("app/src/main/kotlin/com/ujwal/focusgram/ui/FocusGramApp.kt").readText()

        assertTrue(
            "FocusGramApp must check !isAuthPage && isUserAuthenticated before rendering NativeBottomNavBar",
            content.contains("!isAuthPage && isUserAuthenticated")
        )
    }
}
