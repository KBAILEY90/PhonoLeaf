# CLAUDE.md

Guidance for working in this repository. **This file is intentionally lean —
current facts and gotchas only, no narrative.** Full history (why each
decision was made, what was tried and rejected, device-test reports, exact
verification steps) lives in `CLAUDE_HISTORY.md`, organized in the same
section order as this file. It is NOT auto-loaded — `grep`/`Read` it when you
need the reasoning behind something below, especially before "fixing"
anything in the Critical Facts or Gotchas sections.

## What this is

**PhonoLeaf** — a mobile-first PWA that reads your epubs aloud. Connects to
Google Drive (read-only) and/or a local device folder, lists epub files,
renders them with epub.js, and reads the text using either a native
on-device neural voice (Kokoro/Piper) or the browser's Web Speech engine as
fallback.

- Live: **https://phonoleaf.com/** (GitHub Pages, custom domain, HTTPS
  enforced). DNS at Cloudflare, DNS-only/grey-cloud. The old
  `kbailey90.github.io/PhonoLeaf/` origin 301s to it and is deliberately kept
  as an authorized OAuth origin/JS origin (old installs still work) — do not
  remove it.
- Repo: https://github.com/KBAILEY90/PhonoLeaf
- Status: **production-bound** — real users, not a personal project. Treat
  changes with multi-user/security/cost awareness.
- Brand: **PhonoLeaf**. Nothing may be named `koboaudio` anywhere in code —
  the only permitted appearance is the one-time migration block that deletes
  the old-named IndexedDB/localStorage keys.

## Tech stack & structure

- **Pure HTML/CSS/JS, no build step** for the web app. Nearly everything is
  one file: `index.html` (markup + styles + inline `<script>`).
- `sw.js` — service worker (offline app shell, network-first for HTML).
  Bump `CACHE` whenever the precached asset list changes.
- `manifest.json` — PWA manifest.
- `privacy.html` / `terms.html` (+ `-fr` variants) — standalone legal pages,
  staged into the native shell, linked from Settings.
- `home.html` (+ `-fr`) — public marketing/landing page. Web-only, not
  staged into the native app.
- **Native shell (Capacitor 8)**: wraps the same web app for the Play Store
  build. `package.json` (scripts only), `capacitor.config.json`
  (`com.phonoleaf.app`, webDir `www`), `scripts/stage-www.js` (copies
  index.html/manifest/sw/fonts/vendor into `www/` — gitignored, generated),
  `android/` (committed Capacitor Android project; build outputs gitignored).
  Loop: `npm run sync` (stage + copy into android) → `npm run open` (Android
  Studio) → Run ▶ on device.
  - **`scripts/stage-test.js`** + `npm run sync:test` — stages
    `index.green.html` (a redesign test page, see below) into `www/index.html`
    instead of the real one, so a native test build can run without touching
    the live app. `npm run sync` restores the real app afterward.
  - Installed plugins: `@capacitor/browser` + `@capacitor/app` (native
    auth), `@capacitor/filesystem` (local import, bug-report photos),
    `CapacitorHttp` (core, used for the OAuth token endpoint — no CORS
    headers from Google, so a WebView fetch would be blocked).
  - **Native TTS plugin**: `PhonoLeafTtsPlugin.kt` (registered in
    `MainActivity.java`) wraps sherpa-onnx's `OfflineTts` — multi-model
    (Kokoro + Piper), models are downloaded on-device (nothing bundled in
    the APK), auto-detects the model family from the folder's files
    (`voices.bin` → Kokoro). See "Voice engine" below.
  - **Secure storage plugin**: `SecureStoragePlugin.kt` — Keystore-backed
    `EncryptedSharedPreferences` `get`/`set`/`remove(key)`, used for the
    native OAuth refresh token (`pl_rtoken`).
  - **Local folder plugin**: `LocalFolderPlugin.kt` — SAF
    (`ACTION_OPEN_DOCUMENT_TREE`) folder picking, listing, and reading, for
    "connect a local device folder" (persists across relaunches via a
    persistable URI permission).
  - **Email composer plugin**: `EmailComposerPlugin.kt` — `ACTION_SEND`
    intent typed `message/rfc822` so only mail apps show in the picker (used
    by Feedback/Report-a-bug, with photo attachment via `FileProvider`).
  - **Background playback**: `PlaybackService.kt` — our own foreground
    service (`mediaPlayback` type) + a CPU wake lock, since audio is a JS
    `onended`-driven chain that Android would otherwise suspend when
    backgrounded. Includes lock-screen `MediaSessionCompat` controls
    (play/pause, page turn mapped to skip-prev/next, chapter jump as custom
    actions — 5-button ceiling on Android 13+).
  - **Voice-pack downloads**: `PackDownloadService.kt` — separate foreground
    service (`dataSync` type) + wake lock, so a multi-minute pack download
    survives the screen locking (same class of problem as playback).
- `fonts/` — self-hosted variable woff2 (Manrope UI, Literata reading).
- `.github/workflows/deploy.yml` — deploys to GitHub Pages on push to `main`.
- `vendor/` — **jszip 3.10.1 + epub.js 0.3.93, self-hosted** (security
  hardening — no CDN with no SRI). Google Identity Services (GIS) is the
  only remaining CDN script, allow-listed in the CSP.
- **Content-Security-Policy** (`<meta http-equiv>` in `index.html`) locks
  script/connect/img-src etc. to known hosts (Google auth/Drive, Open
  Library, jsdelivr+huggingface for the browser-WASM Kokoro fallback).
  `'unsafe-inline'` accepted (no build step) but blocks exfiltration via
  `connect-src`; `'wasm-unsafe-eval'` required for WASM Kokoro.

The inline script is organized into plain object "modules": `CONFIG`,
`State`, `Theme`, `I18n`, `Stats`, `Nav` (tab shell), `Home`, `Settings`,
`App` (auth), `Drive`, `LocalBooks`, `Library`, `Covers`, `BookCache`,
`Reader`, `TTS`, `VoicePacks`, `VoiceModal`, `ChapterModal`, `MyData`,
`Tour`, plus `esc()`/`toast()`/`showView()` helpers.

## Redesign test page — `index.green.html`

A standalone, unlinked file (not part of the live app, not precached) for
testing a deeper visual redesign ("Green Ink"/"Shelf" direction, sourced
from a Claude Design project) before committing to it. `index.html` is
untouched by it. Currently **Phase 1 only**: tab bar renamed (Now/Shelves/
Log/You — display labels only, internal `Nav`/`data-tab` identifiers
unchanged), sharp `2px`-corner visual system, sign-in feature-row copy, and
a dedicated type-to-confirm `EraseModal` for `MyData.deleteAll()`. Also
fixed here (a general, pre-existing gap, not redesign-specific): every
`<button>`/`<input>`/`<select>`/`<textarea>` needs an explicit
`appearance: none` reset, or Safari/WebView renders native rounded chrome
regardless of `border-radius`. **Not yet ported to the real `index.html`.**
See `CLAUDE_HISTORY.md` for the full Phase 1/2/3 scope split and what's
still outstanding (storage manager, in-book search, motion/gesture system,
full accessibility pass) before treating this as done.
`scratchpad`-style working files (`design_redesign.dc.html`,
`PhonoLeaf Redesign.dc.html`) are local reference copies of the Claude
Design source, not part of the shipped app.

## How to deploy

Edit `index.html` (or `sw.js`), commit, and **push to `main`**. GitHub
Actions redeploys to Pages in ~1–2 min. No staging branch.

- A second Claude session has also pushed to `main` in the past — always
  `git fetch` and check `git log HEAD..origin/main` before pushing, rebase
  rather than clobber.
- Bump `CACHE` in `sw.js` whenever the precached asset list changes, to
  force clients off the old shell.
- **After each push to `main`, update this file**: keep it to a 1–5 line
  current-state summary (what changed, any new gotcha), and put the
  reasoning/verification detail in `CLAUDE_HISTORY.md` under a new dated
  entry — do not let long narrative creep back into `CLAUDE.md` itself. The
  owner treats both files as living documentation; this one must stay short
  enough to be worth auto-loading every turn.

## How to verify changes

No test suite. Before pushing, syntax-check the inline script:

```bash
# Two <script> tags exist (early theme-init in <head> + the app script) —
# grab the LAST one.
node -e "const fs=require('fs');const h=fs.readFileSync('index.html','utf8');const p=h.split('<script>').pop();require('vm').compileFunction(p.split('</script>')[0]);console.log('JS OK');"
node --check sw.js
```

Full sign-in → Drive → reading flow can only be tested live (needs a real
Google login); verify by inspection + device testing.

## Critical facts — do NOT "fix" these

- **OAuth Client IDs are on the "PhonoLeaf" Cloud Console project** (project
  id `phonoleaf`). `CONFIG.CLIENT_ID` (web) =
  `88179965472-codmbgtm99mgik9qke2kucbvfkbug3ul.apps.googleusercontent.com`;
  `CONFIG.ANDROID_CLIENT_ID` (native) =
  `88179965472-cs9869nsk2b9i00v5ebo8sd9mrq9kmmn.apps.googleusercontent.com`,
  which must match `AndroidManifest.xml`'s reversed-scheme `oauth2redirect`
  intent-filter exactly — a mismatch breaks the native deep-link return
  silently. Do not change either without the owner explicitly asking.
- **epub.js must load from jsdelivr, not cdnjs** — the cdnjs path 404s.

## Conventions

- **Escape all externally-sourced strings** (filenames, error messages,
  voice names, chapter titles) with `esc()` before `innerHTML`. Prefer
  passing indices to inline handlers over interpolating raw values.
- Match the existing terse, dependency-free style. No frameworks, no build.
- **Clickable `<div>` rows need `role="button" tabindex="0"`** — a
  delegated `keydown` listener already makes Enter/Space call `.click()` on
  any `role="button"` element, so a new row gets keyboard support for free.
- **`STRINGS`/`I18n`** — `en`/`fr` dictionaries, `I18n.t(key, vars)` with
  `{name}`-style placeholders, `data-i18n`/`data-i18n-title`/
  `data-i18n-placeholder` attributes for static markup, direct `I18n.t()`
  calls for JS-templated content. `I18n.setLang()` re-applies to the whole
  body and re-renders Home/Library/Stats/Settings.

## Behavior notes / gotchas (current facts only — see CLAUDE_HISTORY.md for why)

**Auth**
- Web: `drive.readonly` scope, ~1h tokens, no refresh token (implicit
  flow), persisted in `localStorage.pl_auth`, `Drive._fetch` re-auths once
  on 401. **Do not attempt `drive.file`** — it cannot list a picked
  folder's contents (per-file grants only), so "connect a folder, books
  auto-sync" is impossible under it. Already tried and reverted once.
- Native: system-browser PKCE flow (Custom Tabs) against the Android OAuth
  client, refresh token in `SecureStoragePlugin` (Keystore), token
  exchange via `CapacitorHttp` (Google's token endpoint has no CORS
  headers). `App._enterApp()` is the shared post-auth path for all flows.
- CASA AL1 security assessment is required annually for as long as the app
  holds `drive.readonly` (Google-mandated, not optional) — see
  `VERIFICATION.md` for status.

**Theming**
- CSS-variable driven (Daylight light / Midnight dark). `:root` = light
  defaults, `@media (prefers-color-scheme: dark)` = dark, `[data-theme]`
  overrides win over both (forced mode). Always use the tokens
  (`--bg`/`--surface`/`--accent`/`--text`/`--line`/etc.) — never hardcode a
  hex that assumes one mode.

**Reader / page turning**
- **Page turns are instant, not animated** — epub.js's single-iframe
  paginated architecture makes a real slide animation unreliable (flash,
  blank overlays, scroll-position races). Multiple animation approaches
  were tried and abandoned; do not re-attempt without reading history
  first. Finger-drag still gives live visual feedback during the gesture.
- `TTS.loadPageText()` extracts text from nodes whose on-screen box is
  inside the viewer — **never** read `body.innerText` (grabs the whole
  off-screen chapter, loops forever).
- Chunking is **block-aware** (grouped by nearest block ancestor) so a
  heading doesn't run on into the next sentence.
- `_split()`'s regex needs the `|$` alternative — the last sentence on a
  page has no terminal punctuation (cut by the column break).
- Blank pages auto-skip **forward only**, capped at 20 consecutive skips;
  backward into a blank page stops and waits. Only genuinely text-empty
  pages skip — a page with real DOM text that just failed to extract stops
  in place instead of skipping ahead (prevents double-advancing).
- Async callbacks are **generation-guarded** (`TTS._gen`) so a stale
  `onend`/timer from a page just left can't fire a second advance.
- `TTS._retryN` re-speaks a chunk that "ends" faster than it could have
  been spoken (Android sometimes silently eats a `speak()` call right
  after an interrupting `cancel()`).
- Forward-overshoot corrector (`Reader.nextPage`/`_onRelocated`) undoes a
  rare epub.js rounding bug that skips a whole section on phones with
  fractional viewport widths — measures raw container scroll state, not
  `loc.displayed.page` (also unreliable on the affected page).
- `Diag`/`pl_diag` — a 30-entry ring buffer of page-turn diagnostics.
  Useful evidence if a page-turn bug resurfaces; no Settings UI for it.
- Reading auto-starts via `Reader._onRelocated` (after the page **settles**
  at the restored position), not a fixed timer from `open()`.
- Progress (`{cfi, pct}`) saved per page turn to `pl_prog`, restored on
  open, also snapshotted on `visibilitychange`/`pagehide`/`close()`.
- Reader top bar and `ChapterModal` share one `flattenToc()` (TOC subitems
  inline) so their chapter names can't disagree; `Scrub`'s scrubber shares
  the same `chapterLabelFor()`.
- `ChapterModal`/TOC jumps must resolve hrefs via `_resolveHref()` — raw
  TOC hrefs can be relative/fragment-bearing and won't match epub.js's
  spine lookup directly.

**Voice engine**
- Two native models: **Piper** (baseline, always offered) and **Kokoro**
  (higher quality, offered only on devices that pass a synthetic CPU
  benchmark — `_KOKORO_MIN_GFLOPS`, calibrated off one real device; see
  `pl_kokoro_gate`). Terminology: **Built-in** (device OS voice) / **Standard**
  (Piper) / **Upgraded** (Kokoro) — used consistently in UI copy, EN+FR.
- All packs are downloaded on-device (`VOICE_PACKS`), nothing bundled in
  the APK. `MODEL_VERSIONS` is a **per-model** version tag used to name the
  `.ready-$VERSION` marker file — **bump a model's tag whenever its
  underlying file changes, or a device with the old file installed will
  never re-download** (this exact bug happened once with the French/Spanish
  packs).
- Downloads run on their own single-thread executor (never the TTS
  synthesis executor), at `THREAD_PRIORITY_BACKGROUND`, with per-model
  cancellation epochs — never share state across models or across the
  synthesis executor.
- `_synthNative` always resolves the voice sid/model from
  `TTS._modelReady()`'s reported model type before synthesizing — **never
  guess/assume the model family**, sids are model-specific and a wrong
  guess makes the first chunk speak in the wrong voice.
- `pl_voice_native` is a single persisted choice across the union of
  Piper+Kokoro voices (`TTS._allNativeVoices()`) — not split by engine.

**Background playback / lock screen**
- `PlaybackService.kt` must call `context.startService()`, **not**
  `startForegroundService()` — the latter arms a ~5s watchdog that can
  crash the app if `startForeground()` doesn't win the race (hit once,
  fixed). `PackDownloadService.kt` follows the same rule.
- Background reading (screen off) reads book **text directly from the
  spine** (`_bgMode`/`_bgAdvance`), decoupled from the visual
  rendition — epub.js's render loop is frozen with the screen off, so
  visual page-turning can't work backgrounded. **Never call
  `section.load()` on the currently-rendered section** — corrupts epub.js's
  document reference for later `display()` calls.
- `_bgSaveProgress()` runs after every background chunk; `_persistPosition()`
  must defer to it (via the `_bgSection` guard) or it clobbers a good
  background position with a stale visible-reader one.
- Lock-screen media session: 5-button hard ceiling on Android 13+
  (play/pause + 2 skip + 2 custom actions) — page turn maps to skip-prev/
  next, chapter jump to the two custom actions.

**App shell / navigation**
- 4-tab nav (`Nav`): Home/Library/Stats/Settings (internal identifiers —
  the redesign test page only renames the *displayed* labels, see above).
  `Nav.go(tab)` swaps the view, `showView()` toggles `.view` classes.
- Back/edge-swipe uses real `history.pushState` per tab + a buffered
  double-back-to-exit at the base level (`_exitArmed`/`#exit-hint`).
  Reader's back button calls `Reader.back()`, not `history.back()` — a
  click-driven `history.back()` doesn't reliably fire `popstate` in the
  native WebView.
- Mini-player: `Reader.open(index, 'mini')` keeps the reader laid out but
  visually hidden (`position:fixed; inset:0; z-index:-1`) so TTS keeps
  working while a tab view is shown on top — **never `display:none` the
  reader while it should keep playing**, it needs a laid-out rendition.

**Data / storage**
- `BookCache` (IndexedDB `phonoleaf-offline`, separate from `CoverCache`'s
  `phonoleaf` DB) holds offline/local-import book bytes; `pl_offline_books`
  is a synchronous localStorage index of what's cached.
- `MyData.deleteAll()` signs out **before** wiping storage (needs the still
  -valid token to revoke the Google grant), and must sweep every store that
  can hold user data: `pl_*` keys, `CoverCache`'s IndexedDB, `BookCache`
  (native filesDir + web IndexedDB), every `VoicePacks.ALL_PACK_MODELS`
  native pack, and `LocalFolderHandle`'s web IndexedDB handle. Missing any
  one of these has caused real bugs before (stale voice packs breaking
  onboarding, a local-only folder connection surviving "erase everything").
- Local device folder connect (`LocalBooks`) uses Capacitor's built-in file
  chooser (no custom plugin needed for single-file import) plus
  `LocalFolderPlugin.kt`/`showDirectoryPicker()` (web, Chromium-only) for a
  persistent folder connection with manual refresh (no background sync).

**Accessibility**
- Pinch-to-zoom must stay enabled app-wide (no `user-scalable=no`); the
  reader's double-tap-to-play gesture is isolated from browser
  double-tap-zoom via `touch-action: manipulation` on `#reader-touch`
  specifically, not a global zoom disable.
- Blanket `@media (prefers-reduced-motion: reduce)` override clamps all
  animation/transition durations near-zero — safe because nothing in the
  app gates functionality on `transitionend`/`animationend`.

## Productization status (current — see CLAUDE_HISTORY.md for the full trail)

- **OAuth verification**: approved for `drive.readonly` (2026-08-05).
  **CASA AL1 assessment required annually**, deadline **Jan 2, 2027**,
  package with Eydle, paid, **parked** (not yet submitted) until the
  payments backend exists so only one assessment is needed. See
  `VERIFICATION.md`.
- **Pricing**: Monthly $5.99 / Annual $49.99 / 7-day trial / capped
  Founding-Member Lifetime $129. Not yet built — see `BUSINESS.md`,
  `PAYMENTS_SPEC.md`. Entitlement Worker (`worker/`) exists but isn't
  called from the app yet (would paywall current users with no way to pay).
- **iOS**: planning only, no engineering started. Blocked on a replacement
  MacBook (prior purchase fell through). Apple Developer enrollment is the
  next actionable step once hardware exists.
- **Legal**: ToS/Privacy drafted, pricing/lifetime clauses added, **lawyer
  review requested 2026-08-10, still awaiting response** — treat as not
  lawyer-reviewed until confirmed otherwise. Jurisdiction: Québec, Canada.
- **Business registration (REQ, Québec)**: with the lawyer, blocks the bank
  account / GST-QST registration / Stripe account, in that order.
- See `TODO.md` for the actively-maintained task list (this section is a
  snapshot, `TODO.md` is the live source for "what's next").
