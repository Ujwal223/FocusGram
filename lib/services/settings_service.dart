import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'notification_service.dart';

/// Stores and retrieves all user-configurable app settings.
class SettingsService extends ChangeNotifier {
  static const _keyBlurExplore = 'set_blur_explore';
  static const _keyBlurReels = 'set_blur_reels';
  static const _keyTapToUnblur = 'set_tap_to_unblur';
  static const _keyShowBreathGate = 'set_show_breath_gate';
  static const _keyRequireWordChallenge = 'set_require_word_challenge';
  static const _keyRequireLongPress = 'set_require_long_press';
  static const _keyBreathGateSeconds = 'breath_gate_seconds';
  static const _keyWordChallengeCount = 'word_challenge_count';
  static const _keyEnableTextSelection = 'set_enable_text_selection';
  static const _keyEnabledTabs = 'set_enabled_tabs';
  static const _keyShowInstaSettings = 'set_show_insta_settings';
  static const _keyIsFirstRun = 'set_is_first_run';

  // Focus / playback
  static const _keyBlockAutoplay = 'block_autoplay';

  // Extras (Phase 2)
  static const _keyVideoDownloadEnabled = 'video_download_enabled';
  static const _keyHideSuggestedPosts = 'hide_suggested_posts';

  // ── FocusGram v2 overlay toggles ─────────────────────────────────────────
  static const _keyV2GhostModeEnabled = 'v2_ghost_mode_enabled';
  static const _keyV2AdBlockerDomEnabled = 'v2_adblock_dom_enabled';
  static const _keyV2ContentHiderEnabled = 'v2_content_hider_enabled';

  // Content hider flags (consumed by v2/content_hider.js via prefs keys)
  static const _keyContentStories = 'content_stories';
  static const _keyContentPosts = 'content_posts';
  static const _keyContentReels = 'content_reels';
  static const _keyContentSuggested = 'content_suggested';

  // Grayscale mode - now supports multiple schedules
  static const _keyGrayscaleEnabled = 'grayscale_enabled';
  static const _keyGrayscaleSchedules = 'grayscale_schedules';

  // Content filtering / UI hiding
  static const _keyHideLikeCounts = 'hide_like_counts';
  static const _keyHideFollowerCounts = 'hide_follower_counts';
  static const _keyHideShopTab = 'hide_shop_tab';

  // Minimal mode
  static const _keyMinimalModeEnabled = 'minimal_mode_enabled';

  // Minimal mode state tracking for smart restore
  static const _keyMinimalModePrevDisableReels =
      'minimal_mode_prev_disable_reels';
  static const _keyMinimalModePrevDisableExplore =
      'minimal_mode_prev_disable_explore';
  static const _keyMinimalModePrevBlurExplore =
      'minimal_mode_prev_blur_explore';
  static const _keyMinimalModePrevBlockHomeFeedScroll =
      'minimal_mode_prev_block_home_feed_scroll';

  // Reels History
  static const _keyReelsHistoryEnabled = 'reels_history_enabled';

  // ── Adsterra fallback ─────────────────────────────────────
  static const _keyAdsterraZoneUrl = 'adsterra_zone_url';
  static const _keyAdsterraAdCode = 'adsterra_ad_code';

  // ── Startup page ──────────────────────────────────────────
  static const _keyStartupPage = 'startup_page';

  // ── Effort Friction Mode ──────────────────────────────────
  static const _keyEffortFrictionEnabled = 'effort_friction_enabled';

  // Privacy keys
  static const _keySanitizeLinks = 'set_sanitize_links';
  static const _keyNotifyDMs = 'set_notify_dms';
  static const _keyNotifyActivity = 'set_notify_activity';
  static const _keyNotifySessionEnd = 'set_notify_session_end';
  static const _keyNotifyPersistent = 'set_notify_persistent';

  // Focus mode settings
  static const _keyGhostMode = 'ghost_mode';
  static const _keyNoAds = 'no_ads';
  static const _keyNoStories = 'no_stories';
  static const _keyNoReels = 'no_reels';
  static const _keyNoAutoplay = 'no_autoplay';
  static const _keyNoDMs = 'no_dms';

  SharedPreferences? _prefs;

  bool _blurExplore = true;
  bool _blurReels = false;
  bool _tapToUnblur = true;
  bool _requireLongPress = true;
  bool _showBreathGate = true;
  bool _requireWordChallenge = true;
  int _breathGateSeconds = 10;
  int _wordChallengeCount = 30;
  bool _enableTextSelection = false;
  bool _showInstaSettings = true;
  bool _isDarkMode = true; // Default to dark as per existing app theme

  bool _blockAutoplay = true;

  bool _videoDownloadEnabled = false;
  bool _hideSuggestedPosts = false;

  // ── FocusGram v2 overlay toggles ─────────────────────────────────────────
  bool _v2GhostModeEnabled = false;
  bool _v2AdBlockerDomEnabled = false;
  bool _v2ContentHiderEnabled = false;

  // Content hider flags (consumed by v2/content_hider.js via prefs keys)
  bool _contentStories = false;
  bool _contentPosts = false;
  bool _contentReels = false;
  bool _contentSuggested = false;

  // Grayscale mode - now supports multiple schedules
  bool _grayscaleEnabled = false;
  List<Map<String, dynamic>> _grayscaleSchedules = [];

  // Content filtering / UI hiding
  bool _hideLikeCounts = false;
  bool _hideFollowerCounts = false;
  bool _hideShopTab = false;

  // These are now controlled internally by minimal mode
  bool _disableReelsEntirely = false;
  bool _disableExploreEntirely = false;
  bool _blockHomeFeedScroll = false;
  bool _minimalModeEnabled = false;

  // Tracking for smart restore
  bool _prevDisableReels = false;
  bool _prevDisableExplore = false;
  bool _prevBlurExplore = false;
  bool _prevBlockHomeFeedScroll = false;

  bool _reelsHistoryEnabled = true;

  // Privacy defaults - notifications OFF by default
  bool _sanitizeLinks = true;
  bool _notifyDMs = false;
  bool _notifyActivity = false;
  bool _notifySessionEnd = false;
  bool _notifyPersistent = false;

  // Focus mode settings
  bool _effortFrictionEnabled = true;
  String _startupPage = 'home'; // home, following, favorites, direct
  String _adsterraZoneUrl = '';
  String _adsterraAdCode = '';
  bool _ghostMode = false;
  bool _noAds = false;
  bool _noStories = false;
  bool _noReels = false;
  bool _noAutoplay = false;
  bool _noDMs = false;

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
  int get breathGateSeconds => _breathGateSeconds;
  int get wordChallengeCount => _wordChallengeCount;
  bool get enableTextSelection => _enableTextSelection;
  bool get showInstaSettings => _showInstaSettings;
  List<String> get enabledTabs => _enabledTabs;
  bool get isFirstRun => _isFirstRun;
  bool get isDarkMode => _isDarkMode;
  bool get blockAutoplay => _blockAutoplay;

  // Extras (Phase 2)
  bool get videoDownloadEnabled => _videoDownloadEnabled;
  bool get hideSuggestedPosts => _hideSuggestedPosts;

  // ── FocusGram v2 overlay toggles ─────────────────────────────────────────
  bool get v2GhostModeEnabled => _v2GhostModeEnabled;
  bool get v2AdBlockerDomEnabled => _v2AdBlockerDomEnabled;
  bool get v2ContentHiderEnabled => _v2ContentHiderEnabled;

  bool get contentStories => _contentStories;
  bool get contentPosts => _contentPosts;
  bool get contentReels => _contentReels;
  bool get contentSuggested => _contentSuggested;
  bool get notifyDMs => _notifyDMs;
  bool get notifyActivity => _notifyActivity;
  bool get notifySessionEnd => _notifySessionEnd;
  bool get notifyPersistent => _notifyPersistent;

  bool get grayscaleEnabled => _grayscaleEnabled;
  List<Map<String, dynamic>> get grayscaleSchedules => _grayscaleSchedules;

  bool get hideLikeCounts => _hideLikeCounts;
  bool get hideFollowerCounts => _hideFollowerCounts;
  bool get hideShopTab => _hideShopTab;

  // Focus mode settings
  bool get effortFrictionEnabled => _effortFrictionEnabled;
  String get startupPage => _startupPage;
  String get startupUrl {
    switch (_startupPage) {
      case 'following':
        return 'https://www.instagram.com/?variant=following';
      case 'favorites':
        return 'https://www.instagram.com/?variant=favorites';
      case 'direct':
        return 'https://www.instagram.com/direct/inbox/';
      default:
        return 'https://www.instagram.com/';
    }
  }

  String get adsterraZoneUrl => _adsterraZoneUrl;
  String get adsterraAdCode => _adsterraAdCode;
  bool get ghostMode => _ghostMode;
  bool get noAds => _noAds;
  bool get noStories => _noStories;
  bool get noReels => _noReels;
  bool get noAutoplay => _noAutoplay;
  bool get noDMs => _noDMs;

  // These are now controlled by minimal mode only
  bool get disableReelsEntirely => _disableReelsEntirely;
  bool get disableExploreEntirely => _disableExploreEntirely;
  bool get blockHomeFeedScroll => _blockHomeFeedScroll;
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

        final startMinutes =
            int.parse(startParts[0]) * 60 + int.parse(startParts[1]);
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
    _breathGateSeconds = (_prefs!.getInt(_keyBreathGateSeconds) ?? 10)
        .clamp(3, 60)
        .toInt();
    _wordChallengeCount = _normaliseWordChallengeCount(
      _prefs!.getInt(_keyWordChallengeCount) ?? 30,
    );
    _enableTextSelection = _prefs!.getBool(_keyEnableTextSelection) ?? false;
    _showInstaSettings = _prefs!.getBool(_keyShowInstaSettings) ?? true;
    _blockAutoplay = _prefs!.getBool(_keyBlockAutoplay) ?? true;

    // Extras (Phase 2) - defaults OFF for safety/non-invasive behavior
    _videoDownloadEnabled = _prefs!.getBool(_keyVideoDownloadEnabled) ?? false;
    _hideSuggestedPosts = _prefs!.getBool(_keyHideSuggestedPosts) ?? false;

    // ── FocusGram v2 overlay toggles ─────────────────────────────────────────
    _v2GhostModeEnabled = _prefs!.getBool(_keyV2GhostModeEnabled) ?? false;
    _v2AdBlockerDomEnabled =
        _prefs!.getBool(_keyV2AdBlockerDomEnabled) ?? false;
    _v2ContentHiderEnabled =
        _prefs!.getBool(_keyV2ContentHiderEnabled) ?? false;

    _contentStories = _prefs!.getBool(_keyContentStories) ?? false;
    _contentPosts = _prefs!.getBool(_keyContentPosts) ?? false;
    _contentReels = _prefs!.getBool(_keyContentReels) ?? false;
    _contentSuggested = _prefs!.getBool(_keyContentSuggested) ?? false;
    _hideSuggestedPosts = _prefs!.getBool(_keyHideSuggestedPosts) ?? false;

    // Load grayscale toggle + schedules
    _grayscaleEnabled = _prefs!.getBool(_keyGrayscaleEnabled) ?? false;
    final schedulesJson = _prefs!.getString(_keyGrayscaleSchedules);
    if (schedulesJson != null) {
      try {
        _grayscaleSchedules = List<Map<String, dynamic>>.from(
          (jsonDecode(schedulesJson) as List).map(
            (e) => Map<String, dynamic>.from(e),
          ),
        );
      } catch (_) {
        _grayscaleSchedules = [];
      }
    }
    _hideLikeCounts = _prefs!.getBool(_keyHideLikeCounts) ?? false;
    _hideFollowerCounts = _prefs!.getBool(_keyHideFollowerCounts) ?? false;
    _hideShopTab = _prefs!.getBool(_keyHideShopTab) ?? false;

    // Load minimal mode
    _minimalModeEnabled = _prefs!.getBool(_keyMinimalModeEnabled) ?? false;

    // Load previous states for smart restore
    _prevDisableReels =
        _prefs!.getBool(_keyMinimalModePrevDisableReels) ?? false;
    _prevDisableExplore =
        _prefs!.getBool(_keyMinimalModePrevDisableExplore) ?? false;
    _prevBlurExplore = _prefs!.getBool(_keyMinimalModePrevBlurExplore) ?? false;
    _prevBlockHomeFeedScroll =
        _prefs!.getBool(_keyMinimalModePrevBlockHomeFeedScroll) ?? false;

    // These are now internal states, not user-facing settings
    _disableReelsEntirely =
        _prefs!.getBool('internal_disable_reels_entirely') ?? false;
    _disableExploreEntirely =
        _prefs!.getBool('internal_disable_explore_entirely') ?? false;
    _blockHomeFeedScroll =
        _prefs!.getBool('internal_block_home_feed_scroll') ?? false;

    _reelsHistoryEnabled = _prefs!.getBool(_keyReelsHistoryEnabled) ?? true;

    // Focus mode settings
    _effortFrictionEnabled =
        _prefs!.getBool(_keyEffortFrictionEnabled) ?? true;
    _startupPage = _prefs!.getString(_keyStartupPage) ?? 'home';
    _adsterraZoneUrl = _prefs!.getString(_keyAdsterraZoneUrl) ?? '';
    _adsterraAdCode = _prefs!.getString(_keyAdsterraAdCode) ?? '';
    _ghostMode = _prefs!.getBool(_keyGhostMode) ?? false;
    _noAds = _prefs!.getBool(_keyNoAds) ?? false;
    _noStories = _prefs!.getBool(_keyNoStories) ?? false;
    _noReels = _prefs!.getBool(_keyNoReels) ?? false;
    _noAutoplay = _prefs!.getBool(_keyNoAutoplay) ?? false;
    _noDMs = _prefs!.getBool(_keyNoDMs) ?? false;

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
    await _prefs?.setBool(_keyBlurExplore, v);

    if (_minimalModeEnabled) {
      await _checkAndAutoDisableMinimalMode();
    }

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

  Future<void> setBreathGateSeconds(int seconds) async {
    final clamped = seconds.clamp(3, 60);
    _breathGateSeconds = clamped.toInt();
    await _prefs?.setInt(_keyBreathGateSeconds, _breathGateSeconds);
    // Defer notifyListeners to after the current frame to avoid
    // Flutter's 'Dependents.isEmpty' assertion error.
    WidgetsBinding.instance.addPostFrameCallback((_) => notifyListeners());
  }

  Future<void> setWordChallengeCount(int count) async {
    _wordChallengeCount = _normaliseWordChallengeCount(count);
    await _prefs?.setInt(_keyWordChallengeCount, _wordChallengeCount);
    notifyListeners();
  }

  int resolvedWordChallengeCount() {
    if (_wordChallengeCount != 0) return _wordChallengeCount;
    final now = DateTime.now().microsecondsSinceEpoch;
    return 10 + (now % 26);
  }

  static int _normaliseWordChallengeCount(int count) {
    if (count == 0) return 0;
    const allowed = [20, 25, 30, 35];
    return allowed.contains(count) ? count : 30;
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

  // ── Extras (Phase 2) ──────────────────────────────────────────────────────

  Future<void> setVideoDownloadEnabled(bool v) async {
    _videoDownloadEnabled = v;
    await _prefs?.setBool(_keyVideoDownloadEnabled, v);
    notifyListeners();
  }

  Future<void> setHideSuggestedPosts(bool v) async {
    _hideSuggestedPosts = v;
    await _prefs?.setBool(_keyHideSuggestedPosts, v);
    notifyListeners();
  }

  Future<void> setGrayscaleEnabled(bool v) async {
    _grayscaleEnabled = v;
    await _prefs?.setBool(_keyGrayscaleEnabled, v);
    notifyListeners();
  }

  Future<void> setGrayscaleSchedules(
    List<Map<String, dynamic>> schedules,
  ) async {
    _grayscaleSchedules = schedules;
    await _prefs?.setString(_keyGrayscaleSchedules, jsonEncode(schedules));
    notifyListeners();
  }

  Future<void> addGrayscaleSchedule(Map<String, dynamic> schedule) async {
    _grayscaleSchedules.add(schedule);
    await _prefs?.setString(
      _keyGrayscaleSchedules,
      jsonEncode(_grayscaleSchedules),
    );
    notifyListeners();
  }

  Future<void> updateGrayscaleSchedule(
    int index,
    Map<String, dynamic> schedule,
  ) async {
    if (index >= 0 && index < _grayscaleSchedules.length) {
      _grayscaleSchedules[index] = schedule;
      await _prefs?.setString(
        _keyGrayscaleSchedules,
        jsonEncode(_grayscaleSchedules),
      );
      notifyListeners();
    }
  }

  Future<void> removeGrayscaleSchedule(int index) async {
    if (index >= 0 && index < _grayscaleSchedules.length) {
      _grayscaleSchedules.removeAt(index);
      await _prefs?.setString(
        _keyGrayscaleSchedules,
        jsonEncode(_grayscaleSchedules),
      );
      notifyListeners();
    }
  }

  Future<void> setHideShopTab(bool v) async {
    _hideShopTab = v;
    await _prefs?.setBool(_keyHideShopTab, v);
    notifyListeners();
  }

  // ── FocusGram v2 overlay setters ──────────────────────────────────────────
  Future<void> setV2GhostModeEnabled(bool v) async {
    _v2GhostModeEnabled = v;
    await _prefs?.setBool(_keyV2GhostModeEnabled, v);
    notifyListeners();
  }

  Future<void> setV2AdBlockerDomEnabled(bool v) async {
    _v2AdBlockerDomEnabled = v;
    await _prefs?.setBool(_keyV2AdBlockerDomEnabled, v);
    notifyListeners();
  }

  Future<void> setV2ContentHiderEnabled(bool v) async {
    _v2ContentHiderEnabled = v;
    await _prefs?.setBool(_keyV2ContentHiderEnabled, v);
    notifyListeners();
  }

  Future<void> setContentStoriesEnabled(bool v) async {
    if (v && !_v2ContentHiderEnabled) {
      _v2ContentHiderEnabled = true;
      await _prefs?.setBool(_keyV2ContentHiderEnabled, true);
    }
    _contentStories = v;
    await _prefs?.setBool(_keyContentStories, v);
    notifyListeners();
  }

  Future<void> setContentPostsEnabled(bool v) async {
    if (v && !_v2ContentHiderEnabled) {
      _v2ContentHiderEnabled = true;
      await _prefs?.setBool(_keyV2ContentHiderEnabled, true);
    }
    _contentPosts = v;
    await _prefs?.setBool(_keyContentPosts, v);
    notifyListeners();
  }

  Future<void> setContentReelsEnabled(bool v) async {
    if (v && !_v2ContentHiderEnabled) {
      _v2ContentHiderEnabled = true;
      await _prefs?.setBool(_keyV2ContentHiderEnabled, true);
    }
    _contentReels = v;
    await _prefs?.setBool(_keyContentReels, v);
    notifyListeners();
  }

  Future<void> setContentSuggestedEnabled(bool v) async {
    if (v && !_v2ContentHiderEnabled) {
      _v2ContentHiderEnabled = true;
      await _prefs?.setBool(_keyV2ContentHiderEnabled, true);
    }
    _contentSuggested = v;
    await _prefs?.setBool(_keyContentSuggested, v);
    notifyListeners();
  }

  Future<void> setHideFollowerCounts(bool v) async {
    _hideFollowerCounts = v;
    await _prefs?.setBool(_keyHideFollowerCounts, v);
    notifyListeners();
  }

  /// Setter for internal disable reels state (used by minimal mode submenu)
  /// Auto-disables minimal mode if all features are turned off
  Future<void> setDisableReelsEntirelyInternal(bool v) async {
    _disableReelsEntirely = v;
    await _prefs?.setBool('internal_disable_reels_entirely', v);

    // Check if minimal mode should auto-disable
    await _checkAndAutoDisableMinimalMode();

    notifyListeners();
  }

  /// Setter for internal disable explore state (used by minimal mode submenu)
  /// Auto-disables minimal mode if all features are turned off
  Future<void> setDisableExploreEntirelyInternal(bool v) async {
    _disableExploreEntirely = v;
    await _prefs?.setBool('internal_disable_explore_entirely', v);

    // Check if minimal mode should auto-disable
    await _checkAndAutoDisableMinimalMode();

    notifyListeners();
  }

  /// Setter for home feed scroll blocking state (used by minimal mode submenu).
  Future<void> setBlockHomeFeedScrollInternal(bool v) async {
    _blockHomeFeedScroll = v;
    await _prefs?.setBool('internal_block_home_feed_scroll', v);

    await _checkAndAutoDisableMinimalMode();

    notifyListeners();
  }

  /// Helper: Auto-disable minimal mode if all its features are disabled
  /// This ensures minimal mode auto-turns-off when user disables all sub-features
  ///
  /// NOTE: We must check the RAW state variables here, NOT the public getters
  /// (disableReelsEntirely/disableExploreEntirely), because those getters
  /// unconditionally return true when _minimalModeEnabled is true, which would
  /// make the "all disabled" condition impossible to reach.
  Future<void> _checkAndAutoDisableMinimalMode() async {
    if (!_minimalModeEnabled) return;

    // Check the RAW saved state, not the getters
    final rawReels =
        _prefs?.getBool('internal_disable_reels_entirely') ??
        _disableReelsEntirely;
    final rawExplore =
        _prefs?.getBool('internal_disable_explore_entirely') ??
        _disableExploreEntirely;

    final rawHomeFeedScroll =
        _prefs?.getBool('internal_block_home_feed_scroll') ??
        _blockHomeFeedScroll;

    final allDisabled =
        !rawReels && !rawExplore && !rawHomeFeedScroll && !_blurExplore;

    if (allDisabled) {
      _minimalModeEnabled = false;
      await _prefs?.setBool(_keyMinimalModeEnabled, false);
    }
  }

  /// Smart minimal mode toggle with state preservation
  Future<void> setMinimalModeEnabled(bool v) async {
    if (v) {
      // ── Turning ON ──────────────────────────────────────────────────────────
      // Save current pre-minimal-mode states so we can restore them later
      _prevDisableReels = _disableReelsEntirely;
      _prevDisableExplore = _disableExploreEntirely;
      _prevBlurExplore = _blurExplore;
      _prevBlockHomeFeedScroll = _blockHomeFeedScroll;

      await _prefs?.setBool(_keyMinimalModePrevDisableReels, _prevDisableReels);
      await _prefs?.setBool(
        _keyMinimalModePrevDisableExplore,
        _prevDisableExplore,
      );
      await _prefs?.setBool(_keyMinimalModePrevBlurExplore, _prevBlurExplore);
      await _prefs?.setBool(
        _keyMinimalModePrevBlockHomeFeedScroll,
        _prevBlockHomeFeedScroll,
      );

      _minimalModeEnabled = true;
      _disableReelsEntirely = true;
      _disableExploreEntirely = true;
      _blockHomeFeedScroll = true;
      _blurExplore = true; // blurExplore is controlled by minimal mode while ON

      await _prefs?.setBool(_keyMinimalModeEnabled, true);
      await _prefs?.setBool('internal_disable_reels_entirely', true);
      await _prefs?.setBool('internal_disable_explore_entirely', true);
      await _prefs?.setBool('internal_block_home_feed_scroll', true);
      await _prefs?.setBool(_keyBlurExplore, true);
    } else {
      // ── Turning OFF ─────────────────────────────────────────────────────────
      // Restore states that were saved BEFORE minimal mode was enabled.
      // _prevDisableReels/Explore were saved at the moment minimal mode turned ON.
      _minimalModeEnabled = false;
      _disableReelsEntirely = _prevDisableReels;
      _disableExploreEntirely = _prevDisableExplore;
      _blockHomeFeedScroll = _prevBlockHomeFeedScroll;
      // For blurExplore: use _prevBlurExplore if it was saved, otherwise fall back
      // to the saved prefs value (covers the case where no prev was saved).
      _blurExplore = _prevBlurExplore;

      await _prefs?.setBool(_keyMinimalModeEnabled, false);
      await _prefs?.setBool(
        'internal_disable_reels_entirely',
        _disableReelsEntirely,
      );
      await _prefs?.setBool(
        'internal_disable_explore_entirely',
        _disableExploreEntirely,
      );
      await _prefs?.setBool(
        'internal_block_home_feed_scroll',
        _blockHomeFeedScroll,
      );
      await _prefs?.setBool(_keyBlurExplore, _blurExplore);

      // After restoring, check whether the user had ALL minimal features OFF
      // already — if so, minimal mode should stay off (no-op).
      if (!_disableReelsEntirely &&
          !_disableExploreEntirely &&
          !_blockHomeFeedScroll &&
          !_blurExplore) {
        // All features are off — minimal mode correctly stays off. No action needed.
      }
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
    if (v) await NotificationService().requestPermissionsNow();
    notifyListeners();
  }

  Future<void> setNotifyActivity(bool v) async {
    _notifyActivity = v;
    await _prefs?.setBool(_keyNotifyActivity, v);
    if (v) await NotificationService().requestPermissionsNow();
    notifyListeners();
  }

  Future<void> setNotifySessionEnd(bool v) async {
    _notifySessionEnd = v;
    await _prefs?.setBool(_keyNotifySessionEnd, v);
    if (v) await NotificationService().requestPermissionsNow();
    notifyListeners();
  }

  Future<void> setNotifyPersistent(bool v) async {
    _notifyPersistent = v;
    await _prefs?.setBool(_keyNotifyPersistent, v);
    if (v) {
      await NotificationService().requestPermissionsNow();
    } else {
      await NotificationService().cancelPersistentNotification(id: 5001);
    }
    notifyListeners();
  }

  // ── Startup page ─────────────────────────────────────────────────────────────
  Future<void> setStartupPage(String page) async {
    _startupPage = page;
    await _prefs?.setString(_keyStartupPage, page);
    notifyListeners();
  }

  // ── Adsterra zone config ────────────────────────────────────────────────────
  Future<void> setAdsterraZoneUrl(String url) async {
    _adsterraZoneUrl = url;
    await _prefs?.setString(_keyAdsterraZoneUrl, url);
    notifyListeners();
  }

  Future<void> setAdsterraAdCode(String code) async {
    _adsterraAdCode = code;
    await _prefs?.setString(_keyAdsterraAdCode, code);
    notifyListeners();
  }

  // ── Focus mode settings ──────────────────────────────────────────────────────
  Future<void> setEffortFrictionEnabled(bool v) async {
    _effortFrictionEnabled = v;
    await _prefs?.setBool(_keyEffortFrictionEnabled, v);
    notifyListeners();
  }

  Future<void> setGhostMode(bool v) async {
    _ghostMode = v;
    await _prefs?.setBool(_keyGhostMode, v);
    notifyListeners();
  }

  Future<void> setNoAds(bool v) async {
    _noAds = v;
    await _prefs?.setBool(_keyNoAds, v);
    notifyListeners();
  }

  Future<void> setNoStories(bool v) async {
    _noStories = v;
    await _prefs?.setBool(_keyNoStories, v);
    notifyListeners();
  }

  Future<void> setNoReels(bool v) async {
    _noReels = v;
    await _prefs?.setBool(_keyNoReels, v);
    notifyListeners();
  }

  Future<void> setNoAutoplay(bool v) async {
    _noAutoplay = v;
    await _prefs?.setBool(_keyNoAutoplay, v);
    notifyListeners();
  }

  Future<void> setNoDMs(bool v) async {
    _noDMs = v;
    await _prefs?.setBool(_keyNoDMs, v);
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
