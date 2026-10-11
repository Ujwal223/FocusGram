package com.ujwal.focusgram.core.engine

import android.util.Log
import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.core.model.IslandMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

/**
 * ExtensionManager
 * Manages the lifecycle, installation, and two-way messaging of built-in WebExtensions in GeckoView.
 * Provides real-time synchronization between Android Settings and the in-page Content Script,
 * handling SPA navigation, logo actions, typing focus, ad-blocking toggling, and Ghost Mode.
 */
class ExtensionManager(private val runtime: GeckoRuntime) {

    private val _isUBlockActive = MutableStateFlow(false)
    val isUBlockActive: StateFlow<Boolean> = _isUBlockActive.asStateFlow()

    private var uBlockExtension: WebExtension? = null
    private var activePort: WebExtension.Port? = null
    private var cachedSettings: FocusSettings = FocusSettings()

    var onOpenSettings: (() -> Unit)? = null
    var onInputFocused: ((Boolean) -> Unit)? = null
    var onUrlChanged: ((String) -> Unit)? = null
    var onModalOrChatActive: ((Boolean) -> Unit)? = null

    /**
     * Installs and activates uBlock Origin and FocusGram controller from APK assets.
     */
    suspend fun initializeUBlockOrigin(): Result<WebExtension> {
        uBlockExtension?.let { return Result.success(it) }
        return try {
            Log.i(TAG, "Mounting built-in uBlock Origin WebExtension from assets...")
            val extension = runtime.webExtensionController
                .ensureBuiltIn(UBLOCK_ASSET_URI, UBLOCK_EXTENSION_ID)
                .await()

            if (extension != null) {
                uBlockExtension = extension
                _isUBlockActive.value = true
                setupMessageDelegate(extension)
                Log.i(TAG, "✓ uBlock Origin successfully initialized and active! (ID: ${extension.id})")
                Result.success(extension)
            } else {
                val errorMsg = "WebExtensionController returned null extension instance"
                Log.e(TAG, errorMsg)
                _isUBlockActive.value = false
                Result.failure(IllegalStateException(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mount uBlock Origin WebExtension: ${e.message}", e)
            _isUBlockActive.value = false
            Result.failure(e)
        }
    }

    private fun setupMessageDelegate(extension: WebExtension) {
        extension.setMessageDelegate(object : WebExtension.MessageDelegate {
            override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
                Log.d(TAG, "onMessage from $nativeApp: $message")
                handleIncomingMessage(message)
                val response = JSONObject().put("status", "ok")
                return GeckoResult.fromValue(response)
            }

            override fun onConnect(port: WebExtension.Port) {
                Log.i(TAG, "WebExtension native port connected: ${port.name}")
                activePort = port
                port.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, p: WebExtension.Port) {
                        Log.d(TAG, "onPortMessage: $message")
                        handleIncomingMessage(message)
                    }

                    override fun onDisconnect(p: WebExtension.Port) {
                        Log.i(TAG, "WebExtension port disconnected")
                        if (activePort == p) {
                            activePort = null
                        }
                    }
                })
                // Immediately synchronize cached settings
                sendSettingsToPort(port, cachedSettings)
            }
        }, "focusgram")
    }

    private fun handleIncomingMessage(message: Any) {
        val json = when (message) {
            is JSONObject -> message
            is String -> try { JSONObject(message) } catch (_: Exception) { null }
            else -> null
        } ?: return

        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        when (json.optString("action")) {
            "openSettings" -> {
                Log.i(TAG, "Received openSettings request from Instagram web")
                mainHandler.post { onOpenSettings?.invoke() }
            }
            "inputFocused" -> {
                val focused = json.optBoolean("focused", false)
                Log.d(TAG, "Received inputFocused from Instagram web: $focused")
                mainHandler.post { onInputFocused?.invoke(focused) }
            }
            "urlChanged" -> {
                val url = json.optString("url")
                if (url.isNotEmpty()) {
                    Log.d(TAG, "Received urlChanged from Instagram web: $url")
                    mainHandler.post { onUrlChanged?.invoke(url) }
                }
            }
            "modalOrChatActive" -> {
                val active = json.optBoolean("active", false)
                Log.d(TAG, "Received modalOrChatActive from Instagram web: $active")
                mainHandler.post { onModalOrChatActive?.invoke(active) }
            }
            "getSettings" -> {
                activePort?.let { sendSettingsToPort(it, cachedSettings) }
            }
        }
    }

    /**
     * Broadcasts updated FocusSettings to the WebExtension background & content scripts.
     */
    fun broadcastSettings(settings: FocusSettings) {
        cachedSettings = settings
        activePort?.let { sendSettingsToPort(it, settings) }
    }

    private fun sendSettingsToPort(port: WebExtension.Port, settings: FocusSettings) {
        try {
            val settingsJson = JSONObject().apply {
                put("noAds", settings.noAds)
                put("noStories", settings.noStories)
                put("noReels", settings.noReels)
                put("blurExplore", settings.blurExplore)
                put("hideLikeCounts", settings.hideLikeCounts)
                put("hideFollowerCounts", settings.hideFollowerCounts)
                put("hideSuggestedPosts", settings.hideSuggestedPosts)
                put("hideShopTab", settings.hideShopTab)
                put("noAutoplay", settings.noAutoplay)
                put("grayscale", settings.grayscaleEnabled)
                put("ghostModeEnabled", settings.ghostModeEnabled)
                put("ghostHideStorySeen", settings.ghostHideStorySeen)
                put("ghostHideDirectSeen", settings.ghostHideDirectSeen)
                put("ghostHideTypingIndicator", settings.ghostHideTypingIndicator)
            }

            val msg = JSONObject().apply {
                put("action", "updateSettings")
                put("settings", settingsJson)
            }
            port.postMessage(msg)
            Log.d(TAG, "Broadcast settings to WebExtension: noAds=${settings.noAds}, ghost=${settings.ghostModeEnabled}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send settings to WebExtension port", e)
        }
    }

    /**
     * Executes instant client-side SPA navigation inside Instagram web without full reload.
     */
    fun navigateSpa(path: String) {
        try {
            val msg = JSONObject().apply {
                put("action", "navigateSpa")
                put("path", path)
            }
            activePort?.postMessage(msg)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send navigateSpa to port", e)
        }
    }

    /**
     * Opens the authenticated user's profile inside Instagram web.
     */
    fun openProfileSpa() {
        go("profile")
    }

    /**
     * Senior Blueprint: Triggers client-side SPA navigation in Instagram
     * by clicking the real hidden navigation anchor.
     * @param target "home", "search", "reels", "dm", "profile"
     */
    fun go(target: String) {
        try {
            val msg = JSONObject().apply {
                put("action", "nav")
                put("nav", target)
            }
            activePort?.postMessage(msg)
            Log.d(TAG, "Sent go('$target') to WebExtension port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send go to port: ${e.message}", e)
        }
    }

    /**
     * Senior Blueprint: Synchronizes IslandMode (NORMAL, FLOATING, HIDDEN)
     * with the WebExtension content script to dynamically update body padding.
     */
    fun setIslandMode(mode: IslandMode) {
        try {
            val msg = JSONObject().apply {
                put("action", "setIslandMode")
                put("island", mode.name)
            }
            activePort?.postMessage(msg)
            Log.d(TAG, "Sent setIslandMode(${mode.name}) to WebExtension port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send setIslandMode to port: ${e.message}", e)
        }
    }

    suspend fun setUBlockEnabled(enabled: Boolean) {
        val extension = uBlockExtension ?: return
        try {
            if (enabled) {
                runtime.webExtensionController
                    .enable(extension, org.mozilla.geckoview.WebExtensionController.EnableSource.USER)
                    .await()
                _isUBlockActive.value = true
                Log.i(TAG, "uBlock Origin enabled")
            } else {
                runtime.webExtensionController
                    .disable(extension, org.mozilla.geckoview.WebExtensionController.EnableSource.USER)
                    .await()
                _isUBlockActive.value = false
                Log.i(TAG, "uBlock Origin disabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle uBlock Origin state: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "ExtensionManager"
        const val UBLOCK_EXTENSION_ID = "uBlock0@raymondhill.net"
        const val UBLOCK_ASSET_URI = "resource://android/assets/extensions/ublock/"
    }
}
