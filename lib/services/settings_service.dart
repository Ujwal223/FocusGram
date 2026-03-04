import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Stores and retrieves all user-configurable app settings.
class SettingsService extends ChangeNotifier {
  static const _keyBlurExplore = 'set_blur_explore';
  static const _keyBlurReels = 'set_blur_reels';
  static const _keyTapToUnblur = 'set_tap_to_unblur';
  static const _keyRequireLongPress = 'set_require_long_press';
  static const _keyShowBreathGate = 'set_show_breath_gate';
  static const _keyRequireWordChallenge = 'set_require_word_challenge';
  static const _keyEnableTextSelection = 'set_enable_text_selection';
  static const _keyEnabledTabs = 'set_enabled_tabs';
  static const _keyShowInstaSettings = 'set_show_insta_settings';
  static const _keyIsFirstRun = 'set_is_first_run';

  // Focus / playback
  static const _keyBlockAutoplay = 'block_autoplay';

  // Grayscale mode - now supports multiple schedules
  static const _keyGrayscaleEnabled = 'grayscale_enabled';
  static const _keyGrayscaleSchedules = 'grayscale_schedules';

  // Content filtering / UI hiding
  static const _keyHideSponsoredPosts = 'hide_sponsored_posts';
  static const _keyHideLikeCounts = 'hide_like_counts';
  static const _keyHideFollowerCounts = 'hide_follower_counts';
  static const _keyHideShopTab = 'hide_shop_tab';

  // Minimal mode
  static const _keyMinimalModeEnabled = 'minimal_mode_enabled';
  
  // Minimal mode state tracking for smart restore
  static const _keyMinimalModePrevDisableReels = 'minimal_mode_prev_disable_reels';
  static const _keyMinimalModePrevDisableExplore = 'minimal_mode_prev_disable_explore';
  static const _keyMinimalModePrevBlurExplore = 'minimal_mode_prev_blur_explore';

  // Reels History
  static const _keyReelsHistoryEnabled = 'reels_history_enabled';

  // Privacy keys
  static const _keySanitizeLinks = 'set_sanitize_links';
  static const _keyNotifyDMs = 'set_notify_dms';
  static const _keyNotifyActivity = 'set_notify_activity';
  static const _keyNotifySessionEnd = 'set_notify_session_end';
  static const _keyNotifyPersistent = 'set_notify_persistent';

  SharedPreferences? _prefs;

  bool _blurExplore = true;
  bool _blurReels = false;
  bool _tapToUnblur = true;
  bool _requireLongPress = true;
  bool _showBreathGate = true;
  bool _requireWordChallenge = true;
  bool _enableTextSelection = false;
  bool _showInstaSettings = true;
  bool _isDarkMode = true; // Default to dark as per existing app theme

  bool _blockAutoplay = true;

  bool _grayscaleEnabled = false;
  
  // Grayscale schedules - list of {enabled, startTime, endTime}
  // startTime and endTime are in format "HH:MM"
  List<Map<String, dynamic>> _grayscaleSchedules = [];

  bool _hideSponsoredPosts = false;
  bool _hideLikeCounts = false;
  bool _hideFollowerCounts = false;
  bool _hideShopTab = false;

  // These are now controlled internally by minimal mode
  bool _disableReelsEntirely = false;
  bool _disableExploreEntirely = false;
  bool _minimalModeEnabled = false;

  // Tracking for smart restore
  bool _prevDisableReels = false;
  bool _prevDisableExplore = false;
  bool _prevBlurExplore = false;

  bool _reelsHistoryEnabled = true;

  // Privacy defaults - notifications OFF by default
  bool _sanitizeLinks = true;
  bool _notifyDMs = false;
  bool _notifyActivity = false;
  bool _notifySessionEnd = false;
  bool _notifyPersistent = false;

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
  bool get tapToUnblur => _tapToUnblur;
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
  bool get notifyPersistent => _notifyPersistent;

  bool get grayscaleEnabled => _grayscaleEnabled;
  List<Map<String, dynamic>> get grayscaleSchedules => _grayscaleSchedules;

  bool get hideSponsoredPosts => _hideSponsoredPosts;
  bool get hideLikeCounts => _hideLikeCounts;
  bool get hideFollowerCounts => _hideFollowerCounts;
  bool get hideShopTab => _hideShopTab;

  // These are now controlled by minimal mode only
  bool get disableReelsEntirely => _minimalModeEnabled ? true : _disableReelsEntirely;
  bool get disableExploreEntirely => _minimalModeEnabled ? true : _disableExploreEntirely;
  bool get minimalModeEnabled => _minimalModeEnabled;

  bool get reelsHistoryEnabled => _reelsHistoryEnabled;

  /// True if grayscale should currently be applied, considering the manual
  /// toggle and the optional schedules.
  bool get isGrayscaleActiveNow {
    if (_grayscaleEnabled) return true;
    if (_grayscaleSchedules.isEmpty) return false;
    
    final now = DateTime.now();
    final currentMinutes = now.hour * 60 + now.minute;
    
    for (final schedule in _grayscaleSchedules) {
      if (schedule['enabled'] != true) continue;
      
      try {
        final startParts = (schedule['startTime'] as String).split(':');
        final endParts = (schedule['endTime'] as String).split(':');
        
        if (startParts.length != 2 || endParts.length != 2) continue;
        
        final startMinutes = int.parse(startParts[0]) * 60 + int.parse(startParts[1]);
        final endMinutes = int.parse(endParts[0]) * 60 + int.parse(endParts[1]);
        
        // Handle overnight schedules (e.g., 21:00 to 06:00)
        if (endMinutes < startMinutes) {
          // Overnight: active if current time is >= start OR < end
          if (currentMinutes >= startMinutes || currentMinutes < endMinutes) {
            return true;
          }
        } else {
          // Same day: active if current time is between start and end
          if (currentMinutes >= startMinutes && currentMinutes < endMinutes) {
            return true;
          }
        }
      } catch (_) {
        continue;
      }
    }
    return false;
  }

  // Privacy getters
  bool get sanitizeLinks => _sanitizeLinks;

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    _blurExplore = _prefs!.getBool(_keyBlurExplore) ?? true;
    _blurReels = _prefs!.getBool(_keyBlurReels) ?? false;
    _tapToUnblur = _prefs!.getBool(_keyTapToUnblur) ?? true;
    _requireLongPress = _prefs!.getBool(_keyRequireLongPress) ?? true;
    _showBreathGate = _prefs!.getBool(_keyShowBreathGate) ?? true;
    _requireWordChallenge = _prefs!.getBool(_keyRequireWordChallenge) ?? true;
    _enableTextSelection = _prefs!.getBool(_keyEnableTextSelection) ?? false;
    _showInstaSettings = _prefs!.getBool(_keyShowInstaSettings) ?? true;
    _blockAutoplay = _prefs!.getBool(_keyBlockAutoplay) ?? true;

    _grayscaleEnabled = _prefs!.getBool(_keyGrayscaleEnabled) ?? false;
    
    // Load grayscale schedules
    final schedulesJson = _prefs!.getString(_keyGrayscaleSchedules);
    if (schedulesJson != null) {
      try {
        _grayscaleSchedules = List<Map<String, dynamic>>.from(
          (jsonDecode(schedulesJson) as List).map((e) => Map<String, dynamic>.from(e))
        );
      } catch (_) {
        _grayscaleSchedules = [];
      }
    }

    _hideSponsoredPosts = _prefs!.getBool(_keyHideSponsoredPosts) ?? false;
    _hideLikeCounts = _prefs!.getBool(_keyHideLikeCounts) ?? false;
    _hideFollowerCounts = _prefs!.getBool(_keyHideFollowerCounts) ?? false;
    _hideShopTab = _prefs!.getBool(_keyHideShopTab) ?? false;

    // Load minimal mode
    _minimalModeEnabled = _prefs!.getBool(_keyMinimalModeEnabled) ?? false;
    
    // Load previous states for smart restore
    _prevDisableReels = _prefs!.getBool(_keyMinimalModePrevDisableReels) ?? false;
    _prevDisableExplore = _prefs!.getBool(_keyMinimalModePrevDisableExplore) ?? false;
    _prevBlurExplore = _prefs!.getBool(_keyMinimalModePrevBlurExplore) ?? false;

    // These are now internal states, not user-facing settings
    _disableReelsEntirely = _prefs!.getBool('internal_disable_reels_entirely') ?? false;
    _disableExploreEntirely = _prefs!.getBool('internal_disable_explore_entirely') ?? false;

    _reelsHistoryEnabled = _prefs!.getBool(_keyReelsHistoryEnabled) ?? true;

    _sanitizeLinks = _prefs!.getBool(_keySanitizeLinks) ?? true;
    _notifyDMs = _prefs!.getBool(_keyNotifyDMs) ?? false;
    _notifyActivity = _prefs!.getBool(_keyNotifyActivity) ?? false;
    _notifySessionEnd = _prefs!.getBool(_keyNotifySessionEnd) ?? false;
    _notifyPersistent = _prefs!.getBool(_keyNotifyPersistent) ?? false;

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
    // Sync blur explore with blur reels - enabling one enables the other
    if (v && !_blurReels) {
      _blurReels = true;
      await _prefs?.setBool(_keyBlurReels, true);
    }
    await _prefs?.setBool(_keyBlurExplore, v);
    notifyListeners();
  }

  Future<void> setBlurReels(bool v) async {
    _blurReels = v;
    // Sync blur reels with blur explore - enabling one enables the other
    if (v && !_blurExplore) {
      _blurExplore = true;
      await _prefs?.setBool(_keyBlurExplore, true);
    }
    await _prefs?.setBool(_keyBlurReels, v);
    notifyListeners();
  }

  Future<void> setTapToUnblur(bool v) async {
    _tapToUnblur = v;
    await _prefs?.setBool(_keyTapToUnblur, v);
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

  Future<void> setGrayscaleSchedules(List<Map<String, dynamic>> schedules) async {
    _grayscaleSchedules = schedules;
    await _prefs?.setString(_keyGrayscaleSchedules, jsonEncode(schedules));
    notifyListeners();
  }

  Future<void> addGrayscaleSchedule(Map<String, dynamic> schedule) async {
    _grayscaleSchedules.add(schedule);
    await _prefs?.setString(_keyGrayscaleSchedules, jsonEncode(_grayscaleSchedules));
    notifyListeners();
  }

  Future<void> updateGrayscaleSchedule(int index, Map<String, dynamic> schedule) async {
    if (index >= 0 && index < _grayscaleSchedules.length) {
      _grayscaleSchedules[index] = schedule;
      await _prefs?.setString(_keyGrayscaleSchedules, jsonEncode(_grayscaleSchedules));
      notifyListeners();
    }
  }

  Future<void> removeGrayscaleSchedule(int index) async {
    if (index >= 0 && index < _grayscaleSchedules.length) {
      _grayscaleSchedules.removeAt(index);
      await _prefs?.setString(_keyGrayscaleSchedules, jsonEncode(_grayscaleSchedules));
      notifyListeners();
    }
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

  Future<void> setHideShopTab(bool v) async {
    _hideShopTab = v;
    await _prefs?.setBool(_keyHideShopTab, v);
    notifyListeners();
  }

  /// Setter for internal disable reels state (used by minimal mode submenu)
  Future<void> setDisableReelsEntirelyInternal(bool v) async {
    _disableReelsEntirely = v;
    await _prefs?.setBool('internal_disable_reels_entirely', v);
    notifyListeners();
  }

  /// Setter for internal disable explore state (used by minimal mode submenu)
  Future<void> setDisableExploreEntirelyInternal(bool v) async {
    _disableExploreEntirely = v;
    await _prefs?.setBool('internal_disable_explore_entirely', v);
    notifyListeners();
  }

  /// Smart minimal mode toggle with state preservation
  Future<void> setMinimalModeEnabled(bool v) async {
    if (v) {
      // Turning ON - save current states BEFORE enabling minimal mode
      _prevDisableReels = _disableReelsEntirely;
      _prevDisableExplore = _disableExploreEntirely;
      _prevBlurExplore = _blurExplore;
      
      await _prefs?.setBool(_keyMinimalModePrevDisableReels, _prevDisableReels);
      await _prefs?.setBool(_keyMinimalModePrevDisableExplore, _prevDisableExplore);
      await _prefs?.setBool(_keyMinimalModePrevBlurExplore, _prevBlurExplore);
      
      // Enable all minimal mode settings
      _minimalModeEnabled = true;
      _disableReelsEntirely = true;
      _disableExploreEntirely = true;
      _blurExplore = true;
      
      await _prefs?.setBool(_keyMinimalModeEnabled, true);
      await _prefs?.setBool('internal_disable_reels_entirely', true);
      await _prefs?.setBool('internal_disable_explore_entirely', true);
      await _prefs?.setBool(_keyBlurExplore, true);
    } else {
      // Turning OFF - restore to PREVIOUS states (before minimal mode was turned on)
      _minimalModeEnabled = false;
      
      // Simply restore to the states that were saved BEFORE minimal mode was enabled
      _disableReelsEntirely = _prevDisableReels;
      _disableExploreEntirely = _prevDisableExplore;
      _blurExplore = _prevBlurExplore;
      
      // Save the restored states
      await _prefs?.setBool(_keyMinimalModeEnabled, false);
      await _prefs?.setBool('internal_disable_reels_entirely', _disableReelsEntirely);
      await _prefs?.setBool('internal_disable_explore_entirely', _disableExploreEntirely);
      await _prefs?.setBool(_keyBlurExplore, _blurExplore);
    }
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

  Future<void> setNotifyPersistent(bool v) async {
    _notifyPersistent = v;
    await _prefs?.setBool(_keyNotifyPersistent, v);
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
