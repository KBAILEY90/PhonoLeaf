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
  the app + manifest/sw/fonts/vendor into `www/` — gitignored, generated),
  `android/` (committed Capacitor Android project; build outputs gitignored).
  Loop: `npm run sync` (stage + copy into android) → `npm run open` (Android
  Studio) → Run ▶ on device.
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
    **To converge:** promote the redesign into `index.html`, set
    `APP_SOURCE` back to `'index.html'`, drop the `.green` legal pages from
    that script's `FILES`, and delete `stage-test.js`.
  - **`scripts/stage-test.js`** + `npm run sync:test` — now does the same
    thing `stage-www.js` does (it predates the split, when the redesign was
    only a test page). Kept only so the documented `npm run sync:test` keeps
    working; retire it at convergence.
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
Play Store users get. The **website** still serves the repo-root
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
accessibility pass) is done as of 2026-08-29, not yet device-tested.
`scratchpad`-style working files (`PhonoLeaf Redesign.dc.html`) are a local
reference copy of the Claude Design source, not part of the shipped app.

**Home/Shelves/Player rebuild + sleep timer + ±15s skip (2026-08-25)**: the
first Phase-1 pass was too shallow — it didn't match the actual mockups the
owner reviewed, since the owner tests via the NATIVE APP (`npm run
sync:test` + Android Studio), not the website; pushing to Pages alone proves
nothing on-device. Rebuilt Home (streak header, real-cover hero with
book-wide page/time-left, a playback row quoting the live TTS chunk, "Back
on the shelf", "Next up in {book}"), Shelves (genre-grouped grid, real
covers only — no Spines view, owner explicitly rejected fabricated cover
art), and the reader's chrome (compact top bar + a −15s/prev-chapter/play/
next-chapter/+15s control row, replacing the old page-turn pill). Added two
new features: `TTS.skip(±15)` (seeks within the current audio chunk,
re-synthesizes across a chunk boundary, never crosses a page) and a
`SleepTimer`/`SleepModal` module (drag-dial + quick-pick chips, fades
`TTS._audio.volume` over the final 60s, stops exactly at the next sentence
boundary via a flag both `onended` handlers check — never mid-audio). Full
detail, exact line anchors, and verification notes in `CLAUDE_HISTORY.md`.
**Still only in `index.green.html`** — not ported to the real `index.html`,
not pushed.

**Round-2 device feedback fixes (2026-08-25, same day)**: sign-in page no
longer overflows one screen (dropped the redundant "Read-only, always..."
feature-row block, folded its one new fact — voices run on-device — into
the existing privacy paragraph instead); "may run slower" voice-speed
copy removed everywhere (contradicted the fact that Kokoro/Piper are
already offered based on a device benchmark); every `:hover` CSS rule now
scoped to `@media (hover: hover)` (touch devices have no mouseout, so
`:hover` was getting stuck "on" after a tap — reported on the follow-along
icon, but general to every hover rule in the file); the sleep-timer dial
now uses `setPointerCapture` so the drag keeps following the finger once
it moves off the (small) dial, not just while directly over it; Home
reverted to keep the app icon + "Good {time}, {name}" greeting (removed in
the first pass, which went too far toward the mockup's wordmark-only
header) with the play/expand buttons back on the hero card and the
quoted-sentence excerpt row removed entirely; "Next up" now renders before
"Back on the shelf"; book-wide page/time-remaining figures are gated on
`State._locationsReady` (epub.js's `locations.generate()` completing) —
reading them mid-generation showed a small, growing number that looked
like it was tracking the current chapter instead of the whole book;
Shelves'/Stats'/Settings' page titles now share one exact font/position
(`.lib-header h2` matched to `.home-title`); every underline-style
`<select>` (`.speed-select`/`.set-select`) got a small chevron restored,
since `appearance: none` (added for the button-corner fix above) had also
quietly removed their only "this is clickable" cue. **New: a Book Detail
sheet** (`BookDetail` module, `#detail-modal`), opened by long-pressing a
library cover (matching the design's own gesture spec — a normal tap still
opens the reader directly, unchanged) — cover-color hero, Start
listening/Read, duration-at-rate/chapters/percent-read stats (chapters and
duration only ever known for the currently-open book; blank dash
otherwise, never a fabricated number), description (new: `Meta` now also
captures `dc:description` when present), source (Drive vs this device),
file size, and Mark as finished / Forget this book actions. "Mark as
finished" is a plain manual `pct = 100` — the nuanced version (an
automatic near-100% threshold) is still an open, unscoped product question
per `TODO.md`, so this ships the simplest honest interpretation rather than
guessing at the fancier one. Verified in a browser for every item above;
not device-verified.

**Correction the same day: Book Detail is the primary tap target, not a
long-press.** The design mockup's own gesture ("long-press a cover → peek
detail, normal tap opens the reader") was implemented as designed, then the
owner asked for the opposite: a single tap on a library cover/row now opens
`BookDetail`, and `Reader.open()` only happens from its Start
listening/Read buttons. The long-press timer/pointer-capture machinery this
briefly needed was deleted outright (not left dead) once the tap target
changed — a plain `onclick="BookDetail.open(i)"` needs none of it.

**Prototype-derived polish pass (2026-08-25, same day)**: a real interactive
prototype (`PhonoLeaf.dc.html`, built from PhonoLeaf's own extracted design
system) supplied several concrete, adopted improvements — see
`CLAUDE_HISTORY.md` for the full comparison against what the prototype
deliberately did NOT get adopted (its demo-only mechanics: static-progress
sentence highlighting, ±5%-of-total skip, fake stats). Shipped: **tap** now
reveals a Play/Details overlay on grid-mode covers (`CoverReveal`) —
supersedes the "tap opens Book Detail directly" decision above for grid
cards specifically (table/list rows still tap straight to Book Detail);
genre filter chips on Shelves (`Library._activeGenres`, multi-select, ANDed
with search); Stats' listening chart is now a real 7-day calendar week
(was a 14-day rolling window) with day-letter labels and click-to-inspect;
Home gained a "Continue listening" row (last 3 in-progress books,
deduplicated against the hero) above "Next up"/"Back on the shelf"; the bug
report's photo attachment is now a bordered filename row instead of a bare
`<input type="file">`. Sign-in copy was compared word-for-word against the
prototype and already matched exactly — no changes needed there. **Scope
was explicitly the app only** — the owner deferred `privacy.html`/
`terms.html`/`home.html` (website) to a separate pass.

**Round 3 (2026-08-26)**: a 13-item feedback batch, all done in
`index.green.html` only. Settings row-boundary spacing fixed (`.set-group`
now closes with the same hairline as an in-group row); Log's stat tiles
rebuilt to the prototype's exact 6 (Library/Started/Completed/Minutes this
week/Total minutes/Streak, 2×3 grid); Shelves' "Other" genre is now
actually filterable (chip was silently skipped before) and genre names are
localized (`Meta.genreLabel()`/`GENRE_LABEL_KEYS`); the offline-saved icon
no longer swaps to a checkmark, only its color changes; a finished-book
badge + one-tap "Mark as finished" now live on cover overlays; Home
("Now") rebuilt to the prototype's own structure — greeting, Continue
listening, a 2-tile streak/minutes-this-week row, a new "Still reading"
list — dropping the old hero card, "Next up", and "Back on the shelf"
entirely; the sleep timer sheet lost its 15/30/60 chips (dial-drag is the
only numeric control now), the surviving "End of ch." chip actually
highlights when active, and its dial-center label got its own centered/
smaller-font treatment; "Export my data" now opens an explanatory
`#export-modal` before the existing download runs (deliberately **not**
adding Drive-write API scope — see the module's own comment on why: it'd
force an early CASA re-verification; Android's Storage Access Framework
can already save into Drive with zero new scope if that's wanted later); a
shared `.app-header` (leaf icon + wordmark) now sits above Home/Shelves/
Log/Settings; five real French bugs fixed (`tab_now`/`tab_shelves`/
`library_title` were wrong words, not just informal; `si_feat1_r` said
"envoyé" instead of "téléversé"; the reader's Voice pill had no
`data-i18n` at all). **Also back in scope this round**: `privacy.green.html`/
`privacy-fr.green.html`/`terms.green.html`/`terms-fr.green.html` — new
test-copy siblings rebuilt to the prototype's own layout (serif title,
accent-inverted "short version" box on Privacy only, flat accent-heading
sections, no cards/tables-as-boxes) in both themes, legal text unchanged.
Verified in-browser with mocked `State.books`/`Stats.data` (no real
sign-in in this environment) — every item above confirmed rendering
correctly; not yet device-tested.

**Post-round-3 recovery + real device fixes (2026-08-27)**: `index.green.html`
was found deleted from disk (git had it as `D`) — recovered byte-for-byte
from `www/index.html` (see `CLAUDE_HISTORY.md` for why that's a safe source).
Owner's actual device testing then surfaced 4 real bugs, all fixed: one
dynamic-render `data-i18n` spot that never translates (`I18n.setLang()` calls
`apply()` BEFORE re-rendering Home, so any `data-i18n` span created by that
render is too late) plus three more hardcoded-English spots a full re-scan
caught (voice picker's "Natural"/"Local"/"Online" badges, the Log page's
genre breakdown table never localizing genre names); Home rebuilt again to
actually match `PhonoLeaf.dc.html` (kicker+name split into two lines,
per-card progress bars on Continue Listening, individually-bordered/
accent-numbered stat tiles — same fix applied to the Log page's grid too,
Still Reading rows got real-cover thumbnails); `.scrolly` was missing the
same `min-height:0` fix `.books-grid-wrap` already needed, which is why the
bottom tab bar could get dragged off-screen once a page's content grew past
one screen; Settings' round-3 spacing fix had accidentally added a SECOND
hairline at every group boundary (`.set-group`'s new border-bottom stacking
on top of the next group's already-existing row border-top) — removed.

**Five more device-reported fixes, same day**: Settings' key→sub-label text
was nearly touching (`.sr-sub` margin-top 0.1rem→0.3rem); Home's section
gaps were too tight (`.cover-row` padding-bottom / `.home-stat2`
margin-bottom both increased — measured real visual gaps now 25.6px/28.8px);
the offline-download button and finished-badge were two different sizes
(1.6rem vs 1.3rem) — matched; the persistent header sat in a different spot
on Shelves than the other 3 tabs (its `.app-header` lives outside `.scrolly`,
never inherited that padding — added a scoped rule, verified all 4 views now
land at the identical position); page titles were 1.85rem everywhere,
matching each other but not `PhonoLeaf.dc.html`'s actual 26px (28px for
Home's name specifically) — fixed to the real design-file sizes.

**Settings row layout fix, same day**: the `.sr-sub` margin bump above
wasn't the real fix — owner reported the same complaint again. Re-read the
design file's FULL Settings block (not just the first two rows) and found
Theme/App language are the only two rows meant to be a vertical stack
(label on its own line, then the button row below) — every other row is the
horizontal layout already built here. Added a `.set-row-stack` modifier for
just those two rows. See `CLAUDE_HISTORY.md` also for a measurement lesson:
a `body.style.zoom`'d screenshot exaggerated text wrapping far beyond what
the page actually does at real width — `getBoundingClientRect`-based line
counts at `zoom:1` are what actually diagnosed this.

**Shelves title alignment + Home spacing rebuild, same day**: Shelves'
title was still 7.5px off Log/Settings after the earlier padding fix —
root cause was `.lib-header`'s `align-items:center` centering the title
against a taller sibling (the view-toggle icons), fixed to `flex-start`;
all three now measure identically (57.2px). Home's section gaps got a real
rebuild instead of another nudge: the design file spaces every one of
Home's sections apart with ONE uniform `gap:24px` flex column, not three
different hand-picked margins (which is what had accumulated here over two
rounds — 17.6/25.6/28.8px, all different). Added a `.home-sections` wrapper
with `gap:1.5rem` and an `:empty` rule so a section with nothing to show
collapses out of the gap cleanly; also fixed the stat tiles' padding/number
size/label-gap and Continue Listening's card gap to the design file's exact
px values, found on a full re-read. Verified all three Home gaps now
measure exactly 24.0px.

**Settings key/sub-label spacing, same day — first theory was WRONG, see
next entry**: a cross-renderer `line-height` explanation was proposed and a
speculative top-heavy padding rebalance applied. The real cause was found
immediately after (`.set-group`'s margin-bottom); that rebalance has since
been reverted. Kept here only so the wrong turn is on record.

**ACTUAL Settings fix + five design-file corrections, same day**: the
short-above/long-below asymmetry was `.set-group { margin-bottom }` — each
`.set-row`'s padding lives INSIDE its own box, so at a group boundary the
hairline sat 28.8px below the previous text but only 14.4px above the next.
Set to 0 (the design file has no group concept — one flat list). **Measuring
lesson: to check a divider looks centered, measure hairline→rendered-text on
each side, never element-box to element-box.** Also: Privacy/Terms links
still pointed at the OLD `privacy.html`/`terms.html`, so the redesigned
`.green.html` pages were never reachable — repointed, and added them to
`stage-test.js` so `npm run sync:test` actually ships them; `.lib-header`/
`.lib-search` painted `--surface` while other views' headers sit on `--bg`
(the "white vs beige" inconsistency) — now transparent; Continue Listening
covers 88px → the design file's 120px; Log's 6 stat tiles were wrongly using
Home's larger tile spec (the file sizes Log's smaller) — split apart, tile
block 305px → 189px; `.app-header` margin-bottom 0.9rem → 1.5rem.

**Legal pages wired to the app's theme + header, same day**: the four
`.green.html` legal pages had NO `[data-theme]` blocks and no theme-init
script at all, so they followed only the OS and ignored the app's own
Light/Dark setting entirely. Added the same pre-paint `pl_theme` init and
`[data-theme]` token blocks `index.green.html` uses (Privacy's summary-box
shadow folded into a per-theme `--shadow-ink` token). The `pl_lang` redirect
was already correct — verified, not changed. Header geometry rebuilt to
reproduce the app's own `.app-header` inside `.scrolly` exactly, so the
logo/wordmark and page title land at identical coordinates app-wide
(measured: icon 20.8/17.6/22px, `h1` 66.8px at 26px, matching
`#settings-view` to the pixel).

**Status-bar band + "Your forest", same day**: the legal pages were missing
the status-bar band because they carried `viewport-fit=cover` (draws the
page UNDER the status bar); `index.green.html` omits it deliberately.
Removed from all four, and their single green `theme-color` replaced with
the app's light/dark pair. **New `Forest` module** — the last section on
Home, one tree per started book, grown through six stages by that book's
own progress; a finished book's tree fills solid; tap a tree for its Book
Detail; empty state reads "Read your first book to unlock your forest!".
Library order (not progress-sorted) so trees keep their place and only
grow. **Gotcha found building it**: the `role="button" tabindex="0"`
convention is only safe on HTML elements — an `SVGElement` has no
`.click()` in this WebView, so the delegated Enter handler throws. Trees
are real `<button>`s wrapping an `aria-hidden` SVG.

**"Still growing" removed + forest rebuilt, same day**: that section
rendered garbled on device because it reused the class `.sr-info`, which is
already Settings' circled-"i" button (`border-radius: 50%` + border) and is
defined later, so a wide short wrapper drew as a lens through the title.
**`.sr-` in this file means SETTINGS ROW — don't reuse it.** Section deleted
outright (every started book is in the forest anyway). Forest rebuilt: the
species picker was `id.charCodeAt(0) % 2`, and Drive ids nearly all start
with the same character, so every tree came out identical — now FNV-1a over
the whole id; growth is a continuous scale of the finished tree rather than
six clip-art stages; three species (tiered pine / overlapping-crown
broadleaf / poplar); trunks in `--text-dim`; completion shows as
full-strength foliage instead of outline-vs-fill. With the section gone the
Now page fits one screen.

**3-card overflow + Book Detail stats + description-HTML fix, same day**:
Continue Listening's 3rd card was always clipped (3×120px + gaps = 388px
against a ~340px content width) — cards now size to `calc((100% - 2*gap)/3)`
instead of a fixed px, correct on any width by construction. Book Detail's
"at {rate}×" stat replaced with **Pages** (already-fetched
`Meta.data[id].pages`) and **Length @1×** (real for the open book via
`estimateTimeLeft`'s new 3rd `rate` param; ~estimated from pages for a
closed one) — `.detail-stats` is 2×2 now. New `stripHtml()` fixes
descriptions that embed raw HTML (some epubs' `dc:description` does) —
regex-strips tags first, THEN decodes entities via the `<textarea>` RCDATA
trick (full entity coverage, not a hand-rolled table) — verified inert
against `<img onerror>`/`<script>`/double-encoded-tag adversarial input
(window.alert spied, never fired).

**List-view finished icon + Chapters persists + forest ambience, same
day**: list mode's `.finished-badge` (1.6rem) was absolutely overlaying a
list row's tiny 2.6rem cover, covering most of it — now flows as a normal
trailing icon left of the offline button in row mode only, matching where
that button already lived. Chapters count was gated on the book being the
one CURRENTLY open, which in practice is almost never true from Book
Detail (opened to decide what to read, not from inside a session) —
`Meta.fromBook()` now captures a chapter count from the epub's own
navigation the same way it already captures title/author, so it persists
after the book is closed. Explained (not fixed, not a bug) why Pages and
Description are inconsistent: Pages depends on an Open Library title match
actually having that field, Description is just whatever the epub file's
own `dc:description` happens to contain. **New forest ambience**: a
sun/moon in the corner (theme-driven CSS, Feather icons' own paths) and one
bird/butterfly/bee at a time flying across on a random 14–34s schedule —
`Nav`-managed like `Stats`'/`SleepTimer`'s intervals (arms on Home, tears
down on leaving it), and skipped entirely under `prefers-reduced-motion`.

**Forest: fixed-size canvas + real critter flight, same day**: `.forest`
was a flex row that grew a new line per few extra books, so Home's height
(and whether it fit one screen) depended on library size — now a FIXED
5.75rem canvas; each tree is absolutely positioned (hash-derived x/depth/
lean, semi-random but deterministic per book) instead of flowing in a row,
with narrower slots as the library grows meaning MORE overlap rather than
a taller card — verified 25 synthetic books still fit the same fixed box
with zero tree overflowing it. Trees also lean a little (±8°, pivoting on
their own base) and sit at varied depths in the bottom half, not one
shared baseline. Critter flight fixed properly: each keyframe segment now
carries its own real easing (confirmed by reading `@keyframes` back out of
`document.styleSheets`) and the horizontal-distance fraction at each stop
is offset from that stop's time-% (slow-fast-slow), so ground speed
actually varies; a SECOND independent fast `scaleY` pulse on the inner
`<svg>` (decoupled from the flight-path animation on its parent) is what
reads as the wings beating, rather than one linear slide with a wobble.

**Bird wing-hinge, trees reverted to vertical, butterfly/bee hover-pause,
same day**: the whole-svg `scaleY` pulse above doesn't work for the bird
(a static double-arc silhouette, not a shape a uniform squash reads as
"flapping") — bird SVG split into two `<g class="wing-l">`/`<g
class="wing-r">` groups hinging in sync at a shared pivot point, animated
by their own `bird-wing-l`/`bird-wing-r` keyframes; the `scaleY` flutter
stays but is now scoped to butterfly/bee only. **Reverted the ±8° trunk
lean from the previous entry** — "grow diagonally" was about trees'
POSITIONS relative to each other (near/behind, bundled up), not individual
trunks tilting, which just read as trees falling over; `.forest-tree-btn`
is `scale()`-only again. New `--fly-fx1..5` CSS custom properties (with
CSS-side fallback defaults) carry the horizontal-distance fraction owed at
each of the 5 flight keyframes; for butterfly/bee only (never bird), JS
now has a ~55% chance of freezing one adjacent pair equal, i.e. a real
mid-flight hover — dy/rot for that stop still vary (a small idle bob), and
the wingbeat animation is fully independent so wings never stop moving
during a pause. **Also found and fixed while verifying this**: `Home.render()`
(called from many places, including a cover finishing its async load)
replaces `#home-forest`'s whole innerHTML, silently detaching a mid-flight
critter's `<div>` without going through `stopCritters()` — `_critterEl`
was left pointing at that dead node forever, and `_spawnCritter()`'s
one-at-a-time guard checked only truthiness, so once this happened no
critter could ever spawn again for the rest of that Home visit. Guard now
checks `_critterEl.isConnected`. Verified in-browser (mocked `State.books`/
`State.progress`, forced `Math.random` sequences to pin species/pause):
trees render `scale()`-only at multiple progress levels; a forced bird
spawn gets both wing `<g>` groups and the plain unfrozen fx curve; a
forced butterfly spawn gets a frozen adjacent fx pair plus a longer flight
duration, with its `<svg>`'s flutter animation independently confirmed
running via computed style. Not yet device-tested.

**Erase-modal confirmation word now localizes + matches its own button
(2026-08-27, same day)**: the type-to-confirm word in `EraseModal` was
hardcoded English "ERASE" regardless of app language (not a French word at
all in French mode) and didn't even match the English Settings row button
that opens it ("Delete"). New `erase_word` i18n key (DELETE/SUPPRIMER) is
now what's both shown (bolded in the hint) and checked against, aligned
with the existing `delete` key's button text in each language; the input's
aria-label is now translated too (`erase_input_aria`). Verified in both
languages: French mode shows/accepts "SUPPRIMER" and rejects "DELETE";
English unchanged behavior, now literally reading "DELETE" instead of
"ERASE".

**Tour spotlight no longer bleeds into page content behind the tab bar +
more breathing room under the logo/wordmark (2026-08-27, same day)**:
`Tour._reposition()`'s spotlight hole is now clamped to its target's own
parent (bar/row) rect, not just padded outward from the target itself —
the tab bar sits flush against Home's scrollable content, so the old fixed
`pad` could poke past the (opaque) tab bar's edge and reveal a sliver of
real Home content through the tour's transparent hole. `.app-header`'s
`margin-bottom` (the space under the leaf icon + "PhonoLeaf" wordmark,
shared by Home/Shelves/Log/Settings) went from 1.5rem to 2.1rem, matched
in all four `.green.html` legal pages' equivalent `.topbar` rule. Verified
Home and Log both still render with zero scroll overflow at 375×812.

**Forest empty-state copy, colored sun/moon, em-dash sweep, and every page
title aligned to Home's name (2026-08-27, same day)**: forest's empty-state
line changed to "Start reading a book to grow your first tree!" (EN) /
"Commencez un livre pour faire pousser votre premier arbre !" (FR) — names
the action (read) and the payoff (a tree), not "unlock." `.forest-sun`/
`.forest-moon` now have their own explicit colors (warm gold `#E4A63B` /
cool silver-blue `#AEB9D6`) instead of inheriting the ambient `--text-dim`
grey. Swept every user-facing string in `STRINGS.en`/`STRINGS.fr` (~60
lines) plus 4 matching HTML fallback strings for the em dash — the one AI
writing tell flagged before — replaced per-sentence with a period, comma,
colon, or "so"/"donc" depending on what actually reads naturally; code
comments (which lean on em dashes constantly and aren't user-facing) were
deliberately left alone. **All page titles — Home/Shelves/Log/Settings and
all 4 legal pages — now sit at the exact same height as Home's own name**,
not the height of Home's kicker line one row above it (the previous
"aligned" state): `.home-title`'s bare use (Settings/Log) and `.lib-header`
both needed the space added as `padding-top`, not `margin-top` — they sit
as plain block siblings directly under `.app-header`, and adjacent block
siblings' margins COLLAPSE to the larger one rather than summing, which is
why a margin-based fix here silently did nothing. Verified all four land
within 0.06px of each other at 375×812, including both legal pages
(English and French).

**Flagged, not changed**: the actual legal PROSE inside `privacy.green.html`
(and the real, live `privacy.html`) uses the same dash-heavy sentence style
throughout its substantive clauses. That content is carried over verbatim
from the live page per this project's own "legal text unchanged" rule
while a lawyer review is still pending — rewriting clause wording for
style is a bigger, separate decision than the UI-copy sweep above, so it
was left alone and raised with the owner instead of touched unilaterally.

**Page-title alignment fix #2: same font-size everywhere, not just same
position (2026-08-27, same day)**: the owner still saw a difference
switching tabs after the position fix above. Root cause: Home's actual
name rendered a "hair larger" than every other page's title (28px vs
26px, a deliberate design-file match from an earlier round) — even with
identical box positions, a different font-size makes the glyphs sit
differently within their own line box, which can look like a misalignment
even when the box math is exact. Dropped the 28px override entirely;
Home's name is now 26px, identical font/line-height/box to Shelves/Log/
Settings/Privacy/Terms. Verified via `Range.getBoundingClientRect()` (the
actual glyph box, not just the CSS box) that all five now match top AND
bottom to within 0.05px.

**Book Detail: swipe-back closes the sheet, Listen opens+starts the reader,
Read button removed; new mini player bar on every tab (2026-08-27, same
day)**: `BookDetail.open()` now pushes its own history entry, so Back/
edge-swipe closes the sheet instead of silently navigating whatever tab
sits behind it (the global `popstate` handler checks for it before its
usual tab/reader logic). The sheet's two buttons are now one: "Listen"/
"Écouter" (renamed from "Start listening"/"Commencer l'écoute", still
`Reader.open(i)` → auto-starts) — "Read" (opened the reader without audio)
is gone, along with `BookDetail.read()`. **New `MiniPlayer`**: a condensed
reader control row (±chapter, play/pause, speed, sleep, voice — no title/
cover, matching what was asked for) fixed just above the tab bar, shown on
every tab whenever `#reader-view` carries its `minimized` class (a live
background session, whether from `Player.play()` or backing out of the
full reader) and hidden the moment the full reader is showing. Tapping the
bar (not one of its own controls) calls `Reader.expand()` — promotes the
existing session in place, never re-opens/reloads it. `TTS.setRate()` now
syncs every `.speed-select` in the DOM, not just the reader's own, so the
mini player's independent select never drifts from the reader's.

**Tour spotlight pauses around a modal that opens mid-tour + equal-width
Play/Details buttons (2026-08-27, same day)**: `Tour`'s own "nothing else
is open" guard only checks once, right before starting — LangPacksModal in
particular can still open moments later (nothing awaits its own trigger
finishing, see its comment), landing the tour's spotlight hole on/behind
that modal's opaque sheet instead of the real tab bar (reported: a black
box, target invisible). `Tour._watchModals()` now observes every modal for
the rest of the app's life and pauses/resumes the tour around whichever
one opens, if a tour happens to be showing — cheap, one attribute-only
`MutationObserver` per modal, bound once. Fixing this exposed a real
latent bug it would have made worse: `Tour._end()` never cleared `_steps`,
so "is a tour active" was only ever reliable while one was actively being
driven step-by-step — harmless before, but exactly the flag the new
modal-watcher needs to stay accurate hours after a tour finished; now
cleared. Also: `.cr-play`/`.cr-details` (a cover's tap-reveal overlay)
now share a `min-width` — different label lengths ("Play" vs "Details",
worse in French) made them visibly different sizes stacked on top of each
other.

**Play opens the full reader (not just a mini session), explicit expand
icon on the mini player, its speed control restyled, Privacy's dark
summary box darkened (2026-08-27, same day)**: `Player.play()` (a cover's
tap-reveal Play button) now calls `Reader.open(i)` — full mode, arms
auto-start — instead of `Reader.open(i, 'mini')`; owner-reported it
"didn't open the reader page, it only created a hero." The mini player is
now strictly what appears once you back OUT of the reader, never what
Play opens directly into; `Reader.open()`'s now-unused 'mini' branch is
left in place (still how a background session STAYS laid out after
`Reader.minimize()`), just no longer reachable from Play. Added an
explicit `.mp-expand` icon button to the mini player (an up-chevron) —
tapping anywhere else on the bar already expanded to the full reader, but
that wasn't discoverable enough on its own. The mini player's speed
`<select>` no longer uses the reader's own underline style — sitting
directly above the tab bar's own active-tab green top-border, the two
lines read as a rendering glitch ("it looks weird with two lines"); it's a
bordered pill now, matching the Voice button next to it. **Privacy's dark-
mode "short version" box**: it filled with dark mode's `--accent`, which
is a BRIGHT light green meant for text on a near-black page, not a fill —
glaring next to everything else in dark mode being muted. Now a dedicated
deep-green fill in dark mode specifically, with the theme's light text
token instead of `--bg` (which is near-black and would've gone invisible
on a dark fill). Caught and fixed a real miss on the first pass here: a
bare `@media (prefers-color-scheme: dark)` override with nothing to beat
it back also wins when the OS is dark AND the app is explicitly set to
Light — needs a `[data-theme="light"]` restatement at matching specificity
to correctly lose to a forced-light override, the same three-way pattern
(`@media` / `[data-theme="dark"]` / `[data-theme="light"]`) the token
blocks at the top of these files already use for exactly this reason.

**Mini player speed control: text/chevron color to match Voice, same day**:
`.mp-speed` had its bordered-pill shape but was still inheriting
`.speed-select`'s green `--accent` text color — added `color: var(--text)`
(the chevron is `stroke="currentColor"`, so it followed for free), matching
`.mp-voice` exactly.

**Forest moon color, same day**: the cool silver-blue (`#AEB9D6`) still
read as flat grey (owner-reported) — a real night-sky moon is warm ivory,
not cool-toned, and glows rather than sitting flat. Now `#F3E7C4` with a
matching-color `drop-shadow` blur as a soft halo.

**Desktop column + native/web split (2026-08-28)**: the app had NO desktop
layout at all (before this, the only `min-width` queries in either index
file were landscape-phone sign-in tweaks), so phonoleaf.com on a laptop
rendered the phone layout stretched full-bleed. Rather than build and
maintain a second desktop layout, large screens now get the SAME layout
capped to a centred **480px** column (`--app-max`), hairline-edged, with
`.tab-bar`/`.mini-player` re-anchored to centre (they are `position:fixed`
with `left:0;right:0`, so `max-width` alone would have left them pinned
left) and modal sheets constrained to the column while their backdrops stay
full-bleed. Gated `(min-width: 700px) and (min-height: 600px)` — the
min-height is load-bearing: a phone in landscape is wide (~930px) but short
and must keep the full-bleed layout, the same width+height pairing the
sign-in screen's own landscape rules already use. Applied to **both**
`index.green.html` and the live `index.html` (it fixes a pre-existing
website bug, and is desktop-only so phone rendering is untouched).
**Also: `stage-www.js` now stages `index.green.html` as the native app**,
so Android ships the redesign while the website stays on the old
`index.html` — see the native/web split under Tech stack, and the two
separate deploy paths below.

**Storage-modal spacing/sort + finish-flow fixes (2026-08-30)**: Storage
popup's book/pack/cover groups now use `.set-section-label`'s spacing
(same fix Settings already got) and the size/A-Z sort toggle is gone —
cached books always sort by size, no user-facing control. Reaching the
true end of the book while listening now calls `Reader.close(true)`
(closes the reader back to the previous tab) instead of just stopping
playback in place; `StoreReview.maybeAsk()` moved to fire from inside
`Reader.close()` only when `finished` is true, i.e. after the reader has
actually closed from a genuine finish — and it no longer fires from
`BookDetail.markFinished()` (manual mark), per owner feedback that a
manual mark isn't the moment the prompt is for. Not yet device-tested.

**Storage modal boxed groups (2026-08-30, same day)**: owner asked that any
grouped-rows screen look like Settings, not just Settings itself. Storage's
Cached books/Voice packs/Cover images sections now each wrap in `.set-group`
(the same bordered card Settings uses), with each group's first row on
`.set-row-first` to drop its top hairline — previously the rows sat as a
bare unboxed list under the heading. Not yet device-tested.

**Storage modal per-item percentages (2026-08-30, same day)**: each cached
book/pack/cover row's size now also shows its share of the grand total
(`StorageModal._sizeWithPct`, e.g. "12 MB · 34%"), skipping the percentage
entirely when nothing is cached yet (avoids a meaningless 0%/NaN%). Not yet
device-tested.

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
  2026-08-29 entry. Not yet device-tested.
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
  account / GST-QST registration / Stripe account, in that order.
- See `TODO.md` for the actively-maintained task list (this section is a
  snapshot, `TODO.md` is the live source for "what's next").
