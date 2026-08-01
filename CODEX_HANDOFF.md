# Passation technique Codex

Date : 1er août 2026

## Changements structurants

- économie atomique pour les opérations simples, slots, coffres, défis et
  arènes ;
- lecteur ZIP borné commun aux modules et sauvegardes ;
- sauvegarde `v2` complète, bornée et transactionnelle, avec sauvegarde de
  sécurité obligatoire avant restauration ;
- créneaux mémo aléatoires déterministes ;
- limites de slots mémo réellement appliquées ;
- consentement UMP avant toute initialisation AdMob ;
- alarmes coffres restaurées lors des événements système ;
- flash cards protégées contre les doubles réponses ;
- Jardin vivant, caméra corrigée et moteur météo procédural ;
- phase 1 de la refonte UI ;
- chaîne release durcie.

## Points à reprendre en priorité

1. transaction unique pour les achats Boutique multi-dépôts ;
2. tests de migration Room + `exportSchema=true` ;
3. navigation depuis les extras de notification mémo ;
4. identité/version persistées des modules installés ;
5. thème équipé réellement appliqué ;
6. benchmark et culling du Jardin sur grand terrain ;
7. validation appareil de l'accueil et des nuages ;
8. mise à niveau contrôlée du SDK Google Mobile Ads.

## Coordination

Le worktree contient des changements de localisation antérieurs à cette vague.
Ils ont été conservés. Aucun commit, push, tag ni publication n'a été créé par
Codex. Lire `CHANGELOG_DEV.md`, `UI_REDESIGN_STATUS.md` et le résultat de build
ci-dessous avant de poursuivre.

## Build

Chaîne Gradle finale exécutée le 1er août 2026 :

- `test` : réussi ; 239 tests × 2 variantes, soit 478 exécutions, dans 24
  suites par variante ; 0 échec, 0 erreur, 0 ignoré ;
- `lintDebug` et `lintRelease` : réussis ; chacun rapporte 0 erreur,
  77 avertissements et 3 informations ;
- `assembleDebug`, `assembleRelease` et `bundleRelease` : réussis.

Rapports principaux :

- `SankaiLife/app/build/reports/tests/testDebugUnitTest/index.html` ;
- `SankaiLife/app/build/reports/tests/testReleaseUnitTest/index.html` ;
- `SankaiLife/app/build/reports/lint-results-debug.html` ;
- `SankaiLife/app/build/reports/lint-results-release.html`.

Artefacts :

| Variante | Fichier | Taille | SHA-256 |
|---|---|---:|---|
| debug APK | `SankaiLife/app/build/outputs/apk/debug/app-debug.apk` | 24 320 228 octets (23,194 Mio) | `59019C8ABDC3E933FFBEEDC47A5B02EC15C1A88543A40C7BA65A890694C97E0E` |
| release APK | `SankaiLife/app/build/outputs/apk/release/app-release.apk` | 4 761 814 octets (4,541 Mio) | `517307A8229FF3D501675E895B000367F565A7639E2F2EDC2BFAD2005B1430AC` |
| release AAB | `SankaiLife/app/build/outputs/bundle/release/app-release.aab` | 8 874 174 octets (8,463 Mio) | `72D90A70E23DFB49498042138CF20003022C5A48D7E0BD17106AA124F1083495` |

Le debug porte `com.sankailife.debug`, version `1.38.0-debug` / code 40. La
release porte `com.sankailife`, version `1.38.0` / code 40. `apksigner verify`
confirme une signature APK v2 pour les deux et `jarsigner` valide l'AAB ; la
release a un signataire `CN=Sankai Life, OU=Sankai, O=Sankai, L=Paris, ST=IDF,
C=FR` avec une clé RSA 4096 bits. L'empreinte SHA-256 de son certificat est
`C28787646866678B2672582D62AB659FB8615FF364233CDE2F1952CC53C0D602`.
Deux profils baseline `.dm` sont aussi présents pour API 28–30 et API 31+.

## QA appareil non couverte par le build

- installer et lancer debug et release sur au moins API 26 et une version
  Android récente ;
- tester export puis restauration de chaque section, y compris l'échec de la
  sauvegarde de sécurité et la replanification des coffres ;
- tester alarmes et notifications après reboot, Doze, changement d'heure et
  refus des notifications ;
- valider consentement UMP/AdMob avec la configuration réelle de la console ;
- parcourir accueil, navigation, arènes et Jardin sur petits/grands écrans,
  avec TalkBack et tailles de police élevées ;
- mesurer le Jardin avec nuages sur grand terrain et en économie batterie.

La chaîne n'incluait ni `connectedAndroidTest`, ni test de migration Room, ni
Macrobenchmark, ni test de capture Compose. Ces points restent donc ouverts
malgré le succès complet des vérifications automatisées.
