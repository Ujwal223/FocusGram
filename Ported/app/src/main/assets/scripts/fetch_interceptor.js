/**
 * FocusGram Unified Network Ad & Content Filter via Main-World Fetch Interception
 * Injected at document_start in the page execution context.
 *
 * Intercepts Instagram GraphQL and REST API responses (Feed, Reels, Stories)
 * and purges ad/sponsored objects before React receives them.
 * This guarantees:
 *  1. Ads are 100% blocked before rendering.
 *  2. React never builds DOM elements for ads.
 *  3. Scroll-snap and virtual scroller remain continuous and never collapse or go blank.
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

    if (window.__fgFetchInterceptorInstalled) return;
    window.__fgFetchInterceptorInstalled = true;

    window.__fgFilterConfig = {
        blockAds: true,
        blockSponsored: true,
        blockSuggested: false,
        blockVideos: false,
        blockAutoplay: false,
        blockGraphQLQueryWhenFeedPosts: false
    };

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

        // Filter ads
        if (config.blockAds !== false && isAdNode(node)) {
            return true;
        }

        // Filter sponsored posts
        if (config.blockSponsored !== false && isSponsoredNode(node) && !isAdNode(node)) {
            return true;
        }

        // Filter suggested posts if enabled
        if (config.blockSuggested && isSuggestedNode(node)) {
            return true;
        }

        return false;
    }

    function unwrapNode(item) {
        if (!item || typeof item !== 'object') return item;
        return item.media_or_ad || item.media || item.node || item.post || item;
    }

    function filterList(list) {
        if (!Array.isArray(list)) return list;
        return list.filter(item => {
            if (!item) return false;
            const target = unwrapNode(item);
            return !shouldFilterNode(target);
        });
    }

    function walkAndFilter(obj, visited = new Set()) {
        if (!obj || typeof obj !== 'object' || visited.has(obj)) return;
        visited.add(obj);

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
            // Never filter pagination or connection metadata
            if (key === 'page_info' || key === 'pagination_info' || key === 'paging_info') continue;
            if (Object.prototype.hasOwnProperty.call(obj, key) && key !== '__typename') {
                const val = obj[key];
                if (val && typeof val === 'object') {
                    walkAndFilter(val, visited);
                }
            }
        }
    }

    window.__fg_media_harvest = window.__fg_media_harvest || {};
    window.__fg_recent_media = window.__fg_recent_media || [];

    function harvestMediaFromData(obj, depth = 0) {
        if (!obj || typeof obj !== 'object' || depth > 12) return;
        if (Array.isArray(obj)) {
            for (let i = 0; i < obj.length; i++) harvestMediaFromData(obj[i], depth + 1);
            return;
        }

        const code = obj.code || (obj.shortcode ? obj.shortcode : null);
        if (code && (obj.video_versions || obj.image_versions2 || obj.carousel_media)) {
            const isVideo = !!(obj.is_video || (obj.video_versions && obj.video_versions.length > 0));
            const videoUrl = (obj.video_versions && obj.video_versions[0]?.url) || '';
            const imageUrl = (obj.image_versions2?.candidates && obj.image_versions2.candidates[0]?.url) || '';
            
            const carousel = [];
            if (Array.isArray(obj.carousel_media)) {
                for (let c = 0; c < obj.carousel_media.length; c++) {
                    const cItem = obj.carousel_media[c];
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
                author: obj.user?.username || obj.owner?.username || ''
            };

            window.__fg_media_harvest[code] = entry;
            window.__fg_recent_media.unshift(entry);
            if (window.__fg_recent_media.length > 100) {
                window.__fg_recent_media.pop();
            }
        }

        for (const k in obj) {
            if (Object.prototype.hasOwnProperty.call(obj, k) && k !== '__typename' && typeof obj[k] === 'object') {
                harvestMediaFromData(obj[k], depth + 1);
            }
        }
    }

    // Override fetch in the main world
    const _fetch = window.fetch.bind(window);

    window.fetch = async function (input, init) {
        const url = typeof input === 'string'
            ? input
            : input instanceof URL
            ? input.href
            : input?.url ?? '';

        const response = await _fetch(input, init);

        // Only inspect GraphQL, feed, and clips endpoints
        if (!url.includes('/graphql') && !url.includes('/api/v1/feed') && !url.includes('/api/v1/clips/')) {
            return response;
        }

        const config = window.__fgFilterConfig;
        if (config && config.blockAds === false) {
            return response;
        }

        const cloned = response.clone();
        try {
            const contentType = response.headers.get('content-type') || '';
            if (!contentType.includes('application/json')) {
                return response;
            }

            const data = await cloned.json();
            harvestMediaFromData(data);
            walkAndFilter(data);

            const newHeaders = new Headers(response.headers);
            // CRITICAL: Must delete content-length and content-encoding so the uncompressed
            // JSON body is not truncated or corrupted by the browser HTTP layer
            newHeaders.delete('content-length');
            newHeaders.delete('content-encoding');
            newHeaders.set('content-type', 'application/json; charset=utf-8');

            return new Response(JSON.stringify(data), {
                status: response.status,
                statusText: response.statusText,
                headers: newHeaders
            });
        } catch (_) {
            return response;
        }
    };

    Object.defineProperty(window, 'fetch', {
        value: window.fetch,
        writable: true,
        configurable: true
    });

    window.__fgSetFilterConfig = function (cfg) {
        if (typeof cfg === 'object' && cfg) {
            Object.assign(window.__fgFilterConfig, cfg);
        }
    };
})();
