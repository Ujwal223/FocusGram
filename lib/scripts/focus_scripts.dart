import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import '../focus_settings.dart';

// Ghost Mode
const String ghostModeJS = '''
const _WS = window.WebSocket;
window.WebSocket = function(url, protocols) {
  if (url.includes('edge-chat.instagram.com') || 
      url.includes('gateway.instagram.com')) {
    return {
      send: ()=>{}, close: ()=>{},
      readyState: 1,
      addEventListener: ()=>{},
      removeEventListener: ()=>{},
    };
  }
  return new _WS(url, protocols);
};
window.WebSocket.prototype = _WS.prototype;
''';

// No Story Tray
const String hideStoryTrayJS = '''
const style = document.createElement('style');
style.textContent = '[data-pagelet="story_tray"] { display: none !important; }';
document.head.appendChild(style);
''';

// No Autoplay
const String noAutoplayJS = '''
document.addEventListener('play', function(e) {
  if (e.target.tagName === 'VIDEO') {
    e.target.pause();
  }
}, true);
''';

// No Reels / Explore
const String hideReelsJS = '''
const hideReels = () => {
  // nav bar reels icon
  document.querySelectorAll('a[href="/reels/"]').forEach(el => {
    el.closest('div')?.style.setProperty('display', 'none', 'important');
  });
  // explore page
  document.querySelectorAll('a[href="/explore/"]').forEach(el => {
    el.closest('div')?.style.setProperty('display', 'none', 'important');
  });
};

new MutationObserver(hideReels).observe(document.body, {
  childList: true,
  subtree: true
});

hideReels();
''';

// No DMs
const String hideDMsJS = '''
const style = document.createElement('style');
style.textContent = 'a[href="/direct/inbox/"] { display: none !important; }';
document.head.appendChild(style);
''';

List<UserScript> buildUserScripts(FocusSettings settings) {
  final startScripts = <String>[];
  final endScripts = <String>[];

  // AT_DOCUMENT_START scripts
  if (settings.ghostMode) startScripts.add(ghostModeJS);
  if (settings.noAutoplay) startScripts.add(noAutoplayJS);

  // AT_DOCUMENT_END scripts
  if (settings.noStories) endScripts.add(hideStoryTrayJS);
  if (settings.noReels) endScripts.add(hideReelsJS);
  if (settings.noDMs) endScripts.add(hideDMsJS);

  final scripts = <UserScript>[];
  if (startScripts.isNotEmpty) {
    scripts.add(UserScript(
      source: startScripts.join('\n'),
      injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
      forMainFrameOnly: false,
    ));
  }
  if (endScripts.isNotEmpty) {
    scripts.add(UserScript(
      source: endScripts.join('\n'),
      injectionTime: UserScriptInjectionTime.AT_DOCUMENT_END,
      forMainFrameOnly: true,
    ));
  }
  return scripts;
}