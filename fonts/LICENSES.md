# Fonts bundled in `fonts/`

Both faces are embedded in the web app and staged into the Android build, which
is redistribution. Both are under the SIL Open Font License 1.1, which requires
the copyright notice and the licence to be distributed with the font files.

Created 2026-09-01: before this, the directory held two bare `.woff2` files with
no attribution of any kind.

| File | Family | Copyright | Licence | Upstream |
| --- | --- | --- | --- | --- |
| `manrope.woff2` | Manrope | Copyright 2018 The Manrope Project Authors | OFL-1.1 | <https://github.com/sharanda/manrope> |
| `literata.woff2` | Literata | Copyright 2017 The Literata Project Authors | OFL-1.1 | <https://github.com/googlefonts/literata> |

Both are variable subsets: Manrope carries the UI type, Literata the reading
type in the book view.

## Why this is an obligation rather than a courtesy

The OFL's conditions travel with the font binary. Shipping a `.woff2` inside an
app with no notice is the same category of gap as shipping a minified JS library
with its header stripped, which is what happened to `epub.min.js` (see
`vendor/LICENSES.md`). Neither is a serious legal exposure on its own, and both
are the kind of thing a store review or a diligence pass asks about.

Note also that the OFL forbids selling the fonts on their own and requires that
any *modified* version be renamed. Neither applies here, since both are shipped
unmodified as part of an application, but do not subset or rename these files
without re-reading that clause.

## The licence texts

`manrope-OFL.txt` and `literata-OFL.txt` sit beside this file. They were
fetched verbatim from upstream rather than retyped, because an almost-right
licence text is worse than none. Each carries its own project's copyright line,
which is why there are two files for one licence.

Both are staged into the app build by `scripts/stage-www.js`, so they ship with
the product rather than only living in the repo. If a font is ever added,
removed or subset, update this table, fetch that project's OFL, and add it to
the staging list in the same pass.
