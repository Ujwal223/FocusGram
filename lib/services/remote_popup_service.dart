import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class RemotePopupData {
  final bool show;
  final String id;
  final String title;
  final String body;
  final int maxShows;
  final String buttonText;

  RemotePopupData({
    required this.show,
    required this.id,
    required this.title,
    required this.body,
    required this.maxShows,
    required this.buttonText,
  });

  factory RemotePopupData.fromJson(Map<String, dynamic> json) {
    return RemotePopupData(
      show: json['show'] ?? false,
      id: json['id']?.toString() ?? '',
      title: json['header']?.toString() ?? 'Notice',
      body: json['body']?.toString() ?? '',
      maxShows: json['max_shows'] ?? 1,
      buttonText: json['button_text']?.toString() ?? 'OK',
    );
  }
}

class RemotePopupService {
  // Keep placeholder value until you replace it.
  static const String popupUrl =
      'https://raw.githubusercontent.com/Ujwal223/FocusGram/refs/heads/main/android/popup.json';

  static Future<RemotePopupData?> fetchPopup() async {
    try {
      // Cache-busting to avoid stale popup configs from GitHub raw URLs.
      final uri = Uri.parse(
        '$popupUrl?t=${DateTime.now().millisecondsSinceEpoch}',
      );

      final response = await http.get(
        uri,
        headers: const {'Cache-Control': 'no-cache'},
      );

      if (response.statusCode != 200) return null;

      final decoded = jsonDecode(response.body);
      if (decoded is! Map<String, dynamic>) return null;

      return RemotePopupData.fromJson(decoded);
    } catch (_) {
      return null;
    }
  }

  static Future<bool> shouldShow(RemotePopupData data) async {
    if (!data.show) return false;
    if (data.id.isEmpty) return false;

    final prefs = await SharedPreferences.getInstance();
    final key = 'popup_count_${data.id}';
    final shownCount = prefs.getInt(key) ?? 0;

    return shownCount < data.maxShows;
  }

  static Future<void> markShown(RemotePopupData data) async {
    if (data.id.isEmpty) return;

    final prefs = await SharedPreferences.getInstance();
    final key = 'popup_count_${data.id}';
    final current = prefs.getInt(key) ?? 0;
    await prefs.setInt(key, current + 1);
  }
}
