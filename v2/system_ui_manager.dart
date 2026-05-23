import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class SystemUiManager {
  // ── Apply colors read from JS ThemeDetector ─────────────────────────────
  static void applyFromThemePayload(String jsonPayload) {
    try {
      final data = jsonDecode(jsonPayload) as Map<String, dynamic>;
      final isDark = data['isDark'] as bool? ?? false;
      final bodyHex = data['bodyHex'] as String? ?? (isDark ? '#000000' : '#ffffff');
      final navHex = data['navHex'] as String? ?? bodyHex;

      final bodyColor = _parseHex(bodyHex);
      final navColor = _parseHex(navHex);

      SystemChrome.setSystemUIOverlayStyle(
        SystemUiOverlayStyle(
          statusBarColor: bodyColor,
          statusBarIconBrightness: isDark ? Brightness.light : Brightness.dark,
          statusBarBrightness: isDark ? Brightness.dark : Brightness.light,
          systemNavigationBarColor: navColor,
          systemNavigationBarIconBrightness:
              isDark ? Brightness.light : Brightness.dark,
          systemNavigationBarDividerColor: Colors.transparent,
        ),
      );
    } catch (_) {
      // Fallback to safe defaults
      applyLight();
    }
  }

  // ── Fallback presets ─────────────────────────────────────────────────────
  static void applyLight() {
    SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
      statusBarColor: Color(0xFFFFFFFF),
      statusBarIconBrightness: Brightness.dark,
      statusBarBrightness: Brightness.light,
      systemNavigationBarColor: Color(0xFFFFFFFF),
      systemNavigationBarIconBrightness: Brightness.dark,
      systemNavigationBarDividerColor: Colors.transparent,
    ));
  }

  static void applyDark() {
    SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
      statusBarColor: Color(0xFF000000),
      statusBarIconBrightness: Brightness.light,
      statusBarBrightness: Brightness.dark,
      systemNavigationBarColor: Color(0xFF000000),
      systemNavigationBarIconBrightness: Brightness.light,
      systemNavigationBarDividerColor: Colors.transparent,
    ));
  }

  // ── Edge-to-edge setup — call once in main() ─────────────────────────────
  static Future<void> enableEdgeToEdge() async {
    await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
    applyLight(); // default until theme detector fires
  }

  // ─────────────────────────────────────────────────────────────────────────
  static Color _parseHex(String hex) {
    final clean = hex.replaceAll('#', '');
    if (clean.length == 6) {
      return Color(int.parse('FF$clean', radix: 16));
    } else if (clean.length == 8) {
      return Color(int.parse(clean, radix: 16));
    }
    return Colors.white;
  }
}
