// PhonoLeaf service worker — makes the app shell installable & offline-capable.
// Note: this caches the UI and its libraries only. Book bytes come from Google
// Drive at runtime and are intentionally NOT cached (they can be very large and
// require a live auth token).

const CACHE = 'phonoleaf-v13';
const SHELL = [
  './',
  './index.html',
  './manifest.json',
  './fonts/manrope.woff2',
  './fonts/literata.woff2',
  './vendor/jszip.min.js',
  './vendor/epub.min.js',
];

self.addEventListener('install', e => {
  e.waitUntil((async () => {
    const c = await caches.open(CACHE);
    // allSettled: a single CDN hiccup must not abort the whole install.
    await Promise.allSettled(SHELL.map(u => c.add(u)));
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', e => {
  e.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', e => {
  const req = e.request;
  const url = new URL(req.url);

  // Never intercept Google auth (accounts.google.com) or the Drive API
  // (www.googleapis.com) — those must always hit the network with a live token.
  if (url.hostname.includes('google.com') || url.hostname.includes('googleapis.com')) return;
  // Kokoro (Gold tier) model downloads from Hugging Face are huge (~90 MB) and
  // transformers.js manages its own Cache Storage — stay out of the way.
  if (url.hostname.includes('huggingface.co') || url.hostname.includes('hf.co') || url.hostname.includes('cdn-lfs')) return;
  if (req.method !== 'GET') return;

  // HTML navigations: network-first so new deploys show up immediately,
  // falling back to the cached shell when offline.
  if (req.mode === 'navigate') {
    e.respondWith((async () => {
      try {
        const res = await fetch(req);
        const c = await caches.open(CACHE);
        c.put('./index.html', res.clone());
        return res;
      } catch (_) {
        return (await caches.match('./index.html')) || (await caches.match('./'));
      }
    })());
    return;
  }

  // Static assets: cache-first, then network. Same-origin only — jszip/epub.js
  // are vendored locally now (no more CDN scripts to allow-list here), and we
  // don't want to indefinitely cache other cross-origin fetches (e.g. the
  // Kokoro worker's unpinned jsdelivr import) which could go stale silently.
  e.respondWith((async () => {
    const cached = await caches.match(req);
    if (cached) return cached;
    const res = await fetch(req);
    if (res.ok && url.origin === self.location.origin) {
      const c = await caches.open(CACHE);
      c.put(req, res.clone());
    }
    return res;
  })());
});