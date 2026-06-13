import 'package:flutter_test/flutter_test.dart';

void main() {
  // The regex patterns used in shouldInterceptRequest for DM Ghost blocking.
  // These are the same patterns embedded in main_webview_page.dart.
  final seenPattern = RegExp(
    r'/api/v1/media/[\w-]+/seen/|'
    r'/api/v1/stories/reel/seen/|'
    r'/api/v1/direct_v2/threads/[\w-]+/seen/|'
    r'/api/v1/direct_v2/visual_message/[\w-]+/seen/|'
    r'/api/v1/live/[\w-]+/comment/seen/|'
    r'/api/v1/direct_v2/threads/[^/]+/mark_item_seen/|'
    r'/api/v1/direct_v2/mark_item_seen/|'
    r'/api/v1/direct_v2/threads/[^/]+/items/[^/]+/mark_visual_item_seen/|'
    r'/api/v1/direct_v2/visual_thread/[^/]+/seen/|'
    r'/api/v1/direct_v2/threads/[^/]+/items/[^/]+/mark_audio_seen/|'
    r'/api/v1/live/[^/]+/join/|'
    r'/api/v1/live/[^/]+/get_join_requests/|'
    r'/api/v1/media/seen/|'
    r'/api/v1/feed/viewed_story/|'
    r'/api/v1/feed/reels_tray/seen/|'
    r'/api/v1/qe/|'
    r'/api/v1/launcher/sync/|'
    r'/api/v1/logging/|'
    r'/api/v1/fb_onetap_logging/|'
    r'/ajax/bz|'
    r'/ajax/logging/|'
    r'/api/v1/stats/|'
    r'/api/v1/fbanalytics/',
  );

  group('DM Ghost — Seen endpoint pattern matching', () {
    // ── Story seen endpoints ───────────────────────────────────
    test('blocks /api/v1/media/{id}/seen/', () {
      expect(
        seenPattern.hasMatch(
          'https://www.instagram.com/api/v1/media/12345/seen/',
        ),
        isTrue,
      );
    });

    test('blocks /api/v1/stories/reel/seen/', () {
      expect(
        seenPattern.hasMatch(
          'https://www.instagram.com/api/v1/stories/reel/seen/',
        ),
        isTrue,
      );
    });

    test('blocks /api/v1/feed/viewed_story/', () {
      expect(
        seenPattern.hasMatch(
          'https://www.instagram.com/api/v1/feed/viewed_story/',
        ),
        isTrue,
      );
    });

    test('blocks /api/v1/feed/reels_tray/seen/', () {
      expect(
        seenPattern.hasMatch(
          'https://www.instagram.com/api/v1/feed/reels_tray/seen/',
        ),
        isTrue,
      );
    });

    // ── DM read receipts ──────────────────────────────────────
    test('blocks /api/v1/direct_v2/threads/{id}/mark_item_seen/', () {
      expect(
        seenPattern.hasMatch(
          'https://www.instagram.com/api/v1/direct_v2/threads/abc123/mark_item_seen/',
        ),
        isTrue,
      );
    });

    test('blocks /api/v1/direct_v2/mark_item_seen/', () {
      expect(
        seenPattern.hasMatch(
          'https://www.instagram.com/api/v1/direct_v2/mark_item_seen/',
        ),
        isTrue,
      );
    });

    test('blocks /api/v1/direct_v2/threads/{id}/seen/', () {
      expect(
        seenPattern.hasMatch(
          'https://www.instagram.com/api/v1/direct_v2/threads/abc123/seen/',
        ),
        isTrue,
      );
    });

    test('blocks /api/v1/direct_v2/visual_message/{id}/seen/', () {
      expect(
        seenPattern.hasMatch(
          'https://www.instagram.com/api/v1/direct_v2/visual_message/xyz/seen/',
        ),
        isTrue,
      );
    });

    // ── Ephemeral / visual seen ───────────────────────────────
    test(
      'blocks /api/v1/direct_v2/threads/{id}/items/{id}/mark_visual_item_seen/',
      () {
        expect(
          seenPattern.hasMatch(
            'https://www.instagram.com/api/v1/direct_v2/threads/abc/items/def/mark_visual_item_seen/',
          ),
          isTrue,
        );
      },
    );

    test('blocks /api/v1/direct_v2/visual_thread/{id}/seen/', () {
      expect(
        seenPattern.hasMatch(
          'https://www.instagram.com/api/v1/direct_v2/visual_thread/abc/seen/',
        ),
        isTrue,
      );
    });

    // ── Audio seen ────────────────────────────────────────────
    test('blocks /api/v1/direct_v2/threads/{id}/items/{id}/mark_audio_seen/', () {
      expect(
        seenPattern.hasMatch(
          'https://www.instagram.com/api/v1/direct_v2/threads/abc/items/def/mark_audio_seen/',
        ),
        isTrue,
      );
    });

    // ── Live ──────────────────────────────────────────────────
    test('blocks /api/v1/live/{id}/join/', () {
      expect(
        seenPattern.hasMatch(
          'https://www.instagram.com/api/v1/live/abc123/join/',
        ),
        isTrue,
      );
    });

    test('blocks /api/v1/live/{id}/get_join_requests/', () {
      expect(
        seenPattern.hasMatch(
          'https://www.instagram.com/api/v1/live/abc123/get_join_requests/',
        ),
        isTrue,
      );
    });

    test('blocks /api/v1/live/{id}/comment/seen/', () {
      expect(
        seenPattern.hasMatch(
          'https://www.instagram.com/api/v1/live/abc123/comment/seen/',
        ),
        isTrue,
      );
    });

    // ── Analytics / tracking ──────────────────────────────────
    test('blocks /api/v1/qe/', () {
      expect(
        seenPattern.hasMatch('https://www.instagram.com/api/v1/qe/some_param'),
        isTrue,
      );
    });

    test('blocks /api/v1/launcher/sync/', () {
      expect(
        seenPattern.hasMatch('https://www.instagram.com/api/v1/launcher/sync/'),
        isTrue,
      );
    });

    test('blocks /api/v1/logging/', () {
      expect(
        seenPattern.hasMatch('https://www.instagram.com/api/v1/logging/event'),
        isTrue,
      );
    });

    test('blocks /api/v1/stats/', () {
      expect(
        seenPattern.hasMatch('https://www.instagram.com/api/v1/stats/'),
        isTrue,
      );
    });

    test('blocks /api/v1/fbanalytics/', () {
      expect(
        seenPattern.hasMatch(
          'https://www.instagram.com/api/v1/fbanalytics/event',
        ),
        isTrue,
      );
    });

    test('blocks /ajax/bz', () {
      expect(seenPattern.hasMatch('https://www.instagram.com/ajax/bz'), isTrue);
    });

    test('blocks /ajax/logging/', () {
      expect(
        seenPattern.hasMatch('https://www.instagram.com/ajax/logging/'),
        isTrue,
      );
    });

    // ── Should NOT block legitimate endpoints ─────────────────
    test('does NOT block normal feed timeline request', () {
      expect(
        seenPattern.hasMatch('https://www.instagram.com/api/v1/feed/timeline/'),
        isFalse,
      );
    });

    test('does NOT block graphql queries', () {
      expect(
        seenPattern.hasMatch('https://www.instagram.com/api/graphql'),
        isFalse,
      );
    });

    test('does NOT block direct_v2 inbox', () {
      expect(
        seenPattern.hasMatch(
          'https://www.instagram.com/api/v1/direct_v2/inbox/',
        ),
        isFalse,
      );
    });

    test('does NOT block user posts', () {
      expect(
        seenPattern.hasMatch(
          'https://www.instagram.com/api/v1/users/12345/posts/',
        ),
        isFalse,
      );
    });
  });
}
