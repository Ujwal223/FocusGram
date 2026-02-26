import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'session_manager.dart';
import 'settings_service.dart';
import 'injection_controller.dart';
import '../scripts/grayscale.dart' as grayscale;
import '../scripts/ui_hider.dart' as ui_hider;
import '../scripts/content_disabling.dart' as content_disabling;

// Core JS and CSS payloads injected into the Instagram WebView.
//
// WARNING: Do not add any network interception logic ("ghost mode") here.
// All scripts in this file must be limited to UI behaviour, navigation helpers,
// and local-only features that do not modify data sent to Meta's servers.

// ── CSS payloads ──────────────────────────────────────────────────────────────

/// Base UI polish — hides scrollbars and Instagram's nav tab-bar.
const String kGlobalUIFixesCSS = '''
  ::-webkit-scrollbar { display: none !important; width: 0 !important; height: 0 !important; }
  * {
    -ms-overflow-style: none !important;
    scrollbar-width: none !important;
    -webkit-tap-highlight-color: transparent !important;
  }
  body > div > div > [role="tablist"]:not([role="dialog"] [role="tablist"]),
  [aria-label="Direct"] header {
     display: none !important;
     visibility: hidden !important;
     height: 0 !important;
     pointer-events: none !important;
  }
''';

/// Blurs images/videos in the home feed AND on Explore.
const String kBlurHomeFeedAndExploreCSS = '''
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

/// Prevents text selection to keep the app feeling native (only when disabled).
const String kDisableSelectionCSS = '''
  * { -webkit-user-select: none !important; user-select: none !important; }
''';

/// Hides reel posts in the home feed when no Reel Session is active.
const String kHideReelsFeedContentCSS = '''
  a[href*="/reel/"],
  div[data-media-type="2"] {
    display: none !important;
    visibility: hidden !important;
  }
''';

// ── JavaScript helpers ────────────────────────────────────────────────────────

const String kDismissAppBannerJS = '''
  (function fgDismissBanner() {
    ['[id*="app-banner"]','[class*="app-banner"]',
     'div[role="dialog"][aria-label*="app"]','[id*="openInApp"]']
     .forEach(s => document.querySelectorAll(s).forEach(el => el.remove()));
  })();
''';

const String kStrictReelsBlockJS = r'''
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

const String kTrackPathJS = '''
  (function fgTrackPath() {
    if (window.__fgPathTrackerRunning) return;
    window.__fgPathTrackerRunning = true;
    let last = window.location.pathname;
    function check() {
      const p = window.location.pathname;
      if (p !== last) {
        last = p;
        if (document.body) document.body.setAttribute('path', p);
        if (window.flutter_inappwebview) {
          window.flutter_inappwebview.callHandler('UrlChange', p);
        }
      }
    }
    if (document.body) document.body.setAttribute('path', last);
    setInterval(check, 500);
  })();
''';

const String kThemeDetectorJS = r'''
  (function fgThemeSync() {
    if (window.__fgThemeSyncRunning) return;
    window.__fgThemeSyncRunning = true;
    function getTheme() {
      try {
        const h = document.documentElement;
        if (h.classList.contains('style-dark')) return 'dark';
        if (h.classList.contains('style-light')) return 'light';
        const bg = window.getComputedStyle(document.body).backgroundColor;
        const rgb = bg.match(/\d+/g);
        if (rgb && rgb.length >= 3) {
          const luminance = (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]) / 255;
          return luminance < 0.5 ? 'dark' : 'light';
        }
      } catch(_) {}
      return 'dark';
    }
    let last = '';
    function check() {
      const current = getTheme();
      if (current !== last) {
        last = current;
        if (window.flutter_inappwebview) {
          window.flutter_inappwebview.callHandler('FocusGramThemeChannel', current);
        }
      }
    }
    setInterval(check, 1500);
    check();
  })();
''';

const String kReelsMutationObserverJS = r'''
  (function fgReelLock() {
    if (window.__fgReelLockRunning) return;
    window.__fgReelLockRunning = true;

    const MODAL_SEL = '[role="dialog"],[role="menu"],[role="listbox"],[class*="Modal"],[class*="Sheet"],[class*="Drawer"],._aano,[class*="caption"]';

    function lockMode() {
      // Only lock scroll when: DM reel playing OR disableReelsEntirely enabled
      const isDmReel = window.location.pathname.includes('/direct/') &&
                       !!document.querySelector('[class*="ReelsVideoPlayer"]');
      if (isDmReel) return 'dm_reel';
      if (window.__fgDisableReelsEntirely === true) return 'disabled';
      return null;
    }

    function isLocked() { return lockMode() !== null; }

    function allowInteractionTarget(t) {
      if (!t || !t.closest) return false;
      if (t.closest('input,textarea,[contenteditable="true"]')) return true;
      if (t.closest(MODAL_SEL)) return true;
      return false;
    }

    let sy = 0;
    document.addEventListener('touchstart', e => {
      sy = e.touches && e.touches[0] ? e.touches[0].clientY : 0;
    }, { capture: true, passive: true });

    document.addEventListener('touchmove', e => {
      if (!isLocked()) return;
      const dy = e.touches && e.touches[0] ? e.touches[0].clientY - sy : 0;
      if (Math.abs(dy) > 2) {
        if (window.location.pathname.includes('/direct/')) {
          window.__fgDmReelAlreadyLoaded = true;
        }
        if (allowInteractionTarget(e.target)) return;
        if (e.cancelable) e.preventDefault();
        e.stopPropagation();
      }
    }, { capture: true, passive: false });

    function block(e) {
      if (!isLocked()) return;
      if (allowInteractionTarget(e.target)) return;
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
    let __fgOrigHtmlOverflow = null;
    let __fgOrigBodyOverflow = null;

    function applyOverflowLock() {
      try {
        const mode = lockMode();
        const hasReel = !!document.querySelector(REEL_SEL);
        if ((mode === 'dm_reel' || mode === 'disabled') && hasReel) {
          if (__fgOrigHtmlOverflow === null) {
            __fgOrigHtmlOverflow = document.documentElement.style.overflow || '';
            __fgOrigBodyOverflow = document.body ? (document.body.style.overflow || '') : '';
          }
          document.documentElement.style.overflow = 'hidden';
          if (document.body) document.body.style.overflow = 'hidden';
        } else if (__fgOrigHtmlOverflow !== null) {
          document.documentElement.style.overflow = __fgOrigHtmlOverflow;
          if (document.body) document.body.style.overflow = __fgOrigBodyOverflow || '';
          __fgOrigHtmlOverflow = null;
          __fgOrigBodyOverflow = null;
        }
      } catch (_) {}
    }

    function sync() {
      const reels = document.querySelectorAll(REEL_SEL);
      applyOverflowLock();
      if (window.location.pathname.includes('/direct/') && reels.length > 0) {
        if (!window.__fgDmReelTimer) {
          window.__fgDmReelTimer = setTimeout(() => {
            if (document.querySelector(REEL_SEL)) window.__fgDmReelAlreadyLoaded = true;
            window.__fgDmReelTimer = null;
          }, 3500);
        }
      }
      if (reels.length === 0) {
        if (window.__fgDmReelTimer) { clearTimeout(window.__fgDmReelTimer); window.__fgDmReelTimer = null; }
        window.__fgDmReelAlreadyLoaded = false;
      }
    }

    sync();
    new MutationObserver(ms => {
      if (ms.some(m => m.addedNodes.length || m.removedNodes.length)) sync();
    }).observe(document.body, { childList: true, subtree: true });

    if (!window.__fgIsolatedPlayerSync) {
      window.__fgIsolatedPlayerSync = true;
      let _lastPath = window.location.pathname;
      setInterval(() => {
        const p = window.location.pathname;
        if (p === _lastPath) return;
        _lastPath = p;
        window.__focusgramIsolatedPlayer = p.includes('/reel/') && !p.startsWith('/reels');
        if (!p.includes('/direct/')) window.__fgDmReelAlreadyLoaded = false;
        applyOverflowLock();
      }, 400);
    }
  })();
''';

const String kBadgeMonitorJS = r'''
  (function fgBadgeMonitor() {
    if (window.__fgBadgeMonitorRunning) return;
    window.__fgBadgeMonitorRunning = true;
    const startedAt = Date.now();
    let initialised = false;
    let lastDmCount = 0, lastNotifCount = 0, lastTitleUnread = 0;

    function parseBadgeCount(el) {
      if (!el) return 0;
      try {
        const raw = (el.innerText || el.textContent || '').trim();
        const n = parseInt(raw, 10);
        return isNaN(n) ? 1 : n;
      } catch (_) { return 1; }
    }

    function check() {
      try {
        const titleMatch = document.title.match(/\((\d+)\)/);
        const currentTitleUnread = titleMatch ? parseInt(titleMatch[1]) : 0;
        const dmBadge = document.querySelector([
          'a[href*="/direct/inbox/"] [style*="rgb(255, 48, 64)"]',
          'a[href*="/direct/inbox/"] [aria-label*="unread"]',
          'a[href*="/direct/inbox/"] ._a9-v',
        ].join(','));
        const currentDmCount = parseBadgeCount(dmBadge);
        const notifBadge = document.querySelector([
          'a[href*="/notifications"] [style*="rgb(255, 48, 64)"]',
          'a[href*="/notifications"] [aria-label*="unread"]',
        ].join(','));
        const currentNotifCount = parseBadgeCount(notifBadge);

        if (!initialised) {
          lastDmCount = currentDmCount; lastNotifCount = currentNotifCount;
          lastTitleUnread = currentTitleUnread; initialised = true; return;
        }
        if (Date.now() - startedAt < 6000) {
          lastDmCount = currentDmCount; lastNotifCount = currentNotifCount;
          lastTitleUnread = currentTitleUnread; return;
        }
        if (currentDmCount > lastDmCount && window.flutter_inappwebview) {
          window.flutter_inappwebview.callHandler('FocusGramNotificationChannel', 'DM');
        } else if (currentNotifCount > lastNotifCount && window.flutter_inappwebview) {
          window.flutter_inappwebview.callHandler('FocusGramNotificationChannel', 'Activity');
        }
        lastDmCount = currentDmCount; lastNotifCount = currentNotifCount;
        lastTitleUnread = currentTitleUnread;
      } catch(_) {}
    }
    setTimeout(check, 2000);
    setInterval(check, 1000);
  })();
''';

const String kNotificationBridgeJS = '''
  (function fgNotifBridge() {
    if (!window.Notification || window.__fgNotifBridged) return;
    window.__fgNotifBridged = true;
    const startedAt = Date.now();
    const _N = window.Notification;
    window.Notification = function(title, opts) {
      try {
        if (Date.now() - startedAt < 6000) return new _N(title, opts);
        if (window.flutter_inappwebview) {
          window.flutter_inappwebview.callHandler(
            'FocusGramNotificationChannel',
            title + (opts && opts.body ? ': ' + opts.body : ''),
          );
        }
      } catch(_) {}
      return new _N(title, opts);
    };
    window.Notification.permission = 'granted';
    window.Notification.requestPermission = () => Promise.resolve('granted');
  })();
''';

const String kLinkSanitizationJS = r'''
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
        if (window.flutter_inappwebview && u) {
          window.flutter_inappwebview.callHandler(
            'FocusGramShareChannel',
            JSON.stringify({ url: u, title: (d && d.title) || '' }),
          );
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

// ── InjectionManager class ─────────────────────────────────────────────────

class InjectionManager {
  final InAppWebViewController controller;
  final SharedPreferences prefs;
  final SessionManager sessionManager;

  SettingsService? _settingsService;

  InjectionManager({
    required this.controller,
    required this.prefs,
    required this.sessionManager,
  });

  void setSettingsService(SettingsService settingsService) {
    _settingsService = settingsService;
  }

  /// Runs all post-load JavaScript injections based on current settings.
  Future<void> runAllPostLoadInjections(String url) async {
    if (_settingsService == null) return;

    final settings = _settingsService!;
    final sessionActive = sessionManager.isSessionActive;

    // Get settings values
    final blurExplore = settings.blurExplore;
    final enableTextSelection = settings.enableTextSelection;
    final hideSuggestedPosts = settings.hideSuggestedPosts;
    final hideSponsoredPosts = settings.hideSponsoredPosts;
    final hideLikeCounts = settings.hideLikeCounts;
    final hideFollowerCounts = settings.hideFollowerCounts;
    final hideExploreTab = settings.hideExploreTab;
    final hideReelsTab = settings.hideReelsTab;
    final hideShopTab = settings.hideShopTab;
    final disableReelsEntirely = settings.disableReelsEntirely;
    final isGrayscaleActive = settings.isGrayscaleActiveNow;

    final injectionJS = InjectionController.buildInjectionJS(
      sessionActive: sessionActive,
      blurExplore: blurExplore,
      blurReels: false, // Blur reels feature removed
      enableTextSelection: enableTextSelection,
      hideSuggestedPosts: hideSuggestedPosts,
      hideSponsoredPosts: hideSponsoredPosts,
      hideLikeCounts: hideLikeCounts,
      hideFollowerCounts: hideFollowerCounts,
      hideStoriesBar: false, // Story blocking removed
      hideExploreTab: hideExploreTab,
      hideReelsTab: hideReelsTab,
      hideShopTab: hideShopTab,
      disableReelsEntirely: disableReelsEntirely,
    );

    try {
      await controller.evaluateJavascript(source: injectionJS);
    } catch (e) {
      // Silently handle injection errors
    }

    // Inject grayscale when active, remove when not active
    if (isGrayscaleActive) {
      try {
        await controller.evaluateJavascript(source: grayscale.kGrayscaleJS);
      } catch (e) {
        // Silently handle injection errors
      }
    } else {
      try {
        await controller.evaluateJavascript(source: grayscale.kGrayscaleOffJS);
      } catch (e) {
        // Silently handle injection errors
      }
    }

    // Inject hide like counts JS when enabled
    if (hideLikeCounts) {
      try {
        await controller.evaluateJavascript(source: ui_hider.kHideLikeCountsJS);
      } catch (e) {
        // Silently handle injection errors
      }
    }

    // Inject hide suggested posts JS when enabled
    if (hideSuggestedPosts) {
      try {
        await controller.evaluateJavascript(
          source: ui_hider.kHideSuggestedPostsJS,
        );
      } catch (e) {
        // Silently handle injection errors
      }
    }

    // Inject hide sponsored posts JS when enabled
    if (hideSponsoredPosts) {
      try {
        await controller.evaluateJavascript(
          source: ui_hider.kHideSponsoredPostsJS,
        );
      } catch (e) {
        // Silently handle injection errors
      }
    }

    // Inject DM Reel blocker when disableReelsEntirely is enabled
    if (disableReelsEntirely) {
      try {
        await controller.evaluateJavascript(
          source: content_disabling.kDmReelBlockerJS,
        );
      } catch (e) {
        // Silently handle injection errors
      }
    }
  }
}
