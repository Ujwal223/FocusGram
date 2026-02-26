import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Tracks total in-app screen time per day.
///
/// Storage format (in SharedPreferences, key `screen_time_data`):
///   {
///     "2026-02-26": 3420, // seconds
///     "2026-02-25": 1800
///   }
///
/// All data stays on-device only.
class ScreenTimeService extends ChangeNotifier {
  static const String prefKey = 'screen_time_data';

  SharedPreferences? _prefs;
  Map<String, int> _secondsByDate = {};
  Timer? _ticker;
  bool _tracking = false;

  Map<String, int> get secondsByDate => Map.unmodifiable(_secondsByDate);

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    _load();
  }

  void _load() {
    final raw = _prefs?.getString(prefKey);
    if (raw == null || raw.isEmpty) {
      _secondsByDate = {};
      return;
    }
    try {
      final decoded = jsonDecode(raw);
      if (decoded is Map<String, dynamic>) {
        _secondsByDate = decoded.map(
          (k, v) => MapEntry(k, (v as num).toInt()),
        );
      }
    } catch (_) {
      _secondsByDate = {};
    }
  }

  Future<void> _save() async {
    // Prune entries older than 30 days
    final now = DateTime.now();
    final cutoff = now.subtract(const Duration(days: 30));
    _secondsByDate.removeWhere((key, value) {
      try {
        final d = DateTime.parse(key);
        return d.isBefore(DateTime(cutoff.year, cutoff.month, cutoff.day));
      } catch (_) {
        return true;
      }
    });

    await _prefs?.setString(prefKey, jsonEncode(_secondsByDate));
    notifyListeners();
  }

  String _todayKey() {
    final now = DateTime.now();
    return '${now.year.toString().padLeft(4, '0')}-'
        '${now.month.toString().padLeft(2, '0')}-'
        '${now.day.toString().padLeft(2, '0')}';
  }

  void startTracking() {
    if (_tracking) return;
    _tracking = true;
    _ticker ??= Timer.periodic(const Duration(seconds: 1), (_) {
      if (!_tracking) return;
      final key = _todayKey();
      _secondsByDate[key] = (_secondsByDate[key] ?? 0) + 1;
      // Persist every 10 seconds to reduce writes.
      if (_secondsByDate[key]! % 10 == 0) {
        _save();
      } else {
        notifyListeners();
      }
    });
  }

  void stopTracking() {
    if (!_tracking) return;
    _tracking = false;
    _save();
  }

  Future<void> resetAll() async {
    _secondsByDate.clear();
    await _prefs?.remove(prefKey);
    notifyListeners();
  }

  @override
  void dispose() {
    _ticker?.cancel();
    super.dispose();
  }
}

