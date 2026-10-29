/**
 * Network ad and sponsored content filter via main-world fetch and XHR interception.
 *
 * Purges sponsored and recommended nodes from GraphQL and REST payloads
 * before React hydration to preserve virtual scroll dimensions.
 */
'use strict';

(function () {
    function isAuthPage() {
        try {
            var path = (window.location.pathname || '').toLowerCase();
            if (path.includes('/accounts/edit')) return false;
            if (path.startsWith('/accounts') || path.startsWith('/login') || path.startsWith('/signup') || path.startsWith('/two_factor') || path.startsWith('/challenge')) return true;
            if (document.getElementById('loginForm') || document.querySelector('form[action*="login"], form input[type="password"]')) return true;
        } catch (_) {}
        return false;
    }

    if (isAuthPage()) return;

    if (window.__fgFetchInterceptorInstalled) return;
    window.__fgFetchInterceptorInstalled = true;

    window.__fgFilterConfig = {
        blockAds: true,
        blockSponsored: true,
        blockSuggested: false,
        blockVideos: false,
        blockAutoplay: false,
        blockGraphQLQueryWhenFeedPosts: false,
        storySearchEnabled: true,
        customBioFontsEnabled: true
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

        if (config.blockAds !== false && isAdNode(node)) {
            return true;
        }

        if (config.blockSponsored !== false && isSponsoredNode(node) && !isAdNode(node)) {
            return true;
        }

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

    let localChatLockMode = 'individual';
    let localChatLockAll = false;
    let localLockedChatIds = [];
    let localUnlockedThreads = [];
    let localThreadUsernameMap = {};

    function handleChatLockSync(detail) {
        if (!detail) return;
        if (typeof detail.chatLockMode !== 'undefined') localChatLockMode = String(detail.chatLockMode).toLowerCase();
        if (typeof detail.chatLockAll !== 'undefined') localChatLockAll = !!detail.chatLockAll;
        if (Array.isArray(detail.lockedChatIds)) localLockedChatIds = detail.lockedChatIds;
        if (Array.isArray(detail.unlockedThreads)) localUnlockedThreads = detail.unlockedThreads;
        if (detail.threadUsernameMap && typeof detail.threadUsernameMap === 'object') localThreadUsernameMap = detail.threadUsernameMap;
    }

    window.addEventListener('focusgram:chat-lock-sync', (e) => handleChatLockSync(e.detail));
    document.addEventListener('focusgram:chat-lock-sync', (e) => handleChatLockSync(e.detail));

    function isDirectThreadLocked(threadId, username) {
        const mode = window.__fgChatLockMode || localChatLockMode || 'individual';
        if (mode === 'off') return false;

        const cleanTid = String(threadId || '').toLowerCase().trim();
        const cleanUser = String(username || '').toLowerCase().trim().replace(/^@/, '');

        // Resolve mapped usernames/IDs from maps
        const gMap = window.__fgThreadUsernameMap || localThreadUsernameMap || {};
        const mappedUser = cleanTid ? (gMap.get ? gMap.get(cleanTid) : gMap[cleanTid]) : '';
        const mappedId = cleanUser ? (gMap.get ? gMap.get(cleanUser) : gMap[cleanUser]) : '';

        const checkInCollection = (collection, val) => {
            if (!collection || !val) return false;
            if (typeof collection.has === 'function') return collection.has(val);
            if (Array.isArray(collection)) return collection.includes(val);
            return false;
        };

        // Check if unlocked in current session
        const unlockedSources = [window.__fgUnlockedThreads, localUnlockedThreads];
        for (const unl of unlockedSources) {
            if (unl) {
                if (checkInCollection(unl, '__all__') ||
                    checkInCollection(unl, cleanTid) ||
                    checkInCollection(unl, cleanUser) ||
                    (mappedUser && checkInCollection(unl, mappedUser)) ||
                    (mappedId && checkInCollection(unl, mappedId))) {
                    return false;
                }
            }
        }

        // Whole DM lock mode
        if (mode === 'whole_dm' || window.__fgChatLockAll || localChatLockAll) return true;

        // Individual chat lock check
        const lockedSources = [window.__fgLockedChatIds, localLockedChatIds];
        for (const lk of lockedSources) {
            if (lk) {
                if (checkInCollection(lk, cleanTid) ||
                    checkInCollection(lk, cleanUser) ||
                    (mappedUser && checkInCollection(lk, mappedUser)) ||
                    (mappedId && checkInCollection(lk, mappedId))) {
                    return true;
                }
            }
        }
        return false;
    }

    function maskMessageNode(node) {
        if (!node || typeof node !== 'object') return false;
        let didMask = false;
        if (typeof node.text_body === 'string' && node.text_body.length > 0) {
            node.text_body = '••••••••••••';
            didMask = true;
        }
        if (typeof node.text === 'string' && node.text.length > 0) {
            node.text = '••••••••••••';
            didMask = true;
        }
        if (typeof node.snippet === 'string' && node.snippet.length > 0) {
            node.snippet = '••••••••••••';
            didMask = true;
        }
        if (node.content && typeof node.content === 'object') {
            if (typeof node.content.text_body === 'string' && node.content.text_body.length > 0) {
                node.content.text_body = '••••••••••••';
                didMask = true;
            }
            if (typeof node.content.text === 'string' && node.content.text.length > 0) {
                node.content.text = '••••••••••••';
                didMask = true;
            }
            if (typeof node.content.snippet === 'string' && node.content.snippet.length > 0) {
                node.content.snippet = '••••••••••••';
                didMask = true;
            }
        }
        return didMask;
    }

    function maskThreadMessages(thread) {
        if (!thread || typeof thread !== 'object') return false;
        let threadMod = false;

        // 1. slide_messages.edges[].node (HAR / GraphQL IGDThreadDetailQuery format)
        if (thread.slide_messages && Array.isArray(thread.slide_messages.edges)) {
            for (const edge of thread.slide_messages.edges) {
                if (edge && edge.node && maskMessageNode(edge.node)) {
                    threadMod = true;
                }
            }
        }

        // 2. messages.edges or messages.items or messages[]
        if (thread.messages) {
            if (Array.isArray(thread.messages.edges)) {
                for (const edge of thread.messages.edges) {
                    if (edge && edge.node && maskMessageNode(edge.node)) {
                        threadMod = true;
                    }
                }
            } else if (Array.isArray(thread.messages.items)) {
                for (const item of thread.messages.items) {
                    if (maskMessageNode(item)) {
                        threadMod = true;
                    }
                }
            } else if (Array.isArray(thread.messages)) {
                for (const item of thread.messages) {
                    if (maskMessageNode(item)) {
                        threadMod = true;
                    }
                }
            }
        }

        // 3. items[]
        if (Array.isArray(thread.items)) {
            for (const item of thread.items) {
                if (maskMessageNode(item)) {
                    threadMod = true;
                }
            }
        }

        // 4. last_permanent_item
        if (thread.last_permanent_item && maskMessageNode(thread.last_permanent_item)) {
            threadMod = true;
        }

        // 5. snippet
        if (typeof thread.snippet === 'string') {
            thread.snippet = '••••••••••••';
            threadMod = true;
        }

        return threadMod;
    }

    function findThreadsInPayload(root) {
        const threads = [];
        if (!root || typeof root !== 'object') return threads;

        if (root.get_slide_thread_nullable?.as_ig_direct_thread) {
            threads.push(root.get_slide_thread_nullable.as_ig_direct_thread);
        } else if (root.get_slide_thread_nullable && typeof root.get_slide_thread_nullable === 'object') {
            threads.push(root.get_slide_thread_nullable);
        }
        if (root.message_thread && typeof root.message_thread === 'object') {
            threads.push(root.message_thread);
        }
        if (root.thread && typeof root.thread === 'object') {
            threads.push(root.thread);
        }

        const inbox = root.viewer?.inbox || root.inbox;
        if (inbox && Array.isArray(inbox.threads)) {
            for (const t of inbox.threads) {
                if (t && typeof t === 'object') threads.push(t);
            }
        }

        if (threads.length === 0) {
            for (const key of Object.keys(root)) {
                const val = root[key];
                if (val && typeof val === 'object') {
                    if (val.slide_messages || val.as_ig_direct_thread || (val.thread_fbid && (val.items || val.messages))) {
                        threads.push(val.as_ig_direct_thread || val);
                    }
                }
            }
        }

        return threads;
    }

    function processDirectMessagesPayload(data) {
        if (!data || typeof data !== 'object') return false;

        let modified = false;
        const root = data.data || data;
        const threads = findThreadsInPayload(root);
        if (threads.length === 0) return false;

        for (const thread of threads) {
            const tid = String(thread.thread_fbid || thread.id || thread.thread_id || '').toLowerCase().trim();
            const threadKey = String(thread.thread_key || '').toLowerCase().trim();
            let username = '';
            let displayName = thread.thread_title || '';
            const allUsernames = [];

            if (Array.isArray(thread.users)) {
                for (const u of thread.users) {
                    if (u && u.username) {
                        const uname = String(u.username).toLowerCase().trim().replace(/^@/, '');
                        if (uname) allUsernames.push(uname);
                    }
                }
                if (allUsernames.length > 0) {
                    username = allUsernames[0];
                    if (!displayName) displayName = thread.users[0].full_name || username;
                }
            }

            const primaryId = tid || threadKey;
            if (primaryId && (allUsernames.length > 0 || displayName)) {
                window.dispatchEvent(new CustomEvent('focusgram:thread-matched', {
                    detail: {
                        threadId: primaryId,
                        threadKey: threadKey,
                        usernames: allUsernames,
                        displayName: displayName
                    }
                }));
            }

            let isLocked = isDirectThreadLocked(tid, username);
            if (!isLocked && threadKey) isLocked = isDirectThreadLocked(threadKey, username);
            if (!isLocked) {
                for (const u of allUsernames) {
                    if (isDirectThreadLocked(tid || threadKey, u)) {
                        isLocked = true;
                        break;
                    }
                }
            }

            if (isLocked) {
                // Do not mask inbox list previews
                const isInboxItem = !thread.slide_messages && (thread.last_permanent_item || thread.snippet);
                if (isInboxItem) {
                    continue;
                }
                if (maskThreadMessages(thread)) {
                    modified = true;
                }
            }
        }

        return modified;
    }

    window.__fgProcessDirectMessagesPayload = processDirectMessagesPayload;

    const _fetch = window.fetch.bind(window);

    window.fetch = async function (input, init) {
        const url = typeof input === 'string'
            ? input
            : input instanceof URL
            ? input.href
            : input?.url ?? '';

        // Notify UI when story viewers endpoint is requested or resolved
        if (url.includes('/list_reel_media_viewer/')) {
            const mediaIdMatch = url.match(/\/api\/v1\/media\/(\d+)\/list_reel_media_viewer\//);
            const mediaId = mediaIdMatch ? mediaIdMatch[1] : '';
            const loadingDetail = { phase: 'loading', mediaId: mediaId, url: url };
            
            // Dispatch across window and document compartments
            window.dispatchEvent(new CustomEvent('focusgram:story-viewers-detected', { detail: loadingDetail }));
            document.dispatchEvent(new CustomEvent('focusgram:story-viewers-detected', { detail: loadingDetail }));
            window.postMessage({ type: 'FOCUSGRAM_STORY_VIEWERS', detail: loadingDetail }, '*');
            handleStoryViewersDetected(loadingDetail);

            const response = await _fetch(input, init);
            try {
                const cloned = response.clone();
                cloned.json().then(data => {
                    const readyDetail = { phase: 'ready', mediaId: mediaId, url: url, data: data };
                    window.dispatchEvent(new CustomEvent('focusgram:story-viewers-detected', { detail: readyDetail }));
                    document.dispatchEvent(new CustomEvent('focusgram:story-viewers-detected', { detail: readyDetail }));
                    window.postMessage({ type: 'FOCUSGRAM_STORY_VIEWERS', detail: readyDetail }, '*');
                    handleStoryViewersDetected(readyDetail);
                }).catch(() => {});
            } catch (_) {}
            return response;
        }

        const response = await _fetch(input, init);

        if (!url.includes('/graphql') && !url.includes('/api/v1/feed') && !url.includes('/api/v1/clips/') && !url.includes('/api/v1/direct_v2/threads/')) {
            return response;
        }

        const config = window.__fgFilterConfig;

        const cloned = response.clone();
        try {
            const contentType = response.headers.get('content-type') || '';
            if (!contentType.includes('json') && !contentType.includes('javascript')) {
                return response;
            }

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

            harvestMediaFromData(data);

            const directModified = processDirectMessagesPayload(data);

            let adModified = false;
            if (config && config.blockAds !== false) {
                walkAndFilter(data);
                adModified = true;
            }

            if (!directModified && !adModified) {
                return response;
            }

            const originalContentType = response.headers.get('content-type') || 'application/json; charset=utf-8';
            const newHeaders = new Headers(response.headers);
            // Delete content-length and content-encoding so the modified JSON body is not truncated.
            newHeaders.delete('content-length');
            newHeaders.delete('content-encoding');
            newHeaders.set('content-type', originalContentType);

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

    // Hook XMLHttpRequest for background requests
    const origXHR = window.XMLHttpRequest;
    if (origXHR && origXHR.prototype) {
        const origOpen = origXHR.prototype.open;
        const origSend = origXHR.prototype.send;

        origXHR.prototype.open = function(method, url) {
            this._fgUrl = typeof url === 'string' ? url : (url ? url.toString() : '');
            return origOpen.apply(this, arguments);
        };

        origXHR.prototype.send = function() {
            const xhr = this;
            const url = xhr._fgUrl || '';
            if (url.includes('/graphql') || url.includes('/api/v1/direct') || url.includes('/direct/')) {
                const onReady = function() {
                    if (xhr.readyState === 4 && xhr.status === 200) {
                        try {
                            const cType = xhr.getResponseHeader('content-type') || '';
                            if (cType.includes('json') || cType.includes('javascript')) {
                                let text = xhr.responseText;
                                if (text) {
                                    text = text.replace(/^for\s*\(\s*;\s*;\s*\)\s*;\s*/, '');
                                    const data = JSON.parse(text);
                                    if (processDirectMessagesPayload(data)) {
                                        const modifiedText = JSON.stringify(data);
                                        Object.defineProperty(xhr, 'responseText', { value: modifiedText, writable: true, configurable: true });
                                        Object.defineProperty(xhr, 'response', { value: modifiedText, writable: true, configurable: true });
                                    }
                                }
                            }
                        } catch (_) {}
                    }
                };
                xhr.addEventListener('readystatechange', onReady, false);
            }
            return origSend.apply(this, arguments);
        };
    }

    // Story viewer search bar injection for the viewers modal.
    let storyViewersData = [];
    let storySearchCardEl = null;

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

    function createStorySearchCard() {
        const card = document.createElement('div');
        card.id = 'fg-story-search-card';
        card.style.cssText = `
            margin: 6px 16px 10px 16px !important;
            padding: 0 10px !important;
            height: 38px !important;
            background: #262626 !important;
            border: 1px solid rgba(255, 255, 255, 0.1) !important;
            border-radius: 8px !important;
            display: flex !important;
            align-items: center !important;
            gap: 8px !important;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important;
            color: #ffffff !important;
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

        return card;
    }

    function filterViewers(query) {
        const info = findViewersDialog();
        if (!info || !info.dialog) return;

        const scrollContainer = info.scrollContainer || 
                                info.dialog.querySelector('div[style*="overflow"]') || 
                                info.dialog.querySelector('div[style*="height"]') ||
                                info.dialog;

        // Target viewer rows: .x1qnrgzn, or rows containing profile links
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

            if (matched) {
                row.style.display = '';
                delete row.dataset.fgViewerHidden;
                matchedCount++;
            } else {
                row.style.display = 'none';
                row.dataset.fgViewerHidden = 'true';
            }
        });

        // Handle "No results found" placeholder
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

    function cleanupStorySearch() {
        const existing = document.getElementById('fg-story-search-card');
        if (existing) {
            existing.remove();
            storySearchCardEl = null;
        }
        const noResults = document.getElementById('fg-no-viewer-results');
        if (noResults) noResults.remove();

        document.querySelectorAll('[data-fg-viewer-hidden]').forEach(el => {
            el.style.display = '';
            delete el.dataset.fgViewerHidden;
        });
    }

    function isStorySearchEnabled() {
        if (window.__fgFilterConfig && typeof window.__fgFilterConfig.storySearchEnabled === 'boolean') {
            return window.__fgFilterConfig.storySearchEnabled;
        }
        if (window.__FOCUSGRAM_CONFIG__ && typeof window.__FOCUSGRAM_CONFIG__.storySearchEnabled === 'boolean') {
            return window.__FOCUSGRAM_CONFIG__.storySearchEnabled;
        }
        return false;
    }

    function checkAndMountStorySearch() {
        if (!isStorySearchEnabled()) {
            cleanupStorySearch();
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

        const card = createStorySearchCard();
        info.header.after(card);
        storySearchCardEl = card;
    }

    function handleStoryViewersDetected(detail) {
        if (!detail) return;
        if (!isStorySearchEnabled()) {
            cleanupStorySearch();
            return;
        }
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

    window.__fgHandleStoryViewers = handleStoryViewersDetected;
    window.addEventListener('focusgram:story-viewers-detected', (e) => handleStoryViewersDetected(e.detail));
    document.addEventListener('focusgram:story-viewers-detected', (e) => handleStoryViewersDetected(e.detail));

    // Continuous autonomous observer for Viewers modal
    const viewersObserver = new MutationObserver(() => {
        checkAndMountStorySearch();
    });
    viewersObserver.observe(document.documentElement, { childList: true, subtree: true });

    function handleFilterConfigUpdate(cfg) {
        if (typeof cfg === 'object' && cfg) {
            if (!window.__fgFilterConfig) window.__fgFilterConfig = {};
            Object.assign(window.__fgFilterConfig, cfg);
            if (window.__fgFilterConfig.storySearchEnabled === false) {
                cleanupStorySearch();
            } else {
                checkAndMountStorySearch();
            }
        }
    }

    const prevFetchSetFilter = window.__fgSetFilterConfig;
    window.__fgSetFilterConfig = function (cfg) {
        if (typeof prevFetchSetFilter === 'function') prevFetchSetFilter(cfg);
        handleFilterConfigUpdate(cfg);
    };

    window.addEventListener('message', (e) => {
        if (e.data && e.data.type === 'FOCUSGRAM_FILTER_UPDATE' && e.data.config) {
            handleFilterConfigUpdate(e.data.config);
        }
    });
    window.addEventListener('focusgram:filter-config-update', (e) => {
        handleFilterConfigUpdate(e.detail);
    });
    document.addEventListener('focusgram:filter-config-update', (e) => {
        handleFilterConfigUpdate(e.detail);
    });
})();
