// Signs the short entitlement JWT the app caches and trusts offline
// (PAYMENTS_SPEC.md §6). ES256 (ECDSA P-256) via the Workers runtime's
// native Web Crypto — no jose/jsonwebtoken dependency.
import { bytesToBase64Url, jsonToBase64Url } from './jwt-common.js';

let cachedKey = null;
let cachedKeyEnv = null;

async function getSigningKey(env) {
  // Cheap correctness check against a stale isolate reusing a key
  // imported for a different secret value (shouldn't happen across a
  // secret rotation without a redeploy, but the check is nearly free).
  if (cachedKey && cachedKeyEnv === env.ENTITLEMENT_JWT_PRIVATE_KEY) return cachedKey;
  const jwk = JSON.parse(env.ENTITLEMENT_JWT_PRIVATE_KEY);
  cachedKey = await crypto.subtle.importKey(
    'jwk', jwk, { name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign']
  );
  cachedKeyEnv = env.ENTITLEMENT_JWT_PRIVATE_KEY;
  return cachedKey;
}

const SEVEN_DAY_GRACE = 60 * 60 * 24 * 7;   // normal offline grace (§6)
const LIFETIME_REFRESH = 60 * 60 * 24 * 365; // lifetime: long-lived, refreshed silently (§6)

/**
 * Signs an entitlement JWT: {status, plan, sub_hash, iat, exp}. Lifetime
 * entitlements get a long life since the D1 row is meant to be
 * permanent and must never be revoked by a backend change; everything
 * else gets the 7-day grace window so a plan change (or a lapsed
 * subscription) propagates within a week even if the app stays offline.
 */
export async function signEntitlementJwt(env, { status, plan, subHash }) {
  const now = Math.floor(Date.now() / 1000);
  const exp = now + (status === 'lifetime' ? LIFETIME_REFRESH : SEVEN_DAY_GRACE);

  const header = { alg: 'ES256', typ: 'JWT' };
  const payload = { status, plan, sub_hash: subHash, iat: now, exp };
  const signingInput = `${jsonToBase64Url(header)}.${jsonToBase64Url(payload)}`;

  const key = await getSigningKey(env);
  const sigBuf = await crypto.subtle.sign(
    { name: 'ECDSA', hash: 'SHA-256' },
    key,
    new TextEncoder().encode(signingInput)
  );
  // SubtleCrypto's ECDSA signature is the raw (r || s) byte concatenation,
  // which is exactly what JWS ES256 (RFC 7518 §3.4) expects — no DER
  // conversion needed, unlike most other ECDSA signing APIs.
  return `${signingInput}.${bytesToBase64Url(new Uint8Array(sigBuf))}`;
}
