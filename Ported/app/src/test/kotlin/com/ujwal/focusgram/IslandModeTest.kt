package com.ujwal.focusgram

import com.ujwal.focusgram.core.model.IslandMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * IslandModeTest
 * Unit tests verifying route-driven navigation island states and path extraction
 * according to the senior engineering blueprint:
 * - NORMAL: Feed, Explore, User Profile, DM Inbox (96px body padding)
 * - FLOATING: Reels (full-bleed 100vh viewport, 0px body padding)
 * - HIDDEN: Direct conversation threads, Comments, Stories, Create post (0px body padding)
 */
class IslandModeTest {

    @Test
    fun testExtractPathFromAbsoluteUrls() {
        assertEquals("/", IslandMode.extractPath("https://www.instagram.com/"))
        assertEquals("/", IslandMode.extractPath("https://www.instagram.com"))
        assertEquals("/explore/", IslandMode.extractPath("https://www.instagram.com/explore/"))
        assertEquals("/reels/", IslandMode.extractPath("https://www.instagram.com/reels/"))
        assertEquals("/reel/c12345/", IslandMode.extractPath("https://www.instagram.com/reel/C12345/"))
        assertEquals("/direct/inbox/", IslandMode.extractPath("https://www.instagram.com/direct/inbox/"))
        assertEquals("/direct/t/12345", IslandMode.extractPath("https://www.instagram.com/direct/t/12345"))
        assertEquals("/p/xyz/comments/", IslandMode.extractPath("https://www.instagram.com/p/xyz/comments/"))
        assertEquals("/stories/someuser/123/", IslandMode.extractPath("https://www.instagram.com/stories/someuser/123/"))
        assertEquals("/create/style/", IslandMode.extractPath("https://www.instagram.com/create/style/"))
    }

    @Test
    fun testExtractPathFromRelativeUrls() {
        assertEquals("/", IslandMode.extractPath("/"))
        assertEquals("/explore/", IslandMode.extractPath("/explore/"))
        assertEquals("/reels/", IslandMode.extractPath("/reels/"))
        assertEquals("/direct/t/123", IslandMode.extractPath("/direct/t/123"))
    }

    @Test
    fun testNullAndBlankUrlsDefaultToNormal() {
        assertEquals(IslandMode.NORMAL, IslandMode.fromUrl(null))
        assertEquals(IslandMode.NORMAL, IslandMode.fromUrl(""))
        assertEquals(IslandMode.NORMAL, IslandMode.fromUrl("   "))
    }

    @Test
    fun testStandardNavigationRoutesResolveToNormal() {
        // Feed
        assertEquals(IslandMode.NORMAL, IslandMode.fromUrl("https://www.instagram.com/"))
        assertEquals(IslandMode.NORMAL, IslandMode.fromUrl("https://www.instagram.com"))
        // Explore / Search
        assertEquals(IslandMode.NORMAL, IslandMode.fromUrl("https://www.instagram.com/explore/"))
        assertEquals(IslandMode.NORMAL, IslandMode.fromUrl("https://www.instagram.com/explore"))
        // DM Inbox
        assertEquals(IslandMode.NORMAL, IslandMode.fromUrl("https://www.instagram.com/direct/inbox/"))
        // Profile
        assertEquals(IslandMode.NORMAL, IslandMode.fromUrl("https://www.instagram.com/someuser/"))
    }

    @Test
    fun testReelsRoutesResolveToFloating() {
        assertEquals(IslandMode.FLOATING, IslandMode.fromUrl("https://www.instagram.com/reels/"))
        assertEquals(IslandMode.FLOATING, IslandMode.fromUrl("https://www.instagram.com/reels"))
        assertEquals(IslandMode.FLOATING, IslandMode.fromUrl("https://www.instagram.com/reel/C-xyz123/"))
    }

    @Test
    fun testHiddenRoutesResolveToHidden() {
        // Login / Signup / Accounts routes
        assertEquals(IslandMode.HIDDEN, IslandMode.fromUrl("https://www.instagram.com/accounts/login/"))
        assertEquals(IslandMode.HIDDEN, IslandMode.fromUrl("https://www.instagram.com/accounts/emailsignup/"))
        assertEquals(IslandMode.HIDDEN, IslandMode.fromUrl("https://www.instagram.com/login/"))
        assertEquals(IslandMode.HIDDEN, IslandMode.fromUrl("https://www.instagram.com/signup/"))
        // Direct conversation thread
        assertEquals(IslandMode.HIDDEN, IslandMode.fromUrl("https://www.instagram.com/direct/t/178414000000000/"))
        // Comments route
        assertEquals(IslandMode.HIDDEN, IslandMode.fromUrl("https://www.instagram.com/p/C-xyz123/comments/"))
        // Stories route
        assertEquals(IslandMode.HIDDEN, IslandMode.fromUrl("https://www.instagram.com/stories/username/12345/"))
        // Create post route
        assertEquals(IslandMode.HIDDEN, IslandMode.fromUrl("https://www.instagram.com/create/style/"))
        assertEquals(IslandMode.HIDDEN, IslandMode.fromUrl("https://www.instagram.com/create/select/"))
    }
}
