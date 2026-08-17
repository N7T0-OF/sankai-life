# Rapport d'extraction du Jardin

Date de référence : 8 août 2026
Verdict : **le Jardin n'est pas extrait**. Il est seulement en cours de
reclassement produit comme extension facultative.

## Résumé exécutif

Le Jardin et l'Île sont aujourd'hui intégrés au module Android unique `:app`, à
la base Room v20, à la sauvegarde, à la Boutique, aux notifications et à la
navigation. Les effets directs issus de Flashcards et du receiver Mémo ont été
retirés dans le diff courant, mais masquer les boutons ou installer un pack de
données n'enlève ni les classes, ni les ressources embarquées, ni les tables de
l'application.

Le meilleur chemin est en trois paliers :

1. rendre le Jardin **optionnel au niveau des contrats et des effets** sans
   déplacer de code ;
2. déplacer catalogues et médias remplaçables dans un pack local v1 sans code ;
3. seulement ensuite, extraire physiquement le moteur et l'UI vers une Dynamic
   Feature Play ou un APK compagnon.

Un pack local v1 peut être installé/désinstallé avec ses assets et données
éditoriales. Il ne peut pas retirer le moteur Kotlin, qui reste compilé dans
l'APK. Toute communication contraire serait techniquement fausse.

Le diff contient désormais un format générique `.sankaipack` dans
`core/extensions` : modèles, codec de manifeste, inspecteur ZIP passif, store
local atomique, codec de snapshot et 17 tests ciblés présents dans les sources.
`ExtensionsScreen` et `ExtensionsViewModel` permettent l'import local, l'état,
l'ouverture et la désinstallation du pack `sankai.garden` ; la route Extensions
ainsi que les gardes des routes Jardin/Île sont branchées dans `NavGraph`.
Aucune archive `.sankaipack` Jardin de première partie n'est toutefois fournie
dans le dépôt actuel.

Cette coque protège désormais la désinstallation locale : le payload est
retiré ou désactivé, les alarmes Jardin sont annulées, mais aucune table
Jardin/Île ni aucun coffre n'est vidé. Snapshot et état Room détaillé restent
dormants ; une réinstallation compatible retrouve parcelles, cultures,
inventaire, Mimos, défis souvenir, Île et coffres. Le snapshot léger — niveau,
identifiants de plantes découvertes et date — ne suffit toutefois pas encore à
une migration physique autonome vers une feature ou un APK compagnon.

## Inventaire audité

Comptage simple du dépôt courant, à considérer comme une photographie et non
une métrique de build :

| Ensemble | Fichiers Kotlin | Lignes approximatives | Tests Kotlin | Lignes de tests approximatives |
|---|---:|---:|---:|---:|
| Jardin (`core/garden`, `ui/screens/garden`, `ui/art/ArtJardin.kt`) | 29 | 7 590 | 14 | 1 717 |
| Île (`core/island`, `ui/screens/island`) | 27 | 6 461 | 13 | 2 249 |

L'Île n'est pas indépendante du Jardin. Elle réutilise notamment graines,
sols, croissance, Mimos, météo, lumière, caméra et réserve d'eau. Extraire le
Jardin seul laisserait soit une duplication, soit un ensemble de contrats
Garden encore dans la base. Pour la première extraction physique, il est plus
sûr de traiter **Jardin + Île comme une même extension contemplative**, puis de
séparer ultérieurement les moteurs environnementaux réellement communs.

## Cartographie actuelle

### Domaine et données Jardin

- `core/garden/domain` : croissance, sols, graines, météo, lumière, temps,
  expansions, outils, Mimos, dépôt et récompenses d'apprentissage ;
- `core/garden/data/GardenEntities.kt` : sept familles persistées ;
- `core/garden/data/GardenDao.kt` : accès Room et opérations atomiques ;
- `core/garden/data/GardenRepository.kt` : orchestration, économie, temps,
  récolte, inventaire, Mimos et crédits venus de l'apprentissage ;
- `ui/screens/garden` et `ui/art/ArtJardin.kt` : écran Compose, HUD, grille,
  ambiance et catalogue visuel.

### Domaine et données Île

- `core/island/domain` : génération, validation, tuiles, culture, bâtiments,
  forêt, stocks et Mimos ;
- `core/island/data` : quatre tables, DAO et repository ;
- `ui/screens/island` : rendu, panneaux, minimap, sélection, ViewModel.

### Tables à préserver

Jardin :

- `garden_state` ;
- `garden_plot` ;
- `garden_crop` ;
- `garden_crate` ;
- `garden_inventory` ;
- `garden_mimo` ;
- `memo_challenge`.

Île :

- `island` ;
- `island_slot` ;
- `island_building` ;
- `island_stock`.

Elles sont déclarées directement dans `SankaiDatabase`. Les migrations 7→8 à
13→14 introduisent et étendent le Jardin ; 15→16 à 18→19 introduisent et
étendent l'Île. Elles sont également sérialisées dans
`SauvegardeRepository`. Les supprimer sans pont rendrait anciennes sauvegardes
et installations incompatibles.

## Couplages qui bloquent l'extraction

| Appelant dans le cœur | Couplage actuel | Contrat cible |
|---|---|---|
| Flashcards | le crédit direct via `GardenRepository` a été retiré du flux actuel | conserver un éventuel effet Jardin derrière un `OptionalLearningRewardSink` neutre si absent |
| Mémo receiver | l'écriture directe de `MemoChallengeEntity` a été retirée du receiver actuel | événement `MemoDisplayed`, consommateur facultatif si cet effet revient |
| Boutique | achète eau et compost via `GardenRepository` | catalogue de produits fourni par l'extension |
| Notifications coffre | `NotificationCoordinator` centralise la réconciliation ; la désinstallation annule les alarmes Jardin et désactive leur préférence | scheduler enregistré/désenregistré selon l'état réel de l'extension |
| Sauvegarde | connaît chaque entité Jardin | section versionnée fournie par un `BackupContributor` |
| Base Room | importe toutes les entités et expose les DAO | compatibilité conservée dans la base au palier 1 ; base dédiée seulement plus tard |
| Navigation | routes Jardin/Île toujours compilées, mais redirigent vers Extensions si `sankai.garden` n'est pas installé | contribution physique de route seulement avec une future feature |
| Île | lit eau/Mimos du Jardin et ses moteurs de domaine | même extension au départ ou module contrat partagé |
| Profil/statistiques | expose encore thèmes, ressources et progression | résumé facultatif, absent sans extension |

Le diff actuel a donc réduit deux couplages et ajouté des gardes de navigation,
mais Boutique, Room, sauvegarde et Île maintiennent la topologie monolithique.
Les corrections transactionnelles du Jardin et de l'Île doivent être conservées
lors du découplage.

## Fonctions déplacées, conservées ou supprimées de la coque

### À déplacer dans l'extension Jardin + Île

- écrans, ViewModels, repositories, DAO d'exécution et ressources visuelles ;
- catalogues de graines, outils, bâtiments, Mimos et articles Boutique Jardin ;
- notifications et jobs propres au Jardin ;
- logique de récompense décorative et défis souvenir ;
- import/export détaillé de l'état de l'extension.

### À déplacer dans un petit module partagé

Certains moteurs ont un usage plus large ou sont déjà consommés par l'Île :
temps fiable, jour/nuit, météo, lumière, caméra, modèles de graine/sol et
contrats de récompense optionnelle. Ils doivent aller dans un module Kotlin
sans Android, par exemple `:core:environment` ou `:core:extension-contract`,
seulement si leur usage hors extension est prouvé.

### À conserver dans la base au premier palier

- interfaces de capacités facultatives ;
- statut installé/activé ;
- compatibilité avec les anciennes sauvegardes ;
- tables Room v20 et migrateur de reprise ;
- événements métier `LearningCompleted`, `FocusCompleted`, `MemoDisplayed` ;
- implémentation no-op quand l'extension est absente.

### À retirer du parcours principal

- action Jardin dans Aujourd'hui ;
- badges/coffres liés au Jardin dans la barre principale ;
- catégories Boutique et Défis dans le mode minimal ;
- initialisation, travail hors ligne et notifications quand l'extension est
  désactivée.

Retirer du parcours ne signifie pas effacer les données ni supprimer le code
avant le palier physique. L'implémentation actuelle respecte cette protection
en conservant les tables Jardin/Île dormantes après retrait du payload.

## Architecture transitoire recommandée

```text
Apprentissage / Mémo / Focus
            |
            v
  ExtensionEventBus typé en mémoire
            |
       +----+------------------+
       |                       |
 NoOpGardenAdapter      GardenAdapter compilé
 extension absente      extension activée
                               |
                         GardenRepository
```

Pas de bus générique à chaînes libres : chaque événement possède un type et une
sémantique idempotente. L'action d'apprentissage est validée avant l'effet
optionnel. Un échec Jardin est journalisé localement mais ne revient jamais
annuler la session principale.

Le registre d'extensions décide de l'adapter actif. Il doit être consulté par
la navigation, les schedulers et la Boutique, pas seulement par l'écran
Extensions.

## Migration sans perte proposée

La désinstallation locale satisfait maintenant le premier niveau de cette
cible en conservant les tables détaillées dormantes. Le snapshot restant un
simple résumé, l'extraction physique future exige encore un export complet,
des comptes de lignes et une restauration vérifiée.

### Release de préparation

1. passer Room à `exportSchema=true` et versionner le schéma v20 ;
2. ajouter des tests `MigrationTestHelper` depuis chaque version qui a créé ou
   modifié Jardin/Île ;
3. ajouter un export d'extension autonome avec version, comptes de lignes et
   SHA-256 ;
4. introduire les ports optionnels et garder les implémentations actuelles ;
5. rendre navigation, jobs et effets réellement inactifs quand l'extension est
   désactivée.

### Release avec pack local

6. déplacer uniquement médias/catalogues immuables dans un pack de données ;
7. installer le pack de première partie après vérification, sans réinitialiser
   les tables ;
8. désinstaller le payload tout en gardant l'état utilisateur « détaché » ;
9. tester réinstallation de versions identique, plus récente, incompatible et
   corrompue ;
10. conserver le fallback embarqué pendant au moins une version de transition.

### Release avec extraction physique

- Dynamic Feature : le module de base garde contrats et migrateur ; le module
  à la demande apporte code/UI/ressources. Tester installation interrompue,
  désinstallation Play et restauration sur nouvel appareil.
- APK compagnon : exporter un snapshot signé/intègre, importer après
  consentement, comparer chaque compte et hash, puis laisser l'ancienne copie
  en lecture seule jusqu'à confirmation. Les échanges inter-app doivent être
  protégés par signature ou intents explicites.

Aucune migration ne doit faire `DROP TABLE` dans la même release que la copie.
L'effacement final, s'il est un jour justifié, arrive après une période de
compatibilité et une sauvegarde vérifiée. Sans télémétrie distante, proposer à
l'utilisateur un diagnostic local exportable plutôt que supposer la réussite.

## Pack Jardin v1 : portée honnête

Un futur pack de données Jardin peut contenir :

- graines, saisons, textes, conseils et valeurs d'équilibrage bornées ;
- images et sons passifs sur liste blanche ;
- manifeste, compatibilité, sources, droits et checksums ;
- données initiales nécessaires au moteur déjà compilé.

Il ne peut pas contenir :

- règles Kotlin nouvelles, migrations Room arbitraires ou requêtes SQL ;
- Compose, DEX, JavaScript, HTML, SVG ou code natif ;
- receiver, service, permission ou route Android ;
- accès réseau ou exécution dynamique.

La désinstallation actuelle retire ou désactive uniquement le payload du pack,
annule ses alarmes et garde snapshot, parcelles, cultures, bâtiments,
préférences, Île et coffres dans le stockage personnel dormant. Un éventuel
choix « Effacer mes données Jardin » devra rester séparé, explicite et absent du
flux de désinstallation ordinaire.

Le manifeste `.sankaipack` actuel ne possède pas encore de champs structurés de
licence, source ou attribution. Il faut les ajouter et les tester avant de
qualifier ce format de publiable. Ses checksums contrôlent l'intégrité, pas
l'identité de l'éditeur.

## Matrice de validation

| Cas | Attendu |
|---|---|
| Nouvelle installation, sans pack | App démarre ; apprentissage, Mémo et Focus fonctionnent ; Extensions montre « non installé » |
| Ancien utilisateur, extension désinstallée | Toutes les lignes v20 restent dormantes ; alarmes annulées et accès Jardin gardé |
| Pack installé | Route activée après validation ; l'état existant doit être repris sans réinitialisation |
| Pack absent puis réinstallé | Parcelles/inventaire/Île dormants retrouvés à l'identique ; à confirmer par test instrumenté |
| Pack corrompu | Ancienne version conservée ou état absent propre ; cœur utilisable |
| Mise à jour APK sans feature | Base migrée sans charger de classe de feature |
| Sauvegarde ancienne avec Jardin | Restauration garde la section même si extension absente |
| Sauvegarde sans Jardin vers app avec feature | Jardin reste vierge, sans fabrication de données |
| Mode minimal | Aucune route, badge, Boutique, défi ou notification Jardin |
| Révision/Focus sans Jardin | Résultat principal enregistré, adapter no-op, aucun crash |

Les 17 tests unitaires Extensions ont réussi dans la campagne
`testDebugUnitTest` finale, verte à 694/694. `lintDebug` et `lintRelease`
comptent chacun 180 avertissements et zéro erreur. La compilation release,
`assembleRelease` et `bundleRelease` ont réussi avec R8/lint vital,
`--no-parallel`, `-Pksp.incremental=false` et Kotlin in-process. Aucun test
instrumenté ni essai sur appareil n'a été exécuté. Restent requis sur API 26 et
API récente : process death,
installation/désinstallation de feature, stockage plein, reboot, Doze, rotation,
restauration et mise à jour depuis un APK v1.78 réel anonymisé.

## Mesures et économies possibles

Les artefacts courants ont été mesurés après génération réussie. Les autres
valeurs restent des estimations issues de l'audit local, à remesurer après
chaque palier :

| Mesure | Valeur |
|---|---:|
| APK debug final | 26 711 899 octets, environ 25,47 Mio |
| SHA-256 APK debug | `37E37B1FA34B69D620AD08A82DBF0D83B22EC2B974AC508D2AC1B4CB3C7D83BA` |
| APK release final | 5 356 330 octets, environ 5,11 Mio |
| AAB release final | 9 709 094 octets, environ 9,26 Mio |
| Total des ressources app | environ 1,29 Mio |
| Dépôt de travail | environ 109,5 Mio |
| Doublons exacts récupérables dans le dépôt | environ 41,05 Mio |

Les 41,05 Mio de doublons concernent surtout le dépôt de travail ; ils ne sont
pas une estimation du gain APK. R8 et le shrinker de ressources réduisent déjà
la release. Aucune mesure actuelle n'isole le poids compressé du seul Jardin.
Il faut donc comparer APK Analyzer/bundletool avant et après, à fonctionnalités
équivalentes, plutôt que promettre un pourcentage.
Il n'existe pas de baseline strictement comparable aux artefacts courants ; ces
tailles ne prouvent donc à elles seules ni hausse ni baisse.

## Risques principaux

1. régression future qui supprimerait prématurément les onze tables Jardin/Île
   au lieu de conserver l'état dormant ;
2. révision annulée parce qu'un effet optionnel échoue ;
3. sauvegarde ancienne devenue illisible sans l'extension ;
4. régression future qui contournerait les gardes de routes ou la
   réconciliation centralisée des alarmes quand l'extension est absente ;
5. partage artificiel du Jardin et de l'Île malgré leurs modèles communs ;
6. pack ZIP présenté comme suppression de code ;
7. checksum présenté comme signature d'éditeur ;
8. assets sans preuve de droits commerciaux ;
9. gain de taille surestimé à partir des doublons du dépôt ;
10. dépendance exclusive à Play Feature Delivery alors que la distribution
    GitHub doit rester décidée explicitement.

## Ordre de travail

Suivre l'ordre 1–10 de `PRODUCT_REFOCUS.md`. Pour ce chantier, les jalons
critiques sont 4 (registre), 5 (garde Jardin), 6 (ports), 7 (migration), 8
(matrice avec/sans) et 10 (extraction physique). Aucun fichier Garden ne doit
être supprimé avant le succès documenté du jalon 8.

## Sources officielles

- Android, modularisation : <https://developer.android.com/topic/modularization>
- Android, Play Feature Delivery : <https://developer.android.com/guide/playcore/feature-delivery>
- Android, App Bundles et APK générés : <https://developer.android.com/guide/app-bundle>
- Android, migrations Room : <https://developer.android.com/training/data-storage/room/migrating-db-versions>
- Android, sauvegarde des données : <https://developer.android.com/identity/data/autobackup>
- Android, communication inter-app sécurisée : <https://developer.android.com/privacy-and-security/risks/access-control-to-exported-components>
- INPI, droit d'auteur applicable aux assets : <https://www.inpi.fr/proteger-vos-creations/le-droit-dauteur>
