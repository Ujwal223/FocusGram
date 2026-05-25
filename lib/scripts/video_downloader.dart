/// Best-effort Instagram media downloader UI.
///
/// The script only exposes URLs already rendered in the WebView. It cannot
/// decrypt or fetch media that Instagram has not loaded, but it covers visible
/// feed posts, reels, profile avatars, and DM visual/video messages.
const String kVideoDownloadJS = r'''
(function() {
  'use strict';

  if (window.__fgMediaDownloadRunning) return;
  window.__fgMediaDownloadRunning = true;

  const BTN_ATTR = 'data-fg-download-btn';
  const URL_ATTR = 'data-fg-download-url';
  const TYPE_ATTR = 'data-fg-download-type';
  const MAX_PER_PASS = 60;

  function text(value) {
    try { return (value || '').toString(); } catch (_) { return ''; }
  }

  function isHttp(value) {
    const s = text(value);
    return s.indexOf('https://') === 0 || s.indexOf('http://') === 0;
  }

  function cleanUrl(value) {
    const s = text(value).trim();
    if (!isHttp(s)) return null;
    return s.replace(/&amp;/g, '&');
  }

  function bestFromSrcset(srcset) {
    const raw = text(srcset);
    if (!raw) return null;
    let best = null;
    let bestScore = -1;
    raw.split(',').forEach(function(part) {
      const bits = part.trim().split(/\s+/);
      const url = cleanUrl(bits[0]);
      if (!url) return;
      const score = parseFloat(text(bits[1]).replace(/[^\d.]/g, '')) || 1;
      if (score >= bestScore) {
        bestScore = score;
        best = url;
      }
    });
    return best;
  }

  function backgroundUrl(el) {
    try {
      const bg = window.getComputedStyle(el).backgroundImage || '';
      const match = bg.match(/url\(["']?(.*?)["']?\)/);
      return match ? cleanUrl(match[1]) : null;
    } catch (_) {
      return null;
    }
  }

  function urlFromJsonishAttribute(el) {
    const attrs = ['data-store', 'data-props', 'data-visualcompletion'];
    for (let i = 0; i < attrs.length; i++) {
      const value = text(el.getAttribute && el.getAttribute(attrs[i]));
      const match = value.match(/https?:\\?\/\\?\/[^"'\s\\]+/);
      if (match) return cleanUrl(match[0].replace(/\\\//g, '/'));
    }
    return null;
  }

  function mediaUrl(el) {
    if (!el) return null;
    const tag = text(el.tagName).toLowerCase();
    if (tag === 'video') {
      return cleanUrl(el.currentSrc || el.src) ||
        cleanUrl(el.getAttribute('src')) ||
        cleanUrl(el.getAttribute('poster')) ||
        firstSource(el);
    }
    if (tag === 'img') {
      return cleanUrl(el.currentSrc || el.src) ||
        bestFromSrcset(el.getAttribute('srcset')) ||
        cleanUrl(el.getAttribute('src'));
    }
    return backgroundUrl(el) || urlFromJsonishAttribute(el);
  }

  function firstSource(video) {
    try {
      const sources = video.querySelectorAll('source');
      for (let i = 0; i < sources.length; i++) {
        const url = cleanUrl(sources[i].src || sources[i].getAttribute('src'));
        if (url) return url;
      }
    } catch (_) {}
    return null;
  }

  function typeFrom(el, url) {
    const tag = text(el && el.tagName).toLowerCase();
    const u = text(url).toLowerCase();
    if (tag === 'video' || u.indexOf('.mp4') >= 0 || u.indexOf('.m3u8') >= 0) {
      return 'video';
    }
    return 'photo';
  }

  function looksLikeAvatar(el) {
    try {
      const img = el && el.tagName && el.tagName.toLowerCase() === 'img' ? el : null;
      if (!img) return false;
      const alt = text(img.getAttribute('alt')).toLowerCase();
      const r = img.getBoundingClientRect();
      const rounded =
        window.getComputedStyle(img).borderRadius.indexOf('%') >= 0 ||
        parseFloat(window.getComputedStyle(img).borderRadius) >= Math.min(r.width, r.height) / 3;
      return r.width <= 72 && r.height <= 72 && (rounded || alt.indexOf('profile') >= 0 || alt.indexOf('avatar') >= 0);
    } catch (_) {
      return false;
    }
  }

  function mediaScore(item) {
    try {
      const r = item.el.getBoundingClientRect();
      let score = Math.max(0, r.width) * Math.max(0, r.height);
      if (item.type === 'video') score += 10000000;
      if (looksLikeAvatar(item.el)) score -= 10000000;
      if (text(item.url).toLowerCase().indexOf('s150x150') >= 0) score -= 5000000;
      return score;
    } catch (_) {
      return 0;
    }
  }

  function filename(type) {
    const ext = type === 'video' ? 'mp4' : 'jpg';
    return 'focusgram_' + type + '_' + Date.now() + '.' + ext;
  }

  function inView(el) {
    try {
      const r = el.getBoundingClientRect();
      return r.width > 24 && r.height > 24 && r.bottom > 0 && r.top < window.innerHeight;
    } catch (_) {
      return false;
    }
  }

  function icon() {
    return '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="M7 10l5 5 5-5"/><path d="M12 15V3"/></svg>';
  }

  function sendDownload(url, type) {
    try {
      if (!url || !window.flutter_inappwebview || !window.flutter_inappwebview.callHandler) return;
      window.flutter_inappwebview.callHandler('FocusGramMediaDownload', JSON.stringify({
        type: type,
        url: url,
        filename: filename(type),
      }));
    } catch (_) {}
  }

  function makeButton(url, type, mode) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.setAttribute(BTN_ATTR, '1');
    btn.setAttribute(URL_ATTR, url);
    btn.setAttribute(TYPE_ATTR, type);
    btn.setAttribute('aria-label', 'Download media');
    btn.innerHTML = icon();
    btn.style.cssText = [
      'position:absolute',
      'z-index:2147483647',
      'width:34px',
      'height:34px',
      'border-radius:10px',
      'border:1px solid rgba(255,255,255,.18)',
      'background:' + (mode === 'inline' ? 'transparent' : 'rgba(0,0,0,.58)'),
      'color:rgba(255,255,255,.94)',
      'display:flex',
      'align-items:center',
      'justify-content:center',
      'padding:0',
      'cursor:pointer',
      'pointer-events:auto',
      'backdrop-filter:blur(8px)',
      '-webkit-backdrop-filter:blur(8px)',
    ].join(';');
    btn.addEventListener('click', function(e) {
      e.preventDefault();
      e.stopPropagation();
      sendDownload(btn.getAttribute(URL_ATTR), btn.getAttribute(TYPE_ATTR) || type);
    }, true);
    return btn;
  }

  function ensureRelative(container) {
    try {
      const pos = window.getComputedStyle(container).position;
      if (!pos || pos === 'static') container.style.position = 'relative';
    } catch (_) {}
  }

  function placeNearSave(article, url, type) {
    const ref = article.querySelector([
      'button[aria-label*="Save" i]',
      'button[aria-label*="Bookmark" i]',
      'svg[aria-label*="Save" i]',
      'svg[aria-label*="Bookmark" i]',
      'a[href*="/save"]',
    ].join(','));
    if (!ref) return false;

    const target = ref.closest('button,a,div') || ref;
    const bar = target.parentElement || article;
    if (bar.querySelector(':scope > [' + BTN_ATTR + '="1"]')) return true;

    const btn = makeButton(url, type, 'inline');
    btn.style.position = 'relative';
    btn.style.inset = 'auto';
    btn.style.marginLeft = '8px';
    btn.style.color = 'currentColor';
    btn.style.border = '0';
    btn.style.backdropFilter = 'none';
    btn.style.webkitBackdropFilter = 'none';
    try {
      target.insertAdjacentElement('afterend', btn);
      return true;
    } catch (_) {
      return false;
    }
  }

  function placeOverlay(container, url, type, where) {
    if (!container || container.querySelector(':scope > [' + BTN_ATTR + '="1"]')) return true;
    ensureRelative(container);
    const btn = makeButton(url, type, 'overlay');
    if (where === 'reel') {
      btn.style.top = '12px';
      btn.style.right = '12px';
    } else if (where === 'profile') {
      btn.style.top = '8px';
      btn.style.right = '8px';
    } else {
      btn.style.right = '10px';
      btn.style.bottom = '10px';
    }
    container.appendChild(btn);
    return true;
  }

  function visibleMedia(root) {
    return Array.prototype.slice.call(root.querySelectorAll('video,img,[style*="background-image"]'))
      .filter(inView)
      .map(function(el) {
        const url = mediaUrl(el);
        return url ? { el: el, url: url, type: typeFrom(el, url) } : null;
      })
      .filter(Boolean);
  }

  function handleFeed() {
    let added = 0;
    document.querySelectorAll('article').forEach(function(article) {
      if (added >= MAX_PER_PASS || article.querySelector('[' + BTN_ATTR + '="1"]')) return;
      const media = visibleMedia(article)
        .filter(function(item) { return !looksLikeAvatar(item.el); })
        .sort(function(a, b) { return mediaScore(b) - mediaScore(a); })[0];
      if (!media) return;
      if (placeNearSave(article, media.url, media.type) ||
          placeOverlay(article, media.url, media.type, 'feed')) {
        added++;
      }
    });
    return added;
  }

  function handleReels() {
    let added = 0;
    visibleMedia(document).forEach(function(media) {
      if (added >= MAX_PER_PASS) return;
      const container =
        media.el.closest('[class*="ReelsVideoPlayer"]') ||
        media.el.closest('article') ||
        media.el.closest('[role="presentation"]') ||
        media.el.parentElement;
      if (placeOverlay(container, media.url, media.type, 'reel')) added++;
    });
    return added;
  }

  function handleDirect() {
    let added = 0;
    visibleMedia(document).forEach(function(media) {
      if (added >= MAX_PER_PASS) return;
      const bubble =
        media.el.closest('[role="button"]') ||
        media.el.closest('div[style*="max-width"]') ||
        media.el.closest('article') ||
        media.el.parentElement;
      if (placeOverlay(bubble, media.url, media.type, 'dm')) added++;
    });
    return added;
  }

  function handleProfile() {
    let added = 0;
    const path = window.location.pathname || '/';
    if (path === '/' || path.indexOf('/explore') === 0 || path.indexOf('/direct') === 0) return 0;
    document.querySelectorAll('header img,img[alt*="profile" i],img[alt*="avatar" i]').forEach(function(img) {
      if (added >= 4 || !inView(img)) return;
      const url = mediaUrl(img);
      if (!url) return;
      const r = img.getBoundingClientRect();
      if (r.width < 56 && r.height < 56) return;
      const container = img.closest('div') || img.parentElement;
      if (placeOverlay(container, url, 'photo', 'profile')) added++;
    });
    return added;
  }

  function pass() {
    try {
      const path = window.location.pathname || '/';
      if (path.indexOf('/direct') === 0) {
        handleDirect();
      } else if (path.indexOf('/reels') === 0 || path.indexOf('/reel/') >= 0) {
        handleReels();
      } else {
        handleFeed();
        handleProfile();
      }
    } catch (_) {}
  }

  let timer = null;
  function schedule() {
    clearTimeout(timer);
    timer = setTimeout(pass, 220);
  }

  new MutationObserver(schedule).observe(document.documentElement, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ['src', 'srcset', 'style'],
  });
  window.addEventListener('scroll', schedule, { passive: true });
  window.addEventListener('resize', schedule, { passive: true });
  window.addEventListener('focus', schedule, { passive: true });
  pass();
})();
''';
