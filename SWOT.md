# PhonoLeaf: internal SWOT

**Written 2026-08-30.** The inward looking counterpart to `COMPETITOR_SWOT.md`
(which benchmarks PhonoLeaf against ElevenReader, Speechify, @Voice and the rest).
This one looks at PhonoLeaf's own product, codebase, and business position, and
scopes the open `TODO.md` item raised 2026-08-29 as **product + codebase + business**,
not codebase only.

Every claim below traces to something checked in this repo on the date above: file
measurements, `git log`, `android/app/build.gradle`, the worker source, and the
existing docs. Where a doc and the code disagree, the code is what is reported.

---

## Strengths

**The zero marginal cost architecture is structural, not a promise.** Voices are
generated on device, books go from Google Drive to phone directly, and there is no
server in the reading path. Competitors meter because their costs scale with usage:
Speechify counts words, NaturalReader caps at 500K characters a day, ElevenReader
sells hours. PhonoLeaf has no unit to meter even if it wanted one. That is what
permits $49.99 a year against $79 to $139, and it cannot be copied by anyone running
inference in a data centre.

**The native platform work is the real moat, not the TTS models.** 1,893 lines of
Kotlin and Java across seven plugins and two foreground services. `PlaybackService.kt`
(398 lines) solves screen off playback with a foreground service plus a CPU wake lock
plus `MediaSessionCompat` lock screen controls, which is precisely the failure three
of the six competitors ship with and one of them publicly calls unfixable.
`PackDownloadService.kt` does the same for multi minute pack downloads.
`PhonoLeafTtsPlugin.kt` (934 lines) runs two model families with per model
cancellation epochs on a dedicated executor. Piper and Kokoro are open source and
commoditised; this integration is not.

**The reader and TTS loop carries real, hard won correctness.** Generation guards
(`TTS._gen`) so a stale callback from a page already left cannot double advance;
`_retryN` for the case where Android silently eats a `speak()` after an interrupting
`cancel()`; a forward overshoot corrector for an epub.js rounding bug on fractional
viewport widths; blank page skipping that is forward only and capped at 20; block
aware chunking so a heading does not run into the next sentence; background reading
that pulls text from the spine because epub.js's render loop freezes with the screen
off. None of these are things a rebuild would get right by accident.

**Documentation discipline is genuinely exceptional and is an asset, not overhead.**
`CLAUDE_HISTORY.md` is 6,866 lines of preserved reasoning: what was tried, what was
rejected, and why. The "do NOT fix these" section in `CLAUDE.md` exists because
several of those items were broken once already. For a solo project this is rare, and
it is the reason work can resume cleanly after gaps.

**Compliance and legal groundwork is years ahead of the typical indie app.** OAuth
approved for `drive.readonly`, CASA AL1 engaged and paid at $693 with the deadline
extended to January 2027, French legal pages live, Bill 96 obligations mapped, ToS
pricing and auto renewal clauses drafted, Québec governing law identified as non
waivable. Most apps at this stage have a template privacy policy and no idea CASA
exists.

**The privacy claim survives inspection.** CSP locked to known hosts, vendored
epub.js and jszip rather than a CDN, no analytics, and bug report diagnostics audited
to confirm they carry no tokens or storage values. The claim is enforced by the
architecture rather than asserted in a policy.

**A deliberately minimal dependency posture.** No build step, five Capacitor runtime
dependencies, and a worker with zero runtime dependencies. Small supply chain attack
surface, and less for CASA's dependency scan to find on the next assessment.

---

## Weaknesses

**The two file fork is the single largest engineering liability.** `index.green.html`
(10,340 lines) ships as the Android app; `index.html` (7,904 lines) is what
phonoleaf.com serves. Eight substantial modules exist only in the native file:
`StorageModal`, `SleepTimer`, `MiniPlayer`, `BookDetail`, `Forest`, `EraseModal`,
`CoverReveal`, `StoreReview`. Localization coverage is 218 `data-i18n` attributes
against 129. The website is not a slightly older build, it is a materially different
and worse product. Every change from here either costs double or reaches only half the
audience, and the gap widens with each commit. `CLAUDE.md` records the fork as a
permanent owner decision, which is a legitimate call, but the cost it creates is
ongoing and compounding rather than one time.

**The single file, single script architecture has reached its ceiling.** 426,982
characters of JavaScript in one inline `<script>` tag, 7,845 lines, 51 modules. The
`TTS` object alone is 1,809 lines. "No build step" was the right call at 3,000 lines
and is now the reason there is no module boundary, no way to unit test a single
module, and no way to load anything lazily. Nothing here is broken; the constraint is
that the next significant feature costs more than the last one did.

**There is no automated test of any kind.** The documented verification is a
`vm.compileFunction` syntax check plus manual device testing. The only test files in
the repo are Capacitor's stock `ExampleUnitTest.java` and
`ExampleInstrumentedTest.java`. Against 51 modules, 39 timer call sites, and a
concurrency model that depends on generation counters, every regression guarantee
rests on one person's memory plus `CLAUDE_HISTORY.md`. The many "not yet device
tested" markers across recent `CLAUDE.md` entries are a symptom of this, not a
scheduling accident.

**The app cannot currently be released, and the docs read as though it has been.**
`android/app/build.gradle` is `versionCode 1` with no `signingConfig`; there is no
keystore anywhere in the repo; `VERIFICATION.md` still has "Register the Play Console
account" unchecked. Separately, OAuth remains in Testing mode with its 100 user cap,
because leaving it requires CASA, which is parked. So `CLAUDE.md`'s "it is what Play
Store users get" and "production bound, real users" describe an intent, not a current
state. Worth correcting in the docs, because planning against the optimistic reading
produces the wrong priorities.

**The app cannot currently charge anyone.** The entitlement worker exists (324 lines,
deployed, with real ES256 signing and Google JWKS verification) but is deliberately
not called from the app, and `/checkout`, `/portal`, `/verify-play` and both webhook
routes are 501 stubs. Everything downstream (bank account, GST and
QST registration) waits on Québec business registration, which the lawyer is
actively working on, with email every 2-3 days (owner, 2026-09-01). **Corrected
2026-09-01:** this previously said "no response recorded in 20 days", which was
wrong and got repeated into a false crisis. It is ordinary sequencing, not a
stalled engagement.

**One person, one device, one data point.** The owner is engineer, designer, QA,
lawyer liaison, accountant and marketer. Device testing is effectively a single Pixel
7, and the Kokoro quality gate (`_KOKORO_MIN_GFLOPS`) is calibrated off that one
device, with `TODO.md` already flagging that borderline hardware has never been
tested. A threshold fitted to one point is a guess with error bars nobody has
measured.

**Documentation has started drifting from the code it describes.** `COMPETITORS.md`
still lists the better voice on strong devices as "not built yet" and says "it is
Piper for everyone today", which stopped being true on 2026-08-08. The doc corpus is
now roughly 12,000 lines across 14 files with real overlap between `TODO.md`,
`BACKLOG.md`, `BUSINESS.md` and `CLAUDE.md`, so the same fact needs updating in
several places and sometimes only gets updated in one.

**Known competitive feature gaps are identified but unscheduled.**
`COMPETITOR_SWOT.md` names a pronunciation editor as the most praised feature across
three competitors, plus MP3 export and annotation export. None appear in `TODO.md` as
planned work. EPUB only, against competitors reading PDF, DOCX and camera OCR, is a
known and accepted limit, but the gap is not shrinking.

**Branch sprawl, and a history of parallel divergence.** Thirteen remote branches,
several already merged or stale, plus `archive/hero-redesign-2026-08-28-branch`
preserving a fork that cost a genuine reconciliation incident when two sessions
extended the same file for days with no visibility into each other. The safeguard
added afterwards (check `git log --all` before touching `index.green.html`) is a
process fix for a structural problem.

---

## Opportunities

**Converging the fork is cheapest today and gets more expensive every commit.**
Promoting `index.green.html` into `index.html`, repointing `APP_SOURCE`, and deleting
`stage-test.js` is a defined, bounded task already written down in `CLAUDE.md`. Doing
it turns every future change into one change, and immediately upgrades the website
from an eight module deficit to parity.

**There is a large inventory of finished, unshipped product.** Storage manager, in
book search, sleep timer, mini player, book detail sheet, forest, follow along word
highlighting, the motion token system, the accessibility pass. Real features, built
and browser verified, that no user has ever used because there is no release channel.
The value here is gated on distribution rather than on more building, which is a much
better problem to have and a much worse one to leave sitting.

**SUPERSEDED 2026-09-01.** This argued for opening a personal Play Console account
to start the 12 tester, 14 continuous day closed test clock in parallel. The owner
has decided every store account is registered **through the corporation**, and the
14-day clock applies to personal accounts only, so there is no clock to start early
and no calendar time being lost. The paragraph below is kept for its reasoning about
the organization
account alternative removes the 14 day requirement but needs the registered entity
that is itself blocked, so the honest framing is a real tradeoff rather than a free
win: going personal now buys two weeks of calendar back, at the cost of a conversion
that `TODO.md` correctly describes as painful.

**Marketing should lead with reliability rather than privacy.** This is
`COMPETITOR_SWOT.md`'s own strongest finding and it has not yet reached the website
copy. The highest upvoted complaints across every competitor are endless loading,
losing your reading position, and playback dying when the screen locks. PhonoLeaf's
architecture fixes all three, and per chunk position tracking is demonstrable in a
twenty second video. Privacy is why people approve; reliability is why people switch.

**The retention layer is accidentally already built.** `Forest`, streaks, and `Stats`
answer most of the open gamification question in `TODO.md` without committing to a
Duolingo style system. A Wrapped style annual review would run entirely on the
`pl_stats` data already collected, needing a new view and no new data collection.

**The French and Québec market is underserved, and the entry cost is already paid.**
Bill 96 compliance, French legal pages, a French UI, and a French voice pack are all
done. For a competitor those are the cost of entry; here they are sunk.
`COMPETITORS.md` already names this as an opportunity and it remains untaken.

**The documentation corpus makes a second contributor viable.** Once the fork is
resolved, `CLAUDE_HISTORY.md` plus `CLAUDE.md`'s gotchas section is an onboarding
package most solo projects cannot offer, whether the contributor is a person or an
agent.

---

## Threats

**The CASA deadline is 125 days out and sits behind a dependency chain nobody here
controls.** January 2, 2027. Submission is deliberately parked until the payments
backend exists, so the sequence is: lawyer responds, business registers, bank account
opens, GST and QST register, store accounts open, payments get built, then CASA is
submitted. The first link has not moved in 20 days. If it has not moved by roughly
October, the realistic options narrow to submitting without a backend (accepting a
second assessment plus a DAST scan later, which Eydle has already called a likely
significant change) or asking for a second extension after having used one. This is
the highest consequence risk in the project and the one with the least local control.
It deserves a decision date rather than continued waiting.

**Recent effort has gone to polish while the revenue gates have not moved.** The last
five merged pull requests are storage modal percentages, storage modal boxed groups,
storage modal spacing, the store review prompt, and a home page restyle. Over the same
window, Play Console, the release keystore, and every payments prerequisite stayed
exactly where they were. Each individual change is reasonable and the quality bar is
real, but the aggregate pattern is optimizing a product that cannot yet be installed
or purchased. Named here because it is the internal threat most likely to be invisible
from inside, and unlike the others it is entirely within the owner's control.

**Speechify shipped on device voices on iOS in December 2025.** The headline
differentiator is already half taken, on the platform PhonoLeaf has not shipped to, by
a company with 10M+ Android installs. An Android port on their side turns PhonoLeaf's
core pitch into a checkbox. Time is actively unfavourable here.

**@Voice Aloud Reader is one bundled neural pack away from the entire pitch.** 10M+
installs, 4.3 stars across 137K reviews, $15 once, no subscription, no account, more
formats including LCP DRM titles, and it already accepts Piper voices as pluggable
engines. It is the closest competitor and the one whose positioning most overlaps.

**The primary ingestion path is a single restricted scope requiring annual
recertification.** If `drive.readonly` is withdrawn, repriced, or fails a recert, the
main way books get into the app disappears. The local folder feature is a real hedge
and its strategic weight is much larger than its 126 lines of Kotlin suggest.

**The lifetime tier carries a documented failure mode and no reserve policy.** Voice
Dream sold perpetual access, changed voice engines, and now carries one star reviews
from its most loyal buyers. `PAYMENTS_SPEC.md` §13 lists the shutdown reserve as still
open. Selling 500 lifetimes at $129 creates a $64,500 gross intake against a
contractual 12 month refund exposure, and the ToS commitment exists while the reserve
policy does not.

**Bus factor of one, on a 10,340 line file with no tests.** Any interruption, illness,
or extended gap lands on a codebase whose recovery instructions are 6,866 lines of
prose. The docs mitigate this better than most projects manage, but they do not
eliminate it.

**The category's users are hostile to subscriptions and say so unprompted.** A new
paid entrant with no reviews and no installed base, competing against two credible
free substitutes (Play Books preinstalled on 1B+ devices, @Voice free with ads),
starts from a negative prior on price alone.

---

## What this suggests, in order

Not a plan, a reading of the four quadrants above. Ordered by what unblocks the most.

1. **Set a decision date on the CASA sequencing**, for example October 15. If business
   registration has not landed by then, submit the assessment without the backend and
   budget for the second one. Waiting indefinitely on an unresponsive third party is
   the only path here with no ceiling on the downside.
2. **WITHDRAWN 2026-09-01: "chase or replace the lawyer".** Built on a stale claim
   of twenty days of silence. The engagement is active, with email every 2-3 days.
   Left in place, struck through rather than deleted, because the failure worth
   remembering is that a stale status line about a PERSON was repeated across
   sessions until it read as a crisis. Verify claims like that with the owner.
3. **Create the release keystore.** Small, cheap and unblocked today. The Play Console
   account is NOT part of this any more: it gets registered through the corporation
   once incorporation completes, so it waits on that. No 14-day tester clock applies,
   since that requirement is for personal accounts only.
4. **Converge the fork.** Cheapest now, and it doubles the reach of everything already
   built.
5. **Correct the docs to match reality**: no Play Store users yet, still in OAuth
   Testing mode, and `COMPETITORS.md`'s stale Kokoro claim.
6. **Add a minimal test harness** for the places where a regression is expensive and
   silent: chunking and `_split()`, the blank page skip cap, the generation guard, and
   the entitlement worker's trial state machine. Not a suite, just a few dozen
   assertions that run in Node.
7. **Reframe the marketing on reliability**, since the research supporting it is
   already done and unused.
