import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:app_links/app_links.dart';
import 'services/session_manager.dart';
import 'services/settings_service.dart';
import 'services/screen_time_service.dart';
import 'services/focusgram_router.dart';
import 'services/injection_controller.dart';
import 'screens/onboarding_page.dart';
import 'screens/main_webview_page.dart';
import 'screens/breath_gate_screen.dart';
import 'screens/app_session_picker.dart';
import 'screens/cooldown_gate_screen.dart';
import 'services/notification_service.dart';
import 'features/update_checker/update_checker_service.dart';
import 'features/preloader/instagram_preloader.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Lock to portrait
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);

  final sessionManager = SessionManager();
  final settingsService = SettingsService();
  final screenTimeService = ScreenTimeService();

  final updateChecker = UpdateCheckerService();

  await sessionManager.init();
  await settingsService.init();
  await screenTimeService.init();
  await NotificationService().init();

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: sessionManager),
        ChangeNotifierProvider.value(value: settingsService),
        ChangeNotifierProvider.value(value: screenTimeService),
        ChangeNotifierProvider.value(value: updateChecker),
      ],
      child: const FocusGramApp(),
    ),
  );

  // Fire and forget — preloads Instagram while app UI initialises.
  unawaited(InstagramPreloader.start(InjectionController.iOSUserAgent));
}

class FocusGramApp extends StatelessWidget {
  const FocusGramApp({super.key});

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    final isDark = settings.isDarkMode;

    return MaterialApp(
      title: 'FocusGram',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: isDark ? Brightness.dark : Brightness.light,
        colorScheme: isDark
            ? ColorScheme.dark(
                primary: Colors.blue.shade400,
                surface: Colors.black,
              )
            : ColorScheme.light(primary: Colors.blue),
        scaffoldBackgroundColor: isDark ? Colors.black : Colors.white,
        useMaterial3: true,
        splashColor: Colors.transparent,
        highlightColor: Colors.transparent,
      ),
      home: const InitialRouteHandler(),
    );
  }
}

/// Flow on every cold open:
///   1. Onboarding (if first run)
///   2. Cooldown Gate (if app-open cooldown active)
///   3. Breath Gate (if enabled in settings)
///   4. If an app session is already active, resume it
///      otherwise show App Session Picker
///   5. Main WebView
class InitialRouteHandler extends StatefulWidget {
  const InitialRouteHandler({super.key});

  @override
  State<InitialRouteHandler> createState() => _InitialRouteHandlerState();
}

class _InitialRouteHandlerState extends State<InitialRouteHandler> {
  bool _breathCompleted = false;
  bool _appSessionStarted = false;
  bool _onboardingCompleted = false;
  late AppLinks _appLinks;

  @override
  void initState() {
    super.initState();
    _appLinks = AppLinks();
    _initDeepLinks();
  }

  Future<void> _initDeepLinks() async {
    // 1. Handle background links while app is running
    _appLinks.uriLinkStream.listen((uri) {
      debugPrint('Incoming Deep Link: $uri');
      FocusGramRouter.pendingUrl.value = uri.toString();
    });

    // 2. Handle the initial link that opened the app
    final initialUri = await _appLinks.getInitialLink();
    if (initialUri != null) {
      debugPrint('Initial Deep Link: $initialUri');
      FocusGramRouter.pendingUrl.value = initialUri.toString();
    }
  }

  @override
  Widget build(BuildContext context) {
    final sm = context.watch<SessionManager>();
    final settings = context.watch<SettingsService>();

    // Step 1: Onboarding
    if (settings.isFirstRun && !_onboardingCompleted) {
      return OnboardingPage(
        onFinish: () => setState(() => _onboardingCompleted = true),
      );
    }

    // Step 2: Cooldown gate — if too soon since last session
    if (sm.isAppOpenCooldownActive) {
      return const CooldownGateScreen();
    }

    // Step 3: Breath gate
    if (settings.showBreathGate && !_breathCompleted) {
      return BreathGateScreen(
        onFinish: () => setState(() => _breathCompleted = true),
      );
    }

    // Step 4: App session picker / resume existing session
    if (!_appSessionStarted) {
      if (sm.isAppSessionActive) {
        // User already has an active app session — don't ask intention again.
        _appSessionStarted = true;
      } else {
        return AppSessionPickerScreen(
          onSessionStarted: () => setState(() => _appSessionStarted = true),
        );
      }
    }

    // Step 5: Main app
    return const MainWebViewPage();
  }
}
