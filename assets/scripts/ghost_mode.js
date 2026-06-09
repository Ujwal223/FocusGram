/**
 * FocusGram Ghost Mode (V2 Overlay)
 * Injected at DOCUMENT_START — before Instagram's JS loads.
 * Blocks story-seen, message-seen, and online-presence signals.
 *
 * Uses _prev chain pattern: each section saves the PREVIOUS fetch/XHR
 * before overriding, so they compose rather than conflict.
 */
(function () {
  'use strict';

  // ─── First-interaction DM gate ──────────────────────────────────────────
  // On /direct/*, first click blocks all api/graphql (inbox loads first).
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

  // ─── SEEN + ACTIVITY patterns ───────────────────────────────────────────
  const SEEN_PATTERNS = [
    /\/api\/v1\/media\/[\w-]+\/seen\//,
    /\/api\/v1\/stories\/reel\/seen\//,
    /\/api\/v1\/direct_v2\/threads\/[\w-]+\/seen\//,
    /\/api\/v1\/direct_v2\/visual_message\/[\w-]+\/seen\//,
    /\/api\/v1\/live\/[\w-]+\/comment\/seen\//,
  ];

  const ACTIVITY_PATTERNS = [
    /\/api\/v1\/web\/likes\/[\w-]+\/like\//,
    /\/api\/v1\/web\/comments\/add\//,
    /\/api\/v1\/friendships\/[\w-]+\/follow\//,
  ];

  const isSeen = (url) => SEEN_PATTERNS.some((p) => p.test(url));
  const isActivity = (url) => ACTIVITY_PATTERNS.some((p) => p.test(url));

  // ─── Fetch override — chains with whatever was there ──────────────────────
  const _prevFetch = window.fetch;
  window.fetch = async function (input, init) {
    const url =
      typeof input === 'string'
        ? input
        : input instanceof URL
        ? input.href
        : input?.url ?? '';

    // DM first-interaction gate
    if (_blockIfNeeded(url)) {
      return new Response(JSON.stringify({ status: 'ok' }), {
        status: 200, headers: { 'Content-Type': 'application/json' }
      });
    }

    // Seen pattern block
    if (isSeen(url)) {
      if (window.GhostChannel) {
        window.GhostChannel.postMessage(JSON.stringify({ type: 'seen_blocked', url }));
      }
      return new Response(JSON.stringify({ status: 'ok' }), {
        status: 200, headers: { 'Content-Type': 'application/json' }
      });
    }

    // Activity interceptor for local history
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

    return _prevFetch(input, init);
  };
  window.fetch.toString = () => 'function fetch() { [native code] }';

  // ─── XHR override — chains ──────────────────────────────────────────────
  const _prevOpen = XMLHttpRequest.prototype.open;
  const _prevSend = XMLHttpRequest.prototype.send;

  XMLHttpRequest.prototype.open = function (method, url, ...args) {
    this._fg_url = url ?? '';
    this._fg_method = (method ?? '').toUpperCase();
    return _prevOpen.call(this, method, url, ...args);
  };

  XMLHttpRequest.prototype.send = function (body) {
    const url = this._fg_url || '';

    // DM first-interaction gate
    if (_blockIfNeeded(url)) {
      const self = this;
      setTimeout(() => {
        Object.defineProperty(self, 'readyState', { get: () => 4 });
        Object.defineProperty(self, 'status', { get: () => 200 });
        Object.defineProperty(self, 'responseText', { get: () => '{"status":"ok"}' });
        Object.defineProperty(self, 'response', { get: () => '{"status":"ok"}' });
        ['readystatechange', 'load'].forEach(function(t) {
          try { self.dispatchEvent(new Event(t)); } catch(e) {}
        });
      }, 5);
      return;
    }

    // Seen pattern block
    if (url && isSeen(url)) {
      const self = this;
      setTimeout(() => {
        Object.defineProperty(self, 'readyState', { get: () => 4 });
        Object.defineProperty(self, 'status', { get: () => 200 });
        Object.defineProperty(self, 'responseText', { get: () => '{"status":"ok"}' });
        Object.defineProperty(self, 'response', { get: () => '{"status":"ok"}' });
        ['readystatechange', 'load'].forEach(function(t) {
          try { self.dispatchEvent(new Event(t)); } catch(e) {}
        });
      }, 5);
      return;
    }

    return _prevSend.call(this, body);
  };

  // ─── WebSocket intercept (message-seen via WS) ──────────────────────────
  const _WS = window.WebSocket;

  function PatchedWebSocket(url, protocols) {
    const ws = protocols ? new _WS(url, protocols) : new _WS(url);
    const _send = ws.send.bind(ws);

    ws.send = function (data) {
      if (typeof data === 'string') {
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
        if (data.includes('"seen"') && data.includes('"thread_id"')) {
          return;
        }
      }
      return _send(data);
    };

    return ws;
  }

  PatchedWebSocket.prototype = _WS.prototype;
  PatchedWebSocket.CONNECTING = _WS.CONNECTING;
  PatchedWebSocket.OPEN = _WS.OPEN;
  PatchedWebSocket.CLOSING = _WS.CLOSING;
  PatchedWebSocket.CLOSED = _WS.CLOSED;
  window.WebSocket = PatchedWebSocket;

  // ─── Visibility trick — hide "Active Now" ──────────────────────────────
  window.__fgEnableOnlineHide = function () {
    Object.defineProperty(document, 'visibilityState', {
      get: () => 'hidden', configurable: true,
    });
    Object.defineProperty(document, 'hidden', {
      get: () => true, configurable: true,
    });
    document.dispatchEvent(new Event('visibilitychange'));
  };

  window.__fgDisableOnlineHide = function () {
    delete document.visibilityState;
    delete document.hidden;
    document.dispatchEvent(new Event('visibilitychange'));
  };

  // Signal to Flutter that ghost mode JS is active
  if (window.GhostChannel) {
    window.GhostChannel.postMessage(JSON.stringify({ type: 'ready' }));
  }
})();
