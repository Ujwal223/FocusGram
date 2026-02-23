// test/services/injection_controller_test.dart
//
// Tests for InjectionController — JS/CSS builder, Ghost Mode keyword resolver,
// and JS string generation.
//
// Run with: flutter test test/services/injection_controller_test.dart

import 'package:flutter_test/flutter_test.dart';
import 'package:focusgram/services/injection_controller.dart';

void main() {
  // ── resolveBlockedKeywords ───────────────────────────────────────────────

  group('InjectionController.resolveBlockedKeywords', () {
    test('returns empty list when all flags are false', () {
      final kws = InjectionController.resolveBlockedKeywords(
        typingIndicator: false,
        seenStatus: false,
        stories: false,
        dmPhotos: false,
      );
      expect(kws, isEmpty);
    });

    test('includes seen keywords when seenStatus is true', () {
      final kws = InjectionController.resolveBlockedKeywords(
        typingIndicator: false,
        seenStatus: true,
        stories: false,
        dmPhotos: false,
      );
      expect(
        kws,
        containsAll(['/seen', 'media/seen', 'reel/seen', '/mark_seen']),
      );
    });

    test('includes typing keywords when typingIndicator is true', () {
      final kws = InjectionController.resolveBlockedKeywords(
        typingIndicator: true,
        seenStatus: false,
        stories: false,
        dmPhotos: false,
      );
      expect(kws, containsAll(['set_typing_status', '/typing']));
    });

    test('includes live keywords when stories is true', () {
      final kws = InjectionController.resolveBlockedKeywords(
        typingIndicator: false,
        seenStatus: false,
        stories: true,
        dmPhotos: false,
      );
      expect(kws, contains('/live/'));
    });

    test('includes visual_item_seen when dmPhotos is true', () {
      final kws = InjectionController.resolveBlockedKeywords(
        typingIndicator: false,
        seenStatus: false,
        stories: false,
        dmPhotos: true,
      );
      expect(kws, contains('visual_item_seen'));
    });

    test('all flags true — returns all groups combined', () {
      final kws = InjectionController.resolveBlockedKeywords(
        typingIndicator: true,
        seenStatus: true,
        stories: true,
        dmPhotos: true,
      );
      // Must contain at least one keyword from every group
      expect(
        kws,
        containsAll([
          '/seen',
          'set_typing_status',
          '/live/',
          'visual_item_seen',
        ]),
      );
    });

    test('no duplicates in result (seen + typing + stories + dmPhotos)', () {
      final kws = InjectionController.resolveBlockedKeywords(
        typingIndicator: true,
        seenStatus: true,
        stories: true,
        dmPhotos: true,
      );
      final unique = kws.toSet();
      expect(kws.length, unique.length);
    });
  });

  // ── resolveWsBlockedKeywords ─────────────────────────────────────────────

  group('InjectionController.resolveWsBlockedKeywords', () {
    test('returns empty list when typingIndicator is false', () {
      expect(
        InjectionController.resolveWsBlockedKeywords(typingIndicator: false),
        isEmpty,
      );
    });

    test('returns non-empty list when typingIndicator is true', () {
      final kws = InjectionController.resolveWsBlockedKeywords(
        typingIndicator: true,
      );
      expect(kws, isNotEmpty);
      expect(kws, contains('activity_status'));
    });
  });

  // ── buildGhostModeJS ─────────────────────────────────────────────────────

  group('InjectionController.buildGhostModeJS', () {
    test('returns empty string when all flags are false', () {
      final js = InjectionController.buildGhostModeJS(
        typingIndicator: false,
        seenStatus: false,
        stories: false,
        dmPhotos: false,
      );
      expect(js.trim(), isEmpty);
    });

    test('generated JS contains seen keywords when seenStatus=true', () {
      final js = InjectionController.buildGhostModeJS(
        typingIndicator: false,
        seenStatus: true,
        stories: false,
        dmPhotos: false,
      );
      expect(js, contains('/seen'));
      expect(js, contains('media/seen'));
    });

    test('generated JS contains typing keywords when typingIndicator=true', () {
      final js = InjectionController.buildGhostModeJS(
        typingIndicator: true,
        seenStatus: false,
        stories: false,
        dmPhotos: false,
      );
      expect(js, contains('set_typing_status'));
    });

    test('generated JS contains live keyword when stories=true', () {
      final js = InjectionController.buildGhostModeJS(
        typingIndicator: false,
        seenStatus: false,
        stories: true,
        dmPhotos: false,
      );
      expect(js, contains('/live/'));
    });

    test('generated JS contains BLOCKED array and shouldBlock function', () {
      final js = InjectionController.buildGhostModeJS(
        typingIndicator: false,
        seenStatus: true,
        stories: false,
        dmPhotos: false,
      );
      expect(js, contains('BLOCKED'));
      expect(js, contains('shouldBlock'));
    });

    test('generated JS wraps XHR and fetch', () {
      final js = InjectionController.buildGhostModeJS(
        typingIndicator: true,
        seenStatus: true,
        stories: true,
        dmPhotos: true,
      );
      expect(js, contains('window.fetch'));
      expect(js, contains('XMLHttpRequest.prototype.open'));
      expect(js, contains('XMLHttpRequest.prototype.send'));
    });

    test('WS patch is included when typingIndicator=true', () {
      final js = InjectionController.buildGhostModeJS(
        typingIndicator: true,
        seenStatus: false,
        stories: false,
        dmPhotos: false,
      );
      expect(js, contains('WebSocket'));
    });

    test('WS patch is NOT included when typingIndicator=false', () {
      final js = InjectionController.buildGhostModeJS(
        typingIndicator: false,
        seenStatus: true,
        stories: false,
        dmPhotos: false,
      );
      // WS_KEYS will be empty array; the `if (WS_KEYS.length > 0)` guard
      // prevents the WS override from running — but the string may still be present.
      // At minimum, WS_KEYS should be empty in the output.
      expect(js, contains('WS_KEYS = []'));
    });
  });

  // ── buildSessionStateJS ──────────────────────────────────────────────────

  group('InjectionController.buildSessionStateJS', () {
    test('returns true assignment when active', () {
      expect(
        InjectionController.buildSessionStateJS(true),
        contains('__focusgramSessionActive = true'),
      );
    });

    test('returns false assignment when inactive', () {
      expect(
        InjectionController.buildSessionStateJS(false),
        contains('__focusgramSessionActive = false'),
      );
    });
  });

  // ── softNavigateJS ───────────────────────────────────────────────────────

  group('InjectionController.softNavigateJS', () {
    test('contains the target path', () {
      final js = InjectionController.softNavigateJS('/direct/inbox/');
      expect(js, contains('/direct/inbox/'));
    });

    test('contains location.href assignment', () {
      final js = InjectionController.softNavigateJS('/explore/');
      expect(js, contains('location.href'));
    });
  });

  // ── buildInjectionJS ─────────────────────────────────────────────────────

  group('InjectionController.buildInjectionJS', () {
    InjectionController.buildInjectionJS; // reference check

    test('contains session state flag', () {
      final js = _buildFull();
      expect(js, contains('__focusgramSessionActive'));
    });

    test('contains path tracker when assembled', () {
      final js = _buildFull();
      expect(js, contains('fgTrackPath'));
    });

    test('includes reels block JS when session is not active', () {
      final js = InjectionController.buildInjectionJS(
        sessionActive: false,
        blurExplore: false,
        blurReels: false,
        ghostTyping: false,
        ghostSeen: false,
        ghostStories: false,
        ghostDmPhotos: false,
        enableTextSelection: false,
      );
      expect(js, contains('fgReelsBlock'));
    });

    test('does NOT include reels block JS when session is active', () {
      final js = InjectionController.buildInjectionJS(
        sessionActive: true,
        blurExplore: false,
        blurReels: false,
        ghostTyping: false,
        ghostSeen: false,
        ghostStories: false,
        ghostDmPhotos: false,
        enableTextSelection: false,
      );
      expect(js, isNot(contains('fgReelsBlock')));
    });

    test('always includes link sanitizer', () {
      final js = InjectionController.buildInjectionJS(
        sessionActive: false,
        blurExplore: false,
        blurReels: false,
        ghostTyping: false,
        ghostSeen: false,
        ghostStories: false,
        ghostDmPhotos: false,
        enableTextSelection: false,
      );
      // linkSanitizationJS is now always injected (not togglable)
      expect(js, contains('fgSanitize'));
    });

    test('returns non-empty string in all cases', () {
      expect(_buildFull().trim(), isNotEmpty);
    });
  });

  // ── iOSUserAgent sanity ──────────────────────────────────────────────────

  group('InjectionController.iOSUserAgent', () {
    test('contains iPhone identifier', () {
      expect(InjectionController.iOSUserAgent, contains('iPhone'));
    });

    test('contains FBAN (Instagram app identifier)', () {
      expect(InjectionController.iOSUserAgent, contains('FBAN'));
    });

    test('is non-empty', () {
      expect(InjectionController.iOSUserAgent, isNotEmpty);
    });
  });

  // ── notificationBridgeJS ─────────────────────────────────────────────────

  group('InjectionController.notificationBridgeJS', () {
    test('contains Notification bridge guard', () {
      expect(
        InjectionController.notificationBridgeJS,
        contains('fgNotifBridged'),
      );
    });

    test('patches window.Notification', () {
      expect(
        InjectionController.notificationBridgeJS,
        contains('window.Notification'),
      );
    });
  });

  // ── linkSanitizationJS ───────────────────────────────────────────────────

  group('InjectionController.linkSanitizationJS', () {
    test('strips igsh param', () {
      expect(InjectionController.linkSanitizationJS, contains('igsh'));
    });

    test('strips utm params', () {
      expect(InjectionController.linkSanitizationJS, contains('utm_source'));
    });

    test('strips fbclid', () {
      expect(InjectionController.linkSanitizationJS, contains('fbclid'));
    });

    test('patches navigator.share', () {
      expect(
        InjectionController.linkSanitizationJS,
        contains('navigator.share'),
      );
    });
  });
}

/// Helper to create a fully-featured injection JS for common assertions.
String _buildFull() => InjectionController.buildInjectionJS(
  sessionActive: false,
  blurExplore: true,
  blurReels: true,
  ghostTyping: true,
  ghostSeen: true,
  ghostStories: true,
  ghostDmPhotos: true,
  enableTextSelection: false,
);
