/**
 * FocusGram Theme Detector
 * Reads light/dark theme from page and bridges to Flutter.
 * Injected at DOCUMENT_END.
 */
(function () {
  'use strict';

  (function fgThemeSync() {
    if (window.__fgThemeSyncRunning) return;
    window.__fgThemeSyncRunning = true;

    function getTheme() {
      try {
        const h = document.documentElement;
        if (h.classList.contains('style-dark')) return 'dark';
        if (h.classList.contains('style-light')) return 'light';

        const bg = window.getComputedStyle(document.body).backgroundColor;
        const rgb = bg.match(/\d+/g);
        if (rgb && rgb.length >= 3) {
          const luminance =
            (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]) / 255;
          return luminance < 0.5 ? 'dark' : 'light';
        }
      } catch (_) {}
      return 'dark';
    }

    let last = '';
    function check() {
      const current = getTheme();
      if (current !== last) {
        last = current;
        if (window.flutter_inappwebview) {
          window.flutter_inappwebview.callHandler(
            'FocusGramThemeChannel',
            current
          );
        }
      }
    }

    setInterval(check, 1500);
    check();
  })();
})();
