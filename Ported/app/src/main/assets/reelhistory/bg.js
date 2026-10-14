let nativePort = null;

function getPort() {
  if (!nativePort) {
    try {
      if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.connectNative) {
        nativePort = browser.runtime.connectNative('reelhistory');
        nativePort.onDisconnect.addListener(() => {
          nativePort = null;
        });
      }
    } catch (_) {
      nativePort = null;
    }
  }
  return nativePort;
}

browser.runtime.onMessage.addListener((m) => {
  if (!m || m.t !== 'fg-watched') return;
  if (!m.code || !/^[A-Za-z0-9_.-]{3,}$/.test(m.code)) return;

  const payload = {
    type: m.type === 'meta' ? 'meta' : 'watched',
    code: m.code,
    author: String(m.author || ''),
    caption: String(m.caption || ''),
  };

  try {
    const port = getPort();
    if (port) {
      port.postMessage(payload);
    }
  } catch (_) {}
});
