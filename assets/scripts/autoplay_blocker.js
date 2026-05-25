/**
 * FocusGram Autoplay Blocker
 * Injected at DOCUMENT_START — before Instagram's JS loads.
 * Prevents video autoplay by:
 * 1. Blocking play() calls on video elements
 * 2. Disabling autoplay attribute
 * 3. Removing preload attributes
 */
(function () {
  'use strict';

  // This script is only registered when the setting is enabled, so default ON.
  window.__fgBlockAutoplay = typeof window.__fgBlockAutoplay === 'boolean'
    ? window.__fgBlockAutoplay : true;
  const ALLOW_KEY = '__fgUserStartedPlayback';
  let userGestureUntil = 0;

  function isReelRoute() {
    const path = window.location.pathname || '';
    return path.indexOf('/reel/') >= 0 || path === '/reels' || path.indexOf('/reels/') >= 0;
  }

  function isUserGestureActive() {
    return Date.now() < userGestureUntil;
  }

  function markUserGesture(target) {
    userGestureUntil = Date.now() + 1200;
    try {
      let video = target && target.closest ? target.closest('video') : null;
      if (!video && target && target.querySelector) video = target.querySelector('video');
      if (video) video[ALLOW_KEY] = true;
    } catch (_) {}
  }

  document.addEventListener('pointerdown', function (event) {
    markUserGesture(event.target);
  }, true);
  document.addEventListener('touchstart', function (event) {
    markUserGesture(event.target);
  }, true);
  document.addEventListener('click', function (event) {
    markUserGesture(event.target);
  }, true);

  // Override HTMLMediaElement.play() to check our flag
  const _play = HTMLMediaElement.prototype.play;
  HTMLMediaElement.prototype.play = function () {
    if (
      window.__fgBlockAutoplay &&
      !isReelRoute() &&
      this[ALLOW_KEY] !== true &&
      !isUserGestureActive()
    ) {
      // Return a resolved promise to avoid breaking Instagram's code
      try { this.pause(); } catch (_) {}
      return Promise.resolve();
    }
    return _play.call(this);
  };

  // Override autoplay property setter
  const _videoDescriptor = Object.getOwnPropertyDescriptor(HTMLVideoElement.prototype, 'autoplay') || {};
  const _originalAutoplaySetter = _videoDescriptor.set;

  Object.defineProperty(HTMLVideoElement.prototype, 'autoplay', {
    set: function (value) {
      if (window.__fgBlockAutoplay && value) {
        // Silently ignore autoplay attempts when blocking is enabled
        return;
      }
      if (_originalAutoplaySetter) {
        _originalAutoplaySetter.call(this, value);
      }
    },
    get: function () {
      if (_videoDescriptor.get) {
        return _videoDescriptor.get.call(this);
      }
      return this.getAttribute('autoplay') !== null;
    },
    enumerable: _videoDescriptor.enumerable,
    configurable: true,
  });

  // On page load and SPA navigation, scan for video elements and remove autoplay
  const removeAutoplayFromVideos = () => {
    document.querySelectorAll('video, [role="video"]').forEach(el => {
      if (window.__fgBlockAutoplay && !isReelRoute() && el[ALLOW_KEY] !== true) {
        el.autoplay = false;
        el.removeAttribute('autoplay');
        el.removeAttribute('preload');
        try { el.preload = 'none'; } catch (_) {}
        if (el.paused === false) {
          el.pause();
        }
      }
    });
  };

  // Run on load and when document changes
  removeAutoplayFromVideos();

  if (!window.__fgAutoplayObserver) {
    let _timer = null;
    window.__fgAutoplayObserver = new MutationObserver(() => {
      clearTimeout(_timer);
      _timer = setTimeout(removeAutoplayFromVideos, 500);
    });
    window.__fgAutoplayObserver.observe(document.documentElement, {
      childList: true,
      subtree: true,
    });
  }

  // Allow Flutter to toggle
  window.__fgSetBlockAutoplay = function (enabled) {
    window.__fgBlockAutoplay = !!enabled;
    if (enabled) {
      removeAutoplayFromVideos();
    }
  };

  document.addEventListener('play', function (event) {
    if (event.target && event.target.tagName === 'VIDEO' && isUserGestureActive()) {
      event.target[ALLOW_KEY] = true;
    }
  }, true);
})();
