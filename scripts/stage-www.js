// Stage the web app into www/ for the Capacitor native shell.
// The web app has no build step — this is a plain copy of the files the
// wrapper needs. Run via `npm run stage` (or `npm run sync`, which also
// copies www/ into the Android project).
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const out = path.join(root, 'www');

fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(out, { recursive: true });

const FILES = ['index.html', 'manifest.json', 'sw.js'];
const DIRS = ['fonts'];

for (const f of FILES) fs.copyFileSync(path.join(root, f), path.join(out, f));
for (const d of DIRS) fs.cpSync(path.join(root, d), path.join(out, d), { recursive: true });

console.log('Staged web app into www/');
