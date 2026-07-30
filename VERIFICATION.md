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

---

## Demo video — shot list (owner records)

Google requires an unlisted-or-public video, **in English**, showing the OAuth
consent screen with the app name readable, the **OAuth client ID visible**, and
concrete proof of what each restricted scope is actually used for.

### ⚠️ Two things that will get the video rejected if missed

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

### Before you hit record

- [ ] Revoke access (see above).
- [ ] Open [Credentials](https://console.cloud.google.com/apis/credentials?project=phonoleaf)
      in a tab, ready on the **Web** client.
- [ ] Confirm the connected Drive folder has a few epubs in it.
- [ ] Chrome, maximised, English UI.
- [ ] Record the **whole screen** — not a cropped region. A cropped recording
      that hides the address bar is a documented rejection cause.
- [ ] Close unrelated tabs so the address bar is uncluttered and legible.

### The shots

| # | On screen | Say (roughly) |
|---|---|---|
| 1 | Cloud Console → Credentials → the **Web** OAuth client, client ID visible. Hold ~4s. | "This is the OAuth client being verified for PhonoLeaf — client ID `88179965472-codmbgtm…`." |
| 2 | Navigate to `phonoleaf.com` — the sign-in screen. | "PhonoLeaf is a web app that reads the epub files in your own Google Drive aloud." |
| 3 | Click **Sign in with Google**. Consent screen appears. **Hold ~5s, don't rush.** App name "PhonoLeaf" and the Drive permission must both be readable. | "Here's the consent screen. The app name is PhonoLeaf, and it's requesting read-only access to Google Drive." |
| 4 | Grant access → lands on Home. | "I grant access." |
| 5 | The folder browser opens (first run). Browse Drive, pick the ebooks folder. | "PhonoLeaf asks which Drive folder my ebooks are in. This is the only folder it reads." |
| 6 | Library populates with the epubs from that folder. | "It lists the epub files in that folder — this is what the `drive.readonly` scope is used for." |
| 7 | Open a book. Reader appears, press play, **let audio play audibly for ~5s**. | "It downloads the book I choose so it can be displayed and read aloud, entirely on my device." |
| 8 | Optional: Settings → show Privacy Policy / Terms links and Sign out. | "PhonoLeaf never modifies, uploads, or deletes anything in Drive — the access is read-only. There's no backend, so the files are never sent to any server of ours." |

Shots 5–7 should ideally be **one continuous take** — that's the part proving
the scope's actual use, and cuts there invite doubt.

### Before submitting the video

- [ ] Watch it back with sound.
- [ ] The consent screen is on screen long enough to read the app name.
- [ ] The client ID is legible in Shot 1.
- [ ] The address bar is visible throughout (never cropped out).
- [ ] It's on **`phonoleaf.com`**, not `kbailey90.github.io`, and not the
      native app — it's the **Web** OAuth client under review.
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
