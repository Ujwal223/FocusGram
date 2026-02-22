import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Stores and retrieves all user-configurable app settings.
class SettingsService extends ChangeNotifier {
  static const _keyBlurExplore = 'set_blur_explore';
  static const _keyBlurReels = 'set_blur_reels';
  static const _keyRequireLongPress = 'set_require_long_press';
  static const _keyShowBreathGate = 'set_show_breath_gate';

  SharedPreferences? _prefs;

  bool _blurExplore = true; // Default: blur explore feed posts/reels
  bool _blurReels = false; // If false: hide reels in feed (after session ends)
  bool _requireLongPress = true; // Long-press FAB to start session
  bool _showBreathGate = true; // Show breathing gate on every open

  bool get blurExplore => _blurExplore;
  bool get blurReels => _blurReels;
  bool get requireLongPress => _requireLongPress;
  bool get showBreathGate => _showBreathGate;

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    _blurExplore = _prefs!.getBool(_keyBlurExplore) ?? true;
    _blurReels = _prefs!.getBool(_keyBlurReels) ?? false;
    _requireLongPress = _prefs!.getBool(_keyRequireLongPress) ?? true;
    _showBreathGate = _prefs!.getBool(_keyShowBreathGate) ?? true;
    notifyListeners();
  }

  Future<void> setBlurExplore(bool v) async {
    _blurExplore = v;
    await _prefs?.setBool(_keyBlurExplore, v);
    notifyListeners();
  }

  Future<void> setBlurReels(bool v) async {
    _blurReels = v;
    await _prefs?.setBool(_keyBlurReels, v);
    notifyListeners();
  }

  Future<void> setRequireLongPress(bool v) async {
    _requireLongPress = v;
    await _prefs?.setBool(_keyRequireLongPress, v);
    notifyListeners();
  }

  Future<void> setShowBreathGate(bool v) async {
    _showBreathGate = v;
    await _prefs?.setBool(_keyShowBreathGate, v);
    notifyListeners();
  }
}
