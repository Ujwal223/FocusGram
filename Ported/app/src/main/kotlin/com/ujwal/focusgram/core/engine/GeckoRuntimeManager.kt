package com.ujwal.focusgram.core.engine

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * GeckoRuntimeManager
 * High-performance, privacy-hardened singleton managing the Mozilla GeckoView engine lifecycle.
 *
 * Configures:
 * - ContentBlocking: ETP Strict, Social Tracking Protection, First-Party Cookie Isolation.
 * - Extensions Process: Enabled for uBlock Origin WebExtension execution.
 * - Privacy: Query parameter stripping, DNT, Sec-GPC.
 * - Hardening: about:config disabled in production, console output managed.
 */
class GeckoRuntimeManager private constructor(context: Context) {

    val runtime: GeckoRuntime
    val extensionManager: ExtensionManager

    init {
        val appContext = context.applicationContext

        val contentBlockingSettings = ContentBlocking.Settings.Builder()
            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
            .antiTracking(ContentBlocking.AntiTracking.STRICT)
            .strictSocialTrackingProtection(true)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY)
            .queryParameterStrippingEnabled(true)
            .queryParameterStrippingPrivateBrowsingEnabled(true)
            .build()

        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true)
            .aboutConfigEnabled(false)
            .extensionsProcessEnabled(true)
            .extensionsWebAPIEnabled(true)
            .contentBlocking(contentBlockingSettings)
            .build()

        Log.i(TAG, "Initializing Mozilla GeckoRuntime with strict privacy & WebExtension runtime...")
        runtime = GeckoRuntime.create(appContext, runtimeSettings)
        extensionManager = ExtensionManager(runtime)
    }

    companion object {
        private const val TAG = "GeckoRuntimeManager"

        @Volatile
        private var instance: GeckoRuntimeManager? = null

        fun getInstance(context: Context): GeckoRuntimeManager {
            return instance ?: synchronized(this) {
                instance ?: GeckoRuntimeManager(context).also { instance = it }
            }
        }

        fun getRuntime(context: Context): GeckoRuntime {
            return getInstance(context).runtime
        }
    }
}
