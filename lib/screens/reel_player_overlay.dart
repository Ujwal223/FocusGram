import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';
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
  late final WebViewController _controller;
  DateTime? _startTime;

  @override
  void initState() {
    super.initState();
    _startTime = DateTime.now();
    _initWebView();
  }

  void _initWebView() {
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setUserAgent(InjectionController.iOSUserAgent)
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageFinished: (url) {
            // Apply scroll-lock: prevents swiping to next reel in the feed
            _controller.runJavaScript(
              InjectionController.reelScrollLockJS(widget.url),
            );
            // Also hide Instagram's bottom nav inside this overlay
            _controller.runJavaScript(
              InjectionController.buildInjectionJS(
                sessionActive: true,
                blurExplore: false,
              ),
            );
          },
          onNavigationRequest: (request) {
            // Allow only the initial reel URL and instagram.com generally
            final uri = Uri.tryParse(request.url);
            if (uri == null) return NavigationDecision.prevent;
            final host = uri.host;
            if (!host.contains('instagram.com')) {
              return NavigationDecision.prevent;
            }
            return NavigationDecision.navigate;
          },
        ),
      )
      ..loadRequest(Uri.parse(widget.url));
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
      body: WebViewWidget(controller: _controller),
    );
  }
}
