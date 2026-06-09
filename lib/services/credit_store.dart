import 'package:flutter/foundation.dart';
import 'package:hive/hive.dart';

/// Manages time credit balances earned by watching rewarded ads.
///
/// Two balances: [reelsMinutesRemaining] for reel sessions and
/// [instaMinutesRemaining] for Instagram app sessions.
///
/// Also tracks ad watch counts for the Ad Counter dashboard (Phase 5).
class CreditStore extends ChangeNotifier {
  static const String _boxName = 'credit_store';

  late Box _box;

  // ─── Balances ──────────────────────────────────────────────
  int _reelsMinutes = 0;
  int _instaMinutes = 0;

  // ─── Ad counters ───────────────────────────────────────────
  int _adsWatchedToday = 0;
  int _adsWatchedAllTime = 0;
  String _todayKey = '';

  // ─── Gettters ──────────────────────────────────────────────
  int get reelsMinutes => _reelsMinutes;
  int get instaMinutes => _instaMinutes;
  int get adsWatchedToday => _adsWatchedToday;
  int get adsWatchedAllTime => _adsWatchedAllTime;
  int get timeEarnedViaAds => (_adsWatchedAllTime * minutesPerAd);

  bool get hasReelsCredits => _reelsMinutes > 0;
  bool get hasInstaCredits => _instaMinutes > 0;
  bool get canWatchAdToday => _adsWatchedToday < maxDailyAds;

  /// Minutes earned per rewarded ad watch.
  static const int minutesPerAd = 2;
  static const int maxDailyAds = 5;

  // ─── Init ──────────────────────────────────────────────────
  Future<void> init() async {
    _box = await Hive.openBox(_boxName);
    _reelsMinutes = (_box.get('reels_min', defaultValue: 0) as num).toInt();
    _instaMinutes = (_box.get('insta_min', defaultValue: 0) as num).toInt();
    _adsWatchedAllTime = (_box.get('ads_all_time', defaultValue: 0) as num)
        .toInt();
    _todayKey = _dayKey();

    // Restore today's count, reset if date changed
    final savedDate = _box.get('ads_today_date', defaultValue: '') as String;
    if (savedDate == _todayKey) {
      _adsWatchedToday = (_box.get('ads_today_count', defaultValue: 0) as num)
          .toInt();
    } else {
      _adsWatchedToday = 0;
      _box.put('ads_today_date', _todayKey);
      _box.put('ads_today_count', 0);
    }
  }

  // ─── Credit operations ─────────────────────────────────────
  /// Add minutes earned from watching an ad.
  Future<void> addReelsMinutes({int amount = minutesPerAd}) async {
    _reelsMinutes += amount;
    await _box.put('reels_min', _reelsMinutes);
    _incrementAdCounters();
    notifyListeners();
  }

  Future<void> addInstaMinutes({int amount = minutesPerAd}) async {
    _instaMinutes += amount;
    await _box.put('insta_min', _instaMinutes);
    _incrementAdCounters();
    notifyListeners();
  }

  /// Drain 1 minute from the reel balance (called every minute during a session).
  Future<void> drainReelsMinute() async {
    if (_reelsMinutes <= 0) return;
    _reelsMinutes--;
    await _box.put('reels_min', _reelsMinutes);
    notifyListeners();
  }

  /// Drain 1 minute from the Instagram balance.
  Future<void> drainInstaMinute() async {
    if (_instaMinutes <= 0) return;
    _instaMinutes--;
    await _box.put('insta_min', _instaMinutes);
    notifyListeners();
  }

  /// Reset all balances (e.g. on settings toggle off).
  Future<void> resetBalances() async {
    _reelsMinutes = 0;
    _instaMinutes = 0;
    await _box.put('reels_min', 0);
    await _box.put('insta_min', 0);
    notifyListeners();
  }

  /// Add minutes directly from the Bait Me feature.
  Future<void> addBonusMinutes(int minutes) async {
    // Add to reels balance (bait me rewards are for reels)
    _reelsMinutes += minutes;
    await _box.put('reels_min', _reelsMinutes);
    notifyListeners();
  }

  // ─── Ad counter helpers ────────────────────────────────────
  void _incrementAdCounters() {
    _adsWatchedToday++;
    _adsWatchedAllTime++;
    _box.put('ads_today_date', _todayKey);
    _box.put('ads_today_count', _adsWatchedToday);
    _box.put('ads_all_time', _adsWatchedAllTime);
  }

  /// Reset daily ad counter (call on day change).
  Future<void> resetDailyIfNeeded() async {
    final newKey = _dayKey();
    if (newKey != _todayKey) {
      _todayKey = newKey;
      _adsWatchedToday = 0;
      await _box.put('ads_today_date', _todayKey);
      await _box.put('ads_today_count', 0);
      notifyListeners();
    }
  }

  String _dayKey() {
    final now = DateTime.now();
    return '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
  }
}
