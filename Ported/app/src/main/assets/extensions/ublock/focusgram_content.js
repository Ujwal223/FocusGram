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
        blurReels: false,
        blurFeed: false,
        unblurMode: 'tap',
        tapToUnblur: true,
        hideLikeCounts: false,
        hideFollowerCounts: false,
        hideSuggestedPosts: false,
        hideShopTab: false,
        videoDownloadEnabled: true,
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

            window.__fgFilterConfig = {
                blockAds: true,
                blockSponsored: true,
                blockSuggested: false,
                blockVideos: false
            };

            window.addEventListener('message', function(e) {
                if (!e || !e.data) return;
                if (e.data.type === 'GHOSTIFY_SETTINGS_UPDATE' && e.data.settings) {
                    Object.assign(window.__focusgramGhostSettings, e.data.settings);
                }
                if (e.data.type === 'FOCUSGRAM_FILTER_UPDATE' && e.data.config) {
                    Object.assign(window.__fgFilterConfig, e.data.config);
                }
            });

            const AD_KEYWORDS = [
                'sponsored', 'gesponsert', 'sponsorisé', 'sponsorise', 'publicidad',
                'patrocinado', 'sponsorizzato', 'sponsrad', 'paid partnership', 'werbung',
                'anuncio', 'werbeanzeige', 'publicité', 'ad_id', 'ad_tracking', 'sponsor_tags'
            ];

            function textHasAdSignal(value) {
                if (!value) return false;
                const s = String(value).toLowerCase();
                return AD_KEYWORDS.some(kw => s.includes(kw));
            }

            function isAdNode(node) {
                if (!node || typeof node !== 'object') return false;
                const typename = String(node.__typename || '');
                const adText = JSON.stringify({
                    organic_tracking_token: node.organic_tracking_token,
                    sponsor_tags: node.sponsor_tags,
                    social_context: node.social_context,
                    title: node.title,
                    header: node.header,
                    label: node.label,
                    overlay_text: node.overlay_text
                });

                return !!(
                    node.is_ad ||
                    node.is_paid_partnership ||
                    (node.sponsor_tags && node.sponsor_tags.length > 0) ||
                    node.ad_tracking_token ||
                    node.ad_action_link ||
                    node.ad_id ||
                    node.ad_impression_token ||
                    node.ad_metadata ||
                    node.commerciality_status === 'commercial' ||
                    (node.product_type && node.product_type === 'ad') ||
                    (node.ad_header_style && node.ad_header_style !== 'none') ||
                    typename === 'GraphAdStory' ||
                    typename.includes('Ad') ||
                    textHasAdSignal(adText)
                );
            }

            function isSponsoredNode(node) {
                if (!node || typeof node !== 'object') return false;
                return !!(
                    node.is_paid_partnership ||
                    (node.sponsor_tags && node.sponsor_tags.length > 0) ||
                    (node.ad_action_link && node.ad_action_link.href) ||
                    (node.ad_header_style && node.ad_header_style !== 'none') ||
                    textHasAdSignal(JSON.stringify(node.social_context || node.header || node.label || ''))
                );
            }

            function isSuggestedNode(node) {
                if (!node || typeof node !== 'object') return false;
                const typename = String(node.__typename || '');
                const reason = JSON.stringify({
                    reason: node.suggested_reason,
                    social_context: node.social_context,
                    title: node.title,
                    header: node.header,
                    label: node.label
                }).toLowerCase();

                return !!(
                    node.is_suggested ||
                    node.is_suggested_for_you ||
                    node.is_recommendation ||
                    node.suggested_users ||
                    node.suggested_media ||
                    node.suggested_content ||
                    node.recommendation_source ||
                    typename.includes('Suggested') ||
                    typename.includes('Recommendation') ||
                    reason.includes('suggested') ||
                    reason.includes('recommend')
                );
            }

            function shouldFilterNode(node) {
                const config = window.__fgFilterConfig;
                if (!config || !node || typeof node !== 'object') return false;
                if (config.blockAds !== false && isAdNode(node)) return true;
                if (config.blockSponsored !== false && isSponsoredNode(node) && !isAdNode(node)) return true;
                if (config.blockSuggested && isSuggestedNode(node)) return true;
                return false;
            }

            function unwrapNode(item) {
                if (!item || typeof item !== 'object') return item;
                return item.media || item.node || item.post || item;
            }

            function filterList(list) {
                if (!Array.isArray(list)) return list;
                return list.filter(item => {
                    if (!item) return false;
                    const target = unwrapNode(item);
                    return !shouldFilterNode(target);
                });
            }

            function harvestReelFromObj(node) {
                if (!node || typeof node !== 'object') return;
                const code = node.code || node.shortcode;
                if (typeof code === 'string' && code.length >= 4 && !['reels', 'reel', 'audio', 'videos', 'clips'].includes(code.toLowerCase())) {
                    const author = node.user?.username || node.owner?.username || '';
                    let caption = '';
                    if (typeof node.caption === 'string') {
                        caption = node.caption;
                    } else if (node.caption && typeof node.caption.text === 'string') {
                        caption = node.caption.text;
                    } else if (node.edge_media_to_caption?.edges?.[0]?.node?.text) {
                        caption = node.edge_media_to_caption.edges[0].node.text;
                    }
                    if (author || caption) {
                        const metaStr = JSON.stringify({ code, author, caption });
                        try {
                            let cacheEl = document.getElementById('fg-meta-cache');
                            if (!cacheEl) {
                                cacheEl = document.createElement('div');
                                cacheEl.id = 'fg-meta-cache';
                                cacheEl.style.display = 'none';
                                (document.body || document.documentElement).appendChild(cacheEl);
                            }
                            cacheEl.setAttribute('data-reel-' + code, metaStr);
                        } catch (_) {}
                        try {
                            document.dispatchEvent(new CustomEvent('focusgram:reel-meta', {
                                detail: metaStr
                            }));
                        } catch (_) {}
                    }
                }
            }

            function walkAndFilter(obj, visited = new Set()) {
                if (!obj || typeof obj !== 'object' || visited.has(obj)) return;
                visited.add(obj);

                harvestReelFromObj(obj);

                if (Array.isArray(obj)) {
                    for (let i = 0; i < obj.length; i++) {
                        walkAndFilter(obj[i], visited);
                    }
                    return;
                }

                if (obj.edges && Array.isArray(obj.edges)) {
                    obj.edges = filterList(obj.edges);
                }
                if (obj.items && Array.isArray(obj.items)) {
                    obj.items = filterList(obj.items);
                }
                if (obj.feed_items && Array.isArray(obj.feed_items)) {
                    obj.feed_items = filterList(obj.feed_items);
                }
                if (obj.tray && Array.isArray(obj.tray)) {
                    obj.tray = filterList(obj.tray);
                }

                for (const key in obj) {
                    if (Object.prototype.hasOwnProperty.call(obj, key) && key !== '__typename') {
                        const val = obj[key];
                        if (val && typeof val === 'object') {
                            walkAndFilter(val, visited);
                        }
                    }
                }
            }

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

                const response = await originalFetch.apply(this, arguments);

                // Main-World GraphQL, Clips, & Feed Ad Filtering & Reel Meta Harvesting
                if (url.includes('/graphql') || url.includes('/api/v1/feed') || url.includes('/api/v1/clips/') || url.includes('/clips/')) {
                    try {
                        const contentType = response.headers.get('content-type') || '';
                        if (contentType.includes('application/json')) {
                            const cloned = response.clone();
                            const data = await cloned.json();
                            walkAndFilter(data);
                            if (window.__fgFilterConfig && window.__fgFilterConfig.blockAds !== false) {
                                return new Response(JSON.stringify(data), {
                                    status: response.status,
                                    statusText: response.statusText,
                                    headers: response.headers
                                });
                            }
                        }
                    } catch (_) {}
                }

                return response;
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
        const isGhostActive = !!currentSettings.ghostModeEnabled;
        window.postMessage({
            type: "GHOSTIFY_SETTINGS_UPDATE",
            source: "GHOSTIFY_EXTENSION",
            settings: {
                igTyping: isGhostActive && !!currentSettings.ghostHideTypingIndicator,
                igSeen: isGhostActive && !!currentSettings.ghostHideDirectSeen,
                igStory: isGhostActive && !!currentSettings.ghostHideStorySeen
            }
        }, "*");
        window.postMessage({
            type: "FOCUSGRAM_FILTER_UPDATE",
            source: "FOCUSGRAM_EXTENSION",
            config: {
                blockAds: currentSettings.noAds !== false,
                blockSponsored: currentSettings.noAds !== false,
                blockSuggested: !!currentSettings.hideSuggestedPosts
            }
        }, "*");
    }

    window.addEventListener("message", (event) => {
        if (event.data && event.data.type === "GHOSTIFY_SETTINGS_REQUEST") {
            syncGhostSettings();
        }
    });

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

            /* Reels fixed scroller: Ultra-smooth momentum scrolling & snapping */
            [data-fg-reel-scroller],
            div[style*="position: fixed"][style*="overflow-y: scroll"],
            div[style*="position: fixed"][style*="overflow-y: auto"],
            div[style*="position: fixed"][style*="overflow: scroll"] {
                bottom: 0px !important;
                height: 100% !important;
                max-height: none !important;
                scroll-snap-type: y mandatory !important;
                scroll-snap-stop: normal !important;
                overscroll-behavior-y: contain !important;
                -webkit-overflow-scrolling: touch !important;
                touch-action: pan-y !important;
                will-change: scroll-position !important;
                contain: strict !important;
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

            /* Each Reel Card & Video Container: GPU accelerated layer & layout isolation */
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
                scroll-snap-align: start !important;
                scroll-snap-stop: normal !important;
                contain: layout paint !important;
                will-change: transform !important;
                transform: translateZ(0) !important;
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

        if (currentSettings.blurFeed) {
            css += `
                /* Feed Carousel Slides & Single Post Media - ONLY blur leaf media elements, NEVER parent wrappers! */
                html[data-fg-feed] article img:not(header img):not([alt*="profile" i]):not([data-fg-reveal]):not(.fg-unblurred),
                html[data-fg-feed] article video:not([data-fg-reveal]):not(.fg-unblurred),
                html[data-fg-feed] article canvas:not([data-fg-reveal]):not(.fg-unblurred) {
                    filter: blur(28px) !important;
                    transition: filter 0.25s ease-in-out !important;
                }

                /* Preserve feed header avatars unblurred */
                html[data-fg-feed] article header img,
                html[data-fg-feed] article img[alt*="profile" i] {
                    filter: none !important;
                }
            `;
        }

        if (currentSettings.blurExplore) {
            css += `
                main a[href*="/p/"]:not([data-fg-reveal]):not(.fg-unblurred) img,
                main a[href*="/reel/"]:not([data-fg-reveal]):not(.fg-unblurred) img,
                div[style*="grid"]:not([data-fg-reveal]):not(.fg-unblurred) img {
                    filter: blur(24px) !important;
                    transition: filter 0.2s ease-in-out !important;
                }
            `;
        }

        if (currentSettings.blurReels) {
            css += `
                main div:has(> video):not([data-fg-reveal]):not(.fg-unblurred),
                article div:has(> video):not([data-fg-reveal]):not(.fg-unblurred),
                div[data-fg-reel-container]:not([data-fg-reveal]):not(.fg-unblurred) video {
                    filter: blur(24px) !important;
                    transition: filter 0.2s ease-in-out !important;
                }
            `;
        }

        css += `
            [data-fg-reveal],
            [data-fg-reveal] img,
            [data-fg-reveal] video,
            [data-fg-reveal] canvas,
            [data-fg-reveal] div,
            .fg-unblurred,
            .fg-unblurred img,
            .fg-unblurred video,
            .fg-unblurred canvas,
            .fg-unblurred div {
                filter: none !important;
            }
        `;

        if (currentSettings.hideLikeCounts) {
            css += `
                a[href*="/liked_by/"],
                section a[href*="/liked_by/"],
                span:has(> a[href*="/liked_by/"]),
                div:has(> a[href*="/liked_by/"]),
                section:has(svg[aria-label="Like"]) + div:not(:has(form)):not(:has(input)),
                section:has(svg[aria-label="Unlike"]) + div:not(:has(form)):not(:has(input)),
                section:has(svg[aria-label="Activity Feed"]) + div:not(:has(form)):not(:has(input)),
                div[role="button"]:has(svg[aria-label="Like"]) + div span,
                div[role="button"]:has(svg[aria-label="Unlike"]) + div span,
                span[role="button"]:has(svg[aria-label="Like"]) + span,
                span[role="button"]:has(svg[aria-label="Unlike"]) + span,
                div[role="button"]:has(svg[aria-label="Like"]) span,
                div[role="button"]:has(svg[aria-label="Unlike"]) span,
                div:has(> span > svg[aria-label="Like"]) + div span,
                div:has(> span > svg[aria-label="Unlike"]) + div span,
                /* In Reels overlay: the count text right below the like heart icon */
                div:has(> svg[aria-label="Like"]) + div,
                div:has(> svg[aria-label="Unlike"]) + div,
                div:has(> svg[aria-label="Like"]) ~ div,
                div:has(> svg[aria-label="Unlike"]) ~ div {
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




    // --- 3. REELS INTERACTION-BASED COUNTER & HISTORY TRACKER ---
    let lastTrackedReelCode = null;
    let lastTrackedCaption = '';
    let lastTrackedAuthor = '';
    const harvestedReels = new Map();

    function handleReelMetaEvent(raw) {
        try {
            const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
            if (data && data.code) {
                const existing = harvestedReels.get(data.code) || {};
                const updated = {
                    author: data.author || existing.author || '',
                    caption: data.caption || existing.caption || ''
                };
                harvestedReels.set(data.code, updated);

                if (lastTrackedReelCode === data.code) {
                    let changed = false;
                    if (!lastTrackedAuthor && updated.author) {
                        lastTrackedAuthor = updated.author;
                        changed = true;
                    }
                    if (!lastTrackedCaption && updated.caption) {
                        lastTrackedCaption = updated.caption;
                        changed = true;
                    }
                    if (changed) {
                        postToNative({
                            action: 'reelWatched',
                            type: 'meta',
                            code: data.code,
                            url: 'https://www.instagram.com/reel/' + data.code + '/',
                            title: lastTrackedCaption || (lastTrackedAuthor ? ('Reel by @' + lastTrackedAuthor) : ''),
                            caption: lastTrackedCaption,
                            author: lastTrackedAuthor,
                            thumbnail: ''
                        });
                    }
                }
            }
        } catch (_) {}
    }

    document.addEventListener('focusgram:reel-meta', (e) => handleReelMetaEvent(e.detail), true);
    window.addEventListener('focusgram:reel-meta', (e) => handleReelMetaEvent(e.detail), true);

    function findReelCardForVideo(v) {
        if (!v) return null;
        const article = v.closest('article, section, [role="region"], [data-fg-reel-card]');
        if (article && article.querySelectorAll('video').length <= 1) {
            return article;
        }

        let p = v.parentElement;
        let best = null;
        while (p && p !== document.body && p !== document.documentElement && p.tagName !== 'MAIN') {
            if (p.querySelectorAll('video').length > 1) {
                break;
            }
            if (p.querySelector('a[href*="/audio/"]') || p.querySelector('header') || p.querySelector('a[role="link"]:not([href*="/audio/"])')) {
                best = p;
            }
            p = p.parentElement;
        }
        return best || article || v.parentElement;
    }

    function checkAndTrackActiveReel() {
        const path = window.location.pathname || '';
        // Only run when user is on Reels or viewing a Reel
        if (!path.startsWith('/reels') && !path.startsWith('/reel')) {
            return;
        }

        let code = null;

        // 1. Direct URL check: /reel/<code> or /reels/<code>
        const urlMatch = path.match(/\/(?:reel|reels)\/([A-Za-z0-9_-]{4,})/);
        if (urlMatch && !['reels', 'reel', 'audio', 'videos', 'clips', 'explore', 'home', 'direct', 'stories'].includes(urlMatch[1].toLowerCase())) {
            code = urlMatch[1];
        }

        const vh = window.innerHeight || 800;
        let activeCard = null;

        // 2. Identify the active reel card
        // Priority A: If code is known from URL, find card containing a link with that code
        if (code) {
            const codeLink = document.querySelector(`a[href*="/reel/${code}/"], a[href*="/reels/${code}/"], a[href*="/p/${code}/"]`);
            if (codeLink) {
                activeCard = findReelCardForVideo(codeLink);
            }
        }

        // Priority B: Find video actively playing in viewport
        const videos = Array.from(document.querySelectorAll('video'));
        if (!activeCard) {
            for (const v of videos) {
                const rect = v.getBoundingClientRect();
                if (rect.bottom > 50 && rect.top < vh - 50 && !v.paused) {
                    activeCard = findReelCardForVideo(v);
                    break;
                }
            }
        }

        // Priority C: Center-most visible video in viewport
        if (!activeCard) {
            let minDistance = Infinity;
            for (const v of videos) {
                const rect = v.getBoundingClientRect();
                if (rect.bottom > 0 && rect.top < vh && rect.height > 100) {
                    const centerY = rect.top + rect.height / 2;
                    const dist = Math.abs(centerY - vh / 2);
                    if (dist < minDistance) {
                        minDistance = dist;
                        activeCard = findReelCardForVideo(v);
                    }
                }
            }
        }

        if (!activeCard && videos.length > 0) {
            activeCard = findReelCardForVideo(videos[0]);
        }

        // Extract code from activeCard if not in URL
        if (!code && activeCard) {
            const reelLinks = Array.from(activeCard.querySelectorAll('a[href*="/reel/"], a[href*="/reels/"], a[href*="/p/"]'));
            for (const l of reelLinks) {
                const href = l.getAttribute('href') || '';
                if (href.includes('/audio/')) continue;
                const m = href.match(/\/(?:reel|reels|p)\/([A-Za-z0-9_-]{4,})/);
                if (m && !['reels', 'reel', 'audio', 'videos', 'clips', 'explore', 'home', 'direct', 'stories'].includes(m[1].toLowerCase())) {
                    code = m[1];
                    break;
                }
            }
        }

        // If no valid reel shortcode found, DO NOT track
        if (!code || code === 'reels' || code === 'reel' || code === 'audio') return;

        // If this reel card is marked as an ad, DO NOT count or log it
        if (activeCard) {
            if (activeCard.hasAttribute('data-fg-reel-ad') || activeCard.getAttribute('data-fg-ad') === '1') {
                return;
            }
            const adPill = activeCard.querySelector('[aria-label="Ad"], a[href*="/ads/"], a[href*="/ad_"]');
            if (adPill) return;
        }

        let author = '';
        let caption = '';

        // Source 1: Check memory cache from previous events
        if (code && harvestedReels.has(code)) {
            const h = harvestedReels.get(code);
            if (h.author) author = h.author;
            if (h.caption) caption = h.caption.slice(0, 300);
        }

        // Source 2: Check hidden DOM cache element (#fg-meta-cache)
        if (code && (!author || !caption)) {
            const cacheEl = document.getElementById('fg-meta-cache');
            if (cacheEl && cacheEl.hasAttribute('data-reel-' + code)) {
                try {
                    const cached = JSON.parse(cacheEl.getAttribute('data-reel-' + code));
                    if (!author && cached.author) author = cached.author;
                    if (!caption && cached.caption) caption = cached.caption.slice(0, 300);
                } catch (_) {}
            }
        }

        // Source 3: Check meta tags (OG description & meta description)
        if (!caption || !author) {
            const metaDesc = document.querySelector('meta[property="og:description"], meta[name="description"]');
            if (metaDesc && metaDesc.content) {
                const m = metaDesc.content.match(/-\s*([A-Za-z0-9._]+)\s+on\s+[^:]+:\s*["“](.*?)["”]/s);
                if (m) {
                    if (!author && m[1]) author = m[1].trim();
                    if (!caption && m[2]) caption = m[2].trim().slice(0, 300);
                }
            }
        }

        // Source 4: Check document.title ("Author on Instagram: \"Caption\"")
        if (!caption || !author) {
            if (document.title) {
                const m = document.title.match(/^([^:]+?)\s+on\s+Instagram:\s*["“](.*?)["”]/s);
                if (m) {
                    if (!author && m[1]) author = m[1].trim();
                    if (!caption && m[2]) caption = m[2].trim().slice(0, 300);
                }
            }
        }

        // Source 5: Extract Author from activeCard DOM
        if (!author && activeCard) {
            const avatarLink = activeCard.querySelector('header a:has(img), a[href^="/"]:has(img[alt*="profile" i]), a[href^="/"]:has(img)');
            if (avatarLink) {
                const href = (avatarLink.getAttribute('href') || '').replace(/^\/+|\/+$/g, '').split('/')[0];
                if (href && !['home', 'explore', 'reels', 'reel', 'direct', 'stories', 'accounts', 'legal', 'about', 'audio'].includes(href.toLowerCase())) {
                    author = href;
                }
            }
            if (!author) {
                const headerLink = activeCard.querySelector('header a[href^="/"], [role="region"] header a[href^="/"]');
                if (headerLink) {
                    const href = (headerLink.getAttribute('href') || '').replace(/^\/+|\/+$/g, '').split('/')[0];
                    if (href && !['home', 'explore', 'reels', 'reel', 'direct', 'stories', 'accounts', 'legal', 'about', 'audio'].includes(href.toLowerCase())) {
                        author = href;
                    }
                    if (!author) {
                        const text = (headerLink.textContent || '').trim().replace(/^@/, '');
                        if (text && !['home', 'explore', 'reels', 'direct', 'instagram'].includes(text.toLowerCase())) {
                            author = text;
                        }
                    }
                }
            }
            if (!author) {
                const links = Array.from(activeCard.querySelectorAll('a[href^="/"]:not([href*="/audio/"]):not([href*="/reel/"]):not([href*="/reels/"]):not([href*="/explore/"]):not([href*="/tags/"])'));
                for (const a of links) {
                    if (a.closest('a[href*="/audio/"]')) continue;
                    const rawHref = (a.getAttribute('href') || '').replace(/^\/+|\/+$/g, '').split('/')[0];
                    const lower = rawHref.toLowerCase();
                    if (rawHref && !['home', 'explore', 'reels', 'reel', 'direct', 'stories', 'p', 'accounts', 'legal', 'about', 'audio'].includes(lower)) {
                        author = rawHref;
                        break;
                    }
                }
            }
        }

        // Source 6: Extract Caption from activeCard DOM
        if (!caption && activeCard) {
            const candidates = Array.from(activeCard.querySelectorAll('h1, h2, span[dir="auto"], div[dir="auto"], p, span'));
            for (const el of candidates) {
                if (el.closest('a[href*="/audio/"]')) continue;

                let txt = (el.textContent || '').trim();
                if (!txt || txt.length < 2) continue;

                // Strip author username if prepended at start of caption
                if (author) {
                    const cleanAuthor = author.toLowerCase().replace(/^@/, '');
                    if (txt.toLowerCase().startsWith(cleanAuthor)) {
                        txt = txt.slice(cleanAuthor.length).trim().replace(/^[:\-–—\s]+/, '').trim();
                    } else if (txt.toLowerCase().startsWith('@' + cleanAuthor)) {
                        txt = txt.slice(cleanAuthor.length + 1).trim().replace(/^[:\-–—\s]+/, '').trim();
                    }
                }

                const lower = txt.toLowerCase();
                if (txt.length < 2) continue;

                // Ignore if text is purely the author name
                if (author && (lower === author.toLowerCase() || lower === '@' + author.toLowerCase())) continue;

                // Ignore action buttons, counts, and UI badges
                if (/^(?:follow|following|requested|watch\s+more|view\s+profile|sign\s+up|install\s+now|learn\s+more|shop\s+now|more|\.\.\.\s*more)$/i.test(lower)) continue;
                if (lower === 'original audio' || lower.includes('· original audio')) continue;
                if (/^\d+[\d,.]*[KkMm]?$/.test(txt)) continue;
                if (lower.startsWith('liked by ') || lower.startsWith('verified')) continue;
                if (/^(?:ad|sponsored|paid\s+partnership)$/i.test(lower)) continue;
                if (/^(?:like|comment|comments|share|save|remix|send|audio|music|tagged|explore|reels|reel)$/i.test(lower)) continue;

                caption = txt.slice(0, 300);
                break;
            }
        }

        // Cache whatever we resolved
        if (code && (author || caption)) {
            const existing = harvestedReels.get(code) || {};
            harvestedReels.set(code, {
                author: author || existing.author || '',
                caption: caption || existing.caption || ''
            });
        }

        const reelUrl = 'https://www.instagram.com/reel/' + code + '/';

        // If code hasn't changed, check if metadata became available later
        if (code === lastTrackedReelCode) {
            if ((!lastTrackedCaption && caption) || (!lastTrackedAuthor && author)) {
                if (caption) lastTrackedCaption = caption;
                if (author) lastTrackedAuthor = author;
                postToNative({
                    action: 'reelWatched',
                    type: 'meta',
                    code: code,
                    url: reelUrl,
                    title: lastTrackedCaption || (lastTrackedAuthor ? ('Reel by @' + lastTrackedAuthor) : ''),
                    caption: lastTrackedCaption,
                    author: lastTrackedAuthor,
                    thumbnail: ''
                });
            }
            return;
        }

        // CODE CHANGED! Update last tracked state
        lastTrackedReelCode = code;
        lastTrackedCaption = caption;
        lastTrackedAuthor = author;

        postToNative({
            action: 'reelWatched',
            code: code,
            url: reelUrl,
            title: caption || (author ? ('Reel by @' + author) : ''),
            caption: caption,
            author: author || '',
            thumbnail: ''
        });
    }

    // Attach interaction listeners for Reels tracking without thrashing scroll frames
    let checkReelDebounceTimer = null;
    function debouncedCheckAndTrackActiveReel() {
        if (checkReelDebounceTimer) clearTimeout(checkReelDebounceTimer);
        checkReelDebounceTimer = setTimeout(checkAndTrackActiveReel, 120);
    }

    window.addEventListener('scroll', debouncedCheckAndTrackActiveReel, { passive: true });
    window.addEventListener('scrollend', checkAndTrackActiveReel, { passive: true });
    window.addEventListener('touchend', () => setTimeout(checkAndTrackActiveReel, 100), { passive: true });
    window.addEventListener('wheel', debouncedCheckAndTrackActiveReel, { passive: true });
    window.addEventListener('keydown', (e) => {
        if (['ArrowDown', 'ArrowUp', 'PageDown', 'PageUp', 'Space'].includes(e.key)) {
            setTimeout(checkAndTrackActiveReel, 120);
        }
    });
    window.addEventListener('popstate', checkAndTrackActiveReel);
    window.addEventListener('hashchange', checkAndTrackActiveReel);
    window.addEventListener('focusgram:urlchange', checkAndTrackActiveReel);

    // Periodic ticker (every 400ms) to ensure reel transitions are never missed
    setInterval(checkAndTrackActiveReel, 400);

    // Ensure clicking 'Post' or 'Story' in the Create dropdown opens file/media picker
    document.addEventListener('click', (e) => {
        const target = e.target;
        if (!target) return;

        const postOrStoryBtn = target.closest('button, div[role="button"], a[role="button"], li, div[role="menuitem"]');
        if (postOrStoryBtn) {
            const text = (postOrStoryBtn.textContent || '').trim().toLowerCase();
            if (text === 'post' || text === 'story' || text.startsWith('post\n') || text.startsWith('story\n')) {
                const fileInput = postOrStoryBtn.querySelector('input[type="file"]') ||
                                  postOrStoryBtn.parentElement?.querySelector('input[type="file"]') ||
                                  document.querySelector('form input[type="file"]') ||
                                  document.querySelector('input[type="file"]');
                if (fileInput && fileInput !== target) {
                    fileInput.click();
                }
            }
        }
    }, true);

    // --- 3b. REELS LAYOUT SCROLLER & OVERLAY POSITIONING (100% INSPIRATION MATCH) ---
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
            let card = findReelCardForVideo(v);
            if (!card) return;
            // Prevent selecting root page or scroller containers holding multiple reels
            if (card.tagName === 'MAIN' || card.id === 'react-root' || card.querySelectorAll('video').length > 1) {
                return;
            }

            if (!card.hasAttribute('data-fg-reel-card')) {
                card.setAttribute('data-fg-reel-card', '1');
            }

            // Check if this reel is an ad / sponsored
            if (currentSettings.noAds !== false) {
                let isSponsored = !!card.querySelector('[aria-label*="Sponsored" i], [aria-label="Ad" i], [aria-label*="advertisement" i], [data-ad-preview], a[href*="/ads/"], a[href*="/ad_"], a[href*="/about/ads"], a[href*="ads.instagram.com"]');

                if (!isSponsored) {
                    const badges = card.querySelectorAll('span, div[role="button"], button');
                    for (let s = 0; s < badges.length; s++) {
                        const el = badges[s];
                        if (el.children.length > 2) continue;
                        const raw = (el.textContent || '').trim();
                        if (!raw || raw.length > 15) continue;
                        if (/^[\u{1F300}-\u{1F9FF}\s]*ad$/iu.test(raw) || /^sponsored$/i.test(raw) || /^paid\s+partnership$/i.test(raw)) {
                            if (!el.closest('span[dir="auto"]')) {
                                isSponsored = true;
                                break;
                            }
                        }
                    }
                }

                if (isSponsored) {
                    card.setAttribute('data-fg-reel-ad', '1');
                    card.style.setProperty('visibility', 'hidden', 'important');
                    card.style.setProperty('pointer-events', 'none', 'important');
                    v.muted = true;
                    try { v.pause(); } catch (_) {}
                    const vr = v.getBoundingClientRect();
                    if (vr.top >= -vh * 0.4 && vr.bottom <= vh * 1.4) {
                        const scroller = card.closest('[data-fg-reel-scroller]') || document.querySelector('[data-fg-reel-scroller="1"]');
                        if (scroller && scroller !== window) {
                            try { scroller.scrollBy({ top: scroller.clientHeight || vh, behavior: 'instant' }); } catch (_) {}
                        } else {
                            try { window.scrollBy({ top: vh, behavior: 'instant' }); } catch (_) {}
                        }
                    }
                    return;
                }
            }

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

    // Capture scroll events anywhere in document without blocking smooth 60/120fps compositor
    let fixReelsScrollTimer = null;
    document.addEventListener('scroll', () => {
        if (fixReelsScrollTimer) clearTimeout(fixReelsScrollTimer);
        fixReelsScrollTimer = setTimeout(fixReels, 150);
    }, { passive: true, capture: true });
    window.addEventListener('scrollend', fixReels, { passive: true });

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

    // --- 6. UNBLUR REVEAL ENGINE (Tap & 350ms Hold) ---
    const HOLD_MS = 350, MOVE_TOL = 10;
    const root = document.documentElement;
    let timer = 0, sx = 0, sy = 0, swallowUntil = 0;

    function updateFeedFlag() {
      root.toggleAttribute("data-fg-feed", location.pathname === "/" || location.pathname === "");
    }
    updateFeedFlag();
    new MutationObserver(updateFeedFlag).observe(document, { childList: true, subtree: true });
    addEventListener("popstate", updateFeedFlag);

    function getBlurredTarget(el) {
        if (!el) return null;

        if (currentSettings.blurFeed) {
            // Never treat carousel next/prev arrows or navigation buttons as unblur triggers
            if (el.closest('button[aria-label="Next"], button[aria-label="Go back"], button[aria-label="Next slide"], button[aria-label="Previous slide"], button[aria-label="Chevron"]')) {
                return null;
            }

            // 1. Check if clicked inside a specific carousel slide
            const carouselSlide = el.closest('article ul li, article div[role="group"]');
            if (carouselSlide) {
                if (!carouselSlide.hasAttribute('data-fg-reveal') && !carouselSlide.classList.contains('fg-unblurred')) {
                    return carouselSlide;
                }
                return null; // Already unblurred slide; pass through to native Instagram actions
            }

            // 2. Fallback to single post (non-carousel)
            const feedArticle = el.closest('article');
            if (feedArticle && !feedArticle.hasAttribute('data-fg-reveal') && !feedArticle.classList.contains('fg-unblurred')) {
                const inHeader = !!el.closest('header');
                const inFooter = !!el.closest('section:has(svg[aria-label="Like"]), section:has(svg[aria-label="Comment"]), form');
                if (!inHeader && !inFooter) {
                    return feedArticle;
                }
            }
        }

        if (currentSettings.blurReels) {
            const reelContainer = el.closest('main div:has(> video), article div:has(> video)');
            if (reelContainer && !reelContainer.hasAttribute('data-fg-reveal') && !reelContainer.classList.contains('fg-unblurred')) {
                return reelContainer;
            }
            const video = el.closest('video');
            if (video && video.parentElement && !video.parentElement.hasAttribute('data-fg-reveal') && !video.parentElement.classList.contains('fg-unblurred')) {
                return video.parentElement;
            }
        }

        if (currentSettings.blurExplore) {
            const expLink = el.closest('main a[href*="/p/"], main a[href*="/reel/"]');
            if (expLink && !expLink.hasAttribute('data-fg-reveal') && !expLink.classList.contains('fg-unblurred')) {
                return expLink;
            }
        }
        return null;
    }

    function revealTarget(target) {
        if (!target) return;
        target.setAttribute('data-fg-reveal', '1');
        target.classList.add('fg-unblurred');
        target.style.setProperty('filter', 'none', 'important');
        target.querySelectorAll('*').forEach(c => {
            c.setAttribute('data-fg-reveal', '1');
            c.classList.add('fg-unblurred');
            c.style.setProperty('filter', 'none', 'important');
        });
    }

    addEventListener("pointerdown", e => {
      const activeMode = currentSettings.unblurMode || (currentSettings.tapToUnblur ? "tap" : "hold");
      if (activeMode !== "hold") return;
      const target = getBlurredTarget(e.target);
      if (!target) return;
      sx = e.clientX; sy = e.clientY;
      timer = setTimeout(() => {
        revealTarget(target);
        swallowUntil = Date.now() + 450; // stop the release from clicking through
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
      const target = getBlurredTarget(e.target);
      if (!target) return;
      const activeMode = currentSettings.unblurMode || (currentSettings.tapToUnblur ? "tap" : "hold");
      if (activeMode === "tap") {
        e.preventDefault(); e.stopPropagation();
        revealTarget(target);
      }
    }, true);

    addEventListener("contextmenu", e => {
      if (getBlurredTarget(e.target)) e.preventDefault();
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
                title: title || '',
                author: author || '',
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
        if (isAuthRoute(path) || path.startsWith('/direct') || path.startsWith('/stories/') || path.startsWith('/create')) {
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
        const isInDirect = lower.includes('/direct');
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

    function cleanUpDynamicContent() {
        try {
            // Hide Like Counts dynamic text
            if (currentSettings.hideLikeCounts) {
                document.querySelectorAll('a[href*="/liked_by/"]').forEach((el) => {
                    el.style.setProperty('display', 'none', 'important');
                    const parent = el.closest('div');
                    if (parent && parent.textContent && /liked/i.test(parent.textContent)) {
                        parent.style.setProperty('display', 'none', 'important');
                    }
                });
                document.querySelectorAll('article section ~ div span, article section ~ div div, div[role="presentation"] span, article span, article div').forEach((el) => {
                    if (el.children.length > 2) return;
                    const text = (el.textContent || '').trim();
                    if (/^(liked\s+by|[\d,.]+[KkMm]?\s+likes?|[\d,.]+\s+others|be\s+the\s+first\s+to\s+like)/i.test(text)) {
                        el.style.setProperty('display', 'none', 'important');
                    }
                });
                document.querySelectorAll('svg[aria-label="Like"], svg[aria-label="Unlike"]').forEach((svg) => {
                    const btn = svg.closest('[role="button"]') || svg.parentElement;
                    if (btn) {
                        btn.querySelectorAll('span, div').forEach(child => {
                            if (!child.contains(svg) && /^\d+[\d,.]*[KkMm]?$/.test((child.textContent || '').trim())) {
                                child.style.setProperty('display', 'none', 'important');
                            }
                        });
                        let sibling = btn.nextElementSibling;
                        if (sibling && /^\d+[\d,.]*[KkMm]?$/.test((sibling.textContent || '').trim())) {
                            sibling.style.setProperty('display', 'none', 'important');
                        }
                        const parent = btn.parentElement;
                        if (parent && parent.children.length <= 4) {
                            parent.querySelectorAll('span').forEach(sp => {
                                if (/^\d+[\d,.]*[KkMm]?$/.test((sp.textContent || '').trim())) {
                                    sp.style.setProperty('display', 'none', 'important');
                                }
                            });
                        }
                    }
                });
            }

            // Minimal mode redirect: restrict access to DMs and profile only
            if (currentSettings.minimalMode) {
                const curPath = (window.location.pathname || '').toLowerCase();
                if (curPath === '/' || curPath === '' || curPath.startsWith('/explore') || curPath.startsWith('/reels') || curPath.startsWith('/reel') || curPath.startsWith('/p/')) {
                    performNavClick('direct');
                }
            }
        } catch (_) {}
    }

    const FG_ACT_ATTR = 'data-fg-x-act';
    const FG_ACT_CLASS = 'fg-x-act-btn';

    function requestMediaFromPage(code) {
        return new Promise((resolve) => {
            if (!code) return resolve(null);
            const reqId = 'req_' + Math.random().toString(36).substring(2, 9);
            const timer = setTimeout(() => {
                window.removeEventListener('fg-response-media-' + reqId, onResponse);
                resolve(null);
            }, 1800);

            function onResponse(e) {
                clearTimeout(timer);
                window.removeEventListener('fg-response-media-' + reqId, onResponse);
                resolve(e && e.detail ? e.detail : null);
            }

            window.addEventListener('fg-response-media-' + reqId, onResponse);
            window.dispatchEvent(new CustomEvent('fg-request-media', {
                detail: { code: code, reqId: reqId }
            }));
        });
    }

    function extractVideoUrlFromFiber(rawNode) {
        if (!rawNode) return null;
        try {
            const node = rawNode.wrappedJSObject || rawNode;
            const key = Object.keys(node).find(k => k.startsWith('__reactFiber') || k.startsWith('__reactProps'));
            if (!key) return null;
            let fiber = node[key];
            for (let i = 0; i < 40 && fiber; i++) {
                const props = fiber.memoizedProps || fiber.pendingProps;
                if (props) {
                    if (props.videoUrl && typeof props.videoUrl === 'string' && props.videoUrl.startsWith('http')) {
                        return props.videoUrl;
                    }
                    if (props.src && typeof props.src === 'string' && props.src.startsWith('http') && !props.src.startsWith('blob:')) {
                        return props.src;
                    }
                    if (props.video_versions && Array.isArray(props.video_versions) && props.video_versions[0]?.url) {
                        return props.video_versions[0].url;
                    }
                    if (props.media && props.media.video_versions && props.media.video_versions[0]?.url) {
                        return props.media.video_versions[0].url;
                    }
                    if (props.item && props.item.video_versions && props.item.video_versions[0]?.url) {
                        return props.item.video_versions[0].url;
                    }
                    if (props.clip && props.clip.video_versions && props.clip.video_versions[0]?.url) {
                        return props.clip.video_versions[0].url;
                    }
                    if (props.post && props.post.video_versions && props.post.video_versions[0]?.url) {
                        return props.post.video_versions[0].url;
                    }
                }
                fiber = fiber.return;
            }
        } catch (_) {}
        return null;
    }

    function extractBestDomUrl(container) {
        if (!container) return null;
        // 1. Video check
        const video = container.querySelector('video') || (container.tagName === 'VIDEO' ? container : null);
        if (video) {
            const fiberVideoUrl = extractVideoUrlFromFiber(video) ||
                                  extractVideoUrlFromFiber(video.parentElement) ||
                                  extractVideoUrlFromFiber(video.parentElement?.parentElement);
            if (fiberVideoUrl) {
                return { url: fiberVideoUrl, isVideo: true };
            }

            const vSrc = video.src || video.currentSrc || '';
            if (vSrc && (vSrc.startsWith('http://') || vSrc.startsWith('https://')) && !vSrc.startsWith('blob:')) {
                return { url: vSrc, isVideo: true };
            }
            const source = video.querySelector('source');
            if (source && source.src && (source.src.startsWith('http://') || source.src.startsWith('https://')) && !source.src.startsWith('blob:')) {
                return { url: source.src, isVideo: true };
            }
            if (video.poster && (video.poster.startsWith('http://') || video.poster.startsWith('https://'))) {
                return { url: video.poster, isVideo: false };
            }
        }

        const fiberContainerUrl = extractVideoUrlFromFiber(container);
        if (fiberContainerUrl) {
            return { url: fiberContainerUrl, isVideo: true };
        }

        // 2. Image check (best candidate from srcset or src)
        const imgs = Array.from(container.querySelectorAll('img')).filter(img => {
            const r = img.getBoundingClientRect();
            return r.width > 120 && r.height > 120;
        });
        const mainImg = imgs[0];
        if (mainImg) {
            if (mainImg.srcset) {
                const parts = mainImg.srcset.split(',').map(s => s.trim().split(/\s+/));
                const best = parts[parts.length - 1]?.[0];
                if (best && (best.startsWith('http://') || best.startsWith('https://'))) {
                    return { url: best, isVideo: false };
                }
            }
            const iSrc = mainImg.currentSrc || mainImg.src;
            if (iSrc && (iSrc.startsWith('http://') || iSrc.startsWith('https://'))) {
                return { url: iSrc, isVideo: false };
            }
        }

        // 3. Meta og:video & og:image
        const ogVideo = document.querySelector('meta[property="og:video"], meta[property="og:video:secure_url"]');
        if (ogVideo && ogVideo.content && (ogVideo.content.startsWith('http://') || ogVideo.content.startsWith('https://'))) {
            return { url: ogVideo.content, isVideo: true };
        }
        const ogImage = document.querySelector('meta[property="og:image"]');
        if (ogImage && ogImage.content && (ogImage.content.startsWith('http://') || ogImage.content.startsWith('https://'))) {
            return { url: ogImage.content, isVideo: false };
        }
        // 4. Any image inside container
        const anyImg = container.querySelector('img[src*="cdninstagram"], img[src*="fbcdn"], img');
        if (anyImg) {
            const src = anyImg.currentSrc || anyImg.src;
            if (src && (src.startsWith('http://') || src.startsWith('https://'))) {
                return { url: src, isVideo: false };
            }
        }

        return null;
    }

    async function handleMediaDownload(postContainer, shortcode, activeVideo) {
        try {
            // First attempt: fetch from in-page API harvest cache or GraphQL
            let media = null;
            if (shortcode) {
                media = await requestMediaFromPage(shortcode);
            }

            if (media && media.videoUrl && (media.videoUrl.startsWith('http://') || media.videoUrl.startsWith('https://'))) {
                postToNative({
                    action: 'downloadMedia',
                    url: media.videoUrl,
                    filename: 'instagram_' + (shortcode || Date.now()) + '.mp4'
                });
                return;
            }

            if (media && media.imageUrl && (media.imageUrl.startsWith('http://') || media.imageUrl.startsWith('https://'))) {
                postToNative({
                    action: 'downloadMedia',
                    url: media.imageUrl,
                    filename: 'instagram_' + (shortcode || Date.now()) + '.jpg'
                });
                return;
            }

            // Second attempt: extract from DOM
            const domMedia = extractBestDomUrl(activeVideo || postContainer);
            if (domMedia && domMedia.url) {
                const ext = domMedia.isVideo ? '.mp4' : '.jpg';
                postToNative({
                    action: 'downloadMedia',
                    url: domMedia.url,
                    filename: 'instagram_' + (shortcode || Date.now()) + ext
                });
                return;
            }

            // Third attempt: Resource Performance Timings for active streaming video
            try {
                const perfEntries = performance.getEntriesByType('resource');
                for (let i = perfEntries.length - 1; i >= 0 && i >= perfEntries.length - 40; i--) {
                    const entry = perfEntries[i];
                    const name = entry.name || '';
                    if ((name.includes('cdninstagram.com') || name.includes('fbcdn.net')) && 
                        (name.includes('.mp4') || name.includes('bytestart') || name.includes('/v/t') || entry.initiatorType === 'video' || entry.initiatorType === 'fetch')) {
                        if (name.startsWith('http://') || name.startsWith('https://')) {
                            const cleanUrl = name.replace(/[?&]bytestart=\d+/, '').replace(/[?&]byteend=\d+/, '');
                            postToNative({
                                action: 'downloadMedia',
                                url: cleanUrl,
                                filename: 'instagram_' + (shortcode || Date.now()) + '.mp4'
                            });
                            return;
                        }
                    }
                }
            } catch (_) {}

            // Failed to resolve
            postToNative({
                action: 'downloadMedia',
                url: '',
                error: 'Could not extract video stream URL for this Reel'
            });
        } catch (e) {
            postToNative({
                action: 'downloadMedia',
                url: '',
                error: 'Download error: ' + (e.message || e)
            });
        }
    }

    function syncDownloadButtons() {
        if (!currentSettings.videoDownloadEnabled) {
            document.querySelectorAll('.' + FG_ACT_CLASS + ', .fg-feed-download-btn').forEach(btn => btn.remove());
            return;
        }

        const candidates = new Set();

        // 1. Polygon bookmark ribbon shape in action bar (points contain 20 21 or 21)
        document.querySelectorAll('polygon').forEach(poly => {
            const pts = poly.getAttribute('points') || '';
            if (pts.includes('21') || pts.includes('13.44') || pts.includes('13.4') || pts.includes('20 21')) {
                const btn = poly.closest('div[role="button"], button') || poly.closest('svg')?.parentElement;
                if (btn) candidates.add(btn);
            }
        });

        // 2. Multilingual save/bookmark aria-labels
        document.querySelectorAll('svg, [role="button"], button').forEach(el => {
            const label = (el.getAttribute('aria-label') || '').toLowerCase();
            if (label.includes('save') || label.includes('guardar') || label.includes('salvar') || 
                label.includes('speichern') || label.includes('enregistrer') || label.includes('bookmark') ||
                label.includes('sauvegarder') || label.includes('remove') || label.includes('eliminar')) {
                const btn = el.closest('div[role="button"], button') || (el.getAttribute('role') === 'button' ? el : el.parentElement);
                if (btn) candidates.add(btn);
            }
        });

        // 3. Structural action bar fallback (bookmark button is right-aligned in post action row)
        document.querySelectorAll('article, div[role="presentation"]').forEach(post => {
            const svgs = Array.from(post.querySelectorAll('svg'));
            for (const svg of svgs) {
                const btn = svg.closest('div[role="button"], button');
                if (!btn) continue;
                const row = btn.closest('section') || btn.closest('div[style*="flex-direction: row"]') || btn.parentElement?.parentElement;
                if (row && row.children.length >= 2 && !row.querySelector('[' + FG_ACT_ATTR + '="1"]')) {
                    const rightCol = row.children[row.children.length - 1];
                    const saveBtn = rightCol.querySelector('div[role="button"], button') || rightCol;
                    if (saveBtn) {
                        candidates.add(saveBtn);
                        break;
                    }
                }
            }
        });

        candidates.forEach(btnContainer => {
            if (!btnContainer || !btnContainer.parentElement) return;
            const parent = btnContainer.parentElement;

            if (parent.querySelector('[' + FG_ACT_ATTR + '="1"]')) return;

            let postContainer = btnContainer.closest('article') || btnContainer.closest('section');
            if (!postContainer) {
                let curr = btnContainer;
                for (let i = 0; i < 8 && curr && curr !== document.body && curr.tagName !== 'MAIN'; i++) {
                    if (curr.querySelector && (curr.querySelector('video') || curr.querySelector('img[src*="cdninstagram"], img[src*="fbcdn"]'))) {
                        postContainer = curr;
                        break;
                    }
                    curr = curr.parentElement;
                }
            }
            if (!postContainer) postContainer = parent.parentElement?.parentElement;
            if (!postContainer) return;

            // Extract post shortcode if available
            const link = postContainer.querySelector('a[href*="/p/"], a[href*="/reel/"], a[href*="/reels/"]');
            const href = link ? (link.getAttribute('href') || '') : window.location.pathname;
            const codeMatch = href.match(/\/(?:p|reel|reels)\/([A-Za-z0-9_-]+)/);
            const shortcode = codeMatch ? codeMatch[1] : '';

            const dlBtn = document.createElement('div');
            dlBtn.className = FG_ACT_CLASS;
            dlBtn.setAttribute(FG_ACT_ATTR, '1');
            dlBtn.setAttribute('data-fg-download-btn', '1');
            dlBtn.setAttribute('tabindex', '0');
            dlBtn.setAttribute('aria-label', 'Save Media');
            dlBtn.style.cssText = 'display:inline-flex;align-items:center;justify-content:center;cursor:pointer;padding:6px;margin-right:6px;color:currentColor;opacity:0.9;vertical-align:middle;';
            dlBtn.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display:block;pointer-events:none;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>`;

            dlBtn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                handleMediaDownload(postContainer, shortcode);
            }, true);

            parent.insertBefore(dlBtn, btnContainer);
        });

        // 4. Ensure every article in feed has a download button injected in its action bar
        document.querySelectorAll('article').forEach(article => {
            if (article.querySelector('[' + FG_ACT_ATTR + '="1"]')) return;
            const likeBtn = article.querySelector('svg[aria-label*="Like" i], svg[aria-label*="Unlike" i], svg[aria-label*="Gefällt" i], svg[aria-label*="J’aime" i], svg[aria-label*="Curtir" i]')?.closest('div[role="button"], button');
            if (!likeBtn) return;
            const actionRow = likeBtn.closest('section') || likeBtn.parentElement?.parentElement;
            if (!actionRow) return;

            let saveBtn = actionRow.querySelector('svg[aria-label*="Save" i], svg[aria-label*="Bookmark" i], svg[aria-label*="Guardar" i]')?.closest('div[role="button"], button');
            let target = saveBtn;
            let container = saveBtn ? saveBtn.parentElement : (actionRow.children.length > 1 ? actionRow.children[actionRow.children.length - 1] : actionRow);

            if (!container || container.querySelector('[' + FG_ACT_ATTR + '="1"]')) return;

            const link = article.querySelector('a[href*="/p/"], a[href*="/reel/"], a[href*="/reels/"]');
            const href = link ? (link.getAttribute('href') || '') : '';
            const codeMatch = href.match(/\/(?:p|reel|reels)\/([A-Za-z0-9_-]+)/);
            const shortcode = codeMatch ? codeMatch[1] : '';

            const dlBtn = document.createElement('div');
            dlBtn.className = FG_ACT_CLASS;
            dlBtn.setAttribute(FG_ACT_ATTR, '1');
            dlBtn.setAttribute('data-fg-download-btn', '1');
            dlBtn.setAttribute('tabindex', '0');
            dlBtn.setAttribute('role', 'button');
            dlBtn.setAttribute('aria-label', 'Save Media');
            dlBtn.style.cssText = 'display:inline-flex;align-items:center;justify-content:center;cursor:pointer;padding:6px;margin-right:6px;color:currentColor;opacity:0.9;vertical-align:middle;';
            dlBtn.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display:block;pointer-events:none;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>`;

            dlBtn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                handleMediaDownload(article, shortcode);
            }, true);

            if (target && target.parentElement === container) {
                container.insertBefore(dlBtn, target);
            } else {
                container.appendChild(dlBtn);
            }
        });
    }

    function filterFeedAds() {
        if (currentSettings.noAds === false) return;
        const SPONSORED_KW = [
            'sponsored', 'gesponsert', 'sponsorisé', 'publicidad', 
            'patrocinado', 'sponsorizzato', 'paid partnership', 'werbung', 'anuncio', 'werbeanzeige'
        ];
        const STRICT_AD_CTAS = [
            'shop now', 'install now', 'sign up', 'apply now', 'book now', 'order now', 'get offer'
        ];

        document.querySelectorAll('article').forEach(article => {
            if (article.dataset.fgAdFiltered) return;
            let isAd = !!article.querySelector('[data-ad-preview], a[href*="/ads/"], a[href*="/ad_"], a[href*="ads.instagram.com"]');

            if (!isAd) {
                const headerElements = article.querySelectorAll('header span, header [aria-label*="Sponsored" i], header [aria-label="Ad" i]');
                for (let i = 0; i < headerElements.length; i++) {
                    const el = headerElements[i];
                    if (el.children.length > 2) continue;
                    const txt = (el.textContent || '').trim().toLowerCase();
                    if (!txt || txt.length > 30) continue;
                    const clean = txt.replace(/[^a-z]/g, '');
                    if (clean === 'ad' || SPONSORED_KW.some(kw => txt === kw || clean === kw)) {
                        isAd = true;
                        break;
                    }
                }
            }

            if (!isAd) {
                const buttons = article.querySelectorAll('div[role="button"]:not([' + FG_ACT_ATTR + ']):not([data-fg-download-btn]), a[role="button"]:not([' + FG_ACT_ATTR + ']):not([data-fg-download-btn]), button');
                for (let k = 0; k < buttons.length; k++) {
                    const btnTxt = (buttons[k].textContent || '').trim().toLowerCase();
                    if (STRICT_AD_CTAS.some(cta => btnTxt === cta)) {
                        isAd = true;
                        break;
                    }
                }
            }

            if (isAd) {
                article.dataset.fgAdFiltered = 'true';
                article.style.setProperty('display', 'none', 'important');
                article.style.setProperty('height', '0px', 'important');
                article.style.setProperty('min-height', '0px', 'important');
                article.style.setProperty('max-height', '0px', 'important');
                article.style.setProperty('overflow', 'hidden', 'important');
                article.style.setProperty('opacity', '0', 'important');
                article.style.setProperty('pointer-events', 'none', 'important');
            }
        });
    }

    async function downloadActiveReel() {
        try {
            const vh = window.innerHeight;
            const videos = Array.from(document.querySelectorAll('video'));
            // Active video is the one currently visible and playing, or closest to center
            let activeVideo = null;
            let minCenterDist = Infinity;
            for (const v of videos) {
                const r = v.getBoundingClientRect();
                if (r.height > 100 && r.bottom > 0 && r.top < vh) {
                    const dist = Math.abs((r.top + r.height / 2) - vh / 2);
                    if (!v.paused) {
                        activeVideo = v;
                        break;
                    }
                    if (dist < minCenterDist) {
                        minCenterDist = dist;
                        activeVideo = v;
                    }
                }
            }
            if (!activeVideo && videos.length > 0) activeVideo = videos[0];

            const card = activeVideo ? (activeVideo.closest('[data-fg-reel-card]') || activeVideo.closest('article') || activeVideo.parentElement) : document;

            let code = null;
            if (card) {
                const reelLink = card.querySelector('a[href*="/reel/"], a[href*="/p/"], a[href*="/reels/"]');
                if (reelLink && reelLink.href) {
                    const m = reelLink.href.match(/\/(?:reel|reels|p)\/([A-Za-z0-9_-]{4,})/);
                    if (m && m[1] !== 'reels' && m[1] !== 'reel') code = m[1];
                }
            }
            if (!code) {
                const m = window.location.pathname.match(/\/(?:reel|reels|p)\/([A-Za-z0-9_-]{4,})/);
                if (m && m[1] !== 'reels' && m[1] !== 'reel') code = m[1];
            }

            await handleMediaDownload(card, code, activeVideo);
        } catch (e) {
            console.error('[FocusGram] downloadActiveReel error:', e);
            postToNative({ action: 'downloadMedia', url: '', error: 'Download error: ' + (e.message || e) });
        }
    }

    // 250ms periodic heartbeat guarantees zero missed state changes during React renders
    setInterval(() => {
        checkStateAndNotify();
        hideNav();
        updateShareSheetState();
        fixReels();
        filterFeedAds();
        cleanUpDynamicContent();
        syncDownloadButtons();
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
        } else if (msg.action === 'requestReelDownload') {
            downloadActiveReel();
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
                blurReels: config.blurReels !== undefined ? config.blurReels : currentSettings.blurReels,
                blurFeed: config.blurFeed !== undefined ? config.blurFeed : currentSettings.blurFeed,
                unblurMode: config.unblurMode || (config.tapToUnblur ? 'tap' : 'hold') || currentSettings.unblurMode,
                tapToUnblur: config.tapToUnblur !== undefined ? config.tapToUnblur : currentSettings.tapToUnblur,
                hideLikeCounts: config.hideLikeCounts !== undefined ? config.hideLikeCounts : currentSettings.hideLikeCounts,
                hideFollowerCounts: config.hideFollowerCounts !== undefined ? config.hideFollowerCounts : currentSettings.hideFollowerCounts,
                hideSuggestedPosts: config.hideSuggestedPosts !== undefined ? config.hideSuggestedPosts : currentSettings.hideSuggestedPosts,
                hideShopTab: config.hideShopTab !== undefined ? config.hideShopTab : currentSettings.hideShopTab,
                videoDownloadEnabled: config.videoDownloadEnabled !== undefined ? config.videoDownloadEnabled : currentSettings.videoDownloadEnabled,
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

    let infiniteScrollTimer = null;
    window.addEventListener('scroll', () => {
        if (infiniteScrollTimer) clearTimeout(infiniteScrollTimer);
        infiniteScrollTimer = setTimeout(checkAndTriggerInfiniteScroll, 180);
    }, { passive: true });
    window.addEventListener('scrollend', checkAndTriggerInfiniteScroll, { passive: true });
    window.addEventListener('focusgram:download-reel', downloadActiveReel);

})();

