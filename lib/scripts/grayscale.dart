/// Grayscale style injector.
/// Uses a <style> tag with !important so Instagram's CSS cannot override it.
const String kGrayscaleJS = r'''
(function fgGrayscale() {
  try {
    const ID = 'fg-grayscale';
    function inject() {
      let el = document.getElementById(ID);
      if (!el) {
        el = document.createElement('style');
        el.id = ID;
        (document.head || document.documentElement).appendChild(el);
      }
      el.textContent = 'html { filter: grayscale(100%) !important; }';
    }
    inject();
    if (!window.__fgGrayscaleObserver) {
      window.__fgGrayscaleObserver = new MutationObserver(() => {
        if (!document.getElementById('fg-grayscale')) inject();
      });
      window.__fgGrayscaleObserver.observe(
        document.documentElement,
        { childList: true, subtree: true }
      );
    }
  } catch (_) {}
})();
''';

/// Removes grayscale AND disconnects the observer so it cannot re-add it.
/// Previously kGrayscaleOffJS only removed the style tag — the observer
/// immediately re-injected it, requiring an app restart to actually go off.
const String kGrayscaleOffJS = r'''
(function() {
  try {
    // 1. Disconnect the observer FIRST so it cannot react to the removal
    if (window.__fgGrayscaleObserver) {
      window.__fgGrayscaleObserver.disconnect();
      window.__fgGrayscaleObserver = null;
    }
    // 2. Remove the style tag
    const el = document.getElementById('fg-grayscale');
    if (el) el.remove();
    // 3. Clear any inline filter that may have been set by older code
    document.documentElement.style.filter = '';
  } catch (_) {}
})();
''';
