/**
 * FocusGram — Content Hider, UI Guardrails & SPA Navigation UserScript
 *
 * Provides:
 * 1. Hiding Instagram's native web bottom navigation bar so only FocusGram's native bar appears.
 * 2. Instant SPA navigation helper without page reloads.
 * 3. Profile navigation resolving the current user profile rather than edit profile.
 * 4. Dynamic CSS injection reacting to FocusSettings (Stories, Reels, Ads, Suggested Posts, Vanity Metrics).
 */

'use strict';

(function () {
    const STYLE_ID = 'focusgram-dynamic-guardrails-styles';

    // 1. Client-Side SPA Navigation Helper
    window.__focusgramNavigate = function (path) {
        if (!path) return;
        try {
            // Priority 1: Match and click navigation link in the DOM
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

            // Priority 2: HTML5 History API pushState
            if (window.history && typeof window.history.pushState === 'function') {
                window.history.pushState(null, '', path);
                window.dispatchEvent(new PopStateEvent('popstate', { state: null }));
                return true;
            }
        } catch (e) {
            console.warn('[FocusGram] SPA navigation error:', e);
        }

        // Fallback: standard assignment
        window.location.href = path;
    };

    // 2. Client-Side Profile Navigation Helper
    window.__focusgramOpenProfile = function () {
        try {
            // Find user profile avatar/link in navigation bar
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

            // Check viewer username from window configuration objects
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

    // 3. Dynamic Styles Generation reacting to FocusSettings
    function updateStyles() {
        const config = window.__FOCUSGRAM_CONFIG__ || {};
        let css = `
            /* ALWAYS hide Instagram web's own bottom navigation bar — strictly require Explore link so DMs are never hidden */
            nav[role="navigation"]:has(a[href*="/explore/"]),
            div[role="navigation"]:has(a[href*="/explore/"]),
            footer[role="contentinfo"]:has(a[href*="/explore/"]) {
                display: none !important;
                visibility: hidden !important;
                pointer-events: none !important;
            }
        `;

        if (config.hideStories) {
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

        if (config.blurExplore) {
            css += `
                main a[href*="/p/"] img,
                main a[href*="/reel/"] img,
                div[style*="grid"] img {
                    filter: blur(24px) !important;
                    transition: filter 0.2s ease-in-out !important;
                }
                main a[href*="/p/"] img:active,
                main a[href*="/reel/"] img:active {
                    filter: none !important;
                }
            `;
        }

        if (config.blurReels) {
            css += `
                main div:has(> video),
                article div:has(> video) {
                    filter: blur(24px) !important;
                    transition: filter 0.2s ease-in-out !important;
                }
                main div:has(> video):active,
                article div:has(> video):active {
                    filter: none !important;
                }
            `;
        }

        if (config.hideLikeCounts) {
            css += `
                a[href*="/liked_by/"],
                section a[href*="/liked_by/"],
                span:has(> a[href*="/liked_by/"]) {
                    display: none !important;
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

        if (config.noAds !== false) {
            css += `
                article:has(a[href*="/ad_"]),
                article:has(a[href*="/ads/"]),
                article:has(a[href*="/about/ads"]),
                article:has(a[href*="ads.instagram.com"]),
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

    // Dynamic scanning for elements not coverable by pure CSS
    function scanDynamicDOM() {
        const config = window.__FOCUSGRAM_CONFIG__ || {};
        try {
            // Filter Sponsored Ads
            if (config.noAds !== false) {
                const SPONSORED_KW = ['sponsored', 'gesponsert', 'sponsorisé', 'publicidad', 'patrocinado', 'sponsorizzato', 'paid partnership'];
                const AD_CTA = ['learn more', 'shop now', 'install now', 'sign up', 'apply now', 'book now', 'download', 'order now', 'get offer', 'subscribe', 'contact us'];
                // Strictly target individual article elements to avoid hiding feed wrappers
                document.querySelectorAll('article').forEach((article) => {
                    if (article.dataset.fgAdFiltered) return;
                    let isAd = false;
                    const spans = article.querySelectorAll('header span, header a, div[role="button"] span, a[role="button"]');
                    for (let i = 0; i < spans.length; i++) {
                        const t = (spans[i].textContent || '').trim().toLowerCase();
                        if (SPONSORED_KW.some(kw => t === kw || t.startsWith(kw)) || AD_CTA.some(cta => t === cta)) {
                            isAd = true;
                            break;
                        }
                    }
                    if (!isAd && article.querySelector('a[href*="/ad_"], a[href*="/ads/"], a[href*="/about/ads"], a[href*="ads.instagram.com"]')) {
                        isAd = true;
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
                        window.dispatchEvent(new Event('scroll'));
                    }
                });
            }

            // Filter Suggested Posts
            if (config.hideSuggestedPosts) {
                // Strictly target individual article elements or specific suggested-users widgets
                document.querySelectorAll('article').forEach((article) => {
                    if (article.dataset.fgSuggestedHidden) return;
                    const headers = article.querySelectorAll('header span, header h2, header h3, h2, h3, h4, span');
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

            // Hide Like Counts
            if (config.hideLikeCounts) {
                document.querySelectorAll('span, a').forEach((el) => {
                    const text = (el.textContent || '').trim();
                    if (/^[\d,.]+[KkMm]?\s+(like|likes|view|views)$/i.test(text) || /^liked by\s+/i.test(text.toLowerCase())) {
                        el.style.setProperty('display', 'none', 'important');
                    }
                });
            }

            // Hide Follower Counts
            if (config.hideFollowerCounts) {
                document.querySelectorAll('a[href*="/followers/"], a[href*="/following/"]').forEach((el) => {
                    el.querySelectorAll('span').forEach(s => {
                        s.style.setProperty('visibility', 'hidden', 'important');
                    });
                });
            }
        } catch (_) {}
    }

    // Expose hook to reload styles on config update
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

    // Debounced observer to hide dynamic elements smoothly
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
