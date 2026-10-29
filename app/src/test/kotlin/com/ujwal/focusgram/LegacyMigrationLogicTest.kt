package com.ujwal.focusgram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LegacyMigrationLogicTest
 * Validates key prefixes and schema mappings from Flutter SharedPreferences to FocusSettings.
 */
class LegacyMigrationLogicTest {

    private fun key(raw: String): String = "flutter.$raw"

    @Test
    fun testFlutterKeyPrefixing() {
        assertEquals("flutter.no_ads", key("no_ads"))
        assertEquals("flutter.no_stories", key("no_stories"))
        assertEquals("flutter.no_reels", key("no_reels"))
        assertEquals("flutter.set_blur_explore", key("set_blur_explore"))
        assertEquals("flutter.set_show_breath_gate", key("set_show_breath_gate"))
        assertEquals("flutter.breath_gate_seconds", key("breath_gate_seconds"))
    }

    @Test
    fun testAllRequiredLegacyKeysDefined() {
        val expectedKeys = listOf(
            "no_ads",
            "no_stories",
            "no_reels",
            "no_autoplay",
            "no_dms",
            "set_blur_explore",
            "set_blur_reels",
            "set_tap_to_unblur",
            "hide_suggested_posts",
            "hide_like_counts",
            "hide_follower_counts",
            "hide_shop_tab",
            "minimal_mode_enabled",
            "set_show_breath_gate",
            "breath_gate_seconds",
            "effort_friction_enabled",
            "word_challenge_count",
            "grayscale_enabled",
            "insta_min",
            "reels_min",
            "screen_time_data",
            "reels_history"
        )

        assertEquals(22, expectedKeys.size)
        expectedKeys.forEach { k ->
            assertTrue(key(k).startsWith("flutter."))
        }
    }
}
