import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'services/session_manager.dart';
import 'services/settings_service.dart';
import 'screens/onboarding_page.dart';
import 'screens/main_webview_page.dart';
import 'screens/breath_gate_screen.dart';
import 'screens/app_session_picker.dart';
import 'screens/cooldown_gate_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Lock to portrait
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);

  final sessionManager = SessionManager();
  final settingsService = SettingsService();

  await sessionManager.init();
  await settingsService.init();

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: sessionManager),
        ChangeNotifierProvider.value(value: settingsService),
      ],
      child: const FocusGramApp(),
    ),
  );
}

class FocusGramApp extends StatelessWidget {
  const FocusGramApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'FocusGram',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        colorScheme: ColorScheme.dark(
          primary: Colors.blue.shade400,
          surface: Colors.black,
        ),
        scaffoldBackgroundColor: Colors.black,
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
///   4. App Session Picker (always)
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

    // Step 4: App session picker
    if (!_appSessionStarted) {
      return AppSessionPickerScreen(
        onSessionStarted: () => setState(() => _appSessionStarted = true),
      );
    }

    // Step 5: Main app
    return const MainWebViewPage();
  }
}
