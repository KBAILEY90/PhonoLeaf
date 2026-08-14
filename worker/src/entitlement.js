// Entitlement KV logic: hashing `sub`, reading/writing the record, and
// starting the server-side trial. PAYMENTS_SPEC.md §1 (identity) and §3
// (trial). The KV record shape matches §2 exactly.

async function sha256Hex(input) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(input));
  return [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, '0')).join('');
}

/** Never store or log the raw Google `sub` — only this hash (§1, §7). */
export function subHash(sub, pepper) {
  return sha256Hex(`${sub}:${pepper}`);
}

const EMPTY_RECORD = Object.freeze({
  status: 'none',
  source: null,
  plan: null,
  trial_end: null,
  period_end: null,
});

export async function getEntitlement(env, hash) {
  const raw = await env.ENTITLEMENTS.get(hash, 'json');
  return raw || { ...EMPTY_RECORD };
}

export async function putEntitlement(env, hash, record) {
  const stored = { ...record, updated_at: Math.floor(Date.now() / 1000) };
  await env.ENTITLEMENTS.put(hash, JSON.stringify(stored));
  return stored;
}

/**
 * Returns the entitlement record for this user, starting a trial on the
 * very first lookup. This is the ONLY place a trial gets created —
 * everything else (Stripe webhooks, Play verification, once they exist)
 * only ever moves status forward from whatever this produced.
 */
export async function getOrStartTrial(env, hash) {
  const existing = await getEntitlement(env, hash);
  if (existing.status !== 'none') return existing;

  const trialDays = Number(env.TRIAL_DAYS || '7');
  const now = Math.floor(Date.now() / 1000);
  return putEntitlement(env, hash, {
    status: 'trial',
    source: null,
    plan: null,
    trial_end: now + trialDays * 86400,
    period_end: null,
  });
}

/**
 * The status to actually hand to the client right now, accounting for
 * expiry the stored record hasn't caught up to yet. The KV record itself
 * is left alone here — flipping it to "none" for real is a webhook's job
 * (§4), not a read path's. This is only a safety net so a delayed webhook
 * (or an expired trial nobody's written back yet) can't hand out access
 * past its actual end date.
 */
export function effectiveStatus(record, now = Math.floor(Date.now() / 1000)) {
  if (record.status === 'trial' && record.trial_end && now > record.trial_end) return 'none';
  if (record.status === 'active' && record.period_end && now > record.period_end) return 'none';
  return record.status;
}
