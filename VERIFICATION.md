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

1. Add a `CNAME` file at the repo root containing just the bare domain
   (e.g. `phonoleaf.com`). GitHub Pages reads this.
2. DNS at the registrar:
   - Apex `A` records → `185.199.108.153`, `185.199.109.153`,
     `185.199.110.153`, `185.199.111.153`
   - `CNAME` for `www` → `kbailey90.github.io`
   - Confirm current values against GitHub's docs at setup time.
3. GitHub repo → Settings → Pages → set the custom domain, wait for the cert,
   then enable **Enforce HTTPS**.
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
7. Repo cleanup: `privacy.html` hardcodes
   `https://kbailey90.github.io/PhonoLeaf/` in two places (brand link + footer)
   — repoint both. `home.html`, by contrast, uses relative links throughout and
   needs no change.

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

## Demo video — script (owner records)

Google requires an unlisted/public video showing, in English:

1. The **OAuth consent screen**, with the app name clearly readable, and the
   **browser address bar visible showing the OAuth client ID**. Do not crop the
   address bar — this is an explicit requirement and a common rejection cause.
2. The full sign-in flow, start to finish.
3. **What each restricted scope is actually used for.** For `drive.readonly`,
   show in one continuous take:
   - the folder picker listing folders from Drive,
   - choosing the ebook folder,
   - the library populating with epubs from that folder,
   - opening a book and playback starting.
4. State plainly (on screen or in narration) that access is read-only and that
   nothing is written back to Drive.

Suggested narration beats:

> "This is PhonoLeaf, a web app that reads the epub files in your own Google
> Drive aloud. I'm signing in with Google — you can see the consent screen and
> the client ID in the address bar. It requests Drive read-only access. Now I
> pick the Drive folder my ebooks are in. PhonoLeaf lists the epubs in that
> folder and downloads the one I choose so it can be displayed and read aloud
> on my device. It never modifies anything in Drive, and the files are never
> sent to any server — PhonoLeaf has no backend."

Record on the **custom domain**, after step 6 above, so the URL in the video
matches what's submitted.

---

## Owner checklist

- [ ] Re-check availability and buy the domain
- [ ] `CNAME` + DNS + GitHub Pages custom domain + Enforce HTTPS
- [ ] Search Console Domain property, verified **as the Cloud project Owner**
- [ ] Consent screen: authorized domain, homepage/privacy/terms URLs, app logo
- [ ] Web OAuth client: add the new JS origin (keep the old one)
- [ ] Repoint the two hardcoded URLs in `privacy.html`
- [ ] Record the demo video
- [ ] Submit for verification and record what Google says about CASA

## Not blockers, but Play Store will need them later

- A **release keystore** (none exists; `android/app/build.gradle` has no
  `signingConfig` and is still `versionCode 1`)
- A **third Android OAuth client** for the release keystore's SHA-1
