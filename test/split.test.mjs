// Real unit tests for TTS._split(), extracted from the shipped app source.
//
// _split is the highest-value thing in the app to pin down: it is pure, it is
// on the hot path for every page read aloud, and it has already shipped one
// real bug that this file now guards (the `|$` alternative — without it the
// last sentence on a page, cut by the column break and therefore lacking
// terminal punctuation, matched nothing and was silently dropped, so the page
// turned without reading it).
//
// Current contract, as implemented:
//   - splits on . ! ? and newline
//   - accumulates sentences into chunks, starting a new chunk once adding the
//     next sentence would exceed 220 chars (and the current chunk is non-empty)
//   - trims each chunk, and drops any chunk of 3 chars or fewer

import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { readAppSource, methodAsFunction, APP_FILE } from './extract.mjs';

const split = methodAsFunction(readAppSource(), '_split');

describe(`TTS._split (${APP_FILE})`, () => {
  test('returns [] for empty / nullish input', () => {
    assert.deepEqual(split(''), []);
    assert.deepEqual(split(null), []);
    assert.deepEqual(split(undefined), []);
  });

  test('splits a simple multi-sentence paragraph', () => {
    const out = split('The cat sat. The dog ran. The bird flew.');
    assert.equal(out.length, 1, 'short sentences should coalesce into one chunk');
    assert.equal(out[0].replace(/\s+/g, ' '), 'The cat sat. The dog ran. The bird flew.');
  });

  test('DOCUMENTED QUIRK: joined sentences are separated by two spaces', () => {
    // Not a bug being asserted as correct, just current behaviour pinned so a
    // future change is a visible decision rather than an accident: the sentence
    // regex captures the leading space with each sentence, and the accumulator
    // then adds another (`cur += ' ' + s`). Harmless to the TTS engines (both
    // collapse whitespace), which is why it has never surfaced. Anything that
    // tokenises a chunk on whitespace — follow-along word highlighting is the
    // one that would — should expect empty tokens and filter them.
    const out = split('The cat sat. The dog ran. The bird flew.');
    assert.match(out[0], /sat\.\s{2}The dog/);
  });

  test('REGRESSION: keeps a final sentence with no terminal punctuation', () => {
    // This is the `|$` case. Without it this text yields nothing at all.
    const out = split('A complete sentence. A trailing fragment with no period');
    const joined = out.join(' ');
    assert.match(joined, /trailing fragment with no period/,
      'the unterminated last sentence must not be dropped');
  });

  test('REGRESSION: a page that is ONLY an unterminated fragment still reads', () => {
    const out = split('the position that');
    assert.equal(out.length, 1);
    assert.equal(out[0], 'the position that');
  });

  test('starts a new chunk rather than exceeding 220 chars', () => {
    const sentence = 'x'.repeat(100) + '. ';
    const out = split(sentence.repeat(6));
    assert.ok(out.length > 1, 'should have split into several chunks');
    for (const c of out) {
      assert.ok(c.length <= 320,
        `chunk unexpectedly long (${c.length}): the 220 threshold is checked ` +
        `BEFORE appending, so one sentence may overshoot, but not unboundedly`);
    }
  });

  test('a single sentence longer than 220 chars is NOT truncated', () => {
    // The threshold only decides where to START a new chunk; it never cuts a
    // sentence in half. Losing text here would be silent and unrecoverable.
    const long = 'y'.repeat(500) + '.';
    const out = split(long);
    assert.equal(out.join('').replace(/\s/g, '').length, 501);
  });

  test('drops chunks of 3 chars or fewer', () => {
    // "Hi." is 3 chars after trimming and is filtered out by design.
    assert.deepEqual(split('Hi.'), []);
    assert.deepEqual(split('No!'), []);
    // 4 chars survives.
    assert.deepEqual(split('Yes!'), ['Yes!']);
  });

  test('treats newlines as sentence boundaries', () => {
    const out = split('A heading line\nAnd the body text that follows it here.');
    assert.equal(out.length, 1);
    assert.match(out[0], /A heading line/);
  });

  test('handles an ellipsis without producing empty chunks', () => {
    const out = split('He paused... then continued speaking at some length.');
    assert.ok(out.length >= 1);
    for (const c of out) assert.ok(c.trim().length > 3, 'no empty/tiny chunks');
    assert.match(out.join(' '), /He paused/);
    assert.match(out.join(' '), /continued speaking/);
  });

  test('every returned chunk is trimmed', () => {
    const out = split('One sentence here. Another sentence here. A third one here.');
    for (const c of out) assert.equal(c, c.trim());
  });

  test('no chunk is ever empty', () => {
    const messy = '. . . Real content follows this run of stray periods here.';
    for (const c of split(messy)) assert.ok(c.length > 0);
  });
});
