-- PAYMENTS_SPEC.md §2: entitlement records, keyed by sub_hash.
-- Same shape as the KV record this replaces (see git history for
-- src/entitlement.js pre-D1) — status/source/plan/trial_end/period_end,
-- plus updated_at. sub_hash is a SHA-256 hex digest (64 chars), never the
-- raw Google `sub` (§1, §7).
CREATE TABLE IF NOT EXISTS entitlements (
  sub_hash    TEXT PRIMARY KEY,
  status      TEXT NOT NULL,
  source      TEXT,
  plan        TEXT,
  trial_end   INTEGER,
  period_end  INTEGER,
  updated_at  INTEGER NOT NULL
);
