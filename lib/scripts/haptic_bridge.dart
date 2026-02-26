const String kHapticBridgeScript = '''
(function() {
  // Trigger native haptic feedback on double-tap (like gesture on posts)
  // Uses flutter_inappwebview's callHandler instead of postMessage
  document.addEventListener('dblclick', function(e) {
    if (window.flutter_inappwebview) {
      window.flutter_inappwebview.callHandler('Haptic', 'light');
    }
  }, true);
})();
''';

