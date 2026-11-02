/**
 * Native touch physics, keyboard resize adaptation, file input enhancement,
 * and live bio font styling.
 */

'use strict';

(function () {
    function isAuthPage() {
        try {
            var host = (window.location.hostname || '').toLowerCase();
            if (host.includes('facebook.com') || host.includes('accountscenter.') || host.includes('meta.com')) return true;
            var path = (window.location.pathname || '').toLowerCase();
            if (path.includes('/accounts/edit')) return false;
            if (path.startsWith('/accounts') || path.startsWith('/login') || path.startsWith('/signup') ||
                path.startsWith('/two_factor') || path.startsWith('/challenge') || path.startsWith('/onetap') ||
                path.startsWith('/privacy')) return true;
            if (document.getElementById('loginForm') ||
                document.querySelector('form[action*="login"], form[action*="signup"], form input[type="password"], input[name="password"]')) return true;
        } catch (_) {}
        return false;
    }

    if (isAuthPage()) return;

    if (window.__fgNativeFeelInitialized) return;
    window.__fgNativeFeelInitialized = true;

    // Clipboard bridging: forward web page navigator.clipboard.writeText and execCommand to FocusGram
    try {
        function sanitizeUrlsInText(raw) {
            if (!raw || typeof raw !== 'string') return raw;
            try {
                return raw.replace(/https?:\/\/[^\s"'<>]+/gi, function (match) {
                    try {
                        const u = new URL(match);
                        const trackingNames = [
                            'igsh', 'igshid', 'fbclid', 'ref', 'hl', 'taken-by',
                            'source_ve_path', 'ig_rid'
                        ];
                        let changed = false;
                        const params = new URLSearchParams(u.search);
                        const keys = Array.from(params.keys());
                        for (let i = 0; i < keys.length; i++) {
                            const k = keys[i];
                            const lowerK = k.toLowerCase().trim();
                            if (lowerK.startsWith('utm_') || trackingNames.includes(lowerK)) {
                                params.delete(k);
                                changed = true;
                            }
                        }
                        if (changed) {
                            const newSearch = params.toString();
                            u.search = newSearch ? ('?' + newSearch) : '';
                            return u.toString();
                        }
                    } catch (_) {}
                    return match;
                });
            } catch (_) {
                return raw;
            }
        }

        function isBareInstagramRootUrl(val) {
            if (!val || typeof val !== 'string') return true;
            try {
                const trimmed = val.trim();
                if (!trimmed) return true;
                const base = trimmed.split('?')[0].split('#')[0].replace(/\/+$/, '');
                if (base === 'https://www.instagram.com' || base === 'http://www.instagram.com' ||
                    base === 'https://instagram.com' || base === 'http://instagram.com' ||
                    base === '') {
                    return true;
                }
                const u = new URL(trimmed);
                const host = u.hostname.toLowerCase();
                if (host === 'instagram.com' || host === 'www.instagram.com') {
                    const p = u.pathname.replace(/\/+$/, '');
                    if (p === '' || p === '/') return true;
                }
            } catch (_) {}
            return false;
        }

        function extractUrlFromDialog(dialog) {
            if (!dialog) return '';
            try {
                const inputs = dialog.querySelectorAll('input, textarea');
                for (let i = 0; i < inputs.length; i++) {
                    const inp = inputs[i];
                    const val = (inp.value || inp.getAttribute('value') || inp.placeholder || '').trim();
                    if (val && !isBareInstagramRootUrl(val)) {
                        if (val.includes('/p/') || val.includes('/reel/') || val.includes('/reels/') || val.includes('/stories/') || val.includes('instagram.com/')) {
                            return val;
                        }
                    }
                }
                const links = dialog.querySelectorAll('a[href*="/p/"], a[href*="/reel/"], a[href*="/reels/"], a[href*="/stories/"], a[href*="instagram.com/"]');
                for (let i = 0; i < links.length; i++) {
                    const h = (links[i].href || links[i].getAttribute('href') || '').trim();
                    if (h && !isBareInstagramRootUrl(h)) {
                        return h;
                    }
                }
                const html = dialog.innerHTML || '';
                const match = html.match(/https?:\/\/(?:www\.)?instagram\.com\/(?:p|reel|reels|stories)\/[A-Za-z0-9_-]+(?:\/[^\s"'<>]*)?/i);
                if (match && match[0] && !isBareInstagramRootUrl(match[0])) {
                    return match[0];
                }
                const text = dialog.textContent || '';
                const textMatch = text.match(/https?:\/\/[^\s"'<>]+/i);
                if (textMatch && textMatch[0] && !isBareInstagramRootUrl(textMatch[0])) {
                    return textMatch[0];
                }
            } catch (_) {}
            return '';
        }

        function getProfileUrlIfOnProfilePage() {
            try {
                const path = (window.location.pathname || '').trim();
                if (!path || path === '/' || path.length < 2) return '';
                const nonProfilePrefixes = [
                    '/p/', '/reel/', '/reels/', '/direct/', '/explore/', '/stories/',
                    '/accounts/', '/developer/', '/about/', '/legal/', '/privacy/'
                ];
                const lowerPath = path.toLowerCase();
                for (let i = 0; i < nonProfilePrefixes.length; i++) {
                    if (lowerPath.startsWith(nonProfilePrefixes[i])) return '';
                }
                const segments = path.split('/').filter(Boolean);
                if (segments.length === 1) {
                    return window.location.origin + '/' + segments[0] + '/';
                }
            } catch (_) {}
            return '';
        }

        let lastActivePostUrl = '';
        let lastActivePostTimestamp = 0;

        function trackActivePost(target) {
            if (!target) return;
            try {
                const article = target.closest('article, [role="article"], div[data-testid*="post"]');
                if (article) {
                    const link = article.querySelector('a[href*="/p/"], a[href*="/reel/"], a[href*="/reels/"]');
                    if (link && link.href) {
                        const cleanHref = link.href.split('?')[0].replace(/\/comments\/?.*$/, '/');
                        if (!isBareInstagramRootUrl(cleanHref)) {
                            lastActivePostUrl = cleanHref;
                            lastActivePostTimestamp = Date.now();
                            return;
                        }
                    }
                }
                const dialog = target.closest('div[role="dialog"]');
                if (dialog) {
                    const extracted = extractUrlFromDialog(dialog);
                    if (extracted) {
                        lastActivePostUrl = extracted;
                        lastActivePostTimestamp = Date.now();
                        return;
                    }
                }
                const path = window.location.pathname || '';
                const m = path.match(/\/(?:p|reel|reels)\/([A-Za-z0-9_-]+)/);
                if (m && m[1]) {
                    lastActivePostUrl = window.location.origin + '/' + (path.includes('/reel') ? 'reel' : 'p') + '/' + m[1] + '/';
                    lastActivePostTimestamp = Date.now();
                }
            } catch (_) {}
        }

        let lastDispatchedText = '';
        let lastDispatchedTime = 0;
        const dispatchCopyEvent = function (text) {
            if (!text) return;
            const cleanText = sanitizeUrlsInText(String(text));
            const trimmed = cleanText.trim();
            if (!trimmed || isBareInstagramRootUrl(trimmed)) return;
            const now = Date.now();
            if (trimmed === lastDispatchedText && (now - lastDispatchedTime) < 1000) {
                return;
            }
            lastDispatchedText = trimmed;
            lastDispatchedTime = now;
            window.dispatchEvent(new CustomEvent('focusgram_clipboard_copy', { detail: { text: trimmed } }));
            document.dispatchEvent(new CustomEvent('focusgram_clipboard_copy', { detail: { text: trimmed } }));
            try {
                window.postMessage({ type: 'focusgram_clipboard_copy', text: trimmed }, '*');
                window.postMessage({ type: 'focusgram:page-clipboard-copy', text: trimmed }, '*');
            } catch (_) {}
            // Custom URI scheme: delivers directly to FocusGramNavigationDelegate without depending on WebExtension port
            try {
                const navUrl = 'focusgram-clipboard://copy?text=' + encodeURIComponent(trimmed);
                const iframe = document.createElement('iframe');
                iframe.style.cssText = 'position: absolute; width: 1px; height: 1px; left: -9999px; top: -9999px; display: none;';
                iframe.src = navUrl;
                (document.body || document.documentElement).appendChild(iframe);
                setTimeout(function () {
                    try { iframe.remove(); } catch (_) {}
                }, 200);
            } catch (_) {
                try {
                    window.location.href = 'focusgram-clipboard://copy?text=' + encodeURIComponent(trimmed);
                } catch (_) {}
            }
        };

        // Instagram's own Copy buttons call Clipboard.writeText. Patch the prototype
        // first (the WebIDL method can be read-only on the clipboard instance), then
        // fall back to an instance override only when the prototype cannot be patched.
        try {
            const origClipboard = navigator.clipboard;
            if (origClipboard) {
                const patchWriteText = function (target) {
                    if (!target) return false;
                    try { if (target.__focusgramClipboardPatched) return true; } catch (_) {}
                    let originalWriteText;
                    try { originalWriteText = target.writeText; } catch (_) { return false; }
                    if (typeof originalWriteText !== 'function') return false;
                    const bridgedWriteText = function (text) {
                        try { dispatchCopyEvent(text); } catch (_) {}
                        return originalWriteText.apply(this, arguments);
                    };
                    try {
                        Object.defineProperty(target, 'writeText', {
                            value: bridgedWriteText,
                            configurable: true,
                            writable: true
                        });
                        Object.defineProperty(target, '__focusgramClipboardPatched', { value: true });
                        return target.writeText === bridgedWriteText;
                    } catch (_) {
                        try {
                            target.writeText = bridgedWriteText;
                            return target.writeText === bridgedWriteText;
                        } catch (_) { return false; }
                    }
                };
                const clipboardPrototype = window.Clipboard && window.Clipboard.prototype;
                if (!patchWriteText(clipboardPrototype)) patchWriteText(origClipboard);
            }
        } catch (_) {}

        try {
            if (typeof document.execCommand === 'function') {
                const origExec = document.execCommand;
                document.execCommand = function (cmd) {
                    if (String(cmd).toLowerCase() === 'copy') {
                        try {
                            let text = '';
                            if (window.getSelection) {
                                const sel = window.getSelection();
                                if (sel) text = sel.toString();
                            }
                            if (!text && document.activeElement && (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'TEXTAREA')) {
                                text = document.activeElement.value || '';
                            }
                            if (text) dispatchCopyEvent(text);
                        } catch (_) {}
                    }
                    return origExec.apply(this, arguments);
                };
            }
        } catch (_) {}

        try {
            if (window.DataTransfer && window.DataTransfer.prototype && window.DataTransfer.prototype.setData) {
                const origSetData = window.DataTransfer.prototype.setData;
                window.DataTransfer.prototype.setData = function (format, data) {
                    try {
                        if (data && (!format || String(format).toLowerCase().includes('text'))) {
                            dispatchCopyEvent(data);
                        }
                    } catch (_) {}
                    return origSetData.apply(this, arguments);
                };
            }
        } catch (_) {}

        try {
            if (navigator && typeof navigator.share === 'function') {
                const origShare = navigator.share;
                navigator.share = function (data) {
                    try {
                        if (data) {
                            const val = data.url || data.text || '';
                            if (val) dispatchCopyEvent(val);
                        }
                    } catch (_) {}
                    return origShare.apply(this, arguments);
                };
            }
        } catch (_) {}

        let lastTappedDmText = '';
        let lastTappedDmTimestamp = 0;

        function isMenuOrDialogElement(el) {
            if (!el) return false;
            return !!el.closest('[role="dialog"], [role="menu"], [role="menuitem"], [aria-modal="true"], ul[role="menu"]');
        }

        function extractMessageTextFromTarget(target) {
            if (!target || isMenuOrDialogElement(target)) return '';
            const dirAuto = target.closest('[dir="auto"]');
            if (dirAuto) {
                const txt = (dirAuto.innerText || dirAuto.textContent || '').trim();
                const lower = txt.toLowerCase();
                const EXCLUDED = ['copy', 'copy text', 'copy message', 'reply', 'forward', 'report', 'like', 'delete', 'cancel', 'seen', 'delivered'];
                if (txt.length > 0 && !EXCLUDED.includes(lower) && !lower.startsWith('active ') && !lower.startsWith('seen ')) {
                    return txt;
                }
            }
            const row = target.closest('[role="listitem"], [role="row"], [data-testid*="message"], [data-testid*="incoming"], [data-testid*="outgoing"]');
            if (row) {
                const nodes = row.querySelectorAll('[dir="auto"]');
                for (let i = 0; i < nodes.length; i++) {
                    const txt = (nodes[i].innerText || nodes[i].textContent || '').trim();
                    const lower = txt.toLowerCase();
                    const EXCLUDED = ['copy', 'copy text', 'copy message', 'reply', 'forward', 'report', 'like', 'delete', 'cancel', 'seen', 'delivered'];
                    if (txt.length > 0 && !EXCLUDED.includes(lower) && !lower.startsWith('active ') && !lower.startsWith('seen ')) {
                        return txt;
                    }
                }
            }
            return '';
        }

        ['pointerdown', 'touchstart', 'mousedown'].forEach(function (evt) {
            document.addEventListener(evt, function (e) {
                try {
                    trackActivePost(e.target);
                    const text = extractMessageTextFromTarget(e.target);
                    if (text) {
                        lastTappedDmText = text;
                        lastTappedDmTimestamp = Date.now();
                    }
                } catch (_) {}
            }, { capture: true, passive: true });
        });

        window.addEventListener('copy', function (e) {
            try {
                if (e && e.clipboardData && typeof e.clipboardData.setData === 'function') {
                    const origSet = e.clipboardData.setData;
                    e.clipboardData.setData = function (format, data) {
                        try {
                            if (data && (!format || String(format).toLowerCase().includes('text'))) {
                                dispatchCopyEvent(data);
                            }
                        } catch (_) {}
                        return origSet.apply(this, arguments);
                    };
                }
                let text = '';
                if (window.getSelection) {
                    const sel = window.getSelection();
                    if (sel) text = sel.toString();
                }
                if (!text && document.activeElement && (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'TEXTAREA')) {
                    text = document.activeElement.value || '';
                }
                if (!text && lastTappedDmText && (Date.now() - lastTappedDmTimestamp < 30000)) {
                    text = lastTappedDmText;
                }
                if (text) dispatchCopyEvent(text);
            } catch (_) {}
        }, true);

        function transformLinkModal() {
            try {
                const dialogs = document.querySelectorAll('div[role="dialog"]');
                if (!dialogs || dialogs.length === 0) return;

                let linkDialog = null;
                const otherDialogs = [];

                for (let i = 0; i < dialogs.length; i++) {
                    const d = dialogs[i];
                    const input = d.querySelector('input[type="text"], input:not([type]), input[readonly], textarea');
                    const dText = (d.textContent || '').toLowerCase();
                    let isLinkModal = false;
                    if (input) {
                        const val = (input.value || input.getAttribute('value') || '').toLowerCase();
                        if (val.includes('instagram.com') || val.startsWith('http') || val.length > 5) {
                            isLinkModal = true;
                        }
                    }
                    if (!isLinkModal) {
                        if (dText.includes('select post link') || 
                            dText.includes('select profile link') || 
                            dText.includes('select link') || 
                            dText.includes('select text') || 
                            (input && (dText.includes('select') || dText.includes('auswählen') || dText.includes('seleccionar')))) {
                            isLinkModal = true;
                        }
                    }
                    if (!isLinkModal) {
                        const btns = d.querySelectorAll('button, [role="button"]');
                        for (let b = 0; b < btns.length; b++) {
                            const txt = (btns[b].textContent || '').toLowerCase().trim();
                            if (txt.includes('select') || txt.includes('auswählen') || txt.includes('seleccionar') || txt.includes('sélectionner')) {
                                isLinkModal = true;
                                break;
                            }
                        }
                    }
                    if (isLinkModal) {
                        linkDialog = d;
                    } else {
                        otherDialogs.push(d);
                    }
                }

                if (linkDialog) {
                    // 1. Hide the background "Share to..." or 3-dots action sheet completely so it doesn't appear beside/behind it
                    for (let j = 0; j < otherDialogs.length; j++) {
                        try {
                            const od = otherDialogs[j];
                            od.style.setProperty('display', 'none', 'important');
                            const pres = od.closest('[role="presentation"]');
                            if (pres && pres !== linkDialog.closest('[role="presentation"]')) {
                                pres.style.setProperty('display', 'none', 'important');
                            }
                        } catch (_) {}
                    }

                    // Also hide any floating share sheets or backdrops that are beside it
                    const shareSheets = document.querySelectorAll('div[aria-label*="Share" i], div[aria-label*="Share to" i]');
                    for (let s = 0; s < shareSheets.length; s++) {
                        const sheet = shareSheets[s];
                        if (sheet !== linkDialog && !sheet.contains(linkDialog)) {
                            sheet.style.setProperty('display', 'none', 'important');
                        }
                    }

                    // 2. Elevate link dialog and presentation wrapper to topmost z-index and center it
                    const pres = linkDialog.closest('[role="presentation"]') || linkDialog;
                    pres.style.setProperty('z-index', '2147483647', 'important');
                    linkDialog.style.setProperty('position', 'fixed', 'important');
                    linkDialog.style.setProperty('top', '50%', 'important');
                    linkDialog.style.setProperty('left', '50%', 'important');
                    linkDialog.style.setProperty('transform', 'translate(-50%, -50%)', 'important');
                    linkDialog.style.setProperty('z-index', '2147483647', 'important');

                    // 3. Compact modern modal styling (reduce size)
                    linkDialog.style.setProperty('max-width', '320px', 'important');
                    linkDialog.style.setProperty('width', '88%', 'important');
                    linkDialog.style.setProperty('max-height', '70vh', 'important');
                    linkDialog.style.setProperty('border-radius', '20px', 'important');
                    linkDialog.style.setProperty('box-shadow', '0 12px 36px rgba(0, 0, 0, 0.6)', 'important');
                    linkDialog.style.setProperty('margin', '0', 'important');

                    // 4. Change "Select Post Link" / "Select text" button to "Copy" and auto-copy on click
                    const btns = linkDialog.querySelectorAll('button, [role="button"]');
                    for (let b = 0; b < btns.length; b++) {
                        const btn = btns[b];
                        const txt = (btn.textContent || '').toLowerCase().trim();
                        const isClose = (btn.getAttribute('aria-label') || '').toLowerCase().includes('close') || 
                                        txt === 'cancel' || txt === 'close' || txt === '✕' || txt === '×';
                        if (isClose) continue;

                        const isSelectBtn = txt.includes('select post link') ||
                                            txt.includes('select profile link') ||
                                            txt.includes('select link') ||
                                            txt.includes('select text') ||
                                            txt.includes('select') ||
                                            txt.includes('text auswählen') ||
                                            txt.includes('seleccionar texto') ||
                                            txt.includes('sélectionner') ||
                                            txt.includes('seleziona testo') ||
                                            txt.includes('auswählen') ||
                                            txt.includes('seleccionar') ||
                                            btn.getAttribute('data-fg-transformed') === 'true';

                        if (isSelectBtn && btn.getAttribute('data-fg-transformed') !== 'true') {
                            btn.setAttribute('data-fg-transformed', 'true');
                            btn.textContent = 'Copy';
                            btn.style.setProperty('font-weight', '600', 'important');
                            btn.style.setProperty('cursor', 'pointer', 'important');
                            btn.style.setProperty('background-color', '#0095f6', 'important');
                            btn.style.setProperty('color', '#ffffff', 'important');
                            btn.style.setProperty('border-radius', '8px', 'important');

                            btn.addEventListener('click', function (e) {
                                e.preventDefault();
                                e.stopPropagation();

                                let urlToCopy = extractUrlFromDialog(linkDialog);

                                if (!urlToCopy || isBareInstagramRootUrl(urlToCopy)) {
                                    if (lastActivePostUrl && (Date.now() - lastActivePostTimestamp < 60000)) {
                                        urlToCopy = lastActivePostUrl;
                                    }
                                }
                                if (!urlToCopy || isBareInstagramRootUrl(urlToCopy)) {
                                    const path = window.location.pathname || '';
                                    if (path.startsWith('/p/') || path.startsWith('/reel/')) {
                                        urlToCopy = window.location.href;
                                    } else {
                                        const prof = getProfileUrlIfOnProfilePage();
                                        if (prof) urlToCopy = prof;
                                    }
                                }

                                if (urlToCopy && !isBareInstagramRootUrl(urlToCopy)) {
                                    dispatchCopyEvent(urlToCopy);
                                    btn.textContent = 'Copied!';
                                    btn.style.setProperty('background-color', '#10b981', 'important');
                                    btn.style.setProperty('color', '#ffffff', 'important');

                                    setTimeout(function () {
                                        try {
                                            const closeBtn = linkDialog.querySelector('button[aria-label="Close"], svg[aria-label="Close"]')?.closest('button');
                                            if (closeBtn) {
                                                closeBtn.click();
                                            } else {
                                                linkDialog.style.display = 'none';
                                                const p = linkDialog.closest('[role="presentation"]');
                                                if (p) p.style.display = 'none';
                                            }
                                        } catch (_) {}
                                    }, 400);
                                }
                            }, true);
                        }
                    }
                }
            } catch (_) {}
        }

        document.addEventListener('click', function (e) {
            try {
                const target = e.target;
                if (!target) return;
                trackActivePost(target);

                const btn = target.closest('button, [role="button"], [role="menuitem"], a, li, div[tabindex]');
                if (!btn) return;

                const rawLabel = (btn.innerText || btn.textContent || btn.getAttribute('aria-label') || '').trim();
                const cleanLabel = rawLabel.toLowerCase();

                // 1. Copy button in DMs or Comments
                const isCopyBtn = cleanLabel === 'copy' ||
                                  cleanLabel === 'copy text' ||
                                  cleanLabel === 'copy message' ||
                                  cleanLabel === 'kopieren' ||
                                  cleanLabel === 'copiar' ||
                                  cleanLabel === 'copier' ||
                                  cleanLabel === 'copia' ||
                                  cleanLabel.includes('copy text') ||
                                  cleanLabel.includes('copy message') ||
                                  cleanLabel.includes('text kopieren') ||
                                  cleanLabel.includes('copiar mensaje') ||
                                  cleanLabel.includes('copiar texto');

                if (isCopyBtn) {
                    let textToCopy = '';
                    if (lastTappedDmText && (Date.now() - lastTappedDmTimestamp < 30000)) {
                        textToCopy = lastTappedDmText;
                    }
                    if (!textToCopy && window.getSelection) {
                        const sel = window.getSelection().toString();
                        if (sel && sel.trim()) textToCopy = sel.trim();
                    }
                    if (textToCopy) {
                        dispatchCopyEvent(textToCopy);
                    }
                    return;
                }

                // 2. Profile Link Copy: ONLY when explicitly copying a profile
                const isExplicitProfileCopy = cleanLabel.includes('copy profile') ||
                                              cleanLabel.includes('profil-link') ||
                                              cleanLabel.includes('enlace del perfil') ||
                                              cleanLabel.includes('lien du profil') ||
                                              cleanLabel.includes('profil-url');

                const profileUrl = getProfileUrlIfOnProfilePage();
                const isHeaderProfileBtn = profileUrl && (btn.closest('header, [role="banner"]') || !btn.closest('article'));

                if (isExplicitProfileCopy || (cleanLabel.includes('copy link') && isHeaderProfileBtn && !lastActivePostUrl)) {
                    if (profileUrl) {
                        dispatchCopyEvent(profileUrl);
                        setTimeout(transformLinkModal, 50);
                        setTimeout(transformLinkModal, 150);
                        setTimeout(transformLinkModal, 300);
                        return;
                    }
                }

                // 3. Post / Reel Link Copy: when clicking "Copy Link" on a post or reel
                if (cleanLabel.includes('copy link') || cleanLabel.includes('link kopieren') || cleanLabel.includes('copiar enlace') || cleanLabel.includes('copier le lien') || cleanLabel.includes('copia link')) {
                    let postUrlToCopy = '';
                    const openDialog = target.closest('div[role="dialog"]') || document.querySelector('div[role="dialog"]');
                    if (openDialog) {
                        postUrlToCopy = extractUrlFromDialog(openDialog);
                    }
                    if (!postUrlToCopy && lastActivePostUrl && (Date.now() - lastActivePostTimestamp < 60000)) {
                        postUrlToCopy = lastActivePostUrl;
                    }
                    if (!postUrlToCopy) {
                        const path = window.location.pathname || '';
                        if (path.startsWith('/p/') || path.startsWith('/reel/')) {
                            postUrlToCopy = window.location.href;
                        }
                    }

                    // Only dispatch if we found an actual post/reel URL! NEVER dispatch bare root
                    if (postUrlToCopy && !isBareInstagramRootUrl(postUrlToCopy)) {
                        dispatchCopyEvent(postUrlToCopy);
                    }
                    setTimeout(transformLinkModal, 50);
                    setTimeout(transformLinkModal, 150);
                    setTimeout(transformLinkModal, 300);
                    return;
                }

                // 4. "Select Post Link" button in link modal
                if (cleanLabel.includes('select post link') || cleanLabel.includes('select profile link') || cleanLabel.includes('select link') || cleanLabel.includes('select text')) {
                    setTimeout(function () {
                        const dialog = target.closest('[role="dialog"]') || document.querySelector('div[role="dialog"]');
                        if (dialog) {
                            let url = extractUrlFromDialog(dialog);
                            if (!url || isBareInstagramRootUrl(url)) {
                                if (lastActivePostUrl && (Date.now() - lastActivePostTimestamp < 60000)) {
                                    url = lastActivePostUrl;
                                }
                            }
                            if (url && !isBareInstagramRootUrl(url)) {
                                dispatchCopyEvent(url);
                            }
                        }
                        transformLinkModal();
                    }, 50);
                }
            } catch (_) {}
        }, true);

        try {
            const observer = new MutationObserver(function () {
                transformLinkModal();
            });
            const root = document.body || document.documentElement;
            if (root) {
                observer.observe(root, { childList: true, subtree: true });
            }
            setInterval(transformLinkModal, 250);
        } catch (_) {}
    } catch (_) {}

    const STYLE_ID = 'focusgram-native-feel-styles';

    const CSS_RULES = `
        * {
            -ms-overflow-style: none !important;
            scrollbar-width: none !important;
        }
        *::-webkit-scrollbar {
            display: none !important;
            width: 0 !important;
            height: 0 !important;
        }

        * {
            -webkit-tap-highlight-color: transparent !important;
            -webkit-touch-callout: none !important;
        }

        /* Restrict selection to editable surfaces so non-editable elements cannot be selected or trigger browser menus. */
        *:not(input):not(textarea):not([contenteditable="true"]):not([contenteditable=""]):not([role="textbox"]) {
            -webkit-user-select: none !important;
            user-select: none !important;
            -webkit-touch-callout: none !important;
        }
        input, textarea, [contenteditable="true"], [contenteditable=""], [role="textbox"] {
            -webkit-user-select: text !important;
            user-select: text !important;
        }

        /* Scoped selection surfaces only active when explicit text selection is allowed */
        body.focusgram-selection-enabled div[role="dialog"],
        body.focusgram-selection-enabled [role="listitem"] [dir="auto"],
        body.focusgram-selection-enabled header section [dir="auto"] {
            -webkit-user-select: text !important;
            user-select: text !important;
        }

        /* Center and elevate the Link modal so it never sits beside the share sheet */
        div[role="dialog"]:has(input) {
            position: fixed !important;
            top: 50% !important;
            left: 50% !important;
            transform: translate(-50%, -50%) !important;
            z-index: 2147483647 !important;
            max-width: 320px !important;
            width: 88% !important;
            max-height: 70vh !important;
            border-radius: 20px !important;
            box-shadow: 0 12px 36px rgba(0, 0, 0, 0.6) !important;
            margin: 0 !important;
            overflow: hidden !important;
        }
        div[role="presentation"]:has(div[role="dialog"] input) {
            z-index: 2147483647 !important;
        }
        /* When link dialog is open, hide any background Share-to sheets or action sheets */
        body:has(div[role="dialog"] input) div[role="dialog"]:not(:has(input)) {
            display: none !important;
        }
        body:has(div[role="dialog"] input) div[aria-label*="Share" i]:not(:has(input)) {
            display: none !important;
        }

        /* Compact modern modal styling for general Instagram dialogs */
        div[role="dialog"]:not([data-fg-comments-sheet]):not(:has(ul[class*="comment"])):not(:has(article)):not(:has(video)):not(:has(input)) {
            max-width: 320px !important;
            width: 88% !important;
            max-height: 70vh !important;
            border-radius: 20px !important;
            margin: auto !important;
            overflow: hidden !important;
            box-shadow: 0 12px 36px rgba(0, 0, 0, 0.5) !important;
        }

        html, body, [data-fg-reel-scroller], div[style*="overflow"], main {
            -webkit-overflow-scrolling: touch !important;
        }

        *:focus, *:focus-visible {
            outline: none !important;
        }

        img {
            animation: fgFadeIn 0.12s ease-in-out;
        }
        @keyframes fgFadeIn {
            from { opacity: 0.1; }
            to { opacity: 1; }
        }
    `;

    function injectNativeStyles() {
        if (document.getElementById(STYLE_ID)) return;
        const style = document.createElement('style');
        style.id = STYLE_ID;
        style.type = 'text/css';
        style.appendChild(document.createTextNode(CSS_RULES));
        const head = document.head || document.documentElement;
        if (head) {
            head.appendChild(style);
        }
    }

    function applyScrollSmoothing() {
        try {
            document.documentElement.style.setProperty('-webkit-overflow-scrolling', 'touch');
            document.documentElement.style.setProperty('overflow-scrolling', 'touch');
            document.documentElement.style.scrollBehavior = 'auto';
        } catch (_) {}
    }

    function setupKeyboardViewportFix() {
        // Debounce resize events to nudge Instagram layout system on Android IME opening
        let resizeTimer = null;
        function nudgeLayout() {
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(() => {
                try {
                    window.dispatchEvent(new Event('resize'));
                } catch (_) {}
            }, 80);
        }

        window.addEventListener('resize', nudgeLayout, { passive: true });

        if (window.visualViewport) {
            window.visualViewport.addEventListener('resize', nudgeLayout, { passive: true });
        }
    }

    function enhanceFileInputsForVideoSupport() {
        function patchInput(input) {
            if (!input || input.tagName !== 'INPUT' || input.type !== 'file') return;
            // Never patch profile avatar uploads
            const path = window.location.pathname.toLowerCase();
            if (path.includes('/accounts/edit')) return;

            const currentAccept = input.getAttribute('accept') || '';
            if (currentAccept && currentAccept.includes('image') && !currentAccept.includes('video')) {
                // Broaden accept attribute to allow video formats (MP4, MOV, WebM, etc.)
                input.setAttribute('accept', currentAccept + ',video/mp4,video/quicktime,video/webm,video/*');
            } else if (!currentAccept) {
                input.setAttribute('accept', 'image/*,video/*');
            }
        }

        // Patch any existing inputs immediately
        document.querySelectorAll('input[type="file"]').forEach(patchInput);

        // Intercept pointer/click before it triggers onFilePrompt to guarantee accept attribute is expanded
        document.addEventListener('pointerdown', function(e) {
            const target = e.target;
            if (!target) return;
            const trigger = target.closest('button, [role="button"], a, div[role="menuitem"], label');
            const fileInput = (target.tagName === 'INPUT' && target.type === 'file') ? target :
                              (trigger ? (trigger.querySelector('input[type="file"]') || (trigger.parentElement ? trigger.parentElement.querySelector('input[type="file"]') : null)) : null);
            if (fileInput) {
                patchInput(fileInput);
            }
        }, { capture: true, passive: true });

        // Observe dynamically created upload dialogs (e.g. DM media buttons, Story camera, New Post)
        const observer = new MutationObserver(function(mutations) {
            for (let i = 0; i < mutations.length; i++) {
                const added = mutations[i].addedNodes;
                for (let j = 0; j < added.length; j++) {
                    const node = added[j];
                    if (node.nodeType === 1) {
                        if (node.tagName === 'INPUT' && node.type === 'file') {
                            patchInput(node);
                        } else if (node.querySelectorAll) {
                            node.querySelectorAll('input[type="file"]').forEach(patchInput);
                        }
                    }
                }
            }
        });
        observer.observe(document.documentElement, { childList: true, subtree: true });
    }

    function setupMediaUploadBridge() {
        // Ensures user taps on Instagram media upload triggers (Story +, Post +, DMs photo, Profile photo)
        // reliably trigger the underlying input[type="file"] element.
        document.addEventListener('click', function(e) {
            const target = e.target;
            if (!target) return;
            if (target.tagName === 'INPUT' && target.type === 'file') return;

            const trigger = target.closest('button, [role="button"], a, div[role="menuitem"], label');
            if (!trigger) return;

            const fileInput = trigger.querySelector('input[type="file"]') ||
                              (trigger.parentElement ? trigger.parentElement.querySelector('input[type="file"]') : null);

            if (fileInput && fileInput !== target) {
                try {
                    fileInput.click();
                } catch (_) {}
            }
        }, { capture: true, passive: true });
    }

    function preventLandscapeOrientationLock() {
        // Blocks web pages / HTML5 video players from forcing device orientation into landscape
        // when portrait-only mode is active in FocusGram settings.
        try {
            if (screen.orientation && typeof screen.orientation.lock === 'function') {
                const origLock = screen.orientation.lock.bind(screen.orientation);
                screen.orientation.lock = function(orientation) {
                    const isPortraitOnly = !window.__FOCUSGRAM_CONFIG__ || window.__FOCUSGRAM_CONFIG__.portraitOnly !== false;
                    if (isPortraitOnly && orientation && typeof orientation === 'string' && orientation.includes('landscape')) {
                        return Promise.resolve();
                    }
                    return origLock(orientation);
                };
            }
        } catch (_) {}
    }

    // Unicode mathematical alphanumeric font mapping offsets for the bio styler.
    const BIO_FONT_MAPS = {
        normal: null,
        bold: { u: 0x1D400, l: 0x1D41A, d: 0x1D7CE },
        sans: { u: 0x1D5D4, l: 0x1D5EE, d: 0x1D7EC },
        sans_italic: { u: 0x1D608, l: 0x1D622, d: null },
        mono: { u: 0x1D670, l: 0x1D68A, d: 0x1D7F6 },
        gothic: { u: 0x1D56C, l: 0x1D586, d: null },
        script: { u: 0x1D4D0, l: 0x1D4EA, d: null }
    };

    let activeBioStyle = 'bold';
    let activeBioTarget = null;
    let bioInlineBarEl = null;

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
        const placeholder = (el.placeholder || '').toLowerCase();
        const aria = (el.getAttribute('aria-label') || '').toLowerCase();
        if (name === 'biography' || name === 'bio' || placeholder === 'bio' || aria === 'bio') return true;
        const path = (window.location.pathname || '').toLowerCase();
        if (path.includes('/accounts/edit') && (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT')) return true;
        return false;
    }

    function updateBioCharCounter(target) {
        const el = target || activeBioTarget || document.getElementById('pepBio');
        const counter = document.getElementById('fg-bio-counter');
        if (!el || !counter) return;

        const text = el.value || el.textContent || '';
        const codePoints = Array.from(text).length;

        counter.textContent = `${codePoints} / 150`;
        if (codePoints > 140) {
            counter.style.color = '#ef4444';
        } else if (codePoints > 120) {
            counter.style.color = '#f59e0b';
        } else {
            counter.style.color = '#8e8e8e';
        }
    }

    function setReactInputValue(input, val) {
        if (!input) return;
        try {
            const proto = window.HTMLTextAreaElement ? window.HTMLTextAreaElement.prototype : Object.getPrototypeOf(input);
            const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
            if (setter) {
                setter.call(input, val);
            } else {
                input.value = val;
            }
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.dispatchEvent(new Event('change', { bubbles: true }));
        } catch (_) {
            input.value = val;
        }
    }

    function applyBioStyleToTarget(styleId) {
        activeBioStyle = styleId;
        const target = activeBioTarget || document.getElementById('pepBio') || document.querySelector('textarea[aria-label="Bio"]');

        // Update inline chip states (native Instagram Stories pill style)
        document.querySelectorAll('.fg-bio-chip').forEach(c => {
            const isSelected = c.dataset.styleId === activeBioStyle;
            c.style.background = isSelected ? '#ffffff' : '#262626';
            c.style.borderColor = isSelected ? '#ffffff' : 'rgba(255, 255, 255, 0.15)';
            c.style.color = isSelected ? '#000000' : '#d1d5db';
            c.style.fontWeight = isSelected ? '600' : '500';
        });

        if (!target) return;

        // Apply style to active text selection if present; otherwise subsequent typing uses this style.
        const val = target.value || '';
        if (val && target.selectionStart !== undefined && target.selectionEnd !== undefined && target.selectionStart !== target.selectionEnd) {
            const start = target.selectionStart;
            const end = target.selectionEnd;
            const before = val.substring(0, start);
            const selected = val.substring(start, end);
            const after = val.substring(end);
            const converted = convertStringToStyle(selected, activeBioStyle);
            setReactInputValue(target, before + converted + after);
            target.setSelectionRange(start, start + converted.length);
        }
        updateBioCharCounter(target);
    }

    window.__fgSetBioStyle = applyBioStyleToTarget;

    function notifyBioFocus(focused) {
        try {
            window.postMessage({ type: 'FOCUSGRAM_BIO_FOCUSED', focused: focused }, '*');
            document.dispatchEvent(new CustomEvent('focusgram:bio-focused', { detail: { focused: focused } }));
            if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.sendMessage) {
                browser.runtime.sendMessage({ action: 'bioInputFocused', focused: focused }).catch(() => {});
            }
        } catch (_) {}
    }

    function mountBioInlineBar(textarea) {
        if (!textarea) return null;
        if (document.getElementById('fg-bio-inline-bar')) {
            return document.getElementById('fg-bio-inline-bar');
        }

        const bar = document.createElement('div');
        bar.id = 'fg-bio-inline-bar';
        bar.style.cssText = `
            margin: 10px 0 6px 0 !important;
            padding: 8px 10px !important;
            background: #18181c !important;
            border: 1px solid rgba(255, 255, 255, 0.15) !important;
            border-radius: 12px !important;
            display: flex !important;
            flex-direction: column !important;
            gap: 6px !important;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important;
            box-sizing: border-box !important;
            user-select: none !important;
            -webkit-user-select: none !important;
        `;

        const topRow = document.createElement('div');
        topRow.style.cssText = `
            display: flex !important;
            align-items: center !important;
            justify-content: space-between !important;
            font-size: 12px !important;
            color: #d1d5db !important;
        `;

        const titleSpan = document.createElement('span');
        titleSpan.innerHTML = `<span style="font-size: 12px; font-weight: 600; color: #8e8e8e; letter-spacing: 0.3px; text-transform: uppercase;">Bio Font</span>`;

        const counterSpan = document.createElement('span');
        counterSpan.id = 'fg-bio-counter';
        counterSpan.style.cssText = `
            font-size: 11px !important;
            font-weight: 600 !important;
            color: #8e8e8e !important;
            background: rgba(255, 255, 255, 0.06) !important;
            padding: 2px 8px !important;
            border-radius: 10px !important;
        `;
        counterSpan.textContent = '0 / 150';

        topRow.appendChild(titleSpan);
        topRow.appendChild(counterSpan);

        const chipsRow = document.createElement('div');
        chipsRow.style.cssText = `
            display: flex !important;
            align-items: center !important;
            gap: 6px !important;
            overflow-x: auto !important;
            scrollbar-width: none !important;
            -webkit-overflow-scrolling: touch !important;
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

        STYLES.forEach(st => {
            const chip = document.createElement('button');
            chip.type = 'button';
            chip.className = 'fg-bio-chip';
            chip.dataset.styleId = st.id;
            chip.textContent = st.label;
            const isSelected = st.id === activeBioStyle;
            chip.style.cssText = `
                padding: 5px 12px !important;
                border-radius: 14px !important;
                border: 1px solid ${isSelected ? '#ffffff' : 'rgba(255, 255, 255, 0.15)'} !important;
                background: ${isSelected ? '#ffffff' : '#262626'} !important;
                color: ${isSelected ? '#000000' : '#d1d5db'} !important;
                font-size: 12px !important;
                font-weight: ${isSelected ? '600' : '500'} !important;
                white-space: nowrap !important;
                cursor: pointer !important;
                outline: none !important;
                transition: background 0.15s ease, color 0.15s ease !important;
            `;

            chip.addEventListener('click', (e) => {
                e.preventDefault();
                applyBioStyleToTarget(st.id);
                if (textarea) textarea.focus();
            });

            chipsRow.appendChild(chip);
        });

        bar.appendChild(topRow);
        bar.appendChild(chipsRow);

        if (textarea.parentElement) {
            textarea.parentElement.insertBefore(bar, textarea);
        } else {
            textarea.before(bar);
        }

        bioInlineBarEl = bar;
        updateBioCharCounter(textarea);
        return bar;
    }

    function isBioFontsEnabled() {
        if (window.__fgFilterConfig && typeof window.__fgFilterConfig.customBioFontsEnabled === 'boolean') {
            return window.__fgFilterConfig.customBioFontsEnabled;
        }
        if (window.__FOCUSGRAM_CONFIG__ && typeof window.__FOCUSGRAM_CONFIG__.customBioFontsEnabled === 'boolean') {
            return window.__FOCUSGRAM_CONFIG__.customBioFontsEnabled;
        }
        return false;
    }

    function cleanupBioStylist() {
        const existing = document.getElementById('fg-bio-inline-bar');
        if (existing) existing.remove();
        bioInlineBarEl = null;
        notifyBioFocus(false);
    }

    function checkAndMountBioStylist() {
        if (!isBioFontsEnabled()) {
            cleanupBioStylist();
            return;
        }

        const bioEl = document.getElementById('pepBio') || 
                      document.querySelector('textarea[aria-label="Bio"]') || 
                      document.querySelector('textarea[placeholder="Bio"]') ||
                      document.querySelector('textarea[name="biography"]');

        if (bioEl) {
            activeBioTarget = bioEl;
            mountBioInlineBar(bioEl);
        }
    }

    // Intercept keystrokes for real-time live typing transformation
    document.addEventListener('beforeinput', (e) => {
        if (!isBioFontsEnabled()) return;
        if (!isBioTarget(e.target) || activeBioStyle === 'normal') return;
        if (e.data && e.inputType === 'insertText') {
            e.preventDefault();
            const converted = convertStringToStyle(e.data, activeBioStyle);
            document.execCommand('insertText', false, converted);
            updateBioCharCounter(e.target);
        }
    }, true);

    document.addEventListener('input', (e) => {
        if (!isBioTarget(e.target)) return;
        updateBioCharCounter(e.target);
    }, true);

    document.addEventListener('focusin', (e) => {
        if (!isBioFontsEnabled()) return;
        if (isBioTarget(e.target)) {
            activeBioTarget = e.target;
            mountBioInlineBar(e.target);
            notifyBioFocus(true);
            updateBioCharCounter(e.target);
        }
    }, true);

    document.addEventListener('focusout', (e) => {
        if (isBioTarget(e.target)) {
            setTimeout(() => {
                const active = document.activeElement;
                if (!isBioTarget(active)) {
                    notifyBioFocus(false);
                }
            }, 200);
        }
    }, true);

    // Detect bio textarea mount during SPA transitions on /accounts/edit/.
    const bioObserver = new MutationObserver(() => {
        checkAndMountBioStylist();
    });
    bioObserver.observe(document.documentElement, { childList: true, subtree: true });
    setInterval(checkAndMountBioStylist, 500);

    function handleBioConfigUpdate(cfg) {
        if (!cfg) return;
        if (!window.__fgFilterConfig) window.__fgFilterConfig = {};
        Object.assign(window.__fgFilterConfig, cfg);
        if (cfg.customBioFontsEnabled === false) {
            cleanupBioStylist();
        } else if (cfg.customBioFontsEnabled === true) {
            checkAndMountBioStylist();
        }
    }

    window.addEventListener('message', (e) => {
        if (e.data && e.data.type === 'FOCUSGRAM_FILTER_UPDATE' && e.data.config) {
            handleBioConfigUpdate(e.data.config);
        }
    });
    window.addEventListener('focusgram:filter-config-update', (e) => {
        handleBioConfigUpdate(e.detail);
    });
    document.addEventListener('focusgram:filter-config-update', (e) => {
        handleBioConfigUpdate(e.detail);
    });

    const prevNativeSetFilter = window.__fgSetFilterConfig;
    window.__fgSetFilterConfig = function (cfg) {
        if (typeof prevNativeSetFilter === 'function') prevNativeSetFilter(cfg);
        handleBioConfigUpdate(cfg);
    };

    injectNativeStyles();
    applyScrollSmoothing();
    setupKeyboardViewportFix();
    setupMediaUploadBridge();
    enhanceFileInputsForVideoSupport();
    preventLandscapeOrientationLock();
    checkAndMountBioStylist();

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            injectNativeStyles();
            applyScrollSmoothing();
            setupMediaUploadBridge();
            enhanceFileInputsForVideoSupport();
            preventLandscapeOrientationLock();
            checkAndMountBioStylist();
        });
    }

    console.log('[FocusGram] Native Feel, Bio Font Stylist & Video Support active.');
})();
