// Verifies Google ID tokens: JWKS signature check + aud/iss/exp/sub,
// per PAYMENTS_SPEC.md §1 and §7 ("verify Google ID tokens on every
// authed call... reject otherwise").
import { splitJwt } from './jwt-common.js';

const JWKS_URL = 'https://www.googleapis.com/oauth2/v3/certs';

async function fetchGoogleJwks() {
  // Let Cloudflare's edge cache hold this — Google rotates these keys
  // infrequently, and re-fetching per request would add real latency to
  // every authed call for no benefit.
  const res = await fetch(JWKS_URL, { cf: { cacheTtl: 3600, cacheEverything: true } });
  if (!res.ok) throw new Error(`Google JWKS fetch failed: ${res.status}`);
  const { keys } = await res.json();
  return keys;
}

function importGoogleKey(jwk) {
  return crypto.subtle.importKey(
    'jwk',
    jwk,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['verify']
  );
}

/**
 * Verifies a Google ID token and returns its payload (including `sub`).
 * Throws on any failure — bad signature, wrong audience/issuer, expired.
 * `allowedAudiences` should include both the web and Android OAuth client
 * ids, since either sign-in flow can be the one calling this Worker.
 */
export async function verifyGoogleIdToken(idToken, allowedAudiences) {
  const { header, payload, signingInput, signature } = splitJwt(idToken);
  if (header.alg !== 'RS256') throw new Error(`unexpected alg: ${header.alg}`);

  const keys = await fetchGoogleJwks();
  const jwk = keys.find(k => k.kid === header.kid);
  if (!jwk) throw new Error('no matching Google JWKS key for this token (kid)');

  const key = await importGoogleKey(jwk);
  const ok = await crypto.subtle.verify(
    'RSASSA-PKCS1-v1_5',
    key,
    signature,
    new TextEncoder().encode(signingInput)
  );
  if (!ok) throw new Error('Google ID token signature invalid');

  // Small skew allowance. Without it a device whose clock runs a few seconds
  // fast is rejected on a token Google considers valid, which presents to the
  // user as an unexplained sign-in failure that fixes itself later. 60s is the
  // conventional allowance and is far shorter than the ~1h token lifetime, so
  // it does not meaningfully extend the window an expired token is accepted in.
  const SKEW = 60;
  const now = Math.floor(Date.now() / 1000);
  if (typeof payload.exp !== 'number' || payload.exp < now - SKEW) {
    throw new Error('Google ID token expired');
  }
  // Reject a token that claims to have been issued in the future by more than
  // the same allowance — that is a clock problem or a forgery attempt, and
  // either way is not something to hand an entitlement to.
  if (typeof payload.iat === 'number' && payload.iat > now + SKEW) {
    throw new Error('Google ID token issued in the future');
  }
  if (payload.iss !== 'https://accounts.google.com' && payload.iss !== 'accounts.google.com') {
    throw new Error(`unexpected issuer: ${payload.iss}`);
  }
  if (!allowedAudiences.includes(payload.aud)) {
    throw new Error(`unexpected audience: ${payload.aud}`);
  }
  if (!payload.sub) throw new Error('token has no sub');

  return payload;
}
