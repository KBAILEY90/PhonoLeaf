// Stage the web app into www/ for the Capacitor native shell.
// The web app has no build step — this is a plain copy of the files the
// wrapper needs. Run via `npm run stage` (or `npm run sync`, which also
// copies www/ into the Android project).
//
// ---------------------------------------------------------------------
// NATIVE / WEB SPLIT (2026-08-28)
// ---------------------------------------------------------------------
// The Android app and the website are built from DIFFERENT index files on
// purpose right now:
//
//   * Native (this script)  -> index.green.html, the Shelf/Green-Ink
//                              redesign. Shipped to Play Store users.
//   * Website (GitHub Pages) -> the repo-root index.html, still the older
//                              design, untouched by this script.
//
// Why: pushing to `main` auto-deploys Pages, so there is no way to ship a
// changed index.html to phones without the website changing at the same
// moment. The native build does NOT come from a push at all (it is
// `npm run sync` + Android Studio, locally), so pointing ONLY this script
// at the redesign lets the app ship while the website stays put.
//
// THIS SPLIT IS PERMANENT, not a to-do — owner decision 2026-08-28 (see
// CLAUDE.md's KNOWN GAP / redesign section). Earlier drafts of this
// comment called it temporary pending a "desktop/responsive pass [to]
// converge" the two files; that framing is superseded. `index.green.html`
// is the native app's real, standing source and `index.html` is the
// website's, indefinitely. `scripts/stage-test.js` still exists and is
// still needed (it stages `index.green.html` as a *test* build without
// touching the real native build config — see `npm run sync:test` in
// CLAUDE.md).
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const out = path.join(root, 'www');

// The single line to change when native and web converge again.
const APP_SOURCE = 'index.green.html';

if (!fs.existsSync(path.join(root, APP_SOURCE))) {
  console.error(`${APP_SOURCE} not found in repo root — nothing to stage.`);
  process.exit(1);
}

fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(out, { recursive: true });

const FILES = [
  'manifest.json', 'sw.js',
  'home.html', 'privacy.html', 'terms.html',
  'home-fr.html', 'privacy-fr.html', 'terms-fr.html',
  // The redesigned legal pages, staged under their own .green names —
  // index.green.html links to privacy.green.html / terms.green.html by
  // those exact names, and each redirects to its own -fr.green sibling
  // when pl_lang is French, so all four have to be present. The plain
  // privacy.html/terms.html above stay staged too: sw.js precaches them
  // and the older index.html still links them.
  'privacy.green.html', 'terms.green.html',
  'privacy-fr.green.html', 'terms-fr.green.html',
  // About + Licences, same .green pairing. The licences page is a licence
  // OBLIGATION, not a credit roll: two voices are CC BY and one is CC BY-SA,
  // all of which require attribution, so it must ship with the app.
  'about.green.html', 'licences.green.html',
  'about-fr.green.html', 'licences-fr.green.html',
];
const DIRS = ['fonts', 'vendor'];

fs.copyFileSync(path.join(root, APP_SOURCE), path.join(out, 'index.html'));
for (const f of FILES) fs.copyFileSync(path.join(root, f), path.join(out, f));
for (const d of DIRS) fs.cpSync(path.join(root, d), path.join(out, d), { recursive: true });

console.log(`Staged web app into www/ (index.html <- ${APP_SOURCE})`);
