# Third-party libraries bundled in `vendor/`

Both files here are minified redistributions of other people's work, and both
carry licences that require their copyright notice to travel with the code.
This file records what each one is; the notice itself lives at the top of each
`.js` file, which is where a licence scanner and a store reviewer will look.

Created 2026-09-01 after an audit found `epub.min.js` shipping with no notice
at all, its header having been stripped by minification.

| File | Project | Version | Copyright | Licence |
| --- | --- | --- | --- | --- |
| `epub.min.js` | [epub.js](https://github.com/futurepress/epub.js) | 0.3.93 | Copyright (c) 2013, FuturePress | BSD-2-Clause |
| `jszip.min.js` | [JSZip](https://github.com/Stuk/jszip) | 3.10.1 | (c) 2009-2016 Stuart Knightley | MIT (elected) or GPL-3.0 |

## Notes that matter

**JSZip is dual-licensed** under MIT or GPL-3.0, and the licensee chooses.
**PhonoLeaf elects MIT.** This is not a formality: GPL-3.0 is the licence the
whole out-of-process speech engine architecture exists to keep at arm's length
(see `android/app/src/main/java/com/phonoleaf/app/ENGINE_NOTICE.md`), so
electing it here by silence would be a self-inflicted version of the same
problem. Record the election anywhere JSZip is listed.

JSZip also bundles [pako](https://github.com/nodeca/pako), MIT, credited in its
own header.

**epub.js reports as `NOASSERTION` to GitHub's licence API**, because its
licence file is not in a form the automated classifier recognises. The licence
itself is plainly BSD-2-Clause on reading it. Expect automated scanners, of the
kind used in store review and technical diligence, to flag it as unknown, and
have this file ready as the answer.

## Still outstanding

The full verbatim licence texts are not reproduced here. Both licences ask for
their text to accompany redistribution, and the accurate way to satisfy that is
to copy each file from upstream rather than retype it:

- epub.js: <https://github.com/futurepress/epub.js/blob/master/license>
- JSZip: <https://github.com/Stuk/jszip/blob/main/LICENSE.markdown>

Drop them in beside this file as `epub.js-LICENSE.txt` and `jszip-LICENSE.txt`.
It is a copy-paste, but it should be an exact one.
