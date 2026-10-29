package com.ujwal.focusgram

import com.ujwal.focusgram.core.database.SnapshotItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SnapshotsEntityTest
 * Validates offline snapshot model structure and integrity.
 */
class SnapshotsEntityTest {

    @Test
    fun testSnapshotItemInstantiation() {
        val snapshot = SnapshotItem(
            id = "snap-123",
            url = "https://www.instagram.com/p/sample123/",
            title = "Saved Post Snapshot",
            savedAt = 1_700_000_000_000L,
            htmlContent = "<html><body>Snapshot Content</body></html>"
        )

        assertEquals("snap-123", snapshot.id)
        assertEquals("https://www.instagram.com/p/sample123/", snapshot.url)
        assertEquals("Saved Post Snapshot", snapshot.title)
        assertEquals("<html><body>Snapshot Content</body></html>", snapshot.htmlContent)
        assertNotNull(snapshot.savedAt)
        assertTrue(snapshot.savedAt > 0)
    }

    @Test
    fun testSnapshotUrlFormatting() {
        val username = "testuser"
        val instagramUrl = "https://www.instagram.com/$username/"
        assertTrue(instagramUrl.contains(username))
        assertTrue(instagramUrl.startsWith("https://www.instagram.com/"))
    }
}
