/**
 * FocusGram — Feed & Video Autoplay Blocker UserScript
 *
 * Terminates automated HTML5 video autoplay and loops in feed and explore,
 * while seamlessly preserving intentional user clicks and reels playback.
 */

'use strict';

(function () {
    function isAuthPage() {
        try {
            var path = (window.location.pathname || '').toLowerCase();
            if (path.startsWith('/accounts') || path.startsWith('/login') || path.startsWith('/signup')) return true;
            var c = document.cookie || '';
            if (!c.includes('sessionid=') && !c.includes('ds_user_id=')) return true;
            if (document.getElementById('loginForm') || document.querySelector('form[action*="login"], input[name="password"]')) return true;
        } catch (_) {}
        return false;
    }

    if (isAuthPage()) return; // Leave login/signup completely untouched

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

        // Check if inside post container or media wrapper
        const container = target.closest('article, [role="presentation"], div[style*="aspect-ratio"], div:has(video)');
        if (container) {
            const v = container.querySelector('video');
            if (v) return v;
        }

        // Search upward through ancestor elements
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
                // If on Reels or blockAutoplay is off, allow playback
                if (!shouldBlock()) {
                    return originalPlay.apply(this, arguments);
                }

                // Strictly allow ONLY videos that the user explicitly tapped
                if (allowedVideos.has(this)) {
                    return originalPlay.apply(this, arguments);
                }

                // Strip autoplay attributes and freeze playback
                this.removeAttribute('autoplay');
                this.autoplay = false;
                this.loop = false;
                this.pause();

                // Return resolved promise so React/Instagram engine does not crash
                return Promise.resolve();
            } catch (e) {
                return originalPlay.apply(this, arguments);
            }
        };
    }

    // Capture explicit user click/tap gestures directly on post videos
    ['click', 'pointerup'].forEach((eventType) => {
        document.addEventListener(eventType, function (e) {
            if (!e.isTrusted) return;
            // Ignore if tapped on buttons/comments/links outside the video area
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
