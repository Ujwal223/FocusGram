import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:focusgram/services/level_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    if (!Hive.isAdapterRegistered(0)) {
      await Hive.initFlutter();
    }
  });

  group('AppFeature — Your Journey unlock table', () {
    test('fullDmGhost is NOT in the all list', () async {
      expect(AppFeature.fullDmGhost, isNotNull);

      final contains = AppFeature.all.any((f) => f.id == 'full_dm_ghost');
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
      final levelService = LevelService();

      await expectLater(() => levelService.init(), returnsNormally);

      expect(levelService.level, equals(1));
      expect(levelService.xp, equals(0));
    });

    test('addXpForAd awards XP without Firestore', () async {
      final levelService = LevelService();
      await levelService.init();

      await levelService.addXpForAd();

      expect(levelService.xp, greaterThan(0));
      expect(levelService.adsWatchedTotal, equals(1));
    });

    test('level progresses from XP', () async {
      final levelService = LevelService();
      await levelService.init();

      expect(levelService.level, equals(1));
      expect(levelService.xp, equals(0));
      expect(levelService.levelProgress, equals(0.0));
    });
  });
}
