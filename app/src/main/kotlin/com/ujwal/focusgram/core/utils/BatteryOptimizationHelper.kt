package com.ujwal.focusgram.core.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Directs users to platform and OEM-specific battery optimization / autostart settings.
 *
 * Aggressive battery managers (MIUI/HyperOS, Samsung OneUI, ColorOS, FuntouchOS, EMUI)
 * kill headless background processes and WebSocket connections unless the user explicitly
 * grants unrestricted battery and background autostart permissions.
 */
object BatteryOptimizationHelper {

    private const val TAG = "BatteryOptHelper"

    /**
     * Checks whether FocusGram is already exempt from standard system battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }
    }

    /**
     * Launches the most specific battery optimization or autostart screen available for the device.
     * Tries manufacturer-specific activities first, cascading down to standard Android settings.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        val packageName = context.packageName

        // Prioritize opening FocusGram's App Info page directly so users can configure
        // battery usage (Unrestricted), permissions, and background settings directly,
        // rather than opening the generic Device Care / Device Maintenance menu.
        val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(appInfoIntent)
            Log.i(TAG, "Successfully launched App Info page for $packageName")
            return
        } catch (e: Exception) {
            Log.d(TAG, "App Info intent failed, trying fallback intents: ${e.message}")
        }

        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val candidateIntents = mutableListOf<Intent>()

        // 1. Manufacturer-specific OEM autostart and power management screens
        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                candidateIntents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    )
                )
                candidateIntents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.powerkeeper",
                            "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                        )
                    ).putExtra("package_name", packageName)
                        .putExtra("package_label", "FocusGram")
                )
            }
            manufacturer.contains("samsung") -> {
                candidateIntents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.samsung.android.lool",
                            "com.samsung.android.sm.battery.ui.BatteryActivity"
                        )
                    )
                )
                candidateIntents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.samsung.android.sm",
                            "com.samsung.android.sm.battery.ui.BatteryActivity"
                        )
                    )
                )
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> {
                candidateIntents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    )
                )
                candidateIntents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.startupapp.StartupAppListActivity"
                        )
                    )
                )
                candidateIntents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.oplus.battery",
                            "com.oplus.battery.PowerConsumptionActivity"
                        )
                    )
                )
            }
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                candidateIntents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                        )
                    )
                )
                candidateIntents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    )
                )
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                candidateIntents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.optimize.process.ProtectActivity"
                        )
                    )
                )
                candidateIntents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                        )
                    )
                )
            }
        }

        // 2. Standard Android AOSP Battery Optimization Request (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            candidateIntents.add(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
            candidateIntents.add(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        }

        // 3. Fallback: Application Details Settings page
        candidateIntents.add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )

        for (intent in candidateIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.i(TAG, "Successfully launched battery settings intent: ${intent.component ?: intent.action}")
                return
            } catch (e: Exception) {
                Log.d(TAG, "Intent candidate failed (${intent.component ?: intent.action}): ${e.message}")
            }
        }

        Log.w(TAG, "All battery optimization intent candidates failed to launch.")
    }
}

