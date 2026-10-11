/**
 * FocusGram In-Page Controller & Ghostify Privacy Engine
 * 
 * Injected as a WebExtension content script across instagram.com at document_start.
 * Features:
 *  1. Ghost Mode: Main-world fetch and XHR interceptors for Story views, DM Read Receipts, and Typing indicators.
 *  2. Bottom Bar Trimming: Hides Instagram's web bottom navigation bar and adds margin for the native app bar.
 *  3. Dynamic Margins: Removes margin when keyboard/inputs are active so comments and DM input boxes are never blocked.
 *  4. Top Logo Trigger: Intercepts clicks and long-presses on the Instagram logo to show FocusGram Settings.
 *  5. Focus/Typing Bridge: Notifies native Android host to hide/show the floating bottom navigation bar.
 *  6. SPA Navigation: Provides instant client-side route transitions without page reloads.
 *  7. Digital Wellness Guardrails: Dynamic CSS filtering for Stories, Reels, Explore blur, Likes, and Suggested posts.
 */

'use strict';

(function () {
    console.log('[FocusGram] In-page controller initialized.');

    // --- SENIOR BLUEPRINT: Immediate anti-flash style injected at document_start ---
    try {
        const immediateStyle = document.createElement('style');
        immediateStyle.id = 'focusgram-immediate-nav-hide';
        immediateStyle.textContent = `
            [data-fg-hide],
            [data-fg-hide="1"],
            div[data-focusgram-hidden-bottom-bar="true"],
            nav:has(a[href*="/explore/"]),
            nav:has(a[href*="/direct/inbox"]),
            div:has(> div > a[href="/"]):has(a[href*="/explore/"]),
            div:has(> a[href="/"]):has(a[href*="/explore/"]),
            footer:has(a[href*="/explore/"]) {
                display: none !important;
                visibility: hidden !important;
                height: 0 !important;
                min-height: 0 !important;
                max-height: 0 !important;
                overflow: hidden !important;
                opacity: 0 !important;
                pointer-events: none !important;
            }
        `;
        (document.head || document.documentElement).appendChild(immediateStyle);
    } catch (_) {}

    let currentSettings = {
        noAds: true,
        noStories: false,
        noReels: false,
        blurExplore: false,
        hideLikeCounts: false,
        hideFollowerCounts: false,
        hideSuggestedPosts: false,
        hideShopTab: false,
        ghostModeEnabled: false,
        ghostHideStorySeen: true,
        ghostHideDirectSeen: true,
        ghostHideTypingIndicator: true
    };

    // --- 1. GHOST MODE INTERCEPTION ENGINE (100% Ghostify Parity) ---
    function setupGhostModeInterceptors() {
        const GHOST_SCRIPT_ID = 'focusgram-ghost-interceptor';
        if (document.getElementById(GHOST_SCRIPT_ID)) return;

        const script = document.createElement('script');
        script.id = GHOST_SCRIPT_ID;
        script.textContent = `
        (function() {
            window.__focusgramGhostSettings = {
                enabled: false,
                hideStorySeen: true,
                hideDirectSeen: true,
                hideTyping: true
            };

            const STORY_PATTERNS = [
                'storiesupdateseenmutation',
                'polarisstoriesseenmutation',
                'usepolarisstoriesv3seenmutation',
                'reelmediaseen',
                'storiesupdateseen',
                'seenstoriesupdatemutation',
                'polarisapireelseenmutation',
                'xdt_mark_story_reel_seen',
                '26997980659837802',
                'polarisapiforcestoryseenmutation',
                'xdt_api__v1__stories__reel__seen',
                '9647304595318258',
                'api/v1/stories/reel/seen',
                'stories/reel/seen',
                'mark_story_seen',
                'update_seen_for_reel',
                'reel_seen',
                'stories_update_seen',
                'mark_story_read'
            ];

            const SEEN_PATTERNS = [
                'mark_read',
                'mark_seen',
                'thread_seen',
                'directmarkasseen',
                'markasseen',
                'directthreadmarkitemsseen',
                'polarisdirectmarkasseenmutation',
                'directseenmutation',
                'usepolarismarkthreadseenmutation',
                'useigdmarkthreadasreadmutation'
            ];

            const TYPING_PATTERNS = [
                'indicate_activity',
                'typing_indicator',
                'activity_indicator',
                'is_typing',
                'direct_v2/threads/broadcast/typing',
                'direct_v2/threads/typing',
                'sendtypingindicator',
                'send_typing_indicator',
                'typing_on',
                'is_composing',
                'update_activity_status',
                'activity_status'
            ];

            function isDirectSendEndpoint(url) {
                const u = String(url || '').toLowerCase();
                return u.includes('broadcast/text') ||
                       u.includes('broadcast/media') ||
                       u.includes('threads/broadcast') ||
                       u.includes('action=send_message');
            }

            async function decodeBody(body) {
                if (!body) return '';
                try {
                    if (typeof body === 'string') return body;
                    if (typeof URLSearchParams !== 'undefined' && body instanceof URLSearchParams) {
                        return body.toString();
                    }
                    if (typeof FormData !== 'undefined' && body instanceof FormData) {
                        const parts = [];
                        for (const [k, v] of body.entries()) {
                            parts.push(k + '=' + (typeof v === 'string' ? v : ''));
                        }
                        return parts.join('&');
                    }
                    if (typeof Blob !== 'undefined' && body instanceof Blob) {
                        return await body.text();
                    }
                    if (body instanceof ArrayBuffer) {
                        return new TextDecoder().decode(body);
                    }
                    if (ArrayBuffer.isView(body)) {
                        return new TextDecoder().decode(body);
                    }
                } catch (_) {}
                return '';
            }

            function matchesAny(combined, patterns) {
                for (let i = 0; i < patterns.length; i++) {
                    if (combined.includes(patterns[i])) return true;
                }
                return false;
            }

            function checkBlock(url, bodyStr) {
                if (isDirectSendEndpoint(url)) return null;
                const s = window.__focusgramGhostSettings;
                if (!s || !s.enabled) return null;

                const combined = (String(bodyStr || '') + ' ' + String(url || '')).toLowerCase();
                if (s.hideStorySeen && matchesAny(combined, STORY_PATTERNS)) return 'IG_STORY';
                if (s.hideDirectSeen && matchesAny(combined, SEEN_PATTERNS)) return 'IG_SEEN';
                if (s.hideTyping && matchesAny(combined, TYPING_PATTERNS)) return 'IG_TYPING';
                return null;
            }

            function createGhostPayload(blockType, url, bodyStr) {
                const combined = (String(bodyStr || '') + ' ' + String(url || '')).toLowerCase();
                const isGraphQL = String(url || '').includes('/graphql') ||
                                  combined.includes('fb_api_req_friendly_name') ||
                                  combined.includes('doc_id');

                if (!isGraphQL) {
                    return { status: 'ok', blocked: blockType };
                }

                if (blockType === 'IG_STORY') {
                    if (combined.includes('xdt_api__v1__stories__reel__seen') ||
                        combined.includes('polarisapiforcestoryseenmutation') ||
                        combined.includes('9647304595318258')) {
                        return {
                            data: {
                                xdt_api__v1__stories__reel__seen: {
                                    __typename: 'XDTEmptyRecord'
                                }
                            }
                        };
                    }
                    return {
                        data: {
                            xdt_mark_story_reel_seen: {
                                __typename: 'XDTMarkSeenResponse'
                            }
                        }
                    };
                }

                return { data: {} };
            }

            // --- Ghostify Focus & Visibility Spoofing ---
            function shouldSpoofVisibility() {
                const s = window.__focusgramGhostSettings;
                if (!s || !s.enabled) return null;
                const path = String(window.location?.pathname || '').toLowerCase();

                if (s.hideStorySeen && path.startsWith('/stories/')) {
                    return 'unfocused';
                }
                if (s.hideDirectSeen && (path === '/direct/' || path.startsWith('/direct/'))) {
                    return 'unfocused';
                }
                return null;
            }

            try {
                const originalHasFocus = document.hasFocus ? document.hasFocus.bind(document) : () => true;
                Object.defineProperty(document, 'hasFocus', {
                    value: function() {
                        const spoof = shouldSpoofVisibility();
                        if (spoof === 'unfocused') return false;
                        return originalHasFocus();
                    },
                    configurable: true
                });

                const originalVisibilityState = Object.getOwnPropertyDescriptor(Document.prototype, 'visibilityState');
                Object.defineProperty(document, 'visibilityState', {
                    get: function() {
                        const spoof = shouldSpoofVisibility();
                        if (spoof === 'unfocused') return 'visible';
                        return originalVisibilityState && originalVisibilityState.get ?
                            originalVisibilityState.get.call(document) : 'visible';
                    },
                    configurable: true
                });
            } catch (err) {
                console.warn('[FocusGram Ghost] Focus hook notice:', err);
            }

            // --- Hook window.fetch ---
            const originalFetch = window.fetch;
            window.fetch = async function(input, init) {
                const url = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                let rawBody = init && init.body !== undefined ? init.body : null;

                if (!rawBody && typeof Request !== 'undefined' && input instanceof Request) {
                    try {
                        rawBody = await input.clone().text();
                    } catch (_) {}
                }

                const bodyStr = await decodeBody(rawBody);
                const blockType = checkBlock(url, bodyStr);

                if (blockType) {
                    console.log('[FocusGram Ghost] Hooked ' + blockType + ' fetch request: ' + url);
                    const payload = createGhostPayload(blockType, url, bodyStr);
                    return new Response(JSON.stringify(payload), {
                        status: 200,
                        headers: { 'Content-Type': 'application/json' }
                    });
                }

                return originalFetch.apply(this, arguments);
            };

            // --- Hook navigator.sendBeacon ---
            const originalBeacon = navigator.sendBeacon;
            if (typeof originalBeacon === 'function') {
                navigator.sendBeacon = function(url, data) {
                    const urlStr = String(url || '');
                    let dataStr = '';
                    try {
                        if (typeof data === 'string') dataStr = data;
                        else if (typeof URLSearchParams !== 'undefined' && data instanceof URLSearchParams) dataStr = data.toString();
                        else if (typeof FormData !== 'undefined' && data instanceof FormData) {
                            for (const [k, v] of data.entries()) dataStr += k + '=' + v + '&';
                        }
                    } catch (_) {}

                    const blockType = checkBlock(urlStr, dataStr);
                    if (blockType) {
                        console.log('[FocusGram Ghost] Hooked ' + blockType + ' sendBeacon: ' + urlStr);
                        return true;
                    }
                    return originalBeacon.apply(this, arguments);
                };
            }

            // --- Hook XMLHttpRequest ---
            const originalOpen = XMLHttpRequest.prototype.open;
            const originalSend = XMLHttpRequest.prototype.send;

            XMLHttpRequest.prototype.open = function(method, url, ...rest) {
                this._url = url || '';
                return originalOpen.call(this, method, url, ...rest);
            };

            XMLHttpRequest.prototype.send = function(data) {
                const url = this._url || '';
                let dataStr = '';
                try {
                    if (typeof data === 'string') dataStr = data;
                    else if (typeof URLSearchParams !== 'undefined' && data instanceof URLSearchParams) dataStr = data.toString();
                    else if (typeof FormData !== 'undefined' && data instanceof FormData) {
                        for (const [k, v] of data.entries()) dataStr += k + '=' + v + '&';
                    }
                } catch (_) {}

                const blockType = checkBlock(url, dataStr);
                if (blockType) {
                    console.log('[FocusGram Ghost] Hooked ' + blockType + ' XHR request: ' + url);
                    const payload = createGhostPayload(blockType, url, dataStr);
                    Object.defineProperty(this, 'status', { value: 200, writable: true });
                    Object.defineProperty(this, 'readyState', { value: 4, writable: true });
                    Object.defineProperty(this, 'responseText', { value: JSON.stringify(payload), writable: true });
                    setTimeout(() => {
                        this.dispatchEvent(new Event('readystatechange'));
                        this.dispatchEvent(new Event('load'));
                    }, 10);
                    return;
                }

                return originalSend.apply(this, arguments);
            };

            // In-page SPA Navigation helper
            window.__focusgramNavigate = function(path) {
                if (!path) return;
                try {
                    let target = null;
                    if (path === '/') {
                        target = document.querySelector('a[href="/"][role="link"]') ||
                                 document.querySelector('a[href="/"]') ||
                                 document.querySelector('svg[aria-label="Home"]')?.closest('a');
                    } else if (path.includes('explore')) {
                        target = document.querySelector('a[href*="/explore"]') ||
                                 document.querySelector('svg[aria-label="Search"]')?.closest('a');
                    } else if (path.includes('reel')) {
                        target = document.querySelector('a[href*="/reels"]') ||
                                 document.querySelector('svg[aria-label="Reels"]')?.closest('a');
                    } else if (path.includes('direct')) {
                        target = document.querySelector('a[href*="/direct"]') ||
                                 document.querySelector('svg[aria-label="Messages"]')?.closest('a');
                    } else {
                        target = document.querySelector(\`a[href="\${path}"]\`) ||
                                 document.querySelector(\`a[href="\${path}/"]\`);
                    }

                    if (target) {
                        target.click();
                        return;
                    }

                    if (window.history && typeof window.history.pushState === 'function') {
                        window.history.pushState(null, '', path);
                        window.dispatchEvent(new PopStateEvent('popstate', { state: null }));
                        return;
                    }
                } catch (e) {
                    console.warn('[FocusGram] Navigation helper fallback:', e);
                }
                window.location.href = path;
            };

            window.__focusgramOpenProfile = function() {
                try {
                    const profileLink = document.querySelector('a[href*="/profile/"]') ||
                                        document.querySelector('a[role="link"]:has(img[alt*="profile" i])') ||
                                        document.querySelector('a[role="link"]:has(img[alt*="\'s profile picture" i])') ||
                                        document.querySelector('nav a[href^="/"]:last-child') ||
                                        document.querySelector('div[role="navigation"] a[href^="/"]:last-child');
                    if (profileLink) {
                        const href = profileLink.getAttribute('href');
                        if (href && !href.includes('accounts/edit')) {
                            profileLink.click();
                            return;
                        }
                    }

                    const username = window._sharedData?.config?.viewer?.username ||
                                     window.__initialData?.data?.viewer?.username;
                    if (username) {
                        window.__focusgramNavigate(\`/\${username}/\`);
                        return;
                    }
                } catch (e) {
                    console.warn('[FocusGram] OpenProfile fallback:', e);
                }
                window.__focusgramNavigate('/');
            };

            // Hook History API in main world context so Instagram React SPA route changes are detected
            const _origPushState = window.history.pushState;
            window.history.pushState = function() {
                const res = _origPushState.apply(this, arguments);
                try {
                    window.dispatchEvent(new CustomEvent('focusgram:urlchange', { detail: window.location.href }));
                } catch (_) {}
                return res;
            };

            const _origReplaceState = window.history.replaceState;
            window.history.replaceState = function() {
                const res = _origReplaceState.apply(this, arguments);
                try {
                    window.dispatchEvent(new CustomEvent('focusgram:urlchange', { detail: window.location.href }));
                } catch (_) {}
                return res;
            };

            window.addEventListener('popstate', function() {
                try {
                    window.dispatchEvent(new CustomEvent('focusgram:urlchange', { detail: window.location.href }));
                } catch (_) {}
            });

            window.addEventListener('hashchange', function() {
                try {
                    window.dispatchEvent(new CustomEvent('focusgram:urlchange', { detail: window.location.href }));
                } catch (_) {}
            });
        })();
        `;
        (document.head || document.documentElement).appendChild(script);
    }

    function syncGhostSettings() {
        const updateScript = document.createElement('script');
        updateScript.textContent = `
            if (window.__focusgramGhostSettings) {
                window.__focusgramGhostSettings.enabled = ${currentSettings.ghostModeEnabled};
                window.__focusgramGhostSettings.hideStorySeen = ${currentSettings.ghostHideStorySeen};
                window.__focusgramGhostSettings.hideDirectSeen = ${currentSettings.ghostHideDirectSeen};
                window.__focusgramGhostSettings.hideTyping = ${currentSettings.ghostHideTypingIndicator};
            }
        `;
        (document.head || document.documentElement).appendChild(updateScript);
        updateScript.remove();
    }

    // --- 2. BOTTOM NAVBAR TRIMMING & DYNAMIC MARGINS ---
    const STYLE_ID = 'focusgram-ui-guardrails-style';

    function updateGuardrailsCSS() {
        let css = `
            /* Senior Blueprint: Hide Instagram's official bottom navigation bar so only FocusGram's native island appears */
            [data-fg-hide],
            div[data-focusgram-hidden-bottom-bar="true"],
            div[role="tablist"]:not([role="dialog"] div[role="tablist"]),
            body > div > div > [role="tablist"] {
                display: none !important;
                visibility: hidden !important;
                height: 0 !important;
                min-height: 0 !important;
                max-height: 0 !important;
                overflow: hidden !important;
                opacity: 0 !important;
                pointer-events: none !important;
            }

            /* Route-driven body padding: Only applied when island mode is NORMAL (feed, explore, profile, inbox) */
            html[data-fg-island="NORMAL"] body,
            html[data-fg-island="NORMAL"] #react-root,
            html[data-fg-island="NORMAL"] main {
                padding-bottom: 96px !important;
                box-sizing: border-box !important;
            }

            /* When on Reels (FLOATING), DMs/Comments (HIDDEN), or keyboard typing, remove padding completely */
            html[data-fg-island="FLOATING"] body,
            html[data-fg-island="FLOATING"] #react-root,
            html[data-fg-island="FLOATING"] main,
            html[data-fg-island="HIDDEN"] body,
            html[data-fg-island="HIDDEN"] #react-root,
            html[data-fg-island="HIDDEN"] main,
            body.focusgram-modal-open,
            body.focusgram-typing,
            body.focusgram-direct,
            body:has([role="dialog"]),
            body:has(input:focus),
            body:has(textarea:focus),
            body:has(div[contenteditable="true"]:focus) {
                padding-bottom: 0px !important;
            }

            /* Reels layout: Ensure full viewport height without distortion or bottom cut-off */
            html[data-fg-island="FLOATING"] main {
                height: 100% !important;
                max-height: 100% !important;
            }

            /* Elevate Reels bottom metadata / caption container above FocusGram's floating island */
            html[data-fg-island="FLOATING"] [data-fg-elevated="true"] {
                bottom: 76px !important;
            }
        `;

        if (currentSettings.noStories) {
            css += `
                [aria-label*="Stories"],
                [aria-label*="stories"],
                [role="list"]:has([aria-label*="tory"]),
                [role="listbox"]:has([aria-label*="tory"]),
                section > div > div[style*="overflow-x"] {
                    display: none !important;
                }
            `;
        }

        if (currentSettings.noReels) {
            css += `
                a[href*="/reel"],
                a[href*="/reels"],
                div:has(> a[href*="/reel"]) {
                    display: none !important;
                }
            `;
        }

        if (currentSettings.blurExplore) {
            css += `
                main a[href*="/p/"] img,
                main a[href*="/reel/"] img,
                div[style*="grid"] img {
                    filter: blur(24px) !important;
                    transition: filter 0.2s ease-in-out !important;
                }
                main a[href*="/p/"] img:active,
                main a[href*="/reel/"] img:active {
                    filter: none !important;
                }
            `;
        }

        if (currentSettings.hideLikeCounts) {
            css += `
                a[href*="/liked_by/"],
                section a[href*="/liked_by/"],
                span:has(> a[href*="/liked_by/"]) {
                    display: none !important;
                }
            `;
        }

        if (currentSettings.hideFollowerCounts) {
            css += `
                a[href*="/followers/"] span,
                a[href*="/following/"] span {
                    opacity: 0 !important;
                    pointer-events: none !important;
                }
            `;
        }

        if (currentSettings.hideShopTab) {
            css += `
                a[href*="/shop"],
                a[href*="/shopping"] {
                    display: none !important;
                }
            `;
        }

        let styleEl = document.getElementById(STYLE_ID);
        if (!styleEl) {
            styleEl = document.createElement('style');
            styleEl.id = STYLE_ID;
            styleEl.type = 'text/css';
            const head = document.head || document.documentElement;
            if (head) head.appendChild(styleEl);
        }
        styleEl.textContent = css;
    }

    // --- 2. BOTTOM NAVBAR TRIMMING (Senior Blueprint) ---
    function hideNav() {
        try {
            // Find Instagram's bottom navigation bar by walking up from Home/Explore links to the fixed bottom container
            const homeTargets = document.querySelectorAll('a[href="/"], a[href="/?variant=home"], svg[aria-label="Home"], a[href*="/explore/"], a[href*="/direct/inbox"]');
            for (let i = 0; i < homeTargets.length; i++) {
                let el = homeTargets[i].closest('a') || homeTargets[i];
                while (el && el !== document.body && el !== document.documentElement) {
                    if (el.getAttribute('role') === 'dialog' || el.tagName === 'ARTICLE' || el.tagName === 'FORM') {
                        break;
                    }
                    const cs = window.getComputedStyle(el);
                    const rect = el.getBoundingClientRect();
                    const isFixedOrSticky = cs.position === 'fixed' || cs.position === 'sticky';
                    const isDockedAtBottom = (cs.bottom && parseInt(cs.bottom, 10) <= 2) ||
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
            }

            // Also tag any role="tablist", nav, or footer docked at bottom
            document.querySelectorAll('[role="tablist"], nav, footer, div[role="navigation"]').forEach(el => {
                if (!el.closest('[role="dialog"]') && !el.closest('article') && !el.closest('form')) {
                    const cs = window.getComputedStyle(el);
                    const rect = el.getBoundingClientRect();
                    const isFixedOrSticky = cs.position === 'fixed' || cs.position === 'sticky';
                    const isDockedAtBottom = (cs.bottom && parseInt(cs.bottom, 10) <= 2) ||
                                             (rect.height > 28 && rect.height < 120 && rect.bottom >= (window.innerHeight - 8));
                    if (isFixedOrSticky && isDockedAtBottom && el.querySelector('a[href*="/explore"], a[href="/"], svg[aria-label="Home"]')) {
                        el.setAttribute('data-fg-hide', '1');
                        el.setAttribute('data-focusgram-hidden-bottom-bar', 'true');
                        el.style.setProperty('display', 'none', 'important');
                        el.style.setProperty('visibility', 'hidden', 'important');
                        el.style.setProperty('height', '0px', 'important');
                        el.style.setProperty('pointer-events', 'none', 'important');
                    }
                }
            });
        } catch (_) {}
    }

    // Continuously observe DOM mutations to hide bottom navigation immediately
    try {
        new MutationObserver(() => {
            hideNav();
        }).observe(document.documentElement || document, { childList: true, subtree: true });
    } catch (_) {}

    // --- 3. REELS CAPTION ELEVATION & DOM OBSERVERS ---
    function elevateReelsCaptions() {
        if (document.documentElement.getAttribute('data-fg-island') !== 'FLOATING') return;
        try {
            // Find author profile links in reel videos
            const authors = document.querySelectorAll('main a[role="link"]:has(img[alt*="profile" i]), main a[href^="/"][role="link"]:has(span)');
            for (let i = 0; i < authors.length; i++) {
                const author = authors[i];
                const reelContainer = author.closest('article') || author.closest('section') || author.closest('[role="presentation"]');
                if (!reelContainer) continue;

                let el = author.parentElement;
                while (el && el !== reelContainer) {
                    const cs = window.getComputedStyle(el);
                    if (cs.position === 'absolute' && (parseInt(cs.bottom, 10) <= 24 || cs.bottom === '0px')) {
                        if (!el.getAttribute('data-fg-elevated')) {
                            el.setAttribute('data-fg-elevated', 'true');
                            el.style.setProperty('bottom', '76px', 'important');
                        }
                        break;
                    }
                    el = el.parentElement;
                }
            }
        } catch (_) {}
    }

    function setupDomObservers() {
        hideNav();
        checkStateAndNotify();

        const observer = new MutationObserver(() => {
            hideNav();
            checkStateAndNotify();
        });

        const root = document.body || document.documentElement;
        if (root) {
            observer.observe(root, { childList: true, subtree: true });
        }
    }

    // --- 4. SPA URL & MODAL WATCHER ---
    let lastReportedUrl = window.location.href;
    let lastModalState = false;
    let lastInputFocused = false;

    function isAuthRoute(path) {
        return path === '/accounts/' ||
               path === '/accounts' ||
               path.startsWith('/accounts/login') ||
               path.startsWith('/accounts/emailsignup') ||
               path.startsWith('/accounts/signup') ||
               path.startsWith('/accounts/password') ||
               path.startsWith('/accounts/onetap') ||
               path.startsWith('/accounts/account_recovery') ||
               path.startsWith('/login') ||
               path.startsWith('/signup');
    }

    function isAnyModalOpen() {
        // 1. Comments sheet detection (directly matches the active "Add a comment..." composer)
        const hasCommentInput = !!document.querySelector(
            'textarea[placeholder*="comment" i], ' +
            'input[placeholder*="comment" i], ' +
            '[placeholder*="Add a comment" i], ' +
            '[aria-label*="Add a comment" i], ' +
            '[aria-placeholder*="comment" i]'
        );
        if (hasCommentInput) return true;

        // 2. Explicit ARIA dialogs or modals (Comments sheet, share sheet, options)
        const dialog = document.querySelector('div[role="dialog"], section[role="dialog"], [aria-modal="true"]');
        if (dialog) return true;

        // 3. Share sheet / Share drawer
        const shareSheet = document.querySelector(
            'div:has(input[placeholder*="Search" i]):has(svg[aria-label="Share"]), ' +
            'div:has(span):has(svg[aria-label*="Send" i]):has(input[placeholder*="Search" i]), ' +
            'div:has(> div > input[placeholder*="Search" i]):has(div[role="button"])'
        );
        if (shareSheet && shareSheet.closest('div[style*="fixed"], div[style*="absolute"]')) return true;

        // 4. Action sheets / confirmation menus (e.g. 3-dots menu on post/reel)
        const actionSheet = document.querySelector('div[role="presentation"] > div > div[role="menu"], div[role="presentation"] > div > div:has(button[tabindex="0"])');
        if (actionSheet) return true;

        // 5. Route-driven modal states
        const path = window.location.pathname.toLowerCase();
        if (isAuthRoute(path) || path.includes('/comments') || path.startsWith('/direct/t/') || path.startsWith('/stories/') || path.startsWith('/create')) {
            return true;
        }

        return false;
    }

    function updateIslandModeFromUrl() {
        const path = window.location.pathname.toLowerCase();
        const modalActive = isAnyModalOpen();
        let mode = 'NORMAL';
        if (isAuthRoute(path) || path.startsWith('/direct/t/') || path.includes('/comments') || path.startsWith('/stories/') || path.startsWith('/create') || modalActive) {
            mode = 'HIDDEN';
        } else if (path.startsWith('/reels') || path.startsWith('/reel/')) {
            mode = 'FLOATING';
        }
        document.documentElement.setAttribute('data-fg-island', mode);
        return mode;
    }

    function checkStateAndNotify() {
        const currentUrl = window.location.href;
        if (currentUrl !== lastReportedUrl) {
            lastReportedUrl = currentUrl;
            console.log('[FocusGram] Detected URL transition:', currentUrl);
            postToNative({ action: 'urlChanged', url: currentUrl });
        }

        const lower = currentUrl.toLowerCase();
        const isInDirect = lower.includes('/direct/t/');
        const modalActive = isAnyModalOpen();

        updateIslandModeFromUrl();
        elevateReelsCaptions();

        if (modalActive !== lastModalState) {
            lastModalState = modalActive;
            if (modalActive) {
                document.body.classList.add('focusgram-modal-open');
            } else {
                document.body.classList.remove('focusgram-modal-open');
            }
            postToNative({ action: 'modalOrChatActive', active: modalActive });
        }

        if (isInDirect) {
            document.body.classList.add('focusgram-direct');
        } else {
            document.body.classList.remove('focusgram-direct');
        }

        // Active element focus check (cleans up any orphan typing state on navigation)
        const active = document.activeElement;
        const isInput = !!(active && (
            active.tagName === 'INPUT' ||
            active.tagName === 'TEXTAREA' ||
            active.isContentEditable ||
            active.getAttribute('role') === 'textbox'
        ));

        if (isInput !== lastInputFocused) {
            lastInputFocused = isInput;
            if (isInput) {
                document.body.classList.add('focusgram-typing');
            } else {
                document.body.classList.remove('focusgram-typing');
            }
            postToNative({ action: 'inputFocused', focused: isInput });
        }
    }

    window.addEventListener('focusgram:urlchange', () => {
        checkStateAndNotify();
        setTimeout(checkStateAndNotify, 100);
        setTimeout(checkStateAndNotify, 300);
        hideNav();
    });

    window.addEventListener('popstate', () => {
        checkStateAndNotify();
        setTimeout(checkStateAndNotify, 100);
        setTimeout(checkStateAndNotify, 300);
        hideNav();
    });

    window.addEventListener('hashchange', () => {
        checkStateAndNotify();
        setTimeout(checkStateAndNotify, 100);
        hideNav();
    });

    // 250ms periodic heartbeat guarantees zero missed state changes during React renders
    setInterval(() => {
        checkStateAndNotify();
        hideNav();
    }, 250);

    // --- 4. INPUT FOCUS / TYPING BRIDGE ---
    function setupInputFocusListeners() {
        document.addEventListener('focusin', (e) => {
            const target = e.target;
            if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable || target.getAttribute('role') === 'textbox')) {
                document.body.classList.add('focusgram-typing');
                postToNative({ action: 'inputFocused', focused: true });
            }
        }, true);

        document.addEventListener('focusout', (e) => {
            const target = e.target;
            if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable || target.getAttribute('role') === 'textbox')) {
                setTimeout(() => {
                    const active = document.activeElement;
                    if (!active || (active.tagName !== 'INPUT' && active.tagName !== 'TEXTAREA' && !active.isContentEditable && active.getAttribute('role') !== 'textbox')) {
                        document.body.classList.remove('focusgram-typing');
                        postToNative({ action: 'inputFocused', focused: false });
                    }
                }, 100);
            }
        }, true);
    }

    // --- 5. SENIOR BLUEPRINT NATIVE MESSAGING & SPA NAVIGATION ---
    const ROUTES = {
        home: "/",
        search: "/explore/",
        reels: "/reels/",
        dm: "/direct/inbox/"
    };

    function performNavClick(target) {
        if (!target) return;
        console.log('[FocusGram] Navigating to:', target);

        if (target === 'profile') {
            const profileLink = document.querySelector('a[href*="/profile/"]') ||
                                document.querySelector('[data-fg-hide] a:has(img)') ||
                                document.querySelector('[data-fg-hide] a[href^="/"]:last-child') ||
                                document.querySelector('nav a[href^="/"]:last-child');
            if (profileLink) {
                profileLink.click();
                return;
            }
            if (window.__focusgramOpenProfile) {
                window.__focusgramOpenProfile();
                return;
            }
            window.location.assign('/');
            return;
        }

        const path = ROUTES[target] || target;
        const link = document.querySelector(`a[href="${path}"]`) ||
                     document.querySelector(`a[href="${path.replace(/\/$/, '')}"]`) ||
                     document.querySelector(`a[href="${path}/"]`) ||
                     document.querySelector(`[data-fg-hide] a[href="${path}"]`) ||
                     document.querySelector(`[data-fg-hide] a[href="${path.replace(/\/$/, '')}"]`);

        if (link) {
            link.click();
        } else if (window.__focusgramNavigate) {
            window.__focusgramNavigate(path);
        } else {
            window.location.assign(path);
        }
    }

    function handleIncomingMessage(msg) {
        if (!msg) return;

        if (msg.action === 'nav' || msg.nav) {
            performNavClick(msg.nav || msg.target);
        } else if (msg.action === 'setIslandMode' || msg.island) {
            const mode = msg.island || msg.mode;
            if (mode) {
                console.log('[FocusGram] Set Island Mode:', mode);
                document.documentElement.setAttribute('data-fg-island', mode);
            }
        } else if (msg.action === 'updateSettings' && msg.settings) {
            Object.assign(currentSettings, msg.settings);
            updateGuardrailsCSS();
            syncGhostSettings();
        } else if (msg.action === 'navigateSpa' && msg.path) {
            performNavClick(msg.path);
        } else if (msg.action === 'openProfileSpa') {
            performNavClick('profile');
        }
    }

    // Direct native port connection with GeckoView native messaging
    let nativePort = null;
    function connectNativePort() {
        try {
            if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.connectNative) {
                nativePort = browser.runtime.connectNative("focusgram");
                nativePort.onMessage.addListener(handleIncomingMessage);
                nativePort.onDisconnect.addListener(() => {
                    nativePort = null;
                    setTimeout(connectNativePort, 3000);
                });
            }
        } catch (e) {
            console.warn('[FocusGram] connectNative error:', e);
        }
    }

    function postToNative(message) {
        if (nativePort) {
            try {
                nativePort.postMessage(message);
                return;
            } catch (_) {}
        }
        if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.sendMessage) {
            try {
                browser.runtime.sendMessage(message);
            } catch (_) {}
        }
    }

    // Listen for relayed messages from background script as fallback
    if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.onMessage) {
        browser.runtime.onMessage.addListener(handleIncomingMessage);
    }

    // Connect native port
    connectNativePort();

    // Query initial settings from background
    if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.sendMessage) {
        browser.runtime.sendMessage({ action: 'getSettings' }, (response) => {
            if (response && response.settings) {
                Object.assign(currentSettings, response.settings);
                updateGuardrailsCSS();
                syncGhostSettings();
            }
        });
    }

    // --- 6. INITIALIZE ---
    updateIslandModeFromUrl();
    setupGhostModeInterceptors();
    updateGuardrailsCSS();
    hideNav();
    setupDomObservers();
    setupInputFocusListeners();

})();
