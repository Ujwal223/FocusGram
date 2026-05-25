import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'core/theme/system_ui_manager.dart';
import 'core/webview/instagram_webview.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Enable web contents debugging for ghost mode verification
  if (kDebugMode) {
    InAppWebViewController.setWebContentsDebuggingEnabled(true);
  }

  await SystemUiManager.enableEdgeToEdge();
  runApp(const FocusGramApp());
}

class FocusGramApp extends StatelessWidget {
  const FocusGramApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'FocusGram',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF0095F6)),
        useMaterial3: true,
      ),
      home: const MainScreen(),
    );
  }
}

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  final _webViewKey = GlobalKey<InstagramWebViewState>();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      // backgroundColor transparent — lets WebView color bleed to system bars
      backgroundColor: Colors.black,
      body: SafeArea(
        // bottom: false — let WebView extend behind nav bar for true edge-to-edge
        bottom: false,
        child: InstagramWebView(key: _webViewKey),
      ),
    );
  }
}
