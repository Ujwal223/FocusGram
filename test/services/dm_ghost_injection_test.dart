import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:focusgram/focus_settings.dart';
import 'package:focusgram/scripts/focus_scripts.dart';

void main() {
  group('FocusSettings — Field cleanup', () {
    test('only ghostMode remains (fullDmGhost and storyGhost removed)',
        () async {
      const settings = FocusSettings(ghostMode: true);

      expect(settings.ghostMode, isTrue);
      expect(settings.noAds, isTrue);
      expect(settings.noStories, isFalse);
      expect(settings.noReels, isFalse);
      expect(settings.noAutoplay, isFalse);
      expect(settings.noDMs, isFalse);

      // Verify fullDmGhost and storyGhost are NOT fields anymore
      // (these would be compile errors if they existed)
    });

    test('default ghostMode is false', () async {
      const settings = FocusSettings();
      expect(settings.ghostMode, isFalse);
    });
  });

  group('buildUserScripts — DM Ghost injection', () {
    test('injects kFullDmGhostJS when ghostMode is true', () async {
      final scripts = buildUserScripts(const FocusSettings(ghostMode: true));

      expect(scripts.length, equals(1));
      expect(
        scripts[0].injectionTime,
        equals(UserScriptInjectionTime.AT_DOCUMENT_START),
      );

      // Verify the comprehensive Full DM ghost JS is injected
      final src = scripts[0].source;
      expect(src, contains('__fgFullDmGhost=true'));
      expect(src, contains('__fgFullDmGhostPatched'));
      expect(src, contains('shouldBlockDmPath'));
      expect(src, contains('DM_URLS'));
      expect(src, contains('DM_OPS'));
      expect(src, contains('serviceWorker'));
      expect(src, contains('sendBeacon'));
    });

    test('does NOT inject ghost scripts when ghostMode is false', () async {
      final scripts =
          buildUserScripts(const FocusSettings(ghostMode: false));

      // Should have no DOCUMENT_START scripts
      final startScripts =
          scripts.where((s) =>
              s.injectionTime == UserScriptInjectionTime.AT_DOCUMENT_START);
      for (final s in startScripts) {
        expect(s.source.contains('__fgFullDmGhost'), isFalse);
      }
    });

    test('injects noAutoplay alongside DM Ghost', () async {
      final scripts = buildUserScripts(
        const FocusSettings(ghostMode: true, noAutoplay: true),
      );

      final startScripts =
          scripts.where((s) =>
              s.injectionTime == UserScriptInjectionTime.AT_DOCUMENT_START);
      expect(startScripts.length, equals(1));
      expect(startScripts.first.source, contains('__fgFullDmGhost=true'));
      expect(startScripts.first.source, contains('document.addEventListener'));
    });

    test('injects hideStoryTray at DOCUMENT_END when noStories is true',
        () async {
      final scripts = buildUserScripts(
        const FocusSettings(noStories: true),
      );

      final endScripts =
          scripts.where((s) =>
              s.injectionTime == UserScriptInjectionTime.AT_DOCUMENT_END);
      expect(endScripts.length, equals(1));
      expect(
        endScripts.first.source,
        contains('[data-pagelet="story_tray"]'),
      );
    });
  });
}
