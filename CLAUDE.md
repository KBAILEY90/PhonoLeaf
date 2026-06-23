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
- `.github/workflows/deploy.yml` — deploys to GitHub Pages on push to `main`.
- External libs via CDN: **jszip** + **epub.js** (jsdelivr), **Google Identity
  Services** (GIS) for OAuth.

The inline script is organized into plain object "modules":
`CONFIG`, `State`, `App` (auth), `Drive` (Drive API), `Library`, `Reader`,
`TTS`, `VoiceModal`, `ChapterModal`, plus `esc()`/`toast()`/`showView()` helpers.

## How to deploy

Edit `index.html` (or `sw.js`), commit, and **push to `main`**. GitHub Actions
(`deploy.yml`) redeploys to Pages in ~1–2 min. There is no staging branch.

- A **second Claude session has also pushed to `main`** in the past — always
  `git fetch` and check `git log HEAD..origin/main` before pushing to avoid
  collisions, and rebase rather than clobber.
- The service worker is **network-first for the HTML** so deploys show up, but
  bump `CACHE` in `sw.js` (e.g. `koboaudio-v2` → `-v3`) whenever the precached
  asset list changes, to force clients off the old shell.

## How to verify changes

There's no test suite. Before pushing, syntax-check the inline script:

```bash
node -e "const fs=require('fs');const h=fs.readFileSync('index.html','utf8');const m=h.match(/<script>([\s\S]*?)<\/script>\s*<\/body>/);require('vm').compileFunction(m[1]);console.log('JS OK');"
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
- **TTS reads only the currently visible page**, then turns the page via
  `rendition.next()`. `TTS.loadPageText()` extracts text from nodes whose
  on-screen box is inside the viewer (epub.js paginated mode keeps the whole
  chapter in off-screen columns; reading `body.innerText` would grab the whole
  chapter and loop forever — this was a real bug, don't reintroduce it).
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
