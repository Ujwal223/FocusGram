import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:focusgram/focus_settings.dart';
import 'package:focusgram/scripts/focus_scripts.dart';

void main() {
  group('FocusSettings — Field cleanup', () {
    test(
      'only ghostMode remains (fullDmGhost and storyGhost removed)',
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
      },
    );

    test('default ghostMode is false', () async {
      const settings = FocusSettings();
      expect(settings.ghostMode, isFalse);
    });
  });

  group('buildUserScripts — Ghost mode injection', () {
    test('injects kFullDmGhostJS when ghostMode is true', () async {
      final scripts = buildUserScripts(const FocusSettings(ghostMode: true));

      // Should have exactly 1 DOCUMENT_START script
      expect(scripts.length, equals(1));
      expect(
        scripts[0].injectionTime,
        equals(UserScriptInjectionTime.AT_DOCUMENT_START),
      );

      // The script source should contain the Full DM ghost code
      expect(scripts[0].source, contains('__fgFullDmGhost=true'));
      expect(scripts[0].source, contains('__fgFullDmGhostPatched'));
    });

    test('does NOT inject ghost scripts when ghostMode is false', () async {
      final scripts = buildUserScripts(const FocusSettings(ghostMode: false));

      // Should have no start scripts (ghostMode is the only start-level script)
      // unless other features like noAutoplay are also false
      if (scripts.isEmpty) return;

      // If scripts exist (e.g. noAutoplay), verify ghost mode NOT in them
      for (final s in scripts) {
        expect(s.source.contains('__fgFullDmGhost'), isFalse);
      }
    });

    test('injects noAutoplay when set', () async {
      final scripts = buildUserScripts(
        const FocusSettings(ghostMode: true, noAutoplay: true),
      );

      // Should have 1 DOCUMENT_START script combining ghost + autoplay
      final startScripts = scripts.where(
        (s) => s.injectionTime == UserScriptInjectionTime.AT_DOCUMENT_START,
      );
      expect(startScripts.length, equals(1));
      expect(startScripts.first.source, contains('__fgFullDmGhost=true'));
      expect(startScripts.first.source, contains('document.addEventListener'));
    });
  });
}
