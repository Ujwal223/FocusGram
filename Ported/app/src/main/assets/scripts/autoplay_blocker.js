/**
 * FocusGram — Feed & Video Autoplay Blocker UserScript
 *
 * Terminates automated HTML5 video autoplay and loop loops in feed and explore,
 * while preserving intentional user clicks and reels playback.
 */

'use strict';

(function () {
    if (window.__fgAutoplayPatched) return;
    window.__fgAutoplayPatched = true;

    const ALLOW_KEY = '__fgUserExplicitlyPlayed';

    function isReelVideo() {
        try {
            const path = window.location.pathname || '';
            return path.includes('/reel/') || path.includes('/reels/');
        } catch (_) {
            return false;
        }
    }

    function shouldBlock() {
        if (isReelVideo()) return false;
        const config = window.__FOCUSGRAM_CONFIG__ || {};
        return config.blockAutoplay !== false;
    }

    const originalPlay = HTMLVideoElement.prototype.play;

    if (HTMLVideoElement.prototype.play) {
        HTMLVideoElement.prototype.play = function () {
            try {
                if (!shouldBlock() || this[ALLOW_KEY]) {
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

    // Capture user taps to unlock explicit playback
    document.addEventListener('click', function (e) {
        const video = e.target.closest('video') || e.target.querySelector('video');
        if (video) {
            video[ALLOW_KEY] = true;
            try {
                video.play();
            } catch (_) {}
        }
    }, true);

    console.log('[FocusGram] Feed Autoplay Blocker active.');
})();
