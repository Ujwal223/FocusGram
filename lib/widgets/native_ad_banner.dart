import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

// ── Adsterra banner codes ────────────────────────────────────────────
// 320×50 — standard mobile banner, used at bottom of screens
const String _kBanner320x50 = '''
<script>
  atOptions = {
    'key' : 'd00c3602dafbd199f16d4a6426156cd6',
    'format' : 'iframe',
    'height' : 50,
    'width' : 320,
    'params' : {}
  };
</script>
<script src="https://www.highperformanceformat.com/d00c3602dafbd199f16d4a6426156cd6/invoke.js"></script>
''';

/// A small 320×50 banner that loads natively inside the app.
/// Place at the bottom of screens.
class NativeAdBanner extends StatelessWidget {
  final double height;
  final String? customCode;

  const NativeAdBanner({super.key, this.height = 60, this.customCode});

  String get _html {
    final code = customCode ?? _kBanner320x50;
    return '''
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no">
  <style>
    * { margin:0; padding:0; box-sizing:border-box; }
    html, body {
      width:100%; height:100%;
      background:transparent;
      display:flex; align-items:center; justify-content:center;
      overflow:hidden;
    }
    iframe { border:none; max-width:100%; }
  </style>
</head>
<body>$code</body>
</html>
''';
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      // Subtle native look — barely visible border, no "AD" label
      decoration: BoxDecoration(
        color: Colors.transparent,
        border: Border(
          top: BorderSide(color: Colors.grey.withValues(alpha: 0.08)),
        ),
      ),
      child: SizedBox(
        height: height,
        child: InAppWebView(
          initialSettings: InAppWebViewSettings(
            javaScriptEnabled: true,
            domStorageEnabled: true,
            transparentBackground: true,
            cacheEnabled: false,
            safeBrowsingEnabled: false,
            useHybridComposition: true,
          ),
          onWebViewCreated: (c) async {
            await c.loadData(
              data: _html,
              mimeType: 'text/html',
              encoding: 'utf-8',
              baseUrl: WebUri('https://adsterra.com'),
            );
          },
        ),
      ),
    );
  }
}
