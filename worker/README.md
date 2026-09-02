# PhonoLeaf entitlement Worker

Implements `PAYMENTS_SPEC.md` (repo root) §§1–7: identity, the entitlement
store, the server-side trial, and signed entitlement delivery. The store
verification endpoints from §2 are routed but stubbed (`501
not_yet_available`) until the account each needs exists.

**Billing is store-only** (owner decision, 2026-09-02): subscriptions are sold
through Google Play and the App Store, never a web checkout. There is no Stripe
account and no plan for one — see `PAYMENTS_SPEC.md` §4 for the reasoning, which
is mostly about who is responsible for remitting consumption tax.

## What's real right now

- `GET /entitlement` — verifies a Google ID token (web or Android client),
  hashes `sub`, starts a 7-day server-side trial on first-ever lookup, and
  returns a signed ES256 JWT the app can cache and trust offline (§6).
- Google ID token verification against Google's live JWKS (§7): signature,
  audience, issuer, expiry.
- `sub_hash` derivation — SHA-256 of `sub` with a server-side pepper (§1) —
  so the entitlement store never holds a raw Google account id.

## What's stubbed, and why

`/verify-play` and `/webhooks/play` need a Play Developer API service account
(`PAYMENTS_SPEC.md` §11 #6, itself blocked on business registration).
`/verify-apple` needs an App Store Connect key and an iOS build to buy from.

Each stub 501s naming in WORDS what it is missing, not a spec section number:
the numbering drifted every time the spec was reorganised, and a stale pointer
is worse than none.

The checkout, billing-portal and Stripe webhook routes that used to sit here
were removed on 2026-09-02 with the store-only decision.

## Zero runtime dependencies, on purpose

No `jsonwebtoken`, no router library — everything here runs on the Workers
runtime's native Web Crypto and Cache APIs. This isn't a style preference
carried over by accident: `PAYMENTS_SPEC.md` §12 flags that adding a
backend brings CASA's dependency scan (F3) into scope for the next
assessment, and this repo already keeps its web app vendored/dependency-
light for the same class of reason (see `CLAUDE.md`'s Security hardening
section). Fewer runtime dependencies here means less for that scan to
find, and one less thing that can go stale unnoticed. `wrangler` is a
devDependency only — it never ships in the deployed Worker.

## Local setup

1. Install:
   ```
   cd worker
   npm install
   ```
2. Create the D1 database (once, needs a Cloudflare login —
   `npx wrangler login` first if you haven't):
   ```
   npx wrangler d1 create phonoleaf-entitlement
   ```
   Paste the id this prints into `wrangler.toml`'s `[[d1_databases]]`
   block, replacing `REPLACE_ME_RUN_WRANGLER_D1_CREATE`. Then apply the
   schema (`migrations/0001_create_entitlements.sql`) to both the remote
   database and a local one — local `wrangler dev` runs against its own
   emulated copy, so local testing never touches production data:
   ```
   npx wrangler d1 migrations apply phonoleaf-entitlement --remote
   npx wrangler d1 migrations apply phonoleaf-entitlement --local
   ```
3. Generate the entitlement-signing keypair (once):
   ```
   npm run generate-keys
   ```
   Set the private key as a secret:
   ```
   npx wrangler secret put ENTITLEMENT_JWT_PRIVATE_KEY
   ```
   (paste the private JWK JSON when prompted). Keep the public key output
   somewhere safe — it's not used yet, but gets embedded in the app in a
   later step.
4. Set the pepper secret (any long random string — e.g.
   `openssl rand -hex 32`):
   ```
   npx wrangler secret put SUB_HASH_PEPPER
   ```
5. For local dev, `wrangler dev` also reads a `.dev.vars` file (gitignored)
   for secrets instead of the real production secrets above. Create
   `worker/.dev.vars`:
   ```
   SUB_HASH_PEPPER=some-long-random-string-for-local-testing-only
   ENTITLEMENT_JWT_PRIVATE_KEY={"kty":"EC", ... the JWK from step 3 ...}
   ```
6. Run it:
   ```
   npm run dev
   ```
7. Test it:
   ```
   curl http://localhost:8787/
   curl -H "Authorization: Bearer <a real Google ID token>" http://localhost:8787/entitlement
   ```
   A real ID token is easiest to get once the app requests the `openid`
   scope — which it doesn't yet; see "Not wired up yet" below.

## Deploying

```
npx wrangler deploy
```

The Worker is live on Cloudflare (`*.workers.dev`), a side effect of the
first `wrangler secret put` — see `PAYMENTS_SPEC.md` §9 step 1. It is not
called from the app.

## Staging environment (`api.staging.phonoleaf.com`)

`PAYMENTS_SPEC.md` §11 #9 / §12: the next CASA assessment's DAST scan needs
a URL mapping to a subdomain of a Google-authorized domain — a
`*.workers.dev` URL doesn't qualify. **Set up 2026-08-21**, deliberately a
SEPARATE environment from production, not a route pointed at production
data — §12 also calls for a staging-only auth-bypass route for the lab's
test account later, which must be physically absent from the production
Worker, so keeping the environments separate from day one avoids that ever
being bolted onto production under deadline pressure.

- Own D1 database (`phonoleaf-entitlement-staging`, bound as `DB` inside
  the `staging` environment) and its own `SUB_HASH_PEPPER` /
  `ENTITLEMENT_JWT_PRIVATE_KEY` secrets — freshly generated, not shared
  with production, so a leak during a third-party security scan can't
  touch real entitlement data. Create + migrate it the same way as
  production (see "Local setup" above), substituting the staging database
  name — **and add `--env staging`**, since this database is defined
  under `[env.staging.d1_databases]`, not the top-level config:
  `npx wrangler d1 migrations apply phonoleaf-entitlement-staging --remote --env staging`.
  (Omitting `--env staging` fails with "Couldn't find a D1 DB with the
  name or binding ... in your wrangler.toml file" — hit this exact error
  2026-08-28, easy to repeat if this note isn't here.)
- `wrangler.toml`'s `[env.staging]` block routes
  `api.staging.phonoleaf.com` to this environment via a Cloudflare
  **Custom Domain** (`custom_domain = true`), which self-provisions the
  DNS record and SSL certificate — confirmed working with only this
  account's existing `zone:read` token (no `zone:edit` needed, contrary to
  the initial assumption that DNS write access would be required).
- Deploy: `npx wrangler deploy --env staging`. Secrets:
  `npx wrangler secret put NAME --env staging`.
- **Verified live 2026-08-21**: `https://api.staging.phonoleaf.com/`
  returns `{"ok":true,"service":"phonoleaf-entitlement"}`, and
  `/entitlement` correctly 401s without a token — matching production's
  behavior on the same routes. The SSL certificate took a few minutes to
  finish issuing after `wrangler deploy` first reported the custom domain
  provisioned; that lag is normal for a freshly created Cloudflare custom
  domain.

## Not wired up yet, deliberately

Nothing in the live app calls this Worker. Making that connection means two
things, and both are held back on purpose rather than being a side effect
of standing this up:

- **Requesting the `openid` scope** in `index.html`'s Google sign-in, so
  the app receives an ID token to send here. `PAYMENTS_SPEC.md` §11 flags
  not to do this while a CASA assessment is actively open — CASA is
  currently parked rather than active (see `VERIFICATION.md`), so this is
  believed safe, but worth a quick confirmation with Eydle before it ships
  since it touches the OAuth consent screen.
- **Gating the app on the entitlement response.** Doing this before any
  purchase path exists would show every current user a trial countdown and,
  eventually, a paywall with no way to pay — so this waits until Play Billing
  (§9 step 2) is real, not until this Worker merely deploys.
