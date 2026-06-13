// lib/services/ghost_mode_script.dart
// Injected at AT_DOCUMENT_START — before Instagram's JS caches fetch/XHR refs

const String kGhostModeJS = r"""
(function () {
  'use strict';

  // ─── BLOCKED REST ENDPOINTS ───────────────────────────────────────────────
  // Patterns matched against full request URL
  const URL_BLOCKLIST = [
    // Story viewed receipts
    /\/api\/v1\/media\/seen\//,
    /\/api\/v1\/feed\/viewed_story\//,
    /\/api\/v1\/feed\/reels_tray\/seen\//,

    // DM read receipts (REST fallback path)
    /\/api\/v1\/direct_v2\/threads\/[^/]+\/mark_item_seen\//,
    /\/api\/v1\/direct_v2\/mark_item_seen\//,

    // Ephemeral photo/video reply viewed (Anti-Reply Image)
    /\/api\/v1\/direct_v2\/threads\/[^/]+\/items\/[^/]+\/mark_visual_item_seen\//,
    /\/api\/v1\/direct_v2\/visual_thread\/[^/]+\/seen\//,

    // Voice message listened receipt
    /\/api\/v1\/direct_v2\/threads\/[^/]+\/items\/[^/]+\/mark_audio_seen\//,

    // Live join broadcast notification
    /\/api\/v1\/live\/[^/]+\/join\//,
    /\/api\/v1\/live\/[^/]+\/get_join_requests\//,
    /\/api\/v1\/live\/[^/]+\/start_broadcast\//,

    // Analytics / tracking
    /\/api\/v1\/qe\//,
    /\/api\/v1\/launcher\/sync\//,
    /\/api\/v1\/logging\//,
    /\/api\/v1\/fb_onetap_logging\//,
    /\/ajax\/bz/,
    /\/ajax\/logging\//,
    /\/api\/v1\/stats\//,
    /\/api\/v1\/fbanalytics\//,
    /\/api\/v1\/growth\/account_linked_now\//,
  ];

  // ─── BLOCKED GRAPHQL OPERATIONS ───────────────────────────────────────────
  // Instagram web uses GraphQL for many actions — match by operation name in body
  const GRAPHQL_OP_BLOCKLIST = [
    // Story seen
    'MarkStorySeen',
    'markStorySeen',
    'ReelSeenMutation',
    'reel_seen',
    'IgFeedSeen',

    // DM read receipts
    'MarkDirectThreadItemSeen',
    'markDirectThreadItemSeen',
    'DirectMarkItemSeen',
    'DirectThreadMarkSeen',

    // Ephemeral media seen
    'MarkVisualMessageSeen',
    'DirectMarkVisualItemSeen',

    // Voice message listened
    'MarkAudioMessageSeen',
    'AudioSeenMutation',

    // Live join
    'LiveJoinBroadcast',
    'JoinLiveBroadcast',
    'MarkLiveViewer',

    // Analytics mutations
    'LogImpression',
    'LogClick',
    'FeedbackSeenMutation',
  ];

  // ─── HELPERS ──────────────────────────────────────────────────────────────
  function shouldBlockUrl(url) {
    if (!url) return false;
    try {
      const path = new URL(url, location.origin).pathname + new URL(url, location.origin).search;
      return URL_BLOCKLIST.some(p => p.test(path));
    } catch {
      return URL_BLOCKLIST.some(p => p.test(url));
    }
  }

  function shouldBlockGraphQL(body) {
    if (!body) return false;
    let str = '';
    if (typeof body === 'string') {
      str = body;
    } else if (body instanceof URLSearchParams) {
      str = body.toString();
    }
    return GRAPHQL_OP_BLOCKLIST.some(op => str.includes(op));
  }

  function isGraphQLEndpoint(url) {
    return url.includes('/graphql') || url.includes('/api/graphql');
  }

  function fakeOk(body) {
    return new Response(
      JSON.stringify(body || { status: 'ok', result: 'success' }),
      {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }
    );
  }

  // ─── FETCH INTERCEPT ──────────────────────────────────────────────────────
  const _fetch = window.fetch;
  window.fetch = async function (input, init) {
    const url =
      typeof input === 'string'
        ? input
        : input instanceof Request
        ? input.url
        : String(input);

    if (shouldBlockUrl(url)) {
      return fakeOk();
    }

    // Clone body for GraphQL inspection without consuming it
    if (isGraphQLEndpoint(url) && init) {
      let bodyStr = '';
      if (typeof init.body === 'string') {
        bodyStr = init.body;
      } else if (init.body instanceof URLSearchParams) {
        bodyStr = init.body.toString();
      } else if (init.body instanceof FormData) {
        // FormData: iterate entries to build string
        try {
          init.body.forEach((v, k) => { bodyStr += k + '=' + v + '&'; });
        } catch {}
      }
      if (shouldBlockGraphQL(bodyStr)) {
        return fakeOk();
      }
    }

    return _fetch.apply(this, arguments);
  };

  // ─── XHR INTERCEPT ───────────────────────────────────────────────────────
  const _xhrOpen = XMLHttpRequest.prototype.open;
  const _xhrSend = XMLHttpRequest.prototype.send;

  XMLHttpRequest.prototype.open = function (method, url) {
    this.__ghostUrl = url;
    return _xhrOpen.apply(this, arguments);
  };

  XMLHttpRequest.prototype.send = function (body) {
    const url = this.__ghostUrl || '';

    const blockByUrl = shouldBlockUrl(url);
    const blockByOp = isGraphQLEndpoint(url) && shouldBlockGraphQL(
      typeof body === 'string' ? body : ''
    );

    if (blockByUrl || blockByOp) {
      const self = this;
      // Must use defineProperty because readyState etc are read-only
      Object.defineProperty(self, 'readyState', { get: () => 4, configurable: true });
      Object.defineProperty(self, 'status',    { get: () => 200, configurable: true });
      Object.defineProperty(self, 'responseText', {
        get: () => '{"status":"ok"}',
        configurable: true,
      });
      Object.defineProperty(self, 'response', {
        get: () => '{"status":"ok"}',
        configurable: true,
      });
      setTimeout(() => {
        try { self.onreadystatechange && self.onreadystatechange(); } catch {}
        try { self.onload && self.onload(); } catch {}
        // Fire events
        ['readystatechange', 'load'].forEach(t => {
          try { self.dispatchEvent(new Event(t)); } catch {}
        });
      }, 10);
      return;
    }

    return _xhrSend.apply(this, arguments);
  };

  // ─── WEBSOCKET INTERCEPT (typing + live join) ─────────────────────────────
  // Instagram uses MQTT over WebSocket for real-time events.
  // Typing indicator = MQTT PUBLISH to topic containing typing/activity tokens.
  // Live join viewer notification = MQTT PUBLISH with live topic.
  const _OrigWS = window.WebSocket;

  function GhostWebSocket(url, protocols) {
    const ws = protocols ? new _OrigWS(url, protocols) : new _OrigWS(url);
    const _wsSend = ws.send.bind(ws);

    ws.send = function (data) {
      if (data instanceof ArrayBuffer || data instanceof Uint8Array) {
        const bytes = data instanceof ArrayBuffer ? new Uint8Array(data) : data;

        // MQTT packet type in top 4 bits of byte 0
        // PUBLISH = 0x3x (0x30 QoS0, 0x32 QoS1, 0x34 QoS2)
        const packetType = bytes[0] & 0xF0;
        if (packetType === 0x30) {
          // Read remaining length (byte 1, simplified for short packets)
          // MQTT topic starts at byte 4 (2 byte remaining-len + 2 byte topic-len)
          try {
            const decoded = new TextDecoder('utf-8', { fatal: false }).decode(bytes);
            // Block typing / activity indicator / seen-receipt publishes
            if (
              decoded.includes('/t_fs')          || // foreground state (typing)
              decoded.includes('/t_mt')          || // mark thread seen
              decoded.includes('/t_s')           || // seen receipt
              decoded.includes('/t_se')          || // seen receipt (alt)
              decoded.includes('activity_indicator') ||
              decoded.includes('is_typing')       ||
              decoded.includes('direct_typing')   ||
              decoded.includes('/live/viewer')    ||   // live join notification
              decoded.includes('live_viewer_list')
            ) {
              return; // Drop packet silently
            }
          } catch {}
        }
      } else if (typeof data === 'string') {
        // Some WS implementations send JSON
        if (
          data.includes('typing') ||
          data.includes('live_viewer') ||
          data.includes('is_typing')
        ) {
          return;
        }
      }

      return _wsSend(data);
    };

    return ws;
  }

  // Preserve static properties
  GhostWebSocket.prototype = _OrigWS.prototype;
  Object.assign(GhostWebSocket, {
    CONNECTING: _OrigWS.CONNECTING,
    OPEN:       _OrigWS.OPEN,
    CLOSING:    _OrigWS.CLOSING,
    CLOSED:     _OrigWS.CLOSED,
  });
  window.WebSocket = GhostWebSocket;

  // ─── KILL SERVICE WORKER ──────────────────────────────────────────────────
  // SW runs in separate context — bypasses all JS intercepts above.
  // Kill registration so our fetch/XHR overrides are the only intercept layer.
  if ('serviceWorker' in navigator) {
    // Block new registrations
    navigator.serviceWorker.register = function () {
      return Promise.reject(new Error('[GhostMode] SW blocked'));
    };
    // Unregister any already registered
    navigator.serviceWorker.getRegistrations().then(regs => {
      regs.forEach(r => r.unregister());
    }).catch(() => {});
  }

  // ─── BEACON API BLOCK ────────────────────────────────────────────────────
  // Instagram uses sendBeacon for analytics on page unload
  if (navigator.sendBeacon) {
    navigator.sendBeacon = function (url) {
      if (shouldBlockUrl(url)) return true; // Lie — say it succeeded
      // Block all beacon calls to ig domains — analytics only
      if (url.includes('instagram.com') || url.includes('facebook.com')) return true;
      return false;
    };
  }

  console.log('[FocusGram] GhostMode active');
})();
""";
