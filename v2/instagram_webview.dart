import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../injection/script_engine.dart';
import '../injection/script_registry.dart';
import '../channels/channel_registry.dart';
import '../webview/webview_config.dart';

class InstagramWebView extends StatefulWidget {
  const InstagramWebView({super.key});

  @override
  State<InstagramWebView> createState() => InstagramWebViewState();
}

class InstagramWebViewState extends State<InstagramWebView> {
  InAppWebViewController? _controller;
  ScriptEngine? _engine;
  bool _loading = true;

  // ── Public API — call from Settings screen ────────────────────────────────
  Future<void> toggleScript(ScriptId id, bool enabled) async {
    await _engine?.toggle(id, enabled);
  }

  Future<void> setContentFlag(String flag, bool value) async {
    await _engine?.setContentFlag(flag, value);
  }

  Future<void> setOnlineHide(bool enabled) async {
    await _engine?.setOnlineHide(enabled);
  }

  // ─────────────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        InAppWebView(
          initialUrlRequest: WebViewConfig.initialRequest,
          initialSettings: WebViewConfig.settings,

          // ── ContentBlockers — merged base + EasyList rules ──────────────
          contentBlockers: WebViewConfig.baseContentBlockers,
          // TODO Phase 1.5: merge EasyListParser.load() here at startup

          // ── JavaScript channels ─────────────────────────────────────────
          javascriptChannels: ChannelRegistry(
            onActivityEvent: (event) {
              // Forward to history DB in Phase 2
              debugPrint('[Activity] $event');
            },
          ).build(),

          onWebViewCreated: (controller) async {
            _controller = controller;
            final prefs = await SharedPreferences.getInstance();
            _engine = ScriptEngine(controller: controller, prefs: prefs);

            // Inject DOCUMENT_START scripts (ghost mode, etc.)
            await _engine!.initDocumentStartScripts();
          },

          onLoadStop: (controller, url) async {
            // Inject DOCUMENT_END scripts
            await _engine?.injectDocumentEndScripts();
            setState(() => _loading = false);
          },

          onLoadStart: (controller, url) {
            setState(() => _loading = true);
          },

          onProgressChanged: (controller, progress) {
            if (progress >= 80 && _loading) {
              setState(() => _loading = false);
            }
          },

          // ── Navigation policy ───────────────────────────────────────────
          shouldOverrideUrlLoading: (controller, navigationAction) async {
            final url = navigationAction.request.url?.toString() ?? '';

            // Block external redirects — keep user inside instagram.com
            if (!url.contains('instagram.com') &&
                !url.contains('cdninstagram.com') &&
                !url.contains('fbcdn.net') &&
                url.startsWith('http')) {
              // TODO: open in external browser via url_launcher
              return NavigationActionPolicy.CANCEL;
            }

            return NavigationActionPolicy.ALLOW;
          },

          // ── Re-inject on SPA navigation ─────────────────────────────────
          // Instagram is a SPA — URL changes via pushState don't trigger
          // onLoadStop. Re-inject DOM scripts on URL change.
          onUpdateVisitedHistory: (controller, url, isReload) async {
            if (!isReload!) {
              await _engine?.injectDocumentEndScripts();
            }
          },
        ),

        // ── Subtle loading indicator ──────────────────────────────────────
        if (_loading)
          const LinearProgressIndicator(
            minHeight: 2,
            backgroundColor: Colors.transparent,
          ),
      ],
    );
  }
}
