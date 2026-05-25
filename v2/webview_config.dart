import 'package:flutter_inappwebview/flutter_inappwebview.dart';

class WebViewConfig {
  // ── User agent — exactly as user specified ────────────────────────────────
  static const String userAgent =
      'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) '
      'AppleWebKit/605.1.15 (KHTML, like Gecko) '
      'Version/26.0 Mobile/15E148 Safari/604.1';

  static const String instagramUrl = 'https://www.instagram.com/';

  // ── Base InAppWebView settings ────────────────────────────────────────────
  static InAppWebViewSettings get settings => InAppWebViewSettings(
    // Identity
    userAgent: userAgent,

    // Performance
    hardwareAcceleration: true,
    // useHybridComposition: false breaks some Android 12+ devices — keep true
    useHybridComposition: true,
    cacheEnabled: true,
    cacheMode: CacheMode.LOAD_DEFAULT,

    // Media
    mediaPlaybackRequiresUserGesture: false,
    allowsInlineMediaPlayback: true,
    allowsPictureInPictureMediaPlayback: true,

    // UX — feel like native, not browser
    overScrollMode: OverScrollMode.NEVER,
    verticalScrollBarEnabled: false,
    horizontalScrollBarEnabled: false,
    supportZoom: false,
    builtInZoomControls: false,
    displayZoomControls: false,
    scrollsToTop: true,

    // JS & storage — IG needs all of these
    javaScriptEnabled: true,
    javaScriptCanOpenWindowsAutomatically: false,
    domStorageEnabled: true,
    databaseEnabled: true,
    allowFileAccessFromFileURLs: false,
    allowUniversalAccessFromFileURLs: false,

    // Compat
    mixedContentMode: MixedContentMode.COMPATIBILITY_MODE,
    safeBrowsingEnabled:
        false, // IG known-safe domain, no need for extra latency
    // Disable Chrome custom tabs popup (links open in WebView)
    suppressesIncrementalRendering: false,

    // iOS specific
    allowsBackForwardNavigationGestures: true,
    allowsLinkPreview: false,
    isFraudulentWebsiteWarningEnabled: false,

    // Android specific
    forceDark: ForceDark.AUTO, // respect system dark mode
    algorithmicDarkeningAllowed: true,
  );

  // ── ContentBlocker rules — ad network blocking ─────────────────────────
  // These are baked-in rules targeting known ad/tracking domains.
  // Full EasyList parsing is handled separately and merged at runtime.
  // This set is always-on regardless of user toggle.
  static List<ContentBlocker> get baseContentBlockers => [
    // Meta ad infrastructure
    _block('.*connect\\.facebook\\.net.*'),
    _block('.*graph\\.facebook\\.com.*ads.*'),
    _block('.*an\\.facebook\\.com.*'),

    // Google ad networks
    _block('.*doubleclick\\.net.*'),
    _block('.*googleadservices\\.com.*'),
    _block('.*googlesyndication\\.com.*'),
    _block('.*adservice\\.google\\..*'),

    // Common trackers
    _block('.*scorecardresearch\\.com.*'),
    _block('.*quantserve\\.com.*'),
    _block('.*chartbeat\\.com.*'),
    _block('.*newrelic\\.com.*'),

    // Ad servers
    _block('.*ads\\.yahoo\\.com.*'),
    _block('.*advertising\\.com.*'),
    _block('.*adnxs\\.com.*'),
    _block('.*adsrvr\\.org.*'),
    _block('.*taboola\\.com.*'),
    _block('.*outbrain\\.com.*'),
    _block('.*pubmatic\\.com.*'),
    _block('.*rubiconproject\\.com.*'),
    _block('.*openx\\.net.*'),
    _block('.*casalemedia\\.com.*'),
    _block('.*criteo\\.com.*'),
    _block('.*criteo\\.net.*'),

    // Pixel trackers
    _block('.*pixel\\.quantserve\\.com.*'),
    _block('.*pixel\\.facebook\\.com.*'),

    // IG-specific ad endpoints (safe to block — don't affect core IG)
    _block('.*\\.instagram\\.com.*\\/ads\\/.*'),
  ];

  static ContentBlocker _block(String pattern) => ContentBlocker(
    trigger: ContentBlockerTrigger(urlFilter: pattern),
    action: ContentBlockerAction(type: ContentBlockerActionType.BLOCK),
  );

  // ── URLRequest for initial load ───────────────────────────────────────────
  static URLRequest get initialRequest => URLRequest(
    url: WebUri(instagramUrl),
    headers: {'Accept-Language': 'en-US,en;q=0.9', 'DNT': '1'},
  );
}
