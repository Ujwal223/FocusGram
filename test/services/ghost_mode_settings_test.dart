import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:focusgram/services/settings_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('SettingsService — Ghost mode toggle', () {
    test('ghostMode defaults to false', () async {
      final s = SettingsService();
      await s.init();
      expect(s.ghostMode, isFalse);
    });

    test('ghostMode toggle persists and loads on restart', () async {
      final s = SettingsService();
      await s.init();
      await s.setGhostMode(true);

      expect(s.ghostMode, isTrue);

      // Simulate restart by creating a new instance with saved prefs
      final s2 = SettingsService();
      await s2.init();
      expect(s2.ghostMode, isTrue);
    });

    test('ghostMode toggles off correctly', () async {
      final s = SettingsService();
      await s.init();
      await s.setGhostMode(true);
      expect(s.ghostMode, isTrue);

      await s.setGhostMode(false);
      expect(s.ghostMode, isFalse);
    });
  });

  group('SettingsService — Grayscale persistence', () {
    test('grayscaleEnabled defaults to false', () async {
      final s = SettingsService();
      await s.init();
      expect(s.grayscaleEnabled, isFalse);
    });

    test('setGrayscaleEnabled persists and isActiveNow returns true', () async {
      final s = SettingsService();
      await s.init();
      await s.setGrayscaleEnabled(true);

      expect(s.grayscaleEnabled, isTrue);
      expect(s.isGrayscaleActiveNow, isTrue);

      // Simulate restart
      final s2 = SettingsService();
      await s2.init();
      expect(s2.grayscaleEnabled, isTrue);
    });

    test('isGrayscaleActiveNow returns true when toggle is on', () async {
      final s = SettingsService();
      await s.init();
      await s.setGrayscaleEnabled(true);
      expect(s.isGrayscaleActiveNow, isTrue);
    });

    test(
      'isGrayscaleActiveNow returns false when toggle off and no schedules',
      () async {
        final s = SettingsService();
        await s.init();
        expect(s.isGrayscaleActiveNow, isFalse);
      },
    );
  });
}
