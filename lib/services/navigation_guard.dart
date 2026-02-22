/// Determines whether a navigation request should be blocked.
///
/// Rules:
/// - /reels/* and /reel/* are blocked unless [sessionActive] is true OR
///   [isDmReelException] is true (single DM reel open).
/// - /explore/ is allowed (but explore content is blurred via CSS).
/// - Only instagram.com domains are allowed (blocks external redirects).
class NavigationGuard {
  static const _allowedHosts = ['instagram.com', 'www.instagram.com'];

  static const _blockedPathPrefixes = ['/reels', '/reel/'];

  /// Returns a [BlockDecision] for the given [url].
  static BlockDecision evaluate({
    required String url,
    required bool sessionActive,
    required bool isDmReelException,
  }) {
    Uri uri;
    try {
      uri = Uri.parse(url);
    } catch (_) {
      return BlockDecision(blocked: false, reason: null);
    }

    // Allow non-HTTP schemes (about:blank, data:, etc.)
    if (!uri.scheme.startsWith('http')) {
      return BlockDecision(blocked: false, reason: null);
    }

    // Block non-Instagram domains (prevents phishing redirects)
    final host = uri.host.toLowerCase();
    if (!_allowedHosts.any((h) => host == h || host.endsWith('.$h'))) {
      return BlockDecision(
        blocked: true,
        reason: 'External domain blocked: $host',
      );
    }

    // Check reel/reels path
    final path = uri.path.toLowerCase();
    final isReelUrl = _blockedPathPrefixes.any((p) => path.startsWith(p));

    if (isReelUrl) {
      if (sessionActive || isDmReelException) {
        return BlockDecision(blocked: false, reason: null);
      }
      return BlockDecision(
        blocked: true,
        reason: 'Reel navigation blocked — no active session',
      );
    }

    return BlockDecision(blocked: false, reason: null);
  }

  /// Returns true if the URL looks like a Reel link from a DM.
  static bool isDmReelLink(String url) {
    try {
      final uri = Uri.parse(url);
      final path = uri.path.toLowerCase();
      return path.startsWith('/reel/') || path.startsWith('/reels/');
    } catch (_) {
      return false;
    }
  }

  /// Extracts a canonical single-reel URL from a DM reel link.
  /// Strips query params that might trigger Reels feed.
  static String? canonicalizeDmReelUrl(String url) {
    try {
      final uri = Uri.parse(url);
      // Keep only the reel path, strip all query parameters
      return Uri(
        scheme: 'https',
        host: 'www.instagram.com',
        path: uri.path,
      ).toString();
    } catch (_) {
      return null;
    }
  }
}

class BlockDecision {
  final bool blocked;
  final String? reason;
  const BlockDecision({required this.blocked, required this.reason});
}
