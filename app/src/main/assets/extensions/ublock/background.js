/**
 * Background script routing ad/telemetry blocking, native host messaging,
 * and content script coordination for web requests and settings updates.
 */

'use strict';

let cachedSettings = {
    noAds: true,
    noStories: false,
    noReels: false,
    blurExplore: false,
    hideLikeCounts: false,
    hideFollowerCounts: false,
    hideSuggestedPosts: false,
    hideShopTab: false,
    noAutoplay: false,
    grayscale: false,
    minimalMode: false,
    ghostModeEnabled: false,
    ghostHideStorySeen: true,
    ghostHideDirectSeen: true,
    ghostHideTypingIndicator: true,
    unblurMode: 'tap',
    videoDownloadEnabled: false,
    storySearchEnabled: true,
    customBioFontsEnabled: true,
    chatLockMode: 'individual',
    chatLockAll: false,
    lockedChatIds: [],
    lockedChatUsernames: {}
};

let userIsAuthenticated = false;

const BLOCKED_URL_PATTERNS = [
    "*://*.instagram.com/logging_client_events*",
    "*://*.instagram.com/ajax/bz*",
    "*://graph.instagram.com/*",
    "*://*.instagram.com/api/v1/ads/*",
    "*://*.instagram.com/api/v1/ad_*",
    "*://*.instagram.com/api/v1/feed/saved/ads*",
    "*://*.instagram.com/api/v1/feed/reels_tray/ads*",
    "*://*.instagram.com/api/v1/stories/tray/ads*",
    "*://*.instagram.com/api/v1/stories/ads*",
    "*://*.instagram.com/api/v1/feed/timeline/ads*",
    "*://*.instagram.com/api/v1/clips/home/ads*",
    "*://*.instagram.com/api/v1/clips/ads*",
    "*://*.instagram.com/api/v1/clips/stream/ads*",
    "*://*.facebook.com/tr/*",
    "*://*.facebook.com/tr?*",
    "*://*.facebook.net/*",
    "*://connect.facebook.net/*",
    "*://*.meta.com/telemetry*",
    "*://*.doubleclick.net/*",
    "*://*.googleadservices.com/*",
    "*://*.googlesyndication.com/*",
    "*://*.adservice.google.*/*",
    "*://*.cdninstagram.com/v/t51.2885-15/e35/*_ad*"
];

const GHOST_BLOCKED_URL_PATTERNS = [
    "*://*.instagram.com/api/v1/stories/reel/seen*",
    "*://*.instagram.com/stories/reel/seen*",
    "*://*.instagram.com/api/v1/stories/update_seen*",
    "*://*.instagram.com/api/v1/direct_v2/threads/*/items/*/seen*",
    "*://*.instagram.com/api/v1/direct_v2/threads/broadcast/typing*",
    "*://*.instagram.com/api/v1/direct_v2/threads/typing*",
    "*://*.instagram.com/api/v1/direct_v2/threads/seen*",
    "*://*.instagram.com/api/v1/direct_v2/threads/*/seen*",
    "*://*.instagram.com/api/v1/direct_v2/threads/*/mark_seen*",
    "*://*.instagram.com/api/v1/direct_v2/threads/*/mark_read*"
];

if (typeof browser !== 'undefined' && browser.webRequest && browser.webRequest.onBeforeRequest) {
    browser.webRequest.onBeforeRequest.addListener(
        function (details) {
            if (cachedSettings.noAds === false || !userIsAuthenticated) {
                return { cancel: false };
            }
            const url = (details.url || '').toLowerCase();
            // Preserve auth flows and challenge endpoints without interception.
            if (url.includes('/accounts/') || url.includes('/login') || url.includes('/signup') ||
                url.includes('facebook.net') || url.includes('facebook.com') || url.includes('/ajax/bz') ||
                url.includes('/api/v1/web/accounts/') || url.includes('/api/v1/accounts/')) {
                return { cancel: false };
            }
            console.log('[Content Blocker] Intercepted and blocked network ad request:', details.url);
            return { cancel: true };
        },
        { urls: BLOCKED_URL_PATTERNS },
        ["blocking"]
    );
    console.log('[Content Blocker] Network interception active across', BLOCKED_URL_PATTERNS.length, 'patterns.');

    browser.webRequest.onBeforeRequest.addListener(
        function (details) {
            if (!cachedSettings.ghostModeEnabled) return { cancel: false };
            const url = (details.url || '').toLowerCase();
            if (url.includes('/broadcast/text') || url.includes('/broadcast/media')) {
                return { cancel: false };
            }
            if (cachedSettings.ghostHideStorySeen && (url.includes('stories/reel/seen') || url.includes('story/seen') || url.includes('update_seen'))) {
                console.log('[Ghost Mode] Blocked story seen network call:', details.url);
                return { cancel: true };
            }
            if (cachedSettings.ghostHideDirectSeen && (url.includes('/seen') || url.includes('mark_seen') || url.includes('mark_read'))) {
                console.log('[Ghost Mode] Blocked direct seen network call:', details.url);
                return { cancel: true };
            }
            if (cachedSettings.ghostHideTypingIndicator && url.includes('/typing')) {
                console.log('[Ghost Mode] Blocked direct typing network call:', details.url);
                return { cancel: true };
            }
            return { cancel: false };
        },
        { urls: GHOST_BLOCKED_URL_PATTERNS },
        ["blocking"]
    );

    // Passively capture viewers list responses without issuing external requests.
    const storyViewersCache = new Map();

    browser.webRequest.onBeforeRequest.addListener(
        function (details) {
            const url = details.url || '';
            const match = url.match(/\/api\/v1\/media\/(\d+)\/list_reel_media_viewer\//);
            if (!match) return;

            const mediaId = match[1];

            // Broadcast early phase so UI surfaces the search bar before payload arrives.
            broadcastToTabs({
                action: 'storyViewersDetected',
                phase: 'loading',
                mediaId: mediaId,
                url: url
            });

            if (typeof browser.webRequest.filterResponseData === 'function') {
                try {
                    const filter = browser.webRequest.filterResponseData(details.requestId);
                    const chunks = [];

                    filter.ondata = function (event) {
                        chunks.push(event.data);
                        filter.write(event.data);
                    };

                    filter.onstop = async function () {
                        filter.close();
                        try {
                            const blob = new Blob(chunks);
                            const text = await blob.text();
                            const json = JSON.parse(text);
                            const users = json.users || [];
                            const rawViewers = json.viewers || [];

                            const existing = storyViewersCache.get(mediaId) || [];
                            const userMap = new Map(existing.map(u => [String(u.pk || u.id), u]));

                            users.forEach(u => {
                                const pk = String(u.pk || u.id);
                                if (!pk) return;
                                userMap.set(pk, {
                                    pk: pk,
                                    username: u.username || '',
                                    fullName: u.full_name || '',
                                    avatar: u.profile_pic_url || '',
                                    isVerified: !!u.is_verified,
                                    hasLiked: false
                                });
                            });

                            rawViewers.forEach(v => {
                                const u = v.user || v;
                                const pk = String(u.pk || u.id);
                                if (!pk) return;
                                const existingEntry = userMap.get(pk) || {};
                                userMap.set(pk, {
                                    pk: pk,
                                    username: u.username || existingEntry.username || '',
                                    fullName: u.full_name || existingEntry.fullName || '',
                                    avatar: u.profile_pic_url || existingEntry.avatar || '',
                                    isVerified: !!(u.is_verified || existingEntry.isVerified),
                                    hasLiked: !!(v.has_liked || existingEntry.hasLiked)
                                });
                            });

                            const viewerList = Array.from(userMap.values());
                            storyViewersCache.set(mediaId, viewerList);
                            console.log(`[FocusGram] Story viewers captured for ${mediaId}: ${viewerList.length} users`);

                            // Broadcast captured viewers to active tabs
                            broadcastToTabs({
                                action: 'storyViewersDetected',
                                phase: 'ready',
                                mediaId: mediaId,
                                viewers: viewerList
                            });
                            broadcastToTabs({
                                action: 'storyViewersCaptured',
                                mediaId: mediaId,
                                viewers: viewerList
                            });
                        } catch (e) {
                            console.warn('[FocusGram] Error parsing story viewers payload:', e);
                        }
                    };
                } catch (err) {
                    console.warn('[FocusGram] filterResponseData error:', err);
                }
            }
        },
        { urls: ["*://*.instagram.com/api/v1/media/*/list_reel_media_viewer/*"] },
        ["blocking"]
    );
}

let nativePort = null;
const pendingNativeMessages = [];

function sendToNative(msg) {
    if (!msg) return;
    if (nativePort) {
        try {
            nativePort.postMessage(msg);
            console.log('[FocusGram Background] Forwarded to native host:', JSON.stringify(msg));
            return;
        } catch (e) {
            console.warn('[FocusGram Background] postMessage failed, queueing message:', e);
            nativePort = null;
        }
    }
    pendingNativeMessages.push(msg);
    if (pendingNativeMessages.length > 50) pendingNativeMessages.shift();
    connectNativePort();
}

function flushPendingMessages() {
    if (!nativePort) return;
    while (pendingNativeMessages.length > 0) {
        const msg = pendingNativeMessages.shift();
        try {
            nativePort.postMessage(msg);
            console.log('[FocusGram Background] Flushed queued message to native host:', JSON.stringify(msg));
        } catch (e) {
            console.error('[FocusGram Background] Error flushing queued message:', e);
            pendingNativeMessages.unshift(msg);
            break;
        }
    }
}

function connectNativePort() {
    try {
        if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.connectNative) {
            if (nativePort) return;
            nativePort = browser.runtime.connectNative("focusgram");
            console.log('[FocusGram Background] Connected to native host "focusgram"');

            nativePort.onMessage.addListener((msg) => {
                console.log('[FocusGram Background] Message from Android host:', msg);
                if (!msg) return;

                if (msg.action === 'updateSettings' && msg.settings) {
                    Object.assign(cachedSettings, msg.settings);
                }
                if (msg.revealMode) {
                    cachedSettings.unblurMode = msg.revealMode;
                }
                broadcastToTabs(msg);
            });

            nativePort.onDisconnect.addListener((p) => {
                console.warn('[FocusGram Background] Native port disconnected, will retry...');
                nativePort = null;
                setTimeout(connectNativePort, 3000);
            });

            flushPendingMessages();
        }
    } catch (e) {
        console.error('[FocusGram Background] Error connecting native port:', e);
        setTimeout(connectNativePort, 3000);
    }
}

connectNativePort();

function broadcastToTabs(message) {
    if (typeof browser !== 'undefined' && browser.tabs && browser.tabs.query) {
        browser.tabs.query({}, (tabs) => {
            if (tabs && tabs.length > 0) {
                for (const tab of tabs) {
                    try {
                        browser.tabs.sendMessage(tab.id, message).catch(() => {});
                    } catch (_) {}
                }
            }
        });
    }
}

if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.onMessage) {
    browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
        if (!message) return;

        if (message.action === 'authStateChanged') {
            userIsAuthenticated = !!message.isAuthenticated;
        }

        if (message.action === 'getSettings') {
            if (typeof sendResponse === 'function') {
                sendResponse({ settings: cachedSettings });
            }
            return Promise.resolve({ settings: cachedSettings });
        }

        if (message.action === 'getStoryViewers') {
            const mediaId = message.mediaId;
            const viewers = mediaId ? (storyViewersCache.get(mediaId) || []) : [];
            sendResponse({ viewers: viewers });
            return true;
        }

        if (message.action === 'broadcastToTabs' && message.payload) {
            broadcastToTabs(message.payload);
            sendResponse({ status: 'broadcasted' });
            return true;
        }

        if (message.type === 'ReelMetadata' || message.action === 'reelViewed' || message.action === 'reelWatched') {
            const payload = message.payload || message.data || message.reel || message;
            sendToNative({
                action: 'reelViewed',
                type: message.type || 'reelViewed',
                code: payload.code || message.code || '',
                url: payload.url || message.url || '',
                title: payload.caption || payload.title || message.caption || message.title || '',
                caption: payload.caption || message.caption || '',
                author: payload.author || message.author || '',
                thumbnailUrl: payload.thumbnailUrl || message.thumbnailUrl || ''
            });
        } else {
            sendToNative(message);
        }

        sendResponse({ status: 'forwarded' });
        return true;
    });
}

