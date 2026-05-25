import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:focusgram/services/settings_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('SettingsService — Phase 2 Extras', () {
    test('defaults are OFF for video download/hide suggested', () async {
      final s = SettingsService();
      await s.init();

      expect(s.videoDownloadEnabled, isFalse);
      expect(s.hideSuggestedPosts, isFalse);
    });

    test('setVideoDownloadEnabled persists', () async {
      final s = SettingsService();
      await s.init();

      await s.setVideoDownloadEnabled(true);

      final prefs = await SharedPreferences.getInstance();
      expect(s.videoDownloadEnabled, isTrue);
      expect(prefs.getBool('video_download_enabled'), isTrue);
    });

    test('setHideSuggestedPosts persists', () async {
      final s = SettingsService();
      await s.init();

      await s.setHideSuggestedPosts(true);

      final prefs = await SharedPreferences.getInstance();
      expect(s.hideSuggestedPosts, isTrue);
      expect(prefs.getBool('hide_suggested_posts'), isTrue);
    });
  });

  group('SettingsService — minimal mode', () {
    test(
      'home feed scroll can be disabled while minimal mode stays on',
      () async {
        final s = SettingsService();
        await s.init();

        await s.setMinimalModeEnabled(true);
        await s.setBlockHomeFeedScrollInternal(false);

        expect(s.minimalModeEnabled, isTrue);
        expect(s.blockHomeFeedScroll, isFalse);

        final prefs = await SharedPreferences.getInstance();
        expect(prefs.getBool('internal_block_home_feed_scroll'), isFalse);
        expect(prefs.getBool('minimal_mode_enabled'), isTrue);
      },
    );

    test(
      'minimal mode turns off when all child features are disabled',
      () async {
        final s = SettingsService();
        await s.init();

        await s.setMinimalModeEnabled(true);
        await s.setBlurExplore(false);
        await s.setBlockHomeFeedScrollInternal(false);
        await s.setDisableReelsEntirelyInternal(false);
        await s.setDisableExploreEntirelyInternal(false);

        expect(s.minimalModeEnabled, isFalse);
        expect(s.blurExplore, isFalse);
        expect(s.blockHomeFeedScroll, isFalse);
        expect(s.disableReelsEntirely, isFalse);
        expect(s.disableExploreEntirely, isFalse);
      },
    );
  });

  group('SettingsService — v2 filtering split', () {
    test(
      'ad blocker and suggested posts toggles persist independently',
      () async {
        final s = SettingsService();
        await s.init();

        await s.setV2AdBlockerDomEnabled(true);
        await s.setContentSuggestedEnabled(true);
        await s.setV2AdBlockerDomEnabled(false);

        expect(s.v2AdBlockerDomEnabled, isFalse);
        expect(s.contentSuggested, isTrue);
        expect(s.v2ContentHiderEnabled, isTrue);

        final prefs = await SharedPreferences.getInstance();
        expect(prefs.getBool('v2_adblock_dom_enabled'), isFalse);
        expect(prefs.getBool('content_suggested'), isTrue);
      },
    );
  });
}
