# 📋 Sankai Life — Tout ce dont j'ai besoin de toi

Ce fichier est **la check-list des informations que je ne peux pas inventer**.
Chaque section dit : à quoi ça sert, où l'obtenir, combien ça coûte, et où le coller.

Remplis au fur et à mesure. Rien n'est bloquant pour compiler l'APK — tout est
déjà fonctionnel avec des valeurs de test.

---

## 🟠 PRIORITÉ 1 — Pour publier sur le Play Store

### 1.1 Compte développeur Google Play

| Info | Détail |
|---|---|
| Où | https://play.google.com/console |
| Coût | **25 $ une seule fois**, à vie |
| Délai | Vérification d'identité : 1 à 3 jours |
| Prérequis | Pièce d'identité + adresse |

⚠️ Depuis 2023, un compte **personnel** neuf doit faire tester l'app par
**12 personnes pendant 14 jours consécutifs** avant de pouvoir publier
publiquement. Anticipe : prépare une liste de 12 adresses Gmail.
Un compte **organisation** (avec numéro SIRET/DUNS) n'a pas cette contrainte.

### 1.2 Éléments graphiques à fournir

| Élément | Format exact | Statut |
|---|---|---|
| Icône | 512 × 512 px, PNG 32 bits | ⚠️ à faire (une icône provisoire est générée) |
| Bannière | 1024 × 500 px, JPEG ou PNG | ⚠️ à faire |
| Captures téléphone | 2 à 8 images, min 320 px de côté | ⚠️ à faire (après installation) |

Outils gratuits : [Canva](https://canva.com), [Figma](https://figma.com),
[GIMP](https://gimp.org).

### 1.3 Textes à rédiger

| Texte | Limite | Statut |
|---|---|---|
| Titre | 30 caractères | Proposé : `Sankai Life` |
| Description courte | 80 caractères | Brouillon plus bas |
| Description longue | 4000 caractères | Brouillon plus bas |
| Politique de confidentialité | URL publique **obligatoire** | ⚠️ à héberger |

**Brouillon description courte (68 car.) :**
> Mémos motivants, focus et défis. Progresse chaque jour, hors ligne.

**Brouillon description longue :** voir
[`guides/PUBLIER-SUR-PLAY-STORE.md`](guides/PUBLIER-SUR-PLAY-STORE.md), section
« Fiche Play Store », prêt à copier-coller.

### 1.4 Politique de confidentialité

Le Play Store la demande. Elle doit être en ligne à une URL stable.

- Générateur gratuit : https://app-privacy-policy-generator.firebaseapp.com
- Hébergement gratuit : GitHub Pages, Notion (page publique), Google Sites

Le texte doit mentionner qu'aucune donnée personnelle ne quitte l'appareil
(c'est le cas ici), et que l'application est utilisable entièrement hors ligne.

---

## 🟡 PRIORITÉ 2 — Liens de l'app

Ces liens apparaissent dans l'écran Paramètres. Ils sont grisés hors connexion.

| Quoi | Valeur actuelle | À remplacer par |
|---|---|---|
| Page Ko-fi | *(non défini)* | `https://ko-fi.com/tonpseudo` |
| Site Souanpt / Sankai | *(non défini)* | `https://…` |
| Email de contact | *(non défini)* | obligatoire pour le Play Store |

Ko-fi : https://ko-fi.com — gratuit, 0 % de commission sur les dons.

---

## 🟢 PRIORITÉ 4 — Sécurité (ne pas perdre)

### La clé de signature

Générée automatiquement par `scripts/03-generer-keystore.ps1` dans `cles/`.

> ⚠️ **Si tu perds ce fichier ou son mot de passe, tu ne pourras plus JAMAIS
> mettre à jour ton app sur le Play Store.** Il faudrait republier sous un
> nouveau nom de package et repartir de zéro.

À sauvegarder **hors de cet ordinateur** :
- `cles/sankai-release.jks`
- `SankaiLife/keystore.properties` (contient le mot de passe)

Active aussi « Play App Signing » sur la Play Console : Google garde alors une
copie de secours de la clé.

---

## 🟢 PRIORITÉ 5 — Secrets GitHub (optionnel)

Le dépôt privé **https://github.com/N7T0-OF/sankai-life** compile l'APK tout
seul à chaque envoi de code. L'APK de test est produit sans rien configurer.

Pour que GitHub produise aussi l'**APK et l'AAB signés**, il faut lui confier la
clé de signature en secrets chiffrés :

| Secret GitHub | Où le trouver |
|---|---|
| `KEYSTORE_BASE64` | généré par `scripts/05-preparer-secrets-github.ps1` |
| `KEYSTORE_PASSWORD` | dans `SankaiLife/keystore.properties` |
| `KEY_ALIAS` | `sankai` |

Sans ces secrets, rien ne casse : le job signé se termine en vert sans produire
de fichier.

Guide : [`guides/GITHUB-RECUPERER-APK.md`](guides/GITHUB-RECUPERER-APK.md)

---

## 📦 Ce qui est DÉJÀ fait — rien à fournir

- ✅ Chaîne de compilation complète (JDK, Android SDK, Gradle) dans `outils/`
- ✅ Projet Android Kotlin + Jetpack Compose fonctionnel
- ✅ Mémos avec notifications locales et tirage anti-répétition
- ✅ Révisions espacées, focus timer, thèmes clair / sombre / auto
- ✅ Mode hors ligne intégral, aucune publicité
- ✅ Génération d'APK et d'AAB en un double-clic
- ✅ Dépôt GitHub privé + compilation automatique de l'APK à chaque envoi

---

## 🗺️ Ordre recommandé

1. Compiler l'APK et l'installer sur ton téléphone → double-clic sur
   `COMPILER-APK.bat`, puis `INSTALLER-SUR-TELEPHONE.bat`
2. Utiliser l'app une semaine, noter ce qui te déplaît
3. Faire l'icône et les visuels
4. Payer les 25 $ Play Console
5. Rédiger la politique de confidentialité
6. Publier en test fermé avec 12 testeurs
7. Attendre 14 jours, puis passer en production

---

## 📁 Où est quoi

```
Sankai life/
├── COMPILER-APK.bat                  ← double-clic : installe tout et compile
├── COMPILER-VERSION-PLAY-STORE.bat   ← génère l'AAB signé pour le Play Store
├── INSTALLER-SUR-TELEPHONE.bat       ← installe sur un téléphone en USB
├── SankaiLife/                       ← le projet Android (à ouvrir dans Android Studio)
├── scripts/                          ← les scripts d'automatisation
├── outils/                           ← JDK + Android SDK + Gradle (auto-installés)
├── dist/                             ← les APK et AAB générés
├── cles/                             ← la clé de signature (à sauvegarder !)
└── exemple/                          ← ce dossier : docs, configs, exemples
    ├── RESSOURCES-A-FOURNIR.md       ← tu es ici
    ├── guides/                       ← guides pas à pas
    ├── config/                       ← équilibrage en JSON (référence)
    ├── memos/                        ← contenus de mémo prêts à importer
    └── SANKAI_LIFE_SPEC_COMPLETE.md  ← la spec complète d'origine
```
