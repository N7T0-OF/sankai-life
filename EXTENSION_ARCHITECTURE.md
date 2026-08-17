# Architecture des extensions Sankai Life

Date de référence : 8 août 2026
Portée : architecture cible et limites du socle actuellement présent.

## Vérité technique avant toute décision

Le projet Gradle ne contient aujourd'hui qu'un module : `:app`, déclaré dans
`SankaiLife/settings.gradle.kts`. Il n'existe ni Dynamic Feature, ni chargeur de
plugins, ni APK compagnon.

Le socle `core/culture` met en œuvre un **pack local v1 sans code**. Ce pack
peut être inspecté, installé, remplacé atomiquement, chargé et désinstallé. Il
peut transporter du JSON, de la documentation, une licence et des médias
passifs. Il ne peut pas transporter ou exécuter du Kotlin, DEX, JavaScript,
HTML ou SVG.

Cette propriété est volontairement restrictive : elle donne une extension de
contenu sûre, pas une extension de moteur. Si demain le Jardin adopte un pack
v1, désinstaller ce pack pourra retirer ses assets et données éditoriales,
mais **les classes `core/garden`, `core/island` et leurs écrans resteront dans
l'APK**, avec les ressources Jardin/Île actuellement embarquées dans `:app`.
Leur suppression physique exige une future Dynamic Feature distribuée par
Google Play, ou une application/APK séparée.

## Trois notions à ne pas mélanger

| Notion | Contenu | Installation | Retire du code de l'APK de base ? |
|---|---|---|---|
| Pack local v1 | Données et médias passifs | Fichier local `.culturepack` ou futur `.sankaipack` | Non |
| Dynamic Feature Play | Ressources Android et bytecode compilé | Play Feature Delivery | Oui, si module à la demande |
| APK compagnon | Application complète dans un autre bac à sable | Installation Android séparée | Oui, mais avec protocole inter-app |

Un fichier ZIP local ne doit jamais devenir un raccourci pour charger du code
arbitraire. C'est une frontière de sécurité, pas une limitation à contourner.

## Socle déjà disponible

- `BoundedZipReader` compte toutes les entrées et tous les octets pendant la
  décompression.
- `CulturePackImporter` impose une liste blanche de chemins et d'extensions,
  un manifeste de compatibilité et une empreinte SHA-256 pour chaque payload.
- `CultureJson` limite profondeur et nombre de valeurs, refuse les clés
  dupliquées et les JSON/UTF-8 invalides.
- `CulturePackStore` valide avant d'écrire, synchronise le fichier temporaire
  puis remplace l'archive installée de façon atomique quand le système de
  fichiers le permet.
- `DailyCultureSelector` choisit localement une capsule stable par profil,
  date et version de catalogue, sans flux infini ni score d'engagement.
- `ui/screens/capsules` lit le pack culturel embarqué, conserve sélection,
  favoris et réflexion dans un stockage privé, et n'offre aucune action
  « suivante ».
- `core/extensions` définit un second contrat générique `.sankaipack` :
  manifeste `dataOnly`, compatibilité, capabilities, écran hôte déclaratif,
  réglages, notifications désactivées par défaut, assets et checksums.
- `ExtensionPackInspector` contrôle ce format générique sans exécuter de code ;
  `GardenSnapshotCodec` sait encoder un résumé Jardin très limité.
- `LocalExtensionStore` installe sous `filesDir`, active une version par petit
  pointeur atomique, revérifie le payload installé et conserve par défaut le
  snapshot avant désinstallation. Dix-sept tests unitaires sont présents pour
  l'inspecteur et le store ; ils ont réussi dans la campagne
  `testDebugUnitTest` finale, verte à 694/694.
- `ExtensionsScreen` et `ExtensionsViewModel` sont reliés à `Screen.Extensions`.
  Ils importent un document local, imposent les identifiants hôte
  `sankai.garden` et `garden.home`, et donnent accès au Jardin installé.
- `NavGraph` protège maintenant les routes Jardin et Île : en l'absence du
  pack, il revient vers l'écran Extensions.

Le socle reste incomplet : `CapsulesViewModel` utilise directement le pack
embarqué au lieu de `CulturePackStore`, et aucune UI n'importe encore un
`.culturepack` culturel. Pour `.sankaipack`, le branchement existe mais reste
spécifique au Jardin : ce n'est pas un registre générique de contributions.
Son `GardenExtensionSnapshot` ne conserve que niveau, plantes découvertes et
date ; il ne peut pas, à lui seul, migrer les onze tables Jardin/Île vers une
future feature ou un APK compagnon.

### Protection transitoire de la désinstallation

`LocalExtensionStore` retire ou désactive le payload et conserve le résumé
demandé. `ExtensionsViewModel.uninstall()` annule aussi les alarmes Jardin,
mais ne vide plus aucune table Jardin, Île ou coffre. Parcelles, cultures,
caisses, inventaire, Mimos, défis souvenir, monde, bâtiments, stock et coffres
restent dans Room sous forme d'état dormant ; une réinstallation compatible les
retrouve sans réinitialisation.

Ce choix protège la désinstallation locale sans perte au palier monolithique.
Il ne transforme pas le petit snapshot en export complet : avant une extraction
physique, il faudra encore migrer et vérifier toutes les tables détaillées.

## Architecture cible pour le pack local v1

```text
UI Extensions / Capsules
        |
        v
ExtensionRegistry ---- ExtensionDescriptor
        |                       |
        |                       +-- identité, version, droits, compatibilité
        v
PackInstaller ---- BoundedZipReader ---- validation/checksums
        |
        +-- répertoire privé de l'application / packs/<id>/<version>
        +-- catalogue installé (petit index atomique)
        +-- ExtensionUserState (séparé du payload)
        |
        v
Contribution de données vers Culture, Jardin ou autre moteur déjà compilé
```

### Contrats minimaux

Les noms ci-dessous sont une proposition de contrats, pas des classes déjà
présentes :

```kotlin
data class ExtensionDescriptor(
    val id: String,
    val version: String,
    val schemaVersion: Int,
    val capability: String,
    val minAppVersionCode: Int,
    val maxAppVersionCode: Int?,
    val rightsSummary: String
)

sealed interface ExtensionState {
    data object Absent : ExtensionState
    data class Disabled(val descriptor: ExtensionDescriptor) : ExtensionState
    data class Enabled(val descriptor: ExtensionDescriptor) : ExtensionState
    data class Incompatible(val reason: String) : ExtensionState
    data class Invalid(val reason: String) : ExtensionState
}

interface OptionalGardenPort {
    suspend fun creditLearning(correctAnswers: Int): OptionalReward
    suspend fun creditFocus(minutes: Int): OptionalReward
    fun isEnabled(): Boolean
}
```

Le cœur d'apprentissage dépend du port optionnel, jamais de
`GardenRepository`. L'implémentation absente renvoie un résultat neutre. Cela
permet de tester et d'exécuter le cœur sans Jardin avant toute extraction
physique.

## Cycle d'installation

1. Copier le flux entrant vers une zone privée bornée ; ne jamais travailler
   dans un chemin fourni par l'archive.
2. Vérifier taille compressée, nombre d'entrées et tailles décompressées.
3. Refuser chemins absolus, `..`, antislash, `:`, NUL, doublons exacts ou de casse.
4. Refuser tout type non listé ; aucun exécutable, script, HTML ou SVG. Le
   contrat Culture refuse les polices, tandis que `.sankaipack` accepte
   explicitement TTF/OTF sous les plafonds génériques.
5. Lire le manifeste en UTF-8 strict et vérifier schéma, identifiant, version et
   compatibilité application.
6. Exiger que chaque payload soit déclaré et que chaque déclaration existe.
7. Comparer taille totale et SHA-256 de chaque fichier avant toute écriture.
8. Vérifier le statut de droits et afficher un résumé à l'utilisateur.
9. Écrire dans un fichier temporaire, `fsync`, puis remplacer atomiquement.
10. Indexer le pack comme désactivé ou activé selon un consentement explicite.

Une mise à jour invalide doit laisser la version installée intacte. Le cas
correspondant de `CulturePackStoreTest` a réussi dans la campagne unitaire debug
courante.

## Cycle de désinstallation et données conservées

La désinstallation doit distinguer trois ensembles :

- **payload du pack** : archive, catalogue éditorial et médias ; supprimables ;
- **préférences de l'extension** : activation, filtres, version ; conservées ou
  réinitialisées selon le choix explicite ;
- **données créées par l'utilisateur** : favoris, historique, progression,
  parcelles, constructions ; conservées par défaut.

Procédure cible : désactiver les routes et tâches planifiées, fermer les lecteurs,
supprimer l'archive, marquer l'état utilisateur « détaché », puis proposer
séparément « Effacer aussi mes données ». Une réinstallation compatible doit
rattacher cet état. Pour le Jardin, les tables Room ne doivent donc pas être
supprimées lors de la première extraction. Le flux actuel applique déjà la
partie protectrice de ce contrat : payload retiré/désactivé, alarmes annulées et
état Room laissé dormant.

## Sécurité actuelle du ZIP culturel

| Contrôle | Limite v1 |
|---|---:|
| Fichier compressé lu par le store | 32 Mio |
| Total décompressé | 32 Mio |
| Une entrée | 10 Mio |
| Nombre d'entrées ZIP | 128 |
| Capsules culturelles | 5 000 |
| `pack.json` | 128 Kio |
| `entries.json` | 8 Mio |
| Profondeur JSON | 32 |
| Valeurs JSON | 100 000 |

Fichiers racine obligatoires : `pack.json`, `entries.json`, `README.md` et
`LICENSE`. `sources.json` est optionnel. Les médias sont limités à PNG, JPEG,
WebP, GIF, MP3, OGG, WAV, M4A et Opus sous `media/`.

### Inspecteur d'extension générique actuellement présent

Le nouveau `.sankaipack` utilise d'autres plafonds : 64 Mio compressés et
décompressés, 16 Mio par entrée, 1 024 entrées et manifeste de 512 Kio. Il
accepte sous `assets/` ou `data/` JSON, texte, Markdown, images, sons et polices
TTF/OTF après contrôle de signature de format. Les chemins sont normalisés NFC,
les noms Windows réservés, fichiers cachés, doubles extensions actives et
archives imbriquées sont refusés.

Aucune archive `.sankaipack` Jardin de première partie n'est actuellement
présente dans le dépôt : le contrat, le store et l'interface sont branchés, pas
le livrable de contenu à installer.

Ce format n'impose encore aucun champ de licence, source, attribution ou statut
de droits dans `ExtensionManifest`. C'est bloquant avant une distribution
publique. Les polices augmentent aussi la surface de parsing ; si elles ne sont
pas indispensables, les retirer de la liste blanche v1 est le choix le plus
simple. Les 17 tests présents couvrent notamment types actifs, faux PNG, ZIP
bomb, checksums, compatibilité, installation, mise à jour invalide et
désinstallation avec snapshot ; il reste à les compléter par des tests Android
instrumentés, stockage plein et coupure de processus. Le rebuild final stable
a réussi 694/694 tests. `lintDebug` et `lintRelease` ont chacun produit
180 avertissements et zéro erreur. La compilation release, `assembleRelease`
et `bundleRelease` ont réussi avec R8/lint vital, `--no-parallel`,
`-Pksp.incremental=false` et Kotlin in-process. Aucun test instrumenté ni essai
sur appareil n'a été exécuté.

### Limite de confiance

Le SHA-256 prouve que le payload correspond au manifeste reçu. Il ne prouve
pas qui a créé ce manifeste : un attaquant peut modifier les deux. Pour une
bibliothèque publique distante, ajouter ultérieurement une signature de
manifeste avec clés d'éditeurs épinglées, révocation et rotation documentées.
Pour v1 local, afficher clairement la provenance du fichier et ne pas parler de
pack « signé » au sens cryptographique.

## Compatibilité et migrations de pack

- `schemaVersion` versionne le format, pas le contenu.
- `version` versionne le contenu d'un même `id`.
- `minAppVersionCode` et `maxAppVersionCode` bornent le moteur compatible.
- une mise à jour doit être validée intégralement avant remplacement ;
- un changement de schéma requiert un migrateur pur et des fixtures N-1/N ;
- le store doit pouvoir diagnostiquer un fichier invalide sans le masquer ;
- l'index doit être reconstructible depuis les archives installées.

Ne pas mettre l'état personnel dans l'archive immuable. Il doit vivre dans un
stockage séparé afin qu'une mise à jour de contenu n'écrase jamais favoris ou
historique.

## Tests obligatoires avec et sans pack

| Scénario | Sans pack | Avec pack valide | Pack invalide/incompatible |
|---|---|---|---|
| Démarrage | Aucun crash, état vide utile | Catalogue disponible | Diagnostic, app utilisable |
| Navigation | Capsule/extension affiche l'état vide | Écran fonctionnel | Aucune route morte |
| Mise à jour | Sans effet | Remplacement atomique | Ancienne version conservée |
| Désinstallation cible | Idempotente | Payload retiré, état personnel gardé | Fichier invalide retirable |
| Sauvegarde | Section absente acceptée | État exporté | Restauration ne force pas l'activation |
| Notifications | Aucune culture/jardin | Catégorie et budget respectés | Aucune tâche orpheline |
| Process death | État vide reconstruit | Index reconstruit | Invalidité visible |

Ajouter aussi les corpus hostiles : ZIP bomb, 129 entrées, entrée de 10 Mio + 1,
traversée, casse dupliquée, extension trompeuse, checksum absent, JSON profond,
source HTTP, licence vide et fichier tronqué pendant remplacement.

## Choix futur pour retirer réellement le code

### Dynamic Feature Play

À privilégier si Google Play est le canal principal et si l'on veut une
installation à la demande intégrée. Le module de base porte les contrats ; le
module `:feature:garden` porte UI, repositories, ressources et bytecode. La
livraison dépend de Play Feature Delivery et exige des tests des split APK.

### APK compagnon

À envisager pour une distribution GitHub indépendante. Le Jardin devient une
application distincte, signée par la même identité si un accès privilégié est
nécessaire, et communique par intents explicites ou provider signé. C'est une
surface produit, sécurité et sauvegarde beaucoup plus coûteuse.

### Pack local seul

À garder pour Culture et pour les catalogues/assets du Jardin. C'est le modèle
le plus simple et le plus sûr, mais il ne réduit que le payload remplaçable, pas
le moteur Kotlin compilé.

## Références officielles

- Android, modularisation : <https://developer.android.com/topic/modularization>
- Android, Play Feature Delivery : <https://developer.android.com/guide/playcore/feature-delivery>
- Android, App Bundle : <https://developer.android.com/guide/app-bundle>
- Android, stockage spécifique à l'application : <https://developer.android.com/training/data-storage/app-specific>
- Android, migrations Room : <https://developer.android.com/training/data-storage/room/migrating-db-versions>
- INPI, droit d'auteur : <https://www.inpi.fr/proteger-vos-creations/le-droit-dauteur>
