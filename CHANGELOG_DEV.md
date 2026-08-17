# Changelog développeur

## 1.97.0 — 17 août 2026

### Accueil

- icône Paramètres retirée de la barre d'en-tête (`SankaiFloatingButton` +
  imports inutilisés) ; l'accès reste disponible dans Profil.

### Culture

- page non déroulante : le `verticalScroll` global est retiré, la carte
  retournable occupe l'espace restant (`weight(1f)`) et chaque face fait
  défiler son contenu à l'intérieur (`fillMaxSize().verticalScroll`).

## 1.96.0 — 17 août 2026

### Crash

- `memo_mastery_percent` : `%1$d % maîtrisé` → `%1$d %% maîtrisé` (fr/en/pt).
  Le `%` suivi d'un espace était un spécificateur invalide pour
  `String.format` → `UnknownFormatConversionException` à l'ouverture de
  Mémo avec un parcours présent.

### Capsules

- carte retournable : rotation 3D (`graphicsLayer.rotationY` + `cameraDistance`),
  faces en fondu à 90°, la bascule crédite toujours la source Découverte ;
- écoute : bouton pleine largeur → `IconButton` volume compact ;
- réflexion personnelle retirée (état ViewModel, fonctions, chaînes
  fr/en/pt) ; données locales existantes laissées dormantes ;
- bouton « Fermer Sankai Life » retiré de l'écran Capsules.

### Nommage

- « Aujourd'hui » → « Accueil » (nav_today fr/en/pt, KDoc, PRODUCT_REFOCUS).

## 1.95.0 — 17 août 2026

### Source Concentration (minuteur système)

- `core/concentration/ConcentrationIntegration` : décision pure et testable
  d'une « fin de minuteur » (paquet Horloge connu + canal « timer » +
  non-persistant), déduplication par clé de notification ;
- `core/concentration/MinuteurListener` (`NotificationListenerService`) :
  crédit une fois par jour via `UserRepository.addSourceXp(CONCENTRATION)`,
  relit les notifications présentes au raccordement, ne retient qu'une clé ;
- préférences : `concentration_actif` (désactivé par défaut) et
  `concentration_credits_<date>` (déduplication quotidienne) ;
- confirmation discrète via `NotificationPolicy.tryAcquire(FOCUS)` dans le
  budget quotidien ; le crédit d'XP n'est jamais une notification ;
- widget « Aujourd'hui » rafraîchi après un crédit.

### Activités connectées (Paramètres)

- section Calendrier + Concentration : état, XP du jour, bouton d'accès ;
- état relu au retour des réglages Android (launcher d'activité et
  permission) ; `NotificationManagerCompat.getEnabledListenerPackages` pour
  l'état réel de l'accès aux notifications.

### Validation

- 11 tests unitaires nouveaux (détection, déduplication) ;
- `testDebugUnitTest`, lint et build à revérifier avant publication.

## 1.38.0 — 1er août 2026

### Intégrité

- écritures atomiques pour pièces, gemmes, XP et statistiques ;
- achats de slots recalculés et livrés dans une transaction ;
- récompenses coffres, défis et arènes livrées transactionnellement ;
- progression des défis bornée et réclamation anti-double ;
- verrou de réponse flash cards et reprise unique des cartes ratées.

### Données et sécurité

- lecteur ZIP borné pendant la décompression ;
- installation de modules transactionnelle avec ordre stable ;
- restauration conditionnée par une sauvegarde de sécurité réellement écrite ;
- sauvegarde étendue aux données persistantes promises ;
- consentement UMP 4.0.0 avant initialisation AdMob.

### Notifications

- créneaux aléatoires mémo stables par profil et journée ;
- replanification après activation/désactivation ;
- alarmes coffres restaurées au lancement et après événements système ;
- réglage global respecté par les notifications de coffres.

### Jardin

- synchronisation temporelle liée au cycle de vie ;
- tick partagé et index des cultures ;
- origine caméra stable, recentrage réel, petit terrain centré ;
- moteur visuel météo, vent partagé, éclairage couvert, ombres nuageuses
  procédurales et profils de qualité ;
- économie batterie forçant la qualité faible.

### UI

- tokens d'espacement/rayons et primitives liquid glass ;
- accueil responsive non défilant avec Arène centrale et dock coffres ;
- navigation basse accessible et compatible edge-to-edge ;
- recentrage du parcours Arènes.

### Livraison

- release sans repli sur signature debug ;
- publication dépendante des tests et bloquée si secrets incomplets ;
- lint release bloquant.

### Validation finale

- 239 tests unitaires dans 24 suites, réussis en debug et en release
  (478 exécutions, aucun échec, erreur ou test ignoré) ;
- `lintDebug` et `lintRelease` réussis avec 0 erreur ;
- `assembleDebug`, `assembleRelease` et `bundleRelease` réussis ;
- signatures APK v2 vérifiées, dont la release avec le certificat Sankai Life.
