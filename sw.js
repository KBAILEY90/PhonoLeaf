// KoboAudio service worker — makes the app shell installable & offline-capable.
// Note: this caches the UI and its libraries only. Book bytes come from Google
// Drive at runtime and are intentionally NOT cached (they can be very large and
// require a live auth token).

const CACHE = 'koboaudio-v1';
const SHELL = [
  './',
  './index.html',
  './manifest.json',
  'https://cdnjs.cloudflare.com/ajax/libs/jszip/3.10.1/jszip.min.js',
  'https://cdnjs.cloudflare.com/ajax/libs/epub.js/0.3.93/epub.min.js',
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

  // Static assets / CDN libraries: cache-first, then network (and cache it).
  e.respondWith((async () => {
    const cached = await caches.match(req);
    if (cached) return cached;
    const res = await fetch(req);
    if (res.ok && (url.origin === self.location.origin || url.hostname.includes('cdnjs.cloudflare.com'))) {
      const c = await caches.open(CACHE);
      c.put(req, res.clone());
    }
    return res;
  })());
});
