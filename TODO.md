# PhonoLeaf — consolidated to-do

One place pulling together what's scattered across `BUSINESS.md`,
`PAYMENTS_SPEC.md`, `VERIFICATION.md`, `BACKLOG.md`, and conversation-only
threads. Not a replacement for those files (they carry the reasoning) —
this is the single "what's actually next" list. Update it as things move;
don't let it go stale the way `BACKLOG.md`'s old "Next up" section did.

---

## Blocked on external people/hardware (nothing to build until these move)

- [ ] **Business registration (REQ, Québec)** — with the lawyer, awaiting
      response (engaged 2026-08-10). This is the actual critical path:
      it gates the bank account, GST/QST registration, and the Stripe
      account, in that order. `BUSINESS.md` "Gating, do now" #2.
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

## Actionable now, no blockers

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
- [ ] **Internal SWOT of PhonoLeaf itself**, raised 2026-08-29 — distinct
      from the competitor SWOT above (that one benchmarks against
      ElevenReader/NaturalReader/etc.; this one looks inward: strengths,
      weaknesses, opportunities, threats in PhonoLeaf's own product,
      codebase, and business position). Not scoped yet — worth a short
      framing pass (codebase-only audit vs. also covering business/market
      factors already tracked in `BUSINESS.md`/`PAYMENTS_SPEC.md`) before
      starting.
- [ ] **App redesign exploration with the `design` skill.** Ready whenever
      wanted — needs a scoping conversation first (which screens/flows,
      what direction), not a blind "redesign everything" pass.
- [x] **Port Phase 2/3 features from the archived hero branch into the
      now-canonical `index.green.html`.** Done 2026-08-29: the motion/
      gesture CSS token system, a localized accessibility pass, the storage
      manager ("On this phone") screen, and in-book full-text search are
      all now in `index.green.html`, re-implemented by hand against the
      forest version's markup (not a git merge — see `CLAUDE_HISTORY.md`'s
      2026-08-29 entry). Not yet device-tested — verify via `npm run sync`
      + Android Studio before the next Play Console upload.
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
      yet device-tested — verify the review sheet actually appears via
      `npm run sync` + Android Studio.

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
- [ ] **An "About" section**, raised 2026-08-27 — not scoped (where it
      lives, what it says: version number, credits, links to the website/
      support, licenses for bundled voice models, etc.). Probably belongs
      in Settings alongside Privacy/Terms.
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
