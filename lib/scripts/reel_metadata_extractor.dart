// Reel metadata extraction for history feature.
// Extracts title and thumbnail URL from the page and sends to Flutter.

const String kReelMetadataExtractorScript = r'''
(function() {
  // Track if we've already extracted for this URL to avoid duplicates
  window.__fgReelExtracted = window.__fgReelExtracted || false;
  window.__fgLastExtractedUrl = window.__fgLastExtractedUrl || '';

  function extractAndSend() {
    const currentUrl = window.location.href;
    
    // Skip if already extracted for this URL
    if (window.__fgReelExtracted && window.__fgLastExtractedUrl === currentUrl) {
      return;
    }

    // Check if this is a reel page
    if (!currentUrl.includes('/reel/')) {
      return;
    }

    // Try multiple sources for metadata
    let title = '';
    let thumbnailUrl = '';

    // 1. Try Open Graph tags
    const ogTitle = document.querySelector('meta[property="og:title"]');
    const ogImage = document.querySelector('meta[property="og:image"]');
    
    if (ogTitle) title = ogTitle.content;
    if (ogImage) thumbnailUrl = ogImage.content;

    // 2. Fallback to document title if no OG title
    if (!title && document.title) {
      title = document.title.replace(' on Instagram', '').trim();
      if (!title) title = 'Instagram Reel';
    }

    // 3. Try JSON-LD structured data
    if (!thumbnailUrl) {
      const jsonLdScripts = document.querySelectorAll('script[type="application/ld+json"]');
      jsonLdScripts.forEach(function(script) {
        try {
          const data = JSON.parse(script.textContent);
          if (data.image) {
            if (Array.isArray(data.image)) {
              thumbnailUrl = data.image[0];
            } else if (typeof data.image === 'string') {
              thumbnailUrl = data.image;
            } else if (data.image.url) {
              thumbnailUrl = data.image.url;
            }
          }
        } catch(e) {}
      });
    }

    // 4. Try Twitter card as fallback
    if (!thumbnailUrl) {
      const twitterImage = document.querySelector('meta[name="twitter:image"]');
      if (twitterImage) thumbnailUrl = twitterImage.content;
    }

    // Skip if no thumbnail found
    if (!thumbnailUrl) {
      return;
    }

    // Mark as extracted
    window.__fgReelExtracted = true;
    window.__fgLastExtractedUrl = currentUrl;

    // Send to Flutter
    if (window.flutter_inappwebview) {
      window.flutter_inappwebview.callHandler(
        'ReelMetadata',
        JSON.stringify({
          url: currentUrl,
          title: title || 'Instagram Reel',
          thumbnailUrl: thumbnailUrl
        })
      );
    }
  }

  // Run immediately in case metadata is already loaded
  extractAndSend();

  // Set up MutationObserver to detect page changes and metadata loading
  if (!window.__fgReelObserver) {
    let debounceTimer = null;
    window.__fgReelObserver = new MutationObserver(function(mutations) {
      // Debounce to avoid excessive calls
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(function() {
        extractAndSend();
      }, 500);
    });

    window.__fgReelObserver.observe(document.documentElement, {
      childList: true,
      subtree: true
    });
  }

  // Also listen for URL changes (SPA navigation)
  let lastUrl = location.href;
  setInterval(function() {
    if (location.href !== lastUrl) {
      lastUrl = location.href;
      window.__fgReelExtracted = false;
      window.__fgLastExtractedUrl = '';
      extractAndSend();
    }
  }, 1000);
})();
''';
