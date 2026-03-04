/// JavaScript to block autoplaying videos on Instagram feed/explore while:
/// - Allowing videos to play normally when "Block Autoplay Videos" is OFF
/// - Allowing user-initiated playback on click when blocking is ON
/// - NEVER blocking reels (they should always play normally per user request)
///
/// This script:
/// - Overrides HTMLVideoElement.prototype.play before Instagram initialises.
/// - PAUSES any playing videos immediately when autoplay is blocked (only for feed/explore).
/// - Returns Promise.resolve() for blocked autoplay calls (never throws).
/// - Uses a per-element flag set by user clicks to permanently allow that video to play.
/// - Strips the autoplay attribute from dynamically added <video> elements.
/// - Respects session state - allows autoplay when session is active.
/// - NEVER blocks reels - they always play normally.
/// - Once a video is explicitly played by user, it plays fully without interruption.
const String kAutoplayBlockerJS = r'''
  (function fgAutoplayBlocker() {
    if (window.__fgAutoplayPatched) return;
    window.__fgAutoplayPatched = true;

    // Default to blocking autoplay if not set
    window.__fgBlockAutoplay = window.__fgBlockAutoplay !== false;
    
    // Session state - set by FocusGram when session is active
    // window.__focusgramSessionActive = true/false

    // Helper to check if this is a reel video (should NEVER be blocked)
    function isReelVideo() {
      try {
        const url = window.location.href || '';
        // Check if we're on a reel page
        if (url.includes('/reels/') || url.includes('/reel/')) {
          return true;
        }
        return false;
      } catch (_) {
        return false;
      }
    }

    // Helper to check if we should allow autoplay
    function shouldBlockAutoplay() {
      // If we're on reels page, never block
      if (isReelVideo()) return false;
      
      // If autoplay setting is false, don't block at all
      if (window.__fgBlockAutoplay === false) return false;
      
      // If session is active, don't block autoplay (allow all videos)
      if (window.__focusgramSessionActive === true) return false;
      
      // Otherwise block autoplay for feed/explore videos
      return true;
    }

    // Key to mark a video as explicitly started by user (permanent for that video instance)
    const ALLOW_KEY = '__fgUserExplicitlyPlayed';

    // Mark video as allowed permanently once user explicitly plays it
    function markAllow(video) {
      try {
        video[ALLOW_KEY] = true;
      } catch (_) {}
    }

    // Check if user has explicitly played this video
    function shouldAllow(video) {
      try {
        return video[ALLOW_KEY] === true;
      } catch (_) {
        return false;
      }
    }

    // Pause video and strip autoplay attribute (for blocked autoplay videos)
    function pauseAndFreezeVideo(video) {
      try {
        // Remove autoplay attribute completely
        video.removeAttribute('autoplay');
        try { video.autoplay = false; } catch (_) {}
        // Pause the video
        video.pause();
        // Reset to beginning
        video.currentTime = 0;
      } catch (_) {}
    }

    // Store original play and pause
    const _origPlay = HTMLVideoElement.prototype.play;
    const _origPause = HTMLVideoElement.prototype.pause;

    // Override play method
    if (HTMLVideoElement.prototype.play) {
      HTMLVideoElement.prototype.play = function() {
        try {
          // NEVER block reels - they always play normally
          if (isReelVideo()) {
            return _origPlay.apply(this, arguments);
          }
          
          // Check if we should block based on both settings and session
          if (!shouldBlockAutoplay()) {
            // Autoplay is OFF or session is active - allow all playback
            return _origPlay.apply(this, arguments);
          }
          
          // If user has explicitly played this video before, allow it to continue
          if (shouldAllow(this)) {
            return _origPlay.apply(this, arguments);
          }
          
          // Block autoplay: pause immediately and return resolved promise
          pauseAndFreezeVideo(this);
          return Promise.resolve();
        } catch (_) {
          // Fall back to original behaviour
          try {
            return _origPlay.apply(this, arguments);
          } catch (_) {
            return Promise.resolve();
          }
        }
      };
    }
    
    // Override pause method to work normally
    if (HTMLVideoElement.prototype.pause) {
      HTMLVideoElement.prototype.pause = function() {
        try {
          return _origPause.apply(this, arguments);
        } catch (_) {
          return Promise.resolve();
        }
      };
    }
    
    // Additional safeguard for dynamically created videos
    try {
      document.addEventListener('DOMContentLoaded', function() {
        document.querySelectorAll('video').forEach(function(v) {
          if (v.play) {
            const originalPlay = v.play;
            v.play = function() {
              // NEVER block reels
              if (isReelVideo()) {
                return originalPlay.apply(this, arguments);
              }
              if (!shouldBlockAutoplay()) {
                return originalPlay.apply(this, arguments);
              }
              if (shouldAllow(this)) {
                return originalPlay.apply(this, arguments);
              }
              pauseAndFreezeVideo(this);
              return Promise.resolve();
            };
          }
        });
      });
    } catch (_) {}
    
    // Also handle videos that might be created after DOMContentLoaded
    try {
      const originalCreateElement = document.createElement;
      document.createElement = function(tagName) {
        const element = originalCreateElement.apply(this, arguments);
        if (tagName.toLowerCase() === 'video') {
          // Intercept the play method on dynamically created videos
          const originalPlay = element.play;
          if (originalPlay) {
            element.play = function() {
              // NEVER block reels
              if (isReelVideo()) {
                return originalPlay.apply(this, arguments);
              }
              if (!shouldBlockAutoplay()) {
                return originalPlay.apply(this, arguments);
              }
              if (shouldAllow(this)) {
                return originalPlay.apply(this, arguments);
              }
              pauseAndFreezeVideo(this);
              return Promise.resolve();
            };
          }
        }
        return element;
      };
    } catch (_) {}
    
    // Mark video as allowed on user interaction (click/tap) - permanent for that video
    document.addEventListener('click', function(e) {
      try {
        const video = e.target.closest ? e.target.closest('video') : e.target;
        if (video) {
          // Mark this specific video as user-initiated - permanent
          markAllow(video);
          // Try to play the video if it was previously blocked
          if (shouldBlockAutoplay() && !shouldAllow(video)) {
            // Video will be allowed now, try to play
            try { video.play(); } catch (_) {}
          }
        }
      } catch (_) {}
    }, true);
    
    document.addEventListener('touchstart', function(e) {
      try {
        const video = e.target.closest ? e.target.closest('video') : e.target;
        if (video) {
          markAllow(video);
          if (shouldBlockAutoplay() && !shouldAllow(video)) {
            try { video.play(); } catch (_) {}
          }
        }
      } catch (_) {}
    }, true);
    
    // Also handle play events directly (for Instagram's internal play buttons)
    document.addEventListener('play', function(e) {
      if (e.target && e.target.tagName === 'VIDEO') {
        markAllow(e.target);
      }
    }, true);
  })();
''';
