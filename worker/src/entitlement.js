// Entitlement D1 logic: hashing `sub`, reading/writing the record, and
// starting the server-side trial. PAYMENTS_SPEC.md §1 (identity) and §3
// (trial). The table shape matches §2 / migrations/0001_create_entitlements.sql.

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
  // Parameterized (.bind()) — never string-concatenate SQL (§7). This is
  // the one rule that matters most now that a query language exists at
  // all; KV's plain get/put had no injection surface to defend.
  const row = await env.DB
    .prepare('SELECT status, source, plan, trial_end, period_end FROM entitlements WHERE sub_hash = ?')
    .bind(hash)
    .first();
  return row || { ...EMPTY_RECORD };
}

export async function putEntitlement(env, hash, record) {
  const stored = { ...record, updated_at: Math.floor(Date.now() / 1000) };
  await env.DB
    .prepare(`
      INSERT INTO entitlements (sub_hash, status, source, plan, trial_end, period_end, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(sub_hash) DO UPDATE SET
        status = excluded.status,
        source = excluded.source,
        plan = excluded.plan,
        trial_end = excluded.trial_end,
        period_end = excluded.period_end,
        updated_at = excluded.updated_at
    `)
    .bind(hash, stored.status, stored.source, stored.plan, stored.trial_end, stored.period_end, stored.updated_at)
    .run();
  return stored;
}

/**
 * Returns the entitlement record for this user, starting a trial on the
 * very first lookup. This is the ONLY place a trial gets created —
 * everything else (Play and Apple verification, once they exist)
 * only ever moves status forward from whatever this produced.
 *
 * RACE SAFETY (added 2026-09-01, audit finding C5). The obvious shape here
 * is read-then-write: SELECT, and if the row says `none`, write a trial.
 * That loses a race. `putEntitlement`'s ON CONFLICT clause overwrites
 * `status` unconditionally, so a store webhook writing `active` in the
 * window between the SELECT and the INSERT would be silently demoted back
 * to `trial` — a paying customer downgraded by a read path.
 *
 * The trial insert below therefore uses ON CONFLICT DO NOTHING, which makes
 * it idempotent: it can create the first row, and it can never modify a row
 * somebody else already wrote. We then re-read to return whatever actually
 * won. Do NOT "simplify" this back into putEntitlement — that function is
 * for callers that legitimately own the status they are setting.
 *
 * Note the unit tests for this cannot see an interleaving; they exercise the
 * function against a record. The guarantee lives in the SQL, not the tests.
 */
export async function getOrStartTrial(env, hash) {
  const existing = await getEntitlement(env, hash);
  if (existing.status !== 'none') return existing;

  const trialDays = Number(env.TRIAL_DAYS || '7');
  const now = Math.floor(Date.now() / 1000);

  await env.DB
    .prepare(`
      INSERT INTO entitlements (sub_hash, status, source, plan, trial_end, period_end, updated_at)
      VALUES (?, 'trial', NULL, NULL, ?, NULL, ?)
      ON CONFLICT(sub_hash) DO NOTHING
    `)
    .bind(hash, now + trialDays * 86400, now)
    .run();

  // Re-read rather than returning what we tried to write: if the insert lost
  // the race it changed nothing, and the winner's record is the true one.
  return getEntitlement(env, hash);
}

/**
 * The status to actually hand to the client right now, accounting for
 * expiry the stored record hasn't caught up to yet. The D1 row itself
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
