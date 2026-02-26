/// JS to improve momentum scrolling behaviour inside the WebView, especially
/// for content-heavy feeds like Reels.
///
/// Applies touch-style overflow scrolling hints to the root element.
const String kScrollSmoothingJS = r'''
  (function fgScrollSmoothing() {
    try {
      document.documentElement.style.setProperty('-webkit-overflow-scrolling', 'touch');
      document.documentElement.style.setProperty('overflow-scrolling', 'touch');
    } catch (_) {}
  })();
''';

