# PhonoLeaf — consolidated to-do

One place pulling together what's scattered across `BUSINESS.md`,
`PAYMENTS_SPEC.md`, `VERIFICATION.md`, `BACKLOG.md`, and conversation-only
threads. Not a replacement for those files (they carry the reasoning) —
this is the single "what's actually next" list. Update it as things move;
don't let it go stale the way `BACKLOG.md`'s old "Next up" section did.

---

## WHERE THINGS STAND (2026-09-01) — read this first

**Nothing an agent can build is on the critical path. Every launch blocker is
an owner action.** In order of leverage:

1. **The lawyer is ACTIVE and incorporation is in progress.** Owner
   confirmed 2026-09-01: they exchange email every 2-3 days. **Do not
   describe this as stalled, silent, at risk, or critical, and do not
   suggest replacing the lawyer.** Earlier entries in this file said "20
   days of silence" and agents kept escalating that stale line into a
   crisis. It was wrong. The engagement is working; incorporation simply
   takes the time it takes.
   Four questions ride along with it, all one conversation: (a) does
   espeak-ng's GPL reach the app given it ships in our APK, (b) does the
   separate-process architecture we built make that mere aggregation, (c)
   does the French voice's CC BY-SA ShareAlike reach us, (d) the ToS/Privacy
   review.
2. **CASA trigger date.** Five minutes. Deadline Jan 2, 2027.

**Settled 2026-09-01, stop raising it:** every store and console account
(Google Play, Apple) gets registered **through the corporation**, once
incorporation completes. There is no personal-versus-organization decision
left to make, and therefore no 14-day closed-test clock to worry about.

**Recently settled, do not reopen without new evidence:**

- The **engine architecture**. Synthesis runs in a separate `:tts` process
  behind AIDL so no PhonoLeaf code links GPL espeak-ng. Working on device:
  warm bind 0 ms, 0.29 realtime, audio identical. See `CLAUDE.md`'s Voice
  engine section and `ENGINE_NOTICE.md` before touching any of it.
- The **Kokoro device gate** is correct. Confirmed by ear, not benchmark.
- **Supertonic** was evaluated on device and rejected on quality.
- The **website** is SEO plus an app-store launcher, not a product. Its
  playback is removed once both stores carry the app. No further feature work.

**Best buildable work, if an agent needs something:** the pronunciation editor
(best-evidenced gap in the competitor research, still unscoped), or a
replacement Spanish voice with a clean licence.

---

## SECURITY / CODE / LICENCE AUDIT (2026-09-01) — 15 fixed, 6 remain

Full audit taking the software-engineer, security and licence-review angles at
once. 24 findings. Everything fixable without a device, a keystore or a lawyer
was fixed in the same session; the rest is listed below with why it is blocked.
All of it is committed AND PUSHED (see the end of this section).

### Fixed and verified

- [x] **The error contract that nearly cost Piper now has tests.** Four new
      assertions in `test/invariants.test.mjs` pin the whole chain:
      `TtsService.kt` still emits `err:notdownloaded:`, the plugin still matches
      it AND still translates it in **both** places (synthesize and prepare —
      the first cut-over missed prepare, so a one-sided fix looks correct), and
      the web layer still matches `PACK_NOT_DOWNLOADED:`. Plus the `/cancel/i`
      contract, which had survived only because "cancelled" contains "cancel".
      **Mutation-tested:** all six ways of breaking the chain, including a
      faithful replay of the 2026-09-01 regression, are detected. These are real
      guards, not presence checks that would pass against anything.
- [x] **The licence boundary is now a test too.** Any `com.k2fsa.sherpa.onnx`
      reference outside `TtsService.kt` fails the suite. That rule was only
      prose in `ENGINE_NOTICE.md` before, and it is the single most expensive
      thing in this repo to get wrong.
- [x] **Trial creation can no longer downgrade a paying customer.**
      `getOrStartTrial` read the record then wrote with `ON CONFLICT DO UPDATE`,
      which overwrites `status` unconditionally. A store webhook committing
      `active` between the read and the write was silently demoted to `trial`.
      Now uses a dedicated `DO NOTHING` insert then re-reads. The existing test
      named "never downgrades a paying subscriber" could not see this: it
      exercises the pure function, not an interleaving. A new test simulates the
      stale read directly.
- [x] **Entitlement JWTs carry a `kid`.** There was no key id, so a leaked
      signing key could only be replaced by invalidating every token at once,
      including 365-day lifetime ones. Read from the JWK; the keypair generator
      now stamps a date-based id into both halves. Free to do now because no
      JWT has ever been issued; a migration after launch.
- [x] **Wildcard CORS replaced with an origin allowlist**, echoing the caller
      and sending `vary: origin`. Note `https://localhost` is in the list on
      purpose: that is Capacitor's origin, so dropping it breaks the native app
      while leaving the website working.
- [x] **Clock-skew tolerance on Google ID token verification** (60s), plus an
      `iat`-in-the-future rejection. Without it a device with a slightly fast
      clock fails sign-in for no visible reason.
- [x] **Tar extraction is bounded.** Added a canonical-path traversal guard and
      a 512 MB ceiling. Stripping the leading directory did NOT make entries
      safe: `pack/../../../x` escaped the destination. Only reachable via a
      compromised upstream, but these archives are unsigned.
- [x] **`allowBackup="false"`.** Was backing the Drive access token in WebView
      localStorage up to Google's cloud, and worse, backing up
      `EncryptedSharedPreferences` whose Keystore key does not transfer between
      devices — the documented crash-on-restore pattern, hitting someone who
      just bought a new phone.
- [x] **`androidx.security:security-crypto` off alpha**, `1.1.0-alpha06` to
      `1.1.0`. Note the library is deprecated outright as of beta01; the bump
      removes the alpha exposure, the migration off it is below.
- [x] **FileProvider narrowed** from the whole external storage volume to the
      cache dir it actually uses. Stock Capacitor template value; nothing ever
      shared from external storage.
- [x] **`kokoro-js` pinned to 1.2.1** in both index files, from the floating
      `@1` range. A dynamic import cannot carry SRI, so the version pin is the
      only control available. Any future 1.x would otherwise have executed in
      the app's origin beside the Drive token.
- [x] **Licence record corrected and completed.** Restored the BSD-2-Clause
      header that minification stripped from `vendor/epub.min.js`; added
      `vendor/LICENSES.md` and `fonts/LICENSES.md`; added the four missing
      components to the Licences page in EN and FR — **espeak-ng (GPL-3.0)
      most importantly**, which ships in the APK and was not listed at all —
      plus ONNX Runtime, Commons Compress and AndroidX, and split the
      proprietary Play In-App Review SDK into its own section so the table is
      not read as "all open source". `sw.js` bumped to v60 for the changed
      precached asset.
- [x] **Corrected a stale doc claim:** espeak-ng issue #2131 (relicense to
      LGPL) was recorded here as open. It was closed `not_planned` in January
      2025. There is no upstream fix coming.

### Verified clean, so nobody re-audits them

Google ID token verification is genuinely correct (`alg` pinned to RS256, `kid`
matched, signature/exp/iss/aud/sub all checked). Every D1 query is
parameterised. All three services are `exported=false`. Output escaping is
disciplined across 39 `innerHTML` sites. **No secret has ever been committed** —
checked against full history, not the working tree. Commons Compress 1.28.0 has
no known CVEs. Third-party calls (Open Library, jsdelivr, HuggingFace) are
disclosed in all four privacy pages.

### Still open, and why

- [x] **DONE 2026-09-01. Root `LICENSE` added and the engine terms settled.**
      Counsel reviewed the architecture and confirmed the approach: the bridge
      is GPL-3.0 in its own directory (`android/tts-bridge/`) under its own
      package, and the root `LICENSE` is proprietary with that directory carved
      out. He also asked for, and got, the package rename and generic comments.
      His remaining caveat, recorded because it is the honest position: shipping
      both in ONE installation package leaves residual grey area, which is why
      the bridge must stay impeccably licensed. `npm test` enforces the
      structure. Original item below.
- [ ] ~~Add a root `LICENSE`, and decide the engine component's terms.~~
      **The sharpest legal finding, and deliberately not actioned by an agent.**
      `ENGINE_NOTICE.md` counts "published source" as one of three satisfied
      conditions. But the repo has NO licence file, so under default copyright
      `TtsService.kt` is all-rights-reserved: visible, but offered under no
      terms. If the aggregation argument fails, GPL-3.0 compliance requires
      that component to be *conveyed under GPL-3.0 with its licence text*, and
      being readable on GitHub does not do that. So the current position
      satisfies neither reading cleanly.
      **Put to the lawyer as a specific question:** should the engine component
      carry an explicit GPL-3.0 header, as a hedge that costs nothing if
      aggregation succeeds and matters greatly if it does not. Choosing a
      licence for the business is an owner call, which is why no file was
      created here.
- [x] **Verbatim licence texts DONE 2026-09-01.** Fetched from upstream with
      `curl` rather than retyped, so they are exact:
      `fonts/manrope-OFL.txt`, `fonts/literata-OFL.txt`,
      `vendor/epub.js-LICENSE.txt`, `vendor/jszip-LICENSE.txt`.
      Fetching also corrected two copyright lines that had been recorded
      approximately: the upstream notices are "Copyright 2018 The Manrope
      Project Authors" and "Copyright 2017 The Literata Project Authors", not
      the individual/company names first written down.
      **No staging change was needed** — `stage-www.js` copies `fonts/` and
      `vendor/` recursively via `DIRS`, so all four already ship. Verified by
      running `npm run stage` and listing `www/`.
      Note JSZip's upstream licence file contains BOTH the MIT and GPL-3.0
      texts because the project offers a choice. Shipping it is correct and is
      what upstream distributes; PhonoLeaf's election is MIT, recorded in
      `vendor/LICENSES.md`.
- [x] **Native CSP tightened 2026-09-01; dead code removed 2026-09-02.**
      `cdn.jsdelivr.net` and the HuggingFace hosts are GONE from
      `index.green.html`'s `script-src`/`connect-src`. That was the part with
      real security value: jsdelivr mirrors all of npm, so allowlisting it meant
      `script-src` no longer constrained what an injected script could load.
      Safe because the path is unreachable on native — `_synth` always takes
      `_synthNative` when the plugin is present, and `MainActivity` registers it
      unconditionally. Verified by reading the dispatch, not assumed.
      A guard was added to `TTS._synthKokoro` that rejects on native, so if a
      future refactor ever routed there it fails with a clear message instead of
      an opaque CSP violation inside a Web Worker (which, with no Web Speech on
      native, would present as playback silently stopping — the exact shape that
      cost a session already).
      **`index.html` deliberately UNCHANGED**: the website's fallback genuinely
      runs, so it keeps those hosts. Do not copy the native CSP into it.
      **DONE 2026-09-02 in `45adc26`.** The dead block is deleted: about 200
      lines covering `_kokoroOpts`, `_kokoroWorkerEl` (the worker source with
      the CDN import), `_benchCached`, `_kokoroBench`, `_applyBench`,
      `_synthKokoro`, `_wavBlob` and the worker state. `index.green.html` went
      599471 -> 591627 bytes and now has zero references to any of them.
      `_synth()` rejects with a clear error instead, since reaching that line
      would mean the plugin failed to register.
      KEPT, because the word Kokoro means two things here: `_benchKokoroGate`
      and the `_nativeBench*` family are the NATIVE model's gate and probe and
      run through the AIDL engine. Only the browser fallback went.
      `index.html` (the website) deliberately keeps its fallback; verified it
      still has it.
- [x] **DONE 2026-09-02.** All five packs mirrored to Cloudflare R2
      (`phonoleaf-voice-packs`, ~413 MB), served from `packs.phonoleaf.com`
      (min TLS 1.2), with the upstream release page kept as an automatic
      fallback. Every download is now verified against a recorded SHA-256, so
      falling back cannot smuggle in different bytes.
      **Cost question answered with real numbers:** R2 charges nothing for
      egress, which is the fee that makes this frightening elsewhere. Storage is
      413 MB against a 10 GB free tier; reads are 10M free/month then
      $0.36/million. Reaching $1,000 would take billions of requests, and
      Cloudflare's cache sits in front (verified `cf-cache-status: HIT`).
      **BOTH DASHBOARD ITEMS DONE by the owner, 2026-09-02.** A usage
      notification on R2 Class B operations at 1,000,000 reads (inside the 10M
      free tier, so it fires while the bill is still zero), and one WAF
      rate-limiting rule: URI Path contains `.tar.bz2`, 20 requests per 10s per
      IP, block. See the Cloudflare section below for the Free-plan constraint
      that shaped the rule and why the packs rule is the one to sacrifice if a
      zone rule is ever needed elsewhere.
      Original item below.
- [ ] ~~Mirror the voice packs and pin their hashes.~~ No integrity check exists
      on 65-140 MB archives fetched from a third-party GitHub release and fed to
      a native inference engine. HTTPS covers transit, so the real exposure is
      upstream mutation and, more likely, upstream disappearance: if that
      release is retagged or deleted, every voice download breaks for every user
      at once with no fallback. Mirror to R2, keep upstream as fallback. **Do
      this before charging anyone** — it converts an uncontracted supplier
      dependency into a storage line item.
- [x] **DONE 2026-09-02.** `SecureStoragePlugin.kt` rewritten onto the
      platform Keystore directly: AES-256/GCM, a fresh IV per write taken from
      the Cipher, `setUserAuthenticationRequired(false)` because background
      playback must read the token with the phone locked, and every failure path
      returning null so a lost key prompts a sign-in rather than crashing.
      **The migration is the load-bearing part and was verified on device:** the
      first `get` reads the legacy EncryptedSharedPreferences once, re-encrypts
      into the new store and clears the old copy. Logcat confirmed
      `migrated 'pl_rtoken' from the legacy encrypted store` with no sign-out.
      The deprecated library stays as a dependency for that one read only;
      drop it (and `legacyValue()`) once no installed device can still hold a
      legacy value.
- [x] **DONE 2026-09-02.** `minifyEnabled true` on release with keep rules in
      `proguard-rules.pro` for everything resolved by NAME at runtime: the
      sherpa JNI callbacks (highest risk — native code looks up Kotlin classes
      by exact name), Capacitor's annotation-driven plugin discovery, Tink's key
      managers, the manifest's service names, and SourceFile/LineNumberTable so
      Play Console crash reports stay readable. Release builds 56.2 -> 53.0 MB.
      Verified by temporarily minifying DEBUG (a release build is signed
      differently and would have needed an uninstall, destroying the owner's
      books and packs): clean launch, plugin bridge working, Keystore migration
      running, and the owner confirmed sign-in, Drive, playback, lock-screen
      audio and pack download/delete. That debug setting has been removed again.
      Original item below.
- [ ] ~~Enable R8 for release builds.~~ `minifyEnabled false` today, so once
      entitlement checks exist the paywall logic ships readable. Needs keep
      rules for the Capacitor bridge and registered plugins, and a real device
      pass, because plugin registration is exactly what minification breaks
      quietly. Pairs with the first `assembleRelease`, which has still never run.
- [x] **Rate limiting DONE 2026-09-01.** Turned out to be code plus config
      rather than dashboard-only: Cloudflare's Workers rate-limit binding is
      GA. `[[ratelimits]]` added to `wrangler.toml` for production and staging
      (separate namespaces, so a CASA DAST scan against staging cannot eat
      production's budget), and `withinRateLimit()` checks it in
      `handleEntitlement`.
      Two deliberate choices: it runs **before** token verification, since
      verifying is the expensive part being protected; and it **fails open** if
      the binding is missing, because it is a cost control rather than an
      authorization check and must not take the endpoint down on an older
      deploy. Keyed on IP at 60/60s, generous because NAT means users share a
      bucket. `wrangler.toml` validated as parsing; **not deploy-tested.**
- [ ] **Rotate the entitlement secrets at launch.** `worker/.dev.vars` holds a
      real signing key and pepper. Correctly gitignored and never committed, but
      development values must not become production values.

**PUSHED AND LIVE.** Corrected 2026-09-02: an earlier draft of this section
said the work was committed locally only and left the push as an owner
decision. That was already untrue when written. Commit `e5817bd` includes
`index.html` and `sw.js`, and it was pushed, so those changes deployed to
phonoleaf.com within about two minutes. Nothing here is waiting on anyone.

The point that line was reaching for is still worth keeping, just not as a
status: for the website a push IS the deploy, so any change to `index.html`,
`sw.js`, `home*.html`, a legal page or a comparison page goes public on push.

---

## VOICE MODEL LICENCES — checked 2026-08-31, two real problems

Owner called this a drop-everything question. It is not a full stop: the two
voices that matter most are clean. But two of the five Piper voices carry
terms that need a decision, and one may not be usable commercially at all.

| Component | Licence | Commercial | Note |
| --- | --- | --- | --- |
| Kokoro int8 en v0.19 | Apache 2.0 | Yes | Clean. This is the Upgraded voice. |
| Piper engine | MIT | Yes | Clean |
| en_US libritts_r | CC BY 4.0 | Yes | **Attribution required** |
| en_GB vctk | CC BY 4.0 | Yes | **Attribution required** |
| de_DE thorsten | CC0 | Yes | Clean, no conditions |
| fr_FR upmc | CC BY-SA 4.0 | Yes, but | **ShareAlike copyleft** |
| es_ES sharvard | CC BY 3.0 on the card | **Unclear** | **Fine-tuned from lessac** |

- [x] **es_ES sharvard REMOVED 2026-08-31, owner decision.** Its model card
      claims CC BY 3.0 but also says it was fine-tuned from lessac, which
      carries the Blizzard licence excluding commercial voice synthesis
      products. Removed from `ALL_PACK_MODELS`, the pack catalogue, the voice
      table, and both Kotlin maps, with a marker comment in
      `PhonoLeafTtsPlugin.kt` so nobody re-adds it without reading why.
- [ ] **Find a Spanish voice with a production-clean licence.** Wanted, just
      not at the cost of an unresolvable licence question. Criteria: a licence
      that clearly permits commercial use, and no fine-tune ancestry back to a
      research-only base model. Check the base model, not only the model card:
      sharvard's card looked clean until you read the line about lessac.
- [ ] **fr_FR upmc is ShareAlike, and French is a core market.** CC BY-SA 4.0
      requires derivative works to carry the same licence. Whether synthesized
      audio, or an app shipping the weights, counts as a derivative is exactly
      the question to put to the lawyer. Matters more than Spanish because
      Québec is a strategic market, not an afterthought.
- [x] **Attribution is now a legal obligation, not a nicety.** CC BY 4.0 on both
      **DONE 2026-08-31.** The Licences page ships in the app (EN and FR), lists
      every component with its licence and upstream, and is reachable at Settings
      then About then Licences.
      English Piper voices and CC BY-SA on French require credit. The app needs
      a credits surface naming each model, its source and its licence. See the
      About/credits split in the product ideas section.
- [x] **CONFIRMED 2026-08-31: espeak-ng (GPL-3.0) is statically linked into
      **ENGINEERING RESPONSE DONE 2026-09-01; the legal question remains.** The
      engine now runs in a separate `:tts` process behind an AIDL boundary, so no
      PhonoLeaf code links the GPL library. All three conditions the analysis
      named are met. The `.so` is still in the APK, so this is an improvement in
      position rather than a resolution. See the lawyer item below.
      the sherpa-onnx AAR you ship. Most serious licence finding in this set,
      and it needs the lawyer before any release.**
      Evidence, reproducible in a minute: `android/app/libs/sherpa-onnx.aar`
      has no separate `libespeak-ng.so`, but the strings `espeak-ng` and
      `espeak-ng-data` appear inside both `libsherpa-onnx-jni.so` and
      `libsherpa-onnx-c-api.so`, which ship in the APK. `gh api
      repos/espeak-ng/espeak-ng` reports GPL-3.0.
      **Why it outranks the CC questions:** GPLv3 statically linked into a
      proprietary app is the textbook propagation case. sherpa-onnx is itself
      Apache 2.0, but Apache is one-way compatible with GPLv3, so the
      combination lands on GPLv3 rather than cancelling out. Selling a GPLv3
      app is allowed; keeping its source closed is not.
      **Options, cheapest first, none costed yet:** build sherpa-onnx without
      espeak-ng if the models can phonemize another way; find a prebuilt
      espeak-free variant; change phonemizer; change engine. Note this is not
      a Piper-only problem, so dropping Piper would not obviously fix it.
      **Not legal advice.** Static vs dynamic linking and the scope of
      propagation are precisely what the lawyer is for. Ask it alongside the
      fr_FR ShareAlike question, since both are one conversation.

### Out-of-process engine: WORKS, measured on device 2026-08-31

The GPL isolation architecture is proven. Piper now runs in a `:tts` process
behind an AIDL boundary (`ITtsService`, `TtsService.kt`), talking only in
strings and primitives with raw audio written to a caller-chosen path.

| | cold | warm |
| --- | --- | --- |
| bind | 303 ms | **0 ms** |
| synthesis | 2312 ms | 1111 ms |
| realtime factor | 0.62 | **0.29** |

Audio is indistinguishable from in-process reading (owner: "sound amazing").
0.29 realtime is well inside the existing 25% headroom rule, so the boundary
costs nothing that matters. Cold cost is a one-off 303 ms bind plus the model
load, paid again only if Android reclaims the process.

- [x] **CUT OVER DONE 2026-09-01. The plugin no longer links sherpa.**
      `synthesize()`, `ensureReady()`, the five sherpa imports and the
      resident-model fields are all deleted from `PhonoLeafTtsPlugin.kt`;
      `grep com.k2fsa` on that file now returns nothing. The IPC method took
      over the name `synthesize` and returns the same fields the web layer
      already read, so `index.green.html` needed no change at all.
      Three things had to cross the boundary that the first cut missed:
      `prepare()` (warms a model), and the two places that invalidate a
      cached model after a pack is re-downloaded or deleted. `prepare` and
      `dropModel` were added to the AIDL for those.
      The Supertonic spike is deleted in the same pass: `SupertonicSpike.kt`,
      its plugin methods, its Settings row and sheet, and the
      onnxruntime-android dependency it needed.
      **Still true after the cut-over, and worth not forgetting:** the sherpa
      `.so` remains inside the APK, because both processes are one app. What
      changed is that our code no longer links or calls it. That moves the
      position from "clearly one combined work" to the arguable-aggregation
      one, which is an improvement rather than a resolution.
- [x] **Background position bug FIXED 2026-09-01. It was a race, and it was
      pre-existing, not the cut-over.**
      `_bgResync()` was only ever called from the `visibilitychange` handler,
      which bails unless `_nativeAppActive` is already true. That flag is set
      by Capacitor's `appStateChange`, and the two events have NO guaranteed
      order on resume. When visibilitychange won the race the resync was
      skipped and never retried, so the visible reader stayed on the page where
      the phone was locked, and the next `_persistPosition()` then overwrote the
      good background position with that stale one. Exactly the failure mode
      `CLAUDE.md` already warned about.
      Fixed by also resyncing from the `appStateChange` listener, so whichever
      event lands second does the work. `_bgResync` clears `_bgMode` through
      `skipPage`, so the loser becomes a no-op.
      The `_nativeAppActive` guard itself is deliberate and was left alone: it
      exists because the lock-screen media widget can flip visibilityState to
      visible while the phone is still locked.
- [x] **REGRESSION from the cut-over, fixed 2026-09-01: deleting a pack whose
      voice was selected killed the natural voice for the session.**
      Reported symptom: delete one pack, download another, press play, get
      "downgraded to Built-In", and then nothing plays at all.
      Cause: the JS identifies a missing pack by an error message starting
      `PACK_NOT_DOWNLOADED:`, and responds by switching to an installed voice.
      The cut-over changed that string to `err:notdownloaded:`, so the match
      failed and each attempt counted as an ENGINE failure instead. Two in a
      row sets `_kokoroDead`, which disables the neural voice for the session,
      and on native there is no Web Speech fallback, so playback simply stopped.
      Fixed by translating the service error back to the prefix the JS expects,
      in both `synthesize` and `prepare`.
      **The lesson, worth keeping:** the cut-over changed an error CONTRACT, not
      just an implementation. Error strings crossing that boundary are API. The
      other one (`/cancel/i`) survived only by luck, since `err:cancelled` still
      contains "cancel". Check consumers before changing any of them.

- [x] **Source publication: already satisfied, now made explicit (2026-09-01).**
      The repo is PUBLIC, so `TtsService.kt` and `ITtsService.aidl` were already
      published the moment they were pushed. What was missing was any way to
      tell that was deliberate, so `ENGINE_NOTICE.md` now sits beside them: why
      the boundary exists, the three rules for keeping it intact, upstream
      sources and licences, and an explicit statement that this improves the
      position rather than settling it.
      That completes all three conditions the legal analysis named: generic
      protocol, separate process, published source. **The remaining question is
      the lawyer's, not an engineering one:** whether one APK containing two
      separate programs is aggregation or a combined work.
### The Kokoro-only stack, and the gate that is blocking it (2026-08-31)

Owner asked whether dropping Piper removes espeak. **It does not:** Kokoro in
sherpa-onnx is also espeak-based (`PhonoLeafTtsPlugin.kt` sets
`dataDir = ifExists("espeak-ng-data")` on the Kokoro config, and the release
ships espeak-ng-data). Note the distinction that matters: the GPL problem is
the espeak CODE compiled into `libsherpa-onnx-jni.so`, not the data files, so
voice packs being downloads changes nothing about the licence.

**THE 2026-08-31 "CORRECTION" WAS ITSELF WRONG. RE-CORRECTED 2026-09-01 from a
real signed build.** It claimed the APK does not bundle 185 MB of voice models,
on the reasoning that the `assets/kokoro*` folders are gitignored local
leftovers. Gitignored they are. Packaged they also are.

Evidence, from `assembleRelease` run on the owner's machine and the resulting
APK opened and listed:

| Entry | Size |
| --- | --- |
| `assets/kokoro/en_US-libritts_r-medium.onnx` | 74.9 MB |
| `assets/kokoro-gb/en_GB-vctk-medium.onnx` | 73.4 MB |
| `assets/kokoro/espeak-ng-data/ru_dict` | 8.1 MB |
| `assets/kokoro-gb/espeak-ng-data/ru_dict` | 8.1 MB |
| **Total APK** | **221.7 MB** |

**This is a release blocker.** Google Play caps the per-device download from a
bundle at 200 MB. It is also pure waste: the app downloads these packs at
runtime anyway, so a user who installs would carry the model twice.

**Why both previous entries were wrong, which is the reusable lesson.** The
first claimed bundling from `du` on a working directory. The second denied it
from `.gitignore`. Neither opened the artifact. Gitignored means "not in the
repo", NOT "not in the build": anything sitting in `assets/` is packaged by
aapt regardless of git. And releases are built on this machine, which is
precisely where the leftovers live. **Check the APK, not the repo.**

- [x] **FIXED 2026-09-01, and guarded.** The two folders were moved off the
      build machine (to `~/phonoleaf-bundled-assets-backup/`, not deleted, in
      case anything wanted them; nothing did). Verified safe first: `ASSET_DIR`
      is only ever used as a folder NAME, and `TtsService` passes
      `assetManager = null`, so a bundled copy is never read even in principle.
      **APK went from 221.7 MB to 56.2 MB.** No `.onnx` remains inside.
      A Gradle guard now fails `packageRelease` if any `.onnx` appears under
      `src/main/assets`, naming the file and its size. Tested both ways: a clean
      build passes, and a planted 3 MB dummy model blocks the build.
      One wrinkle worth knowing if that guard is ever edited: the assets path
      must be resolved with `file()` at CONFIGURATION time and captured, not
      called inside the `doFirst` closure. Doing the latter fails under
      Gradle's configuration cache. That mistake was made and fixed here.

**But the owner's instinct points at a real destination: drop sherpa-onnx
itself, not just Piper.** That stack is fully permissive end to end:

| Piece | Licence | Status |
| --- | --- | --- |
| ONNX Runtime, used directly | Apache 2.0 | **Proven working on device by the Supertonic spike** |
| Kokoro | Apache 2.0 | Already a downloadable pack today |
| misaki-rs or similar G2P | MIT | Replaces espeak. Unvalidated. |
| Built-in device voice | n/a | Already shipped, already the fallback tier |

No GPL, no CC BY-SA, no sherpa fork to maintain. Packs are already downloads, so the APK is already small; what this
changes is which engine those downloads feed.

- [x] **VERIFY THE KOKORO GATE. This is now the highest-value cheap test.**
      **CLOSED 2026-08-31: the gate is CORRECT, do not reopen without sustained
      evidence.** Owner heard real Kokoro on a Pixel 7 read one or two sentences
      then stall ~10s, repeatedly. The agent had argued the gate was too strict
      from a one-shot 0.59 realtime measurement, which is exactly the quiet
      benchmark the code warns about. Only sustained reading counts.
      `_KOKORO_MIN_GFLOPS` is 5.0 and the Pixel 7 benchmarks at 2.47, so the
      app judges a Pixel 7 incapable of Kokoro. The Supertonic spike ran on
      that same Pixel 7: 99M params, four ONNX graphs, eight iterations of a
      244 MB estimator, at 0.59 realtime. Kokoro is 82M params in a single
      forward pass, far less work.
      The gate is a synthetic proxy calibrated from one device, and the spike
      is direct evidence the proxy is too conservative. Its deliberate margin
      (5.0 rather than 1.0, for CPU contention and prefetch slack) is sound
      reasoning, but a 2x margin on a mis-measured proxy is not.
      **CLOSED 2026-08-31: the gate is CORRECT. Owner heard real Kokoro on
      the Pixel 7 and it read 1-2 sentences, stalled ~10 seconds, then read
      1-2 more.** That is a real sustained-reading measurement and it settles
      it: a 2022 flagship cannot run Kokoro in realtime, so most devices
      certainly cannot. Kokoro-only is not a viable stack. Do not reopen this
      without new evidence of the same kind, meaning sustained reading rather
      than a one-shot timing.
      **Why the agent got this wrong, worth remembering:** it argued from the
      Supertonic spike hitting 0.59 realtime and concluded the device was
      clearly capable. That was ONE 7-second utterance in isolation, which is
      exactly the "quiet one-shot benchmark" this file already warns about two
      entries above. Sustained synthesis competes with rendering, prefetch and
      thermal throttling.
      **This also weakens the Supertonic timings recorded above:** they are
      one-shot numbers too. 0.59 at 8 steps is not evidence it can read a
      chapter, and 1.0 at 16 steps fails the existing 25% headroom rule
      outright.

      Superseded note: the app ALREADY measures Kokoro for real. `_verifyKokoro`
      runs a genuine synthesis and can demote a device, and the code says
      explicitly that a synthetic benchmark can disagree with real inference,
      which is why it exists. So "measure it directly" was not a new idea.
      **The narrow residual question is real though:** `screenDevice` is a HARD
      gate. A device that fails it is never offered Kokoro, so `_verifyKokoro`
      never runs on it. The real check can only demote, never promote. Nobody
      has therefore ever measured real Kokoro on a device the screen rejects. If a Pixel 7 comfortably beats realtime, the gate is wrong,
      most mid-tier phones can run Kokoro, and the Kokoro-only stack above
      becomes viable. That single number decides whether the GPL problem is
      solved by an architecture we can build or needs a lawyer to bless.
### Escape routes from espeak-ng, researched 2026-08-31

**Finding 1: stock sherpa-onnx cannot ship TTS without GPL code.** Its
CMakeLists has exactly one relevant flag, `SHERPA_ONNX_ENABLE_TTS`
(default ON), and when it is on it unconditionally pulls in espeak-ng and
piper-phonemize. Off means no TTS at all. There is no fine-grained option.

**Finding 2: the lexicon workaround does NOT fix it.** The community advice
(precompute phonemes into a lexicon so espeak is not called at runtime)
solves the wrong problem. GPL obligations attach to DISTRIBUTION, not use.
The code is still compiled into the .so in the APK whether or not it runs.
Both upstream threads on this conflate the two, and neither has a
maintainer answer: hexgrad/kokoro#247 and k2-fsa/sherpa-onnx#2260.

**Finding 3: Piper is structurally tied to espeak.** Piper voices are
trained against espeak-ng phoneme sets via piper-phonemize, so replacing
the phonemizer means reproducing espeak output exactly, or retraining.

**Finding 4: Kokoro has a real escape route, English only.** Kokoro is
Apache 2.0, and `misaki-rs` (MIT, self-contained, lexicons embedded at
compile time, no external GPL deps) is a G2P built specifically for Kokoro.

**The bind:** Kokoro is English only. French and German are Piper. So the
clean path is English-only, and there is no clean non-English path yet
identified. That collides with the Québec market the whole FR strategy
serves.

- [x] **Decide the engine path.** Options, roughly cheapest first:
      **DECIDED 2026-09-01: keep Piper and sherpa, isolate them in a process.**
      Every alternative below was eliminated. Kokoro-only fails on device speed,
      Supertonic failed on quality, a sherpa fork fails because Piper voices are
      trained on espeak phonemes, and two apps was rejected on UX. The answer was
      not a different engine but a different boundary.
      1. **Hybrid: Kokoro for English, OS voice for FR/DE.** No GPL anywhere.
         The built-in system voice tier already exists in the app as the
         fallback, so French keeps working at lower quality rather than
         disappearing. Cheapest by far and reversible.
      2. **Fork sherpa-onnx, strip espeak, drive Kokoro via misaki-rs.**
         Clean and English-only. Real C++/Rust/JNI work plus a fork to
         maintain forever.
      3. **Find a permissively licensed multilingual on-device engine.** Not
         yet researched; would solve it properly if one exists.
      4. **Licence a commercial TTS SDK.** Costs money, removes the question.
      5. **Comply with GPLv3 and open the source.** REJECTED by owner
         2026-08-31: the app stays closed.
      Confidence note: findings 1-3 are from primary sources (CMakeLists,
      the licence, the model cards). Finding 4 is from crate metadata and is
      NOT yet validated against a working Kokoro pipeline on Android.

### Engine options researched 2026-08-31 (owner asked for #3 and #4)

**Clarification that reframes this: Piper itself is NOT the GPL problem.**
The voice weights are CC BY / CC BY-SA / CC0 and the Piper repo is MIT. The
GPL comes only from espeak-ng, used by piper-phonemize for the text-to-
phoneme step. The catch is that Piper voices were TRAINED on espeak phoneme
sets, so they need espeak-compatible input, which is why espeak cannot just
be deleted and Piper kept as-is.

**Correction to the 2026-08-08 note in PhonoLeafTtsPlugin.kt:** Kokoro v1.0
upstream now covers 8 languages including French (`ff_siwis`). That note was
right about the sherpa-onnx assets at the time, but upstream moved. Caveat:
French is ONE voice on under 11 hours of training data, and the model card
itself calls non-English support thin with weak G2P.

| Candidate | Licence | Phonemizer | Languages | Note |
| --- | --- | --- | --- | --- |
| **Supertonic** | code MIT, model OpenRAIL-M | **none at all** | 31 | Best lead. No espeak, no phonemizer, no lexicon: text is NFKD-normalised through a Unicode index table, so the whole problem class disappears. OpenRAIL-M carries use restrictions that are NOT yet verified. |
| **Kokoro + misaki-rs** | Apache 2.0 + MIT | misaki-rs (MIT) | 8 incl. FR | Clean licensing. French is thin, and Kokoro is gated to strong devices, so it cannot replace Piper for most phones. |
| Commercial SDKs | paid | n/a | many | ReadSpeaker, Cerence, Acapela, CereProc. All enterprise sales, no public pricing. Acapela publishes a Google Play licence agreement, so it at least caters to app developers. |

- [x] **Supertonic OpenRAIL-M terms read 2026-08-31. Workable, with one
      condition that lands in the ToS.**
      Good: commercial use is permitted and you may charge for it; there is
      NO requirement to publish source or weights, so the app stays closed;
      attribution is required, which the licences page already handles.
      **The condition:** the use restrictions must be passed downstream as an
      enforceable provision, meaning our Terms of Service would have to bind
      users to them. That is a real obligation, not a formality. It folds
      into the ToS review already sitting with the lawyer rather than being
      a separate job.
      The restrictions themselves are the usual responsible-AI list (illegal
      use, harassment, impersonation, discrimination, medical or legal
      advice, undisclosed AI-generated content, and similar). None obviously
      conflicts with reading someone their own ebook aloud, and dropping MP3
      export helps here, since nothing leaves the device to be passed off as
      anything.
- [x] **Supertonic on Android: plausible, unproven, and you would be the one
      **CLOSED 2026-08-31: proven to RUN, rejected on quality.** It built and ran
      first try. The libonnxruntime.so collision resolved cleanly with pickFirst
      since both were 1.27.0. Audio through our implementation was bad and the
      cause was never found; probable root cause is that the inference contract
      came from a zero-star repo abandoned within half an hour of creation.
      proving it. Researched 2026-08-31.**
      **In favour:** the runtime is ONNX Runtime, which we already ship today
      inside the sherpa AAR, so the hard part is already solved. Supertonic is
      architecturally SIMPLER than what we run now: ONNX models plus a Unicode
      text-mapping step, no phonemizer, no lexicon, no native GPL dependency.
      There is a Kotlin Multiplatform wrapper targeting Android and iOS,
      `nedmah/supertonic-kmp`, and it is MIT.
      **Against:** Android is not an officially supported platform. Issue #51
      asks directly whether it works in a native Android app and has sat open
      with no maintainer reply since 2025-12-15. Other Android issues are open
      too (#193 streaming on Android, #45 Tauri Android integration fails).
      The KMP wrapper is not a dependency to build a business on: zero stars,
      created and last pushed on 2026-05-24 about half an hour apart, untouched
      since. Treat it as a reference implementation, not a library.
      **What this means:** the port is a real engineering project we own, not
      an integration. Budget it as such. The upside is that what we would own
      is simpler than the espeak-shaped problem we own today.
      **Cheapest way to de-risk it before committing:** build the ONNX models


- [x] **SPIKE RUN ON DEVICE 2026-08-31 (Pixel 7). Supertonic RUNS on Android.**
      Built and ran first try. The onnxruntime-android + sherpa AAR
      `libonnxruntime.so` collision resolved cleanly with pickFirst, both
      being 1.27.0. So the platform risk that looked biggest is retired.
      | steps | synthesis | realtime factor |
      | --- | --- | --- |
      | 8 | ~4.4s | 0.59 to 0.77 |
      | 16 | ~7.2 to 8.2s | 0.98 to 1.14 |
      | 32 | ~15s | 2.02 to 2.16 |
      Audio is 7244 ms against a predicted 7.19s, so duration, latent length
      and sample rate are all correct. Model load is consistently ~1.1 to 1.3s.
      **Native heap 448 to 454 MB, flat across step counts** (it is the weights,
      not the work). Java heap stays ~30 MB, which is exactly why native heap
      is the number to watch.
      **Peak sample 1.34 to 1.52 every run**, so the vocoder genuinely runs hot
      and any wav writer clamping at 1.0 distorts it. Normalisation is
      mandatory for this model, not a nicety.
- [x] **RESOLVED 2026-08-31: our implementation is wrong, the model is fine.**
      A/B against the published sample from the same model was decisive: ours
      is far worse, mispronounces, and degrades into a robotic lagging tail.
      **Correction to two earlier entries in this file:** the claim that the
      pipeline was verified because audio length matched the predicted duration
      was CIRCULAR. Latent length is derived from the duration and audio length
      from the latent length, so they agree by construction and prove nothing.
      That false confidence ruled out precisely the area the symptoms point at.
      **Two real suspects, both in what was wrongly cleared:** the tokenizer
      (mispronunciation means wrong token ids) and the duration or masking (a
      degraded tail is what an overlong, unconditioned latent sounds like).
      **Probable root cause:** the contract was derived from
      `nedmah/supertonic-kmp`, a zero-star repo created and abandoned within
      half an hour. It was treated as a specification when it is an unvalidated
      weekend project. If this is ever revisited, port from the official
      supertone-inc implementation instead, and validate against the published
      audio samples before trusting anything.
- [x] **SIZE PROBLEM: Supertonic ships fp32 only, 380 MB. Possibly
      **MOOT: Supertonic rejected.** Kept only because the numbers are useful if
      anyone evaluates it again: 380 MB fp32 with no quantized variant published,
      448 MB native heap, and Play caps per-device download at 200 MB so it would
      have needed Play Asset Delivery.
      disqualifying as-is. Measured 2026-08-31 from the HF repo.**
      `vector_estimator.onnx` 244.7 MB, `vocoder.onnx` 96.7 MB,
      `text_encoder.onnx` 34.7 MB, `duration_predictor.onnx` 3.5 MB. No
      quantized variant is published: those four files are the only option in
      the repo.
      **For scale, our current packs are 67 to 80 MB each.** Supertonic is
      roughly 5x the largest thing we ship today, and the download flow was
      designed around ~78 MB.
      **The memory question is worse than the download.** A 244 MB fp32 model
      needs its weights resident to run. On a 4 GB mid-tier phone, which is
      exactly the hardware Kokoro already fails on, that risks the OS killing
      the app during synthesis. Untested, but it is the right thing to test
      first, ahead of quality.
      **Mitigation: quantize it ourselves.** int8 would land near 95 MB, in
      line with current packs. There is precedent: sherpa-onnx ships
      `kokoro-int8` rather than fp32, and ONNX Runtime has the tooling. But it
      is work we would own, and quantization can cost audio quality, which
      has to be re-checked afterwards.
      **So the order of questions changed:** size and memory now come BEFORE
      quality and licence, because a model that cannot load on a mid-tier
      phone fails regardless of how good it sounds or how clean its terms.
      into a throwaway Android test harness and synthesize one sentence. That
      answers feasibility in a day and needs no product work.

- [x] **380 MB CAN ship with the app, but not in the base module.** Checked
      2026-08-31. Google Play caps the per-device compressed download from an
      app bundle at **200 MB**. Anything larger must use Play Asset Delivery
      or Play Feature Delivery, and asset packs do not count toward that cap
      (max 100 packs per bundle).
      So the owner's instinct is right: an install-time asset pack downloads
      during installation from the store, not in-app, which is exactly the
      behaviour wanted. It is standard, supported, and removes the language-
      pack UX entirely. It is also real work: asset packs are a build-system
      change, not a matter of dropping files into assets/.

- [x] **NEW ESCAPE ROUTE: a separate TTS engine app. This is how competitors
      **SUPERSEDED 2026-09-01.** Rejected on UX (nobody installs two apps), but it
      pointed at the architecture that worked: the same process isolation, inside
      one app, which is what shipped.
      ship Piper without open-sourcing themselves.** Android treats TTS
      engines as standalone installable apps that any app can call through the
      standard `TextToSpeech` API. A GPL engine living in its own app, spoken
      to over IPC, is a separate program rather than code linked into ours, so
      the copyleft does not reach us. There is already an eSpeak NG engine app
      published on Google Play built exactly this way.
      That also answers the owner's question about @Voice: it does not embed
      espeak at all, it calls whichever engine the user has installed. Its own
      code stays closed because the GPL part is somebody else's app.
      **Why it is a fallback and not the plan:** it requires the user to
      install a second app before good voices work, which would wreck
      onboarding for a paid product. We already have the plumbing, since the
      Built-in tier uses this exact API.
      **CORRECTED 2026-09-01: the LGPL relicensing route is CLOSED, not open.**
      This previously said espeak-ng issue #2131 (asking upstream to relicense
      to LGPL, which would dissolve the whole problem) was "Open, not resolved,
      do not plan around it". Checked directly: it was closed as `not_planned`
      in January 2025, within hours of being opened, and espeak-ng remains
      GPL-3.0. The instruction not to plan around it was right; the status was
      wrong. There is no upstream fix coming, so the process boundary plus the
      lawyer is the entire answer. Do not revive this as a reason to wait.
- [x] **Kokoro French is now a real fallback worth keeping in view.** Kokoro
      **DEMOTED to a note.** It was only interesting as part of a Kokoro-only
      stack, and that is dead on device speed. Kept because the fact is true and
      non-obvious: Kokoro v1.0 does cover French, contrary to the 2026-08-08 note.
      v1.0 covers French (`ff_siwis`, Apache 2.0), which the 2026-08-08 note
      said did not exist. Paired with misaki-rs (MIT) it is a fully
      permissive French path with no espeak and no CC BY-SA. Two caveats: it
      is one voice trained on under 11 hours, and Kokoro only runs on devices
      that pass `_KOKORO_MIN_GFLOPS`, so it cannot serve French on mid-tier
      phones. Useful as a component of a mixed answer, not as the answer.
- [x] **Benchmark Supertonic quality and speed against Piper** on a mid-tier
      **MOOT: Supertonic rejected on quality before a fair benchmark was possible.**
      phone before committing. 99M parameters against Piper medium is not a
      like-for-like comparison, and the whole product positions on the voice
      not being robotic.
Sources: hexgrad/Kokoro-82M on Hugging Face (Apache 2.0), rhasspy/piper-voices
MODEL_CARD files per voice, and rhasspy/piper discussion #271 on licensing.

## WEBSITE REDESIGN, BUILT OFF-LIVE, READY FOR LAUNCH DAY (owner, 2026-09-02)

Redesign the website so it is ready to go live the moment the apps do. Build it
in a **test environment, not on the live site**, and switch over at launch.

**This does not reopen the feature freeze.** `CLAUDE.md` and `BUSINESS.md` still
stand: the website is SEO plus an app-store launcher, its playback is removed
once both stores carry the app, and no product features get ported to it. What
is wanted here is the DESIGN, so a visitor arriving from a store listing or a
search result meets something that looks like a finished product.

**Why off-live matters.** A push to `main` deploys `index.html` to
phonoleaf.com in about two minutes. There is no staging branch, so any
in-progress redesign committed to `main` is immediately public, half-finished,
on the domain the SEO work points at. Whatever approach is taken has to keep
the work off the live site until the switch.

Options, cheapest first, none chosen yet:
  1. A separate file (e.g. `home-v2.html`) developed and reviewed in place, then
     promoted to `home.html`/`index.html` at launch. No infrastructure, but it
     IS reachable by URL once pushed, so it must not be linked or indexed
     (`noindex`, and keep it out of `sitemap.xml`).
  2. A branch that is never pushed to `main` until launch day. Keeps it fully
     private, at the cost of the parallel-work risk `CLAUDE.md` already warns
     about.
  3. A Cloudflare Pages preview or a second Worker on a subdomain. Cleanest
     separation, most setup.

**SCOPE SETTLED by the owner, 2026-09-02.** The site exists to explain what the
app does, its advantages, pricing, and how it compares to competitors, and to
carry the SEO. The visitor has exactly two things to do: **install the app** and
**subscribe**. Everything on the page should serve one of those or serve search.

That makes it a conversion page, not a brochure, and it sets the running order:
what it does, why it is better, what it costs, how it compares, then install.
The existing SEO work already decides the argument — `SEO.md` §1 says lead on
RELIABILITY (the app does not stop, does not lose your place, keeps playing with
the screen off), with privacy as support rather than the headline. Pricing is
already decided in `BUSINESS.md`: $5.99/mo, $49.99/yr, 7-day trial, one tier,
with the better voice a free upgrade on capable devices rather than a paid tier.

Still open: whether the ten comparison pages get restyled with the home page or
follow later, and whether the app's Green Ink system carries over to the web.

**A CONSTRAINT ON THE WORD "SUBSCRIBE", checked 2026-09-02.** Google Play
requires Play's own billing for digital subscriptions consumed inside an Android
app, and an app may NOT steer users to an outside payment method. Alternatives
exist only under specific regional programmes (EEA/DMA, US, India, South Korea).

What this does and does not mean:
  * The WEBSITE may show pricing and sell a subscription. The restriction is on
    what the APP does, not on marketing outside it.
  * The ANDROID APP must offer Play Billing for anyone subscribing from inside
    it, and must not link out to a web checkout.
  * Honouring a subscription bought on the web is the grey area, and it is
    exactly the kind of judgement to put to the lawyer already engaged rather
    than to settle by reading a policy page.

**RESOLVED 2026-09-02: store-only.** The site's call to action is therefore
**"Get the app"**, not "Subscribe". Pricing and what each plan includes still
belong on the page — that is a large part of why the page exists — but the
purchase itself happens in the app, through Google Play or the App Store.
Design accordingly: the page sells the decision, the store takes the money.
See `PAYMENTS_SPEC.md` §4.

**Sequencing.** Not blocking anything, and the trigger is the store release, so
it can wait for incorporation to clear. Worth starting before then only because
launch day is a bad time to be designing.

## CLOUDFLARE SETUP AS IT STANDS (2026-09-02)

Recorded so nobody re-derives it, and because one constraint has a trap in it.

**Live and verified:**
- Entitlement Worker deployed to production and staging, each with its own D1
  database and its own rate-limit namespace. Verified in production: health
  endpoint responds, the origin allowlist echoes `phonoleaf.com` and omits the
  header for an unknown origin, and 90 rapid calls produced 70 `429`s.
- `packs.phonoleaf.com` serves the voice packs from R2 (min TLS 1.2), with
  Cloudflare's cache in front (`cf-cache-status: HIT` observed).
- A usage notification on R2 Class B operations at 1,000,000 reads — inside the
  10M free tier, so it fires while the bill is still zero.
- One WAF rate-limiting rule: URI Path contains `.tar.bz2`, 20 requests per
  10s per IP, block.

**THE TRAP, and the answer to it.** The zone is on the **Free plan**, which
allows exactly **ONE** rate-limiting rule, Path-or-Verified-Bot matching only,
per-IP only, and a fixed 10s window and 10s block. That rule is now spent on
the voice packs. The obvious worry is needing another for payments later.

**It is very likely a false worry.** The entitlement Worker does NOT use a zone
WAF rule: it uses the Workers rate-limiting binding (`[[ratelimits]]` in
`worker/wrangler.toml`, checked in `withinRateLimit()`), which is a different
mechanism and does not consume the zone's single rule. Payments are therefore
already rate limited, and adding more there costs nothing from this budget.

So only a rule needed on a *zone route* would compete. If that ever happens,
**sacrifice the packs rule rather than upgrading.** It is the weaker of the
two by a wide margin: R2 charges nothing for egress, reads are 10M free then
$0.36/million, the cache absorbs repeats, and the Free plan can only block for
10 seconds anyway, so a determined caller simply waits. The billing
notification, not the rule, is what actually protects against a surprise.

## WHEN INCORPORATION COMPLETES — do these in this order

One event unblocks all of it. Collected here so nothing is rediscovered late.
Incorporation is in progress with the lawyer (email every 2-3 days as of
2026-09-01); this is a waiting list, not a problem list.

- [ ] **Change the copyright holder in both licence files.** Today they name
      **Kevin Bailey** personally, which is correct while no company exists:
      copyright vests in the author until it is assigned. Once the corporation
      exists, assign it and update the notices to the company name.
      Two files, one line each:
      - `LICENSE` (repo root) — the proprietary notice, first line.
      - `android/tts-bridge/LICENSE` is the GPL-3.0 text itself and must NOT be
        edited. The copyright line to change is in the header of
        `android/tts-bridge/java/com/phonoleaf/ttsbridge/TtsService.kt` and
        `android/tts-bridge/aidl/com/phonoleaf/ttsbridge/ITtsService.aidl`.
      Ask the lawyer to confirm the assignment is documented, since an
      assignment that is only implied is the kind of thing that surfaces during
      due diligence years later. `LICENSE` already carries a note saying this
      change is expected.
- [ ] **Transfer the release keystore to the company as an asset.** The signing
      key is created before incorporation (deliberately: it is a cryptographic
      file, not a legal document, it is tied to no entity, and waiting would
      only delay proving the signed build works). Once Everbloom exists it
      should be listed among the assets assigned to it, alongside the
      copyright, rather than staying informally personal property.
      Nothing technical changes: the same file keeps signing the same app. This
      is a paperwork item for the lawyer, and it belongs in the same assignment
      conversation as the copyright above.
      Note the certificate's name fields (organisation, city, and so on) are
      NOT worth revisiting: they are self-signed, never displayed by Google
      Play, and never verified by anyone. Only the file and its password matter.
- [ ] **Register the Google Play Console account under the company.** Decided
      2026-09-01: organization, not personal. No 14-day closed-test clock
      applies (that is a personal-account rule).
- [ ] **Then create the third Android OAuth client** for the release keystore's
      SHA-1, with "Enable custom URI scheme" ticked under Advanced Settings.
      Its absence has already cost one debugging session.
      **The fingerprint is already known** (captured from the real signed build,
      2026-09-01). Google Cloud Console wants the colon-separated form:
      ```
      78:E2:85:55:D4:C6:41:78:E9:CC:61:A0:B1:EE:71:A8:39:68:78:68
      ```
      Package name is `com.phonoleaf.app`. This is a public certificate
      fingerprint, not a secret; the private key it belongs to never leaves the
      keystore.
      **If the keystore is ever regenerated this value changes** and must be
      re-read with
      `keytool -list -v -keystore <path> -alias phonoleaf`. It already changed
      once, on 2026-09-01, when the first key was replaced.
- [ ] **Register the Apple Developer account under the company.** Same
      reasoning, decision already made (wait for the corporation).
- [ ] **Bank account, then GST/QST registration**, in that order. Both stores
      pay out to a bank account, so this is still the gate even with no Stripe.
      `BUSINESS.md` "Gating, do now" #2.
- [ ] **Then payments become buildable**, and the CASA assessment can be
      submitted once against the finished architecture rather than twice.

## Blocked on external people/hardware (nothing to build until these move)

- [~] **Business registration (REQ, Québec)** — **in progress and moving.**
      The lawyer is actively working on incorporation, with email every 2-3
      days (owner, 2026-09-01). It still gates the bank account, GST/QST
      registration and store payout setup, in that order, so it remains the
      sequence everything commercial waits on. That is normal sequencing, not a
      problem to solve. `BUSINESS.md` "Gating, do now" #2.
- [x] **[SWOT] "Chase or replace the lawyer" — CLOSED 2026-09-01, it was
      based on a false premise.** This item claimed 20 days of silence and
      recommended lining up a second lawyer. The owner corrected it: the
      engagement is active with email every 2-3 days. **Do not re-raise
      this, and do not treat lawyer turnaround as a threat.** Kept rather
      than deleted as a caution: a stale status line in a doc got repeated
      and escalated by successive agents until it read as a crisis. Check
      a claim about a PERSON with the owner before building on it.
- [~] **Lawyer review of ToS/Privacy** — same engagement, in progress
      alongside incorporation. `BUSINESS.md` §3.
- [ ] **iOS engineering** — the M1 MacBook Air was acquired 2026-08-29 (the
      earlier purchase that fell through 2026-08-10 was replaced). No
      longer hardware-blocked: a walkthrough was given the same day, but no
      iOS platform exists yet in this repo (`@capacitor/ios` isn't even a
      dependency) — Apple Developer enrollment and `npx cap add ios` are
      still the next actionable steps, just no longer waiting on hardware.
- [ ] **CASA AL1 assessment** — parked by design until the payments
      backend is finished, so it's assessed once. Deadline **Jan 2, 2027**
      (extended from Nov 3, 2026). Package is with Eydle, paid, not yet
      submitted as complete. `VERIFICATION.md`.

## Decisions needed (owner call, not code)

- [x] **Play Console account type — DECIDED 2026-09-01: organization,
      through the corporation.** Owner confirmed every store and console
      account (Google Play, Apple) gets registered under the company once
      incorporation completes. This also means **no 14-day closed-test
      clock**: that requirement applies to personal accounts only, so the
      calendar risk previously logged here does not exist.
      Practical consequence: the Play Console account cannot be created
      until incorporation finishes, so anything downstream of it (store
      listing, internal testing track, the release SHA-1 OAuth client) waits
      on that and nothing else. `PAYMENTS_SPEC.md` §11 #5.
- [x] **Pricing model: keep one paid tier, or split Standard/Upgraded?**
      > ✅ **DECIDED 2026-08-31 (owner): ONE TIER, provisionally.** Keep the
      > committed $5.99/mo, $49.99/yr, and treat Kokoro as a **free quality
      > upgrade** on devices that pass the benchmark, which is exactly how it
      > is already built. No new billing work, no tier a French user or a
      > mid-range phone can never buy. The Standard/Upgraded split below is
      > **rejected**, for the reasons in the reopening note and in
      > `BUSINESS.md` §1.
      >
      > **Owner framed this as "for now", so here is what would reopen it.**
      > The decision rests on two facts, not on a preference, and it should be
      > revisited if either stops being true:
      > 1. **A Kokoro model gains French (or German/Spanish) coverage.** Today
      >    none exists. This is the constraint that makes a paid Upgraded tier
      >    unsellable to the entire Québec market.
      > 2. **The `_KOKORO_MIN_GFLOPS` gate lands materially lower** after the
      >    borderline-device testing in this file gives it a second
      >    calibration point. It is currently 5.0 against a Pixel 7's 2.47, so
      >    a paid Kokoro tier would exclude most real phones.
      >
      > **The condition this decision depends on:** $5.99 is defensible for
      > the *product*, not for the voice, so the positioning has to price the
      > product. That is what the 2026-08-30 reliability reframe does, since
      > it leads on what every user gets regardless of engine. **Do not let
      > "upgraded voice" become the headline claim anywhere**, or the gap
      > between promise and delivery reopens.
      >
      > Historical note, kept because it explains the two conflicting records
      > below: the 2026-08-28 leaning was toward the opposite conclusion, and
      > was reached without the two facts above. See `BUSINESS.md` §1.

      > ⚠️ **REOPENED 2026-08-31 by new facts, not by opinion.** The leaning
      > below asks for "the actual unit-economics re-run" before it is final.
      > That re-run now exists in `BUSINESS.md` §1 ("The Standard/Upgraded
      > tier split"), and it surfaces two constraints that appear nowhere in
      > the reasoning below, both properties of the Kokoro model rather than
      > of our code: **(1) Kokoro is English only** (no French/German/Spanish
      > model exists at all, confirmed by inspecting the release assets), and
      > **(2) it needs roughly 2x a Pixel 7** to pass `_KOKORO_MIN_GFLOPS`.
      > Taken together, "Standard free, Upgraded paid" means **a French user
      > can never pay, and most English users cannot either**, because the
      > only paid tier is one their device or language cannot reach. That is
      > not a margin question, it is close to zero addressable revenue, and
      > it would apply to the entire Québec market that Bill 96 compliance
      > exists to serve. The upsell-signal idea below inherits the same
      > constraint. **Do not implement against `PAYMENTS_SPEC.md` until this
      > is re-decided.** Two sessions were working in parallel on 2026-08-30
      > and 08-31 without visibility into each other, which is why this
      > analysis and the leaning below were produced independently.

      **Owner leaning (2026-08-28, ~95% confident, not fully final):
      Standard (Piper) free, Upgraded (Kokoro) paid** — the subscription
      should gate voice *quality*, not basic access, since Standard alone
      doesn't sound natural enough to be the thing people pay for. Still
      needs the actual unit-economics re-run (a free tier costs nothing
      directly since TTS runs on-device, but changes the funnel/conversion
      math) before it's final, and before implementing against
      `PAYMENTS_SPEC.md`'s entitlement design. **New follow-on idea, same
      call**: on a device that benchmarks as Kokoro-capable but is still on
      the free Standard tier, proactively prompt/promote the Upgraded
      voice more often than on a device that can't run it at all — turns
      `_KOKORO_MIN_GFLOPS` into an upsell signal. Not scoped or built;
      needs the paywall/entitlement system to exist first, and a decision
      on prompt frequency so it doesn't nag.
- [x] **KV vs D1 for entitlement generally.** **Decided 2026-08-28: D1.**
      Not actually a cost-vs-consistency tradeoff — D1 is both strongly
      consistent (no post-payment paywall-flash risk) and cheaper per
      operation than KV at any scale PhonoLeaf will hit for a long time.
      Full reasoning/pricing numbers in `PAYMENTS_SPEC.md` §13. **Fully
      migrated and live in production + staging** — see "D1 migration"
      below.
- [x] **Refund mechanics, lifetime shutdown reserve, trial abuse** — all
      decided 2026-08-28, `PAYMENTS_SPEC.md` §13 (now zero open items,
      nothing left blocking payments on the decisions front): refunds were to
      be manual — SUPERSEDED 2026-09-02, the stores own refunds now; lifetime reserve
      is a % of each sale held separately until that sale's 12-month
      window closes — mechanism decided, exact percentage is an ongoing
      operational call for the owner to make with the bank/accountant
      once the business account exists, not a pre-decidable engineering
      blocker; trial abuse accepted as-is at launch, mitigation deferred
      post-launch (see below).

## D1 migration (2026-08-28 — done, live in production and staging)

The entitlement Worker was already built and deployed (`worker/`, per
`PAYMENTS_SPEC.md` §9) on Cloudflare KV, holding no real entitlement data
yet (nothing calls it from the app), so this was a clean code swap, not a
data migration. **KV fully removed from the codebase** — no
`[[kv_namespaces]]` blocks, no `env.ENTITLEMENTS` references left
anywhere in `worker/`.

- [x] D1 schema designed (`worker/migrations/0001_create_entitlements.sql`)
      — one `entitlements` table, same columns as the old KV record.
- [x] `worker/src/entitlement.js` rewritten off `env.ENTITLEMENTS.get/put`
      onto D1 prepared statements — every query parameterized (`?`
      placeholders only, `PAYMENTS_SPEC.md` §7's rule), verified
      mechanically in a test harness, not just by eye.
- [x] `worker/wrangler.toml` updated: both `[[kv_namespaces]]` blocks
      (production + `env.staging`) replaced with `[[d1_databases]]`
      bindings (`binding = "DB"`); `workers_dev`/`preview_urls` made
      explicit at the same time (production: workers.dev on, Preview URLs
      off; staging: neither, custom domain only).
- [x] Every KV reference swept from `worker/` (README, code comments,
      `PAYMENTS_SPEC.md` §2); `db:migrate:local` / `db:migrate:remote` /
      `db:migrate:remote:staging` npm scripts added.
- [x] Verified locally against D1 emulation (insert/select/upsert SQL,
      `wrangler dev` health check, a Node harness against the real
      `entitlement.js` functions with a mocked D1 API).
- [x] Real D1 databases created 2026-08-28: production
      `phonoleaf-entitlement` (`b5c17050-89e4-490d-98d4-10d8ab3dcaf8`) and
      staging `phonoleaf-entitlement-staging`
      (`4b5de02b-d72d-41e3-9cbb-bc2932eca035`), both ids in
      `worker/wrangler.toml`.
- [x] Migration applied to both real databases (staging needed
      `--env staging` — its D1 database lives under
      `[env.staging.d1_databases]`, documented in `worker/README.md` so it
      doesn't trip anyone else up) and both redeployed. **Fully live** —
      production and staging both run on D1.
- [ ] **[SWOT] Set a decision date on the CASA sequencing, e.g. Oct 15.**
      The current plan (park CASA, build payments, submit once) is sound
      and remains the preferred path, but it is chained to a lawyer who has
      not replied, and the deadline is Jan 2, 2027, which is 125 days out
      as of 2026-08-30. Pick a date now: if business registration has not
      landed by then, submit the assessment against the current no-backend
      architecture and budget for the second assessment plus the DAST scan
      later (Eydle has already called adding a backend a likely significant
      change, `VERIFICATION.md` 2026-08-14). Deciding the trigger in
      advance is the whole point, since the failure mode is an open-ended
      wait with no ceiling on the downside. `SWOT.md` Threats,
      recommendation 1.

## Actionable now, no blockers

> The first three below are the ones `SWOT.md` argues should jump the
> queue. They are fully unblocked today, and two of them are the reason a
> finished product currently cannot reach a single user.

- [~] **[SWOT] Make a release build possible at all.** **Code half done
      2026-08-30; the two owner steps remain.** `android/app/build.gradle`
      now reads signing credentials from an untracked
      `android/keystore.properties` and registers the release
      `signingConfig` only when that file exists, so a fresh clone and every
      debug build still work untouched. `.gitignore` now excludes `*.jks`,
      `*.keystore` and that properties file (verified with
      `git check-ignore`; nothing of the sort was ever tracked). A
      versioning-scheme comment records the bump discipline.
      **NOT verified by a real Gradle build** (no Android SDK in this
      environment), so run `assembleRelease` once before trusting it.
      **Still owed, both owner actions:** create the keystore with
      `keytool` and back it up off-machine (losing it means never being
      able to update the Play listing again), and register the Play Console
      account once the personal-vs-organization decision above is made,
      plus the third Android OAuth client for the release SHA-1 with
      "Enable custom URI scheme" ticked. Original finding:
      `android/app/build.gradle` is still `versionCode 1` with **no**
      `signingConfig`, and there is no keystore anywhere in the repo, so no
      signed APK/AAB can be produced right now. Three pieces, in order:
      (1) create the release keystore with `keytool` and keep it and its
      passwords out of git, (2) add the Gradle signing config plus a real
      `versionCode`/`versionName` scheme, (3) register the Play Console
      account ($25, identity verification takes days) once the
      personal-vs-organization decision above is made. Also still owed from
      `VERIFICATION.md`: a **third Android OAuth client** for the release
      keystore's SHA-1, with "Enable custom URI scheme" ticked under
      Advanced Settings, since its absence has already cost one debugging
      session. `SWOT.md` Weaknesses + recommendation 3.
- [x] **[SWOT] Converge the `index.html` / `index.green.html` fork.**
      **CANCELLED 2026-08-31 by owner decision, not done and not to be done.**
      The premise was that the website is a worse copy of the product and
      should catch up. The owner's call is that the website should not be the
      product at all: it is SEO plus a launcher for the phone apps, so a
      permanent fork costs nothing and convergence would buy nothing.
      Before the decision, four features were ported to the website by hand
      (sleep timer, in-book search, storage manager, mark-finished/forget plus
      export confirmation), and the website got a real desktop layout. Those
      stay: the desktop layout serves the SEO/launcher role directly, and the
      ported features cost nothing to keep now that they exist.
      **The website is frozen, not dead.** No new features, no parity work.
      It keeps earning its place three ways: the SEO surface, a no-install
      trial for people unwilling to grant Drive access to an unknown
      developer, and the only way an iPhone user can use PhonoLeaf until an
      iOS app exists.
      **DECIDED 2026-08-31: web playback gets REMOVED once both stores carry
      the app.** Not revisited, removed. The owner rejected the agent's
      "try before you install" framing: the website pushes the apps, and is
      not an alternative to them. Trigger is Android AND iOS live, so no
      visitor is ever left with a site pushing an app they cannot get.
      Until then, no further effort goes into the web app at all.
      Scope of the eventual removal, measured 2026-08-31: 30 pages carry an
      "Open PhonoLeaf" CTA pointing at `index.html`, 26 promise "Nothing to
      install", plus the sw.js precache, the legal pages' in-app wording, and
      a call on existing PWA installs. See `BUSINESS.md` "Platform strategy".
- [x] **[SWOT] Doc accuracy pass, so the docs stop over-reporting reality.**
      **Done 2026-08-30.** All three parts, plus four extra stale claims found
      on the way. Everything was verified against code before editing, never
      against another doc.
      - `CLAUDE.md`: the status line no longer says "real users" and the
        redesign section no longer says "what Play Store users get". Both now
        state the checked position: no Play release, OAuth still in Testing
        mode under its 100-user cap, nobody can pay yet.
      - `COMPETITORS.md`: the Kokoro claim was stale, but the correction is
        more nuanced than "it shipped" — it did ship 2026-08-08, yet the gate
        is 5.0 GFLOPS against a Pixel 7's measured 2.47, so most phones still
        get Piper. **Three further stale claims found and fixed**: local
        import and connect-a-folder shipped (so "Drive only" and "no local
        import yet" were both wrong, in the snapshot table and the gaps list),
        and follow along highlighting shipped (so the accessibility
        opportunity was describing built work). Strategic takeaway #2 named
        two gaps that have both since closed; it now names the three that
        actually remain. A cross-doc contradiction is flagged rather than
        silently resolved: takeaway #3 says lead with privacy, while
        `COMPETITOR_SWOT.md`'s review mining says lead with reliability.
      - **All five comparison page pairs corrected (10 files).** The Play
        Books page carried two claims that were not merely stale but false,
        each cited to Google's own documentation, which actually contradicts
        them: uploads DO get Read Aloud, and only the *natural* voice needs a
        connection. Both fixed in the table, the visible FAQ, and the
        JSON-LD, in both languages. Also: `voice-dream-alternative` gained
        the missing subscription-required and 3-day-trial facts plus a
        fairness section, since the page was implying a contrast on offline
        and background playback that Voice Dream genuinely has;
        `voice-aloud-alternative`'s voice row was misleading (users can
        install neural engines into it) and it had no price row at all, so
        the $15 one-time price is now stated; `elevenreader-alternative`
        gained the 60-day download expiry and the no-export restriction;
        `naturalreader-alternative` gained the paid MP3 export footnote.
      Verified after editing: JSON-LD parses in all 10 files, `<tr>` tags
      balance, EN and FR pairs match structurally, and `npm test` stays green.
      **Not done here**, since it is the separate marketing task: rewriting
      the pages to lead with reliability. `SEO.md` §6's own template asks for
      a "choose them if" fairness section on every comparison page, and only
      the two touched above have one.
      Original finding:
      Three concrete corrections found 2026-08-30: (1) `COMPETITORS.md`
      still says the better-voice-on-strong-devices feature is "not built
      yet" and "it is Piper for everyone today", which stopped being true
      on 2026-08-08 when the Kokoro gate shipped; (2) `CLAUDE.md` describes
      `index.green.html` as "what Play Store users get" and the project as
      having "real users", when there is no signed build, no Play Console
      account, and OAuth is still in Testing mode under its 100-user cap;
      (3) `COMPETITOR_SWOT.md`'s own six suggested follow-ups (factual
      fixes to `play-books-alternative.html` and three other comparison
      pages) are still unapplied. This matters beyond tidiness: planning
      against the optimistic reading produces the wrong priorities.
      `SWOT.md` Weaknesses + recommendation 5.
- [x] **[SWOT] Minimal regression test harness.** **Done 2026-08-30.**
      `npm test` now runs 48 assertions in `test/`, zero new dependencies
      (Node's built-in `node:test`), in about 0.4s. Four files:
      - `split.test.mjs` — real unit tests of `TTS._split()`, extracted
        from the shipped source by `extract.mjs` so the tests exercise the
        actual function rather than a copy that drifts. Includes explicit
        regression cases for the `|$` last-sentence bug. Runs against
        either side of the fork via `PHONOLEAF_APP_FILE` (verified passing
        against both `index.green.html` and `index.html`).
      - `invariants.test.mjs` — source-level assertions that the guards
        which cannot be unit tested still exist (blank-page skip cap,
        forward-only skipping, the stop-hastext path, the `_gen` snapshot
        pattern, `.view.minimized` staying `display:flex`, and no Kotlin
        caller using `startForegroundService`). These prove presence, not
        correctness; the file says so at the top so it cannot be mistaken
        for real coverage.
      - `worker-entitlement.test.mjs` — real unit tests of the trial state
        machine against a stub KV, including the three that protect money:
        a second lookup never restarts the trial, a paying subscriber is
        never downgraded to trial, and lifetime never expires.
      - `syntax.test.mjs` — folds `CLAUDE.md`'s documented manual pre-push
        syntax check into the suite so it runs automatically, covering both
        index files, `sw.js`, and all five worker modules.
      Note for whoever runs this: `node --test test/` (directory form)
      misresolves on this Windows setup, which is why the script uses the
      glob `"test/*.test.mjs"`. Two behaviours were documented rather than
      "fixed", per `CLAUDE.md`'s do-not-fix rule: `_split`'s chunk joiner
      emits double spaces between sentences (harmless to both engines, but
      anything tokenising on whitespace should filter empty tokens), and
      the `...` inside its character class is redundant with `.`.
      Original finding: There is currently no
      automated test of any kind; the only test files in the repo are
      Capacitor's stock `ExampleUnitTest.java` and
      `ExampleInstrumentedTest.java`, and verification is a
      `vm.compileFunction` syntax check plus manual device testing. Not a
      suite, just a few dozen Node assertions over the places where a
      regression is expensive and silent: `TTS._split()`'s sentence
      splitting (including the `|$` last-sentence case), the blank-page
      forward-skip cap of 20, the `TTS._gen` generation guard, and the
      worker's trial state machine. The obstacle is that the app code is
      one inline `<script>` with no module boundary, so these either get
      extracted or get exercised by string-loading the script in a `vm`
      context; decide which when starting. `SWOT.md` Weaknesses +
      recommendation 6.
- [~] **[SWOT] Reframe the marketing on reliability rather than privacy.**
      **Strategy + home pages done 2026-08-30; the comparison pages remain.**
      - `SEO.md` §1 now carries the revision and the reasoning, with the four
        review-sourced failure modes quoted. New §2 keyword cluster for
        failure-mode searches ("audiobook app keeps stopping when screen turns
        off", "app loses my place in the book", and so on, plus French
        equivalents). These have low volume individually but near-zero
        competition, because nobody writes pages targeting their own product's
        failures, and the searcher has already decided to leave a competitor.
      - The practical rule recorded there: **keep "turn any ebook into an
        audiobook" as the `<title>`/`<h1>` head term** and spend the lede,
        first features and meta description on reliability. Surrendering the
        category term to chase failure-mode phrases would trade a durable
        keyword for a niche one.
      - `home.html` and `home-fr.html`: meta and og descriptions, hero lede,
        and feature order all now lead with reliability, with privacy kept as
        the supporting paragraph rather than dropped. Four reliability
        features lead the grid. Verified: EN/FR structurally identical (8
        feature blocks, 2 ledes, 4 h2s each), divs balanced, descriptions 151
        and 147 chars. No `sw.js` CACHE bump needed, since `home.html` is not
        precached and HTML navigations are network-first.
      - `SEO.md` §9 has a new checklist for what remains.
      **Hero copy done 2026-08-31** for all six pairs (12 files), not five as
      this line previously said. Head term kept in `<title>`/`<h1>`; the h1
      qualifier, tagline, lede and both meta descriptions now lead on the
      failure modes. Meta descriptions retargeted to 133-146 chars after a
      first pass ran 196-266, which Google truncates around 155 and would
      have cut off the very lead being introduced.
      **DECIDED 2026-08-31: no "choose them if" sections.** The owner
      reaffirmed the 2026-08-17 rule, in their words: do not sugar-coat the
      competitors. `SEO.md` §6 and its checklist are corrected at the source,
      so the docs no longer contradict the instruction. The pages already
      complied, so nothing was removed.
      **Also done 2026-08-31: aimed each page at that competitor's own
      loudest complaint** rather than the generic four failure modes. Three
      of six were mis-aimed, and `voice-dream` was the worst: it led on
      background playback, which Voice Dream genuinely has and advertises,
      instead of the voice quality complaint that is exactly our strength.
      The per-page targeting table is in `SEO.md` §6.
      Also still open: one dedicated page for the reliability cluster.
      Original finding:
      `COMPETITOR_SWOT.md`'s strongest finding never reached the website
      copy: the highest-upvoted complaints across all six competitors are
      endless loading, losing your reading position, and playback dying
      when the screen locks, and PhonoLeaf's architecture fixes all three.
      Privacy is why people approve; reliability is why people switch.
      Touches `home.html`/`home-fr.html` and the comparison pages, and
      pairs naturally with the doc accuracy pass above since both edit the
      same marketing surface. `SWOT.md` Opportunities + recommendation 7.
- [x] **[SWOT] Prune stale branches.** **Done 2026-08-31.** Twenty remote
      branches down to four, and seven local down to two. Every deletion was
      verified with `git merge-base --is-ancestor <branch> origin/main` first,
      not by reading the branch name, so nothing unique was lost; the SHAs were
      recorded before deleting in case one is ever wanted back.
      **Kept deliberately:** `archive/hero-redesign-2026-08-28-branch` (the
      preserved archived fork), `claude/docs-code-review-tam2pr` (genuinely
      unmerged: its Phase 2/3 work was re-implemented by hand rather than
      merged, so git still sees unique commits, and its payments/D1 content was
      separately rescued in PR #13), and `claude/update-status-dooe1q`
      (unmerged, contents not investigated, so left alone).
      One trap worth remembering: `redesign/port-phase-2-3` had a local tip 8
      commits ahead of its own remote, which made `git branch -d` refuse even
      though every one of those commits was already in `origin/main`. That is
      also exactly what real unpushed work looks like, so check ancestry
      against `origin/main` before reaching for `-D`.
      Original finding: Thirteen remote branches as of
      2026-08-30, several already merged (`docs/stale-cleanup`,
      `feature/storage-*`, `fix/storage-finish-flow`,
      `redesign/converge-to-main`). Keep `archive/hero-redesign-2026-08-28-branch`
      deliberately (it preserves the archived fork). Small, but the last
      divergence incident came from exactly this kind of ambiguity about
      which branch is live.
- [ ] **Device-tier testing on borderline hardware (Android + iOS).**
      The Kokoro upgrade is gated by an on-device benchmark
      (`_KOKORO_MIN_GFLOPS`, calibrated off exactly one data point — the
      owner's Pixel 7). Needs testing on phones that sit NEAR that
      threshold, not devices already known to clearly pass or fail, to
      confirm: the gate lands on the right side for real mid-tier hardware,
      the "screen first, download once" flow feels good on a device that
      ends up on Piper, and Kokoro genuinely stays gapless on a device that
      qualifies. Needs real devices, not something buildable from here —
      but every real pass also produces a paired (GFLOPS, measured ratio)
      data point that improves the threshold calibration itself, so it's
      worth logging results back into `CLAUDE.md`'s Native Kokoro section.
- [x] **Competitor SWOT + app store review research.** Already done
      (`COMPETITOR_SWOT.md`, commit `96c1b75`) — full SWOT against all 6
      vetted competitors. Its "Suggested follow-ups" are also done as of
      2026-08-28 (5 of 6, all mechanical factual corrections to the live
      comparison pages — see "SWOT research" below); the 6th (whether the
      `@Voice`/Voice Aloud Reader page needs a different frame, since it's
      the one competitor whose positioning already overlaps PhonoLeaf's
      directly) is left as an open question, not mechanical.
- [x] **Internal SWOT of PhonoLeaf itself**, raised 2026-08-29, done
      2026-08-30. See the new `SWOT.md`. The open framing question
      (codebase-only vs. also business/market) was resolved as **product +
      codebase + business**, since the codebase weaknesses and the
      commercial blockers turned out to be the same story. Distinct from
      `COMPETITOR_SWOT.md`, which benchmarks outward against
      ElevenReader/NaturalReader/etc. Its closing recommendations are now
      folded into this file as tasks, tagged **[SWOT]** so they can be
      traced back to the finding that produced them.
- [ ] **App redesign exploration with the `design` skill.** Ready whenever
      wanted — needs a scoping conversation first (which screens/flows,
      what direction), not a blind "redesign everything" pass.
- [x] **Port Phase 2/3 features from the archived hero branch into the
      now-canonical `index.green.html`.** Done 2026-08-29: the motion/
      gesture CSS token system, a localized accessibility pass, the storage
      manager ("On this phone") screen, and in-book full-text search are
      all now in `index.green.html`, re-implemented by hand against the
      forest version's markup (not a git merge — see `CLAUDE_HISTORY.md`'s
      2026-08-29 entry). **Device-tested and passed 2026-08-31.**
- [x] **Stale doc cleanup**, surfaced 2026-08-20, done 2026-08-29:
      `BACKLOG.md` section H (Kokoro-on-strong-devices) no longer says "not
      implemented" — marked done as of 2026-08-08 with a pointer to
      `CLAUDE.md`'s Voice engine section. Section F's accessibility bullets
      now have DONE markers dated 2026-08-07. `BUSINESS.md`'s gating item 4
      no longer lists French legal pages as open — noted done, pointing at
      the live `terms-fr.html`/`privacy-fr.html`/`home-fr.html`. (This exact
      fix was originally written in the closed PR #4, which got archived
      rather than merged during the branch reconciliation — reapplied here
      from that diff, not redone from scratch.)
- [x] **Store review prompts**, done 2026-08-30 (Android only) — see
      `BACKLOG.md` section I. `StoreReviewPlugin.kt` wraps Google Play's
      In-App Review API, triggered from finishing a book (manual "Mark as
      finished" or reaching the actual last page while listening), gated
      by a local 60-day timestamp. iOS deferred (no `ios/` platform exists
      yet); no web fallback (no reviewable web listing to link to). Not
      **Device-tested and passed 2026-08-31.**

## Bug, just fixed, needs device confirmation

- [x] **Volume swings on native playback ("lasts about one sentence then
      goes back")** — reported 2026-08-20, still present after the first
      mitigation (lowering the gain cap), reported again 2026-08-21. Real
      fix now shipped: `writeWav`'s loudness correction used to be
      recomputed per sentence, which is what caused the swing; it's now
      calibrated once per model per session (average of the first few
      clips) and reused, so natural sentence-to-sentence dynamics survive
      instead of being erased. **Not device-verified — next drive is the
      real test.**

## Post-payments (correctly sequenced, not started)

- [ ] **Referral program** ("both get a free month") — needs the
      entitlement/subscription system to exist first, since a free month
      is a credit against a subscription that isn't live yet. Needs abuse
      prevention (one account referring itself via a second email) when
      designed for real.
- [ ] **KPI tracker** (trial→paid %, MRR/ARR, churn, ARPU, CAC) —
      `BUSINESS.md` roadmap item 8.
- [ ] **Support mailbox migration** off the personal Gmail forward — was
      deliberately deferred until OAuth verification fully resolved
      (2026-08-05); CASA being parked rather than resolved means this is
      still on hold, not forgotten.
- [x] **Automate refunds — DROPPED 2026-09-02, no longer ours.** Google Play
      and the App Store issue refunds under their own policies. What remains is
      REACTING to one: a store notification must move the entitlement to
      `none`, which is `/webhooks/play` and its Apple equivalent, already in
      the endpoint table. `PAYMENTS_SPEC.md` §4.
- [ ] **Trial abuse mitigation**, 2026-08-28 owner call — new Google
      accounts can restart the 7-day trial indefinitely today, accepted
      deliberately at launch. Candidate approach when it's worth building:
      tie trial eligibility to a device or store-account signal. Note the
      store-only decision removes the payment-fingerprint option previously
      sketched here. `PAYMENTS_SPEC.md` §4, §13.

## Product ideas, raised 2026-08-24 — not scoped, not started

Logged as-is, no design work done yet.

- [~] **[SWOT] Competitive feature gaps.** Owner ruled on all three 2026-08-31.
      - **Pronunciation editor: open, explained but not yet decided.** Best
        evidenced gap in the set: most-praised feature across NaturalReader,
        @Voice and Voice Dream, and mispronunciation is the top voice complaint
        in all six. Proposed v1 is a substitution list applied to chunk text
        before synthesis, global plus per-book, with an add-from-reader path.
        Known wrinkle: substitution changes string length, so it must not
        desync the follow-along highlight offsets.
      - **MP3 export: REJECTED 2026-08-31, owner call.** Not on the legal
        question, on product grounds: offline listening already works, so an
        MP3 file adds no capability a user does not already have. That also
        retires the licence and distribution questions it raised, since there
        is nothing to distribute. Do not re-propose without a new use case.
      - **Highlight/annotation export: REJECTED 2026-08-31.** Owner call: the
        brand focuses on books, audiobooks and e-readers, not academic tooling.
        Do not re-propose it.
- [ ] **[SWOT] Lifetime tier shutdown reserve, before selling any.**
      Already open as a decision in `PAYMENTS_SPEC.md` §13, restated here
      because the SWOT put a number on it: 500 lifetimes at $129 is $64,500
      gross against a contractual 12-month refund exposure that the ToS
      already commits to while no reserve policy exists. Voice Dream's
      one-star "early adopter, punished" reviews are the documented failure
      mode. Decide the reserve policy before the first lifetime is sold,
      not after.

- [ ] **"Mark as finished" below 100%.** The last few pages of a book are
      often glossary/acknowledgments/about-the-author matter that a reader
      has no intent to listen to, so a book someone genuinely finished the
      story of may never hit the 100% that Stats' "finished" count currently
      requires. Needs a decision on the mechanism (a manual "mark as
      finished" action vs. an automatic threshold like 95%) before building.
- [ ] **Spotify-Wrap-style annual review.** A personalized yearly summary
      (books read, hours listened, streaks, etc.), presumably shareable.
      Would build on the existing `Stats`/`pl_stats` data already tracked
      per day and per book — no new data collection needed, just a new
      view/export.
- [ ] **Gamification, Duolingo-style?** Open question, not a decision —
      streaks, XP, badges, that kind of thing. Needs to be weighed against
      the app's current tone/positioning before deciding whether it fits.
- [ ] **Should the app have a deliberate tone/voice** (humorous, sassy,
      etc.) rather than the current neutral/functional copy? Affects
      toasts, onboarding copy, empty states, and marketing copy throughout
      — a real brand-voice decision, not a code change, and probably worth
      deciding before or alongside the gamification question above since
      they'd need to agree with each other.
      - 2026-08-26 addition: rather than picking one tone, consider a
        **Settings toggle** letting the user choose the app's own voice for
        its communications — options like Normal / Sassy / Bro / Butler,
        etc. (exact options and copy to be defined later). Would turn the
        brand-voice question above into a per-user preference instead of a
        single global decision.
- [x] **An "About" section. Done 2026-08-31**, native app only per the web
      freeze. A Settings row opens a sheet with the version, website, support,
      privacy and terms links, and the eight open-source components with a link
      to each upstream project.
      **Deliberately missing: the licence strings.** Names, versions and
      upstreams are verifiable facts; the exact licence terms were not checked
      against each upstream, and a wrong licence line in a shipped app is a
      compliance problem rather than a typo. Fill them in before any store
      release. Overlaps the MP3 export question above, since both turn on what
      the voice model licences actually permit.
      `About.APP_VERSION` has no build step behind it, so bump it in step with
      `versionName` in `android/app/build.gradle`.
- [ ] **Maybe rename to "Bokos"?** Owner's idea, raised 2026-08-29 — came
      from a "Books" typo. Not for now, just logged.

## SWOT research (done — see `COMPETITOR_SWOT.md`)

Competitors vetted for this project (each has a live comparison page —
`elevenreader-alternative.html`, `naturalreader-alternative.html`,
`play-books-alternative.html`, `speechify-alternative.html`,
`voice-aloud-alternative.html`, `voice-dream-alternative.html`):
**ElevenReader, NaturalReader, Google Play Books, Speechify, Voice Aloud
Reader, Voice Dream Reader.** Full SWOT + app-store-review-mining pass is
in `COMPETITOR_SWOT.md`. Its factual follow-ups against the live
comparison pages are done as of 2026-08-28: `play-books-alternative.html`
(+FR) now correctly says uploads get Read Aloud and only the natural voice
needs a connection; `naturalreader-alternative.html` (+FR) notes paid MP3
export; `voice-aloud-alternative.html` (+FR) renamed to the app's current
Play name ("@Voice"), added its $15 one-time price, and noted third-party
engines are installable; `voice-dream-alternative.html` (+FR) notes the
subscription is now required for new users with a 3-day trial, and
reframes the voice-quality gap (modern neural vs. Voice Dream's
Acapela/NeoSpeech/Ivona) instead of leaning on offline/background since
Voice Dream claims both; `elevenreader-alternative.html` (+FR) notes the
60-day download expiry and no-export restriction. Owner separately wants
hands-on firsthand testing of these — the two are complementary (agent did
breadth/reviews, owner does depth/feel).
