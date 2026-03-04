/// Determines whether a navigation request should be blocked.
///
/// Rules:
/// - instagram.com/reels  (and /reels/) = BLOCKED — this is the mindless feed tab
/// - instagram.com/reel/SHORTCODE/       = ALLOWED — a specific reel (e.g. from a DM)
/// - /explore/ is allowed (explore content is blurred via CSS instead)
/// - Only instagram.com domains are allowed (blocks external redirects)
class NavigationGuard {
  static const _allowedHosts = ['instagram.com', 'www.instagram.com'];

  /// Regex matching the Reels FEED root — NOT individual reels.
  /// The `(/|\?|$)` suffix ensures query params (e.g. ?fg=blocked) still match.
  static final _reelsFeedRegex = RegExp(
    r'instagram\.com/reels(/|\?|$)',
    caseSensitive: false,
  );

  /// Regex matching a specific reel (e.g. /reel/ABC123/).
  static final _specificReelRegex = RegExp(
    r'instagram\.com/reel/[^/?#]+',
    caseSensitive: false,
  );

  /// Returns a [BlockDecision] for the given [url].
  static BlockDecision evaluate({required String url}) {
    Uri uri;
    try {
      uri = Uri.parse(url);
    } catch (_) {
      return const BlockDecision(blocked: false, reason: null);
    }

    // Allow non-HTTP schemes (about:blank, data:, etc.)
    if (!uri.scheme.startsWith('http')) {
      return const BlockDecision(blocked: false, reason: null);
    }

    // Block non-Instagram domains (prevents phishing/external redirects)
    final host = uri.host.toLowerCase();
    if (!_allowedHosts.any((h) => host == h)) {
      return BlockDecision(
        blocked: true,
        reason: 'External domain blocked: $host',
      );
    }

    // Block ONLY the Reels feed tab root (/reels, /reels/)
    // but allow specific reels (/reel/SHORTCODE/) opened from DMs
    if (_reelsFeedRegex.hasMatch(url)) {
      return const BlockDecision(
        blocked: true,
        reason:
            'Reels feed is disabled — open a specific reel from DMs instead',
      );
    }

    return const BlockDecision(blocked: false, reason: null);
  }

  /// True if the URL is a specific individual reel (from a DM share).
  static bool isSpecificReel(String url) => _specificReelRegex.hasMatch(url);
}

class BlockDecision {
  final bool blocked;
  final String? reason;
  const BlockDecision({required this.blocked, required this.reason});
}
