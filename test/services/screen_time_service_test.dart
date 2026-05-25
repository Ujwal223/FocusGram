import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:focusgram/services/screen_time_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  test('init loads persisted secondsByDate', () async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      ScreenTimeService.prefKey,
      '{"2026-01-01": 42, "2026-01-02": 7}',
    );

    final s = ScreenTimeService();
    await s.init();

    expect(s.secondsByDate['2026-01-01'], 42);
    expect(s.secondsByDate['2026-01-02'], 7);
  });

  test('resetAll clears stored data and in-memory map', () async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(ScreenTimeService.prefKey, '{"2026-01-01": 42}');

    final s = ScreenTimeService();
    await s.init();
    expect(s.secondsByDate.isNotEmpty, isTrue);

    await s.resetAll();
    expect(s.secondsByDate, isEmpty);

    final raw = prefs.getString(ScreenTimeService.prefKey);
    expect(raw, isNull);
  });

  test('startTracking increments today seconds and stopTracking persists', () async {
    final s = ScreenTimeService();
    await s.init();

    final beforeTodayKey = DateTime.now();
    final todayKey =
        '${beforeTodayKey.year.toString().padLeft(4, '0')}-'
        '${beforeTodayKey.month.toString().padLeft(2, '0')}-'
        '${beforeTodayKey.day.toString().padLeft(2, '0')}';

    s.startTracking();

    // Wait ~2 seconds (test is unit-ish; still acceptable).
    await Future<void>.delayed(const Duration(seconds: 2));

    s.stopTracking();

    expect(s.secondsByDate[todayKey], isNotNull);
    expect(s.secondsByDate[todayKey]!, greaterThanOrEqualTo(2));

    final prefs = await SharedPreferences.getInstance();
    final stored = prefs.getString(ScreenTimeService.prefKey);
    expect(stored, isNotNull);
    expect(stored, contains(todayKey));
  });
}
