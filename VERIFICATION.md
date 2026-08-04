# Google OAuth verification — submission plan

PhonoLeaf uses `drive.readonly`, which Google classifies as a **restricted**
scope. The app therefore cannot leave "Testing" mode (100-user cap) until
Google's verification team approves it. This file tracks what's required, what's
done, and what only the owner can do.

Sources:
- https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification
- https://support.google.com/cloud/answer/13804266 (domain verification)
- https://appdefensealliance.dev/casa

---

## BLOCKER 1 — a custom domain is required (owner action, longest lead time)

**`kbailey90.github.io` cannot be used.** Google's domain-verification doc
requires authorized domains be verified as a **Domain Property (DNS-level)** in
Search Console, which means adding a **TXT record to the domain's DNS**. You do
not control DNS for `github.io`, so this is impossible — not merely discouraged.
(Independently, developers report Google's review team rejecting github.io
because it isn't a first-party domain.)

Both the **homepage and the privacy policy must be on that same owned domain**.

CLAUDE.md recorded `phonoleaf.com/.ca/.app/.io` as available on 2026-06-28 —
re-check before buying. `.com` recommended.

### Steps once the domain is bought

1. **Set the custom domain in GitHub FIRST, before touching DNS.** GitHub's docs
   are explicit that adding the domain to GitHub before pointing DNS at it
   prevents a domain-takeover window. Repo → Settings → Pages → Custom domain.
   (This writes the `CNAME` file at the repo root for you — just the bare
   domain, e.g. `phonoleaf.com`.)
2. DNS at the registrar (values confirmed against GitHub's docs 2026-07-26):
   - Apex `A` → `185.199.108.153`, `185.199.109.153`, `185.199.110.153`,
     `185.199.111.153`
   - Apex `AAAA` (optional, IPv6) → `2606:50c0:8000::153`,
     `2606:50c0:8001::153`, `2606:50c0:8002::153`, `2606:50c0:8003::153`
   - `CNAME` for `www` → `kbailey90.github.io`
   - DNS can take up to 24 h to propagate.
3. Back in repo → Settings → Pages, wait for the certificate to issue, then
   enable **Enforce HTTPS**.
4. Google Search Console → add a **Domain property** → add the TXT record it
   gives you. **Verify while signed in as the Cloud project Owner** — the docs
   flag this as critical; verifying from a different account means the OAuth
   system won't recognise the ownership.
5. Cloud Console → OAuth consent screen → add the domain under **Authorized
   domains**, and set Application home page / Privacy policy / Terms URLs to
   the new domain.
6. Cloud Console → Credentials → **Web** OAuth client → add
   `https://phonoleaf.com` as an Authorized JavaScript origin.
   **Keep `https://kbailey90.github.io` in the list** until every existing
   install has migrated, or web sign-in breaks for them. (Per CLAUDE.md the
   origin is host-only, so the old one keeps working today.)
7. Repo cleanup — **DONE 2026-07-26.** `privacy.html` **and `terms.html`** each
   hardcoded `https://kbailey90.github.io/PhonoLeaf/` twice (brand link +
   footer); all four now point at `https://phonoleaf.com/home.html`. (An
   earlier note in this file said privacy.html only — that was wrong.) A stale
   lowercase URL in `TESTING.md` was fixed too. `home.html` uses relative links
   throughout and needed no change. The staged copies under
   `android/app/src/main/assets/public/` are generated and untracked — they
   refresh on the next `npm run sync`.

**Note:** browser storage is per-origin, so users on the old URL will appear
signed-out and lose local progress/stats on the new domain. The native app is
unaffected (its WebView origin doesn't change). Decide whether to redirect the
old path or leave both live for a while.

---

## BLOCKER 2 — a real homepage (DONE)

Google requires a homepage that is "publicly accessible, and not just accessible
to your site's logged-in users" and "clearly demonstrate[s] relevance to the app
under review". The app's root is a **sign-in wall**, which is a standard
rejection reason.

`home.html` was added for this: what the app does, how it works, an explicit
"How PhonoLeaf uses your Google Drive data" section covering the scope and the
no-backend/no-sale/read-only commitments, and links to the privacy policy and
terms. Give Google **this URL** as the Application home page.

Optional later polish: make the landing page the site root and move the app to
`/app/`. Deliberately not done now — it would touch `sw.js` cache scope,
`manifest.json` `start_url`, and `scripts/stage-www.js`, i.e. real regression
risk for no verification benefit, since reviewers use the URL you supply.

---

## CASA security assessment — genuinely unresolved until you submit

Google's rule: *"Every app that requests access to Google users' restricted data
**and has the ability to access data from or through a third-party server** must
go through a security assessment."*

PhonoLeaf has **no backend at all** — books go Google → device directly — so the
qualifying condition arguably isn't met. But the exemptions Google actually
enumerates are: personal-use-only, dev/testing/staging, service-owned data only,
internal-to-a-Workspace-org, and domain-wide installation. **"Public app with no
backend" is not among them.** So the exemption is a reading of the rule, not a
stated carve-out.

Reported costs if it *is* required: **AL1 ≈ $500** (self-assessment: scan +
questionnaire), **AL2 ≈ $3–6k** (full lab assessment). Google — not the
developer — assigns the level. Recertification is annual.

**The authoritative answer comes from submitting.** That is the main reason to
start this early rather than late.

### Re-researched 2026-07-29 — one finding that changes the shape of this

Google's own restricted-scope page states the assessment is initiated by
Google, not by you: **"the Google Trust and Safety team will contact you when
it is time to initiate the security assessment process."** So CASA is not a
gate you clear *before* submitting, and not something to buy pre-emptively —
you submit, and Google tells you whether an assessment is required and at what
level. Two practical consequences:
- **Nothing about CASA should delay recording the video or submitting.**
- **Don't pay for anything until Google asks.** The AL1/AL2 figures are what
  to budget for if they come back asking, not a cost to incur now.

Also confirmed: the assurance level is **dynamic** — Google states it "may
increase based on changes in your user base or data-handling practices," and
an app validated at AL2 stays at AL2 in later years. That is a direct argument
for the sequencing already decided in CLAUDE.md roadmap item 5: **submit while
the architecture is still backend-free**, because adding a payments server
later is exactly the kind of "change in data-handling practices" that could
raise the level on a future recertification.

The Limited Use requirements themselves (verbatim from the Google API Services
User Data Policy) are four: limit use to user-facing features prominent in the
app's UI; don't transfer the data except for those features, security, legal
compliance, or M&A with consent; no human reading of the data; and handle it
securely. Prohibited outright: transferring or selling to advertising
platforms/data brokers/resellers, and using it for ad serving, retargeting,
personalised advertising, or credit/lending decisions. `privacy.html` now
carries an explicit Limited Use commitment covering all of these (added
2026-07-29 — see the pre-submission audit note below).

---

## Adding iOS later — will that upset Google? (researched 2026-07-29)

Owner question ahead of an eventual iOS build: does adding a third OAuth
client (iOS) to an already-approved project, after this submission, risk
re-verification trouble? **Decision: build iOS whenever it's engineering-ready
— do not let it delay this submission.** Reasoning below.

**What's clearly documented.** The list of changes requiring re-verification is
specifically about the **consent screen's displayed elements** — app name,
logo/icon, redirect URI, homepage link, privacy policy link — and Google's own
wording calls this **"brand verification"**, a lighter, distinct process from
full scope re-verification. Adding a new OAuth client isn't on that list, and
no reports were found of it being treated as one — which would be a common
complaint if true, since adding a platform to an existing multi-client Google
Sign-In setup is a routine, extremely common developer action.

**Where it's genuinely ambiguous — being honest about this, not glossing over
it.** That list includes "redirect URI." Google's page doesn't clarify whether
that means *editing* an existing client's redirect URI, or whether
*registering a new client* (which necessarily has its own new redirect URI)
counts too. Best-supported read: it refers to what's displayed on the consent
screen itself, which doesn't enumerate per-client redirect URIs to the end
user (those live under Credentials, not Branding) — but this isn't nailed down
by a verbatim quote, and shouldn't be presented as settled.

**Why it doesn't matter for timing regardless of how that resolves:**
- **Annual re-verification is comprehensive by design, not a diff.** Google's
  own text: *"We require the annual CASA security reassessment to be a
  comprehensive test of your app, **regardless of any changes made to the
  app**."* It's Google-initiated by email and evaluates the project as it
  stands *at that time* — whatever clients exist when the email arrives get
  covered then, iOS included. Same reasoning already applied to keeping
  Android in the demo rather than deleting it.
- **Resubmission isn't limited to once a year** — nothing stops a voluntary
  resubmission sooner. Given the redirect-URI ambiguity above, the
  belt-and-suspenders move once iOS ships: record a short supplementary
  segment and proactively resubmit rather than waiting to be asked. Cheap
  insurance against the one part of this that's genuinely unclear.

**The practical case for "later" is strong on its own, independent of any of
the above.** iOS isn't a config change — it's a real second mobile platform:
a Capacitor iOS target, Apple Developer Program enrollment ($99/yr), a
*different* native auth flow (`ASWebAuthenticationSession`, not the Android
Custom Tab/PKCE plugin already built), a new native TTS plugin, and App Store
review with its own privacy questionnaire. Weeks of work that has not started
and has no version of "do it before this submission."

**Action item for whenever iOS ships:** add a short iOS segment to the demo
video and proactively resubmit, rather than waiting for the annual
re-verification email.

---

## Demo video — shot list (owner records)

Google requires an unlisted-or-public video, **in English**, showing the OAuth
consent screen with the app name readable, the **OAuth client ID visible**, and
concrete proof of what each restricted scope is actually used for.

### ⚠️ THREE things that will get the video rejected if missed

**1. Revoke the app's access BEFORE recording, or no consent screen appears.**
`App.signIn()` calls `getToken('')` — an empty `prompt`. Once you've already
granted access (you have), Google issues a token **silently** and the consent
screen never renders. The video would then contain no consent screen at all →
automatic rejection.
Fix, right before recording: go to
[Google Account → Third-party access](https://myaccount.google.com/permissions),
find **PhonoLeaf**, and click **Remove access**. The next sign-in then shows
the full consent screen. (Harmless — you just re-grant it during the recording.)

**2. The client ID probably WON'T be legible in the address bar.**
Web sign-in uses GIS `initTokenClient`, which opens the consent screen in a
**popup**, and popup windows render a truncated, read-only address bar (origin
only — usually just `accounts.google.com`), not the full URL with
`?client_id=…`. Don't rely on it.
Fix: **open the video on the Cloud Console credentials page with the Web client
ID plainly visible** (Shot 1 below). That satisfies "show the client ID"
unambiguously, regardless of how the popup renders. If the popup *does* happen
to show the full URL, that's a bonus, not the plan.
**Do a dry run and look**: if the popup's address bar shows the client ID,
capture it and you've satisfied the requirement literally.

**3. You have TWO OAuth clients — the video must cover BOTH.**
Google, verbatim: *"If you use multiple clients, and therefore have multiple
OAuth client IDs, you must show how the data is accessed on each OAuth
client."* This project has a **Web** client and an **Android** client, both
requesting `drive.readonly`, both under the same consent screen. A web-only
video is therefore incomplete by Google's own rule.
Add a second segment recorded off the phone (screen recording, or a steady
camera): open the native app → sign in (the Chrome Custom Tab consent flow) →
folder picker → library lists the epubs → open a book → playback. Same story,
second client. Say out loud which client each segment is.
If you'd rather not demo Android yet — reasonable, since the only Android
client today is tied to the **debug** keystore and the app isn't released —
the alternative is to **delete the Android OAuth client before submitting**
and re-create it (against the release keystore) when you actually ship to
Play. Then the project genuinely has one client and a web-only video is
complete. **Decide this before recording**, not after.

**Is delete-then-restore allowed? (researched 2026-07-29)** Google's list of
[changes that require re-verification](https://support.google.com/cloud/answer/13464018)
is short and specific: **adding new sensitive/restricted scopes**, and
**changing the app name, logo/icon, redirect URI, homepage link or privacy
policy link** on the consent screen. **Adding an OAuth client ID is not on
that list**, so mechanically, adding an Android client to an already-approved
project — with scopes that are already approved — does not itself trip a
documented re-verification trigger, and there is no automatic "flag".
But mechanics aren't the whole answer, and two things are worth being clear
about:
- **There's a real difference between housekeeping and concealment.** Deleting
  the client because Android genuinely isn't a shipping platform (true today:
  debug keystore, not on Play, not distributed to anyone) is honest — the
  review then reflects what actually ships. Deleting it purely to keep it out
  of the reviewer's view, intending to restore *the same client* right after
  approval, means the approval rests on an incomplete picture of the app. No
  alarm fires, but it isn't a position worth being in for a product that
  intends to operate on this grant for years.
- **It's a deferral, not an escape.** Restricted-scope apps get **annual
  re-verification**, and a release Android client is needed for Play anyway
  (different SHA-1 from the debug one). So the Android client has to be
  disclosed and demonstrated eventually regardless — the only question is
  whether that happens now or at the next review.

**Practical blocker that likely settles it:** deleting the Android client
breaks native sign-in *immediately*, and verification review can take weeks.
That means no working Android build to test on for the entire review window,
stalling native development. Recording ~3 extra minutes of phone footage is
almost certainly less costly — and removes the judgement call entirely.

### Before you hit record

**Desktop (opening shot + Segment A)**

- [ ] Revoke access (see above).
- [ ] Rename the Android client off "(debug)" — e.g. "PhonoLeaf Android" —
      before recording the opening shot; a "(debug)" label invites the
      reviewer to wonder why a dev build is being submitted.
- [ ] Open [Credentials](https://console.cloud.google.com/apis/credentials?project=phonoleaf)
      in a tab, with both the **Web** and **Android** clients visible.
- [ ] Confirm the connected Drive folder has a few epubs in it.
- [ ] Chrome, maximised, English UI.
- [ ] Record the **whole screen** — not a cropped region. A cropped recording
      that hides the address bar is a documented rejection cause.
- [ ] Close unrelated tabs so the address bar is uncluttered and legible.

**Phone (Segment B)**

- [ ] `npm run sync` + rebuild first — the installed build is behind, and one
      new Kotlin plugin (`EmailComposerPlugin.kt`) has never been compiled.
      Don't discover a build failure mid-recording.
- [ ] Revoke access **again** after Segment A, and sign out inside the app.
      ⚠️ **Expect TWO "PhonoLeaf" entries** on the permissions page — OAuth
      grants are recorded per *client ID*, and this project has a Web and an
      Android client, both showing the same consent-screen branding. **Revoke
      every entry**, not just the first: leaving the Android grant intact means
      Segment B signs in silently with **no consent screen**, which is exactly
      what fails the review. Re-check the page right before recording B.
- [ ] Phone language set to English.
- [ ] Silence notifications (Do Not Disturb) — a banner mid-consent-screen
      looks bad and can obscure the app name.
- [ ] Screen recorder set to capture **microphone** audio for narration.

### The shots

One video, two segments — the Web client, then the Android client. Record them
as two clips and join them; there's no need for a single unbroken take across
devices.

**⚠️ Revoke access AGAIN between the two segments.** Granting during Segment A
means the grant exists when you start Segment B, and the Android consent screen
may then be skipped entirely. Go back to
[Third-party access](https://myaccount.google.com/permissions) → PhonoLeaf →
Remove access, before recording the phone. Also sign out inside the app.

#### Opening shot — establish BOTH clients (desktop, ~8s)

| # | On screen | Say |
|---|---|---|
| 0 | Cloud Console → [Credentials](https://console.cloud.google.com/apis/credentials?project=phonoleaf), showing the **Web** and **Android** OAuth clients in the list (confirm the Android one now reads "PhonoLeaf Android", not "(debug)" — rename it first if you haven't). Click into each briefly so both IDs are readable. | "PhonoLeaf uses two OAuth clients in one project: a Web client, `88179965472-codmbgtm…`, and an Android client, `88179965472-cs9869…`. I'll show Drive access on both." |

This single shot satisfies "show the client ID" for both clients up front, which
matters because neither the GIS popup nor the Android Custom Tab reliably
exposes a full URL in a readable address bar.

#### Segment A — Web client (desktop, Chrome)

| # | On screen | Say |
|---|---|---|
| A1 | Navigate to `phonoleaf.com` — the **welcome screen**. Address bar visible. Let it sit ~3s on the hero + tagline, then scroll slowly through the three numbered steps ("Connect a Drive folder" / "Your library appears" / "Press play") and the read-only/no-server line — **don't click Sign in yet**. | "This is the web app, on phonoleaf.com, using the Web OAuth client. Before asking for anything, it explains what it does: connect a Drive folder, your library appears, press play. And it states up front that access is read-only and there's no backend — nothing leaves the device." |
| A2 | Scroll back up, click **Sign in with Google**. Consent screen appears. **Hold ~5s.** App name "PhonoLeaf" and the Drive permission must both be readable. | "Now I sign in. Here's the consent screen — app name PhonoLeaf, requesting read-only access to Google Drive." |
| A3 | Grant → lands on Home. | "I grant access." |
| A4 | Folder browser opens (first run). Browse Drive, pick the ebooks folder. | "PhonoLeaf asks which Drive folder my ebooks are in. That folder is the only one it reads." |
| A5 | Library fills with the epubs from that folder. | "It lists the epub files in that folder — this is what the drive.readonly scope is used for." |
| A6 | Open a book → reader appears → press play → **let audio play audibly ~5s**. | "It downloads the book I pick so it can be displayed and read aloud, on my device." |
| A7 | Settings → show the Privacy Policy and Terms links, and Sign out. | "PhonoLeaf never modifies, uploads or deletes anything in Drive — the access is read-only. There's no backend, so files are never sent to any server of ours." |

**A4–A6 in one continuous take** — that's the segment proving actual scope use,
and cuts there invite doubt. A1's slow scroll is worth the extra few seconds: it
puts the "why does this app want Drive access" answer on screen before the
consent prompt even appears, which is exactly what a reviewer is looking for.

#### Segment B — Android client (phone)

Record with Android's built-in screen recorder (swipe down → **Screen record**),
**with the microphone enabled** so you can narrate as you go.

Before you start: revoke access (above — check for BOTH grants), and make sure
the app is signed out.

| # | On screen | Say |
|---|---|---|
| B1 | The PhonoLeaf app open on the **welcome screen** (same content as A1 — hero, three steps, read-only/no-server line). Scroll through it slowly, **don't tap Sign in yet**. | "Same app, Android build, using the Android OAuth client `88179965472-cs9869…`. Same explanation up front — what it does, and that Drive access is read-only." |
| B2 | Scroll back up, tap **Sign in with Google**. A Chrome Custom Tab opens the consent screen. **Hold ~5s** — app name and the Drive permission readable. | "It opens the consent screen in the system browser. Same app name, same read-only Drive permission." |
| B3 | Grant → returns to the app, signed in. | "I grant access, and it returns to the app." |
| B4 | Folder browser → pick the ebooks folder. | "I pick the same Drive folder." |
| B5 | Library fills with the epubs. | "The Android client lists the epubs in that folder — the same drive.readonly usage." |
| B6 | Open a book → press play → **let audio play audibly ~5s**. | "And reads it aloud, generated on the device." |
| B7 | Optional but good: lock the phone and show playback continuing on the lock screen. | "Playback continues with the screen off." |

**B4–B6 in one continuous take**, same reasoning as A.

#### Joining and uploading

Stitch the clips in order: opening shot → Segment A → Segment B. Any basic
editor works; no titles or effects are needed, and narration matters more than
production value. Then upload to YouTube as **Unlisted**.

### Before submitting the video

- [ ] Watch it back with sound.
- [ ] The consent screen is on screen long enough to read the app name.
- [ ] The client ID is legible in Shot 1.
- [ ] The address bar is visible throughout (never cropped out).
- [ ] The web segment is on **`phonoleaf.com`**, not `kbailey90.github.io`.
- [ ] **Both OAuth clients are covered** (web + Android), or the Android client
      has been deleted from the project — see trap 3.
- [ ] The app name and branding on screen match the consent screen exactly —
      *"demo doesn't match the submitted app's name and branding"* is a
      documented rejection reason.
- [ ] Upload to YouTube as **Unlisted** (not Private — reviewers must be able
      to open it without being added as a viewer).

---

## OWNER CHECKLIST

Two decisions first — they unblock everything else and one of them can save
you two weeks.

### Decisions

- [ ] **Which domain.** `phonoleaf.com` recommended. Check availability at
      [Namecheap](https://www.namecheap.com/domains/registration/results/?domain=phonoleaf.com),
      [Cloudflare Registrar](https://dash.cloudflare.com/?to=/:account/domains/register)
      (at-cost pricing, no markup), or
      [Porkbun](https://porkbun.com/checkout/search?q=phonoleaf.com).
- [ ] **Play Console account type: personal or organization?** New **personal**
      accounts must run a closed test with **12 testers opted in continuously
      for 14 days** before they can ship to production.
      **Organization accounts registered to a legal business entity are exempt
      entirely.** If you're likely to incorporate anyway, doing it before you
      register removes a hard 14-day gate.
      [Requirement details](https://support.google.com/googleplay/android-developer/answer/14151465)

### Verification track — in this order

- [x] **1. Buy the domain** — `phonoleaf.com`, bought 2026-07-26.
- [x] **1b.** Root `CNAME` file committed, and all hardcoded github.io URLs in
      `privacy.html` / `terms.html` / `TESTING.md` repointed. (Me.)
- [x] **2. Custom domain set in GitHub Pages** (done 2026-07-26).
- [x] **3. DNS records added** — at **Cloudflare, DNS-only / grey-cloud (not
      proxied)**: 4×A, 4×AAAA, `CNAME www` → `kbailey90.github.io`. All 9 live.
- [x] **4. Enforce HTTPS** on, certificate issued.
- [x] **5. Search Console** — `phonoleaf.com` verified as a **Domain property**
      via TXT, **as `baileyke90@gmail.com`**, the account that owns the
      `phonoleaf` GCP project. (This was the critical bit.)
- [x] **6. OAuth consent screen** repointed to the `phonoleaf.com` URLs;
      authorized domains now `phonoleaf.com` **+ the retained
      `kbailey90.github.io`**. App logo uploaded manually by the owner after
      the console threw a generic backend error on automated attempts — the
      asset is `brand/oauth-consent-logo-120.png`.
- [x] **7. Web OAuth client** — `https://phonoleaf.com` added as a second
      authorized JS origin; `https://kbailey90.github.io` **kept**.
- [x] **8. Live site verified 2026-07-26**: `home.html`, `privacy.html` and
      `terms.html` all serve over HTTPS with repointed links, and the old
      `kbailey90.github.io/PhonoLeaf/` returns a **301 → `phonoleaf.com`**.
      **Sign-in on `phonoleaf.com` confirmed working by the owner 2026-07-28** —
      the new JS origin is live and the full Google round trip succeeds on the
      new domain. (Storage is per-origin, so it behaves like a fresh install:
      signed out, no local progress, covers re-downloading. Expected, not a bug.)
- [ ] **9. Record the demo video** — **shot list in the section above.**
      ⚠️ **Revoke the app's access at
      [Third-party access](https://myaccount.google.com/permissions) first**, or
      the consent screen won't appear at all (sign-in passes an empty `prompt`,
      so an existing grant is honoured silently) — that alone would fail the
      review. Open on the Cloud Console credentials page so the client ID is
      legible; the GIS popup's address bar can't be relied on for it.
- [ ] **10. Submit for verification**, then tell me what Google says about
      CASA so it can be recorded here.
      [How to submit](https://support.google.com/cloud/answer/13463073)

### Play Store track — can run in parallel

- [ ] **Register the Play Console account** ($25 one-time).
      [Sign up](https://play.google.com/console/signup)
- [ ] If you went **personal**: start recruiting the 12 testers early — the
      14-day clock is the gate, and it runs independently of everything above.
- [ ] **Create the release keystore.** Tell me when you're ready and I'll give
      you the exact `keytool` command plus the Gradle signing config — the
      keystore file and passwords must never be committed. (Today
      `android/app/build.gradle` has **no** `signingConfig` and is still
      `versionCode 1`, so no release build is possible yet.)
- [ ] **Create a third Android OAuth client** for the release keystore's SHA-1
      (same Credentials page as step 7). Reminder from CLAUDE.md: tick
      **"Enable custom URI scheme"** under Advanced Settings — it's off by
      default and its absence has already cost one debugging session.

### Still owed from earlier work

- [ ] **Confirm sign-out → sign-in still works on device.** The refresh token
      moved to Android Keystore storage and that change was never explicitly
      device-verified (it's likely fine — you've been signing in throughout —
      but a deliberate sign-out/sign-in round trip would close it out).
- [ ] Optional: get the ToS reviewed by an actual lawyer before public launch,
      and decide a jurisdiction (currently left generic).

---

## Pre-submission audit (2026-07-29)

Full pass over requirements, code, branding and legal pages before recording.

### Fixed as part of this audit

- **`privacy.html` had no explicit Limited Use affirmation.** Google requires
  the policy to "comply with the Google API Services User Data Policy and the
  Limited Use requirements for restricted scopes." The policy covered the
  *substance* thoroughly (read-only, no sale, no ads, no AI training, no
  server) but never referenced the policy **by name** — and reviewers look for
  that explicit statement. This is a well-known rejection cause. Added a
  "Limited Use commitment" card naming and linking the Google API Services
  User Data Policy and enumerating all four Limited Use requirements.

### Checked and clean

- **No debug leftovers**: zero `console.log`/`debugger` in `index.html`; no
  `TODO`/`FIXME`/`HACK` markers in `index.html` or `sw.js`.
- **Branding is consistent** — "PhonoLeaf" everywhere: `manifest.json`
  (`name` + `short_name`), `home.html`, `privacy.html`, `terms.html`, the app
  UI, the launcher/notification icons, and the consent screen. This matters
  directly: *"demo doesn't match the submitted app's name and branding"* is a
  documented video-rejection reason.
- **Bug-report diagnostics leak nothing sensitive** — `_diagnostics()` collects
  user agent, screen size, language, build and voice-engine state only; no
  token, no auth state, no `pl_*` storage values. Confirmed by grep.
- **No secrets tracked in git** — no `.jks`, `.keystore`, `.env` or
  credential files. The release keystore (when created) must stay untracked.
- **Homepage requirement satisfied** — `home.html` is publicly accessible (not
  behind the sign-in wall), its relevance to the app is explicit, and it is on
  the same domain as the privacy policy, as required.
- **Privacy policy is on the same domain as the homepage** and linked from the
  consent screen — both required.

### Known, accepted, not blockers

- **`CONFIG.API_KEY` is dead code.** Unused since the Picker revert (the folder
  browser is our own UI). It's a referrer-restricted browser key, so leaving it
  is not a security problem, and CLAUDE.md documents why it was kept. Harmless
  either way — worth deleting only as tidiness, not before submission.
- **ToS still flags itself as not lawyer-reviewed**, and jurisdiction is
  generic. Fine for verification; matters more once payments land.
