/**
 * FocusGram — Hardware-Accelerated CSS Grayscale Filter UserScript
 *
 * Applies full grayscale filter to strip colorful dopamine triggers from Instagram,
 * while allowing instant toggle and schedule support without reload.
 */

'use strict';

(function () {
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

    const initialGrayscale = (window.__FOCUSGRAM_CONFIG__ && window.__FOCUSGRAM_CONFIG__.grayscale) || false;
    applyGrayscale(initialGrayscale);

    console.log('[FocusGram] Grayscale UserScript initialized (Active: ' + initialGrayscale + ').');
})();
