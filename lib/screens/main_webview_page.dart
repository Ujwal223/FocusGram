import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_flutter_android/webview_flutter_android.dart';
import '../services/session_manager.dart';
import '../services/settings_service.dart';
import '../services/injection_controller.dart';
import '../services/navigation_guard.dart';
import '../services/focusgram_router.dart';
import 'package:google_fonts/google_fonts.dart';
import '../services/notification_service.dart';
import '../utils/discipline_challenge.dart';
import 'settings_page.dart';

class MainWebViewPage extends StatefulWidget {
  const MainWebViewPage({super.key});

  @override
  State<MainWebViewPage> createState() => _MainWebViewPageState();
}

class _MainWebViewPageState extends State<MainWebViewPage>
    with WidgetsBindingObserver {
  late final WebViewController _controller;
  final GlobalKey<_EdgePanelState> _edgePanelKey = GlobalKey<_EdgePanelState>();
  bool _isLoading = true;
  Timer? _watchdog;
  bool _extensionDialogShown = false;
  bool _lastSessionActive = false;
  String _currentUrl = 'https://www.instagram.com/';
  bool _hasError = false;
  bool _reelsBlockedOverlay = false;

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
    _initWebView();
    _startWatchdog();

    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<SessionManager>().addListener(_onSessionChanged);
      context.read<SettingsService>().addListener(_onSettingsChanged);
      _lastSessionActive = context.read<SessionManager>().isSessionActive;
    });

    FocusGramRouter.pendingUrl.addListener(_onPendingUrlChanged);
  }

  void _onPendingUrlChanged() {
    final url = FocusGramRouter.pendingUrl.value;
    if (url != null && url.isNotEmpty) {
      FocusGramRouter.pendingUrl.value = null;
      _controller.loadRequest(Uri.parse(url));
    }
  }

  /// Sets the isolated reel player flag in the WebView so the scroll-lock
  /// knows it should block swipe-to-next-reel.
  Future<void> _setIsolatedPlayer(bool active) async {
    await _controller.runJavaScript(
      'window.__focusgramIsolatedPlayer = $active;',
    );
  }

  void _onSessionChanged() {
    if (!mounted) return;
    final sm = context.read<SessionManager>();
    if (_lastSessionActive != sm.isSessionActive) {
      _lastSessionActive = sm.isSessionActive;
      _applyInjections();

      // If session became active and we were showing overlay, hide it
      if (_lastSessionActive && _reelsBlockedOverlay) {
        setState(() => _reelsBlockedOverlay = false);
      }
    }
    setState(() {});
  }

  void _onSettingsChanged() {
    if (!mounted) return;
    _applyInjections();
    // Removed _controller.reload() to improve performance. JS injection now handles updates instantly.
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _watchdog?.cancel();
    FocusGramRouter.pendingUrl.removeListener(_onPendingUrlChanged);
    context.read<SessionManager>().removeListener(_onSessionChanged);
    context.read<SettingsService>().removeListener(_onSettingsChanged);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (!mounted) return;
    final sm = context.read<SessionManager>();
    if (state == AppLifecycleState.resumed) {
      sm.setAppForeground(true);
    } else if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.inactive ||
        state == AppLifecycleState.detached) {
      sm.setAppForeground(false);
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

  void _initWebView() {
    final settings = context.read<SettingsService>();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setUserAgent(InjectionController.iOSUserAgent)
      ..setBackgroundColor(settings.isDarkMode ? Colors.black : Colors.white);

    if (_controller.platform is AndroidWebViewController) {
      final androidController =
          _controller.platform as AndroidWebViewController;
      AndroidWebViewController.enableDebugging(false);
      androidController.setMediaPlaybackRequiresUserGesture(false);
      androidController.setOnShowFileSelector((params) async {
        try {
          final picker = ImagePicker();
          final acceptsVideo = params.acceptTypes.any(
            (t) => t.contains('video'),
          );
          final XFile? file = acceptsVideo
              ? await picker.pickVideo(source: ImageSource.gallery)
              : await picker.pickImage(source: ImageSource.gallery);
          if (file != null) {
            // WebView expects a content:// URI, not a raw filesystem path.
            // XFile.path on Android is already a content:// URI string when
            // picked from the gallery via image_picker >= 0.9, but if it
            // starts with '/' we need to prefix it with 'file://'.
            final path = file.path;
            final uri = path.startsWith('/') ? 'file://$path' : path;
            return [uri];
          }
        } catch (_) {}
        return <String>[];
      });
    }

    _controller
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageStarted: (url) {
            if (mounted) {
              setState(() {
                _isLoading = !url.contains('#');
                _currentUrl = url;
                // If navigating to reels and no session, block it
                if (url.contains('/reels/') &&
                    !context.read<SessionManager>().isSessionActive) {
                  _reelsBlockedOverlay = true;
                } else {
                  _reelsBlockedOverlay = false;
                }
              });
            }
          },
          onPageFinished: (url) {
            if (mounted) {
              setState(() {
                _isLoading = false;
                _currentUrl = url;
              });
            }
            _applyInjections();
            _controller.runJavaScript(InjectionController.notificationBridgeJS);

            // Set isolated player flag: true only when a single reel is opened
            // from a DM thread (URL contains /reel/ but we're coming from /direct/).
            // When the user navigates away, clear the flag.
            final isIsolatedReel =
                url.contains('/reel/') && !url.contains('/reels/');
            _setIsolatedPlayer(isIsolatedReel);
          },
          onNavigationRequest: (request) {
            final uri = Uri.tryParse(request.url);
            if (uri != null &&
                uri.host.contains('instagram.com') &&
                (request.url.contains('accounts/settings') ||
                    request.url.contains('accounts/edit'))) {
              return NavigationDecision.navigate;
            }

            // Block reels feed if no session active
            if (request.url.contains('/reels/') &&
                !context.read<SessionManager>().isSessionActive) {
              setState(() => _reelsBlockedOverlay = true);
              return NavigationDecision.prevent;
            }

            if (uri != null &&
                !uri.host.contains('instagram.com') &&
                !uri.host.contains('facebook.com') &&
                !uri.host.contains('cdninstagram.com') &&
                !uri.host.contains('fbcdn.net')) {
              launchUrl(uri, mode: LaunchMode.externalApplication);
              return NavigationDecision.prevent;
            }

            final decision = NavigationGuard.evaluate(url: request.url);
            if (decision.blocked) {
              // Custom handling for reels in overlay instead of snackbar
              if (request.url.contains('/reels/')) {
                setState(() => _reelsBlockedOverlay = true);
                return NavigationDecision.prevent;
              }
              return NavigationDecision.prevent;
            }

            return NavigationDecision.navigate;
          },
          onWebResourceError: (error) {
            if (error.isForMainFrame == true &&
                (error.errorCode == -2 || error.errorCode == -6)) {
              if (mounted) setState(() => _hasError = true);
            }
          },
        ),
      )
      ..addJavaScriptChannel(
        'FocusGramNotificationChannel',
        onMessageReceived: (message) {
          final settings = context.read<SettingsService>();
          final msg = message.message;

          // Check if it's a bridge payload (Title: Body) or a simple flag (DM/Activity)
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

          if (isDM && !settings.notifyDMs) return;
          if (!isDM && !settings.notifyActivity) return;

          try {
            NotificationService().showNotification(
              id: DateTime.now().millisecond,
              title: title,
              body: body,
            );
          } catch (_) {}
        },
      )
      ..addJavaScriptChannel(
        'FocusGramShareChannel',
        onMessageReceived: (message) {
          try {
            final data = message.message;
            String url = data;
            try {
              final json = RegExp(r'"url":"([^"]+)"').firstMatch(data);
              if (json != null) url = json.group(1) ?? data;
            } catch (_) {}
            Clipboard.setData(ClipboardData(text: url));
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text('Link copied (tracking removed)'),
                  backgroundColor: const Color(0xFF1A1A2E),
                  behavior: SnackBarBehavior.floating,
                  margin: const EdgeInsets.fromLTRB(16, 0, 16, 20),
                ),
              );
            }
          } catch (_) {}
        },
      )
      ..addJavaScriptChannel(
        'FocusGramThemeChannel',
        onMessageReceived: (message) {
          context.read<SettingsService>().setDarkMode(
            message.message == 'dark',
          );
        },
      )
      ..addJavaScriptChannel(
        'FocusGramPathChannel',
        onMessageReceived: (message) {
          if (!mounted) return;
          final path = message.message;
          final sm = context.read<SessionManager>();
          if (path.startsWith('/reels') && !sm.isSessionActive) {
            // SPA navigation landed on Reels without a session — gate it.
            setState(() => _reelsBlockedOverlay = true);
            // Navigate back to home feed so the overlay has content behind it.
            _controller.runJavaScript(
              'if (window.location.pathname.startsWith("/reels")) window.location.href = "/";',
            );
          } else if (_reelsBlockedOverlay && !path.startsWith('/reels')) {
            setState(() => _reelsBlockedOverlay = false);
          }
        },
      )
      ..loadRequest(Uri.parse('https://www.instagram.com/accounts/login/'));
  }

  void _applyInjections() {
    if (!mounted) return;
    if (_isOnOnboardingPage) return;

    final sessionManager = context.read<SessionManager>();
    final settings = context.read<SettingsService>();
    final js = InjectionController.buildInjectionJS(
      sessionActive: sessionManager.isSessionActive,
      blurExplore: settings.blurExplore,
      blurReels: settings.blurReels,
      ghostTyping: settings.ghostTyping,
      ghostSeen: settings.ghostSeen,
      ghostStories: settings.ghostStories,
      ghostDmPhotos: settings.ghostDmPhotos,
      enableTextSelection: settings.enableTextSelection,
    );
    _controller.runJavaScript(js);
  }

  Future<void> _signOut() async {
    final manager = WebViewCookieManager();
    await manager.clearCookies();
    await _controller.clearCache();
    if (mounted) {
      setState(() {
        _isLoading = true;
        _reelsBlockedOverlay = false;
      });
      await _controller.loadRequest(
        Uri.parse('https://www.instagram.com/accounts/login/'),
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
      onTap: () {
        Navigator.pop(context);
        if (sm.startSession(mins)) {
          setState(() => _reelsBlockedOverlay = false);
          _controller.loadRequest(
            Uri.parse('https://www.instagram.com/reels/'),
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
          _controller.goBack();
          return;
        }
        // Run history.back() in the WebView JS context first.
        // This properly closes Instagram's comment sheet / modal overlay
        // (which uses the History API pushState). If the webview itself
        // can go back in its own page-level history, canGoBack() handles it.
        // We use a JS promise to detect whether we actually navigated:
        final didNavigate = await _controller
            .runJavaScriptReturningResult(
              '(function(){'
              '  var before = window.location.href;'
              '  history.back();'
              '  return before;'
              '})()',
            )
            .then((_) => true)
            .catchError((_) => false);
        if (didNavigate) {
          // history.back() was called — wait a frame to let the SPA handle it
          // If the URL didn't change (e.g., no more history states), fall
          // through to webview-level back or app exit.
          await Future.delayed(const Duration(milliseconds: 120));
          return;
        }
        if (await _controller.canGoBack()) {
          await _controller.goBack();
        } else {
          SystemNavigator.pop();
        }
      },
      child: Scaffold(
        backgroundColor: context.watch<SettingsService>().isDarkMode
            ? Colors.black
            : Colors.white,
        body: Stack(
          children: [
            SafeArea(
              child: Column(
                children: [
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
                        return WebViewWidget(controller: _controller);
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
                  _controller.reload();
                },
              ),
            if (_isLoading)
              Positioned(
                top: 60 + MediaQuery.of(context).padding.top,
                left: 0,
                right: 0,
                child: const _InstagramGradientProgressBar(),
              ),
            _EdgePanel(key: _edgePanelKey, controller: _controller),

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
                                    : 'Reels are Blocked',
                                style: GoogleFonts.grandHotel(
                                  color: textMain,
                                  fontSize: 42,
                                ),
                              ),
                              const SizedBox(height: 12),
                              Text(
                                quotaFinished
                                    ? 'You have reached your planned limit for today. Step away and focus on what matters most.'
                                    : 'Start a planned reel session to access the feed. Use Instagram for connection, not distraction.',
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
                                  _controller.goBack();
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
}

class _EdgePanel extends StatefulWidget {
  final WebViewController controller;
  const _EdgePanel({super.key, required this.controller});
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
                if (!sm.isSessionActive && sm.dailyRemainingSeconds > 0) ...[
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
