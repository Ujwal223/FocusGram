/**
 * Reel metadata extractor.
 *
 * Extracts author, caption, and thumbnail URLs from client-side DOM
 * (Open Graph tags, JSON-LD data, and document headers) for local watch history.
 */

'use strict';

(function () {
    if (window.__fgReelExtractorInitialized) return;
    window.__fgReelExtractorInitialized = true;

    let lastExtractedReelKey = '';

    function extractMetadata() {
        const currentUrl = window.location.href;

        if (!currentUrl.includes('/reels/') && !currentUrl.includes('/reel/')) {
            return null;
        }

        const videos = Array.from(document.querySelectorAll('video'));
        const activeVideo = videos.find(v => !v.paused && v.getBoundingClientRect().height > 200) ||
                            videos.find(v => {
                                const r = v.getBoundingClientRect();
                                return r.top >= -50 && r.top < window.innerHeight * 0.6;
                            }) || videos[0];

        let title = '';
        let thumbnailUrl = '';
        let author = '';
        let reelShortcode = '';

        if (activeVideo) {
            thumbnailUrl = activeVideo.getAttribute('poster') || '';
            let card = activeVideo.closest('article, section, div[role="presentation"]') || activeVideo.parentElement;
            while (card && card !== document.body && card !== document.documentElement) {
                if (card.tagName === 'ARTICLE' || card.tagName === 'SECTION') break;
                if (card.getBoundingClientRect().height > window.innerHeight * 0.5) break;
                card = card.parentElement;
            }

            if (card) {
                const link = card.querySelector('a[href*="/reel/"], a[href*="/p/"], a[href*="/reels/"]');
                if (link && link.href) {
                    const lm = /\/(?:reel|reels|p)\/([A-Za-z0-9_-]{5,})/.exec(link.href);
                    if (lm) reelShortcode = lm[1];
                }

                const authorEl = card.querySelector('header a[role="link"], a[role="link"][href^="/"]:not([href*="/audio/"]):not([href*="/reel"]), header h2, header h3, header span[dir="auto"]');
                if (authorEl && authorEl.textContent) {
                    author = authorEl.textContent.trim().replace(/^@/, '');
                } else if (authorEl && authorEl.getAttribute('href')) {
                    const m = authorEl.getAttribute('href').match(/^\/([A-Za-z0-9._]+)\/?/);
                    if (m) author = m[1];
                }

                const textCandidates = Array.from(card.querySelectorAll('h1, h2, span[dir="auto"], div[dir="auto"]'));
                for (const el of textCandidates) {
                    if (el.closest('a[href*="/audio/"]') || el.closest('header') || el.closest('button') || el.closest('a[role="link"]')) continue;
                    const txt = (el.textContent || '').trim();
                    const lower = txt.toLowerCase();
                    if (txt.length >= 2 && txt !== author && !lower.includes('follow') && !lower.startsWith('liked by') && !lower.includes('original audio') && !['ad', 'sponsored'].includes(lower)) {
                        title = txt.slice(0, 300);
                        break;
                    }
                }
            }
        }

        if (!reelShortcode) {
            const urlMatch = /\/(?:reel|reels|p)\/([A-Za-z0-9_-]{5,})/.exec(window.location.pathname);
            if (urlMatch && videos.length <= 1) {
                reelShortcode = urlMatch[1];
            }
        }

        // Only fall back to static head meta when viewing a standalone single-reel URL, never during SPA feed swiping.
        if (!title && !author && videos.length <= 1) {
            const ogTitle = document.querySelector('meta[property="og:title"]');
            const ogImage = document.querySelector('meta[property="og:image"]');
            if (ogTitle && ogTitle.content) title = ogTitle.content.trim();
            if (ogImage && ogImage.content) thumbnailUrl = ogImage.content.trim();

            if (!title && document.title) {
                title = document.title.replace(' on Instagram', '').replace('• Instagram photos and videos', '').trim();
            }

            if (title) {
                const authorMatch = title.match(/^([^(@:]+)(?:\s*\((@[^)]+)\))?/);
                if (authorMatch) {
                    author = (authorMatch[2] || authorMatch[1]).trim().replace(/^@/, '');
                }
            }
        }

        if (!thumbnailUrl && !title && !author && !reelShortcode) {
            return null;
        }

        const effectiveUrl = reelShortcode ? `https://www.instagram.com/reel/${reelShortcode}/` : currentUrl;
        const dedupeKey = `${reelShortcode || author || ''}___${title || ''}`;

        if (lastExtractedReelKey === dedupeKey) {
            return null;
        }
        lastExtractedReelKey = dedupeKey;

        return {
            url: effectiveUrl,
            code: reelShortcode || author || '',
            title: title || 'Instagram Reel',
            author: author || 'Instagram Creator',
            thumbnailUrl: thumbnailUrl || '',
            timestamp: Date.now()
        };
    }

    function extractAndBroadcast() {
        const data = extractMetadata();
        if (!data) return;

        try {
            window.dispatchEvent(new CustomEvent('focusgram:reelmetadata', {
                detail: data
            }));
        } catch (_) {}

        try {
            if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.sendMessage) {
                browser.runtime.sendMessage({
                    type: 'ReelMetadata',
                    payload: data
                }).catch(() => {});
            }
        } catch (_) {}

        console.log('[FocusGram] Reel metadata extracted on-device:', data.url);
    }

    extractAndBroadcast();

    window.addEventListener('focusgram:routechange', () => {
        setTimeout(extractAndBroadcast, 400);
    });

    let debounceTimer = null;
    const observer = new MutationObserver(() => {
        if (window.location.href.includes('/reel')) {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(extractAndBroadcast, 500);
        }
    });

    observer.observe(document.documentElement || document.body, {
        childList: true,
        subtree: true
    });

    console.log('[FocusGram] Reels Metadata Extractor active.');
})();
