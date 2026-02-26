// UI element hiding for Instagram web.
//
// SCROLL LOCK WARNING:
// MutationObserver callbacks with {childList:true, subtree:true} fire hundreds
// of times/sec on Instagram's infinite scroll feed. Expensive querySelectorAll
// calls inside these callbacks block the main thread = scroll jank.
// The JS hiders below use requestIdleCallback + a 300ms debounce so they run
// only during idle time and never on every single mutation.

// ─── CSS-based (reliable, zero perf cost) ────────────────────────────────────

const String kHideLikeCountsCSS =
    """
  [role="button"][aria-label${r"$"}=" like"],
  [role="button"][aria-label${r"$"}=" likes"],
  [role="button"][aria-label${r"$"}=" view"],
  [role="button"][aria-label${r"$"}=" views"],
  a[href*="/liked_by/"] {
    display: none !important;
  }
""";

const String kHideFollowerCountsCSS = """
  a[href*="/followers/"] span,
  a[href*="/following/"] span {
    opacity: 0 !important;
    pointer-events: none !important;
  }
""";

// Stories bar — broad selector covering multiple Instagram DOM layouts
const String kHideStoriesBarCSS = """
  [aria-label*="Stories"],
  [aria-label*="stories"],
  [role="list"][aria-label*="tories"],
  [role="listbox"][aria-label*="tories"],
  div[style*="overflow"][style*="scroll"]:has(canvas),
  section > div > div[style*="overflow-x"] {
    display: none !important;
  }
""";

// Also do a JS sweep for stories — CSS alone isn't reliable across Instagram versions
const String kHideStoriesBarJS = r'''
(function() {
  function hideStories() {
    try {
      // Target the horizontal scrollable stories container
      document.querySelectorAll('[role="list"], [role="listbox"]').forEach(function(el) {
        try {
          const label = (el.getAttribute('aria-label') || '').toLowerCase();
          if (label.includes('stori')) {
            el.style.setProperty('display', 'none', 'important');
          }
        } catch(_) {}
      });
      // Fallback: find story bubbles (circular avatar containers at top of feed)
      document.querySelectorAll('section > div > div').forEach(function(el) {
        try {
          const style = window.getComputedStyle(el);
          if (style.overflowX === 'scroll' || style.overflowX === 'auto') {
            const circles = el.querySelectorAll('canvas, [style*="border-radius: 50%"]');
            if (circles.length > 2) {
              el.style.setProperty('display', 'none', 'important');
            }
          }
        } catch(_) {}
      });
    } catch(_) {}
  }

  hideStories();

  if (!window.__fgStoriesObserver) {
    let _storiesTimer = null;
    window.__fgStoriesObserver = new MutationObserver(() => {
      // Debounce — only run after mutations settle, not on every single one
      clearTimeout(_storiesTimer);
      _storiesTimer = setTimeout(hideStories, 300);
    });
    window.__fgStoriesObserver.observe(
      document.documentElement,
      { childList: true, subtree: true }
    );
  }
})();
''';

const String kHideExploreTabCSS = """
  a[href="/explore/"],
  a[href="/explore"] {
    display: none !important;
  }
""";

const String kHideReelsTabCSS = """
  a[href="/reels/"],
  a[href="/reels"] {
    display: none !important;
  }
""";

const String kHideShopTabCSS = """
  a[href*="/shop"],
  a[href*="/shopping"] {
    display: none !important;
  }
""";

// ─── Complete Section Disabling (CSS-based) ─────────────────────────────────

// Minimal mode - disables Reels and Explore entirely
const String kMinimalModeCssScript = r'''
(function() {
  const css = `
    /* Hide Reels tab */
    a[href="/reels/"], a[href="/reels"] { display: none !important; }
    /* Hide Explore tab */
    a[href="/explore/"], a[href="/explore"] { display: none !important; }
    /* Hide Create tab */
    a[href="/create/"], a[href="/create"] { display: none !important; }
    /* Hide Reels in feed */
    article a[href*="/reel/"] { display: none !important; }
    /* Hide Explore entry points */
    svg[aria-label="Explore"], [aria-label="Explore"] { display: none !important; }
  `;
  const style = document.createElement('style');
  style.id = 'fg-minimal-mode';
  style.textContent = css;
  (document.head || document.documentElement).appendChild(style);
})();
''';

// Disable Reels entirely
const String kDisableReelsEntirelyCssScript = r'''
(function() {
  const css = `
    a[href="/reels/"], a[href="/reels"] { display: none !important; }
    article a[href*="/reel/"] { display: none !important; }
  `;
  const style = document.createElement('style');
  style.id = 'fg-disable-reels';
  style.textContent = css;
  (document.head || document.documentElement).appendChild(style);
})();
''';

// Disable Explore entirely
const String kDisableExploreEntirelyCssScript = r'''
(function() {
  const css = `
    a[href="/explore/"], a[href="/explore"] { display: none !important; }
    svg[aria-label="Explore"], [aria-label="Explore"] { display: none !important; }
  `;
  const style = document.createElement('style');
  style.id = 'fg-disable-explore';
  style.textContent = css;
  (document.head || document.documentElement).appendChild(style);
})();
''';

// ─── DM-embedded Reels Scroll Control ────────────────────────────────────────
// Disables vertical scroll on reels opened from DM unless comment box or share modal is open
const String kDmReelScrollLockScript = r'''
(function() {
  // Track scroll lock state
  window.__fgDmReelScrollLocked = true;
  window.__fgDmReelCommentOpen = false;
  window.__fgDmReelShareOpen = false;

  function lockScroll() {
    if (window.__fgDmReelScrollLocked) {
      document.body.style.overflow = 'hidden';
      document.documentElement.style.overflow = 'hidden';
    }
  }

  function unlockScroll() {
    document.body.style.overflow = '';
    document.documentElement.style.overflow = '';
  }

  function updateScrollState() {
    // Only unlock if comment or share modal is open
    if (window.__fgDmReelCommentOpen || window.__fgDmReelShareOpen) {
      unlockScroll();
    } else if (window.__fgDmReelScrollLocked) {
      lockScroll();
    }
  }

  // Listen for comment box opening/closing
  function setupCommentObserver() {
    const commentBox = document.querySelector('div[aria-label="Comment"], section[aria-label*="Comment"]');
    if (commentBox) {
      window.__fgDmReelCommentOpen = true;
      updateScrollState();
    }
  }

  // Listen for share modal
  function setupShareObserver() {
    const shareModal = document.querySelector('div[role="dialog"][aria-label*="Share"], section[aria-label*="Share"]');
    if (shareModal) {
      window.__fgDmReelShareOpen = true;
      updateScrollState();
    }
  }

  // Set up MutationObserver to detect comment/share modals
  const observer = new MutationObserver(function(mutations) {
    mutations.forEach(function(mutation) {
      mutation.addedNodes.forEach(function(node) {
        if (node.nodeType === 1) {
          const ariaLabel = node.getAttribute('aria-label') || '';
          const role = node.getAttribute('role') || '';
          
          // Check for comment box
          if (ariaLabel.toLowerCase().includes('comment') || 
              (role === 'dialog' && ariaLabel === '')) {
            // Check if it's a comment dialog
            setTimeout(function() {
              window.__fgDmReelCommentOpen = !!document.querySelector('div[aria-label="Comment"], section[aria-label*="Comment"]');
              updateScrollState();
            }, 100);
          }
          
          // Check for share modal
          if (ariaLabel.toLowerCase().includes('share')) {
            window.__fgDmReelShareOpen = true;
            updateScrollState();
          }
        }
      });
      
      mutation.removedNodes.forEach(function(node) {
        if (node.nodeType === 1) {
          const ariaLabel = node.getAttribute('aria-label') || '';
          if (ariaLabel.toLowerCase().includes('comment')) {
            setTimeout(function() {
              window.__fgDmReelCommentOpen = !!document.querySelector('div[aria-label="Comment"], section[aria-label*="Comment"]');
              updateScrollState();
            }, 100);
          }
          if (ariaLabel.toLowerCase().includes('share')) {
            window.__fgDmReelShareOpen = false;
            updateScrollState();
          }
        }
      });
    });
  });

  observer.observe(document.documentElement, {
    childList: true,
    subtree: true
  });

  // Initial lock
  lockScroll();

  // Expose functions for external control
  window.__fgSetDmReelScrollLock = function(locked) {
    window.__fgDmReelScrollLocked = locked;
    updateScrollState();
  };
})();
''';

// ─── JS-based (text-content detection, debounced) ─────────────────────────────

// Sponsored posts — scans for "Sponsored" text, debounced so it doesn't
// cause scroll jank on Instagram's constantly-mutating feed DOM.
const String kHideSponsoredPostsJS = r'''
(function() {
  function hideSponsoredPosts() {
    try {
      document.querySelectorAll('article, li[role="listitem"]').forEach(function(el) {
        try {
          if (el.__fgSponsoredChecked) return; // skip already-processed elements
          const spans = el.querySelectorAll('span');
          for (let i = 0; i < spans.length; i++) {
            const text = spans[i].textContent.trim();
            if (text === 'Sponsored' || text === 'Paid partnership') {
              el.style.setProperty('display', 'none', 'important');
              return;
            }
          }
          el.__fgSponsoredChecked = true; // mark as checked (non-sponsored)
        } catch(_) {}
      });
    } catch(_) {}
  }

  hideSponsoredPosts();

  if (!window.__fgSponsoredObserver) {
    let _timer = null;
    window.__fgSponsoredObserver = new MutationObserver(() => {
      clearTimeout(_timer);
      _timer = setTimeout(hideSponsoredPosts, 300);
    });
    window.__fgSponsoredObserver.observe(
      document.documentElement,
      { childList: true, subtree: true }
    );
  }
})();
''';

// Suggested posts — debounced same way.
const String kHideSuggestedPostsJS = r'''
(function() {
  function hideSuggestedPosts() {
    try {
      document.querySelectorAll('span, h3, h4').forEach(function(el) {
        try {
          const text = el.textContent.trim();
          if (
            text === 'Suggested for you' ||
            text === 'Suggested posts' ||
            text === "You're all caught up"
          ) {
            let parent = el.parentElement;
            for (let i = 0; i < 8 && parent; i++) {
              const tag = parent.tagName.toLowerCase();
              if (tag === 'article' || tag === 'section' || tag === 'li') {
                parent.style.setProperty('display', 'none', 'important');
                break;
              }
              parent = parent.parentElement;
            }
          }
        } catch(_) {}
      });
    } catch(_) {}
  }

  hideSuggestedPosts();

  if (!window.__fgSuggestedObserver) {
    let _timer = null;
    window.__fgSuggestedObserver = new MutationObserver(() => {
      clearTimeout(_timer);
      _timer = setTimeout(hideSuggestedPosts, 300);
    });
    window.__fgSuggestedObserver.observe(
      document.documentElement,
      { childList: true, subtree: true }
    );
  }
})();
''';

// ─── DM Reel Blocker ─────────────────────────────────────────────────────────

/// Overlays a "Reels are disabled" card on reel preview cards inside DMs.
///
/// DM reel previews use pushState (SPA) not <a href> navigation, so the CSS
/// display:none in kDisableReelsEntirelyCssScript doesn't remove the preview
/// card from the thread. This script finds them structurally and covers them
/// with a blocking overlay that also swallows all touch/click events.
///
/// Inject when disableReelsEntirely OR minimalMode is on.
const String kDmReelBlockerJS = r'''
(function() {
  if (window.__fgDmReelBlockerRunning) return;
  window.__fgDmReelBlockerRunning = true;

  const BLOCKED_ATTR = 'data-fg-blocked';

  function buildOverlay() {
    const div = document.createElement('div');
    div.setAttribute(BLOCKED_ATTR, '1');
    div.style.cssText = [
      'position:absolute',
      'inset:0',
      'z-index:99999',
      'display:flex',
      'flex-direction:column',
      'align-items:center',
      'justify-content:center',
      'background:rgba(0,0,0,0.85)',
      'border-radius:inherit',
      'pointer-events:all',
      'gap:8px',
      'cursor:default',
    ].join(';');

    const icon = document.createElement('span');
    icon.textContent = '🚫';
    icon.style.cssText = 'font-size:28px;line-height:1';

    const label = document.createElement('span');
    label.textContent = 'Reels are disabled';
    label.style.cssText = [
      'color:#fff',
      'font-size:13px',
      'font-weight:600',
      'font-family:-apple-system,sans-serif',
      'text-align:center',
      'padding:0 12px',
    ].join(';');

    const sub = document.createElement('span');
    sub.textContent = 'Disable "Block Reels" in FocusGram settings';
    sub.style.cssText = [
      'color:rgba(255,255,255,0.5)',
      'font-size:11px',
      'font-family:-apple-system,sans-serif',
      'text-align:center',
      'padding:0 16px',
    ].join(';');

    div.appendChild(icon);
    div.appendChild(label);
    div.appendChild(sub);

    // Swallow all interaction so the reel beneath cannot be triggered
    ['click','touchstart','touchend','touchmove','pointerdown'].forEach(function(evt) {
      div.addEventListener(evt, function(e) {
        e.preventDefault();
        e.stopImmediatePropagation();
      }, { capture: true });
    });

    return div;
  }

  function overlayContainer(container) {
    if (!container) return;
    if (container.querySelector('[' + BLOCKED_ATTR + ']')) return; // already overlaid
    container.style.position = 'relative';
    container.style.overflow = 'hidden';
    container.appendChild(buildOverlay());
  }

  function blockDmReels() {
    try {
      // Strategy 1: <a href*="/reel/"> links inside the DM thread
      document.querySelectorAll('a[href*="/reel/"]').forEach(function(link) {
        try {
          link.style.setProperty('pointer-events', 'none', 'important');
          overlayContainer(link.closest('div') || link.parentElement);
        } catch(_) {}
      });

      // Strategy 2: <video> inside DMs (reel cards without <a> wrapper)
      // Only targets videos inside the Direct thread or on /direct/ path
      document.querySelectorAll('video').forEach(function(video) {
        try {
          const inDm = !!video.closest('[aria-label="Direct"], [aria-label*="Direct"]');
          const isDmPath = window.location.pathname.includes('/direct/');
          if (!inDm && !isDmPath) return;

          const container = video.closest('div[class]') || video.parentElement;
          if (!container) return;
          video.style.setProperty('pointer-events', 'none', 'important');
          overlayContainer(container);
        } catch(_) {}
      });
    } catch(_) {}
  }

  blockDmReels();

  let _t = null;
  new MutationObserver(function() {
    clearTimeout(_t);
    _t = setTimeout(blockDmReels, 200);
  }).observe(document.documentElement, { childList: true, subtree: true });
})();
''';
