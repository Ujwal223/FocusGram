/// Controller for injecting custom JS and CSS into the WebView.
/// Uses a combination of static strings and dynamic builders to:
/// - Hide native navigation elements.
/// - Inject FocusGram branding into the native header.
/// - Implement "Ghost Mode" (stealth features).
/// - Manage Reels/Explore distractions.
class InjectionController {
  /// The requested iOS 18.6 User Agent for Instagram App feel.
  static const String iOSUserAgent =
      'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/22G86 [FBAN/FBIOS;FBAV/531.0.0.35.77;FBBV/792629356;FBDV/iPhone17,2;FBMD/iPhone;FBSN/iOS;FBSV/18.6;FBSS/3;FBID/phone;FBLC/en_US;FBOP/5;FBRV/0;IABMV/1]';

  // ── CSS & JS injection ──────────────────────────────────────────────────────

  /// CSS to fix UI nuances like tap highlights.
  static const String _globalUIFixesCSS = '''
    * {
      -webkit-tap-highlight-color: transparent !important;
      outline: none !important;
    }
    /* Hide all scrollbars */
    ::-webkit-scrollbar {
      display: none !important;
      width: 0 !important;
      height: 0 !important;
    }
    * {
      -ms-overflow-style: none !important;
      scrollbar-width: none !important;
    }
  ''';

  /// CSS to disable text selection globally.
  static const String _disableSelectionCSS = '''
    * {
      -webkit-user-select: none !important;
      user-select: none !important;
    }
  ''';

  /// Ghost Mode JS: Intercepts network calls to block seen/typing receipts.
  static const String _ghostModeJS = '''
    (function() {
      const blockedUrls = [
        '/api/v1/direct_v2/set_reel_seen/',
        '/api/v1/direct_v2/threads/set_typing_status/',
        '/api/v1/stories/reel/seen/',
        '/api/v1/direct_v2/mark_visual_item_seen/'
      ];

      // Proxy fetch
      const originalFetch = window.fetch;
      window.fetch = function(url, options) {
        if (typeof url === 'string') {
          if (blockedUrls.some(u => url.includes(u))) {
            return Promise.resolve(new Response(null, { status: 204 }));
          }
        }
        return originalFetch.apply(this, arguments);
      };

      // Proxy XHR
      const originalOpen = XMLHttpRequest.prototype.open;
      XMLHttpRequest.prototype.open = function(method, url) {
        this._blocked = blockedUrls.some(u => url.includes(u));
        return originalOpen.apply(this, arguments);
      };
      const originalSend = XMLHttpRequest.prototype.send;
      XMLHttpRequest.prototype.send = function() {
        if (this._blocked) return; 
        return originalSend.apply(this, arguments);
      };
    })();
  ''';

  /// Branding JS: Replaces Instagram logo with FocusGram while keeping icons.
  static const String _brandingJS = '''
    (function() {
      function applyBranding() {
        const igLogo = document.querySelector('svg[aria-label="Instagram"], svg[aria-label="Direct"]');
        if (igLogo && !igLogo.dataset.focusgrammed) {
           const container = igLogo.parentElement;
           if (container) {
              igLogo.style.display = 'none';
              igLogo.dataset.focusgrammed = 'true';
              
              const brandText = document.createElement('span');
              brandText.innerText = 'FocusGram';
              brandText.style.fontFamily = '"Grand Hotel", cursive';
              brandText.style.fontSize = '24px';
              brandText.style.color = 'white';
              brandText.style.marginLeft = '8px';
              brandText.style.verticalAlign = 'middle';
              
              container.appendChild(brandText);
           }
        }
      }
      applyBranding();
      const observer = new MutationObserver(applyBranding);
      observer.observe(document.body, { childList: true, subtree: true });
    })();
  ''';

  /// Robust CSS that hides Instagram's native bottom nav bar.
  static const String _hideInstagramNavCSS = '''
    /* Hide bottom nav but keep search header */
    div[role="tablist"], footer nav, ._acbl, ._aa4b {
      display: none !important;
      visibility: hidden !important;
      height: 0 !important;
      overflow: hidden !important;
      pointer-events: none !important;
    }
    /* Only hide top nav if not on search page */
    body:not([path*="/explore/search/"]) nav[role="navigation"],
    body:not([path*="/explore/search/"]) section nav {
       display: none !important;
    }
    body, #react-root, main {
      padding-bottom: 0 !important;
      margin-bottom: 0 !important;
    }
  ''';

  /// CSS to hide Reel-related elements everywhere.
  static const String _hideReelsCSS = '''
    a[href*="/reel/"], a[href*="/reels"], [aria-label*="Reel"], [aria-label*="Reels"],
    div[data-media-type="2"], [aria-label="Reels"], svg[aria-label="Reels"] {
      display: none !important;
      visibility: hidden !important;
      pointer-events: none !important;
    }
  ''';

  /// CSS that adds bottom padding so feed content doesn't hide behind our bar.
  static const String _bottomPaddingCSS = '''
    body, #react-root > div, [role="presentation"] > div {
      padding-bottom: 72px !important;
    }
    div[style*="bottom: 0px"], div[style*="bottom: 0"], form[method="POST"] {
      padding-bottom: 72px !important;
    }
    div[role="main"] div[style*="position: fixed"] {
       bottom: 72px !important;
    }
  ''';

  /// CSS to blur Explore feed posts/reels.
  static const String _blurExploreCSS = '''
    main[role="main"] section > div > div:not(:first-child) a img,
    main[role="main"] section > div > div:not(:first-child) video,
    main[role="main"] article img, main[role="main"] article video,
    ._aagv img, ._aagv video {
      filter: blur(12px) !important;
      pointer-events: none !important;
    }
  ''';

  static const String _blurReelsCSS = '''
    a[href*="/reel/"] img, a[href*="/reels"] img {
       filter: blur(12px) !important;
    }
  ''';

  /// Auto-dismiss "Open in App" banner.
  static const String _dismissAppBannerJS = '''
    (function dismissBanners() {
      const selectors = ['[id*="app-banner"]', '[class*="app-banner"]', 'div[role="dialog"][aria-label*="app"]'];
      selectors.forEach(sel => document.querySelectorAll(sel).forEach(el => el.remove()));
    })();
  ''';

  /// Periodic remover for bottom nav.
  static const String _periodicNavRemoverJS = '''
    (function periodicNavRemove() {
      function removeNav() {
        document.querySelectorAll('div[role="tablist"], nav[role="navigation"], footer nav').forEach(el => {
          el.style.cssText += ';display:none!important;height:0!important;';
        });
      }
      removeNav();
      setInterval(removeNav, 500);
    })();
  ''';

  /// MutationObserver that continuously re-applies CSS.
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
      const observer = new MutationObserver(() => {
        if (!document.getElementById(STYLE_ID)) injectCSS();
      });
      observer.observe(document.documentElement, { childList: true, subtree: true });
    })();
  ''';

  static String _escapeJsString(String s) {
    final escaped = s.replaceAll(r'\', r'\\').replaceAll('`', r'\`');
    return '`$escaped`';
  }

  // ── Navigation helpers ──────────────────────────────────────────────────────

  static String softNavigateJS(String path) =>
      '''
    (function() {
      const target = ${_escapeJsString(path)};
      if (window.location.pathname !== target) {
        window.location.href = target;
      }
    })();
  ''';

  static const String clickCreateButtonJS = '''
    (function() {
      const btn = document.querySelector('[aria-label="New post"], [aria-label="Create"]');
      if (btn) btn.closest('a, button') ? btn.closest('a, button').click() : btn.click();
    })();
  ''';

  /// Hijacks the Web Notification API to bridge Instagram notifications to native.
  static String get notificationBridgeJS => """
    (function() {
      const NativeNotification = window.Notification;
      if (!NativeNotification) return;

      window.Notification = function(title, options) {
        const body = (options && options.body) ? options.body : "";
        
        // Pass to Flutter
        if (window.FocusGramNotificationChannel) {
          window.FocusGramNotificationChannel.postMessage(title + ": " + body);
        }
        
        return new NativeNotification(title, options);
      };
      
      window.Notification.permission = "granted";
      window.Notification.requestPermission = function() {
        return Promise.resolve("granted");
      };
    })();
  """;

  /// MutationObserver for Reel scroll locking.
  static const String reelsMutationObserverJS = '''
    (function() {
      function lockReelScroll(reelContainer) {
        if (reelContainer.dataset.scrollLocked) return;
        reelContainer.dataset.scrollLocked = 'true';
        let startY = 0;
        reelContainer.addEventListener('touchstart', (e) => startY = e.touches[0].clientY, { passive: true });
        reelContainer.addEventListener('touchmove', (e) => {
          if (window.__focusgramSessionActive === true) return;
          const deltaY = e.touches[0].clientY - startY;
          if (deltaY < -10 && e.cancelable) {
            e.preventDefault();
            e.stopPropagation();
          }
        }, { passive: false });
      }
      const observer = new MutationObserver(() => {
        document.querySelectorAll('[class*="ReelsVideoPlayer"], video').forEach((el) => {
          if (el.tagName === 'VIDEO' && el.closest('article')) return;
          lockReelScroll(el);
          if (el.tagName === 'VIDEO' && el.parentElement) lockReelScroll(el.parentElement);
        });
      });
      observer.observe(document.body, { childList: true, subtree: true });
    })();
  ''';

  static String buildSessionStateJS(bool active) =>
      'window.__focusgramSessionActive = $active;';

  /// Full injection JS to run on page load.
  static String buildInjectionJS({
    required bool sessionActive,
    required bool blurExplore,
    required bool blurReels,
    required bool ghostMode,
    required bool enableTextSelection,
  }) {
    final StringBuffer css = StringBuffer();
    css.writeln(_globalUIFixesCSS);
    if (!enableTextSelection) css.writeln(_disableSelectionCSS);
    css.write(_hideInstagramNavCSS);
    css.write(_bottomPaddingCSS);

    if (!sessionActive) {
      css.write(_hideReelsCSS);
      if (blurExplore) css.write(_blurExploreCSS);
      if (blurReels) css.write(_blurReelsCSS);
    }

    return '''
      ${buildSessionStateJS(sessionActive)}
      /* Set path attribute on body for CSS targeting */
      document.body.setAttribute('path', window.location.pathname);
      ${_buildMutationObserver(css.toString())}
      $_periodicNavRemoverJS
      $_dismissAppBannerJS
      $reelsMutationObserverJS
      $_brandingJS
      ${ghostMode ? _ghostModeJS : ''}
    ''';
  }
}
