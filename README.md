# 📱 Sankai Life

**Productivité gamifiée, entièrement hors ligne.**
Application Android en Kotlin + Jetpack Compose.

[![Compiler l'APK](https://github.com/N7T0-OF/sankai-life/actions/workflows/build-apk.yml/badge.svg)](https://github.com/N7T0-OF/sankai-life/actions/workflows/build-apk.yml)

---

## ⬇️ Installer l'application

**→ [Télécharger la dernière version](https://github.com/N7T0-OF/sankai-life/releases/latest)**

Depuis ton téléphone : ouvre ce lien, télécharge le fichier `.apk`, puis ouvre-le.
Android demandera d'autoriser l'installation depuis cette source — c'est normal
pour une application distribuée hors Play Store.

> Le dépôt étant privé, il faut être connecté à un compte GitHub ayant accès
> pour télécharger.

Android 8.0 (API 26) minimum.

### Version de développement

Chaque envoi de code produit aussi un APK, disponible dans
[**Actions**](https://github.com/N7T0-OF/sankai-life/actions) → dernier run →
section *Artifacts*. Conservé 90 jours.

---

## 🧠 Ce que fait l'application

### Mode Vie — le cœur

**Mémo intelligent.** Colle n'importe quel texte : chaque ligne devient un
message. L'application t'en envoie un au hasard aux heures que tu choisis, en
évitant de répéter les dix derniers. Pensé pour recycler ce que tu as déjà
écrit ailleurs, pas pour te faire taper des paragraphes.

**Minuteur de concentration.** Sessions minutées, récompensées en expérience.

**Objectifs.** Une checklist personnelle. Chaque objectif validé rapporte une
fois, pas à chaque case cochée-décochée.

### Autour

| Écran | Contenu |
|---|---|
| Accueil | Une seule action recommandée, les coffres, la progression |
| Défis | Quotidiens et hebdomadaires |
| Boutique | Coffres, boosts, améliorations permanentes |
| Profil | Niveau, statistiques, badges, paramètres |

Économie à deux monnaies — pièces fréquentes, gemmes rares — avec des coffres à
minuteurs et quatre emplacements maximum, plus un streak quotidien.

---

## 🔌 Trois règles appliquées dans le code

**1. Tout fonctionne hors ligne.**
Mémos, concentration, défis, coffres, expérience, boutique, statistiques : tout
est local. Seules les publicités et les liens externes ont besoin du réseau, et
leurs boutons se grisent proprement sans connexion. Aucune donnée ne quitte le
téléphone.

**2. Aucune fonctionnalité derrière une publicité.**
Regarder une publicité rapporte des pièces. Ne jamais en regarder ne bloque
rien.

**3. L'expérience ne s'achète pas et ne se booste pas.**
Les boosts n'agissent que sur les pièces et les coffres. Le niveau reflète
uniquement ce que tu as réellement fait.

---

## 🛠️ Compiler soi-même

Aucun prérequis : ni Java, ni Android Studio, ni SDK.

```bash
COMPILER-APK.bat
```

Ce script télécharge et installe le JDK 17, l'Android SDK et Gradle dans
`outils/` (~600 Mo, la première fois seulement, sans droits administrateur),
puis compile l'APK dans `dist/`.

| Fichier | Rôle |
|---|---|
| `COMPILER-APK.bat` | APK de test |
| `COMPILER-VERSION-PLAY-STORE.bat` | APK + AAB signés pour le Play Store |
| `INSTALLER-SUR-TELEPHONE.bat` | Installation via USB |

Supprimer `outils/` désinstalle toute la chaîne, sans rien laisser dans Windows.

---

## 🚀 Publier une nouvelle version

```bash
git tag v1.1.0
git push origin v1.1.0
```

GitHub compile et crée la Release avec l'APK attaché, automatiquement.

Pense à incrémenter `versionCode` dans
[`SankaiLife/app/build.gradle.kts`](SankaiLife/app/build.gradle.kts) avant de
taguer.

---

## 🏗️ Architecture

```
SankaiLife/app/src/main/java/com/sankailife/
├── MainActivity.kt          point d'entrée, thème, écran de démarrage
├── SankaiApplication.kt     canaux de notification, WorkManager
├── core/
│   ├── connectivity/        détection en ligne / hors ligne
│   ├── notifications/       canaux, tâche mémo, planification
│   ├── data/                Room, DataStore, dépôts
│   └── domain/engine/       XP, économie, coffres, streak, mémo
└── ui/
    ├── theme/               couleurs, typographie, thèmes clair et sombre
    ├── components/          composants réutilisables
    ├── navigation/          graphe de navigation, barre du bas
    └── screens/             un dossier par écran (écran + ViewModel)
```

MVVM sans Hilt : les ViewModels sont créés par des factories manuelles dans
`ui/navigation/NavGraph.kt`.

| | |
|---|---|
| Kotlin | 2.0.21 |
| Interface | Jetpack Compose, Material 3 |
| Base de données | Room |
| Préférences | DataStore |
| Tâches de fond | WorkManager |
| Publicité | Aucune — 100 % hors ligne |
| Android | 8.0 minimum, cible 15 |
| Build | Gradle 8.9, AGP 8.7.3, JDK 17 |

**Tout l'équilibrage tient dans un seul fichier** :
[`core/domain/engine/Engines.kt`](SankaiLife/app/src/main/java/com/sankailife/core/domain/engine/Engines.kt) —
prix, gains, temps de recharge, minuteurs de coffres, courbe d'expérience.

---

## 📖 Documentation

| Guide | Sujet |
|---|---|
| [Ressources à fournir](exemple/RESSOURCES-A-FOURNIR.md) | Ce qu'il reste à créer : comptes, visuels, textes |
| [Publier sur le Play Store](exemple/guides/PUBLIER-SUR-PLAY-STORE.md) | Compte, fiche, questionnaires, test fermé |
| [Récupérer l'APK depuis GitHub](exemple/guides/GITHUB-RECUPERER-APK.md) | Artefacts, Releases, secrets de signature |
| [Android Studio et Cursor](exemple/guides/ANDROID-STUDIO-ET-CURSOR.md) | Modifier le code |
| [Dépannage](exemple/guides/DEPANNAGE.md) | Quand ça coince |
| [Design system](exemple/design/DESIGN-SYSTEM.md) | Couleurs, formes, composants |
| [Équilibrage](exemple/config/equilibrage.json) | Tous les chiffres du jeu |

---

## 🔐 Ce que ce dépôt ne contient pas

Volontairement exclus par [`.gitignore`](.gitignore) :

| Exclu | Pourquoi |
|---|---|
| `cles/`, `*.jks` | La clé de signature ne doit jamais être versionnée |
| `keystore.properties` | Contient le mot de passe de la clé |
| `outils/` | Toolchain d'environ 1 Go, réinstallable en une commande |
| `dist/` | Les APK sont régénérables |

