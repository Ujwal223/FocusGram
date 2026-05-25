// lib/services/ghost_mode_service.dart
//
// Three-layer ghost mode:
//   1. AT_DOCUMENT_START JS injection  — overrides fetch/XHR/WS before IG code runs
//   2. shouldInterceptRequest           — native Android intercept (catches SW requests too)
//   3. FLAG_SECURE                      — anti-screenshot at OS level (disabled per user request)
//
// Usage:
//   final service = GhostModeService();
//   await service.load();   // reads saved prefs
//
//   InAppWebView(
//     initialUserScripts: service.buildUserScripts(),
//     onWebViewCreated: (c) => service.onWebViewCreated(c),
//     shouldInterceptRequest: service.shouldInterceptRequest,
//   )
//
//   // Anti-screenshot: disabled per user request
//   // service.applyWindowFlags(context);

import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'ghost_mode_script.dart';

// ─── Feature flags ────────────────────────────────────────────────────────────
class GhostFeatures {
  bool hideStoryViews;
  bool hideReadReceipts;
  bool hideLiveJoin;
  bool hideTypingIndicator;
  bool hideVoiceListened;
  bool hideReplyImageViewed;
  bool disableAnalytics;

  GhostFeatures({
    this.hideStoryViews = true,
    this.hideReadReceipts = true,
    this.hideLiveJoin = true,
    this.hideTypingIndicator = true,
    this.hideVoiceListened = true,
    this.hideReplyImageViewed = true,
    this.disableAnalytics = true,
  });

  static const _keys = {
    'hideStoryViews': 'gm_story',
    'hideReadReceipts': 'gm_read',
    'hideLiveJoin': 'gm_live',
    'hideTypingIndicator': 'gm_typing',
    'hideVoiceListened': 'gm_voice',
    'hideReplyImageViewed': 'gm_reply',
    'disableAnalytics': 'gm_analytics',
  };

  Future<void> save() async {
    final p = await SharedPreferences.getInstance();
    await Future.wait([
      p.setBool(_keys['hideStoryViews']!, hideStoryViews),
      p.setBool(_keys['hideReadReceipts']!, hideReadReceipts),
      p.setBool(_keys['hideLiveJoin']!, hideLiveJoin),
      p.setBool(_keys['hideTypingIndicator']!, hideTypingIndicator),
      p.setBool(_keys['hideVoiceListened']!, hideVoiceListened),
      p.setBool(_keys['hideReplyImageViewed']!, hideReplyImageViewed),
      p.setBool(_keys['disableAnalytics']!, disableAnalytics),
    ]);
  }

  static Future<GhostFeatures> load() async {
    final p = await SharedPreferences.getInstance();
    return GhostFeatures(
      hideStoryViews: p.getBool(_keys['hideStoryViews']!) ?? true,
      hideReadReceipts: p.getBool(_keys['hideReadReceipts']!) ?? true,
      hideLiveJoin: p.getBool(_keys['hideLiveJoin']!) ?? true,
      hideTypingIndicator: p.getBool(_keys['hideTypingIndicator']!) ?? true,
      hideVoiceListened: p.getBool(_keys['hideVoiceListened']!) ?? true,
      hideReplyImageViewed: p.getBool(_keys['hideReplyImageViewed']!) ?? true,
      disableAnalytics: p.getBool(_keys['disableAnalytics']!) ?? true,
    );
  }
}

// ─── Native URL blocklist (mirrors JS side — belt & suspenders) ───────────────
final _nativeBlocklist = [
  RegExp(r'/api/v1/media/seen/'),
  RegExp(r'/api/v1/feed/viewed_story/'),
  RegExp(r'/api/v1/feed/reels_tray/seen/'),
  RegExp(r'/api/v1/direct_v2/threads/[^/]+/mark_item_seen/'),
  RegExp(r'/api/v1/direct_v2/mark_item_seen/'),
  RegExp(r'/api/v1/direct_v2/threads/[^/]+/items/[^/]+/mark_visual_item_seen/'),
  RegExp(r'/api/v1/direct_v2/visual_thread/[^/]+/seen/'),
  RegExp(r'/api/v1/direct_v2/threads/[^/]+/items/[^/]+/mark_audio_seen/'),
  RegExp(r'/api/v1/live/[^/]+/join/'),
  RegExp(r'/api/v1/live/[^/]+/get_join_requests/'),
  RegExp(r'/api/v1/qe/'),
  RegExp(r'/api/v1/launcher/sync/'),
  RegExp(r'/api/v1/logging/'),
  RegExp(r'/api/v1/stats/'),
  RegExp(r'/api/v1/fb_onetap_logging/'),
  RegExp(r'/ajax/bz'),
  RegExp(r'/ajax/logging/'),
];

final Uint8List _fakeOkBody = Uint8List.fromList('{"status":"ok"}'.codeUnits);

// ─── Main service ─────────────────────────────────────────────────────────────
class GhostModeService {
  GhostFeatures features = GhostFeatures();
  InAppWebViewController? _controller;

  Future<void> load() async {
    features = await GhostFeatures.load();
  }

  // ─── WebView setup ────────────────────────────────────────────────────────

  /// Call from InAppWebView.onWebViewCreated
  void onWebViewCreated(InAppWebViewController controller) {
    _controller = controller;
  }

  /// Pass to InAppWebView.initialUserScripts
  /// AT_DOCUMENT_START = injected before ANY page script — critical for
  /// overriding fetch/XHR before Instagram caches original refs.
  List<UserScript> buildUserScripts() {
    if (!_anyGhostEnabled()) return [];
    return [
      UserScript(
        source: _buildConfiguredScript(),
        injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
        forMainFrameOnly: false, // Apply to iframes too
      ),
    ];
  }

  /// Pass to InAppWebView.shouldInterceptRequest
  /// Works at native Android level — catches requests from service workers too.
  Future<WebResourceResponse?> shouldInterceptRequest(
    InAppWebViewController controller,
    WebResourceRequest request,
  ) async {
    if (!_anyGhostEnabled()) return null;
    final path = request.url.path;
    if (_nativeBlocklist.any((re) => re.hasMatch(path))) {
      return WebResourceResponse(
        statusCode: 200,
        reasonPhrase: 'OK',
        contentType: 'application/json',
        headers: {'Content-Type': 'application/json'},
        data: _fakeOkBody,
      );
    }
    return null; // Let through
  }

  /// InAppWebViewSettings required for shouldInterceptRequest to fire
  InAppWebViewSettings buildWebViewSettings() {
    return InAppWebViewSettings(
      useShouldInterceptRequest: true, // Enable native intercept callback
      useShouldOverrideUrlLoading: true,
      javaScriptEnabled: true,
      disableDefaultErrorPage: true,
      useHybridComposition:
          true, // Needed for FLAG_SECURE to work (though disabled)
      // Disable service worker cache that can replay seen-events offline
      cacheEnabled: false, // Start clean — optional, tradeoff vs perf
    );
  }

  // ─── Anti-screenshot ────────────────────────────────────────────────────────
  // Anti-screenshot disabled per user request

  Future<void> applyWindowFlags(BuildContext context) async {
    // Anti-screenshot disabled per user request
    return;
  }

  Future<void> clearWindowFlags() async {
    // Anti-screenshot disabled per user request
    return;
  }

  // ─── Re-inject after page nav (SPA navigation doesn't re-run userScripts) ──

  /// Call from InAppWebView.onLoadStop
  Future<void> onPageLoaded(Uri? url) async {
    if (_controller == null || !_anyGhostEnabled()) return;
    // Re-inject on each navigation — SPA route changes don't re-fire userScripts
    await _controller!.evaluateJavascript(source: _buildConfiguredScript());
  }

  // ─── Private helpers ────────────────────────────────────────────────────────

  bool _anyGhostEnabled() =>
      features.hideStoryViews ||
      features.hideReadReceipts ||
      features.hideLiveJoin ||
      features.hideTypingIndicator ||
      features.hideVoiceListened ||
      features.hideReplyImageViewed ||
      features.disableAnalytics;

  /// Build JS with feature flags baked in — disabled features skip their blocks
  String _buildConfiguredScript() {
    // Prepend a config object that the script reads
    // The kGhostModeJS already handles all features unconditionally.
    // If you need per-feature toggles, swap the const for a builder function.
    //
    // For now: only inject if ghost mode is on at all.
    // Per-feature granularity can be added by replacing URL_BLOCKLIST
    // sections conditionally — left as extension point.
    return '''
      window.__GHOST_CONFIG__ = ${_configJson()};
      $kGhostModeJS
    ''';
  }

  String _configJson() {
    return '''{
      "hideStoryViews":       ${features.hideStoryViews},
      "hideReadReceipts":     ${features.hideReadReceipts},
      "hideLiveJoin":         ${features.hideLiveJoin},
      "hideTypingIndicator":  ${features.hideTypingIndicator},
      "hideVoiceListened":    ${features.hideVoiceListened},
      "hideReplyImageViewed": ${features.hideReplyImageViewed},
      "disableAnalytics":     ${features.disableAnalytics}
    }''';
  }
}
