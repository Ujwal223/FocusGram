/**
 * FocusGram DOM Ad Blocker (Fallback)
 * 
 * DEPRECATED: Use fetch_interceptor.js for reliable ad blocking.
 * 
 * This script provides DOM-based ad removal as a FALLBACK for ads that slip through
 * GraphQL filtering. It's not reliable because Instagram has already rendered the content.
 * 
 * Injected at DOCUMENT_END
 * Removes sponsored/posts/tracking elements from the DOM.
 */
(function () {
  'use strict';

  const AD_SIGNALS = [
    'Sponsored',
    'paid partnership',
    'Promoted',
  ];

  const textMatchesSignal = (txt) => {
    if (!txt) return false;
    const t = txt.trim().toLowerCase();
    return AD_SIGNALS.some((s) => t === s.toLowerCase());
  };

  const removeSponsoredArticles = () => {
    try {
      // aria-label routes (best-effort; localization may break)
      document.querySelectorAll('a[aria-label]').forEach((a) => {
        const aria = a.getAttribute('aria-label') || '';
        if (textMatchesSignal(aria)) {
          const article = a.closest('article');
          if (article) article.remove();
        }
      });

      // Text-based removal inside feed articles (best-effort)
      document.querySelectorAll('article').forEach((article) => {
        const walker = document.createTreeWalker(article, NodeFilter.SHOW_TEXT);
        let node;
        while ((node = walker.nextNode())) {
          const txt = node.nodeValue;
          if (textMatchesSignal(txt)) {
            article.remove();
            break;
          }
        }
      });

      // Suggested content is intentionally left alone. Removing suggested
      // units after Instagram has virtualized the feed can snap the viewport
      // back to the top on some accounts.
    } catch (_) {}
  };

  const observer = new MutationObserver(() => removeSponsoredArticles());
  observer.observe(document.body, { childList: true, subtree: true });

  removeSponsoredArticles();
})();
