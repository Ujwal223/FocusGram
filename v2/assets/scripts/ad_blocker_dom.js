/**
 * FocusGram DOM Ad Blocker
 * Removes sponsored posts, "Suggested for you" injections, and ad elements.
 * Uses structure-based selectors — NOT class names (those change weekly).
 * Injected at DOCUMENT_END.
 */
(function () {
  'use strict';

  // ─── Sponsored text signals (Instagram localizes these) ───────────────────
  // We match the STRUCTURE not just English text.
  // In IG mobile web, sponsored label appears as a <span> or <div>
  // that is a direct sibling/child of the article header area.
  const SPONSORED_TEXTS = new Set([
    'sponsored',      // en
    'gesponsert',     // de
    'patrocinado',    // es/pt
    'sponsorisé',     // fr
    'sponsorizzato',  // it
    'sponsrad',       // sv
    'sponsoreret',    // da
    'gesponsord',     // nl
    'рекламa',        // ru
    'विज्ञापन',       // hi
    '广告',           // zh
    'ad',             // en short
  ]);

  const isSponsoredText = (text) =>
    SPONSORED_TEXTS.has(text.trim().toLowerCase());

  // ─── Remove a single article element ──────────────────────────────────────
  const removeArticle = (el) => {
    // Walk up to find the article or main feed item container
    const target = el.closest('article') ?? el.closest('div[data-media-id]') ?? el;
    target.remove();
  };

  // ─── Core ad scanner ──────────────────────────────────────────────────────
  const scanAndRemove = () => {
    // Strategy 1: <a href="/ads/..."> inside feed
    document.querySelectorAll('a[href*="/ads/"]').forEach((a) => {
      a.closest('article')?.remove();
    });

    // Strategy 2: Sponsored text in article spans
    document.querySelectorAll('article').forEach((article) => {
      const spans = article.querySelectorAll('span, div');
      for (const span of spans) {
        if (
          span.children.length === 0 && // leaf node
          isSponsoredText(span.textContent)
        ) {
          article.remove();
          return;
        }
      }
    });

    // Strategy 3: "Suggested for you" feed injections
    document.querySelectorAll('article, section').forEach((el) => {
      const firstText = el.querySelector('span, div, h4')?.textContent?.trim();
      if (
        firstText &&
        (firstText.toLowerCase().startsWith('suggested') ||
          firstText.toLowerCase().startsWith('you might') ||
          firstText.toLowerCase() === 'posts you might like')
      ) {
        el.remove();
      }
    });

    // Strategy 4: Instagram marks some ad containers with aria-label
    document
      .querySelectorAll('[aria-label*="Sponsored"], [aria-label*="Ad"]')
      .forEach((el) => {
        el.closest('article')?.remove();
      });

    // Strategy 5: Tracking pixel iframes / hidden images
    document.querySelectorAll('iframe[width="0"], iframe[height="0"]').forEach((el) => el.remove());
    document
      .querySelectorAll('img[width="1"][height="1"], img[width="0"][height="0"]')
      .forEach((el) => el.remove());
  };

  // ─── Run on load + watch for new content ──────────────────────────────────
  scanAndRemove();

  const observer = new MutationObserver((mutations) => {
    // Only scan if nodes were added (skip attribute/text changes)
    const hasAdditions = mutations.some((m) => m.addedNodes.length > 0);
    if (hasAdditions) scanAndRemove();
  });

  observer.observe(document.body, {
    childList: true,
    subtree: true,
  });
})();
