// Guards against storing signing credentials — or any secret — in a file in
// the working tree.
//
// WHY THIS EXISTS. android/keystore.properties leaked the release signing
// password into a conversation transcript twice (2026-09-01, 2026-09-02).
// Neither leak involved anyone reading the file: assistant tooling watches
// files it has touched and echoes their contents whenever they change, so the
// file's mere existence was the exposure. Rotating the password did not help —
// the second leak WAS the rotated value.
//
// The rule that actually holds is therefore structural, not behavioural: the
// file must not exist, and nothing may read one. A "be careful with this file"
// convention cannot be enforced and has already failed twice.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const ROOT = fileURLToPath(new URL('..', import.meta.url));

test('no signing credentials file exists in the tree', () => {
  const p = join(ROOT, 'android', 'keystore.properties');
  assert.equal(
    existsSync(p), false,
    'android/keystore.properties exists. Signing credentials must live in the ' +
    'PHONOLEAF_* environment variables, never in a file — this file leaked the ' +
    'signing password into a transcript twice. Delete it.'
  );
});

test('the release build reads signing credentials from the environment only', () => {
  const gradle = readFileSync(join(ROOT, 'android', 'app', 'build.gradle'), 'utf8');

  for (const v of ['PHONOLEAF_STORE_FILE', 'PHONOLEAF_STORE_PASSWORD',
                   'PHONOLEAF_KEY_ALIAS', 'PHONOLEAF_KEY_PASSWORD']) {
    assert.ok(gradle.includes("System.getenv('" + v + "')"),
      `build.gradle must read ${v} from the environment`);
  }

  // The old mechanism, in any form. `new Properties()` + `withInputStream` is
  // how a .properties file gets loaded; neither belongs in a signing path.
  assert.ok(!/new\s+Properties\s*\(/.test(gradle),
    'build.gradle loads a .properties file — signing credentials must come from the environment');
  assert.ok(!/keystoreProps\s*\[/.test(gradle),
    'build.gradle still reads values out of a keystore properties file');

  // And the reappearance guard itself must stay.
  assert.ok(/throw new GradleException/.test(gradle) && /keystore\.properties/.test(gradle),
    'build.gradle must fail the build if keystore.properties reappears');
});

test('no tracked file contains a literal signing password', () => {
  const files = execFileSync('git', ['ls-files', '-z'], { cwd: ROOT, encoding: 'utf8' })
    .split('\u0000').filter(Boolean);

  // `...`, ALL_CAPS names, <angle brackets> and the like are documentation.
  const isPlaceholder = (v) =>
    v === '' || v.includes('...') || /^[A-Z_]+$/.test(v) ||
    /[<>{}[\]]/.test(v) || /YOUR|PASTE|EXAMPLE|REDACT/i.test(v);

  const offenders = [];
  for (const f of files) {
    // Only this file describes the pattern it hunts for.
    if (f.endsWith('test/secrets.test.mjs')) continue;

    const full = join(ROOT, f);
    let st;
    try { st = statSync(full); } catch { continue; }
    if (!st.isFile() || st.size > 2000000) continue;

    let text;
    try { text = readFileSync(full, 'utf8'); } catch { continue; }
    if (text.includes('\u0000')) continue; // binary

    for (const m of text.matchAll(/(?:storePassword|keyPassword)\s*=\s*([^\s"'\r\n]*)/g)) {
      if (!isPlaceholder(m[1])) offenders.push(`${f}: ${m[0].split('=')[0]}=<value>`);
    }
  }

  assert.deepEqual(offenders, [],
    'a tracked file contains a literal signing password:\n  ' + offenders.join('\n  '));
});
