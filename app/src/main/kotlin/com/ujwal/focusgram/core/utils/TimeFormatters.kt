package com.ujwal.focusgram.core.utils

import java.util.Locale

object TimeFormatters {
    /**
     * Formats duration in minutes to a human-readable "Xh Ym", "Xh", or "Ym".
     * Never outputs raw minutes over 59 (e.g. 270 min -> "4h 30m").
     */
    fun formatMinutes(minutes: Int): String {
        if (minutes <= 0) return "0m"
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    /**
     * Formats seconds into MM:SS or HH:MM:SS.
     */
    fun formatSeconds(seconds: Int): String {
        if (seconds <= 0) return "00:00"
        val hours = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }
}
