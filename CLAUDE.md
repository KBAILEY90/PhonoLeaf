# CLAUDE.md

Guidance for working in this repository.

## What this is

**PhonoLeaf** (formerly KoboAudio) — a mobile-first PWA that reads your epubs
aloud. It connects to Google Drive (read-only), lists epub files from a folder,
renders them with epub.js, and reads the text using the browser's Web Speech
(TTS) engine.

- Live: **https://phonoleaf.com/** — custom domain, **fully cut over 2026-07-26**
  (DNS live, HTTPS enforced, certificate issued). Moved off `github.io` because
  it **cannot** be used for Google OAuth verification (that needs a DNS-level
  TXT record; see `VERIFICATION.md`). Set via the root `CNAME` file; DNS is at
  **Cloudflare, DNS-only / grey-cloud (NOT proxied)** — 4×A
  (`185.199.108-111.153`), 4×AAAA (`2606:50c0:8000-8003::153`), `CNAME www` →
  `kbailey90.github.io`.
  Pages now serves at the domain ROOT, not the `/PhonoLeaf/` repo path —
  harmless because `manifest.json`'s `start_url` (`./index.html`) and the
  service-worker registration (`./sw.js`) are both RELATIVE, so PWA scope
  follows the new path automatically.
  **Verified live 2026-07-26:** `https://kbailey90.github.io/PhonoLeaf/` now
  returns a **301 to `https://phonoleaf.com/`**, and `home.html` / `privacy.html`
  / `terms.html` all serve correctly over HTTPS with their links repointed.
  The old `kbailey90.github.io` **JS origin and authorized domain are
  deliberately KEPT** (on both the Web OAuth client and the consent screen) so
  existing installs keep working — do not "tidy" them away.
  NB browser storage is per-origin: web users from the old URL will look
  signed-out and lose local progress/stats. Native is unaffected.
- Repo: https://github.com/KBAILEY90/PhonoLeaf
- Status: **production-bound (decided 2026-07-03)** — no longer a personal-use
  app; the owner intends to take it to production "very soon". Treat changes
  accordingly (multi-user assumptions, security, cost awareness), and keep the
  "Productization roadmap" below current — it is now the active work plan, not
  an exploration.
- **Brand vs. infra (post-rename, 2026-06-28):** branded **PhonoLeaf**, and the
  GitHub repo + GitHub Pages path were renamed `koboaudio` → `PhonoLeaf` (Live is
  now `kbailey90.github.io/PhonoLeaf` — case-sensitive, see above). **No OAuth
  change was needed:** the
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
  - ~~The Cloud Console OAuth consent screen App name still said "KoboAudio"~~
    — **SUPERSEDED 2026-07-22**: rather than keep patching the old
    `koboaudio`-named project's branding, the owner had the whole project
    replaced outright (see below) with a project named "PhonoLeaf" from the
    start. No more "KoboAudio wants to access your Google Account" prompt.
  - The owner's local clone folder (`C:\Repo\koboaudio`) must be renamed to
    `C:\Repo\phonoleaf` manually (documented in TESTING.md §3) — note that
    renaming it detaches this project's Claude session history/memory, which
    is keyed to the folder path.
  - **REPLACED, not just re-branded (2026-07-22): the underlying Cloud Console
    project itself was `koboaudio` — renaming consent-screen branding wasn't
    enough for the owner, who wanted zero trace of the old name anywhere, not
    just in what users see.** A brand-new GCP project named **PhonoLeaf**
    (project id `phonoleaf`) was created from scratch (project IDs can't be
    renamed) via a Cowork prompt (Drive API enabled; OAuth consent screen
    External/Testing/test-user configured; new Web + Android OAuth clients),
    and `CONFIG.CLIENT_ID`/`CONFIG.ANDROID_CLIENT_ID` in `index.html` +
    `AndroidManifest.xml`'s reversed-scheme `oauth2redirect` intent-filter were
    all repointed to it — see the "Critical facts" section below for the
    current IDs. Nothing else in the codebase referenced the old project
    (confirmed by grepping the whole repo for `kobo`, case-insensitive — the
    only hits were the already-correct `CoverCache.migrate()` IndexedDB-rename
    code, whose entire job is deleting the old `koboaudio` DB).
    - **Real blocker hit + fixed**: (package name, SHA-1) must be globally
      unique across EVERY GCP/Firebase project, not just within one, so
      creating the Android client on the new project failed outright until
      the OLD project's Android client (holding the same `com.phonoleaf.app` +
      debug-SHA-1 pair) was deleted first. Only that one credential was
      removed from the old project — its Web client and consent screen were
      left untouched so old installs' web sign-in kept working through the
      transition. Expected/accepted side effect: native sign-in on the OLD
      project broke the moment that credential was deleted — fine, since the
      native app was about to be rebuilt against the new project anyway.
    - **"Enable custom URI scheme" was checked under the new Android client's
      Advanced Settings** (owner-confirmed) — required, off by default, and
      this exact gotcha already cost a debugging session on the original
      `koboaudio` Android client (see the Native auth note below) so it was
      verified explicitly this time rather than assumed.
    - **STATUS: MIGRATION COMPLETE (2026-07-22).** Native and web sign-in both
      verified working on the new `phonoleaf` project; the old `koboaudio`
      project has been shut down via Cowork (confirmed: project number
      871446308528, now under Resources pending deletion with the standard
      ~30-day recovery window before permanent deletion; `phonoleaf` was
      confirmed untouched throughout). No "kobo" trace remains anywhere —
      code, branding, or infrastructure.
    - A **third** Android OAuth client (release keystore's SHA-1, once one
      exists) will still be needed later for the Play Store build, on this
      new project — same requirement that would have existed on the old one.
    - The owner's Drive folder is separately named "Rakuten Kobo" (their own
      Drive data, unrelated to this repo/GCP project) — renaming it is a
      Drive-side action the owner can do anytime; the app stores the folder's
      *id*, so renaming it doesn't break anything, though the cached display
      name in Settings stays stale until the folder is re-picked.

## Tech stack & structure

- **Pure HTML/CSS/JS, no build step** for the web app itself. Almost
  everything lives in a single file: `index.html` (markup + styles + inline
  `<script>`).
- `sw.js` — service worker (offline app shell).
- `manifest.json` — PWA manifest.
- `privacy.html` / `terms.html` — standalone legal pages (own branded HTML
  shell, not part of the inline-script app) — see the Productization roadmap
  "Privacy policy + ToS" item for what they cover. Staged into the native
  shell by `scripts/stage-www.js`; linked from the Settings footer.
- **Native shell (Stage 2a, added 2026-07-06):** Capacitor 8 wraps the SAME
  web app for the Play Store build. `package.json` (scripts only — the web
  app still has no build), `capacitor.config.json` (`com.phonoleaf.app`,
  webDir `www`), `scripts/stage-www.js` (copies index.html/manifest/sw/fonts
  into `www/` — `www/` and `node_modules/` are gitignored, generated),
  `android/` (committed Capacitor Android project; its build outputs are
  gitignored; Gradle configuration cache is enabled in gradle.properties).
  Installed Capacitor plugins: `@capacitor/browser` + `@capacitor/app`
  (native auth — see the "Native auth" behavior note); `CapacitorHttp` is
  used from core. `@jofr/capacitor-media-session` (tried for Stage 4
  background/lock-screen playback) was **REMOVED 2026-07-22** — it crashed
  the app ~1-2s after pressing play (Capacitor-6-era plugin vs `targetSdk 36`
  FGS rules) and was replaced by our own `PlaybackService.kt` (see the
  "Background playback" behavior note for why and how). Removing it also
  deleted `patches/@jofr+capacitor-media-session+4.0.0.patch` and `.npmrc`
  (`legacy-peer-deps=true`) — both existed solely for this plugin's AGP-9
  incompatibility and peer-dependency conflict with Capacitor 8; `npm install`
  verified to resolve cleanly without either. `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
  + `POST_NOTIFICATIONS` in the manifest are still required (Android 14+ FGS
  rules) — that's for our own `PlaybackService`, not this plugin.
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
  **MODEL SIZES + the Play Store problem (measured 2026-07-29).** Piper/VITS is
  **one model per accent**, and they are big: `kokoro/` (US) = **78.5 MB**,
  `kokoro-gb/` (GB) = **77 MB**, ~**185 MB** total with espeak-ng-data. They
  are also stored uncompressed (`noCompress += ['onnx','bin']` in
  `app/build.gradle`), so nothing shrinks in the APK. That is at/over Google
  Play's app-bundle download ceiling (~200 MB) *before app code*, and a ~200 MB
  install is a bad first impression regardless of whether it technically fits.
  Every future language would add another ~75–80 MB — this does NOT amortise
  the way the web model does (see below).
  **SUPERSEDED 2026-08-04 — NOTHING is bundled now, not even US. See "FULLY
  UNBUNDLED" below;** the decision recorded next (ship US, download the rest)
  was the first step and lasted less than a day.
  **DECISION (owner, 2026-07-29): ship US ONLY in the store build; GB and any
  future languages become in-app downloadable voice packs. IMPLEMENTED
  2026-08-04** (started right after OAuth verification was submitted, per the
  deferral above). `kokoro-gb`/`-fr`/`-de`/`-es` are no longer looked for in
  assets at all — `PhonoLeafTtsPlugin.ensureReady()` treats any model in a new
  `VOICE_PACKS` map as download-only and throws a distinguishable
  `PackNotDownloadedException` ("PACK_NOT_DOWNLOADED:<model>") if its filesDir
  copy isn't there yet, rather than trying (and failing) to copy it from
  assets. Three new `@PluginMethod`s: `packStatus({model})` →
  `{downloaded, approxBytes}` (checks the same `.ready-$MODEL_VERSION` marker
  `ensureReady` already used, so there's a single source of truth — nothing
  duplicated into `localStorage`); `downloadPack({model})` streams the pack's
  `.tar.bz2` straight from the **same public sherpa-onnx GitHub release URL**
  the US model already ships from (TESTING.md §3.6) — no separate hosting —
  extracting via **Apache Commons Compress** (`org.apache.commons:commons-compress:1.28.0`,
  new Maven Central dependency; `java.util.zip` has no bzip2 support) into a
  scratch `-tmp` dir that's only swapped in on full success, so a
  failed/cancelled download can never leave a half-written folder that
  `ensureReady` later treats as ready; emits `packProgress` events
  (`{model, downloaded, total, pct}`, throttled to 5/sec) via the plugin's
  existing `notifyListeners` mechanism (same one `mediaButton` already uses).
  `deletePack({model})` reclaims the space.
  **Catalog, launched with GB then expanded the same day to 4 packs**: British
  English (`gb`, multi-speaker vctk, 4 owner-audition-picked voices, 80488085
  bytes) plus French (`fr`, `fr_FR-siwis`, 67207459 bytes), German (`de`,
  `de_DE-thorsten`, 67214254 bytes), Spanish (`es`, `es_ES-davefx`, 67184952
  bytes) — sizes are exact `.tar.bz2` asset sizes read live from
  `gh release view tts-models --repo k2-fsa/sherpa-onnx`, not estimates. The
  three new ones are **single-speaker** models (no sid audition needed, unlike
  US/GB) picked as the most standard/well-known community Piper voice per
  language — but **NOT owner-audited for quality or gender** (no device in
  this environment); `PIPER_VOICES` labels them generically ("French voice"
  etc.) until that happens, same as the US/GB set was before its own audition
  round.
  **JS side (`VoicePacks` module, `index.html`):** owns the status/progress
  cache. Settings shows a single **"Language packs"** row (no status text on
  the row itself, by owner request — status lives in the popup) with a
  **Downloads** button → **`LangPacksModal`**, a popup listing every catalog
  entry with its own action (size before download, live progress + Cancel
  while downloading, Remove once downloaded) — a single summary row was chosen
  over one Settings row per pack (the first cut) specifically so it scales as
  more languages are added, per owner feedback. `VoicePacks._notify()` keeps
  Settings and whichever of `VoiceModal`/`LangPacksModal` is open in sync as
  downloads progress, without either modal polling. Also gates `VoiceModal`: a
  voice whose pack isn't downloaded shows locked with "tap to get it" instead
  of being selectable, so a user can't pick a voice that then silently fails
  mid-chapter. **The one real correctness trap this uncovered**:
  `TTS._playAudio`'s existing catch-all counted ANY native-synth failure
  toward a 2-strikes-and-disable-Kokoro-for-the-session counter — so hitting
  `PACK_NOT_DOWNLOADED` (e.g. a stale `pl_voice_piper` choice from before this
  feature existed, on a fresh install) would have wrongly killed the natural
  voice ENTIRELY, including the always-bundled US model, over nothing more
  than one missing pack. Fixed by special-casing that message prefix before
  the strike-out check: it falls back to the device voice for just that
  chunk, resets the persisted voice choice back to the model's first
  (always-available) voice so later chunks don't keep re-hitting the same
  wall, and points the user at Settings — without touching
  `_kstrikes`/`_kokoroDead`. **Not device-verified** — same caveat as every
  other native change in this file: no JDK/Android SDK in this environment.
  `index.html`'s JS syntax-checked clean; the Kotlin was reviewed carefully
  against Commons Compress's documented `TarArchiveInputStream`/
  `BZip2CompressorInputStream` API but not compiled.
  **Labels renamed 2026-08-04 (owner request):** "British English"/no US
  entry → **`{model:'us', label:'English (US)'}`** added to `CATALOG` and
  `{model:'gb'}` relabeled to **"English (UK)"**, so the popup shows the
  complete picture (bundled + downloadable) rather than only the optional
  downloads.
  **US made removable the same day (owner request): "it should come in by
  default, but the user can remove it to make some space if they don't use
  it."** `deletePack` no longer special-cases "us" out — it's now allowed
  alongside any real `VOICE_PACKS` entry. This is safe specifically because
  "us" **isn't a real download**: the APK's `assets/kokoro/` copy is
  unaffected (baked into the install, can't be freed without uninstalling),
  but `ensureReady()` also keeps a SEPARATE full copy in `filesDir` (native
  code needs real filesystem paths for espeak-ng-data etc., not an
  AssetManager stream) — deleting THAT copy genuinely frees ~78 MB. `packStatus`
  now checks "us"'s real `filesDir` marker too instead of hardcoding
  `downloaded:true`. In the Language Packs popup this reads as
  "Included"/"Remove" when present and "Not installed"/"Reinstall" when not.
  **First cut had a real bug, caught by the owner on-device: "us" still
  doesn't get removed.** Root cause: `ensureReady()` had NO way to tell "us"
  was never installed apart from "us" was JUST explicitly removed — both look
  identical as "marker file missing" — so it unconditionally treated either
  case as "first launch, bootstrap it" and silently re-copied the model from
  assets on the very next `synthesize()` call (often within seconds, since
  that's just the next sentence being read), making Remove look like it did
  nothing. Fixed with **`removedMarkerFile(model)`**, a sentinel `deletePack`
  writes for any non-`VOICE_PACKS` model ("us"): `ensureReady()` now throws
  `PackNotDownloadedException` (same as a real undownloaded pack) whenever
  that marker is present, instead of falling through to `copyAssetDir`. Only
  a NEW dedicated **`reinstallPack({model})`** method clears the marker and
  re-copies — deliberately NOT folded into `prepare()`, because `prepare()` is
  ALSO called implicitly at TTS startup (`TTS._modelReady()`) to detect the
  loaded model family, and that implicit call must never silently undo a
  deliberate removal just because the user pressed play.
  `VoicePacks.download('us')` calls `reinstallPack`, not `prepare` /
  `downloadPack` — shown as "Installing…" with no Cancel button, since a local
  asset re-copy has no percent/URL and nothing meaningful to interrupt.
  **THE REAL CONSTRAINT — the US model is stored TWICE, and only one copy is
  deletable (surfaced 2026-08-04 after the owner pushed back twice on "US
  doesn't get removed like the others").** Two earlier replies explained the
  *symptom* (the "Reinstall" label, the missing percentage) without stating
  the underlying architecture, which is the part that actually matters:
  - **Copy A — `assets/kokoro/` inside the installed APK, ~78.5 MB.** Stored
    uncompressed (`noCompress += ['onnx','bin']`). **The app can NEVER delete
    this** — it's part of the install package; only uninstalling removes it.
  - **Copy B — `filesDir/kokoro/`, ~78.5 MB.** `ensureReady()` copies A→B on
    first use because the native engine needs real filesystem paths (espeak-ng
    in particular uses ordinary file I/O and cannot read through
    `AssetManager`). **This is the only copy `deletePack` can remove.**
  So a US-only install sits at **~157 MB**, and "Remove" frees ~78 MB of that
  — real, but only half. The remaining ~78.5 MB is stuck for the life of the
  install. `deletePack('us')` is NOT broken and does not special-case "us";
  the asymmetric UI is a *consequence* of this, not the cause: there's no
  `Content-Length` to show a percent against, and "Reinstall" was chosen over
  "Download" because nothing is fetched from a URL.
  **Owner's own diagnosis was the right one: "can't we delete one of them and
  redirect processes to the remaining one?"** Yes — but only in one direction:
  - Drop Copy B, run from Copy A → **fails**, both technically (espeak-ng
    can't read from assets) and on intent (assets are undeletable, so the
    feature the owner wants disappears entirely).
  - **Drop Copy A — i.e. stop bundling the US model in the APK and make "us" a
    real `VOICE_PACKS` entry** pointing at the same GitHub release URL the
    other four already use (`vits-piper-en_US-libritts_r-medium.tar.bz2`,
    82038311 bytes). Then there is exactly ONE copy, in `filesDir`, and it is
    fully deletable — identical Download/Remove/% UI to every other pack.
    `removedMarkerFile`/`reinstallPack` and all the "us" special-casing in
    `VoicePacks`/`VoiceModal` would be **deleted**, not extended.
  Cost: a fresh install ships with no voice, so first launch downloads ~80 MB.
  **In practice this is not a new constraint** — the app already requires
  network on first run (Google sign-in, Drive listing, downloading the book
  itself), and the device voice already covers any gap via the existing
  fallback. Play Store download would drop from ~80 MB to roughly 15 MB.
  **Alternative worth revisiting once actually on Play: Play Asset Delivery.**
  A `fast-follow`/`on-demand` asset pack is extracted to a real filesystem
  path (so no duplicate copy AND no `AssetManager` problem), installs
  automatically alongside the app, and **is** deletable via
  `AssetPackManager.removePack()` — the only option that gets "ships with the
  app" *and* "fully deletable" *and* "no first-run wait" simultaneously. Not
  chosen now because asset packs only work for Play-installed builds (the
  current sideload/debug flow can't properly test them) and it needs a
  separate Gradle asset-pack module plus a different API.
  **FULLY UNBUNDLED — APPROVED AND IMPLEMENTED 2026-08-04.** The owner chose
  the "drop Copy A" option above ("let's do the unbundling, and add a step to
  the onboarding"). `assets/kokoro/` is no longer shipped or read; **"us" is
  now an ordinary `VOICE_PACKS` entry** (`vits-piper-en_US-libritts_r-medium.tar.bz2`,
  82038311 bytes) and behaves exactly like every other pack.
  - **Folder key stays `kokoro`, NOT `kokoro-us`, deliberately** — an existing
    install that already has the old asset-copied model in `filesDir/kokoro`
    keeps its valid `.ready-$MODEL_VERSION` marker, so `packStatus` reports it
    already downloaded and nobody re-fetches ~80 MB just because bundling
    stopped. `MODEL_VERSION` was likewise left alone for the same reason.
  - **Net deletion, not addition:** `removedMarkerFile()`, `reinstallPack()`,
    `copyAssetDir()`, the `AssetManager` import, the "explicitly removed"
    branch in `ensureReady()`, and every `model === 'us'` / `isUs` special case
    in `VoicePacks`/`VoiceModal`/`rowHTML` are all **gone**. `ensureReady()`
    now simply throws `PackNotDownloadedException` whenever the marker is
    missing, for any model.
  - **There is no longer an always-available voice.** `VoiceModal` gates every
    voice on its pack (no `us` exemption) and shows a "No natural voices
    installed yet" empty state above the "Get more voices" row. The
    `PACK_NOT_DOWNLOADED` handler in `TTS._playAudio` no longer assumes it can
    fall back to "the model's first voice": it now switches to a voice from a
    pack that is *actually downloaded*, and if none is installed, shows
    **`NoVoiceModal`** — see the follow-up fix below for why that's a modal and
    not a toast. The device-voice fallback still covers playback either way,
    and this path still must NOT touch `_kstrikes`/`_kokoroDead`.
  - **Owner-reported the same day, reading with no pack installed: "a message
    that seemed to imply it can't work... cropped on the left and right, I
    couldn't see the whole message."** Two separate bugs, both in how that
    condition was surfaced:
    1. **`#toast` had `white-space: nowrap` and no width cap.** Built for
       short pills ("Voice: Ava"), it silently ran off both edges of the
       screen for any longer sentence — and several existing toasts (e.g. the
       Kokoro strike-out message) were already long enough to be at risk, this
       was just the one that got noticed. Fixed generally: `white-space:
       normal` + `max-width: min(88vw, 22rem)`, so every toast in the app wraps
       and stays on-screen instead of only patching this one message.
    2. **The message could also repeat every single chunk.** Nothing about
       "no pack installed" resolves itself between sentences, so the toast
       fired on every chunk for as long as playback continued — a wall of
       identical toasts, not just one cropped one. Replaced with
       **`NoVoiceModal`**, a proper darkened `.confirm-backdrop` overlay
       (matches `VoiceInfo`'s pattern) shown **once per session**
       (`TTS._noVoiceShown`), with a "Download a voice" button straight into
       `LangPacksModal`. The "switched to an installed pack instead" case is
       unaffected (still a toast — it only ever fires once per switch anyway,
       since subsequent chunks succeed on the newly-installed pack).
    **First cut wrongly claimed this doesn't block reading ("the device voice
    keeps going regardless") — owner tested it and confirmed the opposite:
    "the reader won't work without a voice," and removed the "Continue
    reading" button that promised otherwise.** Correct and important:
    `_speakWeb`'s device-voice fallback (`window.speechSynthesis`) **only
    exists on the web build** — Android's WebView has no Web Speech API at
    all, so `_speakWeb` on native hits `if (!window.speechSynthesis) {
    this.stop(); return; }` and stops immediately. Since `NoVoiceModal` is
    only ever triggered by the native plugin's `PACK_NOT_DOWNLOADED` (the web
    build uses browser-WASM Kokoro / Web Speech directly and never throws it),
    "reading will continue with the standard voice" was **never true** in the
    context this modal appears — playback has already genuinely stopped by
    the time it shows. Fixed the copy and buttons to be honest about that:
    title "A voice is needed to read aloud," body states playback has
    stopped, and the dismiss button is **"Not now"** (closes only — makes no
    claim about reading continuing) instead of the misleading "Continue
    reading." No functional/architecture change — the underlying gap (no
    device-voice fallback exists on native at all until a pack is installed)
    is real and not something this fix addresses; a genuine fix would mean
    bridging Android's own `android.speech.tts.TextToSpeech` into a fallback
    path, which is a new native plugin surface, not attempted here.
    **First reply wrongly claimed this doesn't block reading ("the device
    voice keeps going regardless") — owner tested it and confirmed the
    opposite: "the reader won't work without a voice," and removed the
    "Continue reading" button that promised otherwise.** The follow-up
    softened the wording (title/body honest about playback having stopped,
    button renamed to "Not now") but kept a dismiss button — **owner pushed
    back again: "it's not a wording issue... leave the Download a voice
    button and nothing else."** Since dismissing leads nowhere useful (native
    genuinely cannot read without a pack), a dismiss option was pointless
    UI, not a courtesy. `#novoice-modal` now shows exactly one button,
    **"Download a voice"**; the backdrop-tap-to-close behavior in
    `NoVoiceModal.close(e)` was left as-is (not asked to change) as the only
    remaining way to back out without downloading.
    Verified in a browser harness: the toast's rendered bounding box stays
    within a 375px viewport for the exact message that was reported cropped;
    the modal opens exactly once across 5 consecutive simulated chunks with
    the same missing pack; "Download a voice" closes it and opens
    `LangPacksModal`. Not device-verified.
  - **Onboarding step (owner request, same change):** `VoicePacks.maybeOnboard()`
    fires ~400 ms after `setFolder()` — i.e. as the last step of first-run
    setup, right after the Drive folder is picked — and opens `LangPacksModal`
    with an explanatory header. It **awaits `refresh()` before deciding**
    (`refresh()` now returns a promise): opening a popup whose rows all still
    read "Checking…" looks broken, and an install that already has a pack
    (notably one upgrading from the bundled build) must not be prompted at
    all. Guarded once-ever by `pl_packs_onboarded`, which is **only consumed
    on native** — the web build returns before setting it, so a later native
    install still gets its prompt. `LangPacksModal._onboarding` is sticky so
    `_notify()` re-renders during a download keep the header, and is cleared
    by `close()` so opening it later from Settings shows no header.
  - Verified in a browser harness with the native plugin mocked: onboarding
    opens once and not again; US renders as a real Download with a size and
    Remove/Download round-trips; queued-vs-downloading wording; the voice
    picker's empty state, and that it lists US voices only after that pack is
    installed; the no-pack-installed fallback keeping the voice choice vs
    switching to an installed pack's voice; and the web build still hiding the
    row, no-op'ing onboarding, and leaving the flag unconsumed. **The Kotlin
    is NOT device-verified** — no JDK/Android SDK here, as ever.
  - **First real device test must include:** a genuine `downloadPack('us')`
    against the live GitHub URL, and confirming an EXISTING install (which
    still has the old asset-copied `filesDir/kokoro`) is reported as already
    downloaded rather than being asked to re-download.
  **"Queued" vs "Downloading 0%" fixed the same day (owner-reported: with the
  download queue from the concurrency fix above, a pack waiting its turn
  looked identical to one stuck at 0%).** `downloadPack` now emits an initial
  `packProgress` event the MOMENT its task actually starts running on
  `downloadExecutor` (i.e. once dequeued) — a pack still waiting behind
  another gets no event at all until its turn comes. JS tracks this as
  `started: false` until that first event lands, and the Language Packs row
  shows **"Queued…"** (with a working Cancel) instead of "Downloading… 0%"
  until then.
  **Cancel didn't actually cancel — queue wedged behind it (owner-reported
  2026-08-04: started three packs, cancelled the running one, "the other two
  remained at Queue and never started").** Root cause: cancellation was only
  checked **once per tar ENTRY**, and `model.onnx` is a single ~70 MB entry —
  i.e. nearly the whole archive. `tar.copyTo(os)` streams that one entry from
  the network with no cancellation check inside it, so a cancel mid-entry did
  nothing until the *entire* download had finished. The cancelled task kept
  occupying the single-thread `downloadExecutor`, so every queued pack behind
  it stayed on "Queued…" for minutes. (The per-entry check wasn't wrong, just
  uselessly coarse for this archive shape.) Fixed by moving the real check
  **into `ProgressInputStream`**, which now takes an `isCancelled` lambda and
  throws `InterruptedIOException` from `read()` — so a cancel aborts within
  one buffer read (a few KB) and the executor frees immediately. Two related
  fixes in the same pass: a task **cancelled while still queued now bails
  before opening the connection at all** (it previously skipped only the
  "started" event, then downloaded the whole thing anyway before throwing at
  the first entry check); and JS no longer reports a user-initiated cancel as
  a failure — `download()` suppresses the "Download failed" toast when the
  rejection message matches `/cancel/i`, and `cancel()` toasts "Download
  cancelled" instead. Verified in a browser harness that models the real
  design (single-thread queue + per-model epoch + per-chunk cancel check):
  cancelling the RUNNING pack immediately starts the next queued one;
  cancelling a QUEUED pack never downloads it and doesn't disturb the running
  one; progress state drains and only the one intended toast fires. Not
  device-verified.
  **Voice picker simplified the same day (owner feedback: listing every
  voice, including locked/undownloaded ones, "could get convoluted" as more
  languages are added).** `VoiceModal`'s neural branch now filters to only
  voices whose pack is actually downloaded (originally with a `"us"` exemption,
  **removed once nothing was bundled** — every model is gated identically now,
  and an empty list gets its own "No natural voices installed yet" state) and
  appends one trailing
  **"Get more voices"** row that closes the picker and opens
  `LangPacksModal` — replacing the old per-voice locked state + inline
  download that lived here before. `VoiceModal.downloadFor` was removed
  (dead code once nothing calls it) in favor of `VoiceModal.openLangPacks`.
  **Concurrent-download bug fixed 2026-08-04 (owner-reported: downloading two
  packs at once froze both, then the first in line self-cancelled).** Root
  cause: `downloadEpoch` was a single `@Volatile Int` shared across EVERY
  model, and `cancelDownload` bumped it globally while silently ignoring the
  `model` argument JS already sent. Starting pack B's download bumped the
  shared epoch, which instantly looked like a cancel to pack A's still-running
  loop (its next progress-emit/tar-entry check saw `stamp != downloadEpoch`)
  even though nothing about A was actually cancelled — and since both ran on
  the same `genExecutor` (shared with TTS synthesis), B's job sat queued and
  looked frozen until A's job vacated the thread by erroring out. Fixed with
  **`downloadEpochs: ConcurrentHashMap<String, AtomicInteger>`** (per-model,
  via `bumpDownloadEpoch`/`currentDownloadEpoch`) so one model's download can
  never invalidate another's, `cancelDownload({model})` now actually reads and
  scopes to that model, and downloads moved onto their **own dedicated
  `downloadExecutor`** (single-thread, separate from `genExecutor`) — which
  both delivers the queueing the owner asked for (multiple downloads serialize
  in request order instead of racing) and stops a multi-minute pack download
  from blocking `synthesize()` calls behind it on the shared executor, a
  related freeze bug this would otherwise have introduced. Not device-verified.
  **`app/build.gradle`'s `compileOptions`/`kotlinOptions.jvmTarget` MUST
  match `capacitor.build.gradle`'s `compileOptions`** (currently both
  `VERSION_21`/`'21'`) — the latter is auto-regenerated by every `cap sync`/
  `cap update` and applied AFTER `app/build.gradle`, so it silently wins for
  Java while Kotlin keeps whatever `app/build.gradle` says; a mismatch fails
  `compileDebugKotlin` with "Inconsistent JVM-target compatibility" (hit
  2026-07-06). If Capacitor ever regenerates that file at a different JDK
  version, bump `app/build.gradle` to match.
  **Secure storage plugin:** `SecureStoragePlugin.kt` (also registered in
  `MainActivity.java`) exposes a tiny Keystore-backed `get`/`set`/`remove(key)`
  surface via `androidx.security:security-crypto`'s `EncryptedSharedPreferences`
  — used for the native OAuth refresh token (`pl_rtoken`); see the "Native
  auth" behavior note and the Security-hardening section.
  Loop:
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

- **OAuth Client IDs are on the "PhonoLeaf" Cloud Console project (project id
  `phonoleaf`), not the old `koboaudio`-named one** — migrated 2026-07-22, see
  the "Naming policy" note. `CONFIG.CLIENT_ID` (web) =
  `88179965472-codmbgtm99mgik9qke2kucbvfkbug3ul.apps.googleusercontent.com`;
  `CONFIG.ANDROID_CLIENT_ID` (native) =
  `88179965472-cs9869nsk2b9i00v5ebo8sd9mrq9kmmn.apps.googleusercontent.com`,
  which must match `AndroidManifest.xml`'s reversed-scheme `oauth2redirect`
  intent-filter exactly (`com.googleusercontent.apps.<the same id minus the
  .apps.googleusercontent.com suffix>`) — a mismatch breaks the native deep-link
  return silently. Do not change either without the owner explicitly asking —
  they must match the Google Cloud Console.
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
  (`pl_rtoken`, stored in Android Keystore-backed `EncryptedSharedPreferences`
  via `SecureStoragePlugin.kt` since 2026-07-22 — see the Tech-stack note;
  `App._getRefreshToken`/`_setRefreshToken`/`_removeRefreshToken` are the only
  places that should touch it): `tryResume`/`refreshToken` renew silently
  (`_nativeRefresh`), so the native app stays signed in permanently; a failed
  refresh clears `pl_rtoken` and falls back to interactive sign-in; `signOut`
  revokes the grant via `oauth2.googleapis.com/revoke`. Shared post-auth path for all
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
- **`start()` (press-play) also skips the lead pre-pause, same as `_resumeRead`
  — fix for "pressing play doesn't play; pause+play fixes it" (2026-07-21).**
  This is the SAME 3s heading pre-pause as above, hit from a different entry
  point: `start()` never resets `_preIdx`, so on a chapter-heading page it
  legitimately arms the 3s `setTimeout` (no gen-voiding needed this time — the
  timer WOULD fire on its own). But the FIRST press already stamps
  `_preIdx = this.idx` the instant the pre-pause branch is entered — so a
  pause+play retry, even a fraction of a second later, sees `_preIdx === idx`
  and skips the SAME pre-pause, speaking instantly. That made "press play"
  look broken (total silence, no loading indicator) and "pause then play"
  look like the fix — verified in a harness: the original timer never fires at
  all once `stop()` cancels it (`clearTimeout(this._gapT)`); waiting longer on
  the first press doesn't help. This became far more common once background
  reading (below) started crossing chapter boundaries — `_bgResync` frequently
  lands the visible reader right on a fresh heading. Fix: `start()` now also
  sets `_preIdx = this.idx` right before its `_speak()`, so a user-initiated
  play press speaks immediately every time, matching resume. Body chunks are
  unaffected (their `pre` is always 0, so the pre-pause branch never triggers
  for them regardless of `_preIdx`) — only ORGANIC auto-advance into a new
  chapter while already reading (via `_resumeRead`, not `start()`) still gets
  the dramatic pause, which remains intentional there.
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
- **Drive folder selector is a custom themed browser (`FolderBrowser`), NOT the
  Google Picker.** Settings → "Change" / onboarding open `#browser-modal`: a
  normal in-app modal (inherits theme via CSS vars) that lists sub-folders via
  the Drive API (`'<id>' in parents and mimeType=folder`, `orderBy:'name'` →
  **name asc**), with a clickable breadcrumb (`_stack`), tap-a-row to navigate
  in, and an unmissable "Use this folder" button. **Always starts at `root` =
  My Drive**, with the existing pick shown as context in `#fb-current`.
  `setFolder(id,name)` persists `pl_folder_id` (id wins) / `pl_folder` and
  reloads; `Library.load` uses `activeFolderId()` then, only if a folder name
  is set, `findFolder(activeFolder())`. `FolderModal` typed-entry remains the
  not-signed-in fallback. `CONFIG.API_KEY` is unused (see below).
- **`drive.file` + Google Picker was tried on 2026-07-22 and REVERTED the same
  day — do NOT retry it without reading this.** The goal was escaping the
  restricted-scope CASA assessment. It failed on a hard API limitation:
  - **`drive.file` grants access ONLY to files the user picks INDIVIDUALLY.**
    Picking a *folder* yields the folder object itself but **not its contents** —
    `files.list` with `'<folderId>' in parents` returns an empty set. Confirmed
    on device: the folder name appeared correctly in Settings (so `files.get` on
    the folder worked) while the library rendered zero books. The
    connect-a-folder-and-new-books-appear model is therefore **impossible** under
    `drive.file`; it fundamentally requires a restricted scope. The only
    `drive.file`-compatible design is multi-select of individual epub FILES,
    which loses auto-sync (every new book needs re-picking).
  - **A process lesson:** the feasibility test (temporary `PickerTest` module)
    was declared a pass off the owner's "the picker works", which actually meant
    "the dialog opened" — the `files.list` result was never confirmed. Verify the
    *specific* assertion, not an adjacent one.
  - **The Picker's UX was independently a blocker** on mobile: selecting a folder
    required a **long-press** with no visible confirm button (owner: "any person
    would struggle at the first stage of onboarding"). There is also **no
    dark-mode or sort API** on the Picker (checked the full `picker.Feature`
    enum + `PickerBuilder`/`DocsView`), and it renders in a cross-origin iframe
    so CSS injection can't force a theme either. Our own modal has none of these
    problems.
  - Native would have needed a whole separate mechanism too (Google blocks the
    embedded Picker JS in WebViews, as with sign-in): the system-browser
    `trigger_onepick=true&allow_folder_selection=true` flow returning
    `picked_file_ids` on the `oauth2redirect` deep link. All of that is now
    removed — native uses the same in-app `FolderBrowser` as web.
  - **CASA cost — the earlier "~$15k+/yr" figure in this file was WRONG.**
    Reported actuals: **AL1 ≈ $500** (self-assessment: scan + questionnaire),
    **AL2 ≈ $3–6k** (full lab assessment). Caveats: *Google*, not the developer,
    assigns the assurance level ("The framework users (Google..etc) and not the
    application developer calculate and determine which assurance level is
    required" — App Defense Alliance), there are signals that restricted scopes
    now skew toward lab assessment, and a **possible exemption** exists for apps
    with no backend — Google's rule targets apps that have "the ability to access
    data from or through a third-party server", and PhonoLeaf has no server at
    all (books go Google → device directly). Unconfirmed; Google's own page does
    not state that exemption and reads the capability broadly. **The authoritative
    answer comes from submitting the OAuth consent screen for verification** —
    Google's review team then states the actual requirement. Refs:
    `developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification`,
    `support.google.com/cloud/answer/13465431`, `appdefensealliance.dev/casa`.
  - **One-time re-auth for the revert** (`<head>` script, guarded by
    `pl_scope_mig2`): devices that loaded the `drive.file` build hold a token
    granted under that narrower scope, which has no readonly access (scopes never
    silently widen on refresh). Detected precisely via the presence of
    `pl_scope_mig` (only the drive.file build set it) → clears `pl_auth`/
    `pl_rtoken` so the user re-consents once; devices that never ran that build
    keep their valid tokens and are left completely alone. The picked folder is
    deliberately **KEPT** (a folder id is just an id, and readonly can see the
    whole Drive again) so users re-sign-in but never re-pick. A one-shot
    `pl_scope_mig_notice` toast explains it. Verified in a harness for both
    populations: ran-drive.file → tokens cleared + folder preserved; never-ran →
    everything untouched, notice not fired.
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
- **Seek scrubber's chapter name now matches the hamburger menu (fixed
  2026-08-05, owner-reported: "the chapters on the reader's scrollbar are
  different than the ones in the hamburger icon").** `Scrub._info(pct)` had
  its own ad-hoc chapter lookup — `State.toc.find(t => href.includes(...))`,
  a plain substring match against ONLY top-level TOC entries, with no
  fallback — a second, separately-written version of exactly the logic
  `chapterLabelFor()` (see the Lock-screen controls note below) already
  solves correctly and shares with the reader's top bar and lock-screen
  metadata. Any chapter nested under a `subitems` parent (the common case —
  see the `ChapterModal`/overlay note above) was invisible to Scrub's lookup,
  so dragging the scrubber into one of those showed "Section N" or the
  *parent's* title instead of the real chapter name, while the hamburger menu
  (which lists `flattenToc()` directly) showed it correctly — hence the
  mismatch. Fixed by having `Scrub._info` call `chapterLabelFor(sec.href,
  sec.index)` instead of reimplementing the lookup, so there are now three
  places (top bar, lock-screen, scrubber) all resolving through the one
  function and unable to disagree. Verified in a browser harness with a
  synthetic nested TOC: `chapterLabelFor` resolves a subitem chapter that the
  old lookup would have missed, and `Scrub._info` end-to-end returns the same
  label `ChapterModal`'s own `flattenToc()` listing shows for that entry.
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
    **SUPERSEDED 2026-08-07: the "Kokoro returns as a premium voice, chosen
    per device" plan above was never built (confirmed by code audit in
    `BACKLOG.md` section H — native has shipped Piper only, with no device
    capability check anywhere, since 2026-07-06) and the owner decided not to
    build it now** — shipping a second native engine (model packaging, a
    benchmark step, switching logic in the Kotlin plugin) to offer a voice
    that already failed the realtime bar on a representative mid-tier phone
    isn't worth it. **What shipped instead is much smaller: `TTS._nativeBench`**,
    a one-time proactive speed check for the ONE native engine (Piper) that
    already exists. It reuses `_synthNative` on a fixed throwaway sentence
    right after the first voice pack ever finishes downloading (see
    `VoicePacks.download`) — the earliest point a model exists to time, and it
    lands while the user is very likely still on the onboarding screen — and
    caches a `{model, ratio, t}` verdict in `pl_native_bench`
    (`TTS._nativeSlow` = `ratio > 1`, restored synchronously at parse time so
    a warm boot doesn't flash the fast-path text). Unlike `_kokoroBench`
    (web), a slow verdict here never disables the natural voice — Piper is
    still expected to be close to realtime, so this is a soft, informational
    label, not a hard fallback: it appends "(may run slower than real time
    here)" to Settings' always-visible Natural voice row, adds a note above
    the list in `LangPacksModal` (onboarding and later visits) and in
    `VoiceModal`'s neural branch. Since every voice in a downloaded pack
    shares one underlying model file, this is necessarily a device-level
    note, not a per-voice ranking — there is nothing to measure before a pack
    exists to time, so a true onboarding-time "pick from a list ranked by
    speed" was never possible without first downloading something. Not
    device-verified — same caveat as every other native-adjacent change in
    this file; the bench call itself is untested on real hardware.
    **RE-SUPERSEDED 2026-08-08 — native Kokoro was built after all.** The
    2026-08-07 "not worth it" call above was reasoned from ONE data point
    (the owner's own Pixel 7). Owner pushback: **"Kokoro was removed because
    it didn't fit Pixel 7, but PhonoLeaf is not just for Pixel 7s. More
    devices will be using it. I want the user to be able to experience a
    higher quality model if their device can operate it."** Correct — a
    single mid-tier phone's measurement was never a sound basis for deciding
    every future device, only for what to ship as the BASELINE. See the new
    **"Native Kokoro — device-gated English upgrade (2026-08-08)"** entry
    below for the full design; this paragraph stays only for the historical
    trail (why Piper became the baseline, and why that specific baseline
    decision still stands unchanged even after Kokoro's return).
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
    First entry (Ava, us/sid 40) = default.
    **Labels say "English (US)"/"English (UK)", not "US"/"UK" (owner request
    2026-08-05)** — e.g. "Ben · English (US) male" — so the voice picker
    names languages the same way the Language Packs popup does
    (`VoicePacks.CATALOG`'s labels), rather than two different names for the
    same thing.
    **French/Spanish upgraded to 2-speaker models the same day (owner: "I
    would at least like to ship 1 man 1 woman voice per language").**
    A file-size hunch (that `fr_FR-upmc-medium`/`es_ES-sharvard-medium` might
    be multi-speaker, matching US/GB's size signature) was **confirmed, not
    assumed** — each
    model's own `config.json` was fetched and its `speaker_id_map` read
    directly: `upmc` is `{"jessica":0,"pierre":1}`, `sharvard` is
    `{"M":0,"F":1}`. `VOICE_PACKS`' `fr`/`es` entries were switched from the
    single-speaker siwis/davefx to these, so each language now yields a real
    male + female voice from ONE download instead of needing a second pack —
    no change to the download/pack architecture (`fr`/`es` folder keys
    unchanged) was needed once a genuinely multi-speaker model existed.
    Display names: **Aline** (jessica, owner's pick) / **Pierre** (already a
    real French name, kept as-is) for French; **Mateo** / **Sofía** invented
    for Spanish since `sharvard`'s map is bare `M`/`F` with no names.
    **German stays single-speaker (`thorsten`) — genuinely no good option
    exists in the catalog, not a shortcut.** Checked and ruled out:
    `thorsten_emotional` (80 MB, the one same-size-signature candidate) is
    confirmed via its own config to be **8 emotions of the same single
    speaker** (amused/angry/disgusted/drunk/neutral/sleepy/surprised/whisper),
    not other people. The only female-sounding German Piper voices in the
    release (`eva_k`, `ramona`, `kerstin`) are all `low`/`x_low` quality — a
    real step down from every other voice in this app, all `medium`. Left
    male-only rather than ship a quality-mismatched pair; revisit if a better
    German voice appears on the release, or if the owner decides the quality
    drop is acceptable. "Thorsten" isn't an invented display name — it's the
    real name of the person who recorded the corpus (Thorsten Müller), used
    directly rather than relabeled, same reasoning as keeping "Pierre".
    The picker's `speaker N` sub-line was **removed** (2026-08-05, owner
    request: "if we don't need speaker IDs anymore, let's remove them") — not
    useful once every voice has a real name; each `voice-item` now shows just
    the label.
    **First real device test found a genuine bug: "both French voices are the
    same female voice. Same for Spanish being the same male voice."** Root
    cause was NOT the model — verified that thoroughly first (downloaded the
    actual sherpa-onnx-packaged `.tar.bz2`, not just the HuggingFace copy;
    confirmed via `onnx`/`onnxruntime` in Python that the graph has a real
    `sid` input identical in shape to the known-working GB model, that the
    `emb_g.weight` speaker-embedding table has 2 genuinely distinct rows
    (cosine similarity −0.34, not near-duplicate), and that running inference
    with `sid=0` vs `sid=1` produces different-length, uncorrelated output).
    The real bug: **`MODEL_VERSION` was a single hardcoded string shared by
    every model**, used to name the `.ready-$MODEL_VERSION` marker file that
    `ensureReady()`/`packStatus()` check to decide "is this pack already
    downloaded." Switching `fr`/`es` to different underlying files (siwis→upmc,
    davefx→sharvard) changed the download URL but not this marker name — so a
    device that had already downloaded the OLD single-speaker pack still had a
    marker that matched, and `ensureReady()` never re-fetched anything. The
    stale single-speaker file stayed loaded; asking it for `sid=1` when it only
    has one speaker just silently returns that same one speaker again — exactly
    matching "both voices sound the same." Fixed with a **per-model version
    map** (`MODEL_VERSIONS`/`modelVersion(model)`) instead of one shared
    constant: `fr`/`es` got new tags (`piper-fr-upmc-medium-2spk` /
    `piper-es-sharvard-medium-2spk`) so any device with the old pack correctly
    re-downloads once; `us`/`gb`/`de` deliberately keep their original tag
    unchanged, since their underlying files never changed and bumping them
    too would force a pointless ~78 MB re-download for everyone. **Lesson for
    any future model swap on an existing `VOICE_PACKS` key: the version tag
    MUST change too, or it silently keeps serving the old file** — this is
    exactly the same class of bug the "us" unbundling note warned about in
    the other direction (keep the tag when the file is genuinely the same;
    change it when the file genuinely isn't). **DEVICE-VERIFIED 2026-08-05
    — fix confirmed working: all packs and genders now sound correct.**
    **Same device test surfaced a new, separate issue: voices "mumbling"
    (garbled, not just slow) whenever a pack is downloading in the
    background.** Not a correctness bug in synthesis itself — genuine CPU
    contention. `downloadExecutor` runs on its own thread (separate from
    `genExecutor`, which runs TTS inference) specifically so a download can't
    block synthesis, but being on a different thread doesn't stop the two
    from competing for the SAME CPU cores, and bzip2-decompressing a ~70+ MB
    archive is genuinely heavy work. Fixed by running the download thread at
    Android's own `THREAD_PRIORITY_BACKGROUND` (a real scheduler nice-value
    hint via `android.os.Process.setThreadPriority` — NOT the JVM's
    `Thread.priority`, which Android's CFS scheduler largely ignores), set
    once via a custom `ThreadFactory` the first time the (single, reused)
    download thread runs. This makes the OS consistently favor TTS
    inference/audio playback over pack downloads whenever both want CPU at
    once, without touching `genExecutor`'s priority — that side was never
    reported as a problem on its own, only when a download competed with it.
    **DEVICE-VERIFIED 2026-08-05 — owner confirmed: downloading a pack while
    another voice reads no longer causes any garbling.**
    The on-screen `#tts-dbg` timing
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
    else WASM `q8`; `sw.js`
    passes huggingface/cdn-lfs requests through — transformers.js does its own
    caching). **The worker is NOT optional** — v1 ran inference on the main
    thread and froze the page for tens of seconds per sentence on phones.
    **DOWNLOAD SIZE — this file long said "~90 MB", which is only true for ONE
    of the two paths (corrected 2026-07-29 against the actual HF file
    listing).** `_kokoroOpts()` picks `fp32` whenever `navigator.gpu` exists:
    `model.onnx` (fp32) is **326 MB**, while `model_quantized.onnx` (q8) is
    **92.4 MB**. Since most current Chrome exposes WebGPU, the *typical* web
    user is downloading **~326 MB, not ~90 MB** — a ~3.5× understatement that
    was repeated in code comments and to the owner. **Worth fixing, not yet
    done** (deferred until after the verification video): the same repo ships
    `model_fp16.onnx` at **163 MB** and `model_q8f16.onnx` at **86 MB**, both
    GPU-oriented. The existing `'q8 is unreliable on WebGPU'` comment is
    probably sound, but **fp16 is not q8** and is the standard GPU precision —
    switching WebGPU to fp16 halves the download, q8f16 quarters it. Needs real
    listening tests before shipping, since it's a quality/size trade.
    NB **unlike the native Piper models, this cost does NOT scale per
    language**: Kokoro is one acoustic model plus tiny per-voice embeddings, so
    added voices/languages reuse the same download (phonemizer support aside).
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
- **Natural-voice status row is ALWAYS visible (`#voice-quality-group`,
  renamed from `#fallback-group` 2026-07-28).** It used to render only when
  `TTS._kokoroDead`, which meant the majority of users — the ones for whom the
  natural voice works — never learned the feature existed, and the ones in
  fallback got no explanation of *why*. Now the row always shows, and only its
  wording plus right-hand control change: working ⇒ sub-text "On — generated on
  your device" and a static `.set-stat` reading **On**; fallback ⇒ "Unavailable
  on this device — using the standard voice" and the **Retry** button
  (`Settings.retryNeural()`). Retry is deliberately hidden when it's working —
  there's nothing to retry. A small circled-"i" (`.sr-info`) opens `VoiceInfo`,
  a static explainer modal covering what the natural voice is (on-device, no
  text sent anywhere), why some devices fall back (it must generate faster than
  realtime), and that the Android app is faster at it than the web build.
  `#vh-row` (VoiceHelp) still shows only in fallback mode AND on mobile.
- **Your data: export + erase (`MyData`, Settings) — added 2026-07-28.**
  GDPR-style access and erasure, which the app can genuinely satisfy alone
  because there is no backend: everything is `pl_*` localStorage + the
  IndexedDB cover cache.
  - `MyData.export()` serialises every `pl_*` key to a downloaded JSON file
    (`phonoleaf-data-YYYY-MM-DD.json`), parsing values back into real JSON
    where possible. **`pl_auth` and `pl_rtoken` are deliberately EXCLUDED**
    (`MyData._SECRET`) — writing a live access/refresh token into a file the
    user may share would hand over their whole Drive. Non-`pl_` keys are
    untouched.
  - `MyData.deleteAll()` **signs out FIRST, then wipes** — that order is
    load-bearing: `App.signOut()` needs the still-valid token to revoke the
    grant at Google, so clearing storage first would destroy the token and
    leave the grant dangling on Google's side. Then it removes every `pl_*`
    key, deletes the `phonoleaf` IndexedDB, and reloads.
  - `ConfirmModal.show(cb, msg, okLabel)` gained optional text params for the
    destructive confirm; both **default back to the original stats-reset
    wording on every call**, so a custom message can never linger and mislabel
    the next confirmation (verified in a harness).
  - `privacy.html` documents all three routes (export / delete / stats-only
    reset) under "Your rights over your data" — a policy claiming rights the
    UI doesn't offer would be worse than not claiming them.
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
  foreground-service rules; confirmed by a kill-switch bisect). **REMOVED from
  package.json 2026-07-22** (+ its patch-package patch + `.npmrc`) — see the
  "Native shell" note in Tech stack.
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
  title + current chapter. The Web-Speech (device-voice fallback) path still
  can't play backgrounded. (Lock-screen PAUSE control added 2026-07-22 — see
  the "Lock-screen media controls" note below.)
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
- **Lock-screen media controls (2026-07-22, upgraded to full play/pause the
  same day) — working PAUSE **and** RESUME from the lock screen.** First cut
  shipped pause-only (pressing pause tore the notification down via the
  existing `stopPlaybackService()` path, leaving nothing to press "play" on
  afterward); owner tested it and pushed back immediately ("the pop up leaves
  when you press it. The lockscreen popup needs to allow play and pause"), so
  the same day this was redesigned so **the foreground service now survives a
  pause**. `PlaybackService` creates a `MediaSessionCompat` in `onCreate()`
  (before the 5s startForeground watchdog window — same tier as channel
  setup), activated + given `MediaMetadataCompat` (title/chapter, matching the
  notification's own text) and a `PlaybackStateCompat` on every
  `onStartCommand` — including the metadata-only refreshes described below, so
  it stays current as chapters change, not just at session start.
  **Design: `TTS._mediaState(playing)` (index.html) always calls
  `startPlaybackService` — for BOTH play and pause — carrying a `playing`
  boolean extra (`EXTRA_PLAYING`, defaults `true`), never `stopPlaybackService`
  on a plain pause.** `onStartCommand` reads it and passes it through to
  `buildNotification(title, text, playing)` (swaps the action button between
  "Pause" targeting `ACTION_PAUSE` and "Play" targeting `ACTION_PLAY`, distinct
  `PendingIntent` request codes 2/3 so `FLAG_UPDATE_CURRENT` can't collide them)
  and `updateMediaSession(title, text, playing)` (sets `STATE_PLAYING` or
  `STATE_PAUSED`, always advertising `ACTION_PLAY or ACTION_PAUSE or
  ACTION_PLAY_PAUSE` so the system UI shows the right control regardless of
  OEM). The CPU wake lock still tracks play/pause precisely
  (`acquireCpuWakeLock()`/`releaseCpuWakeLock()` — nothing needs the CPU while
  genuinely paused). **The service is now only ever really torn down by
  `TTS._mediaStop()`**, wired into `App.signOut()` right after `TTS.stop()`
  (`index.html`, with a comment noting `TTS.stop()` alone only pauses the
  notification now) — there's no other "stop reading this book, for good"
  action in the app today (`Reader.close()` exists but has no caller; the
  mini-player model is that the loaded book persists across tabs), so signing
  out is the one clear "done" signal.
  **Two independent ways a press reaches JS**, both ending at the same
  `PhonoLeafTtsPlugin.notifyMediaButton(action)` → a `"mediaButton"`
  Capacitor event → `TTS._mediaSetup`'s listener → `TTS.stop()`/`TTS.start()`:
  (1) the system's own lock-screen/quick-settings/Bluetooth transport
  controls, which Android delivers to `MediaSessionCompat.Callback.onPause`/
  `onPlay` automatically once the session is active — no extra plumbing
  needed for this path; (2) the notification's own play/pause **button**
  (inside the expanded notification), which needed its own `PendingIntent` per
  state — built as a **plain custom action targeting `PlaybackService`
  directly** (`ACTION_PAUSE`/`ACTION_PLAY`, handled at the top of
  `onStartCommand`, returning `START_NOT_STICKY` without touching the
  foreground state), deliberately **NOT**
  `MediaButtonReceiver.buildMediaButtonPendingIntent` — that official-looking
  helper needs a manifest `<receiver>` PLUS the service itself calling
  `MediaButtonReceiver.handleIntent()` to route raw `ACTION_MEDIA_BUTTON`
  broadcasts, and getting that exactly right without a device to verify on
  felt like the wrong risk to take for one button; the custom-action approach
  reuses the SAME `context.startService()` mechanism `TTS._mediaMeta()`
  already relies on (proven not to re-arm the FGS watchdog, per the crash-fix
  above), so it's fully understood rather than assumed. `PhonoLeafTtsPlugin`
  exposes the JS-facing half via a `WeakReference`-held companion instance
  (set in an added `load()` override) — `PlaybackService` is a separate
  Android component with no direct line to the Capacitor bridge otherwise.
  `MediaStyle` (`androidx.media.app.NotificationCompat.MediaStyle`, from the
  `androidx.media:media:1.7.0` dependency) is attached to the notification
  purely for DISPLAY (tells the system to render it as a media notification
  and back the lock-screen media widget with this session) — independent of
  the button-routing above. `onDestroy` deactivates + releases the session
  alongside the existing wake-lock cleanup.
  **Two native bugs broke the resume half on first device test (fixed
  2026-07-25, owner-reported "reading does not resume when pressing play") —
  both in `PhonoLeafTtsPlugin.startPlaybackService`, the bridge BETWEEN the
  JS and the service, and both individually sufficient to cause it:**
  1. **The `playing` flag was never forwarded into the Intent.** `EXTRA_PLAYING`
     was added to `PlaybackService` and `playing` was added to the JS payload,
     but this method still only put `EXTRA_TITLE`/`EXTRA_TEXT` — so the service
     fell through to its `getBooleanExtra(EXTRA_PLAYING, true)` default and
     every pause was read as a play. **Any new field in the `_mediaState`
     payload must be added in THREE places** (JS payload → this `putExtra` →
     the service's `getXExtra`); the middle one is easy to miss because
     nothing fails loudly when it's skipped.
  2. **The `appInForeground()` guard dropped the update entirely.** It exists
     to never cold-start an FGS from the background, but a lock-screen pause
     ORIGINATES from the background by definition, so the state update was
     skipped and the session stayed `STATE_PLAYING`. Note an app whose only
     foreground component is a running FGS is **not** "foreground" by that
     check — its importance is `IMPORTANCE_FOREGROUND_SERVICE` (125), above
     the `IMPORTANCE_FOREGROUND` (100) threshold it compares against. Now
     guarded as `!appInForeground() && !PlaybackService.isRunning`
     (a new `@Volatile` companion flag set in `onCreate`/cleared in
     `onDestroy`), so cold starts stay protected while updates to an
     already-running service go through. `startService()` to a running
     service is allowed from the background (Android counts an app with an
     active FGS as foreground for the background-start restriction), and the
     existing catch turns any surprise into a `reject` rather than a crash.
  Net effect of the two: after a lock-screen pause the notification kept
  showing a **Pause** button and the session kept reporting `STATE_PLAYING`,
  so the next press dispatched `pause` again — which `TTS._mediaSetup`'s
  listener correctly no-ops on (`if (this.active)`, already false). Nothing
  resumed.
  **Process lesson:** the browser harness below mocked the native plugin, so
  it could never have caught either bug — it verified the JS emits
  `{playing:false}` and stopped exactly where the real defect began. A mock
  validates the half you didn't break. When a feature spans the JS/native
  bridge, the payload has to be traced THROUGH the plugin into the service by
  reading that code, not inferred from the JS end behaving correctly.
  Verified in a browser harness (mocked the native plugin, fresh tab — an
  earlier run in a long-reused tab produced contradictory results that turned
  out to be stale state from prior test scripts in that same tab, not a real
  bug; confirmed by re-running clean): `_mediaState(true)` sends
  `{playing:true}` via `startPlaybackService`; `_mediaState(false)` ALSO goes
  through `startPlaybackService` with `{playing:false}` and never touches
  `stopPlaybackService`; a simulated lock-screen play press while paused
  correctly runs `TTS.start()`; a simulated pause press while playing
  correctly runs `TTS.stop()`; `_mediaStop()` (the sign-out path) is the only
  call that reaches `stopPlaybackService`. **The native Kotlin/Gradle side is
  NOT device-verified** — no JDK/Android SDK in this environment (reviewed
  carefully against the documented `MediaSessionCompat`/`NotificationCompat.Action`
  APIs and confirmed brace/paren-balanced, but review is not a substitute for
  a real build + device test). **Still not attempted**: showing playback
  position/progress in the notification, and media-button handling via
  Bluetooth hardware keys specifically (only verified via the transport-control
  callback path, which should cover it, but wasn't tested against real
  hardware).
- **Lock-screen controls: cover artwork, page numbers, page/chapter buttons
  (2026-07-25).** Owner request on top of the working play/pause above.
  - **Five buttons, and that is the hard ceiling.** Android 13+ builds the media
    UI from the **MediaSession**, not the notification's actions, and allocates
    exactly 5 slots: play/pause, `ACTION_SKIP_TO_PREVIOUS`,
    `ACTION_SKIP_TO_NEXT`, then custom actions in order. So **page turns are
    mapped onto skip-prev/next** (they also survive into the 3-button compact
    view, via `setShowActionsInCompactView(1, 2, 3)`) and **chapters onto the two
    custom-action slots** (`CUSTOM_PREV_CHAPTER`/`CUSTOM_NEXT_CHAPTER`,
    `onCustomAction`). That fills all five exactly — there is no room for a sixth
    control without dropping one of these. The notification also carries all five
    as `NotificationCompat.Action`s (for the expanded view / older Android), each
    with its **own PendingIntent request code** (2–7; the content tap is 0) —
    sharing one would make `FLAG_UPDATE_CURRENT` alias them and buttons would
    fire each other's action. Icons were originally stock
    `android.R.drawable.ic_media_*` (`rew`/`ff` for chapters, `previous`/`next`
    for pages) — see the icon-consistency fix below for why that changed.
  - **Lock-screen icon consistency fixed 2026-08-04 (owner-reported: the four
    buttons "aren't the same format" — two rendered as clean white outlines,
    two as filled grey-and-white).** Root cause: `ic_media_previous`/
    `ic_media_next` and `ic_media_rew`/`ic_media_ff` are two DIFFERENT stock
    Android icon families that were never designed to appear together — old
    framework bitmap-style glyphs, visually inconsistent by nature, not a
    theming bug. Also relevant: **only the two CUSTOM actions (chapter
    prev/next) are actually app-controlled on the real lock-screen MediaSession
    widget** — its skip-previous/skip-next icons for `ACTION_SKIP_TO_PREVIOUS`/
    `ACTION_SKIP_TO_NEXT` are drawn entirely by Android's own System UI and
    can't be overridden by any resource we supply, which is why the two skip
    icons in the screenshot already looked clean/uniform (system-drawn) while
    the two chapter icons looked mismatched (our old stock drawables). Fixed
    by replacing all four with **our own vector drawables**
    (`ic_skip_previous`/`ic_skip_next`/`ic_fast_rewind`/`ic_fast_forward` in
    `android/app/src/main/res/drawable/`), built from the authoritative
    Material Icons SVG path data (fetched from `google/material-design-icons`
    on GitHub, not guessed — the notification small-icon saga below already
    burned one mistake on a guessed/assumed icon design). Wired into both the
    `PlaybackStateCompat` custom actions (the two that actually matter on the
    lock-screen widget) and the notification's own `NotificationCompat.Action`
    list (for consistency in that secondary view too, even though its
    skip-icons there ARE app-controlled, unlike the lock-screen widget's).
    Not device-verified — no visual confirmation possible without a real
    device, same caveat as the small-icon design below.
  - **Notification small icon (badge) is `R.drawable.ic_notification`, a
    DEDICATED bitmap — not `android.R.drawable.ic_media_play`, and not
    `R.mipmap.ic_launcher` either. Took THREE attempts, two of them wrong,
    both confirmed wrong on device, before landing on the right design.** The
    original stock play-triangle looked exactly like an extra playback control
    and was mistaken for one (owner: "I see a 'play' button that sends me to
    the app"). It was never a control — tapping the notification body/icon has
    always launched the app via `setContentIntent(tap)`, standard for every
    media notification — it just LOOKED like a button because of the icon.
    1. **First attempt (`R.mipmap.ic_launcher`) rendered as a blank circle.**
       On API 26+ that resolves to `mipmap-anydpi-v26/ic_launcher.xml`, an
       `<adaptive-icon>` (separate background-color + foreground-layer XML
       meant for the launcher's own compositing) — `Icon.createWithResource()`/
       `setSmallIcon()` can't flatten that outside a real launcher context, and
       silently fell back to a blank placeholder.
    2. **Second attempt — a dedicated flat PNG (`R.drawable.ic_notification`)
       of the FULL brand mark (green tile + cream leaf + waveform) — STILL
       rendered blank, this time a plain white rounded square, confirmed on
       device.** The shape changing (circle → square) between attempts proved
       the reference WAS updating each time — the rendering itself was still
       failing. Root cause: Android forces notification small icons through
       ALPHA-CHANNEL monochrome extraction (fills the opaque region solid
       white/tinted, expecting a thin white-on-transparent silhouette design —
       this is documented Android guidance, ignored in the first two
       attempts). The full mark is >85% opaque (a solid-filled tile), so the
       extraction just produced a solid white blob with no recognizable shape
       — not a rendering failure, a correct rendering of unsuitable source art.
    3. **Fixed by building a proper silhouette**: the leaf+stem filled solid
       white, with the waveform cut out as a genuinely transparent notch via
       an SVG `<mask>` (`white` fill = kept, `black` stroke = cut), replicating
       the two-tone design's visual read as pure alpha content — ~15% opaque,
       the rest fully transparent. Verified via canvas pixel inspection before
       writing to disk (100% of opaque pixels pure white; renders as a clean
       recognizable silhouette against both light and dark test backgrounds)
       — the earlier attempts were each visually spot-checked too, but only by
       eye against a plain background, never by directly measuring opacity
       composition, which is what would have caught this before shipping it
       twice. `drawable-*dpi/ic_notification.png` regenerated at 24/36/48/72/
       96px (standard status-bar icon sizes, smaller than the full launcher
       icon — Android's small-icon convention, not the adaptive safe zone).
  - **This also surfaced a real, separate gap while investigating: the
    NATIVE APP'S ACTUAL ICON (`ic_launcher`/`ic_launcher_round`, i.e. the real
    home-screen icon, not just this notification) had NEVER been replaced from
    the default Capacitor/Android-Studio scaffold** (a generic blue crossed-
    arrows mark on white) — nobody had touched `android/app/src/main/res/
    mipmap-*` since the project was scaffolded. Fixed the same day: regenerated
    the full icon set (flat `ic_launcher.png`/`ic_launcher_round.png` at
    48/72/96/144/192px per density, the adaptive `ic_launcher_foreground.png`
    at 108/162/216/324/432px scaled into Android's ~66/108 safe zone, plus
    `ic_notification.png` above) from the SAME inline SVG already used for the
    PWA favicon/manifest icon (`index.html`'s "Soundwave Vein" mark) — not a
    new design, a re-render of the already-approved brand asset, done via a
    throwaway HTML canvas page served from the project root (file:// URLs
    outside the served root render as static snapshots, not interactive) with
    the resulting PNGs decoded straight to their `res/` paths. Also updated
    `values/ic_launcher_background.xml`'s color from the scaffold default
    `#FFFFFF` to the brand green `#3E8E6B`. `drawable-v24/ic_launcher_
    foreground.xml` (a vector "Android robot" leftover from the same scaffold)
    was left alone — confirmed nothing references `@drawable/ic_launcher_
    foreground` (only `@mipmap/ic_launcher_foreground`, which the new PNGs
    now serve), so it's dead and harmless.
  - **Page buttons do something DIFFERENT when locked, on purpose.** epub's page
    turn and section render both need the render loop, which Android freezes with
    the screen off — that is the entire reason `_bgAdvance` exists — so routing a
    lock-screen press through `skipPage()` would hang on `_awaitingPage` and
    silently kill playback. `TTS._mediaNav` therefore branches on
    `document.hidden` (plus the same `_engineNow() !== 'web'` check `_speak` uses,
    since only the native path reads backgrounded at all): **foreground** → the
    normal `Reader.next()`/`prev()` (keeping the overshoot corrector) and
    `_jumpChapter()`; **backgrounded** → `_bgNav()`, which moves inside the
    background text reader, where a "page" is **one spoken chunk** (a
    sentence/paragraph) — the only unit that means anything without a layout, and
    the useful audio analogue. Running off either end of a section falls through
    to `_bgGotoSection`, which walks to the nearest usable spine section (skipping
    empty/nav ones), starts at its top, and **stays put at the ends of the book**.
    It reads the currently-rendered section via `_currentSectionChunksWithNodes`,
    never `section.load()` — that corrupts epub's later `display()` of it (see
    `_loadSectionChunksWithNodes`). Pressing a transport button while paused
    resumes listening, matching what the in-app page buttons already do.
  - `_bgEnter()` was split out of `_bgAdvance` (identical logic, still fails
    closed) so `_bgNav` can enter background mode when a button is pressed before
    any background step has run.
  - **Page numbers are chapter-relative** (`displayed.page/total` are
    section-relative). Sent as a separate `page` field: the notification puts it
    in `setSubText`, and `updateMediaSession` folds it into the metadata subtitle
    as `"Chapter · Page X / Y"` — the modern system UI shows **metadata**, not the
    notification's own title/text, so anything that must appear on the lock screen
    has to be in the metadata.
  - **Two follow-up bugs, fixed the same day (owner-reported "the page number
    does not change" + "disappears when I change chapters"):**
    1. **`_bgNav`'s in-section page move never called `_mediaMeta()`.** The
       chapter-jump branch (`_bgGotoSection`) did; the same-chapter page-turn
       branch just updated `idx` and spoke — so pressing next/prev page while
       locked moved the audio but never told the notification, and it looked
       inert. One-line fix: `_bgNav` now calls `_mediaMeta()` right after moving
       `idx`, same as the chapter branch already did.
    2. **Background mode's page field was unconditionally `''` by original
       design** — the first cut's reasoning ("pages are a property of the
       rendered layout, and there isn't one here") was correct about the
       RENDERED page number but wrong to conclude nothing usable exists.
    Verified in a fresh-tab harness with a synthetic 3-section/17-location book:
    boundary search for each section and the past-the-end case, page position at
    the start/middle/end of a chapter, cache recomputing on a chapter change,
    graceful blanks (no CFI / no locations), and — matching the owner's exact
    reports — `_bgNav('nextPage')` now pushes an updated page number and a
    chapter change (`_bgNav('nextChapter')`) now pushes a populated one instead
    of blank.
  - **REPLACED the same day, on device (2026-07-26): `book.locations` was the
    WRONG data source for the page count, not just an implementation detail —
    owner-reported "the page change buttons sometimes increase the page number
    by 2, sometimes by 0" while locked (working fine merely backgrounded,
    i.e. not a JS-throttling/race issue — a genuine granularity mismatch).**
    `book.locations` is epub.js's whole-book position index at a **fixed
    ~1024-character granularity**, generated independent of paragraph
    boundaries. But `_bgNav` always moves by exactly **one chunk** (one
    paragraph) per press — and paragraph length varies a lot: a short line of
    dialogue might not cross a single 1024-char boundary (→ the reported page
    number doesn't move: the "+0" case), while one long descriptive paragraph
    can span several boundaries on its own (→ the number jumps by 2+ on a
    single press: the "+2" case). The two granularities simply don't
    correspond 1:1, so no amount of correct CFI math fixes it.
    **`_bgChapterPage()` now counts CHUNKS instead of locations**: in
    background mode `this.chunks` already **is** the full current-chapter
    chunk list (set by `_bgEnter`/`_bgGotoSection`), so
    `` `Page ${idx + 1} / ${chunks.length}` `` moves by exactly ±1 per press,
    by construction — no CFI, no `book.locations`, no per-section cache. This
    deleted `_bgLocBounds`/`_bgPageBoundsIdx`/`_bgPageBounds` entirely (and the
    matching reset in `Reader.open`) — dead code once nothing keys off spine
    index anymore. Verified in a harness: pressing next/prev repeatedly across
    chunks of deliberately mismatched lengths (a 6-char chunk next to an
    1800-char one) now always changes the shown number by exactly 1, forward
    and backward, plus blank-on-empty and the removed fields confirmed gone.
  - **The chapter name now tracks background reading.** It used to be scraped
    from `#rs-chapter`, which is frozen wherever the screen locked, so it named
    the chapter you STARTED in. `_mediaPayload` now derives it from the spine
    section the audio is actually in, via a new module-level `chapterLabelFor()`
    (extracted verbatim from `_onRelocated`, so the reader's top bar and the lock
    screen can't drift apart), and `_bgAdvance`/`_bgGotoSection` push a
    `_mediaMeta()` refresh on section change — about once per section, not per
    sentence.
  - **Artwork** is the cached cover from `CoverCache`, downscaled to 512px and
    JPEG-encoded in JS (`_coverB64`), sent as `coverB64`. It goes as its **own
    follow-up update** (`_mediaCoverSync` off `_mediaState`) so decoding never
    delays playback starting, and **only once per book** (`_mediaCoverKey`, set
    only on success so failures retry; cleared by `_mediaStop`) — this is the same
    bridge where a per-sentence base64 payload once froze the WebView, so it must
    not ride along on every chapter change. The plugin decodes it and hands it to
    the service through a **static** (`PlaybackService.setArtwork`), NOT an Intent
    extra: extras are parceled through binder even for a same-process service and
    a bitmap would blow the ~1 MB transaction limit. Set as both
    `METADATA_KEY_ALBUM_ART` (what the system player renders behind the controls)
    and `METADATA_KEY_ART`, plus `setLargeIcon` on the notification.
  - Verified in a browser harness (fresh tabs): payload shape foreground vs
    background, all six actions routing correctly in each mode (including the
    Web-Speech fallback still using the visual turn), `_bgNav` chunk movement and
    its fall-through to a section move, `_bgGotoSection` skipping empty sections /
    reading the rendered section from the live iframe / staying put at the book's
    start / aborting on a `_gen` bump or a stop mid-load, `_bgAdvance` still
    bootstrapping and still failing closed after the `_bgEnter` split, and the
    cover round-tripping (900×1400 → 329×512, sent once, not re-sent, key cleared
    on stop). A `scratchpad/bridgecheck.js`-style static check also confirmed
    **every payload field and action name lines up across all three layers**
    (JS → plugin `putExtra` → service `getXExtra`) — the exact class of gap that
    caused the resume bug above. **The Kotlin was NOT device-verified when this
    shipped** (no JDK/Android SDK here) — **owner-tested on device 2026-07-26**:
    chapter buttons worked correctly; the page counter and the notification
    icon did not (both fixed above — see the "REPLACED the same day" and
    "Notification small icon" bullets).
  - **Two more bugs found in the SAME 2026-07-26 device test, this time in
    navigation itself, not just display** (owner: "the first time I skip a
    page, it skips many pages at once. Then it stabilizes... if I go back a
    page, and then move forward again, it skips many pages ahead once again"):
    1. **`_bgGotoSection` always landed on chunk 0 regardless of direction** —
       correct when moving FORWARD into a new chapter, wrong moving BACKWARD
       (a "previous page" press from a chapter's first chunk landed on the
       PREVIOUS chapter's first chunk, not its last — read as an unexpected
       forward jump relative to where a "one page back" press should land).
       Fixed: `this.idx = step > 0 ? 0 : built.chunks.length - 1`.
    2. **`_bgResync()` was firing on a screen-on blip, not just a genuine
       resume — the real explanation for "the first press after any period of
       screen-off jumps, then stabilizes, then jumps again after going back."**
       `_bgResync()` (wired to `visibilitychange`→`'visible'`) calls
       `skipPage()`, which sets `_bgMode = false` — exiting background chunk-
       reading entirely. It exists so a GENUINE unlock-and-return snaps the
       visible reader to where background audio reached. But the lock screen's
       OWN media widget can light up the screen (and, on some Android/WebView
       combinations, flip `document.visibilityState` to `'visible'`) without
       the app's Activity ever actually resuming — the phone is still locked.
       Every such blip silently exited `_bgMode`, so the NEXT nav press had to
       call `_bgEnter()`/`_bgAlign()` again, re-placing the reader via fuzzy
       text matching (see `_bgAlign`) — which almost never returns exactly
       where it left off, reading as "skipped many pages." This self-repeats
       exactly on the pattern reported: fine while actively tapping (screen
       staying lit, no blip), bad again after any lull long enough for the
       screen to properly go dark and re-light for the next tap. Fixed by
       cross-checking a NEW native-only signal that tracks the real Activity
       lifecycle instead of trusting WebView page-visibility alone:
       `TTS._bindAppStateListener()` (called once from `_mediaSetup`) listens
       to `@capacitor/app`'s `appStateChange` and keeps `TTS._nativeAppActive`
       live; the `visibilitychange` handler now only calls `_bgResync()` when
       `TTS._bgMode && TTS._nativeAppActive` — a screen-on blip with the
       Activity still backgrounded no longer exits background mode. Defaults
       to `true` so web (no such native signal, and no lock-screen widget
       either) is unaffected.
    Both fixed in a fresh-tab harness: backward chapter-boundary landing now
    lands on the last chunk (forward landing unchanged); the resync gate
    correctly skips `_bgResync()` when `_nativeAppActive` is false and fires
    it when true, verified against the exact conditional shipped in
    `index.html`, not just the logic in isolation. **Whether Android really
    does flip `document.visibilityState` for a lock-screen widget blip on this
    device is inferred from the reported symptom plus the confirmed existence
    of the `_bgResync`→`_bgMode=false` mechanism, not confirmed via Logcat —
    if the owner still sees the jump after this fix, that inference was wrong
    and the real trigger needs on-device diagnostics.**
  - **Two MORE bugs found on the NEXT device test round (2026-07-26,
    owner: "if I press pause, the page number returns to normal but now it
    skips pages again" + "there are not 300 pages in that chapter alone"):**
    1. **Raw chunk count made an implausible "page" total.** The
       `idx+1/chunks.length` fix above DID move by exactly 1 per press (fixing
       the +2/+0 bug), but a real chapter can have 300+ paragraphs, and "Page
       28 / 300" reads as obviously wrong even though it's internally
       consistent. Fixed by **bucketing a fixed number of chunks
       (`_BG_CHUNKS_PER_PAGE = 6`) into each displayed "page"**:
       `Math.floor(idx/6)+1` / `Math.ceil(chunks.length/6)`. This preserves the
       core guarantee — incrementing `idx` by exactly 1 changes a fixed-divisor
       floor-quotient by 0 or 1, NEVER more, a property of integer division
       regardless of paragraph length — while producing a believable total
       (300 chunks → "50 pages"). Verified: 20 consecutive presses across
       chunks of deliberately mismatched length never showed a delta outside
       {0, 1}.
    2. **`stop()`/`start()` threw away the exact background position on every
       pause, forcing a re-alignment on resume — the SAME class of bug as the
       `_bgResync` fix above, different trigger.** `stop()` (the lock-screen
       PAUSE button, via `TTS._mediaSetup`'s listener) cleared
       `_bgSection`/left `this.chunks`/`this.idx` to be overwritten by the next
       `start()`; `start()` (lock-screen PLAY) unconditionally called
       `loadPageText()`, which reads the CURRENTLY RENDERED page — except
       background reading never touches the visible rendition, so that page is
       still wherever the screen was when it FIRST locked, not where the audio
       actually got to. So every pause+resume cycle silently reset position AND
       forced the next nav press to `_bgEnter()`/`_bgAlign()` fresh again,
       reading as "skips pages" (this was likely responsible for at least some
       of the earlier "big jump" reports too, not just the `_bgResync` blip).
       Fixed: `stop()` now clears only `_bgMode` (the "actively reading" flag)
       and deliberately KEEPS `_bgSection`/`this.chunks`/`this.idx` intact.
       `start()` checks a new condition — `!_isForeground() && _engineNow() !==
       'web' && this._bgSection` — and if true, resumes background mode
       directly from the preserved position instead of calling `loadPageText()`.
       A genuine foreground play press (screen on, Activity resumed) is
       unaffected (`_isForeground()` is true, so it always takes the normal
       "read the visible page" path); the very FIRST play of a session (no
       `_bgSection` yet) also falls through correctly, since there is no
       background position to resume. `Reader.open()` now explicitly clears
       `TTS._bgSection` when opening a book, since `stop()` no longer does —
       otherwise a freshly opened book could inherit stale background state
       from whichever book was open before.
    3. **New shared `TTS._isForeground()` helper**, replacing raw
       `document.hidden` checks at every background-reading entry point
       (`_speak`'s forward-advance gate, `_mediaNav`, and the new `start()`
       check above) for consistency with the `_bgResync` fix's reasoning: on
       native it trusts `_nativeAppActive` over `document.hidden` alone, since
       the lock-screen widget can light the screen without the Activity truly
       resuming; on web it's just `!document.hidden` (no native signal, no
       lock-screen widget to blip it).
    Verified in a fresh-tab harness: the bucketed counter's 0-or-1 guarantee
    across mismatched chunk lengths; `_isForeground()`'s full truth table
    (web foreground/hidden, native active/inactive × visible/hidden); a
    simulated pause-then-resume-while-still-locked correctly restores the
    exact prior `_bgMode`/`idx`/chunk list rather than falling through to a
    mocked "stale visible page" state; a genuine foreground play press still
    takes the normal path; a first-ever play with no prior background position
    falls through safely. **Still not device-verified** — same caveat as
    always, no JDK/Android SDK in this environment.
  - **Icon fix confirmed working on device (2026-07-26) — but that same test
    surfaced a bug the previous fix introduced**: owner reported "if I press
    pause and play, they both show different numbers," plus "the page number
    doesn't align with what's being read" while actively playing.
    `_mediaPayload()` branched on `this._bgMode` — but `stop()` (above)
    deliberately clears `_bgMode` on every pause while KEEPING `_bgSection`/
    `this.chunks`/`this.idx` intact for `start()` to resume from. That meant
    pausing flipped the DISPLAY to the foreground branch (the stale, pre-lock
    rendered page count) while playing showed the background chunk-bucketed
    count — two different numbering systems, visibly different numbers,
    exactly matching the report. Fixed by keying `_mediaPayload()`'s branch on
    `this._bgSection` instead of `this._bgMode`: `_bgSection` is only ever
    non-null when there's a real background position to show, whether
    actively reading or merely paused, so it's the correct signal for what to
    DISPLAY (as opposed to `_bgMode`, which correctly answers a DIFFERENT
    question — "should a forward-advance route through `_bgAdvance` right
    now"). A pure foreground session (never backgrounded) is unaffected —
    `_bgSection` stays `null` the whole time, so it still takes the rendered-
    page branch. Verified in a harness: simulated playing → pause → resume
    all report the identical page string; a pure-foreground payload still
    takes the rendered-page branch untouched.
  - **THE UNIT WAS WRONG ALL ALONG — page position is now measured in
    CHARACTERS, calibrated against the chapter's real rendered page count
    (2026-07-26, third and final rework).** Owner: "it starts at page 23, I
    press back one page, it now displays page 30 ... then I go forward, the
    voice reads something different each time but the display stays 30."
    Two separate defects, one root cause — counting the wrong thing:
    1. **The 23 → 30 jump at handover.** Rendered pages are equal-AREA (hence
       roughly equal-CHARACTER); chunks are PARAGRAPHS, whose length varies
       enormously. So paragraph-position and text-position diverge wildly
       within a chapter — measured in a harness on a deliberately uneven but
       realistic chapter (long descriptive paragraphs early, short dialogue
       later), **53% of the way through the TEXT was only 14% of the way
       through the PARAGRAPHS.** No fixed chunks-per-page constant can
       reconcile them, so the number necessarily jumped the moment background
       mode took over from the rendered count.
    2. **The display freezing.** With 6 chunks bucketed per page, a one-chunk
       press changed `floor(idx/6)` only once every six presses — so five
       presses in six moved the audio (owner heard it) while the number sat
       still, reading as a dead button.
    Fixed by rebuilding the model around characters:
    `_bgReindex(spineIdx)` builds a prefix-sum character index over the
    chapter's chunks, and — crucially — **when that section is the one epub
    still has laid out, calibrates chars-per-page from its ACTUAL
    `displayed.total`** (device/font specific; cached in `pl_cpp` and reused
    for chapters never rendered while the screen is off, with a 1800 fallback
    and a 300–6000 sanity clamp so one freak section can't poison later ones).
    `_bgPageOf`/`_bgPageCount`/`_bgIdxForPage` then convert between character
    offset and page. **`_bgNav` now seeks a whole PAGE per press**
    (`_bgIdxForPage(currentPage ± 1)`) instead of one chunk, with a guard
    forcing `idx ± 1` if a paragraph longer than a page would otherwise leave
    the seek on the same chunk — so a press always both moves the audio and
    changes the number by exactly 1. Deleted `_BG_CHUNKS_PER_PAGE`.
    Verified in a harness on the uneven-chapter model above: calibration
    yields ~1953 chars/page and a 43-page count matching the real rendered
    total; the handover now reports "Page 23 / 43" (the old model said 30);
    the exact reported sequence (back one, then four forward) gives deltas
    `-1,+1,+1,+1,+1` with the audio index moving on every single press; plus
    a >1-page paragraph still advances, an absurd calibration is rejected
    without persisting, a missing index degrades to blank, and a learned
    calibration is correctly reused for a chapter that is never rendered.
  - **"Previous chapter" now always opens at page 1, matching "next chapter"
    (2026-07-26).** Owner: moving backward a chapter landed on the LAST page
    of the previous chapter instead of its first. `_bgGotoSection`'s backward-
    lands-on-the-last-chunk behavior (added earlier the same day, see above)
    is correct for a PAGE-button press that happens to spill past the start of
    a chapter — that's the natural continuation of "one page back" across the
    boundary — but wrong for an explicit chapter jump, which should always
    open at the start regardless of direction. `_bgNav` now passes a third
    `atStart` argument (true only for `nextChapter`/`prevChapter`) that
    overrides the backward case to land on chunk 0 too; the page-button
    fallthrough path (`step` alone, no explicit chapter action) is unchanged.
    Verified in a harness: the chapter buttons land on chunk 0 in both
    directions, while a "previous page" press that crosses into the prior
    chapter still lands on its last chunk as before.
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
  next off-screen COLUMN) crossed pages but NOT sections, and stalled on short
  "sliver" last pages. **Replaced with reading the book's TEXT straight from the
  spine, decoupled from visual rendering** — flows across pages AND chapters with
  the screen off. When `document.hidden` and not the Web-Speech fallback,
  `_speak()`'s forward-advance calls **`_bgAdvance()`** (async):
  - **First step**: switch from the visible page's geometry chunks to the WHOLE
    current section's chunks via **`_currentSectionChunksWithNodes()`** — reads
    the ALREADY-RENDERED iframe document, sync, no reload. **Never call
    `section.load()` on the currently-rendered section** — verified via harness
    that a second `.load()` on it corrupts epub.js's per-section document
    reference: a later `display(cfi)` into that section reports the right page
    number but the `.epub-container` never actually scrolls (`scrollLeft` stuck
    at 0 while `currentLocation()` claimed page 19/21). `_bgAlign()` finds where
    in the section we already are (matches a visible-page chunk inside the
    section chunks, continues just past it) so there's no re-read or skip; if it
    can't align it fails closed (stops).
  - **Section boundary**: `_loadSectionChunksWithNodes(idx+1)` loads the NEXT
    spine section's text via `section.load(book.load…)` (no render) — safe here
    since that section isn't rendered yet — and reads from its top, crossing the
    chapter boundary a frozen visual turn can't. Skips empty/nav sections; stops
    at end of book.
  - **`_sectionSegments(docOrEl)`** extracts block-grouped segments like
    `loadPageText` but with no geometry filter; it must handle BOTH a Document
    and the bare `<html>` element (what `section.load()` returns), AND
    **uppercase tag names** — spine sections parse as XHTML where `tagName` is
    lowercase (`h1`,`p`), so without `toUpperCase()` every node fell through to
    `<body>` as one giant segment (heading glued to the first paragraph). We do
    NOT `unload()` loaded sections (shared with the live rendition).
    **`_chunksFromSegments` carries `node: seg.block`** on every chunk (harmless
    unused property for normal foreground reading) — background reading uses it
    to build a precise CFI for the exact chunk being read.
  - **Progress is saved DURING background reading (`_bgSaveProgress`, called
    after every chunk).** The only OTHER place progress saves is
    `Reader._onRelocated`, which fires on a visual page turn — background reading
    deliberately avoids those, so without this, `State.progress` stayed frozen at
    wherever the phone was locked. Any interruption (unlock, or Android reclaiming
    the process) then reopened the book at that STALE position and re-read it
    from the top — owner-reported as "pressing play restarts the page," both from
    a live unlock AND from reopening the app fresh, regardless of how far the
    audio had actually gotten. `_bgSaveProgress` builds a CFI via **`_bgCfi()`**
    (`section.cfiFromRange()` on a Range over the current chunk's `node`) and
    writes it into `State.progress[bookId]` like a normal page turn would.
  - **`Reader._persistPosition()` must NOT run while background reading holds a
    position — it was silently rolling progress back for a whole listening
    session (owner-reported 2026-07-28: "listened ~40 min locked, reopened, was
    brought back many pages").** That handler saves
    `State.rendition.currentLocation()` — the VISIBLE reader — which during
    background reading is frozen wherever the screen locked, while the audio may
    be chapters ahead. It is wired to `visibilitychange`→hidden, `pagehide` AND
    `Reader.close()`, and a locked phone lights and darkens repeatedly over a
    long session (the lock-screen media widget alone does it), so every one of
    those fired an unconditional overwrite of the good per-chunk background CFI
    with the stale lock-point one. `_bgSaveProgress` was working perfectly the
    whole time; its writes were just being clobbered afterwards. Fixed by
    branching on `TTS._bgSection` (non-null ⇔ a background position exists, and
    it is by definition more current than the frozen visible one): that path now
    calls `TTS._bgSaveProgress()` and returns, so the last write before an app
    kill is the audio's true position. `_bgSection` is the right guard because
    `skipPage()` clears it on any real visual turn/seek — including via
    `_bgResync()` on a genuine unlock — so a user who has returned to normal
    foreground reading is unaffected. Verified in a harness: the background CFI
    survives ten simulated screen blips and a `Reader.close()`, while a
    pure-foreground session still saves the visible location as before.
  - **On unlock** (`visibilitychange`→visible) **`_bgResync()`** brings the
    visible reader to the EXACT chunk the audio reached (same CFI as
    `_bgSaveProgress`, not just the chapter's first page) via
    `skipPage(()=>display(cfi))`, falling back to the section's href only if a
    CFI genuinely can't be built. Verified in a harness (60-paragraph, 21-page
    synthetic chapter): landed on page 19/21 with the visible text matching the
    exact target chunk, and the same worked crossing into a chapter that had
    never been rendered before.
  - `_bgMode`/`_bgSection` reset on `start()`/`skipPage()`/`stop()`; the whole
    path is gated on `document.hidden`, so **foreground reading is unchanged**.
    Only the neural/native audio path backgrounds (Web-Speech can't).
  - **DEVICE-VERIFIED (2026-07-22, owner-confirmed "seems to work"):** logic
    was already confirmed in a browser harness against synthetic epubs; the
    owner then tested background reading + the press-play pre-pause fix (next
    note) together on-device and confirmed it works.
  The old `_vpage`/`_resyncVisual`/`_resyncing`/`_armResyncWatchdog`/`_cancelResync`
  machinery from the virtual-page attempt was **REMOVED 2026-07-22** (including
  `loadPageText`'s `vpage` parameter and the `_onRelocated` resync-hijack
  branch that used it) — verified in a browser harness that normal page
  reading and `start`/`stop`/`skipPage` all still work correctly.
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

## Localization (2026-08-08 — Settings-only first pass, expanded to the whole app same day)

BACKLOG.md section B: Bill 96 covers the app's own interface for Québec
consumers, not only the marketing/legal pages (which already had French
versions — `home-fr.html`/`terms-fr.html`/`privacy-fr.html`). Shipped as a
Settings-only pilot first; the owner tested it and pushed back the same day
("many things remain in English, even if the app is switched to French...
all text, static or popup, should be in French"), so it was expanded to
cover the entire app in the same session.

- **`STRINGS` (en/fr dictionary) + `I18n`** (`index.html`, right after
  `Theme`): `I18n.lang()` resolves from `pl_lang` — **the same key the
  marketing pages already use** (`LEGAL_FR.md`), so a choice made on
  phonoleaf.com and a choice made inside the app agree — falling back to
  `navigator.languages` (`fr*` → French) when nothing's saved yet, matching
  the marketing pages' own auto-detect rule. `I18n.t(key, vars)` looks up the
  current language, falling back to English for a missing key; `{name}`-style
  placeholders only, deliberately not a template engine. The dictionary grew
  from ~25 Settings-only keys to ~150 covering sign-in, Home, Library, Stats,
  the Reader chrome, the tab bar, and every modal.
- **Two complementary mechanisms, matched to how each surface was already
  built**, rather than converting everything to one style:
  - **`data-i18n`/`data-i18n-title`/`data-i18n-placeholder` attributes** for
    genuinely static markup (sign-in, the Home/Library/Reader/Settings
    shells, the tab bar, every modal's fixed chrome — titles, labels, button
    text, input placeholders). `I18n.apply(root)` walks a subtree and fills
    them from the dictionary.
  - **Direct `I18n.t()` calls inside template strings** for JS-generated
    content (Library's grid and empty states, Stats' whole `render()` +
    `_breakdown()`, `VoiceModal`/`LangPacksModal`/`ChapterModal`/
    `FolderBrowser` rows, every `toast()` call). These re-render from scratch
    on every call anyway, so there's nothing for `data-i18n` to attach to —
    reusing it here would just mean writing `I18n.apply()` after every
    `innerHTML =` for no benefit over calling `I18n.t()` directly in the
    template.
- **`I18n.setLang()` now sweeps the whole app, not just Settings**:
  `I18n.apply(document.body)` (every static shell, whichever are hidden at
  the moment) plus explicit re-renders of `Home`/`Library`/`StatsPage`/
  `Settings`, each wrapped in its own `try/catch` — `Library.render()` has no
  fallback for `State.books` being `undefined` (unlike Home/Stats, which
  already default it to `[]`), so switching language before the library has
  ever loaded must not throw.
- **`window.addEventListener('load', ...)` now opens with
  `I18n.apply(document.body)`**, before `App.init()` runs. Without this a
  French-locale device would see an English sign-in screen — the only place
  the language toggle exists is Settings, which is unreachable before
  signing in, so device auto-detect previously had no effect until *after*
  first login and a Settings visit.
- **Genuine French elision bug caught in review, not by guessing**: the
  Stats empty-state template was `'Aucune donnée de {type}...'` with
  `type_author: 'auteur'`, producing the ungrammatical "donnée de auteur".
  Moved the connector into each type value instead (`type_author:
  'd’auteur'`, `type_book: 'de livre'`, ...) so the template itself carries
  no connector — verified by rendering the actual breakdown table and
  reading the output, not by inspecting the source strings alone.
- **The language toggle lives in Settings** (`#lang-seg`, styled for free by
  the existing generic `.seg`/`.seg button` rules — not `#theme-seg`-scoped).
- **Still intentionally not covered**: the raw diagnostic text inside
  `BugReport._diagnostics()` (device/build info folded into an email body,
  not app UI) and `MyData.export()`'s JSON file `note` field (metadata
  inside a downloaded file, read by whoever the user shares it with, not
  displayed in the app). Both are content the app *produces* for an email or
  a file, not interface text the user reads on screen.
- Verified in a browser harness across languages and views: the sign-in
  screen renders fully in French on a cold boot (no prior Settings visit)
  purely from `navigator.languages` detection; Home, Library, and Stats
  (including the breakdown table's headers and the elision fix) render
  correctly with synthetic data; `pl_lang` persists and the toggle
  round-trips; no new console errors (the one error present — a blocked
  fetch to `accounts.google.com/gsi/client` — is the pre-existing, expected
  result of no network access in this sandboxed environment, unrelated to
  this change). Not device-verified — same caveat as everything native
  in this file, though nothing here touches native code.

**Bug fixes on the same feature (2026-08-08, owner device testing):**
- **The Settings "Natural voice" row named the underlying engine — owner:
  "users don't need to know the technical details."** `natural_voice_kokoro`/
  `natural_voice_piper` said "Kokoro, high quality" / "Piper, medium
  quality"; dropped the engine names entirely (now "high quality" /
  "standard quality" — "standard" to match the picker's existing
  `voice_tier_standard` badge wording rather than introduce a third term for
  the same tier).
- **Voice picker names stayed English even in French — a real gap, not a
  missed `data-i18n` spot.** `PIPER_VOICES`/`KOKORO_VOICES` baked the whole
  label ("Ava · English (US) female") into a literal string per entry, so
  there was nothing for `I18n.t()` to intercept. Restructured every entry to
  `[id, name, sid, model, langKey, genderKey]` — `name` (Ava, Emma, Thorsten,
  ...) stays a literal proper name and is never translated; `langKey`/
  `genderKey` are `STRINGS` keys composed live by a new `voiceLabel(entry)`
  helper, so the label re-renders correctly on every language switch instead
  of being frozen at parse time. New `gender_female`/`gender_male` keys;
  reused the existing `lang_en_us`/`lang_en_gb`/`lang_fr`/`lang_de`/`lang_es`
  keys already built for the Language Packs catalog — also fixes a small
  pre-existing inconsistency where Kokoro's labels said bare "US"/"UK" while
  Piper's said "English (US)"/"English (UK)" for the same accents; both now
  match. `activeVoiceLabel()` (Settings' voice sub-label) had the same
  bug for its own "System default" fallback text — was a raw English
  literal, not `I18n.t()`, on top of the same baked-label problem. Every
  read site (`VoiceModal`'s two render branches, `selectNative`'s toast,
  `activeVoiceLabel`, the `PACK_NOT_DOWNLOADED` fallback-voice toast) had to
  move from reading `v[1]` as a full label to calling `voiceLabel(v)` —
  `v[1]` is now just the name. `_voiceSid()`/`_voiceModel()` (indices 2/3)
  and every `id`-keyed lookup (index 0) are unaffected by the shift.
- Verified in a browser harness: every Piper voice (US/UK/French/German/
  Spanish) and every Kokoro voice produces the correct composed label in
  both languages, flips live on `Settings.setLang()` with no reload, the
  name itself never gets translated, and the Settings quality row shows no
  engine name in either language while still correctly distinguishing
  Kokoro from Piper by tier wording alone.

## Conventions

- **Escape all externally-sourced strings** (file names, error messages, voice
  names, chapter titles) with `esc()` before putting them in `innerHTML`. Prefer
  passing indices to inline handlers over interpolating raw values.
- Match the existing terse, dependency-free style. No frameworks, no build.
- **Clickable rows built as `<div onclick=...>` need `role="button"
  tabindex="0"`** (a plain `<div>` isn't keyboard-focusable or announced by a
  screen reader as interactive). A single delegated `keydown` listener (added
  2026-08-07, right after the `input`/`change` scrubber listeners) makes
  Enter/Space activate any element with `role="button"` by calling `.click()`
  on it — add the two attributes to a new row and it works for free, no
  per-row keyboard handler needed. Real `<button>`s and inputs don't need this.

## Accessibility (audited 2026-08-07)

BACKLOG.md section F asked for a pass on screen-reader support, respecting
system text-size/motion settings, and tap target size. Findings and fixes:
- **Pinch-to-zoom was disabled app-wide** (`user-scalable=no,
  maximum-scale=1.0` in the viewport meta) — a WCAG 1.4.4/1.4.10 failure that
  blocked the one thing most likely to actually help a low-vision user, on
  top of the 74 already-`rem`-based font sizes doing nothing for anyone who
  couldn't zoom in the first place. Removed. The one real reason it might
  have been there — the reader's double-tap-to-play gesture racing the
  browser's own double-tap-to-zoom — is handled surgically instead via
  `touch-action: manipulation` on `#reader-touch` (kills double-tap-zoom
  there specifically; pinch-zoom still works everywhere, including in the
  reader).
- **No `prefers-reduced-motion` support anywhere.** Added one blanket
  `@media (prefers-reduced-motion: reduce)` override (clamps every
  `animation`/`transition` duration to ~0 via `!important`) rather than
  auditing each of the app's ~20 individual transitions — confirmed safe
  first: nothing in the app listens for `transitionend`/`animationend`, so no
  functionality depends on an animation actually taking time.
- **Most gesture-only actions already had labelled button equivalents**
  (page turn: `.reader-edge`/`.ctrl-btn.nav`; play/pause: `#play-btn`; both
  already carried `aria-label`s) — the swipe/double-tap gesture surface was
  never the only way to do either. What was missing: several **clickable
  `<div>`/`<a>` rows with no keyboard/screen-reader semantics at all** — book
  cards, the Home "jump back in" cards and its "See all" link, the Home
  stats tile, chapter-jump rows, the folder browser's rows and breadcrumbs,
  and the voice picker's rows. Fixed with `role="button" tabindex="0"` (see
  the Conventions note above for the shared keydown handler) plus
  `aria-label`s where the visible text alone wouldn't say what gets opened
  (e.g. a bare cover image), and `aria-current="true"` on the voice picker's
  currently-selected row. Left alone: modal backdrops (tap-to-dismiss is a
  convenience on top of a real Cancel/close button, not the only way to
  close anything) and the Stats page's non-interactive tiles.
- One real icon-only control had no accessible name at all: Library's
  refresh button only had a `title` (unreliable on touch/screen readers, no
  hover to trigger it) — added `aria-label="Refresh library"`.
- Existing tap targets were already reasonable (`.ctrl-btn` 40–50px,
  `.tab` a full flex column, `.icon-btn` ~35px effective with its padding) —
  no changes made there.
- **Not done, flagged as a bigger feature per BACKLOG.md**: follow-along
  word/sentence highlighting while reading aloud. Genuinely useful for
  dyslexia/ADHD but a real build (needs word-level timing from the TTS
  engine), not part of this pass.
- Verified in a browser harness: the delegated keydown listener fires
  `.click()` on Enter for a `role="button"` element; the sign-in screen
  renders with no console errors after the viewport/CSS changes; the
  `touch-action` and viewport values are exactly as intended. Not
  device-verified — same caveat as any native-adjacent claim in this file,
  though nothing in this pass touches native code.

## Bug fixes (2026-08-08, owner device testing)

- **"Delete my data" didn't actually delete voice packs — and that silently
  broke onboarding too.** `MyData.deleteAll()` only ever cleared `pl_*`
  localStorage keys and the `phonoleaf` IndexedDB; it never touched the
  native plugin's downloaded voice models (Android filesDir, entirely outside
  that scope). So after wiping data and signing back in, a previously
  downloaded pack (e.g. "us") was still on disk, and
  `VoicePacks.maybeOnboard()` correctly (by its own, still-valid logic for
  the upgrade case) saw a pack already downloaded and skipped the
  onboarding prompt — which from the owner's side looked like "I went
  through onboarding again and was never asked to download a voice."
  Fixed: `deleteAll()` now also calls the native plugin's `deletePack` for
  every `VoicePacks.CATALOG` entry (harmless no-op for ones that aren't
  downloaded — confirmed in `PhonoLeafTtsPlugin.kt`'s `deletePack`, a
  `deleteRecursively()` on a folder that doesn't exist just returns false).
  `privacy.html`/`privacy-fr.html`'s "Erasure" bullet updated to say so —
  it previously only mentioned local storage and cached covers.
- **Stats' empty-state and "loading" sentences were being clipped to a few
  words** ("No author data yet - press pl..."). Root cause: both reused
  `.aname`, a class sized for a short author/book/genre name in a 4-column
  data-row grid (`nowrap` + `ellipsis`), for a full sentence that has no
  data to share the row with. Added `.aname-msg` (`grid-column: 1 / -1`,
  `white-space: normal`) and applied it alongside `.aname` on both
  `StatsPage._emptyBreak()` and the "Page counts are loading in the
  background…" row. Verified in a browser harness at a 375px viewport: the
  full sentence now renders unclipped and stays inside the viewport.
- **The reader's bottom progress readout showed a bare "· 45%" with nothing
  to its left.** `Reader._onRelocated` set `#tts-chapter`'s text to
  `` ` · ${pct}%` `` — a leading separator with no partner text, unlike
  `#rs-chapter` in the top bar which legitimately joins
  "Chapter Name  ·  Page X / Y". Fixed to `` `${pct}% through the book` ``,
  matching what this element was already documented elsewhere in this file
  as showing.
- **Library search missed real matches** (typing "The anth" didn't find
  "The Anthropocene Reviewed"). `Library.filter` did a literal substring
  match against the raw Drive filename, which commonly uses `.`/`_` where a
  typed query uses a space (e.g. `The_Anthropocene_Reviewed.epub`), so a
  query with a space never matched a filename without one. Added
  `Library._normalize()` (lowercases, collapses `.`/`_`/`-` runs to a single
  space) applied to both the query and the search haystack, and broadened
  the haystack to also include the epub's captured `Meta` title/author when
  known — covers both the separator mismatch and the case where a Drive
  filename's word order doesn't match the real title (also makes author-name
  search work as a side effect). Verified in an isolated harness across
  underscore/dot-separated filenames, a Meta-title-only match, and a
  no-match case.
- **Library covers were being cropped ("halved").** `.book-cover img` used
  `object-fit: cover` inside a fixed 2:3 box — a real cover's actual
  proportions rarely match 2:3 exactly, and `cover` crops off whatever
  doesn't fit rather than shrinking to show it all. Changed to
  `object-fit: contain`, which always shows the whole image, letterboxed on
  `--cover-fallback` (already the box's background) when the ratio doesn't
  match. Verified in a browser harness with a synthetic square test image
  (labelled top/bottom): both edges are now visible with letterbox bars,
  where `cover` would have cropped one off. Home's smaller cover thumbnails
  (`.hh-cover`/`.cr-cover`, ~54–120px `background-size: cover`) weren't
  touched — not what was reported, and cropping matters less at that size.
  **THIS WAS A MISDIAGNOSIS — `object-fit` was never the cause. See the
  "Library grid crushed its rows" entry below for the real bug (2026-08-09).**
  The `contain` change is kept (it does deliver "show the whole cover" for a
  cover whose ratio isn't exactly 2:3) but it fixed nothing on its own, and
  three rounds of device reports kept coming back cropped because the actual
  cause was untouched.

## Library grid crushed its rows, clipping covers (2026-08-09)

**The real cause of the long-running "book covers are cropped/halved"
report — and the owner diagnosed it, after two wrong fixes from me.** Owner:
*"Is it maybe because the size of the book covers is adapting to the number
of books and instead of adding a scrollbar, it just resizes the book covers
to make sure they all fit in one page?"* Exactly right.

- **`.books-grid` never scrolled.** Measured in a browser at a 375px
  viewport with a synthetic library: `scrollHeight` stayed pinned to
  `clientHeight` (700px) at EVERY book count. Instead of overflowing, the
  grid shrank its rows so every book fit on one screen — card height 292px
  at 4 books (correct), then 190px at 6, 138px at 8, 46px at 20, **15px at
  40** — while `.book-cover` stayed at its correct 241px throughout.
  `.book-card { overflow: hidden }` then clipped the cover to the leftover
  height. At 8 books that's 138/241 ≈ 57%, i.e. literally "halved."
- **Why the severity looked random across reports:** it tracked library
  size, not anything about the cover images. That is also why it survived a
  genuine uninstall + reinstall, and why no amount of `object-fit` tuning
  helped — the image was never being cropped by `object-fit` at all, the
  CARD around it was being crushed.
- **Mechanism:** `.book-cover` derives its height from `aspect-ratio: 2/3`
  against a percentage width. An aspect-ratio-derived height is a
  *compressible* contribution when `auto` rows are sized against a definite
  container height — and this grid is `flex: 1` inside a flex column, with
  `overflow-y: auto`, which makes its flex `min-height: auto` resolve to 0,
  so it cannot push back against being sized to the available space. The
  rows were therefore squeezed to fit rather than overflowing.
- **Fix: `grid-auto-rows: max-content` on `.books-grid`.** Pins every row to
  its true content height, so cards render at their natural 295px and the
  grid overflows into a normal scroll (`scrollHeight` 6312 for 40 books
  instead of a crushed 700). One line; `align-content: start` alone was not
  enough and neither was anything about the image.
- Verified in the browser across 2/4/6/8/10/14/20/40 books (card height
  constant at 295, `clipped: false`, `scrolls: true` throughout), plus the
  `.loading`/`.empty` states still spanning both columns at their 200px
  `min-height`. `grid-auto-rows: min-content` behaves identically here;
  `max-content` was chosen as the clearer statement of intent.
- **Process lesson, worth keeping:** the first two attempts reasoned from
  the *description* of the symptom ("cropped") straight to the property that
  usually causes cropping (`object-fit`), and then — when the report came
  back unchanged — blamed the build/deploy pipeline rather than re-opening
  the diagnosis. What actually settled it was measuring the rendered DOM at
  several library sizes instead of inspecting the CSS and reasoning about
  it. When a fix that is verifiably correct in isolation doesn't move a
  device symptom, that is evidence the diagnosis is wrong, not evidence the
  fix didn't ship.

## Library view-mode switcher: 2/3/4-col grid + table/list (2026-08-09)

Owner request, off the back of the grid-crush fix above: *"I just thought
that it could be interesting for users to have multiple ways of seeing
their covers... 2 per row / 3 per row (locked ratio) / 4 per row (locked
ratio) / Table of Book Name, Artist name."*

- **Four icon toggles in `.lib-header`** (`#lib-view-toggle`, next to the
  existing refresh button), styled as a `.seg`-like segmented group rather
  than independent `.icon-btn`s since it's a mutually-exclusive choice.
  `Library.setView(v)` persists to `pl_libview` (`'2'`|`'3'`|`'4'`|`'table'`,
  same pattern as `pl_speed`/`pl_theme`) and re-renders.
- **Grid modes are explicit fixed column counts** (`repeat(2/3/4, 1fr)` via
  `#books-grid[data-view]`), not the previous responsive
  `auto-fill, minmax(130px, 1fr)`. "2 per row" needed to be a well-defined,
  switchable option rather than something that could silently become 3+ on
  a wider viewport — the app is portrait-locked/mobile-first anyway (see the
  native-shell orientation note), so this is a behavior-neutral change on a
  phone and a more predictable one on a wider browser window. Cover ratio
  ("locked ratio" in the request) was already guaranteed by
  `.book-cover { aspect-ratio: 2/3 }` regardless of column count, so no
  separate work was needed for that part.
- **Table mode** (`#books-grid[data-view="table"] { display: flex;
  flex-direction: column }`) is a new compact `.book-row` layout: small
  thumbnail, title, author, thin progress bar. Both the grid cards and table
  rows now go through one shared `Library._itemHTML(b, i, isRow)` — same
  title/progress/cover resolution either way, just a different wrapper
  class — instead of duplicating that logic into a second render path.
- `.loading`/`.empty` (which rely on `grid-column: 1/-1` under the grid
  modes) needed no separate table-mode markup — that rule is simply unused
  and harmless under `display: flex`.
- Verified in a browser harness with a synthetic 9-book library: correct
  column counts and zero clipped covers in all three grid modes, the table
  row's progress bar renders at the right width, the empty state still
  renders correctly in table mode, and `pl_libview` persists and re-syncs
  the toggle's highlighted button across a reload.
- **Alphabet scrubber shipped the same day** — see the entry directly below.

## A-Z fast-scroll scrubber for the library (2026-08-09)

The follow-up flagged in the view-switcher entry above. `#az-scrub`, a
narrow vertical letter strip pinned to the grid's right edge (iOS Contacts
pattern): press or drag jumps straight to the first book whose resolved
title starts with that letter, snapping to the **nearest available**
letter (not doing nothing) when the one under your finger has no books —
most libraries don't have something under every letter.

- **Necessary side effect: the library is now genuinely sorted
  alphabetically**, not just the scrubber's internal letter map. A scrubber
  is meaningless against a list that isn't actually in that order. Sort key
  is `Library._sortTitle(b)` — `Meta.get(b.id)?.title` (the epub's own
  captured title, same source `Meta.fetchAll` already uses for Open Library
  lookups) falling back to `Meta._cleanName(b.name)` — instead of Drive's
  raw `orderBy: 'name'` filename order, which is close but not the same
  (e.g. "Reviewed, The Anthropocene - John Green.epub"). Both view-mode
  render paths (grid cards, table rows) already shared one `_itemHTML`
  helper from the switcher work, so this sort sits once ahead of it rather
  than needing to be duplicated.
- **Gated**: hidden below `Library._AZ_THRESHOLD` (20 books — not worth the
  clutter on a small library) and hidden during an active search
  (`Library._q`) — a letter index over a filtered subset doesn't mean
  anything reliable, most letters would be empty or misleading.
- **`.books-grid` gained a wrapping `.books-grid-wrap`**
  (`position: relative; flex: 1; min-height: 0`) so the scrubber can be
  pinned to the grid's own box, independent of scroll position, without
  also overlapping `.lib-header`/`.lib-search` above it. `.books-grid`
  itself moved from `flex: 1` to `position: absolute; inset: 0` against
  that wrapper — same effective sizing as before (still constrained to the
  available flex space, still needs `grid-auto-rows: max-content` for the
  same reason as the grid-crush fix above), just now sized against the
  wrapper's box instead of directly by the parent flex column.
- Bucket letters are `#ABCDEFGHIJKLMNOPQRSTUVWXYZ` — anything not starting
  with A-Z (a title beginning with a digit, say) buckets under `#`.
- Verified in a browser harness with a synthetic 41-book library
  deliberately built with gaps (no K/L/Q titles) and one numeric title: the
  letter index correctly marks the gap letters unavailable and the numeric
  title under `#`; dragging onto a gap snaps to the nearest real letter and
  scrolls the matching titles to the top of the visible area; the bubble
  and active-letter highlight show/hide correctly across REAL dispatched
  `mousedown`/`mousemove`/`mouseup` events (not just calling `_scrubTo`
  directly, which would only prove the math and not the event wiring); and
  the strip is hidden both below the 20-book threshold and during an active
  search, reappearing once the query clears.

## Offline reading — cached library list + saved books (2026-08-10)

Implements `BACKLOG.md` section C ("Offline (bug + differentiator)"). Root
cause of the pre-existing "no books offline" behavior: the library list is a
live Drive API call and book bytes are downloaded from Drive at read time
with nothing cached — offline, the Drive call fails, the library goes empty,
and nothing can open. Only the app shell and the on-device voice worked
offline before this. Triggered off the store-listing prep work: the drafted
`STORE_LISTINGS.md` copy already claimed offline listening, gated on this
feature actually shipping — owner chose to build it now rather than strip
the claim.

- **Library file-list caching** (`Library._cacheBookList`/`_cachedBookList`,
  localStorage under `pl_libcache`, keyed to the folder id): after every
  successful `Drive.listEpubs()`, the list is cached; `Library.load()`'s
  catch block falls back to it — but only if the cached folder id matches
  the CURRENT one, so switching folders while offline can't show a stale
  different folder's books. A cache hit but 0 books legitimately falls
  through to the normal error/retry path.
- **`BookCache`** (right after `CoverCache` in `index.html`) — a
  **separate** IndexedDB database (`phonoleaf-offline`, store `books`) from
  `CoverCache`'s `phonoleaf` DB, deliberately isolated so nothing here can
  ever risk that DB's existing schema/migration history. Web: raw
  `ArrayBuffer` stored directly (structured-clone handles it, no encoding
  needed). Native: `@capacitor/filesystem` (already a dependency, used
  elsewhere for the bug-report photo feature) — `Directory: 'DATA'`
  specifically, **not** `'CACHE'`, which the OS can purge under storage
  pressure and would silently undo a deliberate "save for offline." The
  Capacitor bridge only accepts base64, so buffers are chunk-encoded
  (32KB at a time via `String.fromCharCode.apply`) to avoid a stack overflow
  from spreading a huge typed array as call arguments — the same class of
  concern as the TTS pipeline's "1MB base64 froze the WebView" lesson, though
  here it's a one-time save action (not per-sentence, repeated) so the
  tradeoff of using the standard Filesystem base64 API (rather than building
  a dedicated streaming native plugin method, like the TTS WAV-file-path
  fix did) was judged acceptable for v1.
  A lightweight **localStorage index** (`pl_offline_books`, id → `{bytes,
  ts}`) makes `has()`/`list()` synchronous and cheap — the library grid can
  check "is this saved?" for every visible card on render without touching
  IndexedDB/Filesystem at all.
  **`get()` never throws** (returns `null` on any failure — missing,
  corrupt, plugin unavailable — so a cache-read problem can never break
  opening a book, it just falls through to a fresh Drive download).
  **`set()`/`remove()` DO throw** on failure — deliberately, so the explicit
  "save for offline" button can tell the user it actually failed instead of
  falsely claiming success; the passive auto-cache-on-open call site is the
  one that chooses to swallow failures, via its own `.catch(() => {})`.
- **`Reader.open()`** now checks `BookCache.get(book.id)` before ever
  calling `Drive.download()` — a hit skips the network (and re-download)
  entirely; a miss downloads as before and fire-and-forget auto-caches the
  fresh bytes in the background (`BookCache.set(...).then(() =>
  Library._refreshOfflineBadge(...)).catch(() => {})`), never blocking
  getting into the book. The failure path now distinguishes the specific
  "offline and never saved" case (`!navigator.onLine && !BookCache.has(id)`)
  with a clear message, instead of surfacing a raw fetch-error string for
  what's actually the most common offline failure.
- **Explicit save/remove control + availability badge**
  (`Library._offlineBtnHTML`/`_refreshOfflineBadge`/`toggleOffline`) on every
  library card — covers "I know I'm about to lose signal," complementing
  the passive auto-cache-on-open. Grid mode: an absolute-positioned corner
  overlay on the cover (`.book-cover .offline-btn`, translucent dark
  background so it reads against any cover art color). Table mode: the same
  button as a normal trailing flex child of `.book-row`, no special
  positioning needed. Icons are simple stroke-based shapes (a down-arrow +
  tray, a checkmark) — deliberately NOT hand-guessed complex Material path
  data, per the lesson from the notification-icon saga elsewhere in this
  file about not guessing icon designs; low risk here regardless since
  these render as normal RGBA SVG in a webview, not subject to Android's
  alpha-silhouette extraction that made that earlier case unforgiving.
  `Library._itemHTML` gained one shared `_offlineBtnHTML(id)` call used by
  both the grid-card and table-row paths (same button markup, placed
  differently via the existing `isRow` conditional).
- `sw.js`'s own header comment ("book bytes... intentionally NOT cached")
  was stale after this — updated to clarify that's still true of the
  service worker's own Cache Storage specifically, while the app itself now
  maintains a separate offline cache via `BookCache`.
- Verified in a browser harness: `BookCache` round-trips correctly
  (including a 2MB buffer) on the IndexedDB path; the save/remove toggle
  updates the badge class, aria-label, and cache state correctly in both
  directions, visually confirmed via screenshot in both grid and table
  view; the library-list cache round-trips and `Library.load()` correctly
  falls back to it when `Drive.listEpubs()` throws; the offline-vs-generic
  error message selection logic picks correctly across all three states
  (offline+uncached, offline+cached, online+generic-failure).
  **DEVICE-VERIFIED 2026-08-10 — owner confirmed download + offline mode
  both work.** No JDK/Android SDK in this environment, so the native
  `@capacitor/filesystem` path was written carefully against the same
  `writeFile`/`readFile` API shape already proven working for the bug-report
  photo feature but never compiled here — the owner's own on-device test is
  what confirmed it. `STORE_LISTINGS.md`'s offline claim is now safe to
  publish; its gating note should be treated as resolved.

## Cloudflare Web Analytics on the marketing pages only (2026-08-10)

First piece of the marketing-foundation work (`BUSINESS.md` item 6). Owner
picked analytics as the starting point; chose Cloudflare Web Analytics since
Cloudflare already runs phonoleaf.com's DNS — free, cookieless/privacy-
respecting by design, no new vendor relationship.

- **Deliberately scoped to `home.html`/`home-fr.html` only — not
  `index.html` (the signed-in app), and not `privacy.html`/`terms.html`
  either.** The first cut added the beacon to all six marketing-adjacent
  pages, but `privacy.html`/`terms.html` (+ `-fr` variants) are staged into
  the native Android app's bundled assets and reachable from in-app Settings
  via a plain same-context `<a href>` (see the Tech-stack note) — so the
  beacon would have run *inside the native app's WebView* whenever someone
  tapped Privacy Policy or Terms from Settings, exactly the "nothing in the
  app talks to a third party" boundary that mattered for the CASA/OAuth
  verification story. Reverted from those four, kept only on the two pages
  confirmed elsewhere in this file as **"Web-only by design... NOT in
  `stage-www.js`'s `FILES`... no marketing page in the APK."**
- **Setup needed the manual JS-snippet path, not Cloudflare's "Enable"
  auto-inject option** — auto-inject only works for traffic actually
  proxied through Cloudflare, and phonoleaf.com's DNS is deliberately
  grey-cloud/DNS-only (see the Live section at the top of this file), so
  Cloudflare never sees the traffic to inject anything into. The dashboard
  also auto-creates a DNS zone page that looks like an analytics dashboard
  but isn't one — Web Analytics is a separate, account-level product,
  easy to miss on first look.
- Snippet: `<script type='module' src='https://static.cloudflareinsights.com/beacon.min.js' data-cf-beacon='{"token":"…"}'>`,
  inserted right before `</body>` on both pages. No CSP change needed
  (neither page carries a Content-Security-Policy — only `index.html`
  does). No `sw.js` precache change needed (these two pages were already
  excluded from precaching).
- Verified in a local preview: pages render unaffected; the beacon's RUM
  POST is correctly rejected under the local origin (Cloudflare validates
  the request `Origin` against the registered `phonoleaf.com` hostname) —
  expected behavior in local dev, not a bug, and it will fire normally once
  actually served from phonoleaf.com.

## Voice-pack downloads dying on screen lock + sign-in language toggle (2026-08-09)

- **"If I download language packs, it never completes the download... I'm
  thinking it's because the download fails if the screen shuts down?"**
  Correct diagnosis, and the same class of bug this project already solved
  once for TTS playback: `downloadPack()` in `PhonoLeafTtsPlugin.kt` ran on
  a plain background thread (`downloadExecutor`) with **no foreground-service
  or wake-lock protection at all** — unlike playback, which needed exactly
  that (`PlaybackService.kt`) to survive the screen locking. A voice pack is
  65–140 MB; almost nobody keeps the screen on that long, so Android's
  Doze/App Standby was suspending the download thread and throttling network
  access the moment the screen turned off, stalling the transfer for good.
  Fixed with a new **`PackDownloadService.kt`**, deliberately much simpler
  than `PlaybackService` (no media session, just a progress notification +
  wake lock) but following the exact same hard-won rules from that file's
  own history, since they're Android's rules, not this app's:
  `android:foregroundServiceType="dataSync"` (the type Android's own docs
  specify for "transferring data through the network and shouldn't be
  interrupted by the system" — a different type than playback's
  `mediaPlayback`, so it needed its own manifest `<service>` entry and its
  own `FOREGROUND_SERVICE_DATA_SYNC` permission), `startForeground()` called
  immediately in `onStartCommand`, a `PowerManager.PARTIAL_WAKE_LOCK`
  (`"PhonoLeaf:download"`, capped at 30 min) held for the download's
  duration, and `context.startService()` rather than
  `startForegroundService()` — the same choice `PlaybackService` already
  made after hitting a real `ForegroundServiceDidNotStartInTimeException`
  crash, and safe here for the same reason: a download always starts from a
  user tapping Download while the app is genuinely in the foreground.
  Tracked with a simple `@Volatile` counter (`start()`/`finish()`) rather
  than one service instance per download — `downloadExecutor` is already
  single-threaded so only one download ever runs at once, but several can be
  queued; the counter means a mid-queue handoff from one pack to the next
  just updates the existing notification instead of stopping and
  restarting. Wired into `PhonoLeafTtsPlugin.kt`'s `downloadPack()`: `start()`
  right after the queued-cancellation check (so a task cancelled while still
  queued never touches the service at all), `progress()` piggybacked on the
  existing throttled `packProgress` emit, `finish()` in the `finally` block
  (covers success, failure, and cancel uniformly) guarded by a
  `serviceStarted` flag so it can't decrement a counter it never
  incremented. `cancelDownload()` needed no changes — cancellation already
  works by bumping the per-model epoch, which the download loop's own check
  throws on, landing in the same `catch`/`finally` path.
  **DEVICE-VERIFIED 2026-08-10 — owner confirmed working.** No JDK/Android
  SDK in this environment, so this was written and reviewed carefully
  against `PlaybackService.kt`'s proven pattern but never compiled here; the
  owner's own on-device rebuild + test is what confirmed it.
- **"I don't think the home pages, onboarding, sign-in pages have [a language
  toggle] right now."** Correct — the only in-app toggle lived in Settings
  (`#lang-seg`), unreachable before signing in, so a wrong auto-detected
  language (or a user who just wants the other one) had no way to be
  corrected pre-auth, unlike `home.html`'s own EN/FR switcher for signed-out
  visitors. Added a small matching **EN/FR toggle to the sign-in screen**
  (`.si-langtoggle`, top-right of `.si-wrap`), calling `I18n.setLang()`
  directly. Refactored `I18n.apply(root)` to also sync any `.lang-seg`
  button group's `.on` state (previously done as an ad-hoc extra line inside
  `Settings.render()`, querying `#lang-seg` specifically) — both the
  sign-in and Settings toggles now share one `.lang-seg` class and can never
  disagree about which button is highlighted, wherever `apply()` runs.
  `app_language_sub`'s copy ("Translates the Settings tab") was stale from
  the original Settings-only pilot; updated to "Translates the whole app" in
  both languages. Verified in a browser harness: the sign-in screen renders
  in French purely from clicking FR pre-auth (no prior Settings visit,
  `pl_lang` unset beforehand), the toggle's highlighted button stays correct
  after the switch, and `pl_lang` persists across reload.

## Voice-tier terminology aligned: Built-in, Standard, Upgraded (2026-08-10)

Owner: *"We should align on what we call the models. I think we should go
Built-in, Standard, and Upgraded."* Prompted by reviewing the "Upgraded
voice" rename above and the VoiceInfo copy fix that followed it — both were
correct in isolation, but "standard" was being used across the app to mean
two genuinely different things depending on which string you happened to
read: **Piper** (the baseline on-device neural voice) in some places, the
**phone's own OS voice** (the true last-resort fallback) in others. Same
word, contradictory meaning.

Three names now used consistently everywhere a voice tier is named —
Settings subtext, toasts, the voice picker's tier badge, and the VoiceInfo
modal, EN + FR:
- **Built-in** — the device's own OS voice (Web Speech / native
  system TTS). FR: **"voix intégrée"** (already the term
  `slow_note_body` used — extended everywhere else that previously said the
  ambiguous "voix standard" for this specific meaning).
- **Standard** — Piper, the baseline on-device neural voice. FR: **"voix
  standard"** (this usage was already correct/unambiguous; unchanged).
- **Upgraded** — Kokoro, the higher-quality on-device neural voice. FR:
  **"voix améliorée"** (matches the Settings row's own title, `natural_voice`
  = "Voix améliorée").
- **English capitalizes all three as named tiers** wherever they appear,
  including mid-sentence ("using the Built-in voice", "get the Upgraded
  voice") — a deliberate style choice treating them as fixed labels rather
  than generic adjectives, matching how the owner presented them. **French
  deliberately does NOT capitalize the equivalent adjectives mid-sentence**
  ("voix intégrée", "voix améliorée", "voix standard") — French capitalization
  rules don't extend to common-noun-style branded terms the way English
  marketing conventions do, and capitalizing there would read as a mistake,
  not emphasis.
- Touched: `natural_voice_dead`/`natural_voice_kokoro`/`natural_voice_piper`
  (Settings subtext), `voice_tier_high` (renamed from "High" to "Upgraded" —
  the voice picker's `✨ Natural · {tier}` badge), `downloading_hq_pct`,
  `device_runs_best_standard`, `installing_standard_instead`,
  `natural_voice_struggling`, `device_cant_run_realtime`, `slow_note_body`,
  and `voiceinfo_p2` (both the STRINGS value and the static HTML fallback
  text in the modal markup, kept in sync per the file's own convention).
  `voice_tier_standard` ("Standard") and `device_runs_best_standard`/
  `installing_standard_instead` (already correctly about Piper) needed no
  content change, only this verification that they weren't part of the
  ambiguity.
- Verified in a browser harness across all five Settings "Upgraded voice"
  row states, the VoiceInfo modal in both languages, and the voice picker's
  tier badges rendered for a mix of real Kokoro/Piper voice entries (French
  Piper voices correctly show "· Standard", English Kokoro voices "· Upgraded").

## Settings "Natural voice" renamed "Upgraded voice", On badge gated on Kokoro (2026-08-10)

Owner: *"Let's rename the Natural Voice section to something like 'Upgraded
voice'... have the On switch if Kokoro is on, not Piper since Piper is lower
quality. Natural voice is always on, so it doesn't make sense to have
Natural Voice there."* Correct — the row's "On" badge (`#neural-status`)
previously showed whenever ANY native/neural engine was active, Piper
included, and Piper is the baseline that's running almost all the time
regardless of device (see the Kokoro-gating design below), so "On" conveyed
nothing.

- `natural_voice` string renamed to "Upgraded voice" (FR: "Voix améliorée").
- **`kokoroOn = !dead && (!nativeActive || gate === 'yes')`** — badge and
  status text now key off Kokoro specifically: native + `gate==='yes'` (a
  qualifying device) or web (Kokoro-WASM is the only upgrade path there, no
  Piper/gate concept at all) both read as "on"; native + `gate` `'no'` OR
  still `'pending'` are BOTH now treated as "not upgraded" — badge hidden,
  `natural_voice_piper`'s text repurposed to "Not available on this device —
  using the standard voice" (previously said "On — standard quality...",
  which is exactly the claim being removed).
- Retry (`Settings.retryKokoroGate` → `VoicePacks.retryKokoro`) broadened
  from showing only on `gate==='no'` to any `gate !== 'yes'` while native is
  active, so a still-`'pending'` gate also gets an explicit way to trigger
  screening rather than silently sitting unupgraded with no visible action.
- Dropped `natural_voice_piper_slow` (both languages) — the "may run slower
  than real time" nuance mattered when Piper was badged "On" and speed was
  the open question; it doesn't add anything to a message that's now just
  "the upgrade isn't available here."
- Scoped deliberately narrow: only this Settings row's visible label and
  logic changed. Broader "natural voice" terminology elsewhere (the
  `VoiceInfo` modal's on-device-generation privacy explainer, the sign-in
  screen's marketing copy, the voice picker's "No natural voices installed
  yet" empty state) was left alone — those describe a different, still-
  accurate concept (on-device generation in general, or "no pack downloaded
  at all"), not the Kokoro-vs-Piper distinction this row is specifically
  about.
- Verified in a browser harness across all five reachable states (dead/
  Web-Speech fallback, native+Kokoro, native+Piper, native+gate-pending,
  web-only Kokoro-WASM) in both languages: the badge shows ONLY for genuine
  Kokoro (native gate=yes, or web), and Retry appears in every state where
  the upgrade isn't currently active.

## Native Kokoro — device-gated English upgrade (2026-08-08)

Reverses the 2026-08-07 "shelve native Kokoro" call (see the RE-SUPERSEDED
note in the Voice engine section) after owner pushback: a single Pixel 7
measurement was the wrong basis for deciding every device, only for what to
ship as the baseline. Design, in order of what was actually verified before
writing code:

- **Kokoro only covers English — confirmed by downloading and inspecting the
  real sherpa-onnx release assets, not assumed.** Every Kokoro `.tar.bz2` on
  `k2-fsa/sherpa-onnx`'s `tts-models` release was downloaded and its
  `model.int8.onnx`'s embedded metadata read directly via `onnxruntime` in
  Python (`get_modelmeta().custom_metadata_map`, keys `speaker_names`/
  `id2speaker`/`language`). The "multi-lang" releases (`kokoro-int8-multi-lang-v1_1`,
  147 MB) add **Chinese**, not French/German/Spanish — its own metadata lists
  103 speakers, 3 English against 100 Mandarin. **`kokoro-int8-en-v0_19`**
  (103 MB) is the only one that fits this app: 11 speakers, `af`/`af_bella`/
  `af_nicole`/`af_sarah`/`af_sky`/`am_adam`/`am_michael` (US) plus `bf_emma`/
  `bf_isabella`/`bm_george`/`bm_lewis` (UK) — covering BOTH accents PhonoLeaf
  already serves via separate Piper packs, from one download. Its
  `speaker2id` map matches `KOKORO_VOICES`' existing sids exactly (that
  catalog already existed for the web-WASM path and, per its own comment,
  was written anticipating this — "for a native Kokoro model (premium tier,
  later)"). **French/German/Spanish get no Kokoro option and stay Piper-only
  unconditionally** — there is nothing to gate for those languages.
- **Kotlin needed almost no changes.** `PhonoLeafTtsPlugin.kt`'s
  `ensureReady()` already auto-detects Kokoro vs Piper purely from whether
  `voices.bin` exists in the downloaded model folder (`hasVoices` branch,
  written when the plugin still ran Kokoro pre-pivot) — every method
  (`synthesize`/`prepare`/`packStatus`/`downloadPack`/`deletePack`/
  `cancelDownload`) is already fully generic over the `model` key. The only
  change: one new `"kokoro"` entry in `VOICE_PACKS` (folder `kokoro-en-hq`,
  the `kokoro-int8-en-v0_19.tar.bz2` URL, exact size `103248205L` from the
  GitHub release API) and `MODEL_VERSIONS`. Verified the archive's internal
  file layout (`voices.bin`, `tokens.txt`, `model.int8.onnx`,
  `espeak-ng-data/`, no `dict/` or `lexicon-*.txt` — the multi-lang-only
  files) matches exactly what `ensureReady()`'s existing `ifExists()` checks
  and the pre-computed (but previously always-empty-for-Piper) `lexicon`
  variable expect.
- **Owner correction mid-build: "the app should first determine the
  performance of your device BEFORE suggesting the models to use, NOT
  default to Kokoro blindly."** The first draft downloaded Kokoro (~98 MB)
  FIRST on every English request and fell back to Piper only if it measured
  slow — exactly the "blindly try the big one" pattern that was ruled out.
  The second draft screened by downloading and timing PIPER first, then
  offering Kokoro. **Both are superseded — see "SCREEN FIRST" below**, which
  removed the download-to-measure step entirely.
- **SCREEN FIRST, DOWNLOAD ONCE (final design, same day).** Owner: *"verify
  the speed of the device, and then, based on the result, display either a
  list of Piper voice packs, or a list of Kokoro voice packs"* plus *"what I
  want to avoid is for the user to have to test the Kokoro voices himself and
  possibly spend time using voices that don't work — that would be a very bad
  first impression."* The Piper-first draft technically never made the user
  audition anything (the benchmark was automatic), but it still downloaded
  ~82 MB of Piper purely as a speed proxy, interrupted with a ConfirmModal,
  then downloaded ~98 MB of Kokoro and deleted the Piper — ~180 MB of
  traffic to end up with 98 MB, on exactly the capable devices where wasting
  a user's data is least excusable. Replaced with a **synthetic CPU
  benchmark that needs no download at all**:
  - **`PhonoLeafTtsPlugin.deviceBench()`** (new `@PluginMethod`) times a
    parallel float matrix-multiply — ~0.5s of pure arithmetic, no model, no
    network — using **`inferenceThreads()`**, extracted so the benchmark runs
    at exactly the parallelism the real engine will get (the big.LITTLE
    formula that was previously inline in `ensureReady`). Warms up first (JIT
    + letting the scheduler migrate onto the big cores), then takes
    best-of-3 to shrug off a scheduling hiccup or thermal dip. Returns
    GFLOPS.
  - **`VoicePacks.screenDevice()`** runs it and sets the gate. Anything that
    goes wrong (no plugin, method missing, throws) resolves to `'no'` — the
    always-safe Piper path is what any failure produces.
  - **`VoicePacks.screenDeviceWithUI()`** wraps that in **`VoiceSetup`**, a
    brief buttonless overlay ("Setting up your voice / Checking which voices
    run best on your device…"), shown for a `MIN_MS` floor of 1.1s so a fast
    benchmark reads as a deliberate setup step rather than a flicker. Fired
    from `maybeOnboard()` right after the Drive folder is picked, **before the
    catalog is ever rendered** — so the first list the user sees already
    contains only voices their device can actually run.
  - **One download, whichever engine won.** No ConfirmModal, no second
    download, no discard. The chosen pack's size shows on its row like any
    other language.
- **The real measurement survives as a safety net, not a gate.**
  **`VoicePacks._verifyKokoro()`** runs after ANY successful Kokoro download —
  wired inside `download()` itself rather than at each call site, so the
  catalog row, onboarding, and Settings → Retry are all covered by the same
  check. The synthetic screen is scalar JVM math, not onnxruntime's NEON
  kernels, so it can only correlate with real inference; the model actually
  being kept therefore gets a genuine `TTS._benchKokoroGate()` synthesis
  before we commit. Failing it is a normal handled outcome, not an error:
  delete Kokoro, set gate `'no'`, **auto-download Piper** (the user would
  otherwise be stranded mid-onboarding with no English voice at all) and say
  so plainly — "That voice needs a faster device — installing the standard
  one instead."
  - **`_KOKORO_KEEP_RATIO` is 0.75, deliberately not 1.0.** "Technically
    faster than realtime" is not good enough: this project already has
    evidence that CPU contention bites in practice (voices audibly garbled
    while a pack downloaded in the background, fixed 2026-08-05), and
    prefetch needs slack to stay ahead across a whole chapter. A model that
    only just keeps up in a quiet one-shot benchmark will stutter in real
    reading — which is precisely the bad first impression this flow exists
    to prevent.
- **`_KOKORO_MIN_GFLOPS` = 5.0, CALIBRATED on device 2026-08-08.** The owner
  ran the build on the Pixel 7: `deviceBench threads=4 ms=53 gflops=2.47`.
  Two things came out of that one line:
  1. **The Kotlin benchmark compiles and runs correctly.** 2.47 is exactly
     what the code predicts — `2 x 160³ x 4 reps x 4 threads = 131,072,000`
     flops ÷ 53 ms — and `threads=4` is the right answer for an 8-core
     device under `inferenceThreads()`. Removes the "never compiled" caveat
     for this method specifically. Also: 53 ms per timed run means the whole
     benchmark (2 warm-up + 3 timed) is ~300–400 ms, so `VoiceSetup.MIN_MS`
     (1.1 s) is what the user actually perceives, which is the intended
     behavior — the floor exists so a fast check doesn't flash past.
  2. **The threshold now has a real anchor.** The Pixel 7 scores 2.47 AND is
     independently known to run Kokoro at ~1.36x realtime — a confirmed
     *failure* case, which is the most useful kind of anchor. Break-even
     (landing exactly on `_KOKORO_KEEP_RATIO`) is `1.36 / 0.75 = 1.81x` the
     Pixel 7 ≈ 4.5 GFLOPS. Set to **5.0 (~2.0x the Pixel 7)**, predicting
     ~0.67x realtime, ~10% clear of the keep bar. That 2x line also matches
     the owner's own framing of the feature ("Pixel 7 is not strong enough
     but Pixel 12 is").
  - **What is still unknown: the SLOPE.** One anchor tells us where the
    Pixel 7 sits, not how scalar-JVM-matmul throughput maps onto NEON ONNX
    inference across other chips. A device could clear this screen and still
    miss on real inference. That is exactly why `_verifyKokoro` exists — the
    screen only has to be good enough to avoid pointless downloads, and a
    wrong `'yes'` costs one recoverable download while a wrong `'no'` costs
    only a missed upgrade.
  - **Self-refining from here:** `screenDevice()` persists the score to
    `pl_device_gflops`, and `_benchKokoroGate()` logs it alongside the real
    measured ratio (`Diag {e:'kgate', r, g}`). Any device that actually goes
    through verification therefore records a **paired** data point — worth
    more for calibration than any further reasoning from this single anchor.
  - Why not an OS API or a lookup table (researched 2026-08-08, not
    assumed): Android's own **performance class** API is OEM-declared and
    frequently absent, and its criteria are media-pipeline oriented rather
    than CPU-inference — **the Pixel 7 rates highly there and still can't
    run Kokoro**, so it would give the wrong answer on the one device we have
    real data for. Public benchmark databases (Geekbench et al.) have **no
    runtime API**, so using them means shipping a chipset table to maintain
    forever, which would be missing exactly the new phones most likely to
    qualify. And **nobody has published "Kokoro real-time factor by device"** —
    even a perfect score table would still need someone to calibrate
    "score X ⇒ Kokoro runs under realtime" by running Kokoro on real
    hardware. Measuring the CPU directly is the only signal that stays
    correct on hardware that doesn't exist yet.
- `pl_kokoro_gate` (`'pending'` | `'no'` | `'yes'`) is the persisted verdict.
  **Settings → Retry** (`Settings.retryKokoroGate` → `VoicePacks.retryKokoro`,
  shown only while gate is `'no'`) forces a fresh screen — **free, since
  screening needs no download** — and installs Kokoro if the device now
  qualifies. Relevant on a new device or after an OS update. Nothing to retry
  once already on Kokoro.
- **`refresh()` now checks `ALL_PACK_MODELS`, not `CATALOG`.** While the gate
  is `'pending'` the catalog contains a VIRTUAL `"english"` row and no real
  English pack, so checking only the catalog would both query a model the
  plugin has never heard of and — the real bug — **miss an English pack
  that's genuinely already on disk**, which is exactly what `maybeOnboard()`
  keys off to decide whether to prompt at all. `maybeOnboard()`'s own
  already-have-a-pack check was switched to `ALL_PACK_MODELS` for the same
  reason.
- **The Language Packs catalog is gate-aware.** `VoicePacks.CATALOG` became a
  getter: while `'pending'`, English shows as ONE row (nothing to split into
  US/UK until it's known which engine will serve it); once `'yes'`, still one
  row, now pointing at the real `"kokoro"` pack; once `'no'`, the familiar
  two-row "English (US)"/"English (UK)" Piper split, since Piper genuinely
  needs two separate downloads for the two accents. The virtual `"english"`
  model key (used only while `'pending'`) has no real pack behind it —
  `VoicePacks._englishRowHTML()` derives its displayed state from whichever
  REAL download is actually in flight (`_progress.us`, `_progress.kokoro`, or
  the `_checkingEnglish` flag during the brief post-download benchmark)
  rather than tracking a parallel state machine that could disagree with
  what's actually happening. `MyData.deleteAll()`'s exhaustive pack wipe was
  switched from `CATALOG` (which only ever shows one English variant) to a
  new `VoicePacks.ALL_PACK_MODELS` constant, so a leftover pack from an
  earlier retry can't survive "delete all my data" un-removed.
- **Voice selection had to become a union of both engines — a real
  refactor, not a config flip.** Before this, `TTS._nativeCatalog()`/
  `_voiceKey()` picked PIPER_VOICES-or-KOKORO_VOICES and
  `pl_voice_piper`-or-`pl_voice_kokoro` based on `_modelType` — "whichever
  model the native engine last happened to load." That only ever worked
  because every pack was Piper, so `_modelType` was always `'vits'`
  regardless of WHICH Piper accent was active; the ternary never actually
  mattered in practice. With Kokoro now a genuinely different, coexistable
  family (a device can have Kokoro for English AND Piper for French
  downloaded at once), "whichever loaded most recently" stops being a
  reliable stand-in for "show me everything I have." Replaced with
  **`TTS._allNativeVoices()`** (`PIPER_VOICES.concat(KOKORO_VOICES)`) and a
  **single persisted choice, `pl_voice_native`**, replacing the
  `pl_voice_piper`/`pl_voice_kokoro` split for native (a one-time migration
  adopts `pl_voice_piper` if present — `pl_voice_kokoro` was web-WASM-only
  before this change, native never had a Kokoro voice to have picked).
  `_voiceSid()`/`_voiceModel()` now resolve synchronously from this union,
  so `_modelReady()` no longer needs to complete first for them to be
  correct — it now instead **prepares the model the user actually has
  selected** (`nat.prepare({model: this._voiceModel()})`) instead of
  unconditionally "us", which fixes a real (if minor) waste: every native
  boot used to load-then-immediately-discard the US model for any user whose
  real choice was French/German/Spanish, since the engine holds one model at
  a time. `VoiceModal`'s picker, `activeVoiceLabel()`, `TTS._nativeBench()`,
  and the `PACK_NOT_DOWNLOADED` same-session fallback search were all
  updated to the union too. **A harness catch worth recording**: the first
  cut of `_tryKokoroUpgrade()`'s success path deleted the Piper pack and
  flipped the gate, but left `pl_voice_native` pointing at whatever it was
  before (a Piper voice id, first in the union array, if nothing had ever
  been explicitly chosen) — which no longer existed on disk. Harmless in
  that the `PACK_NOT_DOWNLOADED` fallback search would have caught it and
  switched to a real Kokoro voice anyway, but it would have surfaced as an
  unnecessary "switched to…" toast at the exact moment the user just
  unlocked the good voice. Fixed by explicitly pointing `pl_voice_native` at
  a real Kokoro voice on a successful upgrade, unless the stored choice was
  already a genuine (prior-install) Kokoro pick.
- **A separate, pre-existing bug found and fixed while rewriting `VoiceModal.open()`
  for the union:** its neural branch filtered candidates by
  `VoicePacks._status[...].downloaded`, but `VoicePacks._status` is native-only
  (never populated when `TTS._nativeTts()` is null) — so on the **web** build,
  which also takes this branch whenever Kokoro-WASM isn't dead
  (`TTS._engineNow() === 'kokoro'` doesn't distinguish native from web), the
  filter always evaluated empty and the picker permanently showed "No natural
  voices installed yet," even while the WASM voice was loaded and working.
  Fixed by branching explicitly on `TTS._nativeTts()`: native takes the
  pack-gated union-catalog path above; web shows `KOKORO_VOICES` directly
  (its own `pl_voice_kokoro` key, no pack system, nothing to gate).
- Voice picker rows now show a quality badge (`✨ Natural · High` for Kokoro,
  `· Standard` for Piper) so a device with both engines downloaded (English
  via Kokoro, another language via Piper) can tell them apart at a glance.
- **Everything above is verified in Node-based harnesses (mocked plugin +
  deterministic clock), NOT on real hardware** — same standing caveat as
  every native change in this file, no JDK/Android SDK in this environment.
  The Kotlin benchmark in particular has never been compiled or run, so its
  absolute GFLOPS scale is unverified (which is the same reason
  `_KOKORO_MIN_GFLOPS` needs the calibration run above). Verified in the
  harness: a fast device screened as capable **before any download**, then
  fetching exactly ONE pack (Kokoro) with no Piper round-trip; a slow device
  resolved from the CPU screen alone, never shown Kokoro anywhere in the
  catalog; a device that screens capable but whose REAL Kokoro measurement
  fails (pack deleted, gate corrected, Piper auto-installed, user never left
  without a voice); a *marginal* Kokoro at 0.9 correctly rejected for lack of
  margin; a benchmark that throws falling back to Piper rather than Kokoro;
  an install that already has a pack skipping screening entirely; and Retry
  re-screening for free and cleaning up the redundant Piper pack. Plus, from
  the earlier round and still passing: `MyData.deleteAll()` wiping packs
  `CATALOG` alone would have hidden, the union picker showing a Kokoro voice
  and a Piper voice simultaneously, the Settings label/Retry visibility
  across all three gate states, and the Language Packs modal's virtual
  English row across its sub-states. The `VoiceSetup` overlay was also
  rendered in a real browser at 375px to confirm it reads correctly.
- **Device-test status (2026-08-08):** the `deviceBench` calibration run is
  DONE (see `_KOKORO_MIN_GFLOPS` above) and confirms the benchmark itself
  works on real hardware. **Still to confirm on device:** that the Pixel 7 is
  screened onto Piper *without* ever downloading Kokoro (2.47 is well under
  the 5.0 bar, so it should never be offered — this is the end-to-end check
  that the gate actually wires through to the catalog); that the overlay
  reads as deliberate rather than sluggish; and, whenever a genuinely faster
  device is available, one real `_verifyKokoro` pass to produce the first
  paired calibration point.

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
2. ~~**Switch `drive.readonly` → `drive.file` + Google Picker**~~ —
   **ATTEMPTED AND REVERTED 2026-07-22. Do not retry without reading the
   "`drive.file` + Google Picker was tried" note above.** `drive.file` cannot
   list a picked folder's contents (per-file grants only), so the app's whole
   connect-a-folder model breaks — library came up empty on device. Staying on
   `drive.readonly`, which means **Google verification + likely a CASA
   assessment is now a REQUIRED launch step**, not an avoidable one.
   The good news: the "~$15k+/yr" figure previously recorded here was wrong —
   actual reported costs are **AL1 ≈ $500 / AL2 ≈ $3–6k**.
   **~~ACTION: submit for verification to learn the real CASA requirement~~ —
   DONE, AND ANSWERED 2026-08-05.** Google's review email states it outright:
   every app requesting restricted APIs **must** complete a third-party CASA
   assessment before approval, **recertified annually**. The hoped-for
   "client-side app with no backend may be exempt" reading is **dead** — no
   such exemption was offered, and the email instead pitches `drive.file`
   precisely *because* it avoids CASA. So the standing cost of `drive.readonly`
   is CASA + annual recert, indefinitely. See the STATUS block below for the
   full outcome and the decision taken.
   **STATUS 2026-08-04 — SUBMITTED FOR VERIFICATION.** The demo video was
   recorded, uploaded Unlisted to YouTube, and linked in the submission; the
   owner then completed the Verification Questionnaire (not personal-use, not
   internal-only, not dev/test-only, not a Gmail SMTP plugin; both
   acknowledgements — requirements met, CASA required for restricted scopes —
   checked) and clicked **Submit for verification**. The app is now with
   Google's review team.

   **STATUS 2026-08-05 — FIRST REVIEW BACK. Two items failed; "Appropriate
   data access" PASSED.** Email from the Third Party Data Safety Team
   (api-oauth-dev-verification-reply@google.com) to both the personal address
   and `support@phonoleaf.com`. Verification Center shows: ✅ Appropriate data
   access · ❗ Privacy policy requirements · ❗ Request minimum scopes ·
   ⋯ Homepage requirements and Additional requirements not yet reviewed.
   1. **Privacy policy — "does not specify any data protection mechanisms for
      sensitive data." FIXED + LIVE 2026-08-05** (commit `8167e8a`). Root
      cause: `privacy.html` covered data *access*, *use* and *sharing*
      thoroughly but never *protection*, which Google's own privacy-policy
      guidance (`support.google.com/cloud/answer/13806988`) lists as a
      required category alongside them, with examples like "security
      procedures are in place to protect the confidentiality of your data"
      and "we use encryption to protect your information". Added a **"How we
      protect your data"** section citing measures that genuinely already
      ship — HTTPS/TLS everywhere, the native app's Keystore-backed
      `EncryptedSharedPreferences` token storage, no-backend/no-central-
      database, the CSP, and user-controlled deletion. **One claim was
      corrected before publishing**: the first draft said third-party
      libraries are never fetched from CDNs at runtime, which is false —
      jsDelivr still serves the `kokoro-js` module for the browser-WASM voice
      fallback and GIS loads live from `accounts.google.com`. Reworded to
      claim only what's true (the core epub-reading libs are vendored).
      Verified live at `https://phonoleaf.com/privacy.html`, effective date
      bumped to August 5, 2026.
   2. **Minimum scopes — "does not appear to use the minimum scope(s)
      necessary for functionality."** This is the standard `drive.file`
      push-back, and the email is the long-form version: it argues
      `drive.file` is non-sensitive, needs no verification, and — critically
      — **needs no CASA and no annual recertification**.
      **THIS EMAIL ANSWERS THE CASA QUESTION THAT HAS BEEN OPEN SINCE
      2026-07-26.** Verbatim: *"all apps requesting access to restricted APIs
      must complete a third-party CASA security assessment before the
      restricted APIs can be approved; this assessment must also be
      recertified annually in order for the app to maintain access to
      restricted APIs."* So: **CASA is REQUIRED on `drive.readonly`, with
      annual recert, and the hoped-for "no backend ⇒ exempt" reading is NOT
      available.** Roadmap item 2's open ACTION is resolved — the answer is
      "yes, CASA, every year, for as long as we hold a restricted scope."
      **Owner decision 2026-08-05: push back (Option 2, "Unable to use
      narrower scopes") rather than switch.** Reasoning: the technical case is
      strong and already device-proven (see the `drive.file` note above —
      `files.list` on a picked folder's children returns empty, so
      connect-a-folder sync is impossible, not merely worse), and the downside
      of trying is only review latency, since `drive.file` needs no
      verification at all and could still be adopted later if Google refuses.
      Risk accepted knowingly: Google explicitly warns *"UI preferences or
      client library limitations alone are not valid policy exceptions"*, so
      refusal is a real possibility, and approval commits the project to CASA
      + annual recert. The reply text sent is recorded in `VERIFICATION.md`.
      **Email rule: DO NOT remove any already-approved scope from the project
      right now** — the email says so explicitly.

   **STATUS 2026-08-05 (same day) — GOOGLE APPROVED `drive.readonly`. The
   "Unable to use narrower scopes" pushback worked.** Google's reply didn't
   ask for `drive.file` again — instead it moved straight to the CASA
   requirement: **"You are required to complete an ADA-CASA AL1 (formerly
   Tier 2) security assessment... by the following date: Nov 3, 2026."** This
   is the FINAL confirmation of what the earlier email already implied: CASA
   is mandatory on this scope, annually, for as long as the project holds it —
   and it's now a hard dated requirement, not a future maybe.
   - **AL1, not AL2** — the email offers both, but AL2 only matters for
     Google Workspace Marketplace badging, which doesn't apply here. AL1 is
     the correct and sufficient tier.
   - **CASA, not MASA** — the owner found a lab (Eydle) offering both and
     asked whether both are needed. They are NOT: CASA is the OAuth
     restricted-scope program (what Google's email names by name), MASA is a
     *separate*, unrelated ADA program tied to the Google Play Store's
     optional "Independent Security Review" listing badge. Only CASA is
     required now; MASA would only be worth revisiting once actually live on
     Play, purely for the trust badge.
   - **Extensions exist but require a "selected lab" first**: the email
     states due-date extensions must be requested *through* the chosen
     ADA-authorized lab, not directly from Google. This is why the owner is
     engaging a lab now rather than waiting for iOS to be ready and risking
     the Nov 3 date with zero lab contact — being "in process" with a lab is
     what unlocks the extension path if needed, not something available from
     a cold start.
   - **iOS bundling question — genuinely unresolved, asked directly to a
     lab rather than assumed.** Both labs researched price "per application"
     (TAC: $675 Basic AL1 / $855 Premium / $3600 Enterprise; Eydle: $300–800
     AL1 / $3000–6000 AL2), which hints a single assessment might cover
     Web+Android+iOS together if done at once — but neither site states what
     happens if a platform is added *after* an assessment is already done.
     **Decision: don't wait for iOS to find out** — it isn't remotely ready
     (no Mac, no device had at the time, no Capacitor iOS target, no auth
     port, no TTS port), and gambling the live Nov 3 deadline against an
     uncertain few-hundred-dollar savings was judged not worth it. **Outreach
     sent 2026-08-05 to Eydle** (`eydle.com/ada`, via their contact form,
     ~872-char message under their 1000-char limit) asking: what's included
     at their $300 vs $800 tier; whether one assessment covers an app across
     platforms and what adding iOS later would cost; whether they support
     Google's extension-request process; and what's needed to start.
     **Eydle replied 2026-08-06 — the key question is answered: adding iOS
     later does NOT require a new assessment.** AL1 quoted at **$770** (a 10%
     "indie developer" discount was offered but not yet claimed → $693 if
     asked for before signing), 3 weeks to initial Letter of Validation,
     unlimited retesting within 3 months. **Real implication beyond price**:
     in AL1 the developer (not the lab) collects most of the evidence, so
     this needs real owner time once engaged, not just payment.
   **DECIDED + ENGAGED 2026-08-10: Eydle chosen (no separate TAC quote
   pursued), invoice paid.** A follow-up email from Birendra Jha (Eydle
   co-founder) reiterated the same terms — USD 770, 3 weeks to initial LOV,
   unlimited retesting within the 3-month window, the 10%-off eligibility
   (startups/nonprofits/indie developers/multiple assessments) — and the
   owner paid the invoice off that email. **Whether the 10% indie-developer
   discount was actually applied to the paid amount is unconfirmed** — the
   email restates the discount as available rather than confirming it was
   used, and this note shouldn't assume either way; check the actual invoice/
   receipt total against $770 vs $693 if it matters later (e.g. for expense
   records).
   **Immediate next steps, per the same email — onboarding, not yet done:**
   (1) sign the Letter of Engagement, (2) complete the Evidence
   Questionnaire, (3) set up the Test Environment (Eydle to provide details).
   Recall from the AL1/AL2 distinction above: in AL1 **the developer collects
   most of the evidence**, not the lab — so these three steps are real owner
   work, not a formality, and the 3-week-to-LOV clock most likely starts once
   onboarding (particularly the evidence questionnaire + test environment)
   is actually complete, not from the payment date. Worth clarifying with
   Eydle if the clock's start date isn't stated explicitly in the onboarding
   materials they send next.
   Full reply text and lab research are in `VERIFICATION.md`.

   **UPDATE 2026-08-14 — CASA package PARKED, not submitted, pending the
   payments backend.** Onboarding with Eydle completed (engagement letter
   signed 2026-08-12, questionnaire drafted, Gen 4/5 answered as the owner's
   own name/address per Eydle's confirmation that CASA identity tracks the
   GCP account holder, not a registered business — see `VERIFICATION.md`).
   Then the owner challenged the "submit now" plan directly: with no backend
   there's no way to charge anyone, and no pool of ~100 testers either, so
   completing verification now bought nothing beyond not missing a date.
   Two things had to be confirmed before parking was safe: whether a second,
   backend-triggered assessment means paying Eydle again in full, and
   whether the Nov 3, 2026 deadline is survivable if verification is delayed
   until the backend is built. Eydle confirmed both favorably (2026-08-14):
   the already-paid engagement covers a future assessment, and Eydle can
   request a due-date extension directly from Google. **Decision: build the
   payments backend first (see `PAYMENTS_SPEC.md`, especially §11–13, added
   the same day), then submit ONE CASA assessment covering the finished
   product**, rather than assessing a no-backend app now and re-assessing a
   few months later regardless. The Google verification email naming the
   Nov 3 deadline was forwarded to Eydle the same day so an extension request
   can be initiated without further owner action. Full exchange in
   `VERIFICATION.md`'s "Decision revisited (2026-08-14)" section.

   Do not change publish status (Testing → Production) or make unnecessary
   consent-screen edits while review is open — the console's own submission
   page warns that both can delay review.
   Completed beforehand (all owner/Cowork console work, verified against the live site):
   - `phonoleaf.com` bought, DNS live at Cloudflare (grey-cloud), GitHub Pages
     custom domain set, **Enforce HTTPS on, certificate issued**.
   - **Google Search Console: `phonoleaf.com` verified as a DOMAIN property**
     (TXT `google-site-verification=NBZEK_Le1xNnfcw61nVrvE94t_afnXWJB15vjVz-HK4`),
     verified as `baileyke90@gmail.com` — the SAME account that owns the
     `phonoleaf` GCP project, which is the part Google's docs call critical.
   - **OAuth consent screen** (Google Auth Platform → Branding) repointed:
     home `https://phonoleaf.com/home.html`, privacy `…/privacy.html`, terms
     `…/terms.html`; authorized domains = `phonoleaf.com` **+ the old
     `kbailey90.github.io`, deliberately kept**. App logo uploaded by the owner
     (Cowork's own upload attempts failed on a generic backend error unrelated
     to the image; the owner did it manually — a 120×120 PNG re-rendered from
     the same inline "Soundwave Vein" SVG lives at
     `brand/oauth-consent-logo-120.png`).
   - **Web OAuth client** gained `https://phonoleaf.com` as a second authorized
     JS origin, alongside the retained `https://kbailey90.github.io`.
   - **`support@phonoleaf.com` set up (2026-07-28) via Cloudflare Email
     Routing**, forwarding to the owner's personal inbox — confirmed working
     with a real cross-account test email (Cloudflare's Activity Log alone
     wasn't trusted as proof: a same-account test from `baileyke90@gmail.com`
     to itself gets deduplicated by Gmail and never visibly arrives, so a
     second test from a different account was used to actually confirm
     delivery). Routing setup added 5 DNS records (3 MX, 1 DKIM TXT, 1 SPF
     TXT) without touching any existing record. Added as **Developer contact
     information** on the consent screen alongside the personal address (both
     kept, not replaced, so Google's own project notifications don't stop
     reaching the inbox that's actually watched). **Cannot** be set as the
     consent screen's own **"User support email"** field — confirmed by
     checking the actual dropdown, which offers only the signed-in Google
     account or a Google Group the owner manages, matching Google's
     documented restriction on that field. `CONFIG.SUPPORT_EMAIL` in
     `index.html` and the contact links in `home.html`/`privacy.html`/
     `terms.html` were switched to it the same day — see the Feedback/bug-
     report behavior note.
     **DEFERRED TASK (owner, 2026-08-05): replace this forward with a real,
     separate mailbox** — right now every support email lands directly in the
     owner's personal Gmail, which they've flagged as clutter. Candidates:
     Google Workspace (paid, gives a proper `@phonoleaf.com` Gmail inbox) or a
     cheaper custom-domain option (e.g. Zoho Mail's free tier). **Deliberately
     not done now** — the owner asked to hold off until OAuth verification
     finishes, since this domain/DNS is exactly what's under active Google
     review and the standing rule is no unnecessary changes while that's
     open. Revisit once verification is fully resolved (approved or the
     `drive.file` fallback taken).
   Submitted 2026-08-04 — see the STATUS note above. **CASA question ANSWERED
   2026-08-05** — see the STATUS block above and `VERIFICATION.md`'s CASA
   section: it is required, with annual recertification, for as long as the
   project holds a restricted scope. Currently awaiting Google's response to
   the "Unable to use narrower scopes" reply sent 2026-08-05.

   **Original research (2026-07-26) — see `VERIFICATION.md` for the full plan.
   Two blockers were found, one a hard stop:**
   - **`kbailey90.github.io` CANNOT be verified — a custom domain is required.**
     Google's domain-verification doc requires authorized domains be verified as
     a **Domain Property (DNS-level)** in Search Console, i.e. a **TXT record in
     the domain's DNS**, which is impossible on `github.io` (no DNS control).
     Developers independently report the review team rejecting github.io as not
     first-party. Homepage AND privacy policy must both live on the owned
     domain. This has the longest lead time of anything remaining (buy → DNS →
     Pages cert → Search Console → consent screen → add the new JS origin to
     the Web OAuth client, keeping the old one so existing installs keep
     working). NB browser storage is per-origin, so web users on the old URL
     will look signed-out and lose local progress on the new domain; native is
     unaffected.
   - **The app root is a sign-in wall**, which fails Google's "publicly
     accessible … clearly demonstrate[s] relevance" homepage rule — a standard
     rejection cause. FIXED: added **`home.html`**, a real public landing page
     (what it does, how it works, an explicit "How PhonoLeaf uses your Google
     Drive data" section, links to privacy/terms). Web-only by design — NOT in
     `stage-www.js`'s `FILES` (no marketing page in the APK) and not precached
     by `sw.js`, matching how privacy/terms are already handled. Uses relative
     links throughout so the domain move needs no edits to it; `privacy.html`
     by contrast hardcodes the github.io URL twice and will need repointing.
     A nicer root-landing/`/app/` split was deliberately NOT done — it would
     touch the SW cache scope, the manifest `start_url` and the native staging
     for zero verification benefit, since reviewers use the URL you supply.
   - CASA remains genuinely unresolved: the rule keys on "ability to access data
     from or through a third-party server" (PhonoLeaf has none), but the
     enumerated exemptions are personal-use / dev-testing / service-owned-data /
     Workspace-internal / domain-wide-install — **"public app with no backend"
     is not among them**. Submitting is still the only authoritative answer.
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
     - Stage 4 — background playback: **CORE FUNCTIONALITY DEVICE-VERIFIED
       (2026-07-22).** The media-session PLUGIN crashed the app ~1-2s after
       play (Cap-6 plugin vs targetSdk 36 FGS rules), proven by a kill-switch
       bisect; replaced with our own `PlaybackService.kt` foreground service +
       a PARTIAL (CPU) wake lock (see the "Background playback" note).
       First page-turning attempt (virtual pages, geometry-shifting the
       extraction window) crossed pages but not chapters and stalled on short
       pages; REPLACED (2026-07-21) with reading the book's TEXT straight from
       the spine, decoupled from rendering (see "Background reading — read
       spine TEXT directly") — crosses chapters, saves progress via CFI as it
       goes, and resyncs the visible reader to the exact chunk on unlock.
       Also fixed on the way: a real crash (`ForegroundServiceDidNotStartInTimeException`,
       fixed by using `startService` instead of `startForegroundService`) and
       "press play does nothing" (chapter-heading pre-pause fix in `start()`).
       Owner-confirmed working on device 2026-07-22. `@jofr/capacitor-media-session`
       removed 2026-07-22 (see the Tech-stack note); the dormant virtual-page
       code (`_vpage`/`_resyncVisual`/`_resyncing`/`_armResyncWatchdog`/
       `_cancelResync`, and `loadPageText`'s now-unused `vpage` parameter) was
       also deleted the same day — verified in a browser harness that
       `loadPageText()`, `start()`/`stop()`/`skipPage()` all still work
       correctly with it gone. MediaSession lock-screen play/pause control
       added 2026-07-22 (see the "Lock-screen media controls" behavior note)
       — same-day upgrade from an initial pause-only cut after owner testing
       showed the notification disappearing on pause with nothing left to
       resume from; the service now survives a pause and both directions
       round-trip. NOT device-verified (JS side verified in a browser
       harness). Still TODO: IndexedDB audio caching.
     - Stage 5 — Play Console ($25 one-time), internal testing track, store
       listing + privacy policy (item 4), then production rollout. iOS
       (Apple $99/yr) after Android is proven.
       **APK-size blocker — RESOLVED 2026-08-04, then over-delivered the same
       day.** The bundled Piper models were ~185 MB uncompressed (US 78.5 +
       GB 77 + espeak data), at/over Play's ~200 MB app-bundle ceiling before
       app code. First fix (decided 2026-07-29): ship US only, everything else
       downloadable. Then the owner pushed further — a bundled model is stored
       TWICE on device (undeletable APK asset + the filesDir copy the engine
       actually needs), so even US-only sat at ~157 MB installed with only
       half of it reclaimable — and approved **fully unbundling**: the store
       build now contains **no voice model at all**, and every language,
       including US, is downloaded on first run (prompted during onboarding,
       right after Drive folder setup). See the model-sizes and "FULLY
       UNBUNDLED" notes in the Tech-stack "Native TTS plugin" section.
       Still not device-verified — no JDK/Android SDK in this environment;
       needs a real build + device test (including an actual `downloadPack`
       run against the live GitHub release URL, and confirming an upgraded
       install isn't asked to re-download the model it already has) before
       this can be marked done for real.
       **Worth revisiting once actually on Play: Play Asset Delivery**, the
       only approach that gets "ships with the app" + "no first-run wait" +
       "fully deletable" at once. Can't be tested until published.
     - **iOS planning started 2026-08-05 (owner: "I will also start the
       business portion in parallel").** Not yet building — this is the
       roadmap + hardware acquisition phase. A detailed 10-step plan was
       written covering: buy hardware; enroll in the Apple Developer Program
       ($99/yr — start early, approval isn't instant); set up Xcode +
       CocoaPods; `npx cap add ios` and confirm the Simulator boots (note:
       `capacitor.config.json` and `scripts/stage-www.js` are ALREADY
       platform-agnostic — only `package.json`'s npm scripts are hardcoded to
       `android`, so there's minimal iOS-specific setup friction there);
       create an iOS OAuth client in Cloud Console (bundle ID
       `com.phonoleaf.app`, no SHA-1 needed unlike Android); port native auth
       to `ASWebAuthenticationSession` + Keychain (replacing the Android
       Custom-Tab/PKCE + `EncryptedSharedPreferences` pattern —
       `SecureStoragePlugin.kt`'s Swift equivalent); build the sherpa-onnx iOS
       framework (**confirmed it supports iOS via an official Swift example
       in the k2-fsa repo, but there is no prebuilt framework — it must be
       compiled from source**, budget real time for this); port
       `PhonoLeafTtsPlugin.kt` to Swift (**same class of gotcha as Commons
       Compress on Android**: Swift has no built-in bzip2/tar support for the
       `.tar.bz2` pack downloads, needs SWCompression or libarchive — also
       port the `MODEL_VERSIONS` per-model marker system and the
       background-priority download thread, both hard-won fixes from
       real bugs, and re-measure thread-count tuning rather than copying
       Android's `cores-4` value, since iOS CPU characteristics differ from
       Android's big.LITTLE); background audio via `AVAudioSession` +
       `MPNowPlayingInfoCenter` + `MPRemoteCommandCenter` (the iOS analogue of
       `PlaybackService.kt`'s foreground service + lock-screen media session
       work — no equivalent of the `ForegroundServiceDidNotStartInTimeException`
       saga expected, iOS's model is simpler here); device testing (Simulator
       cannot validate TTS performance or background audio — both are
       exactly the two hardest things this app solves, and both are known to
       behave unreliably or unrepresentatively in Simulator); then TestFlight.
       **Hardware being acquired**: a free **iPhone XR** (A12 Bionic, offered
       by a contact) — deliberately kept despite being a few years old,
       matching the "test on a realistic mid-tier device, not the newest
       flagship" reasoning already used for the Pixel 7 on Android — and a
       **MacBook Air (M1, 2020, 8 GB/256 GB, macOS Sequoia 15.6.1 already,
       90% battery)** for ~$600 CAD. Two other machines were considered and
       rejected first: a **2015 MacBook** (caps at macOS Monterey; current
       Xcode needs Sequoia 15.6+ — a hard wall, not a "slow but workable"
       situation) and an **Apple A18 Pro-chip "MacBook"** (works, since it's
       Apple Silicon, but has half the performance cores of an M-series chip,
       which matters specifically for compiling sherpa-onnx from source).
       No engineering has started; next concrete step is Apple Developer
       Program enrollment, which can run in parallel with hardware delivery.
       **UPDATE 2026-08-10: the MacBook Air purchase above fell through**
       (seller didn't show) — owner is looking for another one; spec/rejection
       reasoning above still stands as the buying criteria for whichever
       machine replaces it. **iPhone XR now expected 2026-08-12** (Wednesday).
       Apple Developer Program enrollment still hasn't been started and
       remains the actionable next step, since it doesn't depend on either
       device arriving.
4. ~~**Privacy policy + ToS**~~ — **DONE (2026-07-22).** `privacy.html` /
   `terms.html` at the repo root, live at
   `kbailey90.github.io/PhonoLeaf/privacy.html` (and `/terms.html`) — same
   branding/theme as the app (self-hosted Manrope, light/dark via
   `prefers-color-scheme`), linked from the Settings footer, and staged into
   the native shell too (`scripts/stage-www.js`'s `FILES` list). Content
   describes the app accurately (no backend, `drive.readonly` scope and why,
   what's stored locally vs sent to Google/Open Library/jsdelivr, no ad/data
   sale, sign-out revokes the grant). **The ToS explicitly flags itself as not
   lawyer-reviewed** — fine for Testing-mode verification, but worth a real
   legal review before/at public launch, especially liability + jurisdiction
   (jurisdiction was left generic — nothing in the codebase indicates the
   owner's legal jurisdiction to name one).
5. **Backend** for real refresh tokens and payments (Stripe on web; Play
   Billing in-app — note Play takes 15% vs ~3% Stripe). The TTS key proxy is
   no longer needed (no cloud TTS). **When this gets built, fold in the
   desktop bug-report photo upload too** (Cloudflare Worker + R2 — see the
   "Feedback + Report a bug" behavior note's "Desktop" bullet) rather than
   standing up a second, separate backend for it.
   **STARTED 2026-08-14: `worker/` — the entitlement Cloudflare Worker from
   `PAYMENTS_SPEC.md`.** `GET /entitlement` is real (Google ID token
   verification via live JWKS, `sub_hash` derivation, the 7-day server-side
   trial, a signed ES256 entitlement JWT); the Stripe/Play endpoints are
   routed but 501 pending their prerequisites (business registration →
   Stripe account; a Play Developer API service account). Deliberately
   dependency-free (Web Crypto only, no `jsonwebtoken`) to keep the CASA
   dependency-scan surface small — see `worker/README.md`. Verified with a
   Node harness, not deployed. **Not called from the app** — adding the
   `openid` scope and gating the UI on entitlement are separate steps, held
   back because gating now, before Stripe checkout exists, would paywall
   every current user with no way to pay. Full detail in `PAYMENTS_SPEC.md`
   §9 step 1 and `worker/README.md`.
   **Owner intent (2026-07-28): NOT permanently free — a free trial (~1 week)
   then a paid subscription.** `terms.html` was updated the same day to stop
   asserting the app is free, and now carries a **Pricing** section promising
   price/billing/cancellation will be shown before any charge. Notes:
   - **Android could ship subscriptions with NO backend** — the Play Billing
     library can query active purchases on-device. Weaker (spoofable) but
     common for small apps. **Web genuinely needs one**: Stripe has no
     client-safe "is this user subscribed?" call, since checking requires the
     secret key. A Cloudflare Worker is the cheap option — the DNS is already
     there.
   - **Tie entitlement to the Google account id, do NOT build a password
     system.** Sign-in already provides stable identity, so `home.html`'s "no
     separate account to create" claim stays true even once paid.
   - **Sequencing: submit for OAuth verification BEFORE the backend exists —
     DONE, and the underlying reasoning is now PARTLY SUPERSEDED, corrected
     here so it doesn't mislead later.** This originally hoped a no-backend
     architecture might exempt the project from CASA entirely. **That hope is
     now confirmed false** — Google's 2026-08-05 email states CASA is
     required for every app on a restricted scope, with no backend-related
     exemption offered (see roadmap item 2's STATUS blocks). The sequencing
     itself was still the right call for a different reason that still holds:
     assurance levels are dynamic and can be re-evaluated at each annual
     recert based on "changes in data-handling practices" (see
     `VERIFICATION.md`'s CASA section) — so building payments infrastructure
     only AFTER this first AL1 assessment avoids it influencing THIS
     assessment's scope/level, for whatever that's worth. Keep payment
     infrastructure strictly segregated from Google user data regardless, on
     general security-hygiene grounds, not because it changes whether CASA
     applies.
   - **STATUS 2026-08-05: business-side planning begun**, in parallel with
     iOS hardware acquisition (owner: "I will also start the business portion
     in parallel"). No specific backend/payments work done yet — this is a
     status marker for whenever that thread picks up, not a completed step.
   - **PRICING DECIDED (2026-08): Monthly $5.99 / Annual $49.99 (~30% off,
     eff. $4.17/mo) / 7-day free trial / limited Founding-Member Lifetime $129
     (first ~500 buyers, time-boxed).** Priced on value (privacy/offline), not to
     undercut — on-device TTS makes margin near-total (~$48 net per annual web
     sub after Stripe; blended net ARPU modeled ~$42/yr; break-even ~25–70 subs).
     Full rationale, unit economics, competitor pricing (verified 2026-08), GTM,
     and the 3/6/12-month plan live in **`BUSINESS.md`** (repo root) — the
     business source of truth; keep it and this note in sync.
   - **ToS pricing/lifetime clauses added to `terms.html` — DONE 2026-08-05.**
     Plans, free-trial auto-conversion, auto-renewal, cancellation, refunds
     (web = 14-day money-back window; store = via store), lifetime = "life of
     the PRODUCT not the person" + non-transferable + Google-account-tied,
     30-day price-change notice, taxes, discontinuation + 12-month
     lifetime-refund window, app-store-purchase carve-out — all as drafted in
     `BUSINESS.md` §3, with a `TODO: lawyer review` comment left in the HTML.
     Currency (USD) and the three time-window values are Cowork's suggested
     defaults, not owner/lawyer-confirmed yet — see `BUSINESS.md`'s roadmap
     item 4 for what's still open. **Lawyer engaged 2026-08-10 — awaiting
     response.** Owner has retained a lawyer and sent both the ToS/Privacy
     review request and the business-registration question (structure,
     REQ registration — see `BUSINESS.md` "Gating, do now" item 2) in one
     pass; nothing back yet. Still treat as **not lawyer-reviewed** until
     that response lands. Jurisdiction is **Québec, Canada** (owner in
     Longueuil). Québec adds French language (Bill 96), Consumer Protection Act,
     and GST/QST obligations (French Terms/Privacy/marketing, Québec governing
     law, GST+QST, REQ registration); see `BUSINESS.md` "Québec compliance". `home.html`'s CTA note changed from "Free" to "Free 7 day
     trial"; `sw.js` bumped to `phonoleaf-v14` and `www/` restaged.
   - **Lifetime = launch capital, not a core offer:** keep it capped +
     time-boxed; implement as a one-time NON-CONSUMABLE IAP on the stores (not a
     subscription) + one-time Stripe charge on web; store entitlement durably
     against the Google account so a backend change can't revoke a paid lifetime;
     keep a cash reserve against refunds + ongoing obligations (CASA recert,
     Apple fee) rather than booking lifetime revenue as pure profit.
   - **Audio ads were considered and advised against (2026-07-28.)** Spotify
     inserts ads into its OWN licensed catalog; PhonoLeaf would be inserting
     them into the user's private files, which is a different proposition.
     It would also require ad-network tracking, undermining the "nothing leaves
     your device" position that is simultaneously the main marketing line and
     an asset in the Google review. Subscription-only.
6. **In-app Feedback + Report-a-bug forms — SHIPPED 2026-07-28 (option (a):
   `mailto:` now; option (c), a real backend with genuine anonymity, deferred
   until item 5 exists — decision recorded, not yet built).** The owner asked
   for an anonymous option; **with no backend that is impossible to deliver
   honestly** — `mailto:` opens the user's own mail client, so their address is
   on the message no matter what the form claims. A checkbox promising
   anonymity would be a lie, so it was deliberately NOT built.
   - **`Feedback`** (Settings → Send feedback): email (pre-filled from
     `State.userEmail`, editable) + a message box. Empty message blocks send.
   - **`BugReport`** (Settings → Report a bug): email (same pre-fill, always
     shown — this one may need a reply, so no anonymity option even in
     principle), what-happened, optional steps-to-reproduce, and an optional
     photo. `_diagnostics()` auto-collects `navigator.userAgent` (covers "model
     and make of device" on Android without asking the user to type it),
     screen size, language, native-vs-web + `BUILD`, and current voice
     engine/fallback state — all folded into the draft body, sent nowhere
     unless the user presses Send.
   - **Photo attachment — no browser can attach a real file via `mailto:`,
     but the first cut also silently failed to attach on NATIVE, which is
     where most real bug-report photos come from.** Owner pushback
     (2026-07-28): shipping an upload control that quietly degrades to "please
     attach it yourself" is a broken promise, not a fallback — fixed the same
     day. Root cause of the native gap, confirmed by research (not assumed):
     **Android's WebView implements NO Web Share API at all, not even
     Level 1** — `navigator.share` is simply `undefined` there — so the
     original `navigator.share`-only implementation could never fire inside
     the app itself, only in mobile browsers.
     **REWORKED AGAIN the same day (2026-07-28)** — the FIRST fix (route
     through `@capacitor/share`) technically attached the photo, but owner
     feedback made clear it traded one confusing UX for another: `Share.share()`
     always shows Android's full generic "share to ANY app" chooser (Gmail
     buried among Messages/Bluetooth/Drive/WhatsApp/Nearby Share/…), with no
     explanation anywhere that the user needed to tap their mail app out of
     that unrelated list. Replaced with a small custom plugin,
     **`EmailComposerPlugin.kt`** (registered in `MainActivity.java`, same
     pattern as `PhonoLeafTtsPlugin`/`SecureStoragePlugin`), which builds an
     `ACTION_SEND` intent typed `message/rfc822` instead of a generic share —
     only email apps register an intent-filter for that MIME type, so the OS
     resolves it itself: with exactly one mail app installed (the common case)
     it launches straight into the compose screen with NO picker at all; with
     more than one, the picker it shows is restricted to just those apps, not
     the full share sheet. `App.composeEmail({to, subject, body,
     attachmentUri})` wraps the plugin call, resolving `false` on web (where
     there's no share-chooser confusion to route around — a plain `mailto:`
     already goes straight to the one mail app on both platforms) so callers
     fall back to plain mailto automatically. **`@capacitor/share` was removed
     entirely** (`package.json`, confirmed gone from `cap sync`'s plugin list
     and both regenerated Gradle files) — nothing uses it anymore.
     **`@capacitor/filesystem` is still used**, for staging the photo into the
     cache dir before handing a URI to the new plugin.
     **A real correctness bug was caught and fixed while building this, not
     assumed to be fine**: `Filesystem.writeFile()` returns a `file://` URI,
     but Android throws `FileUriExposedException` if a raw `file://` URI (into
     OUR app's private storage) is handed to ANOTHER app's process via an
     Intent — it has to be wrapped into a `content://` URI via
     `FileProvider.getUriForFile()` first, using the SAME FileProvider already
     configured in `AndroidManifest.xml`/`file_paths.xml` (originally set up
     for `@capacitor/share`'s internal use, which did this same conversion for
     us — invisibly, since we never wrote that code ourselves until now).
     `EmailComposerPlugin.kt` does this conversion explicitly. Also uses
     `activity.startActivity()`, not `context.startActivity()` — the latter
     throws unless `FLAG_ACTIVITY_NEW_TASK` is set, since Capacitor's plugin
     `context` is not guaranteed to be an Activity context; `activity` is.
     On web, `navigator.share`+`canShare({files})` (Chrome/Safari mobile
     support Web Share Level 2 with files) is unchanged. Web Share has no
     concept of "recipient" either way, so the destination address is folded
     into the shared text and the user picks the destination app themselves.
   - **Desktop — DECIDED 2026-07-28: ship the honest, no-infrastructure fix
     now (a clear instruction, not a silent "didn't work"); a real fix (upload
     + link) is noted for later, not built.** Desktop genuinely can't auto-
     attach without a backend: most browsers lack file-share support entirely
     (Firefox has none at all), and no browser on any platform can attach a
     file via `mailto:` — a hard protocol limit, not something to code around.
     The fix that shipped: when the final mailto fallback is reached with a
     photo still un-attached (desktop, or any native/web attach path failing),
     the body names the file explicitly — *"Don't forget to attach the photo
     you selected (`filename.jpg`) — drag it into this email…"* — stated
     BEFORE the mail app opens, not apologized for after. This was the actual
     owner complaint ("taking them for idiots") — not that desktop lacks true
     automation (every app hits that same wall), but that the app tried
     silently and only admitted failure afterward.
     **NOT built — Option A, for later**: a small upload endpoint (Cloudflare
     Worker + R2 — Cloudflare is already the DNS provider) that the photo
     uploads to, with a link folded into the mailto body instead of an
     attachment. Would give true parity with native/mobile web. Should be
     built as PART OF the payments backend (roadmap item 5), not a second
     separate backend effort — and per that item's own reasoning, **submit for
     OAuth verification before either exists**, so the CASA "third-party
     server" answer stays a clean no for as long as possible. Would also need:
     size/type limits, an expiry policy (R2 lifecycle rules) so screenshots
     don't accumulate forever, and a privacy-policy line, since a photo would
     then briefly live somewhere that isn't purely "on your device" — the
     current strongest privacy claim, for this one feature only.
   - **`App.loadUser()`** now also requests `emailAddress` from the Drive
     `about` fields (previously `displayName` only) and caches it as
     `State.userEmail`/`pl_email`, purely to pre-fill these two forms.
   - **`CONFIG.SUPPORT_EMAIL` is `support@phonoleaf.com`** (switched from the
     owner's personal address 2026-07-28, after confirming the Cloudflare
     Email Routing forward with a real cross-account test email). Same address
     also now used in `home.html`/`privacy.html`/`terms.html`'s contact
     links — all four updated together. Routes to the owner's personal inbox —
     see the OAuth verification status section below for the Cloud Console
     side of this same change (Developer contact info, and why the consent
     screen's own "User support email" field can't use it).
   - Verified in a browser harness: email pre-fill, empty-field blocking on
     both forms; the native path (mocked `Capacitor.Plugins.Filesystem`/
     `EmailComposer`) staging base64 photo data to the CACHE directory and
     calling `EmailComposer.compose` with a `file://` attachment URI, correct
     `to`/`subject`; a Filesystem staging failure still sending the report
     (without the attachment, filename noted in the body) rather than blocking
     it; a total `EmailComposer` failure falling through to mailto without an
     uncaught exception; native-with-no-photo never touching Filesystem at
     all; the web path still invoking `navigator.share` with the real `File`
     object; and the exact desktop fallback note text, confirmed to include
     the real filename. **The native plugin calls themselves are NOT
     device-verified** — same caveat as every other native-only change in this
     file: no JDK/Android SDK in this environment. `npm install` + `npm run
     sync` WERE run here and confirmed clean both times (adding, then removing,
     `@capacitor/share`; `cap sync` correctly lists only the 3 plugins actually
     in use for the second run), so the JS/native wiring is at least known to
     build correctly.

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
3. ~~**native refresh token in WebView localStorage**~~ — **DONE (2026-07-22).**
   `pl_rtoken` moved to Android Keystore-backed `EncryptedSharedPreferences`
   via a new `SecureStoragePlugin.kt` (`androidx.security:security-crypto`).
   All read/write/delete sites (`tryResume`, `_onDeepLink`, `refreshToken`,
   `_nativeRefresh`, `signOut`) go through `App._getRefreshToken()` /
   `_setRefreshToken()` / `_removeRefreshToken()` instead of localStorage
   directly; web no-ops cleanly (the plugin only exists natively). A one-time
   `App._migrateRefreshTokenToSecureStorage()` moves any existing plaintext
   token on first native boot after this change, then clears the old key.
   Verified in a browser harness (mocked `SecureStorage` as an in-memory
   store): get/set/remove round-trip, migration (including idempotent
   re-run), `tryResume()` correctly triggers native refresh from a token in
   secure storage, `signOut()` revokes and clears it, and `refreshToken()`'s
   failure path clears it before rethrowing. **The actual Keystore
   encryption/Gradle build is NOT device-verified** — no JDK/Android SDK in
   this environment; review the Kotlin plugin (`SecureStoragePlugin.kt`)
   carefully and confirm sign-in / resume / sign-out all still work on device.
4. **WON'T FIX — narrowing the Drive scope to `drive.file`.** It would have been
   the deepest available security fix (a compromised token could only read
   explicitly-picked books, not the whole Drive), but it is **incompatible with
   this app's core model**: `drive.file` can't enumerate a folder's contents, so
   "connect a folder, new books appear" cannot work. Attempted and reverted
   2026-07-22 — see the behavior note above. The app therefore stays on the
   restricted `drive.readonly` scope, and the compensating control is Google's
   own verification/CASA review (roadmap item 2). Revisit only if the product
   ever accepts a per-file "select your books" model with no auto-sync.
5. ~~**Capacitor's native-bridge logging could dump OAuth tokens to
   Logcat**~~ — **DONE (2026-08-13).** Found while capturing F4 evidence for
   the CASA questionnaire: a Logcat capture during native sign-in contained
   the full Google access and refresh tokens in plaintext. Root cause is
   Capacitor's own `native-bridge.js` — `cap.toNative()` calls
   `cap.logToNative(callData)` whenever `Capacitor.isLoggingEnabled` is
   true, dumping the full plugin call payload (including `CapacitorHttp`'s
   raw HTTP response body from the token exchange) through `console.info`,
   which Android's default WebView console handler forwards to Logcat.
   **Not a production vulnerability** — `capacitor.config.json` had no
   `loggingBehavior` set, so it defaulted to `"debug"`, which
   `CapConfig.java` ties to Android's own `ApplicationInfo.FLAG_DEBUGGABLE`
   (true only for debug-variant builds, never a signed release build). The
   token only appeared because the capture was taken from a debug build
   during testing — exactly as Capacitor intends. Set `loggingBehavior`
   explicitly to `"none"` in `capacitor.config.json` anyway rather than rely
   on that implicit default: cheap, removes a dependency on a Capacitor
   default that could change in a future major version, and now directly
   backs the claim in Config 2 of the CASA questionnaire. Verified the
   setting reaches the built app via `npm run sync`.
