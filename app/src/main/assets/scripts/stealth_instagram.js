/**
 * Client-side privacy filter.
 *
 * Suppresses story viewed telemetry, direct message read receipts, and typing indicators
 * via fetch, XMLHttpRequest, sendBeacon, and WebSocket interception.
 */

'use strict';

(function () {
    if (window.__FG_STEALTH_INITIALIZED__) return;
    window.__FG_STEALTH_INITIALIZED__ = true;

    const DEFAULT_CONFIG = {
        enabled: false,
        igStory: true,
        igSeen: true,
        igTyping: true,
        hideStorySeen: true,
        hideDirectSeen: true,
        hideTyping: true
    };

    window.__FOCUSGRAM_GHOST_CONFIG__ = Object.assign({}, DEFAULT_CONFIG, window.__FOCUSGRAM_GHOST_CONFIG__ || {});

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
            "is_composing",
            "update_activity_status",
            "activity_status",
            "typingindicatorstoredprocedure",
            "directsendtyping",
            "direct_send_typing",
            "igdsendtyping"
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
            "useigdmarkthreadasreadmutation",
            "markdirectthreadseen",
            "mark_direct_thread_seen",
            "direct_v2/threads/seen",
            "direct_v2/threads/mark_seen",
            "direct_v2/threads/mark_read"
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
            "mark_story_read",
            "viewseenat"
        ]
    };

    function shouldBlock(data, url, method) {
        const config = window.__FOCUSGRAM_GHOST_CONFIG__;
        if (!config || !config.enabled) return null;

        const path = String(window.location?.pathname || '').toLowerCase();
        if (path.startsWith('/accounts') || path.startsWith('/login')) return null;

        const urlString = String(url || "").toLowerCase();
        const bodyText = (typeof data === "string" ? data : "").toLowerCase();
        const text = `${bodyText} ${urlString}`;

        // Do not suppress actual user message dispatch or media uploads.
        if (urlString.includes("threads/broadcast/text") ||
            urlString.includes("direct_v2/threads/broadcast") ||
            urlString.includes("broadcast/media") ||
            urlString.includes("action=send_message") ||
            urlString.includes("send_item") ||
            urlString.includes("publish_message") ||
            urlString.includes("configure")) {
            return null;
        }

        const typingActive = config.hideTyping !== undefined ? config.hideTyping : !!config.igTyping;
        const storyActive = config.hideStorySeen !== undefined ? config.hideStorySeen : !!config.igStory;
        const seenActive = config.hideDirectSeen !== undefined ? config.hideDirectSeen : !!config.igSeen;

        if (typingActive && PATTERNS.igTyping.some(p => text.includes(p))) {
            return "IG_TYPING";
        }

        if (storyActive && PATTERNS.igStory.some(p => text.includes(p))) {
            return "IG_STORY";
        }

        if (seenActive && PATTERNS.igSeen.some(p => text.includes(p))) {
            return "IG_SEEN";
        }

        return null;
    }

    function createMockPayload(blockType, url, bodyText) {
        const combined = `${bodyText} ${url}`.toLowerCase();
        const isGraphQL = combined.includes("/graphql") || combined.includes("fb_api_req_friendly_name") || combined.includes("doc_id");

        if (!isGraphQL) {
            return { status: "ok", blocked: blockType };
        }

        if (blockType === "IG_STORY") {
            if (combined.includes("xdt_api__v1__stories__reel__seen") ||
                combined.includes("polarisapiforcestoryseenmutation") ||
                combined.includes("9647304595318258")) {
                return {
                    data: {
                        xdt_api__v1__stories__reel__seen: {
                            __typename: "XDTEmptyRecord"
                        }
                    }
                };
            }
            return {
                data: {
                    xdt_mark_story_reel_seen: {
                        __typename: "XDTMarkSeenResponse"
                    }
                }
            };
        }

        return { data: {} };
    }

    const originalFetch = window.fetch;
    window.fetch = async function (input, init) {
        const url = typeof input === "string" ? input : (input && input.url ? input.url : "");
        const method = (init && init.method) || (input && input.method) || "GET";
        const body = (init && init.body) || "";

        let bodyText = "";
        try {
            if (typeof body === "string") {
                bodyText = body;
            } else if (typeof URLSearchParams !== "undefined" && body instanceof URLSearchParams) {
                bodyText = body.toString();
            } else if (typeof FormData !== "undefined" && body instanceof FormData) {
                const parts = [];
                for (const [k, v] of body.entries()) {
                    parts.push(k + '=' + (typeof v === 'string' ? v : ''));
                }
                bodyText = parts.join('&');
            } else if (typeof Blob !== "undefined" && body instanceof Blob) {
                bodyText = await body.text();
            } else if (body instanceof ArrayBuffer) {
                bodyText = new TextDecoder().decode(body);
            } else if (ArrayBuffer.isView(body)) {
                bodyText = new TextDecoder().decode(body);
            } else if (!body && typeof Request !== "undefined" && input instanceof Request) {
                const cloned = input.clone();
                bodyText = await cloned.text();
            }
        } catch (_) {}

        const blockType = shouldBlock(bodyText, url, method);
        if (blockType) {
            console.log(`[FocusGram Stealth] Filtered fetch: ${blockType} -> ${url}`);
            const payload = createMockPayload(blockType, url, bodyText);
            return new Response(JSON.stringify(payload), {
                status: 200,
                headers: { "Content-Type": "application/json" }
            });
        }

        return originalFetch.apply(this, arguments);
    };

    const originalXhrOpen = XMLHttpRequest.prototype.open;
    const originalXhrSend = XMLHttpRequest.prototype.send;

    XMLHttpRequest.prototype.open = function (method, url) {
        this._fgStealthUrl = url;
        this._fgStealthMethod = method;
        return originalXhrOpen.apply(this, arguments);
    };

    XMLHttpRequest.prototype.send = function (body) {
        let bodyText = "";
        try {
            if (typeof body === "string") {
                bodyText = body;
            } else if (typeof URLSearchParams !== "undefined" && body instanceof URLSearchParams) {
                bodyText = body.toString();
            } else if (typeof FormData !== "undefined" && body instanceof FormData) {
                const parts = [];
                for (const [k, v] of body.entries()) {
                    parts.push(k + '=' + (typeof v === 'string' ? v : ''));
                }
                bodyText = parts.join('&');
            }
        } catch (_) {}

        const blockType = shouldBlock(bodyText, this._fgStealthUrl, this._fgStealthMethod);
        if (blockType) {
            console.log(`[FocusGram Stealth] Filtered XHR: ${blockType} -> ${this._fgStealthUrl}`);
            const payload = createMockPayload(blockType, this._fgStealthUrl, bodyText);
            Object.defineProperty(this, 'readyState', { value: 4, writable: true });
            Object.defineProperty(this, 'status', { value: 200, writable: true });
            Object.defineProperty(this, 'responseText', { value: JSON.stringify(payload), writable: true });
            Object.defineProperty(this, 'response', { value: JSON.stringify(payload), writable: true });
            setTimeout(() => {
                this.dispatchEvent(new Event('readystatechange'));
                this.dispatchEvent(new Event('load'));
            }, 10);
            return;
        }

        return originalXhrSend.apply(this, arguments);
    };

    // Intercept WebSocket frames (MQTT typing indicators and read receipts).
    if (typeof WebSocket !== 'undefined' && WebSocket.prototype && WebSocket.prototype.send) {
        if (!window.__FG_STEALTH_WS_HOOKED__) {
            window.__FG_STEALTH_WS_HOOKED__ = true;
            const originalWsSend = WebSocket.prototype.send;
            const wsUrlMap = new WeakMap();

            const OriginalWs = window.WebSocket;
            window.WebSocket = new Proxy(OriginalWs, {
                construct(target, args, newTarget) {
                    const ws = Reflect.construct(target, args, newTarget);
                    try {
                        wsUrlMap.set(ws, String(args[0] || ''));
                    } catch (_) {}
                    return ws;
                }
            });

            WebSocket.prototype.send = function (data) {
                try {
                    const socketUrl = wsUrlMap.get(this) || this.url || '';
                    let text = '';
                    if (typeof data === 'string') {
                        text = data;
                    } else if (data instanceof ArrayBuffer) {
                        text = new TextDecoder().decode(data);
                    } else if (ArrayBuffer.isView(data)) {
                        text = new TextDecoder().decode(data);
                    }
                    const blockType = shouldBlock(text, socketUrl, 'WS');
                    if (blockType) {
                        console.log(`[FocusGram Stealth] Filtered WebSocket frame: ${blockType} -> ${socketUrl}`);
                        return; // Drop silently
                    }
                } catch (e) {
                    console.warn('[FocusGram Stealth] WebSocket send check error:', e);
                }
                return originalWsSend.apply(this, arguments);
            };
        }
    }

    // Report false for document focus and hidden for visibility on stories to suppress auto-seen telemetry.
    const originalHasFocus = document.hasFocus.bind(document);
    document.hasFocus = function () {
        const config = window.__FOCUSGRAM_GHOST_CONFIG__;
        const storyActive = config && config.enabled && (config.hideStorySeen !== undefined ? config.hideStorySeen : config.igStory);
        if (storyActive && window.location.pathname.startsWith('/stories/')) {
            return false;
        }
        return originalHasFocus();
    };

    try {
        const originalVisibilityState = Object.getOwnPropertyDescriptor(Document.prototype, 'visibilityState');
        Object.defineProperty(document, 'visibilityState', {
            get: function () {
                const config = window.__FOCUSGRAM_GHOST_CONFIG__;
                const storyActive = config && config.enabled && (config.hideStorySeen !== undefined ? config.hideStorySeen : config.igStory);
                if (storyActive && window.location.pathname.startsWith('/stories/')) {
                    return 'hidden';
                }
                return originalVisibilityState && originalVisibilityState.get ?
                    originalVisibilityState.get.call(document) : 'visible';
            },
            configurable: true
        });

        const originalHidden = Object.getOwnPropertyDescriptor(Document.prototype, 'hidden');
        Object.defineProperty(document, 'hidden', {
            get: function () {
                const config = window.__FOCUSGRAM_GHOST_CONFIG__;
                const storyActive = config && config.enabled && (config.hideStorySeen !== undefined ? config.hideStorySeen : config.igStory);
                if (storyActive && window.location.pathname.startsWith('/stories/')) {
                    return true;
                }
                return originalHidden && originalHidden.get ?
                    originalHidden.get.call(document) : false;
            },
            configurable: true
        });
    } catch (_) {}

    // Sync updated privacy toggles from host and extension messages.
    function applyGhostSettings(source) {
        if (!source) return;
        const enabled = source.enabled !== undefined ? !!source.enabled : window.__FOCUSGRAM_GHOST_CONFIG__.enabled;
        const story = source.hideStorySeen !== undefined ? !!source.hideStorySeen : (source.igStory !== undefined ? !!source.igStory : window.__FOCUSGRAM_GHOST_CONFIG__.igStory);
        const seen = source.hideDirectSeen !== undefined ? !!source.hideDirectSeen : (source.igSeen !== undefined ? !!source.igSeen : window.__FOCUSGRAM_GHOST_CONFIG__.igSeen);
        const typing = source.hideTyping !== undefined ? !!source.hideTyping : (source.igTyping !== undefined ? !!source.igTyping : window.__FOCUSGRAM_GHOST_CONFIG__.igTyping);
        Object.assign(window.__FOCUSGRAM_GHOST_CONFIG__, {
            enabled: enabled,
            hideStorySeen: story,
            hideDirectSeen: seen,
            hideTyping: typing,
            igStory: story,
            igSeen: seen,
            igTyping: typing
        });
    }

    window.addEventListener("message", (event) => {
        if (!event.data) return;
        if (event.data.type === "GHOSTIFY_SETTINGS_UPDATE" && event.data.settings) {
            applyGhostSettings(event.data.settings);
        }
        if (event.data.source === "FOCUSGRAM_HOST" && (event.data.type === "FOCUSGRAM_UPDATE" || event.data.type === "STEALTH_UPDATE")) {
            const ghost = event.data.ghost || event.data.config || {};
            applyGhostSettings(ghost);
        }
    });

    console.log("[FocusGram] Stealth Privacy Engine active.");
})();
