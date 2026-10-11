/**
 * uBlock Origin for FocusGram — Cosmetic Filtering & DOM Scriptlet
 * License: GNU General Public License v3.0 (GPL-3.0)
 *
 * Injects CSS cosmetic filtering rules and monitors dynamic SPA DOM
 * insertions to eliminate sponsored posts and ad banners when enabled.
 */

'use strict';

(function () {
    const COSMETIC_STYLE_ID = 'ublock-focusgram-cosmetic-filters';
    let adBlockingEnabled = true;

    const CSS_RULES = `
        /* Hide sponsored feed articles and containers */
        article:has(a[href*="/explore/locations/"]),
        article:has(div[role="button"]:has(span:empty)),
        div[data-ad-preview],
        div[data-testid="post_ad"],
        div[aria-label*="Sponsored"],
        div[aria-label*="sponsored"],
        a[href*="/ad/"],
        a[href*="ads.instagram.com"] {
            display: none !important;
            visibility: hidden !important;
            height: 0 !important;
            min-height: 0 !important;
            margin: 0 !important;
            padding: 0 !important;
            pointer-events: none !important;
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
        'publicidad', 'patrocinado', 'sponsorizzato', 'sponsrad'
    ];

    function filterDynamicSponsoredPosts() {
        if (!adBlockingEnabled) return;
        const articles = document.querySelectorAll('article');
        for (let i = 0; i < articles.length; i++) {
            const article = articles[i];
            if (article.dataset.ublockFiltered) continue;

            const spans = article.querySelectorAll('header span, header a, span[dir="auto"]');
            for (let j = 0; j < spans.length; j++) {
                const text = (spans[j].textContent || '').trim().toLowerCase();
                if (SPONSORED_KEYWORDS.includes(text)) {
                    article.style.setProperty('display', 'none', 'important');
                    article.dataset.ublockFiltered = 'true';
                    break;
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
