/**
 * FocusGram — Ghostify Instagram Privacy & Stealth Engine
 *
 * Adapted directly from Ghostify (https://github.com/Hendrizzzz/Ghostify)
 * Intercepts client-side network calls and DOM visibility signals on instagram.com to provide:
 * 1. Anonymous Story Viewing (suppressing story view and reel seen mutations)
 * 2. Incognito Direct Messages (blocking read receipts and seen acknowledgements)
 * 3. Invisible Typing (suppressing typing indicators in chats)
 * 4. Visibility spoofing for story viewer containers
 */

'use strict';

(function () {
    if (window.__GHOSTIFY_INITIALIZED__) return;
    window.__GHOSTIFY_INITIALIZED__ = true;

    const DEFAULT_CONFIG = {
        enabled: false,
        igStory: true,
        igSeen: true,
        igTyping: true
    };

    window.__GHOSTIFY_CONFIG__ = Object.assign({}, DEFAULT_CONFIG, window.__GHOSTIFY_CONFIG__ || {});

    const PATTERNS = {
        igTyping: [
            "indicate_activity",
            "typing_indicator",
            "activity_indicator",
            "is_typing",
            "direct_v2/threads/broadcast/typing",
            "direct_v2/threads/typing",
            "sendtypingindicator",
            "send_typing_indicator",
            "typing_on",
            "is_composing"
        ],
        igSeen: [
            "mark_read",
            "mark_seen",
            "thread_seen",
            "directmarkasseen",
            "markasseen",
            "directthreadmarkitemsseen",
            "polarisdirectmarkasseenmutation",
            "directseenmutation",
            "usepolarismarkthreadseenmutation",
            "useigdmarkthreadasreadmutation"
        ],
        igStory: [
            "storiesupdateseenmutation",
            "polarisstoriesseenmutation",
            "usepolarisstoriesv3seenmutation",
            "reelmediaseen",
            "storiesupdateseen",
            "seenstoriesupdatemutation",
            "polarisapireelseenmutation",
            "xdt_mark_story_reel_seen",
            "26997980659837802",
            "polarisapiforcestoryseenmutation",
            "xdt_api__v1__stories__reel__seen",
            "9647304595318258",
            "api/v1/stories/reel/seen",
            "stories/reel/seen",
            "mark_story_seen",
            "update_seen_for_reel",
            "reel_seen",
            "stories_update_seen",
            "mark_story_read"
        ]
    };

    function shouldBlock(data, url, method) {
        const config = window.__GHOSTIFY_CONFIG__;
        if (!config || !config.enabled) return null;

        const urlString = String(url || "").toLowerCase();
        const bodyText = (typeof data === "string" ? data : "").toLowerCase();
        const text = `${bodyText} ${urlString}`;

        // Never block actual message sending or media uploads
        if (urlString.includes("threads/broadcast/text") ||
            urlString.includes("direct_v2/threads/broadcast") ||
            urlString.includes("configure")) {
            return null;
        }

        if (config.igTyping && PATTERNS.igTyping.some(p => text.includes(p))) {
            return "IG_TYPING";
        }

        if (config.igStory && PATTERNS.igStory.some(p => text.includes(p))) {
            return "IG_STORY";
        }

        if (config.igSeen && PATTERNS.igSeen.some(p => text.includes(p))) {
            return "IG_SEEN";
        }

        return null;
    }

    // 1. Intercept Fetch API
    const originalFetch = window.fetch;
    window.fetch = async function (input, init) {
        const url = typeof input === "string" ? input : (input && input.url ? input.url : "");
        const method = (init && init.method) || (input && input.method) || "GET";
        const body = (init && init.body) || "";

        let bodyText = "";
        try {
            if (typeof body === "string") {
                bodyText = body;
            } else if (body instanceof URLSearchParams) {
                bodyText = body.toString();
            }
        } catch (_) {}

        const blockType = shouldBlock(bodyText, url, method);
        if (blockType) {
            console.log(`[Ghostify] Blocked fetch request: ${blockType} -> ${url}`);
            return new Response(JSON.stringify({ status: "ok", blocked_by: "Ghostify" }), {
                status: 200,
                headers: { "Content-Type": "application/json" }
            });
        }

        return originalFetch.apply(this, arguments);
    };

    // 2. Intercept XMLHttpRequest
    const originalXhrOpen = XMLHttpRequest.prototype.open;
    const originalXhrSend = XMLHttpRequest.prototype.send;

    XMLHttpRequest.prototype.open = function (method, url) {
        this._ghostifyUrl = url;
        this._ghostifyMethod = method;
        return originalXhrOpen.apply(this, arguments);
    };

    XMLHttpRequest.prototype.send = function (body) {
        let bodyText = "";
        try {
            if (typeof body === "string") bodyText = body;
        } catch (_) {}

        const blockType = shouldBlock(bodyText, this._ghostifyUrl, this._ghostifyMethod);
        if (blockType) {
            console.log(`[Ghostify] Blocked XHR request: ${blockType} -> ${this._ghostifyUrl}`);
            Object.defineProperty(this, 'readyState', { value: 4 });
            Object.defineProperty(this, 'status', { value: 200 });
            Object.defineProperty(this, 'responseText', { value: JSON.stringify({ status: "ok" }) });
            if (typeof this.onload === 'function') {
                this.onload();
            }
            return;
        }

        return originalXhrSend.apply(this, arguments);
    };

    // 3. Visibility and Focus Spoofing (Prevents story seen triggers during passive browsing)
    const originalHasFocus = document.hasFocus.bind(document);
    document.hasFocus = function () {
        const config = window.__GHOSTIFY_CONFIG__;
        if (config && config.enabled && config.igStory && window.location.pathname.startsWith('/stories/')) {
            return false;
        }
        return originalHasFocus();
    };

    // 4. Listen for realtime configuration updates
    window.addEventListener("message", (event) => {
        if (!event.data || event.data.source !== "FOCUSGRAM_HOST") return;
        if (event.data.type === "GHOSTIFY_UPDATE") {
            Object.assign(window.__GHOSTIFY_CONFIG__, event.data.config || {});
            console.log("[Ghostify] Configuration updated:", window.__GHOSTIFY_CONFIG__);
        }
    });

    console.log("[FocusGram] Ghostify Instagram Privacy Engine active.");
})();
