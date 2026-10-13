/**
 * FocusGram In-Page Controller & Stealth Privacy Engine
 * 
 * Injected as a WebExtension content script across instagram.com at document_start.
 * Features:
 *  1. Stealth Mode: Main-world fetch and XHR interceptors for Story views, DM Read Receipts, and Typing indicators.
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

    // --- Immediate anti-flash style injected at document_start ---
    try {
        const immediateStyle = document.createElement('style');
        immediateStyle.id = 'focusgram-immediate-nav-hide';
        immediateStyle.textContent = `
            [data-fg-hide],
            [data-fg-hide="1"],
            div[data-focusgram-hidden-bottom-bar="true"],
            nav:has(a[href*="/explore/"]),
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

    // --- 1. GHOST MODE INTERCEPTION ENGINE ---
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
                const path = String(window.location?.pathname || '').toLowerCase();
                if (path.startsWith('/accounts') || path.startsWith('/login')) return null;
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

            // --- Focus & Visibility Spoofing ---
            function shouldSpoofVisibility() {
                const s = window.__focusgramGhostSettings;
                if (!s || !s.enabled) return null;
                const path = String(window.location?.pathname || '').toLowerCase();

                if (s.hideStorySeen && path.startsWith('/stories/')) {
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
                        target = document.querySelector('svg[aria-label="Messages"]')?.closest('a') ||
                                 document.querySelector('svg[aria-label="Direct"]')?.closest('a') ||
                                 document.querySelector('svg[aria-label="Chats"]')?.closest('a') ||
                                 document.querySelector('a[href*="/direct/inbox"]') ||
                                 document.querySelector('a[href*="/direct"]');
                    } else {
                        target = document.querySelector(\`a[href="\${path}"]\`) ||
                                 document.querySelector(\`a[href="\${path}/"]\`);
                    }

                    if (target) {
                        target.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: window }));
                        target.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, view: window }));
                        target.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                        target.click();
                        return;
                    }

                    if (path.includes('direct') || path.startsWith('/direct')) {
                        window.location.assign('https://www.instagram.com/direct/inbox/');
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

            // Intercept navigation to Suggested Accounts (/explore/people) to prevent trap when backing out of Favorites
            document.addEventListener('click', function(e) {
                const targetLink = e.target.closest('a[href*="/explore/people"]');
                if (targetLink) {
                    e.preventDefault();
                    e.stopPropagation();
                    window.__focusgramNavigate('/');
                }
            }, true);

            function checkSuggestedTrap() {
                if (window.location.pathname.includes('/explore/people')) {
                    window.__focusgramNavigate('/');
                }
            }

            // Hook History API in main world context so Instagram React SPA route changes are detected
            const _origPushState = window.history.pushState;
            window.history.pushState = function() {
                const res = _origPushState.apply(this, arguments);
                try {
                    window.dispatchEvent(new CustomEvent('focusgram:urlchange', { detail: window.location.href }));
                    checkSuggestedTrap();
                } catch (_) {}
                return res;
            };

            const _origReplaceState = window.history.replaceState;
            window.history.replaceState = function() {
                const res = _origReplaceState.apply(this, arguments);
                try {
                    window.dispatchEvent(new CustomEvent('focusgram:urlchange', { detail: window.location.href }));
                    checkSuggestedTrap();
                } catch (_) {}
                return res;
            };

            window.addEventListener('popstate', function() {
                try {
                    window.dispatchEvent(new CustomEvent('focusgram:urlchange', { detail: window.location.href }));
                    checkSuggestedTrap();
                } catch (_) {}
            });

            window.addEventListener('hashchange', function() {
                try {
                    window.dispatchEvent(new CustomEvent('focusgram:urlchange', { detail: window.location.href }));
                    checkSuggestedTrap();
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
            /* Hide Instagram's official bottom navigation bar so only FocusGram's native island appears */
            [data-fg-hide],
            div[data-focusgram-hidden-bottom-bar="true"] {
                display: none !important;
                visibility: hidden !important;
                height: 0 !important;
                min-height: 0 !important;
                max-height: 0 !important;
                overflow: hidden !important;
                opacity: 0 !important;
                pointer-events: none !important;
            }

            /* Safety guardrail: Core application containers must ALWAYS remain visible and full-height */
            html, body, #react-root, main {
                min-height: 100% !important;
            }

            /* Route-driven body padding: Only applied when island mode is NORMAL (feed, explore, profile) */
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

            /* ==========================================================================
               3. INSTAGRAM REELS (100% INSPIRATION MATCH)
               - Full viewport edge-to-edge container (100dvh).
               - Video element covers 100% without letterboxing or black bars.
               - Floating navigation bar sits below.
               - FULL CAPTION, AUDIO BANNER, USER PROFILE, and ACTION BUTTONS sit ABOVE it.
               ========================================================================== */
            :root {
                --fg-reel-bottom-offset: 16px;
                --fg-reel-scrubber-offset: 2px;
            }

            /* Reels fixed scroller: Remove Instagram's 45px bottom navigation reservation */
            [data-fg-reel-scroller],
            div[style*="position: fixed"][style*="overflow-y: scroll"],
            div[style*="position: fixed"][style*="overflow-y: auto"],
            div[style*="position: fixed"][style*="overflow: scroll"] {
                bottom: 0px !important;
                height: 100% !important;
                max-height: none !important;
            }

            /* Container full-bleed lock for Reels */
            html[data-fg-island="FLOATING"],
            html[data-fg-island="FLOATING"] body,
            html[data-fg-island="FLOATING"] #react-root,
            html[data-fg-island="FLOATING"] main {
                height: 100% !important;
                min-height: 100% !important;
                max-height: 100% !important;
                width: 100% !important;
                margin: 0 !important;
                padding: 0 !important;
                overflow: hidden !important;
                background-color: #000000 !important;
            }

            /* Each Reel Card & Video Container */
            [data-fg-reel-card],
            [data-fg-reel],
            html[data-fg-island="FLOATING"] main section,
            html[data-fg-island="FLOATING"] main article,
            html[data-fg-island="FLOATING"] div[role="region"] {
                height: 100% !important;
                min-height: 100% !important;
                max-height: 100% !important;
                width: 100% !important;
                position: relative !important;
                box-sizing: border-box !important;
                background-color: #000000 !important;
            }

            /* Video scaling: seamless edge-to-edge presentation */
            [data-fg-reel-card] video,
            [data-fg-reel] video,
            html[data-fg-island="FLOATING"] video {
                width: 100% !important;
                height: 100% !important;
                object-fit: cover !important;
                position: absolute !important;
                top: 0 !important;
                left: 0 !important;
                bottom: 0 !important;
                right: 0 !important;
            }

            /* Bottom metadata wrapper: LIFTED CLEANLY ABOVE THE FLOATING NAVBAR */
            [data-fg-reel-overlay] {
                bottom: var(--fg-reel-bottom-offset, 88px) !important;
                margin-bottom: 0px !important;
                z-index: 25 !important;
                pointer-events: auto !important;
            }

            /* Right-hand interaction rail (Like, Comment, Share, Bookmark, More, Audio disc) */
            [data-fg-reel-actions] {
                bottom: var(--fg-reel-bottom-offset, 88px) !important;
                z-index: 25 !important;
                pointer-events: auto !important;
            }

            /* Video Scrubber Progress Line: Sits right above the floating navbar */
            [data-fg-reel-scrubber],
            html[data-fg-island="FLOATING"] div[role="progressbar"],
            html[data-fg-island="FLOATING"] div[style*="bottom: 0"]:has(> div[style*="transform"]),
            html[data-fg-island="FLOATING"] div[style*="bottom: 0px"]:has(> div[style*="width"]) {
                bottom: var(--fg-reel-scrubber-offset, 76px) !important;
                z-index: 26 !important;
            }

            /* Comments bottom sheet & dialogs: Docked cleanly to viewport bottom above native navbar */
            html[data-fg-island="FLOATING"] div[role="dialog"],
            html[data-fg-island="FLOATING"] div[aria-modal="true"] {
                bottom: 0px !important;
                max-height: 80% !important;
            }

            form:has(textarea[placeholder*="comment" i]),
            form:has(input[placeholder*="comment" i]),
            div[role="dialog"] form,
            div[aria-modal="true"] form {
                padding-bottom: 8px !important;
                margin-bottom: 0px !important;
            }

            /* Full caption readability: Uncut, unclipped, readable */
            [data-fg-reel-caption],
            html[data-fg-island="FLOATING"] span[dir="auto"],
            html[data-fg-island="FLOATING"] div[dir="auto"] {
                color: #ffffff !important;
                text-shadow: 0 1px 4px rgba(0, 0, 0, 0.85) !important;
                word-break: break-word !important;
                max-height: 140px !important;
                overflow-y: auto !important;
                line-height: 1.35 !important;
            }

            /* Audio Banner / Track info */
            a[href*="/audio/"],
            a[href*="/audio/"] * {
                display: inline-flex !important;
                align-items: center !important;
                visibility: visible !important;
                opacity: 1 !important;
                color: #ffffff !important;
                font-weight: 600 !important;
                font-size: 13px !important;
                text-shadow: 0 1px 3px rgba(0, 0, 0, 0.85) !important;
            }


            /* Feed Reveal Guardrail: Blurred posts on home feed */
            html[data-fg-feed] article:not([data-fg-reveal]) img,
            html[data-fg-feed] article:not([data-fg-reveal]) video,
            html[data-fg-feed] article:not([data-fg-reveal]) div._aagv,
            html[data-fg-feed] article:not([data-fg-reveal]) div[role="presentation"] img,
            html[data-fg-feed] article:not([data-fg-reveal]) div[role="presentation"] video {
                filter: blur(28px) !important;
                transition: filter 0.25s ease !important;
            }

            html[data-fg-feed] article[data-fg-reveal] img,
            html[data-fg-feed] article[data-fg-reveal] video,
            html[data-fg-feed] article[data-fg-reveal] div._aagv,
            html[data-fg-feed] article[data-fg-reveal] div[role="presentation"] img,
            html[data-fg-feed] article[data-fg-reveal] div[role="presentation"] video {
                filter: none !important;
            }

            /* Ensure comments sheet / input composer sits flush above keyboard without artificial gap */
            [role="dialog"] form,
            [role="dialog"] [style*="position: fixed"],
            [role="dialog"] [style*="position: absolute"],
            div._ac7o,
            div._ac76,
            div:has(> input[placeholder*="comment" i]),
            div:has(> textarea[placeholder*="comment" i]) {
                bottom: 0px !important;
            }
        `;

        if (currentSettings.noStories) {
            css += `
                div[data-pagelet="story_tray"],
                div[data-pagelet*="story"],
                [data-pagelet="story_tray"],
                [data-pagelet*="story_tray"],
                div:has(> div > div > div[aria-label*="Story by"]),
                div:has(> div > div > div[aria-label*="Your story"]),
                [aria-label*="Stories"],
                [aria-label*="stories"],
                [role="list"]:has([aria-label*="tory"]),
                [role="listbox"]:has([aria-label*="tory"]),
                section > div > div[style*="overflow-x"] {
                    display: none !important;
                    visibility: hidden !important;
                    height: 0 !important;
                    min-height: 0 !important;
                    max-height: 0 !important;
                    overflow: hidden !important;
                    margin: 0 !important;
                    padding: 0 !important;
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
                .fg-unblurred,
                .fg-unblurred img,
                a.fg-unblurred img {
                    filter: none !important;
                }
            `;
            if (currentSettings.requireLongPress || currentSettings.unblurMode === 'hold') {
                css += `
                    main a[href*="/p/"] img:active,
                    main a[href*="/reel/"] img:active {
                        filter: none !important;
                    }
                `;
            }
        }

        if (currentSettings.blurReels) {
            css += `
                main div:has(> video),
                article div:has(> video) {
                    filter: blur(24px) !important;
                    transition: filter 0.2s ease-in-out !important;
                }
                .fg-unblurred,
                .fg-unblurred video,
                div.fg-unblurred video {
                    filter: none !important;
                }
            `;
            if (currentSettings.requireLongPress || currentSettings.unblurMode === 'hold') {
                css += `
                    main div:has(> video):active,
                    article div:has(> video):active {
                        filter: none !important;
                    }
                `;
            }
        }

        if (currentSettings.hideLikeCounts) {
            css += `
                a[href*="/liked_by/"],
                section a[href*="/liked_by/"],
                span:has(> a[href*="/liked_by/"]),
                article section:has(svg[aria-label="Like"]) + div span,
                article section:has(svg[aria-label="Activity Feed"]) + div span,
                article div:has(> a[href*="/liked_by/"]) {
                    display: none !important;
                }
            `;
        }

        if (currentSettings.hideFollowerCounts) {
            css += `
                a[href*="/followers/"] span,
                a[href*="/following/"] span,
                header section ul li span,
                ul li:has(a[href*="/followers/"]) span,
                ul li:has(a[href*="/following/"]) span {
                    opacity: 0 !important;
                    visibility: hidden !important;
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

        if (currentSettings.grayscale) {
            css += `
                html {
                    filter: grayscale(100%) !important;
                    -webkit-filter: grayscale(100%) !important;
                }
            `;
        }

        if (currentSettings.hideSuggestedPosts) {
            css += `
                div[data-testid="suggested-users"] {
                    display: none !important;
                }
                article:has(a[href*="/explore/people/"]),
                [data-fg-suggested-hidden="true"] {
                    height: 1px !important;
                    min-height: 1px !important;
                    max-height: 1px !important;
                    overflow: hidden !important;
                    opacity: 0 !important;
                    pointer-events: none !important;
                    margin: 0 !important;
                    padding: 0 !important;
                    border: none !important;
                }
            `;
        }

        if (currentSettings.minimalMode) {
            css += `
                article footer section + div,
                div[data-testid="suggested-users"] {
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

    // --- 2. BOTTOM NAVBAR TRIMMING ---
    function hideNav() {
        try {
            if (window.location.pathname.startsWith('/direct')) return;
            // Find Instagram's bottom navigation bar by walking up from Home/Explore links to the fixed bottom container
            // Exclude direct inbox links so share sheets and chats are NEVER mistaken for bottom nav
            const homeTargets = document.querySelectorAll('a[href="/"], a[href="/?variant=home"], svg[aria-label="Home"], a[href*="/explore/"]');
            for (let i = 0; i < homeTargets.length; i++) {
                let el = homeTargets[i].closest('a') || homeTargets[i];
                while (el && el !== document.body && el !== document.documentElement) {
                    if (el.getAttribute('role') === 'dialog' || el.getAttribute('aria-modal') === 'true' || el.tagName === 'ARTICLE' || el.tagName === 'FORM' || el.tagName === 'MAIN') {
                        break;
                    }
                    const cs = window.getComputedStyle(el);
                    const rect = el.getBoundingClientRect();
                    const isFixedOrSticky = cs.position === 'fixed' || cs.position === 'sticky';
                    // A bottom nav bar is strictly <= 75px in height and docked right at the bottom edge
                    const isDockedAtBottom = (rect.height > 28 && rect.height <= 75) &&
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
            }

            // Also tag any role="tablist", nav, or footer docked at bottom (strictly <= 75px height)
            document.querySelectorAll('[role="tablist"], nav, footer, div[role="navigation"]').forEach(el => {
                if (!el.closest('[role="dialog"]') && !el.closest('[aria-modal="true"]') && !el.closest('article') && !el.closest('form') && !el.closest('main')) {
                    const cs = window.getComputedStyle(el);
                    const rect = el.getBoundingClientRect();
                    const isFixedOrSticky = cs.position === 'fixed' || cs.position === 'sticky';
                    const isDockedAtBottom = (rect.height > 28 && rect.height <= 75) &&
                                             (rect.bottom >= (window.innerHeight - 8)) &&
                                             (rect.top >= (window.innerHeight - 85));
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
        let hideNavDebounceTimer = null;
        const hideNavDebounce = 100; // ms
        
        new MutationObserver(() => {
            clearTimeout(hideNavDebounceTimer);
            hideNavDebounceTimer = setTimeout(() => {
                hideNav();
            }, hideNavDebounce);
        }).observe(document.documentElement || document, { childList: true, subtree: true });
    } catch (_) {}




    // --- 3. REELS LAYOUT SCROLLER & OVERLAY POSITIONING (100% INSPIRATION MATCH) ---
    function fixReels() {
        const path = window.location.pathname.toLowerCase();
        if (!path.startsWith('/reels') && !path.startsWith('/reel')) return;
        const vh = window.innerHeight;
        if (!vh) return;

        // 0. Stretch the Reels fixed viewport scroll container
        // Instagram reserves ~45-48px for its bottom navigation by default.
        // Expanding this single container to bottom: 0; height: 100% eliminates the 45px gap without breaking layout.
        const firstVideo = document.querySelector('video');
        if (firstVideo) {
            let el = firstVideo.parentElement;
            while (el && el !== document.body && el !== document.documentElement) {
                const cs = window.getComputedStyle(el);
                if (cs.position === 'fixed' || cs.overflowY === 'scroll' || cs.overflowY === 'auto') {
                    if (!el.hasAttribute('data-fg-reel-scroller')) {
                        el.setAttribute('data-fg-reel-scroller', '1');
                    }
                    el.style.setProperty('bottom', '0px', 'important');
                    el.style.setProperty('height', '100%', 'important');
                    el.style.setProperty('max-height', 'none', 'important');
                    break;
                }
                el = el.parentElement;
            }
        }

        // 1. Identify each reel card and ensure full bleed
        document.querySelectorAll('video').forEach(v => {
            // Ensure video object-fit cover
            v.style.setProperty('object-fit', 'cover', 'important');
            v.style.setProperty('width', '100%', 'important');
            v.style.setProperty('height', '100%', 'important');

            // Find closest reel card container
            let card = v.closest('article') || v.closest('section') || v.closest('[role="region"]') || v.parentElement;
            while (card && card !== document.body && card !== document.documentElement) {
                if (card.tagName === 'ARTICLE' || card.tagName === 'SECTION' || card.parentElement?.tagName === 'MAIN') {
                    break;
                }
                const r = card.getBoundingClientRect();
                if (r.height > vh * 0.5) {
                    break;
                }
                card = card.parentElement;
            }

            if (!card) return;
            card.setAttribute('data-fg-reel-card', '1');
            card.setAttribute('data-fg-reel', '1');

            // 2. Identify the bottom overlay containing user profile, caption, and audio banner
            let overlay = null;
            const audioLink = card.querySelector('a[href*="/audio/"]');
            if (audioLink) {
                let p = audioLink.parentElement;
                while (p && p !== card && p !== document.body) {
                    const hasAuthorOrCaption = p.querySelector('a[role="link"]:not([href*="/audio/"])') || 
                                               p.querySelector('span[dir="auto"]');
                    if (hasAuthorOrCaption) {
                        overlay = p;
                        let candidate = p;
                        while (candidate && candidate.parentElement && candidate.parentElement !== card) {
                            const cs = window.getComputedStyle(candidate);
                            if (cs.position === 'absolute' || cs.position === 'fixed') {
                                overlay = candidate;
                                break;
                            }
                            if (candidate.parentElement.querySelector('video') || candidate.parentElement.querySelector('svg[aria-label="Like"]')) {
                                overlay = candidate;
                                break;
                            }
                            candidate = candidate.parentElement;
                        }
                        break;
                    }
                    p = p.parentElement;
                }
                if (!overlay) overlay = audioLink.closest('div[style*="bottom"]') || audioLink.parentElement;
            }

            if (!overlay) {
                const captions = card.querySelectorAll('span[dir="auto"], div[dir="auto"]');
                for (let i = 0; i < captions.length; i++) {
                    const c = captions[i];
                    if (c.textContent && c.textContent.trim().length > 0) {
                        let p = c.closest('div');
                        while (p && p !== card && p !== document.body) {
                            const cs = window.getComputedStyle(p);
                            if (cs.position === 'absolute' || cs.position === 'fixed' || p.parentElement === card) {
                                overlay = p;
                                break;
                            }
                            p = p.parentElement;
                        }
                        if (overlay) break;
                    }
                }
            }

            if (overlay && overlay !== card) {
                overlay.setAttribute('data-fg-reel-overlay', '1');
                overlay.style.setProperty('bottom', 'var(--fg-reel-bottom-offset, 16px)', 'important');
                overlay.style.removeProperty('padding-bottom');
                overlay.style.setProperty('margin-bottom', '0px', 'important');
                overlay.style.setProperty('z-index', '25', 'important');
                overlay.style.setProperty('pointer-events', 'auto', 'important');
            }

            // 3. Identify right action buttons rail (Like, Comment, Share, More)
            const likeIcon = card.querySelector('svg[aria-label="Like"], svg[aria-label="Unlike"], svg[aria-label="Liked"]');
            if (likeIcon) {
                let actionRail = likeIcon.closest('div[role="button"]') || likeIcon.parentElement;
                while (actionRail && actionRail !== card && actionRail !== document.body) {
                    const cs = window.getComputedStyle(actionRail);
                    if (cs.position === 'absolute' || cs.position === 'fixed' || (actionRail.parentElement === card)) {
                        break;
                    }
                    if (actionRail.parentElement && (actionRail.parentElement.querySelector('a[href*="/audio/"]') || actionRail.parentElement.querySelector('video'))) {
                        break;
                    }
                    actionRail = actionRail.parentElement;
                }
                if (actionRail && actionRail !== card) {
                    actionRail.setAttribute('data-fg-reel-actions', '1');
                    actionRail.style.setProperty('bottom', 'var(--fg-reel-bottom-offset, 16px)', 'important');
                    actionRail.style.setProperty('z-index', '25', 'important');
                    actionRail.style.setProperty('pointer-events', 'auto', 'important');
                }
            }

            // 4. Identify progress bar / scrubber
            const progress = card.querySelector('[role="progressbar"], div[style*="transform"][style*="scaleX"]');
            if (progress) {
                const scrubber = progress.closest('div[style*="bottom"]') || progress.parentElement;
                if (scrubber && scrubber !== card) {
                    scrubber.setAttribute('data-fg-reel-scrubber', '1');
                    scrubber.style.setProperty('bottom', 'var(--fg-reel-scrubber-offset, 2px)', 'important');
                    scrubber.style.setProperty('z-index', '26', 'important');
                }
            }
        });
    }

    // Attach to video playback, navigation events, time updates, and scrolling across document
    document.addEventListener('play', fixReels, true);
    document.addEventListener('playing', fixReels, true);
    document.addEventListener('loadeddata', fixReels, true);
    document.addEventListener('canplay', fixReels, true);
    window.addEventListener('popstate', fixReels);
    window.addEventListener('focusgram:urlchange', fixReels);

    // Capture scroll events anywhere in document
    document.addEventListener('scroll', () => {
        fixReels();
    }, { passive: true, capture: true });

    let reelTimeThrottle = null;
    document.addEventListener('timeupdate', () => {
        if (!reelTimeThrottle) {
            reelTimeThrottle = setTimeout(() => {
                reelTimeThrottle = null;
                fixReels();
            }, 250);
        }
    }, true);

    function setupDomObservers() {
        if (!document.getElementById(STYLE_ID)) {
            updateGuardrailsCSS();
        }
        hideNav();
        checkStateAndNotify();

        const observer = new MutationObserver(() => {
            if (!document.getElementById(STYLE_ID)) {
                updateGuardrailsCSS();
            }
            hideNav();
            checkStateAndNotify();
        });

        const root = document.body || document.documentElement;
        if (root) {
            observer.observe(root, { childList: true, subtree: true });
        }

        // --- DEDICATED MODAL DETECTION OBSERVER (Senior Blueprint) ---
        // Watch for modal-related attribute changes and structure modifications
        let lastModalCheckTime = 0;
        const modalDebounceMs = 50;

        const modalObserver = new MutationObserver(() => {
            const now = Date.now();
            if (now - lastModalCheckTime >= modalDebounceMs) {
                lastModalCheckTime = now;
                checkStateAndNotify();
            }
        });

        if (root) {
            modalObserver.observe(root, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ["role", "aria-hidden", "aria-modal", "style"],
                attributeOldValue: false
            });
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

    // --- SHARE SHEET DETECTION ---
    // Track when share/modal API was called (scroll lock is primary indicator for comments)
    let shareSheetActive = false;
    let shareSheetCheckInterval = null;
    let lastShareSheetCheckTime = 0;

    function updateShareSheetState() {
        const now = Date.now();
        if (now - lastShareSheetCheckTime < 150) return;
        lastShareSheetCheckTime = now;

        // Efficient modal lookup without DOM thrashing
        const shareModals = Array.from(document.querySelectorAll('[role="dialog"], [aria-modal="true"], div[style*="fixed"]')).filter(el => {
            if (el.getAttribute('data-fg-hide')) return false;
            const cs = window.getComputedStyle(el);
            const rect = el.getBoundingClientRect();
            return (cs.position === 'fixed' || cs.position === 'absolute') && 
                   rect.width >= window.innerWidth * 0.8 &&
                   rect.height >= window.innerHeight * 0.35 &&
                   (el.querySelector('input[placeholder*="Search" i], [aria-label*="Close" i], svg[aria-label*="Close" i]') ||
                    (el.innerText && (el.innerText.includes('Share') || el.innerText.includes('Copy link'))));
        });
        
        const wasActive = shareSheetActive;
        shareSheetActive = shareModals.length > 0;
        
        if (wasActive !== shareSheetActive) {
            console.log('[FocusGram] Share sheet state changed:', shareSheetActive);
            checkStateAndNotify();
        }
    }

    function startShareSheetMonitoring() {
        if (shareSheetCheckInterval) return;
        shareSheetCheckInterval = setInterval(() => {
            updateShareSheetState();
        }, 250);
    }

    function stopShareSheetMonitoring() {
        if (shareSheetCheckInterval) {
            clearInterval(shareSheetCheckInterval);
            shareSheetCheckInterval = null;
        }
    }

    // Intercept fetch to detect share/DM API calls and start monitoring
    const originalFetch = window.fetch.bind(window);
    window.fetch = function(...args) {
        const url = typeof args[0] === 'string' ? args[0] : (args[0]?.url || '');
        
        if (url.includes('share') || url.includes('direct_share') || url.includes('direct/share')) {
            console.log('[FocusGram] Share API detected:', url);
            shareSheetActive = true;
            checkStateAndNotify();
            startShareSheetMonitoring();
        }
        
        return originalFetch.apply(this, args);
    };

    // --- 6. FEED POST REVEAL ENGINE (Tap & 350ms Hold) ---
    const HOLD_MS = 350, MOVE_TOL = 10;
    const root = document.documentElement;
    let mode = "hold"; // "tap" | "hold"
    let timer = 0, sx = 0, sy = 0, swallowUntil = 0;

    function updateFeedFlag() {
      root.toggleAttribute("data-fg-feed", location.pathname === "/");
    }
    updateFeedFlag();
    new MutationObserver(updateFeedFlag).observe(document, { childList: true, subtree: true });
    addEventListener("popstate", updateFeedFlag);

    const blurredPost = el =>
      root.hasAttribute("data-fg-feed") && el.closest("article:not([data-fg-reveal])");

    addEventListener("pointerdown", e => {
      if (mode !== "hold") return;
      const post = blurredPost(e.target);
      if (!post) return;
      sx = e.clientX; sy = e.clientY;
      timer = setTimeout(() => {
        post.setAttribute("data-fg-reveal", "1");
        swallowUntil = Date.now() + 400; // stop the release from clicking through
      }, HOLD_MS);
    }, true);

    addEventListener("pointermove", e => {
      if (timer && Math.hypot(e.clientX - sx, e.clientY - sy) > MOVE_TOL) {
        clearTimeout(timer); timer = 0;
      }
    }, true);

    ["pointerup", "pointercancel"].forEach(t =>
      addEventListener(t, () => { clearTimeout(timer); timer = 0; }, true));

    addEventListener("click", e => {
      if (Date.now() < swallowUntil) { e.preventDefault(); e.stopPropagation(); return; }
      const post = blurredPost(e.target);
      if (!post) return;
      e.preventDefault(); e.stopPropagation(); // a blurred post never gets a real click
      if (mode === "tap") post.setAttribute("data-fg-reveal", "1");
    }, true);

    addEventListener("contextmenu", e => {
      if (blurredPost(e.target)) e.preventDefault();
    }, true);

    // --- In-Page Reels Metadata Extractor ---
    let lastExtractedReelUrl = '';
    function checkAndExtractReelMetadata() {
        const url = window.location.href;
        if (!url.includes('/reel/') && !url.includes('/reels/')) return;
        if (lastExtractedReelUrl === url) return;

        let title = '';
        let thumbnailUrl = '';
        let author = '';

        const ogTitle = document.querySelector('meta[property="og:title"]');
        const ogImage = document.querySelector('meta[property="og:image"]');
        if (ogTitle && ogTitle.content) title = ogTitle.content.trim();
        if (ogImage && ogImage.content) thumbnailUrl = ogImage.content.trim();

        if (!title && document.title) {
            title = document.title.replace(' on Instagram', '').replace('• Instagram photos and videos', '').trim();
        }
        if (title) {
            const authorMatch = title.match(/^([^(@:]+)(?:\s*\((@[^)]+)\))?/);
            if (authorMatch) {
                author = (authorMatch[2] || authorMatch[1]).trim();
            }
        }

        if (!thumbnailUrl) {
            const videoEl = document.querySelector('video[poster]') || document.querySelector('video');
            if (videoEl && videoEl.getAttribute('poster')) {
                thumbnailUrl = videoEl.getAttribute('poster');
            }
        }
        if (!author) {
            const authorEl = document.querySelector('a[role="link"][tabindex="0"] span') ||
                             document.querySelector('header a') ||
                             document.querySelector('article header a');
            if (authorEl && authorEl.textContent) {
                author = authorEl.textContent.trim();
            }
        }

        if (title || thumbnailUrl) {
            lastExtractedReelUrl = url;
            postToNative({
                action: 'reelViewed',
                type: 'reelViewed',
                url: url,
                title: title || 'Instagram Reel',
                author: author || 'Instagram Creator',
                thumbnailUrl: thumbnailUrl || ''
            });
            console.log('[FocusGram] In-page extracted reel metadata:', url);
        }
    }

    function isBottomSheetOpen() {
        try {
            const vh = window.innerHeight;
            const vw = window.innerWidth;
            const bodyText = document.body ? (document.body.innerText || '') : '';

            // 1. Unconditional check for note reply sheets
            if (bodyText.includes('shared a note') || bodyText.includes('shared notes')) {
                return true;
            }

            // 2. Unconditional check for "Send message" composer pill when in Direct inbox
            if (window.location.pathname.startsWith('/direct') && bodyText.includes('Send message')) {
                return true;
            }

            // 3. Check for bottom sheets docked at viewport bottom
            const candidates = document.querySelectorAll('div[style*="fixed"], div[style*="absolute"], body > div, [role="dialog"], [aria-modal="true"], section');
            for (let i = 0; i < candidates.length; i++) {
                const el = candidates[i];
                if (el.getAttribute('data-fg-hide') || el.hasAttribute('data-focusgram-hidden-bottom-bar')) continue;
                if (el.id === 'react-root' || el.tagName === 'MAIN' || el.tagName === 'BODY' || el.tagName === 'HTML') continue;
                
                const cs = window.getComputedStyle(el);
                if (cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity || '1') < 0.1) continue;
                if (cs.position !== 'fixed' && cs.position !== 'absolute') continue;

                const rect = el.getBoundingClientRect();
                // Docked at bottom, taller than 100px (not a navbar), doesn't start at top of screen
                if (rect.bottom >= vh - 25 && rect.top >= 60 && rect.height >= 100 && rect.width >= vw * 0.70) {
                    return true;
                }
            }

            // 4. Any visible text input located in the lower half of viewport
            const inputs = document.querySelectorAll('input:not([type="hidden"]):not([type="checkbox"]):not([type="radio"]):not([type="file"]), textarea, [contenteditable="true"], [role="textbox"]');
            for (let i = 0; i < inputs.length; i++) {
                const inp = inputs[i];
                if (inp.offsetParent === null && inp.offsetWidth === 0 && inp.offsetHeight === 0) continue;
                const rect = inp.getBoundingClientRect();
                if (rect.width > 0 && rect.height > 0) {
                    if (rect.top > vh * 0.40 && rect.bottom <= vh + 80) {
                        return true;
                    }
                }
            }
        } catch (_) {}
        return false;
    }

    function isAnyModalOpen() {
        const path = window.location.pathname.toLowerCase();

        // 1. Route-based detection (DM chats, stories, auth, create)
        if (isAuthRoute(path) || path.includes('/comments') || path.startsWith('/direct/t/') || path.startsWith('/stories/') || path.startsWith('/create')) {
            return true;
        }

        // 2. Bottom input or sheet (Note reply, comments sheet, composer)
        if (isBottomSheetOpen()) {
            return true;
        }

        // 3. Direct DOM structure checks for Comments modal/sheet & Share sheets
        try {
            const headings = document.querySelectorAll('[role="heading"], h1, h2, h3, div[aria-level="1"]');
            for (let i = 0; i < headings.length; i++) {
                const hText = (headings[i].textContent || '').trim().toLowerCase();
                if (hText === 'comments' || hText === 'share' || hText === 'share to') {
                    return true;
                }
            }
            if (document.querySelector('div._ac76, div._ac78, div._ac7o, input[placeholder*="comment" i], textarea[placeholder*="comment" i], [aria-label*="Add a comment" i], textarea[placeholder*="Add a comment"], input[aria-label*="comment" i]')) {
                return true;
            }
        } catch (_) {}

        // 4. Scan rendered text for Comments, Share, Note sheets, and Create modals
        try {
            const bodyText = document.body ? (document.body.innerText || '') : '';
            if (bodyText) {
                if (bodyText.includes('shared a note') || bodyText.includes('shared notes')) {
                    return true;
                }

                // Comments modal detection: "Comments" AND ("Add a comment..." / "Add a comment" / "Add comment")
                const hasComments = bodyText.includes('Comments') || /Comments\b/i.test(bodyText);
                const hasCommentInput = bodyText.includes('Add a comment') ||
                                        bodyText.includes('Add a comment…') ||
                                        bodyText.includes('Add comment') ||
                                        !!document.querySelector('input[placeholder*="comment" i], textarea[placeholder*="comment" i], [aria-label*="Add a comment" i]');
                if (hasComments && hasCommentInput) {
                    return true;
                }

                // Share modal detection: ("Share" OR "Share to") AND ("Search" OR "Copy link" OR "Copy Link")
                const hasShare = bodyText.includes('Share to') || /\bShare\b/i.test(bodyText);
                const hasShareAction = bodyText.includes('Copy link') ||
                                       bodyText.includes('Copy Link') ||
                                       (hasShare && bodyText.includes('Search'));
                if (hasShare && hasShareAction) {
                    return true;
                }

                // Create (+) modal: Post & Story options
                if ((bodyText.includes('Post') && bodyText.includes('Story')) && document.querySelector('form input[type="file"], [role="dialog"] input[type="file"]')) {
                    return true;
                }
            }
        } catch (_) {}

        // 5. Check for role="dialog", role="menu", or aria-modal="true" elements that are visible
        const dialogs = document.querySelectorAll('[role="dialog"], [role="menu"], [aria-modal="true"]');
        for (let i = 0; i < dialogs.length; i++) {
            const d = dialogs[i];
            const cs = window.getComputedStyle(d);
            const ariaHidden = d.getAttribute('aria-hidden');
            if (cs.display !== 'none' && cs.visibility !== 'hidden' && ariaHidden !== 'true') {
                return true;
            }
        }

        // 6. Share sheet API detection
        if (shareSheetActive) {
            return true;
        }

        // 7. Scroll lock on body (exclude Direct inbox so inbox is not mistaken for a modal)
        if (!path.startsWith('/direct')) {
            const bodyOverflow = getComputedStyle(document.body).overflow;
            if (bodyOverflow === "hidden" || bodyOverflow === "hidden auto") {
                return true;
            }
        }

        return false;
    }

    function updateIslandModeFromUrl() {
        const path = window.location.pathname.toLowerCase();
        let mode = 'NORMAL';
        if (isAuthRoute(path) || path.startsWith('/direct/t/') || path.startsWith('/stories/') || path.startsWith('/create')) {
            mode = 'HIDDEN';
        } else if (path.startsWith('/reels') || path.startsWith('/reel/')) {
            mode = 'FLOATING';
        } else if (isAnyModalOpen()) {
            mode = 'HIDDEN';
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
        checkAndExtractReelMetadata();

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
        updateShareSheetState();
        fixReels();
    }, 250);

    // --- CREATE (+) DROPDOWN "POST" & "STORY" HANDLER ---
    document.addEventListener('click', (e) => {
        try {
            const target = e.target;
            if (!target) return;
            const text = (target.textContent || '').trim();
            if (text === 'Post' || text === 'Story') {
                const container = target.closest('form') || target.closest('[role="dialog"]') || target.closest('[role="menu"]') || document;
                const fileInput = container.querySelector('input[type="file"]');
                if (fileInput && fileInput !== target) {
                    fileInput.click();
                }
            }
        } catch (_) {}
    }, true);

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
        if (!path) return;
        const link = document.querySelector(`a[href="${path}"]`) ||
                     document.querySelector(`a[href="${path.replace(/\/$/, '')}"]`) ||
                     document.querySelector(`a[href="${path}/"]`) ||
                     document.querySelector(`[data-fg-hide] a[href="${path}"]`) ||
                     document.querySelector(`[data-fg-hide] a[href="${path.replace(/\/$/, '')}"]`);

        if (link) {
            link.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: window }));
            link.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, view: window }));
            link.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
            link.click();
        } else if (window.__focusgramNavigate) {
            window.__focusgramNavigate(path);
        } else {
            window.location.assign(path);
        }
    }

    function handleIncomingMessage(msg) {
        if (!msg) return;

        if (msg.revealMode) {
            mode = msg.revealMode;
            return;
        }

        if (msg.action === 'revealMode' && msg.revealMode) {
            mode = msg.revealMode;
            return;
        }

        if (msg.action === 'nav' || msg.nav) {
            performNavClick(msg.nav || msg.target);
            return;
        } else if (msg.action === 'setIslandMode' || msg.island) {
            const modeVal = msg.island || msg.mode;
            if (modeVal) {
                console.log('[FocusGram] Set Island Mode:', modeVal);
                document.documentElement.setAttribute('data-fg-island', modeVal);
            }
            return;
        } else if (msg.action === 'updateSettings' && msg.settings) {
            Object.assign(currentSettings, msg.settings);
            if (msg.settings.unblurMode) {
                mode = msg.settings.unblurMode;
            }
            updateGuardrailsCSS();
            syncGhostSettings();
            return;
        } else if (msg.action === 'navigateSpa' && msg.path) {
            performNavClick(msg.path);
            return;
        } else if (msg.action === 'openProfileSpa') {
            performNavClick('profile');
            return;
        } else if (msg.action === 'triggerCreate') {
            const createBtn = document.querySelector('svg[aria-label="New post"]')?.closest('a, button, div[role="button"]') ||
                              document.querySelector('a[href*="/create/"]') ||
                              document.querySelector('svg[aria-label="Create"]')?.closest('a, button, div[role="button"]') ||
                              document.querySelector('svg[aria-label="New Post"]')?.closest('a, button, div[role="button"]');
            if (createBtn) createBtn.click();
            return;
        }
    }

    // Listen for host postMessage events (e.g. from UserScriptManager)
    window.addEventListener('message', (event) => {
        if (!event || !event.data) return;
        if (event.data.source === 'FOCUSGRAM_HOST' && event.data.type === 'FOCUSGRAM_UPDATE') {
            const config = event.data.config || {};
            const ghost = event.data.ghost || {};
            Object.assign(currentSettings, {
                noAds: config.noAds !== undefined ? config.noAds : currentSettings.noAds,
                noStories: config.hideStories !== undefined ? config.hideStories : currentSettings.noStories,
                noReels: config.hideReels !== undefined ? config.hideReels : currentSettings.noReels,
                blurExplore: config.blurExplore !== undefined ? config.blurExplore : currentSettings.blurExplore,
                hideLikeCounts: config.hideLikeCounts !== undefined ? config.hideLikeCounts : currentSettings.hideLikeCounts,
                hideFollowerCounts: config.hideFollowerCounts !== undefined ? config.hideFollowerCounts : currentSettings.hideFollowerCounts,
                hideSuggestedPosts: config.hideSuggestedPosts !== undefined ? config.hideSuggestedPosts : currentSettings.hideSuggestedPosts,
                hideShopTab: config.hideShopTab !== undefined ? config.hideShopTab : currentSettings.hideShopTab,
                grayscale: config.grayscale !== undefined ? config.grayscale : currentSettings.grayscale,
                minimalMode: config.minimalMode !== undefined ? config.minimalMode : currentSettings.minimalMode,
                ghostModeEnabled: ghost.enabled !== undefined ? ghost.enabled : currentSettings.ghostModeEnabled,
                ghostHideStorySeen: ghost.igStory !== undefined ? ghost.igStory : currentSettings.ghostHideStorySeen,
                ghostHideDirectSeen: ghost.igSeen !== undefined ? ghost.igSeen : currentSettings.ghostHideDirectSeen,
                ghostHideTypingIndicator: ghost.igTyping !== undefined ? ghost.igTyping : currentSettings.ghostHideTypingIndicator
            });
            updateGuardrailsCSS();
            syncGhostSettings();
        }
    });

    // Forward Reels metadata events to native host
    window.addEventListener('focusgram:reelmetadata', (event) => {
        if (event && event.detail) {
            postToNative({
                action: 'reelViewed',
                type: 'reelViewed',
                url: event.detail.url,
                title: event.detail.title,
                author: event.detail.author,
                thumbnailUrl: event.detail.thumbnailUrl
            });
        }
    });

    // Senior Blueprint: Direct content script communication with WebExtension background script
    function postToNative(message) {
        if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.sendMessage) {
            try {
                browser.runtime.sendMessage(message);
            } catch (e) {
                console.warn('[FocusGram] sendMessage error:', e);
            }
        }
    }

    // --- INSTAGRAM THEME DETECTION ---
    // Detect if Instagram is in light or dark mode and notify native
    let lastSentTheme = null;
    
    function detectAndSendTheme() {
        try {
            const htmlElement = document.documentElement;
            let detectedTheme = htmlElement.getAttribute('data-theme') || (document.body && document.body.getAttribute('data-theme'));
            
            // Priority 1: Check computed background color of body (ground truth of rendered pixels)
            if (!detectedTheme && document.body) {
                const bodyBgColor = window.getComputedStyle(document.body).backgroundColor;
                if (bodyBgColor && bodyBgColor.startsWith('rgb')) {
                    const rgbMatch = bodyBgColor.match(/\d+/g);
                    if (rgbMatch && rgbMatch.length >= 3) {
                        const [r, g, b] = rgbMatch.map(Number);
                        const a = rgbMatch.length >= 4 ? parseFloat(rgbMatch[3]) : 1;
                        if (a > 0.05) {
                            const brightness = (r * 299 + g * 587 + b * 114) / 1000;
                            detectedTheme = brightness > 128 ? 'light' : 'dark';
                        }
                    }
                }
            }
            
            // Priority 2: Check computed background color of htmlElement
            if (!detectedTheme && htmlElement) {
                const htmlBgColor = window.getComputedStyle(htmlElement).backgroundColor;
                if (htmlBgColor && htmlBgColor.startsWith('rgb')) {
                    const rgbMatch = htmlBgColor.match(/\d+/g);
                    if (rgbMatch && rgbMatch.length >= 3) {
                        const [r, g, b] = rgbMatch.map(Number);
                        const a = rgbMatch.length >= 4 ? parseFloat(rgbMatch[3]) : 1;
                        if (a > 0.05) {
                            const brightness = (r * 299 + g * 587 + b * 114) / 1000;
                            detectedTheme = brightness > 128 ? 'light' : 'dark';
                        }
                    }
                }
            }

            // Priority 3: Check CSS custom properties defined by Instagram for colors
            if (!detectedTheme) {
                const igPrimary = getComputedStyle(document.documentElement).getPropertyValue('--ig-primary-background');
                if (igPrimary && igPrimary.trim()) {
                    const parts = igPrimary.split(',').map(s => parseInt(s.trim(), 10));
                    if (parts.length >= 3 && !isNaN(parts[0])) {
                        const brightness = (parts[0] * 299 + parts[1] * 587 + parts[2] * 114) / 1000;
                        detectedTheme = brightness > 128 ? 'light' : 'dark';
                    }
                }
            }

            // Priority 4: System media query fallback
            if (!detectedTheme) {
                const computedTheme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
                detectedTheme = computedTheme;
            }
            
            // Only send if theme actually changed
            if (detectedTheme && detectedTheme !== lastSentTheme) {
                lastSentTheme = detectedTheme;
                console.log('[FocusGram] Theme detected:', detectedTheme);
                postToNative({ action: 'themeChanged', theme: detectedTheme });
            }
        } catch (e) {
            console.warn('[FocusGram] Theme detection error:', e);
        }
    }

    // Listen for relayed messages from background script as fallback
    if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.onMessage) {
        browser.runtime.onMessage.addListener(handleIncomingMessage);
    }

    // Query initial settings from background
    if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.sendMessage) {
        browser.runtime.sendMessage({ action: 'getSettings' }, (response) => {
            if (response && response.settings) {
                Object.assign(currentSettings, response.settings);
                if (response.settings.unblurMode) {
                    mode = response.settings.unblurMode;
                }
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
    
    // Detect and notify native of Instagram's theme (light/dark) with continuous polling
    detectAndSendTheme();
    
    // Continuous theme polling every 500ms to catch all theme changes
    setInterval(() => {
        detectAndSendTheme();
    }, 500);
    
    // Watch for theme changes via media query
    try {
        const darkModeMediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
        if (darkModeMediaQuery.addListener) {
            darkModeMediaQuery.addListener(() => {
                setTimeout(detectAndSendTheme, 100);
            });
        } else if (darkModeMediaQuery.addEventListener) {
            darkModeMediaQuery.addEventListener('change', () => {
                setTimeout(detectAndSendTheme, 100);
            });
        }
    } catch (e) {
        console.warn('[FocusGram] Media query listener error:', e);
    }
    
    // Also watch for data-theme attribute changes on html
    const themeObserver = new MutationObserver(() => {
        setTimeout(detectAndSendTheme, 100);
    });
    themeObserver.observe(document.documentElement, {
        attributes: true,
        attributeFilter: ['data-theme', 'class', 'style'],
        subtree: false
    });

    // Watch for body class/style changes that indicate theme changes
    const bodyThemeObserver = new MutationObserver(() => {
        setTimeout(detectAndSendTheme, 100);
    });
    bodyThemeObserver.observe(document.body, {
        attributes: true,
        attributeFilter: ['class', 'style', 'data-theme'],
        subtree: false
    });

    // --- 7. INFINITE SCROLL PRESERVATION ---
    // Instagram web uses IntersectionObserver and scroll sentinels to trigger next batch of feed posts.
    // When ads are hidden or sentinel reaches bottom, synthetic scroll events and clicking "View older posts"
    // ensures smooth, uninterrupted infinite feed scrolling past post 9.
    let lastInfiniteCheck = 0;
    function checkAndTriggerInfiniteScroll() {
        const now = Date.now();
        if (now - lastInfiniteCheck < 400) return;
        lastInfiniteCheck = now;

        try {
            const scrollY = window.scrollY || window.pageYOffset || document.documentElement.scrollTop || 0;
            const windowHeight = window.innerHeight;
            const scrollHeight = document.documentElement.scrollHeight || document.body.scrollHeight || 0;

            if (scrollY + windowHeight >= scrollHeight - 1500) {
                window.dispatchEvent(new Event('scroll'));
                document.dispatchEvent(new Event('scroll'));

                const sentinels = document.querySelectorAll('a, button, [role="button"], div[tabindex="0"]');
                for (let i = 0; i < sentinels.length; i++) {
                    const text = (sentinels[i].textContent || '').trim().toLowerCase();
                    if (text === 'view older posts' || text === 'see older posts' || text === 'load more' || text === 'load older posts') {
                        sentinels[i].click();
                        break;
                    }
                }
            }
        } catch (_) {}
    }

    window.addEventListener('scroll', checkAndTriggerInfiniteScroll, { passive: true });

})();

