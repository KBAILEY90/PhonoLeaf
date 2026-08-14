// PhonoLeaf entitlement Worker. Implements PAYMENTS_SPEC.md §2's endpoint
// table. Only /entitlement is real right now (§9 build order, step 1) —
// everything else is routed and documented but 501s, because it needs a
// prerequisite from §11 that doesn't exist yet (Stripe account, Play
// Developer API service account). See worker/README.md.
import { verifyGoogleIdToken } from './google-auth.js';
import { subHash, getOrStartTrial, effectiveStatus } from './entitlement.js';
import { signEntitlementJwt } from './entitlement-jwt.js';

function corsHeaders() {
  // Nothing calls this Worker yet (index.html isn't wired to it — see the
  // README). Tighten this to the real app origin(s) as part of that step,
  // rather than shipping a wildcard once real traffic exists.
  return {
    'access-control-allow-origin': '*',
    'access-control-allow-headers': 'authorization, content-type',
    'access-control-allow-methods': 'GET, POST, OPTIONS',
  };
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', ...corsHeaders() },
  });
}

function notYetAvailable(prereqNumber, what) {
  return json({
    error: 'not_yet_available',
    detail: `${what} is blocked on prerequisite #${prereqNumber} in PAYMENTS_SPEC.md §11.`,
  }, 501);
}

async function requireGoogleAuth(request, env) {
  const authz = request.headers.get('authorization') || '';
  const [scheme, token] = authz.split(' ');
  if (scheme !== 'Bearer' || !token) throw new Error('missing bearer token');

  const allowed = [env.GOOGLE_CLIENT_ID, env.GOOGLE_ANDROID_CLIENT_ID].filter(Boolean);
  return verifyGoogleIdToken(token, allowed);
}

async function handleEntitlement(request, env) {
  let googlePayload;
  try {
    googlePayload = await requireGoogleAuth(request, env);
  } catch (e) {
    return json({ error: 'unauthorized', detail: e.message }, 401);
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
  });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }

    if (url.pathname === '/' && request.method === 'GET') {
      return json({ ok: true, service: 'phonoleaf-entitlement' });
    }

    if (url.pathname === '/entitlement' && request.method === 'GET') {
      return handleEntitlement(request, env);
    }

    if (url.pathname === '/checkout' && request.method === 'POST') {
      return notYetAvailable(4, 'Stripe Checkout');
    }
    if (url.pathname === '/portal' && request.method === 'POST') {
      return notYetAvailable(4, 'Stripe Billing Portal');
    }
    if (url.pathname === '/webhooks/stripe' && request.method === 'POST') {
      return notYetAvailable(4, 'Stripe webhooks');
    }
    if (url.pathname === '/verify-play' && request.method === 'POST') {
      return notYetAvailable(6, 'Play purchase verification');
    }
    if (url.pathname === '/webhooks/play' && request.method === 'POST') {
      return notYetAvailable(6, 'Play RTDN webhooks');
    }
    if (url.pathname === '/verify-apple' && request.method === 'POST') {
      return notYetAvailable(4, 'Apple StoreKit2 verification (later, per §9 step 5)');
    }

    return json({ error: 'not_found' }, 404);
  },
};
