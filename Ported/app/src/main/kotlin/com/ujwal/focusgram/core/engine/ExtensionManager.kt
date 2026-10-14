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

    private val _isContentBlockerActive = MutableStateFlow(false)
    val isContentBlockerActive: StateFlow<Boolean> = _isContentBlockerActive.asStateFlow()

    private var contentBlockerExtension: WebExtension? = null
    private var activePort: WebExtension.Port? = null
    private var cachedSettings: FocusSettings = FocusSettings()

    var onOpenSettings: (() -> Unit)? = null
    var onInputFocused: ((Boolean) -> Unit)? = null
    var onUrlChanged: ((String) -> Unit)? = null
    var onModalOrChatActive: ((Boolean) -> Unit)? = null
    var onThemeChanged: ((String) -> Unit)? = null
    var onReelViewed: ((url: String, title: String, author: String, thumbnail: String) -> Unit)? = null
    var onReelMeta: ((url: String, title: String, author: String) -> Unit)? = null
    var onDownloadRequested: ((url: String, filename: String) -> Unit)? = null

    /**
     * Installs and activates built-in content blocking and FocusGram controller from APK assets.
     */
    suspend fun initializeContentBlocker(): Result<WebExtension> {
        contentBlockerExtension?.let { return Result.success(it) }
        return try {
            Log.i(TAG, "Mounting built-in Content Blocker WebExtension from assets...")
            val extension = runtime.webExtensionController
                .ensureBuiltIn(CONTENT_BLOCKER_ASSET_URI, CONTENT_BLOCKER_EXTENSION_ID)
                .await()

            if (extension != null) {
                try {
                    runtime.webExtensionController
                        .enable(extension, org.mozilla.geckoview.WebExtensionController.EnableSource.USER)
                        .await()
                } catch (_: Exception) {}
                contentBlockerExtension = extension
                _isContentBlockerActive.value = true
                setupMessageDelegate(extension)
                Log.i(TAG, "✓ Content Blocker successfully initialized and active! (ID: ${extension.id})")
                Result.success(extension)
            } else {
                val errorMsg = "WebExtensionController returned null extension instance"
                Log.e(TAG, errorMsg)
                _isContentBlockerActive.value = false
                Result.failure(IllegalStateException(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mount Content Blocker WebExtension: ${e.message}", e)
            _isContentBlockerActive.value = false
            Result.failure(e)
        }
    }

    suspend fun initializeUBlockOrigin(): Result<WebExtension> = initializeContentBlocker()

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
                // Immediately synchronize cached settings and reveal mode
                sendSettingsToPort(port, cachedSettings)
                try {
                    port.postMessage(JSONObject().put("revealMode", cachedSettings.unblurMode))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send initial revealMode to port: ${e.message}", e)
                }
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
            "themeChanged" -> {
                val theme = json.optString("theme", "dark")
                Log.d(TAG, "Received themeChanged from Instagram web: $theme")
                mainHandler.post { onThemeChanged?.invoke(theme) }
            }
            "reelViewed", "reelWatched" -> {
                val reelObj = json.optJSONObject("reel")
                var code = json.optString("code", "").trim()
                if (code.isBlank() && reelObj != null) {
                    code = reelObj.optString("code", "").trim()
                }
                var url = reelObj?.optString("url")?.takeIf { it.isNotBlank() } ?: json.optString("url", "")
                val rawCaption = reelObj?.optString("caption")?.takeIf { it.isNotBlank() } ?: json.optString("caption", "")
                val rawTitle = reelObj?.optString("title")?.takeIf { it.isNotBlank() } ?: json.optString("title", "")
                val title = when {
                    rawCaption.isNotBlank() -> rawCaption
                    rawTitle.isNotBlank() && !rawTitle.equals("Instagram Reel", ignoreCase = true) -> rawTitle
                    else -> ""
                }
                val rawAuthor = reelObj?.optString("author")?.takeIf { it.isNotBlank() } ?: json.optString("author", "")
                val author = if (rawAuthor.equals("Instagram Creator", ignoreCase = true)) "" else rawAuthor
                val thumbnail = reelObj?.optString("thumbnailUrl")?.takeIf { it.isNotBlank() } ?: json.optString("thumbnailUrl", "")

                if (url.isBlank() && code.isNotBlank()) {
                    url = if (code.startsWith("reel_")) "https://www.instagram.com/${code.removePrefix("reel_")}/" else "https://www.instagram.com/reel/$code/"
                }

                if (url.isNotBlank()) {
                    val isMeta = json.optString("type") == "meta"
                    Log.d(TAG, "Received reelWatched: code=$code, url=$url, author=$author, title=$title, isMeta=$isMeta")
                    mainHandler.post {
                        if (isMeta) {
                            onReelMeta?.invoke(url, title, author)
                        } else {
                            onReelViewed?.invoke(url, title, author, thumbnail)
                        }
                    }
                }
            }
            "downloadMedia" -> {
                val mediaUrl = json.optString("url")
                val filename = json.optString("filename")
                Log.d(TAG, "Received downloadMedia from web: url=$mediaUrl, filename=$filename")
                mainHandler.post {
                    onDownloadRequested?.invoke(mediaUrl, filename)
                }
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
                put("hideFeedPosts", settings.hideFeedPosts)
                put("blurExplore", settings.blurExplore)
                put("blurReels", settings.blurReels)
                put("tapToUnblur", settings.tapToUnblur)
                put("requireLongPress", settings.requireLongPress)
                put("unblurMode", settings.unblurMode)
                put("hideLikeCounts", settings.hideLikeCounts)
                put("hideFollowerCounts", settings.hideFollowerCounts)
                put("hideSuggestedPosts", settings.hideSuggestedPosts)
                put("hideShopTab", settings.hideShopTab)
                put("noAutoplay", settings.noAutoplay)
                put("grayscale", settings.grayscaleEnabled)
                put("minimalMode", settings.minimalModeEnabled)
                put("videoDownloadEnabled", settings.videoDownloadEnabled)
                put("ghostModeEnabled", settings.ghostModeEnabled)
                put("ghostHideStorySeen", settings.ghostHideStorySeen)
                put("ghostHideDirectSeen", settings.ghostHideDirectSeen)
                put("ghostHideTypingIndicator", settings.ghostHideTypingIndicator)
            }

            val msg = JSONObject().apply {
                put("action", "updateSettings")
                put("settings", settingsJson)
                put("revealMode", settings.unblurMode)
            }
            port.postMessage(msg)
            Log.d(TAG, "Broadcast settings to WebExtension: noAds=${settings.noAds}, ghost=${settings.ghostModeEnabled}, revealMode=${settings.unblurMode}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send settings to WebExtension port", e)
        }
    }

    /**
     * Senior Blueprint: Sets feed post reveal mode ("tap" or "hold") dynamically.
     */
    fun setRevealMode(mode: String) {
        try {
            val msg = JSONObject().apply {
                put("action", "revealMode")
                put("revealMode", mode)
            }
            activePort?.postMessage(msg)
            Log.d(TAG, "Sent revealMode('$mode') to WebExtension port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send revealMode to port: ${e.message}", e)
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
     * Triggers post/story creation or camera dialog in Instagram Web.
     */
    fun triggerCreate() {
        try {
            val msg = JSONObject().apply {
                put("action", "triggerCreate")
            }
            activePort?.postMessage(msg)
            Log.d(TAG, "Sent triggerCreate to WebExtension port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send triggerCreate to port: ${e.message}", e)
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

    suspend fun setContentBlockerEnabled(enabled: Boolean) {
        _isContentBlockerActive.value = enabled
        cachedSettings = cachedSettings.copy(noAds = enabled)
        broadcastSettings(cachedSettings)
        Log.i(TAG, "Content ad-blocker dynamic state updated: noAds=$enabled")
    }

    suspend fun setUBlockEnabled(enabled: Boolean) = setContentBlockerEnabled(enabled)

    /**
     * Sends request to in-page content script to extract and report active Reel video URL for downloading.
     */
    fun triggerReelDownload() {
        try {
            val msg = JSONObject().apply {
                put("action", "requestReelDownload")
            }
            activePort?.postMessage(msg)
            Log.d(TAG, "Sent requestReelDownload to WebExtension port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send requestReelDownload to port: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "ExtensionManager"
        const val CONTENT_BLOCKER_EXTENSION_ID = "uBlock0@raymondhill.net"
        const val CONTENT_BLOCKER_ASSET_URI = "resource://android/assets/extensions/ublock/"
        const val UBLOCK_EXTENSION_ID = CONTENT_BLOCKER_EXTENSION_ID
        const val UBLOCK_ASSET_URI = CONTENT_BLOCKER_ASSET_URI
    }
}
