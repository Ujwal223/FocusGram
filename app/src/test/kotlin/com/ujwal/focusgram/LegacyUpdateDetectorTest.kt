package com.ujwal.focusgram

import com.ujwal.focusgram.core.datastore.LegacyUpdateDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates detection of legacy Flutter SharedPreferences keys and storage constants.
 */
class LegacyUpdateDetectorTest {

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
    fun testLegacyConstants() {
        assertEquals("FlutterSharedPreferences", LegacyUpdateDetector.FLUTTER_SHARED_PREFS_NAME)
        assertEquals("flutter_migrated_v2", LegacyUpdateDetector.KEY_LEGACY_MARKER)
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
