import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:hive/hive.dart';

/// A saved page that can be viewed offline via WebView cache.
/// No API calls — just bookmarks URLs you've already visited
/// so the WebView's built-in cache (`LOAD_CACHE_ELSE_NETWORK`)
/// can serve them when offline.
class SavedPage {
  final String id;
  final String url;
  final String title;
  final DateTime savedAt;
  final String? htmlContent; // captured page HTML for offline viewing

  const SavedPage({
    required this.id,
    required this.url,
    required this.title,
    required this.savedAt,
    this.htmlContent,
  });

  Map<String, dynamic> toJson() => {
    'id': id,
    'url': url,
    'title': title,
    'savedAt': savedAt.toIso8601String(),
    if (htmlContent != null) 'html': htmlContent,
  };

  factory SavedPage.fromJson(Map<String, dynamic> json) => SavedPage(
    id: json['id'] as String? ?? '',
    url: json['url'] as String? ?? '',
    title: json['title'] as String? ?? 'Instagram',
    savedAt:
        DateTime.tryParse(json['savedAt'] as String? ?? '') ?? DateTime.now(),
    htmlContent: json['html'] as String?,
  );
}

/// Manages saved pages for offline viewing.
///
/// How it works:
/// 1. The WebView already has `cacheMode: LOAD_CACHE_ELSE_NETWORK`
/// 2. When you visit a page online, the WebView caches it automatically
/// 3. This service just bookmarks URLs so you can navigate to them offline
/// 4. The WebView serves the cached version when there's no internet
///
/// No Instagram API needed. No content downloading. Just cache + bookmarks.
class SnapshotService extends ChangeNotifier {
  static const String _hiveBox = 'saved_pages';

  late Box _box;
  List<SavedPage> _savedPages = [];

  List<SavedPage> get savedPages => List.unmodifiable(_savedPages);
  int get totalSaved => _savedPages.length;

  Future<void> init() async {
    _box = await Hive.openBox(_hiveBox);
    _loadFromCache();
  }

  void _loadFromCache() {
    try {
      final raw = _box.get('page_list') as String?;
      if (raw != null) {
        final decoded = jsonDecode(raw) as List;
        _savedPages =
            decoded
                .map((e) => SavedPage.fromJson(e as Map<String, dynamic>))
                .toList()
              ..sort((a, b) => b.savedAt.compareTo(a.savedAt));
      }
    } catch (_) {}
  }

  Future<void> _saveToCache() async {
    final json = jsonEncode(_savedPages.map((e) => e.toJson()).toList());
    await _box.put('page_list', json);
  }

  /// Save a page. Optionally pass [htmlContent] captured from the WebView.
  Future<void> savePage(
    String url, {
    String title = 'Instagram',
    String? htmlContent,
  }) async {
    if (url.isEmpty) return;
    // Avoid duplicates
    if (_savedPages.any((p) => p.url == url)) return;

    final page = SavedPage(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      url: url,
      title: title,
      savedAt: DateTime.now(),
      htmlContent: htmlContent,
    );
    _savedPages.insert(0, page);
    await _saveToCache();
    notifyListeners();
  }

  /// Remove a saved page.
  Future<void> deletePage(String id) async {
    _savedPages.removeWhere((p) => p.id == id);
    await _saveToCache();
    notifyListeners();
  }

  /// Remove all saved pages.
  Future<void> deleteAll() async {
    _savedPages.clear();
    await _saveToCache();
    notifyListeners();
  }

  /// Get the total count.
  int get count => _savedPages.length;
}
