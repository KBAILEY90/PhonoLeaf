# PhonoLeaf: Business source of truth

Companion to `CLAUDE.md` (which is the engineering source of truth). This file holds
the commercial decisions: pricing, terms, priorities, and go to market. Keep it and
`CLAUDE.md`'s Productization roadmap item 5 in sync. The full narrative business plan
(positioning, market sizing, unit economics, competitor table verified 2026-08,
GTM, risks, 3/6/12-month plan) is the separate `PhonoLeaf_Business_Plan.docx`.

> ⚠️ **Not legal or tax advice.** The Terms clauses below are practical drafts to
> start from and are **not reviewed by a lawyer**. Subscription automatic renewal disclosure,
> free trial to paid conversion, and refund rights are regulated differently by
> jurisdiction (US state ARL / FTC "click-to-cancel"; EU/UK 14-day withdrawal;
> Canada provincial consumer protection and future performance rules). **Owner is
> in Longueuil, Québec, Canada**, so `[JURISDICTION]` = Québec, Canada, and
> Québec adds obligations most of Canada lacks: French language law (Bill 96), a
> strict Consumer Protection Act, and GST+QST. See "Québec compliance" below and
> confirm with a Québec lawyer/accountant before launch.

Placeholders (CONFIRMED by owner 2026-08): `[JURISDICTION]=Québec, Canada`,
`[CURRENCY]=USD`, `[REFUND_WINDOW]=14 days`, `[PRICE_CHANGE_NOTICE]=30 days`,
`[LIFETIME_REFUND_WINDOW]=12 months`. Support: `support@phonoleaf.com`.

### Québec compliance (owner is in Québec, don't skip)

Running a consumer app from Québec triggers obligations most of Canada lacks.
Confirm all of this with a Québec lawyer/accountant; recorded here so it isn't missed:

- **French language law (Bill 96 / Charter of the French Language).** A business
  with an establishment in Québec serving Québec consumers must provide a French
  version of content facing consumers (**including the Terms and Privacy policy,
  contracts of adhesion, marketing, and store listings**), on terms at least as
  favourable as English, and adhesion contracts should be **French first** (other
  languages only at the customer's request). Practical impact: `terms.html`,
  `privacy.html`, `home.html`, and the Play/App Store listings need French
  versions, and the app should be usable in French for Québec users. (The app
  already ships a French TTS voice pack, but that's the reading voice, not the
  UI/legal text.) This is real work to schedule before public launch, not a
  placeholder.
- **Governing law = Québec, and it can't be contracted away for Québec consumers.**
  Under the Consumer Protection Act a Québec consumer generally can't be forced to
  another jurisdiction's law/courts, and broad liability caps or class action /
  arbitration waivers may be unenforceable against them. The ToS governing law
  clause should name **Québec**; have the lawyer pressure test the liability +
  discontinuation clauses against the CPA.
- **Tax: GST + QST.** Digital subscriptions to Québec consumers attract GST (5%,
  CRA) and QST (9.975%, Revenu Québec). Register once over the small supplier
  threshold (or voluntarily); Stripe Tax / the app stores can compute, but
  registration and remittance are on you.
- **Business registration = Registraire des entreprises du Québec (REQ).**
  Operating under the name "PhonoLeaf" (not your own legal name) requires
  registering with the REQ, whether sole proprietor or incorporated (Québec or
  federal).

---

## Company structure

**Everbloom** is the parent company; **PhonoLeaf** is its first product. The plan is
a family of products, each named after something in nature (PhonoLeaf is a leaf), all
owned by Everbloom, whose name represents the whole of thriving nature. Everbloom is
the legal entity and brand umbrella; PhonoLeaf is the thing that has the website, the
app, and the customers, so it carries its own domain (phonoleaf.com) while Everbloom
does not need one.

- Use the same spelling of "Everbloom" on the CASA assessment and the Québec business
  registration (Registraire des entreprises du Québec) so the entity, the assessment,
  and future paperwork all line up.
- Trademarking the parent name is a later, optional step. "Everbloom" is somewhat
  common in other fields (gardening, wellness, retail) and the exact .com is taken,
  so a distinguished form (Everbloom plus a word) or a logo mark may be needed if the
  parent brand is ever protected. None of that affects using Everbloom as the company
  name now.

## 1. Pricing (DECIDED 2026-08)

| Plan | Price | Notes |
|---|---|---|
| Monthly | **$5.99** | Low friction entry; still well below Speechify. |
| Annual | **$49.99** | ~30% off, eff. $4.17/mo. Push this: best margin on web, lower churn. |
| Founding Member Lifetime | **$129** | One time payment, **limited to first ~500 buyers**, time boxed. Launch capital, not a core offer. |
| Free trial | **7 days** | Automatically converts to the selected paid plan unless cancelled. |

- **Model:** subscription only (no ads, since ads would break the privacy promise).
- **Positioning:** priced on value (privacy + offline + your own library), *not*
  to be the cheapest. Frame the low price as "runs on your device, so there's no
  cloud bill to pass on to you."
- **Economics:** on device TTS ⇒ ~zero COGS. Net ~$48 per annual web sub after
  Stripe (2.9% + $0.30); Play/Apple take 15% (steer buyers to annual on web).
  Blended net ARPU modeled ~$42/yr; break even, roughly 25–70 subscribers.
- **Entitlement:** tied to the Google account (no passwords). Web = Stripe +
  Cloudflare Worker; Android = Play Billing; lifetime = non consumable IAP / one-
  time Stripe charge, entitlement stored durably so a backend change can't revoke it.

### The Standard/Upgraded tier split: RESOLVED 2026-08-31, one tier stands

**Owner decision 2026-08-31, framed as provisional ("for now"): keep the one
tier above ($5.99/mo, $49.99/yr) and treat Kokoro as a free quality upgrade on
devices that pass the benchmark, which is how it is already built.** The
Standard/Upgraded split is rejected. No change to the table above; no new
billing work; nothing to implement.

**What would reopen it**, since the decision rests on two facts rather than a
preference: a Kokoro model gaining French/German/Spanish coverage (none exists
today), or `_KOKORO_MIN_GFLOPS` landing materially lower once borderline-device
testing gives it a second calibration point (it is 5.0 against a Pixel 7's
2.47). Either would change the arithmetic below.

**The condition this decision depends on:** $5.99 is defensible for the
*product*, not for the voice. The positioning therefore has to price the
product, which is what the 2026-08-30 reliability reframe in `SEO.md` §1 does.
Do not let "upgraded voice" become the headline claim anywhere.

The analysis that produced this follows.

### The Standard/Upgraded tier split: analysis (raised 2026-08-21, worked through 2026-08-30)

`TODO.md` carries this as an open decision: leave Standard (Piper) free or cheap
at $1–2/mo and charge $5.99–6.99 only for Upgraded (Kokoro). Below is the input
to that decision. Still the owner's call; nothing here changes the committed
pricing above.

**Two facts about Kokoro make it a bad axis to price on. Both are properties of
the model, not of our implementation, so neither is fixable by us.**

1. **Kokoro is English only, permanently.** `PhonoLeafTtsPlugin.kt` records this
   from inspecting the actual release assets on 2026-08-08: the "multi-lang"
   Kokoro releases add Mandarin, not French, German or Spanish, so there is **no
   Kokoro coverage for those languages at all**. A French user therefore cannot
   buy an Upgraded tier. Not "is unlikely to" but *cannot*. We would be showing
   the Québec market, the one Bill 96 compliance and four French pages exist to
   serve, an upgrade that is permanently unavailable to it.
2. **The hardware gate excludes most English users too.** `_KOKORO_MIN_GFLOPS` is
   5.0 against the reference Pixel 7's measured 2.47, i.e. roughly twice a 2022
   flagship (the owner's own framing at calibration time: "Pixel 7 is not strong
   enough but Pixel 12 is").

Together: the addressable market for an Upgraded tier is English speakers on very
high end phones. Everyone else lands on the cheap tier by default, so the split
would set the *real* price for most users at $1–2 while the premium tier stays a
rounding error. That is ARPU collapse rather than segmentation.

**Small monthly prices are destroyed by Stripe's fixed fee**, independent of the
above. Net revenue per user per year:

| Plan | Gross | Net | Fees |
|---|---|---|---|
| Current $49.99/yr | $49.99 | **$48.24** | 3.5% |
| $1.99/mo billed monthly | $23.88 | **$11.97** | 49.9% |
| $0.99/mo billed monthly | $11.88 | **$4.15** | 65.1% |
| $1.99/mo billed annually ($19.99) | $19.99 | **$19.11** | 4.4% |

At $0.99 monthly we keep 35 cents on the dollar. **Any cheap tier must be annual
only** to survive the $0.30 per transaction. Note this is a billing frequency
problem, not a "too cheap" problem.

**An argument that was raised and then withdrawn, recorded so it is not made
again:** that charging for Piper specifically is weak because @Voice users can
install Piper into it for free. This does not hold. Voice Dream charges **$79.99
a year** running Acapela, NeoSpeech and Ivona, all older generation engines its
own reviewers call stilted and robotic, which Piper comfortably beats. The market
pays for the product (folder sync, per paragraph position tracking, background
playback that survives the screen locking, offline caching, chapter navigation,
follow along highlighting), not for the engine inside it. @Voice's ~$15 is a one
time ad removal unlock on an ad supported app, a different business model, and
getting Piper into it means the user finds, installs and configures a third party
engine themselves.

**Conclusion, and the connected marketing point.** $5.99/$49.99 is defensible for
the *product*, not for the voice. The condition that has to hold is that our
positioning prices the product rather than the engine. This is exactly why the
2026-08-30 reliability reframe in `SEO.md` §1 matters commercially and not just
for search: it leads on what every user receives regardless of engine (keeps
playing when the screen locks, remembers your exact place, never waits on a
server) instead of on a voice most users will not get. **Do not let "upgraded
voice" become the headline claim anywhere**, or the gap between what is promised
and what most users receive reopens. Treat Kokoro as a free bonus where the
hardware allows, which is how it is already built.

---

## 2. Business priorities (owner view)

Ordered by what gates revenue or has a deadline. "Code" = implementable in the repo;
"Owner" = needs the owner (and sometimes an accountant/lawyer).

### Gating, do now
1. **Google OAuth verification, CASA AL1 assessment.** Hard deadline **Nov 3,
   2026**; nothing goes public without it. Engage the lab (Eydle), get "in
   process." *(Owner; see `VERIFICATION.md`.)*
2. **Register the business + money basics.** Structure (sole prop vs incorporate);
   register the "PhonoLeaf" name with the **Registraire des entreprises du Québec
   (REQ)**; business bank account; register for **GST (CRA) + QST (Revenu
   Québec)** for digital sales (Stripe Tax / the app stores compute; you register
   & remit). Needed **before** taking payments. *(Owner + accountant; see "Québec
   compliance".)*
3. **Payments + paywall backend.** Cloudflare Worker + Stripe (web), Play Billing
   (Android); 7 day trial; entitlement tied to Google account; lifetime as
   non consumable. Fold in the deferred bug report photo upload endpoint here
   (roadmap item 5). **Full architecture + endpoints + build order in
   `PAYMENTS_SPEC.md`, which also now carries the pre-implementation prep:
   prerequisites and their ordering (§11), the CASA impact (§12), and the
   decisions still open (§13).** *(Code.)*
   **Carries a CASA cost, confirmed by Eydle 2026-08-14: "Adding a backend
   will require a DAST scan. It may count as a significant change."** Today
   PhonoLeaf has no backend, which is what exempts it from the entire DAST
   and test-environment portion of the AL1 assessment. Standing one up
   removes that exemption. Budget **~$700–800 for a second assessment**
   whenever payments ship — the engagement letter's included re-assessments
   cover "verification of remediated findings only", so a scope change is
   very unlikely to be free. Confirm the exact figure with Eydle before
   committing to a payments timeline. Do **not** delay payments to avoid
   this: one assessment costs less than a single month of delayed
   subscription revenue at even modest volume. See `VERIFICATION.md`,
   "Eydle's answers (2026-08-14)".
4. **Finalize legal docs.** ~~Apply the Terms clauses in §3~~. **DONE
   2026-08-05**: `terms.html`'s old one paragraph "Pricing" section replaced
   with the full "Pricing & Payments" section from §3 (plans, free trial,
   automatic renewal, cancellation, refunds, lifetime terms, price changes, taxes,
   discontinuation, app store purchases), with a `TODO: lawyer review`
   comment left in place. Placeholders filled with the suggested defaults:
   USD, 14-day refund window, 30-day price change notice, 12-month
   lifetime refund window (**not yet confirmed by the owner or a lawyer**),
   easy to change (single edit each, values aren't repeated elsewhere).
   `[JURISDICTION]` still unset. It isn't referenced by the new Pricing &
   Payments block, but is still open in this ToS's existing generic
   liability section from before. `home.html`'s CTA note changed from
   "Free" to "Free 7 day trial"; `sw.js` CACHE bumped to `phonoleaf-v14` and
   `www/` restaged. **JURISDICTION confirmed 2026-08: Québec, Canada** (Longueuil); currency USD
   and the 14/30/12 windows all confirmed. ~~French versions of
   Terms/Privacy/marketing~~ **DONE** — `terms-fr.html`, `privacy-fr.html`,
   `home-fr.html` are live (Québec Bill 96). **Still gating launch**: a
   Québec CPA review of the liability and discontinuation clauses, and the
   overall lawyer review, see "Québec compliance".

### Launch
5. **Play Store release**: listing, ASO, screenshots, internal to closed to production.
   **Listing copy (EN + FR-CA) in `STORE_LISTINGS.md`.** *(Owner + Code for
   assets/copy.)*
6. **Landing + marketing foundation**: phonoleaf.com marketing/SEO pages,
   analytics, Product Hunt / Show HN, seed communities (r/kobo, r/ebooks,
   r/Calibre, r/audiobooks, r/dyslexia, MobileRead). Can start now (organic).
   *(Code drafts copy; Owner posts.)*

### After launch
7. **iOS (App Store) + growth**: referral program, small metered paid
   experiments, more voice packs. *(Owner + Code.)*
8. **Ongoing**: KPI tracker (trial to paid %, MRR/ARR, churn, ARPU, CAC), optional
   pitch deck. *(Code.)*

---

## 3. Terms of Service: "Pricing & Payments" (drop into `terms.html`)

Match `terms.html`'s existing heading/paragraph markup + light/dark styling. Leave a
`TODO: lawyer review` comment near this block. Also: remove the word "free" from `home.html`
("free 7 day trial, then a subscription"; keep "no separate account, sign in with
Google"); add a payments processor line to `privacy.html` once Stripe is live; bump
`sw.js` CACHE + run again `scripts/stage-www.js` when `terms.html`/`home.html` change.

**Québec additions (see `LEGAL_FR.md`):** add the **Governing law (Québec)** clause
to `terms.html` (after "Limitation of liability"), and (required by Bill 96)
create French versions of the Terms, Privacy, and landing pages. The full French
translations plus the governing law clause (English & French) are in
`LEGAL_FR.md`, with instructions for wiring in `terms-fr.html` / `privacy-fr.html`
/ `home-fr.html` and a language toggle.

```html
<h2>Pricing &amp; Payments</h2>

<h3>Plans and prices</h3>
<p>PhonoLeaf is offered as a paid subscription after a free trial. Current plans
(in [CURRENCY], excluding any applicable taxes):</p>
<ul>
  <li><strong>Monthly</strong>: $5.99 per month.</li>
  <li><strong>Annual</strong>: $49.99 per year.</li>
  <li><strong>Founding Member Lifetime</strong>: a one time payment of $129, offered
      in limited quantity to early supporters (see "Lifetime access" below).</li>
</ul>
<p>Prices are shown before you are asked to pay, and may change over time
(see "Changes to prices").</p>

<h3>Free trial</h3>
<p>New subscribers may start with a 7 day free trial. <strong>Unless you cancel
before the trial ends, it automatically converts to a paid subscription</strong>
(the plan you selected) and your payment method is charged at the then current
price. One trial per person/account. You can cancel at any time during the trial
to avoid being charged (see "Cancellation").</p>

<h3>Billing and automatic renewal</h3>
<p>Monthly and annual subscriptions <strong>renew automatically</strong> at the end
of each billing period at the then current price, and your payment method is
charged, until you cancel. By subscribing you authorize these recurring charges.
We will tell you the price and billing frequency before you subscribe.</p>

<h3>Cancellation</h3>
<p>You can cancel at any time. Cancellation stops future renewals; your access
continues until the end of the period you have already paid for. Cancel from your
account settings on phonoleaf.com if you subscribed on the web, or through your
Google Play or Apple App Store subscription settings if you subscribed in the app.</p>

<h3>Refunds</h3>
<p>For subscriptions purchased on <strong>phonoleaf.com</strong>: if you are not
satisfied, contact <a href="mailto:support@phonoleaf.com">support@phonoleaf.com</a>
within <strong>[REFUND_WINDOW]</strong> of your first payment for a full refund.
After that window, payments already made are not refundable except where required
by law; cancelling stops future charges. For purchases made through the
<strong>Google Play Store or Apple App Store</strong>, refunds are handled by that
store under its own policies, please request them there.</p>

<h3>Lifetime access ("Founding Member")</h3>
<p>A Founding Member Lifetime purchase is a one time payment that grants access for
the <strong>lifetime of the PhonoLeaf service</strong> (that is, for as long as
PhonoLeaf continues to be offered) and not for the natural life of the purchaser.
It is tied to your Google account, is not transferable, and is offered in limited
quantity. If PhonoLeaf is permanently discontinued (see "Availability and
discontinuation"), Lifetime access ends at that time; refunds in that event are
addressed in that section.</p>

<h3>Changes to prices</h3>
<p>We may change subscription prices. For existing subscribers, we will give at
least <strong>[PRICE_CHANGE_NOTICE]</strong> notice before a price change takes
effect at your next renewal, and you may cancel before then if you do not agree.
A price change does not affect a Lifetime purchase already made.</p>

<h3>Taxes</h3>
<p>Prices are exclusive of taxes unless stated otherwise. You are responsible for
any applicable sales tax, VAT, GST/HST or similar, which may be added at checkout
or collected by the app store.</p>

<h3>Availability and discontinuation</h3>
<p>We may modify, suspend, or discontinue PhonoLeaf (in whole or in part) with
reasonable notice where practicable. If we <strong>permanently discontinue</strong>
the service: (a) for monthly/annual subscribers, we will not charge further
renewals and will, where required or reasonable, refund the unused portion of a
prepaid term; and (b) for Founding Member Lifetime purchasers within the prior
<strong>[LIFETIME_REFUND_WINDOW]</strong>, we will provide a refund (full or
prorated at our reasonable discretion). Our total liability for the service is
limited to the amounts you paid us in the <strong>[LIFETIME_REFUND_WINDOW]</strong>
before the claim, except where such a limit is not permitted by law.</p>

<h3>Purchases through app stores</h3>
<p>If you buy a subscription or Lifetime access through the Google Play Store or
Apple App Store, that store's terms and billing, cancellation, and refund policies
also apply and may govern the transaction.</p>
```

---

## 4. Ready prompt for Claude Code

> Read `CLAUDE.md`, `BUSINESS.md`, `terms.html`, and `home.html`. Using `BUSINESS.md`
> §3: (1) add the "Pricing & Payments" section to `terms.html`, matching the page's
> existing markup and light/dark styles and replacing any older/placeholder pricing
> text; (2) update `home.html` to say "free 7-day trial, then a subscription" and
> remove any claim the app is free (keep the "no separate account, sign in with
> Google" line); (3) bump `sw.js` CACHE and run again `scripts/stage-www.js`. Fill the
> `[JURISDICTION]`, `[CURRENCY]`, `[REFUND_WINDOW]`, `[PRICE_CHANGE_NOTICE]`, and
> `[LIFETIME_REFUND_WINDOW]` placeholders with the values I give you, and leave a
> `TODO: lawyer review` comment near the new terms. Then run the repo's JS syntax
> check and commit. Do not remove the "not reviewed by a lawyer" caveat.
