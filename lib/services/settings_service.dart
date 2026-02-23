import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Stores and retrieves all user-configurable app settings.
class SettingsService extends ChangeNotifier {
  static const _keyBlurExplore = 'set_blur_explore';
  static const _keyBlurReels = 'set_blur_reels';
  static const _keyRequireLongPress = 'set_require_long_press';
  static const _keyShowBreathGate = 'set_show_breath_gate';
  static const _keyRequireWordChallenge = 'set_require_word_challenge';
  static const _keyEnableTextSelection = 'set_enable_text_selection';
  static const _keyEnabledTabs = 'set_enabled_tabs';
  static const _keyShowInstaSettings = 'set_show_insta_settings';
  static const _keyIsFirstRun = 'set_is_first_run';

  // Granular Ghost Mode keys
  static const _keyGhostTyping = 'set_ghost_typing';
  static const _keyGhostSeen = 'set_ghost_seen';
  static const _keyGhostStories = 'set_ghost_stories';
  static const _keyGhostDmPhotos = 'set_ghost_dm_photos';

  // Privacy keys
  static const _keySanitizeLinks = 'set_sanitize_links';
  static const _keyNotifyDMs = 'set_notify_dms';
  static const _keyNotifyActivity = 'set_notify_activity';

  // Legacy key for migration
  static const _keyGhostModeLegacy = 'set_ghost_mode';

  SharedPreferences? _prefs;

  bool _blurExplore = true;
  bool _blurReels = false;
  bool _requireLongPress = true;
  bool _showBreathGate = true;
  bool _requireWordChallenge = true;
  bool _enableTextSelection = false;
  bool _showInstaSettings = true;
  bool _isDarkMode = true; // Default to dark as per existing app theme

  // Granular Ghost Mode defaults (all on)
  bool _ghostTyping = true;
  bool _ghostSeen = true;
  bool _ghostStories = true;
  bool _ghostDmPhotos = true;

  // Privacy defaults
  bool _sanitizeLinks = true;
  bool _notifyDMs = true;
  bool _notifyActivity = true;

  List<String> _enabledTabs = [
    'Home',
    'Search',
    'Reels',
    'Messages',
    'Profile',
  ];
  bool _isFirstRun = true;

  bool get blurExplore => _blurExplore;
  bool get blurReels => _blurReels;
  bool get requireLongPress => _requireLongPress;
  bool get showBreathGate => _showBreathGate;
  bool get requireWordChallenge => _requireWordChallenge;
  bool get enableTextSelection => _enableTextSelection;
  bool get showInstaSettings => _showInstaSettings;
  List<String> get enabledTabs => _enabledTabs;
  bool get isFirstRun => _isFirstRun;
  bool get isDarkMode => _isDarkMode;

  // Granular Ghost Mode getters
  bool get ghostTyping => _ghostTyping;
  bool get ghostSeen => _ghostSeen;
  bool get ghostStories => _ghostStories;
  bool get ghostDmPhotos => _ghostDmPhotos;
  bool get notifyDMs => _notifyDMs;
  bool get notifyActivity => _notifyActivity;

  /// True if ANY ghost mode setting is enabled (for injection logic).
  bool get anyGhostModeEnabled =>
      _ghostTyping || _ghostSeen || _ghostStories || _ghostDmPhotos;

  // Privacy getters
  bool get sanitizeLinks => _sanitizeLinks;

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    _blurExplore = _prefs!.getBool(_keyBlurExplore) ?? true;
    _blurReels = _prefs!.getBool(_keyBlurReels) ?? false;
    _requireLongPress = _prefs!.getBool(_keyRequireLongPress) ?? true;
    _showBreathGate = _prefs!.getBool(_keyShowBreathGate) ?? true;
    _requireWordChallenge = _prefs!.getBool(_keyRequireWordChallenge) ?? true;
    _enableTextSelection = _prefs!.getBool(_keyEnableTextSelection) ?? false;
    _showInstaSettings = _prefs!.getBool(_keyShowInstaSettings) ?? true;

    // Migrate legacy ghostMode key -> all granular keys
    final legacyGhostMode = _prefs!.getBool(_keyGhostModeLegacy);
    if (legacyGhostMode != null) {
      // Seed all four granular keys with the legacy value
      _ghostTyping = legacyGhostMode;
      _ghostSeen = legacyGhostMode;
      _ghostStories = legacyGhostMode;
      _ghostDmPhotos = legacyGhostMode;
      // Save granular keys and remove legacy key
      await _prefs!.setBool(_keyGhostTyping, legacyGhostMode);
      await _prefs!.setBool(_keyGhostSeen, legacyGhostMode);
      await _prefs!.setBool(_keyGhostStories, legacyGhostMode);
      await _prefs!.setBool(_keyGhostDmPhotos, legacyGhostMode);
      await _prefs!.remove(_keyGhostModeLegacy);
    } else {
      _ghostTyping = _prefs!.getBool(_keyGhostTyping) ?? true;
      _ghostSeen = _prefs!.getBool(_keyGhostSeen) ?? true;
      _ghostStories = _prefs!.getBool(_keyGhostStories) ?? true;
      _ghostDmPhotos = _prefs!.getBool(_keyGhostDmPhotos) ?? true;
    }

    _sanitizeLinks = _prefs!.getBool(_keySanitizeLinks) ?? true;
    _notifyDMs = _prefs!.getBool(_keyNotifyDMs) ?? true;
    _notifyActivity = _prefs!.getBool(_keyNotifyActivity) ?? true;

    _enabledTabs =
        (_prefs!.getStringList(_keyEnabledTabs) ??
              ['Home', 'Search', 'Reels', 'Messages', 'Profile'])
          ..remove('Create');
    if (!_enabledTabs.contains('Messages') && _enabledTabs.length < 5) {
      // Migration: add Messages if missing
      _enabledTabs.insert(3, 'Messages');
    }
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

  void setDarkMode(bool dark) {
    if (_isDarkMode != dark) {
      _isDarkMode = dark;
      notifyListeners();
    }
  }

  // Granular Ghost Mode setters
  Future<void> setGhostTyping(bool v) async {
    _ghostTyping = v;
    await _prefs?.setBool(_keyGhostTyping, v);
    notifyListeners();
  }

  Future<void> setGhostSeen(bool v) async {
    _ghostSeen = v;
    await _prefs?.setBool(_keyGhostSeen, v);
    notifyListeners();
  }

  Future<void> setGhostStories(bool v) async {
    _ghostStories = v;
    await _prefs?.setBool(_keyGhostStories, v);
    notifyListeners();
  }

  Future<void> setGhostDmPhotos(bool v) async {
    _ghostDmPhotos = v;
    await _prefs?.setBool(_keyGhostDmPhotos, v);
    notifyListeners();
  }

  Future<void> setSanitizeLinks(bool v) async {
    _sanitizeLinks = v;
    await _prefs?.setBool(_keySanitizeLinks, v);
    notifyListeners();
  }

  Future<void> setNotifyDMs(bool v) async {
    _notifyDMs = v;
    await _prefs?.setBool(_keyNotifyDMs, v);
    notifyListeners();
  }

  Future<void> setNotifyActivity(bool v) async {
    _notifyActivity = v;
    await _prefs?.setBool(_keyNotifyActivity, v);
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

  Future<void> reorderTab(int oldIndex, int newIndex) async {
    if (newIndex > oldIndex) newIndex -= 1;
    final String item = _enabledTabs.removeAt(oldIndex);
    _enabledTabs.insert(newIndex, item);
    await _prefs?.setStringList(_keyEnabledTabs, _enabledTabs);
    notifyListeners();
  }
}
