/**
 * FocusGram — SPA Navigation Monitor & Dynamic Mutation Bridge
 *
 * Hooks into HTML5 History API (pushState, replaceState) and popstate events
 * to detect client-side route transitions in Instagram's Single Page Application (SPA).
 * Instantly re-enforces content disabling guards and informs native delegates
 * within 5ms without relying on full page reload cycles.
 */

'use strict';

(function () {
    if (window.__fgSpaNavigationMonitorInitialized) return;
    window.__fgSpaNavigationMonitorInitialized = true;

    const originalPushState = history.pushState;
    const originalReplaceState = history.replaceState;
    let lastUrl = window.location.href;

    function handleRouteChange(newUrl) {
        const targetUrl = newUrl ? (new URL(newUrl, window.location.origin)).href : window.location.href;
        if (targetUrl === lastUrl) return;
        lastUrl = targetUrl;

        // 1. Immediately re-apply content hiding guards (< 5ms)
        if (typeof window.__fgContentHiderSweep === 'function') {
            try {
                window.__fgContentHiderSweep();
            } catch (e) {
                console.warn('[FocusGram SPA Monitor] Content hider sweep error:', e);
            }
        }

        // 2. Dispatch custom DOM event for other modular UserScripts (e.g. Autoplay blocker, Reels metadata)
        try {
            window.dispatchEvent(new CustomEvent('focusgram:routechange', {
                detail: { url: targetUrl, timestamp: Date.now() }
            }));
        } catch (e) {
            console.warn('[FocusGram SPA Monitor] Event dispatch error:', e);
        }

        // 3. Inform WebExtension runtime if bridge is present
        try {
            if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.sendMessage) {
                browser.runtime.sendMessage({
                    type: 'UrlChange',
                    url: targetUrl
                }).catch(() => {});
            }
        } catch (_) {}
    }

    history.pushState = function () {
        originalPushState.apply(this, arguments);
        const nextUrl = arguments[2];
        handleRouteChange(nextUrl);
    };

    history.replaceState = function () {
        originalReplaceState.apply(this, arguments);
        const nextUrl = arguments[2];
        handleRouteChange(nextUrl);
    };

    window.addEventListener('popstate', function () {
        handleRouteChange(window.location.href);
    });

    window.addEventListener('hashchange', function () {
        handleRouteChange(window.location.href);
    });

    console.log('[FocusGram] SPA Navigation Monitor active.');
})();
