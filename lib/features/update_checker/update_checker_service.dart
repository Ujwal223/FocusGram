import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

class UpdateInfo {
  final String latestVersion; // e.g. "1.0.0"
  final String releaseUrl; // html_url
  final String whatsNew; // trimmed body
  final bool isUpdateAvailable;

  const UpdateInfo({
    required this.latestVersion,
    required this.releaseUrl,
    required this.whatsNew,
    required this.isUpdateAvailable,
  });
}

class UpdateCheckerService extends ChangeNotifier {
  static const String _lastDismissedKey = 'last_dismissed_update_version';
  static const String _githubUrl =
      'https://api.github.com/repos/Ujwal223/FocusGram/releases/latest';

  UpdateInfo? _updateInfo;
  bool _isDismissed = false;

  bool get hasUpdate => _updateInfo != null && !_isDismissed;
  UpdateInfo? get updateInfo => hasUpdate ? _updateInfo : null;

  Future<void> checkForUpdates() async {
    try {
      final response = await http
          .get(Uri.parse(_githubUrl))
          .timeout(const Duration(seconds: 5));
      if (response.statusCode != 200) return;

      final data = json.decode(response.body);
      final String gitVersionTag =
          data['tag_name'] ?? ''; // e.g. "v0.9.8-beta.2"
      final String htmlUrl = data['html_url'] ?? '';
      final String body = (data['body'] as String?) ?? '';

      if (gitVersionTag.isEmpty || htmlUrl.isEmpty) return;

      final packageInfo = await PackageInfo.fromPlatform();
      final currentVersion = packageInfo.version; // e.g. "0.9.8-beta.2"

      if (!_isNewerVersion(gitVersionTag, currentVersion)) return;

      final prefs = await SharedPreferences.getInstance();
      final dismissedVersion = prefs.getString(_lastDismissedKey);
      if (dismissedVersion == gitVersionTag) {
        _isDismissed = true;
        return;
      }

      final cleanVersion =
          gitVersionTag.startsWith('v') ? gitVersionTag.substring(1) : gitVersionTag;

      var trimmed = body.trim();
      if (trimmed.length > 1500) {
        trimmed = trimmed.substring(0, 1500).trim();
      }

      _updateInfo = UpdateInfo(
        latestVersion: cleanVersion,
        releaseUrl: htmlUrl,
        whatsNew: trimmed,
        isUpdateAvailable: true,
      );
      _isDismissed = false;
      notifyListeners();
    } catch (e) {
      debugPrint('Update check failed: $e');
    }
  }

  Future<void> dismissUpdate() async {
    if (_updateInfo == null) return;
    _isDismissed = true;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_lastDismissedKey, _updateInfo!.latestVersion);
    notifyListeners();
  }

  bool _isNewerVersion(String gitTag, String current) {
    // Clean versions: strip 'v' and everything after '-' (beta/rc)
    String cleanGit = gitTag.startsWith('v') ? gitTag.substring(1) : gitTag;
    String cleanCurrent = current;

    List<String> gitParts = cleanGit.split('-')[0].split('.');
    List<String> currentParts = cleanCurrent.split('-')[0].split('.');

    for (int i = 0; i < gitParts.length && i < currentParts.length; i++) {
      int gitNum = int.tryParse(gitParts[i]) ?? 0;
      int curNum = int.tryParse(currentParts[i]) ?? 0;
      if (gitNum > curNum) return true;
      if (gitNum < curNum) return false;
    }

    return false;
  }
}
