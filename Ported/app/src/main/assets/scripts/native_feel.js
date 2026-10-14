/**
 * FocusGram — Native Feel, Touch Smoothing & Keyboard Fix UserScript
 *
 * Implements native Android application styling and touch physics:
 * 1. Hides web scrollbars and eliminates blue tap highlights.
 * 2. Restricts text selection to input and editable areas to prevent accidental selection during gestures.
 * 3. Enforces hardware-accelerated momentum scrolling (120Hz display refresh support).
 * 4. Dispatches synthetic viewport and orientation change events upon soft keyboard show/hide,
 *    preventing Instagram Direct Message (DM) thread freeze and layout stalls.
 */

'use strict';

(function () {
    if (window.__fgNativeFeelInitialized) return;
    window.__fgNativeFeelInitialized = true;

    const STYLE_ID = 'focusgram-native-feel-styles';

    const CSS_RULES = `
        /* 1. Hide all scrollbars across containers */
        * {
            -ms-overflow-style: none !important;
            scrollbar-width: none !important;
        }
        *::-webkit-scrollbar {
            display: none !important;
            width: 0 !important;
            height: 0 !important;
        }

        /* 2. Eliminate web touch callouts and tap highlights */
        * {
            -webkit-tap-highlight-color: transparent !important;
            -webkit-touch-callout: none !important;
        }

        /* 3. Disable browser text selection except on editable fields */
        * {
            -webkit-user-select: none !important;
            user-select: none !important;
        }
        input, textarea, [contenteditable="true"], [role="textbox"] {
            -webkit-user-select: text !important;
            user-select: text !important;
        }

        /* 4. Enforce hardware-accelerated momentum touch scrolling on scrollable containers */
        html, body, [data-fg-reel-scroller], div[style*="overflow"], main {
            -webkit-overflow-scrolling: touch !important;
        }

        /* 5. Remove web focus rings and outlines */
        *:focus, *:focus-visible {
            outline: none !important;
        }

        /* 6. Subtle smooth fade-in for image loading */
        img {
            animation: fgFadeIn 0.12s ease-in-out;
        }
        @keyframes fgFadeIn {
            from { opacity: 0.1; }
            to { opacity: 1; }
        }
    `;

    function injectNativeStyles() {
        if (document.getElementById(STYLE_ID)) return;
        const style = document.createElement('style');
        style.id = STYLE_ID;
        style.type = 'text/css';
        style.appendChild(document.createTextNode(CSS_RULES));
        const head = document.head || document.documentElement;
        if (head) {
            head.appendChild(style);
        }
    }

    function applyScrollSmoothing() {
        try {
            document.documentElement.style.setProperty('-webkit-overflow-scrolling', 'touch');
            document.documentElement.style.setProperty('overflow-scrolling', 'touch');
            document.documentElement.style.scrollBehavior = 'auto';
        } catch (_) {}
    }

    function setupKeyboardViewportFix() {
        // Debounce resize events to nudge Instagram layout system on Android IME opening
        let resizeTimer = null;
        function nudgeLayout() {
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(() => {
                try {
                    window.dispatchEvent(new Event('resize'));
                } catch (_) {}
            }, 80);
        }

        window.addEventListener('resize', nudgeLayout, { passive: true });

        if (window.visualViewport) {
            window.visualViewport.addEventListener('resize', nudgeLayout, { passive: true });
        }
    }

    // Initialize immediately or upon DOM ready
    injectNativeStyles();
    applyScrollSmoothing();
    setupKeyboardViewportFix();

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            injectNativeStyles();
            applyScrollSmoothing();
        });
    }

    console.log('[FocusGram] Native Feel & Touch Smoothing active.');
})();
