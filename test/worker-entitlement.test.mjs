// Real unit tests for the entitlement Worker's trial state machine.
//
// Unlike the app, this is a clean ES module with no DOM, so it can be imported
// and tested properly. That matters more here than anywhere else in the repo:
// this code decides who has paid. Its failure modes are handing out free access
// or, much worse, downgrading someone who paid.
//
// PAYMENTS_SPEC.md §1 (identity), §3 (trial), §6 (durability).

import { test, describe, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

import {
  subHash,
  getEntitlement,
  putEntitlement,
  getOrStartTrial,
  effectiveStatus,
} from '../worker/src/entitlement.js';

const DAY = 86400;
const now = () => Math.floor(Date.now() / 1000);

/**
 * Minimal in-memory stand-in for the D1 binding (`env.DB`).
 *
 * The worker moved from KV to D1 on 2026-08-28, so this models
 * `.prepare(sql).bind(...).first() / .run()` rather than KV get/put. It
 * dispatches on the leading SQL verb, which is enough for the two statements
 * entitlement.js actually issues and keeps the stub honest about the real
 * shape (a SELECT returns a row object or null; the upsert replaces by key).
 *
 * Deliberately NOT a SQL engine. If a third statement shape appears, this
 * throws rather than silently returning undefined, so the test fails loudly
 * instead of passing against a stub that quietly does nothing.
 */
function makeEnv(overrides = {}) {
  const rows = new Map();
  // Lets a test simulate a lost race: the first N SELECTs report "no row"
  // even though one exists, which is exactly what a stale read looks like
  // when a webhook commits between our SELECT and our INSERT.
  let staleReads = overrides.staleReads || 0;
  delete overrides.staleReads;

  return {
    _rows: rows,
    DB: {
      prepare(sql) {
        const verb = sql.trim().split(/\s+/)[0].toUpperCase();
        // Two INSERT shapes exist on purpose and must not be conflated:
        // the 7-arg upsert (putEntitlement, owns the status it writes) and
        // the 3-arg trial insert (getOrStartTrial, must never modify an
        // existing row). See entitlement.js's RACE SAFETY note.
        const doNothing = /DO NOTHING/i.test(sql);
        return {
          bind(...args) {
            return {
              async first() {
                if (verb !== 'SELECT') throw new Error(`stub: unexpected first() on ${verb}`);
                if (staleReads > 0) { staleReads--; return null; }
                return rows.get(args[0]) ?? null;
              },
              async run() {
                if (verb !== 'INSERT') throw new Error(`stub: unexpected run() on ${verb}`);
                if (doNothing) {
                  const [sub_hash, trial_end, updated_at] = args;
                  // ON CONFLICT DO NOTHING: create only, never overwrite.
                  if (!rows.has(sub_hash)) {
                    rows.set(sub_hash, {
                      status: 'trial', source: null, plan: null,
                      trial_end, period_end: null, updated_at,
                    });
                  }
                  return { success: true };
                }
                const [sub_hash, status, source, plan, trial_end, period_end, updated_at] = args;
                // Mirrors ON CONFLICT(sub_hash) DO UPDATE: replace by key.
                rows.set(sub_hash, { status, source, plan, trial_end, period_end, updated_at });
                return { success: true };
              },
            };
          },
        };
      },
    },
    ...overrides,
  };
}

describe('subHash (PAYMENTS_SPEC §1)', () => {
  test('produces a stable 64-char hex digest', async () => {
    const h = await subHash('1234567890', 'pepper');
    assert.match(h, /^[0-9a-f]{64}$/);
    assert.equal(h, await subHash('1234567890', 'pepper'), 'must be deterministic');
  });

  test('never contains the raw Google sub', async () => {
    // The whole point: the entitlement store must not be trivially linkable
    // back to a Google identity.
    const sub = '109876543210987654321';
    const h = await subHash(sub, 'pepper');
    assert.ok(!h.includes(sub));
  });

  test('a different pepper produces a different hash', async () => {
    assert.notEqual(await subHash('same-sub', 'pepper-a'), await subHash('same-sub', 'pepper-b'));
  });

  test('different subs do not collide under the same pepper', async () => {
    assert.notEqual(await subHash('user-a', 'p'), await subHash('user-b', 'p'));
  });
});

describe('getOrStartTrial (PAYMENTS_SPEC §3)', () => {
  let env;
  beforeEach(() => { env = makeEnv(); });

  test('unknown user reads as status "none" before any lookup', async () => {
    const rec = await getEntitlement(env, 'unknown');
    assert.equal(rec.status, 'none');
    assert.equal(rec.plan, null);
    assert.equal(rec.trial_end, null);
  });

  test('starts a 7-day trial on the first ever lookup', async () => {
    const rec = await getOrStartTrial(env, 'hash-1');
    assert.equal(rec.status, 'trial');
    // Allow a couple of seconds of slack for clock movement during the test.
    assert.ok(Math.abs(rec.trial_end - (now() + 7 * DAY)) <= 2,
      `trial_end should be ~7 days out, got ${rec.trial_end - now()}s`);
  });

  test('honours TRIAL_DAYS from env', async () => {
    const e = makeEnv({ TRIAL_DAYS: '14' });
    const rec = await getOrStartTrial(e, 'hash-1');
    assert.ok(Math.abs(rec.trial_end - (now() + 14 * DAY)) <= 2);
  });

  test('CRITICAL: a second lookup does not restart the trial', async () => {
    // If this regresses, the trial is effectively infinite and nobody ever
    // reaches the paywall.
    const first = await getOrStartTrial(env, 'hash-1');
    await new Promise(r => setTimeout(r, 10));
    const second = await getOrStartTrial(env, 'hash-1');
    assert.equal(second.trial_end, first.trial_end, 'trial_end must not move');
  });

  test('CRITICAL: never downgrades a paying subscriber to trial', async () => {
    await putEntitlement(env, 'payer', {
      status: 'active', source: 'stripe', plan: 'annual',
      trial_end: null, period_end: now() + 300 * DAY,
    });
    const rec = await getOrStartTrial(env, 'payer');
    assert.equal(rec.status, 'active');
    assert.equal(rec.plan, 'annual');
  });

  test('CRITICAL: a lost race cannot downgrade a payer who committed mid-call', async () => {
    // Audit finding C5. The two tests above prove the LOGIC is right given a
    // record. This one covers the interleaving they cannot see: our SELECT
    // returns "no row", a Stripe webhook commits `active`, and only then does
    // our INSERT land. With ON CONFLICT DO UPDATE that INSERT overwrote the
    // payment. With DO NOTHING it must be a no-op, and the re-read must
    // return the webhook's record rather than the trial we tried to write.
    const raced = makeEnv({ staleReads: 1 });
    raced._rows.set('racer', {
      status: 'active', source: 'stripe', plan: 'annual',
      trial_end: null, period_end: now() + 300 * DAY, updated_at: now(),
    });

    const rec = await getOrStartTrial(raced, 'racer');
    assert.equal(rec.status, 'active', 'a trial insert overwrote a paid record');
    assert.equal(rec.plan, 'annual');
    assert.equal(raced._rows.get('racer').status, 'active');
  });

  test('CRITICAL: never downgrades a lifetime purchaser', async () => {
    // The ToS sells this as permanent. A backend change must not revoke it.
    await putEntitlement(env, 'lifer', {
      status: 'lifetime', source: 'stripe', plan: 'lifetime',
      trial_end: null, period_end: null,
    });
    const rec = await getOrStartTrial(env, 'lifer');
    assert.equal(rec.status, 'lifetime');
  });

  test('an expired trial is not silently restarted', async () => {
    await putEntitlement(env, 'expired', {
      status: 'trial', source: null, plan: null,
      trial_end: now() - DAY, period_end: null,
    });
    const rec = await getOrStartTrial(env, 'expired');
    assert.ok(rec.trial_end < now(), 'the expired trial_end must be preserved');
  });

  test('stamps updated_at on write', async () => {
    const rec = await getOrStartTrial(env, 'hash-1');
    assert.ok(Math.abs(rec.updated_at - now()) <= 2);
  });
});

describe('effectiveStatus (PAYMENTS_SPEC §6)', () => {
  test('an in-date trial reads as trial', () => {
    assert.equal(effectiveStatus({ status: 'trial', trial_end: now() + DAY }), 'trial');
  });

  test('an expired trial reads as none even before a webhook catches up', () => {
    assert.equal(effectiveStatus({ status: 'trial', trial_end: now() - 1 }), 'none');
  });

  test('an in-date subscription reads as active', () => {
    assert.equal(effectiveStatus({ status: 'active', period_end: now() + DAY }), 'active');
  });

  test('a lapsed subscription reads as none', () => {
    assert.equal(effectiveStatus({ status: 'active', period_end: now() - 1 }), 'none');
  });

  test('CRITICAL: lifetime never expires', () => {
    // No period_end, no trial_end, and it must survive any clock value.
    assert.equal(effectiveStatus({ status: 'lifetime', trial_end: null, period_end: null }), 'lifetime');
    assert.equal(
      effectiveStatus({ status: 'lifetime', trial_end: null, period_end: null }, now() + 100 * 365 * DAY),
      'lifetime',
      'lifetime must still be lifetime a century from now'
    );
  });

  test('a record with no expiry field is passed through unchanged', () => {
    assert.equal(effectiveStatus({ status: 'active', period_end: null }), 'active');
    assert.equal(effectiveStatus({ status: 'trial', trial_end: null }), 'trial');
  });

  test('does not mutate the record it is given', () => {
    const rec = { status: 'trial', trial_end: now() - 1 };
    const copy = { ...rec };
    effectiveStatus(rec);
    assert.deepEqual(rec, copy, 'flipping the stored record is a webhook job, not a read path');
  });

  test('accepts an explicit now, for deterministic checks', () => {
    const rec = { status: 'trial', trial_end: 1000 };
    assert.equal(effectiveStatus(rec, 999), 'trial');
    assert.equal(effectiveStatus(rec, 1001), 'none');
  });
});
