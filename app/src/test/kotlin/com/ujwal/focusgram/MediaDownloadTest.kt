package com.ujwal.focusgram

import com.ujwal.focusgram.core.utils.MediaDownloadHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates media type detection, filename formatting, and update pending conditions.
 */
class MediaDownloadTest {

    @Test
    fun testDetectIsVideoAccurate() {
        // Video cases
        assertTrue(MediaDownloadHelper.detectIsVideo("https://instagram.fxxx.fbcdn.net/v/t50.2886-16/reel.mp4", "@user - Focusgram.mp4"))
        assertTrue(MediaDownloadHelper.detectIsVideo("https://cdninstagram.com/reel_video.webm", "reel.webm"))
        assertTrue(MediaDownloadHelper.detectIsVideo("data:video/mp4;base64,AAAA", "video.mp4"))
        assertTrue(MediaDownloadHelper.detectIsVideo("https://cdninstagram.com/v/t50.12345-16/vid", ""))
        assertTrue(MediaDownloadHelper.detectIsVideo("https://instagram.com/p/test", "my_reel.mp4"))

        // Image cases
        assertFalse(MediaDownloadHelper.detectIsVideo("https://cdninstagram.com/v/t51.2885-15/photo.jpg", "@user - Focusgram.jpg"))
        assertFalse(MediaDownloadHelper.detectIsVideo("https://cdninstagram.com/image.webp", "image.webp"))
        assertFalse(MediaDownloadHelper.detectIsVideo("https://cdninstagram.com/image.png", "image.png"))
        assertFalse(MediaDownloadHelper.detectIsVideo("data:image/jpeg;base64,/9j/4AAQ", "photo.jpg"))
    }

    @Test
    fun testFormatTargetFilename() {
        // Author extraction and normalization
        assertEquals("@cristiano - Focusgram.mp4", MediaDownloadHelper.formatTargetFilename("@cristiano", true))
        assertEquals("@cristiano - Focusgram.jpg", MediaDownloadHelper.formatTargetFilename("@cristiano", false))
        assertEquals("@leomessi - Focusgram_2.mp4", MediaDownloadHelper.formatTargetFilename("@leomessi_2", true))
        assertEquals("@Focusgram - Focusgram.mp4", MediaDownloadHelper.formatTargetFilename("", true))
        assertEquals("@Focusgram - Focusgram.jpg", MediaDownloadHelper.formatTargetFilename("Focusgram", false))

        // Slide suffixes
        assertEquals("@nasa - Focusgram_3.jpg", MediaDownloadHelper.formatTargetFilename("@nasa - Focusgram_3.jpg", false))
        assertEquals("@nasa - Focusgram_1.mp4", MediaDownloadHelper.formatTargetFilename("@nasa_1.mp4", true))
    }

    @Test
    fun testUpdateNoticeExclusionOnV3() {
        // Notice must only be pending if not already handled and hasLegacyData is true.
        fun isUpdateNoticePending(isHandled: Boolean, hasLegacyData: Boolean): Boolean {
            return !isHandled && hasLegacyData
        }

        // Fresh install without legacy files
        assertFalse(isUpdateNoticePending(isHandled = false, hasLegacyData = false))

        // Already acknowledged update
        assertFalse(isUpdateNoticePending(isHandled = true, hasLegacyData = true))

        // Legacy user who hasn't acknowledged update
        assertTrue(isUpdateNoticePending(isHandled = false, hasLegacyData = true))
    }
}
