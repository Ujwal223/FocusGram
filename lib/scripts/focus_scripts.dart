import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import '../focus_settings.dart';

/// Flutter sets these flags after settings load to enable ghost modes.
/// Must be called from onWebViewCreated or on settings change.
const String kSetGhostFlagsJS = '''
(function(){
  // Placeholder — Flutter replaces these with actual setting values:
  // window.__fgPartialGhost = true/false;
  // window.__fgFullDmGhost = true/false;
  // window.__fgStoryGhost = true/false;
  // window.__fgGhostReady = true;  // signals scripts can proceed
})();
''';

// ═══════════════════════════════════════════════════════════════
// PARTIAL GHOST MODE — existing behavior
// Blocks seen API patterns, WebSocket chat gateways, and uses
// first-click gate for api/graphql on /direct/* (inbox loads, then block).
// ═══════════════════════════════════════════════════════════════
const String kPartialGhostJS = r'''
(function() {
  if (window.__fgPartialGhostPatched) return;
  window.__fgPartialGhostPatched = true;

  // ── Seen API patterns ──────────────────────────────────────
  var SEEN = [/\/api\/v1\/media\/[\w-]+\/seen\//,
              /\/api\/v1\/stories\/reel\/seen\//,
              /\/api\/v1\/direct_v2\/threads\/[\w-]+\/seen\//,
              /\/api\/v1\/direct_v2\/visual_message\/[\w-]+\/seen\//,
              /\/api\/v1\/live\/[\w-]+\/comment\/seen\//];
  function isSeen(u) { for(var i=0;i<SEEN.length;i++){if(SEEN[i].test(u))return true;}return false; }

  // ── First-click gate for api/graphql on /direct/* ──────────
  window.__fgDirectApiBlocked = false;
  document.addEventListener('click',function(){
    if(window.location.pathname.indexOf('/direct/')===0) window.__fgDirectApiBlocked=true;
  },true);
  document.addEventListener('touchstart',function(){
    if(window.location.pathname.indexOf('/direct/')===0) window.__fgDirectApiBlocked=true;
  },true);
  var _prevD=window.location.pathname.indexOf('/direct/')===0;
  setInterval(function(){
    var n=window.location.pathname.indexOf('/direct/')===0;
    if(n!==_prevD){_prevD=n;window.__fgDirectApiBlocked=false;}
  },300);

  function partialEnabled() { return window.__fgPartialGhost===true; }
  function shouldBlock(u) {
    if (!partialEnabled()) return false;
    return window.location.pathname.indexOf('/direct/')===0 &&
           window.__fgDirectApiBlocked &&
           u.indexOf('/api/graphql')!==-1;
  }

  // ── Fetch override (chain with previous fetch) ─────────────
  var _prevFetch = window.fetch;
  window.fetch=function(i,init){
    var u=(typeof i==='string')?i:(i&&i.url)?i.url:'';
    if(partialEnabled()&&(isSeen(u)||shouldBlock(u))) return Promise.resolve(new Response(JSON.stringify({status:'ok'}),{status:200,headers:{'Content-Type':'application/json'}}));
    return _prevFetch.call(window,i,init);
  };

  // ── XHR override (chain) ───────────────────────────────────
  var _prevOpen=XMLHttpRequest.prototype.open,_prevSend=XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open=function(m,u){this.__fgU=u||'';return _prevOpen.apply(this,arguments);};
  XMLHttpRequest.prototype.send=function(b){
    if(partialEnabled()&&(isSeen(this.__fgU||'')||shouldBlock(this.__fgU||''))){
      var self=this;setTimeout(function(){
        Object.defineProperty(self,'readyState',{get:function(){return 4}});
        Object.defineProperty(self,'status',{get:function(){return 200}});
        Object.defineProperty(self,'responseText',{get:function(){return '{"status":"ok"}'}});
        Object.defineProperty(self,'response',{get:function(){return '{"status":"ok"}'}});
        try{self.onreadystatechange&&self.onreadystatechange();}catch(e){}
        try{self.onload&&self.onload();}catch(e){}
        ['readystatechange','load'].forEach(function(t){try{self.dispatchEvent(new Event(t));}catch(e){}});
      },5);return;
    }
    return _prevSend.apply(this,arguments);
  };

  // ── Selective WS seen-message filter (no gouger) ───────────
  (function() {
    var _WS = window.WebSocket;
    function PartialWS(url, protocols) {
      var ws = protocols ? new _WS(url, protocols) : new _WS(url);
      var _send = ws.send.bind(ws);
      ws.send = function(data) {
        if (typeof data === 'string') {
          try {
            var parsed = JSON.parse(data);
            if (parsed && (parsed.op === '4' || parsed.op === 'seen')) return;
          } catch(e) {}
          if (data.indexOf('"seen"') !== -1 && data.indexOf('"thread_id"') !== -1) return;
        }
        return _send(data);
      };
      return ws;
    }
    PartialWS.prototype = _WS.prototype;
    PartialWS.CONNECTING = _WS.CONNECTING;
    PartialWS.OPEN = _WS.OPEN;
    PartialWS.CLOSING = _WS.CLOSING;
    PartialWS.CLOSED = _WS.CLOSED;
    window.WebSocket = PartialWS;
  })();
})();
''';

// ═══════════════════════════════════════════════════════════════
// FULL DM GHOST — blocks ALL api/graphql on /direct/* immediately
// (inbox won't load, messages can't be sent)
// ═══════════════════════════════════════════════════════════════
const String kFullDmGhostJS = r'''
(function() {
  if (window.__fgFullDmGhostPatched) return;
  window.__fgFullDmGhostPatched = true;

  // ── Smart path-based blocking ──────────────────────────────
  // /direct/inbox/   → allow (inbox loads)
  // /direct/t/*      → block ALL api/graphql immediately
  // any /direct/*    → block except /direct/inbox/
  function shouldBlockDmPath() {
    if (window.__fgFullDmGhost !== true) return false;
    var p = window.location.pathname;
    if (p.indexOf('/direct/') !== 0) return false;
    if (p === '/direct/inbox/' || p === '/direct/inbox') return false;
    return true;
  }

  // ── DM URL blocklist ───────────────────────────────────────
  var DM_URLS = [
    /\\/api\\/v1\\/direct_v2\\/threads\\/[^/]+\\/mark_item_seen\\//,
    /\\/api\\/v1\\/direct_v2\\/mark_item_seen\\//,
    /\\/api\\/v1\\/direct_v2\\/threads\\/[^/]+\\/items\\/[^/]+\\/mark_visual_item_seen\\//,
    /\\/api\\/v1\\/direct_v2\\/visual_thread\\/[^/]+\\/seen\\//,
    /\\/api\\/v1\\/direct_v2\\/threads\\/[^/]+\\/items\\/[^/]+\\/mark_audio_seen\\//,
    /\\/api\\/v1\\/live\\/[^/]+\\/join\\//,
    /\\/api\\/v1\\/live\\/[^/]+\\/get_join_requests\\//,
    /\\/api\\/v1\\/media\\/seen\\//,
    /\\/api\\/v1\\/feed\\/viewed_story\\//,
    /\\/api\\/v1\\/feed\\/reels_tray\\/seen\\//,
    /\\/api\\/v1\\/media\\/[\\w-]+\\/seen\\//,
    /\\/api\\/v1\\/stories\\/reel\\/seen\\//,
    /\\/api\\/v1\\/direct_v2\\/threads\\/[\\w-]+\\/seen\\//,
    /\\/api\\/v1\\/direct_v2\\/visual_message\\/[\\w-]+\\/seen\\//,
    /\\/api\\/v1\\/live\\/[\\w-]+\\/comment\\/seen\\//,
    /\\/api\\/v1\\/qe\\//,
    /\\/api\\/v1\\/launcher\\/sync\\//,
    /\\/api\\/v1\\/logging\\//,
    /\\/api\\/v1\\/fb_onetap_logging\\//,
    /\\/ajax\\/bz/,
    /\\/ajax\\/logging\\//,
    /\\/api\\/v1\\/stats\\//,
    /\\/api\\/v1\\/fbanalytics\\//,
  ];

  function matchUrl(url) {
    if (!url) return false;
    for (var i = 0; i < DM_URLS.length; i++) { if (DM_URLS[i].test(url)) return true; }
    return false;
  }

  // ── DM GraphQL operations ──────────────────────────────────
  var DM_OPS = [
    'MarkDirectThreadItemSeen','markDirectThreadItemSeen',
    'DirectMarkItemSeen','DirectThreadMarkSeen',
    'MarkVisualMessageSeen','DirectMarkVisualItemSeen',
    'MarkAudioMessageSeen','AudioSeenMutation',
    'LiveJoinBroadcast','JoinLiveBroadcast','MarkLiveViewer',
    'MarkStorySeen','markStorySeen','ReelSeenMutation','reel_seen','IgFeedSeen',
    'LogImpression','LogClick','FeedbackSeenMutation',
  ];

  function matchGraphQL(body) {
    if (!body) return false;
    var str = typeof body === 'string' ? body : String(body);
    for (var i = 0; i < DM_OPS.length; i++) { if (str.indexOf(DM_OPS[i]) !== -1) return true; }
    return false;
  }

  function isGraphql(url) {
    return url.indexOf('/api/graphql') !== -1 || url.indexOf('/graphql') !== -1;
  }

  function shouldBlock(url, init) {
    // 1. Path-based: on /direct/t/* block ALL graphql
    if (shouldBlockDmPath() && isGraphql(url)) return true;
    // 2. URL blocklist match
    if (matchUrl(url)) return true;
    // 3. GraphQL body op-name match
    if (isGraphql(url) && init) {
      var bs = '';
      if (typeof init.body === 'string') bs = init.body;
      else if (init.body && init.body.toString) bs = init.body.toString();
      if (matchGraphQL(bs)) return true;
    }
    return false;
  }

  function fakeOk() { return new Response(JSON.stringify({status:'ok'}),{status:200,headers:{'Content-Type':'application/json'}}); }

  // ── Fetch override (chain) ─────────────────────────────────
  var _prevFetch = window.fetch;
  window.fetch = function(i, init) {
    var u = (typeof i === 'string') ? i : (i && i.url) ? i.url : String(i);
    if (shouldBlock(u, init)) return Promise.resolve(fakeOk());
    return _prevFetch.apply(this, arguments);
  };

  // ── XHR override (chain) ───────────────────────────────────
  var _prevOpen = XMLHttpRequest.prototype.open;
  var _prevSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function(m, u) { this.__fgDU = u || ''; return _prevOpen.apply(this, arguments); };
  XMLHttpRequest.prototype.send = function(b) {
    var u = this.__fgDU || '';
    if (shouldBlock(u, {body: b}) || (isGraphql(u) && shouldBlockDmPath())) {
      var self = this;
      setTimeout(function() {
        Object.defineProperty(self,'readyState',{get:function(){return 4}});
        Object.defineProperty(self,'status',{get:function(){return 200}});
        Object.defineProperty(self,'responseText',{get:function(){return '{"status":"ok"}'}});
        Object.defineProperty(self,'response',{get:function(){return '{"status":"ok"}'}});
        try{self.onreadystatechange&&self.onreadystatechange();}catch(e){}
        try{self.onload&&self.onload();}catch(e){}
        ['readystatechange','load'].forEach(function(t){try{self.dispatchEvent(new Event(t));}catch(e){}});
      }, 5);
      return;
    }
    return _prevSend.apply(this, arguments);
  };

  // ── SW killer ──────────────────────────────────────────────
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register = function() { return Promise.reject(new Error('blocked')); };
    navigator.serviceWorker.getRegistrations().then(function(regs) { regs.forEach(function(r) { r.unregister(); }); }).catch(function(){});
  }

  // ── Beacon blocker ─────────────────────────────────────────
  if (navigator.sendBeacon) {
    navigator.sendBeacon = function(url) { return true; };
  }

  // ── MQTT WS intercept (typing / live viewer / seen) ────────
  // Instagram uses MQTT over WebSocket for real-time events.
  // '/t_fs' = foreground state, '/t_mt' = mark thread seen,
  // '/t_s' and '/t_se' = seen receipts, 'activity_indicator' = active status.
  (function() {
    var _WS = window.WebSocket;
    function DmGhostWS(url, protocols) {
      var ws = protocols ? new _WS(url, protocols) : new _WS(url);
      var _send = ws.send.bind(ws);
      ws.send = function(data) {
        if (data instanceof ArrayBuffer || data instanceof Uint8Array) {
          var bytes = data instanceof ArrayBuffer ? new Uint8Array(data) : data;
          var packetType = bytes[0] & 0xF0;
          if (packetType === 0x30) {
            try {
              var decoded = new TextDecoder('utf-8').decode(bytes);
              if (decoded.indexOf('/t_fs') !== -1 || decoded.indexOf('/t_mt') !== -1 ||
                  decoded.indexOf('/t_s') !== -1 || decoded.indexOf('/t_se') !== -1 ||
                  decoded.indexOf('activity_indicator') !== -1 ||
                  decoded.indexOf('is_typing') !== -1 || decoded.indexOf('direct_typing') !== -1 ||
                  decoded.indexOf('/live/viewer') !== -1 || decoded.indexOf('live_viewer_list') !== -1) {
                return;
              }
            } catch(e) {}
          }
        } else if (typeof data === 'string') {
          if (data.indexOf('typing') !== -1 || data.indexOf('live_viewer') !== -1 ||
              data.indexOf('is_typing') !== -1 || data.indexOf('mark_seen') !== -1 ||
              data.indexOf('mark_read') !== -1 || data.indexOf('receipt') !== -1) return;
        }
        return _send(data);
      };
      return ws;
    }
    DmGhostWS.prototype = _WS.prototype;
    DmGhostWS.CONNECTING = _WS.CONNECTING;
    DmGhostWS.OPEN = _WS.OPEN;
    DmGhostWS.CLOSING = _WS.CLOSING;
    DmGhostWS.CLOSED = _WS.CLOSED;
    window.WebSocket = DmGhostWS;
  })();
})();
''';

// ═══════════════════════════════════════════════════════════════
// STORY GHOST — blocks api/graphql on homepage (/) and /stories/*
// Allows viewing stories without sending seen indicators.
// ═══════════════════════════════════════════════════════════════
const String kStoryGhostJS = r'''
(function() {
  if (window.__fgStoryGhostPatched) return;
  window.__fgStoryGhostPatched = true;

  // ── Smart path-based blocking ──────────────────────────────
  // On /, /stories/*, /story/* → block ALL api/graphql
  // On /direct/inbox/ → allow (DMs need graphql to load messages)
  function shouldBlockByPath() {
    if (window.__fgStoryGhost !== true) return false;
    var p = window.location.pathname;
    // Don't block on DM pages
    if (p.indexOf('/direct/') === 0) return false;
    var isStory = p.indexOf('/stories/') === 0 || p.indexOf('/story/') === 0;
    var isHome = p === '/' || p === '';
    return isHome || isStory;
  }

  // ── Story URL blocklist ────────────────────────────────────
  var STORY_URLS = [
    /\\/api\\/v1\\/media\\/[\\w-]+\\/seen\\//,
    /\\/api\\/v1\\/stories\\/reel\\/seen\\//,
    /\\/api\\/v1\\/feed\\/viewed_story\\//,
    /\\/api\\/v1\\/feed\\/reels_tray\\/seen\\//,
    /\\/api\\/v1\\/media\\/seen\\//,
  ];

  function matchUrl(url) {
    if (!url) return false;
    for (var i = 0; i < STORY_URLS.length; i++) { if (STORY_URLS[i].test(url)) return true; }
    return false;
  }

  // ── Story GraphQL operations ───────────────────────────────
  var STORY_OPS = [
    'MarkStorySeen','markStorySeen','ReelSeenMutation','reel_seen','IgFeedSeen',
    'FeedbackSeenMutation',
  ];

  function matchGraphQL(body) {
    if (!body) return false;
    var str = typeof body === 'string' ? body : String(body);
    for (var i = 0; i < STORY_OPS.length; i++) { if (str.indexOf(STORY_OPS[i]) !== -1) return true; }
    return false;
  }

  function isGraphql(url) {
    return url.indexOf('/api/graphql') !== -1 || url.indexOf('/graphql') !== -1;
  }

  function shouldBlock(url, init) {
    // 1. Path-based: on story pages block ALL graphql
    if (shouldBlockByPath() && isGraphql(url)) return true;
    // 2. URL blocklist match
    if (matchUrl(url)) return true;
    // 3. GraphQL body op-name match
    if (isGraphql(url) && init) {
      var bs = '';
      if (typeof init.body === 'string') bs = init.body;
      else if (init.body && init.body.toString) bs = init.body.toString();
      if (matchGraphQL(bs)) return true;
    }
    return false;
  }

  function fakeOk() { return new Response(JSON.stringify({status:'ok'}),{status:200,headers:{'Content-Type':'application/json'}}); }

  // ── Fetch override (chain) ─────────────────────────────────
  var _prevFetch = window.fetch;
  window.fetch = function(i, init) {
    var u = (typeof i === 'string') ? i : (i && i.url) ? i.url : String(i);
    if (shouldBlock(u, init)) return Promise.resolve(fakeOk());
    return _prevFetch.apply(this, arguments);
  };

  // ── XHR override (chain) ───────────────────────────────────
  var _prevOpen = XMLHttpRequest.prototype.open;
  var _prevSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function(m, u) { this.__fgSU = u || ''; return _prevOpen.apply(this, arguments); };
  XMLHttpRequest.prototype.send = function(b) {
    var u = this.__fgSU || '';
    if (shouldBlock(u, {body: b}) || (isGraphql(u) && shouldBlockByPath())) {
      var self = this;
      setTimeout(function() {
        Object.defineProperty(self,'readyState',{get:function(){return 4}});
        Object.defineProperty(self,'status',{get:function(){return 200}});
        Object.defineProperty(self,'responseText',{get:function(){return '{"status":"ok"}'}});
        Object.defineProperty(self,'response',{get:function(){return '{"status":"ok"}'}});
        try{self.onreadystatechange&&self.onreadystatechange();}catch(e){}
        try{self.onload&&self.onload();}catch(e){}
        ['readystatechange','load'].forEach(function(t){try{self.dispatchEvent(new Event(t));}catch(e){}});
      }, 5);
      return;
    }
    return _prevSend.apply(this, arguments);
  };

  // ── SW killer ──────────────────────────────────────────────
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register = function() { return Promise.reject(new Error('blocked')); };
    navigator.serviceWorker.getRegistrations().then(function(regs) { regs.forEach(function(r) { r.unregister(); }); }).catch(function(){});
  }

  // ── Beacon blocker ─────────────────────────────────────────
  if (navigator.sendBeacon) {
    navigator.sendBeacon = function(url) { return true; };
  }
})();
''';

// ═══════════════════════════════════════════════════════════════
// Builder — injects the right scripts based on settings
// ═══════════════════════════════════════════════════════════════
List<UserScript> buildUserScripts(FocusSettings settings) {
  final startScripts = <String>[];
  final endScripts = <String>[];

  // Prepend flag values directly into the script so they survive page navigation.
  // (evaluateJavascript-set flags are destroyed when the JS context resets on load.)
  // DM Ghost uses the comprehensive Full DM approach (URL blocklist, GraphQL ops, SW killer, beacon, WS).
  // it should have worked, but sadly it didnt
  if (settings.ghostMode) {
    startScripts.add('window.__fgFullDmGhost=true;$kFullDmGhostJS');
  }
  if (settings.noAutoplay) startScripts.add(noAutoplayJS);

  // AT_DOCUMENT_END
  if (settings.noStories) endScripts.add(hideStoryTrayJS);
  if (settings.noReels) endScripts.add(hideReelsJS);
  if (settings.noDMs) endScripts.add(hideDMsJS);

  final scripts = <UserScript>[];
  if (startScripts.isNotEmpty) {
    scripts.add(
      UserScript(
        source: startScripts.join('\n'),
        injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
        forMainFrameOnly: false,
      ),
    );
  }
  if (endScripts.isNotEmpty) {
    scripts.add(
      UserScript(
        source: endScripts.join('\n'),
        injectionTime: UserScriptInjectionTime.AT_DOCUMENT_END,
        forMainFrameOnly: true,
      ),
    );
  }
  return scripts;
}

// ── Existing non-ghost helpers (unchanged) ───────────────────

const String noAutoplayJS = '''
document.addEventListener('play', function(e) {
  if (e.target.tagName === 'VIDEO') e.target.pause();
}, true);
''';

const String hideStoryTrayJS = '''
(function(){var s=document.createElement('style');s.textContent='[data-pagelet="story_tray"]{display:none!important}';document.head.appendChild(s);})();
''';

const String hideReelsJS = '''
(function(){new MutationObserver(function(){document.querySelectorAll('a[href="/reels/"]').forEach(function(e){var p=e.closest('div');if(p)p.style.setProperty('display','none','important')});document.querySelectorAll('a[href="/explore/"]').forEach(function(e){var p=e.closest('div');if(p)p.style.setProperty('display','none','important')})}).observe(document.body,{childList:true,subtree:true});})();
''';

const String hideDMsJS = '''
(function(){var s=document.createElement('style');s.textContent='a[href="/direct/inbox/"]{display:none!important}';document.head.appendChild(s);})();
''';
