// Source-level invariant checks on the app's inline <script>.
//
// READ THIS BEFORE ADDING TO THIS FILE. These are NOT unit tests. They assert
// that specific hard-won guards still exist in the source, because the code
// they protect is welded into large stateful methods that touch epub.js, the
// DOM and a live audio chain, and cannot be exercised in Node without building
// a fake browser. That is a real project (see SWOT.md); this is the cheap
// thing that works today.
//
// What they are genuinely good for: catching the failure mode CLAUDE.md's
// "Critical facts — do NOT fix these" section exists to prevent, which is a
// later pass deleting a guard that looks redundant. Every assertion below
// corresponds to a bug that actually shipped once.
//
// What they are NOT good for: proving the guards WORK. A test here passing
// means the code is still present, not that it is still correct. Do not let
// this file create false confidence, and do not add assertions here that
// could have been written as real tests instead.

import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { readAppSource, APP_FILE } from './extract.mjs';

const src = readAppSource();

/** Strip HTML so we only assert against the actual script, not markup/comments. */
const script = src.split('<script>').pop().split('</script>')[0];

describe(`app source invariants (${APP_FILE})`, () => {
  test('blank-page auto-skip is still capped', () => {
    // An all-image book would otherwise turn pages forever. Cap shipped as 20.
    assert.match(script, /\+\+this\._skips\s*>\s*\d+/,
      'the _skips cap guarding against an endless run of blank pages is gone');
  });

  test('blank-page skipping is forward-only', () => {
    // Arriving at a chunkless page while moving BACKWARD must stop and wait,
    // never skip further back. Guarded by a _dir check before the turn.
    assert.match(script, /this\._dir\s*<\s*0/,
      'the backward-direction guard before auto-advancing is gone');
  });

  test('a page with real DOM text is never silently skipped', () => {
    // If extraction failed but the DOM has text, stop in place so the page
    // is not jumped over. Diagnostic marker is part of the contract.
    assert.match(script, /stop-hastext/,
      'the "page has text we failed to extract, stop instead of skipping" path is gone');
  });

  test('the generation counter exists and is captured before async work', () => {
    assert.match(script, /_gen:\s*0/, 'TTS._gen declaration is gone');
    // The pattern that matters: snapshot `const gen = this._gen` and compare
    // later, so a stale callback from a page already left cannot double-advance.
    assert.match(script, /const gen = this\._gen/,
      'the generation snapshot taken before async work is gone');
    assert.match(script, /this\._gen !== gen/,
      'the stale-callback comparison against the snapshot is gone');
  });

  test('_split keeps the |$ alternative that saves the last sentence', () => {
    // Also covered behaviourally in split.test.mjs. Asserted here too because
    // this exact character sequence is what a "tidy up the regex" pass removes.
    assert.match(script, /\(\?:\[[^\]]*\]\+\|\$\)/,
      'the `|$` alternative in _split is gone; the last sentence on a page ' +
      'will be silently dropped and the page will turn without reading it');
  });

  test('the minimized reader stays laid out rather than display:none', () => {
    // Backgrounded playback needs a laid-out rendition, so the minimized state
    // hides the reader with z-index and keeps display:flex. display:none here
    // stops TTS. This rule lives in the <style> block, not the script.
    const rule = /\.view\.minimized\s*\{[^}]*\}/.exec(src);
    assert.ok(rule, 'the .view.minimized rule is gone entirely');
    assert.match(rule[0], /display:\s*flex\s*!important/,
      '.view.minimized must force display:flex — display:none stops playback');
    assert.match(rule[0], /z-index:\s*-1/,
      '.view.minimized must hide via z-index, not by removing it from layout');
  });

  test('no Kotlin caller uses startForegroundService', async () => {
    // startForegroundService arms a ~5s watchdog that already crashed the app
    // once when startForeground() lost the race, so every call site must use
    // context.startService(). Comments legitimately MENTION the banned API to
    // explain why it is banned, so strip comments before asserting.
    const { readFileSync, readdirSync } = await import('node:fs');
    const dir = new URL('../android/app/src/main/java/com/phonoleaf/app/', import.meta.url);
    const offenders = [];
    for (const f of readdirSync(dir).filter(f => f.endsWith('.kt'))) {
      const code = readFileSync(new URL(f, dir), 'utf8')
        .replace(/\/\*[\s\S]*?\*\//g, '')  // block comments
        .replace(/\/\/.*$/gm, '');          // line comments
      if (/startForegroundService\s*\(/.test(code)) offenders.push(f);
    }
    assert.deepEqual(offenders, [],
      `these files call startForegroundService in real code: ${offenders.join(', ')}. ` +
      `It arms the ~5s watchdog that crashed the app once. Use context.startService().`);
  });

  test('each foreground service still promotes itself with startForeground', async () => {
    // The flip side of the rule above: the SERVICE must still promote itself in
    // onStartCommand or Android kills it. Banning the STARTER api must never be
    // mistaken for banning this one, which is what makes the pairing safe.
    const { readFileSync } = await import('node:fs');
    const dir = new URL('../android/app/src/main/java/com/phonoleaf/app/', import.meta.url);
    for (const f of ['PlaybackService.kt', 'PackDownloadService.kt']) {
      const code = readFileSync(new URL(f, dir), 'utf8')
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/\/\/.*$/gm, '');
      assert.match(code, /startForeground\s*\(/,
        `${f} no longer calls startForeground(); Android will kill the service`);
    }
  });
});
