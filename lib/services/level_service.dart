import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:hive/hive.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:intl/intl.dart';

/// Feature identifiers for level gating.
/// Every gated feature checks [LevelService.isFeatureUnlocked].
class AppFeature {
  final String id;
  final String name;
  final int requiredLevel;

  const AppFeature._(this.id, this.name, this.requiredLevel);

  static const effortFriction = AppFeature._(
    'effort_friction',
    'Effort Friction Mode',
    2,
  );
  static const reelsHistory = AppFeature._('reels_history', 'Reels History', 2);
  static const downloadMedia = AppFeature._(
    'download_media',
    'Download Media',
    2,
  );
  static const fullDmGhost = AppFeature._('full_dm_ghost', 'Full DM Ghost', 1);
  static const ghostMode = AppFeature._('ghost_mode', 'Ghost Mode', 2);
  static const baitMe = AppFeature._('bait_me', 'Bait Me Button', 3);
  static const appLock = AppFeature._('app_lock', 'App Lock', 3);
  static const customFriction = AppFeature._(
    'custom_friction',
    'Custom Friction Rules',
    4,
  );

  static const List<AppFeature> all = [
    effortFriction,
    downloadMedia,
    ghostMode,
    baitMe,
    appLock,
  ];
}

/// XP thresholds for each level.
/// Level 1 = 0 XP (always start here).
const Map<int, int> levelThresholds = {1: 0, 2: 100, 3: 250, 4: 450, 5: 700};

const int maxLevel = 5;

/// A single XP event — logged for the XP history view.
class _XpEvent {
  final int amount;
  final String reason;
  final DateTime time;
  _XpEvent(this.amount, this.reason, this.time);
}

/// Tracks XP, level progression, degradation, and monthly resets.
///
/// Always-on (not toggleable). All new features are gated behind levels.
///
/// **Storage:** Hive box `level_cache` (persistent local storage).
class LevelService extends ChangeNotifier {
  // ─── Hive box ──────────────────────────────────────────────
  static const String _hiveBox = 'level_cache';
  late Box _cache;

  // ─── Runtime state ─────────────────────────────────────────
  int _level = 1;
  int _xp = 0;
  DateTime? _lastResetDate;
  List<int> _dailyReelCounts = []; // last 30 days
  int _totalReelsAllTime = 0;
  int _adsWatchedTotal = 0;

  // Track today for daily reel logging
  int _todayReelCount = 0;
  String _todayKey = '';

  // ─── Getters ───────────────────────────────────────────────
  int get level => _level;
  int get xp => _xp;
  int get totalReelsAllTime => _totalReelsAllTime;
  int get adsWatchedTotal => _adsWatchedTotal;

  /// XP needed for the current level (cumulative threshold for this level).
  int get xpForCurrentLevel => levelThresholds[_level] ?? 0;

  /// XP needed to reach the next level (or current if at max).
  int get xpForNextLevel {
    if (_level >= maxLevel) return levelThresholds[maxLevel]!;
    return levelThresholds[_level + 1] ?? xpForCurrentLevel;
  }

  /// Progress 0.0–1.0 within the current level.
  double get levelProgress {
    final current = _xp - xpForCurrentLevel;
    final needed = xpForNextLevel - xpForCurrentLevel;
    if (needed <= 0) return 1.0;
    return (current / needed).clamp(0.0, 1.0);
  }

  /// Whether the user has reached (or exceeded) the required level.
  bool isFeatureUnlocked(AppFeature feature) => _level >= feature.requiredLevel;

  /// The next locked feature with level requirement — for "What's next?" display.
  AppFeature? get nextLockedFeature {
    for (final f in AppFeature.all) {
      if (!isFeatureUnlocked(f)) return f;
    }
    return null;
  }

  // ─── Initialization ────────────────────────────────────────
  Future<void> init() async {
    // 1. Open Hive cache box
    _cache = await Hive.openBox(_hiveBox);
    _loadFromCache();

    // 2. Set up today tracking
    _todayKey = DateFormat('yyyy-MM-dd').format(DateTime.now());

    // 3. Check monthly reset
    await _checkMonthlyReset();

    // 4. Check daily degradation
    await _checkDailyDegradation();

    notifyListeners();
  }

  void _loadFromCache() {
    try {
      _level = (_cache.get('level') ?? 1) as int;
      _xp = (_cache.get('xp') ?? 0) as int;
      final lastReset = _cache.get('lastResetDate') as String?;
      if (lastReset != null) {
        _lastResetDate = DateTime.tryParse(lastReset);
      }
      final countsRaw = _cache.get('dailyReelCounts') as String?;
      if (countsRaw != null) {
        _dailyReelCounts = (jsonDecode(countsRaw) as List).cast<int>();
      }
      _totalReelsAllTime = (_cache.get('totalReelsAllTime') ?? 0) as int;
      _adsWatchedTotal = (_cache.get('adsWatchedTotal') ?? 0) as int;
    } catch (_) {
      // Fall back to defaults
    }
  }

  Future<void> _saveToCache() async {
    await _cache.put('level', _level);
    await _cache.put('xp', _xp);
    await _cache.put('lastResetDate', _lastResetDate?.toIso8601String());
    await _cache.put('dailyReelCounts', jsonEncode(_dailyReelCounts));
    await _cache.put('totalReelsAllTime', _totalReelsAllTime);
    await _cache.put('adsWatchedTotal', _adsWatchedTotal);
  }

  // ─── XP History ────────────────────────────────────────────
  final List<_XpEvent> _xpHistory = [];

  List<_XpEvent> get xpHistory => List.unmodifiable(_xpHistory);

  /// Human-readable recent XP log for "Your Journey".
  List<Map<String, dynamic>> get recentXpLog {
    return _xpHistory.reversed
        .take(50)
        .map(
          (e) => {
            'amount': e.amount,
            'reason': e.reason,
            'time': e.time.toIso8601String(),
          },
        )
        .toList();
  }

  // ─── XP Earning ────────────────────────────────────────────
  static const int _dailyAdXpCap = 20;
  int _adsWatchedToday = 0;

  /// Call when a rewarded ad is completed.
  Future<void> addXpForAd() async {
    if (_adsWatchedToday >= _dailyAdXpCap) return; // Cap reached

    _adsWatchedToday++;
    _adsWatchedTotal++;
    await _awardXp(10, reason: 'Watched an ad');
  }

  /// Call when a session ends — awards XP for self-control.
  /// [reelsWatchedToday] = total reels watched so far today.
  Future<void> evaluateDailyReelControl(int reelsWatchedToday) async {
    // Calculate 7-day average
    final avg7 = _sevenDayAverage();
    if (avg7 <= 0) return; // Not enough data yet

    if (reelsWatchedToday < avg7) {
      // User watched fewer reels than average — award XP
      final reelsSaved = (avg7 - reelsWatchedToday).floor();
      final xpGain = min(reelsSaved * 10, 50); // Max +50 XP per day
      await _awardXp(xpGain, reason: 'Reduced reel count');
    }

    // Log today's count
    await _logDailyReelCount(reelsWatchedToday);
  }

  /// Call once per day when the user opens the app.
  Future<void> addDailyCheckinXp() async {
    final prefs = await SharedPreferences.getInstance();
    final today = DateFormat('yyyy-MM-dd').format(DateTime.now());
    final lastCheckin = prefs.getString('level_last_checkin') ?? '';
    if (lastCheckin == today) return; // Already checked in today

    await prefs.setString('level_last_checkin', today);
    await _awardXp(1, reason: 'Daily check-in');
  }

  /// Complete a full day under the daily reel limit.
  Future<void> awardDayUnderLimit() async {
    await _awardXp(15, reason: 'Day under limit');
  }

  Future<void> _awardXp(int amount, {String reason = 'general'}) async {
    _xp += amount;
    _xp = max(0, min(_xp, levelThresholds[maxLevel]!));

    // Log to history
    _xpHistory.add(_XpEvent(amount, reason, DateTime.now()));
    // Keep last 200 entries
    if (_xpHistory.length > 200) {
      _xpHistory.removeRange(0, _xpHistory.length - 200);
    }

    await _checkLevelUp();
    await _saveToCache();
    notifyListeners();
  }

  Future<void> _checkLevelUp() async {
    while (_level < maxLevel) {
      final nextThreshold = levelThresholds[_level + 1]!;
      if (_xp >= nextThreshold) {
        _level++;
        //debugPrint('🎉 Level up! Now Level $_level');
      } else {
        break;
      }
    }
  }

  // ─── XP Decay / Degradation ────────────────────────────────
  Future<void> _checkDailyDegradation() async {
    if (_dailyReelCounts.isEmpty) return;

    final avg7 = _sevenDayAverage();
    final allTimeAvg = _allTimeAverage();

    // Check if today's count (from yesterday, since this runs at startup)
    // exceeds both averages
    final yesterdayCount = _dailyReelCounts.isNotEmpty
        ? _dailyReelCounts.last
        : 0;

    if (yesterdayCount > avg7 && yesterdayCount > allTimeAvg && avg7 > 0) {
      // Deduct XP
      _xp = max(0, _xp - 20);
      notifyListeners();
    }

    // Check for level drop: exceeded app time limit 3 days in a row
    // (We check via a streak counter stored in prefs)
    await _checkLevelDropStreak();
  }

  Future<void> _checkLevelDropStreak() async {
    final prefs = await SharedPreferences.getInstance();
    final streakKey = 'level_drop_streak';
    int streak = prefs.getInt(streakKey) ?? 0;

    if (_dailyReelCounts.length >= 3) {
      final last3 = _dailyReelCounts.sublist(_dailyReelCounts.length - 3);
      final avg7 = _sevenDayAverage();
      final allExceeded = last3.every((c) => c > avg7 && avg7 > 0);

      if (allExceeded) {
        streak++;
        await prefs.setInt(streakKey, streak);
      } else {
        // Reset streak
        await prefs.setInt(streakKey, 0);
      }

      if (streak >= 3 && _level > 1) {
        // Drop one full level
        _level = max(1, _level - 1);
        // Also reduce XP to the threshold of the new level
        _xp = levelThresholds[_level]!;
        await prefs.setInt(streakKey, 0);
        //debugPrint('⚠️ Level dropped to $_level due to 3-day streak');
      }
    }

    await _saveToCache();
  }

  // ─── Monthly Reset ─────────────────────────────────────────
  Future<void> _checkMonthlyReset() async {
    if (_lastResetDate == null) {
      _lastResetDate = DateTime.now();
      return;
    }

    final daysSinceReset = DateTime.now().difference(_lastResetDate!).inDays;
    if (daysSinceReset >= 30) {
      _xp = 0; // Reset XP to 0
      // Level is preserved (loss aversion)
      _lastResetDate = DateTime.now();
      _dailyReelCounts = []; // Clear daily history
      _dailyReelCountsAddedToday = false;

      await _saveToCache();
      notifyListeners();

      // Show monthly summary (handled by the UI layer by checking a flag)
      _showMonthlySummary = true;
    }
  }

  /// Flag consumed by UI to show "New month, fresh start" screen.
  bool _showMonthlySummary = false;
  bool get showMonthlySummary => _showMonthlySummary;
  void dismissMonthlySummary() {
    _showMonthlySummary = false;
    notifyListeners();
  }

  // ─── Daily Reel Logging ────────────────────────────────────
  bool _dailyReelCountsAddedToday = false;

  Future<void> _logDailyReelCount(int reelCount) async {
    if (_dailyReelCountsAddedToday) return;

    _dailyReelCounts.add(reelCount);
    _totalReelsAllTime += reelCount;

    // Keep only last 30 days
    if (_dailyReelCounts.length > 30) {
      _dailyReelCounts.removeRange(0, _dailyReelCounts.length - 30);
    }

    _dailyReelCountsAddedToday = true;
    await _saveToCache();
  }

  double _sevenDayAverage() {
    if (_dailyReelCounts.isEmpty) return 0;
    final recent = _dailyReelCounts.length >= 7
        ? _dailyReelCounts.sublist(_dailyReelCounts.length - 7)
        : _dailyReelCounts;
    final sum = recent.fold<int>(0, (a, b) => a + b);
    return sum / recent.length;
  }

  double _allTimeAverage() {
    if (_dailyReelCounts.isEmpty) return 0;
    final sum = _dailyReelCounts.fold<int>(0, (a, b) => a + b);
    return sum / _dailyReelCounts.length;
  }

  /// Call this at the end of each day to award "day under limit" XP.
  Future<void> finalizeDay(
    int reelsWatchedToday,
    int dailyReelLimitMinutes,
  ) async {
    final dailyReelCount = reelsWatchedToday; // in minutes
    if (dailyReelCount <= dailyReelLimitMinutes) {
      await awardDayUnderLimit();
    }
  }

  /// Reset the daily ad counter (call at midnight).
  void resetDailyAdCounter() {
    _adsWatchedToday = 0;
  }

  /*/// Grant XP with a custom reason (used from the debug section in settings).
  Future<void> grantDebugXp(int amount, String reason) async {
    await _awardXp(amount, reason: reason);
  }

  // ─── Debug Methods ─────────────────────────────────────────
  /// Force-set level and XP (debug only).
  Future<void> debugSetLevel(int level, int xp) async {
    _level = level.clamp(1, maxLevel);
    _xp = xp.clamp(0, levelThresholds[maxLevel]!);
    await _saveToCache();
    notifyListeners();
  }

  /// Reset all level data (debug only).
  Future<void> debugReset() async {
    _level = 1;
    _xp = 0;
    _dailyReelCounts = [];
    _totalReelsAllTime = 0;
    _adsWatchedTotal = 0;
    _adsWatchedToday = 0;
    _lastResetDate = DateTime.now();
    _dailyReelCountsAddedToday = false;
    await _saveToCache();
    notifyListeners();
  }*/
}
