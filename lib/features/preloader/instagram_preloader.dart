import 'dart:collection';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import '../../scripts/autoplay_blocker.dart';
import '../../scripts/spa_navigation_monitor.dart';
import '../../scripts/native_feel.dart';

class InstagramPreloader {
  static HeadlessInAppWebView? _headlessWebView;
  static InAppWebViewController? controller;
  static final InAppWebViewKeepAlive keepAlive = InAppWebViewKeepAlive();
  static bool isReady = false;

  static Future<void> start(String userAgent) async {
    if (_headlessWebView != null) return; // don't start twice
    
    _headlessWebView = HeadlessInAppWebView(
      keepAlive: keepAlive,
      initialUrlRequest: URLRequest(
        url: WebUri('https://www.instagram.com/'),
      ),
      initialSettings: InAppWebViewSettings(
        userAgent: userAgent,
        mediaPlaybackRequiresUserGesture: true,
        useHybridComposition: true,
        cacheEnabled: true,
        cacheMode: CacheMode.LOAD_CACHE_ELSE_NETWORK,
        domStorageEnabled: true,
        databaseEnabled: true,
        hardwareAcceleration: true,
        transparentBackground: true,
        safeBrowsingEnabled: false,
      ),
      initialUserScripts: UnmodifiableListView([
        UserScript(
          source: 'window.__fgBlockAutoplay = true;',
          injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
        ),
        UserScript(
          source: kAutoplayBlockerJS,
          injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
        ),
        UserScript(
          source: kSpaNavigationMonitorScript,
          injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
        ),
        UserScript(
          source: kNativeFeelingScript,
          injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
        ),
      ]),
      onWebViewCreated: (c) {
        controller = c;
      },
      onLoadStop: (c, url) async {
        isReady = true;
        await c.evaluateJavascript(source: kNativeFeelingPostLoadScript);
      },
    );

    await _headlessWebView!.run();
  }

  static void dispose() {
    _headlessWebView?.dispose();
    _headlessWebView = null;
    controller = null;
    isReady = false;
  }
}

