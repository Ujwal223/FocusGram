import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import '../services/injection_controller.dart';
import '../services/session_manager.dart';
import 'package:provider/provider.dart';

/// An isolated player for a single Reel opened from a DM.
/// Uses JS history interception to lock the user to the initial reel URL.
class ReelPlayerOverlay extends StatefulWidget {
  final String url;
  const ReelPlayerOverlay({super.key, required this.url});

  @override
  State<ReelPlayerOverlay> createState() => _ReelPlayerOverlayState();
}

class _ReelPlayerOverlayState extends State<ReelPlayerOverlay> {
  DateTime? _startTime;

  @override
  void initState() {
    super.initState();
    _startTime = DateTime.now();
  }

  @override
  void dispose() {
    // Record viewing time toward daily count
    if (_startTime != null) {
      final durationSeconds = DateTime.now().difference(_startTime!).inSeconds;
      if (mounted) {
        context.read<SessionManager>().accrueSeconds(durationSeconds);
      }
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, color: Colors.white),
          onPressed: () => Navigator.pop(context),
        ),
        title: const Text(
          'Reel',
          style: TextStyle(color: Colors.white, fontSize: 16),
        ),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: Colors.orange.withValues(alpha: 0.2),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: Colors.orangeAccent, width: 0.5),
              ),
              child: const Text(
                'Locked',
                style: TextStyle(color: Colors.orangeAccent, fontSize: 11),
              ),
            ),
          ),
        ],
      ),
      body: InAppWebView(
        initialUrlRequest: URLRequest(url: WebUri(widget.url)),
        initialSettings: InAppWebViewSettings(
          userAgent: InjectionController.iOSUserAgent,
          mediaPlaybackRequiresUserGesture: true,
          useHybridComposition: true,
          cacheEnabled: true,
          cacheMode: CacheMode.LOAD_CACHE_ELSE_NETWORK,
          domStorageEnabled: true,
          databaseEnabled: true,
          hardwareAcceleration: true,
          transparentBackground: true,
          safeBrowsingEnabled: false,
          supportZoom: false,
          allowsInlineMediaPlayback: true,
          verticalScrollBarEnabled: false,
          horizontalScrollBarEnabled: false,
        ),
        onWebViewCreated: (controller) {
          // Controller is not stored; this overlay is self-contained.
        },
        onLoadStop: (controller, url) async {
          // Set isolated player flag to ensure scroll-lock applies even if a session is active globally
          await controller.evaluateJavascript(
            source: 'window.__focusgramIsolatedPlayer = true;',
          );
          // Apply scroll-lock via MutationObserver: prevents swiping to next reel
          await controller.evaluateJavascript(
            source: InjectionController.reelsMutationObserverJS,
          );
          // Also apply FocusGram baseline CSS (hides bottom nav etc.)
          await controller.evaluateJavascript(
            source: InjectionController.buildInjectionJS(
              sessionActive: true,
              blurExplore: false,
              blurReels: false,
              enableTextSelection: true,
              hideSuggestedPosts: false,
              hideSponsoredPosts: false,
              hideLikeCounts: false,
              hideFollowerCounts: false,
              hideStoriesBar: false,
              hideExploreTab: false,
              hideReelsTab: false,
              hideShopTab: false,
              disableReelsEntirely: false,
            ),
          );
        },
        shouldOverrideUrlLoading: (controller, action) async {
          // Keep this overlay locked to instagram.com pages only
          final uri = action.request.url;
          if (uri == null) return NavigationActionPolicy.CANCEL;
          if (!uri.host.contains('instagram.com')) {
            return NavigationActionPolicy.CANCEL;
          }
          return NavigationActionPolicy.ALLOW;
        },
      ),
    );
  }
}
