// android/app/src/main/kotlin/com/focusgram/focusgram/MainActivity.kt
//
// Adds:
//   1. Platform channel for FLAG_SECURE (anti-screenshot at OS level)
//   2. Ghost mode WebView integration notes

package com.focusgram.focusgram

import android.os.Bundle
import android.view.WindowManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val CHANNEL = "com.focusgram/window_flags"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "setSecure" -> {
                    val secure = call.argument<Boolean>("secure") ?: false
                    if (secure) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// WEBVIEW WIDGET INTEGRATION
// ─────────────────────────────────────────────────────────────────────────────
//
// In your WebView widget (wherever InAppWebView is constructed):
//
// class InstagramWebView extends StatefulWidget { ... }
//
// class _InstagramWebViewState extends State<InstagramWebView> {
//   late GhostModeService _ghost;
//
//   @override
//   void initState() {
//     super.initState();
//     _ghost = GhostModeService();
//     _ghost.load().then((_) {
//       WidgetsBinding.instance.addPostFrameCallback((_) {
//         _ghost.applyWindowFlags(context);
//       });
//       setState(() {});
//     });
//   }
//
//   @override
//   Widget build(BuildContext context) {
//     return InAppWebView(
//       initialUrlRequest: URLRequest(
//         url: WebUri('https://www.instagram.com'),
//       ),
//       initialSettings: _ghost.buildWebViewSettings(),
//       initialUserScripts: UnmodifiableListView(_ghost.buildUserScripts()),
//       onWebViewCreated: (controller) {
//         _ghost.onWebViewCreated(controller);
//       },
//       onLoadStop: (controller, url) async {
//         await _ghost.onPageLoaded(url?.uriValue);
//       },
//       shouldInterceptRequest: (controller, request) {
//         return _ghost.shouldInterceptRequest(controller, request);
//       },
//     );
//   }
// }
//
// ─────────────────────────────────────────────────────────────────────────────
// PUBSPEC ADDITIONS
// ─────────────────────────────────────────────────────────────────────────────
//
// dependencies:
//   flutter_inappwebview: ^6.1.5   # already present
//   shared_preferences: ^2.3.0
//
// ─────────────────────────────────────────────────────────────────────────────
// DEBUGGING: HOW TO VERIFY GHOST MODE WORKING
// ─────────────────────────────────────────────────────────────────────────────
//
// 1. Enable WebView remote debugging:
//    In main.dart: if (kDebugMode) { InAppWebViewController.setWebContentsDebuggingEnabled(true); }
//
// 2. Open chrome://inspect in desktop Chrome while app runs on USB device.
//
// 3. In DevTools console, run:
//    window.fetch('/api/v1/media/seen/test/', {method:'POST'})
//      .then(r => r.text()).then(console.log)
//    → Should print: {"status":"ok"}   (blocked, not sent)
//
// 4. Check Network tab — blocked requests should NOT appear (they resolve locally).
//
// 5. For story view test: open a Story, check Network tab for any request to
//    /media/seen/ or /viewed_story/ — should be absent.
