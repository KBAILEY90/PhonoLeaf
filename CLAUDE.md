# CLAUDE.md

Guidance for working in this repository.

## What this is

**PhonoLeaf** (formerly KoboAudio) — a mobile-first PWA that reads your epubs
aloud. It connects to Google Drive (read-only), lists epub files from a folder,
renders them with epub.js, and reads the text using the browser's Web Speech
(TTS) engine.

- Live: https://kbailey90.github.io/phonoleaf/
- Repo: https://github.com/KBAILEY90/phonoleaf
- Status: **production-bound (decided 2026-07-03)** — no longer a personal-use
  app; the owner intends to take it to production "very soon". Treat changes
  accordingly (multi-user assumptions, security, cost awareness), and keep the
  "Productization roadmap" below current — it is now the active work plan, not
  an exploration.
- **Brand vs. infra (post-rename, 2026-06-28):** branded **PhonoLeaf**, and the
  GitHub repo + GitHub Pages path were renamed `koboaudio` → `phonoleaf` (Live is
  now `kbailey90.github.io/phonoleaf`). **No OAuth change was needed:** the
  authorized JavaScript origin is host-only (`https://kbailey90.github.io`) — the
  same for the old and new path — so Google sign-in keeps working. Browser storage
  is per-origin (host, not path), so existing users kept their data across the
  rename (keys were still `kba_*` then; renamed to `pl_*` 2026-07-06 — see the
  Naming policy note). Only a future custom
  domain (e.g. `phonoleaf.com`) would require adding a NEW authorized origin in
  Google Cloud Console. (The Drive folder is no longer hardcoded — see "Folder
  onboarding" below.)
- **Naming policy (owner directive, 2026-07-06): nothing may be NAMED
  `koboaudio` anywhere** — the old name appears only in historical notes like
  this section. Status of the last holdouts:
  - IndexedDB: renamed `koboaudio` → `phonoleaf` with a one-time migration
    (`CoverCache.migrate()`, ran at boot, guarded by `pl_dbmig`): copies the
    `covers` store into the new DB and deletes the old one, so installs keep
    their cached covers without re-downloading every book.
  - The `kba_*` localStorage prefix (KoboAudio-era) was RENAMED to `pl_*`
    on 2026-07-06 via a one-time migration at the TOP of the `<head>` script
    (guarded by `pl_mig`; must stay before any storage read — several
    modules read keys at parse time and the theme is read pre-paint).
    Mapping: `kba_X` → `pl_X`, except gold-era names (`kba_voice_gold` →
    `pl_voice_kokoro`, `kba_gold_bench` → `pl_kokoro_bench`) and obsolete
    keys (`kba_tier`, `kba_gtts_key`, `kba_voicetip`, `kba_voice_diamond`)
    which are deleted. No device loses data. The migration block is the ONLY
    place `kba_` may appear in code.
  - The Cloud Console **OAuth consent screen App name** still said
    "KoboAudio" (found 2026-07-06 on the native consent page: "KoboAudio
    wants to access your Google Account") — it lives in the Google project's
    branding settings, not in this repo; owner renames it to PhonoLeaf under
    APIs & Services → OAuth consent screen (a.k.a. Google Auth Platform →
    Branding).
  - The owner's local clone folder (`C:\Repo\koboaudio`) must be renamed to
    `C:\Repo\phonoleaf` manually (documented in TESTING.md §3) — note that
    renaming it detaches this project's Claude session history/memory, which
    is keyed to the folder path.

## Tech stack & structure

- **Pure HTML/CSS/JS, no build step** for the web app itself. Almost
  everything lives in a single file: `index.html` (markup + styles + inline
  `<script>`).
- `sw.js` — service worker (offline app shell).
- `manifest.json` — PWA manifest.
- **Native shell (Stage 2a, added 2026-07-06):** Capacitor 8 wraps the SAME
  web app for the Play Store build. `package.json` (scripts only — the web
  app still has no build), `capacitor.config.json` (`com.phonoleaf.app`,
  webDir `www`), `scripts/stage-www.js` (copies index.html/manifest/sw/fonts
  into `www/` — `www/` and `node_modules/` are gitignored, generated),
  `android/` (committed Capacitor Android project; its build outputs are
  gitignored; Gradle configuration cache is enabled in gradle.properties).
  Installed Capacitor plugins: `@capacitor/browser` + `@capacitor/app`
  (native auth — see the "Native auth" behavior note); `CapacitorHttp` is
  used from core; **`@jofr/capacitor-media-session`** (Stage 4 background/
  lock-screen playback — see that behavior note). NB the media-session plugin
  is built for Capacitor 6, installed with `--legacy-peer-deps`; it compiles on
  Cap 8 (its Gradle has a `namespace` + AGP 8) but only declares
  `FOREGROUND_SERVICE`, so the app manifest adds `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
  (required on Android 14+ or the FGS crashes) + `POST_NOTIFICATIONS`.
  **AGP 9 breaks its vendored `build.gradle` (hit 2026-07-18):** after
  bumping AGP `8.13.0`→`9.2.1` / Gradle `8.14.3`→`9.4.1` (Android Studio's
  own "Fix with AI"/upgrade assistant — that same tool also fixed
  `app/build.gradle`'s own `proguardFiles` line, but has no reach into
  `node_modules`), the plugin's own
  `node_modules/@jofr/capacitor-media-session/android/build.gradle` line 33
  still calls `getDefaultProguardFile('proguard-android.txt')`, which AGP 9
  rejects outright (not just a warning — a hard `EvalIssueException`).
  Fixed via **`patch-package`** (`patches/@jofr+capacitor-media-session+4.0.0.patch`,
  applied by `postinstall` in `package.json`) rather than hand-editing
  `node_modules` each time, since that gets wiped on every `npm install`.
  **`npx patch-package <name>` couldn't auto-generate this patch** — its
  own temp-reinstall step failed silently for this scoped package name on
  Windows (a `cross-spawn` call with `stdio: "ignore"`, so no error ever
  surfaces) — the patch file was hand-written as a plain unified diff
  instead; patch-package's *apply* step doesn't care that it wasn't
  machine-generated, and applying it was verified directly (reverted the
  vendored file, ran `npm install`, confirmed `postinstall` put the fix
  back). Also added `.npmrc` (`legacy-peer-deps=true`) so this plugin's
  peer-dependency conflict with Capacitor 8 doesn't need `--legacy-peer-deps`
  typed out by hand on every install. **Confirmed working in Android Studio**
  — the environment that made this fix had no JDK/Android SDK
  to run Gradle directly, so this was verified on-device rather than here;
  the AGP `8.13.0`→`9.2.1` / Gradle `8.14.3`→`9.4.1` bump that came with it
  builds clean too.
  **Native TTS plugin (Stage 2b):** `PhonoLeafTtsPlugin.kt`
  (registered in `MainActivity.java`) wraps sherpa-onnx's `OfflineTts`
  (Kokoro) — see the "Voice engine" note; the prebuilt AAR is committed at
  `android/app/libs/sherpa-onnx.aar` (no Maven artifact exists — un-ignored
  in `android/.gitignore`), Kotlin is enabled in `app/build.gradle`, ABIs are
  limited to arm. Voice models are owner-placed (gitignored — TESTING.md §3.6):
  `app/src/main/assets/kokoro/` = primary/US (`vits-piper-en_US-libritts_r-medium`),
  `.../kokoro-gb/` = British (`vits-piper-en_GB-vctk-medium`). The plugin is
  **multi-model**: `synthesize`/`prepare` take a `model` key ("us"|"gb");
  `ensureReady(model)` loads the matching folder (`folderFor`) and **reloads
  one model at a time** when the selected voice's accent changes (keeps peak
  RAM to one model). It also auto-detects Kokoro vs Piper per folder from the
  files (`voices.bin` → Kokoro).
  **`app/build.gradle`'s `compileOptions`/`kotlinOptions.jvmTarget` MUST
  match `capacitor.build.gradle`'s `compileOptions`** (currently both
  `VERSION_21`/`'21'`) — the latter is auto-regenerated by every `cap sync`/
  `cap update` and applied AFTER `app/build.gradle`, so it silently wins for
  Java while Kotlin keeps whatever `app/build.gradle` says; a mismatch fails
  `compileDebugKotlin` with "Inconsistent JVM-target compatibility" (hit
  2026-07-06). If Capacitor ever regenerates that file at a different JDK
  version, bump `app/build.gradle` to match. Loop:
  `npm run sync` (stage + copy into android) →
  `npm run open` (Android Studio) → Run ▶ on device — see TESTING.md §3.
  NB: GitHub Pages still deploys the repo root exactly as before — the web
  app is unaffected by the native shell.
- `fonts/` — self-hosted variable woff2 (`manrope.woff2` UI, `literata.woff2`
  reading), latin subset; precached by `sw.js`. No Google Fonts hotlink.
- `.github/workflows/deploy.yml` — deploys to GitHub Pages on push to `main`.
- `vendor/` — **jszip 3.10.1 + epub.js 0.3.93, self-hosted** (2026-07-20,
  security hardening ahead of production). Previously loaded from cdnjs/jsdelivr
  with no Subresource Integrity — a compromised CDN could have run arbitrary JS
  with the user's live Drive token. Vendored verbatim (not modified); bump the
  version and re-fetch from the same upstream URLs if ever updated. Precached
  by `sw.js` (bumped to `phonoleaf-v13`) and copied into the native shell by
  `scripts/stage-www.js`. **Google Identity Services (GIS)** for OAuth is the
  only remaining CDN script (`accounts.google.com/gsi/client`) — first-party
  Google, allow-listed explicitly in the CSP below rather than vendored.
- **Content-Security-Policy** (`<meta http-equiv>` in `index.html`, added
  alongside the vendoring above) locks `script-src`/`connect-src`/`img-src`/etc.
  to the known hosts (Google auth/Drive, Open Library, jsdelivr+huggingface for
  the browser-WASM Kokoro fallback). `'unsafe-inline'` is accepted for
  script-src/style-src — there's no build step, so inline `<script>`/`<style>`
  can't use hashes that survive every edit — but this still blocks an injected
  `<script src="https://evil.com/...">` and, more importantly, blocks an
  injected inline script from ever exfiltrating data (connect-src has no
  attacker-controlled host to fetch to). `'wasm-unsafe-eval'` is required for
  the WASM Kokoro fallback to run at all. Verified (this environment): app
  boots with zero CSP violations, jszip/epub.js/GIS all load, and a synthetic
  epub renders + extracts text correctly (iframe/srcdoc goes through
  `frame-src 'self'`). **Not yet verified on a real device** — if the natural
  voice or sign-in ever silently breaks after touching the CSP, check the
  browser console for a CSP violation first, same as any other native-only
  behavior in this file.

The inline script is organized into plain object "modules":
`CONFIG`, `State`, `Theme`, `Stats`, `Nav` (tab shell), `Home`, `Settings`,
`App` (auth), `Drive` (Drive API), `Library`, `Covers`, `Reader`, `TTS`,
`VoiceModal`, `ChapterModal`, plus `esc()`/`toast()`/`showView()` helpers.

## How to deploy

Edit `index.html` (or `sw.js`), commit, and **push to `main`**. GitHub Actions
(`deploy.yml`) redeploys to Pages in ~1–2 min. There is no staging branch.

- A **second Claude session has also pushed to `main`** in the past — always
  `git fetch` and check `git log HEAD..origin/main` before pushing to avoid
  collisions, and rebase rather than clobber.
- The service worker is **network-first for the HTML** so deploys show up, but
  bump `CACHE` in `sw.js` (currently `phonoleaf-v12`; e.g. `-v12` → `-v13`)
  whenever the precached asset list changes, to force clients off the old shell.
- **After each push to `main`, update this CLAUDE.md** to reflect the shipped
  change (behavior notes / gotchas / roadmap status) and push the doc too. The
  owner treats this file as the living source of truth — keep it current without
  being asked.

## How to verify changes

There's no test suite. Before pushing, syntax-check the inline script:

```bash
# NB: there are two <script> tags now (early theme-init in <head> + the app
# script before </body>), so grab the LAST script block, not the first.
node -e "const fs=require('fs');const h=fs.readFileSync('index.html','utf8');const p=h.split('<script>').pop();require('vm').compileFunction(p.split('</script>')[0]);console.log('JS OK');"
node --check sw.js
```

Full sign-in → Drive → reading flow can only be tested live (needs a real
Google login); verify by inspection + the owner testing on device.

## Critical facts — do NOT "fix" these

- **OAuth Client ID is `...ms3bp55...` (b before p).** It is
  `871446308528-r12mb3i1r87jrk681ms3bp55msubojt7.apps.googleusercontent.com`.
  An earlier message wrote it as `ms3pb55`; that is WRONG. Do not change it
  without the owner explicitly asking — it must match the Google Cloud Console.
- **epub.js must load from jsdelivr**, not cdnjs. The cdnjs path
  (`cdnjs.cloudflare.com/.../epub.js/...`) returns **404**.

## Behavior notes / gotchas

- **OAuth scope is `drive.readonly`** (a *restricted* scope). Tokens last ~1h.
  `App` persists the token in `localStorage` (`pl_auth`) and resumes the
  session on load ("keep me logged in"); `Drive._fetch` silently re-auths once
  on a 401. On the WEB there is no refresh token (implicit flow).
- **Native auth (Stage 3, shipped 2026-07-06) — system-browser PKCE flow.**
  Google blocks OAuth inside embedded WebViews (GIS never initializes — the
  observed symptom was the "Auth loading" guard toast), so when
  `App.isNative()` (Capacitor) the whole GIS path is bypassed: `_nativeSignIn`
  opens a Chrome Custom Tab (`@capacitor/browser`) on
  `accounts.google.com/o/oauth2/v2/auth` with authorization-code + PKCE
  (S256, random state) against an **ANDROID-type OAuth client**
  (`CONFIG.ANDROID_CLIENT_ID`, no client secret); the redirect is the
  REVERSED-client-id custom scheme
  (`com.googleusercontent.apps.<id>:/oauth2redirect`), registered as a
  manifest intent-filter and delivered back via `@capacitor/app`'s
  `appUrlOpen` → `_onDeepLink` (verifies state, closes the tab). The code
  exchange and refresh go through `_tokenRequest`, which uses **`CapacitorHttp`
  (native bridge) because Google's token endpoint sends no CORS headers** — a
  WebView fetch would be blocked. The response includes a **refresh token**
  (`pl_rtoken`): `tryResume`/`refreshToken` renew silently (`_nativeRefresh`),
  so the native app stays signed in permanently; a failed refresh clears
  `pl_rtoken` and falls back to interactive sign-in; `signOut` revokes the
  grant via `oauth2.googleapis.com/revoke`. Shared post-auth path for all
  flows: `App._enterApp()`. STATUS: Android OAuth client created 2026-07-06
  ("PhonoLeaf Android (debug)", package `com.phonoleaf.app`, debug-keystore
  SHA-1) and wired into `CONFIG.ANDROID_CLIENT_ID` + the manifest
  `<data android:scheme>` — ready to test on device (Run ▶, TESTING.md §3).
  If that client is ever recreated in Cloud Console, both places must be
  updated together (a mismatch breaks the deep-link return silently), and
  **"Enable custom URI scheme" must be checked under the client's Advanced
  Settings** — it is OFF by default on new Android clients and sign-in then
  fails with `Error 400: Custom URI scheme is not enabled` (hit 2026-07-06;
  takes ~5 min to propagate after saving). **VERIFIED ON DEVICE 2026-07-06:**
  full native flow works — sign-in via Custom Tab, deep-link return, Drive,
  library, reader. NB the WebView origin has its OWN localStorage: the native
  app starts fresh (no progress/stats carried over from the Chrome PWA), and
  Kokoro still runs as browser-WASM inside the WebView until Stage 2b — on
  the owner's phone it reads ~2 sentences then stalls ~10s (generation
  slightly slower than realtime; prefetch absorbs it only briefly).
- **Theming is CSS-variable driven (Daylight light / Midnight dark).** `:root`
  holds the **Daylight** (light) tokens as the default; a
  `@media (prefers-color-scheme: dark)` block supplies **Midnight** (dark)
  automatically, and `[data-theme="light"]`/`[data-theme="dark"]` blocks (placed
  after the media query so they win) force a mode. An early inline `<script>` in
  `<head>` reads `localStorage.pl_theme` (`auto`|`light`|`dark`; default `auto`)
  and sets `document.documentElement.dataset.theme` before paint (no flash).
  **Settings → Theme** (Light/Dark/Auto segmented control) drives it via
  `Theme.apply()` (writes `pl_theme`, sets/clears `data-theme`, re-skins an open
  book); `Theme.isDark()` resolves the effective mode. Both themes share fonts
  (Manrope UI + Literata reading); switching only flips colors. Use the tokens — `--bg`,
  `--surface`, `--card`, `--accent`/`--accent-rgb`, `--text`, `--text-dim`,
  `--line` (hairlines), `--overlay` (hover), `--track` (subtle fills/borders),
  `--cover-fallback`, `--read-bg`/`--read-text`, `--font-ui`/`--font-read` — never
  hardcode a hex that assumes one mode.
- **Reader reading surface (dimmed + centered).** `Reader.applyReadTheme()`
  registers an epub.js theme (`rendition.themes.register('kb', …)`) that sets the
  iframe `body` to match the app theme — light is a **softly dimmed** `#F3F0E7`
  (warm paper, not stark white) / `#2b2b2b`, dark `#14160F`/`#CDD0C4` — plus `padding:0.5em
  5%` (side margins to centre the text) and `line-height:1.65`. `--read-bg`/
  `--read-text` match so the letterbox agrees. Called on `Reader.open` and
  re-applied by `Theme.apply` / the `prefers-color-scheme` listener when `auto`.
- **Brand palette & logo (PhonoLeaf).** Accent is **leaf green** (light `#3E8E6B`
  / dark `#6FC598`) with a **brass** secondary `--accent2` (light `#C98A3C` / dark
  `#E0A857`) on warm-paper / forest neutrals; tokens live in `:root`, the dark
  media query, and the `[data-theme]` blocks, with `applyReadTheme()` mirroring the
  reading surface and `theme-color` metas matching. The app icon (`manifest.json`)
  and favicon are an inline-SVG **"Soundwave Vein"** mark — a cream leaf on a green
  tile whose central vein is a sound waveform. Manifest `theme_color` = leaf green,
  `background_color` = forest `#0F1310`.
- **Heading pauses are per-chunk (not a section timer).** Chunks are now
  `{text, pre, post}` (ms of silence around each); `_chunksFromSegments` gives a
  **chapter title** (`H1`/`HGROUP`) `pre 3000 / post 2000` and a **subtitle**
  (`H2–H6`) `pre 2000 / post 1000`; body chunks get `0/0`. `_speak` applies `pre`
  once per chunk (`_preIdx` guard, `_gapT` timer) and `post` in `onend`. Reset
  `_preIdx` whenever the page text reloads.
- **Page turn = clean INSTANT turn (`Reader.turnPage`).** Every animated slide
  attempt fought epub.js's single-iframe architecture and is documented here so
  nobody re-tries them: epub does the real turn as an **instant scroll** of the
  `.epub-container` (one `clientWidth` per page; the whole section is pre-rendered
  as side-by-side columns in a ~5120px iframe) and **reports the new position one
  frame late** — so (a) a `transform` on the live iframe flashes the *page-after*
  for one frame ("page-3 flash"); (b) a manual `scrollLeft` is reverted by epub
  and `reportLocation()` snaps back to epub's tracked position; (c) CSS
  `scroll-behavior:smooth` breaks epub's scrolling; (d) a cloned-document
  snapshot overlay (`importNode` of the epub doc into a throwaway iframe) renders
  **blank/unreliably**. So `turnPage(dir)` now just clears any transform and calls
  epub's own `next()`/`prev()` — no overlay, no transform race ⇒ no blank, flash,
  or flicker, at the cost of no slide animation. The **finger-drag still gives
  live feedback** during the swipe (`touchmove` sets `transform:translateX(dx)` on
  the live iframe, revealing the neighbour from the pre-rendered columns; it's
  `{passive:false}`+`preventDefault()` once horizontal or the browser swallows
  the move); the commit (`_dragCommit`→`turnPage`) snaps that back to 0 and lets
  epub turn. `_dragTurn` suppresses the `_turnAnim` fade in `skipPage`.
  `Reader._pageEl()` returns the `#viewer iframe` (re-queried; epub may swap it).
  Paths: **finger drag** past ~20% width → `_dragCommit`→`turnPage`, else
  `_dragSnapBack()`; **buttons/edge-arrows/keyboard** → `turnPage(dir)`. (A real
  no-flash *slide* would need a reliably-rendered snapshot of the leaving page —
  unsolved; revisit only with live device testing.)
- **Double-tap = play/pause with icon feedback.** The double-tap toggles `TTS`
  and `Reader._tapFeedback(playing)` fades a centered play/pause glyph
  (`#tap-fb`, `@keyframes tapfb`) in and out.
- **App shell is a 4-tab nav (`Nav`): Home · Library · Stats · Settings.** A fixed
  `.tab-bar` (`#tab-bar`, `.show` toggles visibility) sits under the main `.view`s;
  `Nav.go(tab)` swaps the view via `showView`, marks the active tab, shows the
  bar, and re-renders Home/Stats/Settings. The bar is hidden on sign-in and in the
  reader (`Nav.hideBar()`); `Reader.close()` returns via `Nav.go`. Auth success
  lands on **Home**.
- **Home (`Home.render`)** = "Continue" hero (most recent `pl_prog` entry, which
  now stores `{cfi,pct,chapter,ts}`), three stat tiles (`Stats.summary()`; tapping
  them opens the Stats tab), and a "Jump back in" cover row. **Library** keeps the
  grid + a search field (`Library.filter`, index-preserving). **Settings**
  (`Settings`) holds the theme switcher, default speed (`pl_speed`), voice picker,
  account/sign-out + folder. The Home title shows the greeting + user name.
- **Stats tab (`StatsPage.render` → `#stats-view`)** layout:
  - **Row 1 tiles**: all-time hours · this-week hours · day streak.
  - **Row 2 tiles**: in library · started · finished.
  - **"Listening · last 14 days"** CSS bar chart. Bar heights are explicit px
    (ratio × 92px) computed in JS from raw seconds — percentage heights don't
    resolve reliably through flexbox. Peak day = full height; non-zero days get
    at least 4px. Hover (desktop) or tap (mobile) a bar to reveal a centered
    `"Xmin"` pill above it (`StatsPage.tapBar`); only one shows at a time.
    Empty state shows a `.bars-empty` hint. (Publication-year range and languages
    were intentionally removed.)
  - **Breakdown table with a grouping dropdown** (`.atable`): a `.set-select`
    (persisted to `pl_stats_group`, default `author`) switches `StatsPage._group`
    between **By author**, **By book**, **By genre**, and **By book length**;
    `setGroup()` saves the choice + re-renders, and
    `StatsPage._breakdown(g, books, bookSecs, prog)` builds the rows.
    All four are 4-column grids: *Author* (Min read · Started · Read; top 8
    by minutes), *Book* (Min read · % · Read; top 8 by minutes), *Genre*
    (Books · Min read · Finished; top 8 by minutes; genres from Open Library),
    *Length* (Books · Min read · Finished; bucketed **<300 pages / 300–499 pages /
    500+ pages** using `Meta.get(b.id).pages` from Open Library). **All four
    views count only books with activity** (listening minutes or a `pl_prog`
    started entry) — genre/length must not tally the whole library, or rows
    survive a stats reset. Length and genre show a "loading in background"
    placeholder until `Meta.fetchAll` has fetched the data (`known` counts
    books with metadata regardless of activity, so the placeholder only shows
    while metadata is genuinely missing); with metadata but no activity they
    show the `_emptyBreak` "press play" hint. `—` shows for zero values.
    A **"Reset listening data"** ghost button at the bottom opens a custom
    `ConfirmModal` dialog (no browser domain row) and on confirm clears `pl_stats`
    **and** `pl_prog` (so "started" + "finished" tiles also reset to 0), then
    re-renders Stats and Home.
- **Epub metadata (`Meta`, `pl_meta`)**: `Meta.capture(id, book)` reads
  `book.packaging.metadata` (**title**, author=`creator`, `year` from `pubdate`,
  publisher, language) for free during cover extraction
  (`Covers._extract`/`fromBook`) and on open, and caches it in `localStorage`.
  `capture` **merges** into an existing entry (backfills fields older captures
  didn't store — e.g. `title` — without clobbering fetched genre/pages).
  `Meta.fetchAll(books)` runs in the background after the library loads (2
  concurrent requests); for each book without genre/pages it calls
  `Meta._fetchOL(id, title, author)` → Open Library `search.json` → stores
  `pages` (number_of_pages_median) and `genre` (`Meta._pickGenre` maps the
  subject list against `Meta._GENRE_MAP` to a normalized label: Science fiction /
  Fantasy / Mystery / Romance / Thriller / Horror / Historical fiction /
  Biography / History / Self-help / Young adult / Children's).
  **`_pickGenre` returns `''` when nothing matches — NEVER fall back to the
  first OL subject**: that subject is usually a topic, often echoing the book's
  own title (Cixin Liu's *Ball Lightning* → genre "Ball lightning"), which isn't
  a genre. `Meta._cleanGenres()` (boot, guarded by `pl_genrefix`) drops cached
  genres left by that old fallback — any value not in `_GENRE_MAP`'s labels.
  The Stats **By genre** breakdown then shows genre-less books as **"Other"**
  (always sorted last). NB its "still loading" check keys off `genre || pages`,
  NOT `genre` — a book with no recognised genre legitimately has none now, so
  the old genre-only check would have shown "loading" forever.
  **The OL title must be a real title**: raw Drive filenames ("Author - Title",
  dots/underscores, bracketed junk) match nothing or the WRONG book (this made
  By genre / By book length permanently empty). `fetchAll` prefers the
  captured epub `title`, else `Meta._cleanName(filename)` (strips extension,
  `(...)`/`[...]` groups, separator dots/underscores, and keeps the last
  ` - `-separated part). `_fetchOL` retries once on title-only when
  title+author found nothing (epub author strings often differ from OL's
  canonical name). After all fetches complete, re-renders the Stats tab if active.
  Library cards show the author on a fixed-height `.book-meta` line.
- **Covers/metadata refresh Home as they load.** `Covers` runs in the
  background after the library loads; each finished cover now also re-renders
  Home (when it's the active tab) so the dashboard's covers/authors fill in
  without first visiting Library.
- **Listening stats (`Stats`, `pl_stats`)**: a 5-second interval started in
  `TTS.start`/`skipPage` and cleared in `TTS.stop` accumulates seconds both
  per day (`data.days[YYYY-MM-DD]`) **and** per book (`data.books[driveFileId]`)
  via `State.currentBook.id`. Per-book tracking was added mid-project — earlier
  day-only totals cannot be retroactively attributed to books. `summary()`
  derives hours-this-week, a consecutive-day streak, and books-in-progress for
  Home.
- **Immersive reader chrome auto-hides; a gesture overlay drives it.** Controls
  are absolute overlays over a full-bleed `#viewer`: thin top progress
  (`#tts-prog`), a `.reader-top` bar, a seek scrubber + the floating `.tts-pill`
  in `.reader-bottom`. Touches inside the epub iframe don't reach the parent, so
  a transparent `#reader-touch` overlay (`Reader._bindGestures`, bound once)
  captures them: **swipe L/R turns the page**, a **single tap toggles** the
  controls (debounced ~280ms via `_tapT`), a **double-tap plays/pauses**
  (`TTS.toggle`); on desktop a **click toggles** and **mousemove reveals**.
  `hideChromeSoon(ms=5000)` hides `hide-chrome`; it's armed **once** in
  `TTS.start` (not per chunk) so a tap/`revealChrome` gives a full ~5s before it
  fades again; `Reader.expand()` always shows controls on entry. **Once touch is
  used (`_touchUsed`), the `click`/`mousemove` handlers are ignored** — a delayed
  synthetic `click` was toggling the just-revealed controls back off (~0.5s bug).
  The reader's top-left button is a clear back **arrow** (`Reader.minimize()` → Home).
- **Audio↔page sync (`TTS._resumeRead`).** After any page change the resume
  retries extraction until the new page's text is actually laid out **and** is no
  longer the page we left (`_prevText`, set in `skipPage`/forward-advance) —
  forcing a fresh `loadPageText` each try (64×/60ms, ~3.8s max). epub can
  report the old column for a frame after `next()`, so without the `_prevText`
  guard the audio read the previous page; this is the real fix for "audio
  doesn't match". The retry budget is 64×/60ms (~3.8s), bumped from 24× on
  2026-07-20 to give a fresh section's heavier layout headroom.
- **Resume SKIPS the first chunk's lead pre-pause (`_resumeRead` sets
  `_preIdx = idx`) — the real fix for "the chapter changes but never reads."**
  Owner-reported: landing on a chapter's first page (e.g. "Siberia" in Ball
  Lightning) never read aloud, in EVERY arrival direction (auto-advance, next,
  prev, chapter jump — all route through `_resumeRead`), yet pressing
  pause+play always fixed it. Root cause: a chapter's first chunk is its title
  (`H1`), which carries a 3s pre-pause implemented in `_speak` as a
  **gen-guarded silent `setTimeout`** (`if (active && gen === this._gen)
  this._speak()`). On a fresh section, a stray second relocation / turn can bump
  `TTS._gen` during that 3s window → the timer fires, the gen no longer matches,
  it no-ops, and the page NEVER speaks. pause+play worked ONLY because `start()`
  leaves `_preIdx` untouched (doesn't reset it to `-1` like `_resumeRead` did),
  so its `_speak` sees `_preIdx === idx` and skips the same pre-pause, speaking
  immediately. Fix: `_resumeRead` now sets `_preIdx = this.idx` right before its
  `_speak()`, so the resumed page's first chunk speaks synchronously — no
  gen-guarded deferral to lose. Verified in a harness: bumping `_gen` right
  after resume no longer suppresses the read (it did before). Cost: the dramatic
  inter-chapter lead silence is gone; **mid-page subtitle pauses (`idx > 0`)
  still apply** (only the first chunk is exempted). A separate `Diag` breadcrumb
  (`{e:'stop-hastext'}`) now marks the OTHER silent-stop path (page has DOM text
  but extraction found no chunks) so `pl_diag` shows which one fired if a
  non-reading page is seen again. NB the earlier (2026-07-20) theory that the
  retry budget was the cause was WRONG — more retries didn't help because
  extraction wasn't the bottleneck; the gen-voided pre-pause was.
- **Back/edge-swipe uses real tab history (no flash).** `App._initHistory()`
  (after auth) does `replaceState({app:'base'})`; `Nav.go(tab)` then pushes
  `{app:'tab',tab}` per navigation, and the full reader pushes `{app:'reader'}`.
  The `popstate` handler: full reader → `Reader.minimize()`; a `{tab}` entry →
  `Nav.go(tab, fromPop=true)` (a real back to that tab, so the gesture peek's
  snapshot matches — fixes the previous-page flash); at base → arm `_exitArmed`,
  show the centered dimmed `#exit-hint` ("Swipe again to leave", `ExitHint`),
  push a buffer, and reset after **2s** (a back after the window re-prompts; a
  back within it calls `history.back()` to leave). The reader back arrow calls
  **`Reader.back()`, NOT `history.back()`** — in the native Capacitor WebView a
  click-driven `history.back()` doesn't reliably fire `popstate` (tap
  registered — button highlighted — but nothing happened), so `back()`
  minimizes directly and then consumes the pushed `reader` history entry with a
  `_skipPop`-guarded `history.back()` (the guard makes the popstate handler skip
  its minimize/tab logic so it isn't done twice).
- **Seek scrubber (`Scrub`)** lives on the Home mini-player hero and in the
  reader; both are `.scrub` range inputs wired by **delegated** input/change.
  Dragging shows `#scrub-pop` (chapter + `p. N/total` + %, from
  `locations.cfiFromPercentage`/`spine.get`); release seeks via
  `rendition.display(cfi)` through `TTS.skipPage`. `_onRelocated` calls
  `Scrub.setPct` (skipped while dragging). Needs generated locations; before
  they're ready the popup shows only a %.
- **Home greeting uses the user's name.** `App.loadUser()` reads Drive
  `about → user.displayName` (works under `drive.readonly`), caches the first
  name in `pl_user`/`State.userName`, and the Home title shows
  "Good {morning/afternoon/evening}, {name}".
- **Drive folder selector is a custom themed browser (`FolderBrowser`), not the
  Google Picker.** The Picker couldn't be themed or name-sorted (and looked
  off), so Settings → "Change" / onboarding open `#browser-modal`: a normal
  in-app modal (inherits theme via CSS vars) that lists sub-folders via the Drive
  API (`'<id>' in parents and mimeType=folder`, `orderBy:'name'` → **name asc**),
  with a clickable breadcrumb (`_stack`), tap-a-row to navigate in, and "Use this
  folder" to pick the current one. **Always starts at `root` = My Drive**, with
  the existing pick shown as context in `#fb-current` ("Currently: X — open a
  folder to browse…"). It used to open INSIDE the current folder, which listed
  only its sub-folders — usually "No sub-folders here" — so changing looked
  impossible unless you guessed the breadcrumb was tappable (owner reported this). `setFolder(id,name)` persists `pl_folder_id` (id wins) /
  `pl_folder` and reloads; `Library.load` uses `activeFolderId()` then, only if
  a folder name is set, `findFolder(activeFolder())`. (`FolderModal` typed-entry
  remains only as a not-signed-in fallback; `CONFIG.API_KEY` is now unused.)
- **Folder onboarding — no hardcoded default.** `activeFolder()` returns `''`
  when nothing is chosen (the old `CONFIG.FOLDER_NAME = 'Rakuten Kobo'` default
  was removed), and `hasChosenFolder()` reports whether `pl_folder_id` /
  `pl_folder` is set. After auth, `App._promptFolderIfNeeded()` (called from both
  `signIn` and `tryResume`) auto-opens `FolderBrowser` ~300ms later when no folder
  is chosen, so first-run users are prompted to pick their Drive books folder.
  When no folder is chosen / no books, the Library and Home empty states also show
  a "Choose folder" button (`Library._pickBtn` → `FolderBrowser.open`).
  `State.ready` (set when a load attempt finishes) gates the Home prompt so it
  shows "Loading your library…" first rather than flashing onboarding for users
  who do have books.
- **Home "jump back in"** shows only *started* books (a `pl_prog` entry with a
  `ts`) opened in the **last 30 days**, newest (left) → oldest; clamped to the
  cover width (`.cr-item { min-width:0; overflow:hidden }`) so a long title can't
  space the covers apart (flex `min-width:auto` guard).
- **Mini-player + minimize (playback decoupled from the visible reader).**
  `Reader.open(index, mode)`: `'full'` (from Library / expand) shows the reading
  page; `'mini'` (from Home / `Player.play`) keeps the reader **laid out but
  hidden** — `.view.minimized` is `position:fixed; inset:0; z-index:-1;
  pointer-events:none`, so the rendition geometry stays valid and TTS keeps
  working while the Home hero acts as the player. The hero shows the
  loaded/most-recent book with stacked play/pause + expand buttons (`Player`);
  `Reader.expand()` un-minimizes to full screen and the reader's top-left button
  (`Reader.minimize()`) shrinks back to Home. Tab views are opaque (`--bg`) so
  the minimized reader stays hidden behind them. NB: TTS needs a laid-out
  rendition — never `display:none` the reader while it should keep playing.
- **TTS reads only the currently visible page**, then turns the page via
  `rendition.next()`. `TTS.loadPageText()` extracts text from nodes whose
  on-screen box is inside the viewer (epub.js paginated mode keeps the whole
  chapter in off-screen columns; reading `body.innerText` would grab the whole
  chapter and loop forever — this was a real bug, don't reintroduce it).
- **Visibility is word-level at the page edges (`inView`).** A text node fully on
  the page is taken whole (fast path); a node that straddles the column break (a
  paragraph that began on the previous page or runs onto the next) is measured
  **per word** (a `Range` over each `\S+`) and only the words actually on this
  page are kept. Without this, the audio started at the paragraph's first word on
  the *previous* page and ran past the last visible word into the next.
- **Chunking is block-aware — don't flatten text into one string.**
  `loadPageText()` groups visible text by its nearest block-level ancestor
  (`P`/`DIV`/`LI`/`H1-6`/…) and `_chunksFromSegments()` makes each block its own
  spoken chunk (= its own utterance with a gap after). This keeps a chapter
  **heading** from gluing onto the first sentence of the chapter (the original
  bug: headings have no terminal punctuation, so the old space-join produced
  `"The Crossing It was dawn…"` read as one run-on). Headings (`h1–h6`/`hgroup`)
  are flagged and get a forced terminal stop so the voice falls/pauses. If you
  ever move to cloud neural TTS, emit SSML (`<break>`/`<s>`) from these same
  block segments rather than re-flattening.
- **`_split()` must keep the trailing unpunctuated fragment.** Its regex is
  `/[^.!?…\n]+(?:[.!?…\n]+|$)/g` — the `|$` is load-bearing: the last sentence
  on a page is cut by the column break and has NO terminal punctuation, so
  without `|$` the regex captured nothing for it and the fragment was silently
  dropped → the page turned without reading its last sentence (fixed 2026-07-06,
  "skipping the cut last sentence").
- **Empty pages are skipped — direction-aware.** A page with no extractable
  text (the cover, or any image-only page) used to make `start()` bail with "No
  text found to read". Now blank-page handling depends on travel direction
  (`TTS._dir`: `1` forward, `-1` back; reset to `1` whenever a real chunk is
  spoken or play is pressed): going **forward** into a blank page skips ahead to
  the next page; going **backward** into a blank page **stops and waits** for the
  user (no reading, no further skipping). `TTS._skips` caps consecutive forward
  skips at 20 (then stops with the toast) so an all-image book can't loop forever.
  **Only GENUINELY blank pages auto-skip.** `_speak()`'s forward-skip distinguishes
  "finished reading this page" (`chunks.length > 0` → advance, normal continuous
  reading) from "no chunks at all" (`chunks.length === 0`). For the latter it only
  skips when the page is truly text-empty (`doc.body.textContent` blank — a cover/
  image); if the page **has** DOM text we just failed to extract (e.g. a short last
  page of a chapter whose geometry was mis-measured), it **stops on the page**
  rather than skipping to the next chapter. This fixed "the last page of a chapter
  gets skipped on a forward swipe" — the swipe's `rendition.next()` plus the
  over-eager forward-skip were double-advancing.
- **Stale TTS callbacks are generation-guarded (`TTS._gen`) — the OTHER
  "swipe skips the short last page" double-advance.** Two async leftovers from
  the page being left could each fire `_speak()` after a swipe's `skipPage()`
  and issue a SECOND `rendition.next()` on top of the swipe's own: (1) the
  just-finished utterance's queued `onend` (`cancel()` even *fires* `onend` on
  some Androids) doing `idx++` → `_speak()` → "finished page, going forward" →
  advance; (2) a pending heading-pause timer (`_gapT`), which `skipPage` never
  cleared. Fixes: `_gen` is bumped in `start`/`stop`/`skipPage`/`setRate`;
  `_speak` stamps `const gen = this._gen` into its `onend` and gap timers, which
  bail if the gen moved on; `skipPage` also `clearTimeout(_gapT)`; and `_speak`'s
  entry guard is now `!active || _awaitingPage` (nothing may speak or advance
  while a turn is in flight — only the relocation's `_resumeRead` continues,
  after `_onRelocated` clears `_awaitingPage`). Side effects: pausing mid-sentence
  now resumes at the START of that sentence (the stale `onend` no longer `idx++`s),
  and a speed change can't skip the rest of the current sentence.
- **Starved-utterance retry (`TTS._retryN`) — the swipe-skips-short-pages cause
  the gen guards could NOT catch.** On Android, `speak()` soon after an
  *interrupting* `cancel()` (exactly what a swipe does mid-speech) can be
  silently eaten by the engine: the fresh, CURRENT-gen utterance fires `onend`
  instantly with no audio. On a long page that just swallows the first sentence;
  on a low-word page (one chunk) the instant `onend` looks like "page finished"
  → legitimate auto-advance → the page visibly skips unread. `_speak()` now
  timestamps each utterance and, if it "ends" faster than its text could be
  spoken (`min(250ms, len*25/rate)`), re-speaks the chunk (≤3 tries, 150ms·n
  backoff) instead of advancing; a current-gen `onerror('interrupted'/'canceled')`
  retries the same way (our own cancels bump `_gen` first, so a current-gen
  interruption can only be the engine) rather than stalling. A `done` flag makes
  onend/onerror act once (some engines fire both). The pre-speak `cancel()` is
  also now conditional on `speaking || pending` — a gratuitous cancel right
  before `speak()` is what tends to trigger the starvation.
- **Forward-overshoot corrector (`Reader.nextPage`/`_onRelocated`) — the VISUAL
  "page 1 → 3" skip on phones.** epub.js's `next()` boundary check
  (`scrollLeft + width + delta <= scrollWidth`) is pixel arithmetic; on phones
  the viewport width is fractional, and when a section's LAST page holds little
  text the few-px rounding error makes epub.js jump to the next section instead
  of showing the sliver page (desktop widths are integers — never trips). All
  single-page forward turns (swipe/buttons/keyboard AND the TTS auto-advance in
  `_speak`) route through `Reader.nextPage()`, which flags `_fwd` and measures
  the raw container scroll state: `_fwdSkip` = more than **half a page** of the
  section was still unseen to the right (`.epub-container`'s
  `scrollWidth - scrollLeft - clientWidth > clientWidth/2`). `_onRelocated`
  then checks: `_fwdSkip` yet landed in the NEXT spine section ⇒ overshoot ⇒
  `rendition.prev()` back onto the skipped page and `return` (the overshot page
  is never saved as progress and TTS's `_awaitingPage` is re-armed so speech
  resumes on the corrected page). **Do NOT use `loc.displayed.page/total` as
  the signal** — the same rounding bug misreports the page number ON the sliver
  page (it says 1 of 2), so a page/total-based check made a legit swipe OFF the
  sliver look like another overshoot and trapped the user there in a
  correction loop (shipped briefly; fixed by the scroll-state measurement,
  whose ½-page tolerance is immune to few-px errors). `_fwdFixed` limits it to
  one correction per turn (no loops); chapter jumps and scrub seeks don't set
  `_fwd`, so multi-section `display(cfi)` moves are never "corrected". Legit
  chapter changes leave ~0px unseen and pass untouched.
  **Anti-trap (`_fixedAtCfi`):** the corrector never fires twice from the same
  start CFI — if detection misfires at some spot (device-specific scroll-state
  surprises), the first swipe there may bounce back but the second ALWAYS
  passes through, so the user can never be stuck on a page. The trap spot is
  cleared on any clean (uncorrected) forward pass so a later genuine overshoot
  at that CFI corrects again.
- **On-device diagnostics (`Diag`, `pl_diag`) + build stamp.** Every forward
  turn logs `{e:'next', sl/sw/cw, skip}` (container scroll state + overshoot
  verdict) and each forward relocation logs `{e:'rel', i, pi, p, tot}` plus
  `FIX` / `trap-skip` events into a 30-entry ring buffer in `localStorage`
  (`pl_diag`). **The Settings UI for this was REMOVED (2026-07-06, owner
  request)** — no Debug-log `<details>`, no build stamp (it always read `dev`
  in the native build since only `deploy.yml` sed-stamps `BUILD`, so it was
  just noise). `Diag` still records to `pl_diag`; re-expose it temporarily if a
  page-turn bug needs on-device evidence again.
- **Don't re-read stale text on a blank page.** `TTS.loadPageText()` must
  *clear* `chunks` when a page is genuinely blank, or `_speak()` re-reads the
  previous page (a real bug). It tells a true blank page (the iframe's
  `doc.body.textContent` is empty) apart from a text page whose layout hasn't
  settled yet (textContent present but geometry not yet measurable) — only the
  former clears chunks; the latter keeps them and is retried by the resume path.
- **Reading auto-starts on navigation.** Opening a book sets `Reader._autoStartBook`
  and `_onRelocated` starts TTS ~400ms after the page **settles** to the restored
  `display(saved.cfi)` position — NOT a fixed timer from `open` (which read the
  page that was briefly visible mid-settle, i.e. the *previous* page). Manual page turns
  (`next()`/`prev()`/swipe) and chapter jumps go through `TTS.skipPage()`, which
  cancels current speech, marks TTS active + `_awaitingPage`, turns the page, and
  lets `Reader._onRelocated` resume reading on the new page. (iOS may block
  auto-start-on-open since the async Drive download breaks the tap's gesture
  chain; skip/jump fire off the gesture so they're fine.)
- **Resume where you left off.** Progress (`{cfi, pct}`) is saved per page turn
  to `localStorage` (`pl_prog`); `Reader.open` restores via
  `display(saved.cfi)` and shows a "Resuming where you left off" toast.
  `Reader._persistPosition()` also snapshots the current page on
  `visibilitychange`(hidden)/`pagehide`/`Reader.close()` so abrupt PWA exits
  don't lose the spot.
- **Reader overlay top bar (`.reader-top`)** shows: `[← back]` · `[chapter · Page X/Y center]` · `[≡ chapters]`. A single `#rs-chapter` element (`.reader-top-info`, `0.7rem`) displays the combined string `"Chapter Name  ·  Page X / Y"`. `_onRelocated` populates it by flattening the full TOC tree (including `subitems`) and matching by basename — TOC hrefs are often bare filenames while `loc.start.href` has a path prefix (`xhtml/ch.xhtml`). If no direct match, falls back to the nearest preceding TOC entry by spine index (handles flat TOCs where sub-chapters aren't listed individually). **The overlay and the `ChapterModal` share the module-level `flattenToc()`** (subitems inline, with depth) so their chapter names match exactly — the modal used to list only top-level `State.toc` while the overlay could show a subitem name. The bottom `reader-meta` shows only `{pct}% through the book` (`#tts-chapter`). `applyReadTheme()` measures `.reader-top` and `.reader-bottom` `offsetHeight` and uses those as pixel padding for the epub `body`, so text isn't hidden under either overlay.
- **Chapter jump** (`ChapterModal`): TOC hrefs can be relative to the nav doc
  and/or carry a `#fragment` that won't match epub.js's spine lookup, so passing
  the raw href to `display()` silently fails. `_resolveHref()` resolves it to a
  canonical, fragment-free spine section href (`spine.get`, then a basename match
  against `spine.spineItems`) so the jump lands on the chapter's first page;
  failures now toast "Could not open chapter" instead of failing silently.
- **Playback speed** is a fixed `0.5x–2x` dropdown in `0.25` steps (default
  `1.0x`), applied via `TTS.setRate()` which restarts the current utterance at
  the new rate.
- **Play/pause icon is drawn in CSS, not a font glyph.** `#play-btn` toggles a
  `.playing` class; `.ctrl-btn.play::before`/`::after` draw a triangle (idle) or
  two bars (playing), always white. Do NOT go back to a `⏸`/`▶` text glyph — the
  `⏸` emoji (U+23F8) renders as an orange color-emoji on Windows against the
  green accent button. `start()`/`skipPage()` add `.playing`; `stop()` removes it.
- **Book covers**: `Covers` extracts each epub's real cover via
  `book.coverUrl()` and caches the image in IndexedDB (`CoverCache`, store
  `covers`, keyed by `id:size`) so it's a one-time download per book. Loading is
  throttled (`MAX` concurrent) and runs in the background after the grid renders;
  opening a book also caches its cover for free via `Covers.fromBook`. Drive's
  `thumbnailLink` is only a placeholder. Tradeoff: first view downloads each book
  once to grab its cover — heavy on a large library (revisit for the product).
- **Voices**: `TTS` ranks system voices (Natural/Neural/Siri/Google/Online float
  to top), auto-selects the best, persists the choice (`pl_voice`), and shows
  the active voice on the reader's `#voice-btn`. **`VoiceModal`** caches the
  voice list at `open()` time into `_list` (fixes an index-mismatch bug where
  `allVoices()` could re-sort between render and select). Selection is
  name-based (`selectNamed`, `data-vname` attribute) not index-based.
  `_speak()` always resolves the voice from a live `getVoices()` call before
  creating each `SpeechSynthesisUtterance` — cached voice objects are silently
  ignored by Chrome/Safari if the browser's voice list has refreshed. It
  resolves from the **persisted `pl_voice` name first** (`pickDefaultVoice`
  can transiently clobber `TTS.voice` when Android returns a partial voice
  list) and **sets `u.lang = voice.lang`** — Android ignores `u.voice` unless
  the utterance lang agrees, which made every selected voice sound like the
  system default (accent/gender never changed).
- **Voice engine — Kokoro-only (tiers REMOVED 2026-07-04).** Kokoro-82M is
  THE product voice; the device's Web Speech engine survives only as an
  automatic fallback. One chunk state machine, two paths:
  - **Native path (`_synthNative`, preferred when present)**: on the
    Capacitor Android build, `TTS._nativeTts()` finds the `PhonoLeafTts`
    plugin and `_synth` routes to it — sherpa-onnx runs Kokoro natively,
    returning **a WAV FILE path + durationMs** (NOT base64: a ~1 MB base64
    string per sentence decoded into a `data:` URL froze the WebView main
    thread — the reader UI, incl. the back button, stopped responding). JS
    loads it via `Capacitor.convertFileSrc(path)` (local-server stream, off
    the main thread) into the same `<audio>`/prefetch pipeline. The plugin
    generates on a **single-thread executor** (serialized, off-main). Uses the
    **int8** model (`kokoro-int8-en-v0_19`) — the fp32 model took 10-30s to
    generate ONE sentence on the owner's phone, pegging every core for the full
    window and freezing the reader UI (back button dead); int8 is 2-4× faster
    (validated: the owner's standalone sherpa APK ran int8 faster than
    realtime). **big.LITTLE thread tuning (measured on the owner's 8-core
    phone, 2026-07-06):** `numThreads` matters a LOT — 7 threads → ratio 2.4×
    realtime (little cores drag + sync overhead), 2 threads → 1.6×, 4 threads →
    **~1.36×** (best; 4 = the phone's fast-core count, 5+ regresses onto slow
    cores). Set to `maxOf(2, minOf(4, cores-4))`. **CEILING FINDING: even
    optimally tuned this phone runs Kokoro-int8 at ~1.36× realtime → NOT
    gapless (generation can't outpace playback, so no buffering fixes it).
    NNAPI was also tried (offload to the Pixel's Tensor NPU) — it ENGAGED
    (`prov=nnapi`) but didn't accelerate the TTS model (~1.45×, no better than
    CPU; the known "NNAPI helps ASR not TTS" limitation) — so NNAPI was
    dropped, CPU only.** **DECISION (2026-07-06): Kokoro is not viable on the
    Pixel 7 / mid-tier Android; switched the native BASELINE to a lighter
    Piper/VITS model (`vits-piper-en_US-libritts_r-medium`, should run <1× on
    phones = gapless). Kokoro to return later as a PREMIUM voice auto-enabled
    only on capable devices (flagships/iPhones) via the ratio measurement.**
    The plugin **auto-detects the model family** from the placed files
    (`voices.bin` present → Kokoro config; else → VITS/Piper config), so
    switching engines is just a model-file swap. cancel() bounds the
    leave-delay to one in-flight synth. WAV files rotate through a small
    cacheDir ring; each clip is **peak-normalized** to ~0.95 (gain capped 6×)
    in `writeWav` so voices/models match in loudness (the UK vctk model was
    quieter than the US libritts one). `_stopAudio()` calls the plugin's `cancel()` (bumps an
    `epoch`; queued-but-unstarted synths whose stamp is stale are skipped) so
    leaving the reader doesn't leave seconds of dead inference pegging the CPU.
    A genuine failure (e.g. model files not placed) strikes out to the device
    voice per chunk like any synthesis failure. `_synthNative` returns
    `{path, durationMs, provider, modelType}`; the last two show in the on
    -screen debug readout (`#tts-dbg`, e.g. `vits/cpu`). **The voice→speaker
    -id map lives in JS** (catalog `[id,label,sid,model]`, third field = sherpa
    `sid`; `_voiceSid()`), so a wrong-sounding voice is a one-line JS fix +
    `npm run sync`, no Gradle rebuild — but the sids are MODEL-SPECIFIC.
    **NEVER guess the model type** — sids are catalog-specific, so guessing wrong
    makes the first chunk speak in the wrong voice and then audibly switch
    (owner-reported twice; an earlier "assume vits when native" shortcut was
    removed at the owner's request — don't reintroduce it). Instead
    `_synthNative` **awaits `TTS._modelReady()`**, which resolves from the
    plugin's `prepare()` report. Only the FIRST session waits: `_setModelType`
    caches to `pl_modeltype` and `_modelType` is restored synchronously at parse
    time, so later sessions resolve instantly (verified ~0ms).
    **Per-model catalogs (`TTS._modelType`, set from `prepare()`/`_synthNative`):**
    `_nativeCatalog()` returns `PIPER_VOICES` when a Piper/vits model is loaded,
    else `KOKORO_VOICES`; `_voiceKey()` persists the choice separately
    (`pl_voice_piper` vs `pl_voice_kokoro`), so switching models keeps each
    one's voice. `VoiceModal.selectNative` + `_voiceSid` + `activeVoiceLabel`
    all go through these. `PIPER_VOICES` entries are `[id, label, sid, model]`
    where `model` ("us"/"gb") selects the native model folder; `_voiceModel()`
    reads it and `_synthNative` passes it to the plugin. Current set (8, all
    owner-auditioned, 2 female + 2 male per accent): **US** (libritts_r) Ava 40 /
    Nora 160 female, Ben 16 / Jack 520 male — speakers 92 & 600 were also good
    males if a swap is ever wanted; **UK** (vctk) Amelia 0 / Ruby 85 female,
    Sam 70 / Max 20 male. Rejected along the way: US 0/256/400, UK 10/50/92.
    First entry (Ava, us/sid 40) = default. The picker still prints `speaker N`
    under each voice — drop that once the UK genders are confirmed. The on-screen `#tts-dbg` timing
    readout was removed once Piper proved gapless. The Kotlin plugin
    (`PhonoLeafTtsPlugin.kt`) is model-agnostic:
    it only sets the optional `dataDir`/`dictDir`/`lexicon` paths that actually
    exist (the
    English model has espeak-ng-data but no dict/lexicon), **resolves the ONNX
    filename at runtime** (`model.onnx` for fp32, `model.int8.onnx` for int8,
    else any `*.onnx`) — hard-coding `model.onnx` made the int8 model's missing
    file crash the native loader with no catchable exception (the app just
    closed); a genuinely absent model now throws a catchable
    `FileNotFoundException` → device-voice fallback instead of a crash — and a
    `MODEL_VERSION`-stamped `.ready` marker forces the filesDir copy to refresh
    when the bundled model changes (else the old copy wins). `_synthNative`
    logs gen-ms / audio-ms / ratio per chunk to Diag (`{e:'nsynth',g,a,r,len}`)
    so device speed is measurable from the Debug log. Speed is applied via
    `<audio>.playbackRate` (synth always at 1.0) so prefetched/cached chunks
    survive speed changes.
  - **Neural path (`_playAudio` → browser-WASM, web + fallback)**: Kokoro-82M **in a Web Worker**
    (`_kokoroWorkerEl` builds the worker from a Blob; the worker `import()`s
    `kokoro-js@1/+esm` from jsdelivr and loads HuggingFace
    `onnx-community/Kokoro-82M-v1.0-ONNX`, WebGPU `fp32` when `navigator.gpu`
    else WASM `q8`, ~90 MB one-time download with a progress toast; `sw.js`
    passes huggingface/cdn-lfs requests through — transformers.js does its own
    caching). **The worker is NOT optional** — v1 ran inference on the main
    thread and froze the page for tens of seconds per sentence on phones.
    Playback via a shared `<audio>` element: `onended` drives the same
    idx++/post-pause chain as Web Speech `onend`, gen guards apply, the NEXT
    chunk is **prefetched during playback** (`_preSynth`), blob URLs are
    revoked, `stop()`/`skipPage()` call `_stopAudio()`, `setRate` just sets
    `playbackRate` live. Model **pre-warmed at every boot** (`_kokoroWarm`);
    "Generating audio…" toast when synthesis is audibly slow (no prefetch
    ready after ~600ms). Voice catalog `KOKORO_VOICES`, choice persisted in
    `pl_voice_kokoro` (migrated from the tier-era `kba_voice_gold`; same
    for the `pl_kokoro_bench` bench key).
  - **Fallback path (`_speakWeb`)**: the pre-existing Web Speech code with all
    the gen-guard/starvation logic. Entered per chunk on any synthesis
    failure, and for the whole session when `_kokoroDead` is set — by **2
    consecutive failures** (30s timeout each) or by the **speed probe**
    (`_kokoroBench`: after model load, the worker generates a fixed test
    sentence; generation-time ÷ audio-duration > 1.25 ⇒ the device can't
    sustain continuous playback — the owner's phone measured 2-3× slower than
    realtime in browser WASM). The probe verdict is cached per backend; at
    boot a cached slow verdict skips the model download entirely and goes
    straight to fallback (silently — the toast only fires on a FRESH
    verdict). Probe ratios are logged to Diag (`{e:'bench', be, r}`).
    **Settings surfaces fallback mode** (`#fallback-group`: "Natural voice —
    Unavailable on this device" + **Retry** = `Settings.retryNeural()`, which
    clears the bench cache + strike-out and re-warms). `VoiceModal` shows the
    Kokoro catalog normally, the system-voice list in fallback mode.
  - REMOVED in the tier teardown (don't resurrect): the `Tier` object,
    `pl_tier`, the Settings tier dropdown, Diamond/Google Cloud TTS
    (`_synthGoogle`, `_gcache`, `GOOGLE_VOICES`, `pl_voice_diamond`,
    `pl_gtts_key`, `Settings.setGKey`), and the `VoiceHelp` onboarding
    popup + `pl_voicetip`. Stale keys may linger in old installs; harmless.
- **Better-voices helper (`VoiceHelp`) — fallback-mode only, MOBILE ONLY.**
  Points users at higher-quality SYSTEM voices, relevant only when the app is
  reading with the device voice: the Settings row `#vh-row` shows only when
  `TTS._kokoroDead && VoiceHelp.available()`. **Android's button opens the
  "Install voice data" screen directly** via an `intent:` URI
  (`android.speech.tts.engine.INSTALL_TTS_DATA` with `S.browser_fallback_url`
  → the Google TTS Play-Store page when an OEM blocks the intent); it must be
  launched with `location.href` from the click (top-level navigation —
  `window.open` gets blocked). iOS gets Enhanced/Premium-Siri steps, no
  button. Desktop excluded (`VoiceHelp.available()`): desktop voice installs
  add *variants*, not *quality*.
- **Background playback (Stage 4, native — OUR OWN foreground service).**
  `PlaybackService.kt` + `PhonoLeafTts.startPlaybackService/stopPlaybackService`,
  called from `TTS._mediaState(true/false)` on `start`/`stop`. **Why it's
  needed:** our audio is a CHAIN of one-sentence clips driven by a JS
  `onended` loop; backgrounded, Android suspends the app, so the playing clip
  and the already-prefetched one finish and then the chain dies (owner-observed
  exactly that). A foreground service keeps the process alive so the loop keeps
  running with the screen off.
  **We do NOT use `@jofr/capacitor-media-session`** — it crashed the app ~1-2s
  after pressing play (Capacitor-6-era plugin vs `targetSdk 36`'s much stricter
  foreground-service rules; confirmed by a kill-switch bisect). It's still in
  package.json but UNUSED — remove it (and its patch-package patch + `.npmrc`)
  in a cleanup pass.
  **It also holds a PARTIAL (CPU) wake lock while playing** — the service stops
  the app being KILLED but not the CPU SLEEPING once the screen locks, and every
  sentence needs the CPU for the JS `onended` loop + Piper inference. Without it
  playback still died ~2 sentences after locking (i.e. when the pre-generated
  buffer ran out) even with the service running and battery unrestricted. NB
  `TTS._acquireWakeLock()` is a *screen* lock (`navigator.wakeLock`), which
  Android releases the moment the screen turns off — it does nothing for this.
  Android-16 requirements our service satisfies, each of which will crash or
  kill the app if missed: `android:foregroundServiceType="mediaPlayback"` on the
  `<service>`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
  permissions, `startForeground()` called IMMEDIATELY in `onStartCommand`
  (~5s limit — the likely old crash), the matching type passed to
  `ServiceCompat.startForeground` (API 29+), a notification channel (API 26+),
  and `PendingIntent.FLAG_IMMUTABLE` (API 31+). The notification shows the book
  title + current chapter. NB **no MediaSession yet** → no lock-screen
  play/pause/skip buttons; that's a separate follow-up. The Web-Speech
  (device-voice fallback) path still can't play backgrounded.
  **FGS-start crash + fix (device Logcat, 2026-07-21):** pressing play right
  after unlocking (reader open but the app still mid keyguard-resume) crashed
  the app — Logcat showed
  `RemoteServiceException$ForegroundServiceDidNotStartInTimeException:
  Context.startForegroundService() did not then call Service.startForeground()`.
  On Android 12+ starting a `mediaPlayback` FGS from a not-fully-foreground state
  is disallowed: our `startForeground()` was refused, and because
  `startForegroundService()` had already armed the OS's ~5s watchdog, Android
  force-crashed the process regardless of our try/catch (the watchdog exception
  fires system-side and is NOT catchable). **A first fix — gating the start on
  `appInForeground()` + hardening the service — did NOT stop it** (the app WAS
  foreground when play was pressed, so the guard passed; the real cause is the
  ~5s watchdog firing because the main thread was too busy at play time — resync
  page-turns + model load — for the service's `onStartCommand` to run in time,
  i.e. a TIMEOUT, matching "DidNotStart**InTime**"). **The fix that works: use
  `context.startService()` instead of `ContextCompat.startForegroundService()`
  in `startPlaybackService`.** `startService` is the ONLY thing that changes —
  it's allowed from the foreground (we still keep the `appInForeground()` guard
  so it can't throw `IllegalStateException` from the background) and it does NOT
  arm the 5s watchdog, so a late `startForeground()` (called in `onStartCommand`
  as before, to become a real mediaPlayback FGS) is fine instead of fatal. The
  `ForegroundServiceDidNotStartInTimeException` is thrown ONLY by the
  `startForegroundService` path, so switching eliminates the class entirely.
  `PlaybackService` stays hardened (channel in `onCreate`, `startForeground`
  first in `onStartCommand`, `stopForeground(REMOVE)`+`stopSelf` if refused).
  Native-only; couldn't run Gradle here (no JDK/SDK), verified by review.
- **`window.speechSynthesis` is UNDEFINED in the native Android WebView.** The
  Web-Speech device-voice fallback (`_speakWeb`, `allVoices`,
  `VoiceModal.selectNamed`, `pickDefaultVoice`) must guard every
  `window.speechSynthesis.*` access (`?.`/`|| []`) or it throws at boot on
  native — the Logcat above also showed
  `Cannot read properties of undefined (reading 'getVoices')` at boot.
  `_speakWeb` now bails via `stop()` when there's no engine (native has native
  Piper/Kokoro; there's nothing to fall back TO). Keep new speechSynthesis calls
  guarded.
- **Background reading — read spine TEXT directly (`TTS._bgMode`, replaced the
  virtual-page approach 2026-07-21).** The foreground service + wake lock keep
  the audio *alive* backgrounded, but the reader stopped at page/chapter
  boundaries: epub.js's page turn AND section render both run through the render
  loop (`requestAnimationFrame`), which Android FREEZES with the screen off. The
  first attempt (virtual pages: geometry-shift the extraction window to read the
  next off-screen COLUMN, `_vpage`/`loadPageText(vpage)`/`_resyncVisual`) crossed
  pages but NOT sections, and stalled on short "sliver" last pages (the fixed
  `vpage*width` band misaligned). **Replaced with reading the book's TEXT
  straight from the spine, decoupled from visual rendering** — flows across pages
  AND chapters with the screen off. When `document.hidden` and not the Web-Speech
  fallback, `_speak()`'s forward-advance calls **`_bgAdvance()`** (async):
  - **First step**: switch from the visible page's geometry chunks to the WHOLE
    current section's chunks, read from the already-rendered iframe doc (NOT a
    spine load — that would touch the section epub is actively showing).
    `_bgAlign()` finds where in the section we already are (matches a visible-page
    chunk inside the section chunks, continues just past it) so there's no re-read
    or skip; if it can't align it fails closed (stops).
  - **Section boundary**: `_loadSectionChunks(idx+1)` loads the NEXT spine
    section's text via `section.load(book.load…)` (no render) and reads from its
    top — crossing the chapter boundary a frozen visual turn can't. Skips
    empty/nav sections; stops at end of book.
  - **`_sectionSegments(docOrEl)`** extracts block-grouped segments like
    `loadPageText` but with no geometry filter; it must handle BOTH a Document
    and the bare `<html>` element (what `section.load()` returns), AND
    **uppercase tag names** — spine sections parse as XHTML where `tagName` is
    lowercase (`h1`,`p`), so without `toUpperCase()` every node fell through to
    `<body>` as one giant segment (heading glued to the first paragraph). We do
    NOT `unload()` loaded sections (shared with the live rendition; a few
    chapters of text is negligible).
  - **On unlock** (`visibilitychange`→visible) **`_bgResync()`** brings the
    visible reader to the section the audio reached via
    `skipPage(()=>display(section.href))`, which resets `_bgMode` and resumes
    normal foreground page-reading from that chapter's first page — the audio
    re-reads at most the current chapter's already-heard portion (bounded), never
    a lost chapter or a desync.
  - `_bgMode` resets on `start()`/`skipPage()`/`stop()`; the whole path is gated
    on `document.hidden`, so **foreground reading is unchanged**. Only the
    neural/native audio path backgrounds (Web-Speech can't). Verified in a browser
    harness: alignment continues at the exact next chunk, and cross-section
    load+chunking yields correctly-separated heading/sentence chunks — but the
    audio + on-device background timing are NOT yet device-verified.
  The old `_vpage`/`_resyncVisual`/`_resyncing`/`_armResyncWatchdog`/`_cancelResync`
  machinery is now DORMANT (kept to avoid churn; `_vpage` stays 0 so
  `_resyncVisual` never fires) — remove in a later cleanup. Its historical bug:
  an interrupted resync left `_resyncing` stuck true, and `_onRelocated`'s resync
  guard then swallowed every later relocation ("chapter changes, page turns, but
  never reads"), fixed via `_cancelResync()` from all entry points + a 4s
  watchdog.
- **The native app is PORTRAIT-LOCKED** (`android:screenOrientation="portrait"`
  on `MainActivity`). Rotating re-flows the epub into a different column layout,
  so page counts/positions shift under the reader — a page-end auto-advance then
  jumped backwards several pages (owner-reported). The UI is portrait-first
  anyway (matches `manifest.json`'s `"orientation": "portrait"`). Supporting
  landscape properly = re-anchor to the current CFI on resize and reset the
  page-turn corrector's stale `_prevLoc`/`_fixedAtCfi`; revisit only if wanted
  (tablets/foldables would benefit).
- Use `100dvh` (not `100vh`) for full-height views so mobile browser chrome
  doesn't hide the bottom controls.

## Conventions

- **Escape all externally-sourced strings** (file names, error messages, voice
  names, chapter titles) with `esc()` before putting them in `innerHTML`. Prefer
  passing indices to inline handlers over interpolating raw values.
- Match the existing terse, dependency-free style. No frameworks, no build.

## Productization roadmap (ACTIVE — production-bound as of 2026-07-03)

The owner has decided this will ship as a product "very soon"; this section is
the working plan, not an exploration.

1. ~~**Rename/rebrand** off "Kobo" (trademark)~~ — **DONE (2026-06-28):
   rebranded to PhonoLeaf**, and the GitHub repo + Pages path renamed
   `koboaudio` → `phonoleaf`. OAuth needed no change — the JS origin is host-only
   (`https://kbailey90.github.io`), the same for both paths — and because storage
   is per-origin, user data carried over. (2026-07-06: the IndexedDB was also
   renamed to `phonoleaf` with a covers migration, and all `kba_*` keys were
   migrated to `pl_*` — see the "Naming policy" note.) Domains `phonoleaf.com/.ca/.app/.io` were all available and
   no conflicting trademark was found (formal CIPO/USPTO clearance still
   recommended before filing).
2. **Switch `drive.readonly` → `drive.file` + Google Picker** to escape
   restricted-scope verification (avoids a ~$15k+/yr security assessment).
   Free; needs the Picker API enabled + a (public, referrer-restricted) API key.
3. **Voice: single Kokoro engine, shipped natively — DECIDED 2026-07-04**
   (supersedes the three-tier system, which shipped 2026-07-03 and was torn
   out the next day; Diamond/Google Cloud TTS and the testing tier-dropdown
   are REMOVED — see the "Voice engine" behavior note).
   - **Why:** owner rates Kokoro quality as the product voice; on-device =
     zero COGS (vs ~$1/listening-hour for cloud neural — a 30 h/mo user
     would have cost ~$30/mo), no key proxy, one tier = simpler product.
   - **Validated on device (2026-07-04):** browser WASM Kokoro on the
     owner's phone = 2-3× slower than realtime (unusable, 30-45s gaps);
     sherpa-onnx's native Kokoro APK as system TTS engine = faster than
     realtime (<10s gaps, and those only because the system-TTS path can't
     pre-synthesize). Conclusion: native inference + our existing prefetch
     (`_playAudio`/`_preSynth`, synthesize next chunk during playback) =
     gapless.
   - **Distribution: Play Store first, App Store later.** Owner wants
     everything testable before any store push — see `TESTING.md` (novice
     -friendly guide: web testing now, Android Studio + USB setup, the
     Stage-2 run loop, regression checklist, publishing refs).
   - **Native build plan (staged):**
     - Stage 1 — DONE (2026-07-04): web app refactored to Kokoro-only with
       automatic device-voice fallback; production logic, no test switches.
     - Stage 2a — DONE (2026-07-06): Capacitor 8 shell scaffolded (see
       "Native shell" note in Tech stack). Builds/installs via Android
       Studio; shows the sign-in screen (which can't proceed until Stage 3).
     - Stage 2b — native TTS plugin: **DONE, on device (2026-07-06)** —
       `PhonoLeafTtsPlugin.kt` (sherpa-onnx `OfflineTts`) exposes
       synthesize(text, sid, speed) → WAV file path; `_synth` prefers it over
       the Web Worker when present. On-device iterations: model
       multi-lang-v1_1 → en-v0_19 → kokoro-int8-en-v0_19; file path not base64
       (froze the WebView); `cancel()` on stop; big.LITTLE thread tuning
       (7→2.4×, 4→1.36×); NNAPI (engaged, no TTS speedup). **VERDICT: Kokoro
       ~1.36× realtime on the Pixel 7 = not gapless. Pivoted the native
       baseline to Piper (`vits-piper-en_US-libritts_r-medium`), auto-detected
       by the plugin — owner testing quality+speed. If Piper's quality is
       acceptable it's the baseline everywhere; Kokoro returns as a premium
       voice on capable devices.** See the Voice-engine note.
     - Stage 3 — auth rework: **DONE + VERIFIED ON DEVICE (2026-07-06)** —
       system-browser (Chrome Custom Tabs) authorization-code + PKCE flow
       with refresh tokens; see the "Native auth" behavior note. Two
       Cloud-Console follow-ups were needed: enable custom URI scheme on
       the Android client, and rename the consent-screen App name off
       "KoboAudio". Plugins added: @capacitor/browser, @capacitor/app.
       Full native flow confirmed working; voice is still WebView-WASM
       Kokoro (~10s stalls every ~2 sentences on the owner's phone) until
       Stage 2b ships the native engine.
     - Stage 4 — background playback: **IN PROGRESS (2026-07-20)** — the
       media-session PLUGIN crashed the app ~1-2s after play (Cap-6 plugin vs
       targetSdk 36 FGS rules), proven by a kill-switch bisect; replaced with
       our own `PlaybackService.kt` foreground service + a PARTIAL (CPU) wake
       lock (see the "Background playback" note) — verified on device that audio
       survives the screen turning off. Then hit "reads only ONE page locked":
       epub's rAF-gated page turn is frozen when hidden, so added **virtual-page
       background turning** (`TTS._vpage` / `loadPageText(vpage)` / `_resyncVisual`
       — see the "Background PAGE-TURNING" note). Needs on-device verification
       that reading now continues across pages with the screen off. Still TODO:
       MediaSession lock-screen transport controls, IndexedDB audio caching,
       removing the unused jofr plugin, and (background) a real section/chapter
       turn while hidden (currently stops at the chapter boundary until unlock).
     - Stage 5 — Play Console ($25 one-time), internal testing track, store
       listing + privacy policy (item 4), then production rollout. iOS
       (Apple $99/yr) after Android is proven.
4. **Privacy policy + ToS** (required for Google verification & stores).
5. **Backend** for real refresh tokens and payments (Stripe on web; Play
   Billing in-app — note Play takes 15% vs ~3% Stripe). The TTS key proxy is
   no longer needed (no cloud TTS).

Already hardened for multi-user: XSS escaping of dynamic content.

## Security hardening (in progress, 2026-07-20)

Prompted by production-bound status + Drive access. Audit found the XSS
escaping already solid (every `innerHTML` sink checked) and the web auth
token low-risk by design (~1h, no refresh token in the browser). Gaps found
and fixed, in priority order:
1. ~~**No Subresource Integrity on the CDN scripts**~~ — **DONE**: jszip +
   epub.js self-hosted in `vendor/` instead (see the Tech-stack note). This
   was the biggest real exposure (a compromised CDN could run arbitrary JS
   with the user's live Drive token).
2. ~~**No Content-Security-Policy**~~ — **DONE**: added (see the Tech-stack
   note). Locks script/connect/img/etc. origins to the known hosts as a
   backstop against XSS and exfiltration.
3. **TODO — native refresh token in WebView localStorage.** Sandboxed to the
   app (not exposed to other apps) but should move to Android Keystore-backed
   encrypted storage before launch.
4. **TODO — switch `drive.readonly` → `drive.file` + Google Picker**
   (roadmap item 2 above). Avoids the CASA security-assessment requirement
   for restricted scopes (~$15k+/yr) AND is the deepest security fix
   available: even a fully compromised token could then only ever read the
   books the user explicitly picked, not their whole Drive. Reshapes folder
   onboarding — the custom `FolderBrowser` can't exist under `drive.file`
   (the app can only see files the user picked via the Picker), so this is a
   real feature change, not just a scope swap.
