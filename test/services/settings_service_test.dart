// test/services/settings_service_test.dart
//
// Tests for SettingsService — default values, setters, tab management,
// and the legacy GhostMode key migration.
//
// Note: SettingsService requires SharedPreferences. We use
// SharedPreferences.setMockInitialValues({}) to avoid platform channel calls.
//
// Run with: flutter test test/services/settings_service_test.dart

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:focusgram/services/settings_service.dart';

/// Helper: create an initialised SettingsService with a clean prefs slate.
Future<SettingsService> makeService() async {
  SharedPreferences.setMockInitialValues({});
  final svc = SettingsService();
  await svc.init();
  return svc;
}

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  // ── Default values ──────────────────────────────────────────────────────

  group('SettingsService — defaults', () {
    test('blurExplore defaults to true', () async {
      expect((await makeService()).blurExplore, isTrue);
    });

    test('blurReels defaults to false', () async {
      expect((await makeService()).blurReels, isFalse);
    });

    test('requireLongPress defaults to true', () async {
      expect((await makeService()).requireLongPress, isTrue);
    });

    test('showBreathGate defaults to true', () async {
      expect((await makeService()).showBreathGate, isTrue);
    });

    test('requireWordChallenge defaults to true', () async {
      expect((await makeService()).requireWordChallenge, isTrue);
    });

    test('enableTextSelection defaults to false', () async {
      expect((await makeService()).enableTextSelection, isFalse);
    });

    test('ghostTyping defaults to true', () async {
      expect((await makeService()).ghostTyping, isTrue);
    });

    test('ghostSeen defaults to true', () async {
      expect((await makeService()).ghostSeen, isTrue);
    });

    test('ghostStories defaults to true', () async {
      expect((await makeService()).ghostStories, isTrue);
    });

    test('ghostDmPhotos defaults to true', () async {
      expect((await makeService()).ghostDmPhotos, isTrue);
    });

    test('sanitizeLinks defaults to true', () async {
      expect((await makeService()).sanitizeLinks, isTrue);
    });

    test('isFirstRun defaults to true', () async {
      expect((await makeService()).isFirstRun, isTrue);
    });

    test(
      'anyGhostModeEnabled is true when all ghost settings are true',
      () async {
        expect((await makeService()).anyGhostModeEnabled, isTrue);
      },
    );
  });

  // ── Setters ─────────────────────────────────────────────────────────────

  group('SettingsService — setters persist and notify', () {
    test('setBlurExplore changes value and notifies', () async {
      final svc = await makeService();
      bool notified = false;
      svc.addListener(() => notified = true);
      await svc.setBlurExplore(false);
      expect(svc.blurExplore, isFalse);
      expect(notified, isTrue);
    });

    test('setBlurReels persists', () async {
      final svc = await makeService();
      await svc.setBlurReels(true);
      expect(svc.blurReels, isTrue);
    });

    test('setRequireLongPress persists', () async {
      final svc = await makeService();
      await svc.setRequireLongPress(false);
      expect(svc.requireLongPress, isFalse);
    });

    test('setGhostTyping turns off ghost typing', () async {
      final svc = await makeService();
      await svc.setGhostTyping(false);
      expect(svc.ghostTyping, isFalse);
    });

    test('setGhostSeen turns off ghost seen', () async {
      final svc = await makeService();
      await svc.setGhostSeen(false);
      expect(svc.ghostSeen, isFalse);
    });

    test('setGhostStories turns off ghost stories', () async {
      final svc = await makeService();
      await svc.setGhostStories(false);
      expect(svc.ghostStories, isFalse);
    });

    test('setGhostDmPhotos turns off ghost dm photos', () async {
      final svc = await makeService();
      await svc.setGhostDmPhotos(false);
      expect(svc.ghostDmPhotos, isFalse);
    });

    test('setSanitizeLinks persists', () async {
      final svc = await makeService();
      await svc.setSanitizeLinks(false);
      expect(svc.sanitizeLinks, isFalse);
    });

    test('setFirstRunCompleted sets isFirstRun to false', () async {
      final svc = await makeService();
      await svc.setFirstRunCompleted();
      expect(svc.isFirstRun, isFalse);
    });

    test('setEnableTextSelection persists', () async {
      final svc = await makeService();
      await svc.setEnableTextSelection(true);
      expect(svc.enableTextSelection, isTrue);
    });
  });

  // ── anyGhostModeEnabled ──────────────────────────────────────────────────

  group('SettingsService.anyGhostModeEnabled', () {
    test('is false when all ghost flags are off', () async {
      final svc = await makeService();
      await svc.setGhostTyping(false);
      await svc.setGhostSeen(false);
      await svc.setGhostStories(false);
      await svc.setGhostDmPhotos(false);
      expect(svc.anyGhostModeEnabled, isFalse);
    });

    test('is true when only one ghost flag is on', () async {
      final svc = await makeService();
      await svc.setGhostTyping(false);
      await svc.setGhostSeen(false);
      await svc.setGhostStories(false);
      await svc.setGhostDmPhotos(true); // only dmPhotos on
      expect(svc.anyGhostModeEnabled, isTrue);
    });
  });

  // ── Tab management ───────────────────────────────────────────────────────

  group('SettingsService — tab management', () {
    test('default tabs include Home, Reels, Messages, Profile', () async {
      final svc = await makeService();
      expect(
        svc.enabledTabs,
        containsAll(['Home', 'Reels', 'Messages', 'Profile']),
      );
    });

    test('toggleTab removes an enabled tab', () async {
      final svc = await makeService();
      final before = List<String>.from(svc.enabledTabs);
      await svc.toggleTab('Reels');
      expect(svc.enabledTabs, isNot(contains('Reels')));
      expect(svc.enabledTabs.length, before.length - 1);
    });

    test('toggleTab adds a tab back when toggled again', () async {
      final svc = await makeService();
      await svc.toggleTab('Reels');
      await svc.toggleTab('Reels');
      expect(svc.enabledTabs, contains('Reels'));
    });

    test('toggleTab does not remove the last remaining tab', () async {
      final svc = await makeService();
      final tabs = List<String>.from(svc.enabledTabs);
      for (final t in tabs.sublist(0, tabs.length - 1)) {
        await svc.toggleTab(t);
      }
      final last = svc.enabledTabs.first;
      await svc.toggleTab(last); // try to remove the last one
      expect(svc.enabledTabs.length, 1); // still 1
    });

    test('reorderTab moves item correctly — no tabs are lost', () async {
      final svc = await makeService();
      final original = List<String>.from(svc.enabledTabs);
      await svc.reorderTab(0, 1);
      expect(svc.enabledTabs.toSet(), original.toSet());
    });
  });

  // ── Legacy Ghost Mode migration ──────────────────────────────────────────

  group('SettingsService — legacy ghost mode migration', () {
    test(
      'migrates legacy ghost_mode=true to all four granular flags',
      () async {
        SharedPreferences.setMockInitialValues({'set_ghost_mode': true});
        final svc = SettingsService();
        await svc.init();
        expect(svc.ghostTyping, isTrue);
        expect(svc.ghostSeen, isTrue);
        expect(svc.ghostStories, isTrue);
        expect(svc.ghostDmPhotos, isTrue);
      },
    );

    test(
      'migrates legacy ghost_mode=false to all four granular flags off',
      () async {
        SharedPreferences.setMockInitialValues({'set_ghost_mode': false});
        final svc = SettingsService();
        await svc.init();
        expect(svc.ghostTyping, isFalse);
        expect(svc.ghostSeen, isFalse);
        expect(svc.ghostStories, isFalse);
        expect(svc.ghostDmPhotos, isFalse);
      },
    );
  });
}
