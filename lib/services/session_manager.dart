import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'notification_service.dart';

class FocusSchedule {
  final int startHour;
  final int startMinute;
  final int endHour;
  final int endMinute;

  FocusSchedule({
    required this.startHour,
    required this.startMinute,
    required this.endHour,
    required this.endMinute,
  });

  Map<String, dynamic> toJson() => {
    'startH': startHour,
    'startM': startMinute,
    'endH': endHour,
    'endM': endMinute,
  };

  factory FocusSchedule.fromJson(Map<String, dynamic> json) => FocusSchedule(
    startHour: json['startH'] as int,
    startMinute: json['startM'] as int,
    endHour: json['endH'] as int,
    endMinute: json['endM'] as int,
  );
}

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
  static const _keyScheduleEnabled = 'sched_enabled';
  static const _keyScheduleStartHour = 'sched_start_h';
  static const _keyScheduleStartMin = 'sched_start_m';
  static const _keyScheduleEndHour = 'sched_end_h';
  static const _keyScheduleEndMin = 'sched_end_m';
  static const _keySchedulesJson = 'sched_list_json';

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

  // ── Scheduled Blocking runtime ─────────────────────────────
  bool _scheduleEnabled = false;
  int _schedStartHour = 22; // Default 10 PM
  int _schedStartMin = 0;
  int _schedEndHour = 7;
  int _schedEndMin = 0;
  List<FocusSchedule> _schedules = [];
  bool _lastScheduleState = false;

  bool _isInForeground = true; // Tracking app lifecycle state
  int _cachedRemainingSessionSeconds = 0;
  int _cachedRemainingAppSessionSeconds = 0;

  // ── Settings defaults ──────────────────────────────────────
  int _dailyLimitSeconds = 30 * 60; // 30 min
  int _perSessionSeconds = 5 * 60; // 5 min
  int _cooldownSeconds = 15 * 60; // 15 min cooldown between reel sessions

  // ── Public getters — Reel session ─────────────────────────
  bool get isSessionActive => _isSessionActive;

  int get remainingSessionSeconds {
    if (!_isSessionActive || _sessionExpiry == null) return 0;
    // If not in foreground, the clock "freezes" visually too (or we could shift the expiry)
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
  DateTime? get lastSessionEnd => _lastSessionEnd;

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

  // ── Scheduled Blocking Getters ─────────────────────────────
  bool get scheduleEnabled => _scheduleEnabled;
  int get schedStartHour => _schedStartHour;
  int get schedStartMin => _schedStartMin;
  int get schedEndHour => _schedEndHour;
  int get schedEndMin => _schedEndMin;
  List<FocusSchedule> get schedules => _schedules;

  bool get isScheduledBlockActive {
    if (!_scheduleEnabled) return false;
    final now = DateTime.now();
    final currentTime = now.hour * 60 + now.minute;

    for (final s in _schedules) {
      final startTime = s.startHour * 60 + s.startMinute;
      final endTime = s.endHour * 60 + s.endMinute;

      if (startTime < endTime) {
        // Simple range (e.g., 9:00 to 17:00)
        if (currentTime >= startTime && currentTime < endTime) return true;
      } else {
        // Over-midnight range (e.g., 22:00 to 07:00)
        if (currentTime >= startTime || currentTime < endTime) return true;
      }
    }
    return false;
  }

  String? get activeScheduleText {
    if (!isScheduledBlockActive) return null;
    final now = DateTime.now();
    final currentTime = now.hour * 60 + now.minute;

    for (final s in _schedules) {
      final startTime = s.startHour * 60 + s.startMinute;
      final endTime = s.endHour * 60 + s.endMinute;

      bool active = false;
      if (startTime < endTime) {
        if (currentTime >= startTime && currentTime < endTime) active = true;
      } else {
        if (currentTime >= startTime || currentTime < endTime) active = true;
      }
      if (active) {
        return '${formatTime12h(s.startHour, s.startMinute)} to ${formatTime12h(s.endHour, s.endMinute)}';
      }
    }
    return null;
  }

  String formatTime12h(int h, int m) {
    var hour = h % 12;
    if (hour == 0) hour = 12;
    final period = h >= 12 ? 'PM' : 'AM';
    final min = m.toString().padLeft(2, '0');
    return '$hour:$min $period';
  }

  // ── Initialization ─────────────────────────────────────────
  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    await _resetDailyIfNeeded();
    _loadPersisted();
    _lastScheduleState = isScheduledBlockActive;
    _startTicker();
    _incrementOpenCount();
  }

  void setAppForeground(bool v) {
    if (_isInForeground == v) return;
    _isInForeground = v;

    if (v) {
      // Returning to foreground: resume sessions by shifting expiry
      final now = DateTime.now();
      if (_isSessionActive) {
        _sessionExpiry = now.add(
          Duration(seconds: _cachedRemainingSessionSeconds),
        );
      }
      if (_appSessionEnd != null) {
        _appSessionEnd = now.add(
          Duration(seconds: _cachedRemainingAppSessionSeconds),
        );
      }
    } else {
      // Entering background: cache remaining time
      _cachedRemainingSessionSeconds = remainingSessionSeconds;
      _cachedRemainingAppSessionSeconds = appSessionRemainingSeconds;
    }
    notifyListeners();
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

    _scheduleEnabled = _prefs!.getBool(_keyScheduleEnabled) ?? false;
    _schedStartHour = _prefs!.getInt(_keyScheduleStartHour) ?? 22;
    _schedStartMin = _prefs!.getInt(_keyScheduleStartMin) ?? 0;
    _schedEndHour = _prefs!.getInt(_keyScheduleEndHour) ?? 7;
    _schedEndMin = _prefs!.getInt(_keyScheduleEndMin) ?? 0;

    final schedJson = _prefs!.getString(_keySchedulesJson);
    if (schedJson != null) {
      final List decode = jsonDecode(schedJson);
      _schedules = decode.map((m) => FocusSchedule.fromJson(m)).toList();
    } else {
      // Migrate old single schedule if it exists
      _schedules = [
        FocusSchedule(
          startHour: _schedStartHour,
          startMinute: _schedStartMin,
          endHour: _schedEndHour,
          endMinute: _schedEndMin,
        ),
      ];
      _saveSchedulesToPrefs();
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
    if (!_isInForeground) return; // Freeze everything when in background

    bool changed = false;

    // Reel session countdown
    if (_isSessionActive) {
      // Recalculate expiry every tick to "pause" it while backgrounded:
      // We don't change _sessionExpiry, but we increment _dailyUsedSeconds.
      // If we want it to actually pause, we should probably store "remaining seconds"
      // and update expiry ONLY when in foreground.

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
    if (_appSessionEnd != null && !_appSessionExpiredFlag) {
      if (DateTime.now().isAfter(_appSessionEnd!)) {
        _appSessionExpiredFlag = true;
        changed = true;
      }
    }

    if (isCooldownActive) {
      changed = true;
    } else if (appOpenCooldownRemainingSeconds <= 0 &&
        _lastAppSessionEnd != null) {
      // Just expired
      changed = true;
    }

    // Schedule check
    final sched = isScheduledBlockActive;
    if (sched != _lastScheduleState) {
      _lastScheduleState = sched;
      changed = true;
    }

    if (changed) notifyListeners();
  }

  void _cleanupExpiredReelSession() {
    _isSessionActive = false;
    _sessionExpiry = null;
    _lastSessionEnd = DateTime.now();
    _prefs?.setInt(_keySessionExpiry, 0);
    _prefs?.setInt(_keyLastSessionEnd, _lastSessionEnd!.millisecondsSinceEpoch);

    // Alert User
    NotificationService().showNotification(
      id: 999,
      title: 'Session Ended',
      body: 'Your Reel session has expired. Time to focus!',
    );
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

  Future<void> setScheduleEnabled(bool v) async {
    _scheduleEnabled = v;
    await _prefs?.setBool(_keyScheduleEnabled, v);
    notifyListeners();
  }

  Future<void> setScheduleTime({
    required int startH,
    required int startM,
    required int endH,
    required int endM,
  }) async {
    _schedEndHour = endH;
    _schedEndMin = endM;
    // Update the first schedule for compatibility? Or just replace all?
    // Let's replace all schedules with this one if this method is called.
    _schedules = [
      FocusSchedule(
        startHour: startH,
        startMinute: startM,
        endHour: endH,
        endMinute: endM,
      ),
    ];
    await _prefs?.setInt(_keyScheduleStartHour, startH);
    await _prefs?.setInt(_keyScheduleStartMin, startM);
    await _prefs?.setInt(_keyScheduleEndHour, endH);
    await _prefs?.setInt(_keyScheduleEndMin, endM);
    await _saveSchedulesToPrefs();
    notifyListeners();
  }

  Future<void> _saveSchedulesToPrefs() async {
    final json = jsonEncode(_schedules.map((s) => s.toJson()).toList());
    await _prefs?.setString(_keySchedulesJson, json);
  }

  Future<void> addSchedule(FocusSchedule s) async {
    _schedules.add(s);
    await _saveSchedulesToPrefs();
    notifyListeners();
  }

  Future<void> removeScheduleAt(int index) async {
    if (index >= 0 && index < _schedules.length) {
      _schedules.removeAt(index);
      await _saveSchedulesToPrefs();
      notifyListeners();
    }
  }

  Future<void> updateScheduleAt(int index, FocusSchedule s) async {
    if (index >= 0 && index < _schedules.length) {
      _schedules[index] = s;
      await _saveSchedulesToPrefs();
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _ticker?.cancel();
    super.dispose();
  }
}
