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

  test('the model-ready cache is invalidated when the loaded model can change', () => {
    // Owner-reported 2026-09-01: delete a voice pack, download a different one,
    // press play, and every sentence stutters.
    //
    // Cause: _modelReadyP ("a model is loaded and ready") was assigned once and
    // NOTHING anywhere ever cleared it. After a pack change the app still
    // believed the old model was warm, so the new one was never pre-warmed and
    // every chunk paid the cold load. _modelType was stale too, and
    // _synthNative resolves the voice sid from it, so the sid was resolved
    // against the wrong model family.
    //
    // Two independent protections, because one alone has a gap:
    //   * _modelReady() compares the cached model against the selected one and
    //     self-heals. Covers switching VOICE, and cannot be forgotten by a new
    //     call site, which is how the original bug happened.
    //   * Explicit invalidation on pack download/delete. Covers the SAME model
    //     being re-downloaded, where the name does not change but the engine
    //     has dropped its copy.
    assert.match(script, /_invalidateModelReady\s*\(\)\s*\{/,
      'TTS._invalidateModelReady is gone. Nothing then clears the "model is ready" ' +
      'cache, and a pack swap goes back to stuttering on every sentence.');

    assert.match(script, /_modelReadyFor/,
      '_modelReadyFor is gone, so _modelReady() can no longer tell "already warm" ' +
      'from "warm for a DIFFERENT model" and will hand back a stale promise.');

    // localStorage copy must be cleared too, or a stale family survives a restart.
    const at = script.indexOf('_invalidateModelReady() {');
    assert.ok(at > -1, 'could not locate the _invalidateModelReady body');
    const inv = [script.slice(at, at + 400)];
    assert.match(inv[0], /removeItem\(['"]pl_modeltype['"]\)/,
      '_invalidateModelReady no longer clears pl_modeltype. _modelType is restored ' +
      'from localStorage at parse time, so a stale value would outlive the fix.');

    // And it must actually be CALLED from the pack paths, not just defined.
    const calls = (script.match(/_invalidateModelReady\(\)/g) || []).length;
    assert.ok(calls >= 4,
      `_invalidateModelReady is referenced ${calls} time(s); expected at least 4 ` +
      '(definition, the self-heal in _modelReady, and the download / delete paths). ' +
      'A definition nothing calls fixes nothing.');
  });

  test('a page turn waits for the text to stop changing before reading it', () => {
    // Owner-reported 2026-09-01: "when the page changes, the voice skips the
    // first 1-2 lines."
    //
    // _resumeRead only retried when it got NOTHING, or got the PREVIOUS page.
    // Neither catches a PARTIAL page, which is the common case mid-turn: the
    // column is still settling, the opening lines still measure as belonging to
    // the outgoing page and fail loadPageText()'s in-view test, and the rest of
    // the page passes it. That yields a non-empty, non-stale extraction missing
    // its first lines, which read as success. The lines were never spoken and
    // nothing reported it.
    //
    // The fix requires the same extraction twice in a row. This asserts the
    // comparison against the previous pass still exists, because removing it
    // looks like removing a redundant retry and silently drops content again.
    assert.match(script, /_resumeRead\(tries,\s*prevExtract\)/,
      '_resumeRead no longer takes prevExtract, so it cannot compare this pass ' +
      'against the last one and will read partially-laid-out pages again.');
    assert.match(script, /this\._lastText\s*!==\s*prevExtract/,
      'the stability comparison in _resumeRead is gone; a page turn can once ' +
      'again start reading before the first lines have laid out, skipping them.');
  });

  test('the speed benchmark yields to playback instead of competing with it', () => {
    // Owner-reported 2026-09-01: "deleted a pack, downloaded another, pressed
    // play, had to press play again, stuttered for a full page, and when the
    // page turned the stuttering was gone."
    //
    // _nativeBench does a REAL synthesis, and the engine serves one request at
    // a time. Fired un-awaited from the download path it sat in front of the
    // reader's own chunks: every sentence queued behind it, prefetch never got
    // ahead, and the first play press looked dead because it was in the same
    // queue. The stutter ended exactly when the benchmark finished.
    //
    // The fix is a guard that defers the benchmark while TTS.active. Asserted
    // here because it reads like a redundant early-return: it is informational
    // work, so nothing breaks or errors if the guard is deleted. Playback just
    // quietly gets worse again after every download.
    const at = script.indexOf('async _nativeBench(model)');
    assert.ok(at > -1, '_nativeBench is gone');
    const body = script.slice(at, at + 3000);

    const guard = body.indexOf('if (this.active)');
    const synth = body.indexOf('nat.synthesize');
    assert.ok(guard > -1,
      '_nativeBench no longer checks TTS.active. It will run a full synthesis ' +
      'while the user is reading, and every sentence will queue behind it.');
    assert.ok(synth > -1, 'could not find the benchmark synthesis call');
    assert.ok(guard < synth,
      'the TTS.active guard in _nativeBench no longer comes BEFORE its ' +
      'synthesize call, so it can still contend with playback.');
    // Assert the DEFERRAL too, not just a check. An early `return` when active
    // would satisfy the ordering above while quietly meaning the benchmark
    // never runs at all on a device that is used for reading -- Settings would
    // then never label the pack's speed. _benchWait is unique to the retry, so
    // it distinguishes "yields and comes back" from "gives up".
    assert.ok(body.includes('_benchWait'),
      '_nativeBench no longer defers via _benchWait. Either it competes with ' +
      'playback again, or it silently never runs on a device in active use.');
  });

  test('the follow-along highlight stops when a sentence ends', () => {
    // Owner-reported 2026-09-01: the highlight flashed back to the START of a
    // sentence for about a second as that sentence finished, which also read as
    // the sentence being repeated.
    //
    // The loop's guards are `active`, `gen`, and "a newer schedule replaced
    // mine". A sentence merely ENDING trips none of them: playback continues,
    // gen changes only on a page turn or stop, and the next schedule is not
    // installed until the next chunk's audio has loaded. The shared audio
    // element's currentTime has meanwhile reset to 0, so the still-running loop
    // resolves to word 0 and highlights the first word of the sentence that
    // just finished.
    //
    // Two protections, deliberately kept together: the ended handler clears the
    // loop, and the loop itself refuses to act on a finished element. Either
    // alone fixes the visible symptom, but the ordering of the ended event
    // against the next animation frame is not guaranteed.
    //
    // Worth a test because nothing errors. The highlight simply points at the
    // wrong words, which is a correctness bug in the one feature whose entire
    // job is pointing at the right words.
    const at = script.indexOf('a.onended = () => {');
    assert.ok(at > -1, 'the native audio ended handler is gone');
    // Bounded by the sibling onerror handler rather than a byte count: the
    // comment explaining this fix is itself ~800 chars, so a fixed window sized
    // to "the handler" silently excluded the very line being asserted.
    const end = script.indexOf('a.onerror', at);
    const handler = script.slice(at, end > at ? end : at + 2500);
    assert.ok(handler.includes('this._clearHighlight()'),
      'the native ended handler no longer clears the follow-along highlight, so ' +
      'the loop keeps running into the next chunk and re-highlights word 0.');

    const tickAt = script.indexOf('const tick = () => {');
    assert.ok(tickAt > -1, 'the highlight tick loop is gone');
    const tick = script.slice(tickAt, tickAt + 700);
    assert.ok(tick.includes('a.ended'),
      'the highlight loop no longer bails on finished audio. currentTime reads 0 ' +
      'once ended, which resolves to the first word of the sentence just spoken.');
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

  test('the missing-pack error contract survives both process boundaries', async () => {
    // THE 2026-09-01 BUG, in test form. Deleting a voice pack killed the
    // natural voice for the whole session, silently, with no crash.
    //
    // Why it happened: a missing pack is signalled by a STRING that crosses
    // two boundaries. TtsService.kt (a separate OS process) emits
    // "err:notdownloaded:<model>"; PhonoLeafTtsPlugin.kt renames it to
    // "PACK_NOT_DOWNLOADED:<model>"; the web layer matches that prefix and
    // responds by switching voices rather than counting a failure. The
    // out-of-process cut-over changed the service's wording, the rename no
    // longer matched, and every attempt counted as an ENGINE failure instead.
    // Two in a row set _kokoroDead, and native has no Web Speech fallback,
    // so playback just stopped.
    //
    // The lesson worth encoding: strings that cross this boundary are API,
    // not implementation. This test is the thing that was missing.
    const { readFileSync } = await import('node:fs');
    const appDir = new URL('../android/app/src/main/java/com/phonoleaf/app/', import.meta.url);
    // The engine bridge moved OUT of the app's source tree on 2026-09-01, onto
    // legal advice: it is GPL-3.0 and the app is proprietary, so they are kept
    // in separate directories with separate licences and separate packages.
    const bridgeDir = new URL('../android/tts-bridge/java/com/phonoleaf/ttsbridge/', import.meta.url);
    const strip = (f, dir) => readFileSync(new URL(f, dir), 'utf8')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/\/\/.*$/gm, '');

    const service = strip('TtsService.kt', bridgeDir);
    const plugin = strip('PhonoLeafTtsPlugin.kt', appDir);

    // 1. The engine still emits the prefix the plugin looks for.
    assert.match(service, /"err:notdownloaded:/,
      'TtsService.kt no longer emits "err:notdownloaded:". The plugin matches on ' +
      'that exact prefix, so renaming it here silently breaks pack-switching.');

    // 2. The plugin still recognises it AND still renames it to what JS expects.
    assert.match(plugin, /startsWith\("err:notdownloaded:"\)/,
      'PhonoLeafTtsPlugin.kt no longer matches the service\'s "err:notdownloaded:" prefix.');

    // Both paths must translate: synthesize() AND prepare(). The first cut-over
    // missed prepare(), so a fix applied to only one of them looks correct
    // while leaving half the failure live.
    const translations = plugin.match(/"PACK_NOT_DOWNLOADED:"/g) || [];
    assert.ok(translations.length >= 2,
      `PhonoLeafTtsPlugin.kt translates to PACK_NOT_DOWNLOADED: in ${translations.length} ` +
      'place(s), expected at least 2 (synthesize and prepare). A translation added to ' +
      'only one path leaves the other reporting a generic engine failure.');

    // 3. The web layer still matches the name the plugin produces.
    assert.match(script, /startsWith\('PACK_NOT_DOWNLOADED:'\)/,
      'The web layer no longer matches "PACK_NOT_DOWNLOADED:". Without it a missing ' +
      'pack counts as an engine failure, and two of those disable the neural voice ' +
      'for the session with no fallback on native.');
  });

  test('the cancel error contract still matches across the boundary', () => {
    // The other string that crosses the same boundary, and the one that
    // survived the cut-over only by luck: the service says "err:cancelled",
    // the web layer tests /cancel/i, and those agree solely because
    // "cancelled" happens to contain "cancel". If either side is reworded to
    // something like "err:aborted", a user-initiated cancel starts reporting
    // itself as a download FAILURE toast. Cheap to assert, so assert it.
    assert.match(script, /\/cancel\/i/,
      'The web layer no longer tests /cancel/i, so a cancelled download will be ' +
      'reported to the user as a failure.');
  });

  test('the speech engine stays behind its licence boundary', async () => {
    // Not a correctness guard — a LICENCE one, and the most expensive thing in
    // this repo to get wrong. espeak-ng is GPL-3.0 and is statically linked
    // into the sherpa-onnx native library. The argument that PhonoLeaf can
    // stay closed source rests on no PhonoLeaf code linking it: synthesis runs
    // in its own :tts process, reached only over AIDL. One stray import
    // anywhere else collapses that. See ENGINE_NOTICE.md.
    const { readFileSync, readdirSync } = await import('node:fs');
    const dir = new URL('../android/app/src/main/java/com/phonoleaf/app/', import.meta.url);
    // No exclusion any more: since 2026-09-01 the bridge lives outside this
    // directory entirely, so NOTHING under the app package may name the engine.
    const offenders = [];
    for (const f of readdirSync(dir).filter(f => f.endsWith('.kt'))) {
      const code = readFileSync(new URL(f, dir), 'utf8')
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/\/\/.*$/gm, '');
      if (/com\.k2fsa\.sherpa\.onnx/.test(code)) offenders.push(f);
    }
    assert.deepEqual(offenders, [],
      `these files reference sherpa-onnx outside TtsService.kt: ${offenders.join(', ')}. ` +
      'That links GPL-3.0 espeak-ng into the app and breaks the licence boundary ' +
      'ENGINE_NOTICE.md describes. Only TtsService.kt may touch the engine.');
  });

  test('the GPL bridge stays in its own directory, package and licence', async () => {
    // Structural separation, required on legal advice (2026-09-01). The bridge
    // links GPL-3.0 espeak-ng; PhonoLeaf is proprietary. The advice was that
    // keeping GPL sources mixed in with proprietary ones weakens the argument
    // that they are separate programs combined only at packaging time, so the
    // bridge has its own directory, its own package name and its own LICENSE.
    //
    // This test exists because that separation is invisible at runtime. Nothing
    // breaks if someone "tidies" the bridge back into the app package; it just
    // quietly costs the legal position. Fail loudly instead.
    const { readFileSync, existsSync } = await import('node:fs');
    const root = new URL('../android/tts-bridge/', import.meta.url);
    const files = [
      'java/com/phonoleaf/ttsbridge/TtsService.kt',
      'aidl/com/phonoleaf/ttsbridge/ITtsService.aidl',
    ];

    for (const f of files) {
      const url = new URL(f, root);
      assert.ok(existsSync(url), `${f} is missing from android/tts-bridge/. The GPL bridge ` +
        'must stay outside the app source tree.');
      const text = readFileSync(url, 'utf8');
      assert.match(text, /package com\.phonoleaf\.ttsbridge/,
        `${f} must declare package com.phonoleaf.ttsbridge, separate from the app's ` +
        'com.phonoleaf.app. Two distinct packages is part of the separation argument.');
      assert.match(text, /GNU General Public License/,
        `${f} lost its GPL-3.0 header. Required: this file links GPL code.`);
    }

    assert.ok(existsSync(new URL('LICENSE', root)),
      'android/tts-bridge/LICENSE is gone. The GPL-3.0 text must ship beside the code it covers.');
    assert.match(readFileSync(new URL('LICENSE', root), 'utf8'), /GNU GENERAL PUBLIC LICENSE/,
      'android/tts-bridge/LICENSE is no longer the GPL text.');

    // And the reverse: no proprietary app file may claim the bridge package.
    const appDir = new URL('../android/app/src/main/java/com/phonoleaf/app/', import.meta.url);
    const { readdirSync } = await import('node:fs');
    for (const f of readdirSync(appDir).filter(f => f.endsWith('.kt'))) {
      assert.doesNotMatch(readFileSync(new URL(f, appDir), 'utf8'), /^package com\.phonoleaf\.ttsbridge/m,
        `${f} declares the bridge package while sitting in the proprietary app directory.`);
    }
  });

  test('the engine process is restarted whenever a pack changes on disk', async () => {
    // Owner-reported 2026-09-01, and the hardest bug of the session to find.
    //
    // Symptom: play something, delete that voice's pack, download a different
    // LANGUAGE, play again, and every sentence came out as a fraction of a
    // second of noise. Measured on device: text that gives 8800ms of audio on a
    // settled pack gave 487ms, consistently ~14x short.
    //
    // Cause: the phonemizer inside the engine initialises its data directory
    // ONCE PER PROCESS, from whichever pack loads first, and never revisits it.
    // Installing or deleting a pack deletes that directory, leaving the engine
    // process pointing at something gone. Freeing the engine object does NOT
    // fix it, because the state belongs to the process, not the instance.
    //
    // Why it looked erratic, and why this is worth a test: it only bit when the
    // deleted pack was the one that had initialised the phonemizer, and
    // re-downloading "us" appeared to heal it purely because "us" maps to the
    // folder "kokoro", so the recreated directory landed back on the cached
    // path. gb/fr/de map to other folders and stayed broken. Nothing logs an
    // error in any of this.
    const { readFileSync } = await import('node:fs');
    const appDir = new URL('../android/app/src/main/java/com/phonoleaf/app/', import.meta.url);
    const bridgeDir = new URL('../android/tts-bridge/', import.meta.url);
    const strip = f => readFileSync(f, 'utf8')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/\/\/.*$/gm, '');

    const plugin = strip(new URL('PhonoLeafTtsPlugin.kt', appDir));
    const service = strip(new URL('java/com/phonoleaf/ttsbridge/TtsService.kt', bridgeDir));
    const aidl = strip(new URL('aidl/com/phonoleaf/ttsbridge/ITtsService.aidl', bridgeDir));

    assert.ok(aidl.includes('void shutdown()'),
      'ITtsService.shutdown() is gone. Without a process-level reset there is no ' +
      'way to clear the phonemizer state a pack change invalidates.');
    assert.ok(service.includes('override fun shutdown()') && service.includes('killProcess'),
      'TtsService.shutdown() no longer ends its process. Releasing the engine ' +
      'alone does NOT reset the phonemizer, which is process-global.');

    // Both paths that change a pack's files must trigger it. Install-only or
    // delete-only leaves half the bug live, and the symptom is silent.
    const resets = (plugin.match(/resetEngineProcess\(\)/g) || []).length;
    assert.ok(resets >= 3,
      `resetEngineProcess is referenced ${resets} time(s) in the plugin; expected at ` +
      'least 3 (its definition, the pack INSTALL path, and the pack DELETE path).');

    // And the engine must still be freed explicitly rather than left to the GC.
    assert.ok(service.includes('releaseEngine()') && service.includes('.release()'),
      'TtsService no longer frees the native engine explicitly. It then lingers ' +
      'until a finalizer runs, holding memory and stale native state.');
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
