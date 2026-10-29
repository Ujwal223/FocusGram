package com.ujwal.focusgram.core.engine

import android.util.Log
import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.core.model.IslandMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

/**
 * Bridges between native Android settings and the bundled uBlock Origin / FocusGram
 * content script over GeckoView WebExtension messaging ports.
 */
class ExtensionManager(private val runtime: GeckoRuntime) {

    private val _isContentBlockerActive = MutableStateFlow(false)
    val isContentBlockerActive: StateFlow<Boolean> = _isContentBlockerActive.asStateFlow()

    private var contentBlockerExtension: WebExtension? = null
    private var activePort: WebExtension.Port? = null
    private var cachedSettings: FocusSettings = FocusSettings()

    var onOpenSettings: (() -> Unit)? = null
    var onInputFocused: ((Boolean) -> Unit)? = null
    var onBioInputFocused: ((Boolean) -> Unit)? = null
    var onUrlChanged: ((String) -> Unit)? = null
    var onModalOrChatActive: ((Boolean) -> Unit)? = null
    var onThemeChanged: ((String) -> Unit)? = null
    var onReelViewed: ((url: String, title: String, author: String, thumbnail: String) -> Unit)? = null
    var onReelMeta: ((url: String, title: String, author: String) -> Unit)? = null
    var onDownloadRequested: ((url: String, filename: String) -> Unit)? = null
    var onDownloadError: ((error: String) -> Unit)? = null
    var onDownloadStarted: ((filename: String) -> Unit)? = null
    var onAuthStateChanged: ((isAuthenticated: Boolean, isAuthPage: Boolean) -> Unit)? = null
    var onThreadUsernameMapped: ((threadId: String, usernames: List<String>) -> Unit)? = null
    var onToggleChatLock: ((threadId: String, username: String?) -> Unit)? = null
    var onRequestUnlockChat: ((threadId: String, username: String?, targetUrl: String?) -> Unit)? = null

    /**
     * Installs and activates built-in content blocking and FocusGram controller from APK assets.
     */
    suspend fun initializeContentBlocker(): Result<WebExtension> {
        contentBlockerExtension?.let { return Result.success(it) }
        return try {
            Log.i(TAG, "Mounting built-in Content Blocker WebExtension from assets...")
            val extension = try {
                runtime.webExtensionController
                    .installBuiltIn(CONTENT_BLOCKER_ASSET_URI)
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "installBuiltIn fallback to ensureBuiltIn: ${e.message}")
                runtime.webExtensionController
                    .ensureBuiltIn(CONTENT_BLOCKER_ASSET_URI, CONTENT_BLOCKER_EXTENSION_ID)
                    .await()
            }

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
            "bioInputFocused" -> {
                val focused = json.optBoolean("focused", false)
                Log.d(TAG, "Received bioInputFocused from Instagram web: $focused")
                mainHandler.post { onBioInputFocused?.invoke(focused) }
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
            "downloadStarted" -> {
                val filename = json.optString("filename")
                Log.d(TAG, "Received downloadStarted from web: filename=$filename")
                mainHandler.post {
                    onDownloadStarted?.invoke(filename)
                }
            }
            "downloadMedia" -> {
                val mediaUrl = json.optString("url")
                val filename = json.optString("filename")
                val error = json.optString("error")
                Log.d(TAG, "Received downloadMedia from web: url=$mediaUrl, filename=$filename, error=$error")
                mainHandler.post {
                    if (mediaUrl.isNotBlank()) {
                        onDownloadRequested?.invoke(mediaUrl, filename)
                    } else if (error.isNotBlank()) {
                        onDownloadError?.invoke(error)
                    }
                }
            }
            "authStateChanged" -> {
                val isAuthenticated = json.optBoolean("isAuthenticated", true)
                val isAuthPage = json.optBoolean("isAuthPage", false)
                Log.d(TAG, "Received authStateChanged from web: isAuthenticated=$isAuthenticated, isAuthPage=$isAuthPage")
                mainHandler.post {
                    onAuthStateChanged?.invoke(isAuthenticated, isAuthPage)
                }
            }
            "threadUsernameMapped" -> {
                val threadId = json.optString("threadId")
                val usernamesArr = json.optJSONArray("usernames")
                val singleUser = json.optString("username").takeIf { it.isNotBlank() }
                val displayName = json.optString("displayName").takeIf { it.isNotBlank() }
                val list = mutableListOf<String>()
                if (usernamesArr != null) {
                    for (i in 0 until usernamesArr.length()) {
                        val u = usernamesArr.optString(i)
                        if (u.isNotBlank()) list.add(u)
                    }
                }
                if (singleUser != null && !list.contains(singleUser)) {
                    list.add(0, singleUser)
                }
                if (displayName != null && !list.contains(displayName)) {
                    list.add(displayName)
                }
                if (threadId.isNotBlank()) {
                    Log.d(TAG, "Received threadUsernameMapped from web: threadId=$threadId, usernames=$list, displayName=$displayName")
                    mainHandler.post {
                        onThreadUsernameMapped?.invoke(threadId, list)
                    }
                }
            }
            "requestUnlockChat" -> {
                val threadId = json.optString("threadId")
                val username = json.optString("username").takeIf { it.isNotBlank() }
                val targetUrl = json.optString("targetUrl").takeIf { it.isNotBlank() }
                if (threadId.isNotBlank()) {
                    Log.d(TAG, "Received requestUnlockChat from web: threadId=$threadId, username=$username, targetUrl=$targetUrl")
                    mainHandler.post {
                        onRequestUnlockChat?.invoke(threadId, username, targetUrl)
                    }
                }
            }
            "toggleChatLock" -> {
                val threadId = json.optString("threadId")
                val username = json.optString("username").takeIf { it.isNotBlank() }
                if (threadId.isNotBlank()) {
                    Log.d(TAG, "Received toggleChatLock from web: threadId=$threadId, username=$username")
                    mainHandler.post {
                        onToggleChatLock?.invoke(threadId, username)
                    }
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
                put("portraitOnly", settings.portraitOnly)
                put("storySearchEnabled", settings.storySearchEnabled)
                put("customBioFontsEnabled", settings.customBioFontsEnabled)
                put("ghostModeEnabled", settings.ghostModeEnabled)
                put("ghostHideStorySeen", settings.ghostHideStorySeen)
                put("ghostHideDirectSeen", settings.ghostHideDirectSeen)
                put("ghostHideTypingIndicator", settings.ghostHideTypingIndicator)

                put("chatLockMode", settings.chatLockMode)
                put("chatLockAll", settings.chatLockAll)
                put("lockedChatIds", JSONArray(settings.lockedChatIds.toList()))
                put("lockedChatUsernames", JSONObject(settings.lockedChatUsernames))
            }

            val msg = JSONObject().apply {
                put("action", "updateSettings")
                put("settings", settingsJson)
                put("revealMode", settings.unblurMode)
            }
            port.postMessage(msg)
            Log.d(TAG, "Broadcast settings to WebExtension: noAds=${settings.noAds}, ghost=${settings.ghostModeEnabled}, chatLockAll=${settings.chatLockAll}, lockedChats=${settings.lockedChatIds.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send settings to WebExtension port", e)
        }
    }

    /**
     * Informs WebExtension content script that a locked chat has successfully been unlocked.
     */
    fun notifyChatUnlocked(threadId: String, isAll: Boolean = false) {
        try {
            val msg = JSONObject().apply {
                put("action", "unlockChat")
                put("threadId", threadId)
                put("all", isAll)
            }
            activePort?.postMessage(msg)
            Log.d(TAG, "Sent unlockChat to WebExtension port: threadId=$threadId, all=$isAll")
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending unlockChat to WebExtension", e)
        }
    }

    /**
     * Informs WebExtension content script that a conversation has been locked.
     */
    fun notifyChatLocked(threadId: String) {
        try {
            val msg = JSONObject().apply {
                put("action", "lockChat")
                put("threadId", threadId)
            }
            activePort?.postMessage(msg)
            Log.d(TAG, "Sent lockChat to WebExtension port: threadId=$threadId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending lockChat to WebExtension", e)
        }
    }

    /**
     * Updates feed post reveal mode ("tap" or "hold") in the running content script.
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
     * Directs the in-page content script to navigate via client-side History pushState.
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
     * Navigates to the current user's profile without reloading the page.
     */
    fun openProfileSpa() {
        go("profile")
    }

    /**
     * Simulates a user click on Instagram's internal navigation anchor.
     *
     * @param target One of "home", "search", "reels", "dm", "profile".
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
     * Signals navigation layout changes so the content script adjusts document body padding.
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
     * Requests the content script to extract and report the active Reel video source URL.
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

    /**
     * Sends the selected Unicode character style to the content script for in-place text styling.
     */
    fun setBioStyle(style: String) {
        try {
            val msg = JSONObject().apply {
                put("action", "setBioStyle")
                put("style", style)
            }
            activePort?.postMessage(msg)
            Log.d(TAG, "Sent setBioStyle ($style) to WebExtension port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send setBioStyle to port: ${e.message}", e)
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
