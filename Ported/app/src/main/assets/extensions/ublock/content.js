/**
 * FocusGram — Cosmetic Filtering & DOM Scriptlet
 *
 * Injects CSS cosmetic filtering rules and monitors dynamic SPA DOM
 * insertions to eliminate sponsored posts and ad banners when enabled.
 */

'use strict';

(function () {
    const COSMETIC_STYLE_ID = 'focusgram-cosmetic-filters';
    let adBlockingEnabled = true;

    const CSS_RULES = `
        /* Hide sponsored feed articles and containers without breaking Infinite Scroll IntersectionObserver */
        div[data-ad-preview],
        div[data-testid="post_ad"],
        div[aria-label*="Sponsored" i]:not(main):not([role="feed"]),
        div[aria-label*="sponsored" i]:not(main):not([role="feed"]),
        article:has(a[href*="/ads/"]),
        article:has(a[href*="/about/ads"]),
        article:has(a[href*="ads.instagram.com"]),
        article[data-ad-filtered="true"] {
            height: 1px !important;
            min-height: 1px !important;
            max-height: 1px !important;
            overflow: hidden !important;
            opacity: 0 !important;
            pointer-events: none !important;
            margin: 0 !important;
            padding: 0 !important;
            border: none !important;
        }
        a[href*="/ads/"],
        a[href*="ads.instagram.com"] {
            display: none !important;
        }
    `;

    function updateCosmeticFilters() {
        const existing = document.getElementById(COSMETIC_STYLE_ID);
        if (adBlockingEnabled) {
            if (!existing) {
                const style = document.createElement('style');
                style.id = COSMETIC_STYLE_ID;
                style.type = 'text/css';
                style.appendChild(document.createTextNode(CSS_RULES));
                const target = document.head || document.documentElement;
                if (target) target.appendChild(style);
            }
            filterDynamicSponsoredPosts();
        } else {
            if (existing) {
                existing.remove();
            }
        }
    }

    const SPONSORED_KEYWORDS = [
        'sponsored', 'gesponsert', 'sponsorisé', 'sponsorise',
        'publicidad', 'patrocinado', 'sponsorizzato', 'sponsrad',
        'paid partnership'
    ];

    const AD_CTA_KEYWORDS = [
        'learn more', 'shop now', 'install now', 'sign up', 'apply now',
        'book now', 'download', 'order now', 'get offer', 'subscribe'
    ];

    function filterDynamicSponsoredPosts() {
        if (!adBlockingEnabled) return;
        // Strictly target individual article posts to avoid hiding high-level feed containers
        const articles = document.querySelectorAll('article');
        for (let i = 0; i < articles.length; i++) {
            const article = articles[i];
            if (article.dataset.adFiltered) continue;

            let isAd = false;

            // Check header text (Sponsored, Paid partnership)
            const spans = article.querySelectorAll('header span, header a, span[dir="auto"], [role="button"] span');
            for (let j = 0; j < spans.length; j++) {
                const text = (spans[j].textContent || '').trim().toLowerCase();
                if (SPONSORED_KEYWORDS.some(kw => text === kw || text.startsWith(kw) || text.includes(kw))) {
                    isAd = true;
                    break;
                }
            }

            // Check CTA button text
            if (!isAd) {
                const buttons = article.querySelectorAll('div[role="button"], a[role="button"]');
                for (let k = 0; k < buttons.length; k++) {
                    const btnText = (buttons[k].textContent || '').trim().toLowerCase();
                    if (AD_CTA_KEYWORDS.some(cta => btnText === cta)) {
                        isAd = true;
                        break;
                    }
                }
            }

            // Check ad-specific links (strictly /ads/, /ad_, ads.instagram.com, /about/ads)
            if (!isAd) {
                if (article.querySelector('a[href*="/ads/"], a[href*="/ad_"], a[href*="/about/ads"], a[href*="ads.instagram.com"]')) {
                    isAd = true;
                }
            }

            if (isAd) {
                article.style.setProperty('height', '1px', 'important');
                article.style.setProperty('min-height', '1px', 'important');
                article.style.setProperty('max-height', '1px', 'important');
                article.style.setProperty('overflow', 'hidden', 'important');
                article.style.setProperty('opacity', '0', 'important');
                article.style.setProperty('pointer-events', 'none', 'important');
                article.style.setProperty('margin', '0', 'important');
                article.style.setProperty('padding', '0', 'important');
                article.dataset.adFiltered = 'true';
                window.dispatchEvent(new Event('scroll'));
            }
        }

        // Filter Reels video ads on reels and feed surfaces
        const videos = document.querySelectorAll('video');
        for (let i = 0; i < videos.length; i++) {
            const video = videos[i];
            let container = video.closest('article') || video.closest('section') || video.closest('[role="region"]') || video.parentElement;
            while (container && container !== document.body && container !== document.documentElement) {
                if (container.tagName === 'ARTICLE' || container.tagName === 'SECTION' || container.parentElement?.tagName === 'MAIN') break;
                const r = container.getBoundingClientRect();
                if (r.height > window.innerHeight * 0.45) break;
                container = container.parentElement;
            }
            if (!container || container.dataset.adFiltered) continue;

            const text = (container.textContent || '').trim().toLowerCase();
            const isSponsored = SPONSORED_KEYWORDS.some(kw => text.includes(kw)) ||
                                !!container.querySelector('a[href*="/ads/"], a[href*="/ad_"], a[href*="/about/ads"], a[href*="ads.instagram.com"]') ||
                                !!container.querySelector('[aria-label*="Sponsored" i], [aria-label*="sponsored" i], [data-ad-preview]');

            const hasAdCTA = !!container.querySelector('div[role="button"], a[role="button"]') &&
                             AD_CTA_KEYWORDS.some(cta => text.includes(cta));

            if (isSponsored || hasAdCTA) {
                video.muted = true;
                try { video.pause(); } catch (_) {}
                container.style.setProperty('display', 'none', 'important');
                container.style.setProperty('height', '0px', 'important');
                container.style.setProperty('opacity', '0', 'important');
                container.style.setProperty('pointer-events', 'none', 'important');
                container.dataset.adFiltered = 'true';

                // Skip ad reel automatically if currently visible
                const r = container.getBoundingClientRect();
                if (r.top >= -100 && r.top <= window.innerHeight * 0.8) {
                    const evt = new KeyboardEvent('keydown', { key: 'ArrowDown', code: 'ArrowDown', keyCode: 40, which: 40, bubbles: true });
                    window.dispatchEvent(evt);
                    document.dispatchEvent(evt);
                }
            }
        }
    }

    // Dynamic MutationObserver for SPA scrolling
    const observer = new MutationObserver(() => {
        if (adBlockingEnabled) {
            filterDynamicSponsoredPosts();
        }
    });

    function init() {
        updateCosmeticFilters();
        const root = document.body || document.documentElement;
        if (root) {
            observer.observe(root, { childList: true, subtree: true });
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // Listen for settings changes from background
    if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.onMessage) {
        browser.runtime.onMessage.addListener((msg) => {
            if (msg && msg.action === 'updateSettings' && msg.settings) {
                adBlockingEnabled = msg.settings.noAds !== false;
                updateCosmeticFilters();
            }
        });

        browser.runtime.sendMessage({ action: 'getSettings' }, (response) => {
            if (response && response.settings) {
                adBlockingEnabled = response.settings.noAds !== false;
                updateCosmeticFilters();
            }
        });
    }
})();
