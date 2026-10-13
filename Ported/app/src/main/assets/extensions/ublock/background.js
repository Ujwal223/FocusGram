/**
 * FocusGram Privacy & Content Blocker Engine — WebExtension Background Script
 *
 * Provides:
 *  1. Network-level interception of Instagram ads & telemetry (toggled dynamically via settings).
 *  2. Native messaging bridge connecting GeckoView Android host with in-page content scripts.
 *  3. Dispatcher for SPA navigation, stealth updates, Reels metadata, and UI events.
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
    unblurMode: 'tap'
};

const BLOCKED_URL_PATTERNS = [
    "*://*.instagram.com/logging_client_events*",
    "*://*.instagram.com/ajax/bz*",
    "*://graph.instagram.com/*",
    "*://*.instagram.com/api/v1/ads/*",
    "*://*.instagram.com/api/v1/feed/saved/ads*",
    "*://*.instagram.com/api/v1/stories/tray/ads*",
    "*://*.instagram.com/api/v1/feed/timeline/ads*",
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

// Network-level interception: ONLY active when noAds is enabled in settings
if (typeof browser !== 'undefined' && browser.webRequest && browser.webRequest.onBeforeRequest) {
    browser.webRequest.onBeforeRequest.addListener(
        function (details) {
            if (cachedSettings.noAds === false) {
                // User turned ad blocker off: do not cancel requests
                return { cancel: false };
            }
            console.log('[Content Blocker] Intercepted and blocked network ad request:', details.url);
            return { cancel: true };
        },
        { urls: BLOCKED_URL_PATTERNS },
        ["blocking"]
    );
    console.log('[Content Blocker] Network interception active across', BLOCKED_URL_PATTERNS.length, 'patterns.');
}

// Establish native messaging port with FocusGram Android host
let nativePort = null;

function connectNativePort() {
    try {
        if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.connectNative) {
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
                setTimeout(connectNativePort, 5000);
            });
        }
    } catch (e) {
        console.error('[FocusGram Background] Error connecting native port:', e);
        setTimeout(connectNativePort, 5000);
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

// Listen for messages from in-page content scripts
if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.onMessage) {
    browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
        if (!message) return;

        if (message.action === 'getSettings') {
            sendResponse({ settings: cachedSettings });
            return true;
        }

        if (!nativePort) {
            connectNativePort();
        }

        if (nativePort) {
            try {
                if (message.type === 'ReelMetadata' || message.action === 'reelViewed') {
                    const payload = message.payload || message.data || message.reel || message;
                    nativePort.postMessage({
                        action: 'reelViewed',
                        type: 'reelViewed',
                        url: payload.url || '',
                        title: payload.title || 'Instagram Reel',
                        author: payload.author || 'Instagram Creator',
                        thumbnailUrl: payload.thumbnailUrl || ''
                    });
                } else {
                    nativePort.postMessage(message);
                }
                console.log('[FocusGram Background] Forwarded to native host:', JSON.stringify(message));
            } catch (e) {
                console.error('[FocusGram Background] Failed to forward message to native host:', e);
            }
        }
        sendResponse({ status: 'forwarded' });
        return true;
    });
}
