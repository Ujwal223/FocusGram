/**
 * FocusGram Unified Feed Filter via Fetch Interception
 * Injected at DOCUMENT_START — before Instagram's JS loads.
 * 
 * This script intercepts GraphQL fetch calls and filters feed content based on:
 * - Ads (is_ad, ad_action_link, product_type, ad_id, ad_header_style)
 * - Sponsored posts (ad_action_link, ad_header_style)
 * - Suggested posts (is_suggested, is_suggested_for_you, __typename)
 * - Videos/Reels (is_video, media_type, clips_metadata)
 * - Autoplay blocking (video autoplay prevention)
 */
(function () {
  'use strict';

  // Configuration flags (set by Flutter via prefs)
  window.__fgFilterConfig = {
    blockAds: false,
    blockSponsored: false,
    blockSuggested: false,
    blockVideos: false,
    blockAutoplay: false,
  };

  // Helper: Check if a node is an ad
  const isAdNode = (node) => {
  if (!node || typeof node !== 'object') return false;
  return !!(
    node.is_ad ||
    node.ad_id ||
    node.ad_action_link ||
    node.ad_action_links?.length > 0 ||           
    node.is_paid_partnership ||                    
    node.sponsor_tags?.length > 0 ||              
    (node.commerciality_status === 'ad') ||        
    (node.commerciality_status === 'shoppable_feed_ad') ||  
    (node.product_type === 'ad') ||
    (node.ad_header_style && node.ad_header_style !== 'none') ||
    node.__typename === 'GraphAdStory' ||
    node.__typename === 'XDTAdFeedUnit' ||         
    (node.__typename?.toLowerCase().includes('ad')) 
  );
  };

  // Helper: Check if a node is sponsored
  const isSponsoredNode = (node) => {
    if (!node || typeof node !== 'object') return false;
    
    return !!(
      (node.ad_action_link && node.ad_action_link.href) ||
      (node.ad_header_style && node.ad_header_style !== 'none')
    );
  };

  // Helper: Check if a node is suggested content
  const isSuggestedNode = (node) => {
    if (!node || typeof node !== 'object') return false;
    
    return !!(
      node.is_suggested ||
      node.is_suggested_for_you ||
      (node.__typename && node.__typename.includes('Suggested'))
    );
  };

  // Helper: Check if a node is a video/reel
  const isVideoNode = (node) => {
    if (!node || typeof node !== 'object') return false;
    
    return !!(
      node.is_video ||
      (node.media_type === 2) ||
      node.clips_metadata ||
      (node.__typename && (
        node.__typename.includes('Clips') ||
        node.__typename.includes('Video')
      ))
    );
  };

  // Helper: Check for media in carousel
  const hasVideoInCarousel = (node) => {
    if (!node || typeof node !== 'object') return false;
    
    if (node.media_type === 8) {
      const edges = node.edge_sidecar_to_children?.edges || [];
      return edges.some(edge => isVideoNode(edge.node));
    }
    return false;
  };

  // Main filter function for feed nodes
  const shouldFilterNode = (node) => {
    const config = window.__fgFilterConfig;
    
    if (!node || typeof node !== 'object') return false;

    // Check ads
    if (config.blockAds && isAdNode(node)) {
      return true;
    }

    // Check sponsored (separate from ads)
    if (config.blockSponsored && isSponsoredNode(node) && !isAdNode(node)) {
      return true;
    }

    // Check suggested content
    if (config.blockSuggested && isSuggestedNode(node)) {
      return true;
    }

    // Check videos/reels
    if (config.blockVideos && (isVideoNode(node) || hasVideoInCarousel(node))) {
      return true;
    }

    return false;
  };

  // Recursively filter GraphQL response edges
  const filterEdges = (edges, path = []) => {
    if (!Array.isArray(edges)) return edges;
    
    return edges.filter(edge => {
      if (!edge || !edge.node) return true;
      const node = edge.node;
      
      // Keep the edge if it doesn't match any filter
      if (!shouldFilterNode(node)) return true;
      
      // Log filtered content for debugging
      if (window.__fgDebugFilter) {
        const type = node.__typename || 'Unknown';
        console.debug('[FocusGram Filter]', `Filtered ${type} at ${path.join('/')}`);
      }
      
      return false;
    });
  };

  // Recursively walk GraphQL response and filter edges
  const walkAndFilter = (obj, visited = new Set()) => {
    if (!obj || typeof obj !== 'object' || visited.has(obj)) return;
    visited.add(obj);

    // Handle arrays
    if (Array.isArray(obj)) {
      obj.forEach(item => walkAndFilter(item, visited));
      return;
    }

    // Check for edges array (common GraphQL pattern)
    if (obj.edges && Array.isArray(obj.edges)) {
      obj.edges = filterEdges(obj.edges);
    }

    // Recurse into children
    for (const key in obj) {
      if (obj.hasOwnProperty(key) && key !== '__typename') {
        const val = obj[key];
        if (val && typeof val === 'object') {
          walkAndFilter(val, visited);
        }
      }
    }
  };
  

  // Override fetch
  const _fetch = window.fetch.bind(window);

  window.fetch = async function (input, init) {
    const url = typeof input === 'string'
      ? input
      : input instanceof URL
      ? input.href
      : input?.url ?? '';

    // Call original fetch
    let response = await _fetch(input, init);

    // Only intercept GraphQL feed queries
    if (!url.includes('/graphql') && !url.includes('/api/v1/feed')) {
      return response;
    }

    // Clone response to read body
    const cloned = response.clone();
    
    try {
      const contentType = response.headers.get('content-type') || '';
      if (!contentType.includes('application/json')) {
        return response;
      }

      const data = await cloned.json();
      
      // Filter the response data
      walkAndFilter(data);

      // Return modified response
      return new Response(JSON.stringify(data), {
        status: response.status,
        statusText: response.statusText,
        headers: response.headers,
      });
    } catch (e) {
      // On error, return original response
      return response;
    }
  };

  // Preserve native function appearance
  Object.defineProperty(window, 'fetch', {
    value: window.fetch,
    writable: true,
    configurable: true,
  });
  window.fetch.toString = () => 'function fetch() { [native code] }';

  // Allow Flutter to update config flags
  window.__fgSetFilterConfig = function (config) {
    if (typeof config === 'object') {
      Object.assign(window.__fgFilterConfig, config);
      if (window.__fgDebugFilter) {
        console.debug('[FocusGram Filter] Config updated:', window.__fgFilterConfig);
      }
    }
  };

  // Enable debug logging
  window.__fgDebugFilter = false;
})();
