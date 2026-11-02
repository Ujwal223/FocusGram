package com.ujwal.focusgram

import com.ujwal.focusgram.core.model.FocusSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates Grayscale Schedules evaluation and time parsing utilities.
 */
class GrayscaleScheduleTest {

    @Test
    fun testManualGrayscaleAlwaysActive() {
        val settings = FocusSettings(
            grayscaleEnabled = true,
            grayscaleSchedules = "[]"
        )
        // Should be active regardless of time
        assertTrue(settings.isGrayscaleActive(currentHour = 12, currentMinute = 0))
        assertTrue(settings.isGrayscaleActive(currentHour = 3, currentMinute = 30))
    }

    @Test
    fun testOvernightGrayscaleSchedule() {
        // Schedule: 21:00 to 06:00
        val scheduleJson = """[{"startTime":"21:00","endTime":"06:00","enabled":true}]"""
        val settings = FocusSettings(
            grayscaleEnabled = false,
            grayscaleSchedules = scheduleJson
        )

        // At 21:00 -> Active
        assertTrue(settings.isGrayscaleActive(currentHour = 21, currentMinute = 0))
        // At 23:45 -> Active
        assertTrue(settings.isGrayscaleActive(currentHour = 23, currentMinute = 45))
        // At 03:15 -> Active
        assertTrue(settings.isGrayscaleActive(currentHour = 3, currentMinute = 15))
        // At 05:59 -> Active
        assertTrue(settings.isGrayscaleActive(currentHour = 5, currentMinute = 59))
        // At 06:00 -> Inactive (end of schedule)
        assertFalse(settings.isGrayscaleActive(currentHour = 6, currentMinute = 0))
        // At 12:00 -> Inactive
        assertFalse(settings.isGrayscaleActive(currentHour = 12, currentMinute = 0))
        // At 20:59 -> Inactive
        assertFalse(settings.isGrayscaleActive(currentHour = 20, currentMinute = 59))
    }

    @Test
    fun testDaytimeGrayscaleSchedule() {
        // Schedule: 09:00 to 17:00
        val scheduleJson = """[{"startTime":"09:00","endTime":"17:00","enabled":true}]"""
        val settings = FocusSettings(
            grayscaleEnabled = false,
            grayscaleSchedules = scheduleJson
        )

        // At 09:00 -> Active
        assertTrue(settings.isGrayscaleActive(currentHour = 9, currentMinute = 0))
        // At 14:30 -> Active
        assertTrue(settings.isGrayscaleActive(currentHour = 14, currentMinute = 30))
        // At 17:00 -> Inactive
        assertFalse(settings.isGrayscaleActive(currentHour = 17, currentMinute = 0))
        // At 08:59 -> Inactive
        assertFalse(settings.isGrayscaleActive(currentHour = 8, currentMinute = 59))
    }

    @Test
    fun testDisabledScheduleDoesNotTrigger() {
        val scheduleJson = """[{"startTime":"09:00","endTime":"17:00","enabled":false}]"""
        val settings = FocusSettings(
            grayscaleEnabled = false,
            grayscaleSchedules = scheduleJson
        )

        assertFalse(settings.isGrayscaleActive(currentHour = 12, currentMinute = 0))
    }

    @Test
    fun testMultipleSchedulesEvaluated() {
        // Two schedules: Morning (08:00 - 10:00) and Evening (20:00 - 22:00)
        val scheduleJson = """[
            {"startTime":"08:00","endTime":"10:00","enabled":true},
            {"startTime":"20:00","endTime":"22:00","enabled":true}
        ]"""
        val settings = FocusSettings(
            grayscaleEnabled = false,
            grayscaleSchedules = scheduleJson
        )

        assertTrue(settings.isGrayscaleActive(currentHour = 8, currentMinute = 0))
        assertFalse(settings.isGrayscaleActive(currentHour = 12, currentMinute = 0))
        assertTrue(settings.isGrayscaleActive(currentHour = 21, currentMinute = 30))
        assertFalse(settings.isGrayscaleActive(currentHour = 23, currentMinute = 30))
    }

    @Test
    fun test12HourClockFormatting() {
        assertEquals("9:00 PM", FocusSettings.formatTo12Hour("21:00"))
        assertEquals("6:00 AM", FocusSettings.formatTo12Hour("06:00"))
        assertEquals("12:00 AM", FocusSettings.formatTo12Hour("00:00"))
        assertEquals("12:00 PM", FocusSettings.formatTo12Hour("12:00"))
        assertEquals("12:30 PM", FocusSettings.formatTo12Hour("12:30"))
        assertEquals("9:05 AM", FocusSettings.formatTo12Hour("09:05"))
        assertEquals("9:00 PM", FocusSettings.formatTo12Hour("9:00 PM"))
    }

    @Test
    fun testTimeToMinutesParsing() {
        assertEquals(1260, FocusSettings.parseTimeToMinutes("21:00"))
        assertEquals(1260, FocusSettings.parseTimeToMinutes("9:00 PM"))
        assertEquals(360, FocusSettings.parseTimeToMinutes("6:00 AM"))
        assertEquals(360, FocusSettings.parseTimeToMinutes("06:00"))
        assertEquals(0, FocusSettings.parseTimeToMinutes("12:00 AM"))
        assertEquals(0, FocusSettings.parseTimeToMinutes("00:00"))
        assertEquals(720, FocusSettings.parseTimeToMinutes("12:00 PM"))
        assertEquals(720, FocusSettings.parseTimeToMinutes("12:00"))
        assertEquals(1439, FocusSettings.parseTimeToMinutes("11:59 PM"))
        assertEquals(1439, FocusSettings.parseTimeToMinutes("23:59"))
    }

    @Test
    fun test12HourFormattedScheduleActive() {
        val scheduleJson = """[{"startTime":"9:00 PM","endTime":"6:00 AM","enabled":true}]"""
        val settings = FocusSettings(
            grayscaleEnabled = false,
            grayscaleSchedules = scheduleJson
        )

        // At 22:00 (10 PM) -> Active
        assertTrue(settings.isGrayscaleActive(currentHour = 22, currentMinute = 0))
        // At 04:00 (4 AM) -> Active
        assertTrue(settings.isGrayscaleActive(currentHour = 4, currentMinute = 0))
        // At 12:00 PM -> Inactive
        assertFalse(settings.isGrayscaleActive(currentHour = 12, currentMinute = 0))
    }
}
