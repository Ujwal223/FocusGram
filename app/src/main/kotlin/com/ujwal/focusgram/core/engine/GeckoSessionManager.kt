package com.ujwal.focusgram.core.engine

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.core.model.IslandMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.BasicSelectionActionDelegate
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

internal fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Manages the primary [GeckoSession] lifecycle, foreground/background media states,
 * and DOM navigation bridge for Instagram web.
 */
class GeckoSessionManager(private val runtime: GeckoRuntime) {

    val session: GeckoSession
    var userScriptManager: UserScriptManager? = null
    var extensionManager: ExtensionManager? = null
    var currentSettings: FocusSettings = FocusSettings()
    var isModalActive: Boolean = false

    private var basicSelectionDelegate: BasicSelectionActionDelegate? = null

    fun attachActivity(activity: Activity?) {
        basicSelectionDelegate = if (activity != null) {
            BasicSelectionActionDelegate(activity)
        } else {
            null
        }
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _currentUrl = MutableStateFlow(DEFAULT_URL)
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _islandMode = MutableStateFlow(IslandMode.NORMAL)
    val islandMode: StateFlow<IslandMode> = _islandMode.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    private val _instagramTheme = MutableStateFlow("auto")
    val instagramTheme: StateFlow<String> = _instagramTheme.asStateFlow()

    private val _hasNetworkError = MutableStateFlow(false)
    val hasNetworkError: StateFlow<Boolean> = _hasNetworkError.asStateFlow()

    fun setNetworkError(hasError: Boolean) {
        _hasNetworkError.value = hasError
        if (hasError) {
            _isLoading.value = false
        }
    }

    private var isOpen = false
    var isAuthenticated: Boolean = true
        private set

    fun setAuthenticated(auth: Boolean) {
        val changed = isAuthenticated != auth
        isAuthenticated = auth
        if (auth && changed) {
            val url = _currentUrl.value.lowercase()
            val isAuth = isAuthUrl(url)
            if (!isAuth) {
                injectBottomBarTrimmer()
                extensionManager?.broadcastSettings(currentSettings)
                userScriptManager?.injectAll(session, currentSettings, isAuthenticated = true)
            }
        }
    }

    init {
        val settings = GeckoSessionSettings.Builder()
            .useTrackingProtection(false)
            .suspendMediaWhenInactive(true)
            .allowJavascript(true)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .userAgentOverride(IOS_USER_AGENT)
            // False keeps session cookies on disk; private mode clears them on process death
            .usePrivateMode(false)
            .build()

        session = GeckoSession(settings)
        session.selectionActionDelegate = object : GeckoSession.SelectionActionDelegate {
            override fun onShowActionRequest(
                session: GeckoSession,
                selection: GeckoSession.SelectionActionDelegate.Selection
            ) {
                val isEditable = (selection.flags and GeckoSession.SelectionActionDelegate.FLAG_IS_EDITABLE) != 0
                val hasText = !selection.text.isNullOrBlank()

                // Allow selection action mode (Copy/Share menu) when:
                // 1. Text is editable (input/textarea)
                // 2. A modal or dialog is active (e.g. post link copy modal)
                // 3. User selected text from allowed content surfaces (DMs, Bio, captions)
                if (isEditable || isModalActive || hasText) {
                    val delegate = basicSelectionDelegate
                    if (delegate != null) {
                        delegate.onShowActionRequest(session, selection)
                        return
                    }
                }

                // Block default browser select/copy menus on non-editable elements
                if ((selection.flags and GeckoSession.SelectionActionDelegate.FLAG_IS_EDITABLE) == 0) {
                    try {
                        selection.hide()
                    } catch (_: Exception) {}
                }
            }

            override fun onHideAction(session: GeckoSession, reason: Int) {
                basicSelectionDelegate?.onHideAction(session, reason)
            }

            override fun onShowClipboardPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.SelectionActionDelegate.ClipboardPermission
            ): org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.AllowOrDeny> {
                return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.ALLOW)
            }

            override fun onDismissClipboardPermissionRequest(session: GeckoSession) {
                basicSelectionDelegate?.onDismissClipboardPermissionRequest(session)
            }
        }
        setupProgressDelegate()
    }

    private fun setupProgressDelegate() {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                Log.d(TAG, "Page started loading")
                if (!url.startsWith("focusgram-download:") && !url.startsWith("focusgram-clipboard:") && !url.startsWith("javascript:")) {
                    _isLoading.value = true
                    _currentUrl.value = url
                    _progress.value = 10
                    _hasNetworkError.value = false
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                Log.d(TAG, "Page stopped loading. Success: $success")
                _isLoading.value = false
                _progress.value = 100
                if (success) {
                    _hasNetworkError.value = false
                    val url = _currentUrl.value.lowercase()
                    val isAuth = isAuthUrl(url)
                    if (!isAuth) {
                        injectBottomBarTrimmer()
                        extensionManager?.broadcastSettings(currentSettings)
                        userScriptManager?.injectAll(session, currentSettings, isAuthenticated = true)
                    }
                } else {
                    val url = _currentUrl.value.lowercase()
                    val isAppScheme = url.startsWith("focusgram-download:") || url.startsWith("focusgram-clipboard:") || url.startsWith("javascript:") || url.startsWith("about:")
                    if (!isAppScheme) {
                        val isOffline = !com.ujwal.focusgram.core.utils.NetworkUtils.isNetworkAvailable(com.ujwal.focusgram.FocusGramApp.instance)
                        if (isOffline) {
                            _hasNetworkError.value = true
                        }
                    }
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                _progress.value = progress
                if (progress >= 100) {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Attaches the session to the runtime without issuing an immediate network load.
     * Used when migration or onboarding must complete before loading the target URL.
     */
    fun openSessionWithoutLoading() {
        if (!isOpen) {
            Log.i(TAG, "Opening GeckoSession with runtime (standby mode)...")
            session.open(runtime)
            isOpen = true
            session.setActive(true)
        }
    }

    /**
     * Opens the session attached to the GeckoRuntime.
     */
    fun openSession(initialUrl: String = DEFAULT_URL, cookieHeader: String? = null) {
        if (!isOpen) {
            Log.i(TAG, "Opening GeckoSession with runtime...")
            session.open(runtime)
            isOpen = true
            session.setActive(true)
            if (!initialUrl.startsWith("about:") && !com.ujwal.focusgram.core.utils.NetworkUtils.isNetworkAvailable(com.ujwal.focusgram.FocusGramApp.instance)) {
                _hasNetworkError.value = true
            }
            if (!cookieHeader.isNullOrBlank()) {
                val loader = GeckoSession.Loader()
                    .uri(initialUrl)
                    .additionalHeaders(mapOf("Cookie" to cookieHeader))
                session.load(loader)
            } else {
                session.loadUri(initialUrl)
            }
        }
    }

    fun openAndLoad(initialUrl: String = DEFAULT_URL, cookieHeader: String? = null) {
        if (!isOpen) {
            openSession(initialUrl, cookieHeader)
        } else {
            loadUri(initialUrl, cookieHeader)
        }
    }

    /**
     * Pauses session, suspending active media and freeing background GPU memory.
     */
    fun onPause() {
        if (isOpen) {
            Log.d(TAG, "Suspending GeckoSession for background...")
            pauseMedia()
        }
    }

    /**
     * Resumes session when the user returns to foreground.
     */
    fun onResume() {
        if (isOpen) {
            Log.d(TAG, "Resuming GeckoSession for foreground...")
            session.setActive(true)
        }
    }

    fun loadUri(uri: String, cookieHeader: String? = null) {
        _currentUrl.value = uri
        session.setActive(true)
        if (!uri.startsWith("about:") && !com.ujwal.focusgram.core.utils.NetworkUtils.isNetworkAvailable(com.ujwal.focusgram.FocusGramApp.instance)) {
            _hasNetworkError.value = true
        }
        if (!cookieHeader.isNullOrBlank()) {
            val loader = GeckoSession.Loader()
                .uri(uri)
                .additionalHeaders(mapOf("Cookie" to cookieHeader))
            session.load(loader)
        } else {
            session.loadUri(uri)
        }
    }

    fun loadUrl(url: String, cookieHeader: String? = null) {
        loadUri(url, cookieHeader)
    }

    fun updateIslandMode(mode: IslandMode) {
        _islandMode.value = mode
    }

    fun updateTheme(theme: String) {
        _instagramTheme.value = theme
    }

    fun updateSettings(settings: FocusSettings) {
        currentSettings = settings
        extensionManager?.broadcastSettings(settings)
        val url = _currentUrl.value.lowercase()
        if (!isAuthUrl(url)) {
            userScriptManager?.injectConfig(session, settings, isAuthenticated = true)
        }
    }

    fun updateCurrentUrl(url: String) {
        if (!url.startsWith("javascript:")) {
            _currentUrl.value = url
            _islandMode.value = IslandMode.fromUrl(url)
        }
    }

    /**
     * Evaluates JavaScript safely within the running session by percent-encoding the wrapped payload.
     */
    fun evaluateJs(script: String) {
        val mgr = userScriptManager
        if (mgr != null) {
            mgr.evaluate(session, script)
        } else {
            try {
                val wrapped = "(function(){ try {\n$script\n} catch(e) { console.error('[GeckoSession] Execution error:', e); } })();"
                val encoded = "javascript:" + java.net.URLEncoder.encode(wrapped, "UTF-8").replace("+", "%20")
                session.loadUri(encoded)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to evaluate script: ${e.message}", e)
            }
        }
    }

    /**
     * Executes client-side Single Page Application (SPA) navigation without triggering a full page reload.
     */
    fun navigateSpa(path: String) {
        val fullUrl = if (path.startsWith("http")) path else "https://www.instagram.com$path"
        val relativePath = if (path.startsWith("http")) {
            path.removePrefix("https://www.instagram.com").removePrefix("http://www.instagram.com")
        } else {
            path
        }.ifEmpty { "/" }

        val safeRelative = org.json.JSONObject.quote(relativePath)
        val safeFull = org.json.JSONObject.quote(fullUrl)

        val js = """
            try {
                var p = $safeRelative;
                var full = $safeFull;
                if (window.__focusgramNavigate) {
                    window.__focusgramNavigate(p);
                    return;
                }
                var target = null;
                if (p === '/' || p === '') {
                    target = document.querySelector('svg[aria-label="Home"]')?.closest('a') ||
                             document.querySelector('a[href="/"]') ||
                             document.querySelector('a[href="/?variant=home"]');
                } else if (p.indexOf('explore') !== -1) {
                    target = document.querySelector('svg[aria-label="Search"]')?.closest('a') ||
                             document.querySelector('svg[aria-label="Explore"]')?.closest('a') ||
                             document.querySelector('a[href*="/explore"]');
                } else if (p.indexOf('reel') !== -1) {
                    target = document.querySelector('svg[aria-label="Reels"]')?.closest('a') ||
                             document.querySelector('a[href*="/reels"]') ||
                             document.querySelector('a[href*="/reel/"]');
                } else if (p == "/direct" || p == "/direct/" || p == "/direct/inbox" || p == "/direct/inbox/") {
                    target = document.querySelector('svg[aria-label="Messages"]')?.closest('a') ||
                             document.querySelector('svg[aria-label="Direct"]')?.closest('a') ||
                             document.querySelector('a[href="/direct/inbox/"]') ||
                             document.querySelector('a[href="/direct/inbox"]') ||
                             document.querySelector('a[href="/direct/"]');
                } else if (p.indexOf('/direct/t/') !== -1) {
                    target = document.querySelector('a[href*="' + p + '"]') ||
                             document.querySelector('a[href*="' + p.replace(/\/$/, '') + '"]');
                }
                if (!target) {
                    target = document.querySelector('a[href="' + p + '"]') ||
                             document.querySelector('a[href="' + p.replace(/\/$/, '') + '"]') ||
                             document.querySelector('a[href="' + p.replace(/\/$/, '') + '/"]') ||
                             document.querySelector('[data-fg-hide] a[href*="' + p.replace(/\/$/, '') + '"]');
                }

                if (target) {
                    target.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: window }));
                    target.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, view: window }));
                    target.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                    target.click();
                    return;
                }

                window.location.assign(full);
            } catch(e) {
                window.location.assign(full);
            }
        """.trimIndent()
        evaluateJs(js)
    }

    /**
     * Navigates to a specific Instagram URL via SPA without reload, immediately updating local state.
     */
    fun navigateTo(url: String) {
        _currentUrl.value = url
        _islandMode.value = IslandMode.fromUrl(url)
        navigateSpa(url)
    }

    /**
     * Executes SPA navigation to the logged-in user's profile without opening the edit profile page.
     */
    fun openProfileSpa() {
        val js = """
            try {
                if (window.__focusgramOpenProfile) {
                    window.__focusgramOpenProfile();
                    return;
                }
                var profileLink = document.querySelector('a[role="link"]:has(img[alt*="profile" i])') ||
                                  document.querySelector('a[role="link"]:has(img[alt*="\'s profile picture" i])') ||
                                  document.querySelector('a[href*="/profile/"]') ||
                                  document.querySelector('[data-focusgram-hidden-bottom-bar] a:last-child') ||
                                  document.querySelector('[data-fg-hide] a:last-child') ||
                                  document.querySelector('nav a[href^="/"]:last-child') ||
                                  document.querySelector('div[role="navigation"] a[href^="/"]:last-child');
                if (profileLink) {
                    var href = profileLink.getAttribute('href');
                    if (href && href !== '/accounts/edit/' && href !== '/accounts/edit' && href !== '/' && href.length > 2) {
                        profileLink.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: window }));
                        profileLink.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, view: window }));
                        profileLink.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                        profileLink.click();
                        return;
                    }
                }
                var username = (window._sharedData && window._sharedData.config && window._sharedData.config.viewer && window._sharedData.config.viewer.username) ||
                               (window.__initialData && window.__initialData.data && window.__initialData.data.viewer && window.__initialData.data.viewer.username) ||
                               window.__viewerUsername;
                if (username) {
                    window.location.assign('https://www.instagram.com/' + username + '/');
                    return;
                }
                window.location.assign('https://www.instagram.com/');
            } catch(e) {
                window.location.assign('https://www.instagram.com/');
            }
        """.trimIndent()
        evaluateJs(js)
    }

    /**
     * Identifies Instagram's fixed bottom tab bar in the DOM and suppresses it
     * so it does not collide with the native Jetpack Compose Island navigation bar.
     */
    fun injectBottomBarTrimmer() {
        if (!isAuthenticated) return
        val current = _currentUrl.value.lowercase()
        if (isAuthUrl(current)) return

        val js = """
            try {
                var p = (window.location.pathname || '').toLowerCase();
                var h = (window.location.hostname || '').toLowerCase();
                if (p.startsWith('/direct') || (p.startsWith('/accounts') && !p.startsWith('/accounts/edit')) || p.startsWith('/login') || p.startsWith('/signup') || p.startsWith('/two_factor') || p.startsWith('/challenge') || p.startsWith('/onetap') || h.includes('facebook.com') || h.includes('accountscenter.')) return;
                if (document.getElementById('loginForm') || document.querySelector('form[action*="login"], input[name="password"]')) return;
                document.querySelectorAll('a[href="/"], a[href="/?variant=home"], svg[aria-label="Home"], a[href*="/explore/"]').forEach(function(target) {
                    var el = target.closest('a') || target;
                    while (el && el !== document.body && el !== document.documentElement) {
                        if (el.getAttribute('role') === 'dialog' || el.tagName === 'ARTICLE' || el.tagName === 'FORM' || el.tagName === 'MAIN') break;
                        var cs = window.getComputedStyle(el);
                        var rect = el.getBoundingClientRect();
                        var isFixedOrSticky = cs.position === 'fixed' || cs.position === 'sticky';
                        var isDockedAtBottom = (rect.height > 28 && rect.height <= 75) &&
                                               (rect.bottom >= (window.innerHeight - 8)) &&
                                               (rect.top >= (window.innerHeight - 85));
                        if (isFixedOrSticky && isDockedAtBottom) {
                            el.setAttribute('data-fg-hide', '1');
                            el.setAttribute('data-focusgram-hidden-bottom-bar', 'true');
                            el.style.setProperty('display', 'none', 'important');
                            el.style.setProperty('visibility', 'hidden', 'important');
                            el.style.setProperty('height', '0px', 'important');
                            el.style.setProperty('pointer-events', 'none', 'important');
                            break;
                        }
                        el = el.parentElement;
                    }
                });
            } catch (_) {}
        """.trimIndent()
        evaluateJs(js)
    }

    fun reload() {
        _hasNetworkError.value = false
        _isLoading.value = true
        session.reload()
    }

    /**
     * Refreshes the Home feed and scrolls smoothly to the top.
     */
    fun refreshHome() {
        val js = """
            try {
                window.scrollTo(0, 0);
                var main = document.querySelector('main') || document.querySelector('[role="main"]');
                if (main) main.scrollTop = 0;
                var home = document.querySelector('a[href="/"], a[href="/?variant=home"]');
                if (home) { home.click(); }
                window.location.reload();
            } catch(e) {
                window.location.reload();
            }
        """.trimIndent()
        evaluateJs(js)
    }

    /**
     * Refreshes the Reels feed.
     */
    fun refreshReels() {
        val js = """
            try {
                window.scrollTo(0, 0);
                var reels = document.querySelector('a[href*="/reels/"], a[href*="/reel/"]');
                if (reels) { reels.click(); }
                window.location.reload();
            } catch(e) {
                window.location.reload();
            }
        """.trimIndent()
        evaluateJs(js)
    }

    fun goBack(): Boolean {
        val currentUrlStr = _currentUrl.value.lowercase()
        val path = IslandMode.extractPath(currentUrlStr).substringBefore('?').substringBefore('#').trimEnd('/').ifEmpty { "/" }

        val isDmThread = path.startsWith("/direct/t/") || path.startsWith("/direct/threads/") || (path.startsWith("/direct/") && path != "/direct/inbox" && path != "/direct")
        val isDmInbox = path == "/direct" || path == "/direct/inbox"
        val isRootHome = path == "/" || path.isEmpty()

        if (isDmThread) {
            navigateTo("https://www.instagram.com/direct/inbox/")
            return true
        }
        if (isDmInbox) {
            navigateTo("https://www.instagram.com/")
            return true
        }
        if (isRootHome) {
            return false
        }

        if (_canGoBack.value) {
            session.goBack()
            return true
        }
        val js = "if(window.history && window.history.length > 1) { window.history.back(); } else { window.location.href='/'; }"
        evaluateJs(js)
        return true
    }

    fun goForward(): Boolean {
        if (_canGoForward.value) {
            session.goForward()
            return true
        }
        return false
    }

    fun attachNavigationDelegate(
        context: Context,
        onRouteChanged: ((String) -> Unit)? = null
    ) {
        session.navigationDelegate = FocusGramNavigationDelegate(context, this, onRouteChanged)
    }

    fun attachPromptDelegate(context: Context, filePickerHandler: FocusGramPromptDelegate.FilePickerHandler? = null) {
        session.promptDelegate = FocusGramPromptDelegate(context, filePickerHandler)
    }

    fun updateNavigationState(canBack: Boolean, canForward: Boolean) {
        _canGoBack.value = canBack
        _canGoForward.value = canForward
    }

    fun pauseMedia() {
        try {
            val pauseJs = "try { window.__fgMediaPaused = true; document.querySelectorAll('video, audio').forEach(function(m){ try { m.pause(); } catch(_) {} }); } catch(_) {}"
            evaluateJs(pauseJs)
            session.setActive(false)
        } catch (e: Exception) {
            Log.w(TAG, "Error pausing media: ${e.message}")
        }
    }

    fun resumeMedia() {
        try {
            session.setActive(true)
            val resumeJs = "try { window.__fgMediaPaused = false; } catch(_) {}"
            evaluateJs(resumeJs)
        } catch (e: Exception) {
            Log.w(TAG, "Error resuming media: ${e.message}")
        }
    }

    fun close() {
        if (isOpen) {
            Log.i(TAG, "Closing GeckoSession...")
            session.close()
            isOpen = false
        }
    }

    companion object {
        private const val TAG = "GeckoSessionManager"
        const val DEFAULT_URL = "https://www.instagram.com/"

        fun isAuthUrl(url: String): Boolean {
            val lower = url.lowercase()
            if (lower.startsWith("about:") || lower.startsWith("blob:") || lower.startsWith("data:")) return false
            if (lower.contains("accountscenter.") || lower.contains("facebook.com") || lower.contains("meta.com")) return true
            val uri = try { android.net.Uri.parse(url) } catch (_: Exception) { null }
            val path = uri?.path?.lowercase() ?: ""
            if (path.startsWith("/accounts/edit") ||
                path.startsWith("/accounts/activity") ||
                path.startsWith("/accounts/privacy_and_security") ||
                path.startsWith("/accounts/supervision") ||
                path.startsWith("/accounts/professional_account")
            ) {
                return false
            }
            return path.startsWith("/accounts/login") ||
                    path.startsWith("/accounts/emailsignup") ||
                    path.startsWith("/accounts/signup") ||
                    path.startsWith("/accounts/two_factor") ||
                    path.startsWith("/accounts/password") ||
                    path.startsWith("/accounts/suspended") ||
                    path.startsWith("/accounts/vetted_user") ||
                    path.startsWith("/accounts/account_recovery") ||
                    path.startsWith("/accounts/disavow") ||
                    path.startsWith("/accounts/contact_history") ||
                    path.startsWith("/login") ||
                    path.startsWith("/signup") ||
                    path.startsWith("/two_factor") ||
                    path.startsWith("/challenge") ||
                    path.startsWith("/onetap") ||
                    (lower.contains("/accounts") && !path.startsWith("/accounts/"))
        }

        // Mobile Safari UA causes Instagram to serve touch-friendly web layouts without Google Play banners
        const val IOS_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
    }
}
