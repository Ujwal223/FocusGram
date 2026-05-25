import 'package:flutter_inappwebview/flutter_inappwebview.dart';

enum V2OverlayScriptId {
  ghostMode,
  themeDetector,
  adBlockerDom,
  contentHider,
  fetchInterceptor,
  autoplayBlocker,
}

class V2OverlayInstaScript {
  final V2OverlayScriptId id;
  final String name;
  final String assetPath;
  final UserScriptInjectionTime injectionTime;
  bool enabled;

  V2OverlayInstaScript({
    required this.id,
    required this.name,
    required this.assetPath,
    this.injectionTime = UserScriptInjectionTime.AT_DOCUMENT_END,
    this.enabled = false,
  });
}

class V2OverlayScriptRegistry {
  static final List<V2OverlayInstaScript> all = [
    V2OverlayInstaScript(
      id: V2OverlayScriptId.ghostMode,
      name: 'ghost_mode',
      assetPath: 'assets/scripts/ghost_mode.js',
      injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
      enabled: false,
    ),
    V2OverlayInstaScript(
      id: V2OverlayScriptId.themeDetector,
      name: 'theme_detector',
      assetPath: 'assets/scripts/theme_detector.js',
      injectionTime: UserScriptInjectionTime.AT_DOCUMENT_END,
      enabled: true,
    ),
    V2OverlayInstaScript(
      id: V2OverlayScriptId.adBlockerDom,
      name: 'ad_blocker_dom',
      assetPath: 'assets/scripts/ad_blocker_dom.js',
      injectionTime: UserScriptInjectionTime.AT_DOCUMENT_END,
      enabled: false,
    ),
    V2OverlayInstaScript(
      id: V2OverlayScriptId.contentHider,
      name: 'content_hider',
      assetPath: 'assets/scripts/content_hider.js',
      injectionTime: UserScriptInjectionTime.AT_DOCUMENT_END,
      enabled: false,
    ),
    V2OverlayInstaScript(
      id: V2OverlayScriptId.fetchInterceptor,
      name: 'fetch_interceptor',
      assetPath: 'assets/scripts/fetch_interceptor.js',
      injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
      enabled: false,
    ),
    V2OverlayInstaScript(
      id: V2OverlayScriptId.autoplayBlocker,
      name: 'autoplay_blocker',
      assetPath: 'assets/scripts/autoplay_blocker.js',
      injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
      enabled: false,
    ),
  ];

  static V2OverlayInstaScript byId(V2OverlayScriptId id) {
    return all.firstWhere((s) => s.id == id);
  }
}
