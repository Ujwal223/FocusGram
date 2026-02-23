import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_flutter_android/webview_flutter_android.dart';
import '../services/session_manager.dart';
import '../services/settings_service.dart';
import '../services/injection_controller.dart';
import '../services/navigation_guard.dart';
import 'package:google_fonts/google_fonts.dart';
import '../services/notification_service.dart';
import 'settings_page.dart';
import 'app_session_picker.dart';

class MainWebViewPage extends StatefulWidget {
  const MainWebViewPage({super.key});

  @override
  State<MainWebViewPage> createState() => _MainWebViewPageState();
}

class _MainWebViewPageState extends State<MainWebViewPage>
    with WidgetsBindingObserver {
  late final WebViewController _controller;
  int _currentIndex = 0;
  bool _isLoading = true;
  // Watchdog for app-session expiry
  Timer? _watchdog;
  bool _extensionDialogShown = false;
  bool _lastSessionActive = false;
  String? _cachedUsername;
  String _currentUrl = 'https://www.instagram.com/';
  bool _hasError = false;

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

  /// Helper to determine if we are inside Direct Messages.
  bool get _isInDirect {
    return _currentUrl.contains('instagram.com/direct/');
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _initWebView();
    _startWatchdog();

    // Listen to session & settings changes
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<SessionManager>().addListener(_onSessionChanged);
      context.read<SettingsService>().addListener(_onSettingsChanged);
      _lastSessionActive = context.read<SessionManager>().isSessionActive;
    });
  }

  void _onSessionChanged() {
    if (!mounted) return;
    final sm = context.read<SessionManager>();
    if (_lastSessionActive != sm.isSessionActive) {
      _lastSessionActive = sm.isSessionActive;
      _applyInjections();
    }
    // Force rebuild for timer updates
    setState(() {});
  }

  void _onSettingsChanged() {
    if (!mounted) return;
    _applyInjections();
    _controller.reload();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _watchdog?.cancel();
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
              SystemNavigator.pop(); // Force close
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
                _extensionDialogShown =
                    false; // Reset so watchdog can fire again at next expiry
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
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setUserAgent(InjectionController.iOSUserAgent)
      ..setBackgroundColor(Colors.black);

    // Support file uploads on Android
    if (_controller.platform is AndroidWebViewController) {
      AndroidWebViewController.enableDebugging(true);
      (_controller.platform as AndroidWebViewController).setOnShowFileSelector((
        FileSelectorParams params,
      ) async {
        // Standard Android implementation triggers the file picker intent automatically
        // when this callback is set, or we can use file_picker package if needed.
        // For now, returning empty list if we want to custom handle, or better:
        // By default, setting a non-null callback enables the system picker.
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
                _currentUrl = url; // Update immediately to hide/show UI
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
            _updateCurrentTab(url);
            _cacheUsername();
            // Inject Notification Bridge Hook
            _controller.runJavaScript(InjectionController.notificationBridgeJS);
            // Inject MutationObserver to lock reel scrolling resiliently
            _controller.runJavaScript(
              InjectionController.reelsMutationObserverJS,
            );
          },
          onNavigationRequest: (request) {
            // Handle external links (non-Instagram/Facebook)
            final uri = Uri.tryParse(request.url);
            if (uri != null &&
                !uri.host.contains('instagram.com') &&
                !uri.host.contains('facebook.com')) {
              launchUrl(uri, mode: LaunchMode.externalApplication);
              return NavigationDecision.prevent;
            }

            // Facebook Login Warning
            if (uri != null &&
                uri.host.contains('facebook.com') &&
                _isOnOnboardingPage) {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Sorry, Please use Email login')),
              );
              return NavigationDecision.prevent;
            }

            final decision = NavigationGuard.evaluate(url: request.url);

            if (decision.blocked) {
              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(decision.reason ?? 'Navigation blocked'),
                    backgroundColor: Colors.red.shade900,
                    behavior: SnackBarBehavior.floating,
                    margin: const EdgeInsets.fromLTRB(16, 0, 16, 80),
                    duration: const Duration(seconds: 2),
                  ),
                );
              }
              return NavigationDecision.prevent;
            }

            return NavigationDecision.navigate;
          },
        ),
      )
      ..addJavaScriptChannel(
        'FocusGramNotificationChannel',
        onMessageReceived: (message) {
          try {
            // Instagram sends notification data; we bridge to native
            NotificationService().showNotification(
              id: DateTime.now().millisecond,
              title: 'Instagram',
              body: message.message,
            );
          } catch (_) {}
        },
      )
      ..loadRequest(Uri.parse('https://www.instagram.com/accounts/login/'));
  }

  void _applyInjections() {
    if (!mounted) return;
    if (_isOnOnboardingPage) return; // Restore native login/signup behavior

    final sessionManager = context.read<SessionManager>();
    final settings = context.read<SettingsService>();
    final js = InjectionController.buildInjectionJS(
      sessionActive: sessionManager.isSessionActive,
      blurExplore: settings.blurExplore,
      blurReels: settings.blurReels,
      ghostMode: settings.ghostMode,
      enableTextSelection: settings.enableTextSelection,
    );
    _controller.runJavaScript(js);
  }

  Future<void> _signOut() async {
    final manager = WebViewCookieManager();
    await manager.clearCookies();
    await _controller.clearCache();
    // Force immediate state update and navigation
    if (mounted) {
      setState(() {
        _currentIndex = 0;
        _cachedUsername = null;
        _isLoading = true; // Show indicator during reload
      });
      await _controller.loadRequest(
        Uri.parse('https://www.instagram.com/accounts/login/'),
      );
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Signed out successfully')));
    }
  }

  Future<void> _cacheUsername() async {
    try {
      final result = await _controller.runJavaScriptReturningResult(
        "document.querySelector('header h2')?.innerText || ''",
      );
      final raw = result.toString().replaceAll('"', '').replaceAll("'", '');
      if (raw.isNotEmpty && raw != 'null' && raw != 'undefined') {
        _cachedUsername = raw;
      }
    } catch (_) {}
  }

  void _updateCurrentTab(String url) {
    final uri = Uri.tryParse(url);
    if (uri == null) return;
    final path = uri.path;

    int newIndex = _currentIndex;
    if (path == '/' || path.isEmpty) {
      newIndex = 0;
    } else if (path.startsWith('/explore') || path.startsWith('/search')) {
      newIndex = 1;
    } else if (path.startsWith('/direct')) {
      newIndex = 3;
    } else if (_cachedUsername != null &&
        path.startsWith('/$_cachedUsername')) {
      newIndex = 4;
    }

    if (newIndex != _currentIndex) {
      setState(() => _currentIndex = newIndex);
    }
  }

  /// Navigate using JS when already on Instagram (avoids full page reload).
  /// Falls back to loadRequest if not on instagram.com.
  Future<void> _navigateTo(String path) async {
    try {
      final currentUrl = await _controller.currentUrl();
      if (currentUrl != null && currentUrl.contains('instagram.com')) {
        // SPA soft nav — instant, no full reload
        await _controller.runJavaScript(
          InjectionController.softNavigateJS(path),
        );
        return;
      }
    } catch (_) {}
    // Fallback: full load
    await _controller.loadRequest(Uri.parse('https://www.instagram.com$path'));
  }

  Future<void> _onTabTapped(String label) async {
    final sm = context.read<SessionManager>();

    switch (label) {
      case 'Home':
        await _navigateTo('/');
        break;
      case 'Search':
        await _navigateTo('/explore/');
        break;
      case 'Create':
        await _navigateTo('/reels/create/'); // Default create path
        break;
      case 'Notifications':
        await _navigateTo('/notifications/');
        break;
      case 'Reels':
        if (sm.isSessionActive) {
          await _navigateTo('/reels/');
        } else {
          // Show session picker if no session active
          _showSessionPicker();
        }
        break;
      case 'Profile':
        if (_cachedUsername != null) {
          await _navigateTo('/$_cachedUsername/');
        } else {
          await _cacheUsername();
          if (_cachedUsername != null) {
            await _navigateTo('/$_cachedUsername/');
          } else {
            await _navigateTo('/accounts/edit/');
          }
        }
        break;
    }
  }

  void _showSessionPicker() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => Container(
        height: MediaQuery.of(context).size.height * 0.7,
        decoration: const BoxDecoration(
          color: Color(0xFF121212),
          borderRadius: BorderRadius.only(
            topLeft: Radius.circular(25),
            topRight: Radius.circular(25),
          ),
        ),
        child: ClipRRect(
          borderRadius: const BorderRadius.only(
            topLeft: Radius.circular(25),
            topRight: Radius.circular(25),
          ),
          child: Navigator(
            onGenerateRoute: (_) => MaterialPageRoute(
              builder: (ctx) => AppSessionPickerScreen(
                onSessionStarted: () => Navigator.pop(context),
              ),
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) async {
        if (didPop) return;
        if (await _controller.canGoBack()) {
          await _controller.goBack();
        } else {
          SystemNavigator.pop();
        }
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        body: Stack(
          children: [
            // ── Main Content Layout ────────────────────────────────────
            SafeArea(
              child: Column(
                children: [
                  if (!_isOnOnboardingPage) _BrandedTopBar(),
                  Expanded(child: WebViewWidget(controller: _controller)),
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

            // ── Thin loading indicator (Placed below Top Bar) ──────────
            if (_isLoading)
              Positioned(
                top: 60 + MediaQuery.of(context).padding.top,
                left: 0,
                right: 0,
                child: const _InstagramGradientProgressBar(),
              ),

            // ── The Edge Panel ──────────────────────────────────────────
            _EdgePanel(controller: _controller),

            // ── Our bottom bar ──────────────────────────────────────────
            if (!_isOnOnboardingPage && !_isInDirect)
              Positioned(
                bottom: 0,
                left: 0,
                right: 0,
                child: const _FocusGramNavBar(),
              ),
          ],
        ),
      ),
    );
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// Edge Panel Widget — Samsung-style swipe-to-reveal side panel
// ──────────────────────────────────────────────────────────────────────────────

class _EdgePanel extends StatefulWidget {
  final WebViewController controller;
  const _EdgePanel({required this.controller});

  @override
  State<_EdgePanel> createState() => _EdgePanelState();
}

class _EdgePanelState extends State<_EdgePanel> {
  bool _isExpanded = false;

  @override
  void initState() {
    super.initState();
  }

  @override
  void dispose() {
    super.dispose();
  }

  void _toggleExpansion() {
    setState(() {
      _isExpanded = !_isExpanded;
    });
  }

  @override
  Widget build(BuildContext context) {
    final sm = context.watch<SessionManager>();
    final int remaining = sm.remainingSessionSeconds;
    final double progress = sm.perSessionSeconds > 0
        ? (remaining / sm.perSessionSeconds).clamp(0.0, 1.0)
        : 0;

    Color barColor = Colors.grey.withValues(alpha: 0.6);
    if (progress < 0.2) {
      barColor = Colors.redAccent;
    } else if (progress < 0.5) {
      barColor = Colors.yellowAccent.withValues(alpha: 0.8);
    }

    // We use a transparent Stack filling the screen to position elements anywhere.
    // Hits will pass through the Stack to the WebView except on our children.
    return Stack(
      children: [
        if (_isExpanded)
          Positioned.fill(
            child: GestureDetector(
              onTap: _toggleExpansion,
              behavior: HitTestBehavior.opaque,
              child: Container(color: Colors.black.withValues(alpha: 0.15)),
            ),
          ),

        // ── The Handle (Minimized State) ──
        if (!_isExpanded)
          Positioned(
            left: 0,
            top: MediaQuery.of(context).size.height * 0.35 + 30, // Added margin
            child: Material(
              color: Colors.transparent,
              child: Column(
                children: [
                  GestureDetector(
                    onHorizontalDragUpdate: (details) {
                      if (details.delta.dx > 10) _toggleExpansion();
                    },
                    onTap: _toggleExpansion,
                    child: Container(
                      width: 10,
                      height: 100,
                      decoration: BoxDecoration(
                        color: Colors.black.withValues(alpha: 0.7),
                        borderRadius: const BorderRadius.only(
                          topRight: Radius.circular(10),
                          bottomRight: Radius.circular(10),
                        ),
                        border: Border.all(color: Colors.white24, width: 0.5),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withValues(alpha: 0.3),
                            blurRadius: 10,
                          ),
                        ],
                      ),
                      padding: const EdgeInsets.symmetric(
                        vertical: 6,
                        horizontal: 2,
                      ),
                      child: Align(
                        alignment: Alignment.bottomCenter,
                        child: Container(
                          width: 4,
                          decoration: BoxDecoration(
                            color: barColor,
                            borderRadius: BorderRadius.circular(4),
                          ),
                          // Height determined by progress
                          height: (progress * 88).clamp(4.0, 88.0),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  // Gear icon below handle
                  GestureDetector(
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => const SettingsPage()),
                    ),
                    child: Container(
                      width: 36,
                      height: 36,
                      decoration: BoxDecoration(
                        color: Colors.black.withValues(alpha: 0.7),
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.white24, width: 0.5),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withValues(alpha: 0.3),
                            blurRadius: 8,
                          ),
                        ],
                      ),
                      child: const Icon(
                        Icons.settings_rounded,
                        color: Colors.white70,
                        size: 18,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),

        // ── The Panel (Expanded State) ──
        AnimatedPositioned(
          duration: const Duration(milliseconds: 350),
          curve: Curves.easeOutQuart,
          left: _isExpanded ? 0 : -220,
          top: MediaQuery.of(context).size.height * 0.25 + 30, // Added margin
          child: GestureDetector(
            onHorizontalDragUpdate: (details) {
              if (details.delta.dx < -10) _toggleExpansion();
            },
            child: Container(
              width: 210,
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: const Color(0xFF121212).withValues(alpha: 0.98),
                borderRadius: const BorderRadius.only(
                  topRight: Radius.circular(28),
                  bottomRight: Radius.circular(28),
                ),
                border: Border.all(color: Colors.white12, width: 0.5),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.6),
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
                        icon: const Icon(
                          Icons.chevron_left_rounded,
                          color: Colors.white70,
                          size: 28,
                        ),
                        onPressed: _toggleExpansion,
                        padding: EdgeInsets.zero,
                        constraints: const BoxConstraints(),
                      ),
                    ],
                  ),
                  const SizedBox(height: 32),
                  // Reel Session Timer
                  const Text(
                    'REEL SESSION',
                    style: TextStyle(
                      color: Colors.white30,
                      fontSize: 11,
                      letterSpacing: 1,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    context.read<SessionManager>().isSessionActive
                        ? _formatTime(
                            context
                                .read<SessionManager>()
                                .remainingSessionSeconds,
                          )
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
                  ),
                  _buildStatRow(
                    'AUTO-CLOSE',
                    _formatTime(sm.appSessionRemainingSeconds),
                    Icons.hourglass_empty_rounded,
                  ),
                  _buildStatRow(
                    'COOLDOWN',
                    sm.isCooldownActive
                        ? _formatTime(sm.cooldownRemainingSeconds)
                        : 'Off',
                    Icons.coffee_rounded,
                    isWarning: sm.isCooldownActive,
                  ),
                  const SizedBox(height: 32),
                  if (!context
                      .findAncestorStateOfType<_MainWebViewPageState>()!
                      ._isOnOnboardingPage) ...[
                    const Divider(color: Colors.white10),
                    const SizedBox(height: 8),
                    ListTile(
                      onTap: () async {
                        _toggleExpansion();
                        final state = context
                            .findAncestorStateOfType<_MainWebViewPageState>();
                        if (state != null) {
                          await state._signOut();
                        }
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
                    const SizedBox(height: 8),
                  ],
                ],
              ),
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
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 20),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: isWarning
                  ? Colors.redAccent.withValues(alpha: 0.1)
                  : Colors.white.withValues(alpha: 0.05),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(
              icon,
              color: isWarning ? Colors.redAccent : Colors.white70,
              size: 16,
            ),
          ),
          const SizedBox(width: 16),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: const TextStyle(
                  color: Colors.white38,
                  fontSize: 9,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 1,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                value,
                style: TextStyle(
                  color: isWarning ? Colors.redAccent : Colors.white,
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

// ──────────────────────────────────────────────────────────────────────────────
// Branded Top Bar — minimal, Instagram-like font
// ──────────────────────────────────────────────────────────────────────────────

class _BrandedTopBar extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      height: 60,
      decoration: const BoxDecoration(
        color: Colors.black,
        border: Border(bottom: BorderSide(color: Colors.white12, width: 0.5)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(
            'FocusGram',
            style: GoogleFonts.grandHotel(
              color: Colors.white,
              fontSize: 32,
              letterSpacing: 0.5,
            ),
          ),
        ],
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
      child: Stack(
        children: [
          Container(
            decoration: const BoxDecoration(
              gradient: LinearGradient(
                colors: [
                  Color(0xFFFEDA75), // Yellow
                  Color(0xFFFA7E1E), // Orange
                  Color(0xFFD62976), // Pink
                  Color(0xFF962FBF), // Purple
                  Color(0xFF4F5BD5), // Blue
                ],
                begin: Alignment.centerLeft,
                end: Alignment.centerRight,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _FocusGramNavBar extends StatelessWidget {
  const _FocusGramNavBar();

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    final allItems = {
      'Home': (Icons.home_outlined, Icons.home_rounded),
      'Search': (Icons.search, Icons.search),
      'Create': (Icons.add_box_outlined, Icons.add_box),
      'Notifications': (Icons.favorite_border, Icons.favorite),
      'Reels': (Icons.play_circle_outline, Icons.play_circle_filled),
      'Profile': (Icons.person_outline, Icons.person),
    };

    final activeTabs = settings.enabledTabs;
    final state = context.findAncestorStateOfType<_MainWebViewPageState>()!;
    final sm = context.watch<SessionManager>();

    return Container(
      color: Colors.black,
      child: SafeArea(
        top: false,
        child: Container(
          height: 56,
          decoration: const BoxDecoration(
            border: Border(top: BorderSide(color: Colors.white12, width: 0.5)),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: activeTabs.map((tab) {
              final icons = allItems[tab];
              if (icons == null) return const SizedBox();

              final isSelected = _isTabSelected(
                tab,
                state._currentUrl,
                state._cachedUsername,
              );

              return GestureDetector(
                onTap: () => state._onTabTapped(tab),
                behavior: HitTestBehavior.opaque,
                child: SizedBox(
                  width:
                      MediaQuery.of(context).size.width /
                      activeTabs.length.clamp(1, 6),
                  height: double.infinity,
                  child: Center(
                    child: Icon(
                      isSelected ? icons.$2 : icons.$1,
                      color: isSelected
                          ? Colors.white
                          : (tab == 'Reels' && !sm.isSessionActive)
                          ? Colors.white24
                          : Colors.white54,
                      size: 26,
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
        ),
      ),
    );
  }

  bool _isTabSelected(String tab, String url, String? username) {
    final path = Uri.tryParse(url)?.path ?? '';
    switch (tab) {
      case 'Home':
        return path == '/' || path.isEmpty;
      case 'Search':
        return path.startsWith('/explore');
      case 'Create':
        return path.startsWith('/create');
      case 'Notifications':
        return path.startsWith('/notifications');
      case 'Reels':
        return path.startsWith('/reels');
      case 'Profile':
        return username != null && path.startsWith('/$username');
      default:
        return false;
    }
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// No Internet Screen — minimal, branded
// ──────────────────────────────────────────────────────────────────────────────

class _NoInternetScreen extends StatelessWidget {
  final VoidCallback onRetry;
  const _NoInternetScreen({required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.black,
      width: double.infinity,
      height: double.infinity,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.wifi_off_rounded, color: Colors.white24, size: 80),
          const SizedBox(height: 24),
          const Text(
            'No Connection',
            style: TextStyle(
              color: Colors.white,
              fontSize: 20,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            'Please check your internet settings.',
            style: TextStyle(color: Colors.white38, fontSize: 14),
          ),
          const SizedBox(height: 40),
          ElevatedButton(
            onPressed: onRetry,
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.white,
              foregroundColor: Colors.black,
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
