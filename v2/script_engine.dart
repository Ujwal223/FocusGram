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
  }

  // ── Called from onLoadStop: inject all DOCUMENT_END enabled scripts ────────
  Future<void> injectDocumentEndScripts() async {
    for (final script in ScriptRegistry.all
        .where((s) => s.injectionTime == UserScriptInjectionTime.AT_DOCUMENT_END && s.enabled)) {
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

  // ── Online status hide ─────────────────────────────────────────────────────
  Future<void> setOnlineHide(bool enabled) async {
    await prefs.setBool('ghost_online_hide', enabled);
    if (enabled) {
      await controller.evaluateJavascript(
          source: 'window.__fgEnableOnlineHide?.()');
    } else {
      await controller.evaluateJavascript(
          source: 'window.__fgDisableOnlineHide?.()');
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
