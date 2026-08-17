# Schéma des packs culturels Sankai — v1

Date de référence : 8 août 2026
Implémentation de référence : `core/culture`.

## Statut

Ce document décrit le format **effectivement compris par le code actuel**. Un
pack culturel v1 est une archive ZIP de données passives. L'extension de
fichier recommandée est `.culturepack`, utilisée par `CulturePackStore`.

Le pack ne peut pas ajouter une fonctionnalité Kotlin : il apporte des textes,
des métadonnées et éventuellement des images ou sons. Installer ou
désinstaller le pack agit sur ces assets et données uniquement ; le moteur de
lecture et de sélection reste compilé dans l'APK.

Le dossier `app/src/main/assets/culture/classics-fr-v1` est une **forme source
d'exemple**, pas une archive installée dans `CulturePackStore`. Les tests et le
nouveau `CapsulesViewModel` le transforment en ZIP en mémoire puis le passent au
même importeur. L'écran Capsules et sa destination `NavHost` sont maintenant
présents. Ils lisent toutefois uniquement ce pack embarqué : aucun fichier
`.culturepack` externe ne peut encore être choisi dans l'UI.

## Structure de l'archive

```text
pack.json       obligatoire
entries.json    obligatoire
README.md       obligatoire
LICENSE         obligatoire
sources.json    facultatif
media/          facultatif
  cover.webp
  lecture.ogg
```

Tous les fichiers autres que `pack.json` doivent être déclarés dans `files` et
inclus dans `payloadBytes`. Aucun fichier déclaré ne peut manquer et aucun
fichier supplémentaire ne peut être présent.

Types autorisés sous `media/` : `.png`, `.jpg`, `.jpeg`, `.webp`, `.gif`,
`.mp3`, `.ogg`, `.wav`, `.m4a` et `.opus`. HTML, SVG, scripts, DEX, JAR, APK,
polices et formats non listés sont refusés.

## `pack.json`

### Exemple minimal

```json
{
  "schemaVersion": 1,
  "id": "classics-fr",
  "version": "1.0.0",
  "title": "Classiques français",
  "description": "Capsules sourcées et consultables hors connexion.",
  "languages": ["fr"],
  "license": "Domaine public + notices CC0-1.0",
  "sourceLabel": "Sources détaillées dans README.md",
  "minAppVersionCode": 80,
  "entryCount": 3,
  "payloadBytes": 5533,
  "files": {
    "entries.json": "<sha256 hexadécimal sur 64 caractères>",
    "README.md": "<sha256 hexadécimal sur 64 caractères>",
    "LICENSE": "<sha256 hexadécimal sur 64 caractères>"
  }
}
```

### Champs et contraintes

| Champ | Type | Contraintes v1 |
|---|---|---|
| `schemaVersion` | entier | exactement `1` |
| `id` | chaîne | `^[a-z0-9]+(?:[._-][a-z0-9]+)*$` |
| `version` | chaîne | 1 à 64 caractères, alphanumérique puis alphanumérique/`.`/`_`/`+`/`-` |
| `title` | chaîne | non vide, 120 caractères maximum |
| `description` | chaîne | non vide, 1 000 caractères maximum |
| `languages` | tableau unique après lecture | au moins une langue conforme au sous-ensemble BCP-47 accepté |
| `license` | chaîne | non vide, 200 caractères maximum |
| `sourceLabel` | chaîne | non vide, 500 caractères maximum |
| `minAppVersionCode` | entier | strictement positif |
| `maxAppVersionCode` | entier facultatif | supérieur ou égal au minimum |
| `entryCount` | entier | 1 à 5 000 et égal au nombre réel d'entrées |
| `payloadBytes` | entier | somme exacte des fichiers hors manifeste, entre 1 et 32 Mio |
| `files` | objet chemin → SHA-256 | toutes les charges utiles, aucune autre ; hash hexadécimal de 64 caractères |

Langues acceptées : code ISO en minuscules sur 2 ou 3 lettres, suivi
éventuellement d'un script et/ou d'une région, par exemple `fr`, `pt-BR` ou
`zh-Hans-CN`. Les variantes BCP-47 plus complexes ne sont pas comprises en v1.

`pack.json` ne se référence jamais lui-même : son empreinte ne peut pas vivre
dans le document qu'elle modifierait.

## `entries.json`

Racine obligatoire :

```json
{
  "entries": [
    {
      "id": "hugo-demain-des-aube",
      "type": "POEM",
      "title": "Demain, dès l'aube…",
      "body": "Texte…",
      "author": "Victor Hugo",
      "authorBirthYear": 1802,
      "authorDeathYear": 1885,
      "workDate": "3 septembre 1847",
      "publicationDate": "1856",
      "countryCode": "FR",
      "languageCode": "fr",
      "context": "Contexte éditorial…",
      "sourceLabel": "Les Contemplations, Wikisource",
      "sourceUrl": "https://fr.wikisource.org/…",
      "rightsStatus": "PUBLIC_DOMAIN",
      "license": "Domaine public",
      "tags": ["poésie", "mémoire"],
      "difficulty": "STANDARD"
    }
  ]
}
```

### Valeurs fermées

`type` : `POEM`, `QUOTE`, `PROVERB`, `ARTWORK`, `HISTORY`, `SCIENCE`, `WORD`
ou `BIOGRAPHY`.

`rightsStatus` :

- `PUBLIC_DOMAIN` : œuvre distribuable après vérification juridique ;
- `CREATIVE_COMMONS` : licence CC précise et obligations respectées ;
- `LICENSED` : autorisation contractuelle documentée ;
- `METADATA_ONLY` : aucune reproduction du corps protégé.

`difficulty` : `LIGHT`, `STANDARD` ou `DEEP`. Le champ est facultatif et vaut
`STANDARD` par défaut. Il sert à varier les lectures, pas à classer l'utilisateur.

### Champs d'une entrée

| Champ | Présence | Contraintes v1 |
|---|---|---|
| `id` | obligatoire | même grammaire que l'identifiant de pack ; unique dans le catalogue |
| `type` | obligatoire | valeur fermée ci-dessus |
| `title` | obligatoire | non vide, 300 caractères maximum |
| `body` | selon type/droits | 30 000 caractères maximum ; interdit pour `METADATA_ONLY` ; requis pour poème, citation, proverbe et mot distribués |
| `author` | facultatif | 200 caractères maximum |
| `authorBirthYear`, `authorDeathYear` | facultatifs | de -5000 à 3000 ; décès non antérieur à la naissance |
| `workDate`, `publicationDate` | facultatifs | chaînes documentaires ; pas de parsing juridique automatique |
| `countryCode` | facultatif | deux lettres majuscules |
| `languageCode` | obligatoire | langue valide et déclarée par le manifeste |
| `context` | facultatif | 4 000 caractères maximum |
| `sourceLabel` | obligatoire en validation | non vide, 500 caractères maximum |
| `sourceUrl` | facultatif | URL commençant par `https://` |
| `rightsStatus` | obligatoire | valeur fermée ci-dessus |
| `license` | obligatoire sauf métadonnées seules | non vide, 200 caractères maximum |
| `tags` | facultatif | 20 maximum, uniques, 60 caractères maximum chacun |
| `difficulty` | facultatif | `STANDARD` par défaut |
| `mediaPath` | facultatif | chemin `media/...` présent et déclaré dans l'archive |

Une chaîne facultative vide est traitée comme absente. Les champs textuels
contrôlés refusent le caractère NUL. Les clés JSON dupliquées, nombres hors
limites et valeurs d'énumération inconnues sont refusés.

## Intégrité, sûreté et limites

Le lecteur applique les contrôles suivants avant installation :

- au plus 32 Mio compressés lus par le store ;
- au plus 32 Mio décompressés, 10 Mio par entrée et 128 entrées ;
- `pack.json` au plus 128 Kio et `entries.json` au plus 8 Mio ;
- profondeur JSON 32 et 100 000 valeurs maximum ;
- chemins relatifs uniquement, sans antislash, deux-points, NUL, segment vide,
  `.` ou `..` ;
- refus des doublons de chemin, y compris ceux qui ne diffèrent que par la casse ;
- UTF-8 strict ;
- égalité exacte entre contenu ZIP, liste du manifeste et taille annoncée ;
- SHA-256 vérifié pour chaque payload ;
- compatibilité avec le `versionCode` de l'application.

Le store conserve l'archive validée sans l'extraire sur le système de fichiers,
ce qui réduit la surface de traversée de chemins. L'installation valide tout en
mémoire avant d'écrire dans un fichier temporaire, puis remplace atomiquement
la version précédente. Une mise à jour invalide conserve l'ancienne version.

### SHA-256 n'est pas une signature d'éditeur

Les checksums détectent corruption et divergence par rapport au manifeste.
Ils ne prouvent pas l'identité du créateur, car un tiers peut recalculer le
manifeste. L'interface v1 doit dire « intégrité vérifiée », jamais « éditeur
authentifié ». Une distribution distante future devra signer le manifeste et
gérer des clés d'éditeurs approuvées.

## Droits, licences et attribution

Le moteur impose une source par entrée et une licence pour tout contenu
reproduit, mais il ne peut pas décider juridiquement qu'une œuvre est dans le
domaine public. Chaque pack doit conserver une fiche de provenance : auteur,
œuvre, dates, édition/source consultée, URL, statut, licence, date de contrôle
et personne ayant contrôlé.

Précautions :

- ne pas déduire automatiquement le domaine public de la seule date de décès ;
- tenir compte du territoire, des prorogations éventuelles et du droit moral ;
- distinguer l'œuvre de sa traduction, photographie, transcription ou édition ;
- pour une licence Creative Commons, conserver la version exacte, l'URL, le
  crédit demandé et les modifications ;
- si le droit de reproduction est incertain, distribuer uniquement les
  métadonnées avec `METADATA_ONLY` et `body: null` ;
- faire une recherche de marque distincte pour le nom du pack et son visuel.

Le pack d'exemple documente Victor Hugo, Joachim du Bellay et Blaise Pascal,
leurs sources Wikisource et une mise à disposition CC0 des notices originales.
Cela constitue un exemple, pas une validation automatique de futurs contenus.

## Installation, mise à jour et désinstallation

- même `id` + nouvelle `version` : remplacement atomique après validation complète ;
- version incompatible : refus avant écriture ;
- fichier corrompu déjà présent : état `Invalid` visible dans le diagnostic ;
- désinstallation : suppression du fichier `<id>.culturepack` ;
- préférences, favoris et historique utilisateur doivent vivre hors de
  l'archive et être conservés par défaut ;
- réinstallation : rattachement de l'état personnel seulement après contrôle
  de compatibilité.

Le store actuel sait installer/charger/désinstaller l'archive. La séparation de
l'état personnel est amorcée par l'écran embarqué : sélection, favoris et
réflexion sont écrits atomiquement sous `noBackupFilesDir`, donc exclus de la
sauvegarde cloud Android. L'import de packs externes et le raccordement de cet
état à `CulturePackStore` ne sont pas encore implémentés.

## Couverture de tests présente

Le lot `core/culture` contient 28 tests ciblés couvrant notamment : archive
valide, empreinte incorrecte, exécutable refusé, traversée, doublon de casse,
compatibilité, nombre/taille, média passif, métadonnées seules, identifiants,
JSON dupliqué, remplacement atomique, conservation de l'ancienne version,
diagnostic d'un pack corrompu et sélection quotidienne déterministe.
Ces cas ont réussi au sein de la campagne `testDebugUnitTest` finale, verte à
694/694. `lintDebug` et `lintRelease` comptent chacun 180 avertissements et zéro
erreur. La compilation release, `assembleRelease` et `bundleRelease` ont réussi,
y compris R8 et le lint vital, avec `--no-parallel`,
`-Pksp.incremental=false` et Kotlin in-process. Aucun test instrumenté ni essai
sur appareil n'a été exécuté.

À ajouter avant UI publique : fuzzing du parseur, archive tronquée, limites
exactes ±1, coupure disque pendant remplacement, stockage plein, concurrence
install/load/uninstall, reconstruction d'index et tests instrumentés sur API 26.

## Évolutions possibles du schéma

Un schéma v2 devrait envisager des champs structurés `licenseSpdx`,
`licenseUrl`, `attribution`, `rightsTerritories`, `rightsCheckedOn`,
`contentSha256` et `publisherKeyId`. Ne pas ajouter silencieusement ces champs à
la sémantique v1 : augmenter `schemaVersion` et fournir une migration testée.

## Références

- Android, stockage privé de l'application : <https://developer.android.com/training/data-storage/app-specific>
- Android, recommandations d'architecture des données : <https://developer.android.com/topic/architecture/data-layer>
- INPI, droit d'auteur : <https://www.inpi.fr/proteger-vos-creations/le-droit-dauteur>
- INPI, base publique de propriété industrielle : <https://data.inpi.fr/>
- Creative Commons, CC0 1.0 : <https://creativecommons.org/publicdomain/zero/1.0/>
- OWASP, validation des fichiers téléversés : <https://owasp.org/www-community/vulnerabilities/Unrestricted_File_Upload>
