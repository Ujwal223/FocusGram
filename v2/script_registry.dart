import 'package:flutter_inappwebview/flutter_inappwebview.dart';

enum ScriptId {
  ghostMode,
  themeDetector,
  adBlockerDom,
  contentHider,
  fetchInterceptor,
  autoplayBlocker,
  mediaDetector,
  historyTracker,
}

class InstaScript {
  final ScriptId id;
  final String name;
  final String description;
  final String assetPath;
  final UserScriptInjectionTime injectionTime;
  bool enabled;

  InstaScript({
    required this.id,
    required this.name,
    required this.description,
    required this.assetPath,
    this.injectionTime = UserScriptInjectionTime.AT_DOCUMENT_END,
    this.enabled = false,
  });
}

class ScriptRegistry {
  static final List<InstaScript> all = [
    // ── DOCUMENT_START — must be before IG's JS loads ──
    InstaScript(
      id: ScriptId.ghostMode,
      name: 'Ghost Mode',
      description: 'Blocks story seen, message seen, and online status signals.',
      assetPath: 'assets/scripts/ghost_mode.js',
      injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
      enabled: false,
    ),
    InstaScript(
      id: ScriptId.fetchInterceptor,
      name: 'Fetch Interceptor',
      description: 'Unified feed filter: blocks ads, sponsored, suggested, videos via GraphQL interception.',
      assetPath: 'assets/scripts/fetch_interceptor.js',
      injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
      enabled: false,
    ),
    InstaScript(
      id: ScriptId.autoplayBlocker,
      name: 'Autoplay Blocker',
      description: 'Prevents video autoplay.',
      assetPath: 'assets/scripts/autoplay_blocker.js',
      injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
      enabled: false,
    ),

    // ── DOCUMENT_END — DOM must be ready ──
    InstaScript(
      id: ScriptId.themeDetector,
      name: 'Theme Detector',
      description: 'Reads page colors and syncs system UI bars.',
      assetPath: 'assets/scripts/theme_detector.js',
      injectionTime: UserScriptInjectionTime.AT_DOCUMENT_END,
      enabled: true, // always on — needed for native feel
    ),
    InstaScript(
      id: ScriptId.adBlockerDom,
      name: 'DOM Ad Blocker',
      description: 'Removes sponsored posts and tracking elements from feed (legacy - use Fetch Interceptor instead).',
      assetPath: 'assets/scripts/ad_blocker_dom.js',
      injectionTime: UserScriptInjectionTime.AT_DOCUMENT_END,
      enabled: false,
    ),
    InstaScript(
      id: ScriptId.contentHider,
      name: 'Content Hider',
      description: 'Toggleable hide for stories, posts, reels, suggested.',
      assetPath: 'assets/scripts/content_hider.js',
      injectionTime: UserScriptInjectionTime.AT_DOCUMENT_END,
      enabled: false,
    ),
    // Phase 2 scripts — registered but empty asset paths for now
    InstaScript(
      id: ScriptId.mediaDetector,
      name: 'Media Downloader',
      description: 'Injects download buttons on photos and reels.',
      assetPath: 'assets/scripts/media_detector.js',
      injectionTime: UserScriptInjectionTime.AT_DOCUMENT_END,
      enabled: false,
    ),
    InstaScript(
      id: ScriptId.historyTracker,
      name: 'History Tracker',
      description: 'Locally tracks reels watched and actions taken.',
      assetPath: 'assets/scripts/history_tracker.js',
      injectionTime: UserScriptInjectionTime.AT_DOCUMENT_END,
      enabled: false,
    ),
  ];

  static InstaScript byId(ScriptId id) => all.firstWhere((s) => s.id == id);
}
