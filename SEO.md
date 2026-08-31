# PhonoLeaf — SEO plan

Organic search is the highest leverage channel for a bootstrapped app: it compounds,
costs time not money, and the buyers are high intent (they are literally searching
for what we do). This plan covers keywords, the pages to build, on page and
technical SEO, and a first three month schedule. English first; French (`fr-CA`)
mirrors follow for the Québec market and are a bonus for search.

> Validate the exact search volumes later with free tools: **Google Search Console**
> (already verified for the domain) for real queries and positions, and **Google
> Keyword Planner** for volume estimates. The groupings below are by search intent,
> which matters more than raw volume at our stage.

---

## 1. Positioning for search

We win on the specific intent that the big cloud apps ignore: **listening to the
ebooks you already own, privately and on your device.** So we target "read my own
books aloud" style queries, not the generic "text to speech" head term where
Speechify and NaturalReader dominate. Our angle in every title: **turn any ebook
into an audiobook, from your own library, private and offline.**

### Revised 2026-08-30: lead with reliability, keep privacy as support

`COMPETITOR_SWOT.md` mined actual store reviews across all six competitors, and
the finding changes what the copy should lead with. The highest upvoted
complaints in that entire research set are **not** about privacy. They are about
things breaking:

- playback dying when the screen locks (three of the six, and @Voice's own
  developer says it "cannot be fixed in the app code")
- losing your reading position (Speechify: "it drops you SOMEWHERE in the
  vicinity, and that could be a couple chapters forward or backward", 270 found
  helpful)
- endless loading and failed imports (Speechify: "a book that's been scanning for
  more than 2 weeks", 276 found helpful)
- the good voice silently downgrading when the network is judged weak

**Privacy is why people approve. Reliability is why people switch.** Somebody
searching "privacy" is browsing; somebody searching "audiobook app keeps stopping
when screen turns off" is actively churning off a competitor and is the single
highest intent visitor we can get.

PhonoLeaf's architecture happens to fix all four of those, and not as a feature
choice: generating on device means there is no server round trip to fail, no
network state to misjudge, and no queue to get stuck in. So the reliability claim
is structural, which is exactly what makes it defensible in copy.

**Practical rule for every page:** keep "turn any ebook into an audiobook" as the
head term in `<title>` and `<h1>` (it is the category term people search and we
should not surrender it), and spend the lede, the first feature block, and the
meta description on reliability. Privacy moves to the supporting paragraph, where
it still does its job.

This supersedes `COMPETITORS.md`'s strategic takeaway #3 ("keep the privacy story
loud"), which is now cross referenced from that file so the two do not disagree.

## 2. Keyword map (by intent)

**Core (medium head terms, our category):**
- turn ebook into audiobook
- read ebooks aloud
- listen to ebooks
- ebook to audiobook app
- text to speech ebook reader

**Long tail (lower competition, our sweet spot, build pages for these):**
- read my epub aloud
- listen to my own ebooks
- text to speech for books I own
- turn epub into audiobook (free)
- read Google Drive ebooks aloud
- private text to speech reader
- offline text to speech app
- read my Calibre library aloud
- audiobook from my own epub
- on device text to speech (no cloud)

**By segment / use case:**
- text to speech for dyslexia
- read aloud app for dyslexia
- listen to books while commuting
- convert ebook to audio

**Reliability / failure mode (added 2026-08-30, highest intent in the whole map):**

These are what somebody types when a competitor has just failed them. Volumes are
low individually, competition is near zero because nobody writes pages targeting
their own product's failures, and intent is as high as it gets: the searcher has
already decided to leave something. Long tail phrasing matters more than exact
match here, so answer the question plainly in the copy.

- audiobook app keeps stopping when screen turns off
- text to speech stops when phone locks
- app loses my place in the book
- text to speech app forgets where I stopped
- ebook reader stuck on processing
- text to speech not working offline
- read aloud app that works without internet
- text to speech app that doesn't need wifi
- audiobook app no loading

**French (`fr-CA`) equivalents:**
- la lecture s'arrête quand l'écran s'éteint
- application qui perd ma page
- synthèse vocale sans connexion Internet

**Comparison / bottom of funnel (high converting, build pages):**
- Speechify alternative
- private Speechify alternative
- alternative to Google Play Books read aloud
- NaturalReader alternative
- ElevenReader alternative
- best text to speech app for your own books

**French (`fr-CA`):**
- écouter mes livres numériques
- lire un epub à voix haute
- transformer un epub en livre audio
- application de synthèse vocale pour livres
- lecteur epub avec voix

## 3. Site architecture (pages to create)

All of these are public marketing pages that reuse the existing branded HTML shell
(like `home.html`), are web only (NOT staged into the native app), and link to each
other and to the app. The app itself (`index.html`) sits behind sign in and is not
an SEO surface; `home.html` is the SEO homepage.

- **Home** (`home.html`): head term "turn any ebook into an audiobook."
- **Use case pages** (one tight topic each):
  - `read-ebooks-aloud.html` — "Read your ebooks aloud"
  - `epub-to-audiobook.html` — "Turn an EPUB into an audiobook"
  - `google-drive-audiobooks.html` — "Listen to your Google Drive ebooks"
  - `offline-text-to-speech.html` — "Offline, private text to speech" (publish only
    once the offline feature in `BACKLOG.md` C ships)
  - `text-to-speech-dyslexia.html` — "Read aloud for dyslexia"
- **Comparison pages** (bottom of funnel):
  - `speechify-alternative.html` (flagship, draft copy in section 6)
  - `vs-google-play-books.html`
  - `naturalreader-alternative.html`
- **Blog** (`/blog/...`, informational, compounding):
  - "How to turn any ebook into an audiobook"
  - "How to listen to your Google Drive ebooks"
  - "How to read your Calibre library aloud"
  - "Why on device text to speech is more private"
  - "The best text to speech apps for books you already own (2026)"
- **French mirrors** of the home page and the top two or three pages
  (`*-fr.html`), linked with hreflang (see section 5).

Internal linking: hub and spoke. Home links to every use case and comparison page;
each of those links back to home and to the app; blog posts link to the relevant use
case page. This spreads authority and keeps visitors moving toward the app.

## 4. On page SEO

Rules for every page:
- One clear `<title>` under about 60 characters with the target phrase near the
  front. One `<meta name="description">` under about 155 characters that earns the
  click. One `<h1>` containing the target phrase; `<h2>` for subtopics.
- Descriptive, keyword aware `alt` text on images and screenshots.
- A short FAQ block on each page (three to five real questions) with FAQ schema, to
  win rich results.
- A visible call to action to open the app on every page.

### Ready snippet: `home.html` `<head>` additions
```html
<title>PhonoLeaf: Turn any ebook into an audiobook</title>
<meta name="description" content="PhonoLeaf reads the ebooks in your Google Drive aloud in a natural voice, right on your device. Private, no cloud, background playback. Free 7 day trial.">
<link rel="canonical" href="https://phonoleaf.com/home.html">
<meta property="og:type" content="website">
<meta property="og:title" content="Turn any ebook into an audiobook — PhonoLeaf">
<meta property="og:description" content="Listen to the ebooks in your Google Drive, read in a natural on device voice. Private, offline, background playback.">
<meta property="og:url" content="https://phonoleaf.com/home.html">
<meta property="og:image" content="https://phonoleaf.com/og-image.png">
<meta name="twitter:card" content="summary_large_image">
<script type="application/ld+json">
{
  "@context": "https://schema.org",
  "@type": "SoftwareApplication",
  "name": "PhonoLeaf",
  "applicationCategory": "BookApplication",
  "operatingSystem": "Android, Web",
  "description": "PhonoLeaf turns the ebooks in your Google Drive into audiobooks, read aloud in a natural on device voice. Private, offline, background playback.",
  "url": "https://phonoleaf.com/",
  "offers": { "@type": "Offer", "price": "5.99", "priceCurrency": "USD" }
}
</script>
```
Do NOT add a fake `aggregateRating`. Add one only once there are real store reviews.

## 5. Technical SEO (GitHub Pages specifics)

**[CODE] robots.txt** at the repo root:
```
User-agent: *
Allow: /
Sitemap: https://phonoleaf.com/sitemap.xml
```

**[CODE] sitemap.xml** at the root, listing every public page with its French
alternate. Example entry pattern:
```xml
<url>
  <loc>https://phonoleaf.com/home.html</loc>
  <xhtml:link rel="alternate" hreflang="en" href="https://phonoleaf.com/home.html"/>
  <xhtml:link rel="alternate" hreflang="fr-ca" href="https://phonoleaf.com/home-fr.html"/>
  <xhtml:link rel="alternate" hreflang="x-default" href="https://phonoleaf.com/home.html"/>
</url>
```

**[CODE] hreflang tags** in the `<head>` of each EN page and its FR mirror, pointing
at each other and an `x-default`:
```html
<link rel="alternate" hreflang="en" href="https://phonoleaf.com/home.html">
<link rel="alternate" hreflang="fr-ca" href="https://phonoleaf.com/home-fr.html">
<link rel="alternate" hreflang="x-default" href="https://phonoleaf.com/home.html">
```

Other technical items:
- **[OWNER]** In Google Search Console (domain already verified), submit
  `sitemap.xml` and watch the Performance report for real queries to double down on.
- Page speed is already strong (no framework, self hosted fonts). Keep it that way.
- Every marketing page needs its own title, description, canonical, and og tags.
- Add these pages to `scripts/stage-www.js` only if you want them in the app; the
  marketing and blog pages should stay web only, like `home.html` today.

## 6. Flagship comparison page (draft copy): `speechify-alternative.html`

Title: `A private Speechify alternative for your own ebooks — PhonoLeaf`
Meta description: `Prefer to keep your reading private and on your device? PhonoLeaf reads the ebooks you already own aloud, offline, for less. A calm Speechify alternative.`
H1: `The private, on device Speechify alternative for the books you already own`

Body outline (write in full, honest, nominative use of the competitor name only):
- One paragraph: Speechify is a great general reader for documents, articles and
  the web. PhonoLeaf is for a narrower job done well: listening to the ebooks in
  your own library, privately and on your device.
- A short comparison table: what runs on device vs the cloud, whether your text is
  uploaded, price, and whether it is built around your own book library. Use the
  verified prices from `STORE_LISTINGS.md` and the business plan (Speechify about
  $139 a year; PhonoLeaf $5.99 a month or $49.99 a year).
- "Choose PhonoLeaf if" list: reliability first, then that you mainly listen to
  your own ebooks, then price. **No "Choose [competitor] if" list.** Owner
  instruction 2026-08-17, reaffirmed 2026-08-31: a comparison page does not
  concede ground to the competitor, and carries no table row where they win
  outright. This supersedes the earlier "be fair" framing that stood here.
- FAQ with schema: Is PhonoLeaf really private? Does it work offline? Can it read my
  own epub files? How much does it cost?
- Call to action to open the app and start the free trial.

The same template makes `vs-google-play-books.html` (angle: better voices, your
whole Drive library across ecosystems, not locked to one store) and
`naturalreader-alternative.html` (angle: private, on device, built around your
books).

### Aim each page at THAT competitor's loudest complaint (added 2026-08-31)

The four failure modes in §1 are aggregate findings across all six
competitors. They are the right frame for `home.html`, but on a comparison
page the aggregate is weaker than the specific, and it can even be wrong:
leading a Voice Dream page on background playback attacks something Voice
Dream genuinely does well and says so, while ignoring the complaint that is
exactly our strength. Take the lead from that competitor's own
`COMPETITOR_SWOT.md` "Recurring complaints" section:

| Page | Lead on |
| --- | --- |
| `speechify-alternative` | Endless scanning and the silent downgrade to a worse voice when it judges the network weak |
| `naturalreader-alternative` | Metering the good voices by the day, even on paid plans |
| `play-books-alternative` | Read-aloud controls so crude you cannot properly pause or choose where to start |
| `elevenreader-alternative` | Downloads expiring at 60 days, no export, and total network dependence |
| `voice-aloud-alternative` | Ads, and playback dying at screen lock, which its own developer says he cannot fix |
| `voice-dream-alternative` | Voices its own reviewers now call stilted and robotic |

**Do not lead a Voice Dream or @Voice page on offline or on-device.** Both
genuinely have it and say so, so that angle attacks a strength. Applied
2026-08-31; before that, three of the six led on the generic message.

## 7. First three months

- **Month 1:** optimize `home.html` (section 4 snippet), add `robots.txt`,
  `sitemap.xml`, and schema, submit the sitemap in Search Console, and publish three
  use case pages (`read-ebooks-aloud`, `epub-to-audiobook`,
  `google-drive-audiobooks`).
- **Month 2:** publish `speechify-alternative` and one more comparison page, plus two
  blog posts. Add internal links from the blog to the use case pages.
- **Month 3:** two or three more blog posts, French mirrors of the home page and the
  top two pages with hreflang, and a first refresh of titles and descriptions based
  on the Search Console query data.

## 8. Measurement
- Google Search Console: impressions, clicks, average position, and the actual
  queries bringing people in. Iterate titles and content toward the queries that are
  close to ranking (positions 5 to 20).
- Track a small set of target phrases monthly. Celebrate page one, then push CTR
  with better titles and descriptions.

## 9. Code task checklist (for Claude Code)

**Added 2026-08-30, from the reliability revision in §1:**
- [x] Rewrite `home.html` / `home-fr.html` to lead with reliability, keeping the
      head term in `<title>`/`<h1>`. Done 2026-08-30.
- [x] Rewrite the comparison page pairs' hero copy on the same principle.
      Done 2026-08-31, all **six** pairs (12 files). Note the count: this
      line said five, but there are six (elevenreader, naturalreader,
      play-books, speechify, voice-aloud, voice-dream). Head term kept in
      `<title>`/`<h1>`; the h1 qualifier, tagline, lede and both meta
      descriptions now lead on reliability. Descriptions were retargeted to
      133-146 chars after a first pass ran 196-266, which Google truncates
      around 155 and would have cut off the new lead.
- [x] **DECIDED 2026-08-31: no "choose them if" sections, ever.** The owner
      reaffirmed the 2026-08-17 instruction: do not sugar-coat competitors
      and do not concede ground to them. §6 has been corrected at the source
      so the two files no longer disagree. The pages already complied, so
      nothing had to be removed. Two record corrections made at the same
      time: there are six pairs, not five, and `voice-dream-alternative`
      never had a fairness section despite this file once saying it did.
- [x] **Dedicated reliability-cluster page. Done 2026-08-31.**
      `audiobook-app-that-doesnt-stop.html` plus its French twin. One section
      per failure mode from §2, each naming why it happens in other apps before
      saying why it does not happen here, then a section arguing the whole thing
      is structural rather than a feature that could be traded away later.
      Five FAQ entries with schema, targeting the long-tail question phrasings
      directly. Wired into `sitemap.xml` with full hreflang blocks and linked
      from both home pages, per the standing EN/FR pairing rule.
- [ ] Add `robots.txt` and `sitemap.xml` at the repo root (section 5).
- [ ] Add the `<head>` SEO block and JSON-LD to `home.html` (section 4).
- [ ] Create the use case and comparison page shells from the `home.html` template,
      web only, linked in the footer and to the app, each with its own title, meta
      description, canonical, og tags, and an FAQ with schema.
- [ ] Add hreflang tags between each EN page and its FR mirror plus `x-default`.
- [ ] Keep the offline related page and any "offline" wording unpublished until the
      offline feature (`BACKLOG.md` C) ships.
- [ ] Do not stage the marketing and blog pages into the native app.
