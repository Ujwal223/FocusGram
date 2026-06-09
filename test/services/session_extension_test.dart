import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:focusgram/services/session_manager.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('SessionManager — Extension flow', () {
    test('canExtendAppSession is true when session just ended', () async {
      final sm = SessionManager();
      await sm.init();

      // Start an app session
      sm.startAppSession(60);
      expect(sm.isSessionActive, isTrue);

      // End it
      sm.endAppSession();
      expect(sm.isAppSessionExpired, isTrue);
      expect(sm.canExtendAppSession, isTrue);
    });

    test('extendAppSession sets canExtendAppSession to false', () async {
      final sm = SessionManager();
      await sm.init();

      sm.startAppSession(60);
      sm.endAppSession();
      expect(sm.canExtendAppSession, isTrue);

      sm.extendAppSession();
      expect(sm.canExtendAppSession, isFalse);
      expect(sm.isSessionActive, isTrue);
    });

    test('canExtendAppSession is false after re-ending an extended session',
        () async {
      final sm = SessionManager();
      await sm.init();

      sm.startAppSession(60);
      sm.endAppSession();
      sm.extendAppSession();
      sm.endAppSession();

      expect(sm.canExtendAppSession, isFalse);
    });
  });

  group('SessionManager — App session lifecycle', () {
    test('startAppSession sets isSessionActive', () async {
      final sm = SessionManager();
      await sm.init();

      sm.startAppSession(30);
      expect(sm.isSessionActive, isTrue);
    });

    test('endAppSession clears session and sets expired', () async {
      final sm = SessionManager();
      await sm.init();

      sm.startAppSession(30);
      sm.endAppSession();

      expect(sm.isSessionActive, isFalse);
      expect(sm.isAppSessionExpired, isTrue);
    });
  });
}
