/// Generates all CSS and JavaScript injection strings for the WebView.
///
/// Strategy:
/// - Instagram's own bottom nav bar is hidden via both CSS and a periodic JS
///   removal loop, since SPA re-renders can outpace MutationObserver.
/// - Reel elements are hidden/blurred based on settings/session state.
/// - A MutationObserver keeps re-applying the rules after SPA re-renders.
/// - App-install banners are auto-dismissed.
class InjectionController {
  /// iOS Safari user-agent — reduces login friction with Instagram.
  static const String iOSUserAgent =
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) '
      'AppleWebKit/605.1.15 (KHTML, like Gecko) '
      'Version/17.0 Mobile/15E148 Safari/604.1';

  // ── CSS injection ───────────────────────────────────────────────────────────

  /// Robust CSS that hides Instagram's native bottom nav bar.
  /// Covers all known selector patterns including dynamic class names.
  static const String _hideInstagramNavCSS = '''
    /* ── Instagram bottom navigation bar — hide completely ── */
    /* Role-based selectors */
    div[role="tablist"],
    nav[role="navigation"],
    /* Fixed-position bottom bar */
    div[style*="position: fixed"][style*="bottom"],
    div[style*="position:fixed"][style*="bottom"],
    /* Instagram legacy class names */
    ._acbl, ._aa4b, ._aahi, ._ab8s,
    /* Section nav elements */
    section nav,
    /* Any nav inside the main app shell */
    #react-root nav,
    /* The outer wrapper of the bottom bar (PWA/mobile web) */
    [class*="x1n2onr6"][class*="x1vjfegm"] > nav,
    /* Catch-all: any fixed bottom element containing nav links */
    footer nav,
    div[class*="bottom"] nav {
      display: none !important;
      visibility: hidden !important;
      height: 0 !important;
      overflow: hidden !important;
      pointer-events: none !important;
    }
    /* Ensure the body doesn't add bottom padding for the nav */
    body, #react-root, main {
      padding-bottom: 0 !important;
      margin-bottom: 0 !important;
    }
  ''';

  /// CSS to hide Reel-related elements everywhere (feed, profile, search).
  /// Used when session is NOT active.
  static const String _hideReelsCSS = '''
    /* Hide reel thumbnails and links */
    a[href*="/reel/"],
    a[href*="/reels"],
    [aria-label*="Reel"],
    [aria-label*="Reels"],
    div[data-media-type="2"],
    /* Profile grid reel filter tabs */
    [aria-label="Reels"],
    /* Reel indicators on feed thumbnails */
    svg[aria-label="Reels"],
    /* Video/reel chips in feed */
    [class*="reel"],
    [class*="Reel"] {
      display: none !important;
      visibility: hidden !important;
      pointer-events: none !important;
    }
  ''';

  /// CSS that adds bottom padding so feed content doesn't hide behind our bar.
  /// Added more selectors to cover dynamic drawers like Notes and Reactions.
  static const String _bottomPaddingCSS = '''
    body, #react-root > div, [role="presentation"] > div {
      padding-bottom: 72px !important;
    }
    /* Special handling for dynamic bottom drawers */
    div[style*="bottom: 0px"], div[style*="bottom: 0"] {
      padding-bottom: 72px !important;
    }
  ''';

  /// CSS to push IG content down so it doesn't hide behind our status bar.
  static const String _topPaddingCSS = '''
    header, #react-root > div > div > div:first-child {
      margin-top: 44px !important;
    }
    /* Shift fixed headers down */
    div[style*="position: fixed"][style*="top: 0"] {
       top: 44px !important;
    }
  ''';

  /// CSS to blur Explore feed posts/reels (keeps stories visible).
  static const String _blurExploreCSS = '''
    /* Blur Explore grid posts and reel cards (not stories row) */
    main[role="main"] section > div > div:not(:first-child) a img,
    main[role="main"] section > div > div:not(:first-child) video,
    main[role="main"] section > div > div:not(:first-child) [class*="x6s0dn4"],
    main[role="main"] article img,
    main[role="main"] article video,
    /* Explore page grid */
    ._aagv img,
    ._aagv video {
      filter: blur(12px) !important;
      pointer-events: none !important;
    }
    /* Overlay to block tapping blurred content */
    ._aagv::after {
      content: "";
      position: absolute;
      inset: 0;
      z-index: 99;
      cursor: not-allowed;
    }
    ._aagv {
      position: relative !important;
      overflow: hidden !important;
    }
  ''';

  /// Auto-dismiss "Open in App" banner that Instagram shows in mobile browsers.
  static const String _dismissAppBannerJS = '''
    (function dismissBanners() {
      const selectors = [
        '[id*="app-banner"]',
        '[class*="app-banner"]',
        '[data-testid*="app-banner"]',
        'div[role="dialog"][aria-label*="app"]',
        'div[role="dialog"][aria-label*="App"]',
      ];
      selectors.forEach(sel => {
        document.querySelectorAll(sel).forEach(el => el.remove());
      });
    })();
  ''';

  /// Periodic remover: every 500ms force-removes the bottom nav.
  /// Complements the MutationObserver for sites that rebuild DOM faster.
  static const String _periodicNavRemoverJS = '''
    (function periodicNavRemove() {
      function removeNav() {
        // Target all fixed-bottom elements that could be the nav bar
        document.querySelectorAll([
          'div[role="tablist"]',
          'nav[role="navigation"]',
          '._acbl', '._aa4b', '._aahi', '._ab8s',
          'section nav',
          'footer nav'
        ].join(',')).forEach(function(el) {
          el.style.cssText += ';display:none!important;height:0!important;overflow:hidden!important;';
        });
        // Also hide any element that is fixed at the bottom and contains nav links
        document.querySelectorAll('div[style]').forEach(function(el) {
          const s = el.style;
          if ((s.position === 'fixed' || s.position === 'sticky') &&
              (s.bottom === '0px' || s.bottom === '0') &&
              el.querySelector('a,button')) {
            el.style.cssText += ';display:none!important;';
          }
        });
      }
      removeNav();
      setInterval(removeNav, 500);
    })();
  ''';

  /// MutationObserver that continuously re-applies CSS after SPA re-renders.
  static String _buildMutationObserver(String cssContent) =>
      '''
    (function applyFocusGramStyles() {
      const STYLE_ID = 'focusgram-injected-style';

      function injectCSS() {
        let el = document.getElementById(STYLE_ID);
        if (!el) {
          el = document.createElement('style');
          el.id = STYLE_ID;
          document.head.appendChild(el);
        }
        el.textContent = ${_escapeJsString(cssContent)};
      }

      injectCSS();

      const observer = new MutationObserver(function() {
        if (!document.getElementById(STYLE_ID)) {
          injectCSS();
        }
      });

      observer.observe(document.documentElement, {
        childList: true,
        subtree: true,
      });
    })();
  ''';

  static String _escapeJsString(String s) {
    // Wrap in JS template literal backticks; escape any internal backticks.
    final escaped = s.replaceAll(r'\', r'\\').replaceAll('`', r'\`');
    return '`$escaped`';
  }

  // ── Navigation helpers ──────────────────────────────────────────────────────

  /// JS that soft-navigates Instagram's SPA without a full page reload.
  /// [path] should start with / e.g. '/direct/inbox/'.
  static String softNavigateJS(String path) =>
      '''
    (function() {
      const target = ${_escapeJsString(path)};
      // Try React Router / Instagram SPA navigation first (pushState trick)
      if (window.location.pathname !== target) {
        window.location.href = target;
      }
    })();
  ''';

  /// JS to click Instagram's native "create post" button.
  static const String clickCreateButtonJS = '''
    (function() {
      const btn = document.querySelector(
        '[aria-label="New post"], [aria-label="Create"], svg[aria-label="New post"]'
      );
      if (btn) {
        btn.closest('a, button') ? btn.closest('a, button').click() : btn.click();
      } else {
        // Fallback: navigate to home first, create will open as modal
        window.location.href = '/';
      }
    })();
  ''';

  /// JS to get the currently logged-in user's username.
  static const String getLoggedInUsernameJS = '''
    (function() {
      try {
        // Try shared data approach
        const scripts = Array.from(document.querySelectorAll('script[type="application/json"]'));
        for (const s of scripts) {
          try {
            const d = JSON.parse(s.textContent);
            if (d && d.config && d.config.viewer && d.config.viewer.username) {
              return d.config.viewer.username;
            }
          } catch(e){}
        }
        // Try window additionalDataLoaded
        if (window.__additionalDataLoaded) {
          const keys = Object.keys(window.__additionalDataLoaded || {});
          for (const k of keys) {
            const v = window.__additionalDataLoaded[k];
            if (v && v.data && v.data.user && v.data.user.username) {
              return v.data.user.username;
            }
          }
        }
        // Fallback: try profile anchor in nav
        const profileLink = document.querySelector('a[href][aria-label*="rofile"]');
        if (profileLink) {
          const href = profileLink.getAttribute('href');
          if (href) {
            const parts = href.replace(/^[/]/, "").split("/");
            if (parts[0] && parts[0].length > 0) return parts[0];
          }
        }
        return null;
      } catch(e) { return null; }
    })();
  ''';

  /// MutationObserver to watch for Reel players and lock their scrolling.
  static const String reelsMutationObserverJS = '''
    (function() {
      function lockReelScroll(reelContainer) {
        if (reelContainer.dataset.scrollLocked) return;
        reelContainer.dataset.scrollLocked = 'true';
        
        let startY = 0;

        reelContainer.addEventListener('touchstart', (e) => {
          startY = e.touches[0].clientY;
        }, { passive: true });

        reelContainer.addEventListener('touchmove', (e) => {
          const deltaY = e.touches[0].clientY - startY;
          // Block upward swipe (next reel), allow downward (go back)
          if (deltaY < -10) {
            if (e.cancelable) {
              e.preventDefault();
              e.stopPropagation();
            }
          }
        }, { passive: false });
      }

      // Watch for reel player being injected into DOM
      const observer = new MutationObserver(() => {
        // Instagram's reel player containers — multiple selectors for resilience
        const reelContainers = document.querySelectorAll(
          '[class*="reel"], [class*="Reel"], video'
        );
        reelContainers.forEach((el) => {
          // If it's a video or a reel container, wrap it
          lockReelScroll(el);
          // Also try parent if it's a video
          if (el.tagName === 'VIDEO' && el.parentElement) {
            lockReelScroll(el.parentElement);
          }
        });
      });

      observer.observe(document.body, { childList: true, subtree: true });
    })();
  ''';

  /// JS to disable swipe-to-next behavior inside the isolated Reel player.
  static const String disableReelSwipeJS = '''
    (function disableSwipeNavigation() {
      let startX = 0;
      document.addEventListener('touchstart', e => { startX = e.touches[0].clientX; }, {passive: true});
      document.addEventListener('touchmove', e => {
        const dx = Math.abs(e.touches[0].clientX - startX);
        if (dx > 30) e.preventDefault();
      }, {passive: false});
    })();
  ''';

  // ── Public API ──────────────────────────────────────────────────────────────

  /// Full injection JS to run on every page load.
  static String buildInjectionJS({
    required bool sessionActive,
    required bool blurExplore,
  }) {
    final StringBuffer css = StringBuffer();
    css.write(_hideInstagramNavCSS);
    css.write(_bottomPaddingCSS);
    css.write(_topPaddingCSS);
    if (!sessionActive) css.write(_hideReelsCSS);
    if (blurExplore) css.write(_blurExploreCSS);

    return '''
      ${_buildMutationObserver(css.toString())}
      $_periodicNavRemoverJS
      $_dismissAppBannerJS
    ''';
  }
}
