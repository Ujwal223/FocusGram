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

  // Focus / playback
  static const _keyBlockAutoplay = 'block_autoplay';

  // Grayscale mode
  static const _keyGrayscaleEnabled = 'grayscale_enabled';
  static const _keyGrayscaleScheduleEnabled = 'grayscale_schedule_enabled';
  static const _keyGrayscaleScheduleTime = 'grayscale_schedule_time';

  // Content filtering / UI hiding
  static const _keyHideSuggestedPosts = 'hide_suggested_posts';
  static const _keyHideSponsoredPosts = 'hide_sponsored_posts';
  static const _keyHideLikeCounts = 'hide_like_counts';
  static const _keyHideFollowerCounts = 'hide_follower_counts';
  static const _keyHideStoriesBar = 'hide_stories_bar';
  static const _keyHideExploreTab = 'hide_explore_tab';
  static const _keyHideReelsTab = 'hide_reels_tab';
  static const _keyHideShopTab = 'hide_shop_tab';

  // Complete section disabling / Minimal mode
  static const _keyDisableReelsEntirely = 'disable_reels_entirely';
  static const _keyDisableExploreEntirely = 'disable_explore_entirely';
  static const _keyMinimalModeEnabled = 'minimal_mode_enabled';

  // Reels History
  static const _keyReelsHistoryEnabled = 'reels_history_enabled';

  // Privacy keys
  static const _keySanitizeLinks = 'set_sanitize_links';
  static const _keyNotifyDMs = 'set_notify_dms';
  static const _keyNotifyActivity = 'set_notify_activity';
  static const _keyNotifySessionEnd = 'set_notify_session_end';

  SharedPreferences? _prefs;

  bool _blurExplore = true;
  bool _blurReels = false;
  bool _requireLongPress = true;
  bool _showBreathGate = true;
  bool _requireWordChallenge = true;
  bool _enableTextSelection = false;
  bool _showInstaSettings = true;
  bool _isDarkMode = true; // Default to dark as per existing app theme

  bool _blockAutoplay = true;

  bool _grayscaleEnabled = false;
  bool _grayscaleScheduleEnabled = false;
  String _grayscaleScheduleTime = '21:00'; // 9:00 PM default

  bool _hideSuggestedPosts = false;
  bool _hideSponsoredPosts = false;
  bool _hideLikeCounts = false;
  bool _hideFollowerCounts = false;
  bool _hideStoriesBar = false;
  bool _hideExploreTab = false;
  bool _hideReelsTab = false;
  bool _hideShopTab = false;

  bool _disableReelsEntirely = false;
  bool _disableExploreEntirely = false;
  bool _minimalModeEnabled = false;

  bool _reelsHistoryEnabled = true;

  // Privacy defaults - notifications OFF by default
  bool _sanitizeLinks = true;
  bool _notifyDMs = false;
  bool _notifyActivity = false;
  bool _notifySessionEnd = false;

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
  bool get blockAutoplay => _blockAutoplay;
  bool get notifyDMs => _notifyDMs;
  bool get notifyActivity => _notifyActivity;
  bool get notifySessionEnd => _notifySessionEnd;

  bool get grayscaleEnabled => _grayscaleEnabled;
  bool get grayscaleScheduleEnabled => _grayscaleScheduleEnabled;
  String get grayscaleScheduleTime => _grayscaleScheduleTime;

  bool get hideSuggestedPosts => _hideSuggestedPosts;
  bool get hideSponsoredPosts => _hideSponsoredPosts;
  bool get hideLikeCounts => _hideLikeCounts;
  bool get hideFollowerCounts => _hideFollowerCounts;
  bool get hideStoriesBar => _hideStoriesBar;
  bool get hideExploreTab => _hideExploreTab;
  bool get hideReelsTab => _hideReelsTab;
  bool get hideShopTab => _hideShopTab;

  bool get disableReelsEntirely => _disableReelsEntirely;
  bool get disableExploreEntirely => _disableExploreEntirely;
  bool get minimalModeEnabled => _minimalModeEnabled;

  bool get reelsHistoryEnabled => _reelsHistoryEnabled;

  /// True if grayscale should currently be applied, considering the manual
  /// toggle and the optional schedule.
  bool get isGrayscaleActiveNow {
    if (_grayscaleEnabled) return true;
    if (!_grayscaleScheduleEnabled) return false;
    try {
      final parts = _grayscaleScheduleTime.split(':');
      if (parts.length != 2) return false;
      final h = int.parse(parts[0]);
      final m = int.parse(parts[1]);
      final now = DateTime.now();
      final currentMinutes = now.hour * 60 + now.minute;
      final startMinutes = h * 60 + m;
      // Active from the configured time until midnight.
      return currentMinutes >= startMinutes;
    } catch (_) {
      return false;
    }
  }

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
    _blockAutoplay = _prefs!.getBool(_keyBlockAutoplay) ?? true;

    _grayscaleEnabled = _prefs!.getBool(_keyGrayscaleEnabled) ?? false;
    _grayscaleScheduleEnabled =
        _prefs!.getBool(_keyGrayscaleScheduleEnabled) ?? false;
    _grayscaleScheduleTime =
        _prefs!.getString(_keyGrayscaleScheduleTime) ?? '21:00';

    _hideSuggestedPosts = _prefs!.getBool(_keyHideSuggestedPosts) ?? false;
    _hideSponsoredPosts = _prefs!.getBool(_keyHideSponsoredPosts) ?? false;
    _hideLikeCounts = _prefs!.getBool(_keyHideLikeCounts) ?? false;
    _hideFollowerCounts = _prefs!.getBool(_keyHideFollowerCounts) ?? false;
    _hideStoriesBar = _prefs!.getBool(_keyHideStoriesBar) ?? false;
    _hideExploreTab = _prefs!.getBool(_keyHideExploreTab) ?? false;
    _hideReelsTab = _prefs!.getBool(_keyHideReelsTab) ?? false;
    _hideShopTab = _prefs!.getBool(_keyHideShopTab) ?? false;

    _disableReelsEntirely = _prefs!.getBool(_keyDisableReelsEntirely) ?? false;
    _disableExploreEntirely =
        _prefs!.getBool(_keyDisableExploreEntirely) ?? false;
    _minimalModeEnabled = _prefs!.getBool(_keyMinimalModeEnabled) ?? false;

    _reelsHistoryEnabled = _prefs!.getBool(_keyReelsHistoryEnabled) ?? true;

    _sanitizeLinks = _prefs!.getBool(_keySanitizeLinks) ?? true;
    _notifyDMs = _prefs!.getBool(_keyNotifyDMs) ?? false;
    _notifyActivity = _prefs!.getBool(_keyNotifyActivity) ?? false;
    _notifySessionEnd = _prefs!.getBool(_keyNotifySessionEnd) ?? false;

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

  Future<void> setBlockAutoplay(bool v) async {
    _blockAutoplay = v;
    await _prefs?.setBool(_keyBlockAutoplay, v);
    notifyListeners();
  }

  Future<void> setGrayscaleEnabled(bool v) async {
    _grayscaleEnabled = v;
    await _prefs?.setBool(_keyGrayscaleEnabled, v);
    notifyListeners();
  }

  Future<void> setGrayscaleScheduleEnabled(bool v) async {
    _grayscaleScheduleEnabled = v;
    await _prefs?.setBool(_keyGrayscaleScheduleEnabled, v);
    notifyListeners();
  }

  Future<void> setGrayscaleScheduleTime(String hhmm) async {
    _grayscaleScheduleTime = hhmm;
    await _prefs?.setString(_keyGrayscaleScheduleTime, hhmm);
    notifyListeners();
  }

  Future<void> setHideSuggestedPosts(bool v) async {
    _hideSuggestedPosts = v;
    await _prefs?.setBool(_keyHideSuggestedPosts, v);
    notifyListeners();
  }

  Future<void> setHideSponsoredPosts(bool v) async {
    _hideSponsoredPosts = v;
    await _prefs?.setBool(_keyHideSponsoredPosts, v);
    notifyListeners();
  }

  Future<void> setHideLikeCounts(bool v) async {
    _hideLikeCounts = v;
    await _prefs?.setBool(_keyHideLikeCounts, v);
    notifyListeners();
  }

  Future<void> setHideFollowerCounts(bool v) async {
    _hideFollowerCounts = v;
    await _prefs?.setBool(_keyHideFollowerCounts, v);
    notifyListeners();
  }

  Future<void> setHideStoriesBar(bool v) async {
    _hideStoriesBar = v;
    await _prefs?.setBool(_keyHideStoriesBar, v);
    notifyListeners();
  }

  Future<void> setHideExploreTab(bool v) async {
    _hideExploreTab = v;
    await _prefs?.setBool(_keyHideExploreTab, v);
    notifyListeners();
  }

  Future<void> setHideReelsTab(bool v) async {
    _hideReelsTab = v;
    await _prefs?.setBool(_keyHideReelsTab, v);
    notifyListeners();
  }

  Future<void> setHideShopTab(bool v) async {
    _hideShopTab = v;
    await _prefs?.setBool(_keyHideShopTab, v);
    notifyListeners();
  }

  Future<void> setDisableReelsEntirely(bool v) async {
    _disableReelsEntirely = v;
    await _prefs?.setBool(_keyDisableReelsEntirely, v);
    notifyListeners();
  }

  Future<void> setDisableExploreEntirely(bool v) async {
    _disableExploreEntirely = v;
    await _prefs?.setBool(_keyDisableExploreEntirely, v);
    notifyListeners();
  }

  Future<void> setMinimalModeEnabled(bool v) async {
    _minimalModeEnabled = v;
    await _prefs?.setBool(_keyMinimalModeEnabled, v);
    notifyListeners();
  }

  Future<void> setReelsHistoryEnabled(bool v) async {
    _reelsHistoryEnabled = v;
    await _prefs?.setBool(_keyReelsHistoryEnabled, v);
    notifyListeners();
  }

  void setDarkMode(bool dark) {
    if (_isDarkMode != dark) {
      _isDarkMode = dark;
      notifyListeners();
    }
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

  Future<void> setNotifySessionEnd(bool v) async {
    _notifySessionEnd = v;
    await _prefs?.setBool(_keyNotifySessionEnd, v);
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
