import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import '../theme/system_ui_manager.dart';

typedef ActivityCallback = void Function(Map<String, dynamic> event);

class ChannelRegistry {
  final ActivityCallback? onActivityEvent;

  const ChannelRegistry({this.onActivityEvent});

  // ── Build all JavaScript channels ─────────────────────────────────────────
  Set<JavaScriptChannel> build() {
    return {
      _ghostChannel(),
      _themeChannel(),
      _contentChannel(),
      _activityChannel(),
    };
  }

  // ─────────────────────────────────────────────────────────────────────────

  JavaScriptChannel _ghostChannel() => JavaScriptChannel(
    name: 'GhostChannel',
    onMessageReceived: (msg) {
      try {
        final data = jsonDecode(msg.message) as Map<String, dynamic>;
        if (kDebugMode) {
          debugPrint('[Ghost] ${data['type']} — ${data['url'] ?? ''}');
        }
        // In release: silent. Could surface to a debug overlay in dev builds.
      } catch (_) {}
    },
  );

  JavaScriptChannel _themeChannel() => JavaScriptChannel(
    name: 'ThemeChannel',
    onMessageReceived: (msg) {
      SystemUiManager.applyFromThemePayload(msg.message);
    },
  );

  JavaScriptChannel _contentChannel() => JavaScriptChannel(
    name: 'ContentChannel',
    onMessageReceived: (msg) {
      // 'ready' signal — engine pushes flags back via evaluateJavascript
      // handled in ScriptEngine.injectDocumentEndScripts()
      if (kDebugMode) debugPrint('[Content] ${msg.message}');
    },
  );

  JavaScriptChannel _activityChannel() => JavaScriptChannel(
    name: 'ActivityChannel',
    onMessageReceived: (msg) {
      try {
        final data = jsonDecode(msg.message) as Map<String, dynamic>;
        onActivityEvent?.call(data);
      } catch (_) {}
    },
  );
}
