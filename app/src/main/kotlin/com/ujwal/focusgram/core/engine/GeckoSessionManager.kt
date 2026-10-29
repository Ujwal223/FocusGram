package com.ujwal.focusgram.core.engine

import android.content.Context
import android.util.Log
import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.core.model.IslandMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/**
 * Manages the primary [GeckoSession] lifecycle, foreground/background media states,
 * and DOM navigation bridge for Instagram web.
 */
class GeckoSessionManager(private val runtime: GeckoRuntime) {

    val session: GeckoSession
    var userScriptManager: UserScriptManager? = null
    var extensionManager: ExtensionManager? = null
    var currentSettings: FocusSettings = FocusSettings()

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

    private var isOpen = false
    var isAuthenticated: Boolean = false
        private set

    fun setAuthenticated(auth: Boolean) {
        val changed = isAuthenticated != auth
        isAuthenticated = auth
        if (auth && changed) {
            val url = _currentUrl.value.lowercase()
            val isAuth = (url.contains("/accounts") && !url.contains("/accounts/edit")) || url.contains("/login") || url.contains("/signup")
            if (!isAuth) {
                injectBottomBarTrimmer()
                extensionManager?.broadcastSettings(currentSettings)
                userScriptManager?.injectAll(session, currentSettings)
            }
        }
    }

    init {
        val settings = GeckoSessionSettings.Builder()
            .useTrackingProtection(true)
            .suspendMediaWhenInactive(true)
            .allowJavascript(true)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .userAgentOverride(IOS_USER_AGENT)
            // False keeps session cookies on disk; private mode clears them on process death
            .usePrivateMode(false)
            .build()

        session = GeckoSession(settings)
        setupProgressDelegate()
    }

    private fun setupProgressDelegate() {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                Log.d(TAG, "Page started loading: $url")
                if (!url.startsWith("focusgram-download:") && !url.startsWith("javascript:")) {
                    _isLoading.value = true
                    _currentUrl.value = url
                    _progress.value = 10
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                Log.d(TAG, "Page stopped loading. Success: $success")
                _isLoading.value = false
                _progress.value = 100
                if (success) {
                    val url = _currentUrl.value.lowercase()
                    val isAuth = (url.contains("/accounts") && !url.contains("/accounts/edit")) || url.contains("/login") || url.contains("/signup")
                    if (isAuthenticated && !isAuth) {
                        injectBottomBarTrimmer()
                        extensionManager?.broadcastSettings(currentSettings)
                        userScriptManager?.injectAll(session, currentSettings)
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
     * Opens the session attached to the GeckoRuntime.
     */
    fun openSession(initialUrl: String = DEFAULT_URL) {
        if (!isOpen) {
            Log.i(TAG, "Opening GeckoSession with runtime...")
            session.open(runtime)
            isOpen = true
            session.loadUri(initialUrl)
        }
    }

    fun openAndLoad(initialUrl: String = DEFAULT_URL) {
        if (!isOpen) {
            openSession(initialUrl)
        } else {
            loadUri(initialUrl)
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

    fun loadUri(uri: String) {
        _currentUrl.value = uri
        session.loadUri(uri)
    }

    fun loadUrl(url: String) {
        loadUri(url)
    }

    fun updateIslandMode(mode: IslandMode) {
        _islandMode.value = mode
    }

    fun updateTheme(theme: String) {
        _instagramTheme.value = theme
    }

    fun updateSettings(settings: FocusSettings) {
        currentSettings = settings
        val url = _currentUrl.value.lowercase()
        val isAuth = (url.contains("/accounts") && !url.contains("/accounts/edit")) || url.contains("/login") || url.contains("/signup")
        if (!isAuth) {
            userScriptManager?.injectConfig(session, settings)
        }
    }

    fun updateCurrentUrl(url: String) {
        if (!url.startsWith("javascript:")) {
            _currentUrl.value = url
            _islandMode.value = IslandMode.fromUrl(url)
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

        val js = """javascript:(function(){
            try {
                var p = '$relativePath';
                var full = '$fullUrl';
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
                window.location.assign('$fullUrl');
            }
        })();"""
        session.loadUri(js)
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
        val js = """javascript:(function(){
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
        })();"""
        session.loadUri(js)
    }

    /**
     * Identifies Instagram's fixed bottom tab bar in the DOM and suppresses it
     * so it does not collide with the native Jetpack Compose Island navigation bar.
     */
    fun injectBottomBarTrimmer() {
        val js = """javascript:(function() {
            try {
                var p = (window.location.pathname || '').toLowerCase();
                var h = (window.location.hostname || '').toLowerCase();
                if (p.startsWith('/direct') || (p.startsWith('/accounts') && !p.startsWith('/accounts/edit')) || p.startsWith('/login') || p.startsWith('/signup') || p.startsWith('/two_factor') || p.startsWith('/challenge') || p.startsWith('/onetap') || h.includes('facebook.com') || h.includes('accountscenter.')) return;
                var c = document.cookie || '';
                if ((p === '/' || p === '') && !c.includes('sessionid=') && !c.includes('ds_user_id=')) return;
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
        })();"""
        session.loadUri(js)
    }

    fun reload() {
        session.reload()
    }

    /**
     * Refreshes the Home feed and scrolls smoothly to the top.
     */
    fun refreshHome() {
        val js = """javascript:(function(){
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
        })();"""
        session.loadUri(js)
    }

    /**
     * Refreshes the Reels feed.
     */
    fun refreshReels() {
        val js = """javascript:(function(){
            try {
                window.scrollTo(0, 0);
                var reels = document.querySelector('a[href*="/reels/"], a[href*="/reel/"]');
                if (reels) { reels.click(); }
                window.location.reload();
            } catch(e) {
                window.location.reload();
            }
        })();"""
        session.loadUri(js)
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
        val js = "javascript:(function(){ if(window.history && window.history.length > 1) { window.history.back(); } else { window.location.href='/'; } })();"
        session.loadUri(js)
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
            val pauseJs = "javascript:(function(){ try { window.__fgMediaPaused = true; document.querySelectorAll('video, audio').forEach(function(m){ try { m.pause(); } catch(_) {} }); } catch(_) {} })();"
            session.loadUri(pauseJs)
            session.setActive(false)
        } catch (e: Exception) {
            Log.w(TAG, "Error pausing media: ${e.message}")
        }
    }

    fun resumeMedia() {
        try {
            session.setActive(true)
            val resumeJs = "javascript:(function(){ try { window.__fgMediaPaused = false; } catch(_) {} })();"
            session.loadUri(resumeJs)
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

        // Mobile Safari UA causes Instagram to serve touch-friendly web layouts without Google Play banners
        const val IOS_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
    }
}
