package com.ujwal.focusgram.core.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

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

        // 1. Allow internal asset protocols, data URIs, and javascript evaluation
        if (scheme == "resource" || scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript") {
            return GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }

        // 2. Prevent deep-linking into the official native Instagram app
        if (scheme == "instagram") {
            Log.d(TAG, "Prevented native Instagram app deep link: ${request.uri}")
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }

        // 2b. Intercept native media download requests from in-page client scripts
        if (scheme == "focusgram-download") {
            val mediaUrl = uri.getQueryParameter("url") ?: ""
            val filename = uri.getQueryParameter("filename") ?: ""
            val error = uri.getQueryParameter("error") ?: ""
            Log.d(TAG, "Intercepted focusgram-download: url=$mediaUrl, filename=$filename, error=$error")
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mainHandler.post {
                if (mediaUrl.isNotBlank()) {
                    com.ujwal.focusgram.core.utils.MediaDownloadHelper.download(context, mediaUrl, filename)
                } else if (error.isNotBlank()) {
                    android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }

        // 3. Intercept navigation trap to suggested accounts (/explore/people) from Favorites
        val path = uri.path?.lowercase() ?: ""
        if (path.contains("/explore/people")) {
            Log.i(TAG, "Intercepted suggested accounts trap (/explore/people). Redirecting to home feed.")
            session.loadUri("https://www.instagram.com/")
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }

        // 3b. Minimal Mode enforcement: redirect home feed, explore, reels, and individual posts to DMs inbox
        if (sessionManager.currentSettings.minimalModeEnabled && (host == "instagram.com" || host == "www.instagram.com")) {
            if (path == "/" || path == "" || path.startsWith("/explore") || path.startsWith("/reels") || path.startsWith("/reel") || path.startsWith("/p/")) {
                Log.i(TAG, "Minimal Mode active. Redirecting restricted path $path to /direct/inbox/")
                session.loadUri("https://www.instagram.com/direct/inbox/")
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }
        }

        // 4. Allow Instagram, Meta CDN, and Authentication login domains
        if (isAllowedHost(host)) {
            return GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }

        // 5. For external links:
        // ONLY launch external system browser if the navigation was triggered by an explicit user gesture
        // (tapping an external link in bio/story) or opening a new tab (target="_blank").
        // NEVER launch external browser for background pings, telemetry, iframes, or non-user requests!
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
            if (it.startsWith("focusgram-download:")) return
            if (it.lowercase().contains("/explore/people")) {
                Log.i(TAG, "Location redirected away from /explore/people to home")
                sessionManager.loadUrl("https://www.instagram.com/")
                return
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
            sessionManager.injectBottomBarTrimmer()
        }
    }

    override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
        sessionManager.updateNavigationState(canBack = canGoBack, canForward = false)
    }

    override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
        // Will be called when forward navigation changes
    }

    private fun isAllowedHost(host: String): Boolean {
        return ALLOWED_HOSTS.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    private fun handleExternalIntent(uri: Uri) {
        try {
            val targetUri = if (sessionManager.currentSettings.sanitizeLinks) {
                sanitizeUri(uri)
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

    private fun sanitizeUri(uri: Uri): Uri {
        if (!uri.isHierarchical) return uri
        val trackingParams = setOf(
            "igsh", "igshid", "fbclid",
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "source_ve_path"
        )
        val queryNames = uri.queryParameterNames
        if (queryNames.none { it.lowercase() in trackingParams }) return uri

        val builder = uri.buildUpon().clearQuery()
        for (param in queryNames) {
            if (param.lowercase() !in trackingParams) {
                for (value in uri.getQueryParameters(param)) {
                    builder.appendQueryParameter(param, value)
                }
            }
        }
        return builder.build()
    }

    companion object {
        private const val TAG = "NavigationDelegate"

        private val ALLOWED_HOSTS = listOf(
            "instagram.com",
            "instagr.am",
            "cdninstagram.com",
            "fbcdn.net",
            // Required for Facebook Authentication & Account Center logins
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
