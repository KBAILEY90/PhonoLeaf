# CLAUDE.md

Guidance for working in this repository.

## What this is

**KoboAudio** — a mobile-first PWA that reads your epubs aloud. It connects to
Google Drive (read-only), lists epub files from a folder, renders them with
epub.js, and reads the text using the browser's Web Speech (TTS) engine.

- Live: https://kbailey90.github.io/koboaudio/
- Repo: https://github.com/KBAILEY90/koboaudio
- Status: working personal project; owner is evaluating turning it into a
  sellable product (see "Productization roadmap" below).

## Tech stack & structure

- **Pure HTML/CSS/JS, no build step.** Almost everything lives in a single
  file: `index.html` (markup + styles + inline `<script>`).
- `sw.js` — service worker (offline app shell).
- `manifest.json` — PWA manifest.
- `fonts/` — self-hosted variable woff2 (`manrope.woff2` UI, `literata.woff2`
  reading), latin subset; precached by `sw.js`. No Google Fonts hotlink.
- `.github/workflows/deploy.yml` — deploys to GitHub Pages on push to `main`.
- External libs via CDN: **jszip** + **epub.js** (jsdelivr), **Google Identity
  Services** (GIS) for OAuth.

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
  bump `CACHE` in `sw.js` (e.g. `koboaudio-v2` → `-v3`) whenever the precached
  asset list changes, to force clients off the old shell.
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
  `App` persists the token in `localStorage` (`kba_auth`) and resumes the
  session on load ("keep me logged in"); `Drive._fetch` silently re-auths once
  on a 401. There is no refresh token (browser-only implicit flow).
- **Theming is CSS-variable driven (Daylight light / Midnight dark).** `:root`
  holds the **Daylight** (light) tokens as the default; a
  `@media (prefers-color-scheme: dark)` block supplies **Midnight** (dark)
  automatically, and `[data-theme="light"]`/`[data-theme="dark"]` blocks (placed
  after the media query so they win) force a mode. An early inline `<script>` in
  `<head>` reads `localStorage.kba_theme` (`auto`|`light`|`dark`; default `auto`)
  and sets `document.documentElement.dataset.theme` before paint (no flash).
  **Settings → Theme** (Light/Dark/Auto segmented control) drives it via
  `Theme.apply()` (writes `kba_theme`, sets/clears `data-theme`, re-skins an open
  book); `Theme.isDark()` resolves the effective mode. Both themes share fonts
  (Manrope UI + Literata reading); switching only flips colors. Use the tokens — `--bg`,
  `--surface`, `--card`, `--accent`/`--accent-rgb`, `--text`, `--text-dim`,
  `--line` (hairlines), `--overlay` (hover), `--track` (subtle fills/borders),
  `--cover-fallback`, `--read-bg`/`--read-text`, `--font-ui`/`--font-read` — never
  hardcode a hex that assumes one mode.
- **Reader has a real dark reading surface.** `Reader.applyReadTheme()`
  registers an epub.js theme (`rendition.themes.register('kb', …)`) that sets the
  iframe `body` color/background to match the app theme (light `#fff`/`#2a2a2e`,
  dark `#15161e`/`#cdd0e0`), and `--read-bg`/`--read-text` match so the letterbox
  agrees. Called on `Reader.open` and re-applied by `Theme.apply` / the
  `prefers-color-scheme` change listener when in `auto`.
- **App shell is a 3-tab nav (`Nav`): Home · Library · Settings.** A fixed
  `.tab-bar` (`#tab-bar`, `.show` toggles visibility) sits under the three main
  `.view`s; `Nav.go(tab)` swaps the view via `showView`, marks the active tab,
  shows the bar, and re-renders Home/Settings. The bar is hidden on sign-in and
  in the reader (`Nav.hideBar()`); `Reader.close()` returns via `Nav.go`. Auth
  success now lands on **Home**, not Library.
- **Home (`Home.render`)** = "Continue" hero (most recent `kba_prog` entry, which
  now stores `{cfi,pct,chapter,ts}`), three stat tiles (`Stats.summary()`), and a
  "Jump back in" cover row. **Library** keeps the grid + a search field
  (`Library.filter`, index-preserving). **Settings (`Settings`)** holds the theme
  switcher, default speed (`kba_speed`, restored on boot + in `TTS.rate`), the
  voice picker (`VoiceModal`), and account/sign-out + folder. The Home title is
  "Home" (not "Your library" — it's not the Library tab).
- **Epub metadata (`Meta`, `kba_meta`)**: `Meta.capture(id, book)` reads
  `book.packaging.metadata` (author=`creator`, `year` from `pubdate`, publisher,
  language) for free during cover extraction (`Covers._extract`/`fromBook`) and
  on open, and caches it in `localStorage`. Library cards show the author on a
  fixed-height `.book-meta` line (replaced the file-size line) so cards are a
  uniform height — grid spacing no longer depends on title length; titles are
  single-line ellipsis-clipped to the cover width. (Genre isn't a reliable epub
  field; `subject` is sometimes present but inconsistent.)
- **Covers/metadata refresh Home as they load.** `Covers` runs in the
  background after the library loads; each finished cover now also re-renders
  Home (when it's the active tab) so the dashboard's covers/authors fill in
  without first visiting Library.
- **Listening stats (`Stats`, `kba_stats`)**: a 5-second interval started in
  `TTS.start`/`skipPage` and cleared in `TTS.stop` accumulates seconds per day
  (`days[YYYY-MM-DD]`); `summary()` derives hours-this-week, a consecutive-day
  streak, and books-in-progress for Home.
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
  fades again; `Reader.expand()` always shows controls on entry. The reader's
  top-left button is a clear back **arrow** (`Reader.minimize()` → Home).
- **Audio↔page sync (`TTS._resumeRead`).** After any page change the resume
  retries extraction until the new page's text is actually laid out **and** is no
  longer the page we left (`_prevText`, set in `skipPage`/forward-advance) —
  forcing a fresh `loadPageText` each try (~14×/110ms). epub can report the old
  column for a frame after `next()`, so without the `_prevText` guard the audio
  read the previous page; this is the real fix for "audio doesn't match".
- **Back/edge-swipe steps back in-app, doesn't exit.** The full reader
  `pushState({app:'reader'})` (in `open` 'full' + `expand`); a `popstate` handler
  (bound in boot, after `replaceState({app:'base'})`) minimizes the reader to
  Home instead of leaving, and otherwise re-pushes a sentinel to stay in the app.
- **Seek scrubber (`Scrub`)** lives on the Home mini-player hero and in the
  reader; both are `.scrub` range inputs wired by **delegated** input/change.
  Dragging shows `#scrub-pop` (chapter + `p. N/total` + %, from
  `locations.cfiFromPercentage`/`spine.get`); release seeks via
  `rendition.display(cfi)` through `TTS.skipPage`. `_onRelocated` calls
  `Scrub.setPct` (skipped while dragging). Needs generated locations; before
  they're ready the popup shows only a %.
- **Home greeting uses the user's name.** `App.loadUser()` reads Drive
  `about → user.displayName` (works under `drive.readonly`), caches the first
  name in `kba_user`/`State.userName`, and the Home title shows
  "Good {morning/afternoon/evening}, {name}".
- **Drive folder is changeable (Google Picker + fallback).** Settings → "Change"
  → `FolderPicker.open()` lazy-loads `apis.google.com/js/api.js` + the Picker and
  browses Drive **hierarchically** (`DocsView(ViewId.DOCS).setParent(start)` with
  `setIncludeFolders`/`setSelectFolderEnabled`, folders-only, LIST mode), where
  `start` = the current folder id or `'root'` (My Drive). Uses the **login OAuth
  token** (`setOAuthToken`) + `setOrigin` — **no separate API key**;
  `CONFIG.API_KEY` is optional (`setDeveloperKey`, quota only). Needs the Picker
  API enabled. Falls back to `FolderModal` (styled in-app typed-name modal, no
  native `prompt`) if not signed in or the Picker fails. `setFolder(id, name)`
  persists `kba_folder_id` (picked id, wins) / `kba_folder` (name) and reloads;
  `Library.load` uses `activeFolderId() || findFolder(activeFolder())`.
- **Folder onboarding.** When no folder is found / no books, the Library and Home
  empty states show a "Choose folder" button (`Library._pickBtn` →
  `FolderPicker.open`). `State.ready` (set when a load attempt finishes) gates the
  Home prompt so it shows "Loading your library…" first rather than flashing the
  onboarding for users who do have books.
- **Home "jump back in"** shows only *started* books (a `kba_prog` entry with a
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
- **Empty pages are skipped — direction-aware.** A page with no extractable
  text (the cover, or any image-only page) used to make `start()` bail with "No
  text found to read". Now blank-page handling depends on travel direction
  (`TTS._dir`: `1` forward, `-1` back; reset to `1` whenever a real chunk is
  spoken or play is pressed): going **forward** into a blank page skips ahead to
  the next page; going **backward** into a blank page **stops and waits** for the
  user (no reading, no further skipping). `TTS._skips` caps consecutive forward
  skips at 20 (then stops with the toast) so an all-image book can't loop forever.
- **Don't re-read stale text on a blank page.** `TTS.loadPageText()` must
  *clear* `chunks` when a page is genuinely blank, or `_speak()` re-reads the
  previous page (a real bug). It tells a true blank page (the iframe's
  `doc.body.textContent` is empty) apart from a text page whose layout hasn't
  settled yet (textContent present but geometry not yet measurable) — only the
  former clears chunks; the latter keeps them and is retried by the resume path.
- **Reading auto-starts on navigation.** Opening a book auto-starts TTS once the
  first page lays out (`Reader.open`, ~400ms delay). Manual page turns
  (`next()`/`prev()`/swipe) and chapter jumps go through `TTS.skipPage()`, which
  cancels current speech, marks TTS active + `_awaitingPage`, turns the page, and
  lets `Reader._onRelocated` resume reading on the new page. (iOS may block
  auto-start-on-open since the async Drive download breaks the tap's gesture
  chain; skip/jump fire off the gesture so they're fine.)
- **Resume where you left off.** Progress (`{cfi, pct}`) is saved per page turn
  to `localStorage` (`kba_prog`); `Reader.open` restores via
  `display(saved.cfi)` and shows a "Resuming where you left off" toast.
  `Reader._persistPosition()` also snapshots the current page on
  `visibilitychange`(hidden)/`pagehide`/`Reader.close()` so abrupt PWA exits
  don't lose the spot.
- **Reader status bar** (`#rs-chapter` + `#rs-page` pill) shows the chapter name
  and `Page X / Y` (epub.js's per-section `loc.start.displayed`). It's a pinned
  bar in the column flex layout (visible on any device); the `relocated` handler
  is registered *before* `display()` so it populates on open. The bottom panel's
  small line shows `"{pct}% through the book"` to avoid duplicating the chapter.
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
  coral button. `start()`/`skipPage()` add `.playing`; `stop()` removes it.
- **Book covers**: `Covers` extracts each epub's real cover via
  `book.coverUrl()` and caches the image in IndexedDB (`CoverCache`, store
  `covers`, keyed by `id:size`) so it's a one-time download per book. Loading is
  throttled (`MAX` concurrent) and runs in the background after the grid renders;
  opening a book also caches its cover for free via `Covers.fromBook`. Drive's
  `thumbnailLink` is only a placeholder. Tradeoff: first view downloads each book
  once to grab its cover — heavy on a large library (revisit for the product).
- **Voices**: `TTS` ranks system voices (Natural/Neural/Siri/Google/Online float
  to top), auto-selects the best, persists the choice (`kba_voice`), and shows
  the active voice on the reader's `#voice-btn`.
- **Web Speech TTS does not play in the background / with the screen locked** on
  mobile. This is a platform limitation, not a bug — see roadmap.
- Use `100dvh` (not `100vh`) for full-height views so mobile browser chrome
  doesn't hide the bottom controls.

## Conventions

- **Escape all externally-sourced strings** (file names, error messages, voice
  names, chapter titles) with `esc()` before putting them in `innerHTML`. Prefer
  passing indices to inline handlers over interpolating raw values.
- Match the existing terse, dependency-free style. No frameworks, no build.

## Productization roadmap (owner is exploring selling this)

Pending / discussed, not yet done:
1. **Rename/rebrand** off "Kobo" (trademark) — owner will supply a new name.
2. **Switch `drive.readonly` → `drive.file` + Google Picker** to escape
   restricted-scope verification (avoids a ~$15k+/yr security assessment).
   Free; needs the Picker API enabled + a (public, referrer-restricted) API key.
3. **Cloud neural TTS + `<audio>` + MediaSession API** for natural voices and
   background/lock-screen playback. Not free (TTS cost + backend).
4. **Privacy policy + ToS** (required for Google verification & stores).
5. **Backend** for real refresh tokens, payments (Stripe), and a TTS key proxy.

Already hardened for multi-user: XSS escaping of dynamic content.
