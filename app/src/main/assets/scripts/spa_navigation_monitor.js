/**
 * Client-side SPA navigation monitor.
 *
 * Patches HTML5 History API and listens to navigation events to notify
 * content filters and native delegates of in-page route changes.
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

        if (typeof window.__fgContentHiderSweep === 'function') {
            try {
                window.__fgContentHiderSweep();
            } catch (e) {
                console.warn('[FocusGram SPA Monitor] Content hider sweep error:', e);
            }
        }

        // Notify other user scripts (autoplay blocker, reel extractor).
        try {
            window.dispatchEvent(new CustomEvent('focusgram:routechange', {
                detail: { url: targetUrl, timestamp: Date.now() }
            }));
        } catch (e) {
            console.warn('[FocusGram SPA Monitor] Event dispatch error:', e);
        }

        // Forward route change to WebExtension background worker when available.
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
