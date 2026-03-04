// test/services/navigation_guard_test.dart
//
// Tests for NavigationGuard — the URL allow/block logic.
//
// Run with: flutter test test/services/navigation_guard_test.dart

import 'package:flutter_test/flutter_test.dart';
import 'package:focusgram/services/navigation_guard.dart';

void main() {
  group('NavigationGuard.evaluate', () {
    // ── Instagram domain — allowed ──────────────────────────────────────────

    test('allows root instagram.com', () {
      final d = NavigationGuard.evaluate(url: 'https://www.instagram.com/');
      expect(d.blocked, isFalse);
    });

    test('allows profile pages', () {
      final d = NavigationGuard.evaluate(
        url: 'https://www.instagram.com/someuser/',
      );
      expect(d.blocked, isFalse);
    });

    test('allows DM inbox', () {
      final d = NavigationGuard.evaluate(
        url: 'https://www.instagram.com/direct/inbox/',
      );
      expect(d.blocked, isFalse);
    });

    test('allows Explore page', () {
      final d = NavigationGuard.evaluate(
        url: 'https://www.instagram.com/explore/',
      );
      expect(d.blocked, isFalse);
    });

    test('allows instagram.com without www', () {
      final d = NavigationGuard.evaluate(
        url: 'https://instagram.com/accounts/login/',
      );
      expect(d.blocked, isFalse);
    });

    test('allows a specific reel URL from a DM share', () {
      final d = NavigationGuard.evaluate(
        url: 'https://www.instagram.com/reel/ABC123xyz/',
      );
      expect(d.blocked, isFalse);
    });

    test('allows login page', () {
      final d = NavigationGuard.evaluate(
        url: 'https://www.instagram.com/accounts/login/',
      );
      expect(d.blocked, isFalse);
    });

    // ── Reels FEED tab — blocked ────────────────────────────────────────────

    test('blocks the reels feed tab /reels/', () {
      final d = NavigationGuard.evaluate(
        url: 'https://www.instagram.com/reels/',
      );
      expect(d.blocked, isTrue);
      expect(d.reason, isNotNull);
    });

    test('blocks the reels feed tab /reels (no trailing slash)', () {
      final d = NavigationGuard.evaluate(
        url: 'https://www.instagram.com/reels',
      );
      expect(d.blocked, isTrue);
    });

    test('blocks the reels feed with our FocusGram query param', () {
      // NavigationDelegate fires this URL from _strictReelsBlockJS
      final d = NavigationGuard.evaluate(
        url: 'https://www.instagram.com/reels/?fg=blocked',
      );
      // NOTE: The guard blocks /reels/ root; query params don't change outcome
      // This is expected to be blocked since it matches _reelsFeedRegex
      expect(d.blocked, isTrue);
    });

    // ── External domains — blocked ──────────────────────────────────────────

    test('blocks external HTTP domains', () {
      final d = NavigationGuard.evaluate(url: 'https://evil.com/phish');
      expect(d.blocked, isTrue);
      expect(d.reason, contains('External domain'));
    });

    test('blocks Facebook redirects', () {
      final d = NavigationGuard.evaluate(
        url: 'https://facebook.com/redirect?url=...',
      );
      expect(d.blocked, isTrue);
    });

    test('blocks l.instagram.com tracking redirect', () {
      final d = NavigationGuard.evaluate(
        url: 'https://l.instagram.com/?u=https%3A%2F%2Fexample.com',
      );
      // l.instagram.com is not in _allowedHosts → blocked
      expect(d.blocked, isTrue);
    });

    // ── Non-HTTP schemes — allowed ──────────────────────────────────────────

    test('allows about:blank', () {
      final d = NavigationGuard.evaluate(url: 'about:blank');
      expect(d.blocked, isFalse);
    });

    test('allows data: URIs', () {
      final d = NavigationGuard.evaluate(url: 'data:text/html,hello');
      expect(d.blocked, isFalse);
    });

    // ── Invalid URL — safe fallback ─────────────────────────────────────────

    test('does not throw on empty string — returns not blocked', () {
      final d = NavigationGuard.evaluate(url: '');
      expect(d.blocked, isFalse);
    });

    test('does not throw on malformed URL', () {
      final d = NavigationGuard.evaluate(url: ':::bad:::');
      expect(d.blocked, isFalse);
    });
  });

  group('NavigationGuard.isSpecificReel', () {
    test('returns true for a real reel URL', () {
      expect(
        NavigationGuard.isSpecificReel(
          'https://www.instagram.com/reel/ABC123/',
        ),
        isTrue,
      );
    });

    test('returns true for reel URL without trailing slash', () {
      expect(
        NavigationGuard.isSpecificReel('https://www.instagram.com/reel/XYZ'),
        isTrue,
      );
    });

    test('returns false for the reels FEED root', () {
      expect(
        NavigationGuard.isSpecificReel('https://www.instagram.com/reels/'),
        isFalse,
      );
    });

    test('returns false for profile URL', () {
      expect(
        NavigationGuard.isSpecificReel('https://www.instagram.com/someuser/'),
        isFalse,
      );
    });

    test('returns false for DM inbox', () {
      expect(
        NavigationGuard.isSpecificReel(
          'https://www.instagram.com/direct/inbox/',
        ),
        isFalse,
      );
    });

    test('returns false for empty string', () {
      expect(NavigationGuard.isSpecificReel(''), isFalse);
    });
  });

  group('BlockDecision', () {
    test('const constructor fields are accessible', () {
      const d = BlockDecision(blocked: true, reason: 'test');
      expect(d.blocked, isTrue);
      expect(d.reason, 'test');
    });

    test('reason can be null', () {
      const d = BlockDecision(blocked: false, reason: null);
      expect(d.reason, isNull);
    });
  });
}
