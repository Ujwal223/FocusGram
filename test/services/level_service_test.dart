import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:hive/hive.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:focusgram/services/level_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    // Ensure Hive is available for LevelService
    if (!Hive.isAdapterRegistered(0)) {
      await Hive.initFlutter();
    }
  });

  group('AppFeature — Your Journey unlock table', () {
    test('fullDmGhost is NOT in the all list', () async {
      // fullDmGhost should still be defined as a constant
      expect(AppFeature.fullDmGhost, isNotNull);

      // But should NOT appear in the unlock table shown to users
      final contains = AppFeature.all.any(
        (f) => f.id == 'full_dm_ghost',
      );
      expect(contains, isFalse);
    });

    test('storyGhost and reelsHistory are NOT in the all list', () async {
      final hasStory = AppFeature.all.any((f) => f.id == 'custom_friction');
      final hasReels = AppFeature.all.any((f) => f.id == 'reels_history');
      expect(hasStory, isFalse);
      expect(hasReels, isFalse);
    });

    test('all list contains only active features', () async {
      final ids = AppFeature.all.map((f) => f.id).toSet();
      expect(ids, contains('ghost_mode'));
      expect(ids, contains('effort_friction'));
      expect(ids, contains('download_media'));
      expect(ids, contains('bait_me'));
      expect(ids, contains('app_lock'));
      expect(ids.length, equals(5));
    });
  });

  group('LevelService — No Firestore dependency', () {
    test('init succeeds without Firestore (uses Hive only)', () async {
      // This would crash if init tried to reach Firestore
      // Since we removed Firebase, it should work with just Hive cache
      final levelService = LevelService();

      // Should not throw — even if no Firestore is available
      await expectLater(
        () => levelService.init(),
        returnsNormally,
      );

      // Default state
      expect(levelService.level, equals(1));
      expect(levelService.xp, equals(0));
      expect(levelService.synced, isFalse);
    });

    test('addXpForAd awards XP without Firestore', () async {
      final levelService = LevelService();
      await levelService.init();

      await levelService.addXpForAd();

      expect(levelService.xp, greaterThan(0));
      expect(levelService.adsWatchedTotal, equals(1));
    });

    test('debugSetLevel works with Hive-only storage', () async {
      final levelService = LevelService();
      await levelService.init();

      await levelService.debugSetLevel(3, 300);

      expect(levelService.level, equals(3));
      expect(levelService.xp, equals(300));
    });
  });
}
