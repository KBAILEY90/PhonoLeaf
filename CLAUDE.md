# CLAUDE.md

Guidance for working in this repository. **This file is intentionally lean —
current facts and gotchas only, no narrative.** Full history (why each
decision was made, what was tried and rejected, device-test reports, exact
verification steps) lives in `CLAUDE_HISTORY.md`, organized in the same
section order as this file. It is NOT auto-loaded — `grep`/`Read` it when you
need the reasoning behind something below, especially before "fixing"
anything in the Critical Facts or Gotchas sections.

## START HERE: start every session on an up-to-date `main`

Run this first, every session, before reading anything else:

```
git fetch origin
git checkout main && git pull --ff-only
git log --oneline -5
```

Opening a session on a stale branch means every file you read is out of
date, and nothing about reading files reveals it. It happened on
2026-08-31 and cost a status report written against the previous day's
tree. Ten seconds of git rules it out.

**This is a solo project. Work directly on `main` and push when it is
ready.** No feature branch, no PR, no claiming tasks in `TODO.md` first,
no WIP branch for visibility. Nobody else reviews this, so those steps
only cost time. (They existed because two Claude sessions once ran in
parallel and collided; if that ever happens again, re-read this section
in `CLAUDE_HISTORY.md`, which keeps the longer version.)

**The one rule that still matters: test before you push, because for the
website a push IS the deploy.** There is no staging environment. Pushing
`index.html`, `home.html`, `sw.js`, a legal page or a comparison page to
`main` puts it on phonoleaf.com in about two minutes.

- **Website files:** verify locally first (open the file, or run the
  syntax check under "How to verify changes"), then commit and push.
- **Native app files** (`index.green.html`, `android/`): pushing deploys
  nothing at all. The app ships locally via `npm run sync` and Android
  Studio, so commit freely and test on device before building a release.
- **Docs, `worker/`:** a push deploys nothing. `worker/` goes live only
  when you run `wrangler deploy` yourself.

## WHO YOU ARE TALKING TO: the owner is not a developer

**The owner is not a software engineer and does not read code.** They decide
everything about this product, but they do not build it. Jargon they have to
look up is a blocker, not a shortcut, and having to keep asking for plainer
explanations is the specific thing that frustrates them (said directly on
2026-09-01).

- **Say what something does and why it matters to the product before naming
  it.** If a term is genuinely needed, define it once, in plain words.
- **Avoid unexplained terms**: race condition, CORS, CSP, R8, path traversal,
  binding, idempotent, fail open, supply chain. Describe the effect on the
  user or the business instead.
- **Step-by-step means literal steps**: where to click, what to type, what a
  good result looks like, what to do when it does not. Naming a command is
  not an instruction.
- **Lead with consequences.** "Lose this file and you can never update the app
  on the Play Store again" beats any explanation of signing keys.
- This governs chat replies, reports and documents the owner reads. It does
  NOT govern code comments, commit messages or this file's own technical
  sections, which exist for whoever is writing code and should stay precise.

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
- Status: **production-bound, but not yet shipped or earning.** Treat changes
  with multi-user/security/cost awareness: this is built to be a real product,
  not a personal toy. But be accurate about where it actually is (checked
  2026-09-02) — **no Play Store release exists** (`versionCode 1`, no Play
  Console account; a keystore and a working `signingConfig` DO now exist and
  a signed 53 MB build has been produced), OAuth is still in
  **Testing mode under its 100-user cap** because CASA is parked, and the
  entitlement Worker is deployed but not called from the app, so **nobody can
  pay yet**. Do not plan as though there is an installed base to protect or
  revenue to lose. See `SWOT.md`.
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
  the app + manifest/sw/fonts/vendor into `www/` — gitignored, generated),
  `android/` (committed Capacitor Android project; build outputs gitignored).
  Loop: `npm run sync` (stage + copy into android) → `npm run open` (Android
  Studio) → Run ▶ on device.
  - **`npm run stage` ALONE DOES NOT UPDATE THE ANDROID APP.** `stage` writes
    `www/` and stops; only `sync` runs `npx cap sync android`, which copies
    `www/` into `android/app/src/main/assets/public/`. Building and installing
    after a stage-only run silently ships the PREVIOUS web build, with no error
    and no warning — the APK is fresh, its web content is not.
    This cost two rounds of device testing on 2026-09-01: two follow-along
    fixes were reported as "still broken" because they were never actually in
    the installed app, which then sent the investigation looking for a third
    cause that did not exist. **Always `npm run sync` before building.**
  - **NATIVE AND WEB SHIP DIFFERENT INDEX FILES right now (2026-08-28).**
    `stage-www.js`'s `APP_SOURCE` is **`index.green.html`** (the Shelf/
    Green-Ink redesign), so `npm run sync` builds the Android app from the
    redesign. The **website** (GitHub Pages) still serves the repo-root
    **`index.html`**, the older design, which this script no longer touches.
    Reason: pushing to `main` auto-deploys Pages, so a changed `index.html`
    cannot reach phones without the website changing at the same moment —
    but the native build never comes from a push at all (it is local:
    `npm run sync` + Android Studio). Pointing only the staging script at the
    redesign lets the app ship while the website waits for the desktop pass.
    **CONVERGENCE CANCELLED, owner decision 2026-08-31.** The two designs
    stay separate permanently. The website is no longer a product surface:
    it is SEO plus a launcher for the phone apps, and it keeps the feature
    set it had on 2026-08-31 without further parity work. Its playback is
    REMOVED once both app stores carry the app (owner decision 2026-08-31),
    so put no further effort into the web app at all. Do NOT promote
    the redesign into `index.html`, and do NOT spend a session converging
    the fork. See `CLAUDE_HISTORY.md` 2026-08-31 and `BUSINESS.md` for the
    reasoning and the conditions that would reopen it.
  - **`scripts/stage-test.js`** + `npm run sync:test` — now does the same
    thing `stage-www.js` does (it predates the split, when the redesign was
    only a test page). Kept only so the documented `npm run sync:test` keeps
    working. Convergence is cancelled (above), so it simply stays.
  - Installed plugins: `@capacitor/browser` + `@capacitor/app` (native
    auth), `@capacitor/filesystem` (local import, bug-report photos),
    `CapacitorHttp` (core, used for the OAuth token endpoint — no CORS
    headers from Google, so a WebView fetch would be blocked).
  - **Native TTS, split across a PROCESS BOUNDARY (2026-09-01).** The engine
    lives in `TtsService.kt`, running in its own `:tts` process, reached only
    through the `ITtsService` AIDL interface. `PhonoLeafTtsPlugin.kt` no
    longer imports or links sherpa-onnx at all.
    **This is a licence boundary, not an optimisation.** espeak-ng is GPL-3.0
    and is statically linked into the sherpa AAR, which is incompatible with
    a closed-source app. Keeping it behind a generic text-in/audio-out
    interface, in a separate process, with `TtsService.kt` published as
    source, is what makes the two arguably separate works. **Do not move
    sherpa calls back into the plugin, and keep the interface generic:**
    strings and primitives in, raw audio to a caller-chosen path out. Adding
    a rich or app-aware protocol would undermine the whole point.
    **RESTRUCTURED 2026-09-01 on legal advice, and now test-enforced.** The two
    bridge files live in **`android/tts-bridge/`**, outside the app's source
    tree, under their own package **`com.phonoleaf.ttsbridge`**, with the full
    GPL-3.0 text in `android/tts-bridge/LICENSE` and a licence header in each
    file. The repo root now has a proprietary `LICENSE` that carves out that one
    directory. Counsel's point: publishing source is not licensing it, and GPL
    files sitting inside a proprietary directory weaken the separate-works
    argument. `npm test` fails if the files move back, lose their headers, or
    change package. Do NOT "tidy" any of that away: nothing breaks at runtime,
    which is precisely why it is guarded.
    Measured on device: warm bind 0 ms, 0.29 realtime, audio identical to the
    old in-process path. Models are still downloaded, nothing in the APK.
    See `TODO.md`'s voice model licence section.
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
  - **Store review plugin**: `StoreReviewPlugin.kt` — Google Play's In-App
    Review API, fired only after a genuine finish (`Reader.close(true)`,
    i.e. reading/listening to the true end), never from a manual "Mark as
    finished" and never on launch. Locally rate-limited to once per 60 days
    (`pl_review_asked_at`) on top of Play's own opaque limiting. Android
    only — no `ios/` platform exists yet, and the web build has no
    reviewable store listing to point at.
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

## Redesign — `index.green.html` (SHIPS AS THE NATIVE ANDROID APP)

The "Green Ink"/"Shelf" redesign, sourced from a Claude Design project.
**No longer a mere test page:** as of 2026-08-28 `stage-www.js` builds the
Android app from THIS file (see the native/web split above), so it is what
every Android build is made from, and what Play Store users **will** get once
a release actually ships. To be exact (corrected 2026-08-30): no Play release
exists yet, so today its only audience is the owner's own device via
`npm run sync`. The **website** still serves the repo-root
`index.html` (older design) and is untouched by it. **This fork is
deliberate and permanent, not a temporary state pending a merge** — owner
decision 2026-08-28 (see the KNOWN GAP resolution below), overriding this
section's own earlier "converge later" framing. Not precached on the web
side, not linked from the live site. Originally **Phase 1 only**: tab bar renamed (Now/Shelves/
Log/You — display labels only, internal `Nav`/`data-tab` identifiers
unchanged), sharp `2px`-corner visual system, sign-in feature-row copy, and
a dedicated type-to-confirm `EraseModal` for `MyData.deleteAll()`. Also
fixed here (a general, pre-existing gap, not redesign-specific): every
`<button>`/`<input>`/`<select>`/`<textarea>` needs an explicit
`appearance: none` reset, or Safari/WebView renders native rounded chrome
regardless of `border-radius`. **Still not ported into the real
`index.html`** (the website's file) — only the native build uses it.
See `CLAUDE_HISTORY.md` for the full Phase 1/2/3 scope split. Phase 2/3
(storage manager, in-book search, motion/gesture system, full
accessibility pass) is done as of 2026-08-29, **device-tested and passed
2026-08-31**.
`scratchpad`-style working files (`PhonoLeaf Redesign.dc.html`) are a local
reference copy of the Claude Design source, not part of the shipped app.

Roughly 35 dated entries of build-and-fix narrative for this redesign were
moved verbatim into `CLAUDE_HISTORY.md` on 2026-08-31 (see its "Redesign
narrative moved out of CLAUDE.md" section). They made this file 991 lines /
64KB, auto-loaded on every turn of every session, against this file’s own
rule of a 1-5 line current-state summary per change. **Nothing was deleted;**
read that section for the reasoning behind any specific redesign decision,
especially before changing forest, Book Detail, mini player, sleep timer,
Tour spotlight, or Settings row layout, all of which took several passes to
get right and record exactly why.

**Current state:** Phase 1, 2 and 3 are all done. Phase 2/3 (storage manager,
in-book search, motion/gesture token system, full accessibility pass) landed
2026-08-29 and is **device-tested and passed 2026-08-31**.

**Gotchas from that work that still bite, kept here deliberately:**

- Every `<button>`/`<input>`/`<select>`/`<textarea>` needs an explicit
  `appearance: none` reset, or Safari/WebView draws native rounded chrome
  regardless of `border-radius`. General, not redesign-specific.
- **`.sr-` in this file means SETTINGS ROW.** Do not reuse the prefix for
  anything else. Reusing `.sr-info` (Settings’ circled "i" button, which is
  `border-radius: 50%` plus a border and defined later) once rendered a whole
  section as a lens through its own title on device.
- Adjacent block siblings’ margins **collapse** to the larger one rather than
  summing, so spacing a page title away from `.app-header` needs
  `padding-top`, not `margin-top`. A margin-based fix here silently does
  nothing.
- To check a divider looks centred, measure hairline-to-rendered-text on each
  side, never element-box to element-box.
- Desktop is the same layout capped to a centred 480px column (`--app-max`),
  gated `(min-width: 700px) and (min-height: 600px)`. **The min-height is
  load-bearing:** a phone in landscape is wide (~930px) but short and must
  keep the full-bleed layout. `.tab-bar`/`.mini-player` are `position: fixed`
  with `left:0;right:0`, so they need re-anchoring to centre, not just a
  `max-width`.
- `role="button" tabindex="0"` is only safe on HTML elements. An `SVGElement`
  has no `.click()` in this WebView, so the delegated Enter handler throws.
## How to deploy

**Two separate targets since the 2026-08-28 native/web split — a push ships
only ONE of them.**

- **Website** (phonoleaf.com): edit the repo-root `index.html` (or `sw.js`),
  commit, **push to `main`**. GitHub Actions redeploys Pages in ~1–2 min.
  No staging branch. A push does NOT update the Android app.
- **Android app**: edit `index.green.html`, then `npm run sync` →
  `npm run open` → build/sign in Android Studio → upload to Play Console.
  Entirely local; **no push is involved, and pushing does not ship it**.
  Conversely a change to `index.green.html` alone never reaches the website.

- **Merged to `main` 2026-08-28** (owner explicitly asked for this specific
  step, per the KNOWN GAP resolution below): this whole native/web split,
  plus every redesign item logged below, landed on `main` via
  `redesign/converge-to-main` (a clean fast-forward — `main` had zero
  commits since the branches' shared base, so there was nothing to
  reconcile at the git level). The desktop-column CSS fix in the real
  `index.html` is now live on phonoleaf.com; `index.green.html` (the
  native app source) is not linked from the website and doesn't affect it.

- **KNOWN GAP, RESOLVED 2026-08-28 — two divergent branches, reconciled by
  owner decision, not by merging.** `claude/docs-code-review-tam2pr` had
  independently built its own Phase 2/3 of the redesign on top of Phase 1
  (the `--motion-fast/base/slow/ease` token system, in-book full-text
  search, the storage manager "On this phone" screen, and a localized
  accessibility pass) — real root cause, confirmed from this file's own
  history below, not the "best guess" originally logged here: this file
  went missing from the working tree between sessions and was correctly
  recovered from `www/index.html` (a byte-identical build artifact); the
  actual problem was simply two sessions extending the same file for days
  with zero visibility into each other, nothing to do with a bad recovery.
  **Owner's call**: `redesign/native-android-ship` (this branch — the
  forest, mini player, Book Detail fixes, the native/web build split) is
  the permanent, active, productionized source for the native app. The
  other branch's version is archived, not merged in — preserved at branch
  `archive/hero-redesign-2026-08-28-branch` (same content as its closed
  PR #4) — see `CLAUDE_HISTORY.md` for the full writeup. **Phase 2/3's four
  features were ported into this file 2026-08-29** (re-implemented by hand
  against the forest markup, not a git merge) — see `CLAUDE_HISTORY.md`'s
  2026-08-29 entry. Device-tested and passed 2026-08-31.
  **Safeguard, so this doesn't happen again**: before starting substantial
  work on `index.green.html`, run `git log --all --oneline --
  index.green.html` and `git branch -r` — a 10-second check for other
  recent activity on this file. That check alone would have caught this
  the first time.
  **Second safeguard, added 2026-08-30 after the archival orphaned real
  work**: that branch also carried the entire payments/D1 workstream, which
  the archival silently disposed of along with the redesign — including
  a migration already deployed to live Cloudflare infrastructure, leaving
  `main`'s `worker/` describing storage that no longer existed (recovered
  in PR #13, see `CLAUDE_HISTORY.md` 2026-08-30). **Before archiving or
  abandoning any branch, run `git diff <base> <branch> --stat` and rescue
  everything outside the scope of the decision being made** — a branch is
  rarely only about the thing you're deciding on.

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
- **Release signing credentials come from ENVIRONMENT VARIABLES
  (`PHONOLEAF_STORE_FILE`, `PHONOLEAF_STORE_PASSWORD`, `PHONOLEAF_KEY_ALIAS`,
  `PHONOLEAF_KEY_PASSWORD`), never from a file. Do not create
  `android/keystore.properties`, and do not add a fallback that reads one.**
  This is a security boundary, not a preference. That file leaked the signing
  password into a conversation transcript TWICE (2026-09-01, 2026-09-02) —
  neither time by anyone reading it: tooling watches files it has touched and
  echoes their contents on every change, so the file's existence was the
  exposure. Rotating the password did not help; the second leak was the
  rotated value. `android/app/build.gradle` therefore throws a
  `GradleException` if the file reappears, and `test/secrets.test.mjs` fails
  if it exists, if the build reads a properties file, if that guard is
  removed, or if any tracked file holds a literal password.
  **Never write a secret into a file to hand it to the owner** — give them the
  command and let them type the value. Creating the file is what starts the
  watching. Never open any `*.jks`, `*.keystore`, or `worker/.dev.vars`.
  Only the password ever leaked, never the `.jks` itself, so rotation is
  `keytool -storepasswd` on the existing keystore — a NEW key would change the
  certificate SHA-1 already recorded in `TODO.md`. (`-keypasswd` does not apply:
  the keystore is PKCS12, which has no separate key password.)
  **Never run a command that prints the whole environment** — `env`, `printenv`,
  `set`, `Get-ChildItem Env:`, `[Environment]::GetEnvironmentVariables()` — and
  never `System.getenv()` with no argument. The signing password now lives in
  `PHONOLEAF_STORE_PASSWORD`, and a Claude Code process started AFTER the owner
  set it inherits it, so an environment dump would print it straight into the
  transcript. Read named variables only, and test presence with
  `[ -n "$VAR" ]` rather than echoing. This is the one residual path left after
  the file was removed; nothing in the repo enumerates the environment, verified
  2026-09-02.

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
- **Synthesis runs in a SEPARATE PROCESS (`:tts`) and is reached only over
  AIDL.** Not an optimisation: espeak-ng is GPL-3.0 and statically linked
  into the sherpa AAR, so isolating it is what lets this app stay closed
  source. `PhonoLeafTtsPlugin.kt` must never import `com.k2fsa.sherpa.onnx`
  again, and the interface must stay generic (strings and primitives in, raw
  audio to a caller-chosen path out). Read
  `android/tts-bridge/ENGINE_NOTICE.md` before
  touching any of it.
- **Error strings crossing that boundary are API, not implementation.** The
  web layer switches voices when a synthesis error starts with
  `PACK_NOT_DOWNLOADED:`, and treats anything else as an engine failure; two
  in a row disable the neural voice for the session, and native has no Web
  Speech to fall back on. Renaming that string during the cut-over is exactly
  how deleting a pack silently killed playback (2026-09-01). Check consumers
  before changing any error text.
  **`npm test` now enforces this** (2026-09-01): `test/invariants.test.mjs`
  pins the whole chain — the service's `err:notdownloaded:` prefix, the
  plugin's translation in **both** places (synthesize *and* prepare), the web
  layer's match, the `/cancel/i` contract, and the ban on `com.k2fsa` outside
  `TtsService.kt`. If you change an error string, the suite tells you.
- Two native models: **Piper** (baseline, always offered) and **Kokoro**
  (higher quality, gated on a CPU benchmark, `_KOKORO_MIN_GFLOPS`, see
  `pl_kokoro_gate`). **The gate is CORRECT and was confirmed by ear
  2026-08-31:** real Kokoro on a Pixel 7 read one or two sentences then
  stalled ~10s, repeatedly. Do not loosen it on the strength of a one-shot
  benchmark; only sustained reading counts. Terminology: **Built-in** (device
  OS voice) / **Standard**
  (Piper) / **Upgraded** (Kokoro) — used consistently in UI copy, EN+FR.
- All packs are downloaded on-device (`VOICE_PACKS`), nothing bundled in
  the APK. `MODEL_VERSIONS` is a **per-model** version tag used to name the
  `.ready-$VERSION` marker file — **bump a model's tag whenever its
  underlying file changes, or a device with the old file installed will
  never re-download** (this exact bug happened once with the French/Spanish
  packs).
- **The Spanish pack was REMOVED 2026-08-31 on a licence finding**, not a
  technical one: its model card claims CC BY 3.0 but says it was fine-tuned
  from lessac, which is Blizzard-licensed and excludes commercial voice
  synthesis. Do not re-add it without a replacement whose licence is clean,
  and check the BASE model, not just the card.
- **The engine PROCESS must be restarted whenever a pack's files change on
  disk** (`resetEngineProcess()` in the plugin, `ITtsService.shutdown()`).
  espeak initialises its data directory ONCE PER PROCESS, from whichever pack
  loads first, and never revisits it. Installing or deleting a pack deletes
  that directory, so the engine is left pointing at something gone and every
  sentence collapses to a fraction of a second of noise — silently, with no
  error anywhere. Freeing the engine object does NOT help: the state belongs
  to the process. Owner-reported 2026-09-01; `npm test` guards it.
  **Why it looked erratic:** it only bit when the deleted pack was the one
  that had initialised espeak, and re-downloading `us` appeared to heal it
  purely because `us` maps to the folder `kokoro`, so the recreated directory
  landed back on the cached path. `gb`/`fr`/`de` use other folders and stayed
  broken. Any theory that does not explain the `us` exception is wrong.
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
  `PAYMENTS_SPEC.md`. Owner leaning (2026-08-28, ~95% confident, not final):
  gate only the Upgraded/Kokoro voice behind the paywall, keep Standard/
  Piper free — needs a real unit-economics re-run before it's final.
  Entitlement Worker (`worker/`) exists, runs on **D1** (migrated off KV
  2026-08-28, live in production + staging — see `TODO.md`'s "D1
  migration"), but isn't called from the app yet (would paywall current
  users with no way to pay).
- **iOS**: planning only, no engineering started. Hardware is no longer the
  blocker — an M1 MacBook Air was acquired 2026-08-29 (the earlier purchase
  that fell through 2026-08-10 was replaced). No `ios/` Capacitor platform
  exists yet (`@capacitor/ios` isn't a dependency). Apple Developer
  enrollment and `npx cap add ios` are the next actionable steps.
- **Legal**: ToS/Privacy drafted, pricing/lifetime clauses added, **lawyer
  review requested 2026-08-10, still awaiting response** — treat as not
  lawyer-reviewed until confirmed otherwise. Jurisdiction: Québec, Canada.
- **Business registration (REQ, Québec)**: with the lawyer, blocks the bank
  account / GST-QST registration / store payout setup, in that order. **Billing is STORE-ONLY as of 2026-09-02** (Play + App Store, no web checkout, no Stripe) — see `PAYMENTS_SPEC.md` §4.
- **Security/code/licence audit done 2026-09-01**: 24 findings, 15 fixed the
  same day (error-contract tests, a trial race that could downgrade a payer,
  JWT `kid`, CORS allowlist, per-IP rate limiting, tar traversal guard,
  `allowBackup=false`, pinned `kokoro-js`, licence record + verbatim texts in
  EN+FR). Six remain, all needing a device, a lawyer or an owner call. **Two release blockers: the espeak-ng
  GPL question, and that the repo has no `LICENSE` file at all — so the
  engine source is published but not licensed, which is not the same thing.**
  See `TODO.md`'s audit section.
- See `TODO.md` for the actively-maintained task list (this section is a
  snapshot, `TODO.md` is the live source for "what's next").
