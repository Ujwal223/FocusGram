package com.ujwal.focusgram.feature.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ujwal.focusgram.core.datastore.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restarts [DmNotificationService] on device boot or package upgrade
 * if the user has opted into Instant Direct Message Notifications.
 */
class DmBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        Log.i(TAG, "Received broadcast action: $action. Evaluating DM notification state...")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val repository = SettingsRepository(context)
                val settings = repository.getSettingsSync()
                if (settings.dmNotificationsEnabled) {
                    Log.i(TAG, "DM notifications are enabled. Restarting DmNotificationService...")
                    DmNotificationService.start(context)
                } else {
                    Log.d(TAG, "DM notifications are disabled. No action taken.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in DmBootReceiver: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "DmBootReceiver"
    }
}

