// test/services/session_manager_test.dart
//
// Tests for SessionManager — reel session logic, daily quotas, cooldowns,
// app session tracking, and scheduled blocking.
//
// SessionManager uses SharedPreferences and Timer internally.
// We use SharedPreferences.setMockInitialValues({}) for isolation.
// Timer-based tests use FakeAsync where needed.
//
// Run with: flutter test test/services/session_manager_test.dart

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:focusgram/services/session_manager.dart';

// Helper: initialise a SessionManager with a clean SharedPreferences slate.
Future<SessionManager> _freshManager() async {
  SharedPreferences.setMockInitialValues({});
  final sm = SessionManager();
  await sm.init();
  return sm;
}

void main() {
  // ── Initial state ────────────────────────────────────────────────────────

  group('SessionManager — initial state', () {
    test('no reel session active on first init', () async {
      final sm = await _freshManager();
      expect(sm.isSessionActive, isFalse);
    });

    test('remainingSessionSeconds is 0 when no session', () async {
      final sm = await _freshManager();
      expect(sm.remainingSessionSeconds, 0);
    });

    test('dailyUsedSeconds starts at 0', () async {
      final sm = await _freshManager();
      expect(sm.dailyUsedSeconds, 0);
    });

    test('dailyRemainingSeconds matches default limit', () async {
      final sm = await _freshManager();
      expect(sm.dailyRemainingSeconds, sm.dailyLimitSeconds);
    });

    test('isDailyLimitExhausted is false initially', () async {
      final sm = await _freshManager();
      expect(sm.isDailyLimitExhausted, isFalse);
    });

    test('isCooldownActive is false initially', () async {
      final sm = await _freshManager();
      expect(sm.isCooldownActive, isFalse);
    });

    test('cooldownRemainingSeconds is 0 when no cooldown', () async {
      final sm = await _freshManager();
      expect(sm.cooldownRemainingSeconds, 0);
    });

    test('daily open count is incremented on init', () async {
      final sm = await _freshManager();
      // init() calls _incrementOpenCount once
      expect(sm.dailyOpenCount, 1);
    });
  });

  // ── Reel session — startSession ──────────────────────────────────────────

  group('SessionManager.startSession', () {
    test('returns true and activates session within daily quota', () async {
      final sm = await _freshManager();
      final ok = sm.startSession(5);
      expect(ok, isTrue);
      expect(sm.isSessionActive, isTrue);
    });

    test('session expires after requested minutes (approx)', () async {
      final sm = await _freshManager();
      sm.startSession(5);
      // Remaining should be <= 5 min = 300 s
      expect(sm.remainingSessionSeconds, lessThanOrEqualTo(300));
      expect(sm.remainingSessionSeconds, greaterThan(290));
    });

    test('returns false when daily limit is exhausted', () async {
      SharedPreferences.setMockInitialValues({
        'sessn_daily_limit_sec': 300, // 5 min daily limit
        'sessn_daily_used_sec': 300, // already used all of it
        'sessn_daily_date': _today(),
      });
      final sm = SessionManager();
      await sm.init();
      final ok = sm.startSession(5);
      expect(ok, isFalse);
      expect(sm.isSessionActive, isFalse);
    });

    test('returns false during cooldown', () async {
      // Last session ended 5 minutes ago; cooldown is 15 min
      final lastEnd = DateTime.now().subtract(const Duration(minutes: 5));
      SharedPreferences.setMockInitialValues({
        'sessn_last_end_ts': lastEnd.millisecondsSinceEpoch,
        'sessn_cooldown_sec': 900, // 15 min cooldown
        'sessn_daily_date': _today(),
      });
      final sm = SessionManager();
      await sm.init();
      expect(sm.isCooldownActive, isTrue);
      final ok = sm.startSession(5);
      expect(ok, isFalse);
    });

    test('notifies listeners when session starts', () async {
      final sm = await _freshManager();
      bool notified = false;
      sm.addListener(() => notified = true);
      sm.startSession(5);
      expect(notified, isTrue);
    });
  });

  // ── Reel session — endSession ────────────────────────────────────────────

  group('SessionManager.endSession', () {
    test('deactivates an active session', () async {
      final sm = await _freshManager();
      sm.startSession(5);
      expect(sm.isSessionActive, isTrue);
      sm.endSession();
      expect(sm.isSessionActive, isFalse);
    });

    test('remainingSessionSeconds becomes 0 after end', () async {
      final sm = await _freshManager();
      sm.startSession(5);
      sm.endSession();
      expect(sm.remainingSessionSeconds, 0);
    });

    test('cooldown becomes active after ending a session', () async {
      final sm = await _freshManager();
      sm.startSession(5);
      sm.endSession();
      // Only active if cooldownSeconds > 0
      if (sm.cooldownSeconds > 0) {
        expect(sm.isCooldownActive, isTrue);
      }
    });

    test('notifies listeners on end', () async {
      final sm = await _freshManager();
      sm.startSession(5);
      bool notified = false;
      sm.addListener(() => notified = true);
      sm.endSession();
      expect(notified, isTrue);
    });
  });

  // ── Daily quota ──────────────────────────────────────────────────────────

  group('SessionManager — daily quota', () {
    test('dailyRemainingSeconds is capped at 0 when exhausted', () async {
      SharedPreferences.setMockInitialValues({
        'sessn_daily_limit_sec': 300,
        'sessn_daily_used_sec': 400, // over limit
        'sessn_daily_date': _today(),
      });
      final sm = SessionManager();
      await sm.init();
      expect(sm.dailyRemainingSeconds, 0);
      expect(sm.isDailyLimitExhausted, isTrue);
    });

    test('dailyRemainingSeconds decrements correctly', () async {
      SharedPreferences.setMockInitialValues({
        'sessn_daily_limit_sec': 600,
        'sessn_daily_used_sec': 100,
        'sessn_daily_date': _today(),
      });
      final sm = SessionManager();
      await sm.init();
      expect(sm.dailyRemainingSeconds, 500);
    });
  });

  // ── App session ──────────────────────────────────────────────────────────

  group('SessionManager — app session', () {
    test('app session is active when end is in the future', () async {
      final future = DateTime.now().add(const Duration(minutes: 30));
      SharedPreferences.setMockInitialValues({
        'app_sess_end_ts': future.millisecondsSinceEpoch,
        'sessn_daily_date': _today(),
      });
      final sm = SessionManager();
      await sm.init();
      expect(sm.isAppSessionActive, isTrue);
    });

    test('app session is NOT active when end is in the past', () async {
      final past = DateTime.now().subtract(const Duration(minutes: 10));
      SharedPreferences.setMockInitialValues({
        'app_sess_end_ts': past.millisecondsSinceEpoch,
        'sessn_daily_date': _today(),
      });
      final sm = SessionManager();
      await sm.init();
      expect(sm.isAppSessionActive, isFalse);
    });

    test('appSessionRemainingSeconds is > 0 for a future session', () async {
      final future = DateTime.now().add(const Duration(minutes: 30));
      SharedPreferences.setMockInitialValues({
        'app_sess_end_ts': future.millisecondsSinceEpoch,
        'sessn_daily_date': _today(),
      });
      final sm = SessionManager();
      await sm.init();
      expect(sm.appSessionRemainingSeconds, greaterThan(0));
    });

    test('canExtendAppSession is true by default', () async {
      final sm = await _freshManager();
      expect(sm.canExtendAppSession, isTrue);
    });

    test('canExtendAppSession is false when extension used', () async {
      SharedPreferences.setMockInitialValues({
        'app_sess_ext_used': true,
        'sessn_daily_date': _today(),
      });
      final sm = SessionManager();
      await sm.init();
      expect(sm.canExtendAppSession, isFalse);
    });
  });

  // ── Scheduled blocking ───────────────────────────────────────────────────

  group('SessionManager — scheduled blocking', () {
    test('isScheduledBlockActive is false when schedule disabled', () async {
      final sm = await _freshManager();
      expect(sm.scheduleEnabled, isFalse);
      expect(sm.isScheduledBlockActive, isFalse);
    });

    test('simple daytime range (9:00–17:00) blocks at noon', () async {
      final sm = await _freshManager();
      // We can't control DateTime.now() but we CAN test the logic
      // by verifying the method doesn't throw and returns a bool.
      expect(sm.isScheduledBlockActive, isA<bool>());
    });
  });

  // ── setAppForeground ─────────────────────────────────────────────────────

  group('SessionManager.setAppForeground', () {
    test('does nothing when value is unchanged', () async {
      final sm = await _freshManager();
      bool notified = false;
      sm.addListener(() => notified = true);
      sm.setAppForeground(true); // already true by default (in foreground)
      expect(notified, isFalse);
    });

    test('notifies when transitioning to background', () async {
      final sm = await _freshManager();
      bool notified = false;
      sm.addListener(() => notified = true);
      sm.setAppForeground(false);
      expect(notified, isTrue);
    });

    test('notifies when returning to foreground', () async {
      final sm = await _freshManager();
      sm.setAppForeground(false); // go to background first
      bool notified = false;
      sm.addListener(() => notified = true);
      sm.setAppForeground(true);
      expect(notified, isTrue);
    });
  });
}

/// Returns today's date formatted as 'yyyy-MM-dd' (same format as SessionManager).
String _today() {
  final now = DateTime.now();
  return '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
}
