let lastBeat = 0;   // last time the page said it is on /direct/t/
let armedAt = 0;    // last time a reel feed request was cancelled

browser.runtime.onMessage.addListener((m) => {
  if (!m || m.t !== 'fg-reel') return;
  lastBeat = (m.active && m.enabled !== false) ? Date.now() : 0;
  return Promise.resolve({ armedAt });   // tell the page when a reel was last detected
});

// Match request names first, doc_ids as a backup (doc_ids change on Instagram updates).
const NAME_RE = /Reels?|Clips|SuggestedQuery/i;
const DOC_IDS = new Set(['28311980015163583', '28774841968839907']);

browser.webRequest.onBeforeRequest.addListener(
  (d) => {
    if (Date.now() - lastBeat > 1500) return {};
    const f = d.requestBody && d.requestBody.formData;
    if (!f) return {};
    const name = (f.fb_api_req_friendly_name || [])[0] || '';
    const doc = (f.doc_id || [])[0] || '';
    if (NAME_RE.test(name) || DOC_IDS.has(doc)) {
      armedAt = Date.now();
      return { cancel: true };
    }
    return {};
  },
  { urls: ['*://www.instagram.com/graphql/query*'], types: ['xmlhttprequest'] },
  ['blocking', 'requestBody']
);
