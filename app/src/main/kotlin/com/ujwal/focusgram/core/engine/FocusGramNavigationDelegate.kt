package com.ujwal.focusgram.core.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebRequestError

/**
 * FocusGramNavigationDelegate
 * Enforces Instagram domain containment, intercepts external link escapes,
 * and maintains navigation history state.
 */
class FocusGramNavigationDelegate(
    private val context: Context,
    private val sessionManager: GeckoSessionManager,
    private val onRouteChanged: ((String) -> Unit)? = null
) : GeckoSession.NavigationDelegate {

    override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest
    ): GeckoResult<AllowOrDeny> {
        val uri = Uri.parse(request.uri)
        val host = uri.host?.lowercase() ?: ""
        val scheme = uri.scheme?.lowercase() ?: ""

        // Internal scheme evaluation
        if (scheme == "resource" || scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript") {
            return GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }

        // Allow internal local app-private files only if strictly within app filesDir (prevent traversal)
        if (scheme == "file") {
            try {
                val appFilesPath = context.filesDir.canonicalPath
                val targetFile = java.io.File(uri.path ?: "").canonicalFile
                if (targetFile.toPath().startsWith(java.io.File(appFilesPath).toPath())) {
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }
            } catch (_: Exception) {}
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }

        // Block attempts to hand off navigation to the official native Instagram app
        if (scheme == "instagram") {
            Log.d(TAG, "Prevented native Instagram app deep link: ${request.uri}")
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }

        // Custom scheme used by in-page downloader scripts to pass media URLs to native storage
        if (scheme == "focusgram-download") {
            val mediaUrl = uri.getQueryParameter("url") ?: ""
            val filename = uri.getQueryParameter("filename") ?: ""
            val error = uri.getQueryParameter("error") ?: ""
            Log.d(TAG, "Intercepted focusgram-download: url=$mediaUrl, filename=$filename, error=$error")
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mainHandler.post {
                if (mediaUrl.isNotBlank()) {
                    val parsed = try { Uri.parse(mediaUrl) } catch (_: Exception) { null }
                    val s = parsed?.scheme?.lowercase() ?: ""
                    if (s == "http" || s == "https" || mediaUrl.startsWith("data:")) {
                        com.ujwal.focusgram.core.utils.MediaDownloadHelper.download(context, mediaUrl, filename)
                    } else {
                        Log.w(TAG, "Rejected focusgram-download with unsafe URL scheme: $s")
                    }
                } else if (error.isNotBlank()) {
                    android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }

        // Custom scheme used by in-page scripts to write copied links/text directly to Android clipboard
        if (scheme == "focusgram-clipboard") {
            val text = uri.getQueryParameter("text") ?: ""
            if (text.isNotBlank()) {
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                mainHandler.post {
                    try {
                        val settings = sessionManager.currentSettings
                        val finalText = if (settings.sanitizeLinks) {
                            com.ujwal.focusgram.core.utils.NetworkUtils.sanitizeText(text)
                        } else {
                            text
                        }
                        if (com.ujwal.focusgram.core.utils.NetworkUtils.isBareInstagramRootUrl(finalText)) {
                            Log.w(TAG, "Suppressed bare Instagram root URL clipboard write: $finalText")
                            return@post
                        }
                        if (!com.ujwal.focusgram.core.utils.ClipboardDebouncer.shouldCopy(finalText)) {
                            return@post
                        }
                        val isSingleUrl = (finalText.startsWith("http://") || finalText.startsWith("https://")) && !finalText.contains("\n") && !finalText.contains(" ")
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText(if (isSingleUrl) "Instagram" else "Text", finalText)
                        clipboard.setPrimaryClip(clip)
                        val toastMsg = if (isSingleUrl) "Link copied to clipboard" else "Copied to clipboard"
                        android.widget.Toast.makeText(context, toastMsg, android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed writing focusgram-clipboard text: ${e.message}")
                    }
                }
            }
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }

        // Instagram traps users into /explore/people when Favorites feed is empty; force back home
        val path = uri.path?.lowercase() ?: ""
        if (path.contains("/explore/people")) {
            Log.i(TAG, "Intercepted suggested accounts trap (/explore/people). Redirecting to home feed.")
            session.loadUri("https://www.instagram.com/")
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }

        // Enforce URL blocking when feature toggles (Block DMs, Block Reels, Hide Stories) are active
        val settings = sessionManager.currentSettings
        if (sessionManager.isAuthenticated && (host == "instagram.com" || host == "www.instagram.com")) {
            val blockedMessage = when {
                settings.noDMs && (path.startsWith("/direct") || path.startsWith("/messages")) -> "Direct Messages are blocked"
                settings.noReels && (path.startsWith("/reel/") || path.startsWith("/reels") || path == "/reel") -> "Reels are blocked"
                settings.noStories && (path.startsWith("/stories/") || path == "/stories") -> "Stories are blocked"
                else -> null
            }
            if (blockedMessage != null) {
                Log.i(TAG, "Feature blocked path: $path ($blockedMessage). Redirecting to home feed.")
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                mainHandler.post {
                    android.widget.Toast.makeText(context, blockedMessage, android.widget.Toast.LENGTH_SHORT).show()
                }
                session.loadUri("https://www.instagram.com/")
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }
        }

        // Minimal mode restrains usage exclusively to Direct Messages when authenticated
        if (sessionManager.isAuthenticated && sessionManager.currentSettings.minimalModeEnabled && (host == "instagram.com" || host == "www.instagram.com")) {
            if (path == "/" || path == "" || path.startsWith("/explore") || path.startsWith("/reels") || path.startsWith("/reel") || path.startsWith("/p/")) {
                Log.i(TAG, "Minimal Mode active. Redirecting restricted path $path to /direct/inbox/")
                session.loadUri("https://www.instagram.com/direct/inbox/")
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }
        }

        // Whitelisted domain tree (Instagram, CDN, Meta Account Center)
        if (isAllowedHost(host)) {
            return GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }

        // Launch external browser only for intentional user taps, suppressing background redirects/telemetry
        val isExplicitUserNavigation = request.hasUserGesture || request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW
        if (isExplicitUserNavigation && (scheme == "http" || scheme == "https")) {
            Log.i(TAG, "User clicked external link: ${request.uri}. Launching external browser intent.")
            handleExternalIntent(uri)
        } else {
            Log.d(TAG, "Silently blocking unauthorized background request: ${request.uri}")
        }

        return GeckoResult.fromValue(AllowOrDeny.DENY)
    }

    override fun onLocationChange(
        session: GeckoSession,
        url: String?,
        permissions: List<GeckoSession.PermissionDelegate.ContentPermission>,
        hasUserGesture: Boolean
    ) {
        url?.let {
            if (it.startsWith("focusgram-download:") || it.startsWith("focusgram-clipboard:")) return
            if (it.lowercase().contains("/explore/people")) {
                Log.i(TAG, "Location redirected away from /explore/people to home")
                sessionManager.loadUrl("https://www.instagram.com/")
                return
            }
            val locUri = try { Uri.parse(it) } catch (_: Exception) { null }
            val locHost = locUri?.host?.lowercase() ?: ""
            val locPath = locUri?.path?.lowercase() ?: ""
            if (sessionManager.isAuthenticated && (locHost == "instagram.com" || locHost == "www.instagram.com")) {
                val blockedMessage = when {
                    sessionManager.currentSettings.noDMs && (locPath.startsWith("/direct") || locPath.startsWith("/messages")) -> "Direct Messages are blocked"
                    sessionManager.currentSettings.noReels && (locPath.startsWith("/reel/") || locPath.startsWith("/reels") || locPath == "/reel") -> "Reels are blocked"
                    sessionManager.currentSettings.noStories && (locPath.startsWith("/stories/") || locPath == "/stories") -> "Stories are blocked"
                    else -> null
                }
                if (blockedMessage != null) {
                    Log.i(TAG, "Location change blocked by feature: $locPath ($blockedMessage). Redirecting home.")
                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    mainHandler.post {
                        android.widget.Toast.makeText(context, blockedMessage, android.widget.Toast.LENGTH_SHORT).show()
                    }
                    sessionManager.loadUrl("https://www.instagram.com/")
                    return
                }
            }
            if (sessionManager.currentSettings.minimalModeEnabled) {
                val locUri = Uri.parse(it)
                val locHost = locUri.host?.lowercase() ?: ""
                val locPath = locUri.path?.lowercase() ?: ""
                if ((locHost == "instagram.com" || locHost == "www.instagram.com") &&
                    (locPath == "/" || locPath == "" || locPath.startsWith("/explore") || locPath.startsWith("/reels") || locPath.startsWith("/reel") || locPath.startsWith("/p/"))
                ) {
                    Log.i(TAG, "Minimal Mode location redirected away from $locPath to /direct/inbox/")
                    sessionManager.loadUrl("https://www.instagram.com/direct/inbox/")
                    return
                }
            }
            Log.d(TAG, "Location changed to: $it")
            val lower = it.lowercase()
            if (lower.contains("/direct/t/")) {
                val path = try { Uri.parse(it).path?.lowercase() ?: "" } catch (_: Exception) { "" }
                val tid = path.removePrefix("/direct/t/").trim('/').split('/').firstOrNull()?.trim() ?: ""
                val settings = sessionManager.currentSettings
                val isLocked = if (settings.chatLockMode == "off") {
                    false
                } else if (settings.chatLockMode == "whole_dm" || settings.chatLockAll) {
                    true
                } else {
                    settings.lockedChatIds.any { lockedId ->
                        val clean = lockedId.trim().lowercase().removePrefix("@")
                        clean.equals(tid, ignoreCase = true) || lower.contains(clean)
                    }
                }
                if (isLocked) {
                    session.loadUri("javascript:(function(){if(typeof window.__fgApplyPrivacyVeil==='function'){window.__fgApplyPrivacyVeil();}})()")
                }
            }
            sessionManager.updateCurrentUrl(it)
            onRouteChanged?.invoke(it)
            if (sessionManager.isAuthenticated && !GeckoSessionManager.isAuthUrl(it)) {
                sessionManager.injectBottomBarTrimmer()
            }
        }
    }

    override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
        sessionManager.updateNavigationState(canBack = canGoBack, canForward = false)
    }

    override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {}

    override fun onLoadError(
        session: GeckoSession,
        uri: String?,
        error: WebRequestError
    ): GeckoResult<String>? {
        Log.w(TAG, "Navigation error: uri=$uri, category=${error.category}, code=${error.code}")
        val lowerUri = uri?.lowercase() ?: ""
        val isNonNetworkScheme = lowerUri.startsWith("focusgram-download:") ||
                lowerUri.startsWith("focusgram-clipboard:") ||
                lowerUri.startsWith("about:") ||
                lowerUri.startsWith("javascript:") ||
                lowerUri.startsWith("blob:") ||
                lowerUri.startsWith("data:")

        if (!isNonNetworkScheme) {
            val isOffline = !com.ujwal.focusgram.core.utils.NetworkUtils.isNetworkAvailable(context)
            if (isOffline) {
                sessionManager.setNetworkError(true)
            }
        }
        return GeckoResult.fromValue(null)
    }

    private fun isAllowedHost(host: String): Boolean {
        return ALLOWED_HOSTS.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    private fun handleExternalIntent(uri: Uri) {
        try {
            val targetUri = if (sessionManager.currentSettings.sanitizeLinks) {
                com.ujwal.focusgram.core.utils.NetworkUtils.sanitizeUri(uri)
            } else {
                uri
            }
            val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "No activity found to handle external URI: $uri", e)
        }
    }

    companion object {
        private const val TAG = "NavigationDelegate"

        private val ALLOWED_HOSTS = listOf(
            "instagram.com",
            "instagr.am",
            "cdninstagram.com",
            "fbcdn.net",
            // Meta Account Center & OAuth domains necessary for Instagram login
            "facebook.com",
            "m.facebook.com",
            "accountscenter.instagram.com",
            "accountscenter.facebook.com",
            "threads.net",
            "meta.com",
            "facebook.net",
            "connect.facebook.net"
        )
    }
}
