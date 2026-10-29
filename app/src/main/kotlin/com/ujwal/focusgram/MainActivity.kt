package com.ujwal.focusgram

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ujwal.focusgram.core.ui.theme.FocusGramTheme
import com.ujwal.focusgram.ui.FocusGramApp

/**
 * Single-Activity host mounting Compose UI and coordinating window attributes.
 *
 * Requests the highest available display refresh mode (e.g., 90Hz/120Hz) on supported hardware
 * and keeps window-level task descriptions aligned with the active launcher icon alias.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        applyHighRefreshRate()
        setContent {
            FocusGramTheme {
                FocusGramApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val appIcon = com.ujwal.focusgram.core.datastore.SettingsRepository(this).getSettingsSync().appIcon
            com.ujwal.focusgram.core.utils.AppIconManager.applyTaskDescription(this, appIcon)
        } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        // Ensure Live Session Time Counter notification auto-dismisses immediately when minimized
        com.ujwal.focusgram.feature.screentime.ScreenTimeService.stopSession(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        com.ujwal.focusgram.feature.screentime.ScreenTimeService.stopSession(this)
    }

    private fun applyHighRefreshRate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val display = display
                val modes = display?.supportedModes ?: emptyArray()
                val maxMode = modes.maxByOrNull { it.refreshRate }
                if (maxMode != null) {
                    val params = window.attributes
                    params.preferredDisplayModeId = maxMode.modeId
                    params.preferredRefreshRate = maxMode.refreshRate
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        params.setFrameRateBoostOnTouchEnabled(true)
                    }
                    window.attributes = params
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val dm = getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
                val modes = dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.supportedModes ?: emptyArray()
                val maxMode = modes.maxByOrNull { it.refreshRate }
                if (maxMode != null) {
                    val params = window.attributes
                    params.preferredDisplayModeId = maxMode.modeId
                    params.preferredRefreshRate = maxMode.refreshRate
                    window.attributes = params
                }
            }
        } catch (_: Exception) {}
    }
}
