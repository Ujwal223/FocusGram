/**
 * FocusGram Theme Detector
 * Reads Instagram's background + bottom nav color and reports to Flutter.
 * Injected at DOCUMENT_END so DOM is ready.
 */
(function () {
  'use strict';

  const parseRgb = (str) => {
    // Parses "rgb(255, 255, 255)" or "rgba(0, 0, 0, 1)" → { r, g, b, a }
    const m = str.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/);
    if (!m) return null;
    return {
      r: parseInt(m[1]),
      g: parseInt(m[2]),
      b: parseInt(m[3]),
      a: m[4] !== undefined ? parseFloat(m[4]) : 1,
    };
  };

  const toHex = ({ r, g, b }) =>
    '#' +
    [r, g, b].map((v) => v.toString(16).padStart(2, '0')).join('');

  const detectColors = () => {
    // Background — Instagram sets it on <body> or a root div
    const bodyBg = getComputedStyle(document.body).backgroundColor;

    // Bottom nav — IG mobile web renders a fixed bottom bar
    // Target by role="navigation" or position:fixed at bottom
    let navBg = bodyBg;
    const navCandidates = document.querySelectorAll(
      'nav, [role="navigation"], div[style*="bottom"]'
    );
    for (const el of navCandidates) {
      const style = getComputedStyle(el);
      if (
        style.position === 'fixed' &&
        parseInt(style.bottom) <= 10 &&
        style.backgroundColor !== 'rgba(0, 0, 0, 0)'
      ) {
        navBg = style.backgroundColor;
        break;
      }
    }

    const bodyColor = parseRgb(bodyBg);
    const navColor = parseRgb(navBg);

    if (!bodyColor) return;

    // Determine dark/light
    const luminance = (0.299 * bodyColor.r + 0.587 * bodyColor.g + 0.114 * bodyColor.b) / 255;
    const isDark = luminance < 0.5;

    const payload = {
      bodyHex: toHex(bodyColor),
      navHex: navColor ? toHex(navColor) : toHex(bodyColor),
      isDark,
    };

    if (window.ThemeChannel) {
      window.ThemeChannel.postMessage(JSON.stringify(payload));
    }
  };

  // Run on load
  detectColors();

  // Watch for Instagram's dark mode toggle (adds/removes class on <html>)
  const observer = new MutationObserver(detectColors);
  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class', 'style', 'color-scheme'],
  });
  observer.observe(document.body, {
    attributes: true,
    attributeFilter: ['class', 'style'],
  });

  // Also run after navigation (Instagram is SPA, URL changes without reload)
  let lastUrl = location.href;
  new MutationObserver(() => {
    if (location.href !== lastUrl) {
      lastUrl = location.href;
      setTimeout(detectColors, 300); // small delay for IG to render new page
    }
  }).observe(document.body, { childList: true, subtree: true });
})();
