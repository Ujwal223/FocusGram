/// JS to help Instagram's layout detect viewport changes when the Android
/// soft keyboard appears in a WebView container.
///
/// It listens for resize events and re-dispatches an `orientationchange`
/// event, which nudges Instagram's layout system out of the DM loading
/// spinner state.
const String kDmKeyboardFixJS = r'''
  // Fix: tell Instagram's layout system the viewport has changed after keyboard events
  // This resolves the loading state that appears on DM screens in WebView
  window.addEventListener('resize', function() {
    try {
      window.dispatchEvent(new Event('orientationchange'));
    } catch (_) {}
  });
''';

