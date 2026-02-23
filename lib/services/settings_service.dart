import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Stores and retrieves all user-configurable app settings.
class SettingsService extends ChangeNotifier {
  static const _keyBlurExplore = 'set_blur_explore';
  static const _keyBlurReels = 'set_blur_reels';
  static const _keyRequireLongPress = 'set_require_long_press';
  static const _keyShowBreathGate = 'set_show_breath_gate';
  static const _keyRequireWordChallenge = 'set_require_word_challenge';
  static const _keyGhostMode = 'set_ghost_mode';
  static const _keyEnableTextSelection = 'set_enable_text_selection';

  SharedPreferences? _prefs;

  bool _blurExplore = true; // Default: blur explore feed posts/reels
  bool _blurReels = false; // If false: hide reels in feed (after session ends)
  bool _requireLongPress = true; // Long-press FAB to start session
  bool _showBreathGate = true; // Show breathing gate on every open
  bool _requireWordChallenge = true;
  bool _ghostMode = true; // Default: hide seen/typing
  bool _enableTextSelection = false; // Default: disabled

  bool get blurExplore => _blurExplore;
  bool get blurReels => _blurReels;
  bool get requireLongPress => _requireLongPress;
  bool get showBreathGate => _showBreathGate;
  bool get requireWordChallenge => _requireWordChallenge;
  bool get ghostMode => _ghostMode;
  bool get enableTextSelection => _enableTextSelection;

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    _blurExplore = _prefs!.getBool(_keyBlurExplore) ?? true;
    _blurReels = _prefs!.getBool(_keyBlurReels) ?? false;
    _requireLongPress = _prefs!.getBool(_keyRequireLongPress) ?? true;
    _showBreathGate = _prefs!.getBool(_keyShowBreathGate) ?? true;
    _requireWordChallenge = _prefs!.getBool(_keyRequireWordChallenge) ?? true;
    _ghostMode = _prefs!.getBool(_keyGhostMode) ?? true;
    _enableTextSelection = _prefs!.getBool(_keyEnableTextSelection) ?? false;
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

  Future<void> setRequireWordChallenge(bool v) async {
    _requireWordChallenge = v;
    await _prefs?.setBool(_keyRequireWordChallenge, v);
    notifyListeners();
  }

  Future<void> setGhostMode(bool v) async {
    _ghostMode = v;
    await _prefs?.setBool(_keyGhostMode, v);
    notifyListeners();
  }

  Future<void> setEnableTextSelection(bool v) async {
    _enableTextSelection = v;
    await _prefs?.setBool(_keyEnableTextSelection, v);
    notifyListeners();
  }
}
