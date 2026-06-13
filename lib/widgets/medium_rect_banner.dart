import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

/// Adsterra 300×250 medium rectangle banner.
/// Native-looking container, no "AD" label.
/// Best for in-content placements (settings page, panel).

const String _kMediumRectCode = '''
<script>
  atOptions = {
    'key' : '99233324430f9128f2b01c30b6eebc20',
    'format' : 'iframe',
    'height' : 250,
    'width' : 300,
    'params' : {}
  };
</script>
<script src="https://www.highperformanceformat.com/99233324430f9128f2b01c30b6eebc20/invoke.js"></script>
''';

class MediumRectBanner extends StatelessWidget {
  const MediumRectBanner({super.key});

  String get _html =>
      '''
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
    }
    iframe { border:none; max-width:100%; }
  </style>
</head>
<body>$_kMediumRectCode</body>
</html>
''';

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      constraints: const BoxConstraints(maxHeight: 270),
      decoration: BoxDecoration(
        color: Colors.transparent,
        border: Border(
          top: BorderSide(color: Colors.grey.withValues(alpha: 0.08)),
          bottom: BorderSide(color: Colors.grey.withValues(alpha: 0.08)),
        ),
      ),
      child: SizedBox(
        height: 250,
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
