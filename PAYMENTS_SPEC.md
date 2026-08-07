# PhonoLeaf — Payments & entitlement spec (roadmap item 5)

How PhonoLeaf turns "signed in with Google" into "is this user allowed to use the
paid features," across web (Stripe) and the app stores (Play Billing now, StoreKit
later), with a 7-day trial and a one-time lifetime — without building a password
system and without compromising the privacy story.

> Design rules carried from `CLAUDE.md`: no user accounts/passwords (identity =
> Google account); no per-user server for reading data; keep any payments
> infrastructure **strictly segregated from Google Drive user data**; entitlement
> must survive backend changes (especially lifetime). Pricing is in `BUSINESS.md`
> ($5.99/mo · $49.99/yr · $129 lifetime · 7-day trial).

---

## 1. Identity

- Add the `openid` scope to the existing Google sign-in so the app receives an
  **ID token** containing a stable, unique `sub` (the Google account id). `sub` —
  not email (emails change) — is the entitlement key.
- The client never asserts its own entitlement. Every call to the entitlement
  backend carries the **Google ID token**; the backend verifies it
  (Google JWKS, `aud` = our CLIENT_ID, `iss`, `exp`) and derives `sub` server-side.
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
onto the same `sub` — so a user who buys on web is entitled on Android, and vice
versa. (Fold the deferred bug-report photo-upload endpoint into this same Worker,
per `CLAUDE.md` roadmap item 5 — one backend, not two.)

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

Recommended: a **server-side trial**, not a Stripe card-required trial — better
conversion and it fits the Worker-as-source-of-truth model.
- On first authenticated `GET /entitlement` for a new `sub`, set
  `status:"trial"`, `trial_end = now + 7d`. Return trial.
- App gates paid features on `status in {trial, active, lifetime}`.
- When the trial expires, `/entitlement` returns `status:"none"`; the app shows the
  paywall.
- Abuse (new Google accounts to re-trial) is accepted for an indie launch —
  creating Google accounts is real friction. Revisit device signals only if abused.

## 4. Web payments (Stripe)

- **Products/prices:** subscription with two prices ($5.99/mo, $49.99/yr) and a
  **one-time** price for lifetime ($129, Checkout `mode: "payment"`).
- **Flow:** app (user already Google-signed-in) → `POST /checkout {plan}` → Worker
  creates Checkout session with `client_reference_id = sub_hash` and metadata →
  redirect to Stripe → on return, app re-fetches `/entitlement`.
- **Truth comes from webhooks**, not the redirect: handle
  `checkout.session.completed`, `customer.subscription.updated/deleted`,
  `invoice.paid`, `invoice.payment_failed`. Each maps the Stripe
  customer/subscription to `sub_hash` and writes the KV record (`active` +
  `period_end`, or `none`). Verify the Stripe signature; make handlers idempotent.
- **Cancellation / manage:** `POST /portal` → Stripe Billing Portal.
- **Tax:** enable **Stripe Tax** so GST + QST (Québec) and other jurisdictions are
  computed and collected. (Registration/remittance is the owner's — see
  `BUSINESS.md` "Québec compliance".)

## 5. Android payments (Google Play Billing)

- **Products:** a subscription with `monthly` and `annual` base plans + a
  **one-time non-consumable** product for lifetime (NOT a subscription).
- **Flow:** native app launches Play Billing purchase → on success, send the
  `purchaseToken` + `productId` to `POST /verify-play` → Worker verifies with the
  Google Play Developer API and writes the KV record keyed to the same `sub_hash`.
  This unifies Play purchases with web entitlement.
- **MVP shortcut (optional):** the app can also trust Play's on-device
  `queryPurchases()` for Android-local entitlement so it works even if the Worker
  is briefly unreachable — but the Worker record is still the cross-platform truth.
- **Renewals/refunds:** add Play **Real-Time Developer Notifications** (RTDN via
  Pub/Sub) → `POST /webhooks/play` later; until then, re-verify on app launch.
- Play takes **15%** at this scale — so in-app, still offer annual and (where
  policy permits) point web-savvy users to web checkout for the better margin.

## 6. Entitlement delivery + offline grace (durability)

- `GET /entitlement` returns a **short JWT signed by the Worker** (Worker holds the
  private key; the app ships the public key) containing `{status, plan, exp}`.
- The app **caches** the signed entitlement and trusts it offline until its `exp`
  (e.g. 7-day grace for subscriptions). This means a temporary Worker outage never
  locks paying users out.
- **Lifetime is extra-durable:** issue lifetime entitlement JWTs with a long life
  (e.g. 1 year) and auto-refresh silently; the KV record is permanent. A backend
  change must never revoke a paid lifetime (see `BUSINESS.md`). Keep a durable,
  exportable record of lifetime `sub_hash`es.

## 7. Security & privacy

- Verify Google ID tokens on every authed call (signature via Google JWKS, `aud`,
  `iss`, `exp`). Reject otherwise.
- Verify Stripe webhook signatures and Play/Apple receipts **server-side** — never
  trust the client for entitlement.
- Store the **minimum**: `sub_hash`, entitlement, Stripe customer/subscription id,
  Play/Apple order ids. **No Google Drive data, no OAuth Drive tokens** in the
  payments store — keep it a separate system from the reading app's data.
- **Privacy policy update:** once Stripe is live, add a line to `privacy.html` /
  `privacy-fr.html`: payment details are processed by the payment provider
  (Stripe / the app store) and are not stored by PhonoLeaf; a hashed Google account
  identifier is stored solely to remember your subscription status. (Today's policy
  says "no backend" — that becomes "no backend for your books/reading; a minimal
  entitlement service records only subscription status.")

## 8. In-app paywall (both stores require this)

The paywall must clearly show, before purchase: the **price**, the **billing
period**, that it **auto-renews**, how to **cancel**, and link to Terms + Privacy.
(The ToS "Pricing & Payments" section already covers the legal side — see
`BUSINESS.md` §3 / `terms.html`.) Trial screens must state that the trial converts
to a paid subscription and when.

## 9. Build order (suggested)
1. Add `openid` scope; get `sub`; stand up the Worker + KV + `GET /entitlement`
   with the server-side trial. Gate the app on entitlement. (No money yet —
   validates the whole gate.)
2. Stripe web: products, `/checkout`, `/portal`, webhooks, Stripe Tax. Web paid.
3. Play Billing: products, purchase flow, `/verify-play`. Android paid + unified.
4. Offline-grace signed entitlement + lifetime durability hardening.
5. Later: Apple StoreKit2 + `/verify-apple`; Play RTDN + `/webhooks/play`.

## 10. Testing
- Stripe **test mode** + test clocks for renewals/trials; verify webhook idempotency.
- Play **license testing** / closed track sandbox for purchase + verification.
- Entitlement gate: trial start/expiry, active↔none transitions, lifetime never
  expires, offline-grace within/after `exp`, cross-platform merge (buy on web →
  entitled on Android with the same Google account).
- Refund/chargeback/cancel → entitlement drops to `none` on the next webhook/verify.
