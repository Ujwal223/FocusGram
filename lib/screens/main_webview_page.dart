import 'dart:async';
import 'dart:collection';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../services/session_manager.dart';
import '../services/settings_service.dart';
import '../services/injection_controller.dart';
import '../services/injection_manager.dart';
import '../scripts/native_feel.dart';
import '../scripts/grayscale.dart' as grayscale;
import '../services/screen_time_service.dart';
import '../services/navigation_guard.dart';
import '../services/focusgram_router.dart';
import 'package:google_fonts/google_fonts.dart';
import '../services/notification_service.dart';
import '../services/bait_engine.dart';
import '../services/credit_store.dart';
import '../services/level_service.dart';
import 'bait_me_full_screen.dart';
import '../services/app_lock_service.dart';
// snapshot_service import removed — offline feature deleted
// reels_history_service import removed — feature deleted
import 'app_lock_screen.dart';
import '../features/update_checker/update_checker_service.dart';
import '../utils/discipline_challenge.dart';
import 'settings_page.dart';
import '../features/loading/skeleton_screen.dart';
import '../features/preloader/instagram_preloader.dart';
import '../v2_integration/script_engine_v2_overlay.dart';
import '../v2_integration/script_registry_v2_overlay.dart';
import '../scripts/focus_scripts.dart';
import 'adsterra_ad_screen.dart';
import '../focus_settings.dart';

import '../services/adblock/adblock_content_blocker_loader.dart';

/// Core validator/dispatcher for the JS → Flutter bridge:
/// `window.flutter_inappwebview.callHandler('FocusGramMediaDownload', JSON)`
Future<bool> handleFocusGramMediaDownload({
  required String raw,
  required Future<void> Function(Uri uri) launch,
}) async {
  try {
    final payload = jsonDecode(raw) as Map<String, dynamic>;

    final url = payload['url'] as String?;
    if (url == null || url.isEmpty) return false;

    final uri = Uri.tryParse(url);
    if (uri == null || !(uri.isScheme('http') || uri.isScheme('https'))) {
      return false;
    }

    // Best-effort origin allow-list (Instagram/CDN). Kept permissive to avoid
    // breaking legitimate downloads while still blocking obvious abuse.
    final host = uri.host.toLowerCase();
    final looksInstagramCdn =
        host.contains('cdninstagram.com') ||
        host.contains('fbcdn.net') ||
        host.contains('instagram.com');

    if (!looksInstagramCdn) return false;

    await launch(uri);
    return true;
  } catch (_) {
    // Best-effort only; never crash UI.
    return false;
  }
}

class MainWebViewPage extends StatefulWidget {
  const MainWebViewPage({super.key});

  @override
  State<MainWebViewPage> createState() => _MainWebViewPageState();
}

class _MainWebViewPageState extends State<MainWebViewPage>
    with WidgetsBindingObserver {
  static const String _donationPopupShownKey = 'donation_popup_shown_once';
  static final Uri _donateUri = Uri.parse('https://buymemomo.com/ujwal');

  InAppWebViewController? _controller;

  AdblockContentBlockerData? _adblockData;
  late final PullToRefreshController _pullToRefreshController;
  InjectionManager? _injectionManager;
  ScriptEngineV2Overlay? _v2Engine;
  final GlobalKey<_EdgePanelState> _edgePanelKey = GlobalKey<_EdgePanelState>();
  bool _showSkeleton =
      true; // true from the start so skeleton covers black Scaffold before WebView first paints
  bool _isLoading = true;
  Timer? _watchdog;
  // FIX 4: Safety timer to clear stuck loading state
  Timer? _loadingTimeout;
  bool _extensionDialogShown = false;
  bool _lastSessionActive = false;
  String _currentUrl = 'https://www.instagram.com/';
  bool _hasError = false;
  bool _reelsBlockedOverlay = false;
  bool _exploreBlockedOverlay = false;
  bool _isPreloaded = false;
  bool _minimalModeBannerDismissed = false;
  bool _isInDirectThread = false;
  DateTime _lastMainFrameLoadStartedAt = DateTime.fromMillisecondsSinceEpoch(0);
  SkeletonType _skeletonType = SkeletonType.generic;

  /// True when on the homepage and should block api/graphql + gateway.
  /// Updated in onLoadStart / UrlChange before shouldInterceptRequest fires.
  bool _blockHomepageGraphql = false;

  /// Helper to determine if we are on a login/onboarding page.
  bool get _isOnOnboardingPage {
    final path = Uri.tryParse(_currentUrl)?.path ?? '';
    final lowerPath = path.toLowerCase();
    return lowerPath.contains('/accounts/login') ||
        lowerPath.contains('/accounts/emailsignup') ||
        lowerPath.contains('/accounts/signup') ||
        lowerPath.contains('/legal/') ||
        lowerPath.contains('/help/') ||
        _currentUrl.contains('instagram.com/accounts/login');
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _initPullToRefresh();
    _initWebView();
    _startWatchdog();

    // Check for updates on launch
    context.read<UpdateCheckerService>().checkForUpdates();

    // Load adblock data early. If adblock is enabled, we wait for initial data
    // to be loaded so the WebView can apply contentBlockers on first render.
    // This prevents ads from loading before filters are applied.
    unawaited(_loadAdblockerDataEarly());

    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<SessionManager>().addListener(_onSessionChanged);
      context.read<SettingsService>().addListener(_onSettingsChanged);
      context.read<ScreenTimeService>().addListener(_onScreenTimeChanged);
      context.read<ScreenTimeService>().startTracking();
      _lastSessionActive = context.read<SessionManager>().isSessionActive;
      // Initialise structural snapshots so first change is detected correctly
      final settings = context.read<SettingsService>();
      _lastMinimalMode = settings.minimalModeEnabled;
      _lastDisableReels = settings.disableReelsEntirely;
      _lastDisableExplore = settings.disableExploreEntirely;
      _lastBlockHomeFeedScroll = settings.blockHomeFeedScroll;
      _lastBlockAutoplay = settings.blockAutoplay;
      _lastGhostMode = settings.ghostMode;
      _lastNoAds = settings.noAds;
      _lastNoStories = settings.noStories;
      _lastNoReels = settings.noReels;
      _lastNoAutoplay = settings.noAutoplay;
      _lastNoDMs = settings.noDMs;
      _lastV2GhostModeEnabled = settings.ghostMode;
      _lastV2AdBlockerDomEnabled = settings.v2AdBlockerDomEnabled;
      _lastV2ContentHiderEnabled = settings.v2ContentHiderEnabled;
      _lastV2FetchInterceptorEnabled = _shouldEnableFetchInterceptor(settings);
      _lastV2AutoplayBlockerEnabled = settings.blockAutoplay;
      _lastAdblockToggleValue = settings.v2AdBlockerDomEnabled;
      _onScreenTimeChanged();

      // Load full adblock data with longer timeout after UI is initialized
      unawaited(_loadAdblockerData());
    });

    FocusGramRouter.pendingUrl.addListener(_onPendingUrlChanged);
  }

  void _onPendingUrlChanged() {
    final url = FocusGramRouter.pendingUrl.value;
    if (url != null && url.isNotEmpty) {
      FocusGramRouter.pendingUrl.value = null;
      _controller?.loadUrl(urlRequest: URLRequest(url: WebUri(url)));
    }
  }

  bool _shouldEnableFetchInterceptor(SettingsService settings) {
    return settings.ghostMode ||
        settings.noAds ||
        settings.v2AdBlockerDomEnabled ||
        settings.noReels ||
        settings.hideSuggestedPosts ||
        (settings.v2ContentHiderEnabled &&
            (settings.contentPosts ||
                settings.contentReels ||
                settings.contentSuggested));
  }

  Future<void> _onScreenTimeChanged() async {
    if (!mounted) return;
    if (context.read<ScreenTimeService>().totalSeconds < 300) return;

    final prefs = await SharedPreferences.getInstance();
    if (prefs.getBool(_donationPopupShownKey) ?? false) return;
    await prefs.setBool(_donationPopupShownKey, true);
    if (!mounted) return;

    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Support FocusGram'),
        content: const Text(
          'Please donate to support the development of this project.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Not now'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.pop(context);
              launchUrl(_donateUri, mode: LaunchMode.externalApplication);
            },
            child: const Text('Donate'),
          ),
        ],
      ),
    );
  }

  /// Sets the isolated reel player flag in the WebView so the scroll-lock
  /// knows it should block swipe-to-next-reel.
  Future<void> _setIsolatedPlayer(bool active) async {
    await _controller?.evaluateJavascript(
      source: 'window.__focusgramIsolatedPlayer = $active;',
    );
  }

  /// Show a full-screen lock gate when navigating to Instagram DMs.
  void _showDmLockGate() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (ctx) => Scaffold(
          backgroundColor: Colors.black,
          body: SafeArea(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Center(
                child: SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(
                        Icons.lock_outline,
                        color: Colors.white54,
                        size: 64,
                      ),
                      const SizedBox(height: 24),
                      const Text(
                        'Messages Locked',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 22,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        'Enter your PIN to access Direct Messages',
                        style: TextStyle(color: Colors.white54, fontSize: 14),
                      ),
                      const SizedBox(height: 40),
                      ElevatedButton.icon(
                        onPressed: () async {
                          final result = await Navigator.push<bool>(
                            ctx,
                            MaterialPageRoute(
                              builder: (_) => const AppLockScreen(
                                forAppWide: false,
                                title: 'Messages Locked',
                                subtitle:
                                    'Enter your PIN to access Direct Messages',
                              ),
                            ),
                          );
                          if (!ctx.mounted) return;
                          if (result == true) {
                            _dmLockOverride = true;
                            Navigator.pop(ctx);
                          } else {
                            _controller?.evaluateJavascript(
                              source: 'window.location.href = "/";',
                            );
                            Navigator.pop(ctx);
                          }
                        },
                        icon: const Icon(Icons.lock_open_rounded),
                        label: const Text('Unlock'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.blueAccent,
                          padding: const EdgeInsets.symmetric(
                            horizontal: 32,
                            vertical: 14,
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                      TextButton(
                        onPressed: () {
                          _controller?.evaluateJavascript(
                            source: 'window.location.href = "/";',
                          );
                          Navigator.pop(ctx);
                        },
                        child: const Text(
                          'Cancel — Go to Home',
                          style: TextStyle(color: Colors.white38),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  /// Set ghost mode flags in the WebView so the pre-injected scripts activate.
  void _setGhostModeFlags(InAppWebViewController c, SettingsService s) {
    c.evaluateJavascript(
      source:
          '''
window.__fgFullDmGhost = ${s.ghostMode};
''',
    );
  }

  /// Re-inject grayscale on app resume (fixes cold-start persistence bug
  /// where the preloader cache can bypass onLoadStop).
  void _syncGrayscaleOnResume(SettingsService settings) {
    if (_injectionManager == null || _controller == null) return;
    if (settings.isGrayscaleActiveNow) {
      _injectionManager!.runAllPostLoadInjections(_currentUrl);
    } else {
      // Explicitly remove grayscale
      _controller?.evaluateJavascript(source: grayscale.kGrayscaleOffJS);
    }
  }

  void _onSessionChanged() {
    if (!mounted) return;
    final sm = context.read<SessionManager>();
    if (_lastSessionActive != sm.isSessionActive) {
      _lastSessionActive = sm.isSessionActive;

      if (_lastSessionActive) {
        HapticFeedback.mediumImpact();
      } else {
        HapticFeedback.heavyImpact();
      }

      if (_controller != null && _injectionManager != null) {
        _injectionManager!.runAllPostLoadInjections(_currentUrl);
      }

      // If session became active and we were showing overlay, hide it
      if (_lastSessionActive && _reelsBlockedOverlay) {
        setState(() => _reelsBlockedOverlay = false);
      }
    }
    setState(() {});
  }

  // Debounce timer so rapid toggles don't spam reloads
  Timer? _reloadDebounce;

  // Snapshot of structural settings — used to detect when a reload is needed
  bool _lastMinimalMode = false;
  bool _lastDisableReels = false;
  bool _lastDisableExplore = false;
  bool _lastBlockHomeFeedScroll = false;
  bool _lastBlockAutoplay = false;
  bool _lastGhostMode = false;
  bool _lastNoAds = false;
  bool _lastNoStories = false;
  bool _lastNoReels = false;
  bool _lastNoAutoplay = false;
  bool _lastNoDMs = false;
  bool _lastV2GhostModeEnabled = false;
  bool _lastV2AdBlockerDomEnabled = false;
  bool _lastV2ContentHiderEnabled = false;
  bool _lastV2FetchInterceptorEnabled = false;
  bool _lastV2AutoplayBlockerEnabled = false;

  // Tracks v2 adblock toggle to know when to reload WebView for ContentBlocker changes
  bool _lastAdblockToggleValue = false;

  void _onSettingsChanged() {
    if (!mounted) return;
    final settings = context.read<SettingsService>();

    // If adblock toggle flipped, rebuild WebView so `contentBlockers` applies.
    // IMPORTANT: do NOT early-return, otherwise we skip the v2 overlay prefs sync
    // (which is what enables the DOM fallback script + other v2 toggles).
    if (_lastAdblockToggleValue != settings.v2AdBlockerDomEnabled) {
      _lastAdblockToggleValue = settings.v2AdBlockerDomEnabled;
      _adblockData = null;
      _loadAdblockerData();
      _controller?.reload();
    }

    // 0. V2 overlay sync (prefs must be updated before toggling)
    unawaited(() async {
      final prefs = await SharedPreferences.getInstance();
      if (_v2Engine != null) {
        await prefs.setBool(
          'fg_v2_${V2OverlayScriptId.ghostMode.name}_enabled',
          settings.ghostMode,
        );
        await prefs.setBool(
          'fg_v2_${V2OverlayScriptId.adBlockerDom.name}_enabled',
          settings.v2AdBlockerDomEnabled,
        );
        await prefs.setBool(
          'fg_v2_${V2OverlayScriptId.contentHider.name}_enabled',
          settings.v2ContentHiderEnabled,
        );

        final bool fetchInterceptorEnabled = _shouldEnableFetchInterceptor(
          settings,
        );
        final bool autoplayBlockerEnabled = settings.blockAutoplay;

        await prefs.setBool(
          'fg_v2_${V2OverlayScriptId.fetchInterceptor.name}_enabled',
          fetchInterceptorEnabled,
        );
        await prefs.setBool(
          'fg_v2_${V2OverlayScriptId.autoplayBlocker.name}_enabled',
          autoplayBlockerEnabled,
        );

        await prefs.setBool('content_stories', settings.contentStories);
        await prefs.setBool('content_posts', settings.contentPosts);
        await prefs.setBool('content_reels', settings.contentReels);
        await prefs.setBool('content_suggested', settings.contentSuggested);

        final shouldReloadV2 =
            _lastV2GhostModeEnabled != settings.ghostMode ||
            _lastV2AdBlockerDomEnabled != settings.v2AdBlockerDomEnabled ||
            _lastV2ContentHiderEnabled != settings.v2ContentHiderEnabled ||
            _lastV2FetchInterceptorEnabled != fetchInterceptorEnabled ||
            _lastV2AutoplayBlockerEnabled != autoplayBlockerEnabled;

        _lastV2GhostModeEnabled = settings.ghostMode;
        _lastV2AdBlockerDomEnabled = settings.v2AdBlockerDomEnabled;
        _lastV2ContentHiderEnabled = settings.v2ContentHiderEnabled;
        _lastV2FetchInterceptorEnabled = fetchInterceptorEnabled;
        _lastV2AutoplayBlockerEnabled = autoplayBlockerEnabled;

        if (shouldReloadV2) {
          _reloadDebounce?.cancel();
          _reloadDebounce = Timer(const Duration(milliseconds: 600), () {
            if (mounted) _controller?.reload();
          });
        } else {
          await _v2Engine?.injectDocumentEndScripts();
        }
      }
    }());

    // 1. Apply all cosmetic changes immediately via injection
    if (_controller != null) {
      _controller!.evaluateJavascript(
        source:
            'window.__fgSetBlockAutoplay?.(${settings.blockAutoplay}); window.__fgBlockAutoplay = ${settings.blockAutoplay}; window.__fgTapToUnblur = ${settings.blurExplore && settings.tapToUnblur};',
      );
    }
    if (_controller != null && _injectionManager != null) {
      _injectionManager!.runAllPostLoadInjections(_currentUrl);
    }

    // Ghost mode flags update + reload (scripts already injected by preloader,
    // but need to reload so the fetch/XHR interceptors see the new flags from
    // the start of page load).
    if (_lastGhostMode != settings.ghostMode) {
      _lastGhostMode = settings.ghostMode;
      if (_controller != null) {
        _setGhostModeFlags(_controller!, settings);
        // Schedule a reload so the flags take effect on fresh page load
        _reloadDebounce?.cancel();
        _reloadDebounce = Timer(const Duration(milliseconds: 300), () {
          if (mounted) _controller?.reload();
        });
      }
    }

    // 2. Rebuild Flutter widget tree (e.g. overlay conditions, banner state)
    setState(() {});

    // 3. Detect structural changes that need a full reload.
    //    CSS injection alone can't undo Instagram's already-rendered React DOM.
    final structuralChange =
        settings.minimalModeEnabled != _lastMinimalMode ||
        settings.disableReelsEntirely != _lastDisableReels ||
        settings.disableExploreEntirely != _lastDisableExplore ||
        settings.blockHomeFeedScroll != _lastBlockHomeFeedScroll ||
        settings.blockAutoplay != _lastBlockAutoplay ||
        settings.ghostMode != _lastGhostMode ||
        settings.noAds != _lastNoAds ||
        settings.noStories != _lastNoStories ||
        settings.noReels != _lastNoReels ||
        settings.noAutoplay != _lastNoAutoplay ||
        settings.noDMs != _lastNoDMs;

    _lastMinimalMode = settings.minimalModeEnabled;
    _lastDisableReels = settings.disableReelsEntirely;
    _lastDisableExplore = settings.disableExploreEntirely;
    _lastBlockHomeFeedScroll = settings.blockHomeFeedScroll;
    _lastBlockAutoplay = settings.blockAutoplay;
    _lastGhostMode = settings.ghostMode;
    _lastNoAds = settings.noAds;
    _lastNoStories = settings.noStories;
    _lastNoReels = settings.noReels;
    _lastNoAutoplay = settings.noAutoplay;
    _lastNoDMs = settings.noDMs;

    if (structuralChange && _controller != null) {
      // Debounce: if user toggles rapidly, only reload once they stop
      _reloadDebounce?.cancel();
      _reloadDebounce = Timer(const Duration(milliseconds: 600), () {
        if (mounted) _controller?.reload();
      });
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _watchdog?.cancel();
    _loadingTimeout?.cancel();
    _reloadDebounce?.cancel();
    FocusGramRouter.pendingUrl.removeListener(_onPendingUrlChanged);
    context.read<SessionManager>().removeListener(_onSessionChanged);
    context.read<SettingsService>().removeListener(_onSettingsChanged);
    context.read<ScreenTimeService>().removeListener(_onScreenTimeChanged);
    context.read<ScreenTimeService>().stopTracking();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (!mounted) return;
    final sm = context.read<SessionManager>();
    final screenTime = context.read<ScreenTimeService>();
    final settings = context.read<SettingsService>();

    if (state == AppLifecycleState.resumed) {
      sm.setAppForeground(true);
      screenTime.startTracking();
      // Cancel persistent notification when app comes to foreground
      NotificationService().cancelPersistentNotification(id: 5001);

      // Re-inject grayscale on resume — schedules may have changed
      // while the app was backgrounded, and injection can be lost on cold
      // start due to the preloader cache bypassing onLoadStop.
      _syncGrayscaleOnResume(settings);
    } else if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.inactive ||
        state == AppLifecycleState.detached) {
      sm.setAppForeground(false);
      screenTime.stopTracking();

      // Show persistent notification when schedules are active (if enabled)
      if (settings.notifyPersistent) {
        final isScheduleActive = sm.isScheduledBlockActive;
        final isGrayscaleActive = settings.isGrayscaleActiveNow;

        if (isScheduleActive) {
          NotificationService().showPersistentNotification(
            id: 5001,
            title: 'FocusGram - Schedule Active',
            body: 'Instagram is blocked during your focus hours',
          );
        } else if (isGrayscaleActive) {
          NotificationService().showPersistentNotification(
            id: 5001,
            title: 'FocusGram - Grayscale Active',
            body: 'Instagram is in grayscale mode',
          );
        }
      }
    }
  }

  void _startWatchdog() {
    _watchdog = Timer.periodic(const Duration(seconds: 15), (_) {
      if (!mounted) return;
      final sm = context.read<SessionManager>();
      if (sm.isAppSessionExpired && !_extensionDialogShown) {
        _extensionDialogShown = true;
        _showSessionExpiredDialog(sm);
      }
    });
  }

  // FIX 4: Cancel any existing loading timeout and start a fresh one.
  // If onLoadStop or onReceivedError haven't fired after 12 seconds,
  // force-clear the loading/skeleton state so the app never appears stuck.
  void _resetLoadingTimeout() {
    _loadingTimeout?.cancel();
    _loadingTimeout = Timer(const Duration(seconds: 12), () {
      if (!mounted) return;
      if (_isLoading || _showSkeleton) {
        setState(() {
          _isLoading = false;
          _showSkeleton = false;
        });
      }
    });
  }

  void _showSessionExpiredDialog(SessionManager sm) {
    // Helper function to handle "Close App" action
    void closeApp() {
      Navigator.of(context, rootNavigator: true).pop();
      sm.endAppSession();
      SystemNavigator.pop();
    }

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => PopScope(
        canPop: false,
        onPopInvokedWithResult: (didPop, result) {
          // Intercept back button - treat it as "Close App" action
          if (!didPop) {
            closeApp();
          }
        },
        child: AlertDialog(
          backgroundColor: const Color(0xFF1A1A1A),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
          ),
          title: const Text(
            'Session Complete ✓',
            style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Your planned Instagram time is up.',
                style: TextStyle(color: Colors.white70),
              ),
              if (sm.canExtendAppSession) ...[
                const SizedBox(height: 8),
                const Text(
                  'You can extend once by 10 minutes.',
                  style: TextStyle(color: Colors.white54, fontSize: 13),
                ),
              ],
            ],
          ),
          actions: [
            TextButton(
              onPressed: closeApp,
              child: const Text(
                'Close App',
                style: TextStyle(color: Colors.redAccent),
              ),
            ),
            if (sm.canExtendAppSession)
              ElevatedButton(
                onPressed: () async {
                  Navigator.of(context, rootNavigator: true).pop();
                  // Keep _extensionDialogShown = true while ad runs so the
                  // watchdog timer doesn't re-show the dialog over the ad screen.
                  if (!mounted) return;
                  final adResult = await Navigator.push<bool>(
                    context,
                    MaterialPageRoute(
                      builder: (_) => const AdsterraAdScreen(
                        sessionType: 'reels',
                        requiredSeconds: 20,
                      ),
                    ),
                  );
                  _extensionDialogShown = false;
                  if (adResult == true && mounted) {
                    sm.extendAppSession();
                  }
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.blue,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                ),
                child: const Text('Watch Ad (+10 min)'),
              ),
          ],
        ),
      ),
    );
  }

  void _initPullToRefresh() {
    _pullToRefreshController = PullToRefreshController(
      settings: PullToRefreshSettings(color: Colors.blue),
      onRefresh: () async {
        await _controller?.reload();
      },
    );
  }

  Future<AdblockContentBlockerData> _loadAdblockerData() async {
    final settings = context.read<SettingsService>();
    final prefs = await SharedPreferences.getInstance();
    final previousHosts = _adblockData?.blockedHosts;

    final loader = AdblockContentBlockerLoader();
    final data = await loader.loadOrUpdateIfNeeded(
      enabled: settings.v2AdBlockerDomEnabled,
      prefs: prefs,
    );

    if (mounted) {
      setState(() => _adblockData = data);
      if (settings.v2AdBlockerDomEnabled &&
          data.blockedHosts.isNotEmpty &&
          _controller != null &&
          (previousHosts == null ||
              !setEquals(previousHosts, data.blockedHosts))) {
        unawaited(_controller?.reload());
      }
    }
    return data;
  }

  Future<void> _loadAdblockerDataEarly() async {
    final settings = context.read<SettingsService>();
    if (!settings.v2AdBlockerDomEnabled) return;

    try {
      final prefs = await SharedPreferences.getInstance();
      final loader = AdblockContentBlockerLoader();
      final data = await loader.loadOrUpdateIfNeeded(
        enabled: true,
        prefs: prefs,
        timeoutMs: 5000, // Short timeout for early load
      );

      if (mounted) {
        setState(() => _adblockData = data);
      }
    } catch (_) {
      // If loading fails, continue without blocking app startup
      // AdblockData will be retried in _loadAdblockerData()
    }
  }

  bool _isBlockedByAdblockHostList(WebUri uri, Set<String>? blockedHosts) {
    if (blockedHosts == null || blockedHosts.isEmpty) return false;

    var host = uri.host.toLowerCase();
    if (blockedHosts.contains(host)) return true;

    while (true) {
      final dot = host.indexOf('.');
      if (dot < 0 || dot == host.length - 1) return false;
      host = host.substring(dot + 1);
      if (blockedHosts.contains(host)) return true;
    }
  }

  void _initWebView() {
    // Preloader disabled — keepAlive WebView silently fails when app cold-starts,
    // leaving _isPreloaded = true with no content, causing permanent black screen.
    // The fresh load path is reliable; the ~300ms preload gain is not worth it.
    _isPreloaded = false;

    setState(() {
      _currentUrl = 'https://www.instagram.com/accounts/login/';
    });

    // If not preloaded, controller will be created in onWebViewCreated
    _injectionManager = null;

    // Nothing else to do here – configuration is on the InAppWebView widget
  }

  Future<void> _signOut() async {
    final cookieManager = CookieManager.instance();
    await cookieManager.deleteAllCookies();
    await InAppWebViewController.clearAllCache();
    if (mounted) {
      setState(() {
        _showSkeleton = true;
        _isLoading = true;
        _reelsBlockedOverlay = false;
      });
      await _controller?.loadUrl(
        urlRequest: URLRequest(
          url: WebUri('https://www.instagram.com/accounts/login/'),
        ),
      );
    }
  }

  /// Formats [seconds] as `MM:SS` for the cooldown countdown display.
  static String _fmtSeconds(int seconds) {
    final m = (seconds ~/ 60).toString().padLeft(2, '0');
    final s = (seconds % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  static bool _isHomeFeedUrl(String url) {
    final uri = Uri.tryParse(url);
    if (uri == null) return url == '/' || url.isEmpty;
    final path = uri.path.isEmpty ? '/' : uri.path;
    return uri.host.contains('instagram.com') && path == '/';
  }

  static bool _isDirectThreadUrl(String url) {
    final path = Uri.tryParse(url)?.path ?? url;
    // Match both /direct/inbox/ and /direct/t/{thread_id}
    return RegExp(r'^/direct/').hasMatch(path);
  }

  /* unused after CDN block was removed
  static bool _isFktmInstagramCdn(String url) {
    final host = Uri.tryParse(url)?.host.toLowerCase() ?? '';
    return RegExp(r'^instagram\.fktm\d+-\d+\.fna\.fbcdn\.net$').hasMatch(host);
  }
  */

  void _syncDirectThreadState(String url) {
    final active = _isDirectThreadUrl(url);
    if (_isInDirectThread == active) return;
    _isInDirectThread = active;

    // Reset override when leaving DMs
    if (!active) _dmLockOverride = false;

    // If Messages Tab Lock is enabled and user navigated to DMs,
    // show a lock overlay.
    if (active && mounted) {
      final appLock = context.read<AppLockService>();
      if (appLock.messagesLockReady && !_dmLockOverride) {
        _showDmLockGate();
      }
    }
  }

  bool _dmLockOverride = false;

  Future<void> _showReelSessionPicker() async {
    final settings = context.read<SettingsService>();
    if (settings.requireWordChallenge) {
      final passed = await DisciplineChallenge.show(
        context,
        count: settings.resolvedWordChallengeCount(),
      );
      if (!passed || !mounted) return;
    }
    _showReelSessionPickerBottomSheet();
  }

  void _showReelSessionPickerBottomSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => Container(
        height: MediaQuery.of(context).size.height * 0.7,
        decoration: BoxDecoration(
          color: const Color(0xFF121212),
          borderRadius: BorderRadius.circular(25),
        ),
        child: Column(
          children: [
            const SizedBox(height: 16),
            const Text(
              'Start Reel Session',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const Padding(
              padding: EdgeInsets.all(12),
              child: Text(
                'Reels will be unblocked for the duration you choose.',
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.white54, fontSize: 13),
              ),
            ),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                children: [
                  _buildReelSessionTile(1),
                  _buildReelSessionTile(3),
                  _buildReelSessionTile(5),
                  _buildReelSessionTile(10),
                  _buildReelSessionTile(15),
                  _buildReelSessionTile(20),
                  _buildReelSessionTile(30),
                  const SizedBox(height: 40),
                  TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: const Text(
                      'Cancel',
                      style: TextStyle(color: Colors.white38),
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

  Widget _buildReelSessionTile(int mins) {
    final sm = context.read<SessionManager>();
    return ListTile(
      title: Text('$mins Minutes', style: const TextStyle(color: Colors.white)),
      trailing: const Icon(
        Icons.arrow_forward_ios,
        color: Colors.white24,
        size: 14,
      ),
      onTap: () async {
        Navigator.pop(context);
        if (sm.startSession(mins)) {
          HapticFeedback.mediumImpact();
          setState(() => _reelsBlockedOverlay = false);
          await _controller?.loadUrl(
            urlRequest: URLRequest(
              url: WebUri('https://www.instagram.com/reels/'),
            ),
          );
        }
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) async {
        if (didPop) return;
        if (_reelsBlockedOverlay) {
          setState(() => _reelsBlockedOverlay = false);
          await _controller?.goBack();
          return;
        }
        if (_isHomeFeedUrl(_currentUrl)) {
          SystemNavigator.pop();
          return;
        }
        final didNavigate =
            await (_controller
                ?.evaluateJavascript(
                  source:
                      '(function(){'
                      '  var before = window.location.href;'
                      '  history.back();'
                      '  return before;'
                      '})()',
                )
                .then((_) => true)
                .catchError((_) => false)) ??
            false;
        if (didNavigate == true) {
          await Future.delayed(const Duration(milliseconds: 120));
          return;
        }
        if (await (_controller?.canGoBack() ?? Future.value(false))) {
          await _controller?.goBack();
        } else {
          SystemNavigator.pop();
        }
      },
      child: Scaffold(
        // FIX 1: Use a solid color that matches the WebView background.
        // When transparentBackground is false (see WebView settings), the
        // WebView renders its own white/black background. Using black here
        // matches the dark-mode WebView bg and prevents "flash of white".
        backgroundColor: Colors.black,
        body: Stack(
          children: [
            SafeArea(
              child: Column(
                children: [
                  const _UpdateBanner(),
                  if (!_isOnOnboardingPage)
                    _BrandedTopBar(
                      onFocusControlTap: () =>
                          _edgePanelKey.currentState?._toggleExpansion(),
                      onDmGhostToggle: () {
                        context.read<SettingsService>().setGhostMode(false);
                        _controller?.reload();
                      },
                      onReload: () => _controller?.reload(),
                      currentUrl: _currentUrl,
                      dmGhostActive: context.read<SettingsService>().ghostMode,
                    ),
                  Expanded(
                    child: Consumer<SessionManager>(
                      builder: (ctx, sm, _) {
                        if (sm.isScheduledBlockActive) {
                          return Container(
                            color: Colors.black,
                            padding: const EdgeInsets.all(32),
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                const Icon(
                                  Icons.bedtime_rounded,
                                  color: Colors.blueAccent,
                                  size: 80,
                                ),
                                const SizedBox(height: 24),
                                Text(
                                  'Focus Hours Active',
                                  style: GoogleFonts.grandHotel(
                                    color: Colors.white,
                                    fontSize: 42,
                                  ),
                                ),
                                const SizedBox(height: 12),
                                Text(
                                  'Instagram is blocked according to your schedule (${sm.activeScheduleText ?? 'Focus Hours'}).',
                                  textAlign: TextAlign.center,
                                  style: const TextStyle(
                                    color: Colors.white70,
                                    fontSize: 15,
                                    height: 1.5,
                                  ),
                                ),
                                const SizedBox(height: 48),
                                const Text(
                                  'Your future self will thank you for the extra sleep and focus.',
                                  textAlign: TextAlign.center,
                                  style: TextStyle(
                                    color: Colors.white38,
                                    fontSize: 12,
                                    fontStyle: FontStyle.italic,
                                  ),
                                ),
                              ],
                            ),
                          );
                        }

                        final settings = context.read<SettingsService>();

                        return Stack(
                          children: [
                            InAppWebView(
                              keepAlive: InstagramPreloader.keepAlive,
                              initialUrlRequest: _isPreloaded
                                  ? null
                                  : URLRequest(
                                      url: WebUri(
                                        'https://www.instagram.com/accounts/login/',
                                      ),
                                    ),
                              initialSettings: InAppWebViewSettings(
                                userAgent: InjectionController.iOSUserAgent,
                                mediaPlaybackRequiresUserGesture:
                                    settings.blockAutoplay,
                                useHybridComposition: true,
                                cacheEnabled: true,
                                cacheMode: CacheMode.LOAD_CACHE_ELSE_NETWORK,
                                domStorageEnabled: true,
                                databaseEnabled: true,
                                thirdPartyCookiesEnabled: false,
                                hardwareAcceleration: true,
                                // FIX 2: Set to false so the WebView renders
                                // its own opaque background. When true + black
                                // Scaffold, you see black until Instagram
                                // finishes painting — looks like a freeze/hang.
                                transparentBackground: false,
                                safeBrowsingEnabled: false,
                                disableContextMenu: false,
                                supportZoom: false,
                                allowsInlineMediaPlayback: true,
                                verticalScrollBarEnabled: false,
                                horizontalScrollBarEnabled: false,
                                contentBlockers:
                                    _adblockData?.contentBlockers ?? const [],
                              ),
                              initialUserScripts: UnmodifiableListView([
                                ...const <UserScript>[],
                                ...buildUserScripts(
                                  FocusSettings(
                                    ghostMode: settings.ghostMode,
                                    noAds: settings.noAds,
                                    noStories: settings.noStories,
                                    noReels: settings.noReels,
                                    noAutoplay: settings.noAutoplay,
                                    noDMs: settings.noDMs,
                                  ),
                                ),
                              ]),
                              pullToRefreshController: _pullToRefreshController,
                              shouldInterceptRequest: (controller, request) async {
                                final url = request.url.toString();

                                const adDomains = [
                                  'an.facebook.com',
                                  'connect.facebook.net',
                                  'pixel.facebook.com',
                                  'graph.facebook.com/logging',
                                  'www.instagram.com/ajax/bz',
                                  'www.instagram.com/api/v1/web/comet/logcalls',
                                  'doubleclick.net',
                                  'googletagmanager.com',
                                  'scorecardresearch.com',
                                ];
                                if (adDomains.any(url.contains)) {
                                  return WebResourceResponse(
                                    data: Uint8List(0),
                                  );
                                }

                                final referrer =
                                    request.headers?['Referer'] ??
                                    request.headers?['referer'];
                                if (referrer != null &&
                                    _isDirectThreadUrl(referrer)) {
                                  _syncDirectThreadState(referrer);
                                }

                                /*if (_isInDirectThread &&
                                    _isFktmInstagramCdn(url)) {
                                  if (_dmThreadCdnBlockArmed) {
                                    return WebResourceResponse(
                                      data: Uint8List(0),
                                    );
                                  }
                                  _dmThreadCdnBlockArmed = true;
                                }
*/
                                // Strict/high-priority domain blocking from uBlock-style lists.
                                final adblockHosts = _adblockData?.blockedHosts;
                                if (_isBlockedByAdblockHostList(
                                  request.url,
                                  adblockHosts,
                                )) {
                                  return WebResourceResponse(
                                    data: Uint8List(0),
                                  );
                                }

                                // Block trackers + paid pixel iframes (hardcoded safety)
                                const blockedDomains = [
                                  'fbsbx.com/paid_ads_pixel',
                                  'fbsbx.com/paid_ads',
                                  'facebook.com/tr',
                                  'instagram.com/paid_ads',
                                  'analytics.facebook.com',
                                  'facebook.com/tracking',
                                ];
                                if (blockedDomains.any(
                                  (d) => url.contains(d),
                                )) {
                                  return WebResourceResponse(
                                    data: Uint8List(0),
                                  );
                                }

                                // Also block any IG paid-pixel iframe HTML documents
                                if (url.contains('/paid_ads_pixel/iframe/') ||
                                    url.contains('/generete_pixels/')) {
                                  return WebResourceResponse(
                                    data: Uint8List(0),
                                  );
                                }

                                // Block Reels API
                                if (settings.noReels &&
                                    (url.contains('/api/v1/clips/') ||
                                        url.contains('/api/v1/discover/'))) {
                                  return WebResourceResponse(
                                    data: Uint8List(0),
                                  );
                                }

                                // Block DMs API
                                if (settings.noDMs &&
                                    (url.contains('edge-chat.instagram.com') ||
                                        url.contains('/api/v1/direct_v2/'))) {
                                  return WebResourceResponse(
                                    data: Uint8List(0),
                                  );
                                }

                                // ── DM Ghost: block ALL seen signals ────────────────
                                // Like Chrome DevTools "Block request URL" — catches all
                                // sources at the native WebView level.
                                //
                                // Rules:
                                // 1. Block specific seen endpoint patterns everywhere
                                // 2. Block /api/graphql on homepage (/) and DM threads
                                //    (/direct/t/*). Allow on /direct/inbox/ so inbox loads.
                                if (settings.ghostMode) {
                                  // — Seen endpoint patterns (always block) —
                                  final seenBlocked = RegExp(
                                    r'/api/v1/media/[\w-]+/seen/|'
                                    r'/api/v1/stories/reel/seen/|'
                                    r'/api/v1/direct_v2/threads/[\w-]+/seen/|'
                                    r'/api/v1/direct_v2/visual_message/[\w-]+/seen/|'
                                    r'/api/v1/live/[\w-]+/comment/seen/|'
                                    r'/api/v1/direct_v2/threads/[^/]+/mark_item_seen/|'
                                    r'/api/v1/direct_v2/mark_item_seen/|'
                                    r'/api/v1/direct_v2/threads/[^/]+/items/[^/]+/mark_visual_item_seen/|'
                                    r'/api/v1/direct_v2/visual_thread/[^/]+/seen/|'
                                    r'/api/v1/direct_v2/threads/[^/]+/items/[^/]+/mark_audio_seen/|'
                                    r'/api/v1/live/[^/]+/join/|'
                                    r'/api/v1/live/[^/]+/get_join_requests/|'
                                    r'/api/v1/media/seen/|'
                                    r'/api/v1/feed/viewed_story/|'
                                    r'/api/v1/feed/reels_tray/seen/|'
                                    r'/api/v1/qe/|'
                                    r'/api/v1/launcher/sync/|'
                                    r'/api/v1/logging/|'
                                    r'/api/v1/fb_onetap_logging/|'
                                    r'/ajax/bz|'
                                    r'/ajax/logging/|'
                                    r'/api/v1/stats/|'
                                    r'/api/v1/fbanalytics/',
                                  ).hasMatch(url);
                                  if (seenBlocked) {
                                    return WebResourceResponse(
                                      data: Uint8List.fromList(
                                        utf8.encode('{"status":"ok"}'),
                                      ),
                                      statusCode: 200,
                                      contentType: 'application/json',
                                    );
                                  }

                                  // — Block /api/graphql + gateway on homepage &
                                  //    ANY /direct/* page (not just /direct/t/).
                                  //    Allow on /direct/inbox/ so inbox loads.
                                  //    Broader scope catches seen indicators sent
                                  //    during SPA transitions on re-entry.
                                  final currentPath =
                                      Uri.tryParse(_currentUrl)?.path ??
                                      _currentUrl;
                                  final isHomepage =
                                      currentPath == '/' || currentPath == '';
                                  final isOnDirect = currentPath.startsWith(
                                    '/direct/',
                                  );
                                  if (!currentPath.startsWith(
                                        '/direct/inbox/',
                                      ) &&
                                      (isHomepage || isOnDirect) &&
                                      (url.contains('/api/graphql') ||
                                          url.contains(
                                            'gateway.instagram.com',
                                          ))) {
                                    return WebResourceResponse(
                                      data: Uint8List.fromList(
                                        utf8.encode('{"status":"ok"}'),
                                      ),
                                      statusCode: 200,
                                      contentType: 'application/json',
                                    );
                                  }
                                }

                                // Legacy homepage graphql + gateway block
                                // (kept for safety — the ghost mode block above now covers it)
                                if (_blockHomepageGraphql &&
                                    (url.contains('/api/graphql') ||
                                        url.contains(
                                          'gateway.instagram.com',
                                        ))) {
                                  return WebResourceResponse(
                                    data: Uint8List.fromList(
                                      utf8.encode('{"status":"ok"}'),
                                    ),
                                    statusCode: 200,
                                    contentType: 'application/json',
                                  );
                                }

                                /* Strip ads from feed (JS handles it)
                                if (settings.noAds &&
                                    url.contains(
                                      'instagram.com/graphql/query',
                                    )) {/
                                  try {
                                    final res = await http.post(
                                      Uri.parse(url),
                                      headers: Map<String, String>.from(
                                        request.headers ?? {},
                                      ),
                                    );

                                    final json = jsonDecode(res.body);
                                    final connection =
                                        json['data']?['xdt_api__v1__feed__timeline__connection'];

                                    if (connection != null &&
                                        connection['edges'] is List) {
                                      final edges = connection['edges'] as List;
                                      edges.removeWhere((e) {
                                        final node = e['node'];
                                        if (node == null) return false;
                                        // Strip ads from feed
                                        if (settings.noAds &&
                                            url.contains(
                                              'instagram.com/graphql',
                                            )) {
                                          try {
                                            final res = await http.post(
                                              Uri.parse(url),
                                              headers: Map<String, String>.from(
                                                request.headers ?? {},
                                              ),
                                            );

                                            final json = jsonDecode(res.body);

                                            void filterEdges(dynamic obj) {
                                              if (obj == null) return;
                                              if (obj is Map) {
                                                if (obj['edges'] is List) {
                                                  (obj['edges'] as List).removeWhere((
                                                    e,
                                                  ) {
                                                    final node = e is Map
                                                        ? e['node']
                                                        : null;
                                                    if (node == null ||
                                                        node is! Map)
                                                      return false;
                                                    return node['is_ad'] ==
                                                            true ||
                                                        node['ad_id'] != null ||
                                                        node['ad_action_links'] !=
                                                            null ||
                                                        node['is_paid_partnership'] ==
                                                            true ||
                                                        node['sponsor_tags'] !=
                                                            null ||
                                                        node['commerciality_status'] ==
                                                            'ad' ||
                                                        node['commerciality_status'] ==
                                                            'shoppable_feed_ad' ||
                                                        (node['__typename']
                                                                ?.toString()
                                                                .toLowerCase()
                                                                .contains(
                                                                  'ad',
                                                                ) ??
                                                            false);
                                                  });
                                                }
                                                obj.values.forEach(filterEdges);
                                              } else if (obj is List) {
                                                obj.forEach(filterEdges);
                                              }
                                            }

                                            filterEdges(json);

                                            return WebResourceResponse(
                                              data: Uint8List.fromList(
                                                utf8.encode(jsonEncode(json)),
                                              ),
                                              headers: res.headers,
                                              statusCode: 200,
                                              contentType: 'application/json',
                                            );
                                          } catch (_) {
                                            return null;
                                          }
                                        }
                                      });
                                    }

                                    return WebResourceResponse(
                                      data: Uint8List.fromList(
                                        utf8.encode(jsonEncode(json)),
                                      ),
                                      headers: res.headers,
                                      statusCode: 200,
                                      contentType: 'application/json',
                                    );
                                  } catch (e) {
                                    // if anything fails, pass through original request unmodified
                                    return null;
                                  }
                                }*/

                                return null;
                              },
                              onWebViewCreated: (controller) async {
                                _controller = controller;

                                // Capture settingsService before async gap to avoid BuildContext warning
                                final settingsService = context
                                    .read<SettingsService>();
                                final prefs =
                                    await SharedPreferences.getInstance();
                                _injectionManager = InjectionManager(
                                  controller: controller,
                                  prefs: prefs,
                                  sessionManager: sm,
                                );
                                _injectionManager!.setSettingsService(
                                  settingsService,
                                );

                                // Set ghost mode flags (scripts already injected by preloader)
                                _setGhostModeFlags(controller, settingsService);

                                // Navigate to startup page if not Home
                                if (settingsService.startupPage != 'home') {
                                  await controller.loadUrl(
                                    urlRequest: URLRequest(
                                      url: WebUri(settingsService.startupUrl),
                                    ),
                                  );
                                }

                                // Force-inject grayscale on initial WebView creation,
                                // because the preloader's keepAlive causes the main
                                // WebView to skip onLoadStop on cold start.
                                if (settingsService.isGrayscaleActiveNow) {
                                  try {
                                    await controller.evaluateJavascript(
                                      source: grayscale.kGrayscaleJS,
                                    );
                                  } catch (_) {}
                                }

                                _registerJavaScriptHandlers(controller);

                                // ── FocusGram v2 overlay initial sync ───────────────
                                // ScriptEngineV2Overlay reads enabled state from prefs keys:
                                //   fg_v2_{scriptName}_enabled
                                // Set them BEFORE DOCUMENT_START scripts are injected.
                                // V2 overlay toggles:
                                // - ghost_mode: user FocusGram "ghostMode" controls it
                                // - others: keep using existing v2 toggles
                                await prefs.setBool(
                                  'fg_v2_${V2OverlayScriptId.ghostMode.name}_enabled',
                                  settingsService.ghostMode,
                                );
                                await prefs.setBool(
                                  'fg_v2_${V2OverlayScriptId.adBlockerDom.name}_enabled',
                                  settingsService.v2AdBlockerDomEnabled,
                                );
                                await prefs.setBool(
                                  'fg_v2_${V2OverlayScriptId.contentHider.name}_enabled',
                                  settingsService.v2ContentHiderEnabled,
                                );
                                await prefs.setBool(
                                  'fg_v2_${V2OverlayScriptId.fetchInterceptor.name}_enabled',
                                  _shouldEnableFetchInterceptor(
                                    settingsService,
                                  ),
                                );
                                await prefs.setBool(
                                  'fg_v2_${V2OverlayScriptId.autoplayBlocker.name}_enabled',
                                  settingsService.blockAutoplay,
                                );

                                // Content hider flags consumed by v2/content_hider.js
                                await prefs.setBool(
                                  'content_stories',
                                  settingsService.contentStories,
                                );
                                await prefs.setBool(
                                  'content_posts',
                                  settingsService.contentPosts,
                                );
                                await prefs.setBool(
                                  'content_reels',
                                  settingsService.contentReels,
                                );
                                await prefs.setBool(
                                  'content_suggested',
                                  settingsService.contentSuggested,
                                );

                                // Phase 1 V2 overlay engine (theme + best-effort ad DOM cleanup)
                                _v2Engine = ScriptEngineV2Overlay(
                                  controller: controller,
                                  prefs: prefs,
                                );
                                await _v2Engine!.initDocumentStartScripts();

                                // Start safety timeout — clears loading state
                                // if onLoadStop never fires (e.g. network stall).
                                _resetLoadingTimeout();
                              },
                              onLoadStart: (controller, url) {
                                if (!mounted) return;
                                final u = url?.toString() ?? '';
                                _syncDirectThreadState(u);
                                // Update homepage graphql block flag SYNCHRONOUSLY
                                // (before setState, so shouldInterceptRequest sees it)
                                final path = Uri.tryParse(u)?.path ?? u;
                                _blockHomepageGraphql =
                                    settings.ghostMode &&
                                    (path == '/' ||
                                        path == '' ||
                                        path == '/explore/');
                                final lower = u.toLowerCase();
                                final isOnboardingUrl =
                                    lower.contains('/accounts/login') ||
                                    lower.contains('/accounts/emailsignup') ||
                                    lower.contains('/accounts/signup') ||
                                    lower.contains('/legal/') ||
                                    lower.contains('/help/');
                                setState(() {
                                  _isLoading = true;
                                  _lastMainFrameLoadStartedAt = DateTime.now();
                                  _currentUrl = u;
                                  _hasError = false;
                                  _showSkeleton = !isOnboardingUrl;
                                  // Update skeleton type based on the URL being loaded
                                  _skeletonType = getSkeletonTypeFromUrl(u);
                                });
                                // FIX 4: Reset the safety timeout on each new load
                                _resetLoadingTimeout();
                              },
                              onLoadStop: (controller, url) async {
                                _pullToRefreshController.endRefreshing();
                                if (!mounted) return;

                                // FIX 4: Cancel the safety timeout — load completed normally
                                _loadingTimeout?.cancel();

                                final current = url?.toString() ?? '';
                                _syncDirectThreadState(current);

                                // Re-set ghost mode flags on every page load.
                                // evaluateJavascript-set flags are destroyed when
                                // the JS context resets on navigation. The flags
                                // are also prepended to initialUserScripts, but
                                // this covers the toggle-off → reload case.
                                final s = context.read<SettingsService>();
                                _setGhostModeFlags(controller, s);

                                setState(() {
                                  _isLoading = false;
                                  _currentUrl = current;
                                  _hasError = false;
                                });

                                await _injectionManager
                                    ?.runAllPostLoadInjections(current);

                                // Phase 1 V2 overlay DOM scripts
                                await _v2Engine?.injectDocumentEndScripts();

                                // Re-inject grayscale on every page load
                                if (s.isGrayscaleActiveNow) {
                                  await controller.evaluateJavascript(
                                    source: grayscale.kGrayscaleJS,
                                  );
                                } else {
                                  await controller.evaluateJavascript(
                                    source: grayscale.kGrayscaleOffJS,
                                  );
                                }

                                await controller.evaluateJavascript(
                                  source:
                                      InjectionController.notificationBridgeJS,
                                );

                                final isIsolatedReel =
                                    current.contains('/reel/') &&
                                    !current.contains('/reels/');
                                await _setIsolatedPlayer(isIsolatedReel);

                                await controller.evaluateJavascript(
                                  source: kNativeFeelingPostLoadScript,
                                );

                                await Future.delayed(
                                  const Duration(milliseconds: 100),
                                );
                                if (mounted) {
                                  setState(() => _showSkeleton = false);
                                }
                              },
                              shouldOverrideUrlLoading:
                                  (controller, navigationAction) async {
                                    final url =
                                        navigationAction.request.url
                                            ?.toString() ??
                                        '';
                                    final uri = navigationAction.request.url;
                                    final appSettings = context
                                        .read<SettingsService>();
                                    _syncDirectThreadState(url);

                                    final disableReels =
                                        appSettings.disableReelsEntirely;
                                    final disableExplore =
                                        appSettings.disableExploreEntirely;

                                    bool isReelsUrl(String u) =>
                                        u.contains('/reel/') ||
                                        u.contains('/reels/');
                                    bool isExploreUrl(String u) =>
                                        u.contains('/explore/');

                                    void showBlocked(String msg) {
                                      if (!mounted) return;
                                      ScaffoldMessenger.of(
                                        context,
                                      ).showSnackBar(
                                        SnackBar(
                                          content: Text(msg),
                                          behavior: SnackBarBehavior.floating,
                                          margin: const EdgeInsets.fromLTRB(
                                            16,
                                            0,
                                            16,
                                            20,
                                          ),
                                        ),
                                      );
                                    }

                                    if (disableReels && isReelsUrl(url)) {
                                      showBlocked('Reels are disabled');
                                      return NavigationActionPolicy.CANCEL;
                                    }

                                    if (disableExplore && isExploreUrl(url)) {
                                      // Show overlay immediately without navigating away
                                      setState(
                                        () => _exploreBlockedOverlay = true,
                                      );
                                      // Don't go back - just block the navigation
                                      return NavigationActionPolicy.CANCEL;
                                    }

                                    if (uri != null &&
                                        uri.host.contains('instagram.com') &&
                                        (url.contains('accounts/settings') ||
                                            url.contains('accounts/edit'))) {
                                      return NavigationActionPolicy.ALLOW;
                                    }

                                    if (url.contains('/reels/') &&
                                        !context
                                            .read<SessionManager>()
                                            .isSessionActive) {
                                      setState(
                                        () => _reelsBlockedOverlay = true,
                                      );
                                      return NavigationActionPolicy.CANCEL;
                                    }

                                    if (uri != null &&
                                        !uri.host.contains('instagram.com') &&
                                        !uri.host.contains('facebook.com') &&
                                        !uri.host.contains(
                                          'cdninstagram.com',
                                        ) &&
                                        !uri.host.contains('fbcdn.net')) {
                                      await launchUrl(
                                        uri,
                                        mode: LaunchMode.externalApplication,
                                      );
                                      return NavigationActionPolicy.CANCEL;
                                    }

                                    final decision = NavigationGuard.evaluate(
                                      url: url,
                                    );
                                    if (decision.blocked) {
                                      if (url.contains('/reels/')) {
                                        setState(
                                          () => _reelsBlockedOverlay = true,
                                        );
                                        return NavigationActionPolicy.CANCEL;
                                      }
                                      return NavigationActionPolicy.CANCEL;
                                    }

                                    return NavigationActionPolicy.ALLOW;
                                  },
                              onReceivedError: (controller, request, error) {
                                // FIX 5: Clear loading state on ANY main-frame
                                // error, not just HOST_LOOKUP and TIMEOUT.
                                // Previously, errors like CONNECTION_REFUSED or
                                // FAILED_URL_BLOCKED left _isLoading = true
                                // forever, causing the apparent "hang".
                                if (request.isForMainFrame == true) {
                                  _loadingTimeout?.cancel();
                                  if (mounted) {
                                    setState(() {
                                      _isLoading = false;
                                      _showSkeleton = false;
                                      // Only show the full error screen for
                                      // network-level failures, not blocked URLs
                                      if (error.type ==
                                              WebResourceErrorType
                                                  .HOST_LOOKUP ||
                                          error.type ==
                                              WebResourceErrorType.TIMEOUT) {
                                        _hasError = true;
                                      }
                                    });
                                  }
                                }
                              },
                            ),

                            if (_showSkeleton)
                              SkeletonScreen(skeletonType: _skeletonType),

                            if (!_isOnOnboardingPage &&
                                settings.minimalModeEnabled &&
                                !_minimalModeBannerDismissed)
                              Positioned(
                                left: 12,
                                right: 12,
                                top: 12,
                                child: _MinimalModeBanner(
                                  onDismiss: () {
                                    HapticFeedback.lightImpact();
                                    setState(
                                      () => _minimalModeBannerDismissed = true,
                                    );
                                  },
                                ),
                              ),

                            // Instagram's native bottom nav is used directly.
                            // NativeBottomNav overlay removed — faster, looks native,
                            // and reels tap naturally hits shouldOverrideUrlLoading.
                          ],
                        );
                      },
                    ),
                  ),
                ],
              ),
            ),
            if (_hasError)
              _NoInternetScreen(
                onRetry: () {
                  setState(() => _hasError = false);
                  _controller?.reload();
                },
              ),
            if (_isLoading)
              Positioned(
                top:
                    (_isOnOnboardingPage ? 0 : 60) +
                    MediaQuery.of(context).padding.top,
                left: 0,
                right: 0,
                child: const _InstagramGradientProgressBar(),
              ),
            _EdgePanel(key: _edgePanelKey, currentUrl: _currentUrl),

            if (_exploreBlockedOverlay)
              Positioned.fill(
                child: Consumer<SettingsService>(
                  builder: (ctx, settings, _) {
                    final isDark = settings.isDarkMode;
                    final bg = isDark ? Colors.black : Colors.white;
                    final textMain = isDark ? Colors.white : Colors.black;
                    final textDim = isDark ? Colors.white70 : Colors.black87;
                    final textSub = isDark ? Colors.white38 : Colors.black45;

                    return Container(
                      color: bg.withValues(alpha: 0.95),
                      padding: const EdgeInsets.all(32),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(
                            Icons.explore_off_rounded,
                            color: Colors.orangeAccent,
                            size: 80,
                          ),
                          const SizedBox(height: 24),
                          Text(
                            'Explore is Disabled',
                            style: GoogleFonts.grandHotel(
                              color: textMain,
                              fontSize: 42,
                            ),
                          ),
                          const SizedBox(height: 12),
                          Text(
                            'Explore is disabled in your FocusGram settings.',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                              color: textDim,
                              fontSize: 15,
                              height: 1.5,
                            ),
                          ),
                          const SizedBox(height: 48),
                          Text(
                            'You can re-enable Explore in Settings > Focus.',
                            textAlign: TextAlign.center,
                            style: TextStyle(color: textSub, fontSize: 12),
                          ),
                          const SizedBox(height: 20),
                          TextButton(
                            onPressed: () {
                              setState(() => _exploreBlockedOverlay = false);
                              _controller?.goBack();
                            },
                            child: Text(
                              'Go Back',
                              style: TextStyle(color: textSub),
                            ),
                          ),
                        ],
                      ),
                    );
                  },
                ),
              ),

            if (_reelsBlockedOverlay)
              Positioned.fill(
                child: Consumer<SettingsService>(
                  builder: (ctx, settings, _) {
                    final isDark = settings.isDarkMode;
                    final bg = isDark ? Colors.black : Colors.white;
                    final textMain = isDark ? Colors.white : Colors.black;
                    final textDim = isDark ? Colors.white70 : Colors.black87;
                    final textSub = isDark ? Colors.white38 : Colors.black45;

                    return Container(
                      color: bg.withValues(alpha: 0.95),
                      padding: const EdgeInsets.all(32),
                      child: Consumer<SessionManager>(
                        builder: (ctx, sm, _) {
                          final onCooldown = sm.isCooldownActive;
                          final quotaFinished = sm.dailyRemainingSeconds <= 0;
                          final reelsHardDisabled =
                              settings.disableReelsEntirely;

                          return Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(
                                quotaFinished
                                    ? Icons.timer_off_rounded
                                    : Icons.lock_clock_rounded,
                                color: quotaFinished
                                    ? Colors.redAccent
                                    : Colors.blueAccent,
                                size: 80,
                              ),
                              const SizedBox(height: 24),
                              Text(
                                quotaFinished
                                    ? 'Daily Quota Finished'
                                    : (reelsHardDisabled
                                          ? 'Reels are Disabled'
                                          : 'Reels are Blocked'),
                                style: GoogleFonts.grandHotel(
                                  color: textMain,
                                  fontSize: 42,
                                ),
                              ),
                              const SizedBox(height: 12),
                              Text(
                                quotaFinished
                                    ? 'You have reached your planned limit for today. Step away and focus on what matters most.'
                                    : (reelsHardDisabled
                                          ? 'Reels are disabled in your settings.'
                                          : 'Start a planned reel session to access the feed. Use Instagram for connection, not distraction.'),
                                textAlign: TextAlign.center,
                                style: TextStyle(
                                  color: textDim,
                                  fontSize: 15,
                                  height: 1.5,
                                ),
                              ),
                              const SizedBox(height: 48),
                              if (quotaFinished) ...[
                                Text(
                                  'Your discipline is your strength.',
                                  style: TextStyle(
                                    color: Colors.greenAccent.withValues(
                                      alpha: 0.8,
                                    ),
                                    fontStyle: FontStyle.italic,
                                  ),
                                ),
                                const SizedBox(height: 24),
                                Text(
                                  'To adjust your daily limit, go to Settings > Guardrails.',
                                  textAlign: TextAlign.center,
                                  style: TextStyle(
                                    color: textSub,
                                    fontSize: 12,
                                  ),
                                ),
                              ] else if (reelsHardDisabled) ...[
                                Text(
                                  'You can re-enable Reels in Settings > Focus.',
                                  textAlign: TextAlign.center,
                                  style: TextStyle(
                                    color: textSub,
                                    fontSize: 12,
                                  ),
                                ),
                              ] else if (onCooldown) ...[
                                Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 28,
                                    vertical: 14,
                                  ),
                                  decoration: BoxDecoration(
                                    color: Colors.orange.withValues(
                                      alpha: 0.12,
                                    ),
                                    borderRadius: BorderRadius.circular(30),
                                    border: Border.all(
                                      color: Colors.orange.withValues(
                                        alpha: 0.4,
                                      ),
                                    ),
                                  ),
                                  child: Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      const Icon(
                                        Icons.hourglass_bottom_rounded,
                                        color: Colors.orangeAccent,
                                        size: 18,
                                      ),
                                      const SizedBox(width: 8),
                                      Text(
                                        'Cooldown: ${_fmtSeconds(sm.cooldownRemainingSeconds)}',
                                        style: const TextStyle(
                                          color: Colors.orangeAccent,
                                          fontSize: 16,
                                          fontWeight: FontWeight.w600,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                const SizedBox(height: 8),
                                Text(
                                  'Wait for the cooldown to expire before starting a new session.',
                                  textAlign: TextAlign.center,
                                  style: TextStyle(
                                    color: textSub,
                                    fontSize: 12,
                                  ),
                                ),
                              ] else
                                ElevatedButton(
                                  onPressed: _showReelSessionPicker,
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: Colors.blueAccent,
                                    foregroundColor: Colors.white,
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 40,
                                      vertical: 16,
                                    ),
                                    shape: RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(30),
                                    ),
                                  ),
                                  child: const Text(
                                    'Start Session',
                                    style: TextStyle(
                                      fontSize: 16,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ),
                              const SizedBox(height: 20),
                              TextButton(
                                onPressed: () {
                                  setState(() => _reelsBlockedOverlay = false);
                                  _controller?.goBack();
                                },
                                child: Text(
                                  'Go Back',
                                  style: TextStyle(color: textSub),
                                ),
                              ),
                            ],
                          );
                        },
                      ),
                    );
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }

  void _registerJavaScriptHandlers(InAppWebViewController controller) {
    controller.addJavaScriptHandler(
      handlerName: 'FocusGramNotificationChannel',
      callback: (args) {
        if (!mounted) return null;
        final settings = context.read<SettingsService>();
        final msg = (args.isNotEmpty ? args[0] : '') as String;

        if (DateTime.now().difference(_lastMainFrameLoadStartedAt).inSeconds <
            6) {
          return null;
        }

        String title = '';
        String body = '';
        bool isDM = false;

        if (msg.contains(': ')) {
          final parts = msg.split(': ');
          title = parts[0];
          body = parts.sublist(1).join(': ');
          isDM =
              title.toLowerCase().contains('message') ||
              title.toLowerCase().contains('direct');
        } else {
          isDM = msg == 'DM';
          title = isDM ? 'Instagram Message' : 'Instagram Notification';
          body = isDM
              ? 'Someone messaged you'
              : 'New activity in notifications';
        }

        if (isDM && !settings.notifyDMs) return null;
        if (!isDM && !settings.notifyActivity) return null;

        try {
          NotificationService().showNotification(
            // Use hash of message for unique ID - prevents random duplicate notifications
            id: (msg.hashCode.abs() % 100000) + 2000,
            title: title,
            body: body,
          );
        } catch (_) {}
        return null;
      },
    );

    controller.addJavaScriptHandler(
      handlerName: 'FocusGramBlocked',
      callback: (args) {
        if (!mounted) return null;
        final what = (args.isNotEmpty ? args[0] : '') as String? ?? '';
        final text = what == 'reels'
            ? 'Reels are disabled'
            : (what == 'explore' ? 'Explore is disabled' : 'Content disabled');
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(text),
            behavior: SnackBarBehavior.floating,
            margin: const EdgeInsets.fromLTRB(16, 0, 16, 20),
          ),
        );
        return null;
      },
    );

    controller.addJavaScriptHandler(
      handlerName: 'FocusGramShareChannel',
      callback: (args) {
        if (!mounted) return;
        try {
          final data = (args.isNotEmpty ? args[0] : '') as String;
          String url = data;
          try {
            final match = RegExp(r'"url":"([^"]+)"').firstMatch(data);
            if (match != null) url = match.group(1) ?? data;
          } catch (_) {}
          Clipboard.setData(ClipboardData(text: url));
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Link copied (tracking removed)'),
              backgroundColor: Color(0xFF1A1A2E),
              behavior: SnackBarBehavior.floating,
              margin: EdgeInsets.fromLTRB(16, 0, 16, 20),
            ),
          );
        } catch (_) {}
      },
    );

    controller.addJavaScriptHandler(
      handlerName: 'FocusGramMediaDownload',
      callback: (args) async {
        if (!mounted) return null;

        final raw = (args.isNotEmpty ? args[0] : '') as String;

        // We still want to show a tailored snackbar message, but the heavy
        // JSON + security validation is delegated to the pure helper.
        String type = 'video';
        try {
          final payload = jsonDecode(raw) as Map<String, dynamic>;
          type = (payload['type'] as String? ?? 'video').toString();
        } catch (_) {
          // If payload isn't parseable, helper will reject anyway.
        }

        final ok = await handleFocusGramMediaDownload(
          raw: raw,
          launch: (uri) => launchUrl(uri, mode: LaunchMode.externalApplication),
        );

        if (!mounted) return null;

        if (!ok) return null;

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              type == 'photo'
                  ? 'Opening photo download…'
                  : 'Opening video download…',
            ),
            behavior: SnackBarBehavior.floating,
            margin: const EdgeInsets.fromLTRB(16, 0, 16, 20),
          ),
        );
        return null;
      }, // closes callback
    ); // closes addJavaScriptHandler

    controller.addJavaScriptHandler(
      handlerName: 'FocusGramThemeChannel',
      callback: (args) {
        final value = (args.isNotEmpty ? args[0] : '') as String;
        context.read<SettingsService>().setDarkMode(value == 'dark');
      },
    );

    controller.addJavaScriptHandler(
      handlerName: 'Haptic',
      callback: (args) {
        HapticFeedback.lightImpact();
        return null;
      },
    );

    // ReelMetadata handler removed — reel history feature deleted

    controller.addJavaScriptHandler(
      handlerName: 'UrlChange',
      callback: (args) async {
        final url = (args.isNotEmpty ? args[0] : '') as String? ?? '';

        // Update _currentUrl SYNCHRONOUSLY before any async operations,
        // so shouldInterceptRequest sees the correct path immediately.
        _currentUrl = url;

        _syncDirectThreadState(url);

        final s = context.read<SettingsService>();

        // Update homepage graphql block for SPA navigation
        final path = Uri.tryParse(url)?.path ?? url;
        _blockHomepageGraphql =
            s.ghostMode && (path == '/' || path == '' || path == '/explore/');

        // Re-set ghost mode flags on SPA navigation (no page reload).
        _setGhostModeFlags(controller, s);

        await _injectionManager?.runAllPostLoadInjections(url);

        // Re-inject grayscale on SPA nav (no page reload)
        if (s.isGrayscaleActiveNow) {
          await controller.evaluateJavascript(source: grayscale.kGrayscaleJS);
        } else {
          await controller.evaluateJavascript(
            source: grayscale.kGrayscaleOffJS,
          );
        }

        // Phase 1 V2 overlay re-inject on SPA route changes
        await _v2Engine?.injectDocumentEndScripts();

        if (!mounted) return;
        setState(() {
          _currentUrl = url;
          // SPA navigations never fire onLoadStop — clear skeleton here
          // so it doesn't stay visible forever (e.g. when navigating to DMs)
          _showSkeleton = false;
          _isLoading = false;
        });

        final disableReels = context
            .read<SettingsService>()
            .disableReelsEntirely;
        final disableExplore = context
            .read<SettingsService>()
            .disableExploreEntirely;

        final isReels = path.startsWith('/reels') || path.startsWith('/reel/');
        final isExplore = path.startsWith('/explore');

        // Block reel navigation that slipped through (e.g. DM-embedded reels)
        if (disableReels && isReels) {
          setState(() => _reelsBlockedOverlay = true);
          await _controller?.goBack();
          return;
        }

        if (_controller != null) {
          if (disableExplore && isExplore) {
            await _controller!.loadUrl(
              urlRequest: URLRequest(url: WebUri('https://www.instagram.com/')),
            );
          }
        }

        // Update isolated player flag for DM-embedded reels
        final isIsolatedReel =
            path.contains('/reel/') && !path.startsWith('/reels');
        await _setIsolatedPlayer(isIsolatedReel);

        return null;
      },
    );
  }
}

// ─── Supporting widgets (unchanged) ──────────────────────────────────────────

class _MinimalModeBanner extends StatelessWidget {
  final VoidCallback onDismiss;

  const _MinimalModeBanner({required this.onDismiss});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Material(
      color: Colors.transparent,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: (isDark ? Colors.black : Colors.white).withValues(alpha: 0.92),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: isDark ? Colors.white12 : Colors.black12,
            width: 0.5,
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: isDark ? 0.35 : 0.12),
              blurRadius: 18,
              spreadRadius: 2,
            ),
          ],
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(
                'Minimal mode — Feed & DMs only 🎯',
                style: TextStyle(
                  color: isDark ? Colors.white : Colors.black,
                  fontWeight: FontWeight.w600,
                  fontSize: 13,
                ),
              ),
            ),
            InkWell(
              onTap: onDismiss,
              borderRadius: BorderRadius.circular(20),
              child: Padding(
                padding: const EdgeInsets.all(4),
                child: Icon(
                  Icons.close,
                  size: 18,
                  color: isDark ? Colors.white70 : Colors.black54,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EdgePanel extends StatefulWidget {
  final String currentUrl;
  const _EdgePanel({super.key, this.currentUrl = ''});
  @override
  State<_EdgePanel> createState() => _EdgePanelState();
}

class _EdgePanelState extends State<_EdgePanel> {
  bool _isExpanded = false;
  void _toggleExpansion() => setState(() => _isExpanded = !_isExpanded);

  @override
  Widget build(BuildContext context) {
    final sm = context.watch<SessionManager>();
    final settings = context.watch<SettingsService>();
    final isDark = settings.isDarkMode;
    final reelsHardDisabled = settings.disableReelsEntirely;
    final panelBg = isDark ? const Color(0xFF111214) : Colors.white;
    final textMain = isDark ? Colors.white : Colors.black87;
    final textSub = isDark ? Colors.white60 : Colors.black54;
    final border = isDark ? Colors.white12 : Colors.black12;
    final canStart =
        !reelsHardDisabled &&
        !sm.isSessionActive &&
        !sm.isCooldownActive &&
        sm.dailyRemainingSeconds > 0;
    final statusColor = reelsHardDisabled
        ? Colors.redAccent
        : sm.isSessionActive
        ? Colors.greenAccent
        : sm.isCooldownActive
        ? Colors.orangeAccent
        : Colors.blueAccent;
    final statusText = reelsHardDisabled
        ? 'Reels blocked'
        : sm.isSessionActive
        ? 'Session active'
        : sm.isCooldownActive
        ? 'Cooldown'
        : sm.dailyRemainingSeconds <= 0
        ? 'Daily limit reached'
        : 'Ready';
    final sessionProgress = sm.isSessionActive && sm.perSessionSeconds > 0
        ? (sm.remainingSessionSeconds / sm.perSessionSeconds).clamp(0.0, 1.0)
        : 0.0;

    return Stack(
      children: [
        if (_isExpanded)
          Positioned.fill(
            child: GestureDetector(
              onTap: _toggleExpansion,
              behavior: HitTestBehavior.opaque,
              child: Container(
                color: Colors.black.withValues(alpha: isDark ? 0.15 : 0.05),
              ),
            ),
          ),
        AnimatedPositioned(
          duration: const Duration(milliseconds: 260),
          curve: Curves.easeOutCubic,
          right: _isExpanded ? 12 : -328,
          top: MediaQuery.of(context).padding.top + 72,
          child: Container(
            width: 316,
            constraints: BoxConstraints(
              maxHeight:
                  MediaQuery.of(context).size.height -
                  MediaQuery.of(context).padding.top -
                  96,
            ),
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: panelBg.withValues(alpha: 0.98),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: border, width: 0.5),
              boxShadow: [
                BoxShadow(
                  color: (isDark ? Colors.black : Colors.black12).withValues(
                    alpha: 0.3,
                  ),
                  blurRadius: 24,
                  offset: const Offset(0, 12),
                ),
              ],
            ),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 38,
                        height: 38,
                        decoration: BoxDecoration(
                          color: statusColor.withValues(alpha: 0.14),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Icon(Icons.timer_outlined, color: statusColor),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Focus Control',
                              style: TextStyle(
                                color: textMain,
                                fontSize: 17,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              statusText,
                              style: TextStyle(
                                color: statusColor,
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ),
                      ),
                      // Level badge
                      Consumer<LevelService>(
                        builder: (context, lv, _) => Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 3,
                          ),
                          decoration: BoxDecoration(
                            color: lv.level >= 3
                                ? Colors.purple.withValues(alpha: 0.2)
                                : Colors.grey.withValues(alpha: 0.15),
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(
                              color: lv.level >= 3
                                  ? Colors.purpleAccent.withValues(alpha: 0.4)
                                  : Colors.grey.withValues(alpha: 0.2),
                            ),
                          ),
                          child: Text(
                            'Lv ${lv.level}',
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                              color: lv.level >= 3
                                  ? Colors.purpleAccent
                                  : Colors.grey,
                            ),
                          ),
                        ),
                      ),
                      // Save current page — REMOVED
                      const SizedBox(width: 4),
                      IconButton(
                        tooltip: 'Close',
                        icon: Icon(
                          Icons.close_rounded,
                          color: textSub,
                          size: 22,
                        ),
                        onPressed: _toggleExpansion,
                      ),
                    ],
                  ),
                  // Bait Me button row
                  _BaitMeButtonRow(),
                  const SizedBox(height: 18),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: (isDark ? Colors.white : Colors.black).withValues(
                        alpha: 0.05,
                      ),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Reel session',
                          style: TextStyle(color: textSub, fontSize: 12),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          sm.isSessionActive
                              ? _formatTime(sm.remainingSessionSeconds)
                              : 'Not running',
                          style: TextStyle(
                            color: sm.isSessionActive ? statusColor : textMain,
                            fontSize: sm.isSessionActive ? 38 : 24,
                            fontWeight: FontWeight.w700,
                            fontFeatures: const [FontFeature.tabularFigures()],
                          ),
                        ),
                        const SizedBox(height: 12),
                        ClipRRect(
                          borderRadius: BorderRadius.circular(999),
                          child: LinearProgressIndicator(
                            value: sm.isSessionActive ? sessionProgress : 0,
                            minHeight: 6,
                            backgroundColor: isDark
                                ? Colors.white10
                                : Colors.black12,
                            valueColor: AlwaysStoppedAnimation<Color>(
                              statusColor,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 14),
                  Row(
                    children: [
                      Expanded(
                        child: _buildStatCard(
                          'Quota',
                          '${sm.dailyRemainingSeconds ~/ 60}m',
                          Icons.hourglass_bottom_rounded,
                          isDark: isDark,
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: _buildStatCard(
                          'Auto-close app',
                          sm.appSessionRemainingSeconds > 0
                              ? _formatTime(sm.appSessionRemainingSeconds)
                              : 'Off',
                          Icons.lock_clock_rounded,
                          isDark: isDark,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  _buildStatusRow(
                    icon: Icons.local_cafe_outlined,
                    label: 'Cooldown',
                    value: sm.isCooldownActive
                        ? _formatTime(sm.cooldownRemainingSeconds)
                        : 'Inactive',
                    color: sm.isCooldownActive ? Colors.orangeAccent : textSub,
                    isDark: isDark,
                  ),
                  _buildStatusRow(
                    icon: Icons.block_rounded,
                    label: 'Hard block',
                    value: reelsHardDisabled ? 'On' : 'Off',
                    color: reelsHardDisabled ? Colors.redAccent : textSub,
                    isDark: isDark,
                  ),

                  const SizedBox(height: 16),
                  if (sm.isSessionActive)
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton.icon(
                        onPressed: () {
                          context.read<SessionManager>().endSession();
                          HapticFeedback.mediumImpact();
                        },
                        icon: const Icon(Icons.stop_circle_outlined, size: 18),
                        label: const Text('End Session'),
                        style: FilledButton.styleFrom(
                          backgroundColor: Colors.redAccent,
                          foregroundColor: Colors.white,
                        ),
                      ),
                    )
                  else
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton.icon(
                        onPressed: canStart
                            ? () {
                                _toggleExpansion();
                                context
                                    .findAncestorStateOfType<
                                      _MainWebViewPageState
                                    >()
                                    ?._showReelSessionPicker();
                              }
                            : null,
                        icon: const Icon(Icons.play_arrow_rounded, size: 20),
                        label: const Text('Start Session'),
                      ),
                    ),
                  const SizedBox(height: 8),
                  if (!canStart && !sm.isSessionActive)
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          reelsHardDisabled
                              ? 'Turn off hard Reels blocking in Focus Mode to use timed sessions.'
                              : sm.isCooldownActive
                              ? 'A cooldown is active before the next Reel session.'
                              : 'Your daily Reel quota is used up.',
                          style: TextStyle(
                            color: textSub,
                            fontSize: 12,
                            height: 1.35,
                          ),
                        ),
                        if (sm.dailyRemainingSeconds <= 0 &&
                            !reelsHardDisabled &&
                            !sm.isCooldownActive)
                          Padding(
                            padding: const EdgeInsets.only(top: 8),
                            child: Consumer<CreditStore>(
                              builder: (ctx, credits, _) {
                                if (!credits.canWatchAdToday) {
                                  return Text(
                                    'Ad limit reached (3/day)',
                                    style: TextStyle(
                                      color: textSub,
                                      fontSize: 11,
                                    ),
                                  );
                                }
                                return SizedBox(
                                  width: double.infinity,
                                  height: 40,
                                  child: OutlinedButton.icon(
                                    onPressed: () async {
                                      final adResult =
                                          await Navigator.push<bool>(
                                            context,
                                            MaterialPageRoute(
                                              builder: (_) =>
                                                  const AdsterraAdScreen(
                                                    sessionType: 'reels',
                                                    requiredSeconds: 20,
                                                  ),
                                            ),
                                          );
                                      if (adResult == true && context.mounted) {
                                        context
                                            .read<CreditStore>()
                                            .addReelsMinutes(amount: 2);
                                        context
                                            .read<SessionManager>()
                                            .addBonusDailyMinutes(2);
                                        HapticFeedback.heavyImpact();
                                      }
                                    },
                                    icon: const Icon(Icons.videocam, size: 16),
                                    label: Text(
                                      'Watch Ad (+2 min) '
                                      '(${CreditStore.maxDailyAds - credits.adsWatchedToday}/3 today)',
                                      style: const TextStyle(fontSize: 12),
                                    ),
                                    style: OutlinedButton.styleFrom(
                                      foregroundColor: Colors.orangeAccent,
                                      side: BorderSide(
                                        color: Colors.orangeAccent.withValues(
                                          alpha: 0.4,
                                        ),
                                      ),
                                      shape: RoundedRectangleBorder(
                                        borderRadius: BorderRadius.circular(10),
                                      ),
                                    ),
                                  ),
                                );
                              },
                            ),
                          ),
                      ],
                    ),
                  const SizedBox(height: 10),
                  Divider(color: border),
                  ListTile(
                    onTap: () {
                      _toggleExpansion();
                      context
                          .findAncestorStateOfType<_MainWebViewPageState>()
                          ?._signOut();
                    },
                    leading: const Icon(
                      Icons.logout_rounded,
                      color: Colors.redAccent,
                      size: 20,
                    ),
                    title: const Text(
                      'Switch Account',
                      style: TextStyle(
                        color: Colors.redAccent,
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildStatCard(
    String label,
    String value,
    IconData icon, {
    bool isDark = true,
  }) {
    final textMain = isDark ? Colors.white : Colors.black;
    final textSub = isDark ? Colors.white54 : Colors.black54;
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: (isDark ? Colors.white : Colors.black).withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: textSub, size: 18),
          const SizedBox(height: 8),
          Text(label, style: TextStyle(color: textSub, fontSize: 11)),
          const SizedBox(height: 2),
          Text(
            value,
            style: TextStyle(
              color: textMain,
              fontSize: 18,
              fontWeight: FontWeight.w700,
              fontFeatures: const [FontFeature.tabularFigures()],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStatusRow({
    required IconData icon,
    required String label,
    required String value,
    required Color color,
    bool isDark = true,
  }) {
    final textMain = isDark ? Colors.white : Colors.black87;
    final textSub = isDark ? Colors.white54 : Colors.black54;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Icon(icon, color: color, size: 18),
          const SizedBox(width: 10),
          Expanded(
            child: Text(label, style: TextStyle(color: textSub, fontSize: 13)),
          ),
          Text(
            value,
            style: TextStyle(
              color: color == textSub ? textMain : color,
              fontWeight: FontWeight.w600,
              fontFeatures: const [FontFeature.tabularFigures()],
            ),
          ),
        ],
      ),
    );
  }

  String _formatTime(int seconds) {
    final m = seconds ~/ 60;
    final s = seconds % 60;
    return '${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
  }
}

/// Small row showing the Bait Me button and daily XP for the edge panel.
class _BaitMeButtonRow extends StatelessWidget {
  const _BaitMeButtonRow();

  @override
  Widget build(BuildContext context) {
    final levelService = context.watch<LevelService>();
    final baitEngine = context.watch<BaitEngine>();
    final isUnlocked = levelService.isFeatureUnlocked(AppFeature.baitMe);

    if (!isUnlocked) return const SizedBox.shrink();

    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: SizedBox(
        width: double.infinity,
        height: 48,
        child: ElevatedButton.icon(
          onPressed: baitEngine.isOnCooldown
              ? null
              : () => _openBaitMe(context),
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.purpleAccent.withValues(alpha: 0.2),
            foregroundColor: Colors.purpleAccent,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
          ),
          icon: const Icon(Icons.casino_rounded, size: 20),
          label: Text(
            baitEngine.isOnCooldown
                ? 'Bait Me (${baitEngine.cooldownRemainingMinutes}m cooldown)'
                : '🎲 Bait Me — Feel Lucky?',
            style: const TextStyle(fontWeight: FontWeight.w600),
          ),
        ),
      ),
    );
  }

  void _openBaitMe(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const BaitMeFullScreen()),
    );
  }
}

class _BrandedTopBar extends StatelessWidget {
  final VoidCallback? onFocusControlTap;
  final VoidCallback? onDmGhostToggle;
  final VoidCallback? onReload;
  final String currentUrl;
  final bool dmGhostActive;
  const _BrandedTopBar({
    this.onFocusControlTap,
    this.onDmGhostToggle,
    this.onReload,
    this.currentUrl = '',
    this.dmGhostActive = false,
  });

  static bool _isDirectInbox(String url) {
    final path = Uri.tryParse(url)?.path ?? url;
    return path == '/direct/inbox/' || path == '/direct/inbox';
  }

  static bool _isDirectThread(String url) {
    final path = Uri.tryParse(url)?.path ?? url;
    return RegExp(r'^/direct/t/').hasMatch(path);
  }

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    final isDark = settings.isDarkMode;
    final barBg = isDark ? Colors.black : Colors.white;
    final textMain = isDark ? Colors.white : Colors.black;
    final iconColor = isDark ? Colors.white70 : Colors.black54;
    final border = isDark ? Colors.white12 : Colors.black12;
    final showDmGhostBtn = _isDirectThread(currentUrl) && dmGhostActive;
    final showReloadBtn = _isDirectInbox(currentUrl);

    return Container(
      height: 60,
      decoration: BoxDecoration(
        color: barBg,
        border: Border(bottom: BorderSide(color: border, width: 0.5)),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            // Left: settings icon
            IconButton(
              icon: Icon(Icons.settings_outlined, color: iconColor, size: 22),
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const SettingsPage()),
              ),
            ),

            // Center: FocusGram logo (or DM ghost badge)
            if (showDmGhostBtn)
              GestureDetector(
                onTap: onDmGhostToggle,
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.redAccent.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                      color: Colors.redAccent.withValues(alpha: 0.4),
                    ),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(
                        Icons.visibility_off,
                        color: Colors.redAccent,
                        size: 16,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        'DM Ghost ON',
                        style: TextStyle(
                          color: Colors.redAccent,
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(width: 4),
                      Icon(
                        Icons.close,
                        color: Colors.redAccent.withValues(alpha: 0.6),
                        size: 14,
                      ),
                    ],
                  ),
                ),
              )
            else
              Text(
                'FocusGram',
                style: GoogleFonts.grandHotel(
                  color: textMain,
                  fontSize: 32,
                  letterSpacing: 0.5,
                ),
              ),

            // Right: reload button + timer icon
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (showReloadBtn)
                  IconButton(
                    icon: Icon(
                      Icons.refresh_rounded,
                      color: iconColor,
                      size: 22,
                    ),
                    onPressed: onReload,
                    tooltip: 'Reload page',
                  ),
                IconButton(
                  icon: const Icon(
                    Icons.timer_outlined,
                    color: Colors.blueAccent,
                    size: 22,
                  ),
                  onPressed: onFocusControlTap,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _InstagramGradientProgressBar extends StatelessWidget {
  const _InstagramGradientProgressBar();
  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 2.5,
      child: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [
              Color(0xFFFEDA75),
              Color(0xFFFA7E1E),
              Color(0xFFD62976),
              Color(0xFF962FBF),
              Color(0xFF4F5BD5),
            ],
            begin: Alignment.centerLeft,
            end: Alignment.centerRight,
          ),
        ),
      ),
    );
  }
}

class _UpdateBanner extends StatelessWidget {
  const _UpdateBanner();

  @override
  Widget build(BuildContext context) {
    return Consumer<UpdateCheckerService>(
      builder: (context, update, _) {
        if (!update.hasUpdate) return const SizedBox.shrink();

        return Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [Colors.blue.shade700, Colors.blue.shade900],
            ),
          ),
          child: Row(
            children: [
              const Icon(
                Icons.system_update_alt,
                color: Colors.white,
                size: 18,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Update Available: ${update.updateInfo?.latestVersion ?? ''}',
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 13,
                      ),
                    ),
                    const SizedBox(height: 2),
                    InkWell(
                      onTap: () {
                        final url = update.updateInfo?.releaseUrl;
                        if (url != null && url.isNotEmpty) {
                          launchUrl(
                            Uri.parse(url),
                            mode: LaunchMode.externalApplication,
                          );
                        }
                      },
                      child: const Text(
                        'Download on GitHub →',
                        style: TextStyle(
                          color: Colors.white70,
                          fontSize: 12,
                          decoration: TextDecoration.underline,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              IconButton(
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(),
                onPressed: () => update.dismissUpdate(),
                icon: const Icon(Icons.close, color: Colors.white70, size: 18),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _NoInternetScreen extends StatelessWidget {
  final VoidCallback onRetry;
  const _NoInternetScreen({required this.onRetry});
  @override
  Widget build(BuildContext context) {
    final isDark = context.watch<SettingsService>().isDarkMode;
    return Container(
      color: isDark ? Colors.black : Colors.white,
      width: double.infinity,
      height: double.infinity,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.wifi_off_rounded,
            color: isDark ? Colors.white24 : Colors.black12,
            size: 80,
          ),
          const SizedBox(height: 24),
          Text(
            'No Connection',
            style: TextStyle(
              color: isDark ? Colors.white : Colors.black,
              fontSize: 20,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Please check your internet settings.',
            style: TextStyle(
              color: isDark ? Colors.white38 : Colors.black38,
              fontSize: 14,
            ),
          ),
          const SizedBox(height: 40),
          ElevatedButton(
            onPressed: onRetry,
            style: ElevatedButton.styleFrom(
              backgroundColor: isDark ? Colors.white : Colors.black,
              foregroundColor: isDark ? Colors.black : Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 12),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(20),
              ),
            ),
            child: const Text('Retry'),
          ),
        ],
      ),
    );
  }
}
