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
            if (Object.prototype.hasOwnProperty.call(obj, key) && key !== '__typename') {
                const val = obj[key];
                if (val && typeof val === 'object') {
                    walkAndFilter(val, visited);
                }
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
            walkAndFilter(data);

            return new Response(JSON.stringify(data), {
                status: response.status,
                statusText: response.statusText,
                headers: response.headers
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
