# 🛠️ Ouvrir le projet dans Android Studio et Cursor

Le projet compile **sans Android Studio** (les scripts `.bat` suffisent).
Android Studio devient utile pour : voir l'aperçu de l'interface, déboguer,
lire les logs, utiliser un émulateur.

---

## Android Studio

### Installation
https://developer.android.com/studio — gratuit, ~1 Go.

### Ouvrir le projet
**File → Open** → sélectionner le dossier **`SankaiLife`**
(pas le dossier parent `Sankai life`, sinon Gradle ne trouve rien).

### Utiliser la toolchain déjà installée

Les scripts ont déjà téléchargé un JDK, un SDK Android et Gradle dans `outils/`.
Deux options :

**Option A — laisser Android Studio installer les siens** (le plus simple).
Il proposera de télécharger ce qui manque, accepte. Les deux toolchains
cohabitent sans conflit.

**Option B — réutiliser celle des scripts** (économise ~1 Go) :

- *Settings → Build → Build Tools → Gradle → Gradle JDK* → **Add JDK** →
  `outils\jdk`
- *Settings → Languages & Frameworks → Android SDK* → *Android SDK Location* →
  `outils\android-sdk`

> `SankaiLife/local.properties` pointe déjà sur `outils/android-sdk`. Ce fichier
> n'est pas versionné : chaque machine a le sien.

### Lancer l'app
Brancher un téléphone (débogage USB activé) ou créer un émulateur
(*Device Manager → Create Device*), puis ▶ **Run**.

---

## Cursor (ou VS Code)

Cursor est excellent pour écrire du Kotlin avec de l'IA, mais **ne compile pas
d'Android** tout seul. Le duo qui marche :

- **Cursor** pour éditer le code
- **`COMPILER-APK.bat`** pour compiler
- **Android Studio** quand tu as besoin d'un débogueur ou d'un émulateur

### Ouvrir
**File → Open Folder** → le dossier **`Sankai life`** entier (pour avoir aussi
`exemple/` et `scripts/` sous la main).

### Extension recommandée
`Kotlin Language` (fwcd) — coloration syntaxique et complétion de base.

### Prompt de démarrage à donner à Cursor

```
Projet Android Kotlin + Jetpack Compose déjà fonctionnel, dossier SankaiLife/.
Architecture MVVM : Room pour la base, DataStore pour les préférences,
Navigation Compose, pas de Hilt (les ViewModels sont créés via des factories
manuelles dans ui/navigation/NavGraph.kt).

Règles à respecter absolument :
1. L'app doit rester utilisable à 100 % hors ligne. Rien ne doit dépendre du
   réseau à part les pubs et les liens externes.
2. L'XP n'est jamais boostable ni achetable.
3. Aucune fonctionnalité ne doit être placée derrière une publicité.
4. Thème sombre AMOLED par défaut, thème clair et automatique disponibles.
5. Le design system est dans ui/theme/ : n'utilise que MaterialTheme.sankaiColors,
   jamais de couleurs codées en dur.

La spec fonctionnelle complète est dans exemple/SANKAI_LIFE_SPEC_COMPLETE.md.
L'équilibrage chiffré est dans exemple/config/equilibrage.json.
```

---

## Où se trouve quoi dans le code

```
SankaiLife/app/src/main/java/com/sankailife/
├── MainActivity.kt              point d'entrée, thème, splash
├── SankaiApplication.kt         init : canaux de notif, WorkManager
├── core/
│   ├── connectivity/            détection en ligne / hors ligne
│   ├── notifications/           canaux, worker mémo, planification
│   ├── data/
│   │   ├── db/                  Room : base, entités, DAOs
│   │   ├── preferences/         DataStore : réglages
│   │   └── repository/          GameRepository, UserRepository
│   └── domain/engine/           XpEngine, EconomyEngine, ChestEngine, StreakEngine, MemoEngine
└── ui/
    ├── theme/                   couleurs, typo, thèmes clair/sombre
    ├── components/              composants réutilisables (cartes, boutons, barres)
    ├── navigation/              NavGraph, barre du bas, routes
    └── screens/                 un dossier par écran (écran + ViewModel)
```

### Modifier l'équilibrage
Un seul fichier : `core/domain/engine/Engines.kt`.
Prix, gains, cooldowns, timers de coffres, courbe d'XP y sont tous regroupés.

### Modifier les couleurs
`ui/theme/Color.kt` et `ui/theme/Theme.kt`.

### Ajouter un écran
1. Créer `ui/screens/monecran/MonEcranScreen.kt` + `MonEcranViewModel.kt`
2. Ajouter la route dans `ui/navigation/Screen.kt`
3. Ajouter le `composable(...)` dans `ui/navigation/NavGraph.kt`
