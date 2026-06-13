import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:url_launcher/url_launcher.dart';

/// Full-screen ad page. User MUST click the ad to earn the reward.
///
/// Flow:
/// 1. Ad loads in WebView for 20s
/// 2. User taps the ad → opens in external browser via url_launcher
/// 3. Timer continues counting to 20s regardless
/// 4. After 20s, "Continue & Earn Reward" button unlocks if BOTH ads clicked
/// 5. If ads not clicked within time, a Retry button appears to reload

const String _kAdHtml = '''
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no">
  <style>
    * { margin:0; padding:0; box-sizing:border-box; }
    html,body { width:100%; height:100%; background:#111; display:flex; flex-direction:column; align-items:center; justify-content:space-around; }
    .ad-slot { width:100%; text-align:center; }
  </style>
</head>
<body>
  <div class="ad-slot">
    <div style="color:#666;font-size:10px;margin-bottom:4px;">Ad 1</div>
<script async="async" data-cfasync="false" src="https://pl18364273.effectivecpmnetwork.com/e8a9b107824c939fb63d96c218c1336a/invoke.js"></script>
<div id="container-e8a9b107824c939fb63d96c218c1336a"></div>
  </div>
  <div class="ad-slot">
    <div style="color:#666;font-size:10px;margin-bottom:4px;">Ad 2</div>
<script>
  atOptions = {'key':'99233324430f9128f2b01c30b6eebc20','format':'iframe','height':250,'width':300,'params':{}};
</script>
<script src="https://www.highperformanceformat.com/99233324430f9128f2b01c30b6eebc20/invoke.js"></script>
  </div>
</body>
</html>
''';

class AdsterraAdScreen extends StatefulWidget {
  final String sessionType;
  final int requiredSeconds;

  const AdsterraAdScreen({
    super.key,
    required this.sessionType,
    this.requiredSeconds = 20,
  });

  @override
  State<AdsterraAdScreen> createState() => _AdsterraAdScreenState();
}

class _AdsterraAdScreenState extends State<AdsterraAdScreen> {
  int _elapsed = 0;
  Timer? _timer;
  int _adsClicked = 0; // count of ad clicks (need 2 for reward)
  bool _retrying = false;
  InAppWebViewController? _webController;

  @override
  void initState() {
    super.initState();
    _startTimer();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _startTimer() {
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() => _elapsed++);
    });
  }

  Future<void> _retry() async {
    setState(() {
      _retrying = true;
      _elapsed = 0;
      _adsClicked = 0;
    });
    _startTimer();
    try {
      await _webController?.loadData(
        data: _kAdHtml,
        mimeType: 'text/html',
        encoding: 'utf-8',
        baseUrl: WebUri('https://adsterra.com'),
      );
    } catch (_) {}
    if (mounted) setState(() => _retrying = false);
  }

  @override
  Widget build(BuildContext context) {
    final timerDone = _elapsed >= widget.requiredSeconds;
    final bothClicked = _adsClicked >= 2;
    final done = timerDone && bothClicked;

    // When timer expired but ads not clicked, wait a bit then allow skip
    final canSkip = timerDone && !bothClicked;

    String statusText;
    Color statusColor;
    if (bothClicked && timerDone) {
      statusText = 'Ready!';
      statusColor = Colors.greenAccent;
    } else if (bothClicked) {
      statusText = 'Both ads clicked! Waiting for timer…';
      statusColor = Colors.greenAccent;
    } else {
      statusText = 'Tap BOTH ads below to earn XP ($_adsClicked/2)';
      statusColor = Colors.white.withValues(alpha: 0.4);
    }

    String buttonText;
    bool buttonEnabled;
    VoidCallback? buttonAction;

    if (done) {
      buttonText = 'Continue & Earn Reward';
      buttonEnabled = true;
      buttonAction = () => Navigator.pop(context, true);
    } else if (timerDone && !bothClicked) {
      buttonText = 'Tap both ads to continue';
      buttonEnabled = false;
      buttonAction = null;
    } else {
      final remaining = widget.requiredSeconds - _elapsed;
      buttonText = 'Wait ${remaining > 0 ? remaining : 0}s';
      buttonEnabled = false;
      buttonAction = null;
    }

    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Column(
          children: [
            // Top bar
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Row(
                children: [
                  const Icon(Icons.videocam, color: Colors.white54, size: 18),
                  const SizedBox(width: 8),
                  const Text(
                    'Sponsored',
                    style: TextStyle(color: Colors.white54, fontSize: 13),
                  ),
                  const Spacer(),
                  Text(
                    '${_elapsed.clamp(0, widget.requiredSeconds)}s / ${widget.requiredSeconds}s',
                    style: TextStyle(
                      color: done ? Colors.greenAccent : Colors.white54,
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
            // Progress bar
            ClipRRect(
              borderRadius: BorderRadius.circular(999),
              child: LinearProgressIndicator(
                value: (_elapsed / widget.requiredSeconds).clamp(0.0, 1.0),
                minHeight: 3,
                backgroundColor: Colors.white12,
                valueColor: AlwaysStoppedAnimation<Color>(
                  done ? Colors.greenAccent : Colors.blueAccent,
                ),
              ),
            ),
            // Hint text
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 6),
              child: Text(
                statusText,
                style: TextStyle(color: statusColor, fontSize: 11),
              ),
            ),
            // Ad WebView
            Expanded(
              child: InAppWebView(
                initialSettings: InAppWebViewSettings(
                  javaScriptEnabled: true,
                  domStorageEnabled: true,
                  useHybridComposition: true,
                  transparentBackground: true,
                  cacheEnabled: false,
                  safeBrowsingEnabled: false,
                ),
                onWebViewCreated: (c) async {
                  _webController = c;
                  await c.loadData(
                    data: _kAdHtml,
                    mimeType: 'text/html',
                    encoding: 'utf-8',
                    baseUrl: WebUri('https://adsterra.com'),
                  );
                },
                onLoadStop: (_, url) {
                  // ad loaded
                },
                shouldOverrideUrlLoading: (controller, nav) async {
                  final url = nav.request.url?.toString() ?? '';
                  if (url.isNotEmpty &&
                      !url.contains('adsterra.com') &&
                      !url.startsWith('about:')) {
                    if (_adsClicked < 2) _adsClicked++;
                    if (mounted) setState(() {});
                    await launchUrl(
                      Uri.parse(url),
                      mode: LaunchMode.externalApplication,
                    );
                    return NavigationActionPolicy.CANCEL;
                  }
                  return NavigationActionPolicy.ALLOW;
                },
              ),
            ),
            // Button area
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  SizedBox(
                    width: double.infinity,
                    height: 52,
                    child: ElevatedButton.icon(
                      onPressed: buttonEnabled ? buttonAction : null,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: done
                            ? Colors.greenAccent
                            : Colors.grey,
                        foregroundColor: done ? Colors.black : Colors.white38,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(14),
                        ),
                      ),
                      icon: Icon(
                        done ? Icons.check_circle : Icons.timer_outlined,
                        size: 22,
                      ),
                      label: Text(
                        buttonText,
                        style: const TextStyle(
                          fontWeight: FontWeight.w600,
                          fontSize: 16,
                        ),
                      ),
                    ),
                  ),
                  // Retry / Skip buttons when timer done but ads not clicked
                  if (canSkip && !_retrying) ...[
                    const SizedBox(height: 8),
                    SizedBox(
                      width: double.infinity,
                      height: 40,
                      child: OutlinedButton.icon(
                        onPressed: _retry,
                        style: OutlinedButton.styleFrom(
                          foregroundColor: Colors.orangeAccent,
                          side: BorderSide(
                            color: Colors.orangeAccent.withValues(alpha: 0.4),
                          ),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                        icon: const Icon(Icons.refresh, size: 18),
                        label: const Text(
                          'Retry — Reload Ads',
                          style: TextStyle(fontWeight: FontWeight.w600),
                        ),
                      ),
                    ),
                    const SizedBox(height: 4),
                    TextButton(
                      onPressed: () => Navigator.pop(context),
                      child: Text(
                        'Skip (no reward)',
                        style: TextStyle(
                          color: Colors.white.withValues(alpha: 0.3),
                          fontSize: 13,
                        ),
                      ),
                    ),
                  ],
                  if (_retrying)
                    const Padding(
                      padding: EdgeInsets.only(top: 12),
                      child: SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.orangeAccent,
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
