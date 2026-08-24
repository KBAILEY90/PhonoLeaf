# Competitor research and SWOT

**Researched: 2026-08-23.** Standalone research document. Nothing else in the repo
was modified.

Scope: the six competitors already covered by the `*-alternative.html` comparison
pages in this repo. For each: current pricing (checked against what those pages
claim), store review themes drawn from actual reviews rather than assumption, and
positioning notes relevant to PhonoLeaf's stated differentiators (on-device voice,
offline, background/lock-screen playback, no metering, your own files).

**How the review evidence was gathered.** Google Play ratings, download counts and
review text were read directly off each app's live Play listing (the "See all
reviews" panel, sorted by Most relevant). App Store ratings came from Apple's
public lookup API, and App Store review text from Apple's public customer-review
RSS feed sorted by most recent. Quotes below are verbatim from those sources
unless marked as paraphrase. Trustpilot was not usable (bot challenge), so no
Trustpilot figure is cited as fact anywhere in this document.

**A caveat that matters for reading the numbers.** Aggregate star ratings and
written-review sentiment diverge sharply in this category, because in-app rating
prompts collect taps from happy users while written reviews skew to the annoyed.
Speechify is the clearest case: 4.66 on the App Store across 516,932 ratings, but
its most-recent written App Store reviews and its 3.9 Play rating tell a very
different story. Treat the star average as a measure of reach and the written
reviews as a measure of where the product actually hurts.

---

## 1. Speechify

### Pricing (verified 2026-08-23)

| Plan | Price |
|---|---|
| Free | $0. 10 voices described by Speechify itself as "robotic sounding", listening capped at 1.5x |
| Premium | **$29/month**, or **$139/year** |
| Audiobooks add-on | $9.99/month (separate from Premium) |
| Studio (creator product) | Free / $19 / $49 per user per month |

Sources: [speechify.com/pricing](https://speechify.com/pricing/) (free and $29/mo
tiers read directly off the page); annual $139 figure corroborated across
[TextToLab](https://texttolab.com/blog/speechify-pricing) and
[fluxnote.io](https://fluxnote.io/guides/speechify-pricing-guide-2026), and by a
Play review quoting an actual charge of $138 and an App Store review quoting
$139.99.

**Against the repo's `speechify-alternative.html`:** the page claims "~$29/mo or
~$139/yr". **Still accurate.** No change needed.

### Store presence

- Google Play (`com.cliffweitzman.speechify2`): **3.9 stars, 280K reviews, 10M+ downloads**
- App Store (id 1209815023): **4.66 stars, 516,932 ratings**

The 3.9 vs 4.66 split is itself a finding. Android is where Speechify is weakest,
and Android is PhonoLeaf's only shipped native platform.

### Recurring complaints

**Cloud round-trip failures dominate.** This is the single largest theme, and it is
structural rather than cosmetic:

- "Half the time it won't open my content, it says processing your file and never completes it. When it does, sections are frequently skipped or read in the wrong order."
- "I have a book that's been scanning for more than 2 weeks." (276 found helpful)
- "absolutely infuriating process, with the app frequently getting stuck on an endless loading cycle."
- "Currently writing this while waiting for it to load two paragraphs." (App Store, 1 star)
- "in the past half hour of using it, the app has: failed to load an entire page, skipped sections, crashed four times." (App Store, 1 star)

**Silent downgrade to the robotic voice when the network is judged weak.** Directly
relevant to PhonoLeaf's on-device pitch:

- "at random times, it would switch from the nice AI voices to a generic voice which I found grating to listen to. It seemed like the app thought my internet wasn't strong enough despite having 5G!"

**Loses your place across devices.** The highest-value complaint for PhonoLeaf:

- "though you supposedly can switch listening between devices, it's not feasible, because it doesn't remember your place. On opening the file, it drops you SOMEWHERE in the vicinity, and that could be a couple chapters forward or backward." (270 found helpful)

**Billing and cancellation.** Very high volume, both stores:

- "it was a terribly confusing billing system in which I accidentally got charged for a full year's subscription... Overall very scammy practices."
- "I tried the free trial and went to go cancel it... I couldn't find the subscription anywhere on my Google account... of course at midnight it charges me."
- "They give you three days free but make it very hard to cancel the subscription within trial." (App Store)
- "They signed me up for premium and took 138 for it without approval." (App Store)

**Stacked paywalls.** Premium does not unlock the catalogue:

- "it gives you 5 files, and locks everything down until you pay another $100 to upgrade to premium!"
- "once I paid the $20 for the huge library access, I find out it is all free access books"
- "There is a word limit on the premium, which is not advertised."

**Google Drive integration specifically.** Worth noting given PhonoLeaf's own Drive
dependency:

- "It is supposed to link to Google Drive, but only does so maybe one try in 10." (202 found helpful)

**Onboarding friction and an aggressive upsell funnel:**

- "The process of creating an account is too long. I shouldn't have to fill out a 20-questionnaire." (App Store)
- "It waste your time asking personal questions then gives you a trial. If you refuse it asks again at a lower price, if you refuse once more it asks a third time."

**Features removed after purchase:**

- "Since I first bought the subscription a year ago they've removed offline downloading and increased price." (App Store, 1 star)

**Offline is quota-metered.** Independent reporting says offline downloads count
against Premium's monthly word quota, so offline is not unlimited even when paid
for ([Speed Reading Lounge](https://www.speedreadinglounge.com/speechify-review),
[TextToLab](https://texttolab.com/blog/speechify-pricing)).

### Recurring praise

- Voice naturalness on Premium, and very high speed listening (up to 4.5x).
- Accessibility. The founder is dyslexic and the app is genuinely loved by that audience: "With ADHD and dyslexia this has been my lifelong dream... the voices sound realistic." "I am 11 years old and I read fast and it's too hard for me to read. This app really helps."
- Study use: "I'm using speechify to listen to text books as I follow along. It's truly helpful."

### Positioning notes

- **Cloud by default, but no longer exclusively.** On 2025-12-18 Speechify announced [on-device iOS AI voices](https://speechify.com/news/speechify-launches-on-device-ios-ai-voices-for-offline-reading/) with "100 percent local inference, without relying on an internet connection", built on Apple's Core ML. **iOS only. Android is not mentioned.** This is the most important single finding in this document and is carried into Threats below.
- Requires an account, and the trial requires a card. Multiple reviewers report no straightforward path to the free plan without going through the trial flow first.
- Works with the user's own files (upload, Drive, Dropbox, OneDrive, camera scan) *and* sells its own catalogue as a separate purchase.

---

## 2. NaturalReader

### Pricing (verified 2026-08-23)

Personal plans, from
[help.naturalreaders.com/en/articles/8854700](https://help.naturalreaders.com/en/articles/8854700-plans-pricing-personal-version):

| Plan | Monthly | Yearly | Notable limits |
|---|---|---|---|
| Lite | $13.90 | **$79.00** | Lite voices only, 1M mp3 chars/month |
| Plus | $20.90 | **$119.00** | Plus/cloned voices metered at **500K characters per day** |
| Pro | $25.90 | **$159.00** | Adds Pro voices, still 500K chars/day |

Separate, more expensive Commercial plans exist. The older $9.99 "Premium" tier
has been retired and renamed Lite.

**Against the repo's `naturalreader-alternative.html`:** the page claims "$79 to
$159/yr". **Still accurate.**

One nuance the page slightly overstates: it says "Works offline: No / Requires a
connection to generate audio". True for live playback, but paid subscribers can
export MP3 files (capped at 1M characters per month, and free voices cannot be
exported at all) and listen to those offline
([NaturalReader help](https://help.naturalreaders.com/en/articles/11543218-working-with-text-and-audio-personal-version)).
Worth a footnote so the claim stays defensible under scrutiny.

### Store presence

- Google Play (`com.naturalsoft.personalweb`): **4.3 stars, 58.7K reviews, 5M+ downloads**
- App Store (id 1487572960): **4.55 stars, 8,836 ratings**

### Recurring complaints

**Metering on the voices people actually want, even after paying.** The loudest theme:

- "Even if you pay for plus plan...they limit the time on the proper voices, while leaving horrible garbled voices 'unlimited'. If you're using it to listen to books, you hesitate to stop and go back for notes...because it counts towards the plus limit." (29 found helpful)
- "It would be better if subscription wasn't overpriced... $20/month is absurd" (App Store, 2 stars)

**Free voices are poor:**

- "Free AI voices are awful. They pronounce things wrong, have awkward long pauses in the wrong places... It also won't read anything in parentheses, so if you've got text with bracketed thoughts, equations, or notes, they get skipped entirely."
- "Free version is junk... robot voice quality" (App Store)

**Does not remember your settings or your place:**

- "I bought a year's membership for this app. They require an creation of an account to use it... you'd think that one of the benefits of an account would be saving your preferences (voice, last known location, filters etc). No. It starts new each time." (23 found helpful)

**Account required before you can try anything:**

- "Skipped…. Requires account before testing functionality" (App Store, 3 stars)
- "Nope. Refuses account requirement without prior app testing" (App Store, 2 stars)

**Things bought get taken away.** Two separate patterns:

- "Hate subscriptions: Company discontinued one-time voice purchases" (App Store)
- "genuinely why: Removed favorite voices; reduced selection dramatically" (App Store)
- "I purchased the license in 2008... Their advertising 'valid in perpetuity' is false and misleading. Every few years they do a system upgrade and older license are not working anymore." (48 found helpful)

**Pauses at page boundaries.** The developer confirmed the cause in a public reply,
and the reason is architectural (server-side, per-page processing):

- User: "the long pauses especially when pages change, can be distracting and disrupt the flow of a sentence."
- Developer: "It pauses at the page change because it processes the characters by page."

**A user raising privacy unprompted:**

- "it's not much different from what was available without AI five years ago without all the privacy and data mining risks."

**Also:** no DRM-protected books; crashes on long documents; cannot use its voices system-wide.

### Recurring praise

- PDF bookmark navigation, and generally strong handling of academic/PDF material.
- The pronunciation editor, repeatedly singled out.
- Format breadth and the "share to app" flow: "Multiple ways to open a variety of materials... 9/10 times I can open what I'm trying to."
- The free tier is seen as more usable than Speechify's: "The free features (voice selection, capacity limits, etc) still allow you to get the best experience without constantly forcing you to [pay]."
- Responsive, specific developer replies on Play.

### Positioning notes

- Cloud generation. No on-device claim anywhere.
- Account mandatory before any use, which multiple reviewers cite as the reason they never tried it.
- Works with the user's own files, but the licence restricts generated audio to personal use only.

---

## 3. Google Play Books (read aloud)

### Pricing

Free. The app is preinstalled on effectively all Android devices. Revenue is book
sales, not the reading feature.

### Store presence

- Google Play (`com.google.android.apps.books`): **4.6 stars, 3.21M reviews, 1B+ downloads**

Note that these numbers cover the whole store-and-reader app, not the read-aloud
feature specifically. They indicate distribution reach, not read-aloud quality.

### What Google itself documents

From
[support.google.com/googleplay/answer/11938821](https://support.google.com/googleplay/answer/11938821?hl=en&co=GENIE.Platform%3DAndroid),
quoted verbatim:

- "Not all books have this option. Publishers choose whether Read Aloud is available for their books."
- On the natural reading voice: "You can only use this mode when your device is connected to the internet. To use Read Aloud when your device is offline, turn off the natural reading voice."

Separately, Play Books accepts uploads of unprotected EPUB and PDF, and Read Aloud
**does** work on those uploads
([DAISY Consortium overview](https://daisy.org/guidance/info-help/guidance-training/reading-systems/google-play-books-app-overview/)).

### Two corrections needed to `play-books-alternative.html`

This is the one comparison page in the repo with claims that would not survive a
determined reader, and both are worth fixing because the true differentiators are
strong enough without them.

1. The page says **"Works with: Only books bought in that store"**. This is not
   correct. Users can upload their own DRM-free EPUB and PDF files to Play Books
   and Read Aloud works on them. The publisher-can-disable-it restriction applies
   to purchased store titles, not to uploads.
2. The page says **"Works offline: No, requires an internet connection"**. This is
   only true of the *natural* voice. Google explicitly documents that turning the
   natural voice off lets Read Aloud run offline on the device's basic TTS voice.

The genuinely defensible differences against Play Books, all supported above and
below, are: the good voice requires a connection; playback does not survive the
screen going off; there is no connected-folder auto-sync (uploads are manual, one
file at a time); and the reading controls are poor.

### Recurring complaints (read-aloud specific)

From the Play listing:

- "the audio reader function is awful, the voice sucks, the rhythm of reading sucks, turning pages messes the system up, you can't really choose where to start the reading, you can't pause (only turn it off)"
- "text to voice needs more voice and speed options"

Playback dying in the background is a persistent, unresolved complaint. A Google
Play Community thread titled ["Read aloud function stops after a while when I use
other apps"](https://support.google.com/googleplay/thread/285451665/read-aloud-function-stops-after-a-while-when-i-use-other-apps?hl=en)
is now locked, with the "recommended answer" from a Product Expert being generic
advice about storage and battery rather than a fix, and Google's own related-content
sidebar on that page links a thread titled "Book Play Text-to-Speech Is Almost
Useless". Multiple parallel threads exist for the same symptom. Reporting on the
feature also notes that read aloud stops when the screen turns off, so the user
has to keep the screen on and drain the battery
([Speed Reading Lounge and related coverage](https://www.speedreadinglounge.com/)).

Non-read-aloud complaints worth knowing: store-first UI ("it's always 'greeting' me
with 'here! buy this!'"), and upload management regressions ("the option to
permanently delete uploaded epub files is gone").

### Recurring praise

Reliability of the reading app itself, free, and already installed. That is the
whole value proposition, and it is a real one.

### Positioning notes

- The high-quality voice is a Google cloud service. No on-device natural voice.
- Works with your own files, but only via manual per-file upload into Google's locker.
- Free and preinstalled, which makes it the default "good enough" substitute PhonoLeaf must beat on quality, not on access.

---

## 4. ElevenReader (ElevenLabs)

### Pricing (verified 2026-08-23)

From [elevenreader.io/pricing](https://elevenreader.io/pricing):

| Plan | Price | Limits |
|---|---|---|
| Free | $0 | **10 hours** of text-to-audio per month. No offline downloads. No premium audiobooks. |
| Ultra | **$11/month** or **$99/year** | "Unlimited" imports capped at 24 hours of generated audio per calendar day; 20 hours/month of premium titles; offline downloads capped at 20 hours per day; **downloads retained for up to 60 days**; audio cannot be exported from the app |

**Against the repo's `elevenreader-alternative.html`:** the page claims "$11/mo or
$99/yr", "Free plan: 10 hours per month", "Works offline: only pre-downloaded
audio". **All still accurate.** The page can be sharpened: the downloads expire
after roughly 60 days and cannot be exported, which is a stronger contrast than
"only pre-downloaded audio" conveys.

### Store presence

- Google Play (`io.elevenlabs.readerapp`): **4.3 stars, 65.5K reviews, 1M+ downloads**
- App Store (id 6479373050): **4.72 stars, 9,895 ratings**

### Recurring complaints

**Downloads expire, and you never own the audio:**

- "Doesn't allow proper downloads. Neat idea, and good voices, but there are too many restrictions on the audio outputs you generate. The file can be streamed, but not saved and transferred. And it is automatically deleted after a period of time. So, you would need to recreate the file every 90 days if you wanted to keep a copy permanently accessible in the app."

**Total network dependence:**

- "if you lose network/Internet access the entire app stalls and become unusable."

**Background and lock-screen playback is broken in a specific, infuriating way:**

- "Background playback FRUSTRATING!!! Whenever I pause a book with the screen off for any length of time (even a couple minutes), the app will close out of the book and forget my settings (playback speed). Pressing play will do nothing. I have to unlock my phone, open the app, select the book, press play, fix the playback speed. I pause my book hundreds of times per day, which makes this app almost unusable."
- "Does not save place: Cannot use for books requiring multiple sessions as progress isn't saved." (App Store)
- "the app does not keep your place well."

**Metering introduced abruptly, and users noticed.** The single most-upvoted review
found (189 helpful):

- "it's disappointing to see this app suddenly now charging by hours with no warning. only 2hrs/week free and the cheapest plan is only 30hrs/month and the unlimited is double the cost of that... there was absolutely no warning and staff didn't know when it went into effect either and are giving conflicting information on how the hours will actually work, it's all been very sloppy."

**Metering economics do not work for real book listening:**

- "I'm not paying $10 USD/month for 30min per day. (I listen in 2x speed) I'd pay if I could listen 30h per month (2x speed) or 60h (1x)."

**Account terminated right after an annual purchase** (141 helpful):

- "They deleted my account with absolutely no notice or explanation just days after I purchased an annual subscription."

**"Not actually free" is a large App Store complaint cluster,** driven by the
advertising: "Immediate Paywall", "Free is a lie", "Not Free as Advertised",
"Heard was free but is not", "Not remotely free".

**Also:** OCR errors that cannot be corrected because the text is not editable; the
pronunciation editor reportedly not working; UI churn between updates; playback
skipping and losing progress; ads introduced into a paid-tier app.

### Recurring praise

**Voice quality is the best in the set,** and this is unambiguous in the reviews:

- "The voice generation is so clear and natural even on a headset it sounds like a real person reading to me. I actually paid for the subscription after just a few minutes!" (52 helpful, from a resident doctor listening while driving)
- "Definitely the best selection of German voices I've found so far"
- "For brazilian portuguese, your app is 100x better."

**It handles page furniture better than competitors:**

- "Every other app I have ever tried would read everything that stood in the way of the next page, paragraph etc...and it made the experience so damn annoying. This app reads the text ahead of time and simply reads the article or book the way you'd read it."

**Price cuts have moved sentiment,** which matters for PhonoLeaf's price positioning:

- "Since the price changes this app has become much more affordable and is definitely the best app I've tried for reading text aloud in a natural sounding AI voice!"
- "Price was lowered so I went and grabbed a subscription. Honestly, this will probably be a permanent one I think."

### Positioning notes

- Pure cloud. Everything is generated server-side by ElevenLabs. No on-device claim, and none is plausible given the model sizes involved.
- Account required. Free tier is metered by hours, so even non-payers are inside the metering system from day one.
- Works with the user's own files (imports), and also sells a 200K+ title catalogue on top.

---

## 5. @Voice Aloud Reader (Hyperionics)

**Naming note:** the app is now listed on Play as **"@Voice: Text to Speech
Reader"**, not "@Voice Aloud Reader". The repo's `voice-aloud-alternative.html`
uses the older name. Also note there is an unrelated iOS app called "Voice Aloud
Reader" by a different developer (Marcin Olawski, App Store id 1446876360). The
Hyperionics app is Android only, confirmed by absence from the App Store and by
[hyperionics.com/atVoice](https://hyperionics.com/atVoice/) stating it runs on
"Android phones, tablets, emulators, and compatible Android-based devices".

### Pricing (verified 2026-08-23)

- Free, ad-supported.
- **Premium: $15.00 USD, one time**, direct from
  [hyperionics.com/atVoice/pricing.asp](https://hyperionics.com/atVoice/pricing.asp),
  which states it is "a permanent license purchase, not a subscription, with no
  monthly or yearly billing". Reviewers quote in-app Play prices around $9.99 to
  $16, so the Play IAP price appears to differ from the direct price and to vary.

**Against `voice-aloud-alternative.html`:** the page's claims (Android only,
device TTS voice) hold up, with one caveat below.

### Store presence

- Google Play (`com.hyperionics.avar`): **4.3 stars, 137K reviews, 10M+ downloads**, last updated **2026-08-22** (actively maintained)
- Data safety declaration: "No data collected", shares only device IDs (for ads)

### This is the most direct competitive threat, and its own store listing says why

Verbatim from the current Play description:

> "No account required. No subscription. No data collection. A one-time Premium
> purchase removes ads and unlocks a home-screen widget and direct web-link
> handling."

That is close to PhonoLeaf's positioning statement, already shipped, to 10M+
Android installs, for $15 once.

It also already reads more formats than PhonoLeaf: "PDFs, EPUB and AZW3 eBooks,
Word documents, HTML files, and plain text", plus "Legally open and read eBooks
protected with LCP DRM technology". It records to MP3/OGG/WAV, has a pronunciation
rules system, per-character dialog voices, OpenDyslexic, and word and sentence
highlighting.

**And users are already bolting modern neural voices onto it.** A March 2026
review, which is effectively a description of someone hand-building PhonoLeaf out
of @Voice:

> "I ended up deleting my purchased Natural Reader app after finding this gem! I
> LOVE that it can be used offline and can be used directly from files! Integrating
> new TTS voices, including ones from Piper, was seamless. No garbling, no limits,
> no problems... Love it so much I bought the licensed version, which was cheap as
> chips."

Piper is one of the two engines PhonoLeaf ships. The caveat for the comparison
page: describing @Voice as using "your phone's built-in text-to-speech voice" is
true by default but understates that users can install third-party engines,
including the same neural models PhonoLeaf uses.

### Recurring complaints

**Ads in the free version have become the top rating-drop cause,** and the
complaints describe genuinely dark patterns:

- "the ads are becoming unmanageable. There's fake x's that appear and when you tap them it forces other apps to open. There's a button that forces you to visit play store to get out of the ad." (41 found helpful)
- "I love this app, but the commercials have gotten out of hand lately. I would happily buy it, but $16 will take months of surveys to save up... I have had it for a few years and the commercials were never this excessive."

**Playback stops when the screen goes off, and the developer says he cannot fix
it.** This is the single most exploitable gap in the whole competitive set. Public
developer reply on Play:

> "If you mean that reading stops when the screen is off, please read
> https://hyperionics.com/atVoice/stops.html - follow all the links there for your
> phone type and implement settings they propose. **It is something that cannot be
> fixed in the app code, the user must change the system settings.**"

PhonoLeaf solved exactly this with a foreground service plus a partial CPU wake
lock, and it is device-verified in this repo.

**UI and setup friction:**

- "the file explorer is terrible... The UI needs a lot of polish"
- "whenever I press play, audio doesn't start, and a menu pops up saying 'select how to manage voices:'... the Voice manager for some reason says I don't have the English voice installed" (46 found helpful)
- "UI is also a bit counterintuitive And confusing"

**Voice quality and pronunciation,** the recurring cost of relying on system TTS:

- "the voice reader is going high pitch and lower pitch every other sentence"
- "USA pronounces that add 'gov lieutenant governor'. Just say lieutenant!!!"
- "The reader keeps skipping words while reading."

**It now has an online voice path too, with a brittle fallback:**

- "if you even get close to losing data connection for a second, it says error 42, connection lost, switching to offline voice model."

### Recurring praise

- Offline, local files, no subscription. Cited over and over as the reason people prefer it.
- "I've been using this app for a long time. it's simple, does what I need it to do, no annoying subscriptions."
- Depth of customisation: IPA/X-SAMPA pronunciation via SSML, per-sentence bookmarks, content extraction that strips headers/footers/ads.
- One-time purchase framed as good value: "if i knew the paid version was a one time purchase I'd take it instantly".

### Positioning notes

- Android only, which is why PhonoLeaf's Android-first strategy collides with it directly and PhonoLeaf's eventual iOS plan does not.
- No account, no subscription, no data collection. Genuinely, not as marketing.
- Works entirely with the user's own files. No catalogue, no upsell beyond the $15 licence.

---

## 6. Voice Dream Reader

### Pricing and platforms (verified 2026-08-23)

- **iOS and macOS only.** No Android, no web.
- App Store listing name is now **"Voice Dream - Natural Reader"** (seller: Voice Dream LLC), id 496177674, last updated 2026-08-18.
- Its own App Store description states: **"A subscription is required to access the app. A free trial is offered to new users."**
- Price: reviewers report auto-renewal at **$79.99/year** ("Renewed automatically for a year at 79.99") and a trial that is **3 days**, not 7 ("I was offered a 7-day trial...mysteriously changed to 3-days"; "The trial period is only 3 days"). A separate Mac-only annual tier at $49.99 has been reported. Legacy one-time purchasers retain access to the features they already had.

**Against `voice-dream-alternative.html`:** "$79.99/yr" and "iOS and Mac" are both
**still accurate**. Two things should be added: a subscription is now required for
new users at all, and the trial is 3 days in practice.

### The 2024 subscription reversal, which is the most instructive event in this set

Voice Dream announced on its own site that it was moving existing one-time
purchasers to a subscription on 2024-05-01, then reversed it after backlash
([voicedream.com/subscription-pricing-update](https://www.voicedream.com/subscription-pricing-update/)),
quoted verbatim:

> "Following our recent announcement to transition Voice Dream to a subscription,
> we received an overwhelming response from thousands in our community. Your
> feedback, along with the impactful stories shared about Voice Dream being a
> pivotal part of your daily lives, has led us to reverse this change. We will
> continue to provide access to the app's existing features at no additional cost."

This audience will pay once, and will organise against being converted.

### Store presence

- App Store: **4.49 stars, 14,175 ratings**. Carries an Apple Editors' Choice blurb.

### Voice Dream is the closest positional competitor, and already claims PhonoLeaf's differentiators

Verbatim from its own App Store description:

- **"All voices work offline and play in the background even with the screen locked."**
- On its document scanner: **"Works entirely on device: No need for internet and your data stays private."**

So on-device, offline, background/lock-screen and privacy are all claimed. What it
does *not* have is modern neural voice quality (its 200+ premium voices come from
Acapela, NeoSpeech and Ivona, all older-generation engines), Android, or web. The
comparison page should not lean on offline or background playback as the wedge
against this specific competitor. Voice quality, platform, and price are the real
gaps.

### Recurring complaints

**Voice quality is now the biggest one, and it is the exact gap PhonoLeaf fills:**

- "Not natural: This is so far from being a natural reader...had it read in a stilted robotic voice." (1 star)
- "Voices are too robotic, please change it!" (4 stars)
- "its ability to sound human is limited compared to some of the latest voice readers." (4 stars)
- "Overpriced ai voices. Only one tolerable." (2 stars)

**Paid-for things disappearing.** A direct warning for PhonoLeaf's $129 lifetime tier:

- "Early adopter- punished: I paid for lifetime access prior to the integration of AI and now the voice I paid for does not work." (1 star)
- "Scam: They removed voices that I payed for in the legacy version. New version voices are awful." (1 star)

**No way to evaluate before paying:**

- "No sample: Very expensive app that doesnt allow you to test it first. I'm not going to pay 70 dollars for something." (1 star)
- "No trial? I will not even consider buying a Read Aloud without knowing what voices are included or available." (1 star)
- "Why do I need a subscription to unpause it??" (1 star)

**Regressions and reliability:**

- "When reading an EPUB file aloud, it often keeps looping the same section of content over and over."
- "Was reliable for years but no longer... has now deteriorated and lost my entire library."
- "Using Voice Dream makes my phone heat up intensely and eats through my battery very quickly."
- "the integrated kindle app stopped working"

**Interface:** "Horrible Interface: The interface is clunky and requires a ton of
tapping around to do even basic functions", plus repeated confusion about voice
selection.

**Billing:** "Shadow Charging: I was charged even though when I did free trial; I
did not have a charge card attached to the account."

### Recurring praise

**It is genuinely beloved by the accessibility community,** more than anything else
in this set:

- "This program on my phone got me through a very rigorous graduate level program with honors."
- "Being legally blind makes it next to impossible to read a regular book, but Voice Dream makes it totally possible!"
- "I have Asperger's...I need somebody to read something to me to understand what's being read fully."
- "In the five years that I have been using Voice Dream I have managed to increase my reading consumption by more than 20-fold."

**Academic workflow beats Speechify,** per a direct comparison review:

- "Beats Speechify for academics! Voice dream's bookmark and highlight feature allows you to tab sentences...and lets you export only your tabbed text."

**Pronunciation dictionary,** again singled out as the standout feature.

### Positioning notes

- On-device and offline by design, and says so.
- Subscription required for new users, no free tier, no meaningful sample.
- Works with the user's own files. No catalogue.

---

## Cross-competitor summary

| | PhonoLeaf | Speechify | NaturalReader | Play Books | ElevenReader | @Voice | Voice Dream |
|---|---|---|---|---|---|---|---|
| Annual price | **$49.99** | $139 | $79 to $159 | Free | $99 | **$15 once** | $79.99 |
| Voice runs on device | **Yes** | iOS only (since 12/2025) | No | No (natural voice) | No | Yes (system TTS) | Yes |
| Modern neural voice | **Yes** | Yes (cloud) | Yes (cloud) | Yes (cloud) | **Best in set** | Depends on user's engine | No (older engines) |
| Metered / quota | **No** | Yes (word quota) | Yes (500K chars/day) | No | Yes (hours) | No | No |
| Offline generation | **Yes** | iOS only | No (MP3 export only) | Basic voice only | No | Yes | Yes |
| Background / screen-off | **Yes** | Partial | Partial | **Broken** | **Broken** | **Broken (dev says unfixable)** | Yes |
| Android | **Yes** | Yes | Yes | Yes | Yes | Yes | **No** |
| iOS | **No** | Yes | Yes | Yes | Yes | **No** | Yes |
| Web | **Yes** | Yes | Yes | Yes | Yes | No | No |
| Formats | EPUB only | Broad + OCR | Broad + OCR | EPUB, PDF | Broad + OCR | **Broadest, incl. LCP DRM** | Broad |
| Account required | Google sign-in | Yes | Yes | Google | Yes | **No** | Yes |
| Own catalogue upsell | No | Yes | No | Yes (its store) | Yes | No | No |
| Play rating | n/a | **3.9** (280K) | 4.3 (58.7K) | 4.6 (3.21M) | 4.3 (65.5K) | 4.3 (137K) | n/a |
| App Store rating | n/a | 4.66 (517K) | 4.55 (8.8K) | n/a | 4.72 (9.9K) | n/a | 4.49 (14.2K) |

---

# SWOT for PhonoLeaf

Every bullet traces to something found above. Nothing here is a generic market claim.

## Strengths

- **The only product in the set that combines on-device neural voice, offline generation, Android, and working screen-off playback.** Speechify's on-device voices are iOS only as of 2025-12-18. Voice Dream has on-device, offline and lock-screen playback but is iOS/Mac only and its voices are Acapela/NeoSpeech/Ivona era, which its own reviewers call "stilted robotic". @Voice has offline and Android but depends on whatever TTS engine the user installs and cannot keep playing with the screen off. Nobody else occupies the intersection.

- **Screen-off playback is a known, unsolved, actively-complained-about failure in three of the six.** @Voice's developer publicly states it "cannot be fixed in the app code, the user must change the system settings". ElevenReader users describe losing the book and their playback speed after a two-minute pause with the screen off. Play Books has locked forum threads about it with no fix. PhonoLeaf's foreground service plus partial wake lock is device-verified in this repo. This is a demoable win, not a spec-sheet one.

- **No metering, and no way to introduce it.** Speechify's offline downloads count against a monthly word quota. NaturalReader caps its good voices at 500K characters per day even on the $159/year plan. ElevenReader charges by the hour and deletes downloads after roughly 60 days. On-device generation has no marginal cost, so there is no unit to meter, and that is a structural property rather than a promise.

- **Cheapest subscription in the set by a wide margin.** $49.99/year against $139 (Speechify), $79 to $159 (NaturalReader), $79.99 (Voice Dream), $99 (ElevenReader).

- **Network independence reads as reliability, not just privacy, and reliability is what reviewers actually complain about.** "if you lose network/Internet access the entire app stalls and become unusable" (ElevenReader). "it would switch from the nice AI voices to a generic voice... It seemed like the app thought my internet wasn't strong enough despite having 5G" (Speechify). "error 42, connection lost, switching to offline voice model" (@Voice). PhonoLeaf has no path that can produce any of these.

- **No catalogue to upsell, in a category where the upsells are actively resented.** "it gives you 5 files, and locks everything down until you pay another $100" and "once I paid the $20 for the huge library access, I find out it is all free access books" are both Speechify. ElevenReader sells a 200K-title catalogue on top of a metered subscription. PhonoLeaf sells one thing.

- **Connect-a-folder-and-new-books-appear is unmatched.** Speechify's Drive integration is reviewed as working "maybe one try in 10". Play Books requires manual per-file uploads. NaturalReader, ElevenReader, @Voice and Voice Dream are all per-file. PhonoLeaf's Drive folder plus local device folder is a capability nobody else in the set actually delivers.

## Weaknesses

- **No iOS, and iOS is where this category's paying users are.** Speechify has 516,932 App Store ratings against 280K on Play. Voice Dream exists only on iOS and Mac. The one competitor already shipping on-device voices shipped them on iOS. PhonoLeaf's differentiator is currently unavailable on the platform where it would be most valuable, and the repo's own iOS plan has not started engineering.

- **EPUB only, DRM-free only, in a field where everyone reads more.** @Voice handles PDF, EPUB, AZW3, Word, HTML and LCP-DRM titles. Speechify and NaturalReader both do PDFs, documents, web pages and camera OCR. The review evidence shows PDFs and academic papers are a large fraction of real usage across all of them.

- **Voice breadth loses badly to ElevenReader, which is the quality benchmark users cite.** "Definitely the best selection of German voices I've found so far." "For brazilian portuguese, your app is 100x better." PhonoLeaf ships five Piper languages plus device-gated Kokoro for English, and most devices will fail the Kokoro gate (the reference Pixel 7 scores 2.47 GFLOPS against a 5.0 threshold), so the typical user gets the Piper baseline.

- **No brand and no installed base against 10M+, 10M+, 5M+, 1M+ and 1B+ download counts.**

- **Missing features that reviewers of every competitor explicitly ask for and praise.** A pronunciation editor is the single most-praised feature across NaturalReader, @Voice and Voice Dream, and mispronunciation is the most common voice complaint in all six. MP3 export exists in @Voice and NaturalReader and is cited as a reason to use them. Highlight-and-export-annotations is what makes Voice Dream "beat Speechify for academics". PhonoLeaf has none of these.

- **First run downloads roughly 80 MB before it can speak a word.** Every competitor in this set starts talking immediately, because their voices are either in the cloud or already on the phone. PhonoLeaf's fully-unbundled model is architecturally right and is a real onboarding tax that no competitor pays.

- **A structural compliance cost none of them carry.** `drive.readonly` is a restricted scope requiring an ADA-CASA AL1 assessment with annual recertification, confirmed in writing by Google. @Voice and Voice Dream read local files and need none of this; Speechify, NaturalReader and ElevenReader run their own backends and take the same class of cost as a normal operating expense on much larger revenue.

## Opportunities

- **Lead with "it does not stop", not with "it is private".** The highest-upvoted complaints in this entire research set are reliability, not privacy: endless loading, losing your place, playback dying when the screen locks. Privacy is a reason to prefer; reliability is the reason people actually switch, and PhonoLeaf's architecture happens to fix all three.

- **Losing your reading position is a near-universal failure, and PhonoLeaf already solved it harder than anyone.** Speechify: "it drops you SOMEWHERE in the vicinity, and that could be a couple chapters forward or backward" (270 helpful). ElevenReader: "Does not save place." NaturalReader: "It starts new each time." PhonoLeaf writes a CFI per spoken chunk during background reading. This is directly demonstrable in a 20-second video.

- **Billing trust is the largest single complaint cluster across Speechify, ElevenReader and Voice Dream combined,** and it is nearly free to differentiate on. Cancellations that do not work, trials that shorten from 7 days to 3, charges after cancelling, accounts deleted after annual purchase. A visibly honest trial with one-tap cancellation, stated plainly in the store listing, is worth real conversion.

- **The accessibility audience is the most loyal and the worst served.** Voice Dream is loved ("got me through a very rigorous graduate level program with honors") but is iOS-only with dated voices. Speechify is loved by dyslexic and ADHD users and is broken for them. @Voice is capable but ad-ridden. PhonoLeaf's follow-along word highlighting plus a modern neural voice on Android is a combination none of them offers, and this audience writes long, specific, persuasive reviews.

- **Some users are already assembling PhonoLeaf by hand out of a competitor.** "I ended up deleting my purchased Natural Reader app after finding this gem! I LOVE that it can be used offline and can be used directly from files! Integrating new TTS voices, including ones from Piper, was seamless." That user wanted PhonoLeaf and settled for a manual build of it. There is a reachable segment that already knows it wants this.

- **The lifetime tier has a proven, motivated buyer population, and a documented template for how to honour it.** Voice Dream's community forced a full reversal of a subscription conversion in April 2024. Those users pay once and defend it. The same reviews also show the failure mode precisely ("I paid for lifetime access prior to the integration of AI and now the voice I paid for does not work"), which tells PhonoLeaf exactly what the $129 tier must guarantee: voice packs that keep working, forever, regardless of model changes.

- **@Voice's ad problem is a targeted acquisition opportunity.** Its recent rating drops are specifically about ads with fake close buttons that hijack the user into other apps (41 helpful). Those are 10M+ Android installs of people who have already self-selected for offline, local-file, no-subscription TTS.

- **Two of the repo's own comparison pages can be made both more accurate and more persuasive.** The Play Books page currently overstates two things (see section 3). Fixing them and replacing them with the true gaps (natural voice needs a connection, playback dies with the screen off, uploads are manual and per-file, controls are poor and cannot even pause) is both safer and sharper.

## Threats

- **Speechify already took the on-device claim, on the platform PhonoLeaf has not shipped to.** [Announced 2025-12-18](https://speechify.com/news/speechify-launches-on-device-ios-ai-voices-for-offline-reading/): "100 percent local inference, without relying on an internet connection", via Core ML, iOS only. If they port it to Android, PhonoLeaf's headline differentiator becomes a checkbox on a product with 10M+ Android installs, 1,000+ voices and 60+ languages. This is the single largest strategic risk in this document, and it is already half-realised.

- **The technical moat is open source and already commoditised.** sherpa-onnx, Piper and Kokoro are public. Free Android apps shipping exactly this stack already exist (NekoSpeak, VoxSherpa). @Voice already accepts Piper voices as pluggable engines. On-device neural TTS is not defensible on its own; the defensible part is the reading experience, the position tracking, the background service and the folder sync.

- **@Voice is the real Android threat, not Speechify.** 10M+ installs, 4.3 stars across 137K reviews, updated 2026-08-22, $15 once, no subscription, and a Play listing that already reads "No account required. No subscription. No data collection." It reads more formats than PhonoLeaf including DRM-protected LCP titles, records to MP3, and has a deeper pronunciation system. It is one bundled neural-voice pack away from being PhonoLeaf's entire pitch at roughly a tenth of the three-year cost.

- **Price anchoring in this category is falling, not rising, and ElevenReader is the one moving it.** Reviewers explicitly changed their minds after its price cut ("Since the price changes this app has become much more affordable"; "Price was lowered so I went and grabbed a subscription"). At $99/year with the best voices in the set, PhonoLeaf's $49.99 is cheaper but not transformatively so, and the head-to-head on voice quality is unfavourable.

- **Two free substitutes are credible here in a way they usually are not.** Play Books is preinstalled on 1B+ devices, reads uploaded DRM-free EPUBs, and is good enough for anyone indifferent to voice quality and screen-off playback. @Voice is free with ads. PhonoLeaf must justify a subscription against two zero-cost incumbents that already do the core job.

- **A restricted OAuth scope is a single point of failure that no competitor shares.** PhonoLeaf's primary library source depends on `drive.readonly`, which requires annual CASA recertification confirmed in writing by Google. @Voice and Voice Dream read local files and are immune. If that scope is withdrawn, repriced or fails a recert, PhonoLeaf loses its main ingestion path. The local-folder feature is a genuine hedge and its strategic importance is larger than its size suggests.

- **The category's reviewers are actively hostile to subscriptions, and say so unprompted.** "no annoying subscriptions" is given as a reason to prefer @Voice. Voice Dream's conversion attempt was reversed by user revolt. ElevenReader's shift to metered hours produced the single most-upvoted review found in this research. A new entrant asking for recurring payment starts from a negative prior, and needs the free-trial and cancellation experience to be visibly better than anyone else's.

- **The lifetime tier carries a specific, documented failure mode.** Voice Dream sold perpetual access, then changed voice engines, and now carries 1-star reviews reading "Early adopter- punished" and "They removed voices that I payed for in the legacy version." If PhonoLeaf sells 500 lifetimes and later swaps model families, changes the pack hosting, or retires a voice, it inherits that exact review pattern from its most vocal and most loyal buyers.

---

## Suggested follow-ups (not done here)

1. Correct the two factual overstatements in `play-books-alternative.html` and its French twin (uploads do get Read Aloud; only the natural voice needs a connection).
2. Add a footnote to `naturalreader-alternative.html` acknowledging paid MP3 export, so the offline claim stays defensible.
3. Update `voice-aloud-alternative.html` for the app's current name, the $15 one-time price, and the fact that users can install third-party neural engines into it.
4. Update `voice-dream-alternative.html` to note that a subscription is now required for new users and the trial is 3 days, and stop leaning on offline/background as the wedge against it, since Voice Dream claims both.
5. Sharpen `elevenreader-alternative.html` with the 60-day download expiry and the no-export restriction.
6. Consider whether a comparison page against @Voice needs a different frame from the other five, since it is the only competitor whose positioning already overlaps PhonoLeaf's.
