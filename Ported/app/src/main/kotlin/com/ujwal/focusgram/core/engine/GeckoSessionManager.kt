package com.ujwal.focusgram.core.engine

import android.content.Context
import android.util.Log
import com.ujwal.focusgram.core.model.IslandMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/**
 * GeckoSessionManager
 * Manages the primary GeckoSession lifecycle, background suspension, memory reclamation,
 * and page progress tracking for FocusGram.
 */
class GeckoSessionManager(private val runtime: GeckoRuntime) {

    val session: GeckoSession

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

    private var isOpen = false

    init {
        val settings = GeckoSessionSettings.Builder()
            .useTrackingProtection(true)
            .suspendMediaWhenInactive(true)
            .allowJavascript(true)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .userAgentOverride(IOS_USER_AGENT)
            .usePrivateMode(false) // Retain Instagram authentication cookies locally
            .build()

        session = GeckoSession(settings)
        setupProgressDelegate()
    }

    private fun setupProgressDelegate() {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                Log.d(TAG, "Page started loading: $url")
                _isLoading.value = true
                _currentUrl.value = url
                _progress.value = 10
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                Log.d(TAG, "Page stopped loading. Success: $success")
                _isLoading.value = false
                _progress.value = 100
                if (success) {
                    injectBottomBarTrimmer()
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
            session.setActive(false)
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
        val sanitized = path.replace("'", "\\'")
        val js = "javascript:(function(){ if(window.__focusgramNavigate) { window.__focusgramNavigate('$sanitized'); } else { window.location.href='$sanitized'; } })();"
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
        val js = "javascript:(function(){ if(window.__focusgramOpenProfile) { window.__focusgramOpenProfile(); } else { window.location.href='/'; } })();"
        session.loadUri(js)
    }

    /**
     * Senior Blueprint: Ensures bottom navigation bar is marked and hidden
     * using position: fixed and bottom bounds inspection.
     */
    fun injectBottomBarTrimmer() {
        val js = """javascript:(function() {
            try {
                document.querySelectorAll('a[href="/"], a[href="/?variant=home"], svg[aria-label="Home"], a[href*="/explore/"], a[href*="/direct/inbox"]').forEach(function(target) {
                    var el = target.closest('a') || target;
                    while (el && el !== document.body && el !== document.documentElement) {
                        if (el.getAttribute('role') === 'dialog' || el.tagName === 'ARTICLE' || el.tagName === 'FORM') break;
                        var cs = window.getComputedStyle(el);
                        var rect = el.getBoundingClientRect();
                        var isFixedOrSticky = cs.position === 'fixed' || cs.position === 'sticky';
                        var isDockedAtBottom = (cs.bottom && parseInt(cs.bottom, 10) <= 2) ||
                                               (rect.height > 28 && rect.height < 120 && rect.bottom >= (window.innerHeight - 8) && rect.top >= (window.innerHeight - 120));
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

    fun goBack(): Boolean {
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

    fun attachNavigationDelegate(context: Context, onRouteChanged: ((String) -> Unit)? = null) {
        session.navigationDelegate = FocusGramNavigationDelegate(context, this, onRouteChanged)
    }

    fun attachPromptDelegate(context: Context, filePickerHandler: FocusGramPromptDelegate.FilePickerHandler? = null) {
        session.promptDelegate = FocusGramPromptDelegate(context, filePickerHandler)
    }

    fun updateNavigationState(canBack: Boolean, canForward: Boolean) {
        _canGoBack.value = canBack
        _canGoForward.value = canForward
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
        const val IOS_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
    }
}
