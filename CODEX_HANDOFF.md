# Passation Codex — recentrage produit et extensions

Date : 8 août 2026
Branche observée : `codex/product-refocus`
Commit de base observé : `150ad6d` (`v1.78.0`, versionCode 80)
État : travail non commité et partagé entre plusieurs lots.

## Résultat à retenir

Le recentrage vers Aujourd'hui, Académie, Capsules et Mode Vie est bien engagé.
Le socle Culture est concret et un écran Capsules existe. Le Jardin n'est en
revanche pas extrait : le dépôt reste un unique module `:app` et tous ses
moteurs Kotlin ainsi que ses ressources Jardin/Île restent compilés dans l'APK.

Un pack local v1 sans code peut installer/désinstaller ses assets et données.
Il ne peut pas retirer du bytecode déjà compilé. Pour qu'une installation sans
Jardin ne reçoive physiquement aucune classe Jardin/Île, il faudra une future
Dynamic Feature Play ou un APK compagnon séparé.

## Documents de référence créés

1. `PRODUCT_REFOCUS.md` — contrat produit, données conservées et ordre 1–10 ;
2. `EXTENSION_ARCHITECTURE.md` — frontière pack de données / feature / APK ;
3. `CULTURE_PACK_SCHEMA.md` — schéma culturel v1 réellement accepté ;
4. `DIGITAL_WELLBEING_PRINCIPLES.md` — règles anti-rétention et tests ;
5. `GARDEN_EXTRACTION_REPORT.md` — inventaire des couplages et migration ;
6. ce fichier — état de reprise et portes de sortie.

Ces six documents sont les seuls fichiers modifiés par le lot documentaire.
La campagne de validation décrite plus bas a été exécutée séparément par la
racine après ces changements. Aucun commit, tag, push ou nettoyage n'a été
effectué par le lot documentaire.

## État réel du diff applicatif

### Coque produit

- La barre inférieure devient Aujourd'hui, Académie, Capsules, Mode Vie et
  Profil.
- `HomeScreen` est réduit à une action utile, une capsule, un mémo et une fin
  volontaire de journée ; les anciens grands blocs coffres/arène/Jardin ont
  été retirés de ce fichier.
- `HomeViewModel` initialise seulement l'utilisateur et ne crée plus de coffre
  ou défi quotidien.
- `ModeVieScreen` place Mémo et Focus avant objectifs, extensions, Boutique et
  Défis ; les facultatifs disparaissent en mode minimal.
- L'onboarding demande maintenant un objectif quotidien choisi.

### Bien-être numérique et intégrité

- DataStore reçoit mode minimal, objectif quotidien, date de fin, budget de
  notifications, pause, week-end et catégories.
- `NotificationPolicy` impose le budget ; `NotificationCoordinator` annule ou
  recrée les alarmes pertinentes au démarrage et après événements système.
- `FocusRewardEngine` conserve des XP/pièces proportionnels et plafonnés pour
  compatibilité, sans créer de coffre ni progresser de défi ; ces gains restent
  en arrière-plan, notamment en mode minimal.
- Une fin Focus hors écran peut envoyer une notification discrète après le
  contrôle de sa catégorie. Focus, Mémo, Révision et Culture ouvrent désormais
  leur destination Focus, Mémo, Académie ou Capsules depuis la notification.
- Les politiques de récompense Flashcards ont aussi été recentrées.
- Plusieurs opérations Jardin/Île ont reçu des gardes transactionnelles et des
  moteurs de temps plus sûrs. Ne pas perdre ces corrections lors du découpage.

### Culture

- `core/culture` contient modèles, parseur JSON JVM, importeur ZIP borné,
  checksums, compatibilité, store atomique et sélecteur déterministe.
- Le lot ajoute 28 tests culturels ciblés.
- `app/src/main/assets/culture/classics-fr-v1` (v1.1.0) contient treize
  capsules : textes du domaine public (Hugo, du Bellay, Pascal, La
  Fontaine, Ibn Battûta) et notices originales CC0 (mots, proverbes,
  sciences, histoire, culture), avec source, licence et hashes.
- `ui/screens/capsules` affiche une capsule finie, provenance, favori, lecture
  vocale éventuelle et réflexion locale. Il utilise le pack embarqué et un
  `AtomicFile` sous `noBackupFilesDir`, pas encore `CulturePackStore`.

### Extensions

- `core/extensions` définit un manifeste `.sankaipack`, un parseur/validateur,
  des assets passifs, un store local atomique et un petit snapshot Jardin.
- `ExtensionsScreen` et `ExtensionsViewModel` sont branchés dans `NavGraph`.
  L'écran importe un fichier local, affiche l'état du pack, l'ouvre et le
  désinstalle. Le contrat courant est mono-extension : identifiant
  `sankai.garden`, écran d'entrée `garden.home`.
- Les routes Jardin et Île consultent le store et redirigent vers Extensions si
  le pack n'est pas installé. Dix-sept tests unitaires couvrent inspecteur et
  store ; ils ont réussi dans la campagne `testDebugUnitTest` finale, verte à
  694/694.
- Aucune archive `.sankaipack` Jardin installable n'est fournie dans le dépôt ;
  l'interface ne remplace pas encore un livrable de contenu attribué.
- Le snapshot Jardin ne contient qu'un niveau, les plantes découvertes et une
  date. Il ne constitue pas un export autonome des onze tables Jardin/Île pour
  une future extraction physique.
- La désinstallation actuelle retire ou désactive le payload, annule les
  alarmes et conserve snapshot, onze tables Jardin/Île et coffres dans Room.
  Cet état détaillé dormant est retrouvé lors de la réinstallation.
- Le manifeste générique n'a pas encore de champs licence/source/attribution.

## État de livraison et travaux restants

### Protection de données en place

La désinstallation de `sankai.garden` est non destructive au palier actuel : le
payload est retiré/désactivé, les alarmes sont annulées et les données Room
restent dormantes. Le snapshot léger complète ce choix sans remplacer une
future migration complète. Toute extraction physique devra exporter, comparer
et restaurer les onze tables avant d'envisager leur suppression.

### P1 données et extensions

- Ne pas présenter `GardenExtensionSnapshot` comme une migration complète.
- Ne supprimer aucune table Room Jardin/Île lors de la première extraction.
- Les couplages directs depuis Flashcards et le receiver Mémo ont été retirés ;
  brancher encore Boutique, sauvegarde, Room et Île sur des ports optionnels
  avant de déplacer des packages.
- Ajouter droits/licences et tests au format `.sankaipack`.
- Les checksums prouvent l'intégrité par rapport au manifeste reçu, pas
  l'identité de l'éditeur.

### P1 validation

Le rebuild final stable a réussi 694/694 tests via `testDebugUnitTest`.
`lintDebug` et `lintRelease` comptent chacun 180 avertissements et zéro erreur.
La compilation release, `assembleRelease` et `bundleRelease` ont réussi avec R8
et lint vital, `--no-parallel`, `-Pksp.incremental=false` et Kotlin in-process.
Aucun test instrumenté ni essai sur appareil n'a été exécuté ; ces validations
restent donc requises avant release.

## Données à conserver sans exception

- utilisateur, XP, monnaies et historique de focus ;
- profils/lignes Mémo, planification, boîtes et dates de révision ;
- parcours, unités, sessions et résultats d'apprentissage ;
- objectifs, arènes, défis, coffres et personnalisations, même masqués ;
- Jardin : état, parcelles, cultures, caisses, inventaire, Mimos et défis
  souvenir ;
- Île : monde, cases, bâtiments et stock ;
- réglages DataStore, favoris/réflexions Culture et statut d'extensions ;
- compatibilité des sauvegardes qui contiennent une section absente de la
  configuration courante.

La désinstallation du pack respecte maintenant la règle produit : elle retire
ou désactive le payload éditorial mais conserve par défaut l'état Room créé par
l'utilisateur. Un éventuel effacement doit rester une action séparée, explicite
et accompagnée d'un décompte.

## Fonctions supprimées ou déplacées dans l'expérience

| Fonction | Situation après recentrage | Suppression physique ? |
|---|---|---|
| Coffres/arènes/Jardin de l'accueil | retirés d'Aujourd'hui | Non |
| Boutique et Défis | déplacés dans Mode Vie facultatif | Non |
| Série punitive | retirée du chemin principal ; préférer fenêtres 7/30/90 jours | Non, champs historiques conservés |
| Mémo | outil essentiel + Académie | Non |
| Focus | outil essentiel ; aucun coffre ni progrès de défi, XP/pièces de compatibilité en arrière-plan | Non |
| Jardin/Île | cible Extension | Non, toujours dans `:app` |
| Culture | nouvel espace sans flux infini | Nouveau code/asset embarqué |

## Ordre impératif 1–10

1. **Geler la référence** : sauvegarde v20 anonymisée, schéma Room exporté,
   inventaire des routes et mesures reproductibles.
2. **Valider la coque** : vérifier les routes Capsules/Extensions déjà
   enregistrées, les gardes Jardin/Île, tous les clics de la barre et le mode
   minimal.
3. **Finir le branchement Culture** : choisir pack embarqué/local, connecter le
   store, état vide, import et désinstallation.
4. **Généraliser le gestionnaire d'extensions** : partir du store et de l'écran
   mono-Jardin existants pour distinguer absent/installé/activé/incompatible et
   fermer les permissions hôte.
5. **Garder le Jardin** : aucune route, alarme, Boutique ou initialisation quand
   désactivé.
6. **Découpler** : événements typés et ports no-op depuis Apprentissage, Mémo,
   Focus, Boutique, sauvegarde et Île.
7. **Migrer sans perte** : tests Room, snapshot complet, anciennes sauvegardes,
   désactivation/réinstallation et rollback.
8. **Tester les matrices** : avec/sans pack, avec/sans Jardin, pack corrompu,
   mise à jour et restauration sur API 26 + API récente.
9. **Nettoyer après preuve** : ressources inutiles, documents obsolètes et
   doublons exacts, sans supprimer les originaux non attribués.
10. **Extraire physiquement** : Dynamic Feature Play ou APK séparé ; remesurer
    l'APK/AAB et la distribution avant annonce.

## Mesures de référence

Les artefacts courants ont été mesurés directement après leur génération. Les
autres valeurs restent des estimations d'audit local :

| Élément | Valeur |
|---|---:|
| APK debug final | 26 711 899 octets, environ 25,47 Mio |
| SHA-256 APK debug | `37E37B1FA34B69D620AD08A82DBF0D83B22EC2B974AC508D2AC1B4CB3C7D83BA` |
| APK release final | 5 356 330 octets, environ 5,11 Mio |
| AAB release final | 9 709 094 octets, environ 9,26 Mio |
| Ressources app | environ 1,29 Mio |
| Dépôt | environ 109,5 Mio |
| Doublons exacts récupérables | environ 41,05 Mio |

Le volume récupérable du dépôt ne prédit pas le gain APK. Mesurer par APK
Analyzer et bundletool après chaque palier. Il n'existe pas de baseline
strictement comparable aux artefacts courants : ne pas annoncer de hausse ou de
baisse à partir de ces seules valeurs.

## Validation exécutée et validations restantes

Exécuté avec succès sur le worktree courant :

- 694/694 tests unitaires debug ;
- lint debug et release : 180 avertissements chacun, zéro erreur ;
- compilation release, assemblage APK release, R8 et lint vital ;
- génération de l'AAB release ;
- rebuild stabilisé avec `--no-parallel`, `-Pksp.incremental=false` et Kotlin
  in-process.

Non exécuté et restant à valider :

- tests instrumentés, notamment migrations Room depuis v7, v15 et v20 ;
- navigation Capsules/Extensions et destinations des notifications à froid et
  à chaud ;
- matrices `.sankaipack` valides/hostiles, avec/sans pack et extension active ;
- validation de l'AAB par bundletool ;
- contrôles manuels sur API 26 et Android récent : police 200 %, TalkBack,
  reboot/fuseau/Doze, refus notification, mode minimal, fermeture volontaire,
  sauvegarde/restauration et réinstallation d'un pack.

Aucun test instrumenté ni essai sur appareil ne doit être inféré de la campagne
Gradle réussie.

## Droits et licences

- Le pack Culture exemple documente œuvres, sources et licence ; maintenir ce
  niveau pour chaque entrée.
- Le format Extension générique doit encore imposer provenance, licence et
  attribution.
- Avant usage commercial, établir un registre pour chaque image, son, police,
  traduction et texte : auteur, source, licence, territoire, preuve et date de
  contrôle.
- En cas d'incertitude sur un texte, transporter les métadonnées uniquement.
- Vérifier séparément marque, titre et identité visuelle ; une recherche INPI
  n'est pas à elle seule une garantie de disponibilité.

## Sources utiles

- Android, modularisation : <https://developer.android.com/topic/modularization>
- Android, Play Feature Delivery : <https://developer.android.com/guide/playcore/feature-delivery>
- Android, migrations Room : <https://developer.android.com/training/data-storage/room/migrating-db-versions>
- Android, notifications : <https://developer.android.com/develop/ui/views/notifications>
- INPI, droit d'auteur : <https://www.inpi.fr/proteger-vos-creations/le-droit-dauteur>
- INPI, recherche : <https://data.inpi.fr/>
- Mathur et al., *Dark Patterns at Scale*, arXiv:1907.07032 : <https://arxiv.org/abs/1907.07032>

## Consignes de reprise

- Le worktree contient des changements d'autres lots ; ne pas reset, checkout
  ou reformater en masse.
- Inspecter `git diff` avant chaque correction et ne toucher qu'aux fichiers en
  scope.
- Valider d'abord la désinstallation non destructive et la réinstallation sur
  les matrices avec/sans pack avant de nettoyer.
- Ne pas supprimer Jardin/Île pour atteindre un objectif de taille.
- Ne pas annoncer une « extension désinstallable » sans préciser s'il s'agit du
  payload de données ou du code physique.
