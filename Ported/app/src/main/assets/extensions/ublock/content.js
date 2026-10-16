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

    function isAuthPage() {
        try {
            const path = (window.location.pathname || '').toLowerCase();
            if (path.startsWith('/accounts') || path.startsWith('/login') || path.startsWith('/signup')) {
                return true;
            }
            const cookies = document.cookie || '';
            const hasAuthCookie = cookies.includes('sessionid=') || cookies.includes('ds_user_id=');
            if ((path === '/' || path === '') && !hasAuthCookie) {
                return true;
            }
            if (document.getElementById('loginForm') || document.querySelector('form[action*="login"], input[name="password"]')) {
                return true;
            }
        } catch (_) {}
        return false;
    }

    if (isAuthPage()) {
        const existing = document.getElementById(COSMETIC_STYLE_ID);
        if (existing) existing.remove();
        return; // NEVER run on auth, login, or signup pages
    }

    const CSS_RULES = `
        /* Hide sponsored feed articles without breaking Infinite Scroll IntersectionObserver or feed containers */
        div[data-ad-preview],
        div[data-testid="post_ad"],
        article:has(a[href*="/ads/"]),
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
        div[data-fg-reel-ad="1"] {
            visibility: hidden !important;
            pointer-events: none !important;
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
        'paid partnership', 'werbung', 'anuncio', 'werbeanzeige', 'publicité'
    ];

    const STRICT_AD_CTAS = [
        'shop now', 'install now', 'apply now',
        'book now', 'order now', 'get offer'
    ];

    function filterDynamicSponsoredPosts() {
        if (!adBlockingEnabled) return;
        // Strictly target individual article posts to avoid hiding high-level feed containers
        const articles = document.querySelectorAll('article');
        for (let i = 0; i < articles.length; i++) {
            const article = articles[i];
            if (article.dataset.adFiltered) continue;

            let isAd = false;

            // Check post header only (Sponsored, Paid partnership)
            const headerElements = article.querySelectorAll('header span, header [aria-label*="Sponsored" i], header [aria-label="Ad" i]');
            for (let j = 0; j < headerElements.length; j++) {
                if (headerElements[j].children.length > 2) continue;
                const text = (headerElements[j].textContent || '').trim().toLowerCase();
                if (!text || text.length > 30) continue;
                const clean = text.replace(/[^a-z]/g, '');
                if (clean === 'ad' || SPONSORED_KEYWORDS.some(kw => text === kw || clean === kw)) {
                    isAd = true;
                    break;
                }
            }

            // Check CTA button text specifically on buttons (excluding FocusGram download buttons)
            if (!isAd) {
                const buttons = article.querySelectorAll('div[role="button"]:not([data-fg-x-act]):not([data-fg-download-btn]):not(.fg-x-act-btn), a[role="button"]:not([data-fg-x-act]):not([data-fg-download-btn]):not(.fg-x-act-btn), button');
                for (let k = 0; k < buttons.length; k++) {
                    const btnText = (buttons[k].textContent || '').trim().toLowerCase();
                    if (STRICT_AD_CTAS.some(cta => btnText === cta)) {
                        isAd = true;
                        break;
                    }
                }
            }

            // Check ad-specific links (strictly /ads/, /ad_, ads.instagram.com)
            if (!isAd) {
                if (article.querySelector('a[href*="/ads/"], a[href*="/ad_"], a[href*="ads.instagram.com"]')) {
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
            }
        }

        // Filter Reels video ads on reels surface without ever breaking layout
        const videos = document.querySelectorAll('video');
        for (let i = 0; i < videos.length; i++) {
            const video = videos[i];
            const card = video.closest('[data-fg-reel-card="1"]') || video.closest('article');
            if (!card || card.dataset.adFiltered || card.dataset.fgReelAd) continue;

            let isSponsored = !!card.querySelector('a[href*="/ads/"], a[href*="/ad_"], a[href*="ads.instagram.com"], a[href*="/about/ads"]') ||
                              !!card.querySelector('[aria-label*="Sponsored" i], [aria-label="Ad" i], [data-ad-preview]');

            if (!isSponsored) {
                const badges = card.querySelectorAll('span, div[role="button"], button');
                for (let s = 0; s < badges.length; s++) {
                    const el = badges[s];
                    if (el.children.length > 2) continue;
                    const raw = (el.textContent || '').trim();
                    if (!raw || raw.length > 15) continue;
                    if (/^[\u{1F300}-\u{1F9FF}\s]*ad$/iu.test(raw) || /^sponsored$/i.test(raw) || /^paid\s+partnership$/i.test(raw)) {
                        if (!el.closest('span[dir="auto"]')) {
                            isSponsored = true;
                            break;
                        }
                    }
                }
            }

            if (isSponsored) {
                video.muted = true;
                try { video.pause(); } catch (_) {}
                card.setAttribute('data-fg-reel-ad', '1');
                card.dataset.adFiltered = 'true';
                card.style.setProperty('visibility', 'hidden', 'important');
                card.style.setProperty('pointer-events', 'none', 'important');

                // Skip ad reel automatically if currently visible
                const r = card.getBoundingClientRect();
                if (r.top >= -100 && r.top <= window.innerHeight * 0.8) {
                    const scroller = card.closest('[data-fg-reel-scroller]') || document.querySelector('[data-fg-reel-scroller="1"]');
                    if (scroller && scroller !== window) {
                        try { scroller.scrollBy({ top: scroller.clientHeight || window.innerHeight, behavior: 'instant' }); } catch (_) {}
                    } else {
                        try { window.scrollBy({ top: window.innerHeight, behavior: 'instant' }); } catch (_) {}
                    }
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

    setInterval(() => {
        if (adBlockingEnabled) {
            filterDynamicSponsoredPosts();
        }
    }, 300);

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
