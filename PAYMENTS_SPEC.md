# PhonoLeaf: Payments and entitlement spec (roadmap item 5)

How PhonoLeaf turns "signed in with Google" into "is this user allowed to use the
paid features," across web (Stripe) and the app stores (Play Billing now, StoreKit
later), with a 7 day trial and a one time lifetime, without building a password
system and without compromising the privacy story.

> Design rules carried from `CLAUDE.md`: no user accounts/passwords (identity =
> Google account); no per user server for reading data; keep any payments
> infrastructure **strictly segregated from Google Drive user data**; entitlement
> must survive backend changes (especially lifetime). Pricing is in `BUSINESS.md`
> ($5.99/mo · $49.99/yr · $129 lifetime · 7-day trial).

---

## 1. Identity

- Add the `openid` scope to the existing Google sign in so the app receives an
  **ID token** containing a stable, unique `sub` (the Google account id). `sub`,
  not email (emails change), is the entitlement key.
- The client never asserts its own entitlement. Every call to the entitlement
  backend carries the **Google ID token**; the backend verifies it
  (Google JWKS, `aud` = our CLIENT_ID, `iss`, `exp`) and derives `sub` server side.
- Store `sub` **hashed** (e.g. SHA-256 with a server secret) in the entitlement
  store so the payments data isn't trivially linkable to a Google identity.

## 2. Backend: Cloudflare Worker + KV

A single Worker (the DNS is already at Cloudflare) with a KV namespace
`ENTITLEMENTS`. KV value keyed by `sub_hash`:

```json
{
  "status": "none | trial | active | lifetime",
  "source": "stripe | play | apple",
  "plan":   "monthly | annual | lifetime | null",
  "trial_end":  1731000000,
  "period_end": 1733600000,
  "updated_at": 1730000000
}
```

The Worker is the **single source of truth** and merges purchases from any platform
onto the same `sub`, so a user who buys on web is entitled on Android, and vice
versa. (Fold the deferred bug report photo upload endpoint into this same Worker,
per `CLAUDE.md` roadmap item 5, one backend, not two.)

### Endpoints
| Method + path | Auth | Purpose |
|---|---|---|
| `POST /checkout` `{plan}` | Google ID token | Create a Stripe Checkout session (`client_reference_id = sub_hash`); return URL. |
| `POST /portal` | Google ID token | Return a Stripe Billing Portal URL (manage/cancel/update card). |
| `GET /entitlement` | Google ID token | Return a **signed** entitlement (see §6). |
| `POST /verify-play` `{purchaseToken, productId}` | Google ID token | Verify a Play purchase with the Play Developer API, record entitlement. |
| `POST /verify-apple` `{jws}` | Google ID token | (later) Verify a StoreKit2 transaction, record entitlement. |
| `POST /webhooks/stripe` | Stripe signature | React to subscription/payment lifecycle. |
| `POST /webhooks/play` | Pub/Sub push (RTDN) | (later) React to Play renewals/cancels/refunds. |

## 3. Trial (7 days, no card up front)

Recommended: a **server side trial**, not a Stripe trial that requires a card up front, better
conversion and it fits the Worker as source of truth model.
- On first authenticated `GET /entitlement` for a new `sub`, set
  `status:"trial"`, `trial_end = now + 7d`. Return trial.
- App gates paid features on `status in {trial, active, lifetime}`.
- When the trial expires, `/entitlement` returns `status:"none"`; the app shows the
  paywall.
- Abuse (new Google accounts to start a new trial) is accepted for an indie launch,
  creating Google accounts is real friction. Revisit device signals only if abused.

## 4. Web payments (Stripe)

- **Products/prices:** subscription with two prices ($5.99/mo, $49.99/yr) and a
  **one time** price for lifetime ($129, Checkout `mode: "payment"`).
- **Flow:** app (user already signed in with Google) → `POST /checkout {plan}` → Worker
  creates Checkout session with `client_reference_id = sub_hash` and metadata →
  redirect to Stripe → on return, app fetches again `/entitlement`.
- **Truth comes from webhooks**, not the redirect: handle
  `checkout.session.completed`, `customer.subscription.updated/deleted`,
  `invoice.paid`, `invoice.payment_failed`. Each maps the Stripe
  customer/subscription to `sub_hash` and writes the KV record (`active` +
  `period_end`, or `none`). Verify the Stripe signature; make handlers idempotent.
- **Cancellation / manage:** `POST /portal` → Stripe Billing Portal.
- **Tax:** enable **Stripe Tax** so GST + QST (Québec) and other jurisdictions are
  computed and collected. (Registration and remittance is the owner's, see
  `BUSINESS.md` "Québec compliance".)

## 5. Android payments (Google Play Billing)

- **Products:** a subscription with `monthly` and `annual` base plans + a
  **one time, non consumable** product for lifetime (NOT a subscription).
- **Flow:** native app launches Play Billing purchase → on success, send the
  `purchaseToken` + `productId` to `POST /verify-play` → Worker verifies with the
  Google Play Developer API and writes the KV record keyed to the same `sub_hash`.
  This unifies Play purchases with web entitlement.
- **MVP shortcut (optional):** the app can also trust Play's on device
  `queryPurchases()` for Android local entitlement so it works even if the Worker
  is briefly unreachable, but the Worker record is still the cross platform truth.
- **Renewals/refunds:** add Play **real time developer Notifications** (RTDN via
  Pub/Sub) → `POST /webhooks/play` later; until then, verify again on app launch.
- Play takes **15%** at this scale, so in the app, still offer annual and (where
  policy permits) point web savvy users to web checkout for the better margin.

## 6. Entitlement delivery + offline grace (durability)

- `GET /entitlement` returns a **short JWT signed by the Worker** (Worker holds the
  private key; the app ships the public key) containing `{status, plan, exp}`.
- The app **caches** the signed entitlement and trusts it offline until its `exp`
  (e.g. 7-day grace for subscriptions). This means a temporary Worker outage never
  locks paying users out.
- **Lifetime is extra durable:** issue lifetime entitlement JWTs with a long life
  (e.g. 1 year) and refresh silently and automatically; the KV record is permanent. A backend
  change must never revoke a paid lifetime (see `BUSINESS.md`). Keep a durable,
  exportable record of lifetime `sub_hash`es.

## 7. Security & privacy

- Verify Google ID tokens on every authed call (signature via Google JWKS, `aud`,
  `iss`, `exp`). Reject otherwise.
- Verify Stripe webhook signatures and Play/Apple receipts **server side**, never
  trust the client for entitlement.
- Store the **minimum**: `sub_hash`, entitlement, Stripe customer/subscription id,
  Play/Apple order ids. **No Google Drive data, no OAuth Drive tokens** in the
  payments store, keep it a separate system from the reading app's data.
- **Privacy policy update:** once Stripe is live, add a line to `privacy.html` /
  `privacy-fr.html`: payment details are processed by the payment provider
  (Stripe / the app store) and are not stored by PhonoLeaf; a hashed Google account
  identifier is stored solely to remember your subscription status. (Today's policy
  says "no backend" becomes "no backend for your books/reading; a minimal
  entitlement service records only subscription status.")

## 8. In app paywall (both stores require this)

The paywall must clearly show, before purchase: the **price**, the **billing
period**, that it **renews automatically**, how to **cancel**, and link to Terms + Privacy.
(The ToS "Pricing & Payments" section already covers the legal side, see
`BUSINESS.md` §3 / `terms.html`.) Trial screens must state that the trial converts
to a paid subscription and when.

## 9. Build order (suggested)
1. Add `openid` scope; get `sub`; stand up the Worker + KV + `GET /entitlement`
   with the server side trial. Gate the app on entitlement. (No money yet,
   validates the whole gate.)
2. Stripe web: products, `/checkout`, `/portal`, webhooks, Stripe Tax. Web paid.
3. Play Billing: products, purchase flow, `/verify-play`. Android paid + unified.
4. Offline grace signed entitlement + lifetime durability hardening.
5. Later: Apple StoreKit2 + `/verify-apple`; Play RTDN + `/webhooks/play`.

## 10. Testing
- Stripe **test mode** + test clocks for renewals/trials; verify webhook idempotency.
- Play **license testing** / closed track sandbox for purchase + verification.
- Entitlement gate: trial start/expiry, active↔none transitions, lifetime never
  expires, offline grace within/after `exp`, cross platform merge (buy on web →
  entitled on Android with the same Google account).
- Refund/chargeback/cancel → entitlement drops to `none` on the next webhook/verify.

---

# Pre-implementation prep (added 2026-08-14)

Sections 1–10 above describe what to build. Sections 11–13 describe what has to
be true, decided, or budgeted **before** the first line of Worker code, so that
building it is a sprint rather than a stop-start. Written while payments are
deliberately parked pending the CASA decision — see `VERIFICATION.md`.

## 11. Prerequisites, and which ones are on the critical path

The code is not the long pole. Most of these are external processes with real
lead times, and several are strictly ordered.

| # | Prerequisite | Blocks | Owner | Notes |
|---|---|---|---|---|
| 1 | **Business registration** (REQ Québec) | 2, 3, 4 | Owner + lawyer | **The actual critical path.** Currently waiting on counsel. Nothing financial can proceed without it. |
| 2 | Business bank account | 4 | Owner | Needs 1. |
| 3 | **GST + QST registration** (CRA + Revenu Québec) | Stripe Tax config | Owner + accountant | Mandatory above the $30k small-supplier threshold; register before revenue, not after. |
| 4 | **Stripe account** | all web payments | Owner | Needs 1 and 2. Business details + bank account + tax IDs. |
| 5 | **Play Console account** ($25 one-time) | all Android payments | Owner | Identity verification takes days. **Decide personal vs organization before creating it — converting later is painful and an organization account needs a registered entity plus a D-U-N-S number.** |
| 6 | Play Developer API service account | `/verify-play` | Owner + Code | Created in Cloud Console, granted access in Play Console. Produces a JSON key (see §12 secrets). |
| 7 | Cloudflare Workers + KV namespace | everything | Code | Free tier is sufficient at launch. Account already exists (DNS is there). |
| 8 | `openid` scope on the OAuth consent screen | identity (§1) | Owner | See the warning below. |
| 9 | `api.staging.phonoleaf.com` subdomain | the CASA DAST scan | Owner + Code | See §12. Must exist before the next assessment, not during it. |

> **Do not add the `openid` scope while a CASA assessment is open.** Google's
> verification email states: *"if you plan on adding or removing restricted
> scopes to your project during your security assessment, please notify your
> security assessor in advance."* `openid` is non-sensitive rather than
> restricted, so this most likely does not apply — but "most likely" is not
> worth risking mid-assessment. Add it after the current LOV lands, or tell
> Eydle first.

**Honest read on timing:** items 1–4 are sequential and none are under our
control. That chain, not the Worker, is what determines when payments can ship.
Any estimate that starts from "how long to build the Worker" is measuring the
wrong thing.

## 12. What adding a backend does to the CASA assessment

Eydle confirmed 2026-08-14: *"Adding a backend will require a DAST scan. It may
count as a significant change."* Today's questionnaire answers lean heavily on
"we run no server," so a precise list of what changes is worth having up front —
it makes the next assessment predictable instead of a surprise, and it lets the
backend be **designed to pass** rather than remediated afterwards.

### Answers that must be rewritten
| ID | Today | After a backend |
|---|---|---|
| Auth 8 | "no publicly exposed interface exists" | An API is exposed. Must confirm no default credentials on it. |
| Access 3 | "We run no API." | Full endpoint list (§2) becomes the answer. |
| Access 4 | "no API for IDOR to target" | Real answer needed: entitlement is keyed on server-derived `sub_hash`, never a client-supplied id — that *is* the IDOR defence, and it should be stated that way. |
| Access 7 | "no administrative interface" | Still true only if we build no admin UI. If one is added it needs MFA. **Argues for no admin panel at all** — use Cloudflare/Stripe dashboards instead. |
| Config 2 | "no server-side logs exist" | Server logs now exist. **This control is specifically about not logging credentials or payment details** — so log no card data, no Stripe secrets, no ID tokens. Design for this on day one. |
| Config 4 | "no server-side secrets exist" | Becomes a real answer. See the inventory below. |
| F5 | "N/A. No payment system yet." | **Becomes required**: a log sample captured during a payment. |

### New requirements that switch on
- **F1 / DAST scan** — performed by the lab, but needs a test environment.
- **OpenAPI 3.0 specification** — "MUST include all backend routes... and
  Serverless/Edge Functions." Worth generating from the Worker's routes as they
  are written, not reconstructed months later.
- **Staging environment** — the instructions require a URL that "MUST map to a
  subdomain of an Authorized domain registered with Google," so
  `api.staging.phonoleaf.com`, not a `workers.dev` URL. Google rejects generic
  domains.
- **Temporary bypass endpoint** — `POST /v1/auth/bypass-token`, returning a
  session JWT for the lab's test account behind a pre-shared header secret.
  **Design this as a staging-only, environment-gated route from the start**,
  physically absent from the production Worker, rather than bolting on a real
  authentication bypass under deadline pressure. This is the single most
  security-sensitive thing the assessment asks for.
- **WAF / rate-limit relaxation** during the scan window.

### Secrets inventory (pre-answers Config 4)
All belong in Workers secrets (encrypted at rest, never in the repo, never in
`wrangler.toml`):
- `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`
- `SUB_HASH_PEPPER` — the server secret for hashing `sub` (§1)
- `ENTITLEMENT_JWT_PRIVATE_KEY` — the Worker's signing key (§6)
- `PLAY_SERVICE_ACCOUNT_JSON` — Play Developer API credentials
- `EYDLE_LAB_SECRET` — staging only, for the bypass route above
Rotation plan matters too: the assessment asks about storage *and* rotation.

## 13. Decisions still open

These need answers before coding, not during.

1. **Lifetime cap enforcement.** `BUSINESS.md` promises "limited to first ~500
   buyers." Where does the counter live, and what stops two simultaneous
   checkouts from both being #500? Cloudflare KV is eventually consistent and
   is the wrong primitive for a hard limit — this likely needs D1, Durable
   Objects, or simply reconciling against Stripe's own count. Also: is 500 a
   hard stop or a soft marketing number? A public promise that is not enforced
   is a consumer-protection problem, especially under Québec's CPA.
2. **Currency.** `BUSINESS.md` records USD as a *suggested default*, not
   owner-confirmed. Charging Québec customers in USD is legal but adds FX
   friction; CAD may convert better locally. Decide before Stripe products are
   created — changing currency later means recreating prices.
3. **KV vs D1 for entitlement.** KV is eventually consistent (writes can take
   seconds to propagate globally). The signed-JWT + offline-grace design in §6
   mostly absorbs that, but a user who pays and immediately reloads could
   briefly still see the paywall. Acceptable? Or use D1 for strong consistency?
4. **Refund mechanics.** The ToS promises a 14-day web money-back window.
   Manual (owner issues refunds in the Stripe dashboard) or automated? Manual
   is fine at low volume and needs no code — but it needs to actually happen
   within 14 days.
5. **Lifetime shutdown reserve.** The ToS commits to a 12-month refund window
   for lifetime buyers if the product is discontinued. That is a real
   liability against revenue that should not be spent as profit — decide the
   reserve policy before selling any.
6. **Trial abuse.** §3 accepts that new Google accounts can restart the trial.
   Confirm that is still acceptable, since it is a deliberate choice rather
   than an oversight.
