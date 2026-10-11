/**
 * uBlock Origin & FocusGram Privacy Engine — WebExtension Background Script
 * License: GNU General Public License v3.0 (GPL-3.0)
 * Author: Raymond Hill (gorhill) / FocusGram Project
 *
 * Provides:
 *  1. Network-level interception of Instagram ads & telemetry (toggled dynamically via settings).
 *  2. Native messaging bridge connecting GeckoView Android host with in-page content scripts.
 *  3. Dispatcher for SPA navigation, Ghost Mode updates, and UI events.
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
    ghostModeEnabled: false,
    ghostHideStorySeen: true,
    ghostHideDirectSeen: true,
    ghostHideTypingIndicator: true
};

const BLOCKED_URL_PATTERNS = [
    "*://*.instagram.com/logging_client_events*",
    "*://*.instagram.com/ajax/bz*",
    "*://graph.instagram.com/logging_client_events*",
    "*://*.instagram.com/api/v1/ads/*",
    "*://*.instagram.com/api/v1/feed/saved/ads*",
    "*://*.facebook.com/tr/*",
    "*://*.facebook.com/tr?*",
    "*://*.facebook.net/*",
    "*://connect.facebook.net/*",
    "*://*.meta.com/telemetry*",
    "*://*.instagram.com/api/v1/stories/tray/ads*",
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
            console.log('[uBlock Origin] Intercepted and blocked network ad request:', details.url);
            return { cancel: true };
        },
        { urls: BLOCKED_URL_PATTERNS },
        ["blocking"]
    );
    console.log('[uBlock Origin] Network interception active across', BLOCKED_URL_PATTERNS.length, 'patterns.');
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
                    broadcastToTabs(msg);
                } else if (msg.action === 'navigateSpa' || msg.action === 'openProfileSpa' || msg.action === 'nav' || msg.nav || msg.action === 'setIslandMode' || msg.island) {
                    broadcastToTabs(msg);
                }
            });

            nativePort.onDisconnect.addListener((p) => {
                console.warn('[FocusGram Background] Native port disconnected, will retry...');
                nativePort = null;
                setTimeout(connectNativePort, 10000);
            });
        }
    } catch (e) {
        console.error('[FocusGram Background] Error connecting native port:', e);
        setTimeout(connectNativePort, 10000);
    }
}

connectNativePort();

function broadcastToTabs(message) {
    if (typeof browser !== 'undefined' && browser.tabs && browser.tabs.query) {
        browser.tabs.query({ url: "*://*.instagram.com/*" }, (tabs) => {
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

        if (message.action === 'openSettings' || message.action === 'inputFocused' || message.action === 'urlChanged' || message.action === 'modalOrChatActive') {
            if (nativePort) {
                try {
                    nativePort.postMessage(message);
                } catch (e) {
                    console.error('[FocusGram Background] Failed to forward message to native host:', e);
                }
            }
            sendResponse({ status: 'forwarded' });
            return true;
        }
    });
}
