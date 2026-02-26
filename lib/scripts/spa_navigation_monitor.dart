const String kSpaNavigationMonitorScript = '''
(function() {
  // Monitor Instagram's SPA navigation and notify Flutter on every URL change.
  // Instagram uses history.pushState — onLoadStop won't fire for these transitions.
  // This is injected at document start so it wraps pushState before Instagram does.
  
  const originalPushState = history.pushState;
  const originalReplaceState = history.replaceState;
  
  function notifyUrlChange(url) {
    if (window.flutter_inappwebview) {
      window.flutter_inappwebview.callHandler(
        'UrlChange',
        url || window.location.href
      );
    }
  }
  
  history.pushState = function() {
    originalPushState.apply(this, arguments);
    setTimeout(() => notifyUrlChange(arguments[2]), 100);
  };
  
  history.replaceState = function() {
    originalReplaceState.apply(this, arguments);
    setTimeout(() => notifyUrlChange(arguments[2]), 100);
  };
  
  window.addEventListener('popstate', () => notifyUrlChange());
})();
''';

