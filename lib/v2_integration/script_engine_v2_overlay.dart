import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'script_registry_v2_overlay.dart';

class ScriptEngineV2Overlay {
  final InAppWebViewController controller;
  final SharedPreferences prefs;

  final Map<String, String> _cache = {};

  ScriptEngineV2Overlay({required this.controller, required this.prefs});

  Future<void> initDocumentStartScripts() async {
    for (final s in V2OverlayScriptRegistry.all) {
      final enabled = _getEnabled(s.id);
      s.enabled = enabled;

      if (!enabled) continue;

      if (s.injectionTime == UserScriptInjectionTime.AT_DOCUMENT_START) {
        final code = await _load(s.assetPath);
        if (code == null) continue;

        await controller.addUserScript(
          userScript: UserScript(
            source: code,
            injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
            allowedOriginRules: {'https://www.instagram.com'},
          ),
        );
      }
    }
  }

  Future<void> injectDocumentEndScripts() async {
    for (final s in V2OverlayScriptRegistry.all) {
      final enabled = _getEnabled(s.id);
      s.enabled = enabled;
      if (!enabled) continue;

      if (s.injectionTime == UserScriptInjectionTime.AT_DOCUMENT_END) {
        final code = await _load(s.assetPath);
        if (code == null) continue;
        try {
          await controller.evaluateJavascript(source: code);
        } catch (_) {
          // Best-effort injection; never crash UI.
        }
      }
    }

    await _pushContentFlagsIfNeeded();
  }

  Future<void> toggle(V2OverlayScriptId id, bool enabled) async {
    await prefs.setBool(_enabledKey(id), enabled);

    // For DOCUMENT_START scripts, require reload for clean removal.
    if (V2OverlayScriptRegistry.byId(id).injectionTime ==
        UserScriptInjectionTime.AT_DOCUMENT_START) {
      await controller.reload();
      return;
    }

    // For DOCUMENT_END scripts: just reload too to ensure DOM effects stop.
    await controller.reload();
  }

  bool _getEnabled(V2OverlayScriptId id) {
    return prefs.getBool(_enabledKey(id)) ??
        (id == V2OverlayScriptId.themeDetector);
  }

  String _enabledKey(V2OverlayScriptId id) => 'fg_v2_${id.name}_enabled';

  Future<void> _pushContentFlagsIfNeeded() async {
    final contentScriptEnabled = _getEnabled(V2OverlayScriptId.contentHider);

    final contentFlags = <String, bool>{
      'stories': prefs.getBool('content_stories') ?? false,
      'posts': prefs.getBool('content_posts') ?? false,
      'reels': prefs.getBool('content_reels') ?? false,
      'suggested': prefs.getBool('content_suggested') ?? false,
    };

    // Apply DOM content hider flags
    if (contentScriptEnabled) {
      await controller.evaluateJavascript(
        source: 'window.__fgContent?.applyAll(${jsonEncode(contentFlags)});',
      );
    }

    // Also push network filter flags used by fetch_interceptor.js
    // so toggles actually affect request/response behavior.
    final noAds =
        (prefs.getBool('no_ads') ?? false) ||
        (prefs.getBool(_enabledKey(V2OverlayScriptId.adBlockerDom)) ?? false);
    final blockFeedPosts = contentFlags['posts'] ?? false;
    final blockSuggested = contentFlags['suggested'] ?? false;
    final blockReels = contentFlags['reels'] ?? false;
    final blockAutoplay =
        prefs.getBool(_enabledKey(V2OverlayScriptId.autoplayBlocker)) ?? false;

    await controller.evaluateJavascript(
      source:
          'window.__fgSetFilterConfig?.(${jsonEncode({
            // Strictly requested: when Hide Feed Posts is ON, block ALL graphql/query.
            'blockGraphQLQueryWhenFeedPosts': blockFeedPosts,

            // Ads blocker: use existing FocusGram "noAds" toggle (wired elsewhere in prefs).
            'blockAds': noAds,
            'blockSponsored': noAds,

            'blockSuggested': blockSuggested,

            // Keep video blocking controlled by existing toggles if desired.
            'blockVideos': blockReels,
            'blockAutoplay': blockAutoplay,
          })});',
    );

    await controller.evaluateJavascript(
      source: 'window.__fgSetBlockAutoplay?.($blockAutoplay);',
    );
  }

  Future<String?> _load(String assetPath) async {
    if (_cache.containsKey(assetPath)) return _cache[assetPath];
    try {
      final code = await rootBundle.loadString(assetPath);
      _cache[assetPath] = code;
      return code;
    } catch (_) {
      return null;
    }
  }
}
