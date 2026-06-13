import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

class ReelsHistoryEntry {
  final String id;
  final String url;
  final String title;
  final String thumbnailUrl;
  final DateTime visitedAt;
  final int durationSeconds; // How long the session lasted
  final int adsWatchedInSession; // How many ads watched during this session

  const ReelsHistoryEntry({
    required this.id,
    required this.url,
    required this.title,
    required this.thumbnailUrl,
    required this.visitedAt,
    this.durationSeconds = 0,
    this.adsWatchedInSession = 0,
  });

  Map<String, dynamic> toJson() => {
    'id': id,
    'url': url,
    'title': title,
    'thumbnailUrl': thumbnailUrl,
    'visitedAt': visitedAt.toUtc().toIso8601String(),
    'durationSeconds': durationSeconds,
    'adsWatchedInSession': adsWatchedInSession,
  };

  static ReelsHistoryEntry fromJson(Map<String, dynamic> json) {
    return ReelsHistoryEntry(
      id: (json['id'] as String?) ?? '',
      url: (json['url'] as String?) ?? '',
      title: (json['title'] as String?) ?? 'Instagram Reel',
      thumbnailUrl: (json['thumbnailUrl'] as String?) ?? '',
      visitedAt:
          DateTime.tryParse((json['visitedAt'] as String?) ?? '') ??
          DateTime.now().toUtc(),
      durationSeconds: (json['durationSeconds'] as num?)?.toInt() ?? 0,
      adsWatchedInSession: (json['adsWatchedInSession'] as num?)?.toInt() ?? 0,
    );
  }
}

class ReelsHistoryService {
  static const String _prefsKey = 'reels_history';
  static const int _maxEntries = 200;

  SharedPreferences? _prefs;

  Future<SharedPreferences> _getPrefs() async {
    _prefs ??= await SharedPreferences.getInstance();
    return _prefs!;
  }

  Future<List<ReelsHistoryEntry>> getEntries() async {
    final prefs = await _getPrefs();
    final raw = prefs.getString(_prefsKey);
    if (raw == null || raw.isEmpty) return [];
    try {
      final decoded = jsonDecode(raw) as List<dynamic>;
      final entries = decoded
          .whereType<Map>()
          .map((e) => ReelsHistoryEntry.fromJson(e.cast<String, dynamic>()))
          .where((e) => e.url.isNotEmpty)
          .toList();
      entries.sort((a, b) => b.visitedAt.compareTo(a.visitedAt));
      return entries;
    } catch (_) {
      return [];
    }
  }

  Future<void> addEntry({
    required String url,
    required String title,
    required String thumbnailUrl,
    int durationSeconds = 0,
    int adsWatchedInSession = 0,
  }) async {
    if (url.isEmpty) return;
    final now = DateTime.now().toUtc();

    final entries = await getEntries();
    final recentDuplicate = entries.any((e) {
      if (e.url != url) return false;
      final diff = now.difference(e.visitedAt).inSeconds.abs();
      return diff <= 60;
    });
    if (recentDuplicate) return;

    final entry = ReelsHistoryEntry(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      url: url,
      title: title.isEmpty ? 'Instagram Reel' : title,
      thumbnailUrl: thumbnailUrl,
      visitedAt: now,
      durationSeconds: durationSeconds,
      adsWatchedInSession: adsWatchedInSession,
    );

    final updated = [entry, ...entries];
    if (updated.length > _maxEntries) {
      updated.removeRange(_maxEntries, updated.length);
    }
    await _save(updated);
  }

  Future<void> deleteEntry(String id) async {
    final entries = await getEntries();
    entries.removeWhere((e) => e.id == id);
    await _save(entries);
  }

  /// Get average reels watched per day in the last 7 days.
  Future<double> getWeeklyAverageReels() async {
    final entries = await getEntries();
    if (entries.isEmpty) return 0;

    final now = DateTime.now();
    final sevenDaysAgo = now.subtract(const Duration(days: 7));
    final recent = entries
        .where((e) => e.visitedAt.isAfter(sevenDaysAgo))
        .toList();

    if (recent.isEmpty) return 0;
    return recent.length / 7.0;
  }

  /// Get reel counts grouped by day (for the level system).
  Future<Map<String, int>> getDailyReelCounts({int days = 30}) async {
    final entries = await getEntries();
    final now = DateTime.now();
    final cutoff = now.subtract(Duration(days: days));
    final recent = entries.where((e) => e.visitedAt.isAfter(cutoff)).toList();

    final Map<String, int> counts = {};
    for (final entry in recent) {
      final dayKey =
          '${entry.visitedAt.year}-'
          '${entry.visitedAt.month.toString().padLeft(2, '0')}-'
          '${entry.visitedAt.day.toString().padLeft(2, '0')}';
      counts[dayKey] = (counts[dayKey] ?? 0) + 1;
    }
    return counts;
  }

  /// Get total reels watched in the last [days] days.
  Future<int> getRecentReelCount({int days = 7}) async {
    final entries = await getEntries();
    final now = DateTime.now();
    final cutoff = now.subtract(Duration(days: days));
    return entries.where((e) => e.visitedAt.isAfter(cutoff)).length;
  }

  Future<void> clearAll() async {
    final prefs = await _getPrefs();
    await prefs.remove(_prefsKey);
  }

  Future<void> _save(List<ReelsHistoryEntry> entries) async {
    final prefs = await _getPrefs();
    final jsonList = entries.map((e) => e.toJson()).toList();
    await prefs.setString(_prefsKey, jsonEncode(jsonList));
  }
}
