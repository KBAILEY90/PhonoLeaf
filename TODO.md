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
- [ ] **A replacement MacBook** — the M1 Air purchase fell through
      (2026-08-10); nothing iOS-side is possible without it, not even
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
- [ ] **Pricing model: keep one paid tier, or split Standard/Upgraded?**
      Raised 2026-08-21: leave the Standard (Piper) voice completely free
      and charge only for the Upgraded (Kokoro) voice, OR price Standard
      at $1–2/mo and Upgraded at the current $5.99/$49.99. Current
      committed model (`BUSINESS.md`) is one subscription gating all TTS —
      this would be a real change to the unit-economics math already
      baked into that doc, not a copy tweak. Worth thinking through before
      committing: a free Standard tier gives away the core "audiobook from
      your own ebooks" value prop for nothing, which undercuts the whole
      subscription — the natural/Kokoro voice is a quality upgrade on top
      of a already-complete product, not a separate product. A cheaper
      Standard tier ($1–2) is more defensible, but still needs new unit
      economics run before deciding (Stripe's ~3% + fixed fee eats a much
      bigger share of a $1–2 charge than a $5.99 one). Not decided — flag
      for a real pass once payments infrastructure exists.
- [ ] **KV vs D1 for entitlement generally**, **refund mechanics**,
      **lifetime shutdown reserve**, **trial abuse** — `PAYMENTS_SPEC.md`
      §13, all still open, needed before Stripe integration starts.

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
- [ ] **Competitor SWOT + app store review research.** Scoped and ready to
      kick off as a background research task — see "SWOT research" below.
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
- [ ] **Stale doc cleanup**, surfaced 2026-08-20 and not yet done:
      `BACKLOG.md` section H (Kokoro-on-strong-devices) says "not
      implemented" — it shipped 2026-08-08. Section F's accessibility
      bullets have no DONE marker even though the audit shipped
      2026-08-07. `BUSINESS.md`'s gating item 4 still lists French legal
      pages as open — they've been live since before the whole-app i18n
      work.
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

## SWOT research (ready to launch as a background task)

Competitors already vetted for this project (each has a live comparison
page — `elevenreader-alternative.html`, `naturalreader-alternative.html`,
`play-books-alternative.html`, `speechify-alternative.html`,
`voice-aloud-alternative.html`, `voice-dream-alternative.html`): **ElevenReader,
NaturalReader, Google Play Books, Speechify, Voice Aloud Reader, Voice
Dream Reader.** A SWOT + app-store-review-mining pass against this same
list would build directly on research already done rather than starting
cold. Owner separately wants hands-on firsthand testing of these — the
two are complementary (agent does breadth/reviews, owner does depth/feel).
