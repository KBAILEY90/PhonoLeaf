# PhonoLeaf — consolidated to-do

One place pulling together what's scattered across `BUSINESS.md`,
`PAYMENTS_SPEC.md`, `VERIFICATION.md`, `BACKLOG.md`, and conversation-only
threads. Not a replacement for those files (they carry the reasoning) —
this is the single "what's actually next" list. Update it as things move;
don't let it go stale the way `BACKLOG.md`'s old "Next up" section did.

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
- [ ] **Attribution is now a legal obligation, not a nicety.** CC BY 4.0 on both
      English Piper voices and CC BY-SA on French require credit. The app needs
      a credits surface naming each model, its source and its licence. See the
      About/credits split in the product ideas section.
- [ ] **CONFIRMED 2026-08-31: espeak-ng (GPL-3.0) is statically linked into
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

- [ ] **Decide the engine path.** Options, roughly cheapest first:
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
- [ ] **Supertonic on Android: plausible, unproven, and you would be the one
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
      into a throwaway Android test harness and synthesize one sentence. That
      answers feasibility in a day and needs no product work.
- [ ] **Kokoro French is now a real fallback worth keeping in view.** Kokoro
      v1.0 covers French (`ff_siwis`, Apache 2.0), which the 2026-08-08 note
      said did not exist. Paired with misaki-rs (MIT) it is a fully
      permissive French path with no espeak and no CC BY-SA. Two caveats: it
      is one voice trained on under 11 hours, and Kokoro only runs on devices
      that pass `_KOKORO_MIN_GFLOPS`, so it cannot serve French on mid-tier
      phones. Useful as a component of a mixed answer, not as the answer.
- [ ] **Benchmark Supertonic quality and speed against Piper** on a mid-tier
      phone before committing. 99M parameters against Piper medium is not a
      like-for-like comparison, and the whole product positions on the voice
      not being robotic.
Sources: hexgrad/Kokoro-82M on Hugging Face (Apache 2.0), rhasspy/piper-voices
MODEL_CARD files per voice, and rhasspy/piper discussion #271 on licensing.

## Blocked on external people/hardware (nothing to build until these move)

- [ ] **Business registration (REQ, Québec)** — with the lawyer, awaiting
      response (engaged 2026-08-10). This is the actual critical path:
      it gates the bank account, GST/QST registration, and the Stripe
      account, in that order. `BUSINESS.md` "Gating, do now" #2.
- [ ] **[SWOT] Chase or replace the lawyer.** As of 2026-08-30 that is 20
      days of silence on the single item gating every other commercial
      step (registration, bank, GST/QST, Stripe, payments, and therefore
      the CASA submission). The length of the silence is itself the
      evidence about whether this engagement is working. Concretely: send
      one dated follow-up with a reply-by date, and if that passes, get a
      second Québec lawyer quoting on the same scope rather than
      continuing to wait. `SWOT.md` Threats, recommendation 2.
- [ ] **Lawyer review of ToS/Privacy** — same engagement, also awaiting
      response. `BUSINESS.md` §3.
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

- [ ] **Play Console account type: personal vs organization.** A personal
      account needs a closed test with 12 testers for 14 continuous days
      before it can ship to production; an organization account (tied to
      Everbloom once registered) skips that entirely. Same shape as the
      Apple Individual-vs-Organization choice already made (wait for the
      corporation). `PAYMENTS_SPEC.md` §11 #5.
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
      nothing left blocking the start of Stripe integration on the
      decisions front): refunds manual via Stripe dashboard for now
      (automating it is a post-launch item, see below); lifetime reserve
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
- [ ] **Automate refunds**, 2026-08-28 owner call — manual via Stripe
      dashboard is fine to launch with; revisit once volume makes that
      painful. `PAYMENTS_SPEC.md` §13.
- [ ] **Trial abuse mitigation**, 2026-08-28 owner call — new Google
      accounts can restart the 7-day trial indefinitely today, accepted
      deliberately at launch. Candidate approach when it's worth building:
      tie trial eligibility to a Stripe Radar payment-method fingerprint
      or a device signal. `PAYMENTS_SPEC.md` §13.

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
