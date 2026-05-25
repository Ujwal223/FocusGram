import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:focusgram/services/injection_manager.dart';
import 'package:focusgram/services/session_manager.dart';
import 'package:focusgram/services/settings_service.dart';

class _FakeJsEvaluator implements JsEvaluator {
  final List<String> sources = [];

  @override
  Future<void> evaluateJavascript({required String source}) async {
    sources.add(source);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  test(
    'does NOT inject hideSuggestedPosts JS even when legacy setting is true',
    () async {
      final prefs = await SharedPreferences.getInstance();
      final sm = SessionManager();
      final fakeEval = _FakeJsEvaluator();

      final mgr = InjectionManager.forTest(
        jsEvaluator: fakeEval,
        prefs: prefs,
        sessionManager: sm,
      );

      final settings = SettingsService();
      await settings.init();
      await settings.setHideSuggestedPosts(true);

      mgr.setSettingsService(settings);

      await mgr.runAllPostLoadInjections('https://www.instagram.com/');

      final any = fakeEval.sources.any((s) => s.contains('hideSuggestedPosts'));
      expect(any, isFalse);
    },
  );

  test(
    'does NOT inject hideSuggestedPosts JS when settings.hideSuggestedPosts=false',
    () async {
      final prefs = await SharedPreferences.getInstance();
      final sm = SessionManager();
      final fakeEval = _FakeJsEvaluator();

      final mgr = InjectionManager.forTest(
        jsEvaluator: fakeEval,
        prefs: prefs,
        sessionManager: sm,
      );

      final settings = SettingsService();
      await settings.init();
      await settings.setHideSuggestedPosts(false);

      mgr.setSettingsService(settings);

      await mgr.runAllPostLoadInjections('https://www.instagram.com/');

      final any = fakeEval.sources.any((s) => s.contains('hideSuggestedPosts'));
      expect(any, isFalse);
    },
  );

  test(
    'injects video downloader JS only when settings.videoDownloadEnabled=true',
    () async {
      final prefs = await SharedPreferences.getInstance();
      final sm = SessionManager();
      final fakeEval = _FakeJsEvaluator();

      final mgr = InjectionManager.forTest(
        jsEvaluator: fakeEval,
        prefs: prefs,
        sessionManager: sm,
      );

      final settings = SettingsService();
      await settings.init();
      await settings.setVideoDownloadEnabled(true);

      mgr.setSettingsService(settings);

      await mgr.runAllPostLoadInjections('https://www.instagram.com/');

      final any = fakeEval.sources.any(
        (s) => s.contains('__fgMediaDownloadRunning'),
      );
      expect(any, isTrue);
    },
  );

  test(
    'does NOT inject video downloader JS when settings.videoDownloadEnabled=false',
    () async {
      final prefs = await SharedPreferences.getInstance();
      final sm = SessionManager();
      final fakeEval = _FakeJsEvaluator();

      final mgr = InjectionManager.forTest(
        jsEvaluator: fakeEval,
        prefs: prefs,
        sessionManager: sm,
      );

      final settings = SettingsService();
      await settings.init();
      await settings.setVideoDownloadEnabled(false);

      mgr.setSettingsService(settings);

      await mgr.runAllPostLoadInjections('https://www.instagram.com/');

      final any = fakeEval.sources.any(
        (s) => s.contains('__fgMediaDownloadRunning'),
      );
      expect(any, isFalse);
    },
  );

  test('injects home feed scroll lock flag when enabled', () async {
    final prefs = await SharedPreferences.getInstance();
    final sm = SessionManager();
    final fakeEval = _FakeJsEvaluator();

    final mgr = InjectionManager.forTest(
      jsEvaluator: fakeEval,
      prefs: prefs,
      sessionManager: sm,
    );

    final settings = SettingsService();
    await settings.init();
    await settings.setBlockHomeFeedScrollInternal(true);

    mgr.setSettingsService(settings);

    await mgr.runAllPostLoadInjections('https://www.instagram.com/');

    final any = fakeEval.sources.any(
      (s) => s.contains('window.__fgBlockHomeFeedScroll = true;'),
    );
    expect(any, isTrue);
  });
}
