/**
 * FocusGram Instant Direct Message Watcher
 *
 * Runs inside Mozilla GeckoSession to monitor incoming direct messages in real time.
 * Detects unread chat threads via structural DOM attributes, accessibility labels,
 * font-weight indicators, visual indicator dots, and document title badges, and passes
 * metadata to the native Android host via WebExtension messaging.
 *
 * Privacy Invariants:
 * - Never inspects, reads, or transmits cookies or authentication form values.
 * - Never logs private sender names or message snippet contents to console/logcat.
 * - Never executes inside login/auth pages.
 */

'use strict';

(function () {
    // Only execute in the top-level browsing context
    if (window !== window.top) return;

    // Track previously reported message states in memory to avoid redundant notification spam.
    // Permanent per-thread deduplication ensures we never re-ping for the same message state.
    const DUP_TTL_MS = 86400000; // 24 hours retention window constant
    const lastReportedThreadStates = new Map();
    let lastReportedBadgeCount = 0;
    let lastReportedTitleCount = 0;
    let scanDebounceTimer = null;
    let lastDiagnosticAt = 0;

    function isAuthPage() {
        try {
            const host = (window.location.hostname || '').toLowerCase();
            if (host.includes('facebook.com') || host.includes('accountscenter.') || host.includes('meta.com')) {
                return true;
            }
            const path = (window.location.pathname || '').toLowerCase();
            if (path.includes('/accounts/edit')) {
                return false;
            }
            if (path.startsWith('/accounts') || path.startsWith('/login') || path.startsWith('/signup') ||
                path.startsWith('/two_factor') || path.startsWith('/challenge') || path.startsWith('/onetap') ||
                path.startsWith('/privacy')) {
                return true;
            }
            if (document.getElementById('loginForm') ||
                document.querySelector('form[action*="login"], form[action*="signup"], form input[type="password"], input[name="password"]')) {
                return true;
            }
            if (path === '/' || path === '') {
                if (document.querySelector('a[href*="/accounts/login"], button[type="submit"]')) {
                    return true;
                }
            }
        } catch (_) {}
        return false;
    }

    function isUserLoggedIn() {
        try {
            if (isAuthPage()) return false;
            if (document.querySelector('a[href*="/direct/t/"]')) return true;
            if (document.querySelector('a[href*="/direct/"], nav, [role="navigation"], [aria-label*="Direct" i], [aria-label*="Messages" i]')) {
                return true;
            }
            const path = (window.location.pathname || '').toLowerCase();
            if (path.startsWith('/direct/inbox') && !document.querySelector('form, input[type="password"]')) {
                return true;
            }
            // If not on an auth/login page, consider logged in on instagram.com
            return !isAuthPage();
        } catch (_) {
            return false;
        }
    }

    /**
     * Dispatches event to native host via standard WebExtension messaging.
     * Content scripts communicate with background.js via sendMessage, which routes
     * directly across nativePort to Android ExtensionManager.
     */
    // The normal content-script -> background -> native route is retained below.
    // Use GeckoView's one-off native message API as the direct fallback: a DM is
    // an event, not a long-lived command stream, so this avoids Port lifecycle
    // races while the headless session and extension background are starting.
    function postDirectlyToNative(message) {
        if (!message || typeof browser === 'undefined' || !browser.runtime ||
            typeof browser.runtime.sendNativeMessage !== 'function') return;
        try {
            const result = browser.runtime.sendNativeMessage('focusgram', message);
            if (result && typeof result.catch === 'function') result.catch(() => {});
        } catch (_) {
            // The background-script route below remains available as a fallback.
        }
    }

    function postToNative(message) {
        if (!message) return;
        // Send on the direct port as well. DmNotificationManager atomically
        // deduplicates events that also arrive through background.js.
        postDirectlyToNative(message);
        try {
            if (typeof browser !== 'undefined' && browser.runtime) {
                if (typeof browser.runtime.sendMessage === 'function') {
                    browser.runtime.sendMessage(message).catch(() => {});
                }
            }
        } catch (_) {}
    }

    function cleanSenderName(raw) {
        if (!raw) return '';
        return raw
            // Strip internationalized avatar alt prefixes (German, French, Spanish, Portuguese, Italian, Russian, English, etc.)
            .replace(/^(?:profilbild von|photo de profil de|foto del? perfil de|foto do perfil de|immagine del profilo di|фото профиля|profile picture of|profile photo of|avatar of)\s+/i, '')
            // Strip internationalized avatar alt suffixes (English, Japanese, Chinese, Korean, etc.)
            .replace(/(?:'s profile picture|'s profile photo|'s photo| profile picture|'s avatar|のプロフィール写真|的个人头像|님의 프로필 사진).*$/i, '')
            .trim();
    }

    function getViewerUsername() {
        try {
            if (window._sharedData && window._sharedData.config && window._sharedData.config.viewer) {
                const u = window._sharedData.config.viewer.username;
                if (u) return u;
            }
        } catch (_) {}
        try {
            if (window.__initialData && window.__initialData.data && window.__initialData.data.viewer) {
                const u = window.__initialData.data.viewer.username;
                if (u) return u;
            }
        } catch (_) {}
        try {
            // Profile link in nav or header account dropdown
            const profileLink = document.querySelector('nav a[href^="/"][role="link"], a[href^="/"]:has(img[data-testid="user-avatar"])');
            if (profileLink) {
                const href = profileLink.getAttribute('href') || '';
                const match = href.match(/^\/([a-zA-Z0-9._]+)\/?$/);
                if (match && match[1] !== 'explore' && match[1] !== 'direct' && match[1] !== 'reels') {
                    return match[1];
                }
            }
        } catch (_) {}
        try {
            // Account switcher button in header often has span.x1s688f with viewer username
            const headerBtn = document.querySelector('header div[role="button"] span.x1s688f, header span.x1s688f');
            if (headerBtn) {
                const txt = (headerBtn.textContent || '').trim();
                if (txt && !txt.includes(' ') && txt.length < 35 && !/unread|messages|instagram/i.test(txt)) {
                    return txt;
                }
            }
        } catch (_) {}
        return '';
    }

    function isViewer(text) {
        if (!text) return false;
        const viewer = getViewerUsername();
        if (!viewer) return false;
        return text.trim().toLowerCase() === viewer.trim().toLowerCase();
    }

    function isUnreadCountPhrase(text) {
        if (!text) return false;
        const trimmed = text.trim();
        if (/^\d+\+?$/.test(trimmed)) return true;
        if (/(?:you\s+have\s+)?\d+\+?\s+(?:new|unread)\s+messages?/i.test(trimmed)) return true;
        if (/^(?:new|unread)\s+messages?$/i.test(trimmed)) return true;
        if (/^(?:unread|messages|chats|direct)$/i.test(trimmed)) return true;
        return false;
    }

    function isTimestampOrStatus(text) {
        if (!text) return true;
        const trimmed = text.trim();
        if (trimmed.length === 0) return true;
        if (/^(?:[·•\s-]*\d+\s*[smhdw]|now|just now|yesterday)$/i.test(trimmed)) {
            return true;
        }
        if (/^active(?:\s+now|\s+\d+[smhdw]|\s+today|\s+yesterday)?$/i.test(trimmed)) {
            return true;
        }
        if (/^[·•\s\-_–—|]+$/.test(trimmed)) {
            return true;
        }
        return false;
    }

    function isOutgoingMessage(text) {
        if (!text) return false;
        const trimmed = text.trim();
        // Exact outgoing status words (seen, visto, vu, etc.) with optional timestamp
        if (/^(?:seen|visto|vu|gesehen|visualizzato|既読)(?:\s+[·•\s\dsmhdw]+)?$/i.test(trimmed)) {
            return true;
        }
        // Multi-lingual outgoing message prefixes (You:, Tú:, Tu:, Vous:, Du:, Você:, Вы:, Ty:, Jij:, أنت:, 你:, あなた:, 회원님이)
        if (/^(?:you|tú|tu|vous|du|você|вы|ty|jij|أنت|你|あなた|회원님이)\s*:\s*/i.test(trimmed)) {
            return true;
        }
        // Multi-lingual outgoing action prefixes (You sent, You shared, Enviaste, etc.)
        if (/^(?:you\s+(?:sent|shared|replied|reacted|forwarded)|enviaste|vous\s+avez\s+envoyé|gesendet|hai\s+inviato|enviou|вы\s+отправили)/i.test(trimmed)) {
            return true;
        }
        return false;
    }

    function isNotesElement(el) {
        if (!el) return false;
        try {
            // A conversation row with a real contact title or direct-thread link
            // is never a Note. Instagram can put the entire inbox below a broad
            // container whose accessibility label includes "Notes", so inspect
            // the row's own identity before applying Notes exclusions.
            if (el.matches('a[href*="/direct/t/"]') ||
                el.querySelector('a[href*="/direct/t/"], span[title]')) {
                return false;
            }
            // Do not use StyleX layout classes (in particular x1c4vz4f) here. Instagram
            // now uses them on ordinary inbox rows too, so treating that class as Notes
            // makes every conversation invisible to the watcher.
            if (el.closest('[aria-label*="Note" i], [aria-label*="Notes" i]')) return true;
            if (el.closest('[data-testid*="note" i], [data-visualcompletion*="note" i]')) return true;
            // Notes feature note speech bubble and explicit Notes labels.
            if (el.querySelector('.xsnw5ke') || el.closest('.xsnw5ke')) return true;
            const text = (el.textContent || '').trim();
            if (/^(?:your|add a)\s+note\b/i.test(text)) return true;
        } catch (_) {}
        return false;
    }

    function isChatMuted(container) {
        if (!container) return false;
        try {
            // Check aria-label / title attributes for muted state across languages
            if (container.querySelector('svg[aria-label="Muted" i], [aria-label*="Muted" i], [aria-label*="Silenciado" i], [aria-label*="Stumm" i], [aria-label*="Masqu" i]')) {
                return true;
            }
            const titles = container.querySelectorAll('svg title');
            for (let i = 0; i < titles.length; i++) {
                if (/muted|silenciado|stumm|masqu|sourdine|disattivato/i.test(titles[i].textContent || '')) {
                    return true;
                }
            }
            // Instagram web bell-with-slash SVG path icon for muted chats
            if (container.querySelector('svg path[d*="M15.209"], svg path[d*="18.294a1"]')) {
                return true;
            }
        } catch (_) {}
        return false;
    }

    // Outer inbox containers vary between Instagram rollouts.  Sender titles
    // and unread markers are far more stable, so resolve their actual row
    // before extracting state rather than relying on a single role wrapper.
    function getThreadContainer(seed) {
        if (!seed || seed.nodeType !== Node.ELEMENT_NODE) return null;

        const roleContainer = seed.closest('div[role="listitem"], div[role="button"]');
        if (roleContainer) return roleContainer;

        let current = seed;
        for (let depth = 0; current && depth < 12; depth += 1, current = current.parentElement) {
            const hasSender = current.matches('span[title]') || !!current.querySelector('span[title]');
            const hasConversationDetail = !!current.querySelector(
                'abbr, time, .x1s688f, .x5n08af, [data-visualcompletion="ignore"]'
            );
            if (hasSender && hasConversationDetail) return current;
        }
        return seed;
    }

    function isElementUnread(link, senderName) {
        if (!link) return false;
        if (isNotesElement(link)) return false;
        const container = getThreadContainer(link) || link;
        if (isNotesElement(container)) return false;
        if (container.closest('header, nav, [role="banner"], [role="navigation"]')) return false;
        if (isChatMuted(container)) return false;

        // 1. Blue dot with accessible "Unread" label — highest confidence signal
        const unreadIndicators = container.querySelectorAll('[data-visualcompletion="ignore"], .x10l6tqk, .x1i1rx1s');
        for (let i = 0; i < unreadIndicators.length; i++) {
            if (/unread|ungelesen|non\s+lu|no\s+le[íi]do/i.test(unreadIndicators[i].textContent || '')) {
                return true;
            }
        }

        // 2. Aria-label "unread" check on link, container, and descendants (multi-lingual)
        const unreadRegex = /(?:^|[\s,([{·•])(?:unread|ungelesen|non\s+lu|no\s+le[íi]do|n[ãa]o\s+lido[as]?|non\s+lett[oia]|непрочитан|未読|未读|未讀|읽지\s*않음|غير\s+مقروء)/i;
        const ariaLabel = ((link.getAttribute('aria-label') || '') + ' ' + (container.getAttribute('aria-label') || '')).toLowerCase();
        if (unreadRegex.test(ariaLabel)) return true;
        const ariaChildren = container.querySelectorAll('[aria-label]');
        for (let i = 0; i < ariaChildren.length; i++) {
            if (unreadRegex.test((ariaChildren[i].getAttribute('aria-label') || '').toLowerCase())) return true;
        }

        // 3. Explicit read-state short-circuit:
        //    Instagram applies .xo1l8bm / .x1roi4f4 on preview spans when a thread IS read,
        //    and .x1s688f / .x5n08af when it is UNREAD. If only read classes are present,
        //    we can confidently return false without scanning further.
        const hasReadStateClass = !!container.querySelector('.xo1l8bm, .x1roi4f4');
        const hasUnreadStateClass = !!container.querySelector('.x1s688f, .x5n08af');
        if (hasReadStateClass && !hasUnreadStateClass) return false;

        // 4. StyleX unread classes present on a non-name, non-outgoing preview span
        if (hasUnreadStateClass) {
            const unreadSpans = container.querySelectorAll('.x1s688f, .x5n08af');
            for (let i = 0; i < unreadSpans.length; i++) {
                const el = unreadSpans[i];
                // Skip if this element IS or CONTAINS the contact name title span
                if (el.querySelector('span[title]') || el.closest('span[title]')) continue;
                const txt = (el.textContent || '').trim();
                if (!txt) continue;
                if (isTimestampOrStatus(txt) || isOutgoingMessage(txt) || isUnreadCountPhrase(txt)) continue;
                // Skip if the text matches the sender name (contact name rendered without span[title])
                if (senderName && txt.toLowerCase() === senderName.toLowerCase()) continue;
                return true;
            }
        }

        // 5. Unread count text pattern in thread row ("4+ new messages", "1 new message")
        const fullRowText = (container.textContent || '');
        if (/(?:you\s+have\s+)?\d+\+?\s+(?:new|unread)\s+messages?/i.test(fullRowText)) return true;

        // 6. Standalone badge count span (e.g. <span>3+</span>)
        const leaves = container.querySelectorAll('span, div');
        for (let i = 0; i < leaves.length; i++) {
            const el = leaves[i];
            if (el.children.length === 0) {
                if (el.closest('span[title]')) continue;
                const txt = (el.textContent || '').trim();
                if (/^\d+\+?$/.test(txt) && parseInt(txt, 10) > 0) return true;
                if (/\b\d*\+?\s*new\s+messages?\b/i.test(txt) || /\b\d*\+?\s*unread\s+messages?\b/i.test(txt)) return true;
            }
        }

        // 7. Visual blue dot (Meta blue rgb(0, 149, 246)) — empty leaf element with Meta blue background
        const descendants = container.querySelectorAll('div, span, circle, svg');
        for (let i = 0; i < descendants.length; i++) {
            const el = descendants[i];
            if (el.children.length > 0 || el.tagName === 'IMG' || el.querySelector('img')) continue;
            if ((el.textContent || '').trim().length > 0) continue;
            try {
                const style = window.getComputedStyle(el);
                if (style.display === 'none' || style.visibility === 'hidden') continue;
                const bg = style.backgroundColor || style.fill || '';
                const match = bg.match(/rgba?\(\s*(\d+)[\s,]+(\d+)[\s,]+(\d+)(?:[\s,/]+([\d.]+))?\s*\)/);
                if (match) {
                    const r = parseInt(match[1], 10);
                    const b = parseInt(match[3], 10);
                    const a = match[4] !== undefined ? parseFloat(match[4]) : 1.0;
                    if (a > 0.1 && b >= 150 && r <= 140 && b > r + 25) return true;
                }
            } catch (_) {}
        }

        return false;
    }

    function extractThreadInfo(link) {
        if (!link) return null;
        if (link.closest('header, nav, [role="banner"], [role="navigation"]')) {
            return null;
        }
        const container = getThreadContainer(link) || link;
        if (container.closest('header, nav, [role="banner"], [role="navigation"]')) {
            return null;
        }
        if (isNotesElement(container)) {
            return null;
        }
        if (isChatMuted(container)) {
            return { isMuted: true };
        }
        const threadLink = link.tagName === 'A' && (link.getAttribute('href') || '').includes('/direct/t/')
            ? link
            : (container.querySelector('a[href*="/direct/t/"]') || link.closest('a[href*="/direct/t/"]') || link);
        const href = threadLink.getAttribute('href') || '';
        const match = href.match(/\/direct\/t\/([^\/\?]+)/);
        let threadId = match ? match[1] : '';

        let sender = '';
        let snippet = '';
        let boldSnippet = '';
        let messageCount = 1;
        let messageCountStr = '';
        let messageTime = '';

        // Extract message timestamp (e.g. "2m", "4h", "now", or from <abbr>/<time>)
        const abbrEl = container.querySelector('abbr, time');
        if (abbrEl) {
            messageTime = (abbrEl.getAttribute('aria-label') || abbrEl.getAttribute('title') || abbrEl.textContent || '').trim();
        }
        if (!messageTime) {
            const timeLeaves = container.querySelectorAll('span, div');
            for (let i = 0; i < timeLeaves.length; i++) {
                if (timeLeaves[i].children.length === 0) {
                    const txt = (timeLeaves[i].textContent || '').trim();
                    if (/^(?:[·•\s-]*\d+\s*[smhdw]|now|just now|yesterday)$/i.test(txt)) {
                        messageTime = txt.replace(/^[·•\s-]+/, '').trim();
                        break;
                    }
                }
            }
        }

        // Extract explicit unread message count pattern (e.g. "2 new messages", "3+ new messages", "4 new messages", "3+ unread messages")
        const fullRowText = (container.textContent || '');
        const countMatch = fullRowText.match(/(?:you\s+have\s+)?(\d+\+?)\s+(?:new|unread)\s+messages?/i);
        if (countMatch) {
            messageCountStr = countMatch[1];
            messageCount = parseInt(countMatch[1], 10) || 1;
        } else {
            // Also check for standalone unread badge counts (e.g. <span>3+</span>, <span>2</span>)
            const badges = container.querySelectorAll('span, div');
            for (let i = 0; i < badges.length; i++) {
                if (badges[i].children.length === 0) {
                    const txt = (badges[i].textContent || '').trim();
                    if (/^\d+\+?$/.test(txt) && parseInt(txt, 10) > 0) {
                        messageCountStr = txt;
                        messageCount = parseInt(txt, 10);
                        break;
                    }
                }
            }
        }

        // 1. Extract contact display name / username from canonical span[title]
        const titleSpan = container.querySelector('span[title]');
        if (titleSpan) {
            const titleVal = cleanSenderName((titleSpan.getAttribute('title') || titleSpan.textContent || '').trim());
            if (titleVal && !isTimestampOrStatus(titleVal) && !isOutgoingMessage(titleVal) &&
                !isUnreadCountPhrase(titleVal) && !isViewer(titleVal)) {
                sender = titleVal;
            }
        }

        // 2. Fallback: Extract sender username from avatar image alt attribute with i18n support
        if (!sender) {
            const avatarImg = container.querySelector('img[alt]');
            if (avatarImg) {
                const alt = cleanSenderName((avatarImg.getAttribute('alt') || '').trim());
                if (alt && !isTimestampOrStatus(alt) && !isOutgoingMessage(alt) &&
                    !isUnreadCountPhrase(alt) && !isViewer(alt)) {
                    sender = alt;
                }
            }
        }

        // 3. Inspect text leaves to extract snippet and candidate sender
        const leaves = container.querySelectorAll('span, div');
        const textLeaves = [];
        for (let i = 0; i < leaves.length; i++) {
            const el = leaves[i];
            if (el.children.length === 0) {
                if (el.closest('[data-visualcompletion="ignore"]') || el.textContent.trim().toLowerCase() === 'unread') continue;

                const txt = (el.textContent || '').trim();
                if (txt.length > 0 && !isTimestampOrStatus(txt) && !isUnreadCountPhrase(txt) && !isViewer(txt)) {
                    let isBold = false;
                    try {
                        const weight = window.getComputedStyle(el).fontWeight;
                        isBold = weight === 'bold' || weight === 'bolder' || parseInt(weight, 10) >= 550;
                    } catch (_) {}
                    if (!isBold && (el.closest('.x1s688f, .x5n08af') || el.classList.contains('x1s688f') || el.classList.contains('x5n08af'))) {
                        isBold = true;
                    }
                    textLeaves.push({ text: txt, isBold: isBold });
                }
            }
        }

        if (textLeaves.length > 0) {
            if (!sender) {
                sender = textLeaves[0].text;
            }
            for (let i = 0; i < textLeaves.length; i++) {
                const item = textLeaves[i];
                if (sender && item.text.toLowerCase() === sender.toLowerCase()) continue;
                if (item.isBold && !boldSnippet && !isOutgoingMessage(item.text)) {
                    boldSnippet = item.text;
                }
                if (!snippet && !isOutgoingMessage(item.text)) {
                    snippet = item.text;
                }
            }
        }

        if (boldSnippet) {
            snippet = boldSnippet;
        }

        // If snippet is "<Name> sent an attachment.", extract name if sender is still missing
        if (snippet) {
            const sentMatch = snippet.match(/^(.+?)\s+sent\s+(?:an?\s+)?attachment/i);
            if (sentMatch && (!sender || sender === snippet)) {
                const extracted = sentMatch[1].trim();
                if (!isViewer(extracted) && !isUnreadCountPhrase(extracted)) {
                    sender = extracted;
                }
            }
        }

        // Fallback: aria-label parsing
        if (!sender) {
            const aria = (link.getAttribute('aria-label') || container.getAttribute('aria-label') || '');
            if (aria) {
                const parts = aria.split(',').map(p => p.trim());
                if (parts.length > 0) {
                    const cand = cleanSenderName(parts[0]);
                    if (cand && !isTimestampOrStatus(cand) && !isOutgoingMessage(cand) &&
                        !isUnreadCountPhrase(cand) && !isViewer(cand)) {
                        sender = cand;
                    }
                }
                if (!snippet && parts.length > 1 && !/(?:^|\s)unread(?:\s|$|\.|\,)/i.test(parts[1])) {
                    snippet = parts[1];
                }
            }
        }

        // Strict sender validation: must not be empty, viewer, or unread count phrase
        if (!sender || isViewer(sender) || isUnreadCountPhrase(sender)) {
            return null;
        }

        // Clean snippet: remove international relative timestamps (min, std, h, d, etc.)
        if (snippet) {
            snippet = snippet.replace(/\s*(?:[·•\-_–—|]|\s-\s)\s*\d+\s*(?:[smhdw]|min|std|ч|г|d|m|h|j|seg|分|초|시간|일).*$/is, '').trim();
            snippet = snippet.replace(/[·•\s\-_–—|]+$/, '').trim();
        }

        // Format snippet according to unread count
        if (messageCount > 1 || messageCountStr) {
            const countDisplay = messageCountStr || (messageCount + '+');
            snippet = `${countDisplay} new messages`;
        } else if (boldSnippet) {
            snippet = boldSnippet;
        } else if (!snippet || snippet === 'Sent a message') {
            snippet = '1 new message';
        }

        if (!threadId && sender) {
            threadId = 'thread_' + sender.toLowerCase().replace(/[^a-z0-9_]/g, '');
        }

        return {
            threadId: threadId,
            sender: sender,
            snippet: snippet,
            messageCount: messageCount,
            messageCountStr: messageCountStr,
            messageTime: messageTime
        };
    }

    function scanTitleBadge() {
        try {
            const title = document.title || '';
            const match = title.match(/[\(\[]\s*(\d+)\+?\s*[\)\]]/);
            const count = match ? parseInt(match[1], 10) : 0;
            if (count > 0 && count !== lastReportedTitleCount) {
                lastReportedTitleCount = count;
                console.log('[FocusGram DM Watcher] Document title unread count: ' + count);
                postToNative({
                    action: 'dmBadgeCountChanged',
                    unreadCount: count,
                    timestamp: Date.now()
                });
            } else if (count === 0 && lastReportedTitleCount > 0) {
                lastReportedTitleCount = 0;
                postToNative({
                    action: 'dmBadgeCountChanged',
                    unreadCount: 0,
                    timestamp: Date.now()
                });
            }
            return count;
        } catch (_) {
            return 0;
        }
    }

    function scanDirectInbox() {
        const path = (window.location.pathname || '').toLowerCase();
        // Strictly only scan inbox threads when viewing direct messages
        if (!path.startsWith('/direct')) {
            return false;
        }

        const chatsContainer = document.querySelector('div[aria-label="Chats"], div[aria-label="Messages"], div[role="list"], main') || document.body;
        // Current Instagram inbox rows are often role=button divs with no /direct/t/
        // anchor at all. Scan anchors and rows together instead of using rows only when
        // no anchor happens to exist elsewhere in the inbox.
        const candidates = new Set([
            ...document.querySelectorAll('a[href*="/direct/t/"]'),
            ...chatsContainer.querySelectorAll('div[role="listitem"], div[role="button"]')
        ]);
        // These inner nodes occur in the captured inbox HTML even when the
        // surrounding role wrappers are absent or have moved.
        document.querySelectorAll('span[title], [data-visualcompletion="ignore"], .x1s688f.x5n08af')
            .forEach(node => {
                const row = getThreadContainer(node);
                if (row) candidates.add(row);
            });

        const threadLinks = Array.from(candidates).filter(el => {
            if (isNotesElement(el)) return false;
            if (el.closest('header, nav, [role="banner"], [role="navigation"]')) return false;
            const hasConversationIdentity = el.matches('a[href*="/direct/t/"], span[title]') ||
                !!el.querySelector('a[href*="/direct/t/"], span[title]');
            const hasConversationDetail = !!el.querySelector('abbr, time, .x1s688f, .x5n08af, [data-visualcompletion="ignore"]');
            return hasConversationIdentity && hasConversationDetail;
        });
        if (threadLinks.length === 0) {
            reportWatcherStatus(0, 0);
            return false;
        }

        const now = Date.now();
        let foundUnreadThread = false;

        threadLinks.forEach(link => {
            if (isNotesElement(link)) return;

            const info = extractThreadInfo(link);
            if (!info || info.isMuted) return;
            if (!info.threadId || !info.sender || isViewer(info.sender) || isUnreadCountPhrase(info.sender)) return;

            // Check if thread has unread indicators
            const unread = isElementUnread(link, info.sender);
            if (!unread) {
                // Thread is present in inbox and is confirmed READ.
                // Clear any lingering active notification and deduplication entry for this thread.
                postToNative({
                    action: 'dmThreadRead',
                    threadId: info.threadId,
                    sender: info.sender
                });
                lastReportedThreadStates.delete(info.threadId);
                return;
            }

            // Ignore messages sent by the current user
            if (isOutgoingMessage(info.snippet)) {
                return;
            }

            foundUnreadThread = true;

            // Deduplicate in-memory by threadId and composite stateKey with sliding DUP_TTL_MS window.
            // Relative timestamps change on their own ("2m" -> "3m") and must not
            // generate another alert. The unread count and preview identify a new state.
            const countPart = info.messageCountStr || info.messageCount;
            const stateKey = `${info.threadId}:${info.sender}:${countPart}:${info.snippet}`;
            const existing = lastReportedThreadStates.get(info.threadId);
            if (existing && existing.stateKey === stateKey) {
                return;
            }

            lastReportedThreadStates.set(info.threadId, { stateKey: stateKey, timestamp: now });

            // Cap in-memory map size to prevent unbounded growth
            if (lastReportedThreadStates.size > 50) {
                const firstKey = lastReportedThreadStates.keys().next().value;
                if (firstKey) lastReportedThreadStates.delete(firstKey);
            }

            console.log('[FocusGram DM Watcher] Detected unread direct message event');

            postToNative({
                action: 'dmReceived',
                threadId: info.threadId,
                sender: info.sender,
                snippet: info.snippet,
                messageCount: info.messageCount,
                messageCountStr: info.messageCountStr,
                messageTime: info.messageTime,
                timestamp: now
            });
        });

        // If direct inbox has loaded conversation threads and none are unread,
        // clear all notifications for direct messages.
        if (!foundUnreadThread && threadLinks.length > 0) {
            postToNative({
                action: 'dmAllThreadsRead',
                timestamp: now
            });
        }

        reportWatcherStatus(threadLinks.length, foundUnreadThread ? 1 : 0);

        return foundUnreadThread;
    }

    // Delivery diagnostics deliberately contain no account, sender, preview, or
    // message text. They let native logcat distinguish a DOM mismatch from a
    // failed WebExtension bridge without exposing private conversations.
    function reportWatcherStatus(candidateCount, unreadDetected) {
        const now = Date.now();
        if (now - lastDiagnosticAt < 15000) return;
        lastDiagnosticAt = now;
        postToNative({
            action: 'dmWatcherStatus',
            path: (window.location.pathname || '').toLowerCase(),
            candidateCount: candidateCount,
            unreadDetected: unreadDetected,
            timestamp: now
        });
    }

    function scanInAppToasts() {
        try {
            const toastLinks = document.querySelectorAll('div[role="alert"] a[href*="/direct/t/"], div:has(> a[href*="/direct/t/"]):not([role="listitem"]) a[href*="/direct/t/"]');
            if (toastLinks.length === 0) return false;

            let detected = false;
            toastLinks.forEach(link => {
                const container = link.closest('div[role="alert"], div[role="dialog"]') || link;
                if (isChatMuted(container)) return;
                const info = extractThreadInfo(link);
                if (!info || info.isMuted) return;
                if (!info.threadId || !info.sender || isViewer(info.sender) || isUnreadCountPhrase(info.sender)) return;
                if (isOutgoingMessage(info.snippet)) return;

                const now = Date.now();
                const countPart = info.messageCountStr || info.messageCount;
                const stateKey = `toast:${info.threadId}:${info.sender}:${countPart}:${info.snippet}`;
                const existing = lastReportedThreadStates.get(info.threadId);
                if (existing && existing.stateKey === stateKey) {
                    return;
                }

                lastReportedThreadStates.set(info.threadId, { stateKey: stateKey, timestamp: now });
                detected = true;

                postToNative({
                    action: 'dmReceived',
                    threadId: info.threadId,
                    sender: info.sender,
                    snippet: info.snippet,
                    messageCount: info.messageCount,
                    messageCountStr: info.messageCountStr,
                    messageTime: info.messageTime,
                    timestamp: now
                });
            });
            return detected;
        } catch (_) {
            return false;
        }
    }

    function scanGlobalDirectBadges() {
        const directIconLinks = document.querySelectorAll(
            'a[href*="/direct/inbox/"], a[href*="/direct/"], [aria-label*="Direct" i], [aria-label*="Messages" i]'
        );

        let maxCount = 0;
        directIconLinks.forEach(item => {
            const aria = (item.getAttribute('aria-label') || '').toLowerCase();
            let count = 0;

            const elements = item.querySelectorAll('span, div');
            for (let i = 0; i < elements.length; i++) {
                const text = (elements[i].textContent || '').trim();
                if (/^\d+$/.test(text)) {
                    count = parseInt(text, 10);
                    break;
                }
            }

            if (count === 0 && aria.includes('unread')) {
                const match = aria.match(/(\d+)\s+unread/i);
                count = match ? parseInt(match[1], 10) : 1;
            }

            if (count > maxCount) {
                maxCount = count;
            }
        });

        if (maxCount > 0 && maxCount !== lastReportedBadgeCount) {
            lastReportedBadgeCount = maxCount;
            console.log('[FocusGram DM Watcher] Direct message badge count updated: ' + maxCount);
            postToNative({
                action: 'dmBadgeCountChanged',
                unreadCount: maxCount,
                timestamp: Date.now()
            });
        } else if (maxCount === 0 && lastReportedBadgeCount > 0) {
            lastReportedBadgeCount = 0;
            postToNative({
                action: 'dmBadgeCountChanged',
                unreadCount: 0,
                timestamp: Date.now()
            });
        }
        return maxCount;
    }

    function scanAll() {
        if (!isUserLoggedIn()) return;

        // 1. Scan direct inbox threads if present in the DOM FIRST
        const hasUnreadInbox = scanDirectInbox();

        // 2. Scan in-app notification toasts if present
        const hasToast = scanInAppToasts();

        // If specific inbox threads or toasts were detected and reported,
        // we can skip the generic badge scan to avoid duplicate alerts.
        if (hasUnreadInbox || hasToast) {
            return;
        }

        // 3. Scan title for unread badge count (e.g. "(1) Instagram", "(1) Messages • Instagram")
        // Always runs as fallback even on inbox page when specific threads couldn't be parsed!
        scanTitleBadge();

        // 4. Scan global direct badges (e.g. navigation icon badge count)
        scanGlobalDirectBadges();
    }

    function scheduleScan() {
        if (scanDebounceTimer) {
            clearTimeout(scanDebounceTimer);
        }
        scanDebounceTimer = setTimeout(() => {
            scanDebounceTimer = null;
            scanAll();
        }, 50);
    }

    const observer = new MutationObserver(scheduleScan);
    function startObserver() {
        const target = document.body || document.documentElement;
        if (target) {
            observer.observe(target, {
                childList: true,
                subtree: true,
                attributes: true,
                characterData: true,
                attributeFilter: ['class', 'aria-label', 'data-visualcompletion']
            });
            postToNative({
                action: 'dmWatcherReady',
                path: (window.location.pathname || '').toLowerCase(),
                timestamp: Date.now()
            });
            scanAll();
        } else {
            setTimeout(startObserver, 100);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', startObserver);
    } else {
        startObserver();
    }

    setInterval(scanAll, 2000);

    console.log('[FocusGram DM Watcher] Initialized real-time DM watcher.');
})();
