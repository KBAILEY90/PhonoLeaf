# PhonoLeaf — French legal & marketing copy (Québec / Bill 96)

Owner operates from **Québec**, so under **Bill 96 (Charter of the French Language)**
the consumer-facing pages must have a French version at least as prominent as
English, and adhesion contracts (Terms, Privacy) should be **French-first**. This
file provides: (0) a Québec **governing-law** clause to add to the English Terms,
and (1–3) full French translations of `terms.html`, `privacy.html`, and `home.html`
as body fragments that match the existing page structure.

> ⚠️ **Needs professional review.** This is a faithful working translation, not a
> certified legal translation. Have a Québec lawyer (and ideally a legal
> translator) review both the English and French before launch — Bill 96 + the
> Québec Consumer Protection Act are legally sensitive. Keep the "not lawyer-
> reviewed" caveat on the pages.

## How Claude Code should apply this
1. **Add the governing-law clause (§0 EN)** to `terms.html` (place it right after
   "Limitation of liability"), and to `BUSINESS.md` §3.
2. **Create `terms-fr.html`, `privacy-fr.html`, `home-fr.html`** as copies of the
   English shells with `<html lang="fr">`, French `<title>`, and the French bodies
   below. Reuse the same CSS/shell.
3. **Language default + toggle.** Default to **English everywhere**, and
   auto-select **French only when the browser prefers French** (any `fr*` tag in
   `navigator.languages`) — so non-French visitors outside Québec are never served
   French, while Québec francophones (and other French speakers) get French. Show a
   clear, always-visible **FR / EN toggle** in the header of every page; a manual
   choice wins and is remembered in `localStorage` (`pl_lang`). Per Bill 96 the
   French must be at least as easy to reach as English — the prominent toggle
   satisfies that, and French-preferring users get French by default.

   ```js
   // Default-language logic. Adapt the redirect to your page-pair scheme
   // (e.g. terms.html <-> terms-fr.html). Run this early in <head>.
   (function () {
     var saved = null; try { saved = localStorage.getItem('pl_lang'); } catch (_) {}
     var prefersFr = (navigator.languages || [navigator.language || 'en'])
       .some(function (l) { return /^fr\b/i.test(l); });
     var lang = saved || (prefersFr ? 'fr' : 'en');
     // If this is an EN page and lang === 'fr', redirect to the -fr page (and
     // vice-versa). The FR/EN toggle sets localStorage 'pl_lang' then navigates.
   })();
   ```

   **Later (optional, stronger Bill 96 posture):** once the payments Cloudflare
   Worker exists (roadmap item 5), default by *region* instead of browser language —
   Cloudflare exposes the visitor's region (e.g. `request.cf.regionCode === 'QC'`),
   so Québec visitors can be served French-first regardless of browser language.
   Not possible today: the site is GitHub Pages with Cloudflare in DNS-only mode, so
   there's no Worker in front of these pages yet.
4. Bump `sw.js` CACHE, add the new pages to `scripts/stage-www.js`'s `FILES`, and
   re-stage `www/`. (Marketing pages `home*.html` are web-only, matching how
   `home.html` is handled today.)
5. Keep the `TODO: lawyer review` comments.

---

## 0. Governing-law clause (ADD to the English Terms, after "Limitation of liability")

```html
<h2>Governing law</h2>
<p>These terms are governed by the laws of the Province of Québec and the federal
laws of Canada applicable there, without regard to conflict-of-laws rules. If you
are a consumer resident in Québec, nothing in these terms limits the rights and
protections guaranteed to you by Québec's <em>Consumer Protection Act</em> and other
applicable consumer-protection laws, which apply despite any provision to the
contrary in these terms; where a term conflicts with those mandatory protections,
those protections prevail.</p>
```

---

## 1. `terms-fr.html` — French body (replaces the English body inside the shell)

```html
<h1>Conditions d'utilisation</h1>
<div class="updated">En vigueur le [DATE]</div>

<div class="card">
  <b>Ceci n'est pas un avis juridique et n'a pas encore été révisé par un avocat</b>
  Ce document a été rédigé pour décrire simplement le fonctionnement réel de
  PhonoLeaf. Avant de vous y fier à des fins de protection juridique, en
  particulier lorsque l'application quittera la phase de test et sera offerte au
  public, il est recommandé de le faire réviser par un avocat — notamment les
  sections sur la responsabilité et le droit applicable.
</div>

<p>En utilisant PhonoLeaf (« l'application »), vous acceptez les présentes
conditions. Si vous ne les acceptez pas, veuillez ne pas utiliser l'application.</p>

<h2>Ce que fait l'application</h2>
<p>PhonoLeaf se connecte à un dossier de votre propre Google Drive, lit les
fichiers epub qui s'y trouvent et vous les lit à voix haute au moyen d'une synthèse
vocale exécutée sur votre appareil ou du moteur vocal intégré à celui-ci.
L'application n'a aucun serveur ni infrastructure dorsale : elle fonctionne
entièrement sur votre appareil et communique directement avec les services de
Google.</p>

<h2>Votre compte et votre contenu</h2>
<ul>
  <li>Vous êtes responsable de votre propre compte Google et des livres que vous
      choisissez de conserver dans votre Drive et d'y lire.</li>
  <li>Vous devez détenir le droit de posséder et de lire tout fichier de livre que
      vous utilisez avec l'application. PhonoLeaf ne fournit, n'héberge ni ne
      distribue elle-même aucun contenu de livre.</li>
  <li>L'application ne lit que le dossier que vous connectez explicitement — elle
      n'accède à aucune autre partie de votre Drive.</li>
</ul>

<h2>Utilisation acceptable</h2>
<p>Veuillez ne pas utiliser l'application pour tenter de perturber un service
auquel elle se connecte (y compris les API de Google ou Open Library), de le
rétro-concevoir à des fins malveillantes ou d'y obtenir un accès non autorisé.</p>

<h2>Aucune garantie</h2>
<p>PhonoLeaf est fournie « telle quelle » et « selon la disponibilité », sans
garantie d'aucune sorte, expresse ou implicite — y compris, sans s'y limiter, les
garanties de qualité marchande, d'adéquation à un usage particulier ou d'absence de
contrefaçon. Nous ne garantissons pas que l'application sera ininterrompue, exempte
d'erreurs ou compatible avec tous les appareils ou tous les fichiers de livre.</p>

<h2>Limitation de responsabilité</h2>
<p>Dans toute la mesure permise par la loi, la personne qui développe PhonoLeaf ne
peut être tenue responsable des dommages indirects, accessoires, spéciaux ou
punitifs, ni de toute perte de données découlant de votre utilisation de
l'application. PhonoLeaf est un outil de lecture développé de façon indépendante, et
non un service assorti d'une garantie de disponibilité ou de soutien.</p>

<h2>Tarifs et paiements</h2>

<h3>Forfaits et prix</h3>
<p>PhonoLeaf est offerte sous forme d'abonnement payant à la suite d'un essai
gratuit. Forfaits actuels (en dollars américains, taxes applicables en sus) :</p>
<ul>
  <li><strong>Mensuel</strong> — 5,99 $ US par mois.</li>
  <li><strong>Annuel</strong> — 49,99 $ US par année.</li>
  <li><strong>Accès à vie « Membre fondateur »</strong> — un paiement unique de
      129 $ US, offert en quantité limitée aux premiers abonnés (voir « Accès à
      vie » ci-dessous).</li>
</ul>
<p>Les prix sont affichés avant qu'un paiement vous soit demandé et peuvent
changer avec le temps (voir « Modifications des prix »).</p>

<h3>Essai gratuit</h3>
<p>Les nouveaux abonnés peuvent commencer par un essai gratuit de 7 jours.
<strong>À moins que vous ne l'annuliez avant la fin de l'essai, celui-ci se
convertit automatiquement en abonnement payant</strong> (le forfait choisi) et
votre mode de paiement est débité au prix alors en vigueur. Un seul essai par
personne ou par compte. Vous pouvez annuler à tout moment pendant l'essai pour
éviter d'être facturé (voir « Annulation »).</p>

<h3>Facturation et renouvellement automatique</h3>
<p>Les abonnements mensuels et annuels se <strong>renouvellent
automatiquement</strong> à la fin de chaque période de facturation au prix alors
en vigueur, et votre mode de paiement est débité, jusqu'à ce que vous annuliez. En
vous abonnant, vous autorisez ces débits récurrents. Nous vous indiquerons le prix
et la fréquence de facturation avant votre abonnement.</p>

<h3>Annulation</h3>
<p>Vous pouvez annuler à tout moment. L'annulation met fin aux renouvellements
futurs; votre accès se poursuit jusqu'à la fin de la période déjà payée. Annulez
depuis les paramètres de votre compte sur phonoleaf.com si vous vous êtes abonné
sur le Web, ou depuis les paramètres d'abonnement de Google Play ou de l'App Store
d'Apple si vous vous êtes abonné dans l'application.</p>

<h3>Remboursements</h3>
<p>Pour les abonnements achetés sur <strong>phonoleaf.com</strong> : si vous n'êtes
pas satisfait, communiquez avec
<a href="mailto:support@phonoleaf.com">support@phonoleaf.com</a> dans les
<strong>14 jours</strong> suivant votre premier paiement pour obtenir un
remboursement complet. Passé ce délai, les paiements déjà effectués ne sont pas
remboursables, sauf lorsque la loi l'exige; l'annulation met fin aux débits
futurs. Pour les achats effectués via le <strong>Google Play Store ou l'App Store
d'Apple</strong>, les remboursements sont traités par cette boutique selon ses
propres politiques — veuillez les demander à celle-ci.</p>

<h3>Accès à vie (« Membre fondateur »)</h3>
<p>Un achat d'accès à vie « Membre fondateur » est un paiement unique qui accorde
l'accès pour la <strong>durée de vie du service PhonoLeaf</strong> — c'est-à-dire
aussi longtemps que PhonoLeaf continue d'être offert — et non pour la durée de vie
de l'acheteur. Il est lié à votre compte Google, n'est pas transférable et est
offert en quantité limitée. Si PhonoLeaf est définitivement abandonné (voir
« Disponibilité et abandon »), l'accès à vie prend fin à ce moment; les
remboursements dans ce cas sont traités à cette section.</p>

<h3>Modifications des prix</h3>
<p>Nous pouvons modifier les prix des abonnements. Pour les abonnés existants, nous
donnerons un préavis d'au moins <strong>30 jours</strong> avant qu'une modification
de prix ne prenne effet à votre prochain renouvellement, et vous pourrez annuler
avant cette date si vous n'êtes pas d'accord. Une modification de prix n'a aucune
incidence sur un achat d'accès à vie déjà effectué.</p>

<h3>Taxes</h3>
<p>Les prix sont exprimés hors taxes, sauf indication contraire. Vous êtes
responsable de toute taxe de vente, TVA, TPS/TVH/TVQ ou taxe similaire applicable,
qui peut être ajoutée au moment du paiement ou perçue par la boutique
d'applications.</p>

<h3>Disponibilité et abandon</h3>
<p>Nous pouvons modifier, suspendre ou abandonner PhonoLeaf (en tout ou en partie)
moyennant un préavis raisonnable lorsque cela est possible. Si nous
<strong>abandonnons définitivement</strong> le service : (a) pour les abonnés
mensuels ou annuels, nous ne facturerons aucun renouvellement supplémentaire et
rembourserons, lorsque cela est exigé ou raisonnable, la portion inutilisée d'une
période prépayée; et (b) pour les acheteurs d'un accès à vie « Membre fondateur »
au cours des <strong>12 mois</strong> précédents, nous accorderons un remboursement
(complet ou calculé au prorata, à notre discrétion raisonnable). Notre
responsabilité totale à l'égard du service est limitée aux sommes que vous nous
avez versées au cours des <strong>12 mois</strong> précédant la réclamation, sauf
lorsqu'une telle limite n'est pas permise par la loi.</p>

<h3>Achats via les boutiques d'applications</h3>
<p>Si vous achetez un abonnement ou un accès à vie via le Google Play Store ou
l'App Store d'Apple, les conditions de cette boutique ainsi que ses politiques de
facturation, d'annulation et de remboursement s'appliquent également et peuvent
régir la transaction.</p>

<h2>Droit applicable</h2>
<p>Les présentes conditions sont régies par les lois de la province de Québec et
les lois fédérales du Canada qui y sont applicables, sans égard aux règles de
conflit de lois. Si vous êtes un consommateur résidant au Québec, rien dans les
présentes conditions ne limite les droits et protections que vous garantissent la
<em>Loi sur la protection du consommateur</em> du Québec et les autres lois
applicables en matière de protection du consommateur, lesquelles s'appliquent
malgré toute disposition contraire des présentes; en cas de conflit entre une
clause et ces protections impératives, ces protections prévalent.</p>

<h2>Services de tiers</h2>
<p>L'application dépend de services qu'elle ne contrôle pas — principalement Google
Drive / Connexion Google, et Open Library pour les métadonnées de livres. Leur
disponibilité, leurs conditions et leurs pratiques de confidentialité leur sont
propres; consultez les <a href="https://policies.google.com/terms" target="_blank"
rel="noopener">Conditions d'utilisation de Google</a> pour la partie relative à
Google.</p>

<h2>Modifications de l'application ou des présentes conditions</h2>
<p>L'application est en développement actif et peut évoluer; des fonctionnalités
peuvent être ajoutées, modifiées ou retirées. Si les présentes conditions changent
de façon importante, la version mise à jour sera publiée à la même adresse avec une
nouvelle date d'entrée en vigueur.</p>

<h2>Résiliation</h2>
<p>Vous pouvez cesser d'utiliser l'application et révoquer son accès à votre compte
Google à tout moment (Paramètres → Se déconnecter, ou directement via les
<a href="https://myaccount.google.com/permissions" target="_blank"
rel="noopener">autorisations de votre compte Google</a>). Nous pouvons également
abandonner l'application à tout moment.</p>

<h2>Contact</h2>
<p>Questions au sujet des présentes conditions :
<a href="mailto:support@phonoleaf.com">support@phonoleaf.com</a></p>

<div class="foot">
  <a href="privacy-fr.html">Politique de confidentialité</a> ·
  <a href="home-fr.html">Accueil PhonoLeaf</a>
</div>
```

---

## 2. `privacy-fr.html` — French body

```html
<h1>Politique de confidentialité</h1>
<div class="updated">En vigueur le 5 août 2026</div>

<p>PhonoLeaf (« l'application », « nous ») transforme les livres epub stockés dans
votre Google Drive en lecture audio, sur votre propre appareil. La présente
politique explique quelles données l'application touche, pourquoi, et — tout aussi
important — ce qu'elle ne fait jamais.</p>

<div class="card">
  <b>La version courte</b>
  PhonoLeaf n'a aucun serveur. Vos données ne circulent nulle part ailleurs
  qu'entre votre appareil et les services de Google. Nous (la personne qui
  développe l'application) ne recevons, ne voyons ni ne stockons jamais vos livres,
  le contenu de votre Drive ou votre activité d'écoute où que ce soit.
</div>

<div class="card">
  <b>« PhonoLeaf stocke-t-elle mes données, oui ou non ? »</b>
  Les deux sont vrais à la fois, et la distinction tient simplement au
  <em>lieu</em> : <strong>l'application enregistre bel et bien des choses</strong> —
  votre position de lecture, vos statistiques d'écoute, vos réglages et les
  couvertures de livres mises en cache — et tout cela est écrit sur
  <strong>votre propre appareil</strong>, comme un navigateur enregistre un
  signet. <strong>Rien de tout cela ne nous est jamais transmis.</strong> PhonoLeaf
  n'a ni serveur, ni base de données, ni compte utilisateur; il n'existe donc
  aucun endroit, de notre côté, où une copie pourrait se trouver. C'est aussi
  pourquoi vous pouvez tout exporter ou tout effacer vous-même depuis les
  Paramètres, sans nous le demander et sans attendre — voir
  <a href="#your-rights">Vos droits sur vos données</a> ci-dessous.
</div>

<h2>Ce à quoi l'application accède, et pourquoi</h2>
<table>
  <tr><th>Donnée</th><th>Pourquoi</th><th>Où elle va</th></tr>
  <tr><td>Accès à Google Drive (lecture seule)</td><td>Pour lister et télécharger
      les fichiers epub du dossier que vous choisissez</td><td>Directement entre
      votre appareil et Google — jamais par notre intermédiaire</td></tr>
  <tr><td>Votre nom d'affichage Google</td><td>Pour le message « Bonjour, {nom} »
      sur l'écran d'accueil</td><td>Stocké uniquement sur votre appareil</td></tr>
  <tr><td>Titres et auteurs des livres</td><td>Pour rechercher le genre et le
      nombre de pages</td><td>Envoyés à Open Library (openlibrary.org), une base de
      données de livres publique et tierce — aucune donnée personnelle ou de compte
      n'y est incluse</td></tr>
  <tr><td>Progression de lecture, statistiques, réglages, choix de voix</td>
      <td>Pour que l'application se souvienne de votre position et de vos
      préférences</td><td>Stockés uniquement sur votre appareil (stockage du
      navigateur / de l'application) — jamais transmis où que ce soit</td></tr>
  <tr><td>Couvertures de livres</td><td>Mises en cache pour ne pas les
      retélécharger chaque fois</td><td>Stockées uniquement sur votre
      appareil</td></tr>
</table>

<h2>L'accès à Google Drive, précisément</h2>
<p>PhonoLeaf demande un accès en <strong>lecture seule</strong> à Google Drive
(la portée <code>drive.readonly</code>). Il s'agit d'une portée large, classée
« restreinte » par Google — plus large que ce dont l'application a strictement
besoin pour le seul dossier que vous connectez — choisie parce que c'est la seule
façon de permettre « connectez un dossier une fois, les nouveaux livres
apparaissent automatiquement » sans que vous ayez à resélectionner des fichiers
chaque fois. En raison de cette portée, PhonoLeaf doit se soumettre au processus de
vérification des applications de Google; voir l'explication de Google sur ce que
couvre cet examen à <a href="https://support.google.com/cloud/answer/13463737"
target="_blank" rel="noopener">support.google.com/cloud/answer/13463737</a>.</p>
<p>L'application ne modifie, ne supprime ni ne téléverse jamais quoi que ce soit
dans votre Drive. Elle ne fait que lister et télécharger les fichiers epub du seul
dossier que vous choisissez.</p>

<div class="card">
  <b>Engagement d'utilisation limitée (« Limited Use »)</b>
  L'utilisation et le transfert par PhonoLeaf des renseignements reçus des API de
  Google vers toute autre application respecteront la
  <a href="https://developers.google.com/terms/api-services-user-data-policy"
  target="_blank" rel="noopener">Politique relative aux données utilisateur des
  services d'API Google</a>, y compris les exigences d'<strong>utilisation
  limitée</strong>. Plus précisément : les données utilisateur Google ne servent
  qu'à fournir les fonctions de lecture décrites sur cette page; elles ne sont
  jamais transférées à quiconque, sauf dans la mesure où ces fonctions l'exigent
  sur votre propre appareil; elles ne sont jamais vendues, ni utilisées à des fins
  de publicité, de ciblage publicitaire ou d'évaluation de crédit; aucun humain ne
  les lit; et elles ne servent jamais à entraîner un modèle d'intelligence
  artificielle ou d'apprentissage automatique.
</div>

<h2>Comment nous protégeons vos données</h2>
<ul>
  <li><strong>Chiffrées en transit.</strong> Chaque connexion établie par
      l'application — vers Google, vers Open Library pour les métadonnées, pour
      télécharger un modèle de voix naturelle — passe par HTTPS/TLS. Rien de ce que
      l'application envoie ou reçoit ne circule en clair.</li>
  <li><strong>Votre connexion Google est chiffrée au repos dans l'application
      Android.</strong> L'application native stocke l'autorisation de longue durée
      qui vous garde connecté dans le stockage chiffré adossé au Keystore d'Android
      (<code>EncryptedSharedPreferences</code>), et non dans un stockage en clair.
      La version Web utilise plutôt un jeton d'accès de courte durée (~1 heure); il
      ne réside que dans votre propre profil de navigateur et n'est jamais transmis
      ailleurs que directement à Google.</li>
  <li><strong>Aucun serveur signifie aucune base de données centrale à
      compromettre.</strong> PhonoLeaf n'a aucune infrastructure dorsale; il
      n'existe donc aucun stockage côté serveur de vos livres, de votre progression
      ou de vos statistiques qui pourrait être exposé lors d'une atteinte à un
      serveur — tout n'existe que sur votre propre appareil.</li>
  <li><strong>Une application verrouillée.</strong> L'application applique une
      politique de sécurité du contenu (CSP) qui limite les sites depuis lesquels
      elle peut charger du code ou vers lesquels elle peut envoyer des données à une
      courte liste explicite (Google, Open Library et les CDN précis dont le modèle
      de voix naturelle a besoin). Le code central de lecture d'epub est intégré
      directement à l'application plutôt que chargé depuis un CDN tiers à
      l'exécution.</li>
  <li><strong>Vous contrôlez la suppression, à votre rythme.</strong> Voir
      <a href="#your-rights">Vos droits sur vos données</a> ci-dessous pour savoir
      comment tout effacer de votre appareil, y compris révoquer l'accès de
      l'application à votre compte Google, à tout moment.</li>
</ul>

<h2>Ce que nous ne faisons jamais</h2>
<ul>
  <li>Nous n'exploitons aucun serveur, et aucune version de l'application n'envoie
      vos livres, le contenu de votre Drive, votre activité de lecture ou vos
      données personnelles à nous ou à un serveur que nous contrôlons.</li>
  <li>Nous ne vendons, ne louons ni ne partageons vos données avec des annonceurs
      ou des courtiers en données. Il n'y a aucune publicité dans l'application.</li>
  <li>Nous n'utilisons pas vos données pour entraîner un modèle d'IA ou
      d'apprentissage automatique.</li>
  <li>La voix de synthèse fonctionne entièrement sur votre appareil (ou, à défaut,
      via le moteur vocal intégré de votre appareil). Le texte des livres n'est
      jamais envoyé à un service vocal infonuagique.</li>
</ul>

<h2>Les tiers avec lesquels l'application communique</h2>
<p>Comme PhonoLeaf n'a pas d'infrastructure dorsale, votre appareil communique
directement avec une courte liste fixe de services externes :</p>
<ul>
  <li><strong>Google</strong> (accounts.google.com, googleapis.com) — connexion et
      accès à Drive. Régi par la
      <a href="https://policies.google.com/privacy" target="_blank"
      rel="noopener">Politique de confidentialité de Google</a>.</li>
  <li><strong>Open Library</strong> (openlibrary.org) — une API publique et
      gratuite de métadonnées de livres exploitée par l'Internet Archive, utilisée
      uniquement pour rechercher le genre et le nombre de pages à partir d'un titre
      et d'un auteur.</li>
  <li><strong>jsDelivr / Hugging Face</strong> (cdn.jsdelivr.net, huggingface.co) —
      uniquement si votre appareil se rabat sur le modèle de voix neuronale dans le
      navigateur, ce qui télécharge les fichiers (non personnels) du modèle de
      voix.</li>
</ul>

<h2>Données stockées sur votre appareil</h2>
<p>Tout le reste — votre progression de lecture, vos statistiques d'écoute, votre
choix de thème, votre préférence de voix et le dossier que vous avez connecté — est
stocké localement dans le stockage de votre navigateur ou de l'application. Cela ne
quitte jamais votre appareil. Désinstaller l'application, effacer les données du
site ou utiliser « Réinitialiser les données d'écoute » dans les Paramètres le
supprime.</p>

<h2>Déconnexion</h2>
<p>La déconnexion révoque l'accès de l'application à votre compte Google. Dans
l'application native, cela révoque aussi l'autorisation Google sous-jacente, et pas
seulement la session locale.</p>

<h2 id="your-rights">Vos droits sur vos données</h2>
<p>Comme PhonoLeaf n'a pas d'infrastructure dorsale, aucun compte à vous ne se
trouve sur un serveur que nous pourrions remettre ou effacer — tout est sur votre
propre appareil, et vous pouvez agir directement, sans nous le demander et sans
attendre :</p>
<ul>
  <li><strong>Accès / portabilité.</strong> <em>Paramètres → Exporter mes
      données</em> télécharge tout ce que l'application a enregistré <em>sur cet
      appareil</em> sous forme de fichier JSON lisible : progression de lecture,
      statistiques d'écoute, métadonnées de livres et réglages. Les jetons de
      connexion sont volontairement exclus du fichier, car leur copie donnerait à
      quiconque les détient l'accès à votre Drive.</li>
  <li><strong>Effacement.</strong> <em>Paramètres → Supprimer mes données</em>
      efface tout de cet appareil — le stockage local et les images de couverture en
      cache — et déconnecte votre compte Google en révoquant l'accès de PhonoLeaf
      chez Google. Vos livres numériques dans Google Drive ne sont pas touchés.</li>
  <li><strong>Effacement partiel.</strong> <em>Statistiques → Réinitialiser les
      données d'écoute</em> efface uniquement votre historique d'écoute et votre
      progression, sans toucher aux réglages.</li>
</ul>
<p>Si vous avez une question à laquelle l'application elle-même ne peut répondre,
communiquez avec nous à l'adresse ci-dessous.</p>

<h2>Vie privée des enfants</h2>
<p>PhonoLeaf ne s'adresse pas aux enfants de moins de 13 ans, et nous ne recueillons
pas sciemment de données auprès d'enfants. Comme PhonoLeaf n'a pas d'infrastructure
dorsale, elle ne recueille de données auprès de personne, quel que soit son âge,
sur aucun serveur que nous exploitons.</p>

<h2>Modifications de la présente politique</h2>
<p>Si la présente politique change, la version mise à jour sera publiée à la même
adresse avec une nouvelle date d'entrée en vigueur.</p>

<h2>Contact</h2>
<p>Questions au sujet de la présente politique :
<a href="mailto:support@phonoleaf.com">support@phonoleaf.com</a></p>

<div class="foot">
  <a href="terms-fr.html">Conditions d'utilisation</a> ·
  <a href="home-fr.html">Accueil PhonoLeaf</a>
</div>
```

---

## 3. `home-fr.html` — French body (landing page)

```html
<div class="hero">
  <div class="mark"><!-- same logo SVG as English --></div>
  <h1>Vos epubs Google Drive, lus à voix haute</h1>
  <div class="tagline">Une voix pour chaque page</div>
  <p class="lede">
    PhonoLeaf transforme les livres numériques qui dorment déjà dans votre Google
    Drive en bibliothèque audio. Connectez un dossier une seule fois, et chaque
    epub qu'il contient devient quelque chose que vous pouvez écouter — en marchant,
    en faisant la vaisselle ou dans les transports.
  </p>
  <a class="cta" href="index.html">Ouvrir PhonoLeaf</a>
  <span class="cta-note">Essai gratuit de 7 jours · Connexion avec Google · Rien à installer</span>
</div>

<h2>Comment ça fonctionne</h2>
<ol class="steps">
  <li>
    <b>Connectez-vous avec Google</b>
    <span>Utilisé uniquement pour accéder aux fichiers de livres de votre propre
    Drive. PhonoLeaf n'a aucun compte distinct à créer.</span>
  </li>
  <li>
    <b>Choisissez le dossier Drive où se trouvent vos livres</b>
    <span>Parcourez votre Drive dans l'application et choisissez un dossier.
    Ajoutez-y d'autres epubs plus tard et ils apparaissent tout simplement — sans
    resélection.</span>
  </li>
  <li>
    <b>Appuyez sur lecture</b>
    <span>Choisissez un livre et écoutez. PhonoLeaf se souvient exactement de
    l'endroit où vous vous êtes arrêté, dans chaque livre.</span>
  </li>
</ol>

<h2>Ce que fait l'application</h2>
<div class="grid">
  <div class="feat">
    <b>Voix naturelle</b>
    <span>Une voix neuronale qui fonctionne entièrement sur votre appareil, avec un
    choix d'accents et de voix. Se rabat sur la voix de votre système lorsque ce
    n'est pas disponible.</span>
  </div>
  <div class="feat">
    <b>Continue la lecture, écran éteint</b>
    <span>La lecture se poursuit lorsque votre téléphone est verrouillé, avec les
    commandes lecture/pause, page et chapitre sur l'écran de verrouillage.</span>
  </div>
  <div class="feat">
    <b>Reprend là où vous vous êtes arrêté</b>
    <span>Votre position est enregistrée pour chaque livre, jusqu'au paragraphe —
    malgré les pauses, les chapitres et les redémarrages.</span>
  </div>
  <div class="feat">
    <b>Votre bibliothèque, comme une bibliothèque</b>
    <span>Les vraies couvertures tirées de chaque epub, la recherche, le saut de
    chapitre et une vitesse de lecture réglable de 0,5× à 2×.</span>
  </div>
  <div class="feat">
    <b>Statistiques d'écoute</b>
    <span>Heures écoutées, une série de jours consécutifs, et une répartition par
    auteur, livre, genre ou longueur de livre.</span>
  </div>
  <div class="feat">
    <b>Clair et sombre</b>
    <span>Un thème papier chaud le jour, un thème forêt tamisé la nuit, ou suivez le
    réglage de votre système.</span>
  </div>
</div>

<h2>Comment PhonoLeaf utilise vos données Google Drive</h2>
<p>
  PhonoLeaf demande un accès en <strong>lecture seule</strong> à Google Drive (la
  portée <code>drive.readonly</code>). Elle utilise cet accès dans un seul but :
  lister les fichiers epub du dossier que vous choisissez et les télécharger afin
  de pouvoir les afficher et les lire à voix haute.
</p>
<div class="card">
  <b>Ce que PhonoLeaf ne fait jamais</b>
  <ul>
    <li>Ne crée, ne modifie, ne déplace ni ne supprime jamais quoi que ce soit dans
        votre Drive — l'accès qu'elle détient est en lecture seule.</li>
    <li>N'envoie jamais vos fichiers ou vos données Drive à un serveur qui nous
        appartient. <strong>PhonoLeaf n'a aucune infrastructure dorsale</strong> :
        vos livres vont de Google directement à votre appareil.</li>
    <li>N'utilise jamais vos données à des fins publicitaires, et ne les vend ni ne
        les transfère jamais.</li>
    <li>Ne lit jamais rien en dehors du dossier que vous connectez, en usage
        normal.</li>
  </ul>
</div>
<p>
  Votre position de lecture, vos statistiques d'écoute et vos réglages sont stockés
  localement sur votre propre appareil, et non dans un compte. La déconnexion
  révoque l'accès de PhonoLeaf à votre compte Google. Tous les détails se trouvent
  dans la <a href="privacy-fr.html">Politique de confidentialité</a>.
</p>

<h2>Où vous pouvez l'utiliser</h2>
<p>
  PhonoLeaf fonctionne dans le navigateur sur téléphones, tablettes et ordinateurs,
  et peut être installée sur votre écran d'accueil comme une application. Une
  version Android est actuellement en test privé.
</p>

<div class="foot">
  <a href="privacy-fr.html">Politique de confidentialité</a> ·
  <a href="terms-fr.html">Conditions d'utilisation</a> ·
  <a href="index.html">Ouvrir l'application</a><br><br>
  Contact : <a href="mailto:support@phonoleaf.com">support@phonoleaf.com</a>
</div>
```
