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
            if (path.includes('/accounts/edit')) return false;
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

    /* ==========================================================================
       7. LIVE BIO FONT STYLIST & REAL-TIME INPUT ENGINE
       Directly mounts a dedicated styling accessory bar above textarea#pepBio,
       handles bidirectional Unicode mathematical font conversion,
       and transforms typing in real time.
       ========================================================================== */
    const BIO_FONT_MAPS = {
        normal: null,
        bold: { u: 0x1D400, l: 0x1D41A, d: 0x1D7CE },           // Mathematical Bold (Zero gaps)
        sans: { u: 0x1D5D4, l: 0x1D5EE, d: 0x1D7EC },           // Sans-Serif Bold (Zero gaps)
        sans_italic: { u: 0x1D608, l: 0x1D622, d: null },       // Sans-Serif Italic (Zero gaps)
        mono: { u: 0x1D670, l: 0x1D68A, d: 0x1D7F6 },           // Monospace (Zero gaps)
        gothic: { u: 0x1D56C, l: 0x1D586, d: null },            // Bold Fraktur/Gothic (Zero gaps)
        script: { u: 0x1D4D0, l: 0x1D4EA, d: null }             // Bold Script/Cursive (Zero gaps)
    };

    let activeBioStyle = 'bold';
    let activeBioTarget = null;
    let bioInlineBarEl = null;

    function normalizeChar(char) {
        if (!char) return '';
        const cp = char.codePointAt(0);
        for (const [key, map] of Object.entries(BIO_FONT_MAPS)) {
            if (!map) continue;
            if (cp >= map.u && cp <= map.u + 25) {
                return String.fromCharCode(65 + (cp - map.u));
            }
            if (cp >= map.l && cp <= map.l + 25) {
                return String.fromCharCode(97 + (cp - map.l));
            }
            if (map.d && cp >= map.d && cp <= map.d + 9) {
                return String.fromCharCode(48 + (cp - map.d));
            }
        }
        return char;
    }

    function convertCharToStyle(char, styleKey) {
        const map = BIO_FONT_MAPS[styleKey];
        if (!map || styleKey === 'normal') return normalizeChar(char);
        const norm = normalizeChar(char);
        const cp = norm.codePointAt(0);
        if (cp >= 65 && cp <= 90) { // A-Z
            return String.fromCodePoint(map.u + (cp - 65));
        }
        if (cp >= 97 && cp <= 122) { // a-z
            return String.fromCodePoint(map.l + (cp - 97));
        }
        if (map.d && cp >= 48 && cp <= 57) { // 0-9
            return String.fromCodePoint(map.d + (cp - 48));
        }
        return norm;
    }

    function convertStringToStyle(text, styleKey) {
        if (!text) return '';
        return Array.from(text).map(ch => convertCharToStyle(ch, styleKey)).join('');
    }

    function isBioTarget(el) {
        if (!el) return false;
        if (el.id === 'pepBio') return true;
        const name = (el.name || '').toLowerCase();
        const placeholder = (el.placeholder || '').toLowerCase();
        const aria = (el.getAttribute('aria-label') || '').toLowerCase();
        if (name === 'biography' || name === 'bio' || placeholder === 'bio' || aria === 'bio') return true;
        const path = (window.location.pathname || '').toLowerCase();
        if (path.includes('/accounts/edit') && (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT')) return true;
        return false;
    }

    function updateBioCharCounter(target) {
        const el = target || activeBioTarget || document.getElementById('pepBio');
        const counter = document.getElementById('fg-bio-counter');
        if (!el || !counter) return;

        const text = el.value || el.textContent || '';
        const codePoints = Array.from(text).length;

        counter.textContent = `${codePoints} / 150`;
        if (codePoints > 140) {
            counter.style.color = '#ef4444';
        } else if (codePoints > 120) {
            counter.style.color = '#f59e0b';
        } else {
            counter.style.color = '#8e8e8e';
        }
    }

    function setReactInputValue(input, val) {
        if (!input) return;
        try {
            const proto = window.HTMLTextAreaElement ? window.HTMLTextAreaElement.prototype : Object.getPrototypeOf(input);
            const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
            if (setter) {
                setter.call(input, val);
            } else {
                input.value = val;
            }
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.dispatchEvent(new Event('change', { bubbles: true }));
        } catch (_) {
            input.value = val;
        }
    }

    function applyBioStyleToTarget(styleId) {
        activeBioStyle = styleId;
        const target = activeBioTarget || document.getElementById('pepBio') || document.querySelector('textarea[aria-label="Bio"]');

        // Update inline chip states (native Instagram Stories pill style)
        document.querySelectorAll('.fg-bio-chip').forEach(c => {
            const isSelected = c.dataset.styleId === activeBioStyle;
            c.style.background = isSelected ? '#ffffff' : '#262626';
            c.style.borderColor = isSelected ? '#ffffff' : 'rgba(255, 255, 255, 0.15)';
            c.style.color = isSelected ? '#000000' : '#d1d5db';
            c.style.fontWeight = isSelected ? '600' : '500';
        });

        if (!target) return;

        // Convert ONLY selected text if user made an explicit text selection
        const val = target.value || '';
        if (val && target.selectionStart !== undefined && target.selectionEnd !== undefined && target.selectionStart !== target.selectionEnd) {
            const start = target.selectionStart;
            const end = target.selectionEnd;
            const before = val.substring(0, start);
            const selected = val.substring(start, end);
            const after = val.substring(end);
            const converted = convertStringToStyle(selected, activeBioStyle);
            setReactInputValue(target, before + converted + after);
            target.setSelectionRange(start, start + converted.length);
        }
        // If NO text is selected, do NOT alter existing text!
        // activeBioStyle is set, so only characters typed afterwards will be converted.
        updateBioCharCounter(target);
    }

    window.__fgSetBioStyle = applyBioStyleToTarget;

    function notifyBioFocus(focused) {
        try {
            window.postMessage({ type: 'FOCUSGRAM_BIO_FOCUSED', focused: focused }, '*');
            document.dispatchEvent(new CustomEvent('focusgram:bio-focused', { detail: { focused: focused } }));
            if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.sendMessage) {
                browser.runtime.sendMessage({ action: 'bioInputFocused', focused: focused }).catch(() => {});
            }
        } catch (_) {}
    }

    function mountBioInlineBar(textarea) {
        if (!textarea) return null;
        if (document.getElementById('fg-bio-inline-bar')) {
            return document.getElementById('fg-bio-inline-bar');
        }

        const bar = document.createElement('div');
        bar.id = 'fg-bio-inline-bar';
        bar.style.cssText = `
            margin: 10px 0 6px 0 !important;
            padding: 8px 10px !important;
            background: #18181c !important;
            border: 1px solid rgba(255, 255, 255, 0.15) !important;
            border-radius: 12px !important;
            display: flex !important;
            flex-direction: column !important;
            gap: 6px !important;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important;
            box-sizing: border-box !important;
            user-select: none !important;
            -webkit-user-select: none !important;
        `;

        const topRow = document.createElement('div');
        topRow.style.cssText = `
            display: flex !important;
            align-items: center !important;
            justify-content: space-between !important;
            font-size: 12px !important;
            color: #d1d5db !important;
        `;

        const titleSpan = document.createElement('span');
        titleSpan.innerHTML = `<span style="font-size: 12px; font-weight: 600; color: #8e8e8e; letter-spacing: 0.3px; text-transform: uppercase;">Bio Font</span>`;

        const counterSpan = document.createElement('span');
        counterSpan.id = 'fg-bio-counter';
        counterSpan.style.cssText = `
            font-size: 11px !important;
            font-weight: 600 !important;
            color: #8e8e8e !important;
            background: rgba(255, 255, 255, 0.06) !important;
            padding: 2px 8px !important;
            border-radius: 10px !important;
        `;
        counterSpan.textContent = '0 / 150';

        topRow.appendChild(titleSpan);
        topRow.appendChild(counterSpan);

        const chipsRow = document.createElement('div');
        chipsRow.style.cssText = `
            display: flex !important;
            align-items: center !important;
            gap: 6px !important;
            overflow-x: auto !important;
            scrollbar-width: none !important;
            -webkit-overflow-scrolling: touch !important;
            padding: 2px 0 !important;
        `;

        const STYLES = [
            { id: 'normal', label: 'Normal' },
            { id: 'bold', label: 'Bold' },
            { id: 'sans', label: 'Sans' },
            { id: 'sans_italic', label: 'Italic' },
            { id: 'mono', label: 'Mono' },
            { id: 'gothic', label: 'Gothic' },
            { id: 'script', label: 'Script' }
        ];

        STYLES.forEach(st => {
            const chip = document.createElement('button');
            chip.type = 'button';
            chip.className = 'fg-bio-chip';
            chip.dataset.styleId = st.id;
            chip.textContent = st.label;
            const isSelected = st.id === activeBioStyle;
            chip.style.cssText = `
                padding: 5px 12px !important;
                border-radius: 14px !important;
                border: 1px solid ${isSelected ? '#ffffff' : 'rgba(255, 255, 255, 0.15)'} !important;
                background: ${isSelected ? '#ffffff' : '#262626'} !important;
                color: ${isSelected ? '#000000' : '#d1d5db'} !important;
                font-size: 12px !important;
                font-weight: ${isSelected ? '600' : '500'} !important;
                white-space: nowrap !important;
                cursor: pointer !important;
                outline: none !important;
                transition: background 0.15s ease, color 0.15s ease !important;
            `;

            chip.addEventListener('click', (e) => {
                e.preventDefault();
                applyBioStyleToTarget(st.id);
                if (textarea) textarea.focus();
            });

            chipsRow.appendChild(chip);
        });

        bar.appendChild(topRow);
        bar.appendChild(chipsRow);

        if (textarea.parentElement) {
            textarea.parentElement.insertBefore(bar, textarea);
        } else {
            textarea.before(bar);
        }

        bioInlineBarEl = bar;
        updateBioCharCounter(textarea);
        return bar;
    }

    function checkAndMountBioStylist() {
        const bioEl = document.getElementById('pepBio') || 
                      document.querySelector('textarea[aria-label="Bio"]') || 
                      document.querySelector('textarea[placeholder="Bio"]') ||
                      document.querySelector('textarea[name="biography"]');

        if (bioEl) {
            activeBioTarget = bioEl;
            mountBioInlineBar(bioEl);
        }
    }

    // Intercept keystrokes for real-time live typing transformation
    document.addEventListener('beforeinput', (e) => {
        if (!isBioTarget(e.target) || activeBioStyle === 'normal') return;
        if (e.data && e.inputType === 'insertText') {
            e.preventDefault();
            const converted = convertStringToStyle(e.data, activeBioStyle);
            document.execCommand('insertText', false, converted);
            updateBioCharCounter(e.target);
        }
    }, true);

    document.addEventListener('input', (e) => {
        if (!isBioTarget(e.target)) return;
        updateBioCharCounter(e.target);
    }, true);

    document.addEventListener('focusin', (e) => {
        if (isBioTarget(e.target)) {
            activeBioTarget = e.target;
            mountBioInlineBar(e.target);
            notifyBioFocus(true);
            updateBioCharCounter(e.target);
        }
    }, true);

    document.addEventListener('focusout', (e) => {
        if (isBioTarget(e.target)) {
            setTimeout(() => {
                const active = document.activeElement;
                if (!isBioTarget(active)) {
                    notifyBioFocus(false);
                }
            }, 200);
        }
    }, true);

    // Watch for dynamic rendering of Bio textarea on /accounts/edit/
    const bioObserver = new MutationObserver(() => {
        checkAndMountBioStylist();
    });
    bioObserver.observe(document.documentElement, { childList: true, subtree: true });
    setInterval(checkAndMountBioStylist, 500);

    // Initialize immediately or upon DOM ready
    injectNativeStyles();
    applyScrollSmoothing();
    setupKeyboardViewportFix();
    setupMediaUploadBridge();
    enhanceFileInputsForVideoSupport();
    preventLandscapeOrientationLock();
    checkAndMountBioStylist();

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            injectNativeStyles();
            applyScrollSmoothing();
            setupMediaUploadBridge();
            enhanceFileInputsForVideoSupport();
            preventLandscapeOrientationLock();
            checkAndMountBioStylist();
        });
    }

    console.log('[FocusGram] Native Feel, Bio Font Stylist & Video Support active.');
})();
