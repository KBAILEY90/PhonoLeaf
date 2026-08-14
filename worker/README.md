# PhonoLeaf entitlement Worker

Implements `PAYMENTS_SPEC.md` (repo root) §§1–7: identity, the entitlement
store, the server-side trial, and signed entitlement delivery. The Stripe
and Play Billing endpoints from §2 are routed but stubbed (`501
not_yet_available`) until their prerequisite in §11 exists.

## What's real right now

- `GET /entitlement` — verifies a Google ID token (web or Android client),
  hashes `sub`, starts a 7-day server-side trial on first-ever lookup, and
  returns a signed ES256 JWT the app can cache and trust offline (§6).
- Google ID token verification against Google's live JWKS (§7): signature,
  audience, issuer, expiry.
- `sub_hash` derivation — SHA-256 of `sub` with a server-side pepper (§1) —
  so the entitlement store never holds a raw Google account id.

## What's stubbed, and why

`/checkout`, `/portal`, `/webhooks/stripe` need a Stripe account
(`PAYMENTS_SPEC.md` §11 #4 — blocked on business registration first).
`/verify-play`, `/webhooks/play` need a Play Developer API service account
(§11 #6). `/verify-apple` is later per §9 step 5. Each stub 501s with the
exact prerequisite number blocking it, so the endpoint table in §2 has
somewhere real to point today instead of being purely aspirational.

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
2. Create the KV namespace (once, needs a Cloudflare login —
   `npx wrangler login` first if you haven't):
   ```
   npx wrangler kv namespace create ENTITLEMENTS
   npx wrangler kv namespace create ENTITLEMENTS --preview
   ```
   Paste the two ids this prints into `wrangler.toml` (`id` and
   `preview_id`). The preview namespace is what local `wrangler dev` uses,
   so local testing never touches production data.
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

Not deployed anywhere yet — this only exists locally until you choose to
run this.

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
- **Gating the app on the entitlement response.** Doing this before Stripe
  Checkout exists would show every current user a trial countdown and,
  eventually, a paywall with no way to pay — so this waits until at least
  §9 step 2 (Stripe web) is real, not until this Worker merely deploys.
