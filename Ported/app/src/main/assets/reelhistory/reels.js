const DWELL_MS = 1000;   // a reel counts after 1 second on screen
const REEL_RE = /^\/reels?\/([A-Za-z0-9_-]{8,})\/?/;
const CODE_RE = /^[A-Za-z0-9_.-]{3,}$/;

// ---- In-Page API Interception via Injected Script ----
// Content scripts run in an isolated world where exportFunction may not exist.
// Injecting a DOM script into the page context guarantees direct access to fetch and XMLHttpRequest.
(function injectApiHook() {
  const SCRIPT_ID = 'fg-reel-api-hook';
  if (document.getElementById(SCRIPT_ID)) return;
  const s = document.createElement('script');
  s.id = SCRIPT_ID;
  s.textContent = `
    (function() {
      const CODE_RE = /^[A-Za-z0-9_-]{8,}$/;
      window.__fg_media_cache = window.__fg_media_cache || new Map();

      function harvest(obj, depth) {
        if (!obj || typeof obj !== 'object' || depth > 14) return;
        if (Array.isArray(obj)) { for (const x of obj) harvest(x, depth + 1); return; }
        if (typeof obj.code === 'string' && CODE_RE.test(obj.code)) {
          const cap = obj.caption && typeof obj.caption.text === 'string' ? obj.caption.text : '';
          const author = obj.user && typeof obj.user.username === 'string' ? obj.user.username : '';
          
          let videoUrl = '';
          if (Array.isArray(obj.video_versions) && obj.video_versions.length > 0) {
            videoUrl = obj.video_versions[0].url || '';
          }
          let imageUrl = '';
          if (obj.image_versions2 && Array.isArray(obj.image_versions2.candidates) && obj.image_versions2.candidates.length > 0) {
            imageUrl = obj.image_versions2.candidates[0].url || '';
          }

          if (videoUrl || imageUrl) {
            window.__fg_media_cache.set(obj.code, {
              code: obj.code,
              videoUrl: videoUrl,
              imageUrl: imageUrl,
              isVideo: !!videoUrl,
              author: author,
              caption: cap.slice(0, 300)
            });
            while (window.__fg_media_cache.size > 200) {
              window.__fg_media_cache.delete(window.__fg_media_cache.keys().next().value);
            }
          }

          if (author) {
            window.dispatchEvent(new CustomEvent('fg-api-reel', {
              detail: { code: obj.code, author: author, caption: cap.slice(0, 300) }
            }));
          }
        }
        for (const k in obj) harvest(obj[k], depth + 1);
      }

      function remember(text) {
        try { harvest(JSON.parse(text), 0); } catch (_) {}
      }

      window.__fg_fetch_post_media = async function(shortcode) {
        if (!shortcode) return null;
        if (window.__fg_media_cache && window.__fg_media_cache.has(shortcode)) {
          return window.__fg_media_cache.get(shortcode);
        }
        try {
          const apiURL = new URL('/graphql/query', window.location.origin);
          const params = new URLSearchParams({
            fb_api_caller_class: 'RelayModern',
            fb_api_req_friendly_name: 'PolarisPostRootQuery',
            doc_id: '27852811784380813',
            variables: JSON.stringify({
              shortcode: shortcode,
              __relay_internal__pv__PolarisShortDramaEnabledrelayprovider: false,
              __relay_internal__pv__PolarisMultiCaptionCarouselEnabledrelayprovider: false,
            }),
            server_timestamps: 'true',
          });
          const res = await window.fetch(apiURL.href, {
            method: 'POST',
            headers: {
              'content-type': 'application/x-www-form-urlencoded',
              'x-fb-friendly-name': 'PolarisPostRootQuery',
              'x-ig-app-id': '936619743392459',
              'x-requested-with': 'XMLHttpRequest',
            },
            body: params.toString(),
            credentials: 'include'
          });
          if (res.ok) {
            const data = await res.json();
            const item = data?.data?.xdt_api__v1__media__shortcode__web_info?.items?.[0];
            if (item) {
              let videoUrl = (item.video_versions && item.video_versions[0]?.url) || '';
              let imageUrl = (item.image_versions2?.candidates && item.image_versions2.candidates[0]?.url) || '';
              const result = {
                code: shortcode,
                videoUrl: videoUrl,
                imageUrl: imageUrl,
                isVideo: !!videoUrl,
                author: item.user?.username || '',
                caption: item.caption?.text || ''
              };
              window.__fg_media_cache.set(shortcode, result);
              return result;
            }
          }
        } catch (_) {}
        return null;
      };

      window.addEventListener('fg-request-media', async (e) => {
        const code = e?.detail?.code;
        const reqId = e?.detail?.reqId;
        if (!code || !reqId) return;
        let media = window.__fg_media_cache && window.__fg_media_cache.get(code);
        if (!media && typeof window.__fg_fetch_post_media === 'function') {
          media = await window.__fg_fetch_post_media(code);
        }
        window.dispatchEvent(new CustomEvent('fg-response-media-' + reqId, {
          detail: media || null
        }));
      });

      const isApi = (u) => typeof u === 'string' && (u.includes('graphql') || u.includes('/api/v1/') || u.includes('/clips/'));

      const origFetch = window.fetch;
      if (origFetch) {
        window.fetch = async function(input, init) {
          const p = origFetch.apply(this, arguments);
          try {
            const url = typeof input === 'string' ? input : (input && input.url) || '';
            if (isApi(url)) {
              p.then(res => {
                try { res.clone().text().then(remember).catch(() => {}); } catch (_) {}
              }).catch(() => {});
            }
          } catch (_) {}
          return p;
        };
      }

      const XP = window.XMLHttpRequest && window.XMLHttpRequest.prototype;
      if (XP) {
        const origOpen = XP.open;
        const origSend = XP.send;
        XP.open = function(method, url) {
          this.__fg_url = String(url);
          return origOpen.apply(this, arguments);
        };
        XP.send = function() {
          try {
            if (this.__fg_url && isApi(this.__fg_url)) {
              this.addEventListener('load', function() {
                try {
                  if (!this.responseType || this.responseType === 'text') remember(this.responseText);
                } catch (_) {}
              });
            }
          } catch (_) {}
          return origSend.apply(this, arguments);
        };
      }
    })();
  `;
  (document.head || document.documentElement).appendChild(s);
})();

// ---- Source 2: Store harvested API metadata ----
const apiMeta = new Map();       // code -> { author, caption }
const authorToCode = new Map();  // lowercase username -> code

window.addEventListener('fg-api-reel', (e) => {
  if (e && e.detail) {
    const d = e.detail;
    apiMeta.set(d.code, { author: d.author, caption: d.caption });
    if (d.author) authorToCode.set(d.author.toLowerCase(), d.code);
    while (apiMeta.size > 400) apiMeta.delete(apiMeta.keys().next().value);
  }
});

// ---- Source 1 (primary): the <meta> tags in the page head ----
function fromHead(code) {
  const get = (sel, attr) => {
    const e = document.querySelector(sel);
    return e ? (e.getAttribute(attr) || '') : '';
  };
  const canon = get('link[rel="canonical"]', 'href');
  const ogUrl = get('meta[property="og:url"]', 'content');
  const alUrl = get('meta[property="al:android:url"]', 'content');
  if (![canon, ogUrl, alUrl].some((u) => u.includes(code))) return null;

  let author = '';
  let m = new RegExp('instagram\\.com/([A-Za-z0-9._]+)/reels?/' + code).exec(ogUrl);
  if (m) author = m[1];
  if (!author) {
    m = /\(@([A-Za-z0-9._]+)\)/.exec(get('meta[name="twitter:title"]', 'content'));
    if (m) author = m[1];
  }
  m = /^[\s\S]*? on Instagram: "([\s\S]*)"\s*$/.exec(get('meta[property="og:title"]', 'content'));
  const caption = m ? m[1].slice(0, 300) : '';
  return author || caption ? { author, caption } : null;
}

// Head first, DOM second, API third, field by field.
function lookup(code) {
  const a = apiMeta.get(code) || {};
  const h = fromHead(code) || {};
  let author = a.author || h.author || '';
  let caption = a.caption || h.caption || '';

  if (!author || !caption) {
    const videos = Array.from(document.querySelectorAll('video'));
    for (const v of videos) {
      let card = v.closest('article') || v.closest('section') || v.parentElement;
      while (card && card !== document.body && card !== document.documentElement) {
        if (card.tagName === 'ARTICLE' || card.tagName === 'SECTION') break;
        const r = card.getBoundingClientRect();
        if (r.height > window.innerHeight * 0.5) break;
        card = card.parentElement;
      }
      if (!card) continue;
      const link = card.querySelector('a[href*="/reel/"], a[href*="/p/"], a[href*="/reels/"]');
      if (link && link.href && link.href.includes(code)) {
        if (!author) {
          const aEl = card.querySelector('header a[role="link"], a[role="link"][href^="/"]:not([href*="/audio/"]):not([href*="/reel"])');
          if (aEl) author = aEl.textContent.trim().replace(/^@/, '');
        }
        if (!caption) {
          const textCandidates = Array.from(card.querySelectorAll('h1, h2, span[dir="auto"], div[dir="auto"]'));
          for (const el of textCandidates) {
            if (el.closest('a[href*="/audio/"]') || 
                el.closest('header') || 
                el.closest('button') || 
                el.closest('[role="button"]') ||
                el.closest('a[role="link"]')) {
              continue;
            }
            const txt = (el.textContent || '').trim();
            const lower = txt.toLowerCase();
            if (txt.length >= 2 && 
                txt !== author && 
                !lower.includes('follow') && 
                !lower.startsWith('liked by') && 
                !/^\d+[\d,.]*[KkMm]?$/.test(txt) &&
                !lower.includes('original audio') &&
                !['ad', 'sponsored'].includes(lower)) {
              caption = txt.slice(0, 300);
              break;
            }
          }
        }
        break;
      }
    }
  }

  if (!author && code.startsWith('reel_')) {
    author = code.replace(/^reel_/, '');
  }
  return { author: author || '', caption: caption || '' };
}

// ---- Detect which reel is on screen and report it ----
let cur = null;
let timer = null;
const unfilled = new Set();

function report(type, code) {
  const m = lookup(code);
  browser.runtime.sendMessage({
    t: 'fg-watched', type, code, author: m.author, caption: m.caption,
  }).catch(() => {});
  return m;
}

function getActiveReelCode() {
  const m = REEL_RE.exec(location.pathname);
  if (m) return m[1];

  const videos = Array.from(document.querySelectorAll('video'));
  const activeVideo = videos.find(v => !v.paused && v.getBoundingClientRect().height > 200) ||
                      videos.find(v => {
                        const r = v.getBoundingClientRect();
                        return r.top >= -50 && r.top < window.innerHeight * 0.6;
                      }) || videos[0];
  if (!activeVideo) return null;

  let card = activeVideo.closest('article') || activeVideo.closest('section') || activeVideo.parentElement;
  while (card && card !== document.body && card !== document.documentElement) {
    if (card.tagName === 'ARTICLE' || card.tagName === 'SECTION') break;
    const r = card.getBoundingClientRect();
    if (r.height > window.innerHeight * 0.5) break;
    card = card.parentElement;
  }
  if (!card) return null;

  // 1. Direct link to reel
  const link = card.querySelector('a[href*="/reel/"], a[href*="/p/"], a[href*="/reels/"]');
  if (link && link.href) {
    const lm = /\/(?:reel|reels|p)\/([A-Za-z0-9_-]{8,})/.exec(link.href);
    if (lm) return lm[1];
  }

  // 2. Identify author
  const authorEl = card.querySelector('header a[role="link"], a[role="link"][href^="/"]:not([href*="/audio/"]):not([href*="/reel"]), a[href^="/"]:not([href*="/audio/"]):not([href*="/reel"])');
  if (authorEl) {
    let username = authorEl.textContent.trim().replace(/^@/, '').toLowerCase();
    if (!username && authorEl.getAttribute('href')) {
      const match = authorEl.getAttribute('href').match(/^\/([A-Za-z0-9._]+)\/?/);
      if (match) username = match[1].toLowerCase();
    }
    if (username) {
      return 'reel_' + username;
    }
  }

  return null;
}

function tick() {
  const code = getActiveReelCode();
  if (code === cur) return;
  cur = code;
  clearTimeout(timer);
  if (!code) return;
  timer = setTimeout(() => {
    if (cur !== code || document.visibilityState !== 'visible') return;
    const found = report('watched', code);
    if (!found.author || !found.caption) unfilled.add(code);
  }, DWELL_MS);
}

function fill() {
  unfilled.forEach((code) => {
    const m = lookup(code);
    if (m.author || m.caption) {
      report('meta', code);
      if (m.author && m.caption) unfilled.delete(code);
    }
  });
  if (unfilled.size > 50) unfilled.delete(unfilled.values().next().value);
}

setInterval(tick, 300);
setInterval(fill, 1000);
