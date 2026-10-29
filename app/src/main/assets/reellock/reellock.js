const inDM = () => location.pathname.startsWith('/direct/t/');
const isToggleActive = () => document.documentElement.getAttribute('data-fg-block-dm-reels') !== 'false';
const MIN = 0.45;
let on = false;
let reel = false;
let armedAt = 0;
let consumed = 0;
const locked = new Set();

const css = document.createElement('style');
css.textContent = `
  [data-fg-lock]{overflow:hidden !important;scroll-snap-type:none !important;}
`;
document.documentElement.appendChild(css);

const beat = (active) => {
  const enabled = isToggleActive();
  return browser.runtime.sendMessage({ t: 'fg-reel', active: active && enabled, enabled })
    .then((r) => { if (r && r.armedAt) armedAt = r.armedAt; })
    .catch(() => {});
};

const big = (el) => {
  const r = el.getBoundingClientRect();
  return r.width >= innerWidth * MIN && r.height >= innerHeight * MIN &&
         r.bottom > 0 && r.top < innerHeight;
};

function lockRoot(v) {
  for (let p = v.parentElement; p && p !== document.documentElement; p = p.parentElement) {
    if (p.hasAttribute('data-fg-lock')) return;
    const cs = getComputedStyle(p);
    if (/(auto|scroll)/.test(cs.overflowY) && p.scrollHeight > p.clientHeight + 4) {
      p.setAttribute('data-fg-lock', '');
      locked.add(p);
      return;
    }
  }
}

function unlockAll() {
  locked.forEach(e => e.removeAttribute('data-fg-lock'));
  locked.clear();
}

function endReel() {
  reel = false;
  consumed = armedAt;
  unlockAll();
}

function scan() {
  const now = inDM();
  if (now !== on) {
    on = now;
    if (!on) { endReel(); beat(false); }
  }
  if (!on || !isToggleActive()) {
    if (reel) endReel();
    return;
  }

  const vids = [...document.querySelectorAll('video')].filter(big);

  if (reel && !vids.length) { endReel(); return; }

  if (!reel) {
    // Start only if a reel feed request was cancelled in the last 5 seconds (avoids trapping direct video messages).
    const fresh = armedAt > consumed && Date.now() - armedAt < 5000;
    if (!(fresh && vids.length)) return;
    reel = true;
  }

  vids.forEach(lockRoot);
}

// Block swipe and wheel gestures while a DM reel is open, allowing independent sub-scrollers like comments.
function insideScroller(el) {
  for (let p = el; p && p !== document.body && p !== document.documentElement; p = p.parentElement) {
    if (p.nodeType !== 1) continue;
    const cs = getComputedStyle(p);
    if (/(auto|scroll)/.test(cs.overflowY) && p.scrollHeight > p.clientHeight + 4) return true;
  }
  return false;
}
const stop = (e) => {
  if (!reel || !isToggleActive()) return;
  if (insideScroller(e.target)) return;
  e.preventDefault();
};
addEventListener('touchmove', stop, { capture: true, passive: false });
addEventListener('wheel', stop, { capture: true, passive: false });

let queued = false;
const schedule = () => {
  if (queued) return;
  queued = true;
  requestAnimationFrame(() => { queued = false; scan(); });
};

new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
document.addEventListener('play', schedule, true);
document.addEventListener('loadeddata', schedule, true);
setInterval(scan, 200);
setInterval(() => { if (inDM()) beat(true); }, 200);
