// The syntax check CLAUDE.md documents as the pre-push gate, folded into the
// test run so it happens automatically instead of being remembered.
//
// Both index files are checked, not just the canonical one: while the
// index.html / index.green.html fork exists (see SWOT.md), a change to either
// can ship on its own, so a syntax error in the website file is just as
// releasable as one in the native file.

import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import vm from 'node:vm';

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '..');

/** The app files carry two <script> tags: the early theme init, then the app. */
function inlineAppScript(file) {
  const html = readFileSync(join(repoRoot, file), 'utf8');
  return html.split('<script>').pop().split('</script>')[0];
}

describe('syntax', () => {
  for (const file of ['index.html', 'index.green.html']) {
    test(`${file} inline script compiles`, () => {
      assert.doesNotThrow(
        () => vm.compileFunction(inlineAppScript(file)),
        `${file}'s inline <script> has a syntax error and must not be pushed`
      );
    });
  }

  test('sw.js compiles', () => {
    assert.doesNotThrow(() => new vm.Script(readFileSync(join(repoRoot, 'sw.js'), 'utf8')));
  });

  // Importing each worker module is the real parse check: a syntax error, a bad
  // specifier, or a broken top-level statement all throw here with the actual
  // error attached. These modules are pure (no side effects at import time),
  // which is what makes importing them safe to do in a test.
  for (const file of ['worker/src/index.js', 'worker/src/entitlement.js',
                      'worker/src/google-auth.js', 'worker/src/entitlement-jwt.js',
                      'worker/src/jwt-common.js']) {
    test(`${file} imports cleanly`, async () => {
      const mod = await import(new URL(`../${file}`, import.meta.url));
      assert.ok(mod, `${file} failed to import`);
    });
  }
});
