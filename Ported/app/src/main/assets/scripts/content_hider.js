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
            /* ALWAYS hide Instagram web's own bottom navigation bar */
            nav[role="navigation"],
            div[role="navigation"]:has(a[href="/"]),
            div[role="navigation"]:has(svg[aria-label="Home"]),
            div[role="navigation"]:has(svg[aria-label="Search"]),
            footer[role="contentinfo"],
            div[style*="position: fixed"][style*="bottom: 0"]:has(a[href="/"]),
            div[style*="position: fixed"][style*="bottom: 0px"]:has(a[href="/"]) {
                display: none !important;
                visibility: hidden !important;
                pointer-events: none !important;
            }
        `;

        if (config.hideStories) {
            css += `
                [aria-label*="Stories"],
                [aria-label*="stories"],
                [role="list"]:has([aria-label*="tory"]),
                [role="listbox"]:has([aria-label*="tory"]),
                section > div > div[style*="overflow-x"] {
                    display: none !important;
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
                article:has(span:has-text("Sponsored")),
                article:has(a[href*="/ad_"]),
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
            if (config.hideSuggestedPosts !== false) {
                document.querySelectorAll('span, h2, h3, h4').forEach((el) => {
                    const text = (el.textContent || '').trim().toLowerCase();
                    if (text === 'suggested for you' || text === 'suggested posts' || text === 'sponsored') {
                        const parent = el.closest('article') || el.closest('div[style*="flex"]');
                        if (parent) {
                            parent.style.setProperty('display', 'none', 'important');
                        }
                    }
                });
            }

            if (config.hideLikeCounts) {
                document.querySelectorAll('span').forEach((el) => {
                    const text = (el.textContent || '').trim();
                    if (/^[\d,.]+[KkMm]?\s+(like|likes|view|views)$/i.test(text)) {
                        el.style.setProperty('display', 'none', 'important');
                    }
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
