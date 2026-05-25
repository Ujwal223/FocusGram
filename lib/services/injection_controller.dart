// ============================================================================
// FocusGram — InjectionController
// ============================================================================

import '../scripts/core_injection.dart' as scripts;
import '../scripts/ui_hider.dart' as ui_hider;

class InjectionController {
  static const String iOSUserAgent =
      'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) '
      'AppleWebKit/605.1.15 (KHTML, like Gecko) '
      'Version/26.0 Mobile/15E148 Safari/604.1';

  static const String reelsMutationObserverJS =
      scripts.kReelsMutationObserverJS;
  static const String linkSanitizationJS = scripts.kLinkSanitizationJS;
  static String get notificationBridgeJS => scripts.kNotificationBridgeJS;

  static String _buildMutationObserver(String cssContent) =>
      '''
    (function fgApplyStyles() {
      const ID = 'focusgram-style';
      function inject() {
        let el = document.getElementById(ID);
        if (!el) {
          el = document.createElement('style');
          el.id = ID;
          (document.head || document.documentElement).appendChild(el);
        }
        el.textContent = ${_escapeJsString(cssContent)};
      }
      inject();
      new MutationObserver(() => { if (!document.getElementById(ID)) inject(); })
        .observe(document.documentElement, { childList: true, subtree: true });
    })();
  ''';

  static String _escapeJsString(String s) {
    final escaped = s.replaceAll(r'\', r'\\').replaceAll('`', r'\`');
    return '`$escaped`';
  }

  static String softNavigateJS(String path) =>
      '''
    (function() {
      const t = ${_escapeJsString(path)};
      if (window.location.pathname !== t) window.location.href = t;
    })();
  ''';

  static String buildSessionStateJS(bool active) =>
      'window.__focusgramSessionActive = $active;';

  static String buildInjectionJS({
    required bool sessionActive,
    required bool blurExplore,
    required bool blurReels,
    required bool tapToUnblur,
    required bool enableTextSelection,
    required bool hideSuggestedPosts,
    required bool hideSponsoredPosts,
    required bool hideLikeCounts,
    required bool hideFollowerCounts,
    required bool hideExploreTab,
    required bool hideReelsTab,
    required bool hideShopTab,
    required bool disableReelsEntirely,
    required bool blockHomeFeedScroll,
  }) {
    final css = StringBuffer()..writeln(scripts.kGlobalUIFixesCSS);
    if (!enableTextSelection) css.writeln(scripts.kDisableSelectionCSS);

    if (!sessionActive) {
      // Hide reel feed content when no session active
      css.writeln(scripts.kHideReelsFeedContentCSS);
    }

    if (blurReels) css.writeln(scripts.kBlurReelsCSS);

    if (blurExplore) css.writeln(scripts.kBlurHomeFeedAndExploreCSS);

    if (hideLikeCounts) css.writeln(ui_hider.kHideLikeCountsCSS);
    if (hideFollowerCounts) css.writeln(ui_hider.kHideFollowerCountsCSS);
    if (hideExploreTab) css.writeln(ui_hider.kHideExploreTabCSS);
    if (hideReelsTab) css.writeln(ui_hider.kHideReelsTabCSS);
    if (hideShopTab) css.writeln(ui_hider.kHideShopTabCSS);

    return '''
      ${buildSessionStateJS(sessionActive)}
      window.__fgDisableReelsEntirely = $disableReelsEntirely;
      window.__fgBlockHomeFeedScroll = $blockHomeFeedScroll;
      window.__fgTapToUnblur = $tapToUnblur;
      ${scripts.kTrackPathJS}
      ${_buildMutationObserver(css.toString())}
      ${scripts.kDismissAppBannerJS}
      ${!sessionActive ? scripts.kStrictReelsBlockJS : ''}
      ${scripts.kReelsMutationObserverJS}
      ${tapToUnblur ? scripts.kTapToUnblurJS : ''}
      ${scripts.kLinkSanitizationJS}
      ${scripts.kThemeDetectorJS}
      ${scripts.kBadgeMonitorJS}
    ''';
  }
}
