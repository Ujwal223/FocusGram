package com.ujwal.focusgram.core.utils

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Haptic and vibration feedback utility.
 *
 * Uses direct physical motor pulses (25-60ms) to ensure consistent tactile response
 * across both ERM (eccentric rotating mass) and LRA (linear resonant actuator) hardware,
 * even when system-level touch feedback is toggled off in device settings.
 */
object HapticHelper {

    private const val TAG = "HapticHelper"
    private var lastWheelTickTime = 0L

    /**
     * Retrieves the system Vibrator service using modern, non-deprecated APIs.
     */
    fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(VibratorManager::class.java)
            vibratorManager?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
    }

    /**
     * Drum-wheel scroll tick pulse.
     * 35ms pulse overcomes ERM motor inertia while delivering distinct tactile feedback on LRA hardware.
     * Debounced at 35ms to prevent pulse queueing during rapid flings.
     */
    fun performWheelTick(context: Context, view: View? = null) {
        val now = SystemClock.uptimeMillis()
        if (now - lastWheelTickTime < 35L) return
        lastWheelTickTime = now

        val vibrator = getVibrator(context)
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(35L)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Hardware wheel tick failed: ${e.message}")
            }
        }

        // Companion View haptic for devices that integrate with platform accessibility
        try {
            view?.performHapticFeedback(
                HapticFeedbackConstants.CLOCK_TICK,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        } catch (_: Exception) {}
    }

    /**
     * Performs an alert vibration pattern when a focus session timer expires,
     * daily limit is exhausted, or the lock screen activates.
     * Pattern: Buzz 280ms -> Pause 140ms -> Buzz 280ms.
     */
    fun performSessionCompletionAlert(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        try {
            val pattern = longArrayOf(0, 280, 140, 280)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
            Log.d(TAG, "Session completion vibration triggered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate for session completion: ${e.message}", e)
        }
    }

    /**
     * Performs a tactile click pulse (25ms) when a tab or button is tapped.
     * Uses direct motor pulse so devices with ERM or LRA motors vibrate regardless of
     * system touch-feedback toggles, matching media apps like MX Player.
     */
    fun performClick(context: Context, view: View? = null) {
        val vibrator = getVibrator(context)
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(25L, VibrationEffect.DEFAULT_AMPLITUDE)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(25L)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Hardware click vibration failed: ${e.message}")
            }
        }
        try {
            view?.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        } catch (_: Exception) {}
    }

    /**
     * Performs a tactile long-press pulse (60ms) when long-pressing an element.
     */
    fun performLongPress(context: Context, view: View? = null) {
        val vibrator = getVibrator(context)
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(60L)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Hardware long-press vibration failed: ${e.message}")
            }
        }
        try {
            view?.performHapticFeedback(
                HapticFeedbackConstants.LONG_PRESS,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        } catch (_: Exception) {}
    }

    /**
     * Performs a single medium confirmation pulse (60ms) when completing
     * a mindful breath gate or cooldown waiting period.
     */
    fun performSuccessPulse(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(60L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate success pulse: ${e.message}", e)
        }
    }
}
