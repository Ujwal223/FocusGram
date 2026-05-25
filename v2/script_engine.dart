import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'script_registry.dart';

class ScriptEngine {
  final InAppWebViewController controller;
  final SharedPreferences prefs;

  // Cache raw JS per asset path to avoid repeated rootBundle reads
  final Map<String, String> _cache = {};

  ScriptEngine({required this.controller, required this.prefs});

  // ── Init: restore enabled state from prefs, inject DOCUMENT_START scripts ─
  // Call this from onWebViewCreated (for DOCUMENT_START scripts via addUserScript)
  Future<void> initDocumentStartScripts() async {
    for (final script in ScriptRegistry.all) {
      // Restore enabled state
      final saved = prefs.getBool('script_${script.id.name}');
      if (saved != null) script.enabled = saved;

      if (script.injectionTime == UserScriptInjectionTime.AT_DOCUMENT_START &&
          script.enabled) {
        final code = await _load(script.assetPath);
        if (code == null) continue;
        await controller.addUserScript(
          UserScript(
            source: code,
            injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
            allowedOriginRules: {'https://www.instagram.com'},
          ),
        );
      }
    }

    // Initialize script configurations after scripts are loaded
    await _initializeScriptConfigs();
  }

  // ── Initialize script configurations from saved preferences ────────────────
  Future<void> _initializeScriptConfigs() async {
    // Fetch interceptor config
    final fetchInterceptor = ScriptRegistry.byId(ScriptId.fetchInterceptor);
    if (fetchInterceptor.enabled) {
      await _updateFetchInterceptorConfig();
    }

    // Autoplay blocker config
    final autoplayBlocker = ScriptRegistry.byId(ScriptId.autoplayBlocker);
    if (autoplayBlocker.enabled) {
      await _updateAutoplayBlockerConfig();
    }

    // Content hider flags
    await _pushContentFlags();
  }

  // ── Called from onLoadStop: inject all DOCUMENT_END enabled scripts ────────
  Future<void> injectDocumentEndScripts() async {
    for (final script in ScriptRegistry.all.where(
      (s) =>
          s.injectionTime == UserScriptInjectionTime.AT_DOCUMENT_END &&
          s.enabled,
    )) {
      await _inject(script);
    }
    // After content_hider is injected, push saved content flags
    await _pushContentFlags();
  }

  // ── Toggle a script on/off ─────────────────────────────────────────────────
  Future<void> toggle(ScriptId id, bool enabled) async {
    final script = ScriptRegistry.byId(id);
    script.enabled = enabled;
    await prefs.setBool('script_${id.name}', enabled);

    if (!enabled) {
      if (script.injectionTime == UserScriptInjectionTime.AT_DOCUMENT_START) {
        await controller.removeUserScriptsByGroupName(id.name);
      }
      // For DOM scripts: reload so mutations stop
      await controller.reload();
      return;
    }

    if (script.injectionTime == UserScriptInjectionTime.AT_DOCUMENT_START) {
      final code = await _load(script.assetPath);
      if (code == null) return;
      await controller.addUserScript(
        UserScript(
          source: code,
          injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
          groupName: id.name,
          allowedOriginRules: {'https://www.instagram.com'},
        ),
      );
      await controller.reload();
    } else {
      await _inject(script);
    }

    // Re-initialize configurations after toggle
    await _initializeScriptConfigs();
  }

  // ── Content hider flags ────────────────────────────────────────────────────
  Future<void> setContentFlag(String flag, bool value) async {
    await prefs.setBool('content_$flag', value);
    await _pushContentFlags();
  }

  Future<void> _pushContentFlags() async {
    final contentHider = ScriptRegistry.byId(ScriptId.contentHider);
    if (!contentHider.enabled) return;

    final flags = {
      'stories': prefs.getBool('content_stories') ?? false,
      'posts': prefs.getBool('content_posts') ?? false,
      'reels': prefs.getBool('content_reels') ?? false,
      'suggested': prefs.getBool('content_suggested') ?? false,
    };
    await controller.evaluateJavascript(
      source: 'window.__fgContent?.applyAll(${jsonEncode(flags)})',
    );
  }

  // ── Fetch interceptor configuration ────────────────────────────────────────
  Future<void> setFetchInterceptorConfig({
    bool? blockAds,
    bool? blockSponsored,
    bool? blockSuggested,
    bool? blockVideos,
    bool? blockAutoplay,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    final config = {
      'blockAds': blockAds ?? prefs.getBool('fetch_block_ads') ?? false,
      'blockSponsored':
          blockSponsored ?? prefs.getBool('fetch_block_sponsored') ?? false,
      'blockSuggested':
          blockSuggested ?? prefs.getBool('fetch_block_suggested') ?? false,
      'blockVideos':
          blockVideos ?? prefs.getBool('fetch_block_videos') ?? false,
      'blockAutoplay':
          blockAutoplay ?? prefs.getBool('fetch_block_autoplay') ?? false,
    };

    // Save individual prefs
    await prefs.setBool('fetch_block_ads', config['blockAds']!);
    await prefs.setBool('fetch_block_sponsored', config['blockSponsored']!);
    await prefs.setBool('fetch_block_suggested', config['blockSuggested']!);
    await prefs.setBool('fetch_block_videos', config['blockVideos']!);
    await prefs.setBool('fetch_block_autoplay', config['blockAutoplay']!);

    // Apply to webview
    await controller.evaluateJavascript(
      source: 'window.__fgSetFilterConfig?.(${jsonEncode(config)})',
    );
  }

  Future<void> _updateFetchInterceptorConfig() async {
    final prefs = await SharedPreferences.getInstance();
    await setFetchInterceptorConfig(
      blockAds: prefs.getBool('fetch_block_ads'),
      blockSponsored: prefs.getBool('fetch_block_sponsored'),
      blockSuggested: prefs.getBool('fetch_block_suggested'),
      blockVideos: prefs.getBool('fetch_block_videos'),
      blockAutoplay: prefs.getBool('fetch_block_autoplay'),
    );
  }

  // ── Autoplay blocker configuration ─────────────────────────────────────────
  Future<void> setAutoplayBlockerEnabled(bool enabled) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('autoplay_blocker_enabled', enabled);

    // Apply to webview
    await controller.evaluateJavascript(
      source: 'window.__fgSetBlockAutoplay?.(${jsonEncode(enabled)})',
    );
  }

  Future<void> _updateAutoplayBlockerConfig() async {
    final prefs = await SharedPreferences.getInstance();
    await setAutoplayBlockerEnabled(
      prefs.getBool('autoplay_blocker_enabled') ?? false,
    );
  }

  // ── Online status hide ─────────────────────────────────────────────────────
  Future<void> setOnlineHide(bool enabled) async {
    await prefs.setBool('ghost_online_hide', enabled);
    if (enabled) {
      await controller.evaluateJavascript(
        source: 'window.__fgEnableOnlineHide?.()',
      );
    } else {
      await controller.evaluateJavascript(
        source: 'window.__fgDisableOnlineHide?.()',
      );
    }
  }

  // ── Private helpers ────────────────────────────────────────────────────────
  Future<void> _inject(InstaScript script) async {
    final code = await _load(script.assetPath);
    if (code == null) return;
    try {
      await controller.evaluateJavascript(source: code);
    } catch (e) {
      // Script failed — log but don't crash
      debugPrint('[ScriptEngine] Failed to inject ${script.id.name}: $e');
    }
  }

  Future<String?> _load(String assetPath) async {
    if (_cache.containsKey(assetPath)) return _cache[assetPath];
    try {
      final code = await rootBundle.loadString(assetPath);
      _cache[assetPath] = code;
      return code;
    } catch (e) {
      debugPrint('[ScriptEngine] Asset not found: $assetPath');
      return null;
    }
  }
}
