/**
 * Feed video autoplay blocker.
 *
 * Intercepts HTML5 play() calls to prevent automatic playback in feed and explore
 * while permitting intentional user tap-to-play gestures and reels playback.
 */

'use strict';

(function () {
    function isAuthPage() {
        try {
            var path = (window.location.pathname || '').toLowerCase();
            if (path.includes('/accounts/edit')) return false;
            if (path.startsWith('/accounts') || path.startsWith('/login') || path.startsWith('/signup') || path.startsWith('/two_factor') || path.startsWith('/challenge')) return true;
            if (document.getElementById('loginForm') || document.querySelector('form[action*="login"], form input[type="password"]')) return true;
        } catch (_) {}
        return false;
    }

    if (isAuthPage()) return;

    if (window.__fgAutoplayPatched) return;
    window.__fgAutoplayPatched = true;

    const allowedVideos = new WeakSet();

    function isReelVideo() {
        try {
            const path = (window.location.pathname || '').toLowerCase();
            return path.startsWith('/reel') || path.includes('/reel');
        } catch (_) {
            return false;
        }
    }

    function shouldBlock() {
        if (isReelVideo()) return false;
        const config = window.__FOCUSGRAM_CONFIG__ || {};
        return config.blockAutoplay !== false;
    }

    function findVideoInTarget(target) {
        if (!target) return null;
        if (target.tagName === 'VIDEO') return target;
        const closestVid = target.closest('video');
        if (closestVid) return closestVid;

        const container = target.closest('article, [role="presentation"], div[style*="aspect-ratio"], div:has(video)');
        if (container) {
            const v = container.querySelector('video');
            if (v) return v;
        }

        let p = target;
        for (let i = 0; i < 6 && p; i++) {
            const v = p.querySelector('video');
            if (v) return v;
            p = p.parentElement;
        }
        return null;
    }

    const originalPlay = HTMLVideoElement.prototype.play;

    if (HTMLVideoElement.prototype.play) {
        HTMLVideoElement.prototype.play = function () {
            try {
                if (window.__fgMediaPaused) {
                    this.removeAttribute('autoplay');
                    this.autoplay = false;
                    this.pause();
                    return Promise.resolve();
                }

                if (!shouldBlock()) {
                    return originalPlay.apply(this, arguments);
                }

                if (allowedVideos.has(this)) {
                    return originalPlay.apply(this, arguments);
                }

                this.removeAttribute('autoplay');
                this.autoplay = false;
                this.loop = false;
                this.pause();

                // Resolving promise avoids uncaught rejection in React video wrappers.
                return Promise.resolve();
            } catch (e) {
                return originalPlay.apply(this, arguments);
            }
        };
    }

    ['click', 'pointerup'].forEach((eventType) => {
        document.addEventListener(eventType, function (e) {
            if (!e.isTrusted) return;
            // Ignore interaction when tapping surrounding action buttons or comments.
            if (e.target.closest('button[aria-label="Like"], button[aria-label="Comment"], button[aria-label="Share"], a[role="link"]:not(:has(video))')) {
                return;
            }
            const video = findVideoInTarget(e.target);
            if (video) {
                allowedVideos.add(video);
                try {
                    originalPlay.apply(video);
                } catch (_) {}
            }
        }, true);
    });

    console.log('[FocusGram] Feed Autoplay Blocker active (strict per-post mode).');
})();
