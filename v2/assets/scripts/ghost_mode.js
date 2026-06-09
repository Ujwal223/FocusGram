/**
 * FocusGram Ghost Mode
 * Injected at DOCUMENT_START — before Instagram's JS loads.
 * Blocks story-seen, message-seen, and online-presence signals.
 */
(function () {
  'use strict';

  // ─── Direct Message API block ────────────────────────────────────────────
  // ── First-interaction gate: allow inbox to load, then block ─
  window.__fgDirectApiBlocked = false;
  document.addEventListener('click', function() {
    if (window.location.pathname.indexOf('/direct/') === 0) window.__fgDirectApiBlocked = true;
  }, true);
  document.addEventListener('touchstart', function() {
    if (window.location.pathname.indexOf('/direct/') === 0) window.__fgDirectApiBlocked = true;
  }, true);
  var _prevD = window.location.pathname.indexOf('/direct/') === 0;
  setInterval(function() {
    var now = window.location.pathname.indexOf('/direct/') === 0;
    if (now !== _prevD) { _prevD = now; window.__fgDirectApiBlocked = false; }
  }, 300);

  function _blockIfNeeded(url) {
    return window.__fgDirectApiBlocked &&
           window.location.pathname.indexOf('/direct/') === 0 &&
           url.indexOf('/api/graphql') !== -1;
  }

  const _f = window.fetch.bind(window);
  window.fetch = function(input, init) {
    const url = (typeof input === 'string') ? input : (input && input.url) ? input.url : '';
    if (_blockIfNeeded(url)) {
      return Promise.resolve(new Response(JSON.stringify({status:'ok'}), {
        status: 200, headers: {'Content-Type': 'application/json'}
      }));
    }
    return _f(input, init);
  };

  const _o = XMLHttpRequest.prototype.open;
  const _s = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function(m, u) {
    this.__fgUrl = u || ''; return _o.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function(body) {
    if (_blockIfNeeded(this.__fgUrl || '')) {
      const self = this; setTimeout(function() {
        Object.defineProperty(self,'readyState',{get:function(){return 4}});
        Object.defineProperty(self,'status',{get:function(){return 200}});
        Object.defineProperty(self,'responseText',{get:function(){return '{"status":"ok"}'}});
        self.dispatchEvent(new Event('readystatechange')); self.dispatchEvent(new Event('load'));
      }, 5); return;
    }
    return _s.apply(this, arguments);
  };

  // ─── Seen API patterns ────────────────────────────────────────────────────
  const SEEN_PATTERNS = [
    /\/api\/v1\/media\/[\w-]+\/seen\//,
    /\/api\/v1\/stories\/reel\/seen\//,
    /\/api\/v1\/direct_v2\/threads\/[\w-]+\/seen\//,
    /\/api\/v1\/direct_v2\/visual_message\/[\w-]+\/seen\//,
    /\/api\/v1\/live\/[\w-]+\/comment\/seen\//,
  ];

  // ─── Activity patterns (like, comment) — intercepted for local history ────
  const ACTIVITY_PATTERNS = [
    /\/api\/v1\/web\/likes\/[\w-]+\/like\//,
    /\/api\/v1\/web\/comments\/add\//,
    /\/api\/v1\/friendships\/[\w-]+\/follow\//,
  ];

  const isSeen = (url) => SEEN_PATTERNS.some((p) => p.test(url));
  const isActivity = (url) => ACTIVITY_PATTERNS.some((p) => p.test(url));

  const fakeOkResponse = () =>
    new Response(JSON.stringify({ status: 'ok' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });

  // ─── Fetch override ───────────────────────────────────────────────────────
  const _fetch = window.fetch.bind(window);

  const patchedFetch = async function (input, init) {
    const url =
      typeof input === 'string'
        ? input
        : input instanceof URL
        ? input.href
        : input?.url ?? '';

    // Block seen
    if (isSeen(url)) {
      if (window.GhostChannel) {
        window.GhostChannel.postMessage(
          JSON.stringify({ type: 'seen_blocked', url })
        );
      }
      return fakeOkResponse();
    }

    // Intercept activity for local history
    if (isActivity(url) && window.ActivityChannel) {
      const body = init?.body;
      const bodyText =
        body instanceof URLSearchParams
          ? body.toString()
          : typeof body === 'string'
          ? body
          : '';
      window.ActivityChannel.postMessage(
        JSON.stringify({ url, body: bodyText, timestamp: Date.now() })
      );
    }

    return _fetch(input, init);
  };

  // Disguise as native
  Object.defineProperty(window, 'fetch', {
    value: patchedFetch,
    writable: true,
    configurable: true,
    enumerable: true,
  });
  window.fetch.toString = () => 'function fetch() { [native code] }';
  window.fetch[Symbol.toStringTag] = 'fetch';

  // ─── XMLHttpRequest override ──────────────────────────────────────────────
  const _XHROpen = XMLHttpRequest.prototype.open;
  const _XHRSend = XMLHttpRequest.prototype.send;

  XMLHttpRequest.prototype.open = function (method, url, ...args) {
    this._fg_url = url ?? '';
    this._fg_method = (method ?? '').toUpperCase();
    return _XHROpen.call(this, method, url, ...args);
  };

  XMLHttpRequest.prototype.send = function (body) {
    if (this._fg_url && isSeen(this._fg_url)) {
      // Fire readyState 4 with fake success without actually sending
      const self = this;
      setTimeout(() => {
        Object.defineProperty(self, 'readyState', { get: () => 4 });
        Object.defineProperty(self, 'status', { get: () => 200 });
        Object.defineProperty(self, 'responseText', {
          get: () => '{"status":"ok"}',
        });
        Object.defineProperty(self, 'response', {
          get: () => '{"status":"ok"}',
        });
        self.dispatchEvent(new Event('readystatechange'));
        self.dispatchEvent(new Event('load'));
      }, 10);
      return;
    }
    return _XHRSend.call(this, body);
  };

  // ─── WebSocket intercept (message-seen via WS) ────────────────────────────
  // Strict WS URL blocking (ghost mode requirement)
  // sid/cid vary per user/chat; block by endpoint prefix, not exact query.
  const isBlockedWssUrl = (u) => {
    if (!u) return false;
    const urlStr = String(u);

    return (
      urlStr.startsWith('wss://gateway.instagram.com/ws/streamcontroller') ||
      urlStr.startsWith('wss://edge-chat.instagram.com/chat?sid=')
    );
  };

  // Signal to other injected scripts that ghost-mode is active
  window.__fgGhostModeActive = true;

  const _WS = window.WebSocket;

  function PatchedWebSocket(url, protocols) {
    const urlStr = typeof url === 'string' ? url : url?.toString?.() ?? '';

    // If the WebSocket URL is one of the blocked endpoints, return an inert WS-like object
    if (isBlockedWssUrl(urlStr)) {
      return {
        send: () => {},
        close: () => {},
        readyState: 1,
        addEventListener: () => {},
        removeEventListener: () => {},
      };
    }

    const ws = protocols ? new _WS(url, protocols) : new _WS(url);
    const _send = ws.send.bind(ws);

    ws.send = function (data) {
      if (typeof data === 'string') {
        // IG sends seen ops as JSON with "op":"4" or "op":"seen" depending on version
        try {
          const parsed = JSON.parse(data);
          if (
            parsed?.op === '4' ||
            parsed?.op === 'seen' ||
            (parsed?.payload && JSON.parse(parsed.payload)?.op === 'seen')
          ) {
            return; // drop
          }
        } catch (_) {}
        // Text-based seen signal check
        if (data.includes('"seen"') && data.includes('"thread_id"')) {
          return;
        }
      }
      return _send(data);
    };

    return ws;
  }

  // Preserve WebSocket prototype chain so IG's ws checks pass
  PatchedWebSocket.prototype = _WS.prototype;
  PatchedWebSocket.CONNECTING = _WS.CONNECTING;
  PatchedWebSocket.OPEN = _WS.OPEN;
  PatchedWebSocket.CLOSING = _WS.CLOSING;
  PatchedWebSocket.CLOSED = _WS.CLOSED;
  window.WebSocket = PatchedWebSocket;

  // ─── Visibility trick — hide "Active Now" ────────────────────────────────
  // Only applied if user enables online-status hiding
  // Wrapped in a named fn so Flutter can call it:
  // controller.evaluateJavascript(source: 'window.__fgEnableOnlineHide()')
  window.__fgEnableOnlineHide = function () {
    Object.defineProperty(document, 'visibilityState', {
      get: () => 'hidden',
      configurable: true,
    });
    Object.defineProperty(document, 'hidden', {
      get: () => true,
      configurable: true,
    });
    document.dispatchEvent(new Event('visibilitychange'));
  };

  window.__fgDisableOnlineHide = function () {
    // Restore by deleting the overrides (falls back to native getter)
    delete document.visibilityState;
    delete document.hidden;
    document.dispatchEvent(new Event('visibilitychange'));
  };

  // Signal to Flutter that ghost mode JS is active
  if (window.GhostChannel) {
    window.GhostChannel.postMessage(JSON.stringify({ type: 'ready' }));
  }
})();
