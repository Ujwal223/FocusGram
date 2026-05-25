import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../injection/script_engine.dart';
import '../injection/script_registry.dart';
import '../channels/channel_registry.dart';
import '../webview/webview_config.dart';
import '../services/ghost_mode_service.dart';

class InstagramWebView extends StatefulWidget {
  const InstagramWebView({super.key});

  @override
  State<InstagramWebView> createState() => InstagramWebViewState();
}

class InstagramWebViewState extends State<InstagramWebView> {
  InAppWebViewController? _controller;
  ScriptEngine? _engine;
  GhostModeService? _ghostMode;
  bool _loading = true;

  // ── Public API — call from Settings screen ─────────────────────────────
  Future<void> toggleScript(ScriptId id, bool enabled) async {
    await _engine?.toggle(id, enabled);
  }

  Future<void> setContentFlag(String flag, bool value) async {
    await _engine?.setContentFlag(flag, value);
  }

  Future<void> setOnlineHide(bool enabled) async {
    await _engine?.setOnlineHide(enabled);
  }

  // Ghost mode controls
  Future<void> setGhostModeEnabled(bool enabled) async {
    if (_ghostMode != null) {
      _ghostMode!.features.disableAnalytics = enabled;
      _ghostMode!.features.hideStoryViews = enabled;
      _ghostMode!.features.hideReadReceipts = enabled;
      _ghostMode!.features.hideLiveJoin = enabled;
      _ghostMode!.features.hideTypingIndicator = enabled;
      _ghostMode!.features.hideVoiceListened = enabled;
      _ghostMode!.features.hideReplyImageViewed = enabled;
      await _ghostMode!.features.save();
      // Reapply settings if webview exists
      if (_controller != null) {
        // Force reload to apply new settings
        await _controller!.reload();
      }
    }
  }

  Future<void> setAntiScreenshot(bool enabled) async {
    if (_ghostMode != null) {
      _ghostMode!.features.antiScreenshot = enabled;
      await _ghostMode!.features.save();
      if (_ghostMode!.features.antiScreenshot) {
        await _ghostMode!.applyWindowFlags(context);
      } else {
        await _ghostMode!.clearWindowFlags();
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        InAppWebView(
          initialUrlRequest: WebViewConfig.initialRequest,
          initialSettings:
              _ghostMode?.buildWebViewSettings() ?? WebViewConfig.settings,

          // ── ContentBlockers — merged base + EasyList rules ──────────────
          contentBlockers: WebViewConfig.baseContentBlockers,

          // ── User Scripts — AT_DOCUMENT_START critical for ghost mode ─────
          initialUserScripts: UnmodifiableListView(
            _ghostMode?.buildUserScripts() ?? [],
          ),

          // ── JavaScript channels ─────────────────────────────────────────
          javascriptChannels: ChannelRegistry(
            onActivityEvent: (event) {
              // Forward to history DB in Phase 2
              debugPrint('[Activity] $event');
            },
          ).build(),

          onWebViewCreated: (controller) async {
            _controller = controller;

            //Interceptor for adblock
            shouldInterceptRequest:
            (controller, request) async {
              final url = request.url.toString();

              const adDomains = [
                'an.facebook.com',
                'connect.facebook.net',
                'pixel.facebook.com',
                'graph.facebook.com/logging',
                'www.instagram.com/ajax/bz',
                'www.instagram.com/api/v1/web/comet/logcalls',
                'doubleclick.net',
                'googletagmanager.com',
                'scorecardresearch.com',
              ];

              if (adDomains.any(url.contains)) {
                return WebResourceResponse(
                  contentType: 'application/json',
                  httpStatus: WebResourceResponseHTTPStatus(statusCode: 200),
                  data: Uint8List.fromList(utf8.encode('{}')),
                );
              }
              return null;
            };

            // Initialize GhostModeService
            _ghostMode = GhostModeService();
            await _ghostMode!.load();

            // Initialize existing script engine for other scripts
            final prefs = await SharedPreferences.getInstance();
            _engine = ScriptEngine(controller: controller, prefs: prefs);

            // Inject DOCUMENT_START scripts (ghost mode, etc.)
            await _engine!.initDocumentStartScripts();
          },

          onLoadStop: (controller, url) async {
            // Inject DOCUMENT_END scripts
            await _engine?.injectDocumentEndScripts();

            // Re-inject ghost mode scripts on SPA navigation
            await _ghostMode?.onPageLoaded(url?.uriValue);

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

          // ── Native intercept for service worker requests ────────────────
          shouldInterceptRequest: (controller, request) async {
            return await _ghostMode?.shouldInterceptRequest(
                  controller,
                  request,
                ) ??
                null;
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
