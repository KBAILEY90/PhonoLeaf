# PhonoLeaf — App store listing copy (EN + FR-CA)

Ready to paste listing text for **Google Play** and the **Apple App Store**, in
English and Canadian French. Add the French (Canada, `fr-CA`) localization on both
stores. Under Québec's Bill 96 the store listing is consumer facing content that
needs a French version. Character limits are noted; stay within them.

Positioning: lead with **"turn any ebook into an audiobook."** Do not assume people
know the word "epub"; say "ebook" in the hooks and mention the EPUB format only in a
details line.

> Keep claims accurate. Both stores reject misleading copy, and the honest pitch
> (your own books, private, on your device) is strong on its own.
> **Offline note:** the "download to listen offline" line depends on the offline
> feature in `BACKLOG.md` section C shipping first. Do not publish the offline claim
> until downloaded books actually play with no network.

---

## Google Play

### English (default `en-US`)

- **App name** (max 30): `PhonoLeaf: Ebook to Audiobook`
- **Short description** (max 80):
  `Turn the ebooks in your Google Drive into audiobooks, read in a natural voice.`
- **Full description** (max 4000):

```
Turn any ebook into an audiobook.

PhonoLeaf takes the ebooks already sitting in your Google Drive and reads them aloud in a natural voice, right on your device. Connect a folder once, press play, and listen on a walk, doing the dishes, or on your commute.

Unlike cloud reading apps, PhonoLeaf keeps everything on your device. Your books and your listening never leave your phone, and the voice is generated locally. Nothing is uploaded to a speech server.

WHY PHONOLEAF
• Your own library. Listen to the books you already own, from any source. It is not a bookstore, and there are no fees per book.
• A natural voice that runs entirely on your device, with a choice of accents and voices.
• Private. No tracking, no ads, and nothing about your reading leaves your device.
• Background and lock screen playback. It keeps reading with the screen off, with play, pause, page and chapter controls on the lock screen.
• Download your books to listen offline, with no signal needed.
• Picks up where you left off, in every book, down to the paragraph.
• A real library, with cover art, search, chapter jumping, adjustable speed from 0.5x to 2x, and listening stats.
• Light and dark themes.

HOW IT WORKS
1. Sign in with Google. It is used only to reach the books in your own Drive. There is no separate account to create.
2. Choose the Drive folder your books live in.
3. Press play.

PhonoLeaf reads standard ebook files (EPUB) and asks for read only access to Google Drive so it can list and download the books in the folder you choose. It never changes your Drive, and never sends your files or your reading activity to any server.

Free 7 day trial, then a subscription. See the Terms and Privacy Policy in the app for details.
```

### Français (`fr-CA`)

- **Nom de l'appli** (max 30): `PhonoLeaf : livres en audio`
- **Brève description** (max 80):
  `Transformez les livres de votre Google Drive en livres audio, voix naturelle.`
- **Description complète** (max 4000):

```
Transformez n'importe quel livre numérique en livre audio.

PhonoLeaf prend les livres numériques qui se trouvent déjà dans votre Google Drive et vous les lit à voix haute avec une voix naturelle, directement sur votre appareil. Connectez un dossier une seule fois, appuyez sur lecture, et écoutez en marchant, en faisant la vaisselle ou dans les transports.

Contrairement aux applis de lecture infonuagiques, PhonoLeaf garde tout sur votre appareil. Vos livres et votre écoute ne quittent jamais votre téléphone, et la voix est générée localement. Rien n'est téléversé vers un serveur vocal.

POURQUOI PHONOLEAF
• Votre propre bibliothèque. Écoutez les livres que vous possédez déjà, peu importe la source. Ce n'est pas une librairie, et il n'y a aucuns frais par livre.
• Une voix naturelle qui fonctionne entièrement sur votre appareil, avec un choix d'accents et de voix.
• Privée. Aucun pistage, aucune publicité, et rien de votre lecture ne quitte votre appareil.
• Lecture en arrière-plan et sur l'écran de verrouillage. La lecture continue écran éteint, avec les commandes lecture, pause, page et chapitre.
• Téléchargez vos livres pour les écouter hors ligne, sans connexion.
• Reprend là où vous vous êtes arrêté, dans chaque livre, jusqu'au paragraphe.
• Une vraie bibliothèque, avec les couvertures, la recherche, le saut de chapitre, une vitesse réglable de 0,5x à 2x et des statistiques d'écoute.
• Thèmes clair et sombre.

COMMENT ÇA FONCTIONNE
1. Connectez-vous avec Google. Utilisé uniquement pour accéder aux livres de votre propre Drive. Aucun compte distinct à créer.
2. Choisissez le dossier Drive où se trouvent vos livres.
3. Appuyez sur lecture.

PhonoLeaf lit les fichiers de livres numériques standard (EPUB) et demande un accès en lecture seule à Google Drive pour lister et télécharger les livres du dossier que vous choisissez. Elle ne modifie jamais votre Drive et n'envoie jamais vos fichiers ni votre activité de lecture à un serveur.

Essai gratuit de 7 jours, puis un abonnement. Consultez les Conditions d'utilisation et la Politique de confidentialité dans l'appli.
```

---

## Apple App Store

### English (default)

- **App name** (max 30): `PhonoLeaf: Ebook to Audiobook`
- **Subtitle** (max 30): `Turn ebooks into audiobooks`
- **Promotional text** (max 170):
  `Turn the ebooks in your Google Drive into audiobooks, read in a natural voice on your device. Private, no cloud, background playback. Free 7 day trial.`
- **Keywords** (max 100, comma separated):
  `audiobook,ebook,read aloud,text to speech,tts,google drive,reader,offline,voice,books,dyslexia`
- **Description** (max 4000): reuse the Google Play English full description above.

### Français (`fr-CA`)

- **Nom** (max 30): `PhonoLeaf : livres en audio`
- **Sous-titre** (max 30): `Vos livres, en livres audio`
- **Texte promotionnel** (max 170):
  `Transformez les livres de votre Google Drive en livres audio, lus par une voix naturelle sur votre appareil. Privé, sans nuage, lecture en arrière-plan. Essai de 7 jours.`
- **Mots-clés** (max 100):
  `livre audio,synthèse vocale,ebook,tts,google drive,lecteur,hors ligne,voix,dyslexie,écouter`
- **Description** (max 4000): reuse the French full description above.

---

## Notes for Claude Code / the owner
- Add the `fr-CA` localization on both stores and paste the French copy there
  (Bill 96).
- Screenshots carry text too. Provide French screenshot captions for the `fr-CA`
  listing, mirroring the English ones.
- If a store field rejects the `x` in `0.5x to 2x`, leave it as plain text; it is
  fine in the description.
- The Apple subtitle (30) and keywords (100) are hard limits. Count before pasting.
- Keep the free trial and subscription disclosure in the description. Both stores
  require clear subscription terms, and the in app paywall must also show price,
  period, and auto renewal. See `PAYMENTS_SPEC.md`.
- Do not publish the "download to listen offline" line until the offline feature
  (`BACKLOG.md` C) ships.
