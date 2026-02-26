import 'dart:async';
import 'dart:collection';
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
import '../scripts/autoplay_blocker.dart';
import '../scripts/native_feel.dart';
import '../scripts/haptic_bridge.dart';
import '../scripts/spa_navigation_monitor.dart';
import '../scripts/content_disabling.dart';
import '../services/screen_time_service.dart';
import '../services/navigation_guard.dart';
import '../services/focusgram_router.dart';
import 'package:google_fonts/google_fonts.dart';
import '../services/notification_service.dart';
import '../features/update_checker/update_checker_service.dart';
import '../utils/discipline_challenge.dart';
import 'settings_page.dart';
import '../features/loading/skeleton_screen.dart';
import '../features/preloader/instagram_preloader.dart';

class MainWebViewPage extends StatefulWidget {
  const MainWebViewPage({super.key});

  @override
  State<MainWebViewPage> createState() => _MainWebViewPageState();
}

class _MainWebViewPageState extends State<MainWebViewPage>
    with WidgetsBindingObserver {
  InAppWebViewController? _controller;
  late final PullToRefreshController _pullToRefreshController;
  InjectionManager? _injectionManager;
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
  bool _isPreloaded = false;
  bool _minimalModeBannerDismissed = false;
  DateTime _lastMainFrameLoadStartedAt = DateTime.fromMillisecondsSinceEpoch(0);
  SkeletonType _skeletonType = SkeletonType.generic;

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

    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<SessionManager>().addListener(_onSessionChanged);
      context.read<SettingsService>().addListener(_onSettingsChanged);
      _lastSessionActive = context.read<SessionManager>().isSessionActive;
      // Initialise structural snapshots so first change is detected correctly
      final settings = context.read<SettingsService>();
      _lastMinimalMode = settings.minimalModeEnabled;
      _lastDisableReels = settings.disableReelsEntirely;
      _lastDisableExplore = settings.disableExploreEntirely;
      _lastBlockAutoplay = settings.blockAutoplay;
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

  /// Sets the isolated reel player flag in the WebView so the scroll-lock
  /// knows it should block swipe-to-next-reel.
  Future<void> _setIsolatedPlayer(bool active) async {
    await _controller?.evaluateJavascript(
      source: 'window.__focusgramIsolatedPlayer = $active;',
    );
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
  bool _lastBlockAutoplay = false;

  void _onSettingsChanged() {
    if (!mounted) return;
    final settings = context.read<SettingsService>();

    // 1. Apply all cosmetic changes immediately via injection
    if (_controller != null) {
      _controller!.evaluateJavascript(
        source: 'window.__fgBlockAutoplay = ${settings.blockAutoplay};',
      );
    }
    if (_controller != null && _injectionManager != null) {
      _injectionManager!.runAllPostLoadInjections(_currentUrl);
    }

    // 2. Rebuild Flutter widget tree (e.g. overlay conditions, banner state)
    setState(() {});

    // 3. Detect structural changes that need a full reload.
    //    CSS injection alone can't undo Instagram's already-rendered React DOM.
    final structuralChange =
        settings.minimalModeEnabled != _lastMinimalMode ||
        settings.disableReelsEntirely != _lastDisableReels ||
        settings.disableExploreEntirely != _lastDisableExplore ||
        settings.blockAutoplay != _lastBlockAutoplay;

    _lastMinimalMode = settings.minimalModeEnabled;
    _lastDisableReels = settings.disableReelsEntirely;
    _lastDisableExplore = settings.disableExploreEntirely;
    _lastBlockAutoplay = settings.blockAutoplay;

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
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (!mounted) return;
    final sm = context.read<SessionManager>();
    final screenTime = context.read<ScreenTimeService>();
    if (state == AppLifecycleState.resumed) {
      sm.setAppForeground(true);
      screenTime.startTracking();
    } else if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.inactive ||
        state == AppLifecycleState.detached) {
      sm.setAppForeground(false);
      screenTime.stopTracking();
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
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A1A),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
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
            onPressed: () {
              Navigator.pop(context);
              sm.endAppSession();
              SystemNavigator.pop();
            },
            child: const Text(
              'Close App',
              style: TextStyle(color: Colors.redAccent),
            ),
          ),
          if (sm.canExtendAppSession)
            ElevatedButton(
              onPressed: () {
                Navigator.pop(context);
                sm.extendAppSession();
                _extensionDialogShown = false;
              },
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.blue,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10),
                ),
              ),
              child: const Text('+10 minutes'),
            ),
        ],
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

  Future<void> _showReelSessionPicker() async {
    final settings = context.read<SettingsService>();
    if (settings.requireWordChallenge) {
      final passed = await DisciplineChallenge.show(context);
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
                  _buildReelSessionTile(5),
                  _buildReelSessionTile(10),
                  _buildReelSessionTile(15),
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
                              ),
                              initialUserScripts: UnmodifiableListView([
                                UserScript(
                                  source:
                                      'window.__fgBlockAutoplay = ${settings.blockAutoplay};',
                                  injectionTime:
                                      UserScriptInjectionTime.AT_DOCUMENT_START,
                                ),
                                UserScript(
                                  source: kAutoplayBlockerJS,
                                  injectionTime:
                                      UserScriptInjectionTime.AT_DOCUMENT_START,
                                ),
                                UserScript(
                                  source: kSpaNavigationMonitorScript,
                                  injectionTime:
                                      UserScriptInjectionTime.AT_DOCUMENT_START,
                                ),
                                UserScript(
                                  source: kNativeFeelingScript,
                                  injectionTime:
                                      UserScriptInjectionTime.AT_DOCUMENT_START,
                                ),
                                if (settings.minimalModeEnabled)
                                  UserScript(
                                    source: kMinimalModeCssScript,
                                    injectionTime: UserScriptInjectionTime
                                        .AT_DOCUMENT_START,
                                  )
                                else ...[
                                  if (settings.disableReelsEntirely)
                                    UserScript(
                                      source: kDisableReelsEntirelyCssScript,
                                      injectionTime: UserScriptInjectionTime
                                          .AT_DOCUMENT_START,
                                    ),
                                  if (settings.disableExploreEntirely)
                                    UserScript(
                                      source: kDisableExploreEntirelyCssScript,
                                      injectionTime: UserScriptInjectionTime
                                          .AT_DOCUMENT_START,
                                    ),
                                ],
                                UserScript(
                                  source: kHapticBridgeScript,
                                  injectionTime:
                                      UserScriptInjectionTime.AT_DOCUMENT_START,
                                ),
                              ]),
                              pullToRefreshController: _pullToRefreshController,
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

                                _registerJavaScriptHandlers(controller);

                                // Start safety timeout — clears loading state
                                // if onLoadStop never fires (e.g. network stall).
                                _resetLoadingTimeout();
                              },
                              onLoadStart: (controller, url) {
                                if (!mounted) return;
                                final u = url?.toString() ?? '';
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
                                setState(() {
                                  _isLoading = false;
                                  _currentUrl = current;
                                  _hasError = false;
                                });

                                await _injectionManager
                                    ?.runAllPostLoadInjections(current);

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

                                    final minimal =
                                        appSettings.minimalModeEnabled;
                                    final disableReels =
                                        appSettings.disableReelsEntirely ||
                                        minimal;
                                    final disableExplore =
                                        appSettings.disableExploreEntirely ||
                                        minimal;

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
                                      showBlocked('Explore is disabled');
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
            _EdgePanel(key: _edgePanelKey),

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
                              settings.disableReelsEntirely ||
                              settings.minimalModeEnabled;

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
            id: DateTime.now().millisecond,
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

    controller.addJavaScriptHandler(
      handlerName: 'UrlChange',
      callback: (args) async {
        final url = (args.isNotEmpty ? args[0] : '') as String? ?? '';
        await _injectionManager?.runAllPostLoadInjections(url);
        if (!mounted) return;
        setState(() {
          _currentUrl = url;
          // SPA navigations never fire onLoadStop — clear skeleton here
          // so it doesn't stay visible forever (e.g. when navigating to DMs)
          _showSkeleton = false;
          _isLoading = false;
        });

        final settings = context.read<SettingsService>();
        final minimal = settings.minimalModeEnabled;
        final disableReels = settings.disableReelsEntirely || minimal;
        final disableExplore = settings.disableExploreEntirely || minimal;

        final path = Uri.tryParse(url)?.path ?? url;
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
  const _EdgePanel({super.key});
  @override
  State<_EdgePanel> createState() => _EdgePanelState();
}

class _EdgePanelState extends State<_EdgePanel> {
  bool _isExpanded = false;
  void _toggleExpansion() => setState(() => _isExpanded = !_isExpanded);

  @override
  Widget build(BuildContext context) {
    final sm = context.watch<SessionManager>();
    final int remaining = sm.remainingSessionSeconds;
    final double progress = sm.perSessionSeconds > 0
        ? (remaining / sm.perSessionSeconds).clamp(0.0, 1.0)
        : 0;
    Color barColor = progress < 0.2
        ? Colors.redAccent
        : (progress < 0.5 ? Colors.yellowAccent : Colors.blueAccent);

    final settings = context.watch<SettingsService>();
    final isDark = settings.isDarkMode;
    final reelsHardDisabled =
        settings.disableReelsEntirely || settings.minimalModeEnabled;
    final panelBg = isDark ? const Color(0xFF121212) : Colors.white;
    final textDim = isDark ? Colors.white70 : Colors.black87;
    final textSub = isDark ? Colors.white30 : Colors.black38;
    final border = isDark ? Colors.white12 : Colors.black12;

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
          duration: const Duration(milliseconds: 350),
          curve: Curves.easeOutQuart,
          left: _isExpanded ? 0 : -220,
          top: MediaQuery.of(context).size.height * 0.25 + 30,
          child: Container(
            width: 210,
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: panelBg.withValues(alpha: 0.98),
              borderRadius: const BorderRadius.only(
                topRight: Radius.circular(28),
                bottomRight: Radius.circular(28),
              ),
              border: Border.all(color: border, width: 0.5),
              boxShadow: [
                BoxShadow(
                  color: (isDark ? Colors.black : Colors.black12).withValues(
                    alpha: 0.3,
                  ),
                  blurRadius: 30,
                  spreadRadius: 5,
                ),
              ],
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text(
                      'FOCUS CONTROL',
                      style: TextStyle(
                        color: Colors.blueAccent,
                        fontSize: 10,
                        fontWeight: FontWeight.bold,
                        letterSpacing: 1.5,
                      ),
                    ),
                    IconButton(
                      icon: Icon(
                        Icons.chevron_left_rounded,
                        color: textDim,
                        size: 28,
                      ),
                      onPressed: _toggleExpansion,
                      padding: EdgeInsets.zero,
                      constraints: const BoxConstraints(),
                    ),
                  ],
                ),
                const SizedBox(height: 32),
                Text(
                  'REEL SESSION',
                  style: TextStyle(
                    color: textSub,
                    fontSize: 11,
                    letterSpacing: 1,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  sm.isSessionActive
                      ? _formatTime(sm.remainingSessionSeconds)
                      : 'Off',
                  style: TextStyle(
                    color: barColor,
                    fontSize: 40,
                    fontWeight: FontWeight.w200,
                    letterSpacing: 2,
                  ),
                ),
                const SizedBox(height: 20),
                _buildStatRow(
                  'REEL QUOTA',
                  '${sm.dailyRemainingSeconds ~/ 60}m Left',
                  Icons.timer_outlined,
                  isDark: isDark,
                ),
                _buildStatRow(
                  'AUTO-CLOSE',
                  _formatTime(sm.appSessionRemainingSeconds),
                  Icons.hourglass_empty_rounded,
                  isDark: isDark,
                ),
                _buildStatRow(
                  'COOLDOWN',
                  sm.isCooldownActive
                      ? _formatTime(sm.cooldownRemainingSeconds)
                      : 'Off',
                  Icons.coffee_rounded,
                  isWarning: sm.isCooldownActive,
                  isDark: isDark,
                ),
                if (!reelsHardDisabled &&
                    !sm.isSessionActive &&
                    sm.dailyRemainingSeconds > 0) ...[
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      onPressed: () {
                        context
                            .findAncestorStateOfType<_MainWebViewPageState>()
                            ?._showReelSessionPicker();
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.blueAccent,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      child: const Text(
                        'Start Session',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
                ] else if (reelsHardDisabled) ...[
                  const SizedBox(height: 10),
                  Text(
                    'Reels disabled in settings',
                    style: TextStyle(color: textSub, fontSize: 11),
                  ),
                ],
                const SizedBox(height: 32),
                Divider(color: isDark ? Colors.white10 : Colors.black12),
                const SizedBox(height: 8),
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
      ],
    );
  }

  Widget _buildStatRow(
    String label,
    String value,
    IconData icon, {
    bool isWarning = false,
    bool isDark = true,
  }) {
    final textMain = isDark ? Colors.white : Colors.black;
    final textSub = isDark ? Colors.white38 : Colors.black38;
    return Padding(
      padding: const EdgeInsets.only(bottom: 20),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: isWarning
                  ? Colors.redAccent.withValues(alpha: 0.1)
                  : (isDark ? Colors.white : Colors.black).withValues(
                      alpha: 0.05,
                    ),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(
              icon,
              color: isWarning
                  ? Colors.redAccent
                  : (isDark ? Colors.white70 : Colors.black54),
              size: 16,
            ),
          ),
          const SizedBox(width: 16),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: TextStyle(
                  color: textSub,
                  fontSize: 9,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 1,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                value,
                style: TextStyle(
                  color: isWarning ? Colors.redAccent : textMain,
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
            ],
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

class _BrandedTopBar extends StatelessWidget {
  final VoidCallback? onFocusControlTap;
  const _BrandedTopBar({this.onFocusControlTap});
  @override
  Widget build(BuildContext context) {
    final isDark = context.watch<SettingsService>().isDarkMode;
    final barBg = isDark ? Colors.black : Colors.white;
    final textMain = isDark ? Colors.white : Colors.black;
    final iconColor = isDark ? Colors.white70 : Colors.black54;
    final border = isDark ? Colors.white12 : Colors.black12;

    return Container(
      height: 60,
      decoration: BoxDecoration(
        color: barBg,
        border: Border(bottom: BorderSide(color: border, width: 0.5)),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            IconButton(
              icon: Icon(Icons.settings_outlined, color: iconColor, size: 22),
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const SettingsPage()),
              ),
            ),
            Text(
              'FocusGram',
              style: GoogleFonts.grandHotel(
                color: textMain,
                fontSize: 32,
                letterSpacing: 0.5,
              ),
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
