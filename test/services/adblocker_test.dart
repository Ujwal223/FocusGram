import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:focusgram/services/injection_manager.dart';
import 'package:focusgram/services/adblock/adblock_content_blocker_loader.dart';
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
    'v2AdBlockerDomEnabled(true) does NOT trigger sponsored-post JS injection (handled by V2 engine)',
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
      await settings.setV2AdBlockerDomEnabled(true);

      expect(settings.v2AdBlockerDomEnabled, isTrue);

      mgr.setSettingsService(settings);
      await mgr.runAllPostLoadInjections('https://www.instagram.com/');

      // Verify that sponsored posts JS injection is NOT triggered by InjectionManager
      // (it's handled by the V2 DOM Ad Blocker engine instead)
      final sponsoredPostsInjected = fakeEval.sources.any(
        (s) => s.contains('hideSponsoredPosts') || s.contains('Sponsored'),
      );

      expect(
        sponsoredPostsInjected,
        isFalse,
        reason:
            'Sponsored posts blocking is now handled by V2 DOM Ad Blocker, not JS injection',
      );
    },
  );

  test(
    'adblock parser extracts strict host rules and ignores allow/cosmetic rules',
    () {
      final hosts = AdblockContentBlockerLoader.parseHostsFromFilterText('''
! comment
[Adblock Plus 2.0]
||ads.example.com^
||tracker.example.net/path.js\$third-party
@@||allowed.example.com^
example.com##.sponsored
||wild*.example.com^
||bad,domain.example^
||sub.adguard.example.org^\$script,third-party
''');

      expect(
        hosts,
        containsAll({
          'ads.example.com',
          'tracker.example.net',
          'sub.adguard.example.org',
        }),
      );
      expect(hosts, isNot(contains('allowed.example.com')));
      expect(hosts, isNot(contains('wild*.example.com')));
      expect(hosts, isNot(contains('bad,domain.example')));
    },
  );
}
