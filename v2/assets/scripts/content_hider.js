/**
 * FocusGram Content Hider
 * Toggleable visibility for: stories tray, feed posts, reels, suggested content.
 * Flutter controls via window.__fgContent.*
 * Injected at DOCUMENT_END.
 *
 * Key fixes applied:
 * - Blank-feed fix: hideReels uses DOM removal (not display:none) so layout doesn't collapse
 * - MutationObserver callback now re-applies CSS AND re-runs all hide functions each cycle
 * - SPA-heartbeat via window event listener re-applies CSS on pushState/replaceState
 * - Stories tray detection strengthened for fresh SPA navigations
 * - Suggested posts detection uses multiple text-node matching strategies
 */
(function () {
  'use strict';

  if (window.__fgContent && window.__fgContent.__focusgramReady) {
    return;
  }

  const STYLE_ID = 'fg-content-hider';
  let hideStories = false;
  let hidePosts   = false;
  let hideSuggested = false;
  let hideReels   = false;

  // ─── CSS rules ─────────────────────────────────────────────────────────────

  function buildCSS() {
    const selectors = [];

    if (hideStories) {
      selectors.push(
        '[role="list"]:has([aria-label*="tory"])',
        '[role="listbox"]:has([aria-label*="tory"])',
        '[role="menu"] > ul',
        'section > div > div:first-child [style*="overflow"]',
        '[role="list"] [style*="overflow"]',
      );
    }

    if (hidePosts) {
      selectors.push(
        'body:not([path*="direct"]):not([data-fg-path*="direct"]) article:not([aria-label])',
        'body:not([path*="direct"]):not([data-fg-path*="direct"]) [data-pressable-container] > article',
      );
    }

    // hideReels CSS is intentionally NOT added here.
    // We use DOM removal instead (see removeReels()) so that room is never left
    // blank in the feed, and Instagram's infinite-scroll can prove scroll height.

    return selectors.length
      ? selectors.join(',\n') + ' { display: none !important; visibility: hidden !important; }'
      : '';
  }

  function applyCSS() {
    if (document.body) {
      document.body.setAttribute('data-fg-path', window.location.pathname || '/');
    }
    let style = document.getElementById(STYLE_ID);
    if (!style) {
      style = document.createElement('style');
      style.id = STYLE_ID;
      document.head.appendChild(style);
    }
    style.textContent = buildCSS();
  }

  // ─── Story tray JS ─────────────────────────────────────────────────────────

  function hideStoryTray() {
    if (!hideStories) return;

    // Strategy 1: <ul> children of a named list or menu
    document.querySelectorAll('[role="list"] ul, [role="menu"] ul').forEach(function (ul) {
      try {
        const items = ul.querySelectorAll('li, button, a');
        if (items.length < 2) return;
        ul.style.setProperty('display', 'none', 'important');
      } catch (_) {}
    });

    // Strategy 2: horizontally scrolling container with circle items
    document.querySelectorAll('[style*="overflow"], [style*="overflow-x"]').forEach(function (c) {
      try {
        if ('' === (window.getComputedStyle(c).overflow + '').replace(/none/g, '')) return;
        const cands = c.querySelectorAll('li, div[class*="story"], [class*="story"]');
        if (cands.length < 2) return;
        const s0 = window.getComputedStyle(cands[0]);
        if (s0.width && parseFloat(s0.width) <= 90) {
          c.parentElement && (c.parentElement.style.setProperty('display', 'none', 'important'));
        }
      } catch (_) {}
    });
  }

  // ─── Suggested posts ───────────────────────────────────────────────────────

  function removeSuggested() {
    if (!hideSuggested) return;

    var SIGNALS = [
      'suggested for you',
      'suggested posts',
      'suggested reels',
      'suggested',
      'because you watched',
      'because you follow',
      'you might like',
      'posts you might like',
      'accounts you might like',
      'recommendations',
    ];

    function norm(s) {
      return (s || '').replace(/\s+/g, ' ').trim().toLowerCase();
    }

    function hasSignal(s) {
      var t = norm(s);
      if (!t) return false;
      return SIGNALS.some(function (signal) {
        if (signal === 'suggested') return t === signal;
        return t.indexOf(signal) >= 0;
      });
    }

    function hideContainer(from) {
      var parent = from;
      for (var depth = 0; depth < 10 && parent && parent !== document.body; depth++) {
        var role = parent.getAttribute && parent.getAttribute('role');
        var tag = parent.tagName;
        var hasMedia = parent.querySelector && parent.querySelector('img,video,a[href*="/p/"],a[href*="/reel/"]');
        if (
          tag === 'ARTICLE' ||
          tag === 'SECTION' ||
          role === 'listitem' ||
          (hasMedia && parent.getBoundingClientRect && parent.getBoundingClientRect().height > 120)
        ) {
          parent.style.setProperty('display', 'none', 'important');
          parent.setAttribute('data-fg-hidden-suggested', '1');
          return true;
        }
        parent = parent.parentElement;
      }
      return false;
    }

    document.querySelectorAll('article, section, [role="listitem"]').forEach(function (node) {
      try {
        if (node.getAttribute('data-fg-hidden-suggested') === '1') return;
        var ownLabel = node.getAttribute('aria-label');
        if (hasSignal(ownLabel)) { hideContainer(node); return; }
        var text = norm(node.innerText || node.textContent || '');
        if (
          text.indexOf('suggested for you') >= 0 ||
          text.indexOf('suggested posts') >= 0 ||
          text.indexOf('suggested reels') >= 0 ||
          text.indexOf('because you watched') >= 0 ||
          text.indexOf('because you follow') >= 0
        ) {
          hideContainer(node);
        }
      } catch (_) {}
    });

    document.querySelectorAll('span, h1, h2, h3, h4, div[aria-label], a[aria-label]').forEach(function (el) {
      try {
        if (hasSignal(el.textContent) || hasSignal(el.getAttribute('aria-label'))) {
          hideContainer(el);
        }
      } catch (_) {}
    });
  }

  // ─── Reels – DOM REMOVE (not display:none) ─────────────────────────────────
  // display:none keeps the element in the DOM, so Instagram's virtual-scroll still
  // reserves the slot → blank gaps. Removing the article from the DOM collapses the
  // gap cleanly and lets the feed flow naturally.
  function removeReels() {
    if (!hideReels) return;

    var toRemove = [];
    document.querySelectorAll('article').forEach(function (el) {
      try {
        // Fast path: check for a reel-signal attribute first
        var mt = (el.getAttribute('data-media-type') || el.dataset && el.dataset.mediaType || '').trim();
        if (mt === '2') { toRemove.push(el); return; }

        // Fallback: text-node scan for /reels/ markers
        var walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
        var n;
        while ((n = walker.nextNode())) {
          if (n.nodeValue.indexOf('/reels/') >= 0 || n.nodeValue.indexOf('/reel/') >= 0) {
            toRemove.push(el); break;
          }
        }
      } catch (_) {}
    });

    toRemove.forEach(function (el) { try { el.remove(); } catch (_) {} });
  }

  // ─── Public API ────────────────────────────────────────────────────────────

  window.__fgContent = {
    __focusgramReady: true,
    setHideStories: function (val) { hideStories = !!val; applyCSS(); hideStoryTray(); },
    setHidePosts:   function (val) { hidePosts   = !!val; applyCSS(); },
    setHideSuggested: function (val) {
      hideSuggested = !!val;
      applyCSS();
      if (val) removeSuggested();
    },
    setHideReels: function (val) {
      hideReels = !!val;
      applyCSS();
      if (val) removeReels();
    },
    applyAll: function (flags) {
      hideStories   = !!flags.stories;
      hidePosts     = !!flags.posts;
      hideReels     = !!flags.reels;
      hideSuggested = !!flags.suggested;
      applyCSS();
      if (hideSuggested) removeSuggested();
      if (hideStories)   hideStoryTray();
      if (hideReels)     removeReels();
    },
  };

  // ─── SPA heartbeat ─────────────────────────────────────────────────────────
  // pushState/replaceState don't fire any DOM event we can listen for.
  // Hook the methods themselves so we know a navigation happened, then debounce
  // re-apply. This also catches the case where the MutationObserver was on `body`
  // and that node got replaced by Instagram's SPA re-render.

  function scheduleReapply() {
    clearTimeout(window.__fg_applyTimer);
    window.__fg_applyTimer = setTimeout(function () {
      applyCSS();
      if (hideStories)   hideStoryTray();
      if (hideSuggested) removeSuggested();
      if (hideReels)     removeReels();
    }, 250);
  }

  var _origPush    = history.pushState;
  var _origReplace = history.replaceState;

  history.pushState = function () {
    _origPush.apply(this, arguments);
    scheduleReapply();
  };

  history.replaceState = function () {
    _origReplace.apply(this, arguments);
    scheduleReapply();
  };

  // Reinforce on popstate too (user hits back/forward)
  window.addEventListener('popstate', scheduleReapply, { passive: true });
  // For pushState on the same URL (rare but possible) – poll path briefly
  window.addEventListener('pageshow',  scheduleReapply, { passive: true });
  window.addEventListener('focus',     scheduleReapply, { passive: true });

  // ─── MutationObserver ───────────────────────────────────────────────────────
  // Monitors for dynamic DOM changes (new rows, lazy-loaded articles) and
  // re-applies everything on each cycle.  Does NOT guard on a per-element timer
  // that would never re-fire after the body is replaced by SPA re-render.

  if (!window.__fgContentObserver) {
    window.__fgContentObserver = new MutationObserver(function () {
      clearTimeout(window.__fg_moTimer);
      window.__fg_moTimer = setTimeout(function () {
        applyCSS();
        if (hideStories)   hideStoryTray();
        if (hideSuggested) removeSuggested();
        if (hideReels)     removeReels();
      }, 300);
    });

    // `document.documentElement` survives SPA navigations (body gets replaced
    // but <html> stays). Observing it catches both subtree mutations and, via
    // the SPA heartbeat above, re-applies after pushState.
    window.__fgContentObserver.observe(document.documentElement, {
      childList:  true,
      subtree:    true,
    });
  }

  // ─── Initial run ────────────────────────────────────────────────────────────
  applyCSS();
  if (hideStories)   hideStoryTray();
  if (hideSuggested) removeSuggested();
  if (hideReels)     removeReels();

  // Signal ready — Flutter will call applyAll() with stored prefs
  if (window.ContentChannel) {
    window.ContentChannel.postMessage(JSON.stringify({ type: 'ready' }));
  }
})();
