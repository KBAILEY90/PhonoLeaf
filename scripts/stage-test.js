// Stage the redesign TEST page (index.green.html) into www/ as index.html,
// so the native shell can be built against it without touching the real
// index.html — same file list/logic as stage-www.js, just one substitution.
// Run via `npm run stage:test` (or `npm run sync:test`, which also copies
// www/ into the Android project). To go back to the real app, just run the
// normal `npm run sync` again — it re-stages the real index.html.
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const out = path.join(root, 'www');
const TEST_SOURCE = 'index.green.html';

if (!fs.existsSync(path.join(root, TEST_SOURCE))) {
  console.error(`${TEST_SOURCE} not found in repo root — nothing to stage.`);
  process.exit(1);
}

fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(out, { recursive: true });

const FILES = [
  'manifest.json', 'sw.js',
  'home.html', 'privacy.html', 'terms.html',
  'home-fr.html', 'privacy-fr.html', 'terms-fr.html',
  // The redesigned legal pages, staged under their own .green names (NOT
  // substituted over privacy.html/terms.html the way index.green.html is
  // over index.html) — index.green.html links to them by these exact
  // names, so browser and native test builds resolve identically. The real
  // privacy.html/terms.html above stay staged too: they're what the REAL
  // index.html links to, and `npm run sync` must keep working unchanged.
  'privacy.green.html', 'terms.green.html',
  'privacy-fr.green.html', 'terms-fr.green.html',
];
const DIRS = ['fonts', 'vendor'];

fs.copyFileSync(path.join(root, TEST_SOURCE), path.join(out, 'index.html'));
for (const f of FILES) fs.copyFileSync(path.join(root, f), path.join(out, f));
for (const d of DIRS) fs.cpSync(path.join(root, d), path.join(out, d), { recursive: true });

console.log(`Staged ${TEST_SOURCE} into www/index.html (TEST BUILD).`);
console.log('Run `npm run sync` (not sync:test) afterward to restore the real app.');
