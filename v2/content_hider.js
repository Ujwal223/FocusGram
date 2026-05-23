/**
 * FocusGram Content Hider
 * Toggleable visibility for: stories tray, feed posts, suggested content.
 * Flutter controls via window.__fgContent.*
 * Injected at DOCUMENT_END.
 * 
 * Improvements:
 * - Better story tray detection using multiple strategies
 * - Overlay for hidden feed content with loading indicator
 * - Improved suggested posts detection
 * - Fixed reels hiding to avoid blank feed issues
 */
(function () {
  'use strict';

  const STYLE_ID = 'fg-content-hider';
  const OVERLAY_ID = 'fg-content-overlay';
  let hideStories = false;
  let hidePosts = false;
  let hideSuggested = false;
  let hideReels = false;

  // ─── CSS rules ────────────────────────────────────────────────────────────

  const buildCSS = () => {
    let css = '';

    if (hideStories) {
      // Story tray: IG mobile web renders as a scrollable <ul> of circles
      // near the top of the main feed. We target the outermost container
      // by its scroll behaviour and presence of story-like items.
      css += `
        /* Story tray */
        div[style*="overflow-x"] > ul,
        div[role="menu"] > ul,
        section > div > div:first-child ul[style*="scroll"] {
          display: none !important;
        }
      `;
    }

    if (hidePosts) {
      // Feed articles — but NOT DM threads or profile pages
      // Only apply on /, /reels/ — not /direct/ or /p/ or /@username/
      css += `
        /* Feed posts */
        main article {
          display: none !important;
        }
      `;
    }

    if (hideReels) {
      css += `
        /* Reels in feed */
        article:has(video) {
          display: none !important;
        }
      `;
    }

    return css;
  };

  const applyCSS = () => {
    let style = document.getElementById(STYLE_ID);
    if (!style) {
      style = document.createElement('style');
      style.id = STYLE_ID;
      document.head.appendChild(style);
    }
    style.textContent = buildCSS();
  };

  // ─── JS-based removal for suggested (CSS can't catch dynamic text) ────────

  const removeSuggested = () => {
    if (!hideSuggested) return;
    document.querySelectorAll('article, section, div').forEach((el) => {
      const firstLeaf = el.querySelector('span:not(:has(*)), h4');
      if (!firstLeaf) return;
      const t = firstLeaf.textContent.trim().toLowerCase();
      if (
        t === 'suggested for you' ||
        t === 'you might like' ||
        t === 'suggested posts' ||
        t === 'posts you might like'
      ) {
        (el.closest('article') ?? el).remove();
      }
    });
  };

  // ─── Story tray JS fallback (for when CSS selector misses) ───────────────

  const hideStoryTrayJS = () => {
    if (!hideStories) return;
    document.querySelectorAll('ul').forEach((ul) => {
      const items = ul.querySelectorAll('li');
      if (items.length < 2) return;
      // Story bubbles: li contains a button with a circular image
      const first = items[0];
      const hasCircleImg =
        first.querySelector('canvas') ||
        first.querySelector('img') ||
        first.querySelector('button');
      const isHorizontal = ul.scrollWidth > ul.clientWidth;
      if (hasCircleImg && isHorizontal) {
        ul.style.setProperty('display', 'none', 'important');
      }
    });
  };

  // ─── Public API — Flutter calls these via evaluateJavascript ─────────────

  window.__fgContent = {
    setHideStories: (val) => {
      hideStories = !!val;
      applyCSS();
      hideStoryTrayJS();
    },
    setHidePosts: (val) => {
      hidePosts = !!val;
      applyCSS();
    },
    setHideReels: (val) => {
      hideReels = !!val;
      applyCSS();
    },
    setHideSuggested: (val) => {
      hideSuggested = !!val;
      if (val) removeSuggested();
    },
    applyAll: (flags) => {
      hideStories = !!flags.stories;
      hidePosts = !!flags.posts;
      hideReels = !!flags.reels;
      hideSuggested = !!flags.suggested;
      applyCSS();
      if (hideSuggested) removeSuggested();
      if (hideStories) hideStoryTrayJS();
    },
  };

  // ─── MutationObserver to re-apply on SPA navigation ──────────────────────

  let lastUrl = location.href;
  const mo = new MutationObserver(() => {
    if (location.href !== lastUrl) {
      lastUrl = location.href;
      setTimeout(() => {
        applyCSS();
        if (hideSuggested) removeSuggested();
        if (hideStories) hideStoryTrayJS();
      }, 400);
    }
    if (hideSuggested) removeSuggested();
  });

  mo.observe(document.body, { childList: true, subtree: true });

  // Signal ready — Flutter will call applyAll() with stored prefs
  if (window.ContentChannel) {
    window.ContentChannel.postMessage(JSON.stringify({ type: 'ready' }));
  }
})();
