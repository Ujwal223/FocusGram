// UI element hiding for Instagram web.
//
// SCROLL LOCK WARNING:
// MutationObserver callbacks with {childList:true, subtree:true} fire hundreds
// of times/sec on Instagram's infinite scroll feed. Expensive querySelectorAll
// calls inside these callbacks block the main thread = scroll jank.
// All JS hiders below use a 300ms debounce so they run only after mutations settle.

// ─── CSS-based ────────────────────────────────────────────────────────────────

// FIX: Like count CSS.
// Instagram's like BUTTON has aria-label="Like" (the verb) — NOT the count.
// [role="button"][aria-label$=" likes"] never matches anything.
// The COUNT lives in a[href*="/liked_by/"] (e.g. "1,234 likes" link).
// We hide that link. The JS hider below catches React-rendered span variants.
const String kHideLikeCountsCSS = '''
  a[href*="/liked_by/"],
  section a[href*="/liked_by/"] {
    display: none !important;
  }
''';

const String kHideFollowerCountsCSS = '''
  a[href*="/followers/"] span,
  a[href*="/following/"] span {
    opacity: 0 !important;
    pointer-events: none !important;
  }
''';

// Stories bar CSS — multiple selectors for different Instagram DOM versions.
// :has() is supported in WebKit (Instagram's engine). Targets the container,
// not individual story items which is what [aria-label*="Stories"] matches.
const String kHideStoriesBarCSS = '''
  [aria-label*="Stories"],
  [aria-label*="stories"],
  [role="list"]:has([aria-label*="tory"]),
  [role="listbox"]:has([aria-label*="tory"]),
  div[style*="overflow"][style*="scroll"]:has(canvas),
  section > div > div[style*="overflow-x"] {
    display: none !important;
  }
''';

const String kHideExploreTabCSS = '''
  a[href="/explore/"],
  a[href="/explore"] {
    display: none !important;
  }
''';

const String kHideReelsTabCSS = '''
  a[href="/reels/"],
  a[href="/reels"] {
    display: none !important;
  }
''';

const String kHideShopTabCSS = '''
  a[href*="/shop"],
  a[href*="/shopping"] {
    display: none !important;
  }
''';

// ─── JS-based ─────────────────────────────────────────────────────────────────

// Like counts — JS fallback for React-rendered count spans not caught by CSS.
// Scans for text matching "1,234 likes" / "12.3K views" patterns.
const String kHideLikeCountsJS = r'''
(function() {
  function hideLikeCounts() {
    try {
      // Hide liked_by links and their immediate parent wrapper
      document.querySelectorAll('a[href*="/liked_by/"]').forEach(function(el) {
        try {
          el.style.setProperty('display', 'none', 'important');
          // Also hide the parent span/div that wraps the count text
          if (el.parentElement) {
            el.parentElement.style.setProperty('display', 'none', 'important');
          }
        } catch(_) {}
      });
      // Scan spans for numeric like/view count text patterns
      document.querySelectorAll('span').forEach(function(el) {
        try {
          const text = el.textContent.trim();
          // Matches: "1,234 likes", "12.3K views", "1 like", "45 views", etc.
          if (/^[\d,.]+[KkMm]?\s+(like|likes|view|views)$/.test(text)) {
            el.style.setProperty('display', 'none', 'important');
          }
        } catch(_) {}
      });
    } catch(_) {}
  }

  hideLikeCounts();

  if (!window.__fgLikeCountObserver) {
    let _t = null;
    window.__fgLikeCountObserver = new MutationObserver(() => {
      clearTimeout(_t);
      _t = setTimeout(hideLikeCounts, 300);
    });
    window.__fgLikeCountObserver.observe(
      document.documentElement, { childList: true, subtree: true }
    );
  }
})();
''';

// Stories bar JS — structural detection when CSS selectors don't match.
// Two strategies:
// 1. aria-label scan on role=list/listbox elements
// 2. BoundingClientRect check: story circles are square, narrow (<120px), appear in a row
const String kHideStoriesBarJS = r'''
(function() {
  function hideStories() {
    try {
      // Strategy 1: aria-label on list containers
      document.querySelectorAll('[role="list"], [role="listbox"]').forEach(function(el) {
        try {
          const label = (el.getAttribute('aria-label') || '').toLowerCase();
          if (label.includes('stor')) {
            el.style.setProperty('display', 'none', 'important');
          }
        } catch(_) {}
      });

      // Strategy 2: BoundingClientRect — story circles are narrow square items in a row.
      // Look for a <ul> or <div role=list> whose first child is roughly square and < 120px wide.
      document.querySelectorAll('ul, [role="list"]').forEach(function(el) {
        try {
          const items = el.children;
          if (items.length < 3) return;
          const first = items[0].getBoundingClientRect();
          // Story item: small, roughly square (width ≈ height), near top of viewport
          if (
            first.width > 0 &&
            first.width < 120 &&
            Math.abs(first.width - first.height) < 20 &&
            first.top < 300
          ) {
            el.style.setProperty('display', 'none', 'important');
            // Also hide the section wrapping this if it has no article (pure stories row)
            const section = el.closest('section, div[class]');
            if (section && !section.querySelector('article')) {
              section.style.setProperty('display', 'none', 'important');
            }
          }
        } catch(_) {}
      });

      // Strategy 3: horizontal overflow container before any article in the feed
      document.querySelectorAll('main > div > div > div').forEach(function(container) {
        try {
          if (container.querySelector('article')) return;
          const inner = container.querySelector('div, ul');
          if (!inner) return;
          const s = window.getComputedStyle(inner);
          if (s.overflowX === 'scroll' || s.overflowX === 'auto') {
            container.style.setProperty('display', 'none', 'important');
          }
        } catch(_) {}
      });
    } catch(_) {}
  }

  hideStories();

  if (!window.__fgStoriesObserver) {
    let _t = null;
    window.__fgStoriesObserver = new MutationObserver(() => {
      clearTimeout(_t);
      _t = setTimeout(hideStories, 300);
    });
    window.__fgStoriesObserver.observe(
      document.documentElement, { childList: true, subtree: true }
    );
  }
})();
''';

// Sponsored posts — scans article elements for "Sponsored" text child.
// CSS cannot traverse from child text up to parent — JS only.
const String kHideSponsoredPostsJS = r'''
(function() {
  function hideSponsoredPosts() {
    try {
      document.querySelectorAll('article, li[role="listitem"]').forEach(function(el) {
        try {
          if (el.__fgSponsoredChecked) return;
          const spans = el.querySelectorAll('span');
          for (let i = 0; i < spans.length; i++) {
            const text = spans[i].textContent.trim();
            if (text === 'Sponsored' || text === 'Paid partnership') {
              el.style.setProperty('display', 'none', 'important');
              return;
            }
          }
          el.__fgSponsoredChecked = true;
        } catch(_) {}
      });
    } catch(_) {}
  }

  hideSponsoredPosts();

  if (!window.__fgSponsoredObserver) {
    let _t = null;
    window.__fgSponsoredObserver = new MutationObserver(() => {
      clearTimeout(_t);
      _t = setTimeout(hideSponsoredPosts, 300);
    });
    window.__fgSponsoredObserver.observe(
      document.documentElement, { childList: true, subtree: true }
    );
  }
})();
''';

// Suggested posts — scans for heading text, walks up to parent article/section.
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
    let _t = null;
    window.__fgSuggestedObserver = new MutationObserver(() => {
      clearTimeout(_t);
      _t = setTimeout(hideSuggestedPosts, 300);
    });
    window.__fgSuggestedObserver.observe(
      document.documentElement, { childList: true, subtree: true }
    );
  }
})();
''';
