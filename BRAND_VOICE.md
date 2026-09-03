# PhonoLeaf brand voice

**Written 2026-09-02**, from three rounds of owner review on the humour variant
of the home page (`home-v3.html` / `home-v3-fr.html`). Everything here was
learned by getting it wrong first, so the worked examples matter more than the
principle.

Governs **all user-facing writing**: the website, the app's own copy (toasts,
onboarding, empty states, Settings), and the store listings. It does **not**
govern code comments, commit messages or the internal docs.

---

## The rule

**Be funny. Never at our own expense.**

PhonoLeaf is giving someone their books back. That is a benefit, not an
imposition, and the writing has to behave like it.

## The test that makes it usable

Ask **who the joke is at**.

| Joke aimed at | Verdict | Real examples that shipped |
| --- | --- | --- |
| The **reader** | Fine | "You have simply run out of eyes", "Numbers to feel quietly smug about", "the one you abandoned in March", "the business book someone made you read" |
| A **situation** | Fine | a phone buried in a coat, a tunnel, "the stretch of countryside where your map app quietly gives up", "regardless of what your hardware thinks of the idea" |
| A **competitor** | Fine, and on strategy | "no newsletter", "with no retention email asking whether you are absolutely sure", "whatever you are currently arguing with" |
| **Ourselves** | **Never** | see the table below, all of which had to be removed |

## Why this is a commercial rule, not a style preference

In the owner's words: **we are not annoying the user, we are providing
something beneficial.**

Self-deprecation reads as *charm* from a brand people already trust, and as
*doubt* from one nobody has heard of. PhonoLeaf is currently the second. A
visitor arriving from a search result has no reason to argue us out of our own
low opinion of ourselves.

The specific trap: **the app getting out of your way is a FEATURE.** Every
single failure below came from selling that feature as an apology instead.

## What was actually wrong, and what it became

All eleven were caught in review rather than in writing, which is the point:
self-deprecation reads as wit while you are writing it.

| Before | After | What was wrong |
| --- | --- | --- |
| "The less thrilling rest" | "Everything else, done properly" | Told the visitor to skip half the feature list |
| "A voice you can tolerate for nine hours" | "A voice that lasts a whole novel" | Faint praise for the feature the product is built on |
| "…your system voice, which is honest about being one [a robot]" | "…your system voice steps in and the reading carries on" | Called our own fallback robotic, in the reassurance section |
| "Nobody else sees them, which rather spoils the boasting" | "…so the smugness is entirely yours" | Made privacy sound like a consolation prize |
| "Three steps, then we go quiet" | "Three steps, and the hardest part is choosing the book" | Sold setup as us being tiresome and knowing it |
| "then, crucially, leaves you alone" | "…so they come along with you, on the walk, the commute, or the washing up" | Cast the product as an interruption |
| "puis vous fiche la paix" | "vos livres vous suivent en marchant, dans le trafic ou devant la vaisselle" | Same, in French |
| "The quieter pleasures" | "Everything else, done properly" | Still said this half mattered less |
| "The part with the numbers" / "Le passage obligé des chiffres" | "The numbers, without the games" / "Les chiffres, sans entourloupe" | Called the price a tedious obligation, on the section that has to convert |
| "Cancel whenever you like, we will manage" | "Cancel from your store account, with no phone call to make" | Braced for the customer leaving |
| "Elle ne se tait pas au fond d'une poche" | "Elle continue au fond d'une poche" | Stated the feature as a negative |

**The reusable shape:** where a line said *we will stop bothering you*, it now
says *what you get instead*. The joke usually survives the change; only its
target moves.

## Where humour does not go at all

Not tone, correctness. These are commitments, not copy.

- **The "How PhonoLeaf uses your Google Drive data" section.** A commitment
  made during Google's OAuth verification for `drive.readonly`. It is copied
  **byte-identical** between every variant of the home page, and a test-style
  check of that is worth running after any edit.
- **`privacy.html`, `terms.html` and their `-fr` twins.** Legal text.
- **Prices, plan names, trial length, and the lifetime wording.** These must
  stay unambiguous. The lifetime card is the model: it gets *more* honest under
  humour, not less, saying outright that it promises "as long as PhonoLeaf
  runs" rather than "forever" because forever is not ours to promise. That is
  aimed squarely at the Voice Dream failure mode (see `COMPETITOR_SWOT.md`).
- **Any factual claim.** A joke may never smuggle back an overclaim. The
  "no spinner between you and the next sentence" line was removed on
  2026-09-02 because the app does show a "Generating audio" notice; nothing
  funny may reintroduce it.

## French is written in French

**Never translate an English joke.** The first French draft did, and it read as
English wearing French words: "Survit à votre poche", "Vous manquez d'yeux",
"Aucune salle d'attente", "courriel de rétention". None of those are things a
French speaker says.

Write the joke *in* French, which means it is usually a **different joke**:

| English | French, doing its own thing |
| --- | --- |
| "Numbers to feel quietly smug about" | "De quoi se péter les bretelles" (the Québec idiom for being pleased with yourself; French has no "smug") |
| "…as long as PhonoLeaf runs, not forever" | "« aussi longtemps que PhonoLeaf existe » et non « à vie », parce qu'une vie, ce n'est pas à nous de la promettre" (the à vie / une vie play does not exist in English) |
| "Remembers, so you do not have to" | "Elle, elle se souvient" (the emphatic pronoun, which English cannot do) |
| "the stretch of countryside where your map app gives up" | "le rang où votre GPS renonce" (`rang` is the Québec word for a rural road) |

Do **not** line the two language versions up joke for joke. That instinct is
exactly what produced the calque.

## Standing rules that apply regardless of tone

- **No em dashes, and no other LLM tells**, in any user-facing writing. Raised
  by the owner repeatedly.
- **Keep the SEO head term.** `SEO.md` §1: "turn any ebook into an audiobook"
  stays in `<title>` and `<h1>`. Personality goes in the tagline, lede and
  feature copy, never at the cost of the category phrase.
- **Lead on reliability**, with privacy as support (`SEO.md` §1).
- **Do not sugar-coat competitors** (owner, 2026-08-17 and 2026-08-31), but do
  not make claims about them that are not checked either. The comparison pages
  are the place for detail; the home page links to them.
- **Say who a criticism is about.** "Four ways apps like this let you down" and
  "Les quatre déceptions habituelles" both read as confessions. Name other apps
  explicitly.

## Status

- **The tone itself is NOT yet chosen.** Two complete pairs exist, both
  `noindex` and out of `sitemap.xml`: `home-v2` (straight) and `home-v3`
  (humour). See `TODO.md`'s website redesign section.
- The owner approved this humour **as executed in v3** on 2026-09-02. That
  approves the voice, not yet its adoption everywhere.
- **If the humour voice is adopted, the app is the larger half of the job**:
  every toast, empty state, onboarding line and Settings string, in EN and FR,
  needs the same test applied. That is also where the self-deprecation trap is
  easiest to fall back into, because error and empty states invite apology.
