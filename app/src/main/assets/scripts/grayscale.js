/**
 * Dynamic CSS grayscale filter.
 *
 * Applies a full grayscale filter to media and document root when enabled in settings.
 */

'use strict';

(function () {
    function isAuthPage() {
        try {
            var host = (window.location.hostname || '').toLowerCase();
            if (host.includes('facebook.com') || host.includes('accountscenter.') || host.includes('meta.com')) return true;
            var path = (window.location.pathname || '').toLowerCase();
            if (path.includes('/accounts/edit')) return false;
            if (path.startsWith('/accounts') || path.startsWith('/login') || path.startsWith('/signup') ||
                path.startsWith('/two_factor') || path.startsWith('/challenge') || path.startsWith('/onetap') ||
                path.startsWith('/privacy')) return true;
            if (document.getElementById('loginForm') ||
                document.querySelector('form[action*="login"], form[action*="signup"], form input[type="password"], input[name="password"]')) return true;
        } catch (_) {}
        return false;
    }

    if (isAuthPage()) return;

    const STYLE_ID = 'focusgram-grayscale-filter';

    function applyGrayscale(enable) {
        let style = document.getElementById(STYLE_ID);
        if (enable) {
            if (!style) {
                style = document.createElement('style');
                style.id = STYLE_ID;
                style.type = 'text/css';
                const head = document.head || document.documentElement;
                if (head) head.appendChild(style);
            }
            style.textContent = `
                html, img, video {
                    filter: grayscale(100%) !important;
                    -webkit-filter: grayscale(100%) !important;
                }
            `;
            if (!window.__fgGrayscaleObserver) {
                window.__fgGrayscaleObserver = new MutationObserver(() => {
                    if (!document.getElementById(STYLE_ID)) applyGrayscale(true);
                });
                window.__fgGrayscaleObserver.observe(document.documentElement, { childList: true, subtree: true });
            }
        } else {
            if (window.__fgGrayscaleObserver) {
                window.__fgGrayscaleObserver.disconnect();
                window.__fgGrayscaleObserver = null;
            }
            if (style) style.remove();
            document.documentElement.style.filter = '';
        }
    }

    window.__fgSetGrayscale = applyGrayscale;

    window.addEventListener('message', (e) => {
        if (e.data && e.data.type === 'FOCUSGRAM_UPDATE' && e.data.config) {
            if (e.data.config.grayscale !== undefined) {
                applyGrayscale(!!e.data.config.grayscale);
            }
        }
    });

    const initialGrayscale = (window.__FOCUSGRAM_CONFIG__ && window.__FOCUSGRAM_CONFIG__.grayscale) || false;
    applyGrayscale(initialGrayscale);

    console.log('[FocusGram] Grayscale UserScript initialized (Active: ' + initialGrayscale + ').');
})();
