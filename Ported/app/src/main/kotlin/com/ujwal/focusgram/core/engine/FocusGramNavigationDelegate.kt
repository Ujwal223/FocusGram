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

        // 3. Allow Instagram, Meta CDN, and Authentication login domains
        if (isAllowedHost(host)) {
            return GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }

        // 4. For external links:
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
            Log.d(TAG, "Location changed to: $it")
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
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
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
