package com.ujwal.focusgram

import com.ujwal.focusgram.feature.update.UpdateCheckerService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun testIsNewerVersion() {
        assertTrue(UpdateCheckerService.isNewerVersion("v2.1.0", "2.0.0"))
        assertTrue(UpdateCheckerService.isNewerVersion("2.1.0", "2.0.0"))

        assertTrue(UpdateCheckerService.isNewerVersion("v2.0.1", "2.0.0"))
        assertTrue(UpdateCheckerService.isNewerVersion("2.0.1", "2.0.0"))

        assertTrue(UpdateCheckerService.isNewerVersion("v3.0.0", "2.5.9"))
        assertTrue(UpdateCheckerService.isNewerVersion("v3.0.1", "3.0.0"))
        assertFalse(UpdateCheckerService.isNewerVersion("v3.0.0", "3.0.0"))
        assertFalse(UpdateCheckerService.isNewerVersion("v2.1.0", "3.0.0"))

        assertFalse(UpdateCheckerService.isNewerVersion("v2.0.0", "2.0.0"))
        assertFalse(UpdateCheckerService.isNewerVersion("2.0.0", "2.0.0"))

        assertFalse(UpdateCheckerService.isNewerVersion("v1.9.9", "2.0.0"))
        assertFalse(UpdateCheckerService.isNewerVersion("2.0.0", "2.1.0"))

        assertTrue(UpdateCheckerService.isNewerVersion("v2.1.0-rc1", "2.0.0"))
        assertFalse(UpdateCheckerService.isNewerVersion("v2.0.0-rc1", "2.0.0"))
    }
}
