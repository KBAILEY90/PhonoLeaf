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
- ~~Not done, flagged as a bigger feature per BACKLOG.md: follow-along
  word/sentence highlighting~~ — **SHIPPED 2026-08-19, approximate timing.**
  See "Follow-along word highlighting" near the end of this file.
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
   Node harness, then walked through the real Cloudflare setup on the
   owner's machine (2026-08-14): KV namespace created, both secrets set,
   local `wrangler dev` confirmed working (health check + the 401-without-
   a-token path). **The Worker is now live on Cloudflare** — a side effect
   of `wrangler secret put` offering to create it the first time, not a
   deliberate deploy — but inert, since nothing calls it yet. **Not called
   from the app** — adding the `openid` scope and gating the UI on
   entitlement are separate steps, held
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

## Follow-along word highlighting (approximate timing, 2026-08-19)

BACKLOG.md section F flagged this as a strong accessibility win for
dyslexia/ADHD but noted it needs word-level timing the TTS engine doesn't
provide. **Confirmed directly against sherpa-onnx's own source** (both the
Kotlin binding, `sherpa-onnx/kotlin-api/Tts.kt`, and the C++ core,
`sherpa-onnx/csrc/offline-tts.h`): `generate()` returns only
`{samples, sampleRate}` — no timing of any kind, at any layer, for either
Kokoro or Piper. VITS-family models compute a per-phoneme duration
internally as part of synthesis, but it's consumed inside the ONNX graph and
never exposed as an output; getting it out would mean re-exporting the
underlying models with an added output tensor, which we don't control.
True per-word alignment is off the table.

**Shipped instead: approximate timing.** Each chunk's total spoken duration
is knowable (`a.duration` once the shared `<audio>` element resolves it);
distribute that duration across the chunk's words proportionally by
character count, and highlight whichever word's time window contains the
current playback position. Not frame-accurate, but delivers the real
accessibility benefit (visually tracking roughly where the voice is).
**Scope: native/Kokoro `<audio>`-element path only** (`TTS._playAudio`,
what `_engineNow()` routes to for essentially every real session) — the Web
Speech device-voice fallback (`_speakWeb`) has a different, better mechanism
available (real `onboundary` events) that's a deliberate, separate
follow-up, not built now.

- **Word→DOM mapping is the hard part, not the timing math.** A chunk's
  `.node` (set in `_chunksFromSegments`) is the whole source block element,
  but `_split()` breaks a long paragraph into several ≤220-char chunks that
  all share the same `.node` — so matching by searching for `chunk.text`
  inside the node's flattened text lands on the wrong slice for chunk 2+ (and
  headings get a synthetic trailing period that isn't in the DOM). Fixed by
  matching on **word count** instead: `TTS._buildWordSchedule(chunk)` sums
  the `/\S+/g` word counts of earlier chunks sharing the same `.node` to find
  `wordStart`, then walks the block's real text nodes with a `TreeWalker`
  and slices out exactly this chunk's words. Needs no changes to
  `_chunksFromSegments`/`_split`/`loadPageText` — fully additive.
- **A real bug caught by testing, not assumed away**: a word split across
  DOM nodes by inline formatting with no surrounding whitespace (e.g.
  `extra<em>ordinar</em>ily`) was first tokenized as three separate "words"
  instead of one, since a naive per-text-node `/\S+/g` scan resets at each
  node boundary. Fixed with a merge-across-nodes tokenizer in
  `_buildWordSchedule`: a word that reaches exactly to the end of one text
  node stays "pending" and is merged with the next node's leading fragment
  if it starts at that node's very first character (no intervening
  whitespace node), only flushed as a complete word once whitespace is
  actually encountered. Verified in a browser harness with a synthetic
  `extra<em>ordinar</em>ily` node: the merged word now correctly reports as
  one `Range` spanning multiple text nodes, and a `Hello <em>world</em>!`
  case correctly merges `world!` (no space before the `!`) while keeping
  `Hello` separate.
- **Rendering: CSS Custom Highlight API, not DOM mutation.** Wrapping words
  in `<span>`s would mean mutating the iframe DOM that epub.js owns and
  re-renders — fragile, per the existing warning about `section.load()`
  corrupting epub.js's document reference. Instead `CSS.highlights`/
  `Highlight`/`::highlight()` highlights a `Range` with **zero DOM
  mutation**. Confirmed against the vendored `vendor/epub.min.js` that
  `themes.register()`/`select()` is a thin pass-through to a real
  `CSSStyleSheet.insertRule` against the iframe's own stylesheet — so
  `'::highlight(pl-spoken)': {...}` was added as a third key in the same
  object `Reader.applyReadTheme()` already passes to `themes.register('kb',
  {...})`, no separate `<style>`-injection fallback needed. Follows that
  function's existing convention of hardcoding light/dark hex literals
  (derived from `--accent`) rather than `getComputedStyle`, since the iframe
  can't see the outer `:root` vars either way. `CSS.highlights` is a
  per-document registry — always resolved from the **iframe's own
  `contentWindow`**, never the outer app window; feature-detected and
  silently no-op'd if unsupported.
- **Duration source: `a.duration`, not the native plugin's `durationMs`.**
  `_playAudio` also plays the WASM/browser-Kokoro path (`_synthKokoro`, web
  build), which returns only a blob URL with no duration metadata — so
  `durationMs` (native-plugin-only) can't be the unified source across both
  branches. `a.duration` after a short `loadedmetadata` wait (capped ~300ms,
  fails closed to no-highlight for that chunk, never delays playback) works
  for both. Since `a.currentTime`/`a.duration` already reflect the
  rate-adjusted real-time position while `a.duration` stays intrinsic,
  `currentTime / duration` is rate-invariant automatically — no special
  handling needed when the user changes speed mid-chunk.
- **Driven by `requestAnimationFrame`, not `timeupdate`** (fires too
  coarsely, ~250ms typical, for snappy per-word sync), gen-stamped exactly
  like every other async callback in the file (`gen !== this._gen` bails),
  only touching `CSS.highlights` when the active word index actually
  changes.
- **Teardown is one choke point, not three.** `_stopAudio()` is already
  called from every path that leaves a chunk (`stop()`, `skipPage()`,
  `_bgNav()`) — `TTS._clearHighlight()` (cancels the rAF handle, clears the
  `CSS.highlights` registration, resets schedule state, never throws) is
  the first line inside `_stopAudio()` itself rather than patched into three
  call sites separately.
- **Fails closed on**: the Settings toggle being off, `TTS._bgMode` (no live
  rendered page during background reading — confirmed this guard is
  load-bearing, not redundant, since background mode's
  `_currentSectionChunksWithNodes` deliberately reuses the same live iframe
  document normal reading uses), a missing/disconnected `chunk.node`,
  unsupported `CSS.highlights`, or a word-count reconciliation failure.
  Never throws, never blocks playback.
- **Settings toggle**, copying the "Keep screen on" pattern exactly: new
  `#set-followalong` row, `pl_followalong` localStorage key, **default OFF**
  (unlike wakelock's default-on) since this is a new, approximate, visually
  distinctive change that shouldn't surprise existing users — opt-in.
  `follow_along`/`follow_along_sub` i18n keys added to both `en`/`fr`
  `STRINGS` blocks.
- Verified end-to-end in a browser harness: built a synthetic iframe with
  real epub-like markup (including the inline-tag word-split case above and
  a multi-chunk-per-block case), confirmed `_buildWordSchedule` slices the
  correct words for each chunk in a split paragraph (chunk 2 doesn't restart
  from word 0); played a real `<audio>` element against a synthetic silent
  WAV (as a `blob:` URL — the CSP's `media-src 'self' blob:` correctly
  rejected a first attempt using a `data:` URL, confirming the CSP is doing
  its job) and confirmed the highlight tracks forward correctly as
  `currentTime` advances (first word at t=0, a middle word at the
  proportional halfway point, the last word near the end); confirmed
  `_stopAudio()` cleanly tears down the highlight and cancels the rAF
  handle; confirmed `_bgMode` prevents any highlight from being registered;
  confirmed the Settings toggle round-trips through `localStorage` and
  `Settings.render()` syncs the checkbox correctly in both languages. **Not
  device-verified** — whether the Custom Highlight API actually paints (not
  just avoids throwing) depends on the WebView/browser engine version;
  worth a real on-device check.

**Owner confirmed working on web the same day.** Native/Android still needs
a device check for whether the Custom Highlight API actually paints there.

**Quick-access reader toggle added same day (owner request): "can we add
the option on the reader itself?"** A settings-only toggle meant leaving
the reader to turn this on/off. Added `#hl-btn`, a new icon-only button in
`.reader-top` between the back arrow and the chapters hamburger (a simple
hand-drawn "highlighted text lines" icon — primitive `<line>`/`<rect>`
shapes, not a memorized icon-font path, per the notification-icon lesson
elsewhere in this file about not guessing icon designs), toggled via
`TTS.toggleFollowAlong()`.
- **Consolidated into one entry point** so the Settings checkbox and the
  reader button can never disagree: `TTS.setFollowAlong(on)` now owns the
  localStorage write and both UI syncs (`TTS._syncFollowAlongUI()`);
  `Settings.setFollowAlong` is a one-line delegate to it, and
  `Settings.render()`'s old inline checkbox sync was replaced by the same
  shared sync call.
- **Turning it on while a chunk is actively playing starts highlighting
  immediately, mid-chunk**, rather than waiting for the next chunk —
  `_startHighlight` rebuilds its schedule from the chunk plus the audio's
  current duration regardless of how far playback already is, so calling it
  again mid-stream correctly lands on the right word instead of restarting
  from word 0 (guarded on `this.active && !this._audio.paused &&
  this._engineNow() !== 'web'`, so it's a no-op when paused or on the web
  Speech fallback path).
  `TTS._syncFollowAlongUI()` is also called once from `Reader.open()` so the
  button reflects the persisted setting correctly on a fresh book open, not
  just after a Settings visit.
- Verified in a browser harness: the button's `aria-label`/`title` and its
  `.on` class toggling in both directions (button → Settings checkbox,
  Settings → button); confirmed toggling ON mid-chunk (audio already 75%
  through a synthetic clip) immediately registers the correct current word,
  where before the toggle there was no highlight at all (since it was off
  when that chunk started). Screenshotted the reader top bar in both on/off
  states to confirm the icon renders clearly and doesn't crowd the existing
  back/chapters buttons. Not device-verified.

**Moved into the `.tts-pill` next to Voice the same day (owner request:
"can we put the read-along icon next to Voice instead of next to the
hamburger menu?").** `#hl-btn` relocated out of `.reader-top` into
`.reader-bottom .tts-pill`, immediately before `#voice-btn` (order: prev,
play, next, speed, highlight, voice). New `.hl-pill` class (tighter
`0.4rem` padding, `flex-shrink: 0`) and an 18px (down from 20px) icon so it
sits comfortably among the pill's other compact controls rather than using
`.icon-btn`'s default top-bar-sized padding. `TTS.setFollowAlong`/
`toggleFollowAlong`/`_syncFollowAlongUI` are untouched — this was a pure
markup/CSS move, no JS logic changed. Verified in a browser harness at a
real 375px phone width: the pill (354px) fits inside the viewport with
room either side, correct button order confirmed via DOM traversal
(`hl-btn.nextElementSibling === voice-btn`). Screenshot capture wasn't
available in this session (Browser pane display issue unrelated to the
code), so the visual result rests on the DOM-geometry check plus the
icon's already-confirmed rendering from the prior top-bar placement — worth
a glance next time the pane's up, and this is folded into the same
on-device check as the rest of this feature.

**Real bug found on the first device test (owner-reported): "if I change
page while the read-along is on, it doesn't continue on the next page.
There's no more read-along."** Root cause was in `_buildWordSchedule`'s
`wordStart` calculation, not the page-turn plumbing itself. It computed
`wordStart` by summing the word counts of earlier chunks in `this.chunks`
that share the same `.node` — correct for the SAME-PAGE multi-chunk case
(`_split()` breaking one long paragraph into several chunks), but wrong for
the first chunk of a NEW page: `_resumeRead()` resets `this.chunks = []` on
every page turn, so the first chunk of any page always had `wordStart = 0`,
regardless of whether the underlying paragraph actually **started on the
previous page** — which is the common case, since most page breaks in a
real book fall mid-paragraph, not between paragraphs. The DOM node itself
still holds the paragraph's FULL text (unaffected by pagination), so
`_buildWordSchedule` would confidently build a schedule for words 1-N of
the paragraph and register a `Range` pointing at them — except those words
are the ones that were on the PREVIOUS page, now scrolled out of the
paginated column view. The highlight was technically still being applied,
just to text that had scrolled off-screen, which is indistinguishable from
"no highlight at all" to the user.
- **Fix: anchor the run's start by content match, not by assumed position.**
  Instead of trusting `this.chunks` array position (which resets every
  page), `_buildWordSchedule` now finds the run of same-`.node` chunks
  within the CURRENT page's chunk list (`runStartIdx`, unchanged from
  before), then searches the node's full word list (`blockWords`, from the
  `TreeWalker`) for a 5-word matching prefix of that run's first chunk's
  text, and uses WHEREVER that content actually lands as the true
  `wordStart` — correctly finding the continuation point deep into the
  paragraph when this page picks up mid-sentence, instead of always
  assuming the paragraph's true beginning. Same-page multi-chunk math
  (summing forward from the anchor to the current chunk) is unchanged.
  Fails closed (returns `null`, no highlight) if the anchor genuinely can't
  be found, same fail-closed philosophy as the rest of this feature.
- Verified in a browser harness: all prior regression cases (simple
  paragraph, inline-tag word merge, same-page multi-chunk split) still pass
  unchanged; a new test simulating a 16-word paragraph split across two
  "pages" (`this.chunks` reset between them, exactly matching
  `_resumeRead()`'s real behavior) confirms page 2's chunk now correctly
  anchors starting at word 9 ("nine") instead of wrongly repeating words
  1-8 ("one"..."eight", which would have been off-screen on page 1); and a
  chunk whose claimed text genuinely isn't in the DOM still fails closed
  (returns `null`) rather than guessing or throwing.

## Local device file import (2026-08-19)

`BACKLOG.md` section E flagged broadening beyond Google Drive as real
product value, and named local device import as the one to build first: no
third-party OAuth app to build and get reviewed, and it directly enables
both "copy DRM-free books in" and genuine offline from a completely fresh
install. **Google sign-in stays mandatory** (already decided elsewhere in
this file: entitlement/payments are being built around the Google account
id) — this is a new book *source* inside the authenticated app, not a way
to skip sign-in. v1 is one-shot import only: no folder-watching, no
persistent re-sync, no iOS work (no iOS build exists yet).

- **No native plugin needed — verified by reading Capacitor's own source,
  not assumed.** `node_modules/@capacitor/android`'s
  `BridgeWebChromeClient.onShowFileChooser` already wires a plain
  `<input type="file">` to Android's native Storage Access Framework
  picker, mapping the HTML `accept` attribute to `Intent.EXTRA_MIME_TYPES`.
  No custom plugin, no third-party dependency, no `MainActivity.java`
  change, no new manifest permission — the exact same mechanism the
  bug-report photo attachment already uses (`#bug-photo`,
  `BugReport.onPhotoChange`). Reading is even simpler here:
  `file.arrayBuffer()` needs no Capacitor-bridge base64 round trip, unlike
  the photo-attach flow, which only base64-encodes because it hands off to
  `EmailComposerPlugin`.
  New hidden `<input id="local-import-input" type="file"
  accept=".epub,application/epub+zip" multiple hidden
  onchange="LocalBooks._onPick(this)">`, static body markup, triggered from
  Settings, the Home empty state, and both Library empty states.
- **Identity: `'local:' + crypto.randomUUID()`.** No `source` field added
  to the book object — every place that needs to tell a local book from a
  Drive one just checks the id prefix. `Drive.listEpubs` entries are
  `{id, name, thumbnailLink, size}`; local entries are `{id, name, size}`
  (no `thumbnailLink`, harmless — `Library._itemHTML`'s cover fallback
  chain already tolerates that).
- **Storage: `BookCache` reused exactly as-is, zero changes.** It was
  already fully source-agnostic (`set(id, buf)` takes any string id and any
  `ArrayBuffer`) — built originally for caching Drive downloads offline
  (see the Offline entry earlier in this file), it needed nothing new to
  also hold locally-imported bytes as their *only* copy. `Reader.open()`
  needed **zero changes**: it already checks `BookCache.get(book.id)`
  before ever touching `Drive.download`, so a cached book behaves
  identically regardless of source.
- **Cover/metadata captured eagerly at import time, not lazily.**
  `Covers.fromBook(book, meta)` (already existed, used by `Reader.open` for
  every book) takes an already-open epub.js `Book` plus `{id, size}`,
  captures metadata via `Meta.capture` internally, and caches the cover
  blob under key `` `${id}:${size||''}` ``. Calling it once per file at
  import time — open with `ePub(buf)`, await ready, call `Covers.fromBook`,
  `book.destroy()` — means `Covers.loadAll()`/`Covers._one()` (which WOULD
  call `Drive.download` on a cache miss) instead gets a `CoverCache` hit
  later and never reaches that Drive-specific branch. **No changes needed
  to `Covers._one`.**
- **A real bug caught by testing, not assumed away: `ePub(buf).ready` does
  not reject on malformed input — it hangs forever.** The plan was to use
  "does `ePub()` open successfully" as the validation for a user-picked
  file (a genuine system boundary, unlike a Drive download that was already
  valid when originally added). A try/catch around that call looked
  sufficient, but testing a 5-byte garbage file directly confirmed the
  promise never resolves and never rejects — a try/catch never sees a hang.
  Left unfixed, one bad file anywhere in an import batch would have frozen
  the entire batch (including every file queued after it) with the
  "Importing…" toast stuck up forever, no way out short of reloading the
  app. Fixed by racing `book.ready` against an 8-second timeout inside
  `LocalBooks.import()`'s per-file try block, treating a timeout the same
  as a real validation failure (file skipped, name collected for the
  summary toast). Verified directly: an isolated 5-byte file times out and
  rejects at 8s as designed (confirmed via `Promise.race` in a harness);
  a 3-file batch (2 valid synthetic EPUBs + 1 garbage file) completes in
  ~8s total (not 24s, not forever) with the 2 valid files correctly
  imported and the bad one correctly skipped.
- **`Library.load()`'s empty-state branch needed a real structural fix, not
  just an assignment reorder.** The "no Drive folder configured" branch
  showed the "choose a folder" prompt and returned early *unconditionally*
  on `!fid` — a signed-in user with only local imports and no Drive folder
  would have hit that branch first and never seen their own books. Fixed by
  computing the merged list (`Library._withLocal(books)`, concatenating
  `LocalBooks.asBookEntries()`) *before* the empty-state decision, and
  keying that decision on the merged list's length instead of on `!fid`
  alone. The "empty Drive folder" branch needed no equivalent change — once
  assignment order was fixed there, its existing `!State.books.length`
  check was already checking the merged list. `Library._driveBooks` now
  holds the raw Drive-only list separately, so `_cacheBookList`'s
  folder-scoped Drive-outage snapshot never gets polluted with local
  entries. New `Library.refresh()` re-merges and re-renders without a Drive
  network round trip, used after import/remove instead of a full
  `Library.load()`.
- **`Library._offlineBtnHTML(id)` branches on the id prefix.** A local book
  can't be "un-cached" back to a remote source, so its card gets a delete
  button (new `_ICON_TRASH`, a simple stroke-primitive shape matching this
  file's established icon convention, not a memorized complex path)
  instead of the save/remove-offline toggle — no change needed to
  `_itemHTML` itself, since both the grid card and table row already pull
  their action button from this one function. New `Library.removeLocal(id)`
  uses the existing `ConfirmModal.show(cb, msg, okLabel)` pattern
  (destructive — PhonoLeaf holds the only copy).
- **Deletion thoroughness fix, directly motivated by this feature.**
  `MyData.deleteAll()` already deleted `CoverCache`'s `'phonoleaf'`
  IndexedDB and swept every `pl_*` localStorage key, but never touched
  `BookCache`'s actual storage (`'phonoleaf-offline'` IndexedDB on web, the
  native `books/` directory) — a pre-existing gap that was harmless while
  `BookCache` only ever held copies of things Drive still had, but becomes
  a real correctness problem once it can hold a locally-imported book's
  *only* copy. New **`BookCache.clear()`** — native: one `rmdir({recursive:
  true})` call; web: closes the memoized DB connection before
  `indexedDB.deleteDatabase(...)` (deleting with an open handle can hang) —
  wipes ALL cached book bytes, Drive-offline copies and local imports
  alike, matching the Settings row's own promise to "erase local data."
  Also new **`CoverCache.remove(key)`** (mirrors `set`'s transaction shape),
  called from `LocalBooks.remove()` so a removed local book's cover blob
  doesn't linger forever with no folder-rescan to ever evict it — unlike
  `Meta`, which has no per-id remove and doesn't need one (UUIDs never
  collide, so an orphaned metadata string is genuinely harmless).
  Verified directly: `BookCache.clear()` leaves a fresh `_open()` connection
  reporting a real 0 count in the IndexedDB store (not just an empty
  localStorage index), and `CoverCache.remove()` round-trips correctly.
- **UI entry points**: a new Settings row ("Local books" / "{n} imported")
  mirrors the existing "Drive folder" row exactly, count synced in
  `Settings.render()` alongside the existing folder/account lines. Both
  Library empty states and Home's empty state gained a second button,
  `Library._importBtn()`, alongside the existing `_pickBtn()`. No
  management modal for v1 — removal already lives on the grid card itself.
- Verified end-to-end in a browser harness against two REAL synthetic EPUBs
  built with the vendored JSZip (not mocked file objects): full import →
  `BookCache`/`Meta`/`Covers` all populated correctly → book appears in the
  grid with the delete button, not the offline toggle → `Library.load()`
  with zero Drive folder configured shows the local books directly instead
  of the "choose a folder" prompt → removal via the grid's delete button
  (real `ConfirmModal` open + confirm) correctly removes only the targeted
  book, leaving the other one cached and in the grid → French labels and
  the removal-confirmation message render correctly. **Not device-verified**
  — same caveat as every native-adjacent claim in this file, though the
  central "no plugin needed" finding was reached by reading Capacitor's own
  installed source directly rather than assumed, so the main open question
  on-device is narrower than usual: whether Android's SAF picker's MIME
  filtering ever hides a real `.epub` file whose provider mis-declared its
  type (if so, broaden `accept` and lean on the `ePub()`-open validation as
  the real filter instead).

## Connect a local folder, with manual refresh (2026-08-20)

Owner follow-up on local import: picking a folder in the file picker
"wasn't working" — turned out to be expected, not a bug (Capacitor's file
chooser bridge only builds `ACTION_GET_CONTENT`, individual files, no
folder-tree support — confirmed by grepping its source, no
`webkitdirectory`/`ACTION_OPEN_DOCUMENT_TREE` handling anywhere). The
owner asked for a folder that "stays updated" via a manual Refresh instead
of true background sync, and to unify Settings' two rows ("Drive folder" /
"Local books") into one "Books folder" entry point choosing Drive vs. this
device.

- **The first plugin in this codebase that launches an activity and gets a
  *result* back.** Every existing plugin (`PhonoLeafTtsPlugin`,
  `SecureStoragePlugin`, `EmailComposerPlugin`) only fires an intent and
  forgets (e.g. opening the mail app). Picking a folder needs Android's
  Storage Access Framework `ACTION_OPEN_DOCUMENT_TREE`, which returns a
  result the app must receive. **Verified directly against the installed
  Capacitor 8.4.2 core source**, not assumed or copied from an example (no
  installed plugin package uses this pattern, so there was nothing to copy
  from): `Plugin.startActivityForResult(call, intent, callbackName)`
  paired with a method annotated `@ActivityCallback` taking exactly
  `(PluginCall call, ActivityResult result)` — this contract is spelled
  out in Capacitor's own source (down to the exact rejection message it
  throws if a callback's signature doesn't match). New file
  `LocalFolderPlugin.kt`, three methods:
  - `pickFolder()` — launches the tree picker; on success takes a
    **persistable** URI permission (`FLAG_GRANT_READ_URI_PERMISSION`) —
    without this the grant dies with the process, breaking "connect once,
    refresh later" on next launch; resolves `{uri, name}`. On cancel,
    resolves `{uri: null}` (a clean no-op signal, not an error).
  - `listFolder({uri})` — lists `.epub` children via
    `DocumentFile.fromTreeUri(...).listFiles()`, **re-derived fresh on
    every call** rather than caching child URIs across restarts (sidesteps
    SAF's known stale-child-URI footgun — only the tree URI is ever
    persisted). A revoked permission throws `SecurityException` → reject,
    so JS can prompt to reconnect.
  - `readFile({uri})` — reads a child URI's bytes, base64-encodes them,
    resolves `{data: base64}`. **Deliberately matches
    `@capacitor/filesystem`'s own `readFile` response shape** so JS
    decodes with the exact same `BookCache._b64ToBuf()` helper already
    written for the Filesystem plugin — no second decoder needed, and this
    reuse was confirmed working end to end in the mocked-native harness
    below.
  New Gradle dependency: `androidx.documentfile:documentfile:1.0.1` (wraps
  SAF tree URIs, avoids hand-rolling `DocumentsContract` column queries;
  no new transitive deps beyond `androidx.core`, already present).
- **Web: `showDirectoryPicker()`, feature-detected, never assumed
  available.** Confirmed via research: Chromium-only (Chrome/Edge/Opera),
  Safari and Firefox support neither in any version. A returned
  `FileSystemDirectoryHandle` is natively structured-clone-storable by the
  browser (a real handle, not a plain object — see the testing note
  below) — persisted in a new tiny IndexedDB store, `LocalFolderHandle`,
  mirroring `BookCache`/`CoverCache`'s exact shape (own DB
  `phonoleaf-localfolder`, one store, fixed key since only one folder
  connects at a time). Permission is re-verified on every refresh via
  `queryPermission`/`requestPermission`, called with no `await` ahead of
  it so it stays inside the Refresh button's user-gesture context (a
  documented requirement of the API). **Genuinely unverified: whether
  `showDirectoryPicker` exists at all inside the Capacitor Android
  WebView** (as opposed to a real browser tab) — so `connectFolder()`'s
  web branch degrades to the existing one-shot file input, with an
  explanatory toast, whenever the API is absent. Nothing breaks either way.
- **JS extends `LocalBooks` rather than forking a parallel module.**
  Refactored `import()`'s per-file body out into
  `_importOneBuffer(buf, name, size)` (validate-by-opening with the
  existing 8s timeout race, `BookCache.set`, `Covers.fromBook`, index
  write) so the one-shot file-input path and the new folder-refresh path
  share one pipeline — a bug fixed in one is fixed in both. New:
  `folderInfo()`, `connectFolder()`, `refreshFolder()`, `_listNative()`,
  `_listWeb()`, `disconnectFolder()`.
- **A real correctness catch made during design, not caught by testing
  this time but reasoned through directly**: reconnecting the *same*
  already-connected folder must not reset `pl_local_folder_map` (the
  "already imported from this folder" index), or the next refresh would
  re-import every file in it as duplicates — `_importOneBuffer` always
  mints a fresh id, it has no way to recognize "I already have this
  content under a different id." `connectFolder()` now compares the newly
  picked folder's identity (`uri` on native, `handle.name` on web,
  acknowledged as a weaker signal there but the best available) against
  what was previously stored, and only resets the map when they actually
  differ. **Verified directly in the harness**: reconnecting the same
  mocked folder left the map byte-for-byte identical, and a subsequent
  refresh correctly found nothing new to import.
- **`refreshFolder()` centralizes its "reconnect needed" toast in one
  place**, deliberately not inside `_listWeb()`/`_listNative()`
  themselves — an earlier draft had `_listWeb()` toast on its own denied
  permission while the "Refreshing…" toast (shown with duration 0,
  meaning "stays up until explicitly hidden") was still on screen, which
  would have raced the two toasts and then prematurely hidden the
  *second* one right after showing it. Fixed by having both list functions
  return `null` on any failure and letting `refreshFolder()` call
  `hideToast()` once, then show exactly one outcome toast, regardless of
  whether the failure was a thrown native `SecurityException` or a denied
  web permission.
- **Settings unified**: the two existing rows collapsed into one "Books
  folder" row (label + composed sub-text: "Google Drive: {name}" / "This
  device: {name}" / "Google Drive + this device" / "Not selected"), a
  Refresh button shown only when a local folder is connected, and a
  "Change" button opening a new `FolderChooser` modal. That modal reuses
  the app's existing generic `.modal-backdrop`/`.modal-sheet` chrome
  (already used verbatim by six other modals, confirmed before adding
  anything new) and the `.voice-item` row pattern `VoicePacks` already
  uses for its own state-dependent action rows (`style="cursor:default"`
  override, since `.voice-item` defaults to `cursor:pointer` for
  single-click rows, which this isn't) — the right template specifically
  *because* the device row needs multiple state-dependent actions
  (Connect vs. Refresh+Disconnect) a single clickable row can't express.
  Disconnecting confirms first via the existing `ConfirmModal` (explicit
  that books already imported stay in the library — only the connection
  is removed, matching how changing/removing a Drive folder never deletes
  cached books or progress either). The old one-shot "import individual
  files" capability stays reachable as a static secondary link at the
  bottom of the chooser, not its own Settings row — it still has
  independent value (one odd file that isn't in the connected folder) a
  connected folder doesn't replace.
- **`MyData.deleteAll()`** gets one more line, `LocalFolderHandle.clear()`
  — the generic `pl_*` sweep already clears the folder *pointer*
  (`pl_local_folder_uri`/`_name`/`_map`), same as it already does for
  `pl_local_books`, but the web IndexedDB-stored handle itself needs
  explicit clearing, same reasoning as `BookCache.clear()` before it.
- **Old `drive_folder`/`local_books`/`local_books_none`/`local_books_count`
  i18n keys deliberately left in place, unused** — cheap, and removing
  them risks missing some other reference; a real cleanup pass can verify
  and remove them later if wanted.
- **A real testing lesson, not a bug**: the first harness pass mocked a
  `FileSystemDirectoryHandle` as a plain JS object with function
  properties (`queryPermission`, `values`, etc.) and got a silent
  zero-books result. Root cause: `LocalFolderHandle.set()`'s IndexedDB
  `put()` throws `DataCloneError` on an object containing functions
  (structured clone can't serialize them) — caught by the existing
  try/catch and swallowed, exactly as designed for a real failure, just
  triggered here by an artificial one. Real `FileSystemDirectoryHandle`
  objects have special native browser support for structured cloning that
  a hand-rolled mock can't replicate; fixed by stubbing
  `LocalFolderHandle.get()`/`set()` directly to hand back the mock
  in-memory instead of round-tripping it through real IndexedDB, which
  correctly isolates and tests `LocalBooks`' own logic rather than the
  browser's native handle-cloning support (out of scope to fake). A second
  mocking gap (`App.isNative()` stubbed true without also stubbing
  `Capacitor.Plugins.Filesystem`, which `BookCache.set` separately checks
  on the native path) produced the same "zero books imported" symptom for
  a completely different, equally artificial reason — worth remembering
  next time a native-path harness silently imports zero books: check
  every plugin the call chain touches, not just the one the test is
  actually about.
- Verified end-to-end in a browser harness: web connect → refresh →
  reconnect-same-folder (no duplicate map reset) → disconnect (folder
  cleared, books retained) → denied-permission refresh (map untouched,
  correct toast); native connect → refresh (full pipeline: `pickFolder` →
  `listFolder` → `readFile`'s base64 decoded via the reused
  `BookCache._b64ToBuf` → `BookCache.set` → `_importOneBuffer` →
  `Covers.fromBook`) → cancel (`{uri:null}`, connection unchanged) →
  simulated `SecurityException` (same reconnect-needed toast as the web
  path); the chooser modal's two rows render the correct state-dependent
  buttons in both the connected and not-connected states; the disconnect
  confirmation shows the right message and only disconnects on confirm;
  French labels render correctly. **Not device-verified** — the Kotlin
  cannot be compiled or run here (no JDK/Android SDK, as with every other
  native change in this project); the plugin is written and reviewed
  carefully against Capacitor's real, verified API contract, but a real
  device is needed to confirm `showDirectoryPicker` inside the WebView,
  that the SAF permission survives a real app kill + relaunch, and that
  `DocumentFile.listFiles()` is fast enough on a large real folder.

## Bug: local folder refresh on web threw "is not iterable" (2026-08-20)

Owner-reported the very first time this shipped: connecting/refreshing a
local folder on web always failed with "Permission to that folder was lost.
Reconnect it to refresh." The first two response rounds were a
misdiagnosis — the report initially read as a native-Android permission
issue (device tests earlier the same day were all native), so two rounds of
diagnostic-only fixes went into `_listNative()`/the Kotlin plugin, neither
of which could have touched the real bug. **The owner then clarified they
were testing on web**, which reframed the whole investigation.

**Root cause, found and reproduced live, not guessed**: `_listWeb()` called
`handle.values()` and destructured each result as `[name, entryHandle]` —
but per the File System Access spec, `FileSystemDirectoryHandle.values()`
yields bare handle objects, not `[name, handle]` pairs (mirrors `Map`'s
`keys()`/`values()`/`entries()` split — only `.entries()` returns pairs).
Destructuring a bare handle as a 2-tuple threw `TypeError: .for is not
iterable` on literally every file in the folder, matching the exact text
reported. **Confirmed directly in a live browser using OPFS**
(`navigator.storage.getDirectory()` — same `FileSystemDirectoryHandle`
class, no folder-picker gesture needed to test it): reproduced the exact
error string with `values()`, then confirmed `entries()` yields correct
`[name, handle]` pairs and fixed it. Changed `_listWeb()`'s one line from
`handle.values()` to `handle.entries()`.
**Process lesson**: the two earlier diagnostic-only commits weren't wasted
(the native-side error tagging in `_listNative()` is still a real
improvement, and the `writeWav` gain-cap fix landed in the same push,
unrelated but reported at the same time — see below) but neither could have
found this, because the failure was never on the path they instrumented.
When a report's platform isn't stated, ask before spending a round chasing
the wrong code path.

**Reported in the same message, unrelated**: audiobook volume "sometimes
very loud, sometimes less" while listening continuously in the car. Traced
to `PhonoLeafTtsPlugin.kt`'s `writeWav()`, which peak-normalizes each
synthesized clip (one per sentence/paragraph chunk) independently to ~0.95,
with correction gain capped at 6x (+15.6dB). Real speech has genuine
dynamic range — a quiet or trailing-off sentence has a much lower natural
peak than an emphatic one — so correcting each clip independently to the
same target erases that range and can swing a quiet sentence up to 6x
louder than its neighbors. Lowered the cap to 2x (+6dB): still corrects the
documented cross-model loudness gap (vctk quieter than libritts) this
existed for, just far more gently. **Not a full fix** — a proper one would
normalize once per model/session rather than per clip, or use RMS instead
of peak — this is a conservative, low-risk, easily-tunable reduction.
**Not device-verified** — no JDK/Android SDK in this environment; needs a
real listen to confirm 2x is gentle enough, or whether it needs to go lower.

## Onboarding now offers Drive or local device, and Settings row renamed (2026-08-20)

Owner feedback: onboarding only ever auto-opened the Google Drive folder
browser, even though a local device connection is now an equally real,
persistent source (see "Connect a local folder" above) — a user who wanted
to start with local books had no way to say so at setup time. Also renamed
the Settings row from "Books folder" to **"Book Folders"** (EN) / "Dossiers
de livres" (FR), matching that both sources are first-class and can coexist.

- **`App._promptFolderIfNeeded()`** now opens `FolderChooser` (in onboarding
  mode) instead of jumping straight to `FolderBrowser` (Drive-only) —
  `FolderChooser` already existed for exactly this choice (built for the
  Settings "Change" flow), so this reuses it rather than building a new
  screen. Passing `true` renders a short intro banner ("Add your books" /
  explainer) above the two rows, matching the exact pattern `LangPacksModal`
  already established for its own onboarding intro
  (`open(onboarding)`/`_onboarding` flag) — except `FolderChooser` doesn't
  need a persisted flag, since picking either option closes the modal
  immediately with no async re-render loop to survive in between.
- **A real latent bug found and fixed while touching this**:
  `hasChosenFolder()` (gates the onboarding prompt) only ever checked the
  Drive folder id/name — a local-only connection was invisible to it, so a
  local-only user would have been re-prompted for a Drive folder on *every
  single launch*. This was harmless before today (nothing could reach a
  local-only state during onboarding, since onboarding only offered Drive)
  but would have surfaced the moment this feature shipped. Now also checks
  `LocalBooks.folderInfo().connected`.
- **Voice-pack onboarding (`VoicePacks.maybeOnboard()`) previously only
  fired from `setFolder()`** (the Drive path) — a user who connected only a
  local folder during the new choice step would never have been prompted to
  download a voice. Added the same `setTimeout(() => VoicePacks.maybeOnboard(),
  400)` call to both branches of `LocalBooks.connectFolder()` (native and
  web). Safe to call from multiple sources — `maybeOnboard()` already guards
  itself to fire at most once ever.
- **Drive's row button now reads "Connect" while nothing is picked, "Change"
  once something is** — previously always said "Change," which read oddly
  against an empty "Not selected" value once this modal became the
  onboarding entry point (the local-device row already had this Connect/
  Refresh+Disconnect split; Drive's was the odd one out — since superseded,
  see directly below).
- Verified live in a browser: `hasChosenFolder()` correctly flips to `true`
  for a local-only connection (previously `false`); the "Book Folders" label
  renders correctly in both languages; the onboarding intro banner shows
  only when opened with `onboarding=true` and never otherwise; the Drive
  button correctly reads "Connect" vs "Change" depending on whether a folder
  is already set; screenshotted the full onboarding chooser to confirm the
  layout. Not device-verified for native (nothing here touches native code,
  but the onboarding trigger itself — `App._promptFolderIfNeeded()` — is
  exercised identically on both platforms since it's pure JS).

## Local device row: "Disconnect" replaced with "Change" (2026-08-20)

Owner feedback the same day: the local device row's only connected-state
action was "Disconnect" — unlike the Drive row's "Change," which lets you
pick a different folder without severing anything first. Asked whether to
replace Disconnect with Change entirely or keep both; owner chose replace.

- `FolderChooser._render()`'s `localRight` (connected state) is now
  **Refresh + Change** instead of Refresh + Disconnect. "Change" reuses
  `LocalBooks.connectFolder()` directly — it already handles picking a NEW
  folder cleanly when one is already connected (compares the new folder's
  identity against the old one before deciding whether to reset the
  "already imported" map, per the original connect-a-folder design), so no
  new logic was needed. Refresh is kept — it's genuinely necessary (Drive's
  row has no equivalent because `Library.refresh()` re-fetches Drive live on
  every call, but it does NOT re-scan a connected local folder for new
  files; `LocalBooks.refreshFolder()` is the only thing that does).
- **`FolderChooser.confirmDisconnect()` and `LocalBooks.disconnectFolder()`
  deleted outright**, not left as dead code — the button was their only
  caller, and grepping confirmed nothing else in the app (including
  `MyData.deleteAll()`, which clears the same localStorage keys directly
  via its own generic `pl_*` sweep plus an explicit `LocalFolderHandle.clear()`
  call) ever called either. The `disconnect`/`disconnect_folder_confirm`/
  `local_folder_disconnected` i18n string VALUES were deliberately left in
  place — matches this file's own established precedent (see the
  `drive_folder`/`local_books*` keys note elsewhere) that unused translation
  strings are cheap to leave and risk-free, unlike actual dead executable
  code, which was removed.
- **Net effect: there is no longer a way to fully stop using a connected
  local folder from this screen** — only to replace it with a different one.
  Flagged explicitly to the owner before building (a real capability
  tradeoff, not just a label change) and confirmed as the intended design,
  not an oversight.
- Verified live in a browser: not-connected state still shows a single
  "Connect" button; connected state shows exactly "Refresh" + "Change" (no
  "Disconnect" anywhere); `LocalBooks.disconnectFolder` and
  `FolderChooser.confirmDisconnect` are both `undefined` after the change;
  screenshotted the connected-state row to confirm the visual match with
  Drive's single-action pattern; no new console errors.

## Feature tour: spotlight coachmarks (2026-08-20)

Owner asked about a "slide-show onboarding" mentioned earlier, then
clarified the real ask: not just explanatory slides, but slides that
**highlight where to click** to actually use each feature — a coachmark/
spotlight tour over the real, live UI, not a mockup screen. No such
pattern existed anywhere in the codebase before this (confirmed by a full
grep pass; the closest precedent, `#exit-hint`, is a plain dimmed backdrop
with centered text, not element-relative).

- **One reusable `Tour` module** (near `FolderChooser`/`LangPacksModal`)
  drives one static overlay (`#tour-overlay`/`#tour-hole`/`#tour-tip`),
  placed as a flat `<body>` child alongside `#toast`/`#scrub-pop`/
  `#exit-hint` — deliberately **not** nested inside `#reader-view`/`#viewer`,
  which get `transform`-based page-turn animations that would break a
  `position:fixed` spotlight if it were a descendant (a new stacking/
  containing-block context would clip the "hole" to the transformed
  ancestor's box instead of the viewport). `#tour-hole` is the spotlight
  ring: sized/positioned to the target's live `getBoundingClientRect()`,
  with `box-shadow: 0 0 0 9999px rgba(0,0,0,0.7)` — the standard CSS trick
  for a dimmed backdrop with a see-through hole, no SVG/canvas needed.
  `z-index: 500`, confirmed free in the existing stack (tab-bar 150 <
  modal-backdrop 200 < confirm-backdrop 201 < scrub-pop 300 < exit-hint
  400 < **tour 500** < toast 999). **The overlay blocks all clicks except
  its own Skip/Next** — every spotlighted target (tab nav, reader
  playback/pickers) has a real side effect that shouldn't fire
  mid-explanation, so nothing is click-through.
- **The "seen" flag is set the moment a tour STARTS, not when it finishes**
  — same convention `VoicePacks.maybeOnboard()` already uses for
  `pl_packs_onboarded` (set before its modal even opens). This matters
  concretely for the reader tour: `_onRelocated` can re-fire the trigger
  while a first tour is still showing (every page relocation re-checks
  it), and setting the flag eagerly is what makes a second concurrent call
  a clean no-op instead of restarting the tour mid-way.
- **Two independent tours, triggered completely differently, because their
  targets exist at different moments:**
  1. **Home tour** (3 steps: Library/Stats/Settings tab buttons).
     Deliberately does **not** call `Nav.go()` to actually navigate into
     those tabs — that does a real `history.pushState` plus a full content
     re-render tied into the app's real back-stack handling, so silently
     hopping tabs mid-tour risked corrupting it. Just spotlights the
     button in place and captions what it's for.
     **Trigger (`Tour.maybeStartHomeTour()`), called once from
     `App._enterApp()` with a 500ms initial delay**, polls rather than
     hooking a deterministic "setup finished" callback — there isn't one:
     confirmed directly that `LangPacksModal` has no Done button at all
     (backdrop-tap only) and `VoicePacks.maybeOnboard()`'s three call
     sites never await it, so a brand-new user's folder+voice setup can
     finish at any unpredictable time. Retries every 800ms until (a) not
     already seen, (b) no `.modal-backdrop.open`/`.confirm-backdrop.open`
     anywhere, and (c) `Nav.current === 'home'` (so it can't fire while the
     user has already tapped into another tab during the backoff window).
     This one mechanism uniformly covers a brand-new user (waits out
     however long setup takes) and a returning user with nothing to set up
     (fires almost immediately, since no modal ever opens).
  2. **Reader tour** (4 steps: `#play-btn`, `#voice-btn`, `#hl-btn`,
     `#chapters-btn` — the last needed a new `id="chapters-btn"` added,
     confirmed safe: that button previously shared only class `.icon-btn`
     with the reader's back button, and nothing in the codebase selects on
     `.icon-btn` alone). **Trigger** hooked into `_onRelocated`'s existing
     ~400ms auto-start timer area, gated on `!localStorage.pl_reader_tour_seen`
     AND `!#reader-view.classList.contains('minimized')` — the only
     reliable "is this genuinely the visible full reader, not the hidden
     mini-player" signal, since `mode` isn't stored anywhere after
     `Reader.open()` returns. Calls `Reader.showChrome()` (not
     `revealChrome()`, which re-arms a 5s auto-hide that could hide
     controls mid-tour) before showing, `Reader.hideChromeSoon()` when the
     tour ends to resume normal fade-while-reading behavior.
  - **A real race, found during planning, not by testing**: `Reader.open()`
    sets `_autoStartBook` unconditionally for every open, and the SAME
    ~400ms timer that would trigger the reader tour also starts real audio
    playback via `TTS.start()` — so without handling this, a first-ever
    book would already be reading aloud (and `#play-btn` already showing
    `.playing`) while the tour's first step says "tap here to start
    listening." **Decided with the owner: suppress the very first
    auto-start.** `_onRelocated` now nulls `_autoStartBook` whenever
    `Tour.maybeStartReaderTour()` returns `true` (meaning it actually
    started the tour), so playback waits until the user acts instead of
    starting underneath the tour. This only affects the very first-ever
    full-mode book open on a device (one-time, matches the flag) — every
    subsequent open, and every mini-player open (where
    `maybeStartReaderTour()` returns `false` due to the `minimized` check),
    is completely unaffected.
- **Scope, decided with the owner**: reader tour is 4 steps (play, voice,
  follow-along highlighting, chapters) rather than a shorter 2-step
  (play+voice) cut — covers every reader control at the cost of one more
  tap through on first use.
- No `esc()` needed for tour captions — confirmed the existing convention
  never escapes static app copy pulled from `STRINGS` via `I18n.t()`, only
  externally-sourced strings (filenames, error messages, chapter/voice
  names).
- Verified live in a browser: the poll correctly defers while a modal is
  open and fires within one 800ms retry once it closes; all 3 home-tour
  steps target the exact right tab buttons in order with correct captions
  and a live step counter; Skip works from mid-tour and sets the same
  "seen" flag as finishing; neither tour re-fires once its flag is set;
  the reader tour's 4 steps target the exact right elements
  (`play-btn`/`voice-btn`/`hl-btn`/`chapters-btn`) in order;
  `Reader.showChrome()` is called and chrome is confirmed visible before
  the first step renders; `Reader.hideChromeSoon()` fires on completion;
  the `minimized` gate correctly blocks the reader tour for the mini-player
  case; the `_autoStartBook` suppression correctly nulls it exactly when
  the reader tour starts; French captions render correctly
  (`I18n.setLang('fr')`); screenshotted the rendered spotlight ring +
  caption card at a 375px viewport to confirm legible rendering, correct
  above/below tip placement relative to the target, and no overflow. **Not
  device-verified** — same caveat as every UI-only change in this file,
  though nothing here touches native code.

## Redesign test page — `index.green.html` (2026-08-25, Phase 1 only)

A separate, standalone file, **not linked from anywhere in the live app and
not precached by `sw.js`** — reachable only if someone navigates to
`phonoleaf.com/index.green.html` directly. `index.html` (what every current
user actually gets) is completely untouched by this. Exists to let the owner
test a deeper redesign, sourced from a Claude Design project ("PhonoLeaf
mobile app design", the "Shelf"/"Green Ink" direction), on a real domain —
Google sign-in only works from authorized origins (`phonoleaf.com`,
`kbailey90.github.io`), not `localhost`, so a plain local preview can't
exercise real sign-in/Drive/erase-my-data end to end.

**Scope shipped is explicitly Phase 1 of 3** (IA rename + visual system +
copy — see `PhonoLeaf Redesign.dc.html` in that Claude Design project for
the full spec this was scoped down from):
- **Tab bar renamed** Home→Now, Library→Shelves, Stats→Log, Settings→You
  (`tab_now`/`tab_shelves`/`tab_log`/`tab_you` in `STRINGS`, EN+FR). Only the
  *displayed* label changed — internal identifiers (`data-tab="home"`,
  `Nav.go('home')`, view ids) were deliberately left alone since renaming
  them has zero user-visible effect and only adds regression surface.
- **Visual system** (sharp `2px` corners, hairline-separated rows instead of
  cards, hard non-blurred shadows, underline-style chip pickers for
  speed/quality) was already substantially in place from an earlier,
  narrower design import this same file started from; this pass mainly
  cleaned up the handful of remaining soft corners/blurred shadows that
  earlier pass missed (reader page-count pill, a few inline-style leftovers
  in JS-generated markup, the folder-name modal).
- **Sign-in screen** gained the design's confirmed feature-row treatment
  (hairline rows, monospace right-hand tags: "Read-only, always / nothing is
  uploaded", "Voices run on the phone / works offline", "No account of ours
  / no server", `si_feat1..3_l/_r` keys) in both languages. The existing
  mandatory Google sign-in button and 3-step onboarding were kept as-is —
  the design mockup's dual "choose a local folder / connect Drive" CTA
  implies sign-in is optional, which isn't true here (entitlement is being
  built around the Google account id, see the payments roadmap item), so
  that part of the mockup wasn't imported.
- **New dedicated `EraseModal`**, replacing the plain `ConfirmModal` for
  `MyData.deleteAll()` specifically (every other destructive confirm in the
  app — stats reset, remove a book, etc. — is untouched, still on
  `ConfirmModal`): itemized hairline rows with live-computed counts
  ("Progress in {n} books", "{n}h of listening history", both from
  `State.progress`/`Stats.data` — no new data collection, just reading what
  already exists), static rows for settings/offline-books (deleted) vs.
  Google Drive files (untouched), and a text input that only enables the
  destructive button once the user types `ERASE` (case-insensitive). New
  `erase_*` keys in `STRINGS`, EN+FR.
- **Real, unrelated bug found and fixed while testing this on a real
  device**: every `<button>`/`<input>`/`<select>`/`<textarea>` in the WHOLE
  app (not just this test page — this predates the redesign entirely) had
  `appearance: auto`, meaning it relied on the browser's native OS chrome
  rather than the CSS `border-radius` actually written. Chromium happens to
  render it close enough to the authored style that this was never
  noticed, but Safari/iOS and Android's WebView are well known for imposing
  a native rounded-pill shape on buttons regardless of `border-radius`
  unless `appearance` is explicitly reset — invisible on the OLD rounded
  design (native rounding blended right in) and only became obvious once
  sharp corners were a deliberate, visible commitment. Fixed with one global
  rule right after the existing `*` box-sizing reset:
  `button, input, select, textarea { -webkit-appearance: none; appearance:
  none; }`. **This fix is currently only in `index.green.html`** — if the
  redesign is ever merged into the real `index.html`, or if this turns out
  to visibly affect the LIVE app too, port this one rule over separately;
  it's a real, general CSS gap, not specific to the redesign.

**Explicitly NOT in this pass** (Phase 2/3, not started): the "On this
phone" storage-manager screen, in-book full-text search, the formal
motion/gesture system (the file specifies exact durations/easings — not
wired up as a system yet), and the ~40-label accessibility pass beyond
what the app already has.

Verified: syntax-checked
(`node -e "...compileFunction..."`), manually reviewed the erase-flow JS
for correctness, and browser-tested at a 375px viewport — tab bar labels in
both languages, the sign-in feature rows, and the erase dialog's dynamic
counts + type-to-confirm gate (disabled until exact match, case-insensitive,
enables/disables live) all confirmed working, zero console errors. **Not
device-verified beyond the corner-radius bug the owner caught by eye** — if
anything else looks visually off on a real phone, check for another
`appearance: auto`-style native-chrome override before assuming the
authored CSS is wrong.

**Correction the same day: pushing this to GitHub Pages does NOT reach the
native app.** Owner tested on the native Android app expecting to see it
there and saw nothing — the native shell is a WebView pointed at a LOCALLY
BUNDLED `www/index.html`, baked into the APK at build time
(`scripts/stage-www.js`); it never fetches anything from phonoleaf.com over
the network, so nothing pushed to the website can ever reach an
already-installed native build. Added **`scripts/stage-test.js`** +
`npm run stage:test` / `npm run sync:test` (mirrors `stage-www.js`/`sync`
exactly, substituting `index.green.html` → `www/index.html` in place of the
real `index.html`) so the redesign can be staged into a native TEST build
without touching the real `index.html` at all. To actually see it on
device: `npm run sync:test` (stages + copies into the `android/` project),
then the normal `npm run open` → Run ▶ loop from the "How to deploy"
section below. **Not run through Android Studio here** — no JDK/Android
SDK in this environment, same as every other native change in this file;
only the file-copy half (`stage:test`) was verified directly (`www/index.html`
confirmed byte-identical to `index.green.html` afterward). To go back to
the real app, run the normal `npm run sync` again — it re-stages the real
`index.html` and overwrites the test copy.

## Home/Shelves/Player rebuild + sleep timer + ±15s skip (2026-08-25)

The Phase-1 pass above (tab rename, corner cleanup, sign-in copy, erase
modal) was pushed to the website and the owner tried to verify it — but
they test on the **native app** (`npm run sync:test` + Android Studio), not
a browser, and the website push never reaches an already-installed native
build (the native shell bundles `www/index.html` at build time, no network
fetch — see the `stage-test.js` entry above, which exists for exactly this
reason). Once actually looked at against the real mockups
(`PhonoLeaf Redesign.dc.html`, downloaded fresh by the owner and placed in
the repo — confirmed byte-identical to an earlier working copy), it was
clear Phase 1 was cosmetic-only and didn't match the mockups' actual screen
compositions. The owner chose the full rebuild scope, including two new
features (sleep timer, ±15s skip) neither of which existed in the app at
all before this.

**Scoping corrections from the owner, both binding:**
- **Shelves shows real book covers, not a "Spines" alternate view.** The
  mockup's colored-block "spine" art was placeholder art for books with no
  real cover — PhonoLeaf already extracts real covers, so a Spines toggle
  was dropped entirely rather than built as fake-color spines.
- **Only the most recent light/dark mockup pair per screen counts.**
  Verified directly by grepping the whole mockup file for the screens'
  distinguishing text: Home's only version is section "3a" (line 1157,
  light 1164–1219 / dark 1221–1288) — nothing later redid it. Shelves +
  Player's most recent versions are section "4a"/"4b" (lines 731/939) —
  later sections (5/6/7) only cover onboarding/confirmations/downloads/
  search/zoom/motion, never the core Shelves/Player layout again.
- **The Player replaces the reader's existing top/bottom chrome in place**,
  not a separate "now playing" screen. The mockup's Player has no visible
  reading surface at all (audio-first, full-bleed cover header) — since the
  real reader MUST keep book text as the dominant element, this was
  translated by scale rather than copied at the mockup's literal 352px
  hero height (which would have crowded out the actual page text).

**Home ("Now") rebuild** (`Home.render()`): replaced the old greeting +
3-tile stat row + plain hero with: a header row (`PhonoLeaf` wordmark +
`Stats.summary().streak`, already computed, no new data), a hero card
(real cover via `Covers.urls[id]`, "Still reading"/"Now playing", title,
chapter, a new `p. {x} of {y} · {time left}` line, progress bar — the whole
hero is now a tap target for `Player.expand()`, with `event.stopPropagation()`
on the embedded scrub input so dragging it doesn't also trigger expand), a
playback row (play/pause + the sentence currently being spoken, read
straight from `TTS.chunks[TTS.idx]?.text`, no new fetch + a sleep-timer
readout), the existing "Jump back in" row relabeled "Back on the shelf"
(unchanged logic), and a new "Next up in {book}" numbered chapter list. The
3-tile stat row (hours this week/streak/books in progress) was DROPPED from
Home entirely — the mockup doesn't have it, streak moved to the header, and
the rest is one tap away on the Log tab.
- New shared helpers (placed right after `chapterLabelFor`, since they're
  the same class of book-agnostic utility): `estimateTimeLeft(book, cfi)`
  and `pageOfTotal(book, cfi)`, both reading `book.locations` (an epub.js
  live index, only populated for the CURRENTLY OPEN book — everything
  correctly falls back to a bare `{pct}%` otherwise, same convention
  `Scrub._info()` already used). Time-left is a heuristic (remaining
  locations × ~1024 chars/location ÷ ~900 chars/min, scaled by `TTS.rate`)
  — always prefixed `~`, never claims false precision.
- "Next up" (`Home._nextUpHTML`) reuses `flattenToc(State.toc)` + the same
  href-basename matching `chapterLabelFor()` already does to find the
  current chapter, then slices the next 3 — zero new fetches. Deliberately
  shows **no per-chapter duration** — nothing in the app cheaply estimates
  a single chapter's length without loading its section's DOM, and a
  fabricated number was judged worse than none. Tapping a row
  (`Home.jumpChapter`) reuses `ChapterModal._resolveHref()` directly (it
  doesn't depend on `ChapterModal._flat`, so no need to open that modal
  first) and expands the reader if it's currently minimized.

**Shelves (Library) rebuild** (`Library.render()`): the screen's own
`<h2>` text was changed from "Library" to "Shelves" (the TAB was already
renamed in Phase 1; the screen heading itself was missed) plus a
`"{n} books"` count. Grid mode now groups every book by
`Meta.get(id).genre` (`Library._groupByGenre`, a new function — NOT a
reuse of `StatsPage._breakdown`'s `g==='genre'` branch, which filters to
books with listening activity; Shelves must show every book, read or not).
'Other' (no recognised genre yet) always sorts last, matching the existing
Stats convention. No user-facing toggle for grouping — the mockup shows it
as the screen's permanent state, not optional, so it wasn't built as one.
Table view stays an ungrouped flat list (grouping headers don't compose
with a `display:flex` list the same way they do with `display:grid`'s
`grid-column: 1/-1`), and the A-Z scrubber (keyed to one continuous
alphabetical run) is hidden whenever grouping splits the list into
sections, same as it already hides during an active search.
- **Real gap fixed while here**: `Meta.fetchAll()`'s completion only
  re-rendered `StatsPage` when background genre data finished loading —
  Shelves groups by that same data now, so it needed the same treatment
  (`if (Nav.current === 'library' && State.books) Library.render();`)
  or a freshly-opened library would show almost everything under "Other"
  until an unrelated re-render happened to fire.

**Reader chrome rebuild** (`#reader-view`'s `.reader-top`/`.reader-bottom`):
top bar restyled to a translucent circular back button + a text "CHAPTERS"
label (replacing the hamburger glyph) — `#viewer` (the actual epub
iframe/page content) and the gesture/edge-arrow layer are completely
untouched, only the surrounding chrome changed. The bottom control pill's
prev/next PAGE-turn buttons were replaced with a new primary row:
−15s / prev-chapter / play-pause / next-chapter / +15s. Prev/next-chapter
(`Reader.prevChapter`/`nextChapter`) are thin wrappers over
**`TTS._jumpChapter(dir)`, an already-existing function** (built earlier
for the Android lock-screen media buttons) — reused as-is rather than
reimplemented, so both surfaces behave identically. Page-turning itself
stays fully available via the existing swipe gesture and `.reader-edge`
tap arrows, neither of which this touched. Speed/follow-along/voice moved
into a secondary row below the primary one (still real, used controls —
de-emphasized, not dropped), and a new sleep-timer icon button
(`#sleep-btn`) was added there, opening the sleep sheet.
- Explicitly simplified vs. the mockup: no cover-derived color tint on the
  top bar (would have needed the same canvas dominant-color sampling the
  owner declined for Shelves' Spines view — not worth reintroducing for
  one cosmetic detail). Uses existing surface/line tokens instead.

**±15s skip (`TTS.skip(deltaSec)`)**: the shared `<audio>` element
(`_audioEl()`) is reused per chunk, not a continuous stream — seeking only
works within the currently-loaded chunk's audio. Fast path: if the target
time stays inside `[0, a.duration]`, sets `a.currentTime` directly (same
class of live manipulation `setRate()` already does), no gen bump needed.
`-15s` when more than ~2s into the current chunk just restarts it
(`currentTime = 0`) rather than trying to reconstruct a previous chunk's
already-revoked blob URL. Crossing a chunk boundary (either direction)
estimates how many chunks the requested seconds span at ~14 chars/sec
(≈150 wpm) scaled by `TTS.rate`, walking `this.chunks` from the current
index and clamping at the page's first/last chunk — **never crosses a
PAGE boundary**; running off either end just lands on that edge chunk
(hitting the existing normal end-of-page auto-advance once it finishes
playing, same as any other chunk). A boundary-crossing skip bumps `_gen`,
clears `_gapT`, calls `_stopAudio()` (revokes any prefetched blob first —
skipping this would leak one per skip), sets the new `idx`, and re-enters
via `_speak()` — the exact same shape `skipPage()` already uses for a real
page turn, just without turning a page. Re-synthesis reuses the existing
"Generating audio…" toast path (600ms-gated, so a fast re-synth never
flashes it) — no new loading UI. The Web Speech fallback has no seekable
`<audio>` at all, so it always takes the boundary-crossing path (restart
current utterance for -15s, advance ~1 chunk for +15s) — verified via
`_engineNow() === 'web'` early-return.
- Verified directly in a browser (mocked `TTS._audio` as a plain object
  with the methods `_stopAudio()` actually calls — `pause`/
  `removeAttribute` — since a bare object throws otherwise): -15s at
  `currentTime=3` correctly resets to 0 without bumping `_gen`; +15s past
  a 10s-duration mock chunk correctly bumps `_gen` and advances `idx`,
  clamping at the last available chunk rather than throwing when the
  estimated skip distance exceeds the remaining chunks.

**Sleep timer (`SleepTimer` + `SleepModal`)**: fully new, confirmed nothing
resembling it (volume/fade/`AudioContext`/"sleep") existed anywhere in the
file beforehand.
- `SleepTimer.set(minutes)`/`setChapterMode()`/`cancel()`, a 1s
  `setInterval` (same idiom as `Stats.startTick()/stopTick()`). In the
  final 60s of time mode, ramps `TTS._audio.volume` down linearly. The
  actual STOP is never done by the timer itself — it only sets
  `TTS._sleepExpired = true`, which both existing chunk-boundary handlers
  (`_playAudio`'s `a.onended`, `_speakWeb`'s `u.onend`) now check, right
  before deciding whether to advance to the next chunk; if expired, they
  call `SleepTimer.cancel()` + `TTS.stop()` instead. This guarantees the
  stop always lands exactly at a sentence boundary and needed no new
  gen-guard logic — it rides the exact choke point every other
  "should the next chunk start" decision in `TTS` already goes through.
  Chapter mode's expiry check compares `State.rendition.currentLocation()`'s
  href against the href recorded when the timer was armed.
- **Design correction made during implementation, not left as originally
  planned**: the plan draft said `TTS.stop()` (i.e. every manual pause)
  should cancel the timer, while also saying pressing play again mid-
  countdown should "resume" it — directly contradictory, since a canceled
  timer has nothing left to resume. Resolved by NOT touching `TTS.stop()`
  at all: the countdown ticks in real wall-clock time regardless of
  play/pause state (matching how sleep timers work in other audio apps —
  the point is stopping playback by a real-world time, not "N minutes of
  active listening"). The timer is only ever canceled by its own expiry,
  by opening a genuinely different book (`Reader.open()`, guarded on
  `State.currentBook !== book` so re-opening the SAME book — e.g. tapping
  the Home hero to expand — does not reset an in-progress countdown), or
  by the sheet's explicit "Turn off sleep timer" button.
- **Drag dial** (`SleepModal`): no existing circular-drag precedent in this
  codebase (`Scrub` is a native `<input type="range">`, not custom pointer
  math) — new `pointerdown`/`pointermove`/`pointerup` geometry, angle from
  the dial's center via `Math.atan2`, normalized from 12 o'clock, mapped to
  minutes against a 90-minute max, rendered live as a `conic-gradient`-style
  SVG stroke-dashoffset ring during the drag, committed via `SleepTimer.set`
  on release. Listeners are **delegated on `document`**, matching `Scrub`'s
  own convention — binding directly to `#sleep-dial` inside a
  `DOMContentLoaded` handler was tried first and would never have fired,
  since this is an inline script running after the markup is already
  parsed (DOMContentLoaded had already happened by the time such a listener
  would register); caught before shipping, not after.
- Verified directly in a browser: `set(1)` → `mode:'time'`, 60s remaining;
  65 simulated ticks → `_sleepExpired` true, volume faded to ~0; `cancel()`
  → mode null, volume back to 1, flag cleared; the sheet opens/closes
  correctly; picking a chip updates the dial label and re-opening the sheet
  shows the persisted value; French labels correct (`I18n.setLang('fr')`)
  for the sheet title, the "end of ch." chip, and the cancel button.

**Overall verification**: syntax-checked
(`node -e "...compileFunction..."`) after every section; full browser pass
in both Daylight and Midnight — Home with synthetic progress/stats data
(streak, hero, playback row, back-on-the-shelf, next-up all render
correctly, falls back to `{pct}%` when no book is actually open matching
the "only the open book has live locations" design), Shelves' genre
grouping (`Science fiction`/`Other`, correct counts, `Other` last), the
reader's new control row and sleep sheet, `TTS.skip()`'s both paths, and
the full `SleepTimer` lifecycle including cross-language labels. Zero
console errors on a clean tab load (the one error present —
`accounts.google.com/gsi/client` failing to fetch — is the same
pre-existing, expected sandbox-network-block condition noted everywhere
else in this file, unrelated to this change). **Not device-verified** —
same standing caveat as every UI change in this file; native testing is
available via `npm run sync:test` (see the entry above) whenever wanted.
**Still only in `index.green.html`, not committed to `index.html`, not
pushed** — this was a large enough change that it's worth the owner's
visual review before it goes anywhere near the live app or website.

## Round 3: layout/data fixes, Home/Log rebuild, sleep timer, export flow, French audit, Privacy/Terms (2026-08-26)

A 13-item feedback batch on the round-2 changes above, after two Explore-
agent research passes and two rounds of plan review (the owner rejected the
first plan draft three times over specific mis-scoped items — see below).
All work stayed in `index.green.html` (plus four new `.green.html` Privacy/
Terms test copies); `index.html`/`privacy.html`/`terms.html` untouched.

**Corrections the plan needed before approval** (each caught a real
under-scoping, not just a wording nit):
- **Shelves "Other" genre**: the first pass concluded "not a bug" because
  `_groupByGenre` already puts genre-less books under an "Other" header
  correctly. The owner corrected: *"The problem here is that you can't
  select Other as a group, but you can select Biography."* The real bug was
  in `Library._renderGenreChips()`, which built the CHIP row from
  `if (g) genres.add(g)` — skipping the falsy/"Other" bucket entirely, so
  there was no chip to filter BY even though the group rendered correctly.
  Fixed by tracking whether any book actually lacks a genre while building
  the chip list and adding an `I18n.t('genre_other')` chip (sorted last,
  matching group order), with the filter predicate using the exact same
  `Meta.get(id)?.genre || ''` fallback `_groupByGenre` already uses.
- **French audit**: the first pass diffed `STRINGS.en` vs `STRINGS.fr`
  key-by-key and found 4 issues. The owner: *"There are more errors than
  that! For example, Voice should be Voix, Biography should be Biographie.
  ... Please make a real assessment."* A key-diff can't catch (a) hardcoded
  English text with no `data-i18n` attribute at all, or (b) a data-driven
  label with no i18n layer at all — exactly the two categories the owner's
  examples fell into. Redone by reading the entire `STRINGS.en`/`STRINGS.fr`
  dictionaries directly end-to-end (not sampled) AND grepping the markup for
  hardcoded `<tag>EnglishText</tag>` patterns lacking `data-i18n`. That
  surfaced the real bugs: `#voice-btn` (confirmed via `TTS.updateVoiceLabel()`'s
  own comment that it deliberately never touches that button's text — it
  had simply never been wired to translate at all) and genre names (no
  translation layer existed for them, full stop). Verified several other
  suspects as false positives by tracing their JS call sites (`vh-action`,
  `fb-use`, `tour-next-btn`, `ConfirmModal`'s default "Reset" — all
  overwritten by `I18n.t()` before display).
- **Privacy/Terms**: the first pass planned "swap CSS tokens onto the
  existing card/table layout." The owner: *"This should follow the design
  file's design for both light and dark versions."* Re-specified to match
  the prototype's actual screen structure (see below) rather than a token
  reskin, and re-confirmed both themes explicitly rather than assuming the
  token swap alone would be correct (the accent-inverted summary box is the
  one place where light vs. dark isn't just a token substitution — the box
  is *always* accent-background/bg-colored-text, so which literal hex shows
  up flips between the two themes, and that needed to be checked, not
  assumed).

**1. Settings row spacing.** Root cause confirmed by direct measurement, not
guessed: `.set-row` padding was already symmetric (0.9rem/0.9rem); the
asymmetry was `.set-group`'s `margin-bottom: 1.4rem` with no closing
hairline, making a group-boundary gap (2.3rem) wider than an in-group gap
(1.8rem). Fixed by giving `.set-group` a `border-bottom` and reducing its
`margin-bottom` to 0.9rem, so a boundary measures exactly one row-gap.
Verified in-browser by measuring `getBoundingClientRect()` gaps between
every visible `.set-row` pair on the real rendered Settings page: every gap
came back identically `15.1px` (excluding the native-only hidden language-
packs row, which has to be filtered by `offsetParent !== null` or its
zeroed rect corrupts the measurement — not a real bug, just a measurement
artifact from including a `display:none` row).

**2. Log (Stats) tile grid.** Rebuilt from two uneven 3-tile `.stat-row`s
(all-time hours / this-week / streak, then library / started / finished) to
the prototype's exact six in a 2-column × 3-row grid: Library, Started,
Completed, Minutes this week, Total minutes, Streak. `Stats.summary()`
gained a `secs` field (raw 7-day rolling seconds) alongside its existing
formatted `hours` string, so both the Log page and Home's new stat row (see
§6) can derive "minutes this week" from the same number without
re-implementing the rolling-window math twice. New page-scoped
`.stat-grid2` CSS class (bordered 2-col grid) — deliberately not reusing
the shared `.stat-row` (a single flex row elsewhere) so nothing else in the
app is affected. Verified with mocked `Stats.data`: rendered tiles read
exactly `5 / in library, 5 / started, 1 / finished, 40 / minutes this week,
40 / total minutes, 2 / day streak` against synthetic data where that's the
correct arithmetic.

**3. Shelves "Other" filter + genre localization.** See the correction
above for the root cause. Localization added as `Meta.GENRE_LABEL_KEYS`
(English `_GENRE_MAP` label → new `STRINGS` key, one pair per of the 12
genres, e.g. `genre_scifi`/`genre_biography`) and `Meta.genreLabel(g)`,
used by both `_groupByGenre()`'s header text and `_renderGenreChips()`'s
chip labels for DISPLAY only — the stored/internal `Meta.data[id].genre`
stays the stable English string, so no data migration and no risk to
already-cached metadata. Verified: with `Library.toggleGenre('')` (the
Other sentinel) selected, the grid filtered down to exactly the two
genre-less mocked books; switching to French re-rendered the chip row as
`Biographie / Fantastique / Science-fiction / Autre`.

**4. Offline icon.** `_offlineBtnHTML()`/`_refreshOfflineBadge()` no longer
swap between `_ICON_DOWNLOAD` and `_ICON_SAVED` — always render
`_ICON_DOWNLOAD`, let the pre-existing `.saved` CSS class do 100% of the
saved/not-saved distinction via color alone. `_ICON_SAVED` left defined
(confirmed via grep nothing else references it, but deleting a constant
that might be referenced elsewhere isn't worth the risk for this small a
change).

**5. Finished badge + easier mark-complete.** A `.finished-badge` (filled
`--accent` circle, checkmark, top-left corner — mirrors the offline badge's
top-right corner convention) now renders on both `Library._itemHTML` grid
cards and Home's `_coverRowItemHTML` whenever `pct >= 100`.
`BookDetail.markFinished(i)` was refactored to take an optional index
(`State.books?.[i ?? this._idx]`) so it can be called directly from a cover
overlay's new "Mark as finished" button, not just from inside the open
modal — the overlay's `.cr-finish` button only renders when the book isn't
already finished. Verified: mocked book `b2` at `pct:100` showed the ✓
badge in both Continue Listening and Shelves grid contexts; the other
(non-finished) mocked books showed a working "Mark as finished" link in
their reveal overlay instead.

**6. Home ("Now") rebuild.** Replaced the single hero card + "Next up in
{book}" + "Back on the shelf" structure entirely with the prototype's own
two-plural-list shape: greeting → Continue listening (top 3 in-progress,
unchanged from round 2) → a new 2-tile streak/minutes-this-week row
(reusing `.stat-grid2` from §2, as a 1-row instance via `.home-stat2`) → a
new "Still reading" vertical list (title + thin progress bar, no cover;
4th-onward in-progress books beyond Continue Listening's top 3, capped at
10). `Home._nextUpHTML`/`Home.jumpChapter` were dead code once nothing
called them and were deleted outright, not left behind, along with their
now-orphaned `.home-hero`/`.hh-*`/`.nextup-*` CSS and the `now_playing`/
`back_on_shelf`/`next_up_in`/`home_started_empty`/`streak_days` `STRINGS`
keys (both languages) — same "delete outright" convention as the earlier
long-press removal in this file. Verified with 5 mocked books at varying
progress/timestamps: Continue Listening showed the 3 most-recently-touched
in-progress books (one with a finished badge), the stat row read "2 day
streak" / "40 minutes this week" against synthetic 7-day listening data,
and Still Reading showed exactly the remaining 2 in-progress books — no
hero, no "Next up", no "Back on the shelf" anywhere on the page.

**7. Sleep timer.** Removed the 15/30/60 `<button>`s from the sheet
entirely, leaving only "End of ch." — this was the actual fix for the
owner's reported bug ("press 15/30/60, popup closes, timer isn't set"): the
timer WAS being set correctly (`SleepTimer.set()` runs synchronously before
the sheet closes), but nothing ever visually confirmed it, since
`.sleep-chip.on` was fully styled in CSS but never toggled by any JS on
the sleep sheet's own chips (a real, separate, previously-unreported bug
found while investigating this). Now `SleepTimer.syncReadouts()` — already
running every tick and on sheet open — toggles `.on` on the surviving
`#sleep-chapter-chip` based on `mode === 'chapter'`. The dial itself was
already a live countdown (`_tick()`/`syncReadouts()` already updated the
label and stroke-dashoffset every second); no new countdown mechanism was
needed, just removing the chips that were competing with it as "the"
control. Added `#sleep-dial-label.chapter-mode` (smaller font, tighter
max-width, allows 2-line wrap) so "end of ch." fits the 140px dial cleanly
instead of overflowing the sizing tuned for a 2-digit number. Verified:
opening the sheet shows exactly one chip; `SleepModal.pickChapter()` sets
`mode:'chapter'`, toggles the chip's `.on` class true, and applies
`chapter-mode` to the dial label showing "end of ch.".

**8. Export my data.** New `#export-modal` (`ExportModal.open/close/confirm`,
reusing the `.modal-sheet` pattern) explains what the export actually
contains before triggering it — adapted from `MyData.export()`'s own
existing `payload.note` text. Settings' "Export" button and `EraseModal`'s
"Export a copy first" link both now open this modal instead of calling
`MyData.export()` directly; the download logic itself is unchanged.
**On the Google Drive question the owner asked about**: recommended
against adding Drive-write API access (`drive.file` at minimum) — any new
scope forces Google re-verification and would either trigger an early,
extra CASA pass or leave the feature gated until the already-planned one
happens (`CLAUDE.md`'s documented CASA-parked-until-payments-exist
strategy). Android's own Storage Access Framework can already save into
Drive via the OS's own document-provider system, with zero new OAuth scope
— noted as a legitimate future enhancement rather than built now, since a
native SAF plugin is real, separate effort. Verified: opening the modal
from both Settings and the Erase sheet's "Export a copy first" link works;
confirming still produces the same JSON download as before.

**9. Persistent header.** One shared `.app-header` markup block (the same
inline leaf-icon SVG already used at sign-in, plus a `data-i18n="app_name_caps"`
wordmark — translating "PhonoLeaf" is a deliberate no-op key, kept only so
every visible string in the app has a `data-i18n` hook consistently) now
sits above the scrolling content on Home, Shelves, Log, and Settings —
inserted 4 times in the markup (no shared-partial mechanism exists in this
build-free single-file app, so this is copy-pasted markup, not a
templating trick). Reader/Player keep their own existing top bar
unchanged. Home's old `.home-header-row` (icon + title + streak sharing one
row) was deleted along with its CSS once the persistent header made the
icon redundant and the streak moved into the new stat row (§6). Verified:
`document.querySelectorAll('.app-header').length === 4` on the rendered
page.

**10. French corrections.** Fixed, verified via `I18n.t()`/rendered-DOM
checks after `Settings.setLang('fr')`: `tab_now` 'Écoute'→'Maintenant'
(was translating "Listening", not "Now"); `tab_shelves` and `library_title`
'Rayons'→'Étagères' (generic warehouse-shelving word, not the book-shelf
word the app already uses correctly elsewhere in `back_on_shelf`);
`si_feat1_r` 'rien n'est envoyé'→'rien n'est téléversé' (now matches
`téléverse`, used correctly one line above for the same concept); added
`data-i18n="voice"` to `#voice-btn` (reusing the existing correct
`voice`/'Voix' key — the button had no i18n hook at all before, not a
wrong translation). Genre localization is covered in §3. Confirmed
rendered French tab bar reads exactly "Maintenant / Étagères / Journal /
Vous" and the reader's voice pill reads "Voix".

**11. Privacy & Terms.** New `privacy.green.html`/`privacy-fr.green.html`/
`terms.green.html`/`terms-fr.green.html` — same safe test-copy convention
as everything else, real `privacy.html`/`terms.html`/their `-fr` twins
untouched. Structural rebuild matching the prototype's own screen, not a
token reskin of the old card/table layout: serif (Literata) title +
monospace "Effective {date}" line (kept the real current dates unchanged);
**Privacy only** gets one accent-inverted summary box (solid `--accent`
background, `--bg`-colored text, hard offset shadow, bold "The short
version" lead-in) — everything else, including what used to be additional
`.card`s (the "Does PhonoLeaf store my data" and "Limited Use commitment"
sections), is now a flat `<section>` with a small accent-colored heading
and hairline top border, no boxes; Terms has no accent box (matches the
prototype — only Privacy's page has one) and keeps its `<h3>` sub-headings
for the pricing subsections. Legal text itself is byte-identical to the
current live pages — this was a structural/visual rebuild only, not a
content rewrite, and none of it is lawyer-reviewed any more or less than
before. Verified in both themes: light mode's summary box computed to
`background: rgb(47,107,79)` / `color: rgb(239,233,218)` (light accent on
light bg-color text); dark mode (this sandbox's actual OS preference)
computed to `rgb(111,191,149)` / `rgb(15,20,17)` — correct inversion in
both directions. Zero console errors loading any of the four pages
standalone.

**12. Floral/nature lexicon.** Treated as an ongoing lens rather than a
forced pass this round, per the plan — none of the copy actually touched
above needed obviously better nature-flavored wording that wasn't already
fine as plain English/French, so nothing was changed here specifically.

**13. TODO.md.** Appended a sub-bullet under the existing 2026-08-24
"Should the app have a deliberate tone?" item, proposing a Settings
tone-selector (Normal/Sassy/Bro/Butler, exact copy TBD) as one concrete way
to answer that open question, rather than a single global decision.

**Overall verification**: syntax-checked after each section; a mocked-data
browser pass (`State.books`/`State.progress`/`Stats.data`/`Meta.data` set
directly via console, since this sandbox has no real Google sign-in)
confirmed every item above rendering and behaving correctly — Settings row
gaps, Log's 6-tile grid values, Shelves' Other filter, the finished badge
and its mark-complete button, Home's full new structure, the sleep sheet's
single chip and its `.on`/dial-label behavior, the export modal, all 4
persistent headers, the French label set, and both Privacy/Terms pages in
both color schemes. Zero console errors across every page loaded. Nothing
pushed; `index.html`/`privacy.html`/`terms.html`/their `-fr` twins
untouched — confirmed via `git status`. **Not device-verified** — same
standing caveat as every UI change in this file.

## `index.green.html` deleted from disk, recovered from `www/index.html` + Round 3 fallout fixed from real device feedback (2026-08-27)

**The file went missing.** Between sessions, `index.green.html` was deleted from
the repo root (`git status` showed it as `D`, i.e. still tracked at the Phase-1
commit but gone from the working tree) — most likely a side effect of the
owner extracting/handling a `PhonoLeaf Design System.zip` export. That zip's
own bundled `index.green.html` turned out to be an OLDER snapshot (has
round-2's `CoverReveal`/`BookDetail` but zero round-3 markers), because it was
generated by an earlier `DesignSync` push, before round 3. The real recovery
source was `www/index.html` — `scripts/stage-test.js` does a byte-for-byte
`fs.copyFileSync` (confirmed by reading the script, not assumed) of
`index.green.html` whenever `npm run sync:test` runs, and the owner had
plainly run that recently to test round 3 on-device (`www/index.html`
contained every round-3 marker: `ExportModal`, `home-stillreading`,
`stat-grid2`, `sleep-chapter-chip`, etc.). Copied `www/index.html` back to
`index.green.html` at the repo root, verified with the same
`node -e "...compileFunction..."` syntax check used everywhere else in this
file. **Lesson for next time a `.green.html`/test file goes missing**:
`www/` (gitignored, but persists on disk) is a legitimate byte-identical
recovery source for whichever file `stage-test.js`/`stage-www.js` last
staged — check it before assuming the work is lost.

**Real device feedback, once restored** — the owner had actually tested round
3 on their phone and found four categories of problems, none hypothetical:

1. **"Continue listening" stayed in English under French.** Root cause: this
   ONE spot in `Home.render()`'s dynamically-generated `innerHTML` used
   `data-i18n="continue_listening"` with an English fallback string, copying
   the STATIC-markup convention into DYNAMIC (JS-templated) content by
   mistake. `I18n.setLang()` calls `I18n.apply(document.body)` (which fills
   in `[data-i18n]` from whatever's in the DOM RIGHT NOW) and only THEN calls
   `Home.render()` — so a `data-i18n` span that doesn't exist yet when
   `apply()` runs never gets translated, permanently. Every other dynamic
   render in the codebase already knew to call `I18n.t()` directly inline
   instead (confirmed by grepping the entire script body for a literal
   `>Word<` HTML-text pattern — this was the only match). Fixed by switching
   to inline `I18n.t()` calls, matching the rest of the codebase.
   **Given "please make a real assessment" (again) this round**, went well
   beyond that one spot: re-read the ENTIRE `STRINGS.en`/`STRINGS.fr`
   dictionary side-by-side a second time (only turned up a couple of
   debatable, non-blocking phrasing choices, nothing wrong), then grepped the
   whole script body for hardcoded English patterns the same way, which
   surfaced three more real, confirmed bugs the dictionary comparison alone
   could never have caught (none are STRINGS mistranslations — they're
   strings that were never routed through `I18n.t()`/`STRINGS` AT ALL,
   pre-existing since before this session, not a regression from round 3):
   - The voice picker's "✨ Natural" badge (three separate call sites: web
     Speech-API voices, native Kokoro/Piper voices) and " · Local"/" · Online"
     on web voices — all hardcoded English literals. Added
     `voice_natural_badge`/`voice_local`/`voice_online` `STRINGS` keys, used
     everywhere the badge/suffix renders.
   - The Log page's "By genre" breakdown table showed raw genre names in
     English regardless of language (`Meta.data[id].genre` displayed
     directly, e.g. "Science fiction" never became "Science-fiction"),
     because `_breakdown('genre', …)`'s row builder never went through
     `Meta.genreLabel()` — a second call site with exactly the SAME class of
     bug `_renderGenreChips()` had before it was fixed for Shelves. Also used
     a DIFFERENT "Other" sentinel than the rest of the app (`'Other'` the
     literal string, instead of `''`), which `Meta.genreLabel()` doesn't
     recognize either way — fixed the sentinel to `''` (matching
     `_groupByGenre`/`_activeGenres`'s own convention) AND routed the row
     label through `Meta.genreLabel()`.
   - `send_feedback_btn`'s French value was bare "Envoyer" (just "Send"),
     dropping the object every other send-type button keeps (`send_report`
     → "Envoyer le signalement"). Changed to "Envoyer des commentaires" to
     match.
2. **Home ("Now") "looks nothing like the design file."** Correct on
   inspection — re-read `PhonoLeaf.dc.html`'s actual `isHome` block line by
   line (not from memory) and found several concrete structural gaps versus
   what got built:
   - The greeting was one combined string (`"Good evening, Alex"`); the
     design file is TWO stacked lines — a small tracked kicker ("GOOD
     EVENING") directly above a big serif name ("Alex") on its own. Split
     `#home-title` into `#home-kicker` + `#home-title`, `Home.render()`
     writes each separately.
   - Continue Listening's cards were missing a progress bar + percentage
     entirely (cover + title only) — the design file puts a 3px bar and a
     right-aligned `{pct}%` under every card. Added `.cr-bar`/`.cr-fill`/
     `.cr-pct` to `_coverRowItemHTML`.
   - The 2-tile stat row (and, it turns out, the Log page's 6-tile grid
     built two rounds ago) used a single continuous bordered grid with
     shared hairlines and no gap — that was this codebase's OWN invention,
     not the design file. The design file gives every tile its own
     `background:var(--surface)` + `border:1px solid var(--line)` +
     `border-radius`, with a real `gap` between tiles, and the number itself
     in `var(--accent)`. Rebuilt `.stat-grid2`/`.stat` to match exactly —
     this fixes BOTH Home's stat row and the Log page's grid at once, since
     they share the class.
   - Still Reading rows had no cover/thumbnail at all, just title + bar. The
     design file gives each row a 40×60px avatar (its own mockup uses a flat
     color + initial letter, since it has no real book data) — kept the
     LAYOUT (a 40×60px thumbnail box to the row's left) but used the app's
     own established real-cover-with-fallback-icon convention instead of a
     fabricated color+initial block, consistent with the owner's explicit
     prior rejection of fabricated cover art for Shelves' grid.
   - Copy: adopted the design file's own exact wording where this session
     had used its own — `still_reading` → "Still growing"/"En pleine pousse"
     (a deliberate floral-lexicon label the design file already uses, not
     just "Still reading"), `continue_listening`'s French →
     "Reprendre l'écoute" (design file's exact phrase, was "Poursuivre
     l'écoute" — a fine synonym, but not what the file says).
   - The persistent header's wordmark styling didn't match either — this
     session had it as small-caps/tracked/dim; the design file's own top row
     is plain-case, `font:600 13px`, full `--text` color, tight
     `letter-spacing:-0.01em`. Fixed `.app-header-name` to match exactly.
3. **Bottom tab bar disappearing while scrolling.** Root cause: `.scrolly`
   (the flex-child scroll container used by Home/Shelves/Log/Settings) never
   had `min-height: 0` — a flex item with `overflow-y:auto` still defaults to
   `min-height:auto`, which refuses to shrink below its own content's
   intrinsic height. `.books-grid-wrap` already carries this exact fix, with
   its own comment explaining why (read directly, not guessed) — `.scrolly`
   was simply missed when that lesson was first learned. As long as a page's
   content stayed under one screen, this defect was invisible (nothing ever
   overflowed enough to trigger it); round 3 added enough content to Home/
   Settings/Log that real overflow finally happened, exposing it. Fixed by
   adding the same `min-height: 0` to `.scrolly`. Verified directly: with an
   inflated 40-book mock dataset (`.scrolly`'s `scrollHeight` at 1099px vs.
   the 812px viewport), `.view`'s `clientHeight` and `document.body`'s
   `scrollHeight` both stayed pinned at exactly 812px, and the tab bar's
   `getBoundingClientRect().bottom` stayed at 812 both before AND after
   scrolling `.scrolly` all the way to its own bottom — the overflow is
   fully contained inside `.scrolly` now, so the outer page can never
   inherit it and the fixed tab bar can never be dragged along.
4. **Settings ("You") page "horrendous," "random lines," "no margins."**
   Two related but distinct causes, both introduced by this session's own
   round-3 spacing fix:
   - The "random lines": round 3's fix for uneven group spacing added a
     `border-bottom` directly to `.set-group`. But every `.set-row` — including
     a group's first — ALREADY draws its own `border-top` unconditionally
     (the `.set-row:first-child` rule duplicating the base rule was a tell,
     in hindsight, that this had already been re-solved once). So a group
     boundary was drawing TWO separate hairlines (the group's own new
     border-bottom, then a 0.9rem gap, then the next group's first-row
     border-top) where every other row-gap in the page only ever draws ONE.
     That reads exactly like "extra lines appearing" — because it is. Fixed
     by removing the border-bottom from `.set-group` entirely (the next
     group's first-row border-top was always sufficient) and keeping only
     the margin-bottom reduction, which was the one genuinely necessary
     part of that fix. Verified: `getComputedStyle` on every visible
     `.set-group` now reports `borderBottomWidth: '0px'`.
   - "No margins on the sides" measured out to a false alarm technically
     (`.scrolly`'s `padding-left`/`padding-right` were never touched, and the
     first settings row sits exactly `.scrolly`'s own padding-left away from
     its edge, confirmed by measurement) — almost certainly a description of
     how the double-hairline bug above LOOKED (rules crowding edge-to-edge
     with no breathing room reads as "no margins" even when the technical
     padding is intact), not a second, separate bug. No change was needed
     here beyond the border-bottom fix above; flagging this reasoning
     explicitly rather than quietly assuming it, in case the owner still
     sees it after this fix and it turns out to be something else.

**Overall verification**: syntax-checked after every fix; full mocked-data
browser pass repeating everything from the previous entry PLUS: French
across every screen and every modal (Sleep/Export/Book Detail/Feedback/Bug
report/Voice/Erase) read with zero English leftovers this time; the genre
breakdown table read "Fantastique/Biographie/Science-fiction/Autre" correctly
sorted with Autre last; the tab-bar-survives-scroll test described above; the
`.set-group` zero-border-bottom check; Home's kicker+name split, per-card
progress bars, individually-bordered stat tiles (accent-colored numbers,
confirmed via `getComputedStyle`), and Still Reading's 40×60px real-cover
thumbnails, all confirmed rendering as built. Nothing pushed; `index.html`/
`privacy.html`/`terms.html` untouched. **Not device-verified** — same
standing caveat as always; this whole entry exists because the owner's OWN
device testing is what surfaced these four issues in the first place, so a
follow-up device check before calling this done would be worth it.

## Five more device-reported fixes: spacing, button sizes, header/title consistency (2026-08-27, same day)

A tighter follow-up batch, all traced to a concrete, measured cause rather than
guessed at — `index.green.html` only, nothing pushed.

1. **Settings ("You") spacing "still off."** Row-to-row rhythm was already
   fixed (equal 14.4px gaps, confirmed again this round). What was still off:
   `.sr-sub`'s `margin-top` was `0.1rem` (1.6px) — the sub-label text was
   nearly touching its key line above it, within EVERY row. Bumped to
   `0.3rem` + gave both `.sr-k`/`.sr-sub` real `line-height`s. Measured
   before/after: the key→sub-label gap went from 1.6px to 4.8px; row-to-row
   rhythm unchanged (still 14.4px everywhere).
2. **Home section spacing "should be increased."** `.cover-row`'s own
   `padding-bottom` (the gap after Continue Listening, before the stat row)
   was `0.3rem` — functionally no gap. `.home-stat2`'s `margin-bottom` (gap
   before Still Growing) was `1.3rem`. Bumped to `1.6rem`/`1.8rem`
   respectively. Measured the REAL visual gaps (last cover's bottom edge to
   the stat row's top, and the stat row's bottom to Still Growing's top) —
   25.6px and 28.8px now, clearly wider than before.
3. **Download/Completed buttons different sizes.** `.offline-btn` (top-right,
   the actual button) was `1.6rem`; `.finished-badge` (top-left, the
   checkmark) was `1.3rem` — a real, visible mismatch on any cover carrying
   both. Matched the badge up to the button's `1.6rem`. Confirmed via
   `getComputedStyle`: both render at 25.6px now.
4. **Logo+wordmark not in the same spot on every page.** Root cause: three of
   the four views (Home/Log/Settings) have `.app-header` INSIDE `.scrolly`,
   inheriting its `1.1rem` padding for free; Shelves' header sits OUTSIDE
   `.scrolly` (its own top chrome has to stay put while only the grid below
   scrolls) with no padding of its own at all — landing flush at the literal
   screen edge instead. Added a scoped `#library-view > .app-header` padding
   rule matching `.scrolly`'s exactly (had to match `.app-header`'s own
   extra `0.2rem` top padding too, found by measuring — first attempt was
   3.2px short). Verified by measuring the icon element's own
   `getBoundingClientRect()` (not `.app-header`'s own — padding moves an
   element's CONTENT, not its own box position, so measuring the header div
   itself would have silently reported no change) on all four views: all
   four now land at the identical (20.8, 17.6).
5. **Page title format/font/size/position inconsistent.** Re-checked
   `PhonoLeaf.dc.html` directly rather than assuming the earlier "Shelves
   and Stats looked like two different sizes" fix (which matched Shelves'
   `<h2>` to `.home-title`) had picked the right size to match TO — it
   hadn't: the design file specifies plain page titles (Shelves/Log/Settings)
   at exactly `26px`, and this codebase had them all sharing `1.85rem`
   (29.6px) instead, matched to each other but not to the file. Also, Home's
   OWN "page title" changed meaning in the previous round (kicker + name,
   not a plain title) without anyone deciding what size the name line
   should be — the design file sizes it `28px`, a deliberate 2px LARGER than
   a plain page title, not the same. Fixed `.home-title` (the one shared
   class every screen's title uses) to 26px, with a Home-only override
   bumping just the name line to 28px; also unified the title→content gap
   that follows it (`.lib-header`'s bottom padding vs `.home-title`'s own
   margin-bottom were two different values, 0.9rem vs 1.3rem — now both
   `1.1rem`). Verified via `getComputedStyle` on all four: Shelves/Log/
   Settings all report `26px`/`400`/Literata; Home's name line reports
   `28px`.

**Verification**: syntax-checked after every fix; full mocked-data browser
pass repeating the header-position/title-size/gap/button-size measurements
above on all four views, in both languages where relevant. Nothing pushed;
`index.html`/`privacy.html`/`terms.html` untouched. **Not device-verified**
— every issue in this entry came from the owner's own device testing, so a
follow-up check there remains the real confirmation.

## Settings row layout: Theme/App language needed the design file's OWN stacked treatment (2026-08-27, same day)

The previous entry's `.sr-sub` margin bump wasn't the real fix — the owner
reported the same "spacing... still off" again. Re-read `PhonoLeaf.dc.html`'s
`isSettings` block in full this time (not just the first two rows, which is
what led to the wrong conclusion originally) and found the design file does
NOT use one uniform row layout for all of Settings: every row with a single
control (a text button, one toggle chip, the speed menu) is the horizontal
`justify-content:space-between` layout already built here — but Theme (3
buttons: Light/Dark/Auto) and App language (2: EN/FR) are each their own
`flex-direction:column` stack, label block on its own full-width line and
the option row BELOW it, specifically because a 2-line label plus a
multi-button control never fits comfortably side-by-side on a real phone
width. Confirmed by direct measurement at native 375px width (not a zoomed
screenshot — see below): with the OLD horizontal layout, `.sr-sub`'s
`max-width:52vw` combined with competing space from 3 segmented buttons
pushed "Light, dark, or match device" into multiple cramped lines. Added a
`.set-row-stack` modifier (only on Theme's and App language's markup) that
switches those two rows to the column layout with a `0.6rem` gap between the
label block and the option row, and lifted `.sr-sub`'s `max-width` cap for
stacked rows specifically (no longer needed — nothing else shares the line).
Every other row's markup/CSS is untouched.

**A measurement pitfall worth recording**: first tried to inspect this
visually with `document.body.style.zoom = '2.2'` for a bigger screenshot
(the Browser pane's own `zoom` action doesn't support region-cropping yet).
That EXAGGERATED wrapping far beyond what the page actually does at real
375px width — rows that measure as clean single lines via
`getBoundingClientRect()`/`window.innerWidth` (confirmed still reporting 375
under zoom) appeared to wrap 2-3 lines deep in the zoomed screenshot. Verified
the real behavior by measuring `element.height / computed-line-height` (an
integer line count) at `zoom:1` instead, which is what actually caught that
only Theme/App language needed fixing — everything else was already fine, a
zoomed screenshot alone would have suggested otherwise. Worth remembering
next time a screenshot needs magnifying: trust unzoomed `getBoundingClientRect`
math over a `body.style.zoom`'d screenshot for anything wrapping-sensitive.

**Verification**: syntax-checked; measured every visible Settings row's
label/sub-label line count at native 375px width in both languages — every
row (including the two newly-stacked ones) now reports exactly 1 line for
its key, and 1 line for its sub-label except Export/Delete's naturally
longer descriptions (2 lines there, expected and unchanged, same as the
design file's own free-wrapping sub-labels). Zero console errors. Nothing
pushed. **Not device-verified.**

## Shelves title alignment (real root cause found) + a genuine Home gap-system rebuild (2026-08-27, same day)

**Shelves' title still didn't line up with Log/Settings.** The previous
fix (removing `.lib-header`'s own top padding) was necessary but not
sufficient — measured 82.3px before that fix, 64.7px after, still 7.5px off
Log/Settings' 57.2px. The remaining gap: `.lib-header` uses
`align-items:center`, vertically centering the `<h2>` against the ROW'S
height — and the row's height is set by its TALLEST child, the view-toggle
icon button group, which is taller than the title text alone. So the title
was being pushed down by roughly half the icon-row's extra height on top of
everything else. Changed `.lib-header` to `align-items:flex-start`, so the
title starts flush at the row's own top edge, same as every other page's
title does (nothing before it to center against). Verified: all three now
measure to the exact same 57.2px, and a screenshot confirms the icon row
still reads cleanly anchored to the title's top rather than looking
mis-aligned.

**A genuine review of Home's spacing, not incremental nudges.** Re-read the
design file's `isHome` block with fresh eyes and noticed something the
previous two passes both missed: EVERY section on Home (greeting, Continue
Listening, the stat row, Still Growing) is a child of ONE
`display:flex;flex-direction:column;gap:24px` wrapper in the design file —
a single, uniform section-to-section gap, not a collection of individually
tuned margins. This codebase had instead accumulated three DIFFERENT,
independently-guessed values across two previous rounds (17.6px after the
greeting via `.home-title`'s shared margin, 25.6px after Continue Listening
via `.cover-row` padding-bottom, 28.8px after the stat row via
`.home-stat2` margin-bottom) — closer to the design file's 24px than
before, but still three different numbers, which is very likely why
"spacing... not big enough" kept coming back even as individual values grew.
Rebuilt properly: added a `.home-sections` wrapper (`display:flex;
flex-direction:column;gap:1.5rem`) around Home's four section containers,
removed the individual margin/padding hacks that were standing in for it,
and added `.home-sections > div:empty { display:none }` so a section with
nothing to show (e.g. "Continue listening" when nothing's in progress)
collapses out of the gap entirely instead of leaving a blank 24px hole —
verified directly with a single-book, no-progress dataset: the two empty
section `<div>`s report `display:none` automatically, no dead space in the
rendered page.

Also corrected several component-level details the design file specifies
exactly, found during this same re-read: stat-tile padding 16px (was
14.4px) and the stat number's own font-size 26px mono (was 22.4px — visibly
too small against the design's actual number treatment), a 4px gap between
a stat number and its label (was touching, no margin at all), Continue
Listening's card gap 14px and section-label-to-content gap 12px (was
11.2px/10.4px), and Still Reading's rows switched from `border-top` to
`border-bottom` (the design file trails each row with its divider rather
than leading it, so the label doesn't get an odd hairline immediately
under it).

**Verification**: syntax-checked; measured Home's three section gaps
directly via `getBoundingClientRect()` — all three report exactly 24.0px;
confirmed Shelves/Log/Settings titles all measure to the identical 57.2px;
re-confirmed Settings' key→sub-label gap (4.8px) and row-to-row rhythm are
STILL uniform across all 12 rows after these changes (nothing here touched
`.set-row`/`.sr-k`/`.sr-sub`, but re-checked since the same page was in
scope this round); Log page's own copy of the stat-tile grid re-screenshotted
to confirm the shared CSS change (used by both Home and Log) reads well
there too. Zero console errors. Nothing pushed; `index.html` untouched.
**Not device-verified** — same standing caveat.

## Settings key/sub-label spacing: a likely cross-renderer line-height difference, not a padding bug (2026-08-27, same day)

The owner sent an annotated screenshot from their actual device (native app,
not this browser) marking it directly: every row shows a visibly SHORT gap
above the key text and a visibly LONG gap below the sub-label, consistently,
across rows with and without a group boundary, with and without extra icons,
single- and multi-line subs.

**This did not reproduce in the browser sandbox.** Measured the exact same
row (Export my data) directly: `.set-row`'s padding-top and padding-bottom
were both a provably equal 14.4px in CSS, and the actual rendered gap above
the key text vs. below the sub-label measured 15.06px vs. 14.4px — under 1px
apart, nothing like the screenshot's obvious asymmetry. Ruled out the two
likely CSS-side explanations directly: `.set-row`'s padding values were
confirmed equal, and `document.fonts` confirmed Literata was genuinely
loaded and applied (not silently falling back to a generic serif with
different metrics). That leaves the most likely remaining explanation:
**how a renderer distributes a line's "half-leading"** — the extra space
`line-height` adds beyond a font's own metrics is supposed to split evenly
above and below the glyphs, but this is a genuinely known area of
cross-engine inconsistency (older/different WebView versions in particular
have been known to place most or all of that extra space on ONE side rather
than splitting it) — and Literata's line-heights here were on the generous
side (1.3/1.4), giving whatever asymmetry exists more room to show up. This
session's own Chromium-based sandbox evidently distributes it evenly (hence
no visible bug here); there's no way to confirm the device's exact behavior
without testing on it directly.

**Fix, given that uncertainty**: removed the ambiguity rather than trying to
out-guess one specific renderer's behavior. `.sr-k`/`.sr-sub`'s line-heights
were tightened way down (1.3→1.15, 1.4→1.15) so there's much less "extra"
leading available to be distributed unevenly in the first place; the key→sub
gap itself was moved off `.sr-sub`'s margin-top and onto a real `gap` on a
new `.set-row > div:first-child { display:flex; flex-direction:column }`
wrapper, so that spacing is deterministic box-model space, not
line-height-adjacent margin collapsing into leading. As a defensive
supplement (since the fix above can reduce but not fully rule out a
platform-specific split), also rebalanced `.set-row`'s own padding
top-heavy (0.9rem/0.9rem → 1rem/0.8rem, same total so the page's overall
row-to-row rhythm is unchanged) — directionally corrects for exactly the
"short above, long below" pattern reported, in case some of it survives the
leading fix on their device. This is an empirical correction based on their
screenshot, not something provable from this sandbox alone.

**Verification**: syntax-checked; confirmed every row's key text still fits
on exactly one line in both languages (tightening line-height risks
clipping tall glyphs/accents — checked directly, nothing clipped); zero
console errors; screenshotted the full Settings page, which reads cleanly
balanced in this sandbox (as it did before the fix — this sandbox was never
reproducing the reported bug, so a clean screenshot here isn't proof the
device will look different than before). **Explicitly not verified against
the actual reported symptom** — the owner's own device is genuinely the only
way to confirm this closed the gap, since the sandbox couldn't reproduce it
to begin with. Flagging this clearly rather than claiming a fix that's only
theoretically justified.

## Settings spacing: ACTUAL root cause found (`.set-group` margin), plus five more design-file corrections (2026-08-27, same day)

**The Settings spacing asymmetry finally has a real, reproducible cause —
and my previous entry's cross-renderer line-height theory was WRONG.** The
culprit was `.set-group { margin-bottom }`. Every `.set-row` carries its own
`padding: 0.9rem 0` (14.4px) plus a `border-top`, so *within* a group the
hairline sits exactly 14.4px from the text above and 14.4px below —
balanced. But at a **group boundary** it was the last row's 14.4px
padding-bottom PLUS the group's own 14.4px margin = **28.8px above the
hairline, against only 14.4px below it**. Exactly the "short space above
text, long space below" the owner marked up, and it recurs at every group
boundary down the page — which is why it kept reading as a page-wide
problem rather than a few isolated rows.

**Why three previous attempts missed it**: I kept measuring row-box to
row-box (`rowGaps` came back `[0,0,0,0,0,14.4,...]`) and read the 0s as
"perfectly tight" and the 14.4s as "one clean row-gap." But each row's
padding lives *inside* its own border-box, so box-to-box distance says
nothing about where the hairline sits relative to the *text*. The correct
measurement — hairline-to-text-above vs hairline-to-text-below — exposes it
immediately (39.3 vs 15.1 at boundaries, 14.4 vs 15.1 within groups). The
earlier "equal padding, must be a renderer difference" conclusion was built
on that wrong measurement; the padding genuinely was equal, but padding was
never the whole story. **Lesson: to check whether a divider looks centered,
measure from the divider to the rendered text on each side — never
element-box to element-box on elements that carry their own padding.**
Also reverted that entry's speculative `1rem/0.8rem` top-heavy padding
rebalance, which was compensating for a misdiagnosis; padding is symmetric
`0.9rem` again. Verified after: every row on the page now measures
symmetric content padding (15.1 vs 14.4, i.e. equal within the 1px
border's sub-pixel rounding), including rows whose control is taller than
the label block (those center correctly). The design file has no group
concept at all — it's one flat list of identical rows — so `margin-bottom:
0` matches it as well as fixing the rhythm.

**Five more corrections in the same pass, each traced to the design file or
a concrete defect:**

1. **Privacy/Terms "not changed by the new design" — they were never
   reachable.** The redesigned `privacy.green.html`/`terms.green.html` built
   in an earlier round were correct, but both the sign-in screen and the
   Settings footer still linked to plain `privacy.html`/`terms.html` (the
   old Botanical-Editorial pages), so tapping either in the app always
   showed the OLD design. Repointed all four links at the `.green.html`
   files, and — importantly for how the owner actually tests — added those
   four files to `scripts/stage-test.js`'s staged `FILES` list, since
   otherwise `npm run sync:test` would leave them absent from `www/` and the
   native build would 404 on the very links this fix adds. Staged under
   their own `.green` names (not substituted over `privacy.html` the way
   `index.green.html` is over `index.html`) so browser and native resolve
   identically, and the real `privacy.html`/`terms.html` stay staged for
   the real `npm run sync`. Verified both links return HTTP 200 and the
   redesigned page renders (accent-inverted summary box, serif title).
2. **Header background inconsistent ("sometimes white, sometimes beige").**
   Real and precisely as described: `.lib-header`/`.lib-search` (Shelves
   only — the one view whose header chrome sits outside `.scrolly`) painted
   themselves `var(--surface)`, while Home/Log/Settings' headers sit on
   plain `var(--bg)`. In Daylight those are `#F3EEE1` vs `#EFE9DA` — close
   enough to look like an accident, far enough apart to notice. Set both to
   `background: none`. Verified all four views' header backgrounds now
   resolve to the identical color in BOTH themes (`#EFE9DA` light,
   `#0F1411` dark).
3. **Book covers too small.** The design file's `BookCard` in the Continue
   Listening row is `120px` wide (`width:100%` at `aspect-ratio:2/3`, so
   120×180); the app had `.cr-item` at `88px`. Set to 120px, and adopted the
   file's own card title styling too (`600 0.85rem` UI in full `--text`,
   was a dim `0.66rem`). Verified the rendered cover measures exactly
   120×180. Shelves' grid was already correct (`100%` of column, `gap:16px`).
4. **Log page's top boxes too large.** Found the cause in the design file:
   it sizes Log's six tiles deliberately SMALLER than Home's two
   (`padding:10px` / `17px` number / `10px` label / `8px` gap, vs Home's
   `16` / `26` / `11` / `10`). An earlier pass applied Home's larger numbers
   to the shared `.stat-grid2` class, so six oversized tiles dominated the
   Log page. Split the two variants apart per the file (`.stat-grid2` is now
   the compact Log base; `.home-stat2` overrides up to Home's larger
   treatment). The tile block dropped from ~305px to 189px — 23% of a 812px
   viewport, comfortably under the owner's "no more than a third" bar, and
   the week chart plus breakdown table now fit onscreen with it.
5. **Logo/wordmark too close to the page title.** Increased `.app-header`'s
   `margin-bottom` 0.9rem → 1.5rem (measured gap 14.4px → 24px). Noting
   deliberately that this is an owner-requested increase that goes *beyond*
   the design file's own ~12px: in the file the wordmark lives in a fixed
   top bar visually separate from the scrolling content, which reads as more
   separation than the identical gap does here, where the header scrolls
   inline directly above the title.

**Verification**: syntax-checked `index.green.html` and `stage-test.js`;
per-row hairline-to-text measurements across all 13 Settings rows; header
background equality across all four views in both themes; cover dimensions;
Log tile block as a fraction of viewport; both legal-page links fetched
(200) and the rendered page screenshotted; Home/Settings/Log screenshotted.
Zero console errors. Nothing pushed; `index.html`/`privacy.html`/
`terms.html` untouched. **Not device-verified** — and note item 1 above
means the Privacy/Terms redesign has genuinely never been seen on device
yet, so that one is worth a look specifically.

## Legal pages now follow the app's theme + language, and share its exact header geometry (2026-08-27, same day)

Once the Privacy/Terms links actually resolved (previous entry), the pages
themselves turned out to be disconnected from the app in three ways.

1. **Theme was ignored entirely — the pages only ever followed the OS.**
   The four `.green.html` legal pages had `:root` light defaults plus a
   `@media (prefers-color-scheme: dark)` block and *nothing else*: no
   `[data-theme]` blocks and no theme-init script (confirmed by grep —
   zero occurrences of `data-theme` in all four). So a user who had chosen
   **Light** in the app's Settings, on a phone set to dark, still got dark
   legal pages. Fixed by mirroring the app's own mechanism exactly: the
   same pre-paint init script reading the same `localStorage.pl_theme` key
   that `Theme.apply()` writes, plus `[data-theme="light"]` /
   `[data-theme="dark"]` token blocks placed after the media query so a
   forced choice wins on source order — the identical pattern (and
   identical hex values) `index.green.html` already uses, so the two can't
   drift. Privacy's accent summary box had a separate
   `prefers-color-scheme` shadow override that would have been stranded by
   this; folded it into a `--shadow-ink` token defined per theme instead.
2. **Language was already correct** — the `pl_lang` redirect logic was read
   through and verified sound, then confirmed working end-to-end in the
   browser (setting `pl_lang: 'fr'` and loading `privacy.green.html`
   redirects to `privacy-fr.green.html`, `lang="fr"`, French title). No
   change needed; recording that it was checked rather than assumed, since
   it was reported alongside the theme bug.
3. **Header geometry didn't match the app.** These pages had their own
   unrelated header treatment — a 26px icon, an uppercase letter-spaced
   `--text-dim` wordmark, `2.5rem/1.5rem` page padding, `2.4rem` topbar
   margin — versus the app's 22px icon, plain-case `600 0.82rem --text`
   wordmark, `1.1rem` scroll padding and `0.2rem` header padding-top.
   Rebuilt `.wrap`/`.topbar`/`.brand` to reproduce the app's own geometry
   (`1.1rem` page padding + `0.2rem` topbar padding-top puts the icon at
   the same y; `1.1rem` side padding the same x), matched the icon to 22px,
   and matched `h1` to the app's shared `.home-title`
   (`1.625rem/1.15`, `-0.02em`, `0.5rem` top margin). Per the owner's note
   about knock-on spacing, the title's own top margin and the `.updated`
   line's bottom margin were kept/tuned so the shorter header doesn't
   crowd the text beneath it — screenshotted to confirm it still reads
   naturally rather than merely measuring correctly.

**Verification** (all four files, in the browser): with `pl_theme: 'light'`
on a dark-OS sandbox, the pages render light (`--bg` #EFE9DA) — the exact
case that was broken; with `'dark'`, dark plus the correct dark shadow
token; with the key absent ('auto'), they defer to the OS as before. The
French redirect fires and lands on the `-fr` page with the right `lang`
and title. Header geometry measured against the app's own rendered
Settings header and matches to the pixel on every page: icon top **20.8**,
left **17.6**, width **22**; `h1` top **66.8** at **26px** — identical
values to `#settings-view`'s `.app-header-icon` and `.home-title`.
Zero console errors. Nothing pushed; the real `privacy.html`/`terms.html`
remain untouched. **Not device-verified.**

## Status-bar band on the legal pages + "Your forest" on Home (2026-08-27, same day)

**Status-bar band missing on Privacy/Terms.** Cause: those pages carried
`viewport-fit=cover` in their viewport meta (inherited from the original
`privacy.html`, which is a plain website page where that's fine), which
makes the page draw UNDERNEATH the system status bar — so the band behind
the clock/battery that every other screen shows simply wasn't there.
`index.green.html` deliberately omits `viewport-fit`, so the WebView lays
out below the status bar. Removed it from all four legal pages, and
replaced their single hardcoded green `theme-color` with the app's own
light/dark pair (`#EFE9DA` / `#0F1411`) so the band is tinted identically
rather than green. Verified all four viewport metas now read exactly
`width=device-width, initial-scale=1.0`.

**"Your forest" — the Now page's empty bottom space.** The owner rejected
every book-based fill ("that's what the library is for", and realistically
people are in 1–2 books at a time) and asked for something out of the box.
Of three proposals (a growing plant, a finish-by forecast, an evening
wind-down card) they picked the plant, refined to: **a forest where every
book is a tree, grown by that book's own progress, up to completion.**

New `Forest` module + `#home-forest` section, last on Home:
- One tree per book with progress > 0. **Six growth stages** keyed off that
  book's own percentage (sprout at 20%, two-leaf seedling, sapling, young
  tree, tall tree, full canopy at 100%), drawn bottom-anchored in a fixed
  `44x100` viewBox so every tree stands on the same ground line no matter
  its height — the card's own bottom border IS that ground. The result
  reads as a skyline of exactly where each book stands, so it's an honest
  data view as much as an ornament; nothing is invented, a tree exists only
  because a real book has real progress.
- **Completion is the reward**: an in-progress tree is drawn in outline, a
  finished book's tree fills solid `--accent`. No badge needed.
- Two tree forms (round canopy / conifer), picked deterministically from
  the book id so a given book is always the same kind — purely visual
  variety, encodes nothing.
- **Library order, deliberately not sorted by progress**, so a tree keeps
  its place in the forest and only ever grows; sorting would make trees
  jump around between renders.
- Tapping a tree opens that book's Book Detail, matching the app's existing
  tap convention. A small `{n} growing · {n} fully grown` line sits under
  the forest so the visual also states its own numbers.
- **Empty state** (onboarding, nothing started): the owner's exact copy,
  "Read your first book to unlock your forest!", over a faded sprout that
  hints at what the space becomes. The hint uses `--text-dim` at 0.45
  opacity, not `--line` as first written — `--line` is so close to
  `--surface` in Midnight that the sprout was invisible.
- Localized: `forest_title` / `forest_empty` / `forest_growing` /
  `forest_grown` in both EN and FR.

**One real bug caught during verification, worth recording.** The trees
were first written as `<svg role="button" tabindex="0" onclick=...>`,
following the codebase's usual "clickable div needs role=button" convention.
That convention is backed by a delegated keydown handler that calls
`e.target.click()` — and **an `SVGElement` has no `.click()` method in this
WebView**, confirmed by it throwing `TypeError: tree.click is not a
function`. So every tree would have been focusable but impossible to
activate by keyboard, throwing on each Enter press. Rewrote as a real
`<button>` wrapping an `aria-hidden` SVG: natively focusable and
activatable, needs no role/tabindex, and the global `appearance:none` reset
already strips its chrome. **Lesson: the `role="button"` convention in this
codebase is only safe on HTML elements — an SVG needs a real `<button>`
wrapper.**

**Verification**: syntax-checked; both states rendered in both themes and
both languages (empty state and a seven-book forest spanning all six
stages); measured that seven trees fit one row at 375px (340px wide, 99.7px
tall); enlarged the SVGs temporarily to inspect every stage's shape
individually; confirmed pointer activation opens the right Book Detail and
that the button is genuinely focused (`document.activeElement`) with the
correct `aria-label`. Zero console errors. **Keyboard Enter activation
could NOT be fully proven here** — a probe showed the key event reaching
the focused button with nothing preventing it, but the harness synthesizes
key events with an empty `key` value, so the browser never treats it as
Enter; a synthetic `KeyboardEvent` can't trigger native activation either.
The element being a real `<button>` with a working onclick is the actual
guarantee. Nothing pushed. **Not device-verified.**

## "Still growing" removed (it was rendering garbled), forest rebuilt realistically (2026-08-27, same day)

**The garbled "Still growing" rows had a precise, embarrassing cause: a CSS
class-name collision.** The owner's device screenshot showed every row's
title wrapped in a long lens-shaped outline with a line struck through the
text. `.sr-info` — which the Still Reading rows used for their full-width
title/progress wrapper — **was already taken**: it is Settings' small
circled "i" explainer button, styled
`width: 15px; height: 15px; border: 1px solid var(--accent); border-radius: 50%`.
Defined LATER in the stylesheet, so it won the cascade. A `border-radius:
50%` on a wide, short box renders as exactly that lens, and its border drew
the outline through the title. In this file the `.sr-` prefix already means
**settings row** (`.sr-k`, `.sr-sub`, `.sr-info`); reusing it for "still
reading" was the mistake. A comment now sits where that CSS block was,
warning the next person off the prefix.

The owner asked for the section to be **removed** rather than fixed, which
is also the right call on the merits: every started book already appears in
the forest below, and Shelves is where you browse the library. Removed the
markup, the render block, `_stillReadingRowHTML`, all seven `.sr-*` rules,
and the now-unused `still_reading` string in both languages. Home's
`inProgress` list no longer computes a `rest` slice. Grep confirms zero
remaining references.

**Forest rebuilt for realism** (owner: "surely we can make something more
realistic than this!"). Two real defects plus a general quality pass:

1. **Every tree was the same species.** The species picker was
   `id.charCodeAt(0) % 2` — and **Google Drive file ids overwhelmingly begin
   with the same character**, so in real use every book hashed identically
   and the forest was a row of identical triangles. (It looked varied in my
   synthetic tests only because I'd used ids starting a/b/c/d…, which is
   exactly the wrong test data.) Replaced with **FNV-1a over the whole id**;
   re-tested with seven Drive-style ids all starting `1`, which now yield
   all three species.
2. **Growth was six hand-drawn stages**, so a book jumped between unrelated
   clip-art shapes. Now growth is a **continuous scale of the finished
   tree** about its base point — a part-read book is literally a smaller
   version of what it becomes, which reads like a sapling. Below ~15% it's
   still a sprout, drawn unscaled so it doesn't vanish.
3. **Three species instead of two** — pine (three stacked tiers, not one
   triangle), broadleaf (four overlapping crowns), poplar (tall ellipse with
   an inner highlight) — each with layered per-shape opacity for depth.
4. **Trunks are `--text-dim`, not accent**: a warm grey-brown in Daylight
   and grey-green in Midnight, both of which pass for wood, where a green
   trunk did not.
5. **Deterministic height jitter** (0.92–1.08 from the same hash) so no two
   trees are stamped identical, plus a faint ground shadow ellipse under
   each so they sit on the ground rather than float.
6. **Completion now reads as colour strength, not outline-vs-fill.** Foliage
   opacity ramps with progress and goes full-strength at 100%. The old
   outline style was chosen partly to avoid messy overlapping strokes —
   which is precisely why the crowns had to be single flat shapes; filled
   shapes let the layered, more organic crowns work.
7. Trees now sit shoulder to shoulder (`gap: 0`) so the row reads as a
   treeline; small trees carry their own padding inside the viewBox, so
   nothing collides.

**Verification**: syntax-checked; seven Drive-style ids rendering all three
species; measured the forest fits one row at 375px (111.7px tall);
temporarily enlarged the SVGs to inspect each species individually;
populated and empty states in both themes and both languages. With Still
Growing gone the whole Now page now fits one screen — the empty space this
whole thread was about is closed. Zero console errors. Nothing pushed.
**Not device-verified.**

## Continue Listening's 3rd card no longer clips + Book Detail gains Pages/Length + description HTML actually strips (2026-08-27, same day)

**Continue Listening overflow.** Real, measurable bug, not a device quirk:
3 cards at a fixed 120px + 2×14px gaps measure 388px, against a content
width of ~340px on a 375px phone — the third card was ALWAYS partly clipped
requiring a scroll to see, on any phone this size or narrower. Fixed by
sizing each of the (always ≤3) cards to `calc((100% - 2 * gap) / 3)`
instead of a fixed px guess — this is correct on any device width by
construction, not tuned to one. Verified: 3 cards render at 104px each on a
375px viewport, last card's right edge lands exactly on the row's own right
edge (measured overflow: -0.02px, i.e. none).

**Book Detail — added Pages and Listening length @1×, replacing "at
{rate}×".** The old duration stat scaled with whatever playback speed
happened to be set and only ever showed anything for the currently-open
book; asked "what else could we add," pages and a rate-independent length
were the two concrete, real (not fabricated) numbers the app already had
access to but wasn't showing:
- **Pages**: `Meta.data[id].pages`, already fetched from Open Library in
  the background for every book (used today only for Stats' length-tier
  breakdown) — genuinely free to surface here too.
- **Length @1×**: for the open book, the same live epub.js location count
  `estimateTimeLeft` always used, just pinned to rate 1 (gave the function
  a third `rate` param, default unchanged for its one other caller). For a
  book that ISN'T open there's no live index to measure — estimated instead
  from its page count (~250 words/page at a ~150wpm narration pace),
  marked with a leading "~" specifically for that case, so the two
  accuracies read differently without either being invented data.
`.detail-stats` restructured from a 3-wide row to 2×2 (4 stats now) with
real cell borders. Verified in both languages: EN `PAGES / CHAPTERS / READ
/ LENGTH @1×`, FR `PAGES / CHAPITRES / LU / DURÉE À 1×`; a closed book with
no cached pages correctly shows all-dash rather than a guess.

**Description showing literal `<p><b>` tags** (owner: "To Hold Up the
Sky"). Cause: some epubs' `dc:description` embeds real HTML, and it was
being written via `.textContent` — safe, but shows the raw markup as text
instead of formatting it. New `stripHtml()` helper, **deliberately not**
"just render it as HTML" — `.innerHTML = untrustedString` can trigger
network/JS side effects (`<img onerror>`) even on an element never attached
to the page, which this app's own escape-everything-external convention
exists to rule out. Two-step, both string/DOM-safe: (1) regex converts
block-closing tags to paragraph breaks and strips every remaining tag —
BEFORE any entity decoding runs, so nothing tag-shaped survives to reach
step 2; (2) full entity decoding via the standard `<textarea>` RCDATA
trick (setting `.innerHTML` on a textarea decodes character references
without ever parsing child elements from it) — chosen over a hand-rolled
entity table specifically because real descriptions use far more than
`&amp;`/`&lt;`: tested `&eacute;`/`&mdash;`/`&hellip;`/curly quotes, all
decoded correctly, which a 6-entry map would have missed. `.detail-desc`
gained `white-space: pre-line` so the `\n\n` paragraph breaks the stripper
produces actually render as breaks under a plain `.textContent` assignment.
**Verified adversarial input directly**: `<img src=x onerror="alert(1)">`,
`<script>alert()</script>`, and double-encoded `&lt;script&gt;…` all
produced inert plain text with zero alerts fired (window.alert spied) —
confirmed the tag-strip-before-decode ordering actually holds under
targeted attack, not just the happy path.

**Verification**: syntax-checked; overflow math confirmed via
`getBoundingClientRect`; full open→closed→open BookDetail cycle with a
book carrying HTML-formatted description + page count, in both languages;
`stripHtml()` unit-tested standalone (named/numeric entities, `<br>`,
nested tags, empty/undefined input, all three adversarial cases). Zero
console errors. Nothing pushed. **Not device-verified.**

## List-view finished icon, Chapters actually persists, forest ambience (sun/moon + a passing critter) (2026-08-27, same day)

**List-view finished badge "not well placed."** Real cause: in list/table
mode the cover thumbnail shrinks to `2.6rem` (41.6px), but `.finished-badge`
is a `1.6rem` (25.6px) circle absolutely positioned over the cover's
top-left corner regardless of mode — at that size it covered most of the
tiny cover art. Meanwhile the offline/download button already lived
OUTSIDE the cover in list mode, as a normal trailing icon at the row's far
end. Fixed to match: in row mode the badge now also renders as a normal
(not absolutely-positioned) trailing icon, placed directly before the
offline button in DOM order — literally to its left, as asked — via a new
`.book-row .finished-badge { position: static }` override; grid mode is
untouched (the badge still overlays a full-size cover there, where it
always fit fine).

**"I see a spot for Chapters, but never see any data" — not a bug, but a
real gap, now closed.** The count was gated on `isOpen` (`State.currentBook
=== b`), which is genuinely only ever true if this EXACT book happens to be
the one currently loaded in the reader — and Book Detail is opened from
Shelves/Home specifically to DECIDE what to read, so in practice that's
almost never the case. `Meta.fromBook()` now also computes a `chapters`
count from the epub's own navigation (`flattenToc`, same flattening Book
Detail already used) at the exact point every other piece of metadata is
already captured for free — the moment a book has ever been opened once.
`Meta.capture()`'s merge-list gained `'chapters'` so already-cached entries
backfill it too. Book Detail now uses the live count when open, the cached
one otherwise, and only shows '—' for a book that has genuinely never been
opened. Verified with a mock epub `navigation.toc` (2 top-level entries +
1 nested subitem + 1 href-less entry that should NOT count) → correctly
returns 3; verified a book with a pre-cached `chapters: 14` renders that
number in Book Detail while closed.

**Why Pages/Length and Description are inconsistent — explained, not a
bug, but worth being precise about since two different mechanisms are
involved:**
- **Pages** comes from a background Open Library title(+author) search
  (`Meta.fetchAll`/`_fetchOL`), and only lands if (a) the search actually
  finds a matching edition, AND (b) that specific hit happens to carry
  Open Library's own `number_of_pages_median` field — many self-published,
  foreign-language, or obscure titles never match, or match but lack that
  field. Also: `fetchAll`'s own `pending` filter only retries a book while
  it has NEITHER genre nor pages yet (`!m.genre && !m.pages`) — so a book
  that matched well enough to get a genre back but not a page count on that
  same lookup will never be retried for pages afterward, since it no
  longer qualifies as pending. Not fixed this round (a deliberate,
  pre-existing rate-limiting choice, not a defect) — flagged for awareness.
- **Description** is NOT fetched from anywhere — it's the epub file's own
  `dc:description` OPF field, read directly off the book the moment it's
  opened (`Meta.fromBook`). It's present or absent purely because the
  publisher/converter who made that specific EPUB file did or didn't fill
  that field in; nothing the app does affects it either way.

**Forest ambience.** Two additions, both purely decorative and both gated
so they never run where they shouldn't:
- **Sun (Daylight) / Moon (Midnight)**, small and quiet in the card's
  corner — Feather icons' own proven sun/moon paths, shown/hidden by the
  same `@media (prefers-color-scheme)` + `[data-theme]`-wins-on-source-order
  pattern used everywhere else in this app for a theme choice, so it's
  correct through a live theme change with no JS involved at all.
- **One critter at a time** (bird / butterfly / bee — a squirrel would need
  a ground-level walk cycle, a genuinely different animation, left for a
  future pass rather than making it look like a flying squirrel), appearing
  at one edge of the forest card and exiting the other, on a random
  14–34s schedule, picked freshly each flight (random species, random
  direction, random height, small random vertical bob via CSS custom
  properties computed in JS from the card's own measured width so the
  flight always fully clears both edges regardless of screen size).
  Explicitly `Nav`-managed like `Stats`'/`SleepTimer`'s own intervals: the
  timer arms only when the Home tab becomes active and is torn down the
  moment you leave it, so nothing runs in the background on other tabs.
  Also skipped entirely under `prefers-reduced-motion: reduce` — decorative
  motion with nothing depending on it should just not happen for that
  preference, not merely animate near-instantly under this app's existing
  blanket reduced-motion CSS clamp.

**Verification**: syntax-checked; list-view badge positioning confirmed via
screenshot (checkmark now clearly left of the download icon, both same
size, cover fully visible); `Meta.fromBook()` chapter counting unit-tested
against a mock TOC; sun/moon swap confirmed in both forced themes; critter
lifecycle exercised directly — spawn, a second spawn attempt while one is
already flying correctly ignored, natural re-spawn via the scheduled timer
observed, and `animationend` cleanup confirmed removing the element and
clearing the one-at-a-time reference; `prefers-reduced-motion` confirmed
to prevent the timer from arming at all; confirmed `Forest.stopCritters()`
actually clears the timer when navigating away from Home. Zero console
errors. Nothing pushed. **Not device-verified.**

## Forest: fixed-size canvas with semi-random overlapping trees; critter flight actually varies speed (2026-08-27, same day)

**Forest layout rebuilt from a flex row to a fixed-size absolute-position
canvas.** The previous version used `flex-wrap`, so it grew a new row
every time the treeline ran out of horizontal space — meaning Home's total
height (and whether it fit one screen) depended on how many books were in
progress, exactly the opposite of "fits all the time." `.forest` is now a
FIXED `5.75rem` height regardless of book count; each tree is
absolutely-positioned in its own `.forest-slot` rather than flowing in a
row, computed as:
- **Horizontal**: an even slot per tree (`(idx+0.5)/n`) with jitter inside
  it — not a perfectly-spaced grid, and as the slot count `n` grows, slot
  width shrinks so trees increasingly overlap ("bundle up") rather than the
  canvas ever growing to fit them all separately. This is the actual
  mechanism that keeps a 3-book and a 25-book library both fitting the same
  fixed box.
- **Vertical**: trunk foot at `(hash % 49)%` from the card's OWN bottom
  edge — always in the bottom half (0–49%), per the ask, but not on one
  shared baseline.
- **Depth**: a tree placed higher up (further from the bottom edge) also
  draws smaller (`perspective = 1 - depth/49 * 0.42`) — reads as
  "farther away," and is ALSO the reason a fully-grown tree can never
  overflow the fixed card height regardless of where its base lands (the
  math was solved for the worst case: full-size box at max depth still
  clears the remaining headroom above it).
- **Lean**: a small ±8° rotation per tree, pivoting around its OWN base
  (`transform-origin: bottom center` on the button, positioned by an outer
  wrapper that only translates — kept as two separate elements specifically
  so the position offset and the rotation/scale don't get tangled into one
  ambiguous combined transform) — "grows diagonally" without tipping off
  its own ground point.
All four values come from the SAME per-book hash already used for species,
so a given book lands in roughly the same spot every render (no reshuffling
on every Home re-render) — position is exactly as deterministic as species
already was, just newly load-bearing for layout instead of only cosmetics.
Tree box size itself also shrunk (44×120 viewBox rendered at 26×71px, was
42×96) to help more of them read as a treeline rather than a crowd.
`.forest-empty` (onboarding) got pulled out to its own `display:flex;
height:auto` rule, since the fixed-height canvas rule is specifically for
the tree-bearing case.

**Verified the "always fits" claim directly, not just visually**: rendered
25 synthetic in-progress books (previously this would have wrapped to
several rows) — `.forest` still measures exactly 92px tall, ZERO tree
buttons' bounding rects exceed the card's own bounds (checked
programmatically, not eyeballed), and Home's total scroll height still
equals the viewport height (`812 <= 812`) at both 3 books and 25.

**Critter flight — "just dragged across the screen... no flying motion,"
constant speed.** Genuinely accurate complaint about the first version:
one `linear`-timed animation with only 3 bob points is, mechanically, a
drag with a slight wobble, not flight. Two independent fixes, not one:
1. **Speed actually varies now.** Each keyframe segment carries its own
   real `animation-timing-function` (`ease-out` → `ease-in-out` ×3 →
   `ease-in` ×2 — confirmed by reading the parsed `@keyframes` rule back
   out of `document.styleSheets`, not assumed from the CSS text), AND the
   horizontal-distance FRACTION at each stop is deliberately offset from
   that stop's time-percentage (4% distance at 10% time / 20% at 28% / 50%
   at 50% / 80% at 72% / 96% at 90%) — slow to leave, a faster mid-flight
   surge, slowing to land. Combined, ground speed visibly changes through
   the flight rather than reading as one constant rate.
2. **A second, independent animation for the wingbeat itself** — the inner
   `<svg>` runs its own fast (0.34s), continuously-looping `scaleY` pulse,
   completely decoupled from the outer div's flight-path animation (two
   separate elements, two separate `animation` properties — CSS allows a
   parent and child to animate independently with no conflict). This is
   what actually reads as "flying" rather than "gliding along a wire": the
   wings visibly beat throughout, independent of whatever the flight path
   is doing at that instant.
Also went from 3 to 5 vertical bob points and added 5 independent small
rotation-wobble values (a slight nose-up/down per stop) for a genuinely
wavier, less mechanical path — all still randomized per spawn in JS exactly
like the bob values already were, just more of them.

**Verification**: syntax-checked; confirmed (via `document.styleSheets`)
that all 6 `forest-fly-ltr` segments carry distinct real timing-functions,
not a single global curve; confirmed all 5 dy/rot custom properties are set
per spawn; confirmed the inner `<svg>`'s `forest-flutter` animation runs
independently of the outer element's flight animation (different
`animationName` on each, both present simultaneously). Zero console
errors. Nothing pushed. **Not device-verified.**

## Bird wing-hinge, trees reverted to vertical, butterfly/bee hover-pause (2026-08-27, same day)

Three-part owner correction on the critter/tree work above: "The bird is
not even flying! Let's make the bird flap its wings. Also, the trees
should still be vertical. And let's make the butterfly and bee sometimes
stop here and there (while their wings still flap)."

**1. Bird wing-flap.** The previous fix's whole-`<svg>` `scaleY` pulse
reads fine on the butterfly/bee (their bodies are roughly round, so a
squash plausibly reads as wings compressing) but does nothing convincing
for the bird — a static double-arc silhouette, where squashing the WHOLE
shape doesn't look like anything specific flapping. Rebuilt the bird's SVG
into two independent parts: `<g class="wing-l">` (one arc, pivoting at
`(10,13)`) and `<g class="wing-r">` (the mirrored arc, same pivot), each
with its own `transform-origin: 10px 13px` and its own keyframe animation
(`bird-wing-l`/`bird-wing-r`, `0.3s ease-in-out infinite`, both wings
rotating together — a real bird's wings move in sync, not alternating).
The generic `scaleY` flutter (`forest-flutter`) is now scoped to
`.forest-critter.butterfly svg, .forest-critter.bee svg` only, so the bird
no longer gets a redundant/conflicting whole-body squash on top of its new
wing hinges.

**2. Trees reverted to vertical.** The prior round's entry read "grow
diagonally from other trees" as license to tilt each trunk (±8° via
`rotate()` in the tree button's inline transform) — the owner's correction
clarified that "diagonal" meant trees' POSITIONS relative to each other
(near/behind, allowed to bundle up at different depths), not the trunks
themselves tilting, which just looked like the trees were falling over.
Deleted the `lean` variable and the `rotate(${lean}deg)` term from the
button's inline style entirely — `Forest.render()`'s per-tree markup is
now `transform: scale(${perspective})` only. The semi-random depth/x
positioning from the previous round (which IS what "diagonal"/"bundle up"
meant) is untouched.

**3. Butterfly/bee hover-pause.** New mechanism: the flight keyframes
(`forest-fly-ltr`/`-rtl`) already moved through 5 checkpoints at fixed
time-percentages (10/28/50/72/90%) with a horizontal-distance FRACTION at
each one (previously hardcoded literals: 0.04/0.20/0.50/0.80/0.96). Those
literals became `var(--fly-fx1, 0.04)` through `var(--fly-fx5, 0.96)` — a
per-spawn CSS custom property with the old value as its fallback, so a
plain spawn (or a bird, which never gets these set) behaves exactly as
before. In `_spawnCritter()`, for a butterfly or bee only (never bird, by
name check — birds don't hover), there's now a ~55% chance of picking one
random adjacent pair of the 5 fx values and setting them equal — freezing
horizontal progress across that whole time segment, i.e. a real mid-air
pause at a random point in the flight. `--fly-dy`/`--fly-rot` for that same
stop still get their own small random values, so the critter bobs gently
in place during the pause rather than going perfectly rigid, and because
the wingbeat (`forest-flutter`, or the bird's two wing keyframes) is a
fully separate animation on a child element, wings never stop moving
during a pause — exactly the "stop here and there while their wings still
flap" ask. A paused flight also gets a slightly longer total duration
(9–13s vs. 7–11s), since freezing part of the horizontal budget would
otherwise force the non-paused portion to move faster to compensate,
undercutting the "unhurried hover" feel.

**Also found and fixed while verifying, not part of the original ask**:
`_critterShapes` had to change shape anyway (from 3 plain HTML strings to
`{name, svg}` objects, so CSS can key the wing/flutter rules and the pause
eligibility check off a real name instead of array position) — while
rewriting `_spawnCritter()` around that, noticed `Home.render()` is called
from many places completely unrelated to the forest (a book's cover
finishing its async load among them — `Covers._one()`/`fromBook()` both
call it once their image is ready), and every one of those calls replaces
`#home-forest`'s ENTIRE innerHTML via `Forest.render()`. That silently
detaches a mid-flight critter's `<div>` from the DOM without ever going
through `stopCritters()` — so `Forest._critterEl` was left pointing at a
now-dead, detached node. Since `_spawnCritter()`'s one-at-a-time guard was
a plain `if (this._critterEl) return`, that stale non-null reference meant
no critter could EVER spawn again for the rest of that Home visit, not
just "wait its turn" — a real, silent reliability bug in the very feature
this round is polishing. Fixed by checking `this._critterEl.isConnected`
instead of just truthiness, and clearing the stale reference when it's
found detached.

**Verification**: syntax-checked. In-browser with mocked `State.books`/
`State.progress` (no real sign-in in this sandbox) and `Math.random`
temporarily monkey-patched to pin outcomes deterministically:
- Rendered 4 trees at varied progress (35/72/100/10%) and read every
  `.forest-tree-btn`'s inline `style` back — all four are `scale(...)`
  only, zero `rotate()` present.
- Forced a bird spawn: element gets class `forest-critter bird ltr`, both
  `.wing-l`/`.wing-r` groups exist, and its `--fly-fx1..5` are the plain
  unfrozen default curve (0.04/0.20/0.50/0.80/0.96) — confirmed a bird is
  never eligible for the pause branch.
- Forced a butterfly spawn with a rigged pause: `--fly-fx1`/`--fly-fx2`
  came back equal (0.040/0.040, the frozen pair), animation duration
  bumped to the paused-flight range (11s), and the butterfly's `<svg>`
  independently confirmed still running `forest-flutter` via
  `getComputedStyle(...).animationName` — i.e. the freeze is real (a whole
  segment's horizontal position pinned) and the wingbeat keeps going
  through it, exactly as designed.
- Confirmed the `_critterEl.isConnected` fix compiles and the guard logic
  is sound; did not attempt to reproduce the original race under real
  timing (would need a live, un-mocked cover-load sequence), so treat it
  as logically verified rather than reproduced-then-fixed.
Zero console errors beyond the deliberately-mocked environment's own
missing-network noise. Nothing pushed; `index.html` untouched. **Not
device-verified.**

## Erase-modal confirmation word: localized + matched to its own button (2026-08-27, same day)

Owner report: "when I try to delete all my data while in French mode, the
word to type is still ERASE which is not a french word. Also, let's align
to the button that we just tapped to get there. In english it's DELETE,
and in french, it should be SUPPRIMER."

**Root cause**: `EraseModal`'s type-to-confirm word was a plain hardcoded
literal — `<b>ERASE</b>` in the markup and `!== 'ERASE'` in
`checkInput()`'s JS gate — with no `data-i18n` hook at all, so it never
changed with the app language. It also never matched the Settings row
button that opens the modal (`data-i18n="delete"`, EN "Delete" / FR
"Supprimer") — the two were picked independently and happened to diverge
even in English (button says "Delete", modal asked for "ERASE").

**Fix**: new i18n key `erase_word` — `'DELETE'` (EN) / `'SUPPRIMER'` (FR),
deliberately the uppercase form of the existing `delete` key's value in
each language, not a new independent translation, so the two can't drift
apart again by accident. The markup's `<b>ERASE</b>` became
`<b data-i18n="erase_word">DELETE</b>` (so `I18n.setLang()`'s existing
`apply()` pass updates it for free, same as every other `data-i18n` spot),
and `checkInput()`'s comparison became
`input.value.trim().toUpperCase() !== I18n.t('erase_word')` — reads the
current language's word at check-time rather than a frozen constant.

**Also fixed while in there**: the input's `aria-label` was static English
("Type ERASE to confirm") with no translation mechanism — there's no
existing `data-i18n-aria-label` convention in this codebase (`I18n.apply()`
only walks `[data-i18n]`/`[data-i18n-title]`/`[data-i18n-placeholder]`,
confirmed by reading `apply()` directly rather than assumed), so a one-off
JS-side fix was used instead of inventing a new markup convention for a
single input: new `erase_input_aria` key, set directly via
`input.setAttribute('aria-label', I18n.t('erase_input_aria'))` in
`EraseModal.open()` — matches the codebase's own documented convention of
"direct `I18n.t()` calls for JS-templated content" for exactly this kind
of one-off case.

**Verification**: syntax-checked. In-browser, mocked signed-in state,
called `EraseModal.open()` directly in each language:
- English: bolded word reads "DELETE", aria-label reads "Type the
  confirmation word above", typing "delete" (lowercase) enables the
  confirm button (case-insensitive, unchanged behavior).
- French: bolded word reads "SUPPRIMER", aria-label reads "Tapez le mot de
  confirmation ci-dessus", typing "supprimer" enables the button, and
  typing the OLD English word "DELETE" no longer works (button stays
  disabled) — confirms the check is genuinely language-aware, not just
  the displayed hint text being wrong while the gate silently still
  accepted the old word.
Nothing pushed; `index.html` untouched. **Not device-verified.**

## Tour spotlight bleed + more space under the logo/wordmark (2026-08-27, same day)

Two small owner-reported items from a screenshot of the onboarding tour.

**1. Tour spotlight revealing "the time from the Now page."** `#tour-hole`
is genuinely transparent — the dimming is a `box-shadow: 0 0 0 9999px
rgba(0,0,0,0.7)` around it, not a darkened box itself (confirmed by
re-reading its own CSS comment) — so whatever real content sits inside the
hole's computed rectangle shows through completely undimmed.
`_reposition()` built that rectangle as `target.getBoundingClientRect()`
padded outward by a flat 8px on every side, with no upper bound. The tab
bar sits flush against Home's scrollable content with no gap, and
`align-items: stretch` makes each `.tab` exactly as tall as `.tab-bar`'s
own content box — so the 8px of pad ABOVE a tab pushed the hole's top edge
8px past the tab bar's own (opaque) top edge, into whatever real Home
content happens to be scrolled right up against it. Fixed by clamping the
hole to the target's own parent's bounding rect (`el.parentElement`,
i.e. the tab bar itself for a tab, or a reader toolbar row for a reader-
tour step) on all four sides — the pad can still round the corners out
nicely when there's room, but can never cross the edge of the opaque bar
the target lives in. Verified: with a 46px-tall tab bar and a tab that
exactly fills it, the computed hole is now clamped to `top: 766px, height:
46px` — identical to the tab bar's own rect — where before the same setup
produced `top: 758px, height: 62px`, 8px into the page above.

**2. More space under the logo + wordmark.** `.app-header`'s
`margin-bottom` (shared by Home/Shelves/Log/Settings — the one rule
controlling the gap between the leaf-icon+"PhonoLeaf" row and whatever
follows on each page) went from 1.5rem to 2.1rem, +0.6rem, the same size
step as the round-3 bump that first took it from 0.9rem to 1.5rem. Applied
identically to all four `.green.html` legal pages' `.topbar` rule (same
value by design — its own comment already says it's matched to
`.app-header` so the logo lands at the same on-screen spot everywhere).

**Verification**: syntax-checked. In-browser at 375×812 with a realistic
mocked Home (5 books at varied progress, a 5-tile streak/stats state, a
populated forest): `.app-header`'s computed `margin-bottom` reads 33.6px
(2.1rem) on Home and on Library; Home's `.scrolly` shows zero scroll
overflow (`scrollHeight === clientHeight === 812`) even with the added
space, so it still fits one screen — same for Log/Stats. Settings scrolls
as it already did (that page was never a one-screen-fit target). Loaded
`privacy.green.html` directly: `.topbar`'s margin-bottom also reads 33.6px
and the gap to the `<h1>` measures the same 33.6px in practice — confirms
the two files' independent CSS didn't drift apart. Nothing pushed;
`index.html`/`privacy.html`/`terms.html` untouched. **Not
device-verified.**

## Forest copy/color, em-dash sweep, and page-title alignment to Home's name (2026-08-27, same day)

Four owner requests in one message.

**1. Forest empty-state copy.** "I think the forest should say 'Start
reading a book to grow your first tree!' if no book has been started" —
swapped in verbatim (EN), with a French line built the same way rather
than translated word-for-word from the old one: "Commencez un livre pour
faire pousser votre premier arbre !" Both name the actual action (read/
start a book) and the actual payoff (a tree grows), where the old copy's
"unlock your forest" implied something being gated rather than grown.

**2. Colored sun/moon.** `.forest-sky`'s two icons inherited `color:
var(--text-dim)` — the same flat grey as every other dim UI label, so a
"sun" and a "moon" only read as such from their outlines, not their color.
Gave each its own explicit color instead of the ambient token: `#E4A63B`
(warm gold) for the sun, `#AEB9D6` (cool silver-blue) for the moon, opacity
bumped from 0.55 to 0.75 so the color actually reads at 14px. Neither
needs a per-theme variant — the sun only ever shows in light mode, the
moon only in dark, so each gets exactly one color, not a light/dark pair.

**3. Em-dash sweep.** "Please review all text and make sure it doesn't
look like an AI wrote the text" — read as, at minimum, acting on standing
feedback (recorded in memory, raised twice before this session) that the
em dash specifically reads as an AI tell in user-facing copy. Grepped
`STRINGS.en`/`STRINGS.fr` end to end: ~60 lines used an em dash, almost
always the same pattern (`"X — Y"` where Y is a consequence or elaboration
of X). Rewrote each on its own terms rather than a single mechanical
substitution — most became two sentences (`"X. Y"`), a few became one
sentence joined with a comma or "so"/"donc" where the clauses are too
tightly linked to split, one pair became a colon (`"On: Upgraded voice..."`)
where it's genuinely a label. Also fixed 4 HTML fallback strings that
duplicate STRINGS.en text inline in the markup (the sign-in screen's two
step captions, its privacy paragraph, and the erase-modal hint) so the
pre-i18n-apply() text matches. Deliberately did NOT touch code comments —
this codebase's own commenting style leans on em dashes constantly (CLAUDE.md
and CLAUDE_HISTORY.md included), and comments aren't user-facing; rewriting
hundreds of them would be a large, disruptive, out-of-scope change nobody
asked for. Used a small Node script (pairs of exact old/new literals, each
checked for exactly one match before applying) rather than 60+ individual
tool calls — one string's French apostrophe-before-"?" used a real
non-breaking space (`U+00A0`, correct French typography, already present
in the source) that a plain-space search string didn't match; caught by
the script's own "expected exactly 1 occurrence" check and fixed
separately with the exact byte sequence.

**Also checked for other common AI-writing tells** ("unlock", "seamless",
"leverage", "elevate", "delve", "empower", "unleash", "effortless",
"cutting-edge", "game-changer", "whether you're") across the same
dictionaries — none found beyond the one "unlock" already being replaced
per item 1 above.

**Flagged rather than changed**: `privacy.green.html`'s actual legal
clauses (and the real, live `privacy.html`) use the same em-dash-heavy
style throughout their substantive paragraphs — confirmed by grepping the
live `privacy.html` directly (16 em dashes in real clause text, predating
this session). That's legal content pending lawyer review, carried over
verbatim per this project's "legal text unchanged" rule — rewriting actual
clause wording for style is a bigger, separate decision (risk of subtly
shifting a sentence's meaning in a document someone else is about to
review) than the UI-copy sweep above, so it was left alone and raised with
the owner instead of edited unilaterally.

**4. Every page title aligned to Home's own name position.** "Let's put
all headers at the same position that the name in the Now page is." Home's
title is genuinely two lines — a small kicker ("Good evening") directly
above the user's actual name, the bigger serif text underneath — and every
other page's title (Shelves, Log, Settings, and both Privacy/Terms's `<h1>`)
was landing at the KICKER's height, one line above where Home's name
itself sits (measured: kicker top 76.4px, name top 92.1px, everything else
at 76.4px). Not what any earlier "alignment" pass in this session's history
was checking for — those confirmed the OTHER pages agreed with EACH OTHER,
never against Home's actual name.

Fixing this the obvious way — bumping `margin-top` on the shared
`.home-title` class and the legal pages' `<h1>` — did nothing at first,
and the reason is a genuine CSS gotcha worth recording: `.app-header` and
a bare `.home-title` (Settings/Log) are plain block-level siblings inside
`.scrolly`, and so are `.topbar` and `<h1>` inside the legal pages' `.wrap`.
Adjacent block siblings' vertical margins COLLAPSE — the browser keeps
only the LARGER of the two touching margins, it does not sum them. Since
`.app-header`'s own margin-bottom (2.1rem, from the earlier fix this same
day) is already bigger than any reasonable title margin-top, every
increase to the title's margin-top was being silently absorbed with zero
visible effect — confirmed by reading `getBoundingClientRect().top` before
and after and seeing it not move at all. Padding never collapses, so the
fix is `padding-top` instead of `margin-top` on `.home-title` (bare
use) and on each legal page's `<h1>` — exactly `0.98rem`, the raw
kicker-to-name gap on Home, not "0.98rem plus whatever the old margin
was," since that old margin was already contributing nothing once
collapsed away. `.lib-header` (Shelves) took the same padding-top instead,
added to its own top since it had none before. Home's own two-line title
was untouched (its kicker→name gap already used `margin-top: 0.15rem`
inside `.home-greet-block`, a different, non-colliding context, switched
to `padding-top` too for consistency but functionally already correct).

**Verification**: syntax-checked. In-browser, mocked a 5-book Home with
real progress/streak data plus the four other views, measuring each
title's EFFECTIVE top (`getBoundingClientRect().top + computed
padding-top`, since a padding-based fix moves the text but not the box's
own outer edge — box-top alone would have under-reported the fix as a
false regression, caught and corrected mid-verification): Home 92.12px,
Shelves 92.06px, Settings 92.07px, Log 92.07px — all within 0.06px of each
other. Both legal pages (privacy.green.html, terms.green.html) independently
measured at 92.07px using the same method. Home's `.scrolly` still shows
zero scroll overflow with the added space. Nothing pushed;
`index.html`/`privacy.html`/`terms.html` untouched. **Not
device-verified.**

## Page-title alignment, round 2: unify the font-size, not just the position (2026-08-27, same day)

Owner report: "The headers are not quite aligned with the name. When I
switch tabs, I still see a difference. Maybe align to bottom?"

The previous fix (same session, entry above) made every title's BOX land
at the same y-position, verified by measuring `getBoundingClientRect()`
on the box itself, all five within 0.06px. That measurement was real but
incomplete: it checked where the box started, not where the actual
glyphs render — re-measured this round with `Range.selectNodeContents()
.getBoundingClientRect()` (the true rendered-text bounding box, not the
CSS box) and found Home's title glyphs spanned 87.4–128.8px while every
other page's spanned 87.4–126.1px. Tops matched almost exactly (0.05px);
bottoms differed by 2.7px. Cause: Home's name has carried a deliberate
"hair larger than a plain title" font-size (28px vs 26px) since an early
design-file-matching round in this same session — with box-tops aligned,
a bigger font's glyphs are simply a bigger shape occupying more vertical
room below that same starting point, which reads as "not quite aligned"
even though the top-left corner is genuinely identical. This is also
consistent with the owner's own "maybe align to bottom" guess — aligning
by the bottom instead of the top wouldn't have fixed it either, just
moved which edge looked off, since two different font-sizes can share
at most one edge (top OR bottom OR center), never both, without an actual
per-size baseline calculation.

**Fix**: dropped the 28px override entirely. `.home-greet-block
.home-title` no longer sets `font-size` at all, so Home's name now
inherits the exact same `1.625rem/1.15` (26px) as the shared `.home-title`
base rule every other page's title already uses — genuinely the same
font, same size, same line-height, same box, everywhere. This removes the
last variable that could differ between "kicker + title" (Home) and
"title alone" (everywhere else) beyond simple, render-engine-independent
padding math, which is not something that should ever render
inconsistently across devices/browsers the way font-glyph-within-line-box
positioning subtly can.

**Verification**: syntax-checked. In-browser, `Range`-based glyph
measurement (not just box measurement, per the lesson above) on Home,
Shelves, Settings, Log, and both `privacy.green.html`/`terms.green.html`:
all five now report `top: 87.40±0.05px` AND `bottom: 126.06±0.05px` —
matching on BOTH edges, not just one, confirming the two title types are
now genuinely identical shapes, not just identically-positioned different
ones. Home's `.scrolly` still shows zero scroll overflow with a realistic
5-book mocked dataset, so the one-screen-fit requirement holds. Nothing
pushed; `index.html`/`privacy.html`/`terms.html` untouched. **Still not
device-verified — this fix specifically targets a font-rendering-detail
complaint that could not be reproduced in this desktop-browser sandbox
(measured 0.05px, effectively exact, both before and after — the earlier
fix "should" have already looked right by every number available here),
so on-device confirmation matters more than usual for this one.**

## Book Detail swipe-back, Listen→reader+play, Read button removed, new mini player bar (2026-08-27, same day)

Four related requests in one message about the reading-session flow.

**1. Swipe-back closing the wrong thing.** Owner: "when the Book Detail
page is up and I try to swipe from left to right (to go back), the Book
Detail page stays open and it's the page in the background that changes."
Cause: `BookDetail.open()`/`close()` were pure CSS class toggles with no
history entry of their own — a real OS back-swipe fires a `popstate` for
whatever the actual top of the browser history stack is (the previous
TAB), which the app's own `popstate` handler dutifully processed (`Nav.go`
to that tab), while the modal, having never registered itself in the
stack, just sat there on top, unaffected. Fixed the same way `Reader`
already handles its own full-screen entry: `open()` now calls
`history.pushState({app:'modal', modal:'detail'}, '')`, and the global
`popstate` handler checks for `#detail-modal.open` FIRST, before its
existing reader/tab logic — closing the sheet is the entire effect, since
the tab underneath was never touched by opening the sheet in the first
place. A click-driven `close()` (backdrop tap, or any of the sheet's own
action buttons closing it programmatically) now also consumes that
pushed entry via a guarded `history.back()` with a `_skipPop` flag —
identical pattern to `Reader.back()`'s own comment about why a plain
`history.back()` from a click can't be trusted alone in the native
WebView. **Scoped to Book Detail only** — every other modal in the app
(Sleep, Export, Voice, Chapter, Erase, Confirm, folder pickers, etc., 17
in total) has this exact same latent gap, but only Book Detail was
reported, and generalizing the fix to all of them risked touching working
code nobody asked about; flagging it here as a known, not-yet-fixed class
of bug for if it's reported again elsewhere.

**2. Listen → open the reader AND start reading, Read button removed.**
Already true functionally — `BookDetail.startListening()` has called
`Reader.open(i)` (mode 'full', which arms `_autoStartBook` unconditionally)
since it was written — but the button read "Start listening"/"Commencer
l'écoute", and the owner referred to it simply as "the Listen (or Écouter)
button," which is also a real simplification now that it's the sheet's
ONLY action: renamed the `start_listening` string to "Listen"/"Écouter"
(the "Continue"/"Continuer" in-progress variant is unchanged). Removed the
"Read" button (opened the reader without audio) and its handler,
`BookDetail.read()`, along with the now-fully-unused `.detail-secondary`
CSS rule and `read_label` string pair — `.detail-primary` is already
`flex:1`, so the sheet's one remaining button fills the row on its own
with no other CSS change needed.

**3. New `MiniPlayer` — a persistent Now-Playing bar on every tab.** Owner:
"When a book is playing, and the user is not on the reader itself, a small
version of the reader's bottom overlay should appear at the bottom of
every page (but above the tab names)," with exactly five controls: play/
pause, speed, voice, sleep timer, and previous/next chapter — no title or
cover art, matching the request as given rather than adding one. New
`#mini-player` element, a sibling of `#tab-bar` (outside every `.view`, so
it never needs re-rendering when the active tab changes — it just IS or
ISN'T visible), fixed at `bottom: calc(46px + var(--safe-b))` (46px being
the tab bar's own measured height — no existing CSS variable exposed that,
so it's a second hardcoded copy of the same number, flagged in a comment).

Visibility is a pure function of `#reader-view`'s own `minimized` class —
already the single source of truth for "a background reading session
exists," set the moment `Player.play()` opens a book in `'mini'` mode or
`Reader.minimize()` backs out of the full reader, and cleared only by
`Reader.expand()`. New `MiniPlayer.sync()` reads that class plus
`TTS.active` and the current rate, and is called from every place that
already flips play/pause state or the `minimized` class: `Reader.open()`,
`Reader.minimize()`, `Reader.expand()`, `TTS.start()`, `TTS.stop()`,
`TTS.skipPage()`, and `TTS._bgNav`'s active-flip — six call sites, each
getting one added line rather than a new observer/polling mechanism.
Tapping the bar itself (anywhere that isn't one of its own controls, each
of which calls `event.stopPropagation()`) calls `Reader.expand()` — NOT
`Reader.open()`, which would re-download and reset a session that's
already loaded and playing.

The play/pause button reuses `.rc-play`'s own pure-CSS pseudo-element
trick (`::before`/`::after` redrawing a triangle vs. two bars off a
`.playing` class) at a smaller size, ink-colored instead of white-on-
filled-circle since this bar sits on `--surface`, not an accent-filled
button. The speed control is a second, fully independent `<select>` node
(same `.speed-select` class, own id `#mp-speed-select`) — `TTS.setRate(v)`
now does `document.querySelectorAll('.speed-select').forEach(...)` so
every instance (reader, mini player) stays in agreement regardless of
which one the user actually touched, instead of the previous single-
element sync that only the reader's own select benefited from.

**Verification**: syntax-checked (twice — an early edit dropped the
closing `},` off `Reader.expand()` while inserting a new line into it,
caught immediately by the same syntax-check rather than left for a later
surprise). In-browser: `BookDetail.open()` pushes `{app:'modal',
modal:'detail'}` onto `history.state`; simulating a real back-swipe
(`history.back()`) closes the sheet and leaves `Nav.current` on whatever
tab it already was — the reported bug, reproduced-then-fixed rather than
fixed blind. Book Detail's action row now renders exactly one button,
reading "Listen"/"Continue" correctly by progress. `MiniPlayer`: toggling
`#reader-view`'s `minimized` class and calling `sync()` shows the bar at
`bottom` flush against the tab bar's own top edge (measured gap:
~0.00004px, i.e. exact); switching Home→Shelves→Settings→Log with the bar
shown confirms it stays visible on all four without any per-tab wiring;
flipping `TTS.active` toggles the play/pause icon; `TTS.setRate(1.5)`
updates both the reader's and the mini player's `<select>` to `"1.5"`;
clicking `#mini-player` promotes to the full reader (`active` class set,
bar hides); `Reader.minimize()` brings it back; clicking `#mp-play-btn`
directly does NOT also trigger the expand (event bubbling correctly
stopped). All 6 mini-player controls together measured ~206px of content
in a 375px-wide bar — comfortable margin, no crowding. Nothing pushed;
`index.html` untouched. **Not device-verified**, and real audio playback
specifically couldn't be exercised in this sandbox (no live Drive/TTS) —
every check above used `TTS.active`/`reader-view` class state set directly
rather than a real play press, so the actual `TTS.toggle()` → audio path
from the mini player's own play button is unverified beyond "it calls the
right function and doesn't crash."

## Tour spotlight vs. a modal opening mid-tour, and equal-width cover-overlay buttons (2026-08-27, same day)

Owner sent a screenshot: the tour's spotlight box had turned solid black,
with no visible tab underneath it, while a "Language Packs" sheet (voice-
pack onboarding) was clearly showing on screen at the same time.

**Root cause.** `Tour.maybeStartHomeTour()` polls every 800ms and only
starts once `document.querySelector('.modal-backdrop.open,
.confirm-backdrop.open')` finds nothing open — a real check, but a
one-time one, taken right before `start()` runs. `VoicePacks.maybeOnboard()`
(which opens `LangPacksModal`) has no single moment anything in the app
awaits before it can appear — CLAUDE.md's own note on this ("no single
'initial setup finished' callback exists to hook... can finish at any
unpredictable time") already flags exactly this kind of race. So the
sequence that produced the screenshot: the tour's poll finds nothing open,
starts, shows its first step's spotlight around the Shelves tab — and
moments later LangPacksModal opens on top anyway. `#tour-hole` is a
genuinely transparent element (dimming is an oversized `box-shadow`
around it, not the box itself, per its own CSS comment from an earlier
round) — so the "hole" faithfully reveals whatever real pixels sit at that
screen location, which was now LangPacksModal's own opaque sheet
background, not the tab bar underneath it. Black box, invisible target,
exactly as reported.

**Fix: watch, don't just check once.** New `Tour._watchModals()`, called
once from `start()`: attaches a `MutationObserver` to every
`.modal-backdrop`/`.confirm-backdrop` element in the app (17 of them),
each watching only its own `class` attribute. If any of them gains `.open`
while a tour's `_steps` array is non-empty, the tour PAUSES — overlay
hidden, nothing else touched — rather than trying to keep rendering a
spotlight that's now meaningless. When that same element loses `.open`
again, the tour RESUMES: overlay shown, `_reposition()` re-run (in case
whatever's underneath shifted while paused, e.g. a different tab was
reachable behind the modal). This is deliberately reactive rather than a
tighter one-time check — there's no single moment to check against when
nothing awaits every onboarding modal's own open, so the fix has to keep
watching for as long as a tour could plausibly still be showing.

**A real bug the fix would have made worse, caught while building it.**
`Tour._end()` removed the overlay's `.show` class but never cleared
`this._steps` — harmless in the original code, since nothing outside an
active display cycle ever re-read `_steps.length`. The new modal-watcher
does, for the rest of the app's entire lifetime (any of the 17 modals
could open hours after a tour finished), so a stale non-empty `_steps`
would have made some LATER, completely unrelated modal (Export, Sleep,
whatever) incorrectly toggle the long-since-hidden tour overlay's class
back on and off. `_end()` now clears both `_steps` and `_paused`.

**Also fixed, same message:** "let's keep the Play and Details buttons the
same size as each other" — `.cr-play`/`.cr-details` (the tap-to-reveal
Play/Details overlay on a library cover) shared only a base font/padding
rule; their actual widths were auto, sized to each button's own text —
"Play" (4 chars) vs "Details" (7 chars) in English reads as two visibly
different pill sizes stacked directly on top of each other. Added a
shared `min-width: 4.6rem` + `text-align: center` to both (not
`.cr-finish`, the third action underneath them — a lesser, text-link-
styled action, never asked to match these two).

**Verification**: syntax-checked. In-browser: started a Home tour, then
(mimicking the reported race) added `.open` to `#langpacks-modal` directly
— confirmed the tour overlay hides and `Tour._paused` becomes `true`
(after letting the `MutationObserver`'s microtask actually run — a
synchronous check right after the `classList.add` call is too early and
was a red herring during testing, a real callback tick is normal and
matches how this fires in the live app too); removing `.open` again
confirmed the overlay reappears, `_paused` returns to `false`, and the
spotlight hole is correctly repositioned over the Shelves tab (measured:
flush against the tab bar, matching the earlier tab-bar-clamp fix).
Separately confirmed the stale-`_steps` fix: ended a tour normally
(`Tour.skip()`), confirmed `_steps.length === 0`, then opened and closed
an unrelated modal (`#export-modal`) — the tour overlay never toggled at
all. `.cr-play`/`.cr-details` both measured exactly 73.59px wide in both
English ("Play"/"Details") and French ("Écouter"/"Détails") — identical
to the sub-pixel, neither language's label overflowing the shared
min-width. Nothing pushed; `index.html` untouched. **Not device-verified.**

## Play opens the full reader, explicit expand icon, mini-player speed restyle, Privacy dark box darkened (2026-08-27, same day)

Four related requests, following on directly from the mini-player work
above.

**1. Play didn't open the reader page.** Owner: "I just started a book by
clicking Play, but it didn't open the reader page, it only created a
hero." Root cause: `Player.play(i)` (the cover overlay's Play button) has
always called `Reader.open(i, 'mini')` — laid-out-but-hidden mode, which
is exactly what produces "a hero" (the mini player) with no visible reader
page. That mode made sense for the OLD hero card this session already
removed (Home used to have its own inline play/pause without navigating
away), but with that gone, nothing about tapping Play on a cover should
still open silently in the background — the owner wants it to behave like
Book Detail's Listen button (open the reader, start reading). Changed
`Player.play(i)` to call `Reader.open(i)` (full mode, default — arms
auto-start the same way Listen already does) for a book that isn't
already loaded; for one that IS already loaded (a live background
session), it now resumes playback in place AND promotes to the full
reader via `Reader.expand()` (not a re-open, which would reload a session
already in progress) unless the full reader is already showing. Left
`Reader.open()`'s `'mini'` mode branch itself in place rather than
deleting it — it's still exactly how a session STAYS laid out once
`Reader.minimize()` backs out of the full reader, just no longer how one
GETS STARTED — and it's genuinely nothing calls with `mode: 'mini'`
anymore, confirmed by grepping for it. Also removed `Player.toggle()`/
`Player.expand()`, both entirely dead — leftover from that same removed
hero card, zero callers anywhere in the file (confirmed by grep before
deleting, not assumed).

**2. Explicit "open reader" icon on the mini player.** "We should also add
an icon on the hero to open the reader again." Tapping anywhere on the bar
already expanded to the full reader (built in the previous round), but
that wasn't visible/discoverable as its own affordance. Added a plain
up-chevron `.mp-expand` button at the end of the row, same
`event.stopPropagation()` + `MiniPlayer.expand()` pattern as every other
control in the bar (functionally redundant with the whole-bar tap, since
`Reader.expand()` is idempotent, but consistent with how every other
mini-player control is built, and it's the discoverable one now). New
`open_reader` i18n key for its title/aria-label.

**3. Mini-player speed control's clashing double-line look.** Owner's own
screenshot showed the reader's usual underline-style speed `<select>`
sitting directly above the tab bar's active-tab green top-border — two
thin accent-colored lines a few px apart, described exactly as it reads:
"it looks weird with two lines." `.mp-speed` (kept alongside the shared
`.speed-select` class, so `TTS.setRate()`'s existing
`querySelectorAll('.speed-select')` sync still reaches it) now gets its
own later, higher-`border`-covering rule: a bordered pill instead of an
underline, matching the Voice button sitting right next to it, so the two
read as one row of contained controls rather than a control plus a stray
accent line.

**4. Privacy's dark-mode "short version" box, too bright.** `.summary-box`
fills with `var(--accent)` — deliberately DARK green in light mode (a
correct "inverted" highlight box: dark fill, pale text) but dark mode's
`--accent` is the BRIGHT light green meant for text sitting on a near-
black page, not a fill color; used as one, it read as glaring next to
everything else in dark mode being muted/near-black. Gave dark mode a
dedicated deep-green fill (`#1E4534`) with the theme's own light `--text`
token instead of `--bg` (near-black — would've gone invisible against a
dark fill).

**A real miss on the first attempt, caught while verifying, not left
in.** The first version only added `@media (prefers-color-scheme: dark)`
and `[data-theme="dark"]` overrides. Tested each theme state in turn and
found `[data-theme="light"]` (forced Light while the OS itself reports
dark, exactly this sandbox's own default) STILL rendered the new dark
fill — the bare `@media` rule has equal specificity to the base
`.summary-box` rule and nothing was in place to beat it back for the
forced-light case specifically, so it kept winning regardless of the
explicit override. Root cause was under-applying a pattern this exact
file's own token blocks already use correctly one screen up: `@media` /
`[data-theme="dark"]` / `[data-theme="light"]` all need to exist together
at matching specificity for a forced choice to reliably beat the OS
preference in both directions, not just one. Added the missing
`[data-theme="light"] .summary-box` rule restating the plain
`var(--accent)` look.

**Verification**: syntax-checked. In-browser: `Player.play()` on a book
with no live session now sets `#reader-view`'s `active` class (not
`minimized`) and hides the tab bar — the reported bug, reproduced (mode
never changed) then confirmed fixed; for an already-loaded book, clicking
the mini player's new `.mp-expand` button correctly flips `active`
on/`minimized` off via `Reader.expand()`. `.mp-speed`'s computed
`border`/`border-bottom` now reads a dim `--line` box on all sides, vs the
reader's own `#speed-select` still showing its accent-colored underline
only — confirmed genuinely different treatments, not accidentally shared.
Privacy's `.summary-box` background measured across all three theme
states this round: `light` → `rgb(47,107,79)` (`#2F6B4F`, the original
light fill, unchanged), `dark` → `rgb(30,69,52)` (`#1E4534`, the new muted
fill), `auto` with the sandbox's own dark OS → also `#1E4534` (correct) —
and critically, forcing `light` while the OS itself stays dark now
correctly returns to `#2F6B4F` rather than staying stuck on the dark
fill, confirmed on both `privacy.green.html` and `privacy-fr.green.html`
independently. Nothing pushed; `index.html`/`privacy.html` untouched.
**Not device-verified.**

## Desktop column (Option B) + native/web split so Android can ship alone (2026-08-28)

Started as "let's push to prod before handing off," which surfaced two
things worth separating.

**The blocker, stated plainly.** The website and the Android app were built
from the SAME file (`index.html`), and pushing to `main` auto-deploys
Pages. So "ship the Android redesign but leave the website alone" was not
expressible as a git push at all. What made it solvable: the Android build
never comes from a push in the first place. It is `npm run sync` +
Android Studio + Play Console, entirely local. Only the STAGING script
decides what the native build contains.

**The split.** `scripts/stage-www.js` gained a single named constant,
`APP_SOURCE = 'index.green.html'`, and now copies that to `www/index.html`
instead of the repo-root `index.html`. It also stages the four `.green`
legal pages, because `index.green.html` links `privacy.green.html` /
`terms.green.html` by those exact names and each redirects to its own
`-fr.green` sibling off `pl_lang` (verified by reading the redirect, not
assumed — an early grep suggested the redesign linked the OLD French pages,
which turned out to be a match inside a code comment, not a real link).
The old `privacy.html`/`terms.html` stay staged too: `sw.js` precaches them
and the website's `index.html` still links them. Convergence is deliberately
a one-line change (`APP_SOURCE` back to `'index.html'`), documented at the
top of the script and in `CLAUDE.md`.

`stage-test.js` is now functionally identical to `stage-www.js`. Left in
place rather than deleted, because `CLAUDE.md` documents `npm run sync:test`
and silently breaking a documented command is worse than one redundant
file; both it and the redundancy are flagged for removal at convergence.

**Desktop layout: the actual finding.** Before touching anything, grepped
both index files for `min-width`. The ONLY hits in either were
`(min-width: 640px) and (max-height: 430px | 850px)` — short-viewport
sign-in tweaks for a landscape phone. **There was no desktop layout
anywhere in the app, and never had been.** This is not something the
redesign broke; phonoleaf.com has been rendering a phone layout stretched
across laptop screens for its entire life. Worth stating because the owner
framed it as a redesign problem, and it is not.

**Option B, chosen over a real desktop layout.** Large screens get the same
phone layout capped to a centred 480px column, hairline-edged. Roughly 20
lines of CSS, cannot drift out of sync with the phone layout because it IS
the phone layout, and it fixes the live website's pre-existing problem for
free. A true desktop layout (sidebar nav, wider grid, constrained reader)
was considered and deferred: the CSS is one inline block in a ~9,000-line
file, and a second full layout means every future change needs checking
twice. The library grid is the one screen that genuinely wants width (it
already has 2/3/4-per-row modes) and is the natural first candidate to let
past the column later.

Three implementation details that were not obvious:
1. `.tab-bar` and `.mini-player` are `position: fixed` with `left:0;
   right:0`. A `max-width` alone leaves them pinned to the viewport's left
   edge, so they need explicit re-anchoring (`left:50%; right:auto;
   transform: translateX(-50%)`).
2. Modal backdrops must stay full-bleed (dimming the whole page is
   correct) while the SHEET inside moves into the column — so
   `justify-content: center` on the backdrop plus `max-width` on the sheet,
   not a `max-width` on the backdrop.
3. **The breakpoint is gated on `min-height: 600px` as well as
   `min-width: 700px`, and the min-height is load-bearing.** A phone in
   landscape is wide (up to ~930px) but short, and must keep the full-bleed
   layout — width alone would have columned it. This is the same
   width+height pairing the sign-in screen's own landscape rules already
   use to tell "landscape phone" from "actual big screen".

Applied to **both** `index.green.html` and the live `index.html`. Touching
the live file is a deliberate exception to this session's otherwise strict
"green file only" rule: it is desktop-only CSS, so phone rendering is
provably unchanged, and it repairs an existing website bug. It is committed
to nothing — like everything else this session, it only reaches users on a
push the owner chooses to make.

**Verification** (real numbers, browser, both files syntax-checked plus
`sw.js`):
- 1440x900 — `#home-view`, `#tab-bar` and `#mini-player` each exactly
  480px wide at left 480 / right 960, i.e. centred on 720. All three agree.
- 932x430 (iPhone-15-Pro-Max landscape) — `max-width` computes to `none`,
  everything full-bleed at 932px. **The edge case the min-height gate
  exists for, confirmed working.**
- 768x1024 (portrait tablet) — column applied, 480px at left 144 / right
  624 (centred on 384).
- Book Detail sheet at 768 wide — sheet 144→624 (in the column), backdrop
  0→768 (full-bleed). Exactly the intended split.
- `npm run stage` run for real: `www/index.html` is byte-identical in size
  to `index.green.html` (545,733) and carries every redesign marker
  (`forest-tree`, `Forest`, `tab_shelves`, `EraseModal`,
  `id="mini-player"`); the repo-root `index.html` has **zero** of them and
  is still 424,722 bytes. The split does what it claims in both directions.

Nothing pushed. Screenshots were unavailable all session (the Browser pane
does not composite frames in this environment), so this is measurement-
verified, not eyeball-verified, and **not device-verified** — the column
never applies on a phone by construction, but the Android build itself has
still not been run since these changes.

## Two-branch reconciliation: hero archived, forest promoted to `main` (2026-08-28)

Two Claude sessions independently extended `index.green.html` from the same
base commit (`d1fc64e`) for days, with no visibility into each other —
`claude/docs-code-review-tam2pr` (this session, PR #4) built Phase 2/3 (the
motion/gesture token system, a localized accessibility pass, the storage
manager screen, in-book full-text search) on top of Phase 1, and never
touched the hero. `redesign/native-android-ship` (a different session, the
entry immediately above this one) built several more rounds of real
device-tested work — Home/Shelves/Player rebuild, sleep timer, ±15s skip, a
"forest" Home visualization replacing the hero, Book Detail fixes, a mini-
player bar, an i18n audit, the desktop-column CSS fix, and the native/web
build split (`stage-www.js`'s `APP_SOURCE` pointed at `index.green.html`).
The owner discovered the divergence when a native build (`npm run
sync:test`) surfaced the wrong version's UI and asked pointed questions
about it — not something either session caught on its own.

**Root cause, confirmed by reading `redesign/native-android-ship`'s own
history rather than guessed:** `index.green.html` had gone missing from the
working tree between sessions and was correctly recovered from
`www/index.html` (a byte-identical build artifact from a recent
`sync:test` run) — that recovery was sound, not the bug. The actual
problem was purely two sessions working the same file in parallel with
nothing forcing a check first.

**Owner's decision, three parts, executed exactly as given:**
1. **Archive the hero version.** `git tag` push failed with an HTTP 403
   (this session's git credentials allow branch pushes, not tag pushes —
   confirmed via the agent-proxy status endpoint, not a transient error).
   Used a branch instead: `archive/hero-redesign-2026-08-28-branch`, pushed
   to origin, pointing at PR #4's final commit (`5031bb8`). PR #4 closed
   (not merged) with a comment linking the archive branch and this entry.
   The full Phase 2/3 diff — token system, accessibility pass, storage
   manager, search — lives there untouched, nowhere else.
2. **Promote the forest version.** `redesign/native-android-ship` → `main`
   confirmed as a clean fast-forward (`main` had zero commits since the
   shared base). Branched `redesign/converge-to-main` off it, one cleanup
   commit removing `PhonoLeaf Design System.zip` and `PhonoLeaf.dc.html`
   (accidentally committed design-export artifacts — `CLAUDE.md` already
   says these should stay local-only, and this branch's own history had
   already caught the zip's bundled `index.green.html` copy going stale
   once), added them to `.gitignore`, syntax-checked, PR'd, merged to
   `main`.
3. **Prevent recurrence.** Added a standing check to `CLAUDE.md`'s redesign
   section: before starting substantial work on `index.green.html`, run
   `git log --all --oneline -- index.green.html` and `git branch -r` — a
   10-second check that would have caught this the first time. Also
   corrected `scripts/stage-www.js`'s "TO CONVERGE LATER" comment (and the
   matching language in `CLAUDE.md`), both of which described the
   native/web split as temporary pending a later merge — the owner's
   2026-08-28 call makes the split permanent, not a to-do.

**Explicitly not done today, by owner instruction:** porting Phase 2/3's
four features into the now-canonical `index.green.html`. That's real,
scoped work against a very different version of the file (forest markup,
mini player, desktop column CSS already in place) — logged in `TODO.md`
rather than attempted inline. Also explicitly not attempted: a git merge of
the two branches' `index.green.html` versions against each other — flagged
by the owner as likely to produce unresolvable conflicts given the scale of
independent change on both sides, and not needed since the plan was always
"pick one, archive the other," not "combine them."

Git ref bookkeeping note: an initial archive attempt used a tag name that
collided with a same-named branch created during troubleshooting the 403
(`error: src refspec ... matches more than one`); both local refs were
deleted and only the final `-branch`-suffixed name was pushed. Remote
branch deletion also 403s under this session's credentials, so a
differently-named stray branch from that troubleshooting was left in place
rather than fought further — harmless, just an extra ref.

## Ported the archived Phase 2/3 features into the canonical index.green.html (2026-08-29)

Followed through on the `TODO.md` item from the reconciliation above: the
four features that only ever existed on the archived hero branch
(`archive/hero-redesign-2026-08-28-branch`) — the motion/gesture CSS token
system, a localized accessibility pass, the storage manager ("On this
phone") screen, and in-book full-text search — are now in the real,
canonical `index.green.html` (the forest/native-android-ship version).
**Not a git merge** (the two branches' `index.green.html` versions were
flagged as likely unresolvable, per the reconciliation entry above): each
feature was re-implemented by hand against the current markup/CSS/JS,
using the archived branch's diff (`git diff` against the shared base
commit `d1fc64e`) purely as a reference for behavior and STRINGS wording,
never applied as a patch.

- **Motion tokens**: `--motion-fast/base/slow/ease` custom properties added
  to `:root`; every hardcoded transition duration in the file (24 rules —
  the exact same set the archive branch had touched, confirming this is
  shared Phase-1 CSS neither branch had diverged on) now references one of
  the four tokens, categorized the same way the archive comment specified
  (micro feedback / chrome show-hide / progress fills). Page-turn animation
  and the forest/critter flight keyframes deliberately left alone, as the
  archive's own comment already flagged.
- **Accessibility pass**: `I18n.apply()` gained the same
  `[data-i18n-aria-label]` walk the archive added. Every hardcoded-English
  `aria-label` found in the current file (37 total) got either a
  `data-i18n-aria-label` attribute (reusing an existing STRINGS key where
  one already covered the same phrase — `chapters_label`, `follow_along`,
  `sleep_timer`, `open_reader`, `voiceinfo_title`, `close`, the four
  `lib_view_*` keys, `search_placeholder`, `theme`, `app_language`) or,
  for JS-templated labels (Home's cover-row card, the Log page's calendar
  bars and group-breakdown select, FolderBrowser rows, Library book cards),
  a direct `I18n.t()` call. 17 new `aria_*`/`stat_bar_aria` keys added
  where no equivalent existed yet (the forest version's ±15s skip and
  prev/next-chapter reader controls, the mini player's matching controls,
  and the Log page's calendar-week bars — none of which existed on the
  hero branch this pass was originally built against, so these are new
  coverage, not ports of something that already had an English string).
  Confirmed zero duplicate STRINGS keys afterward (a programmatic dupe
  check across both `en`/`fr` dictionaries, 412 keys each, matching count).
- **Storage manager**: new "On this phone" Settings row + modal, `fmtBytes()`
  helper, `CoverCache.size()/clear()` (no prior size/clear helper existed),
  and the `StorageModal` module — all ported essentially unchanged, since
  `BookCache._index()`, `VoicePacks.ALL_PACK_MODELS`/`_status`/`refresh()`,
  and `LocalBooks.folderInfo()` all still have the exact shape the archive
  branch's version assumed. One real find during the port: the archive's
  own `remove`/`mb` STRINGS keys would have duplicated keys the forest
  branch already had (a different, pre-existing "Remove"/"{mb} MB" pair
  for the voice-pack download UI) — caught by the dupe check above and
  fixed by reusing the existing keys instead of re-adding them.
- **In-book search**: new magnifying-glass button in the reader's top bar
  (a `.rt-search` icon button matching `.rt-back`'s circular style — the
  forest reader chrome has no hamburger-icon slot like the hero version
  did, so this is new markup, not a straight port) opens a search modal
  reusing `.chapter-item`/`.chapter-label` styling from the existing
  Chapter modal. The `Search` module itself ported unchanged — it only
  depends on `TTS._loadSectionChunksWithNodes`/`_currentSectionChunksWithNodes`/
  `skipPage`/`_dir`, all confirmed present with the same shape in the
  forest version's TTS module (this is core Phase-1 machinery neither
  branch touched).

Verified: `node -e "...compileFunction..."` on `index.green.html`,
`node --check sw.js`, `node --check scripts/stage-www.js` all pass; ran
`node scripts/stage-www.js` for real and confirmed the staged
`www/index.html` contains `Search`, `StorageModal`, the `--motion-fast`
token, and all 37 `data-i18n-aria-label` attributes. Not device-tested —
that's still owed before this ships to Play Console via `npm run sync` +
Android Studio. Committed as a single commit rather than four (the
original plan) — by the time all four were implemented their edits had
interleaved within the same STRINGS blocks and shared regions of the
file, and retroactively splitting them via `git add -p` was judged more
likely to introduce a mistake than the value of a cleaner history here.

## Store review prompt after finishing a book (2026-08-30)

`BACKLOG.md` section I, Android only. `StoreReviewPlugin.kt` wraps Google
Play's In-App Review API (matching this repo's pattern of small
first-party Kotlin plugins rather than third-party Capacitor packages),
registered in `MainActivity.java` alongside the others, with
`com.google.android.play:review:2.0.2` added to `android/app/build.gradle`.

Deliberately scoped down from the original spec in two ways, both for the
same reason (nothing real to point at yet): **iOS deferred** —
`SKStoreReviewController` needs an `ios/` Capacitor platform that doesn't
exist in this repo; **no web fallback** — the original spec wanted "a small
dismissible prompt linking to the store," but the web app has no reviewable
store listing of its own, so that prompt would link nowhere.

Rate limiting is two-layered and only one layer is ours: Play's library
self-governs how often the sheet can actually appear (a handful of times a
year, silently no-oping if the user already reviewed) and never reports
back what happened — by design, so apps can't retaliate against a bad
review. `StoreReview._ASK_EVERY_MS` (60 days, via `pl_review_asked_at`)
therefore isn't the real rate limit; it just avoids calling the native API
at every opportunity. Nothing is shown to the user either way, success or
failure, because there's nothing truthful to show.

## Storage modal spacing/sort + the finish-flow fix (2026-08-30)

Four owner-reported items from a device pass, all in `index.green.html`.

**Storage popup spacing/sort**: the groups were rendering as a bare list of
rows under a heading. Reused `.set-section-label` (Settings' own group
heading, `margin-top: 2.25rem`) rather than inventing new spacing, since
Settings had already been through exactly this feedback loop. The size/A-Z
sort toggle was removed outright — `_bookSort`, `setBookSort()`, the
`.seg`-based toggle markup, and the now-orphaned `storage_sort*` STRINGS
keys in both `en`/`fr`; cached books now always sort by size, largest
first, with no user-facing control.

**Reader now closes on a genuine finish**: reaching the true end of the
book while listening used to call `TTS.stop()` and toast in place, leaving
the reader open on the last page. Now calls `Reader.close(true)`. The
argument matters: `Reader.close()` is called from several places (the back
arrow, `Reader.back()`'s minimize path), so the store-review prompt could
not simply live inside it unconditionally — `finished` gates it, and is
true only on this path.

**Store review moved off the manual mark**: `StoreReview.maybeAsk()` was
firing from two places, one of which was `BookDetail.markFinished()`.
Owner feedback was that a manual "Mark as finished" isn't the positive
moment the prompt is for (and reported the organic finish not prompting at
all — because the reader never closed, the prompt fired at a moment the
user wasn't experiencing as "finished"). It now fires from exactly one
place: inside `Reader.close()`, gated on `finished`, i.e. after the reader
has actually closed from a real finish. `markFinished()` carries a comment
saying why it deliberately doesn't ask, so it doesn't get "fixed" back.

## Storage modal: boxed groups, then per-item percentages (2026-08-30, same day)

**Boxed groups**: owner's ask was general, not specific to this screen —
*any* grouped-rows screen should look like Settings, not just Settings.
Each Storage section now wraps in `.set-group` (the same bordered card),
with each group's first row carrying `.set-row-first` to drop the top
hairline that would otherwise double up against the box border. `_row()`
gained a trailing `first` param and the two `.map()` call sites pass
`i === 0`; the covers row (always single) passes `true` literally. Worth
knowing for future screens: `.set-group`/`.set-row`/`.set-row-first`/
`.set-section-label` are all global classes, not scoped to
`#settings-view`, so reusing them elsewhere needs no CSS changes at all.

**Per-item percentages**: each row's size now also shows its share of the
grand total (`_sizeWithPct`, e.g. "12 MB · 34%"), using the same `·`
separator convention already used elsewhere in the file. A zero grand
total (nothing cached anywhere yet) returns the bare size instead of a
meaningless 0%/NaN%. Percentages are computed against the grand total
across all three groups, not per-group, so they answer "what's using my
space" rather than "how big is this within its own category."

## Recovering payments/D1 work orphaned by the branch reconciliation (2026-08-30)

**The bug**: the owner asked for the current to-do list, and it still
listed "Pricing model" and "KV vs D1 for entitlement" as open decisions —
both of which had been decided in this same session on 2026-08-28. Their
correction ("we did it in this one") was right, and re-reading the session
transcript found the root cause.

**Root cause**: the 2026-08-28 reconciliation (see that entry above)
archived `claude/docs-code-review-tam2pr` wholesale and promoted
`redesign/native-android-ship` to `main`. That decision was *about* the
`index.green.html` redesign fork — but the archived branch also carried
completely unrelated work committed to it across the same days: the
pricing-model lean, the KV→D1 decision, all of `PAYMENTS_SPEC.md` §13's
decisions, the entire D1 migration (code + real deployed database ids),
and the competitor-SWOT factual follow-ups. Archiving the branch archived
all of it. The reconciliation was scoped to a UI question and silently
took a payments backend with it.

**Why this mattered more than a stale checklist**: the D1 migration wasn't
just decided, it was *deployed*. Production and staging have been running
on D1 since 2026-08-28 (the owner ran `wrangler d1 create` /
`migrations apply` / `deploy` themselves), but `main`'s `worker/` still
described the KV setup — `[[kv_namespaces]]` blocks with the old namespace
ids, `env.ENTITLEMENTS.get/put` in `entitlement.js`. The repo was
describing infrastructure that no longer existed, so anyone redeploying
from `main` would have deployed against the wrong storage entirely.

**Recovery method**: not a cherry-pick of the eight commits — `TODO.md`
had been restructured on `main` several times since the fork, so replaying
those diffs would have conflicted throughout. Instead, verified per-file
which side had actually changed since the merge base (`d1fc64e`):
`worker/`, `PAYMENTS_SPEC.md`, and all ten comparison pages were
**untouched on `main` since the fork**, so those were taken wholesale from
the archive branch with `git checkout <branch> -- <paths>`. Only `TODO.md`
and `CLAUDE.md` had diverged on both sides, so their content was
re-written by hand into `main`'s current structure rather than overwritten.
`BUSINESS.md` needed nothing — its one relevant fix had already been
hand-reapplied from PR #4's diff during the 2026-08-29 stale-doc cleanup.

**Two more staleness findings while in there**, neither caused by the
archival: `TODO.md` still said the MacBook purchase "fell through" (the M1
Air was acquired 2026-08-29, with a walkthrough given the same day —
`CLAUDE.md`'s Productization section had the same stale line), and still
listed the competitor SWOT as "ready to kick off" even though
`COMPETITOR_SWOT.md` predates the fork entirely and was already done.

**Verified afterward**: swept every remaining open `[ ]` item in `TODO.md`
against the full session transcript and the repo — Play Console account
type, iOS engineering (confirmed no `ios/` directory, no `@capacitor/ios`
dependency; the MacBook walkthrough was instructional only), internal
SWOT, redesign exploration, device-tier testing, and every product idea
are all genuinely still open. Nothing else was lost.

**Lesson, worth more than the fix**: the 2026-08-28 entry's safeguard
(check `git log --all -- index.green.html` before working on it) addresses
divergence on a *file*. This failure was a different shape — a branch
carrying two unrelated workstreams, where a decision about one silently
disposed of the other. Before archiving or abandoning a branch, diff its
full file list against what the decision is actually about
(`git diff <base> <branch> --stat`), and rescue anything outside that
scope first. Especially anything already deployed to real infrastructure.

## Redesign narrative moved out of CLAUDE.md (2026-08-31)

Verbatim move, not a rewrite. These entries lived in `CLAUDE.md`'s Redesign
section and made it 991 lines / 64KB, which is auto-loaded on every turn in
every session. That violated that file's own stated rule (a 1-5 line
current-state summary per change, with the reasoning here).

**Nothing was deleted.** Some of these entries almost certainly duplicate
sections already in this file (the 2026-08-25 Phase 1 work, the 2026-08-27
recovery, and the 2026-08-30 storage modal changes each have their own
heading above). Duplication was chosen deliberately over de-duplication:
a coverage check by identifier overlap was not reliable enough to justify
deleting the owner's documentation, and duplication in this file costs
nothing because it is never auto-loaded. If you are consolidating later,
compare against the existing headings before removing anything.

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
manual mark isn't the moment the prompt is for. Device-tested and passed
2026-08-31.

**Storage modal boxed groups (2026-08-30, same day)**: owner asked that any
grouped-rows screen look like Settings, not just Settings itself. Storage's
Cached books/Voice packs/Cover images sections now each wrap in `.set-group`
(the same bordered card Settings uses), with each group's first row on
`.set-row-first` to drop its top hairline — previously the rows sat as a
bare unboxed list under the heading. Device-tested and passed 2026-08-31.

**Storage modal per-item percentages (2026-08-30, same day)**: each cached
book/pack/cover row's size now also shows its share of the grand total
(`StorageModal._sizeWithPct`, e.g. "12 MB · 34%"), skipping the percentage
entirely when nothing is cached yet (avoids a meaningless 0%/NaN%).
Device-tested and passed 2026-08-31.

## Session protocol cut down to a solo workflow (2026-08-31)

Owner call: this is a one-person project with no reviewer, so the branch,
PR, task-claiming and WIP-visibility steps were pure overhead and are gone.
`CLAUDE.md` now keeps only the two things that still earn their place:
start every session on an up-to-date `main`, and test before pushing,
because for the website a push is the deploy.

The full four-step protocol is preserved below because the collisions that
produced it were real (2026-08-28 and 2026-08-30/31, hours of duplicated
work each). It is the right procedure again the moment two sessions run in
parallel; it was simply solving a problem that does not exist with one
session at a time.


**Multiple Claude sessions work this repo, often on the same day. Two have
already collided badly (2026-08-28, and again 2026-08-30/31), each time
producing hours of duplicated work on the same files. Committed work is
visible; work in progress is invisible. These four steps are what make it
visible. They are not optional and they take about a minute.**

1. **Get onto an up-to-date `main` FIRST, before reading anything else.** Run
   this at the start of every session, without waiting to be asked and without
   first judging whether it looks necessary:
   ```
   git fetch origin
   git checkout main && git pull --ff-only
   git log --oneline HEAD..origin/main
   ```
   If you are deliberately continuing on a feature branch instead of `main`,
   still fetch first, and **rebase onto `origin/main` before editing a single
   file** if that last command prints anything.

   Two real failure modes, both from 2026-08-31, and neither detectable by
   reading files. A session skipped the fetch until push time and found
   `origin/main` six commits ahead, including a KV to D1 migration that broke
   its tests and ten comparison pages another session had already corrected the
   same day. Later the same day another session opened on a branch that had
   already been merged, with local `main` two commits stale, so every file it
   read was one PR out of date and its status report described the previous
   day's tree. Ten seconds of git rules out both.

2. **Claim the work in `TODO.md` before doing it.** Mark the item `[>]` with
   the date and your branch name, then commit and push *that one line change on
   its own, immediately*. It is a lock file made of markdown. An unclaimed task
   will be picked up by another session that has every reason to think it is
   free.

3. **Push a WIP branch within the first ~20 minutes**, before the work is good.
   A pushed branch appears in everyone's `git branch -r`; twenty uncommitted
   files appear to nobody. Polish later, be visible now.

4. **Check the files, not just the branch, before substantial work:**
   ```
   git log --all --oneline -- <path>    # any file you are about to change
   git branch -r
   ```
   This generalizes the older `index.green.html`-only safeguard, which did not
   fire on 2026-08-31 because the collision was in the comparison pages and the
   worker instead. **Apply it to any file, not just that one.**

**Scope a session by file territory, not by task size.** "Docs and marketing"
and "worker and payments" cannot collide. "Whatever is next" collides
constantly. If you are about to work outside your territory, claim it per
step 2 first.

**When you find a collision anyway:** rebase onto `origin/main` and take the
merged version as your base, then re-apply only what is genuinely additive.
Never force push over another session's work, and never resolve a conflict by
taking your own side wholesale just because it is yours.

## Website: desktop layout, four ported features, then a strategy decision (2026-08-31)

A session that started as "close the fork gap" and ended by cancelling the
fork-convergence task outright. Recorded in order, because the later work
changes how the earlier work should be read.

**1. The website had no desktop layout.** Since 2026-08-28 a large screen got
the phone layout capped to a centred 480px column. That was a deliberate trade
at the time (the block comment says so: avoid building and maintaining a second
layout), but on a 1900px display it reads as a phone app in a strip, and the
owner rejected it on sight. Replaced with a real layout at a new >=900px
breakpoint, CSS only, no DOM or JS change, so phones are untouched and none of
it reaches the native build:

- The 700 to 900px band keeps the centred column, since a window that size
  genuinely is tablet shaped.
- The bottom tab bar becomes a left nav rail. Views clear it, except sign-in
  (own centred layout, no rail) and the reader (hides the rail via Nav.hideBar).
- Content gets a gutter and caps at 880px: cards drawn for a 480px column
  stretch into unreadable bands at full width.
- Library fills the width. The 2/3/4 toggles become large/medium/small covers
  via auto-fill rather than a literal column count, which on a wide screen
  would mean four enormous covers.
- The reader caps `#viewer` to 46rem. epub.js paginates to that box, so capping
  it is what shortens the line: a max-width on the text inside the iframe would
  not survive pagination. The chrome, progress line and page-turn zones are
  anchored to the same column, because those zones are `width: 12%` pinned to
  `left:0/right:0` and stranded hundreds of pixels from the page without it.
- Modals become centred dialogs rather than bottom sheets.

**2. Four features ported from `index.green.html`, functionality only.** The
owner was explicit that the designs stay separate, so nothing visual came
across: `Forest` and `CoverReveal` were excluded as design, `StoreReview` as
native-only (no reviewable web listing), and `MiniPlayer` because the website
already solves it a different way (the Home hero is its mini player).

- **Sleep timer.** Timing logic identical, including that expiry never cuts
  audio mid-sentence: it sets `TTS._sleepExpired`, which the existing
  chunk-boundary checks act on. All three hook points existed here already.
  The native drag dial did not come across; quick-pick chips instead.
- **In-book search.** Module verbatim. Every dependency already existed here
  (`TTS._loadSectionChunksWithNodes`, `_currentSectionChunksWithNodes`,
  `skipPage`, `cfiFromRange`), so only the entry point and presentation are new.
- **Storage manager.** Needed `fmtBytes()` and `CoverCache.size()/clear()`,
  which the website lacked. `fmtBytes` is deliberately separate from the
  existing `fmtSize()`: that one returns an empty string for a falsy size,
  which its callers rely on, whereas a storage row must show "0 KB". The
  voice-pack section guards on `VoicePacks.available()`, false on web, so it
  omits itself.
- **Mark finished / forget / export confirmation.** The native build reaches
  the first two from `BookDetail`, which is redesign UI and stayed out, so they
  got a small per-book menu in the existing modal idiom instead.

Two bugs found by running the code rather than reading it, both worth
remembering because reading would not have caught either: the sleep modal was
using the redesign’s `.active` class while this design shows modals with
`.open`, so it rendered `display:none` while reporting itself open; and the
sleep readout is JS-templated, so it survived a language switch untranslated
until `setLang` got an explicit `syncReadouts` call.

**3. A tour bug that the rail exposed.** Reported symptom: the home tour showed
"2 of 3 / Your statistics" while the spotlight was still around Library. The
spotlight transitioned `top`/`left` over 0.25s. On the phone tab bar all four
tabs share a `top`, so only `left` ever changed between steps and a stalled
`top` transition was invisible. The rail makes every step a `top` change, so a
latent bug became the visible symptom. Measured both ways at 1440px: with the
transition on, `top` froze at step one’s value while the inline style already
held the correct one; with it off, all three steps land exactly. The transition
was removed from the spotlight and the tip. **Do not add it back.** A spotlight
that is always on the right element beats a smooth one that sometimes is not,
and nothing in the app gates on `transitionend`.

**4. Then the decision that reframes all of it.** The owner’s call: the phone
apps are the product, and the website is SEO plus a launcher for the app
stores. The web app is frozen at this feature set. Full reasoning and the
reopening conditions are in `BUSINESS.md`, "Platform strategy". The short
version is that the web cannot deliver the reliability claim the product now
leads on, because background playback needs a real foreground service and a
browser tab has no equivalent.

The agent’s counter-argument, recorded because it is the reason the web player
was frozen rather than deleted: there is nothing to link to yet. No Play
release exists, OAuth is capped at 100 testers, and no `ios/` platform exists,
so today the web build is the only working PhonoLeaf anyone can use and the
only iOS story there is. Removing it now would leave a marketing page pointing
at app stores with no listing on them. Hence the explicit trigger: revisit once
the Play listing is live AND iOS has shipped.

**How to read the four ports in hindsight:** under the new direction they are
work the owner might not have chosen to buy. They stay because they already
exist and cost nothing to keep. The desktop layout is different: a site whose
job is SEO and app-store launching has to look right on a desktop screen, so
that part serves the new direction directly.

**Still unverified at the end of this session**, all needing a signed-in
session with real books, which localhost cannot do until
`http://localhost:3000` is added as an authorized JavaScript origin on the web
OAuth client: search indexing and jumping through real text, the storage screen
with real cached sizes, forget actually removing something, the reading measure
with words on screen (46rem is a guess and easy to nudge), and whether the
sleep timer genuinely stops at a sentence boundary mid-listen.

## The GPL problem, found and solved (2026-08-31 to 2026-09-01)

The session that started as "port some features to the website" and ended by
discovering the app could not legally ship, then fixing it. Recorded in the
order things were learned, because the later findings invalidate some of the
earlier work.

### 1. Website work, later made mostly moot

Ported four features from `index.green.html` to `index.html` (sleep timer,
in-book search, storage manager, mark-finished/forget plus export
confirmation), and gave the website a real desktop layout: a left nav rail
instead of the phone tab bar, content gutters, a capped reading measure, and
centred dialogs, all CSS-only behind a >=900px breakpoint.

Then the owner decided the website should be **SEO plus an app-store launcher,
not a product**, and that web playback gets removed once both stores carry the
app. So the four ports are work that would not have been bought under the new
direction. The desktop layout is not: a marketing site has to look right on a
desktop. Full reasoning in `BUSINESS.md` "Platform strategy".

**Two bugs the browser caught that reading would not have**, both worth
remembering as classes of error: the sleep modal used the redesign's `.active`
class while the website shows modals with `.open`, so it rendered
`display:none` while reporting itself open; and the sleep readout is
JS-templated, so it survived a language switch untranslated until `setLang`
got an explicit re-render call.

### 2. SEO sharpened onto per-competitor weaknesses

The comparison pages were reframed on reliability, then re-aimed again after
the owner asked whether they targeted each competitor's specific weaknesses.
They did not: three of six led on the generic four-failure-mode message.
`voice-dream` was the clearest error, leading on background playback, which
Voice Dream genuinely has and advertises, while ignoring the complaint that is
exactly our strength (its own reviewers calling the voices robotic). Per-page
targeting table now lives in `SEO.md` §6.

Also added `audiobook-app-that-doesnt-stop.html` and its French twin for the
failure-mode keyword cluster, and settled a long-standing contradiction: the
owner reaffirmed that comparison pages concede nothing, so `SEO.md` §6's
"choose them if" instruction was corrected at the source rather than argued
with again.

### 3. The licence audit, which changed everything

Owner asked whether the voice models were usable in production, calling it a
drop-everything question. It was.

| Component | Licence | Outcome |
| --- | --- | --- |
| Kokoro int8 v0.19 | Apache 2.0 | clean |
| Piper engine | MIT | clean |
| en_US libritts_r, en_GB vctk | CC BY 4.0 | fine, attribution required |
| de_DE thorsten | CC0 | clean |
| fr_FR upmc | CC BY-SA 4.0 | ShareAlike, lawyer question |
| es_ES sharvard | CC BY 3.0 on the card | **removed** |
| **espeak-ng** | **GPL-3.0** | **the real problem** |

Spanish was removed because its model card claims CC BY 3.0 while also saying
it was fine-tuned from lessac, which carries the Blizzard licence excluding
commercial voice synthesis. Cheaper to drop than to argue. **Lesson: check the
base model, not just the card.**

The serious finding was espeak-ng. It is GPL-3.0 and **statically linked into
the sherpa-onnx AAR**, confirmed by finding `espeak-ng` and `espeak-ng-data`
strings inside `libsherpa-onnx-jni.so`. GPLv3 statically linked into a
proprietary app is the textbook propagation case, and the owner will not
open-source the app. That made shipping legally questionable.

### 4. Escape routes, and one expensive dead end

Researched and eliminated in turn: stock sherpa cannot be built without espeak
(one all-or-nothing CMake flag); the lexicon workaround solves runtime use but
not distribution, which is what GPL attaches to; Piper voices are trained on
espeak phonemes so the phonemizer cannot simply be swapped; and Kokoro-only is
not viable because a Pixel 7 cannot run Kokoro in real time.

**Supertonic was evaluated properly and failed.** An on-device spike proved it
runs on Android (0.59 realtime at 8 steps, 448 MB native heap, 380 MB fp32
download), but audio quality through our implementation was bad and the cause
was never found. Probable root cause: the inference contract was derived from
`nedmah/supertonic-kmp`, a zero-star repo created and abandoned within half an
hour, treated as a specification. Spike deleted; findings kept in `TODO.md`.

**Two reasoning errors made during that spike, recorded because they are easy
to repeat.** First, claiming the pipeline was "verified" because audio length
matched the predicted duration: latent length derives from duration and audio
length from latent length, so they agree by construction and prove nothing.
That false confidence cleared exactly the area the symptoms pointed at.
Second, arguing the Kokoro device gate was too strict because the spike hit
0.59 realtime, when that was a single utterance in isolation, which is
precisely the "quiet one-shot benchmark" the code already warns about. The
owner settled it by ear: real Kokoro on a Pixel 7 reads a sentence or two then
stalls ten seconds. **The gate is correct.**

### 5. The fix that worked: a process boundary

The owner asked why competitors ship Piper without open-sourcing. Answer:
mostly they do not embed it. Android TTS engines are standalone apps called
over a standard API, so the GPL code is somebody else's program. Two apps was
rejected on UX, correctly, but it pointed at the architecture that worked.

The engine now runs in its own `:tts` process behind an AIDL interface
(`TtsService.kt`, `ITtsService.aidl`), and `PhonoLeafTtsPlugin.kt` no longer
imports sherpa at all. The boundary is deliberately arms-length: strings and
primitives in, raw audio written to a caller-chosen path out, no shared memory,
no callbacks, no custom Parcelables. Audio goes via file because Binder caps
transactions near 1 MB.

The split was chosen so the published component stays minimal: the service does
only load-model, generate, write-samples. The loudness calibration and WAV
muxing stayed on the app side, being ours and not derived from anything GPL.

Measured on device: **warm bind 0 ms, 0.29 realtime, audio indistinguishable**
from the old in-process path. Cold costs a one-off 303 ms bind plus model load.

All three conditions the legal analysis named are now met: generic protocol,
separate process, published source (the repo is public, and `ENGINE_NOTICE.md`
makes the intent explicit). **What remains is a lawyer question, not an
engineering one:** the sherpa `.so` is still in the APK because both processes
are one app, so this moves the position from "clearly one combined work" to
arguable aggregation.

### 6. Three bugs from the cut-over, and one that predated it

- **Deleting a pack killed the voice for the session.** The JS identifies a
  missing pack by an error starting `PACK_NOT_DOWNLOADED:` and switches voices
  rather than counting a failure. The cut-over renamed that string, so each
  attempt counted as an engine failure; two in a row set `_kokoroDead`, and
  native has no Web Speech fallback, so playback stopped entirely. **The
  cut-over changed an error CONTRACT, not just an implementation.**
- **First page after a download stuttered.** `_nativeBench` fires un-awaited,
  so it raced the user's first page for the engine's single lock, and whichever
  won also paid the cold model load. Fixed by awaiting `prepare()` first.
- **`inferenceThreads()` was deleted** as collateral, since it sat beside
  `ensureReady()`. It now exists in both the plugin and the service on purpose,
  and the two must stay in step or the benchmark stops predicting the engine.
- **Background reading lost its position (PRE-EXISTING).** `_bgResync()` was
  only called from the `visibilitychange` handler, which bails unless
  `_nativeAppActive` is true, and that flag is set by a separate
  `appStateChange` event with no ordering guarantee. When visibilitychange won
  the race the resync never ran, the visible reader stayed on the locked page,
  and the next `_persistPosition()` overwrote the good background position with
  the stale one. Fixed by resyncing from both events.

### 7. Other decisions made

- **MP3 export rejected**, on product grounds rather than legal: offline
  listening already exists, so the file adds no capability.
- **Highlight/annotation export rejected**: the brand is books and audiobooks,
  not academic tooling.
- **About and Licences pages** added for the native app, EN and FR, linked next
  to Privacy and Terms. The licences page is a legal obligation, not a credit
  roll: CC BY and CC BY-SA both require attribution. It is deliberately buried
  one level in and carries `noindex`.
- **Pronunciation editor** remains open and unscoped. Best-evidenced gap in the
  competitor research.

## The voice that collapsed into a syllable (2026-09-01)

Three device-reported bugs in one session. The third took four wrong theories
to find, and the record of the wrong turns is the useful part.

### What was reported

"Removed a language pack, downloaded another, pressed play, and it stutters."
Later, more precisely: it started the wrong voice, needed a second press, then
stuttered for exactly one page. Later still: stuttered indefinitely.

### Two real bugs found by reading code

**The model-ready cache was never invalidated.** `_modelReadyP` was assigned
once and nothing anywhere cleared it, so after a pack change the app still
believed the old model was warm. The new model was never pre-warmed and
`_modelType` named the wrong family, which `_synthNative` uses to resolve the
voice sid. Fixed two ways: `_modelReady()` now records which model it cached
for and self-heals, and pack download/delete invalidate explicitly. The
self-heal matters more, because the original bug was precisely that every call
site had forgotten.

**The speed benchmark competed with playback.** `_nativeBench` runs a real
synthesis, the engine serves one request at a time, and the download path fires
it un-awaited. Every sentence queued behind it, and the first play press looked
dead because it was in the same queue. "Stuttered for a full page, gone when
the page turned" was really "gone when the benchmark finished". It now defers
while `TTS.active`.

A third fix, for lines being skipped after a page turn: `_resumeRead` only
retried when it extracted NOTHING or the PREVIOUS page, so a partially
laid-out page looked like success and reading began at line three. It now
requires the same extraction twice before speaking.

### The hard one, and four wrong theories

After those fixes the stutter persisted, so the session switched from reading
code to reading the device over adb. That should have happened sooner.

Wrong theory 1: stale engine state within a session. Disproved by a force-stop
that changed nothing.

Wrong theory 2: a corrupt or incomplete pack. Disproved by inspecting the
installed files: 78.5 MB model, correct config, complete espeak-ng-data with
`phondata`/`phontab`/`phonindex`, 159-line token map, `num_speakers: 904` so
sid 40 was valid, correct sample rate.

Wrong theory 3: the cut-over dropped a speech-rate setting. Disproved by
diffing the old in-process config against the new one; byte-identical.

Wrong theory 4: a shared `MODEL_VERSIONS` tag. Real observation, wrong
conclusion: `us`, `gb` and `de` do share a tag, but each marker lives in its
own folder so they cannot collide. Now annotated in both files as
deliberately-not-a-bug, since renaming would force ~150 MB of pointless
re-downloads.

**What the logs actually showed.** The US voice was healthy: 176 chars giving
8800 ms of audio at 0.27 realtime. Every broken clip was a different model.
Pulling the generated WAVs off the device confirmed it: 0.58 s of audio,
81-88% non-silent, correct 22050 Hz header, for text that should have run nine
seconds.

**The cause**, which only the owner's testing could have isolated: espeak
initialises its data directory once per process, from whichever pack loads
first, and never revisits it. Installing or deleting a pack deletes that
directory, leaving the engine pointing at something gone. Freeing the engine
object does not help, because the state belongs to the process.

The owner's observation that it hit `gb`/`fr`/`de` but never `us` is what
proved it. `us` maps to the folder `kokoro`; the others map to `kokoro-gb`,
`kokoro-fr`, `kokoro-de`. Re-downloading `us` recreates the exact path espeak
already cached, so it silently heals. Any other language creates a different
folder and stays broken. **No theory that fails to explain the `us` exception
is the right one.**

Fixed by adding `shutdown()` to the AIDL and restarting the `:tts` process
whenever a pack's files change. The architecture already sanctions this: the
manifest says the process may be killed at any time and the next synthesize
reloads. Also added the explicit `release()` the code never called, which is
correct on its own but was not sufficient.

### Lessons worth keeping

- **Read the device before theorising.** Four theories died to evidence that
  took one adb command to gather.
- **An anomaly that a theory cannot explain is the theory failing**, not a
  detail to set aside. The `us` exception was mentioned and nearly passed over.
- **Guards must be mutation-tested.** The first version of the benchmark guard
  passed even with the protection deleted, because two `if (this.active)`
  occurrences existed and a non-global replace left one standing. Found only by
  trying to break it.


## Audit, device bug hunt, infrastructure, and store-only billing (2026-09-01 to 09-02)

A long session: a full security/code/licence audit, then four device-reported
bugs, then most of the remaining infrastructure. Recorded with the wrong turns,
because those cost more time than the fixes did.

### The audit

24 findings across security, correctness and licensing. Sixteen were fixed the
same day; the rest needed a device, an account, or a lawyer. The ones worth
remembering as classes of problem:

- **The error contract that nearly cost Piper had no test.** A missing pack is
  signalled by a STRING crossing two process boundaries and being renamed once
  on the way. Nothing asserted the ends agreed. Now pinned, including a COUNT
  check that the plugin translates in both places — the original cut-over fixed
  `synthesize` and missed `prepare`, and a one-sided fix reads as correct.
- **A read-then-write race could demote a paying customer.** `getOrStartTrial`
  wrote with `ON CONFLICT DO UPDATE`, so a webhook committing `active` between
  the SELECT and the INSERT was overwritten with `trial`. The existing test
  named "never downgrades a paying subscriber" passed either way, because it
  exercises the pure function and cannot see an interleaving.
- **"Published source" is not "licensed source."** `ENGINE_NOTICE.md` counted
  publication as one of three satisfied conditions, but the repo had no LICENSE
  file at all, so the bridge was visible and offered to nobody.
- **The APK was 221.7 MB** because ~185 MB of models sat in `assets/` on the
  build machine. The docs had twice concluded from `.gitignore` that nothing was
  bundled. `.gitignore` governs the REPO, not the BUILD. Nobody had opened the
  APK. Now 53 MB, with a Gradle guard that fails the build if a model reappears.

### The lawyer, and what he actually said

Counsel reviewed the GPL boundary and approved the architecture, with changes:
the bridge moved to its own directory under its own package
(`com.phonoleaf.ttsbridge`), got explicit GPL-3.0 headers and a LICENSE, and its
comments were made generic. The root LICENSE is proprietary with that directory
carved out. His caveat, worth keeping: shipping both in ONE installation package
leaves residual grey area, which is why the bridge must stay impeccably
licensed. `npm test` now enforces the structure.

Also corrected: espeak-ng's LGPL relicensing request was closed `not_planned` in
January 2025. `TODO.md` had it as open. That route is gone permanently.

### Four device bugs, and four wrong theories

Owner reported: stutter after swapping voice packs, the wrong voice selected, a
3-4s page-turn delay, and follow-along misbehaving.

Two were found by reading code: `_modelReadyP` was assigned once and never
cleared by anything, so after a pack change the app still believed the old model
was warm; and `_nativeBench` runs a REAL synthesis un-awaited, so it sat in
front of the reader's own chunks on a single-threaded engine.

**The third took four wrong theories and should have taken one adb command.**
Symptom: every sentence collapsed to a fraction of a second of noise. Disproved
in turn: stale engine state (a force-stop changed nothing), a corrupt pack (the
installed files were complete and correct), the cut-over dropping a speech-rate
setting (the old and new configs were byte-identical), and a shared
`MODEL_VERSIONS` tag (real observation, wrong conclusion — the markers live in
separate folders and cannot collide).

The cause came from the owner's own testing: it hit `gb`/`fr`/`de` but never
`us`. espeak initialises its data directory ONCE PER PROCESS, from whichever
pack loads first, and never revisits it. Installing or deleting a pack deletes
that directory. `us` maps to the folder `kokoro`, so re-downloading it recreates
the exact cached path and silently heals; the others use different folders and
stay broken. **Any theory that fails to explain the `us` exception is wrong.**
Fixed by restarting the `:tts` process on pack change, which the architecture
already sanctions.

Freeing the engine object was NOT sufficient and should not be mistaken for the
fix — the state belongs to the process.

### The build trap that wasted two test rounds

`npm run stage` writes `www/` and stops. Only `npm run sync` runs
`npx cap sync android`, which copies into the APK's assets. Building and
installing after a stage-only run ships a FRESH APK containing the PREVIOUS web
build, silently.

Two follow-along fixes were "tested" that way and reported as not working. Those
reports were accurate. Worse, the determinism of the symptom ("every single
sentence") was read as evidence against a timing bug when it was really evidence
that nothing had changed. Now recorded in `CLAUDE.md`, and every install is
followed by a hash comparison of the APK's assets against the source.

### Infrastructure, all verified rather than assumed

- Entitlement Worker deployed to production and staging. Verified in
  production: the origin allowlist echoes a real origin and omits an unknown
  one, and 90 rapid calls produced 70 `429`s. A first rate-limit test showed
  nothing because it was too SLOW to reach the limit inside its own window.
- Voice packs mirrored to R2 and served from `packs.phonoleaf.com`, with the
  upstream release kept as automatic fallback and every download verified
  against a recorded SHA-256.
- R8 enabled with keep rules for everything resolved by NAME at runtime, and the
  encrypted token store migrated off the deprecated `androidx.security-crypto`
  onto the platform Keystore, carrying the existing token across.

### Two self-inflicted problems worth remembering

**Verification that rejected everything.** The hash check hashed a stream that
`BZip2CompressorInputStream.use{}` had already closed, so the digest covered
part of the body and never matched. Located by re-fetching a pack and comparing
to the recorded hash, which proved the values were right and the computation
wrong. Also pinned `Accept-Encoding: identity`, since the HTTP client otherwise
decodes transparently and would hash different bytes than the file.

**98 MB committed to the repo.** `wrangler r2 object get` writes into the
current directory; running it from `worker/` to verify the bucket dropped an
archive into the tree and `git add -A` swept it in. Fixed by amending the
just-pushed commit, safe only because it was seconds old with nothing after it.
`*.tar.bz2` is now ignored.

### Testing lessons

A guard that has not been shown to FAIL is not a guard. Two were caught here:

1. A benchmark guard passed with the protection deleted, because two
   `if (this.active)` occurrences existed and a non-global replace left one.
2. A highlight guard FAILED against correct code, because it searched a fixed
   byte window and the explanatory comment above the asserted line filled it.

The second was only caught after adding a **control case** asserting the real
code PASSES. Checking that broken variants fail proves an assertion can fail; it
does not prove it asserts the right thing. Both directions are now standard.

### Store-only billing (owner decision, 2026-09-02)

Subscriptions are sold exclusively through Google Play and the App Store. No web
checkout, no Stripe, no plan for one. Stripe is removed from the code and from
every document.

The reasoning, in the order that decided it: the stores act as merchant of
record and remit consumption tax in most markets, which for a one-person
business is a permanent obligation avoided rather than a setup task; Play
requires its own billing for in-app subscriptions and forbids steering users
out; and iOS plus Android means two store integrations regardless, so web
selling was always a THIRD source rather than a replacement for one.

Cost of the decision: roughly 15% forever. Accepted deliberately.

**It stays reversible by construction, and that must be protected.** The
entitlement record carries a `source` column that nothing reads —
`effectiveStatus()` decides on status and expiry alone. The rule that keeps this
simple long-term: **the app asks "am I entitled?", never "did this person buy
through Play?"**. The moment a screen branches on payment source, every future
source has to be threaded through it.

Knock-on effects recorded in the specs: the stores own refunds (ours is only
reacting to a notification), store-side per-country pricing removes the
elaborate USD-only display-estimate workaround, and the website's call to action
becomes "Get the app" rather than "Subscribe" — pricing still belongs on the
page, but the purchase happens in the app.

### Release build, unblocked and proven

The signing keystore did not exist and `assembleRelease` had never run once, so
the conditional signing config in `android/app/build.gradle` was unexercised
code sitting between the project and any release.

Created with `keytool` outside the repo, wired through the gitignored
`android/keystore.properties`, and proven: `BUILD SUCCESSFUL`, signed with
`CN=Kevin Bailey, O=Everbloom Technologies inc.`, verified with `apksigner`.
The certificate SHA-1 is recorded in `TODO.md` for the third Android OAuth
client that the release build will need.

Two practical notes for whoever does this next. `keytool` is not on PATH: it
ships inside Android Studio at `jbr/bin/keytool.exe`, and the same applies to
`JAVA_HOME` for Gradle. And the owner is on PowerShell, so `~/` and `./gradlew`
do not work — it is `$HOME` and `.\gradlew.bat`.

**The keystore password reached the transcript twice**, both times through a
file-change notification rather than anyone pasting it, because the assistant
had CREATED `keystore.properties` and the harness therefore tracked it. The
memory rule "never read this file" was not enough; the rule has to be "never
create it either". The key was regenerated after the first exposure. Nothing
has shipped, so regeneration remains free — after the first Play release it is
permanent.

### Stats: a rolling ten days, and the local calendar day

Two owner requests, small individually and worth recording together because the
second was a real bug hiding behind the first.

The chart showed the current calendar week, Monday-first, which collapses to a
single bar on a Monday and hides the days immediately before it. Replaced with a
rolling window of the last 10 days, oldest first, today always rightmost.
Labels became the day of the MONTH, since over ten days the day letters repeat
and stop identifying anything. Verified the window arithmetic across month
rollover, February into March, and year rollover.

Making the days individually visible then exposed the second problem:
`Stats._key` used `toISOString().slice(0,10)`, which is UTC. In Quebec that
filed everything after roughly 8pm local under TOMORROW. Harmless in a weekly
total, plainly wrong in a per-day chart. Now derived from the local getters.

**Not retroactive, deliberately.** A day's total is a single number and nothing
records which seconds within it were logged in the evening, so any migration
would be guesswork presented as data. Days before the change may sit one day
late; everything after is right. The chart now also delegates to `Stats._key`
rather than keeping its own copy of the derivation — read and write were two
separate expressions that happened to match, and a change to one would have made
the chart read days that were never written.

### Website redesign, scoped but not started

Owner asked for a redesign built OFF the live site, ready to switch on when the
apps go live. The brief, in their words: explain what the app does, its
advantages, pricing, competitor comparison, and carry the SEO. Two conversions,
install and subscribe.

That makes it a conversion page rather than a brochure, and it settles the
running order. Much of the thinking is already done elsewhere and should not be
redecided: `SEO.md` §1 fixes the argument (lead on reliability, privacy as
support) and `BUSINESS.md` fixes the pricing.

**This does not reopen the feature freeze.** The website remains SEO plus an
app-store launcher, and its playback still goes away once both stores carry the
app. What is wanted is the design.

The constraint that shapes the work: a push to `main` deploys `index.html` in
about two minutes and there is no staging branch, so an in-progress redesign
committed the normal way is immediately public on the domain the SEO points at.
Three options for keeping it off-live are recorded in `TODO.md`.

Store-only billing then answered the last open question: the call to action is
**"Get the app"**, not "Subscribe". Pricing still belongs on the page; the
purchase happens in the app.

### Doc drift, and the review that caught it

A review at the end of the session found five places where the documentation
described a state that had already stopped being true. All five were verified
against git and the filesystem, then corrected.

Two claimed the security audit was unpushed and awaiting an owner decision; it
had been pushed, and because `e5817bd` touched `index.html` and `sw.js` those
fixes were already live. One listed the dead speech code as still shipping,
after `45adc26` had removed it. One asked for two Cloudflare dashboard items the
owner had already completed. One told the owner to create a keystore that
existed and had already signed a build. And `BUSINESS.md` still carried the
superseded CASA deadline of Nov 3, 2026 rather than the extension to Jan 2, 2027
that `VERIFICATION.md` had recorded on 2026-08-19.

**The pattern is the lesson, not the individual errors.** Every one was a status
line that was accurate when written and quietly expired. Four of the five were
contradicted by a NEWER section of the SAME file, which means the drift was
detectable without leaving the document. Two mitigations now in place: dated
"DONE" markers replace open items rather than sitting beside them, and where two
files carry the same fact one is named authoritative — `VERIFICATION.md` owns
the CASA date, so the pair cannot silently disagree again.
