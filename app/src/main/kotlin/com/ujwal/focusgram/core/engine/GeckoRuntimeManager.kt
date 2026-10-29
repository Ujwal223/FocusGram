package com.ujwal.focusgram.core.engine

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Initializes and owns the singleton [GeckoRuntime] lifecycle.
 *
 * Configures strict tracking protection (ETP strict, social tracking isolation,
 * query parameter stripping) and enables WebExtension execution for client-side content scripts.
 */
class GeckoRuntimeManager private constructor(context: Context) {

    val runtime: GeckoRuntime
    val extensionManager: ExtensionManager
    val reelHistory: com.ujwal.focusgram.feature.history.ReelHistory

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
            .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
            .build()

        Log.i(TAG, "Initializing Mozilla GeckoRuntime with strict privacy & WebExtension runtime...")
        runtime = GeckoRuntime.create(appContext, runtimeSettings)
        extensionManager = ExtensionManager(runtime)
        reelHistory = com.ujwal.focusgram.feature.history.ReelHistory(java.io.File(appContext.filesDir, "reel_history.json"))

        runtime.webExtensionController
            .ensureBuiltIn("resource://android/assets/reellock/", "reellock@focusgram.app")
            .accept(
                { Log.d(TAG, "✓ Reel lock WebExtension installed") },
                { e -> Log.e(TAG, "Reel lock WebExtension installation failed", e) }
            )

        runtime.webExtensionController
            .ensureBuiltIn("resource://android/assets/reelhistory/", "reelhistory@focusgram.app")
            .accept(
                { ext ->
                    ext?.setMessageDelegate(com.ujwal.focusgram.feature.history.ReelMessageDelegate(reelHistory), "reelhistory")
                    Log.d(TAG, "✓ Reel history WebExtension installed and delegate registered")
                },
                { e -> Log.e(TAG, "Reel history WebExtension installation failed", e) }
            )
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
