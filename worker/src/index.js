// PhonoLeaf entitlement Worker. Implements PAYMENTS_SPEC.md §2's endpoint
// table. Only /entitlement is real right now — the store verification routes
// are declared and documented but 501, because each needs an account that does
// not exist yet (Play Developer API service account, App Store Connect key).
//
// STORE BILLING ONLY (owner decision, 2026-09-02). Subscriptions are sold
// exclusively through Google Play and the App Store; there is no web checkout
// and no Stripe. See PAYMENTS_SPEC.md §4 for why, including the tax and policy
// reasoning. The entitlement record deliberately stays source-agnostic, so a
// web source could be added later without changing anything here except adding
// a route. See worker/README.md.
import { verifyGoogleIdToken } from './google-auth.js';
import { subHash, getOrStartTrial, effectiveStatus } from './entitlement.js';
import { signEntitlementJwt } from './entitlement-jwt.js';

/**
 * Origins allowed to call this Worker from a browser context.
 *
 * `https://localhost` is NOT a mistake and must not be removed: Capacitor
 * serves the Android app from that origin (androidScheme defaults to https),
 * so it is the native app's Origin header. Dropping it breaks sign-in on the
 * shipping app while leaving the website working, which is the kind of split
 * failure nobody notices until a release.
 *
 * `kbailey90.github.io` stays for the same reason it stays an authorized
 * OAuth origin: old installs still point at it (see CLAUDE.md).
 */
const ALLOWED_ORIGINS = new Set([
  'https://phonoleaf.com',
  'https://www.phonoleaf.com',
  'https://kbailey90.github.io',
  'https://localhost',
  'http://localhost',
]);

/**
 * Was a wildcard until 2026-09-01 (audit finding M2). A wildcard on an
 * endpoint that both mints entitlement tokens and creates trial records lets
 * any page on the web drive it with a token it happens to hold.
 *
 * Echoes the caller's origin when it is allowed, rather than returning the
 * whole list, and sends `vary: origin` so a cache can never serve one
 * origin's CORS decision to another. A request with no Origin header at all
 * (a native HTTP client, curl, a server-to-server call) is not a browser and
 * is left alone: CORS is not what protects this endpoint, the bearer token is.
 */
function corsHeaders(request) {
  const origin = request && request.headers.get('origin');
  const headers = {
    'access-control-allow-headers': 'authorization, content-type',
    'access-control-allow-methods': 'GET, POST, OPTIONS',
    'vary': 'origin',
  };
  if (origin && ALLOWED_ORIGINS.has(origin)) {
    headers['access-control-allow-origin'] = origin;
  }
  return headers;
}

// `request` is threaded through rather than read from a module-scoped
// variable on purpose: a Worker isolate serves concurrent requests, so any
// per-request state held at module scope is a cross-request bug waiting to
// happen. It is optional so a caller with nothing to echo still gets valid
// (origin-less) CORS headers.
function json(body, status = 200, request = null) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', ...corsHeaders(request) },
  });
}

// Takes the missing prerequisite in words rather than a section number: the
// numbers drifted every time PAYMENTS_SPEC.md was reorganised, and a stale
// pointer is worse than none.
function notYetAvailable(needs, request) {
  return json({
    error: 'not_yet_available',
    detail: `Not available yet: this needs ${needs}. See PAYMENTS_SPEC.md.`,
  }, 501, request);
}

/**
 * Per-IP rate limit, applied BEFORE token verification (audit finding M3).
 *
 * The order matters and is the whole point. Verifying a Google ID token costs
 * a JWKS-backed signature check, and a first-time caller then costs a D1
 * write. Rate limiting after auth would still pay for both on every junk
 * request, so this runs first and rejects cheaply.
 *
 * Keyed on the client IP rather than the token, because the token is exactly
 * the thing we have not validated yet at this point. That does mean users
 * behind one NAT share a bucket, which is why the limit is set generously:
 * the app calls this on launch and on refresh, not in a loop, so a real user
 * comes nowhere near it while an abusive caller is bounded hard.
 *
 * Fails OPEN if the binding is missing (local `wrangler dev` without it, or a
 * deploy that predates it). A missing rate limiter must not take the endpoint
 * down; it is a cost control, not an authorization check, and the bearer token
 * is what actually protects this.
 */
async function withinRateLimit(request, env) {
  if (!env.RATE_LIMITER) return true;
  const ip = request.headers.get('cf-connecting-ip') || 'unknown';
  try {
    const { success } = await env.RATE_LIMITER.limit({ key: ip });
    return success;
  } catch (_) {
    return true;
  }
}

async function requireGoogleAuth(request, env) {
  const authz = request.headers.get('authorization') || '';
  const [scheme, token] = authz.split(' ');
  if (scheme !== 'Bearer' || !token) throw new Error('missing bearer token');

  const allowed = [env.GOOGLE_CLIENT_ID, env.GOOGLE_ANDROID_CLIENT_ID].filter(Boolean);
  return verifyGoogleIdToken(token, allowed);
}

async function handleEntitlement(request, env) {
  if (!(await withinRateLimit(request, env))) {
    return json({ error: 'rate_limited', detail: 'Too many requests. Try again shortly.' }, 429, request);
  }

  let googlePayload;
  try {
    googlePayload = await requireGoogleAuth(request, env);
  } catch (e) {
    return json({ error: 'unauthorized', detail: e.message }, 401, request);
  }

  const hash = await subHash(googlePayload.sub, env.SUB_HASH_PEPPER);
  const record = await getOrStartTrial(env, hash);
  const status = effectiveStatus(record);

  const jwt = await signEntitlementJwt(env, { status, plan: record.plan, subHash: hash });
  return json({
    jwt,
    status,
    plan: record.plan,
    trial_end: record.trial_end,
    period_end: record.period_end,
  }, 200, request);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders(request) });
    }

    if (url.pathname === '/' && request.method === 'GET') {
      return json({ ok: true, service: 'phonoleaf-entitlement' }, 200, request);
    }

    if (url.pathname === '/entitlement' && request.method === 'GET') {
      return handleEntitlement(request, env);
    }

    if (url.pathname === '/verify-play' && request.method === 'POST') {
      return notYetAvailable('a Play Developer API service account', request);
    }
    if (url.pathname === '/webhooks/play' && request.method === 'POST') {
      return notYetAvailable('a Play Developer API service account', request);
    }
    if (url.pathname === '/verify-apple' && request.method === 'POST') {
      return notYetAvailable('an App Store Connect key, and an iOS build to buy from', request);
    }

    return json({ error: 'not_found' }, 404, request);
  },
};
