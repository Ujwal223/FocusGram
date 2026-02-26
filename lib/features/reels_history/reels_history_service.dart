import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

class ReelsHistoryEntry {
  final String id;
  final String url;
  final String title;
  final String thumbnailUrl;
  final DateTime visitedAt;

  const ReelsHistoryEntry({
    required this.id,
    required this.url,
    required this.title,
    required this.thumbnailUrl,
    required this.visitedAt,
  });

  Map<String, dynamic> toJson() => {
        'id': id,
        'url': url,
        'title': title,
        'thumbnailUrl': thumbnailUrl,
        'visitedAt': visitedAt.toUtc().toIso8601String(),
      };

  static ReelsHistoryEntry fromJson(Map<String, dynamic> json) {
    return ReelsHistoryEntry(
      id: (json['id'] as String?) ?? '',
      url: (json['url'] as String?) ?? '',
      title: (json['title'] as String?) ?? 'Instagram Reel',
      thumbnailUrl: (json['thumbnailUrl'] as String?) ?? '',
      visitedAt: DateTime.tryParse((json['visitedAt'] as String?) ?? '') ??
          DateTime.now().toUtc(),
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

