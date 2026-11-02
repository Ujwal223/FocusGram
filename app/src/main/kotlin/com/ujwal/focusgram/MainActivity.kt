package com.ujwal.focusgram

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ujwal.focusgram.core.ui.theme.FocusGramTheme
import com.ujwal.focusgram.ui.FocusGramApp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
        handleIncomingIntent(intent)
        setContent {
            FocusGramTheme {
                FocusGramApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val url = intent?.getStringExtra(com.ujwal.focusgram.feature.notifications.DmNotificationManager.EXTRA_TARGET_URL)
            ?: intent?.dataString
        if (!url.isNullOrBlank() && (url.startsWith("https://www.instagram.com") || url.startsWith("https://instagram.com"))) {
            pendingNavigationUrl.value = url
            // Consume the intent payload so activity recreation / configuration changes do not re-navigate
            intent?.removeExtra(com.ujwal.focusgram.feature.notifications.DmNotificationManager.EXTRA_TARGET_URL)
            intent?.data = null
        }
    }

    override fun onResume() {
        super.onResume()
        com.ujwal.focusgram.feature.notifications.DmNotificationManager.getInstance(this).isAppInForeground = true
        com.ujwal.focusgram.feature.notifications.DmNotificationService.onAppForegroundChanged(this, true)
        lifecycleScope.launch {
            try {
                val appIcon = com.ujwal.focusgram.core.datastore.SettingsRepository(this@MainActivity).settingsFlow.first().appIcon
                com.ujwal.focusgram.core.utils.AppIconManager.applyTaskDescription(this@MainActivity, appIcon)
            } catch (_: Exception) {}
        }
    }

    override fun onPause() {
        super.onPause()
        com.ujwal.focusgram.feature.notifications.DmNotificationManager.getInstance(this).isAppInForeground = false
        com.ujwal.focusgram.feature.notifications.DmNotificationService.onAppForegroundChanged(this, false)
    }

    override fun onStop() {
        super.onStop()
        com.ujwal.focusgram.feature.notifications.DmNotificationManager.getInstance(this).isAppInForeground = false
        // Pause session tracking when minimized to keep elapsed time accurate on resume
        com.ujwal.focusgram.feature.screentime.ScreenTimeService.pauseSession(this)
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        params.setFrameRateBoostOnTouchEnabled(true)
                    }
                    window.attributes = params
                }
            } else {
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

    companion object {
        val pendingNavigationUrl = MutableStateFlow<String?>(null)
    }
}
