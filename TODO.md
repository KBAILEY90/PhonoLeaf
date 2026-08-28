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
- [ ] **A replacement MacBook** — **ordered 2026-08-28**: an M1 Air (8GB/128GB,
      93% battery, CA$650, Longueuil). Blocked on delivery/setup, not decision
      anymore. Nothing iOS-side is possible until it's in hand, not even
      Apple Developer enrollment testing. Every other iOS item below is
      blocked behind this one.
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
      Standard (Piper) free, Upgraded (Kokoro) paid.** Reasoning: Standard
      doesn't sound natural enough to be the thing people pay for; the
      subscription should gate voice *quality*, not basic access. This
      reverses the earlier "free tier undercuts the whole subscription"
      concern from 2026-08-21 — still needs the actual unit-economics
      re-run (a free tier costs nothing directly since TTS runs on-device,
      but changes the funnel/conversion math) before it's final, and
      before implementing against `PAYMENTS_SPEC.md`'s entitlement design.
      **New follow-on idea, same conversation:** on a device that benchmarks
      as Kokoro-capable but is still on the free Standard tier, prompt/promote
      the Upgraded voice to that user proactively and more often than on a
      device that can't run it at all — turns the existing
      `_KOKORO_MIN_GFLOPS` capability gate into an upsell signal. Not
      scoped or built; needs the paywall/entitlement system to exist first
      (same as the rest of this section), and a decision on prompt
      frequency/placement so it doesn't nag.
- [x] **KV vs D1 for entitlement generally.** **Decided 2026-08-28: D1.**
      Turned out not to be a cost-vs-consistency tradeoff — D1 is both
      strongly consistent (no post-payment paywall-flash risk) and cheaper
      per operation than KV at any scale PhonoLeaf will hit for a long time.
      Full reasoning and pricing numbers logged in `PAYMENTS_SPEC.md` §13.
      **Fully migrated and live as of 2026-08-28** — see "D1 migration"
      below.
- [x] **Refund mechanics, lifetime shutdown reserve, trial abuse — all
      decided 2026-08-28.** Refunds: manual via Stripe dashboard for now
      (automating it is a post-launch item, see below). Lifetime reserve:
      a % of each sale held separately until that sale's 12-month window
      closes — mechanism decided, **exact percentage still needed before
      lifetime sales go live** (see below). Trial abuse: accepted as-is,
      mitigation deferred to post-launch (see below). Full reasoning in
      `PAYMENTS_SPEC.md` §13. **§13 now has zero open items — nothing left
      blocking the start of Stripe integration (§9 step 2) on the
      decisions front.**
- [x] **Lifetime reserve percentage — not tracked as a task.** Owner call
      2026-08-28: the mechanism (a % of each lifetime sale, held
      separately) is the real decision and it's made; the exact number is
      an ongoing operational/financial call the owner will make with the
      bank/accountant once the business account exists, not a one-time
      engineering blocker to pre-decide. Closed here rather than kept open
      with nothing for anyone to act on.

## D1 migration (2026-08-28 — done, live in production and staging)

The entitlement Worker was already built and deployed (`worker/`, per
`PAYMENTS_SPEC.md` §9) on Cloudflare KV. It held no real entitlement data
(not called from the app yet), so this was a clean code swap, not a data
migration. **KV fully removed from the codebase** — no `[[kv_namespaces]]`
blocks, no `env.ENTITLEMENTS` references left anywhere in `worker/`.

- [x] **Designed the D1 schema.** `worker/migrations/0001_create_entitlements.sql`
      — one `entitlements` table, `sub_hash TEXT PRIMARY KEY`, same columns
      as the old KV record (`status`, `source`, `plan`, `trial_end`,
      `period_end`, `updated_at`).
- [x] **Rewrote `worker/src/entitlement.js`** off `env.ENTITLEMENTS.get/put`
      onto D1 prepared statements (`env.DB.prepare(...).bind(...)`).
      **Every query parameterized, `?` placeholders only** (`PAYMENTS_SPEC.md`
      §7's new rule) — verified mechanically in the test harness below,
      not just by eye.
- [x] **Updated `worker/wrangler.toml`**: both `[[kv_namespaces]]` blocks
      (production + `env.staging`) replaced with `[[d1_databases]]`
      bindings (`binding = "DB"`). Real database ids are placeholders
      (`REPLACE_ME_RUN_WRANGLER_D1_CREATE`) pending the step below —
      wrangler fails loudly on a real deploy until they're filled in,
      rather than silently pointing at the wrong thing.
- [x] **Swept every KV reference** in `worker/`: `PAYMENTS_SPEC.md` §2,
      `worker/README.md` (setup steps now say `wrangler d1 create` +
      `d1 migrations apply`), and code comments in `entitlement.js` /
      `entitlement-jwt.js`. Added `db:migrate:local` / `db:migrate:remote`
      npm scripts.
- [x] **Verified locally** (no Cloudflare account available in this
      session, so this is as far as it goes from here): applied the
      migration against local D1 emulation (`wrangler d1 migrations apply
      --local`, works without cloud auth); ran the exact insert/select/
      upsert SQL from `entitlement.js` directly via `wrangler d1 execute
      --local` and confirmed the conflict-path upsert correctly moves a
      trial row to active; ran `wrangler dev` locally and confirmed the
      D1 binding loads plus the health check and `/entitlement`
      401-without-a-token / malformed-JWT paths match the old KV
      behavior exactly; ran a Node harness against the real
      `entitlement.js` functions with a mocked D1 API (trial creation is
      idempotent on a second lookup, the Stripe-webhook-style upsert
      moves trial→active correctly, `effectiveStatus`'s expiry safety net
      still works, an unseen hash reads as `none` not an error, and every
      bound DB call used bare `?` placeholders with no stray
      concatenation).
- [x] **Created the real D1 databases**, 2026-08-28 (owner, via
      `npx wrangler login` + `wrangler d1 create`): production
      `phonoleaf-entitlement` (`b5c17050-89e4-490d-98d4-10d8ab3dcaf8`) and
      staging `phonoleaf-entitlement-staging`
      (`4b5de02b-d72d-41e3-9cbb-bc2932eca035`). Both ids are now in
      `worker/wrangler.toml`, replacing the placeholders.
- [x] **Applied the migration to both real databases**, 2026-08-28
      (owner). Staging needed `--env staging` on the `migrations apply`
      command (its D1 database is defined under
      `[env.staging.d1_databases]`, not the top-level config) — hit and
      fixed the "Couldn't find a D1 DB" error this caused, now documented
      in `worker/README.md` and a `db:migrate:remote:staging` npm script
      added so it doesn't happen again.
- [x] **Redeployed** production + staging, 2026-08-28 (owner). Also made
      `workers_dev`/`preview_urls` explicit in `wrangler.toml` in the same
      pass (production: workers.dev on, Preview URLs off; staging:
      neither — reachable only via its custom domain) after the first
      deploy warned both were left to Wrangler's defaults. **D1 migration
      is now fully complete and live** — KV is gone, both environments
      run on D1 — production's `migrations apply --remote` reported
      "No migrations to apply!" (already applied from an earlier pass),
      and staging's succeeded once `--env staging` was added.

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
- [x] **Competitor SWOT + app store review research.** **Already done**
      (commit `96c1b75`, predates this list saying "ready to kick off" —
      this list itself had gone stale on this exact item) — full SWOT in
      `COMPETITOR_SWOT.md` against all 6 vetted competitors. **2026-08-28:
      completed the file's own "Suggested follow-ups" (5 of 6, all
      mechanical factual corrections)** — `play-books-alternative.html`
      (+FR) now correctly says uploads get Read Aloud and only the natural
      voice needs a connection; `naturalreader-alternative.html` (+FR)
      notes paid MP3 export; `voice-aloud-alternative.html` (+FR) renamed
      to the app's current Play name ("@Voice"), added its $15 one-time
      price, and noted third-party engines are installable; `voice-dream-
      alternative.html` (+FR) notes the subscription is now required for
      new users with a 3-day trial, and reframes the voice-quality gap
      (modern neural vs. Voice Dream's Acapela/NeoSpeech/Ivona) instead of
      leaning on offline/background since Voice Dream claims both;
      `elevenreader-alternative.html` (+FR) notes the 60-day download
      expiry and no-export restriction. **Not done, left as an open
      question, not mechanical**: follow-up 6, whether the `@Voice`
      comparison page needs a different frame since it's the one
      competitor whose positioning already overlaps PhonoLeaf's directly.
- [ ] **App redesign exploration with the `design` skill.** Ready whenever
      wanted — needs a scoping conversation first (which screens/flows,
      what direction), not a blind "redesign everything" pass.
- [x] **Stale doc cleanup**, surfaced 2026-08-20, **done 2026-08-28**:
      `BACKLOG.md` section H (Kokoro-on-strong-devices) now marked DONE
      with a pointer to `CLAUDE.md`'s "Voice engine" section; section F's
      accessibility bullets now carry their 2026-08-07 DONE markers;
      `BUSINESS.md`'s gating item 4 no longer lists French legal pages as
      open.
- [ ] **Store review prompts** (`BACKLOG.md` section I) — Play In-App
      Review / iOS SKStoreReviewController, triggered after a good moment
      (finishing a book), never nagging. Fully scoped, nothing built yet.
      **Owner call 2026-08-28: hold until actually on the app stores** —
      prompting for a store review before the app is listed anywhere is
      pointless, so this is deliberately not next despite having no
      technical blocker.

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

## SWOT research (done — see `COMPETITOR_SWOT.md`)

Competitors vetted for this project (each has a live comparison page —
`elevenreader-alternative.html`, `naturalreader-alternative.html`,
`play-books-alternative.html`, `speechify-alternative.html`,
`voice-aloud-alternative.html`, `voice-dream-alternative.html`): **ElevenReader,
NaturalReader, Google Play Books, Speechify, Voice Aloud Reader, Voice
Dream Reader.** Full SWOT + app-store-review-mining pass against this list
is in `COMPETITOR_SWOT.md`; its factual follow-ups against the live
comparison pages are done as of 2026-08-28 (see "Actionable now" above).
Owner separately wants hands-on firsthand testing of these — the two are
complementary (agent did breadth/reviews, owner does depth/feel).
