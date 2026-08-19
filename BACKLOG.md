# PhonoLeaf — Backlog & decisions (from owner feedback, 2026-08)

Master list of the latest feedback. Each item is tagged **[CODE]** (Claude Code can
implement it from this file), **[DECISION]** / **[ANSWER]** (already resolved, no
build needed), or **[RESEARCH]** (documented strategy, revisit later). Tell Code
"read the md files and execute the [CODE] items."

> **Style rule (owner, 2026-08): no hyphens in prose.** Write "on device" not the
> hyphenated form, "read aloud" not the hyphenated form, "text to speech" (or TTS),
> and so on. Do NOT strip hyphens from code, identifiers, file names, URLs, locale
> codes (`en-US`, `fr-CA`), cache tags (`phonoleaf-v14`), or French words that are
> spelled with a hyphen. Avoid the long dash entirely; use commas, periods, or
> parentheses.
> **[CODE / me]** Do a careful pass over the existing English docs to remove prose
> hyphens and long dashes, using judgment per the rule above: `BUSINESS.md`,
> `PAYMENTS_SPEC.md`, the `terms.html` English body, `CLAUDE.md` notes added today,
> and the business plan generator (the outputs `build_plan.js`, then regenerate
> `PhonoLeaf_Business_Plan.docx`). `STORE_LISTINGS.md` English and this file are
> already hyphen free. `LEGAL_FR.md` and other French text keep their grammatical
> hyphens.

---

## A. Naming and positioning

**[DECISION]** Lead with "turn any ebook into an audiobook." Stop assuming people
know the word "epub." Use "ebook" in user facing copy; mention supported formats
(EPUB) only in a details line, not the hook.

**[CODE]** Update user facing product name and copy to the audiobook framing:
- `manifest.json` `name` / `description`, and the app `<title>` / tagline in
  `index.html`, to something like name "PhonoLeaf" with a tagline "Turn any ebook
  into an audiobook."
- `home.html` headline: from "Your Google Drive epubs, read aloud" to something
  like "Turn any ebook into an audiobook" with a subline explaining it uses the
  ebooks in your Google Drive.
- Replace user facing "epub" with "ebook" except where the exact format matters.
- Store listing names and descriptions: already rewritten in `STORE_LISTINGS.md`
  (audiobook framing; French name fixed; no "epub" in the hook). Paste those.

**[ANSWER] French app name was uninformative.** The old French store name meant
"read out loud," which says nothing. Fixed in `STORE_LISTINGS.md` to convey "your
books, as audio" (see that file for the exact strings).

---

## B. French in the app itself

**[ANSWER]** Yes. Bill 96 covers the app interface for Québec consumers, not only
the web pages. The app already ships a French TTS voice, but the UI strings are
English.

**[CODE]** Localize the app UI (`index.html` strings) to French:
- Extract the visible UI strings into a small dictionary with `en` and `fr`.
- Auto select language from the device or browser locale (`fr*` to French), with a
  manual language switch in Settings, remembered in storage (reuse the `pl_lang`
  key from `LEGAL_FR.md` so the web pages and the app agree).
- English first for English speakers, French first for French speakers (the owner
  confirmed we do NOT force French for all Québec users).

---

## C. Offline (bug + differentiator)

**[ANSWER] Root cause of "no books offline."** The library list comes from a live
Google Drive API call, and book files are downloaded from Drive at read time and
are deliberately NOT cached. Offline, the Drive call fails, so the library is empty
and nothing can open. Today only the app shell and the on device voice work
offline, not the books. So the current "available offline" claim is not yet true
for the books themselves. The owner is right to want this fixed; it is a strong
differentiator versus cloud competitors.

**[ANSWER] Browser vs phone.** Offline is possible in BOTH. The browser PWA can
store the file list and downloaded ebooks in IndexedDB (ask for persistent storage
via `navigator.storage.persist()`; large libraries can hit browser quota, but
individual downloaded books are fine). The native app is more robust (it can use
the filesystem via Capacitor with no quota). So this is not phone only.

**[DONE 2026-08-10] Make books work offline:**
1. ~~After each successful library load, cache the Drive file list...~~ DONE —
   `Library._cacheBookList`/`_cachedBookList`, `localStorage.pl_libcache`.
2. ~~Cache downloaded ebook bytes in IndexedDB keyed by Drive file id...~~ DONE —
   see the new `BookCache` module (`index.html`, right after `CoverCache`).
3. ~~Auto cache a book when it is opened, and add an explicit "save for offline"
   control plus an indicator...~~ DONE — `Reader.open()` auto-caches on a
   download; `Library._offlineBtnHTML`/`toggleOffline` is the explicit control
   + badge.
4. ~~Native: prefer Capacitor Filesystem for the ebook bytes...~~ DONE —
   `BookCache` branches on `App.isNative()`, using `@capacitor/filesystem`
   (`Directory: 'DATA'`) natively instead of IndexedDB.
5. ~~Still open: gated behind a real device test~~ **DEVICE-VERIFIED
   2026-08-10** — owner confirmed download + offline mode both work on a
   real Android device. `home.html`/`STORE_LISTINGS.md`'s offline wording is
   now safe to publish.

---

## D. Library and third party book sources

**[RESEARCH] Libby, Hoopla, Prêt Numérique (and the USB idea).** The important
reality is DRM. Books lent by Libby/OverDrive, Hoopla, and Prêt Numérique (Cantook)
are almost always protected by DRM (Adobe ADEPT or Readium LCP) and are time
limited. A DRM protected loan cannot simply be copied to a USB stick or a Drive
folder and opened elsewhere, and the engine PhonoLeaf uses (epub.js) does not
decrypt DRM. So DRM protected library loans will NOT work in PhonoLeaf as is. We
will not build or advise any DRM removal (legal and app store issues).

What DOES work today: any DRM free ebook. That is a large market, for example public
domain titles, Project Gutenberg, Standard Ebooks, and DRM free purchases from
stores that offer them. Position around "the books you own, DRM free," not "library
loans."

**[RESEARCH] A legitimate future door: Readium LCP support.** LCP is a lighter,
license based protection that a growing number of libraries use (including some in
Québec via Cantook) and that has official reader SDKs. Adding legitimate LCP
support later could unlock some library loans without any circumvention. It is
nontrivial (a licensing and SDK integration), so it is a roadmap candidate, not a
quick win. Documented so we remember the opportunity.

### Owner question, 2026-08-17: could we read `.acsm` / `.lcpl` files directly?

Sharpens the DRM question above into the two actual file formats. They are not
the same problem.

- **`.acsm` (Adobe Content Server Message) — still a hard no, and this is a legal
  boundary, not an effort estimate.** An ACSM file is only a fulfillment ticket;
  reading it does not get you the book, it points an Adobe-authorized ADEPT
  client (like Adobe Digital Editions) at a download that is then decrypted
  using Adobe's own proprietary, device-activated protocol. There is no
  independent way to build a compatible reader without either an Adobe license
  we do not have, or reverse-engineering Adobe's encryption — the latter is
  exactly the "circumventing a technological protection measure" that the DMCA
  (US) and Canada's Copyright Act (s. 41.1) prohibit, independent of whether
  the underlying loan is legitimate. True even for a book the user honestly
  borrowed. Real legal exposure and an instant app-store takedown. Unchanged
  from the decision above; will not build or advise this.
- **`.lcpl` (Readium LCP License Document) — genuinely different, and worth
  actually scoping now that it has come up twice.** LCP was designed by EDRLab
  specifically so third-party apps CAN support it legitimately: published spec,
  an open-source reference client, and libraries (including Québec's own Prêt
  Numérique, via Cantook) issue LCP loans instead of Adobe DRM for exactly this
  reason. Reading the `.lcpl` file is not the hard part — validating the
  license against EDRLab's license status server and decrypting the content
  needs a real crypto client, which `epub.js` does not provide, so this is new
  engine work (likely a parallel code path for LCP-protected books), not a
  config flag.
  **Still needed before this can be scoped as a real task, none of it done
  yet:** what EDRLab's certification process for a new reading app costs and
  takes, whether Prêt Numérique/Cantook needs anything beyond generic LCP
  compliance, and how large the Readium LCP client integration is against
  PhonoLeaf's own epub.js-based renderer. **[RESEARCH, not started.]** Worth a
  dedicated pass — "your library loans work here" is a real, differentiated
  feature no competitor in `COMPETITORS.md` offers.

### Research pass, 2026-08-17: the real constraint is the web platform, not cost

**LCP is excluded from web/browser apps by EDRLab's own design — not a gap in
`epub.js`, a gap in what any browser app can ever do.** Quoting EDRLab's LCP
FAQ directly: *"No, LCP is not available for Web applications... Web
applications do not provide ways to avoid developers having access to their
code... it is not possible to include in a Web application a real DRM
solution, using secrets concealed in precompiled libraries."* Their
precompiled LCP client libraries exist for iOS, Android, Windows, macOS and
Linux — every one a native app, none a browser. This corrects the framing in
the entry above ("new engine work... not a config flag") — it understated
the constraint. It is not that `epub.js` lacks a crypto client that could in
principle be added; no browser-based app, on any renderer, can ever pass
LCP certification. **PhonoLeaf's web build could not get this feature under
any amount of engineering effort.**

A server-side workaround exists in theory (decrypt on a server, stream
obfuscated content to the browser) but EDRLab itself discourages it, calling
it something that "cannot be done generically" since the approach "would be
too easily defeated if made public." Building it would also mean standing up
real backend infrastructure that touches actual book content — the opposite
of "PhonoLeaf has no backend, nothing is ever uploaded," the core claim
across every comparison page in `COMPETITORS.md`'s "close the two gaps that
matter" strategy. Not pursuing this path.

**Native Android is the only realistic path**, using Readium's Kotlin LCP
library, and would be new, self-contained engineering (a real crypto client,
license validation against EDRLab's license status server) layered onto the
native build the same way `PhonoLeafTtsPlugin.kt`/`SecureStoragePlugin.kt`
already are. iOS would need a second, separate Swift port later.

**Owner pushback on the "web is primary, so native-only is a weak case"
framing above (2026-08-17): fair, and it changes the read.** Audiobook-style
listening skews heavily toward phones regardless of which PhonoLeaf platform
is currently more mature — people listen while walking, driving, commuting,
not typically at a desktop browser. So "native Android (and eventually iOS)
only" is a real limitation, but a much less crippling one than "this only
reaches a minority of realistic listening sessions." The corrected framing:
native-only excludes the web build specifically, but not necessarily the
platforms where this feature would actually get used most.

**Cost — genuinely not the blocker.** EDRLab's published LCP fee for a small
organization (under €300K in digital publication revenue) is **€300/year ≈
$350 USD/year** (EUR/USD ≈ 1.16, checked 2026-08-17 — re-check before
budgeting for real, this floats). That is on top of a separate EDRLab
membership fee that is **not publicly listed** and would need to be
requested directly from EDRLab before this is a complete number. Compare
against the CASA figures already tracked in `VERIFICATION.md`/`BUSINESS.md`
($693–770 one-time, ~$700/yr recurring) — this would be a second, much
smaller annual line item, not a comparable cost.

**Decision: park, don't scope further right now. Revisit if there's
demonstrated demand** (e.g. Québec/French-market users specifically asking
for Prêt Numérique support — see `COMPETITORS.md`'s "lean into the French
and Québec market" opportunity, which this would directly serve). Not
building until then. If it's picked up later, remaining open questions are
narrower than before: the exact EDRLab membership fee, whether Prêt
Numérique/Cantook needs anything beyond generic LCP certification (likely
not — interoperability with any certified reading system is the point of
the standard, but not independently verified), and the real size of a
Kotlin LCP integration against the existing native plugin architecture.

---

## E. More storage sources than Google Drive

**[RESEARCH + CODE later] Add alternatives to Google Drive.** Broadens the market
and reduces dependence on one provider. Feasibility:
- **Local / device import** (a file picker to add ebooks straight from the device
  or a USB import on desktop). Easiest and highest value: no third party OAuth, and
  it also enables the "copy DRM free books in" use case and true offline. Do this
  first.
- **Dropbox** (HTTP API) and **OneDrive / Microsoft Graph**: both feasible on web
  and native; each needs its own OAuth app and, like Google, its own review and
  upkeep, so treat each as a real cost.
- **iCloud Drive**: no general third party web API. On iOS you would use the Files
  document picker to import; there is no "connect a folder, new books appear"
  model. Lowest priority.
Note: every added provider multiplies the auth and verification work (the same kind
of process we are doing for Google), so add them deliberately, not all at once.

---

## F. Accessibility (a target segment)

**[ANSWER]** The device provides the basics (screen reader, font scaling), but
since accessibility users are a named target segment, a little dedicated work pays
off and differentiates.

**[CODE] Accessibility investments (cheap, high goodwill):**
- Audit the app for screen reader support: every control needs a clear label, and
  every gesture only action (swipe to turn, double tap to play) needs an equivalent
  labelled button so it is reachable with TalkBack/VoiceOver.
- Respect the system settings for larger text, high contrast, and reduced motion.
- Large tap targets and a simple onboarding.
- **Follow along highlighting**: highlight the word or sentence as it is read.
  This is a strong aid for dyslexia and ADHD and a genuine differentiator. Bigger
  build; flag as a feature candidate.

---

## G. Support and "who paid," given a minimal backend

**[ANSWER] We are adding a minimal backend for payments** (the entitlement Cloudflare
Worker in `PAYMENTS_SPEC.md`). So the old "no server at all" line is now "no server
for your books or reading data; a small entitlement service records only your
subscription status." Update the privacy wording accordingly when Stripe goes live
(already noted in `PAYMENTS_SPEC.md` section 7).

**[ANSWER] How we track who paid:** the entitlement Worker plus KV store, fed by
Stripe and store webhooks, is the record of who is entitled. See `PAYMENTS_SPEC.md`.

**[ANSWER] Customer support workflow (no big help desk needed):**
- Payment issues: the **Stripe Dashboard** lets you look up any customer by email,
  see charges, and issue refunds. **Google Play Console** and **App Store Connect**
  handle store purchases and refunds.
- App issues: the in app Feedback and Report a bug forms already email
  support@phonoleaf.com with device diagnostics.
- Entitlement fixes: the Worker can expose a tiny admin view or script to read or
  correct an entitlement by account, for the rare manual case.
**[CODE later]** A short internal "support playbook" doc once Stripe is live
(where to look for each issue type). Low effort.

---

## H. Voice engine: Kokoro on strong devices, Piper on weak

**[CONFIRMED in code, 2026-08] The intended behavior is NOT implemented today.**
Checked `index.html`:
- `_engineNow()` returns only `'kokoro'` or `'web'`. There is no Kokoro versus
  Piper switch, and no device capability check anywhere (no `hardwareConcurrency`
  or `deviceMemory` logic).
- Native app: uses the bundled or downloaded native model, which is Piper (vits).
  The family is detected from the model files, not chosen by device strength, and
  no native Kokoro model is shipped. So the Android app is Piper on every device.
  Kokoro was measured too slow on device (about 1.36x realtime on a Pixel 7) and
  was shelved for native.
- Web: uses WASM Kokoro with a speed probe (`_kokoroBench`). If the device is too
  slow (ratio over 1.25) it sets `_kokoroDead` and falls back to the device system
  voice, NOT to Piper.

So today, strong or weak, the Android app is Piper and the web is Kokoro or the
system voice. "Kokoro on capable devices" is only a note, not code.

**[CODE] To deliver the intended behavior on native:** ship or download a Kokoro
model alongside Piper, run a one time speed measurement on the device at first run
(reuse the existing bench idea), and pick Kokoro when the device sustains realtime,
else Piper. Confirm Kokoro covers the same languages as the Piper packs before
enabling it. This is a real feature, not a config flip. **Owner: confirm you want
this built, and whether it should apply on native, web, or both.**

---

## I. Ask for reviews (occasionally, never if already done)

**[CODE]** Prompt for a store review at good moments, without nagging:
- Use the platform review APIs, which already avoid repeat prompts and rate limit
  themselves: **Google Play In App Review** and Apple **SKStoreReviewController**.
- Trigger after a positive moment (for example finishing a book, or the Nth
  listening session), not on launch.
- Track locally that we asked (and roughly when) so we never ask often; the native
  APIs also cap this, and if the user already reviewed, the native prompt simply
  does nothing.
- On the web, a small dismissible prompt linking to the store, shown rarely, with a
  local flag so it does not repeat.

---

## J. Business plan corrections (done by me this pass)
- **CASA cost updated to ~$700 USD per year** (recurring) in `PhonoLeaf_Business_Plan.docx`.
- **Currency: all figures are USD**, noted in the plan. The one CAD figure (the Mac
  for iOS builds) is converted to USD with the CAD value in parentheses.

---

## Next up
Once the above is captured and the quick fixes are in, **SEO** is the next work
item (landing and marketing pages, keyword targeting, comparison pages).
