// ============================================================================
// FocusGram — InjectionController
// ============================================================================
//
// Builds all JavaScript and CSS payloads injected into the Instagram WebView.
//
// ── Ghost Mode Design ────────────────────────────────────────────────────────
//
// Instead of blocking exact URLs (brittle — Instagram renames paths constantly),
// we block by SEMANTIC KEYWORD GROUPS. A request is silenced if its URL contains
// ANY keyword from the relevant group.
//
// Ghost Mode Semantic Groups (last verified: 2025-02)
// ────────────────────────────────────────────────────
//  seenKeywords   — story/DM seen receipts (any endpoint Instagram uses to
//                   tell others you read/watched something)
//  typingKeywords — typing indicator REST calls + WS text frames
//  liveKeywords   — live viewer heartbeat / join_request (presence on streams)
//  photoKeywords  — disappearing / view-once DM photo seen receipts
//
// Adding new endpoints in the future: just append a keyword to the right group
// in _ghostGroups below — no other code needs to change.
//
// ── Confirmed endpoint map ───────────────────────────────────────────────────
//  /api/v1/media/seen/          — story seen v1 (covered by "media/seen")
//  /api/v2/media/seen/          — story seen v2 (covered by "media/seen")
//  /stories/reel/seen           — web story seen (covered by "reel/seen")
//  /api/v1/stories/reel/mark_seen/ — story mark (covered by "mark_seen")
//  /direct_v2/threads/…/seen/  — DM message read (covered by "/seen")
//  /api/v1/direct_v2/set_reel_seen/ — DM story (covered by "reel_seen")
//  /api/v1/direct_v2/mark_visual_item_seen/ — disappearing photos
//  /api/v1/live/…/heartbeat_and_get_viewer_count/ — live presence
//  /api/v1/live/…/join_request/ — live join
//  WS text frames with "typing", "direct_v2/typing", "activity_status"
//
// ============================================================================

/// Central hub for all JavaScript and CSS injected into the Instagram WebView.
class InjectionController {
  // ── User Agent ──────────────────────────────────────────────────────────────

  /// iOS UA ensures Instagram serves the full mobile UI (Reels, Stories, DMs).
  /// Without spoofing, instagram.com returns a stripped desktop-lite shell.
  static const String iOSUserAgent =
      'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) '
      'AppleWebKit/605.1.15 (KHTML, like Gecko) '
      'Mobile/22G86 [FBAN/FBIOS;FBAV/531.0.0.35.77;FBBV/792629356;'
      'FBDV/iPhone17,2;FBMD/iPhone;FBSN/iOS;FBSV/18.6;FBSS/3;'
      'FBID/phone;FBLC/en_US;FBOP/5;FBRV/0;IABMV/1]';

  // ── Ghost Mode keyword groups ────────────────────────────────────────────────

  /// Semantic groups used by [buildGhostModeJS].
  ///
  /// Each group is a list of URL substrings. A network request is suppressed
  /// if its URL contains ANY substring in the enabled groups.
  ///
  /// To add future endpoints: append keywords here — nothing else changes.
  static const Map<String, List<String>> _ghostGroups = {
    // Any URL that records you having seen/read something
    'seen': ['/seen', '/mark_seen', 'reel_seen', 'reel/seen', 'media/seen'],
    // Typing indicator (REST + WebSocket text frames)
    'typing': ['set_typing_status', '/typing', 'activity_status'],
    // Live stream viewer join / heartbeat (you appear in viewer list)
    'live': ['/live/'],
    // Disappearing / view-once DM photos
    'dmPhotos': ['visual_item_seen'],
  };

  // ── CSS ─────────────────────────────────────────────────────────────────────

  /// Base UI polish — hides scrollbars and Instagram's nav tab-bar.
  /// Important: we must NOT hide [role="tablist"] inside dialogs/modals,
  /// because Instagram's comment input sheet also uses that role and the
  /// CSS would paint a grey overlay on top of the typing area.
  static const String _globalUIFixesCSS = '''
    ::-webkit-scrollbar { display: none !important; width: 0 !important; height: 0 !important; }
    * {
      -ms-overflow-style: none !important;
      scrollbar-width: none !important;
      -webkit-tap-highlight-color: transparent !important;
    }
    /* Only hide the PRIMARY nav tablist (bottom bar), not tablist inside dialogs */
    body > div > div > [role="tablist"]:not([role="dialog"] [role="tablist"]),
    [aria-label="Direct"] header {
       display: none !important;
       visibility: hidden !important;
       height: 0 !important;
       pointer-events: none !important;
    }
  ''';

  /// Blurs images/videos in the home feed AND on Explore.
  /// Activated via the body[path] attribute written by [_trackPathJS].
  static const String _blurHomeFeedAndExploreCSS = '''
    body[path="/"] article img,
    body[path="/"] article video,
    body[path^="/explore"] img,
    body[path^="/explore"] video,
    body[path="/explore/"] img,
    body[path="/explore/"] video {
      filter: blur(20px) !important;
      transition: filter 0.15s ease !important;
    }
    body[path="/"] article img:hover,
    body[path="/"] article video:hover,
    body[path^="/explore"] img:hover,
    body[path^="/explore"] video:hover {
      filter: blur(20px) !important;
    }
  ''';

  /// Prevents text selection to keep the app feeling native.
  static const String _disableSelectionCSS = '''
    * { -webkit-user-select: none !important; user-select: none !important; }
  ''';

  /// Hides reel posts in the home feed when no Reel Session is active.
  /// The Reels nav tab is NOT hidden — Flutter intercepts that navigation.
  static const String _hideReelsFeedContentCSS = '''
    a[href*="/reel/"],
    div[data-media-type="2"] {
      display: none !important;
      visibility: hidden !important;
    }
  ''';

  // _blurExploreCSS removed — replaced by _blurHomeFeedAndExploreCSS above.

  /// Blurs reel thumbnail images shown in the feed.
  static const String _blurReelsCSS = '''
    a[href*="/reel/"] img { filter: blur(12px) !important; }
  ''';

  // ── JavaScript helpers ───────────────────────────────────────────────────────

  /// Removes the "Open in App" nag banner.
  static const String _dismissAppBannerJS = '''
    (function fgDismissBanner() {
      ['[id*="app-banner"]','[class*="app-banner"]',
       'div[role="dialog"][aria-label*="app"]','[id*="openInApp"]']
       .forEach(s => document.querySelectorAll(s).forEach(el => el.remove()));
    })();
  ''';

  /// Replaces ONLY the Instagram wordmark SVG with "FocusGram" brand text.
  /// Specifically targets the top-bar logo SVG (aria-label="Instagram") while
  /// explicitly excluding SVG icons inside nav/tablist (home, notifications,
  /// create, reels, profile icons).
  static const String _brandingJS = r'''
    (function fgBranding() {
      // Only the wordmark: SVG with aria-label="Instagram" that is NOT inside
      // a [role="tablist"] (bottom nav) or a [role="navigation"] (nav bar).
      // Also targets the ._ac83 class which Instagram uses for its top wordmark.
      const WORDMARK_SEL = [
        'svg[aria-label="Instagram"]',
        '._ac83 svg[aria-label="Instagram"]',
        'h1[role="presentation"] svg',
      ];
      const STYLE =
        'font-family:"Grand Hotel",cursive;font-size:26px;color:#fff;' +
        'vertical-align:middle;cursor:default;letter-spacing:.5px;display:inline-block;';

      function isNavIcon(el) {
        // Exclude any SVG that lives inside a tablist, nav, or link with
        // non-home/non-root href (these are functional icons, not the wordmark).
        if (el.closest('[role="tablist"]')) return true;
        if (el.closest('[role="navigation"]')) return true;
        // The wordmark is always at the TOP of the page in a header/banner
        const header = el.closest('header, [role="banner"], [role="main"]');
        if (!header && el.closest('[role="button"]')) return true;
        // If the SVG has a meaningful role (img presenting an action icon), skip it
        const role = el.getAttribute('role');
        if (role && role !== 'img') return true;
        // If the parent <a> goes somewhere other than "/" it is a nav link
        const anchor = el.closest('a');
        if (anchor) {
          const href = anchor.getAttribute('href') || '';
          if (href && href !== '/' && !href.startsWith('/?')) return true;
        }
        return false;
      }

      function apply() {
        WORDMARK_SEL.forEach(sel => document.querySelectorAll(sel).forEach(logo => {
          if (logo.dataset.fgBranded) return;
          if (isNavIcon(logo)) return;
          logo.dataset.fgBranded = 'true';
          const span = Object.assign(document.createElement('span'),
            { textContent: 'FocusGram' });
          span.style.cssText = STYLE;
          logo.style.display = 'none';
          logo.parentNode.insertBefore(span, logo.nextSibling);
        }));
      }
      apply();
      new MutationObserver(apply)
        .observe(document.documentElement, { childList: true, subtree: true });
    })();
  ''';

  /// Intercepts clicks on /reels/ links when no session is active and redirects
  /// to a recognisable URL so Flutter's NavigationDelegate can catch and block it.
  ///
  /// Without this, fast SPA clicks bypass the NavigationDelegate entirely.
  static const String _strictReelsBlockJS = r'''
    (function fgReelsBlock() {
      if (window.__fgReelsBlockPatched) return;
      window.__fgReelsBlockPatched = true;
      document.addEventListener('click', e => {
        if (window.__focusgramSessionActive) return;
        const a = e.target && e.target.closest('a[href*="/reels/"]');
        if (!a) return;
        e.preventDefault();
        e.stopPropagation();
        window.location.href = '/reels/?fg=blocked';
      }, true);
    })();
  ''';

  /// SPA route tracker: writes `body[path]` and notifies Flutter of path changes
  /// via `FocusGramPathChannel` so reels can be blocked on SPA navigation.
  static const String _trackPathJS = '''
    (function fgTrackPath() {
      if (window.__fgPathTrackerRunning) return;
      window.__fgPathTrackerRunning = true;
      let last = window.location.pathname;
      function check() {
        const p = window.location.pathname;
        if (p !== last) {
          last = p;
          if (document.body) document.body.setAttribute('path', p);
          if (window.FocusGramPathChannel) window.FocusGramPathChannel.postMessage(p);
        }
      }
      if (document.body) document.body.setAttribute('path', last);
      setInterval(check, 500);
    })();
  ''';

  /// Injects a persistent `style` element and keeps it alive across SPA route
  /// changes by watching for it being removed from `head`.
  static String _buildMutationObserver(String cssContent) =>
      '''
    (function fgApplyStyles() {
      const ID = 'focusgram-style';
      function inject() {
        let el = document.getElementById(ID);
        if (!el) {
          el = document.createElement('style');
          el.id = ID;
          (document.head || document.documentElement).appendChild(el);
        }
        el.textContent = ${_escapeJsString(cssContent)};
      }
      inject();
      new MutationObserver(() => { if (!document.getElementById(ID)) inject(); })
        .observe(document.documentElement, { childList: true, subtree: true });
    })();
  ''';

  static String _escapeJsString(String s) {
    final escaped = s.replaceAll(r'\', r'\\').replaceAll('`', r'\`');
    return '`$escaped`';
  }

  // ── Navigation helpers ───────────────────────────────────────────────────────

  /// Returns JS that navigates to [path] only when not already on it.
  static String softNavigateJS(String path) =>
      '''
    (function() {
      const t = ${_escapeJsString(path)};
      if (window.location.pathname !== t) window.location.href = t;
    })();
  ''';

  // ── Session state ────────────────────────────────────────────────────────────

  /// Writes the current session-active flag into the WebView global scope.
  /// All injected scripts (Ghost Mode, scroll lock) read this flag.
  static String buildSessionStateJS(bool active) =>
      'window.__focusgramSessionActive = $active;';

  // ── Ghost Mode ───────────────────────────────────────────────────────────────

  /// Returns all URL keywords that should be blocked for the given feature flags.
  ///
  /// Exposed as a separate method so unit tests can verify keyword selection
  /// independently of the full JS string.
  static List<String> resolveBlockedKeywords({
    required bool typingIndicator,
    required bool seenStatus,
    required bool stories,
    required bool dmPhotos,
  }) {
    final out = <String>[];
    if (seenStatus) out.addAll(_ghostGroups['seen']!);
    if (typingIndicator) out.addAll(_ghostGroups['typing']!);
    if (stories) out.addAll(_ghostGroups['live']!);
    if (dmPhotos) out.addAll(_ghostGroups['dmPhotos']!);
    return out;
  }

  /// Returns all WebSocket text-frame keywords to drop for the given flags.
  static List<String> resolveWsBlockedKeywords({
    required bool typingIndicator,
  }) {
    if (!typingIndicator) return const [];
    return List.unmodifiable(_ghostGroups['typing']!);
  }

  /// Builds JavaScript that intercepts fetch, XHR, WebSocket, and sendBeacon
  /// traffic to suppress ALL activity receipts (seen, typing, live, DM photos).
  ///
  /// All blocked requests return `{"status":"ok"}` with HTTP 200 so Instagram
  /// does not retry or display an error.
  ///
  /// See [resolveBlockedKeywords] for the URL-keyword logic.
  static String buildGhostModeJS({
    required bool typingIndicator,
    required bool seenStatus,
    required bool stories,
    required bool dmPhotos,
  }) {
    if (!typingIndicator && !seenStatus && !stories && !dmPhotos) return '';

    final blocked = resolveBlockedKeywords(
      typingIndicator: typingIndicator,
      seenStatus: seenStatus,
      stories: stories,
      dmPhotos: dmPhotos,
    );
    final wsBlocked = resolveWsBlockedKeywords(
      typingIndicator: typingIndicator,
    );

    final urlsJson = blocked.map((u) => '"$u"').join(', ');
    final wsJson = wsBlocked.map((u) => '"$u"').join(', ');

    return '''
    (function fgGhostMode() {
      if (window.__fgGhostModeDone) return;
      window.__fgGhostModeDone = true;

      // URL substrings — any request whose URL contains one of these is silenced.
      const BLOCKED = [$urlsJson];
      // WebSocket text-frame keywords to drop (MQTT typing/presence).
      const WS_KEYS = [$wsJson];

      function shouldBlock(url) {
        return typeof url === 'string' && BLOCKED.some(k => url.includes(k));
      }

      function isDmVideoLocked(url) {
        if (typeof url !== 'string') return false;
        if (!url.includes('.mp4') && !url.includes('/v/t') && !url.includes('cdninstagram') && !url.includes('.dash')) return false;
        return window.__fgDmReelAlreadyLoaded === true;
      }

      // ── fetch ──────────────────────────────────────────────────────────────
      const _oFetch = window.__fgOrigFetch || window.fetch;
      window.__fgOrigFetch = _oFetch;
      window.__fgGhostFetch = function(resource, init) {
        const url = typeof resource === 'string' ? resource : (resource && resource.url) || '';
        // Ghost mode: block seen/typing receipts
        if (shouldBlock(url))
          return Promise.resolve(new Response('{"status":"ok"}',
            { status: 200, headers: { 'Content-Type': 'application/json' } }));
        // DM isolation: block additional video segments after first reel loaded
        if (isDmVideoLocked(url))
          return Promise.resolve(new Response('', { status: 200 }));
        return _oFetch.apply(this, arguments);
      };
      window.fetch = window.__fgGhostFetch;

      // ── sendBeacon ─────────────────────────────────────────────────────────
      if (navigator.sendBeacon && !window.__fgBeaconPatched) {
        window.__fgBeaconPatched = true;
        const _oBeacon = navigator.sendBeacon.bind(navigator);
        navigator.sendBeacon = function(url, data) {
          if (shouldBlock(url)) return true;
          return _oBeacon(url, data);
        };
      }

      // ── XHR ────────────────────────────────────────────────────────────────
      const _oOpen = window.__fgOrigXhrOpen || XMLHttpRequest.prototype.open;
      const _oSend = window.__fgOrigXhrSend || XMLHttpRequest.prototype.send;
      window.__fgOrigXhrOpen = _oOpen;
      window.__fgOrigXhrSend = _oSend;
      XMLHttpRequest.prototype.open = function(m, url) {
        this._fgUrl = url;
        this._fgBlock = shouldBlock(url);
        return _oOpen.apply(this, arguments);
      };
      XMLHttpRequest.prototype.send = function() {
        if (this._fgBlock) {
          Object.defineProperty(this, 'readyState',   { get: () => 4, configurable: true });
          Object.defineProperty(this, 'status',       { get: () => 200, configurable: true });
          Object.defineProperty(this, 'responseText', { get: () => '{"status":"ok"}', configurable: true });
          Object.defineProperty(this, 'response',     { get: () => '{"status":"ok"}', configurable: true });
          setTimeout(() => {
            try { if (this.onreadystatechange) this.onreadystatechange(); } catch(_) {}
            try { if (this.onload) this.onload(); } catch(_) {}
          }, 0);
          return;
        }
        // DM isolation: block additional video XHR fetches after first reel loaded
        if (this._fgUrl && isDmVideoLocked(this._fgUrl)) {
          setTimeout(() => { try { this.onload?.(); } catch(_) {} }, 0);
          return;
        }
        return _oSend.apply(this, arguments);
      };

      // ── WebSocket — block text AND binary frames ───────────────────────────
      if (!window.__fgWsGhostDone) {
        window.__fgWsGhostDone = true;
        const _OWS = window.WebSocket;
        const ALL_SEEN = [$urlsJson];
        function containsKeyword(data) {
          if (typeof data === 'string') return ALL_SEEN.some(k => data.includes(k));
          try {
            let bytes;
            if (data instanceof ArrayBuffer) bytes = new Uint8Array(data);
            else if (data instanceof Uint8Array) bytes = data;
            else return false;
            const text = String.fromCharCode.apply(null, bytes);
            return ALL_SEEN.some(k => text.includes(k));
          } catch(_) { return false; }
        }
        function FgWS(url, proto) {
          const ws = proto != null ? new _OWS(url, proto) : new _OWS(url);
          const _send = ws.send.bind(ws);
          ws.send = function(data) {
            if (containsKeyword(data)) return;
            return _send(data);
          };
          return ws;
        }
        FgWS.prototype = _OWS.prototype;
        ['CONNECTING','OPEN','CLOSING','CLOSED'].forEach(k => FgWS[k] = _OWS[k]);
        window.WebSocket = FgWS;
      }

      // Reapply every 3 s in case Instagram replaces window.fetch
      if (!window.__fgGhostReapplyInterval) {
        window.__fgGhostReapplyInterval = setInterval(() => {
          if (window.fetch !== window.__fgGhostFetch && window.__fgOrigFetch)
            window.fetch = window.__fgGhostFetch;
        }, 3000);
      }
    })();
    ''';
  }

  // ── Theme Detector ───────────────────────────────────────────────────────────

  /// Detects Instagram's current theme (dark/light) and notifies Flutter.
  static const String _themeDetectorJS = r'''
    (function fgThemeSync() {
      if (window.__fgThemeSyncRunning) return;
      window.__fgThemeSyncRunning = true;

      function getTheme() {
        try {
          // 1. Check Instagram's specific classes
          const h = document.documentElement;
          if (h.classList.contains('style-dark')) return 'dark';
          if (h.classList.contains('style-light')) return 'light';

          // 2. Check body background color
          const bg = window.getComputedStyle(document.body).backgroundColor;
          const rgb = bg.match(/\d+/g);
          if (rgb && rgb.length >= 3) {
            const luminance = (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]) / 255;
            return luminance < 0.5 ? 'dark' : 'light';
          }
        } catch(_) {}
        return 'dark'; // Fallback
      }

      let last = '';
      function check() {
        const current = getTheme();
        if (current !== last) {
          last = current;
          if (window.FocusGramThemeChannel) {
            window.FocusGramThemeChannel.postMessage(current);
          }
        }
      }
      setInterval(check, 1500);
      check();
    })();
  ''';

  // ── Reel scroll lock ─────────────────────────────────────────────────────────

  /// Prevents swipe-to-next-reel in the isolated DM reel player.
  ///
  /// Lock is active when:
  ///   `window.__focusgramIsolatedPlayer === true`  (DM overlay)
  ///   OR `window.__focusgramSessionActive === false`  (no session)
  ///
  /// Allow-list (these are never blocked):
  ///   • buttons, anchors, [role=button], aria elements
  ///   • dialogs, menus, modals, sheets (comment box, emoji picker, share sheet)
  ///   • keyboard input inside comment / text fields
  /// Prevents swipe-to-next-reel in the isolated DM reel player.
  ///
  /// Uses a document-level capture-phase touchmove listener so it fires BEFORE
  /// Instagram's scroll container can steal the gesture. The lock is active when
  /// `window.__focusgramIsolatedPlayer === true` (single reel from DM),
  /// OR `window.__focusgramSessionActive === false` (reels feed, no session).
  ///
  /// The isolated player flag is also maintained here from the path tracker
  /// so it works for SPA navigations that don't trigger onPageFinished.
  static const String reelsMutationObserverJS = r'''
    (function fgReelLock() {
      if (window.__fgReelLockRunning) return;
      window.__fgReelLockRunning = true;

      const ALLOW_SEL = 'button,a,[role="button"],[aria-label],[aria-haspopup],input,textarea,span,h1,h2,h3';
      const MODAL_SEL = '[role="dialog"],[role="menu"],[role="listbox"],[class*="Modal"],[class*="Sheet"],[class*="Drawer"],._aano,[class*="caption"]';

      function isLocked() {
        const isDmReel = window.location.pathname.includes('/direct/') &&
                         !!document.querySelector('[class*="ReelsVideoPlayer"]');
        return window.__focusgramIsolatedPlayer === true ||
               window.__focusgramSessionActive === false ||
               isDmReel;
      }

      let sy = 0;
      document.addEventListener('touchstart', e => {
        sy = e.touches && e.touches[0] ? e.touches[0].clientY : 0;
      }, { capture: true, passive: true });

      document.addEventListener('touchmove', e => {
        if (!isLocked()) return;
        // Allow vertical swipe if in a session and not on a DM/isolated path
        if (window.__focusgramSessionActive === true && !window.location.pathname.includes('/direct/')) return;
        
        const dy = e.touches && e.touches[0] ? e.touches[0].clientY - sy : 0;
        if (Math.abs(dy) > 2) {
          if (e.target && e.target.closest && (e.target.closest(ALLOW_SEL) || e.target.closest(MODAL_SEL))) return;
          // Mark the first DM reel as loaded on first swipe attempt
          if (window.location.pathname.includes('/direct/')) {
            window.__fgDmReelAlreadyLoaded = true;
          }
          if (e.cancelable) e.preventDefault();
          e.stopPropagation();
        }
      }, { capture: true, passive: false });

      function block(e) {
        if (!isLocked()) return;
        if (e.target && e.target.closest && (e.target.closest(ALLOW_SEL) || e.target.closest(MODAL_SEL))) return;
        if (e.cancelable) e.preventDefault();
        e.stopPropagation();
      }
      document.addEventListener('wheel', block, { capture: true, passive: false });
      document.addEventListener('keydown', e => {
        if (!['ArrowDown','ArrowUp',' ','PageUp','PageDown'].includes(e.key)) return;
        if (e.target && e.target.closest && e.target.closest('input,textarea,[contenteditable="true"]')) return;
        block(e);
      }, { capture: true, passive: false });

      const REEL_SEL = '[class*="ReelsVideoPlayer"], video';

      function sync() {
        const reels = document.querySelectorAll(REEL_SEL);

        if (window.location.pathname.includes('/direct/') && reels.length > 0) {
          // Give the first reel 3.5 s to buffer before activating the DM lock
          if (!window.__fgDmReelTimer) {
            window.__fgDmReelTimer = setTimeout(() => {
              if (document.querySelector(REEL_SEL)) {
                window.__fgDmReelAlreadyLoaded = true;
              }
              window.__fgDmReelTimer = null;
            }, 3500);
          }
        }

        if (reels.length === 0) {
          if (window.__fgDmReelTimer) {
            clearTimeout(window.__fgDmReelTimer);
            window.__fgDmReelTimer = null;
          }
          window.__fgDmReelAlreadyLoaded = false;
        }
      }

      sync();
      new MutationObserver(ms => {
        if (ms.some(m => m.addedNodes.length || m.removedNodes.length)) sync();
      }).observe(document.body, { childList: true, subtree: true });

      // Keep __focusgramIsolatedPlayer in sync with SPA navigations
      if (!window.__fgIsolatedPlayerSync) {
        window.__fgIsolatedPlayerSync = true;
        let _lastPath = window.location.pathname;
        setInterval(() => {
          const p = window.location.pathname;
          if (p === _lastPath) return;
          _lastPath = p;
          window.__focusgramIsolatedPlayer =
            p.includes('/reel/') && !p.startsWith('/reels/');
          if (!p.includes('/direct/')) window.__fgDmReelAlreadyLoaded = false;
        }, 400);
      }
    })();
  ''';

  // ── Badge Monitor ────────────────────────────────────────────────────────────

  /// Periodically checks Instagram's UI for unread counts (badges) on the Direct
  /// and Notifications icons, as well as the page title. Sends an event to
  /// Flutter whenever a new notification is detected.
  static const String _badgeMonitorJS = r'''
    (function fgBadgeMonitor() {
      if (window.__fgBadgeMonitorRunning) return;
      window.__fgBadgeMonitorRunning = true;

      let lastDmCount = 0;
      let lastNotifCount = 0;
      let lastTitleUnread = 0;

      function check() {
        try {
          // 1. Check Title for (N) indicator
          const titleMatch = document.title.match(/\((\d+)\)/);
          const currentTitleUnread = titleMatch ? parseInt(titleMatch[1]) : 0;

          // 2. Scan for DM unread badge
          const dmBadge = document.querySelector([
            'a[href*="/direct/inbox/"] [style*="rgb(255, 48, 64)"]',
            'a[href*="/direct/inbox/"] [style*="255, 48, 64"]',
            'a[href*="/direct/inbox/"] [aria-label*="unread"]',
            'div[role="button"][aria-label*="Direct"] [style*="255, 48, 64"]',
            'a[href*="/direct/inbox/"] svg[aria-label*="Direct"] + div', // New red dot sibling
            'a[href*="/direct/inbox/"] ._a9-v', // Modern common red badge class
          ].join(','));
          const currentDmCount = dmBadge ? (parseInt(dmBadge.innerText) || 1) : 0;

          // 3. Scan for Notifications unread badge
          const notifBadge = document.querySelector([
            'a[href*="/notifications"] [style*="rgb(255, 48, 64)"]',
            'a[href*="/notifications"] [style*="255, 48, 64"]',
            'a[href*="/notifications"] [aria-label*="unread"]'
          ].join(','));
          const currentNotifCount = notifBadge ? (parseInt(notifBadge.innerText) || 1) : 0;

          if (currentDmCount > lastDmCount) {
            window.FocusGramNotificationChannel?.postMessage('DM');
          } else if (currentNotifCount > lastNotifCount) {
            window.FocusGramNotificationChannel?.postMessage('Activity');
          } else if (currentTitleUnread > lastTitleUnread && currentTitleUnread > (currentDmCount + currentNotifCount)) {
            window.FocusGramNotificationChannel?.postMessage('Activity');
          }

          lastDmCount = currentDmCount;
          lastNotifCount = currentNotifCount;
          lastTitleUnread = currentTitleUnread;
        } catch(_) {}
      }

      // Initial check after some delay to let page settle
      setTimeout(check, 2000);
      setInterval(check, 3000);
    })();
  ''';

  // ── Notification bridge ──────────────────────────────────────────────────────

  /// Forwards Web Notification events to the native Flutter channel.
  static String get notificationBridgeJS => '''
    (function fgNotifBridge() {
      if (!window.Notification || window.__fgNotifBridged) return;
      window.__fgNotifBridged = true;
      const _N = window.Notification;
      window.Notification = function(title, opts) {
        try {
          if (window.FocusGramNotificationChannel)
            window.FocusGramNotificationChannel
              .postMessage(title + (opts && opts.body ? ': ' + opts.body : ''));
        } catch(_) {}
        return new _N(title, opts);
      };
      window.Notification.permission = 'granted';
      window.Notification.requestPermission = () => Promise.resolve('granted');
    })();
  ''';

  // ── Link sanitization ────────────────────────────────────────────────────────

  /// Strips tracking query params (igsh, utm_*, fbclid…) from all links and the
  /// native Web Share API. Sanitised share URLs are routed to Flutter's share
  /// channel instead.
  static const String linkSanitizationJS = r'''
    (function fgSanitize() {
      if (window.__fgSanitizePatched) return;
      window.__fgSanitizePatched = true;
      const STRIP = [
        'igsh','igshid','fbclid',
        'utm_source','utm_medium','utm_campaign','utm_term','utm_content',
        'ref','s','_branch_match_id','_branch_referrer',
      ];
      function clean(raw) {
        try {
          const u = new URL(raw, location.origin);
          STRIP.forEach(p => u.searchParams.delete(p));
          return u.toString();
        } catch(_) { return raw; }
      }
      if (navigator.share) {
        const _s = navigator.share.bind(navigator);
        navigator.share = function(d) {
          const u = d && d.url ? clean(d.url) : null;
          if (window.FocusGramShareChannel && u) {
            window.FocusGramShareChannel.postMessage(
              JSON.stringify({ url: u, title: (d && d.title) || '' }));
            return Promise.resolve();
          }
          return _s({ ...d, url: u || (d && d.url) });
        };
      }
      document.addEventListener('click', e => {
        const a = e.target && e.target.closest('a[href]');
        if (!a) return;
        const href = a.getAttribute('href');
        if (!href || href.startsWith('#') || href.startsWith('javascript')) return;
        try {
          const u = new URL(href, location.origin);
          if (STRIP.some(p => u.searchParams.has(p))) {
            STRIP.forEach(p => u.searchParams.delete(p));
            a.href = u.toString();
          }
        } catch(_) {}
      }, true);
    })();
  ''';

  // ── Main injection builder ───────────────────────────────────────────────────

  /// Builds the complete JS payload for a page load or session-state change.
  ///
  /// Injection order matters (later scripts can depend on earlier ones):
  ///   1. Session flag      — other scripts read `__focusgramSessionActive`
  ///   2. Path tracker      — writes `body[path]` for CSS page targeting
  ///   3. CSS observer      — keeps `<style>` alive across SPA navigations
  ///   4. Banner dismiss    — removes "Open in App" nag
  ///   5. Branding          — replaces Instagram logo with FocusGram
  ///   6. Reels JS blocker  — click-interceptor (only when no session)
  ///   7. Ghost Mode        — network interceptors (fetch / XHR / WS)
  ///   8. Link sanitizer    — tracking param stripping
  static String buildInjectionJS({
    required bool sessionActive,
    required bool blurExplore,
    required bool blurReels,
    required bool ghostTyping,
    required bool ghostSeen,
    required bool ghostStories,
    required bool ghostDmPhotos,
    required bool enableTextSelection,
  }) {
    final css = StringBuffer()..writeln(_globalUIFixesCSS);
    if (!enableTextSelection) css.writeln(_disableSelectionCSS);
    if (!sessionActive) {
      css.writeln(_hideReelsFeedContentCSS);
      if (blurReels) css.writeln(_blurReelsCSS);
    }
    // blurExplore now also blurs home-feed posts ("Blur Posts and Explore")
    if (blurExplore) css.writeln(_blurHomeFeedAndExploreCSS);

    final ghost = buildGhostModeJS(
      typingIndicator: ghostTyping,
      seenStatus: ghostSeen,
      stories: ghostStories,
      dmPhotos: ghostDmPhotos,
    );

    return '''
      ${buildSessionStateJS(sessionActive)}
      $_trackPathJS
      ${_buildMutationObserver(css.toString())}
      $_dismissAppBannerJS
      $_brandingJS
      ${!sessionActive ? _strictReelsBlockJS : ''}
      $reelsMutationObserverJS
      $ghost
      $linkSanitizationJS
      $_themeDetectorJS
      $_badgeMonitorJS
    ''';
  }
}
