import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:webview_flutter/webview_flutter.dart';
import '../services/session_manager.dart';
import '../services/settings_service.dart';
import '../services/injection_controller.dart';
import '../services/navigation_guard.dart';
import 'session_modal.dart';
import 'settings_page.dart';
import 'reel_player_overlay.dart';

class MainWebViewPage extends StatefulWidget {
  const MainWebViewPage({super.key});

  @override
  State<MainWebViewPage> createState() => _MainWebViewPageState();
}

class _MainWebViewPageState extends State<MainWebViewPage> {
  late final WebViewController _controller;
  int _currentIndex = 0;
  bool _isLoading = true;

  // Cached username for profile navigation
  String? _cachedUsername;

  // Watchdog for app-session expiry
  Timer? _watchdog;
  bool _extensionDialogShown = false;

  @override
  void initState() {
    super.initState();
    _initWebView();
    _startWatchdog();
  }

  @override
  void dispose() {
    _watchdog?.cancel();
    super.dispose();
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
    final sessionManager = context.read<SessionManager>();

    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setUserAgent(InjectionController.iOSUserAgent)
      ..setBackgroundColor(Colors.black)
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageStarted: (url) {
            // Only show loading if it's a real page load (not SPA nav)
            if (!url.contains('#')) {
              if (mounted) setState(() => _isLoading = true);
            }
          },
          onPageFinished: (url) {
            if (mounted) setState(() => _isLoading = false);
            _applyInjections();
            _updateCurrentTab(url);
            // Cache username whenever we finish loading any page
            _cacheUsername();
          },
          onNavigationRequest: (request) {
            final isDmReel = NavigationGuard.isDmReelLink(request.url);

            final decision = NavigationGuard.evaluate(
              url: request.url,
              sessionActive: sessionManager.isSessionActive,
              isDmReelException: isDmReel,
            );

            if (decision.blocked) {
              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(decision.reason ?? 'Blocked'),
                    backgroundColor: Colors.red.shade900,
                    behavior: SnackBarBehavior.floating,
                    margin: const EdgeInsets.fromLTRB(16, 0, 16, 80),
                    duration: const Duration(seconds: 2),
                  ),
                );
              }
              return NavigationDecision.prevent;
            }

            // Open DM reel in isolated player
            if (isDmReel && !sessionManager.isSessionActive) {
              final canonicalUrl = NavigationGuard.canonicalizeDmReelUrl(
                request.url,
              );
              if (canonicalUrl != null) {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => ReelPlayerOverlay(url: canonicalUrl),
                  ),
                );
                return NavigationDecision.prevent;
              }
            }

            return NavigationDecision.navigate;
          },
        ),
      )
      ..loadRequest(Uri.parse('https://www.instagram.com/'));
  }

  void _applyInjections() {
    final sessionManager = context.read<SessionManager>();
    final settings = context.read<SettingsService>();
    final js = InjectionController.buildInjectionJS(
      sessionActive: sessionManager.isSessionActive,
      blurExplore: settings.blurExplore,
    );
    _controller.runJavaScript(js);
  }

  Future<void> _cacheUsername() async {
    if (_cachedUsername != null) return; // Already known
    try {
      final result = await _controller.runJavaScriptReturningResult(
        InjectionController.getLoggedInUsernameJS,
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

  Future<void> _onTabTapped(int index) async {
    // Don't re-navigate if already on this tab
    if (index == _currentIndex) return;
    setState(() => _currentIndex = index);

    switch (index) {
      case 0:
        await _navigateTo('/');
        break;
      case 1:
        await _navigateTo('/explore/search/');
        break;
      case 2:
        // Try to click Instagram's create button via JS
        try {
          await _controller.runJavaScript(
            InjectionController.clickCreateButtonJS,
          );
        } catch (_) {
          await _navigateTo('/');
        }
        break;
      case 3:
        await _navigateTo('/direct/inbox/');
        break;
      case 4:
        if (_cachedUsername != null) {
          await _navigateTo('/$_cachedUsername/');
        } else {
          // Try to get username first then navigate
          await _cacheUsername();
          if (_cachedUsername != null) {
            await _navigateTo('/$_cachedUsername/');
          } else {
            // Last fallback: navigate to accounts/edit — usually has username
            await _navigateTo('/accounts/edit/');
          }
        }
        break;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            // Status Bar — always on top
            _StatusBar(),

            // WebView
            Expanded(
              child: Stack(
                children: [
                  WebViewWidget(controller: _controller),
                  // Thin loading bar (not full-screen spinner)
                  if (_isLoading)
                    const LinearProgressIndicator(
                      backgroundColor: Colors.transparent,
                      color: Colors.blue,
                      minHeight: 2,
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
      bottomNavigationBar: _FocusGramNavBar(
        currentIndex: _currentIndex,
        onTap: _onTabTapped,
      ),
      floatingActionButton: _SessionFAB(onTap: _openSessionModal),
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
    );
  }

  void _openSessionModal() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const SessionModal(),
    );
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// Status Bar Widget — only rebuilds when session state changes
// ──────────────────────────────────────────────────────────────────────────────

class _StatusBar extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final sm = context.watch<SessionManager>();

    String label;
    Color dotColor;
    IconData dotIcon;

    if (sm.isSessionActive) {
      final m = sm.remainingSessionSeconds ~/ 60;
      final s = sm.remainingSessionSeconds % 60;
      label =
          'Reels: ${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
      dotColor = Colors.greenAccent;
      dotIcon = Icons.play_circle_outline;
    } else if (sm.isCooldownActive) {
      final m = sm.cooldownRemainingSeconds ~/ 60;
      label = 'Cooldown: ${m}m left';
      dotColor = Colors.orangeAccent;
      dotIcon = Icons.timer_outlined;
    } else {
      label = 'Reels Blocked';
      dotColor = Colors.redAccent;
      dotIcon = Icons.block;
    }

    // App session indicator
    final appM = sm.appSessionRemainingSeconds ~/ 60;
    final appS = sm.appSessionRemainingSeconds % 60;
    final appLabel = sm.isAppSessionActive
        ? 'App: ${appM.toString().padLeft(2, '0')}:${appS.toString().padLeft(2, '0')}'
        : '';

    return Container(
      height: 40,
      padding: const EdgeInsets.symmetric(horizontal: 14),
      color: Colors.black,
      child: Row(
        children: [
          // Status dot
          Icon(dotIcon, color: dotColor, size: 13),
          const SizedBox(width: 6),
          Text(
            label,
            style: TextStyle(
              color: dotColor,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
          const Spacer(),
          // App session timer
          if (appLabel.isNotEmpty)
            Text(
              appLabel,
              style: const TextStyle(color: Colors.white38, fontSize: 11),
            ),
          if (appLabel.isNotEmpty) const SizedBox(width: 10),
          // Daily reel usage
          Text(
            'Daily: ${sm.dailyRemainingSeconds ~/ 60}m',
            style: const TextStyle(color: Colors.white38, fontSize: 11),
          ),
          const SizedBox(width: 10),
          // Settings icon
          GestureDetector(
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const SettingsPage()),
            ),
            child: const Icon(Icons.tune, color: Colors.white38, size: 18),
          ),
        ],
      ),
    );
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// Custom Bottom Nav Bar — minimal, Instagram-like
// ──────────────────────────────────────────────────────────────────────────────

class _FocusGramNavBar extends StatelessWidget {
  final int currentIndex;
  final Future<void> Function(int) onTap;

  const _FocusGramNavBar({required this.currentIndex, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final items = [
      (Icons.home_outlined, Icons.home_rounded, 'Home'),
      (Icons.search, Icons.search, 'Search'),
      (Icons.add_box_outlined, Icons.add_box_rounded, 'Create'),
      (Icons.chat_bubble_outline, Icons.chat_bubble, 'Messages'),
      (Icons.person_outline, Icons.person, 'Profile'),
    ];

    return Container(
      color: Colors.black,
      child: SafeArea(
        top: false,
        child: SizedBox(
          height: 52,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: items.asMap().entries.map((entry) {
              final i = entry.key;
              final (outlinedIcon, filledIcon, label) = entry.value;
              final isSelected = i == currentIndex;
              return GestureDetector(
                onTap: () => onTap(i),
                behavior: HitTestBehavior.opaque,
                child: SizedBox(
                  width: 60,
                  child: Center(
                    child: Icon(
                      isSelected ? filledIcon : outlinedIcon,
                      color: isSelected ? Colors.white : Colors.white54,
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
}

// ──────────────────────────────────────────────────────────────────────────────
// Session FAB
// ──────────────────────────────────────────────────────────────────────────────

class _SessionFAB extends StatelessWidget {
  final VoidCallback onTap;
  const _SessionFAB({required this.onTap});

  @override
  Widget build(BuildContext context) {
    final sm = context.watch<SessionManager>();
    final settings = context.watch<SettingsService>();

    if (sm.isSessionActive) {
      // Show "end session" button when session is active
      return FloatingActionButton.small(
        backgroundColor: Colors.green.shade700,
        onPressed: () => sm.endSession(),
        child: const Icon(Icons.stop, color: Colors.white, size: 18),
      );
    }

    final fab = FloatingActionButton.small(
      backgroundColor: Colors.blue.shade700,
      onPressed: settings.requireLongPress ? null : onTap,
      child: const Icon(
        Icons.play_arrow_rounded,
        color: Colors.white,
        size: 22,
      ),
    );

    if (settings.requireLongPress) {
      return GestureDetector(onLongPress: onTap, child: fab);
    }
    return fab;
  }
}
