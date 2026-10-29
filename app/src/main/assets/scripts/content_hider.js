/**
 * Content hider, layout adjustments, and client-side SPA routing.
 */

'use strict';

(function () {
    const STYLE_ID = 'focusgram-dynamic-guardrails-styles';

    function isAuthPage() {
        try {
            var path = (window.location.pathname || '').toLowerCase();
            if (path.includes('/accounts/edit')) return false;
            if (path.startsWith('/accounts') || path.startsWith('/login') || path.startsWith('/signup') || path.startsWith('/two_factor') || path.startsWith('/challenge')) return true;
            if (document.getElementById('loginForm') || document.querySelector('form[action*="login"], form input[type="password"]')) return true;
        } catch (_) {}
        return false;
    }

    if (isAuthPage()) {
        var existing = document.getElementById(STYLE_ID);
        if (existing) existing.remove();
        return; // Leave login and signup untouched.
    }

    window.__focusgramNavigate = function (path) {
        if (!path) return;
        try {
            // First try matching and clicking navigation anchors already in the DOM.
            let target = null;
            if (path === '/') {
                target = document.querySelector('a[href="/"][role="link"]') ||
                         document.querySelector('a[href="/"]') ||
                         document.querySelector('svg[aria-label="Home"]')?.closest('a');
            } else if (path.includes('explore')) {
                target = document.querySelector('a[href*="/explore"]') ||
                         document.querySelector('svg[aria-label="Search"]')?.closest('a') ||
                         document.querySelector('svg[aria-label="Explore"]')?.closest('a');
            } else if (path.includes('reel')) {
                target = document.querySelector('a[href*="/reels"]') ||
                         document.querySelector('svg[aria-label="Reels"]')?.closest('a');
            } else if (path.includes('direct')) {
                target = document.querySelector('a[href*="/direct"]') ||
                         document.querySelector('svg[aria-label="Messages"]')?.closest('a') ||
                         document.querySelector('svg[aria-label="Direct"]')?.closest('a');
            } else {
                target = document.querySelector(`a[href="${path}"]`) ||
                         document.querySelector(`a[href="${path}/"]`);
            }

            if (target) {
                target.click();
                return true;
            }

            if (window.history && typeof window.history.pushState === 'function') {
                window.history.pushState(null, '', path);
                window.dispatchEvent(new PopStateEvent('popstate', { state: null }));
                return true;
            }
        } catch (e) {
            console.warn('[FocusGram] SPA navigation error:', e);
        }

        window.location.href = path;
    };

    window.__focusgramOpenProfile = function () {
        try {
            const profileLink = document.querySelector('a[href*="/profile/"]') ||
                                document.querySelector('a[role="link"]:has(img[alt*="profile" i])') ||
                                document.querySelector('a[role="link"]:has(img[alt*="\'s profile picture" i])') ||
                                document.querySelector('nav a[href^="/"]:last-child') ||
                                document.querySelector('div[role="navigation"] a[href^="/"]:last-child');

            if (profileLink) {
                const href = profileLink.getAttribute('href');
                if (href && href !== '/accounts/edit/' && href !== '/accounts/edit') {
                    profileLink.click();
                    return true;
                }
            }

            const username = window._sharedData?.config?.viewer?.username ||
                             window.__initialData?.data?.viewer?.username ||
                             window.__viewerUsername;

            if (username) {
                window.__focusgramNavigate(`/${username}/`);
                return true;
            }
        } catch (e) {
            console.warn('[FocusGram] Profile lookup error:', e);
        }

        window.__focusgramNavigate('/');
    };

    function updateStyles() {
        const config = window.__FOCUSGRAM_CONFIG__ || {};
        let css = `
            /* Hide Instagram web navigation bar when it contains Explore, leaving DM headers visible. */
            nav[role="navigation"]:has(a[href*="/explore/"]),
            div[role="navigation"]:has(a[href*="/explore/"]),
            footer[role="contentinfo"]:has(a[href*="/explore/"]) {
                display: none !important;
                visibility: hidden !important;
                pointer-events: none !important;
            }
        `;

        if (config.hideStories || config.minimalMode) {
            css += `
                div[data-pagelet="story_tray"],
                div[data-pagelet*="story"],
                [data-pagelet="story_tray"],
                [data-pagelet*="story_tray"],
                div:has(> div > div > div[aria-label*="Story by"]),
                div:has(> div > div > div[aria-label*="Your story"]),
                [aria-label*="Stories"],
                [aria-label*="stories"],
                [role="list"]:has([aria-label*="tory"]),
                [role="listbox"]:has([aria-label*="tory"]),
                section > div > div[style*="overflow-x"] {
                    display: none !important;
                    visibility: hidden !important;
                    height: 0 !important;
                    min-height: 0 !important;
                    max-height: 0 !important;
                    overflow: hidden !important;
                    margin: 0 !important;
                    padding: 0 !important;
                }
            `;
        }

        if (config.hideReels) {
            css += `
                a[href*="/reel"],
                a[href*="/reels"],
                div:has(> a[href*="/reel"]) {
                    display: none !important;
                }
            `;
        }

        if (config.hideFeedPosts || config.minimalMode) {
            css += `
                main article:not([role="dialog"] article),
                div[role="feed"] article {
                    display: none !important;
                }
            `;
        }

        if (config.blurFeed) {
            css += `
                /* Blur feed media elements without blurring parent containers. */
                html[data-fg-feed] article img:not(header img):not([alt*="profile" i]):not([data-fg-reveal]):not(.fg-unblurred),
                html[data-fg-feed] article video:not([data-fg-reveal]):not(.fg-unblurred),
                html[data-fg-feed] article canvas:not([data-fg-reveal]):not(.fg-unblurred) {
                    filter: blur(28px) !important;
                    transition: filter 0.25s ease-in-out !important;
                }

                /* Keep header avatars unblurred. */
                html[data-fg-feed] article header img,
                html[data-fg-feed] article img[alt*="profile" i] {
                    filter: none !important;
                }
            `;
        }

        if (config.blurExplore) {
            css += `
                html[data-fg-explore] main a[href*="/p/"]:not([data-fg-reveal]):not(.fg-unblurred) img,
                html[data-fg-explore] main a[href*="/reel/"]:not([data-fg-reveal]):not(.fg-unblurred) img,
                main a[href*="/explore/"]:not([data-fg-reveal]):not(.fg-unblurred) img {
                    filter: blur(24px) !important;
                    transition: filter 0.2s ease-in-out !important;
                }
            `;
        }

        if (config.blurReels) {
            css += `
                main div:has(> video):not([data-fg-reveal]):not(.fg-unblurred),
                article div:has(> video):not([data-fg-reveal]):not(.fg-unblurred),
                div[data-fg-reel-container]:not([data-fg-reveal]):not(.fg-unblurred) video {
                    filter: blur(24px) !important;
                    transition: filter 0.2s ease-in-out !important;
                }
            `;
        }

        css += `
            [data-fg-reveal],
            [data-fg-reveal] img,
            [data-fg-reveal] video,
            [data-fg-reveal] canvas,
            [data-fg-reveal] div,
            .fg-unblurred,
            .fg-unblurred img,
            .fg-unblurred video,
            .fg-unblurred canvas,
            .fg-unblurred div {
                filter: none !important;
            }

            /* Comment GIFs, sticker picker, and comment media must NEVER be blurred */
            img[src*="giphy"],
            img[src*="tenor"],
            img[alt*="GIF" i],
            img[alt*="gif" i],
            div[role="dialog"] img[src*="giphy"],
            div[aria-modal="true"] img[src*="giphy"],
            article ul img[src*="giphy"],
            ul[class*="comment" i] img,
            form img[src*="giphy"],
            form img[src*="tenor"] {
                filter: none !important;
            }
        `;

        if (config.hideLikeCounts) {
            css += `
                a[href*="/liked_by/"],
                section a[href*="/liked_by/"],
                span:has(> a[href*="/liked_by/"]),
                div:has(> a[href*="/liked_by/"]),
                section:has(svg[aria-label="Like"]) + div:not(:has(form)):not(:has(input)):not(:has(svg)),
                section:has(svg[aria-label="Unlike"]) + div:not(:has(form)):not(:has(input)):not(:has(svg)),
                section:has(svg[aria-label="Activity Feed"]) + div:not(:has(form)):not(:has(input)):not(:has(svg)) {
                    display: none !important;
                    visibility: hidden !important;
                }
            `;
        }

        if (config.hideFollowerCounts) {
            css += `
                a[href*="/followers/"] span,
                a[href*="/following/"] span {
                    opacity: 0 !important;
                    pointer-events: none !important;
                }
            `;
        }

        if (config.hideShopTab) {
            css += `
                a[href*="/shop"],
                a[href*="/shopping"] {
                    display: none !important;
                }
            `;
        }

        css += `
            :root {
                --fg-reel-bottom-offset: 16px;
                --fg-reel-scrubber-offset: 2px;
            }

            [data-fg-reel-scroller],
            div[style*="position: fixed"][style*="overflow-y: scroll"],
            div[style*="position: fixed"][style*="overflow-y: auto"],
            div[style*="position: fixed"][style*="overflow: scroll"] {
                bottom: 0px !important;
                height: 100% !important;
                max-height: none !important;
            }

            [data-fg-reel-overlay] {
                bottom: var(--fg-reel-bottom-offset, 88px) !important;
                margin-bottom: 0px !important;
                z-index: 25 !important;
                pointer-events: auto !important;
            }

            [data-fg-reel-actions] {
                bottom: var(--fg-reel-bottom-offset, 88px) !important;
                z-index: 25 !important;
                pointer-events: auto !important;
            }

            [data-fg-reel-scrubber] {
                bottom: var(--fg-reel-scrubber-offset, 76px) !important;
                z-index: 26 !important;
            }
        `;

        if (config.noAds !== false) {
            css += `
                article:has(a[href*="/ads/"]),
                article:has(a[href*="ads.instagram.com"]),
                article:has([aria-label*="Sponsored" i]),
                article[data-fg-ad-filtered="true"] {
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
                div[data-ad-preview],
                div[data-testid="post_ad"],
                div[data-testid="suggested-users"] {
                    display: none !important;
                }
            `;
        }

        let styleEl = document.getElementById(STYLE_ID);
        if (!styleEl) {
            styleEl = document.createElement('style');
            styleEl.id = STYLE_ID;
            styleEl.type = 'text/css';
            const head = document.head || document.documentElement;
            if (head) head.appendChild(styleEl);
        }
        styleEl.textContent = css;
    }

    function scanDynamicDOM() {
        const config = window.__FOCUSGRAM_CONFIG__ || {};
        try {
            if (config.noAds !== false) {
                const SPONSORED_KW = ['sponsored', 'gesponsert', 'sponsorisé', 'sponsorise', 'publicidad', 'patrocinado', 'sponsorizzato', 'sponsrad', 'paid partnership', 'werbung', 'anuncio', 'werbeanzeige'];
                const AD_CTA = ['shop now', 'install now', 'sign up', 'apply now', 'book now', 'order now', 'get offer'];
                // Collapse individual articles to 1px to avoid blanking feed container wrappers.
                document.querySelectorAll('article').forEach((article) => {
                    if (article.dataset.fgAdFiltered) return;
                    let isAd = false;
                    const headerElements = article.querySelectorAll('header span, header [aria-label*="Sponsored" i], header [aria-label="Ad" i]');
                    for (let i = 0; i < headerElements.length; i++) {
                        if (headerElements[i].children.length > 2) continue;
                        const t = (headerElements[i].textContent || '').trim().toLowerCase();
                        if (!t || t.length > 30) continue;
                        const clean = t.replace(/[^a-z]/g, '');
                        if (clean === 'ad' || SPONSORED_KW.some(kw => t === kw || clean === kw)) {
                            isAd = true;
                            break;
                        }
                    }
                    if (!isAd && article.querySelector('a[href*="/ad_"], a[href*="/ads/"], a[href*="ads.instagram.com"]')) {
                        isAd = true;
                    }
                    if (!isAd) {
                        const ctaButtons = article.querySelectorAll('div[role="button"]:not([data-fg-x-act]):not([data-fg-download-btn]):not(.fg-x-act-btn), a[role="button"]:not([data-fg-x-act]):not([data-fg-download-btn]):not(.fg-x-act-btn), button');
                        for (let b = 0; b < ctaButtons.length; b++) {
                            const bText = (ctaButtons[b].textContent || '').trim().toLowerCase();
                            if (AD_CTA.some(cta => bText === cta)) {
                                isAd = true;
                                break;
                            }
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
                        article.dataset.fgAdFiltered = 'true';
                    }
                });

                // Filter Reels video ads on reels surface without ever breaking layout
                document.querySelectorAll('video').forEach((video) => {
                    const card = video.closest('[data-fg-reel-card="1"]') || video.closest('article');
                    if (!card || card.dataset.fgAdFiltered || card.dataset.fgReelAd) return;

                    let isSponsored = !!card.querySelector('a[href*="/ad_"], a[href*="/ads/"], a[href*="ads.instagram.com"], a[href*="/about/ads"]') ||
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
                        card.dataset.fgAdFiltered = 'true';
                        card.style.setProperty('visibility', 'hidden', 'important');
                        card.style.setProperty('pointer-events', 'none', 'important');

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
                });
            }

            if (config.hideSuggestedPosts) {
                document.querySelectorAll('article').forEach((article) => {
                    if (article.dataset.fgSuggestedHidden) return;
                    const headers = article.querySelectorAll('header span, header h2, header h3, h2, h3, h4');
                    for (let i = 0; i < headers.length; i++) {
                        const text = (headers[i].textContent || '').trim().toLowerCase();
                        if (text === 'suggested for you' || text === 'suggested posts' || text === 'suggested post' || text === 'suggestions for you') {
                            article.style.setProperty('height', '1px', 'important');
                            article.style.setProperty('min-height', '1px', 'important');
                            article.style.setProperty('max-height', '1px', 'important');
                            article.style.setProperty('overflow', 'hidden', 'important');
                            article.style.setProperty('opacity', '0', 'important');
                            article.style.setProperty('pointer-events', 'none', 'important');
                            article.style.setProperty('margin', '0', 'important');
                            article.style.setProperty('padding', '0', 'important');
                            article.dataset.fgSuggestedHidden = 'true';
                            window.dispatchEvent(new Event('scroll'));
                            break;
                        }
                    }
                });
                document.querySelectorAll('div[data-testid="suggested-users"]').forEach((el) => {
                    el.style.setProperty('display', 'none', 'important');
                });
            }

            if (config.hideLikeCounts) {
                // 1. Liked by links and their parent containers
                document.querySelectorAll('a[href*="/liked_by/"]').forEach((el) => {
                    el.style.setProperty('display', 'none', 'important');
                    const parent = el.closest('div');
                    if (parent && !parent.querySelector('svg') && parent.textContent && /liked/i.test(parent.textContent)) {
                        parent.style.setProperty('display', 'none', 'important');
                    }
                });

                // 2. Feed post like counts text (e.g. "Liked by ...", "97 likes", "12 others")
                document.querySelectorAll('article section ~ div span, article section ~ div div, div[role="presentation"] span').forEach((el) => {
                    if (el.querySelector('svg') || el.closest('svg') || el.querySelector('img') || el.closest('form')) return;
                    if (el.children.length > 2) return;
                    const text = (el.textContent || '').trim();
                    if (/^(liked\s+by|[\d,.]+[KkMm]?\s+likes?|[\d,.]+\s+others|be\s+the\s+first\s+to\s+like)/i.test(text)) {
                        el.style.setProperty('display', 'none', 'important');
                    }
                });

                // 3. Standalone count buttons in feed post: <span class="..." role="button">97</span>
                document.querySelectorAll('article span[role="button"], section span[role="button"]').forEach((el) => {
                    if (el.querySelector('svg') || el.closest('svg')) return;
                    const text = (el.textContent || '').trim();
                    if (/^[\d,.]+[KkMm]?$/.test(text)) {
                        el.style.setProperty('display', 'none', 'important');
                    }
                });

                // 4. Reels like count in right interaction rail:
                // Sibling or child span with numbers/K/M (e.g. <span class="html-span ...">192K</span>)
                document.querySelectorAll('svg[aria-label="Like"], svg[aria-label="Unlike"]').forEach((svg) => {
                    const btn = svg.closest('[role="button"]') || svg.parentElement;
                    if (!btn) return;
                    const container = btn.parentElement;
                    if (container) {
                        container.querySelectorAll('span, div').forEach((child) => {
                            if (child.contains(svg) || child.querySelector('svg') || child.closest('svg')) return;
                            const text = (child.textContent || '').trim();
                            if (/^[\d,.]+[KkMm]?$/.test(text) || /^[\d,.]+[KkMm]?\s+likes?$/i.test(text)) {
                                child.style.setProperty('display', 'none', 'important');
                            }
                        });
                        let sib = btn.nextElementSibling;
                        if (sib && !sib.contains(svg) && !sib.querySelector('svg')) {
                            const text = (sib.textContent || '').trim();
                            if (/^[\d,.]+[KkMm]?$/.test(text) || /^[\d,.]+[KkMm]?\s+likes?$/i.test(text)) {
                                sib.style.setProperty('display', 'none', 'important');
                            }
                        }
                    }
                });
            }

            if (config.hideFollowerCounts) {
                document.querySelectorAll('a[href*="/followers/"], a[href*="/following/"]').forEach((el) => {
                    el.querySelectorAll('span').forEach(s => {
                        s.style.setProperty('visibility', 'hidden', 'important');
                    });
                });
            }

            tagReelsOverlay();

            // Minimal mode redirects root/explore browsing into direct messages.
            if (config.minimalMode) {
                const curPath = (window.location.pathname || '').toLowerCase();
                if (curPath === '/' || curPath === '' || curPath.startsWith('/explore') || curPath.startsWith('/reels') || curPath.startsWith('/reel') || curPath.startsWith('/p/')) {
                    window.__focusgramNavigate('/direct/inbox/');
                }
            }
        } catch (_) {}
    }

    function tagReelsOverlay() {
        const path = (window.location.pathname || '').toLowerCase();
        if (!path.startsWith('/reels') && !path.startsWith('/reel')) return;
        const vh = window.innerHeight;
        if (!vh) return;

        const firstVideo = document.querySelector('video');
        if (firstVideo) {
            let el = firstVideo.parentElement;
            while (el && el !== document.body && el !== document.documentElement) {
                const cs = window.getComputedStyle(el);
                if (cs.position === 'fixed' || cs.overflowY === 'scroll' || cs.overflowY === 'auto') {
                    if (!el.hasAttribute('data-fg-reel-scroller')) {
                        el.setAttribute('data-fg-reel-scroller', '1');
                    }
                    el.style.setProperty('bottom', '0px', 'important');
                    el.style.setProperty('height', '100%', 'important');
                    el.style.setProperty('max-height', 'none', 'important');
                    break;
                }
                el = el.parentElement;
            }
        }

        document.querySelectorAll('video').forEach(v => {
            let card = v.closest('article') || v.closest('section') || v.closest('[role="region"]') || v.parentElement;
            while (card && card !== document.body && card !== document.documentElement) {
                if (card.tagName === 'ARTICLE' || card.tagName === 'SECTION') break;
                if (card.getBoundingClientRect().height > vh * 0.5) break;
                card = card.parentElement;
            }
            if (!card || card.tagName === 'MAIN' || card.id === 'react-root' || card.querySelectorAll('video').length > 1) return;

            let overlay = null;
            const audioLink = card.querySelector('a[href*="/audio/"]');
            if (audioLink) {
                let p = audioLink.parentElement;
                while (p && p !== card && p !== document.body) {
                    const hasAuthorOrCaption = p.querySelector('a[role="link"]:not([href*="/audio/"])') || p.querySelector('span[dir="auto"]');
                    if (hasAuthorOrCaption) {
                        overlay = p;
                        let candidate = p;
                        while (candidate && candidate.parentElement && candidate.parentElement !== card) {
                            const cs = window.getComputedStyle(candidate);
                            if (cs.position === 'absolute' || cs.position === 'fixed') {
                                overlay = candidate;
                                break;
                            }
                            candidate = candidate.parentElement;
                        }
                        break;
                    }
                    p = p.parentElement;
                }
                if (!overlay) overlay = audioLink.closest('div[style*="bottom"]') || audioLink.parentElement;
            }

            if (!overlay) {
                const captions = card.querySelectorAll('span[dir="auto"], div[dir="auto"]');
                for (let i = 0; i < captions.length; i++) {
                    const c = captions[i];
                    if (c.textContent && c.textContent.trim().length > 0) {
                        let p = c.closest('div');
                        while (p && p !== card && p !== document.body) {
                            const cs = window.getComputedStyle(p);
                            if (cs.position === 'absolute' || cs.position === 'fixed' || p.parentElement === card) {
                                overlay = p;
                                break;
                            }
                            p = p.parentElement;
                        }
                        if (overlay) break;
                    }
                }
            }

            if (overlay && overlay !== card) {
                overlay.setAttribute('data-fg-reel-overlay', '1');
                overlay.style.setProperty('bottom', 'var(--fg-reel-bottom-offset, 16px)', 'important');
                overlay.style.removeProperty('padding-bottom');
                overlay.style.setProperty('margin-bottom', '0px', 'important');
                overlay.style.setProperty('z-index', '25', 'important');
                overlay.style.setProperty('pointer-events', 'auto', 'important');
            }

            const likeIcon = card.querySelector('svg[aria-label="Like"], svg[aria-label="Unlike"], svg[aria-label="Liked"]');
            if (likeIcon) {
                let actionRail = likeIcon.closest('div[role="button"]') || likeIcon.parentElement;
                while (actionRail && actionRail !== card && actionRail !== document.body) {
                    const cs = window.getComputedStyle(actionRail);
                    if (cs.position === 'absolute' || cs.position === 'fixed' || (actionRail.parentElement === card)) break;
                    if (actionRail.parentElement && (actionRail.parentElement.querySelector('a[href*="/audio/"]') || actionRail.parentElement.querySelector('video'))) break;
                    actionRail = actionRail.parentElement;
                }
                if (actionRail && actionRail !== card) {
                    actionRail.setAttribute('data-fg-reel-actions', '1');
                    actionRail.style.setProperty('bottom', 'var(--fg-reel-bottom-offset, 16px)', 'important');
                    actionRail.style.setProperty('z-index', '25', 'important');
                    actionRail.style.setProperty('pointer-events', 'auto', 'important');
                }
            }
        });
    }

    // --- Feed Path Monitoring & Unblur Interaction Engine ---
    function updateFeedAttr() {
        const path = window.location.pathname || '';
        const isFeed = path === '/' || path === '';
        document.documentElement.toggleAttribute('data-fg-feed', isFeed);
        document.documentElement.toggleAttribute('data-fg-explore', path.startsWith('/explore'));
    }
    updateFeedAttr();
    window.addEventListener('popstate', updateFeedAttr);
    new MutationObserver(updateFeedAttr).observe(document.documentElement, { childList: true, subtree: true });

    let holdTimer = null;
    let holdStartX = 0;
    let holdStartY = 0;
    let swallowUntil = 0;

    function getBlurredTarget(el) {
        if (!el) return null;

        // Comment GIFs, sticker picker, and comment controls must NEVER be treated as unblur targets
        if (el.closest('img[src*="giphy"], img[src*="tenor"], img[alt*="GIF" i], img[alt*="gif" i], form, ul[class*="comment" i]')) {
            return null;
        }
        if (el.tagName === 'IMG' && (el.src?.includes('giphy') || el.src?.includes('tenor') || el.alt?.toLowerCase().includes('gif'))) {
            return null;
        }
        const config = window.__FOCUSGRAM_CONFIG__ || {};

        if (config.blurFeed) {
            // Ignore slide advance controls, headers, and reactions when resolving unblur targets.
            if (el.closest('button[aria-label="Next"], button[aria-label="Go back"], button[aria-label="Next slide"], button[aria-label="Previous slide"], button[aria-label="Chevron"], header, section:has(svg[aria-label="Like"])')) {
                return null;
            }

            const article = el.closest('article');
            if (article) {
                const media = el.matches('img, video, canvas') ? el : (el.closest('img, video, canvas') || el.querySelector('img, video, canvas'));
                const slide = el.closest('li, [role="group"], [style*="scroll-snap-align"], [data-testid*="slide"]') ||
                              (media ? media.closest('div[style*="width: 100%"], div[style*="min-width"]') : null);

                if (slide && !slide.hasAttribute('data-fg-reveal') && !slide.classList.contains('fg-unblurred')) {
                    return slide;
                }
                if (media && !media.hasAttribute('data-fg-reveal') && !media.classList.contains('fg-unblurred')) {
                    return media;
                }

                // Single post media container fallback.
                const mediaContainer = article.querySelector('div[style*="aspect-ratio"], div:has(> img), div:has(> video)');
                if (mediaContainer && mediaContainer.contains(el)) {
                    const postImg = mediaContainer.querySelector('img:not(header img), video');
                    if (postImg && !postImg.hasAttribute('data-fg-reveal') && !postImg.classList.contains('fg-unblurred')) {
                        return postImg;
                    }
                }
            }
        }

        if (config.blurReels) {
            const reelContainer = el.closest('main div:has(> video), article div:has(> video)');
            if (reelContainer && !reelContainer.hasAttribute('data-fg-reveal') && !reelContainer.classList.contains('fg-unblurred')) {
                return reelContainer;
            }
            const video = el.closest('video');
            if (video && video.parentElement && !video.parentElement.hasAttribute('data-fg-reveal') && !video.parentElement.classList.contains('fg-unblurred')) {
                return video.parentElement;
            }
        }

        if (config.blurExplore) {
            const expLink = el.closest('main a[href*="/p/"], main a[href*="/reel/"]');
            if (expLink && !expLink.hasAttribute('data-fg-reveal') && !expLink.classList.contains('fg-unblurred')) {
                return expLink;
            }
        }
        return null;
    }

    function revealTarget(target) {
        if (!target) return;
        target.setAttribute('data-fg-reveal', '1');
        target.classList.add('fg-unblurred');
        target.style.setProperty('filter', 'none', 'important');
        target.querySelectorAll('*').forEach(c => {
            c.setAttribute('data-fg-reveal', '1');
            c.classList.add('fg-unblurred');
            c.style.setProperty('filter', 'none', 'important');
        });
    }

    document.addEventListener('pointerdown', (e) => {
        const config = window.__FOCUSGRAM_CONFIG__ || {};
        const mode = config.unblurMode || (config.tapToUnblur ? 'tap' : 'hold');
        if (mode !== 'hold') return;
        const target = getBlurredTarget(e.target);
        if (!target) return;
        holdStartX = e.clientX;
        holdStartY = e.clientY;
        holdTimer = setTimeout(() => {
            revealTarget(target);
            swallowUntil = Date.now() + 450;
        }, 350);
    }, true);

    document.addEventListener('pointermove', (e) => {
        if (holdTimer && Math.hypot(e.clientX - holdStartX, e.clientY - holdStartY) > 12) {
            clearTimeout(holdTimer);
            holdTimer = null;
        }
    }, true);

    ['pointerup', 'pointercancel'].forEach(evt => {
        document.addEventListener(evt, () => {
            if (holdTimer) {
                clearTimeout(holdTimer);
                holdTimer = null;
            }
        }, true);
    });

    document.addEventListener('click', (e) => {
        if (Date.now() < swallowUntil) {
            e.preventDefault();
            e.stopPropagation();
            return;
        }
        const config = window.__FOCUSGRAM_CONFIG__ || {};
        const mode = config.unblurMode || (config.tapToUnblur ? 'tap' : 'hold');
        const target = getBlurredTarget(e.target);
        if (!target) return;

        if (mode === 'tap') {
            e.preventDefault();
            e.stopPropagation();
            revealTarget(target);
        }
    }, true);

    window.__focusgramUpdateStyles = function () {
        updateStyles();
        scanDynamicDOM();
    };

    updateStyles();
    scanDynamicDOM();

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            updateStyles();
            scanDynamicDOM();
        });
    }

    // Debounce DOM scanning to avoid thrashing on feed mutations.
    let timer = null;
    const observer = new MutationObserver(() => {
        if (timer) clearTimeout(timer);
        timer = setTimeout(scanDynamicDOM, 250);
    });

    observer.observe(document.documentElement || document.body, {
        childList: true,
        subtree: true
    });

    console.log('[FocusGram] Content Hider & SPA Navigator active.');
})();
