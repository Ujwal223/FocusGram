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
  static const _keyEnabledTabs = 'set_enabled_tabs';
  static const _keyShowInstaSettings = 'set_show_insta_settings';

  static const _keyIsFirstRun = 'set_is_first_run';

  SharedPreferences? _prefs;

  bool _blurExplore = true;
  bool _blurReels = false;
  bool _requireLongPress = true;
  bool _showBreathGate = true;
  bool _requireWordChallenge = true;
  bool _ghostMode = true;
  bool _enableTextSelection = false;
  bool _showInstaSettings = true;
  List<String> _enabledTabs = ['Home', 'Search', 'Create', 'Reels', 'Profile'];
  bool _isFirstRun = true;

  bool get blurExplore => _blurExplore;
  bool get blurReels => _blurReels;
  bool get requireLongPress => _requireLongPress;
  bool get showBreathGate => _showBreathGate;
  bool get requireWordChallenge => _requireWordChallenge;
  bool get ghostMode => _ghostMode;
  bool get enableTextSelection => _enableTextSelection;
  bool get showInstaSettings => _showInstaSettings;
  List<String> get enabledTabs => _enabledTabs;
  bool get isFirstRun => _isFirstRun;

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    _blurExplore = _prefs!.getBool(_keyBlurExplore) ?? true;
    _blurReels = _prefs!.getBool(_keyBlurReels) ?? false;
    _requireLongPress = _prefs!.getBool(_keyRequireLongPress) ?? true;
    _showBreathGate = _prefs!.getBool(_keyShowBreathGate) ?? true;
    _requireWordChallenge = _prefs!.getBool(_keyRequireWordChallenge) ?? true;
    _ghostMode = _prefs!.getBool(_keyGhostMode) ?? true;
    _enableTextSelection = _prefs!.getBool(_keyEnableTextSelection) ?? false;
    _showInstaSettings = _prefs!.getBool(_keyShowInstaSettings) ?? true;
    _enabledTabs =
        _prefs!.getStringList(_keyEnabledTabs) ??
        ['Home', 'Search', 'Create', 'Reels', 'Profile'];
    _isFirstRun = _prefs!.getBool(_keyIsFirstRun) ?? true;
    notifyListeners();
  }

  Future<void> setFirstRunCompleted() async {
    _isFirstRun = false;
    await _prefs?.setBool(_keyIsFirstRun, false);
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

  Future<void> setShowInstaSettings(bool v) async {
    _showInstaSettings = v;
    await _prefs?.setBool(_keyShowInstaSettings, v);
    notifyListeners();
  }

  Future<void> toggleTab(String tab) async {
    if (_enabledTabs.contains(tab)) {
      if (_enabledTabs.length > 1) {
        _enabledTabs.remove(tab);
      }
    } else {
      _enabledTabs.add(tab);
    }
    await _prefs?.setStringList(_keyEnabledTabs, _enabledTabs);
    notifyListeners();
  }
}
