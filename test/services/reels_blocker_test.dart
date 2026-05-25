import 'package:flutter_test/flutter_test.dart';
import 'package:focusgram/services/injection_controller.dart';

void main() {
  group('InjectionController reels blocker', () {
    test('includes strict reels blocker JS when sessionActive=false', () {
      final js = InjectionController.buildInjectionJS(
        sessionActive: false,
        blurExplore: false,
        blurReels: false,
        tapToUnblur: false,
        enableTextSelection: false,
        hideSuggestedPosts: false,
        hideSponsoredPosts: false,
        hideLikeCounts: false,
        hideFollowerCounts: false,
        hideExploreTab: false,
        hideReelsTab: false,
        hideShopTab: false,
        disableReelsEntirely: false,
        blockHomeFeedScroll: false,
      );

      expect(js, contains('window.__fgReelsBlockPatched'));
      expect(js, contains("window.location.href = '/reels/?fg=blocked';"));
    });

    test(
      'does NOT include strict reels blocker JS when sessionActive=true',
      () {
        final js = InjectionController.buildInjectionJS(
          sessionActive: true,
          blurExplore: false,
          blurReels: false,
          tapToUnblur: false,
          enableTextSelection: false,
          hideSuggestedPosts: false,
          hideSponsoredPosts: false,
          hideLikeCounts: false,
          hideFollowerCounts: false,
          hideExploreTab: false,
          hideReelsTab: false,
          hideShopTab: false,
          disableReelsEntirely: false,
          blockHomeFeedScroll: false,
        );

        expect(js, isNot(contains('window.__fgReelsBlockPatched')));
        expect(
          js,
          isNot(contains("window.location.href = '/reels/?fg=blocked';")),
        );
      },
    );
  });
}
