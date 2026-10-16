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
    function isAuthPage() {
        try {
            var path = (window.location.pathname || '').toLowerCase();
            if (path.startsWith('/accounts') || path.startsWith('/login') || path.startsWith('/signup')) return true;
            var c = document.cookie || '';
            if (!c.includes('sessionid=') && !c.includes('ds_user_id=')) return true;
            if (document.getElementById('loginForm') || document.querySelector('form[action*="login"], input[name="password"]')) return true;
        } catch (_) {}
        return false;
    }

    if (isAuthPage()) return; // Leave login/signup completely untouched

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

    function enhanceFileInputsForVideoSupport() {
        function patchInput(input) {
            if (!input || input.tagName !== 'INPUT' || input.type !== 'file') return;
            // Never patch profile avatar uploads
            const path = window.location.pathname.toLowerCase();
            if (path.includes('/accounts/edit')) return;

            const currentAccept = input.getAttribute('accept') || '';
            if (currentAccept && currentAccept.includes('image') && !currentAccept.includes('video')) {
                // Broaden accept attribute to allow video formats (MP4, MOV, WebM, etc.)
                input.setAttribute('accept', currentAccept + ',video/mp4,video/quicktime,video/webm,video/*');
            } else if (!currentAccept) {
                input.setAttribute('accept', 'image/*,video/*');
            }
        }

        // Patch any existing inputs immediately
        document.querySelectorAll('input[type="file"]').forEach(patchInput);

        // Intercept pointer/click before it triggers onFilePrompt to guarantee accept attribute is expanded
        document.addEventListener('pointerdown', function(e) {
            const target = e.target;
            if (!target) return;
            const trigger = target.closest('button, [role="button"], a, div[role="menuitem"], label');
            const fileInput = (target.tagName === 'INPUT' && target.type === 'file') ? target :
                              (trigger ? (trigger.querySelector('input[type="file"]') || (trigger.parentElement ? trigger.parentElement.querySelector('input[type="file"]') : null)) : null);
            if (fileInput) {
                patchInput(fileInput);
            }
        }, { capture: true, passive: true });

        // Observe dynamically created upload dialogs (e.g. DM media buttons, Story camera, New Post)
        const observer = new MutationObserver(function(mutations) {
            for (let i = 0; i < mutations.length; i++) {
                const added = mutations[i].addedNodes;
                for (let j = 0; j < added.length; j++) {
                    const node = added[j];
                    if (node.nodeType === 1) {
                        if (node.tagName === 'INPUT' && node.type === 'file') {
                            patchInput(node);
                        } else if (node.querySelectorAll) {
                            node.querySelectorAll('input[type="file"]').forEach(patchInput);
                        }
                    }
                }
            }
        });
        observer.observe(document.documentElement, { childList: true, subtree: true });
    }

    function setupMediaUploadBridge() {
        // Ensures user taps on Instagram media upload triggers (Story +, Post +, DMs photo, Profile photo)
        // reliably trigger the underlying input[type="file"] element.
        document.addEventListener('click', function(e) {
            const target = e.target;
            if (!target) return;
            if (target.tagName === 'INPUT' && target.type === 'file') return;

            const trigger = target.closest('button, [role="button"], a, div[role="menuitem"], label');
            if (!trigger) return;

            const fileInput = trigger.querySelector('input[type="file"]') ||
                              (trigger.parentElement ? trigger.parentElement.querySelector('input[type="file"]') : null);

            if (fileInput && fileInput !== target) {
                try {
                    fileInput.click();
                } catch (_) {}
            }
        }, { capture: true, passive: true });
    }

    function preventLandscapeOrientationLock() {
        // Blocks web pages / HTML5 video players from forcing device orientation into landscape
        // when portrait-only mode is active in FocusGram settings.
        try {
            if (screen.orientation && typeof screen.orientation.lock === 'function') {
                const origLock = screen.orientation.lock.bind(screen.orientation);
                screen.orientation.lock = function(orientation) {
                    const isPortraitOnly = !window.__FOCUSGRAM_CONFIG__ || window.__FOCUSGRAM_CONFIG__.portraitOnly !== false;
                    if (isPortraitOnly && orientation && typeof orientation === 'string' && orientation.includes('landscape')) {
                        return Promise.resolve();
                    }
                    return origLock(orientation);
                };
            }
        } catch (_) {}
    }

    // Initialize immediately or upon DOM ready
    injectNativeStyles();
    applyScrollSmoothing();
    setupKeyboardViewportFix();
    setupMediaUploadBridge();
    enhanceFileInputsForVideoSupport();
    preventLandscapeOrientationLock();

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            injectNativeStyles();
            applyScrollSmoothing();
            setupMediaUploadBridge();
            enhanceFileInputsForVideoSupport();
            preventLandscapeOrientationLock();
        });
    }

    console.log('[FocusGram] Native Feel, Touch Smoothing, Media Upload Bridge & Video Support active.');
})();
