/**
 * FocusGram — Reels Metadata Extractor UserScript
 *
 * Extracts author, title, and thumbnail URL for watched Reels directly from client-side DOM
 * (Open Graph meta tags, JSON-LD structured data, document titles).
 *
 * LEGAL & ARCHITECTURE COMPLIANCE:
 * - 100% Client-side DOM parsing only.
 * - ZERO video media is downloaded or cached to disk.
 * - ZERO network calls or external API calls.
 * - All metadata remains strictly on-device in Room SQLite.
 */

'use strict';

(function () {
    if (window.__fgReelExtractorInitialized) return;
    window.__fgReelExtractorInitialized = true;

    let lastExtractedUrl = '';

    function extractMetadata() {
        const currentUrl = window.location.href;

        // Verify this is an Instagram Reel URL
        if (!currentUrl.includes('/reels/') && !currentUrl.includes('/reel/')) {
            return null;
        }

        // Avoid re-extracting for the identical URL
        if (lastExtractedUrl === currentUrl) {
            return null;
        }

        let title = '';
        let thumbnailUrl = '';
        let author = '';

        // 1. Check Open Graph tags
        const ogTitle = document.querySelector('meta[property="og:title"]');
        const ogImage = document.querySelector('meta[property="og:image"]');
        if (ogTitle && ogTitle.content) title = ogTitle.content.trim();
        if (ogImage && ogImage.content) thumbnailUrl = ogImage.content.trim();

        // 2. Fallback to document title
        if (!title && document.title) {
            title = document.title.replace(' on Instagram', '').replace('• Instagram photos and videos', '').trim();
        }

        // Parse author if available in title (format: "Author (@handle) on Instagram: ...")
        if (title) {
            const authorMatch = title.match(/^([^(@:]+)(?:\s*\((@[^)]+)\))?/);
            if (authorMatch) {
                author = (authorMatch[2] || authorMatch[1]).trim();
            }
        }

        // 3. Fallback to JSON-LD structured data
        if (!thumbnailUrl) {
            const jsonLdElements = document.querySelectorAll('script[type="application/ld+json"]');
            jsonLdElements.forEach(script => {
                try {
                    const data = JSON.parse(script.textContent);
                    if (data && data.image) {
                        if (Array.isArray(data.image) && data.image.length > 0) {
                            thumbnailUrl = data.image[0];
                        } else if (typeof data.image === 'string') {
                            thumbnailUrl = data.image;
                        } else if (data.image.url) {
                            thumbnailUrl = data.image.url;
                        }
                    }
                    if (!title && data.caption) {
                        title = data.caption;
                    }
                } catch (_) {}
            });
        }

        // 4. Fallback to Twitter card image
        if (!thumbnailUrl) {
            const twitterImage = document.querySelector('meta[name="twitter:image"]');
            if (twitterImage && twitterImage.content) {
                thumbnailUrl = twitterImage.content.trim();
            }
        }

        // 5. Fallback to video element poster and DOM author
        if (!thumbnailUrl) {
            const videoEl = document.querySelector('video[poster]') || document.querySelector('video');
            if (videoEl && videoEl.getAttribute('poster')) {
                thumbnailUrl = videoEl.getAttribute('poster');
            }
        }
        if (!author) {
            const authorEl = document.querySelector('a[role="link"][tabindex="0"] span') ||
                             document.querySelector('header a') ||
                             document.querySelector('div[role="dialog"] header a');
            if (authorEl && authorEl.textContent) {
                author = authorEl.textContent.trim();
            }
        }

        // If neither title nor thumbnail is ready, DOM may still be rendering
        if (!thumbnailUrl && !title) {
            return null;
        }

        lastExtractedUrl = currentUrl;

        return {
            url: currentUrl,
            title: title || 'Instagram Reel',
            author: author || 'Instagram Creator',
            thumbnailUrl: thumbnailUrl || '',
            timestamp: Date.now()
        };
    }

    function extractAndBroadcast() {
        const data = extractMetadata();
        if (!data) return;

        // Dispatch DOM event for native page bridge / observers
        try {
            window.dispatchEvent(new CustomEvent('focusgram:reelmetadata', {
                detail: data
            }));
        } catch (_) {}

        // Send via WebExtension messaging if available
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

    // Initial check
    extractAndBroadcast();

    // Listen to route changes from spa_navigation_monitor
    window.addEventListener('focusgram:routechange', () => {
        setTimeout(extractAndBroadcast, 400);
    });

    // Observe DOM mutations for asynchronously loaded OG meta tags
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
