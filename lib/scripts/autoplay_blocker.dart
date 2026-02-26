/// JavaScript to block autoplaying videos on Instagram while still allowing
/// explicit user-initiated playback.
///
/// This script:
/// - Overrides HTMLVideoElement.prototype.play before Instagram initialises.
/// - Returns Promise.resolve() for blocked autoplay calls (never throws).
/// - Uses a short-lived per-element flag set by user clicks to allow play().
/// - Strips the autoplay attribute from dynamically added <video> elements.
const String kAutoplayBlockerJS = r'''
  (function fgAutoplayBlocker() {
    if (window.__fgAutoplayPatched) return;
    window.__fgAutoplayPatched = true;

    // Toggleable at runtime from Flutter:
    // window.__fgBlockAutoplay = true/false
    if (typeof window.__fgBlockAutoplay === 'undefined') {
      window.__fgBlockAutoplay = true;
    }

    const ALLOW_KEY = '__fgAllowPlayUntil';
    const ALLOW_WINDOW_MS = 1000;

    function markAllow(video) {
      try {
        video[ALLOW_KEY] = Date.now() + ALLOW_WINDOW_MS;
      } catch (_) {}
    }

    function shouldAllow(video) {
      try {
        const until = video[ALLOW_KEY] || 0;
        return Date.now() <= until;
      } catch (_) {
        return false;
      }
    }

    function stripAutoplay(root) {
      try {
        if (window.__fgBlockAutoplay !== true) return;
        const all = root.querySelectorAll
          ? root.querySelectorAll('video')
          : (root.tagName === 'VIDEO' ? [root] : []);
        all.forEach(v => {
          v.removeAttribute('autoplay');
          try { v.autoplay = false; } catch (_) {}
        });
      } catch (_) {}
    }

    // Initial pass
    try {
      document.querySelectorAll('video').forEach(v => stripAutoplay(v));
    } catch (_) {}

    // MutationObserver for dynamically added videos
    try {
      const mo = new MutationObserver(ms => {
        if (window.__fgBlockAutoplay !== true) return;
        ms.forEach(m => {
          m.addedNodes.forEach(node => {
            if (!node || node.nodeType !== 1) return;
            if (node.tagName === 'VIDEO') {
              stripAutoplay(node);
            } else {
              stripAutoplay(node);
            }
          });
        });
      });
      mo.observe(document.documentElement, { childList: true, subtree: true });
    } catch (_) {}

    // Allow play() shortly after a direct user click on a video.
    document.addEventListener('click', function(e) {
      try {
        const video = e.target && e.target.closest && e.target.closest('video');
        if (!video) return;
        markAllow(video);
        try { video.play(); } catch (_) {}
      } catch (_) {}
    }, true);

    // Prototype override
    try {
      const origPlay = HTMLVideoElement.prototype.play;
      if (!origPlay) return;
      if (!window.__fgOrigVideoPlay) window.__fgOrigVideoPlay = origPlay;

      HTMLVideoElement.prototype.play = function() {
        try {
          if (window.__fgBlockAutoplay !== true) {
            return origPlay.apply(this, arguments);
          }
          if (shouldAllow(this)) {
            return origPlay.apply(this, arguments);
          }
          // Block autoplay: resolve without actually starting playback.
          return Promise.resolve();
        } catch (_) {
          // If anything goes wrong, fall back to original behaviour to avoid
          // breaking Instagram's player.
          try {
            return origPlay.apply(this, arguments);
          } catch (_) {
            return Promise.resolve();
          }
        }
      };
    } catch (_) {}
  })();
''';

