/**
 * In-page WebExtension controller injected across web sessions at document_start.
 * Handles stealth request filtering, navigation bar trimming, viewport margin offsets,
 * client-side chat locking, and dynamic wellness guardrails.
 */

'use strict';

(function () {
    function postToNative(message) {
        if (!message) return;
        try {
            if (typeof browser !== 'undefined' && browser.runtime) {
                if (browser.runtime.sendNativeMessage) {
                    browser.runtime.sendNativeMessage("focusgram", message).catch(() => {});
                }
                if (browser.runtime.sendMessage) {
                    browser.runtime.sendMessage(message).catch(() => {});
                }
            }
        } catch (_) {}
    }

    function isAuthPage() {
        try {
            const host = (window.location.hostname || '').toLowerCase();
            if (host.includes('facebook.com') || host.includes('accountscenter.')) {
                return true;
            }
            const path = (window.location.pathname || '').toLowerCase();
            if (path.includes('/accounts/edit')) {
                return false;
            }
            if (path.startsWith('/accounts') || path.startsWith('/login') || path.startsWith('/signup') ||
                path.startsWith('/two_factor') || path.startsWith('/challenge') || path.startsWith('/onetap')) {
                return true;
            }
            if (document.getElementById('loginForm') || document.querySelector('form[action*="login"], form input[type="password"]')) {
                return true;
            }
        } catch (_) {}
        return false;
    }

    if (isAuthPage()) {
        postToNative({ action: 'authStateChanged', isAuthenticated: false, isAuthPage: true });
        let authCheckInterval = setInterval(() => {
            if (!isAuthPage()) {
                clearInterval(authCheckInterval);
                window.location.reload();
            }
        }, 1000);
        return;
    } else {
        postToNative({ action: 'authStateChanged', isAuthenticated: true, isAuthPage: false });
    }

    console.log('[FocusGram] In-page controller initialized.');

    // Anti-flash style injected at document_start.
    try {
        const immediateStyle = document.createElement('style');
        immediateStyle.id = 'focusgram-immediate-nav-hide';
        immediateStyle.textContent = `
            [data-fg-hide],
            [data-fg-hide="1"],
            div[data-focusgram-hidden-bottom-bar="true"],
            nav[role="navigation"]:has(a[href*="/explore/"]),
            div[role="navigation"]:has(a[href*="/explore/"]),
            footer[role="contentinfo"]:has(a[href*="/explore/"]) {
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
        noAutoplay: false,
        grayscale: false,
        videoDownloadEnabled: false,
        storySearchEnabled: true,
        customBioFontsEnabled: true,
        ghostModeEnabled: false,
        ghostHideStorySeen: true,
        ghostHideDirectSeen: true,
        ghostHideTypingIndicator: true
    };

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
                blockVideos: false,
                blockAutoplay: false,
                storySearchEnabled: true,
                customBioFontsEnabled: true
            };

            // HTML5 video autoplay interception
            const allowedVideos = new WeakSet();
            function isReelVideo() {
                try {
                    const path = (window.location.pathname || '').toLowerCase();
                    return path.startsWith('/reel') || path.includes('/reel');
                } catch (_) {
                    return false;
                }
            }

            function shouldBlockAutoplay() {
                if (isReelVideo()) return false;
                const cfg = window.__fgFilterConfig;
                return cfg && cfg.blockAutoplay !== false;
            }

            if (window.HTMLVideoElement && HTMLVideoElement.prototype.play) {
                const origPlay = HTMLVideoElement.prototype.play;
                HTMLVideoElement.prototype.play = function() {
                    try {
                        if (window.__fgMediaPaused) {
                            this.removeAttribute('autoplay');
                            this.autoplay = false;
                            this.pause();
                            return Promise.resolve();
                        }
                        if (!shouldBlockAutoplay() || allowedVideos.has(this)) {
                            return origPlay.apply(this, arguments);
                        }
                        this.removeAttribute('autoplay');
                        this.autoplay = false;
                        this.pause();
                        return Promise.resolve();
                    } catch (_) {
                        return origPlay.apply(this, arguments);
                    }
                };

                ['click', 'pointerup'].forEach(function(eventType) {
                    document.addEventListener(eventType, function(e) {
                        if (!e.isTrusted) return;
                        if (e.target.closest('button[aria-label="Like"], button[aria-label="Comment"], button[aria-label="Share"], a[role="link"]:not(:has(video))')) return;
                        const v = e.target.tagName === 'VIDEO' ? e.target : (e.target.closest('video') || (e.target.querySelector ? e.target.querySelector('video') : null));
                        if (v) {
                            allowedVideos.add(v);
                            try { origPlay.apply(v); } catch (_) {}
                        }
                    }, true);
                });
            }

            window.addEventListener('message', function(e) {
                if (!e || !e.data) return;
                if (e.data.type === 'GHOSTIFY_SETTINGS_UPDATE' && e.data.settings) {
                    const s = e.data.settings;
                    if (s.enabled !== undefined) window.__focusgramGhostSettings.enabled = !!s.enabled;
                    const story = s.hideStorySeen !== undefined ? !!s.hideStorySeen : (s.igStory !== undefined ? !!s.igStory : window.__focusgramGhostSettings.hideStorySeen);
                    const seen = s.hideDirectSeen !== undefined ? !!s.hideDirectSeen : (s.igSeen !== undefined ? !!s.igSeen : window.__focusgramGhostSettings.hideDirectSeen);
                    const typing = s.hideTyping !== undefined ? !!s.hideTyping : (s.igTyping !== undefined ? !!s.igTyping : window.__focusgramGhostSettings.hideTyping);
                    window.__focusgramGhostSettings.hideStorySeen = story;
                    window.__focusgramGhostSettings.hideDirectSeen = seen;
                    window.__focusgramGhostSettings.hideTyping = typing;
                    window.__focusgramGhostSettings.igStory = story;
                    window.__focusgramGhostSettings.igSeen = seen;
                    window.__focusgramGhostSettings.igTyping = typing;
                }
                if (e.data.type === 'FOCUSGRAM_FILTER_UPDATE' && e.data.config) {
                    Object.assign(window.__fgFilterConfig, e.data.config);
                }
            });

            const AD_KEYWORDS = [
                'sponsored', 'gesponsert', 'sponsorisé', 'sponsorise', 'publicidad',
                'patrocinado', 'sponsorizzato', 'sponsrad', 'paid partnership', 'werbung',
                'anuncio', 'werbeanzeige', 'publicité'
            ];

            function textHasAdSignal(value) {
                if (!value) return false;
                const s = String(value).toLowerCase();
                return AD_KEYWORDS.some(kw => s.includes(kw));
            }

            function isAdNode(node) {
                if (!node || typeof node !== 'object') return false;
                const typename = String(node.__typename || '');
                if (node.is_ad || node.is_paid_partnership || (Array.isArray(node.sponsor_tags) && node.sponsor_tags.length > 0)) {
                    return true;
                }
                if (node.ad_id || node.ad_action_link || node.ad_impression_token || node.ad_metadata) {
                    return true;
                }
                if (node.commerciality_status === 'commercial' || (node.product_type && node.product_type === 'ad')) {
                    return true;
                }
                if (node.ad_header_style && node.ad_header_style !== 'none') {
                    return true;
                }
                if (typename === 'GraphAdStory' || typename.includes('Ad')) {
                    return true;
                }

                const values = [node.social_context, node.title, node.header, node.label, node.overlay_text]
                    .filter(Boolean).map(v => typeof v === 'object' ? JSON.stringify(v) : String(v)).join(' ');

                return textHasAdSignal(values);
            }

            function isSponsoredNode(node) {
                if (!node || typeof node !== 'object') return false;
                if (node.is_paid_partnership || (Array.isArray(node.sponsor_tags) && node.sponsor_tags.length > 0)) {
                    return true;
                }
                if (node.ad_action_link && node.ad_action_link.href) {
                    return true;
                }
                if (node.ad_header_style && node.ad_header_style !== 'none') {
                    return true;
                }
                const text = [node.social_context, node.header, node.label].filter(Boolean).map(v => typeof v === 'object' ? JSON.stringify(v) : String(v)).join(' ');
                return textHasAdSignal(text);
            }

            function isSuggestedNode(node) {
                if (!node || typeof node !== 'object') return false;
                const typename = String(node.__typename || '');
                if (node.is_suggested || node.is_suggested_for_you || node.is_recommendation ||
                    node.suggested_users || node.suggested_media || node.suggested_content ||
                    node.recommendation_source || typename.includes('Suggested') || typename.includes('Recommendation')
                ) {
                    return true;
                }
                const text = [node.suggested_reason, node.social_context, node.title, node.header, node.label]
                    .filter(Boolean).map(v => typeof v === 'object' ? JSON.stringify(v) : String(v)).join(' ').toLowerCase();
                return text.includes('suggested') || text.includes('recommend');
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

            window.__fg_media_harvest = window.__fg_media_harvest || {};
            window.__fg_recent_media = window.__fg_recent_media || [];
            window.__fg_profile_pics = window.__fg_profile_pics || {};

            function harvestReelFromObj(node) {
                if (!node || typeof node !== 'object') return;

                // Harvest profile pictures
                if (node.user && typeof node.user === 'object' && node.user.username) {
                    const uName = String(node.user.username).toLowerCase();
                    const pic = node.user.profile_pic_url_hd || node.user.profile_pic_url;
                    if (pic) window.__fg_profile_pics[uName] = pic;
                }
                if (node.owner && typeof node.owner === 'object' && node.owner.username) {
                    const uName = String(node.owner.username).toLowerCase();
                    const pic = node.owner.profile_pic_url_hd || node.owner.profile_pic_url;
                    if (pic) window.__fg_profile_pics[uName] = pic;
                }

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

                    // Harvest direct media URLs
                    const isVideo = !!(node.is_video || (node.video_versions && node.video_versions.length > 0) || node.video_url);
                    const videoUrl = (node.video_versions && node.video_versions[0]?.url) || node.video_url || '';
                    const imageUrl = (node.image_versions2?.candidates && node.image_versions2.candidates[0]?.url) || node.display_url || '';

                    const carousel = [];
                    if (Array.isArray(node.carousel_media)) {
                        for (let c = 0; c < node.carousel_media.length; c++) {
                            const cItem = node.carousel_media[c];
                            const cIsVideo = !!(cItem.is_video || (cItem.video_versions && cItem.video_versions.length > 0));
                            const cVideoUrl = (cItem.video_versions && cItem.video_versions[0]?.url) || '';
                            const cImageUrl = (cItem.image_versions2?.candidates && cItem.image_versions2.candidates[0]?.url) || '';
                            carousel.push({
                                url: cIsVideo && cVideoUrl ? cVideoUrl : cImageUrl,
                                videoUrl: cVideoUrl,
                                imageUrl: cImageUrl,
                                isVideo: cIsVideo && !!cVideoUrl
                            });
                        }
                    }

                    const entry = {
                        code: code,
                        videoUrl: videoUrl,
                        imageUrl: imageUrl,
                        url: isVideo && videoUrl ? videoUrl : (imageUrl || videoUrl),
                        isVideo: isVideo && !!videoUrl,
                        carousel: carousel,
                        author: author,
                        caption: caption
                    };

                    window.__fg_media_harvest[code] = entry;
                    window.__fg_recent_media.unshift(entry);
                    if (window.__fg_recent_media.length > 100) {
                        window.__fg_recent_media.pop();
                    }

                    const metaStr = JSON.stringify(entry);
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
                'mark_story_read',
                'viewseenat'
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
                'useigdmarkthreadasreadmutation',
                'markdirectthreadseen',
                'mark_direct_thread_seen',
                'direct_v2/threads/seen',
                'direct_v2/threads/mark_seen',
                'direct_v2/threads/mark_read'
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
                'activity_status',
                'typingindicatorstoredprocedure',
                'directsendtyping',
                'direct_send_typing',
                'igdsendtyping'
            ];

            function isDirectSendEndpoint(url) {
                const u = String(url || '').toLowerCase();
                return u.includes('broadcast/text') ||
                       u.includes('broadcast/media') ||
                       u.includes('threads/broadcast') ||
                       u.includes('action=send_message') ||
                       u.includes('send_item') ||
                       u.includes('publish_message');
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
                if (isDirectSendEndpoint(url) || isDirectSendEndpoint(bodyStr)) return null;
                const path = String(window.location?.pathname || '').toLowerCase();
                if (path.startsWith('/accounts') || path.startsWith('/login')) return null;
                const s = window.__focusgramGhostSettings;
                if (!s || !s.enabled) return null;

                const storyActive = s.hideStorySeen !== undefined ? s.hideStorySeen : !!s.igStory;
                const seenActive = s.hideDirectSeen !== undefined ? s.hideDirectSeen : !!s.igSeen;
                const typingActive = s.hideTyping !== undefined ? s.hideTyping : !!s.igTyping;

                const combined = (String(bodyStr || '') + ' ' + String(url || '')).toLowerCase();
                if (storyActive && matchesAny(combined, STORY_PATTERNS)) return 'IG_STORY';
                if (seenActive && matchesAny(combined, SEEN_PATTERNS)) return 'IG_SEEN';
                if (typingActive && matchesAny(combined, TYPING_PATTERNS)) return 'IG_TYPING';
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

            // Visibility and focus spoofing.
            function shouldSpoofVisibility() {
                const s = window.__focusgramGhostSettings;
                if (!s || !s.enabled) return null;
                const storyActive = s.hideStorySeen !== undefined ? s.hideStorySeen : !!s.igStory;
                const path = String(window.location?.pathname || '').toLowerCase();

                if (storyActive && path.startsWith('/stories/')) {
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
                        if (spoof === 'unfocused') return 'hidden';
                        return originalVisibilityState && originalVisibilityState.get ?
                            originalVisibilityState.get.call(document) : 'visible';
                    },
                    configurable: true
                });

                const originalHidden = Object.getOwnPropertyDescriptor(Document.prototype, 'hidden');
                Object.defineProperty(document, 'hidden', {
                    get: function() {
                        const spoof = shouldSpoofVisibility();
                        if (spoof === 'unfocused') return true;
                        return originalHidden && originalHidden.get ?
                            originalHidden.get.call(document) : false;
                    },
                    configurable: true
                });
            } catch (err) {
                console.warn('[FocusGram Ghost] Focus hook notice:', err);
            }

            // Intercept WebSocket frames (MQTT typing indicators and read receipts).
            if (typeof WebSocket !== 'undefined' && WebSocket.prototype && WebSocket.prototype.send) {
                if (!window.__FG_GHOST_WS_HOOKED__) {
                    window.__FG_GHOST_WS_HOOKED__ = true;
                    const originalWsSend = WebSocket.prototype.send;
                    const wsUrls = new WeakMap();

                    const OriginalWebSocket = window.WebSocket;
                    window.WebSocket = new Proxy(OriginalWebSocket, {
                        construct(target, args, newTarget) {
                            const ws = Reflect.construct(target, args, newTarget);
                            try {
                                wsUrls.set(ws, String(args[0] || ''));
                            } catch (_) {}
                            return ws;
                        }
                    });

                    function decodeWsSync(data) {
                        if (!data) return '';
                        try {
                            if (typeof data === 'string') return data;
                            if (data instanceof ArrayBuffer) return new TextDecoder().decode(data);
                            if (ArrayBuffer.isView(data)) return new TextDecoder().decode(data);
                        } catch (_) {}
                        return '';
                    }

                    WebSocket.prototype.send = function(data) {
                        try {
                            const socketUrl = wsUrls.get(this) || this.url || '';
                            const dataText = decodeWsSync(data);
                            const blockType = checkBlock(socketUrl, dataText);
                            if (blockType) {
                                console.log('[FocusGram Ghost] Hooked ' + blockType + ' WebSocket frame: ' + socketUrl);
                                return; // Drop frame
                            }
                        } catch (e) {
                            console.warn('[FocusGram Ghost] WebSocket check error:', e);
                        }
                        return originalWsSend.apply(this, arguments);
                    };
                }
            }

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

                // Main-World GraphQL, Clips, & Feed Ad Filtering & Reel Meta Harvesting & DM Masking
                if (url.includes('/graphql') || url.includes('/api/v1/feed') || url.includes('/api/v1/clips/') || url.includes('/clips/') || url.includes('/direct/')) {
                    try {
                        const contentType = response.headers.get('content-type') || '';
                        if (contentType.includes('json') || contentType.includes('javascript')) {
                            const cloned = response.clone();
                            let data;
                            try {
                                data = await cloned.json();
                            } catch (_) {
                                try {
                                    let text = await cloned.text();
                                    text = text.replace(/^for\s*\(\s*;\s*;\s*\)\s*;\s*/, '');
                                    data = JSON.parse(text);
                                } catch (e) {
                                    return response;
                                }
                            }

                            let directModified = false;
                            if (typeof window.__fgProcessDirectMessagesPayload === 'function') {
                                directModified = window.__fgProcessDirectMessagesPayload(data);
                            }

                            let adModified = false;
                            if (window.__fgFilterConfig && window.__fgFilterConfig.blockAds !== false) {
                                walkAndFilter(data);
                                adModified = true;
                            }

                            if (directModified || adModified) {
                                const newHeaders = new Headers(response.headers);
                                newHeaders.delete('content-length');
                                newHeaders.delete('content-encoding');
                                newHeaders.set('content-type', contentType || 'application/json; charset=utf-8');
                                return new Response(JSON.stringify(data), {
                                    status: response.status,
                                    statusText: response.statusText,
                                    headers: newHeaders
                                });
                            }
                        }
                    } catch (_) {}
                }

                return response;
            };

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

            // Intercept clicks on in-page Back chevron in DM threads/inbox so it never pops history
            document.addEventListener('click', function(e) {
                const path = (window.location.pathname || '').toLowerCase();
                const backBtn = e.target.closest('svg[aria-label="Back"], svg[aria-label="back"], svg[aria-label="Chevron left"], [aria-label="Back"], [aria-label="back"], a[href*="/direct/inbox"], a[href*="/direct/"]')
                    ?.closest('a, button, div[role="button"]');
                if (backBtn) {
                    if (path.startsWith('/direct/t/') || path.startsWith('/direct/threads/') || (path.startsWith('/direct/') && !path.startsWith('/direct/inbox'))) {
                        e.preventDefault();
                        e.stopPropagation();
                        if (typeof window.__focusgramNavigate === 'function') {
                            window.__focusgramNavigate('/direct/inbox/');
                        } else {
                            window.location.href = '/direct/inbox/';
                        }
                        return;
                    }
                    if (path === '/direct' || path === '/direct/' || path.startsWith('/direct/inbox')) {
                        e.preventDefault();
                        e.stopPropagation();
                        if (typeof window.__focusgramNavigate === 'function') {
                            window.__focusgramNavigate('/');
                        } else {
                            window.location.href = '/';
                        }
                        return;
                    }
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

            // Override history.back and history.go in DMs so navigation follows:
            // user's dm -> /direct/inbox -> home screen
            // without popping previous browser history entries (which exposes locked chats).
            const _origHistoryBack = window.history.back;
            window.history.back = function() {
                const path = (window.location.pathname || '').toLowerCase();
                if (path.startsWith('/direct/t/') || path.startsWith('/direct/threads/') || (path.startsWith('/direct/') && !path.startsWith('/direct/inbox'))) {
                    if (typeof window.__focusgramNavigate === 'function') {
                        window.__focusgramNavigate('/direct/inbox/');
                    } else {
                        window.location.href = '/direct/inbox/';
                    }
                    return;
                }
                if (path === '/direct' || path === '/direct/' || path.startsWith('/direct/inbox')) {
                    if (typeof window.__focusgramNavigate === 'function') {
                        window.__focusgramNavigate('/');
                    } else {
                        window.location.href = '/';
                    }
                    return;
                }
                return _origHistoryBack.apply(this, arguments);
            };

            const _origHistoryGo = window.history.go;
            window.history.go = function(delta) {
                if (typeof delta === 'number' && delta < 0) {
                    const path = (window.location.pathname || '').toLowerCase();
                    if (path.startsWith('/direct/t/') || path.startsWith('/direct/threads/') || (path.startsWith('/direct/') && !path.startsWith('/direct/inbox'))) {
                        if (typeof window.__focusgramNavigate === 'function') {
                            window.__focusgramNavigate('/direct/inbox/');
                        } else {
                            window.location.href = '/direct/inbox/';
                        }
                        return;
                    }
                    if (path === '/direct' || path === '/direct/' || path.startsWith('/direct/inbox')) {
                        if (typeof window.__focusgramNavigate === 'function') {
                            window.__focusgramNavigate('/');
                        } else {
                            window.location.href = '/';
                        }
                        return;
                    }
                }
                return _origHistoryGo.apply(this, arguments);
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
                enabled: isGhostActive,
                hideTyping: isGhostActive && !!currentSettings.ghostHideTypingIndicator,
                hideDirectSeen: isGhostActive && !!currentSettings.ghostHideDirectSeen,
                hideStorySeen: isGhostActive && !!currentSettings.ghostHideStorySeen,
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
                blockSuggested: !!currentSettings.hideSuggestedPosts,
                blockAutoplay: currentSettings.noAutoplay !== false,
                storySearchEnabled: currentSettings.storySearchEnabled !== false,
                customBioFontsEnabled: currentSettings.customBioFontsEnabled !== false
            }
        }, "*");
    }

    window.addEventListener("message", (event) => {
        if (event.data && event.data.type === "GHOSTIFY_SETTINGS_REQUEST") {
            syncGhostSettings();
        }
    });

    // Bottom navbar trimming and dynamic margins.
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

            /* Reels viewport and overlay offsets */
            :root {
                --fg-reel-bottom-offset: 16px;
                --fg-reel-scrubber-offset: 2px;
            }

            /* Reels fixed scroller */
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

            /* Video scaling: edge-to-edge presentation */
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
                html[data-fg-explore] main a[href*="/p/"]:not([data-fg-reveal]):not(.fg-unblurred) img,
                html[data-fg-explore] main a[href*="/reel/"]:not([data-fg-reveal]):not(.fg-unblurred) img,
                main a[href*="/explore/"]:not([data-fg-reveal]):not(.fg-unblurred) img {
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

            /* Comment GIFs, sticker picker, and comment media must NEVER be blurred */
            img[src*="giphy"],
            img[src*="tenor"],
            img[alt*="GIF" i],
            img[alt*="gif" i],
            div[role="dialog"] img[src*="giphy"],
            div[aria-modal="true"] img[src*="giphy"],
            article ul img[src*="giphy"],
            ul[class*="comment" i] img,
            form img[src*="giphy"],
            form img[src*="tenor"] {
                filter: none !important;
            }
        `;

        if (currentSettings.hideLikeCounts) {
            css += `
                a[href*="/liked_by/"],
                section a[href*="/liked_by/"],
                span:has(> a[href*="/liked_by/"]),
                div:has(> a[href*="/liked_by/"]),
                section:has(svg[aria-label="Like"]) + div:not(:has(form)):not(:has(input)):not(:has(svg)),
                section:has(svg[aria-label="Unlike"]) + div:not(:has(form)):not(:has(input)):not(:has(svg)),
                section:has(svg[aria-label="Activity Feed"]) + div:not(:has(form)):not(:has(input)):not(:has(svg)) {
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

    // Bottom navbar trimming.
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




    // Reels interaction tracking and history recorder.
    let lastTrackedReelCode = null;
    let lastTrackedCaption = '';
    let lastTrackedAuthor = '';
    let isInitialReelOnTabEntry = true;
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
            isInitialReelOnTabEntry = true;
            lastTrackedReelCode = null;
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

        // Handle initial reel vs subsequent scrolled reels
        if (isInitialReelOnTabEntry) {
            // First reel encountered on tab mount: capture code and send only metadata
            // so session counter starts at 0 without counting the initial landing reel.
            isInitialReelOnTabEntry = false;
            lastTrackedReelCode = code;
            lastTrackedCaption = caption;
            lastTrackedAuthor = author;

            postToNative({
                action: 'reelWatched',
                type: 'meta',
                code: code,
                url: reelUrl,
                title: caption || (author ? ('Reel by @' + author) : ''),
                caption: caption,
                author: author || '',
                thumbnail: ''
            });
            return;
        }

        // Subsequent reel scrolled to: fire IMMEDIATELY with zero artificial delay!
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

    // Attach interaction listeners for Reels tracking to update instantly
    window.addEventListener('scroll', checkAndTrackActiveReel, { passive: true });
    window.addEventListener('scrollend', checkAndTrackActiveReel, { passive: true });
    window.addEventListener('touchend', () => checkAndTrackActiveReel(), { passive: true });
    window.addEventListener('wheel', checkAndTrackActiveReel, { passive: true });
    window.addEventListener('keydown', (e) => {
        if (['ArrowDown', 'ArrowUp', 'PageDown', 'PageUp', 'Space'].includes(e.key)) {
            checkAndTrackActiveReel();
        }
    });
    window.addEventListener('popstate', checkAndTrackActiveReel);
    window.addEventListener('hashchange', checkAndTrackActiveReel);
    window.addEventListener('focusgram:urlchange', checkAndTrackActiveReel);

    // Periodic ticker (every 250ms) to ensure reel transitions are never missed
    setInterval(checkAndTrackActiveReel, 250);

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

    // Reels layout scroller and overlay positioning.
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
                let isSponsored = !!card.querySelector('[aria-label*="Sponsored" i], [aria-label="Ad" i], [aria-label*="advertisement" i], [data-ad-preview], [data-ad-id], a[href*="/ads/"], a[href*="/ad_"], a[href*="/about/ads"], a[href*="ads.instagram.com"], a[href*="facebook.com/ads"]');

                if (!isSponsored) {
                    const strictAdCtas = ['shop now', 'install now', 'get offer'];
                    const badges = card.querySelectorAll('button:not([data-fg-x-act]):not([data-fg-download-btn]), div[role="button"]:not([data-fg-x-act]):not([data-fg-download-btn])');
                    for (let s = 0; s < badges.length; s++) {
                        const el = badges[s];
                        if (el.children.length > 2) continue;
                        const raw = (el.textContent || '').trim().toLowerCase();
                        if (!raw || raw.length > 25) continue;
                        if (/^[\u{1F300}-\u{1F9FF}\s]*ad$/iu.test(raw) || raw === 'sponsored' || raw === 'paid partnership' || raw === 'advertisement') {
                            if (!el.closest('span[dir="auto"]')) {
                                isSponsored = true;
                                break;
                            }
                        }
                        if (strictAdCtas.includes(raw) && !el.closest('span[dir="auto"]')) {
                            isSponsored = true;
                            break;
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

        // Observe modal attributes and tree changes.
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

    // SPA route and modal state observer.
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

    // Share sheet detection.
    let shareSheetActive = false;
    let shareSheetCheckInterval = null;
    let lastShareSheetCheckTime = 0;

    function updateShareSheetState() {
        const now = Date.now();
        if (now - lastShareSheetCheckTime < 150) return;
        lastShareSheetCheckTime = now;

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

    // Unblur reveal engine (tap and 350ms hold).
    const HOLD_MS = 350, MOVE_TOL = 10;
    const root = document.documentElement;
    let timer = 0, sx = 0, sy = 0, swallowUntil = 0;

    function updateFeedFlag() {
      root.toggleAttribute("data-fg-feed", location.pathname === "/" || location.pathname === "");
      root.toggleAttribute("data-fg-explore", location.pathname.startsWith("/explore"));
    }
    updateFeedFlag();
    new MutationObserver(updateFeedFlag).observe(document, { childList: true, subtree: true });
    addEventListener("popstate", updateFeedFlag);

    function getBlurredTarget(el) {
        if (!el) return null;

        // Comment GIFs, sticker picker, and comment controls must NEVER be treated as unblur targets
        if (el.closest('img[src*="giphy"], img[src*="tenor"], img[alt*="GIF" i], img[alt*="gif" i], form, ul[class*="comment" i]')) {
            return null;
        }
        if (el.tagName === 'IMG' && (el.src?.includes('giphy') || el.src?.includes('tenor') || el.alt?.toLowerCase().includes('gif'))) {
            return null;
        }

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

    // In-page reels metadata extractor.
    let lastExtractedReelKey = '';
    function checkAndExtractReelMetadata() {
        const url = window.location.href;
        if (!url.includes('/reel/') && !url.includes('/reels/')) return;

        const videos = Array.from(document.querySelectorAll('video'));
        const activeVideo = videos.find(v => !v.paused && v.getBoundingClientRect().height > 200) ||
                            videos.find(v => {
                                const r = v.getBoundingClientRect();
                                return r.top >= -50 && r.top < window.innerHeight * 0.6;
                            }) || videos[0];

        let title = '';
        let thumbnailUrl = '';
        let author = '';
        let reelShortcode = '';

        if (activeVideo) {
            thumbnailUrl = activeVideo.getAttribute('poster') || '';
            let card = activeVideo.closest('article, section, div[role="presentation"]') || activeVideo.parentElement;
            while (card && card !== document.body && card !== document.documentElement) {
                if (card.tagName === 'ARTICLE' || card.tagName === 'SECTION') break;
                if (card.getBoundingClientRect().height > window.innerHeight * 0.5) break;
                card = card.parentElement;
            }

            if (card) {
                const link = card.querySelector('a[href*="/reel/"], a[href*="/p/"], a[href*="/reels/"]');
                if (link && link.href) {
                    const lm = /\/(?:reel|reels|p)\/([A-Za-z0-9_-]{5,})/.exec(link.href);
                    if (lm) reelShortcode = lm[1];
                }

                const authorEl = card.querySelector('header a[role="link"], a[role="link"][href^="/"]:not([href*="/audio/"]):not([href*="/reel"]), header h2, header h3, header span[dir="auto"]');
                if (authorEl && authorEl.textContent) {
                    author = authorEl.textContent.trim().replace(/^@/, '');
                } else if (authorEl && authorEl.getAttribute('href')) {
                    const m = authorEl.getAttribute('href').match(/^\/([A-Za-z0-9._]+)\/?/);
                    if (m) author = m[1];
                }

                const textCandidates = Array.from(card.querySelectorAll('h1, h2, span[dir="auto"], div[dir="auto"]'));
                for (const el of textCandidates) {
                    if (el.closest('a[href*="/audio/"]') || el.closest('header') || el.closest('button') || el.closest('a[role="link"]')) continue;
                    const txt = (el.textContent || '').trim();
                    const lower = txt.toLowerCase();
                    if (txt.length >= 2 && txt !== author && !lower.includes('follow') && !lower.startsWith('liked by') && !lower.includes('original audio') && !['ad', 'sponsored'].includes(lower)) {
                        title = txt.slice(0, 300);
                        break;
                    }
                }
            }
        }

        if (!reelShortcode) {
            const urlMatch = /\/(?:reel|reels|p)\/([A-Za-z0-9_-]{5,})/.exec(window.location.pathname);
            if (urlMatch && videos.length <= 1) {
                reelShortcode = urlMatch[1];
            }
        }

        if (!title && !author && videos.length <= 1) {
            const ogTitle = document.querySelector('meta[property="og:title"]');
            if (ogTitle && ogTitle.content) title = ogTitle.content.trim();
            if (title) {
                const authorMatch = title.match(/^([^(@:]+)(?:\s*\((@[^)]+)\))?/);
                if (authorMatch) {
                    author = (authorMatch[2] || authorMatch[1]).trim().replace(/^@/, '');
                }
            }
            const ogImage = document.querySelector('meta[property="og:image"]');
            if (!thumbnailUrl && ogImage && ogImage.content) thumbnailUrl = ogImage.content.trim();
        }

        const effectiveUrl = reelShortcode ? `https://www.instagram.com/reel/${reelShortcode}/` : url;
        const dedupeKey = `${reelShortcode || author || ''}___${title || ''}`;

        if (!title && !author && !reelShortcode) {
            return;
        }

        if (lastExtractedReelKey === dedupeKey) return;
        lastExtractedReelKey = dedupeKey;

        postToNative({
            action: 'reelViewed',
            type: 'reelViewed',
            url: effectiveUrl,
            code: reelShortcode || (author ? `reel_${author.toLowerCase()}` : ''),
            title: title || '',
            author: author || '',
            thumbnailUrl: thumbnailUrl || ''
        });
        console.log('[FocusGram] In-page extracted reel metadata:', effectiveUrl);
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
        if (path.includes('/accounts/edit')) {
            return false;
        }

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

    // Chat veil and inbox preview masking.
    let chatLockMode = 'individual';
    let chatLockAll = false;
    let lockedChatIds = new Set();
    const unlockedThreads = new Set();
    const threadUsernameMap = new Map();

    function syncGlobalsToWindow() {
        const targets = [window];
        if (typeof window.wrappedJSObject !== 'undefined' && window.wrappedJSObject) {
            targets.push(window.wrappedJSObject);
        }
        const lockedArray = Array.from(lockedChatIds);
        const unlockedArray = Array.from(unlockedThreads);
        const userMapObj = {};
        for (const [k, v] of threadUsernameMap.entries()) {
            userMapObj[k] = v;
        }

        for (const target of targets) {
            try {
                target.__fgChatLockMode = chatLockMode;
                target.__fgChatLockAll = chatLockAll;
                target.__fgLockedChatIds = lockedArray;
                target.__fgUnlockedThreads = unlockedArray;
                target.__fgThreadUsernameMap = userMapObj;
            } catch (_) {}
        }

        const syncDetail = {
            chatLockMode: chatLockMode,
            chatLockAll: chatLockAll,
            lockedChatIds: lockedArray,
            unlockedThreads: unlockedArray,
            threadUsernameMap: userMapObj
        };
        try {
            window.dispatchEvent(new CustomEvent('focusgram:chat-lock-sync', { detail: syncDetail }));
            document.dispatchEvent(new CustomEvent('focusgram:chat-lock-sync', { detail: syncDetail }));
        } catch (_) {}
    }

    function syncChatLockSettings(settings) {
        if (!settings) return;
        if (typeof settings.chatLockMode !== 'undefined') {
            chatLockMode = String(settings.chatLockMode).toLowerCase();
        }
        if (typeof settings.chatLockAll !== 'undefined') {
            chatLockAll = !!settings.chatLockAll;
            if (chatLockMode === 'whole_dm') {
                chatLockAll = true;
            } else if (chatLockMode === 'off') {
                chatLockAll = false;
            }
        } else if (chatLockMode === 'whole_dm') {
            chatLockAll = true;
        } else if (chatLockMode === 'off') {
            chatLockAll = false;
        }
        if (Array.isArray(settings.lockedChatIds)) {
            lockedChatIds = new Set(settings.lockedChatIds.map(id => String(id).toLowerCase().trim().replace(/^@/, '')));
        }
        if (settings.lockedChatUsernames && typeof settings.lockedChatUsernames === 'object') {
            for (const [tid, uname] of Object.entries(settings.lockedChatUsernames)) {
                const cleanTid = String(tid).toLowerCase().trim();
                const cleanUname = String(uname).toLowerCase().trim().replace(/^@/, '');
                if (cleanTid && cleanUname) {
                    threadUsernameMap.set(cleanTid, cleanUname);
                    threadUsernameMap.set(cleanUname, cleanTid);
                    if (lockedChatIds.has(cleanTid)) lockedChatIds.add(cleanUname);
                    if (lockedChatIds.has(cleanUname)) lockedChatIds.add(cleanTid);
                }
            }
        }

        syncGlobalsToWindow();
        evaluateChatLockState();
        maskInboxChatPreviews();
    }

    const handleThreadMatched = (e) => {
        const detail = e.detail;
        if (detail && detail.threadId) {
            const cleanTid = String(detail.threadId).toLowerCase().trim();
            const cleanKey = detail.threadKey ? String(detail.threadKey).toLowerCase().trim() : '';
            if (detail.isLocked) {
                lockedChatIds.add(cleanTid);
                if (cleanKey) lockedChatIds.add(cleanKey);
            }
            if (Array.isArray(detail.usernames)) {
                for (const u of detail.usernames) {
                    const cleanUser = String(u).toLowerCase().trim().replace(/^@/, '');
                    if (cleanUser) {
                        threadUsernameMap.set(cleanTid, cleanUser);
                        threadUsernameMap.set(cleanUser, cleanTid);
                        if (cleanKey) {
                            threadUsernameMap.set(cleanKey, cleanUser);
                            threadUsernameMap.set(cleanUser, cleanKey);
                        }
                        if (lockedChatIds.has(cleanUser)) {
                            lockedChatIds.add(cleanTid);
                            if (cleanKey) lockedChatIds.add(cleanKey);
                        }
                        if (lockedChatIds.has(cleanTid) || (cleanKey && lockedChatIds.has(cleanKey))) {
                            lockedChatIds.add(cleanUser);
                        }
                    }
                }
            }
            postToNative({
                action: 'threadUsernameMapped',
                threadId: cleanTid,
                threadKey: cleanKey,
                username: (detail.usernames && detail.usernames[0]) || '',
                usernames: detail.usernames || [],
                displayName: detail.displayName || '',
                isLocked: !!detail.isLocked
            });
            syncGlobalsToWindow();
            evaluateChatLockState();
            maskInboxChatPreviews();
        }
    };
    window.addEventListener('focusgram:thread-matched', handleThreadMatched);
    document.addEventListener('focusgram:thread-matched', handleThreadMatched);

    function handleUnlockChat(threadId, isAll) {
        if (isAll) {
            unlockedThreads.add('__all__');
        }
        if (threadId) {
            const clean = String(threadId).toLowerCase().trim();
            unlockedThreads.add(clean);
            const mapped = threadUsernameMap.get(clean);
            if (mapped) unlockedThreads.add(mapped);
        }
        syncGlobalsToWindow();
        removePrivacyVeil();
        evaluateChatLockState();
        maskInboxChatPreviews();
    }

    function handleLockChat(threadId) {
        if (threadId) {
            const clean = String(threadId).toLowerCase().trim();
            unlockedThreads.delete(clean);
            const mapped = threadUsernameMap.get(clean);
            if (mapped) unlockedThreads.delete(mapped);
            lockedChatIds.add(clean);
            if (mapped) lockedChatIds.add(mapped);
        }
        syncGlobalsToWindow();
        evaluateChatLockState();
        maskInboxChatPreviews();
    }

    function extractCurrentThreadId() {
        const path = window.location.pathname.toLowerCase();
        if (!path.startsWith('/direct/t/')) return null;
        const parts = path.replace('/direct/t/', '').split('/').filter(Boolean);
        return parts[0] ? parts[0].trim() : null;
    }

    function isCurrentThreadExplicitlyLocked(threadId) {
        if (!threadId) return false;
        const cleanId = String(threadId).toLowerCase().trim();
        if (lockedChatIds.has(cleanId)) return true;
        const mappedUser = threadUsernameMap.get(cleanId);
        if (mappedUser && lockedChatIds.has(mappedUser)) return true;
        for (const lockedId of lockedChatIds) {
            if (window.location.href.toLowerCase().includes(lockedId)) return true;
        }
        return false;
    }

    function extractThreadParticipants() {
        const currentThreadId = extractCurrentThreadId();
        if (!currentThreadId) return null;

        let username = '';
        let displayName = '';

        // 1. Inspect document.title
        const docTitle = document.title || '';
        const titleMatch = docTitle.match(/@([a-zA-Z0-9_\.]+)/);
        if (titleMatch && titleMatch[1]) {
            username = titleMatch[1].trim();
        } else if (docTitle.includes('•')) {
            const candidate = docTitle.split('•')[0].trim();
            if (candidate && !['direct', 'messages', 'instagram'].includes(candidate.toLowerCase())) {
                displayName = candidate;
            }
        }

        // 2. Inspect header avatar image alt text
        if (!username) {
            const headerImgs = document.querySelectorAll('header img[alt], [role="banner"] img[alt], [role="main"] header img[alt]');
            for (const img of headerImgs) {
                const alt = img.getAttribute('alt') || '';
                const m = alt.match(/(.+?)'s profile picture/i) || alt.match(/profile picture of (.+)/i);
                if (m && m[1]) {
                    username = m[1].trim().replace(/^@/, '');
                    break;
                }
            }
        }

        // 3. Instagram header profile link inspection
        if (!username) {
            const profileLinks = document.querySelectorAll(
                'header a[aria-label*="profile page of" i], [role="banner"] a[aria-label*="profile page of" i], ' +
                '[role="main"] header a[aria-label*="profile page of" i], a[aria-label*="Open the profile page of" i], ' +
                'header a[href^="/"], [role="banner"] a[href^="/"], [role="main"] header a[href^="/"]'
            );

            for (const link of profileLinks) {
                const aria = link.getAttribute('aria-label') || '';
                const matchAria = aria.match(/profile page of (.+)/i);
                if (matchAria && matchAria[1]) {
                    username = matchAria[1].trim().replace(/^@/, '');
                    break;
                }
                const href = link.getAttribute('href') || '';
                const cleanHref = href.replace(/^https?:\/\/[^\/]+/, '');
                const matchHref = cleanHref.match(/^\/([a-zA-Z0-9_\.]+)\/?(?:\?.*)?$/);
                if (matchHref && matchHref[1]) {
                    const candidate = matchHref[1].trim();
                    const ignored = ['direct', 'explore', 'reels', 'reel', 'stories', 'home', 'accounts', 'p', 'tv'];
                    if (!ignored.includes(candidate.toLowerCase())) {
                        username = candidate;
                        break;
                    }
                }
            }
        }

        // 4. Header heading / title elements
        const headingEl = document.querySelector(
            'header [role="heading"], [role="banner"] [role="heading"], ' +
            'header h1, header h2, header h3, [role="banner"] h1, [role="banner"] h2, ' +
            'header a span, [role="banner"] a span'
        );
        if (headingEl) {
            const hText = headingEl.textContent.trim().replace(/^@/, '');
            if (!displayName) displayName = hText;
            if (!username && hText && !hText.includes(' ') && hText.length < 32) {
                username = hText;
            }
        }

        // 5. In-page bio banner at top of message scroller (View profile button)
        if (!username) {
            const viewProfileLink = document.querySelector('a[href^="/"][role="link"]:not([href*="/direct/"])');
            if (viewProfileLink && viewProfileLink.textContent.toLowerCase().includes('profile')) {
                const href = (viewProfileLink.getAttribute('href') || '').replace(/^https?:\/\/[^\/]+/, '');
                const m = href.match(/^\/([a-zA-Z0-9_\.]+)\/?/);
                if (m && m[1]) {
                    username = m[1].trim();
                }
            }
        }

        const cleanUser = username ? username.toLowerCase().trim().replace(/^@/, '') : '';
        const effectiveUser = cleanUser || (displayName ? displayName.toLowerCase().trim().replace(/^@/, '') : '');

        if (effectiveUser) {
            threadUsernameMap.set(currentThreadId, effectiveUser);
            threadUsernameMap.set(effectiveUser, currentThreadId);
            postToNative({
                action: 'threadUsernameMapped',
                threadId: currentThreadId,
                username: effectiveUser,
                usernames: [effectiveUser],
                displayName: displayName || effectiveUser
            });
            window.__fgActiveThread = { threadId: currentThreadId, username: effectiveUser, displayName: displayName || effectiveUser };
            return { username: effectiveUser, displayName: displayName || effectiveUser };
        }
        return null;
    }
    window.__fgGetThreadParticipant = extractThreadParticipants;

    function isCurrentChatLocked() {
        const path = window.location.pathname.toLowerCase();
        if (!path.startsWith('/direct')) return false;
        if (chatLockMode === 'off') return false;
        if (unlockedThreads.has('__all__')) return false;

        const currentThreadId = extractCurrentThreadId();
        if (currentThreadId) {
            if (unlockedThreads.has(currentThreadId)) return false;
            const mappedUser = threadUsernameMap.get(currentThreadId);
            if (mappedUser && unlockedThreads.has(mappedUser)) return false;

            if (chatLockMode === 'whole_dm' || chatLockAll) return true;
            if (isCurrentThreadExplicitlyLocked(currentThreadId)) return true;

            // Search header text as additional safeguard
            const header = document.querySelector('header, [role="banner"], [role="main"] header');
            if (header) {
                const headerText = header.textContent.toLowerCase();
                for (const lockedId of lockedChatIds) {
                    if (headerText.includes(lockedId)) {
                        lockedChatIds.add(currentThreadId);
                        threadUsernameMap.set(currentThreadId, lockedId);
                        postToNative({ action: 'threadUsernameMapped', threadId: currentThreadId, usernames: [lockedId], isLocked: true });
                        return true;
                    }
                }
            }
            return false;
        }

        if ((chatLockMode === 'whole_dm' || chatLockAll) && (path === '/direct' || path === '/direct/' || path.startsWith('/direct/inbox'))) {
            return !unlockedThreads.has('__all__');
        }

        return false;
    }

    function applyPrivacyVeil() {
        let veil = document.getElementById('focusgram-chat-privacy-veil');
        if (!veil) {
            veil = document.createElement('div');
            veil.id = 'focusgram-chat-privacy-veil';
            veil.style.cssText = 'position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;background:#000000!important;z-index:2147483647!important;pointer-events:all!important;display:block!important;';
            (document.body || document.documentElement).appendChild(veil);
        }
        veil.style.display = 'block';
    }
    window.__fgApplyPrivacyVeil = applyPrivacyVeil;

    function removePrivacyVeil() {
        const veil = document.getElementById('focusgram-chat-privacy-veil');
        if (veil) {
            veil.style.display = 'none';
        }
    }
    window.__fgRemovePrivacyVeil = removePrivacyVeil;

    function evaluateChatLockState() {
        if (isCurrentChatLocked()) {
            try {
                document.body?.setAttribute('data-fg-chat-locked', 'true');
                document.documentElement?.setAttribute('data-fg-chat-locked', 'true');
            } catch (_) {}
            applyPrivacyVeil();
        } else {
            try {
                document.body?.removeAttribute('data-fg-chat-locked');
                document.documentElement?.removeAttribute('data-fg-chat-locked');
            } catch (_) {}
            removePrivacyVeil();
        }
    }

    // Capture-phase link click interception for instant pre-render black cover & blocking server queries
    document.addEventListener('click', (e) => {
        if (chatLockMode === 'off') return;
        const link = e.target.closest('a[href*="/direct/t/"]');
        if (!link) return;
        const href = (link.getAttribute('href') || '').toLowerCase();
        const match = href.match(/\/direct\/t\/([^\/\?]+)/);
        const targetThreadId = match ? match[1].trim() : '';
        if (!targetThreadId) return;

        let shouldLock = (chatLockMode === 'whole_dm' || chatLockAll);
        const mapped = threadUsernameMap.get(targetThreadId);
        if (!shouldLock) {
            if (lockedChatIds.has(targetThreadId)) shouldLock = true;
            if (mapped && lockedChatIds.has(mapped)) shouldLock = true;
            if (!shouldLock) {
                const row = link.closest('[role="listitem"], [role="button"], li') || link;
                const rowText = row.textContent.toLowerCase();
                for (const lockedId of lockedChatIds) {
                    if (rowText.includes(lockedId) || href.includes(lockedId)) {
                        shouldLock = true;
                        lockedChatIds.add(targetThreadId);
                        break;
                    }
                }
            }
        }

        const isUnlocked = unlockedThreads.has('__all__') ||
                           unlockedThreads.has(targetThreadId) ||
                           (mapped && unlockedThreads.has(mapped));

        if (shouldLock && !isUnlocked) {
            // Immediately BLOCK the browser and React router from querying the server or rendering the locked chat!
            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();

            applyPrivacyVeil();

            const mappedUser = mapped || '';
            postToNative({
                action: 'requestUnlockChat',
                threadId: targetThreadId,
                username: mappedUser,
                targetUrl: link.href || ('https://www.instagram.com/direct/t/' + targetThreadId + '/')
            });
        }
    }, true);

    function updateChatMaskCSS() {
        let style = document.getElementById('focusgram-chat-mask-style');
        if (!style) {
            style = document.createElement('style');
            style.id = 'focusgram-chat-mask-style';
            (document.head || document.documentElement).appendChild(style);
        }

        const css = `
            /* FocusGram Chat Lock Veil */
            body[data-fg-chat-locked="true"] [role="list"] [role="row"],
            body[data-fg-chat-locked="true"] div[dir="auto"],
            body[data-fg-chat-locked="true"] [data-message-id] {
                filter: blur(14px) !important;
                user-select: none !important;
                pointer-events: none !important;
            }
        `;
        style.textContent = css;
    }

    function maskInboxChatPreviews() {
        updateChatMaskCSS();

        const path = window.location.pathname.toLowerCase();
        if (!path.startsWith('/direct')) return;

        const isLockOff = (chatLockMode === 'off');

        const chatLinks = document.querySelectorAll('a[href*="/direct/t/"]');
        for (const link of chatLinks) {
            const href = (link.getAttribute('href') || '').toLowerCase();
            const match = href.match(/\/direct\/t\/([^\/\?]+)/);
            const threadId = match ? match[1].trim() : '';
            if (!threadId) continue;

            const rowContainer = link.closest('[role="listitem"], [role="button"], li') || link;

            // Strategy A: Avatar image alt inspection
            let detectedUser = '';
            let displayName = '';
            const avatarImg = rowContainer.querySelector('img[alt]');
            if (avatarImg) {
                const alt = (avatarImg.getAttribute('alt') || '').trim();
                const m = alt.match(/(.+?)'s profile picture/i) || alt.match(/profile picture of (.+)/i);
                if (m && m[1]) {
                    detectedUser = m[1].trim().replace(/^@/, '').toLowerCase();
                    displayName = m[1].trim();
                }
            }

            // Strategy B: TreeWalker text node for title element
            const walker = document.createTreeWalker(
                rowContainer,
                NodeFilter.SHOW_TEXT,
                {
                    acceptNode: function(node) {
                        const val = (node.nodeValue || '').trim();
                        if (!val || val === '🔒' || val === '•' || val === '·') {
                            return NodeFilter.FILTER_REJECT;
                        }
                        return NodeFilter.FILTER_ACCEPT;
                    }
                }
            );

            let candidateTitleEl = null;
            const firstNode = walker.nextNode();
            if (firstNode) {
                candidateTitleEl = firstNode.parentElement;
                if (!detectedUser) {
                    const txt = (firstNode.nodeValue || '').trim();
                    detectedUser = txt.replace(/^@/, '').trim().toLowerCase();
                    displayName = txt;
                }
            }

            if (detectedUser && !threadUsernameMap.has(threadId)) {
                threadUsernameMap.set(threadId, detectedUser);
                threadUsernameMap.set(detectedUser, threadId);
                postToNative({
                    action: 'threadUsernameMapped',
                    threadId: threadId,
                    username: detectedUser,
                    usernames: [detectedUser],
                    displayName: displayName || detectedUser
                });
            }

            let isLocked = false;
            if (!isLockOff) {
                if (chatLockMode === 'whole_dm' || chatLockAll) {
                    isLocked = !unlockedThreads.has('__all__');
                } else {
                    if (unlockedThreads.has(threadId)) {
                        isLocked = false;
                    } else {
                        if (lockedChatIds.has(threadId)) isLocked = true;
                        const mappedUser = threadUsernameMap.get(threadId);
                        if (mappedUser && lockedChatIds.has(mappedUser)) isLocked = true;
                        if (detectedUser && lockedChatIds.has(detectedUser)) isLocked = true;
                        if (!isLocked) {
                            const rowText = rowContainer.textContent.toLowerCase();
                            for (const lockedId of lockedChatIds) {
                                if (rowText.includes(lockedId) || href.includes(lockedId)) {
                                    isLocked = true;
                                    lockedChatIds.add(threadId);
                                    break;
                                }
                            }
                        }
                        if (mappedUser && unlockedThreads.has(mappedUser)) isLocked = false;
                        if (detectedUser && unlockedThreads.has(detectedUser)) isLocked = false;
                    }
                }
            }

            // Clean up any legacy preview masking classes or attributes
            const legacyMasked = rowContainer.querySelectorAll('.fg-masked-snippet-text, [data-fg-mask-preview]');
            for (const el of legacyMasked) {
                el.classList.remove('fg-masked-snippet-text');
                el.removeAttribute('data-fg-mask-preview');
            }

            if (isLocked) {
                link.setAttribute('data-fg-locked-chat', 'true');
                rowContainer.setAttribute('data-fg-locked-chat', 'true');

                const titleTarget = candidateTitleEl || rowContainer.querySelector('[role="heading"], h2, h3, span');
                if (titleTarget && !rowContainer.querySelector('.fg-chat-lock-badge')) {
                    const badge = document.createElement('span');
                    badge.className = 'fg-chat-lock-badge';
                    badge.style.cssText = 'margin-left: 6px; font-size: 11px; opacity: 0.85; vertical-align: middle; display: inline-block;';
                    badge.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" style="display:inline-block;vertical-align:middle;opacity:0.85;"><path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/></svg>';
                    titleTarget.appendChild(badge);
                }
            } else {
                link.removeAttribute('data-fg-locked-chat');
                rowContainer.removeAttribute('data-fg-locked-chat');
                const badge = rowContainer.querySelector('.fg-chat-lock-badge');
                if (badge) badge.remove();
            }
        }
    }

    // Attach dynamic MutationObserver on direct messaging container to ensure previews are masked in real time
    if (!window.__fgInboxObserverInstalled) {
        window.__fgInboxObserverInstalled = true;
        const inboxObserver = new MutationObserver(() => {
            const path = window.location.pathname.toLowerCase();
            if (path.startsWith('/direct')) {
                maskInboxChatPreviews();
            }
        });
        inboxObserver.observe(document.body || document.documentElement, {
            childList: true,
            subtree: true
        });
    }

    function checkStateAndNotify() {
        evaluateChatLockState();
        extractThreadParticipants();
        maskInboxChatPreviews();

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
        lastInputFocused = false;
        postToNative({ action: 'inputFocused', focused: false });
        postToNative({ action: 'bioInputFocused', focused: false });
        checkStateAndNotify();
        setTimeout(checkStateAndNotify, 100);
        setTimeout(checkStateAndNotify, 300);
        hideNav();
    });

    window.addEventListener('popstate', () => {
        lastInputFocused = false;
        postToNative({ action: 'inputFocused', focused: false });
        postToNative({ action: 'bioInputFocused', focused: false });
        checkStateAndNotify();
        setTimeout(checkStateAndNotify, 100);
        setTimeout(checkStateAndNotify, 300);
        hideNav();
    });

    window.addEventListener('hashchange', () => {
        lastInputFocused = false;
        postToNative({ action: 'inputFocused', focused: false });
        postToNative({ action: 'bioInputFocused', focused: false });
        checkStateAndNotify();
        setTimeout(checkStateAndNotify, 100);
        hideNav();
    });

    function cleanUpDynamicContent() {
        try {
            // Hide Like Counts dynamic text
            if (currentSettings.hideLikeCounts) {
                // 1. Liked by links and their parent containers
                document.querySelectorAll('a[href*="/liked_by/"]').forEach((el) => {
                    el.style.setProperty('display', 'none', 'important');
                    const parent = el.closest('div');
                    if (parent && !parent.querySelector('svg') && parent.textContent && /liked/i.test(parent.textContent)) {
                        parent.style.setProperty('display', 'none', 'important');
                    }
                });

                // 2. Feed post like counts text (e.g. "Liked by ...", "97 likes", "12 others")
                document.querySelectorAll('article section ~ div span, article section ~ div div, div[role="presentation"] span').forEach((el) => {
                    if (el.querySelector('svg') || el.closest('svg') || el.querySelector('img') || el.closest('form')) return;
                    if (el.children.length > 2) return;
                    const text = (el.textContent || '').trim();
                    if (/^(liked\s+by|[\d,.]+[KkMm]?\s+likes?|[\d,.]+\s+others|be\s+the\s+first\s+to\s+like)/i.test(text)) {
                        el.style.setProperty('display', 'none', 'important');
                    }
                });

                // 3. Standalone count buttons in feed post: <span class="..." role="button">97</span>
                document.querySelectorAll('article span[role="button"], section span[role="button"]').forEach((el) => {
                    if (el.querySelector('svg') || el.closest('svg')) return;
                    const text = (el.textContent || '').trim();
                    if (/^[\d,.]+[KkMm]?$/.test(text)) {
                        el.style.setProperty('display', 'none', 'important');
                    }
                });

                // 4. Reels like count in right interaction rail:
                // Sibling or child span with numbers/K/M (e.g. <span class="html-span ...">192K</span>)
                document.querySelectorAll('svg[aria-label="Like"], svg[aria-label="Unlike"]').forEach((svg) => {
                    const btn = svg.closest('[role="button"]') || svg.parentElement;
                    if (!btn) return;
                    const container = btn.parentElement;
                    if (container) {
                        container.querySelectorAll('span, div').forEach((child) => {
                            if (child.contains(svg) || child.querySelector('svg') || child.closest('svg')) return;
                            const text = (child.textContent || '').trim();
                            if (/^[\d,.]+[KkMm]?$/.test(text) || /^[\d,.]+[KkMm]?\s+likes?$/i.test(text)) {
                                child.style.setProperty('display', 'none', 'important');
                            }
                        });
                        let sib = btn.nextElementSibling;
                        if (sib && !sib.contains(svg) && !sib.querySelector('svg')) {
                            const text = (sib.textContent || '').trim();
                            if (/^[\d,.]+[KkMm]?$/.test(text) || /^[\d,.]+[KkMm]?\s+likes?$/i.test(text)) {
                                sib.style.setProperty('display', 'none', 'important');
                            }
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
    const IG_POST_ROOT_QUERY_DOC_ID = '27852811784380813';

    function getCookie(name) {
        try {
            const value = `; ${document.cookie}`;
            const parts = value.split(`; ${name}=`);
            if (parts.length === 2) return parts.pop().split(';').shift();
        } catch (_) {}
        return '';
    }

    function getFbDtsg() {
        try {
            for (const script of document.scripts) {
                const text = script.textContent || '';
                const match = text.match(/"DTSGInitialData",\[\],\{"token":"([^"]+)"/) || text.match(/"dtsg":\s*\{\s*"token":"([^"]+)"/);
                if (match) return match[1];
            }
        } catch (_) {}
        return '';
    }

    function parseMediaItem(item, slideIndex = 0) {
        if (!item) return null;

        // 1. Carousel check: carousel_media or edge_sidecar_to_children
        if (item.carousel_media && Array.isArray(item.carousel_media) && item.carousel_media.length > 0) {
            const target = item.carousel_media[slideIndex] || item.carousel_media[0];
            const parsed = parseMediaItem(target, 0);
            if (parsed) {
                parsed.id = item.pk || item.id || parsed.id;
                parsed.code = item.code || parsed.code;
                parsed.author = item.user?.username || parsed.author;
                return parsed;
            }
        }
        if (item.edge_sidecar_to_children?.edges && Array.isArray(item.edge_sidecar_to_children.edges) && item.edge_sidecar_to_children.edges.length > 0) {
            const edge = item.edge_sidecar_to_children.edges[slideIndex] || item.edge_sidecar_to_children.edges[0];
            const target = edge?.node;
            if (target) {
                const parsed = parseMediaItem(target, 0);
                if (parsed) {
                    parsed.id = item.id || parsed.id;
                    parsed.code = item.shortcode || parsed.code;
                    parsed.author = item.owner?.username || parsed.author;
                    return parsed;
                }
            }
        }

        const isVideo = !!(item.is_video || item.media_type === 2 || (item.video_versions && item.video_versions.length > 0) || item.video_url);
        let videoUrl = item.video_url || '';
        let imageUrl = item.display_url || '';

        if (item.video_versions && Array.isArray(item.video_versions) && item.video_versions.length > 0) {
            videoUrl = item.video_versions[0]?.url || videoUrl;
        }
        if (item.image_versions2?.candidates && Array.isArray(item.image_versions2.candidates) && item.image_versions2.candidates.length > 0) {
            imageUrl = item.image_versions2.candidates[0]?.url || imageUrl;
        } else if (item.display_resources && Array.isArray(item.display_resources) && item.display_resources.length > 0) {
            imageUrl = item.display_resources[item.display_resources.length - 1]?.src || imageUrl;
        }

        return {
            isVideo: isVideo && !!videoUrl,
            url: (isVideo && videoUrl) ? videoUrl : (imageUrl || videoUrl),
            videoUrl: videoUrl,
            imageUrl: imageUrl,
            code: item.code || item.shortcode || '',
            id: item.pk || item.id || '',
            author: item.user?.username || item.owner?.username || '',
            caption: item.caption?.text || item.edge_media_to_caption?.edges?.[0]?.node?.text || ''
        };
    }

    async function fetchMediaByShortcode(shortcode, slideIndex = 0) {
        if (!shortcode) return null;

        // 1. Try GraphQL PolarisPostRootQuery (Official Instagram-Downloader technique)
        try {
            const apiURL = new URL('/graphql/query', window.location.origin);
            const headers = {
                'content-type': 'application/x-www-form-urlencoded',
                'x-fb-friendly-name': 'PolarisPostRootQuery',
                'x-ig-app-id': '936619743392459',
                'x-requested-with': 'XMLHttpRequest',
            };
            const csrfToken = getCookie('csrftoken');
            if (csrfToken) headers['x-csrftoken'] = csrfToken;

            const fbDtsg = getFbDtsg();
            const bodyParams = {
                fb_api_caller_class: 'RelayModern',
                fb_api_req_friendly_name: 'PolarisPostRootQuery',
                doc_id: IG_POST_ROOT_QUERY_DOC_ID,
                variables: JSON.stringify({
                    shortcode: shortcode,
                    __relay_internal__pv__PolarisShortDramaEnabledrelayprovider: false,
                    __relay_internal__pv__PolarisMultiCaptionCarouselEnabledrelayprovider: false,
                }),
                server_timestamps: 'true',
            };
            if (fbDtsg) bodyParams.fb_dtsg = fbDtsg;

            const res = await fetch(apiURL.href, {
                method: 'POST',
                headers: headers,
                body: new URLSearchParams(bodyParams).toString(),
                credentials: 'include'
            });

            if (res.ok) {
                const json = await res.json();
                const item = json?.data?.['xdt_api__v1__media__shortcode__web_info']?.items?.[0];
                if (item) {
                    return parseMediaItem(item, slideIndex);
                }
            }
        } catch (e) {
            console.warn('[FocusGram] GraphQL shortcode query error:', e);
        }

        // 2. Fallback to /p/{shortcode}/?__a=1&__d=dis
        try {
            const res = await fetch(`/p/${shortcode}/?__a=1&__d=dis`, {
                headers: {
                    'x-ig-app-id': '936619743392459',
                    'x-requested-with': 'XMLHttpRequest'
                },
                credentials: 'include'
            });
            if (res.ok) {
                const json = await res.json();
                const item = json?.items?.[0] || json?.graphql?.shortcode_media;
                if (item) return parseMediaItem(item, slideIndex);
            }
        } catch (_) {}

        return null;
    }

    function getActiveStreamFromPerformance() {
        try {
            const perf = (window.wrappedJSObject && window.wrappedJSObject.performance) || window.performance;
            if (!perf || typeof perf.getEntriesByType !== 'function') return null;
            const entries = perf.getEntriesByType('resource');
            for (let i = entries.length - 1; i >= 0 && i >= entries.length - 100; i--) {
                const entry = entries[i];
                const name = entry.name || '';
                if ((name.includes('cdninstagram.com') || name.includes('fbcdn.net')) &&
                    (name.includes('.mp4') || name.includes('/v/t50') || name.includes('/v/t') || name.includes('bytestart'))) {
                    if (name.startsWith('http://') || name.startsWith('https://')) {
                        try {
                            const u = new URL(name);
                            u.searchParams.delete('bytestart');
                            u.searchParams.delete('byteend');
                            return { url: u.toString(), isVideo: true };
                        } catch (_) {
                            const cleanUrl = name.replace(/[?&]bytestart=\d+/, '').replace(/[?&]byteend=\d+/, '');
                            return { url: cleanUrl, isVideo: true };
                        }
                    }
                }
            }
        } catch (_) {}
        return null;
    }

    function extractFromFiber(rawNode) {
        if (!rawNode) return null;
        try {
            const node = rawNode.wrappedJSObject || rawNode;
            const key = Object.keys(node).find(k => k.startsWith('__reactFiber') || k.startsWith('__reactProps'));
            if (!key) return null;
            let fiber = node[key];
            for (let i = 0; i < 40 && fiber; i++) {
                const props = fiber.memoizedProps || fiber.pendingProps;
                if (props) {
                    if (props.video_versions && Array.isArray(props.video_versions) && props.video_versions[0]?.url) {
                        return { url: props.video_versions[0].url, isVideo: true, code: props.code || props.shortcode || '' };
                    }
                    if (props.media?.video_versions?.[0]?.url) {
                        return { url: props.media.video_versions[0].url, isVideo: true, code: props.media.code || '' };
                    }
                    if (props.item?.video_versions?.[0]?.url) {
                        return { url: props.item.video_versions[0].url, isVideo: true, code: props.item.code || '' };
                    }
                    if (props.clip?.video_versions?.[0]?.url) {
                        return { url: props.clip.video_versions[0].url, isVideo: true, code: props.clip.code || '' };
                    }
                    if (props.post?.video_versions?.[0]?.url) {
                        return { url: props.post.video_versions[0].url, isVideo: true, code: props.post.code || '' };
                    }
                    if (props.videoUrl && typeof props.videoUrl === 'string' && props.videoUrl.startsWith('http')) {
                        return { url: props.videoUrl, isVideo: true, code: props.code || '' };
                    }
                    if (props.code && typeof props.code === 'string') {
                        return { code: props.code };
                    }
                    if (props.shortcode && typeof props.shortcode === 'string') {
                        return { code: props.shortcode };
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
            const fiberData = extractFromFiber(video) || extractFromFiber(video.parentElement) || extractFromFiber(container);
            if (fiberData && fiberData.url) {
                return fiberData;
            }

            const vSrc = video.src || video.currentSrc || '';
            if (vSrc && (vSrc.startsWith('http://') || vSrc.startsWith('https://')) && !vSrc.startsWith('blob:')) {
                return { url: vSrc, isVideo: true };
            }
            const source = video.querySelector('source');
            if (source && source.src && (source.src.startsWith('http://') || source.src.startsWith('https://')) && !source.src.startsWith('blob:')) {
                return { url: source.src, isVideo: true };
            }
        }

        const fiberContainer = extractFromFiber(container);
        if (fiberContainer && fiberContainer.url) {
            return fiberContainer;
        }

        // 2. Image check (best candidate from srcset or src)
        const imgs = Array.from(container.querySelectorAll('img')).filter(img => {
            const alt = (img.alt || '').toLowerCase();
            if (alt.includes('profile picture') || alt.includes("'s profile")) return false;
            const r = img.getBoundingClientRect();
            if (r.width > 0 && r.width < 80) return false;
            return true;
        });
        const mainImg = imgs[0];
        if (mainImg) {
            if (mainImg.srcset) {
                const parts = mainImg.srcset.split(',').map(s => s.trim().split(/\s+/));
                const best = parts[parts.length - 1]?.[0];
                if (best && (best.startsWith('http://') || best.startsWith('https://'))) {
                    return { url: best.replace(/&amp;/g, '&'), isVideo: false };
                }
            }
            const iSrc = mainImg.currentSrc || mainImg.src;
            if (iSrc && (iSrc.startsWith('http://') || iSrc.startsWith('https://'))) {
                return { url: iSrc.replace(/&amp;/g, '&'), isVideo: false };
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

        return null;
    }

    // Note: Media downloading (Feed, Reels, Profile Avatar) is handled exclusively by media_downloader.js

    function filterFeedAds() {
        if (currentSettings.noAds === false) return;
        const SPONSORED_KW = [
            'sponsored', 'gesponsert', 'sponsorisé', 'publicidad', 
            'patrocinado', 'sponsorizzato', 'paid partnership', 'werbung', 'anuncio', 'werbeanzeige'
        ];
        const STRICT_AD_CTAS = [
            'shop now', 'install now', 'apply now', 'book now', 'order now', 'get offer'
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
                article.style.setProperty('height', '1px', 'important');
                article.style.setProperty('min-height', '1px', 'important');
                article.style.setProperty('max-height', '1px', 'important');
                article.style.setProperty('overflow', 'hidden', 'important');
                article.style.setProperty('opacity', '0', 'important');
                article.style.setProperty('pointer-events', 'none', 'important');
                article.style.setProperty('margin', '0', 'important');
                article.style.setProperty('padding', '0', 'important');
            }
        });
    }

    async function downloadActiveReel() {
        if (typeof window.__fgDownloadActiveReel === 'function' && window.__fgDownloadActiveReel !== downloadActiveReel) {
            window.__fgDownloadActiveReel();
        } else {
            window.dispatchEvent(new CustomEvent('focusgram:download-reel', { bubbles: true }));
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
    }, 250);

    // Input focus and typing bridge.
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

    // Native messaging and SPA route navigation.
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
            if (currentSettings.storySearchEnabled === false) {
                const existing = document.getElementById('fg-story-search-card');
                if (existing) {
                    existing.remove();
                    storySearchCardEl = null;
                }
                const noRes = document.getElementById('fg-no-viewer-results');
                if (noRes) noRes.remove();
                document.querySelectorAll('div[role="dialog"] [data-fg-viewer-hidden]').forEach(el => {
                    el.style.display = '';
                    delete el.dataset.fgViewerHidden;
                });
            }
            if (currentSettings.customBioFontsEnabled === false) {
                if (bioToolbarEl) {
                    bioToolbarEl.remove();
                    bioToolbarEl = null;
                }
                const inlineBar = document.getElementById('fg-bio-inline-bar');
                if (inlineBar) inlineBar.remove();
                postToNative({ action: 'bioInputFocused', focused: false });
            }
            updateGuardrailsCSS();
            syncGhostSettings();
            syncChatLockSettings(msg.settings);
            return;
        } else if (msg.action === 'unlockChat') {
            handleUnlockChat(msg.threadId, msg.all);
            return;
        } else if (msg.action === 'lockChat') {
            handleLockChat(msg.threadId);
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
                noReels: config.hideReels !== undefined ? config.hideReels : currentSettings.hideReels,
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
                storySearchEnabled: config.storySearchEnabled !== undefined ? config.storySearchEnabled : currentSettings.storySearchEnabled,
                customBioFontsEnabled: config.customBioFontsEnabled !== undefined ? config.customBioFontsEnabled : currentSettings.customBioFontsEnabled,
                ghostModeEnabled: ghost.enabled !== undefined ? !!ghost.enabled : currentSettings.ghostModeEnabled,
                ghostHideStorySeen: ghost.hideStorySeen !== undefined ? !!ghost.hideStorySeen : (ghost.igStory !== undefined ? !!ghost.igStory : currentSettings.ghostHideStorySeen),
                ghostHideDirectSeen: ghost.hideDirectSeen !== undefined ? !!ghost.hideDirectSeen : (ghost.igSeen !== undefined ? !!ghost.igSeen : currentSettings.ghostHideDirectSeen),
                ghostHideTypingIndicator: ghost.hideTyping !== undefined ? !!ghost.hideTyping : (ghost.igTyping !== undefined ? !!ghost.igTyping : currentSettings.ghostHideTypingIndicator)
            });

            if (currentSettings.storySearchEnabled === false) {
                const existing = document.getElementById('fg-story-search-card');
                if (existing) {
                    existing.remove();
                    storySearchCardEl = null;
                }
                const noRes = document.getElementById('fg-no-viewer-results');
                if (noRes) noRes.remove();
                document.querySelectorAll('div[role="dialog"] [data-fg-viewer-hidden]').forEach(el => {
                    el.style.display = '';
                    delete el.dataset.fgViewerHidden;
                });
            }

            if (currentSettings.customBioFontsEnabled === false) {
                if (bioToolbarEl) {
                    bioToolbarEl.remove();
                    bioToolbarEl = null;
                }
                const inlineBar = document.getElementById('fg-bio-inline-bar');
                if (inlineBar) inlineBar.remove();
                const fontToolbar = document.getElementById('fg-bio-font-toolbar');
                if (fontToolbar) fontToolbar.remove();
                postToNative({ action: 'bioInputFocused', focused: false });
            }
            updateGuardrailsCSS();
            syncGhostSettings();
        }

        if (event.data && event.data.type === 'FOCUSGRAM_FILTER_UPDATE' && event.data.config) {
            const cfg = event.data.config;
            if (cfg.storySearchEnabled !== undefined) {
                currentSettings.storySearchEnabled = cfg.storySearchEnabled;
            }
            if (cfg.customBioFontsEnabled !== undefined) {
                currentSettings.customBioFontsEnabled = cfg.customBioFontsEnabled;
            }
            if (currentSettings.storySearchEnabled === false) {
                const existing = document.getElementById('fg-story-search-card');
                if (existing) {
                    existing.remove();
                    storySearchCardEl = null;
                }
                const noRes = document.getElementById('fg-no-viewer-results');
                if (noRes) noRes.remove();
                document.querySelectorAll('div[role="dialog"] [data-fg-viewer-hidden]').forEach(el => {
                    el.style.display = '';
                    delete el.dataset.fgViewerHidden;
                });
            }
            if (currentSettings.customBioFontsEnabled === false) {
                if (bioToolbarEl) {
                    bioToolbarEl.remove();
                    bioToolbarEl = null;
                }
                const inlineBar = document.getElementById('fg-bio-inline-bar');
                if (inlineBar) inlineBar.remove();
                const fontToolbar = document.getElementById('fg-bio-font-toolbar');
                if (fontToolbar) fontToolbar.remove();
                postToNative({ action: 'bioInputFocused', focused: false });
            }
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

    function postToNative(message) {
        if (!message) return;
        try {
            if (typeof browser !== 'undefined' && browser.runtime) {
                if (browser.runtime.sendNativeMessage) {
                    browser.runtime.sendNativeMessage("focusgram", message).catch(() => {});
                }
                if (browser.runtime.sendMessage) {
                    browser.runtime.sendMessage(message).catch(() => {});
                }
            }
        } catch (e) {
            console.warn('[FocusGram] postToNative error:', e);
        }
    }

    // Bridge window events from external/page scripts to WebExtension native IPC
    window.addEventListener('focusgram:download-request', (e) => {
        if (!e || !e.detail) return;
        postToNative({
            action: 'downloadMedia',
            url: e.detail.url,
            filename: e.detail.filename,
            error: e.detail.error
        });
    });
    window.addEventListener('message', (e) => {
        if (e && e.data && e.data.type === 'FOCUSGRAM_DOWNLOAD_REQUEST') {
            postToNative({
                action: 'downloadMedia',
                url: e.data.url,
                filename: e.data.filename,
                error: e.data.error
            });
        }
    });

    // Detect theme state and notify native host.
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
        const applyLoadedSettings = (response) => {
            if (response && response.settings) {
                Object.assign(currentSettings, response.settings);
                if (response.settings.unblurMode) {
                    mode = response.settings.unblurMode;
                }
                updateGuardrailsCSS();
                syncGhostSettings();
                syncChatLockSettings(response.settings);
            }
        };

        try {
            const res = browser.runtime.sendMessage({ action: 'getSettings' }, applyLoadedSettings);
            if (res && typeof res.then === 'function') {
                res.then(applyLoadedSettings).catch(() => {});
            }
        } catch (_) {}
    }

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

    // Trigger scroll events and click sentinels so infinite scroll continues after ad removal.
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
    try {
        window.__fgDownloadActiveReel = downloadActiveReel;
        if (typeof window.wrappedJSObject !== 'undefined') {
            window.wrappedJSObject.__fgDownloadActiveReel = downloadActiveReel;
        }
    } catch (_) {}

    // Bio font stylist keyboard accessory and real-time input conversion.
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
    let bioToolbarEl = null;
    let bioCounterEl = null;
    let activeBioTarget = null;

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
        const id = (el.id || '').toLowerCase();
        const placeholder = (el.placeholder || '').toLowerCase();
        const aria = (el.getAttribute('aria-label') || '').toLowerCase();
        if (name === 'biography' || name.includes('bio') || id.includes('bio') || placeholder.includes('bio') || aria.includes('bio')) return true;
        const path = (window.location.pathname || '').toLowerCase();
        if (path.includes('/accounts/edit') && (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT')) return true;
        return false;
    }

    function updateBioCharCounter() {
        if (!bioCounterEl || !activeBioTarget) return;
        const text = activeBioTarget.value || activeBioTarget.textContent || '';
        const codePoints = Array.from(text).length;
        const utf16Length = text.length;

        bioCounterEl.textContent = `${codePoints} / 150`;
        if (codePoints > 140 || utf16Length > 140) {
            bioCounterEl.style.color = '#ef4444';
        } else if (codePoints > 120) {
            bioCounterEl.style.color = '#f59e0b';
        } else {
            bioCounterEl.style.color = '#8e8e8e';
        }
    }

    function updateBioToolbarPosition() {
        if (!bioToolbarEl) return;
        if (window.visualViewport) {
            const kbHeight = window.innerHeight - (window.visualViewport.offsetTop + window.visualViewport.height);
            bioToolbarEl.style.bottom = Math.max(0, Math.round(kbHeight)) + 'px';
        } else {
            bioToolbarEl.style.bottom = '0px';
        }
    }

    function createBioFontToolbar() {
        if (currentSettings.customBioFontsEnabled === false) return null;
        if (document.getElementById('fg-bio-font-toolbar')) {
            return document.getElementById('fg-bio-font-toolbar');
        }

        const toolbar = document.createElement('div');
        toolbar.id = 'fg-bio-font-toolbar';
        toolbar.style.cssText = `
            position: fixed !important;
            bottom: 0 !important;
            left: 0 !important;
            right: 0 !important;
            z-index: 2147483647 !important;
            background: rgba(18, 18, 18, 0.96) !important;
            backdrop-filter: blur(24px) !important;
            -webkit-backdrop-filter: blur(24px) !important;
            border-top: 1px solid rgba(255, 255, 255, 0.15) !important;
            padding: 8px 12px !important;
            display: none !important;
            align-items: center !important;
            justify-content: space-between !important;
            gap: 8px !important;
            box-shadow: 0 -4px 24px rgba(0, 0, 0, 0.7) !important;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important;
            user-select: none !important;
            -webkit-user-select: none !important;
            transition: bottom 0.15s ease-out !important;
        `;

        const chipsScroll = document.createElement('div');
        chipsScroll.style.cssText = `
            display: flex !important;
            align-items: center !important;
            gap: 6px !important;
            overflow-x: auto !important;
            scrollbar-width: none !important;
            -webkit-overflow-scrolling: touch !important;
            flex: 1 !important;
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

        STYLES.forEach(style => {
            const chip = document.createElement('button');
            chip.type = 'button';
            chip.className = 'fg-font-chip';
            chip.dataset.styleId = style.id;
            chip.textContent = style.label;
            const isSelected = style.id === activeBioStyle;
            chip.style.cssText = `
                padding: 6px 14px !important;
                border-radius: 16px !important;
                border: 1px solid ${isSelected ? '#ffffff' : 'rgba(255, 255, 255, 0.15)'} !important;
                background: ${isSelected ? '#ffffff' : '#262626'} !important;
                color: ${isSelected ? '#000000' : '#d1d5db'} !important;
                font-size: 13px !important;
                font-weight: ${isSelected ? '600' : '500'} !important;
                white-space: nowrap !important;
                cursor: pointer !important;
                outline: none !important;
                transition: background 0.15s ease, color 0.15s ease !important;
            `;

            chip.addEventListener('click', (e) => {
                e.preventDefault();
                setBioActiveStyle(style.id);
            });

            chipsScroll.appendChild(chip);
        });

        const counter = document.createElement('span');
        counter.id = 'fg-bio-counter';
        counter.style.cssText = `
            font-size: 11px !important;
            font-weight: 600 !important;
            color: #8e8e8e !important;
            white-space: nowrap !important;
            padding: 4px 8px !important;
            border-radius: 12px !important;
            background: rgba(255, 255, 255, 0.05) !important;
            border: 1px solid rgba(255, 255, 255, 0.08) !important;
        `;
        counter.textContent = '0 / 150';

        toolbar.appendChild(chipsScroll);
        toolbar.appendChild(counter);
        (document.body || document.documentElement).appendChild(toolbar);

        bioToolbarEl = toolbar;
        bioCounterEl = counter;
        return toolbar;
    }

    function setBioActiveStyle(styleId) {
        activeBioStyle = styleId;
        if (bioToolbarEl) {
            bioToolbarEl.querySelectorAll('.fg-font-chip').forEach(c => {
                const isActive = c.dataset.styleId === activeBioStyle;
                c.style.border = `1px solid ${isActive ? '#ffffff' : 'rgba(255, 255, 255, 0.15)'}`;
                c.style.background = isActive ? '#ffffff' : '#262626';
                c.style.color = isActive ? '#000000' : '#d1d5db';
                c.style.fontWeight = isActive ? '600' : '500';
            });
        }

        const target = activeBioTarget || document.getElementById('pepBio') || document.querySelector('textarea[aria-label="Bio"]');
        if (target) {
            const val = target.value || '';
            if (val && target.selectionStart !== undefined && target.selectionEnd !== undefined && target.selectionStart !== target.selectionEnd) {
                const start = target.selectionStart;
                const end = target.selectionEnd;
                const before = val.substring(0, start);
                const selectedText = val.substring(start, end);
                const after = val.substring(end);
                const converted = convertStringToStyle(selectedText, activeBioStyle);
                try {
                    const proto = window.HTMLTextAreaElement ? window.HTMLTextAreaElement.prototype : Object.getPrototypeOf(target);
                    const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
                    if (setter) setter.call(target, before + converted + after);
                    else target.value = before + converted + after;
                    target.dispatchEvent(new Event('input', { bubbles: true }));
                } catch (_) {
                    target.value = before + converted + after;
                }
                target.setSelectionRange(start, start + converted.length);
            }
            // If no text selected, do NOT alter existing text!
            updateBioCharCounter();
        }
    }

    window.__fgSetBioStyle = setBioActiveStyle;

    function showBioFontToolbar(target) {
        if (currentSettings.customBioFontsEnabled === false) {
            hideBioFontToolbar();
            return;
        }
        activeBioTarget = target;
        createBioFontToolbar();
        if (bioToolbarEl) {
            updateBioToolbarPosition();
            bioToolbarEl.style.display = 'flex';
            updateBioCharCounter();
        }
    }

    function hideBioFontToolbar() {
        if (bioToolbarEl) {
            bioToolbarEl.style.display = 'none';
        }
        if (currentSettings.customBioFontsEnabled === false) {
            const tb = document.getElementById('fg-bio-font-toolbar');
            if (tb) tb.remove();
            bioToolbarEl = null;
            const ib = document.getElementById('fg-bio-inline-bar');
            if (ib) ib.remove();
        }
    }

    // Intercept keystrokes on document capture level for instant Unicode glyph conversion
    document.addEventListener('beforeinput', (e) => {
        if (currentSettings.customBioFontsEnabled === false) return;
        if (!activeBioTarget || activeBioStyle === 'normal') return;
        if (e.data && e.inputType === 'insertText') {
            e.preventDefault();
            const converted = convertStringToStyle(e.data, activeBioStyle);
            document.execCommand('insertText', false, converted);
            updateBioCharCounter();
        }
    }, true);

    document.addEventListener('input', (e) => {
        if (activeBioTarget && (e.target === activeBioTarget || isBioTarget(e.target))) {
            updateBioCharCounter();
        }
    }, true);

    // Dynamic focusin detection across document
    document.addEventListener('focusin', (e) => {
        if (currentSettings.customBioFontsEnabled === false) return;
        if (isBioTarget(e.target)) {
            showBioFontToolbar(e.target);
            postToNative({ action: 'bioInputFocused', focused: true });
        }
    }, true);

    document.addEventListener('focusout', () => {
        setTimeout(() => {
            const active = document.activeElement;
            if (!isBioTarget(active) && (!bioToolbarEl || !bioToolbarEl.contains(active))) {
                hideBioFontToolbar();
                postToNative({ action: 'bioInputFocused', focused: false });
            }
        }, 250);
    }, true);

    if (window.visualViewport) {
        window.visualViewport.addEventListener('resize', updateBioToolbarPosition);
        window.visualViewport.addEventListener('scroll', updateBioToolbarPosition);
    }

    // Listen for font style changes from background or native host
    if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.onMessage) {
        browser.runtime.onMessage.addListener((msg) => {
            if (msg.action === 'setBioStyle' && msg.style) {
                setBioActiveStyle(msg.style);
            }
        });
    }

    // Story viewer search triggered by list_reel_media_viewer network activity.
    let storySearchCardEl = null;
    let storyViewersData = [];

    function findViewersDialog() {
        const dialogs = document.querySelectorAll('div[aria-modal="true"][role="dialog"], div[role="dialog"]');
        for (const d of dialogs) {
            const headings = Array.from(d.querySelectorAll('h2, [role="heading"], div'));
            const h2 = headings.find(el => el.textContent.trim().toLowerCase() === 'viewers' && el.children.length === 0);
            if (h2) {
                const headerRow = h2.parentElement;
                const scrollContainer = d.querySelector('div[style*="overflow"]') || 
                                        d.querySelector('div[style*="height"]') ||
                                        (headerRow ? headerRow.nextElementSibling : null);
                return { dialog: d, header: headerRow, scrollContainer: scrollContainer, h2: h2 };
            }
        }
        return null;
    }

    function ensureStorySearchCard() {
        if (document.getElementById('fg-story-search-card')) {
            return document.getElementById('fg-story-search-card');
        }

        const card = document.createElement('div');
        card.id = 'fg-story-search-card';
        card.style.cssText = `
            margin: 6px 16px 10px 16px !important;
            padding: 0 12px !important;
            height: 38px !important;
            background: #262626 !important;
            border: 1px solid rgba(255, 255, 255, 0.12) !important;
            border-radius: 8px !important;
            display: flex !important;
            align-items: center !important;
            gap: 10px !important;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important;
            color: #FAFAFA !important;
            box-sizing: border-box !important;
            flex-shrink: 0 !important;
        `;

        const searchIcon = document.createElement('span');
        searchIcon.style.cssText = 'display: flex; align-items: center; color: #8e8e8e;';
        searchIcon.innerHTML = `<svg aria-label="Search" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" height="15" viewBox="0 0 24 24" width="15"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>`;

        const searchInput = document.createElement('input');
        searchInput.id = 'fg-story-search-input';
        searchInput.type = 'text';
        searchInput.placeholder = 'Search viewers';
        searchInput.autocomplete = 'off';
        searchInput.style.cssText = `
            flex: 1 !important;
            background: transparent !important;
            border: none !important;
            color: #FAFAFA !important;
            font-size: 14px !important;
            outline: none !important;
            padding: 0 !important;
            height: 100% !important;
            font-family: inherit !important;
        `;

        const clearBtn = document.createElement('button');
        clearBtn.type = 'button';
        clearBtn.textContent = '✕';
        clearBtn.style.cssText = `
            background: transparent !important;
            border: none !important;
            color: #8e8e8e !important;
            font-size: 13px !important;
            cursor: pointer !important;
            padding: 4px !important;
            display: none !important;
            line-height: 1 !important;
        `;

        clearBtn.addEventListener('click', (e) => {
            e.preventDefault();
            searchInput.value = '';
            clearBtn.style.display = 'none';
            filterViewers('');
            searchInput.focus();
        });

        searchInput.addEventListener('input', () => {
            const val = searchInput.value;
            clearBtn.style.display = val.length > 0 ? 'block' : 'none';
            filterViewers(val.trim().toLowerCase());
        });

        card.appendChild(searchIcon);
        card.appendChild(searchInput);
        card.appendChild(clearBtn);

        const info = findViewersDialog();
        if (info && info.header) {
            info.header.after(card);
        }

        storySearchCardEl = card;
        return card;
    }

    function filterViewers(query) {
        const info = findViewersDialog();
        if (!info || !info.dialog) return;

        const scrollContainer = info.scrollContainer || 
                                info.dialog.querySelector('div[style*="overflow"]') || 
                                info.dialog.querySelector('div[style*="height"]') ||
                                info.dialog;

        const rows = scrollContainer.querySelectorAll('.x1qnrgzn, div[style*="display: flex"] > div:has(a[href^="/"]), div[style*="padding"] > div:has(a[href^="/"])');
        let matchedCount = 0;
        let totalCount = 0;

        rows.forEach(row => {
            if (row.id === 'fg-story-search-card' || row.contains(document.getElementById('fg-story-search-card'))) return;
            const links = row.querySelectorAll('a[href^="/"]');
            if (links.length === 0) return;

            totalCount++;
            if (!query) {
                row.style.display = '';
                matchedCount++;
                return;
            }

            const text = (row.textContent || '').toLowerCase();
            let matched = text.includes(query);
            for (let i = 0; i < links.length; i++) {
                const href = (links[i].getAttribute('href') || '').toLowerCase().replace(/\//g, '');
                if (href.includes(query)) {
                    matched = true;
                    break;
                }
            }

            row.style.display = matched ? '' : 'none';
            if (matched) matchedCount++;
        });

        let noResultsEl = scrollContainer.querySelector('#fg-no-viewer-results');
        if (query && matchedCount === 0 && totalCount > 0) {
            if (!noResultsEl) {
                noResultsEl = document.createElement('div');
                noResultsEl.id = 'fg-no-viewer-results';
                noResultsEl.style.cssText = 'padding: 24px; text-align: center; color: #8e8e8e; font-size: 14px;';
                noResultsEl.textContent = 'No viewers found';
                scrollContainer.appendChild(noResultsEl);
            }
            noResultsEl.style.display = 'block';
        } else if (noResultsEl) {
            noResultsEl.style.display = 'none';
        }
    }

    function checkAndMountStorySearch() {
        if (currentSettings.storySearchEnabled === false) {
            const existing = document.getElementById('fg-story-search-card');
            if (existing) {
                existing.remove();
                storySearchCardEl = null;
            }
            return;
        }

        const info = findViewersDialog();
        if (!info || !info.header) {
            const existing = document.getElementById('fg-story-search-card');
            if (existing && !document.querySelector('div[aria-modal="true"][role="dialog"]')) {
                existing.remove();
                storySearchCardEl = null;
            }
            return;
        }

        if (info.dialog.querySelector('#fg-story-search-card')) {
            return;
        }

        ensureStorySearchCard();
    }

    function handleStoryViewersDetected(detail) {
        if (!detail) return;
        if (currentSettings.storySearchEnabled === false) return;
        checkAndMountStorySearch();

        if (detail.phase === 'ready' && detail.data) {
            const data = detail.data;
            const users = data.users || [];
            const viewers = data.viewers || [];
            const userMap = new Map();
            users.forEach(u => {
                const pk = String(u.pk || u.id || '');
                if (pk) userMap.set(pk, { username: u.username || '', fullName: u.full_name || '' });
            });
            viewers.forEach(v => {
                const u = v.user || v;
                const pk = String(u.pk || u.id || '');
                if (pk) userMap.set(pk, { username: u.username || '', fullName: u.full_name || '' });
            });
            storyViewersData = Array.from(userMap.values());
            const input = document.getElementById('fg-story-search-input');
            if (input && input.value.trim()) {
                filterViewers(input.value.trim().toLowerCase());
            }
        }
    }

    // Listen to main-world fetch interceptor custom events
    window.addEventListener('focusgram:story-viewers-detected', (e) => {
        handleStoryViewersDetected(e.detail);
    });
    document.addEventListener('focusgram:story-viewers-detected', (e) => {
        handleStoryViewersDetected(e.detail);
    });

    // Listen to WebExtension background script messages
    if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.onMessage) {
        browser.runtime.onMessage.addListener((msg) => {
            if (msg.action === 'storyViewersDetected') {
                handleStoryViewersDetected(msg);
            }
        });
    }

    // Continuous autonomous observer for Viewers modal
    const viewersObserver = new MutationObserver(() => {
        checkAndMountStorySearch();
    });
    viewersObserver.observe(document.documentElement, { childList: true, subtree: true });
    setInterval(checkAndMountStorySearch, 400);

    // Dismiss search bar on route change away from stories
    window.addEventListener('focusgram:urlchange', (e) => {
        const url = (e.detail || window.location.href).toLowerCase();
        if (!url.includes('/stories/')) {
            if (storySearchCardEl) {
                storySearchCardEl.remove();
                storySearchCardEl = null;
            }
        }
    });

})();



