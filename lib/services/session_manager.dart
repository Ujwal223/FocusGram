import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:intl/intl.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Manages all session logic for FocusGram:
///
/// **App Session** — how long the user plans to use Instagram today.
///   Started by the AppSessionPicker on every cold open.
///   Enforced with a watchdog timer; one 10-min extension allowed.
///   Cooldown enforced between app-opens.
///
/// **Reel Session** — a period during which reels are unblocked.
///   Started manually by the user via the FAB.
///   Deducted from the daily reel quota.
class SessionManager extends ChangeNotifier {
  // ── Reel-session keys ──────────────────────────────────────
  static const _keyDailyDate = 'sessn_daily_date';
  static const _keyDailyUsedSeconds = 'sessn_daily_used_sec';
  static const _keySessionExpiry = 'sessn_expiry_ts';
  static const _keyLastSessionEnd = 'sessn_last_end_ts';
  static const _keyDailyLimitSec = 'sessn_daily_limit_sec';
  static const _keyPerSessionSec = 'sessn_per_session_sec';
  static const _keyCooldownSec = 'sessn_cooldown_sec';

  // ── App-session keys ───────────────────────────────────────
  static const _keyAppSessionEnd = 'app_sess_end_ts';
  static const _keyAppSessionExtUsed = 'app_sess_ext_used';
  static const _keyLastAppSessEnd = 'app_sess_last_end_ts';
  static const _keyDailyOpenCount = 'app_open_count';

  SharedPreferences? _prefs;

  // ── Reel-session runtime ───────────────────────────────────
  bool _isSessionActive = false;
  DateTime? _sessionExpiry;
  int _dailyUsedSeconds = 0;
  DateTime? _lastSessionEnd;
  Timer? _ticker;

  // ── App-session runtime ────────────────────────────────────
  DateTime? _appSessionEnd;
  bool _appExtensionUsed = false;
  DateTime? _lastAppSessionEnd;
  bool _appSessionExpiredFlag =
      false; // set when time runs out, waiting for user action
  int _dailyOpenCount = 0;

  // ── Settings defaults ──────────────────────────────────────
  int _dailyLimitSeconds = 30 * 60; // 30 min
  int _perSessionSeconds = 5 * 60; // 5 min
  int _cooldownSeconds = 15 * 60; // 15 min cooldown between reel sessions

  // ── Public getters — Reel session ─────────────────────────
  bool get isSessionActive => _isSessionActive;

  int get remainingSessionSeconds {
    if (!_isSessionActive || _sessionExpiry == null) return 0;
    final diff = _sessionExpiry!.difference(DateTime.now()).inSeconds;
    return diff > 0 ? diff : 0;
  }

  int get dailyUsedSeconds => _dailyUsedSeconds;
  int get dailyLimitSeconds => _dailyLimitSeconds;
  int get dailyRemainingSeconds {
    final rem = _dailyLimitSeconds - _dailyUsedSeconds;
    return rem > 0 ? rem : 0;
  }

  bool get isDailyLimitExhausted => dailyRemainingSeconds <= 0;

  bool get isCooldownActive {
    if (_lastSessionEnd == null) return false;
    final elapsed = DateTime.now().difference(_lastSessionEnd!).inSeconds;
    return elapsed < _cooldownSeconds;
  }

  int get cooldownRemainingSeconds {
    if (!isCooldownActive || _lastSessionEnd == null) return 0;
    final elapsed = DateTime.now().difference(_lastSessionEnd!).inSeconds;
    final rem = _cooldownSeconds - elapsed;
    return rem > 0 ? rem : 0;
  }

  int get perSessionSeconds => _perSessionSeconds;
  int get cooldownSeconds => _cooldownSeconds;

  // ── Public getters — App session ──────────────────────────

  /// Whether the user has an active app session right now.
  bool get isAppSessionActive {
    if (_appSessionEnd == null) return false;
    return DateTime.now().isBefore(_appSessionEnd!);
  }

  /// Seconds left in the current app session.
  int get appSessionRemainingSeconds {
    if (_appSessionEnd == null) return 0;
    final diff = _appSessionEnd!.difference(DateTime.now()).inSeconds;
    return diff > 0 ? diff : 0;
  }

  /// True when the app session has expired and user has not yet acted.
  bool get isAppSessionExpired => _appSessionExpiredFlag;

  /// Whether the 10-min extension has been used.
  bool get canExtendAppSession => !_appExtensionUsed;

  /// Seconds remaining in the app-open cooldown.
  int get appOpenCooldownRemainingSeconds {
    if (_lastAppSessionEnd == null) return 0;
    final elapsed = DateTime.now().difference(_lastAppSessionEnd!).inSeconds;
    final rem = _cooldownSeconds - elapsed;
    return rem > 0 ? rem : 0;
  }

  /// True if the app-open cooldown is still active.
  bool get isAppOpenCooldownActive {
    if (_lastAppSessionEnd == null) return false;
    return appOpenCooldownRemainingSeconds > 0;
  }

  /// How many times the user has opened the app today.
  int get dailyOpenCount => _dailyOpenCount;

  // ── Initialization ─────────────────────────────────────────
  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    await _resetDailyIfNeeded();
    _loadPersisted();
    _startTicker();
    _incrementOpenCount();
  }

  Future<void> _resetDailyIfNeeded() async {
    final today = DateFormat('yyyy-MM-dd').format(DateTime.now());
    final stored = _prefs!.getString(_keyDailyDate) ?? '';
    if (stored != today) {
      await _prefs!.setString(_keyDailyDate, today);
      await _prefs!.setInt(_keyDailyUsedSeconds, 0);
      await _prefs!.setInt(_keyDailyOpenCount, 0);
    }
  }

  void _loadPersisted() {
    _dailyUsedSeconds = _prefs!.getInt(_keyDailyUsedSeconds) ?? 0;
    _dailyLimitSeconds = _prefs!.getInt(_keyDailyLimitSec) ?? 30 * 60;
    _perSessionSeconds = _prefs!.getInt(_keyPerSessionSec) ?? 5 * 60;
    _cooldownSeconds = _prefs!.getInt(_keyCooldownSec) ?? 15 * 60;
    _dailyOpenCount = _prefs!.getInt(_keyDailyOpenCount) ?? 0;

    // Reel session
    final expiryMs = _prefs!.getInt(_keySessionExpiry) ?? 0;
    if (expiryMs > 0) {
      final expiry = DateTime.fromMillisecondsSinceEpoch(expiryMs);
      if (expiry.isAfter(DateTime.now())) {
        _sessionExpiry = expiry;
        _isSessionActive = true;
      } else {
        _cleanupExpiredReelSession();
      }
    }
    final lastEndMs = _prefs!.getInt(_keyLastSessionEnd) ?? 0;
    if (lastEndMs > 0) {
      _lastSessionEnd = DateTime.fromMillisecondsSinceEpoch(lastEndMs);
    }

    // App session
    final appEndMs = _prefs!.getInt(_keyAppSessionEnd) ?? 0;
    if (appEndMs > 0) {
      _appSessionEnd = DateTime.fromMillisecondsSinceEpoch(appEndMs);
    }
    _appExtensionUsed = _prefs!.getBool(_keyAppSessionExtUsed) ?? false;

    final lastAppEndMs = _prefs!.getInt(_keyLastAppSessEnd) ?? 0;
    if (lastAppEndMs > 0) {
      _lastAppSessionEnd = DateTime.fromMillisecondsSinceEpoch(lastAppEndMs);
    }
  }

  void _incrementOpenCount() {
    _dailyOpenCount++;
    _prefs?.setInt(_keyDailyOpenCount, _dailyOpenCount);
  }

  void _startTicker() {
    _ticker?.cancel();
    _ticker = Timer.periodic(const Duration(seconds: 1), (_) => _tick());
  }

  void _tick() {
    bool changed = false;

    // Reel session countdown
    if (_isSessionActive) {
      if (remainingSessionSeconds <= 0) {
        _cleanupExpiredReelSession();
        changed = true;
      } else {
        _dailyUsedSeconds++;
        _prefs?.setInt(_keyDailyUsedSeconds, _dailyUsedSeconds);
        if (isDailyLimitExhausted) _cleanupExpiredReelSession();
        changed = true;
      }
    }

    // App session expiry check
    if (_appSessionEnd != null &&
        !_appSessionExpiredFlag &&
        DateTime.now().isAfter(_appSessionEnd!)) {
      _appSessionExpiredFlag = true;
      changed = true;
    }

    if (isCooldownActive) changed = true;

    if (changed) notifyListeners();
  }

  void _cleanupExpiredReelSession() {
    _isSessionActive = false;
    _sessionExpiry = null;
    _lastSessionEnd = DateTime.now();
    _prefs?.setInt(_keySessionExpiry, 0);
    _prefs?.setInt(_keyLastSessionEnd, _lastSessionEnd!.millisecondsSinceEpoch);
  }

  // ── Reel session API ───────────────────────────────────────

  bool startSession(int minutes) {
    if (isDailyLimitExhausted) return false;
    if (isCooldownActive) return false;
    final allowed = (minutes * 60).clamp(0, dailyRemainingSeconds);
    _sessionExpiry = DateTime.now().add(Duration(seconds: allowed));
    _isSessionActive = true;
    _prefs?.setInt(_keySessionExpiry, _sessionExpiry!.millisecondsSinceEpoch);
    notifyListeners();
    return true;
  }

  void endSession() {
    if (!_isSessionActive) return;
    _cleanupExpiredReelSession();
    notifyListeners();
  }

  void accrueSeconds(int seconds) {
    _dailyUsedSeconds = (_dailyUsedSeconds + seconds).clamp(
      0,
      _dailyLimitSeconds,
    );
    _prefs?.setInt(_keyDailyUsedSeconds, _dailyUsedSeconds);
    if (isDailyLimitExhausted && _isSessionActive) _cleanupExpiredReelSession();
    notifyListeners();
  }

  // ── App session API ────────────────────────────────────────

  /// Start an app session of [minutes] (1–60).
  void startAppSession(int minutes) {
    final end = DateTime.now().add(Duration(minutes: minutes));
    _appSessionEnd = end;
    _appSessionExpiredFlag = false;
    _appExtensionUsed = false;
    _prefs?.setInt(_keyAppSessionEnd, end.millisecondsSinceEpoch);
    _prefs?.setBool(_keyAppSessionExtUsed, false);
    notifyListeners();
  }

  /// Extend the app session by 10 minutes. Only works once.
  bool extendAppSession() {
    if (_appExtensionUsed) return false;
    final base = _appSessionEnd ?? DateTime.now();
    _appSessionEnd = base.add(const Duration(minutes: 10));
    _appExtensionUsed = true;
    _appSessionExpiredFlag = false;
    _prefs?.setInt(_keyAppSessionEnd, _appSessionEnd!.millisecondsSinceEpoch);
    _prefs?.setBool(_keyAppSessionExtUsed, true);
    notifyListeners();
    return true;
  }

  /// Called when the user closes the app voluntarily or after extension denial.
  void endAppSession() {
    _lastAppSessionEnd = DateTime.now();
    _appSessionEnd = null;
    _appSessionExpiredFlag = false;
    _prefs?.setInt(
      _keyLastAppSessEnd,
      _lastAppSessionEnd!.millisecondsSinceEpoch,
    );
    _prefs?.setInt(_keyAppSessionEnd, 0);
    notifyListeners();
  }

  // ── Settings mutations ─────────────────────────────────────

  Future<void> setDailyLimitMinutes(int minutes) async {
    _dailyLimitSeconds = minutes * 60;
    await _prefs?.setInt(_keyDailyLimitSec, _dailyLimitSeconds);
    notifyListeners();
  }

  Future<void> setPerSessionMinutes(int minutes) async {
    _perSessionSeconds = minutes * 60;
    await _prefs?.setInt(_keyPerSessionSec, _perSessionSeconds);
    notifyListeners();
  }

  Future<void> setCooldownMinutes(int minutes) async {
    _cooldownSeconds = minutes * 60;
    await _prefs?.setInt(_keyCooldownSec, _cooldownSeconds);
    notifyListeners();
  }

  Future<void> resetDailyCounter() async {
    _dailyUsedSeconds = 0;
    await _prefs?.setInt(_keyDailyUsedSeconds, 0);
    if (_isSessionActive) endSession();
    notifyListeners();
  }

  @override
  void dispose() {
    _ticker?.cancel();
    super.dispose();
  }
}
