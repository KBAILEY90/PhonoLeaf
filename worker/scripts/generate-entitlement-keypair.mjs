#!/usr/bin/env node
// Generates the ES256 (P-256) keypair used to sign/verify entitlement
// JWTs (PAYMENTS_SPEC.md §6). Uses the same Web Crypto API the Worker
// itself runs on (Node 19+ exposes it as the `crypto` global), so the
// JWK this prints needs no format conversion to work in src/entitlement-jwt.js.
//
// Run once per key generation:
//   node scripts/generate-entitlement-keypair.mjs
//
// Then:
//   1. Set the PRIVATE key as a Worker secret (paste the JSON when prompted):
//        npx wrangler secret put ENTITLEMENT_JWT_PRIVATE_KEY
//   2. Keep the PUBLIC key somewhere safe. It gets embedded as a constant
//      in the app in a later step (full offline JWT verification on the
//      client), not committed here.
//
// Never commit either key to git. This script only prints to stdout.

const { publicKey, privateKey } = await crypto.subtle.generateKey(
  { name: 'ECDSA', namedCurve: 'P-256' },
  true,
  ['sign', 'verify']
);

const publicJwk = await crypto.subtle.exportKey('jwk', publicKey);
const privateJwk = await crypto.subtle.exportKey('jwk', privateKey);

// Stamp a key id into BOTH halves. The Worker copies it into every JWT header
// (`kid`), which is what makes rotation possible: the client can hold two
// public keys at once and pick by id, so a compromised key can be retired
// without invalidating tokens already in circulation. A key with no kid is
// treated as 'k1' by the Worker, so never hand-edit this out.
// Date-prefixed rather than random so the ordering is obvious at a glance
// when two keys are live during a rotation.
const kid = `k${new Date().toISOString().slice(0, 10).replace(/-/g, '')}`;
publicJwk.kid = kid;
privateJwk.kid = kid;

console.log('=== PUBLIC key (save for later; embedded in the app eventually) ===');
console.log(JSON.stringify(publicJwk));
console.log();
console.log('=== PRIVATE key (Worker secret) ===');
console.log('Run: npx wrangler secret put ENTITLEMENT_JWT_PRIVATE_KEY');
console.log('and paste this when prompted:');
console.log(JSON.stringify(privateJwk));
