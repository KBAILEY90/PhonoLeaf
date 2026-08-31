// Pulls a single named method's source straight out of the app's inline
// <script>, so a test can exercise the REAL shipped function rather than a
// copy that silently drifts from it.
//
// Why this exists: the app is one HTML file with one inline <script> that does
// DOM work as it loads, so it cannot simply be imported. Extracting one pure
// method and evaluating it in isolation is the only way to unit test anything
// in there without first restructuring the file. That restructure is a real
// project (see SWOT.md); this is the cheap thing that works today.
//
// The extractor is deliberately strict: if a method is renamed, moved, or has
// its signature changed, extraction THROWS instead of silently skipping the
// test. A test that quietly stops running is worse than no test.

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '..');

/** The canonical app source. See SWOT.md on the index.html / index.green.html fork. */
export const APP_FILE = process.env.PHONOLEAF_APP_FILE || 'index.green.html';

export function readAppSource(file = APP_FILE) {
  return readFileSync(join(repoRoot, file), 'utf8');
}

/**
 * Extract `name(args) { ... }` from the source by brace matching.
 * Returns the method body source, ready to wrap in a function.
 */
export function extractMethod(source, name) {
  // Match an object-literal method at the start of a line: `  _split(text) {`
  const header = new RegExp(`^[ \\t]*${name}\\s*\\(([^)]*)\\)\\s*\\{`, 'm');
  const m = header.exec(source);
  if (!m) {
    throw new Error(
      `extractMethod: could not find method "${name}" in the app source. ` +
      `It was probably renamed or reformatted. Update the test to match the ` +
      `new shape rather than deleting it.`
    );
  }

  const argsSrc = m[1];
  const openIdx = m.index + m[0].length - 1; // index of the '{'

  // Brace-match forward. The app source has no template literals or regex
  // literals containing unbalanced braces inside the methods this is used on;
  // if that ever changes, this throws rather than returning a truncated body.
  let depth = 0;
  let end = -1;
  for (let i = openIdx; i < source.length; i++) {
    const ch = source[i];
    if (ch === '{') depth++;
    else if (ch === '}') {
      depth--;
      if (depth === 0) { end = i; break; }
    }
  }
  if (end === -1) throw new Error(`extractMethod: unbalanced braces reading "${name}"`);

  return { args: argsSrc, body: source.slice(openIdx + 1, end) };
}

/**
 * Extract a method and return it as a callable standalone function.
 * `self` becomes `this` inside the method, so a method that calls a sibling
 * (like _split calling nothing, but others might) can be given stubs.
 */
export function methodAsFunction(source, name, self = {}) {
  const { args, body } = extractMethod(source, name);
  // eslint-disable-next-line no-new-func
  const fn = new Function(args, body);
  return (...callArgs) => fn.apply(self, callArgs);
}
