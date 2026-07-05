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
  is per-origin (host, not path), so existing users keep their token (`kba_auth`),
  progress (`kba_prog`), and cached covers across the rename. Deliberately left
  unchanged (all origin-scoped, not URL-scoped): the OAuth client ID, the `kba_*`
  localStorage keys, and the IndexedDB name (`koboaudio`). Only a future custom
  domain (e.g. `phonoleaf.com`) would require adding a NEW authorized origin in
  Google Cloud Console. (The Drive folder is no longer hardcoded — see "Folder
  onboarding" below.)

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
- **Home (`Home.render`)** = "Continue" hero (most recent `kba_prog` entry, which
  now stores `{cfi,pct,chapter,ts}`), three stat tiles (`Stats.summary()`; tapping
  them opens the Stats tab), and a "Jump back in" cover row. **Library** keeps the
  grid + a search field (`Library.filter`, index-preserving). **Settings**
  (`Settings`) holds the theme switcher, default speed (`kba_speed`), voice picker,
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
    (persisted to `kba_stats_group`, default `author`) switches `StatsPage._group`
    between **By author**, **By book**, **By genre**, and **By book length**;
    `setGroup()` saves the choice + re-renders, and
    `StatsPage._breakdown(g, books, bookSecs, prog)` builds the rows.
    All four are 4-column grids: *Author* (Min read · Started · Read; top 8
    by minutes), *Book* (Min read · % · Read; top 8 by minutes), *Genre*
    (Books · Min read · Finished; top 8 by minutes; genres from Open Library),
    *Length* (Books · Min read · Finished; bucketed **<300 pages / 300–499 pages /
    500+ pages** using `Meta.get(b.id).pages` from Open Library). **All four
    views count only books with activity** (listening minutes or a `kba_prog`
    started entry) — genre/length must not tally the whole library, or rows
    survive a stats reset. Length and genre show a "loading in background"
    placeholder until `Meta.fetchAll` has fetched the data (`known` counts
    books with metadata regardless of activity, so the placeholder only shows
    while metadata is genuinely missing); with metadata but no activity they
    show the `_emptyBreak` "press play" hint. `—` shows for zero values.
    A **"Reset listening data"** ghost button at the bottom opens a custom
    `ConfirmModal` dialog (no browser domain row) and on confirm clears `kba_stats`
    **and** `kba_prog` (so "started" + "finished" tiles also reset to 0), then
    re-renders Stats and Home.
- **Epub metadata (`Meta`, `kba_meta`)**: `Meta.capture(id, book)` reads
  `book.packaging.metadata` (**title**, author=`creator`, `year` from `pubdate`,
  publisher, language) for free during cover extraction
  (`Covers._extract`/`fromBook`) and on open, and caches it in `localStorage`.
  `capture` **merges** into an existing entry (backfills fields older captures
  didn't store — e.g. `title` — without clobbering fetched genre/pages).
  `Meta.fetchAll(books)` runs in the background after the library loads (2
  concurrent requests); for each book without genre/pages it calls
  `Meta._fetchOL(id, title, author)` → Open Library `search.json` → stores
  `pages` (number_of_pages_median) and `genre` (`Meta._pickGenre` maps the
  subject list to a normalized label: Science fiction / Fantasy / Mystery /
  Romance / Thriller / Horror / Historical fiction / Biography / History /
  Self-help / Young adult / Children's; falls back to the first subject).
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
- **Listening stats (`Stats`, `kba_stats`)**: a 5-second interval started in
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
  forcing a fresh `loadPageText` each try (~14×/110ms). epub can report the old
  column for a frame after `next()`, so without the `_prevText` guard the audio
  read the previous page; this is the real fix for "audio doesn't match".
- **Back/edge-swipe uses real tab history (no flash).** `App._initHistory()`
  (after auth) does `replaceState({app:'base'})`; `Nav.go(tab)` then pushes
  `{app:'tab',tab}` per navigation, and the full reader pushes `{app:'reader'}`.
  The `popstate` handler: full reader → `Reader.minimize()`; a `{tab}` entry →
  `Nav.go(tab, fromPop=true)` (a real back to that tab, so the gesture peek's
  snapshot matches — fixes the previous-page flash); at base → arm `_exitArmed`,
  show the centered dimmed `#exit-hint` ("Swipe again to leave", `ExitHint`),
  push a buffer, and reset after **2s** (a back after the window re-prompts; a
  back within it calls `history.back()` to leave). The reader back arrow is just
  `history.back()`.
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
- **Drive folder selector is a custom themed browser (`FolderBrowser`), not the
  Google Picker.** The Picker couldn't be themed or name-sorted (and looked
  off), so Settings → "Change" / onboarding open `#browser-modal`: a normal
  in-app modal (inherits theme via CSS vars) that lists sub-folders via the Drive
  API (`'<id>' in parents and mimeType=folder`, `orderBy:'name'` → **name asc**),
  with a clickable breadcrumb (`_stack`), tap-a-row to navigate in, and "Use this
  folder" to pick the current one. Starts at the current folder (re-selecting) or
  `root` = My Drive. `setFolder(id,name)` persists `kba_folder_id` (id wins) /
  `kba_folder` and reloads; `Library.load` uses `activeFolderId()` then, only if
  a folder name is set, `findFolder(activeFolder())`. (`FolderModal` typed-entry
  remains only as a not-signed-in fallback; `CONFIG.API_KEY` is now unused.)
- **Folder onboarding — no hardcoded default.** `activeFolder()` returns `''`
  when nothing is chosen (the old `CONFIG.FOLDER_NAME = 'Rakuten Kobo'` default
  was removed), and `hasChosenFolder()` reports whether `kba_folder_id` /
  `kba_folder` is set. After auth, `App._promptFolderIfNeeded()` (called from both
  `signIn` and `tryResume`) auto-opens `FolderBrowser` ~300ms later when no folder
  is chosen, so first-run users are prompted to pick their Drive books folder.
  When no folder is chosen / no books, the Library and Home empty states also show
  a "Choose folder" button (`Library._pickBtn` → `FolderBrowser.open`).
  `State.ready` (set when a load attempt finishes) gates the Home prompt so it
  shows "Loading your library…" first rather than flashing onboarding for users
  who do have books.
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
- **On-device diagnostics (`Diag`, `kba_diag`) + build stamp.** Every forward
  turn logs `{e:'next', sl/sw/cw, skip}` (container scroll state + overshoot
  verdict) and each forward relocation logs `{e:'rel', i, pi, p, tot}` plus
  `FIX` / `trap-skip` events into a 30-entry ring buffer in `localStorage`
  (`kba_diag`). **Settings → Debug log** (a `<details>` under the footer) shows
  it with a "Copy log" button — ask the owner to paste it when a page-turn bug
  can't be reproduced locally. The Settings footer also shows the **build**:
  `const BUILD = '__BUILD__'` is sed-stamped with the commit short-SHA by
  `deploy.yml` (shows `dev` locally) — use it to confirm the owner's PWA is
  actually running the latest deploy before debugging further.
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
  to `localStorage` (`kba_prog`); `Reader.open` restores via
  `display(saved.cfi)` and shows a "Resuming where you left off" toast.
  `Reader._persistPosition()` also snapshots the current page on
  `visibilitychange`(hidden)/`pagehide`/`Reader.close()` so abrupt PWA exits
  don't lose the spot.
- **Reader overlay top bar (`.reader-top`)** shows: `[← back]` · `[chapter · Page X/Y center]` · `[≡ chapters]`. A single `#rs-chapter` element (`.reader-top-info`, `0.7rem`) displays the combined string `"Chapter Name  ·  Page X / Y"`. `_onRelocated` populates it by flattening the full TOC tree (including `subitems`) and matching by basename — TOC hrefs are often bare filenames while `loc.start.href` has a path prefix (`xhtml/ch.xhtml`). If no direct match, falls back to the nearest preceding TOC entry by spine index (handles flat TOCs where sub-chapters aren't listed individually). The bottom `reader-meta` shows only `{pct}% through the book` (`#tts-chapter`). `applyReadTheme()` measures `.reader-top` and `.reader-bottom` `offsetHeight` and uses those as pixel padding for the epub `body`, so text isn't hidden under either overlay.
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
  to top), auto-selects the best, persists the choice (`kba_voice`), and shows
  the active voice on the reader's `#voice-btn`. **`VoiceModal`** caches the
  voice list at `open()` time into `_list` (fixes an index-mismatch bug where
  `allVoices()` could re-sort between render and select). Selection is
  name-based (`selectNamed`, `data-vname` attribute) not index-based.
  `_speak()` always resolves the voice from a live `getVoices()` call before
  creating each `SpeechSynthesisUtterance` — cached voice objects are silently
  ignored by Chrome/Safari if the browser's voice list has refreshed. It
  resolves from the **persisted `kba_voice` name first** (`pickDefaultVoice`
  can transiently clobber `TTS.voice` when Android returns a partial voice
  list) and **sets `u.lang = voice.lang`** — Android ignores `u.voice` unless
  the utterance lang agrees, which made every selected voice sound like the
  system default (accent/gender never changed).
- **Voice tiers (`Tier`, `kba_tier`: `member`|`gold`|`diamond`, default member).**
  Three engines behind one chunk state machine: **Member** = device Web Speech
  (`TTS._speakWeb`, all the gen-guard/starvation logic); **Gold** = Kokoro-82M
  neural TTS **in a Web Worker** (`_kokoroWorkerEl` builds the worker from a
  Blob; the worker `import()`s `kokoro-js@1/+esm` from jsdelivr and loads
  HuggingFace `onnx-community/Kokoro-82M-v1.0-ONNX`, WebGPU `fp32` when
  `navigator.gpu` else WASM `q8`, ~90 MB one-time download with a progress
  toast; `sw.js` deliberately passes huggingface/cdn-lfs requests through —
  transformers.js does its own caching). **The worker is NOT optional** — v1
  ran inference on the main thread and WASM generation froze the entire page
  for tens of seconds per sentence on phones (the app looked dead; the owner
  heard one sentence then silence). Guard rails: each `_synthKokoro` request
  has a **30s timeout**; any Gold failure falls back to `_speakWeb` for that
  chunk, and **2 consecutive failures set `_goldDead`** for the session
  (`_engineNow()` then routes Gold → web) with a toast, so playback degrades
  to the device voice instead of stalling — re-selecting Gold in the tier
  dropdown resets the strike-out and re-warms. The model is **pre-warmed** at
  boot when `kba_tier` is already gold, and on switching to gold. A
  "Generating audio…" toast appears when a chunk's synthesis is audibly slow
  (no prefetch ready after ~600ms). **Speed probe (`_kokoroBench`,
  `kba_gold_bench`):** after the model loads, the worker generates a fixed
  test sentence and reports generation-time ÷ audio-duration; a ratio > 1.25
  means the device can't sustain continuous playback (the owner's phone
  measured ~2-3× SLOWER than realtime — 30-45s gaps between sentences;
  prefetch cannot fix a generator that falls further behind every sentence),
  so `_goldDead` is set upfront with one clear toast (recommending Diamond on
  phones) instead of a 30s timeout per chunk. The verdict is cached per
  backend in `kba_gold_bench` (probe skipped on later launches); re-selecting
  Gold clears the cache and re-probes. Probe ratios are logged to the Diag
  debug log (`{e:'bench', be, r}`); **Diamond** = Google Cloud TTS Neural2
  (`_synthGoogle`, direct REST with a key from `kba_gtts_key` — TESTING setup;
  production must proxy the key, roadmap 5; synthesized at 1.0× + MP3 data-URLs
  session-cached in `_gcache` keyed voice|text, speed applied via
  `playbackRate` so the cache survives rate changes). Gold/Diamond play through
  a shared `<audio>` element (`_playAudio`): `onended` drives the same
  idx++/post-pause chain as Web Speech `onend`, gen guards apply, the NEXT
  chunk is **prefetched during playback** (`_preSynth`) to hide synthesis
  latency, blob URLs are revoked, and any synthesis failure **falls back to
  `_speakWeb` for that chunk** (except a missing Diamond key → toast + stop).
  `stop()`/`skipPage()` call `_stopAudio()`; `setRate` on Gold/Diamond just
  sets `playbackRate` live (no restart). Voice catalogs: `KOKORO_VOICES` /
  `GOOGLE_VOICES` (`kba_voice_gold` / `kba_voice_diamond`; `VoiceModal` renders
  the tier's catalog via `selectTier`, Member keeps the system list).
  **The Settings "Voice tier" dropdown is TESTING ONLY — remove it before
  commercialisation** (tier must come from the subscription); same for the
  `prompt()`-based Google-key entry (`Settings.setGKey`).
- **Better-voices helper (`VoiceHelp`, `kba_voicetip`) — MOBILE ONLY.** Member
  -tier blocker reducer: a themed modal pointing users at higher-quality
  SYSTEM voices. **Android's button opens the "Install voice data" screen
  directly** via an `intent:` URI (`android.speech.tts.engine.INSTALL_TTS_DATA`
  with `S.browser_fallback_url` → the Google TTS Play-Store page when an OEM
  blocks the intent); it must be launched with `location.href` from the click
  (top-level navigation — `window.open` gets blocked). iOS gets
  Enhanced/Premium-Siri steps, no button. **Desktop is excluded everywhere**
  (`VoiceHelp.available()` gates the Settings row `#vh-row`, `open()`, and the
  onboarding trigger): the owner correctly observed that desktop voice
  installs add *variants*, not *quality* — Chrome can't see Edge's Natural
  voices, so the tip would be misleading there. Shown ONCE as onboarding:
  `App._promptFolderIfNeeded` opens it ~800ms after launch when the folder is
  already chosen, tier is member, platform is mobile, and `kba_voicetip` is
  unset — never stacked on the folder prompt (first-run users see it next
  launch).
- **Web Speech TTS does not play in the background / with the screen locked** on
  mobile. This is a platform limitation, not a bug — see roadmap. (Gold/Diamond
  play through `<audio>`, which unlocks MediaSession/background playback later —
  not wired yet.)
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
   is per-origin, the `kba_*` keys and `koboaudio` IndexedDB name stay and user
   data carries over. Domains `phonoleaf.com/.ca/.app/.io` were all available and
   no conflicting trademark was found (formal CIPO/USPTO clearance still
   recommended before filing).
2. **Switch `drive.readonly` → `drive.file` + Google Picker** to escape
   restricted-scope verification (avoids a ~$15k+/yr security assessment).
   Free; needs the Picker API enabled + a (public, referrer-restricted) API key.
3. **Cloud neural TTS + `<audio>` + MediaSession API** — the chosen fix
   (2026-07-03) for BOTH open platform limitations: robotic Android system
   voices (Web Speech quality is capped by the device's TTS engine; desktop
   sounds fine, phones don't) and no background/lock-screen playback.
   **STATUS: v1 three-tier system SHIPPED for testing (2026-07-03)** — see the
   "Voice tiers" behavior note: Member (Web Speech + better-voices helper),
   Gold (in-browser Kokoro), Diamond (Google Neural2 via the owner's key /
   free tier). Still to do for production: key proxy (item 5), MediaSession +
   lock-screen playback wiring, IndexedDB audio caching, replacing the
   testing tier-dropdown with subscription-driven tiers.
   **Gold mobile ceiling (measured 2026-07-04):** the owner's phone runs
   Kokoro at ~2-3× slower than realtime (30-45s/sentence) — in-browser
   Kokoro is NOT viable for continuous playback on mid-range phones; a speed
   probe now auto-degrades such devices to standard voices (see "Voice
   tiers" note). Product options if Gold-on-mobile matters: (a) position
   Gold as the desktop/capable-device tier and Diamond as THE mobile
   upgrade; (b) evaluate a faster/smaller in-browser engine for phones
   (e.g. Piper/VITS-class, ~realtime on phone WASM but lower quality than
   Kokoro); (c) **native-app route (analyzed 2026-07-04):** a plain TWA
   wrapper (Bubblewrap) gives Play-Store distribution + Play Billing but
   ZERO speedup (it's the same Chrome engine — browser WASM is stuck
   single-threaded because cross-origin isolation would break Google
   sign-in); a HYBRID (web UI + native inference module, e.g. sherpa-onnx
   running the same Kokoro model) gets multi-threaded fp16 + GPU/NPU,
   ≈2-5× over browser WASM — likely realtime on the owner's phone — while
   keeping one codebase. Strategic driver: on-device Kokoro is zero-COGS
   vs Diamond's ~$1/listening-hour, so hybrid-native is the margin play
   once listening volumes are real. Trade-offs: Play takes 15% of subs (vs
   ~3% Stripe on web), store review, privacy policy (needed anyway; iOS
   would be a separate wrap).
   **PIVOT VALIDATED (2026-07-04): single Kokoro tier, drop Diamond** —
   zero COGS, no key proxy, simpler product. Owner sideloaded sherpa-onnx's
   prebuilt **Kokoro "TTS engine" APK** (k2-fsa.github.io/sherpa → TTS →
   APK engine → kokoro-multi-lang-v1_1, arm64-v8a; Apache 2.0, commercial
   OK), set it as the Android SYSTEM TTS engine (needs Chrome restart for
   its voices to list), and played through PhonoLeaf's Member tier:
   **gaps dropped from 30-45s (browser WASM) to <10s** on ~10-15s
   sentences ⇒ native Kokoro generates FASTER than realtime (~0.5-0.8×).
   The remaining gaps exist only because the system-TTS path synthesizes
   serially with playback (Web Speech can't pre-synthesize); the browser
   Gold tier's existing prefetch (synthesize next chunk DURING playback,
   `_playAudio`/`_preSynth`) closes them to zero once we control synthesis
   directly. **Next step: Capacitor wrapper + native sherpa-onnx plugin**
   — `index.html` unchanged, plugin exposes synthesize(text, voice) →
   audio, wired in as a third Gold backend beside the worker; reuses the
   whole prefetch/fallback pipeline. USB/emulator testing is $0; Play's
   $25 one-time fee only at publication (Apple $99/yr later). Optional
   extra headroom: the kokoro-int8-multi-lang-v1_1 APK (quantized, faster,
   slight quality cost) — untested. Diamond code stays dormant until the
   hybrid ships, then gets removed with the tier dropdown.
   Decision notes:
   - Options considered: (a) better on-device system voices (free, modest,
     user-managed — install higher-quality Google TTS voice data on Android);
     (b) cloud neural TTS via `<audio>` (chosen); (c) in-browser neural TTS
     (Kokoro via WASM/WebGPU — free/offline but ~80MB model + phone CPU/battery
     load; possible future budget tier).
   - **Cost model (neural tier ≈ $15–16/1M chars: Google Neural2/WaveNet $16,
     Azure $15, Polly neural $16; OpenAI tts-1 $15; ElevenLabs ~$100+/1M —
     uneconomical for books; Google/Polly Standard $4/1M but robotic, defeats
     the purpose).** English ≈ ~6 chars/word incl. spaces; a print page ≈
     ~1.6–1.8k chars. Rules of thumb: **~$1 per listening hour**, a ~300-page
     novel (~500–550k chars) ≈ **$8–9 fully synthesized**, 500 pages ≈ ~$14,
     a 1000+-page epic (~2.2M chars) ≈ ~$35. Free tiers (Google 1M chars/mo,
     Azure 0.5M/mo) cover dev/testing but are irrelevant at production scale.
   - **Production architecture: synthesize on-demand per chunk as the user
     listens (reuse the existing block-segment chunking; emit SSML from it),
     never pre-synthesize whole books; cache synthesized audio (IndexedDB
     client-side; optionally server-side keyed by voice+text hash) so re-listens
     are free. COGS ≈ $1/listening-hour drives subscription pricing (a 30 h/mo
     user costs ~$30 — price accordingly or tier voices).**
   - Needs a key-holding proxy (Cloudflare Worker free tier is fine initially —
     see item 5).
4. **Privacy policy + ToS** (required for Google verification & stores).
5. **Backend** for real refresh tokens, payments (Stripe), and a TTS key proxy.

Already hardened for multi-user: XSS escaping of dynamic content.
