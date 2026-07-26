# 📋 Sankai Life — Tout ce dont j'ai besoin de toi

Ce fichier est **la check-list des informations que je ne peux pas inventer**.
Chaque section dit : à quoi ça sert, où l'obtenir, combien ça coûte, et où le coller.

Remplis au fur et à mesure. Rien n'est bloquant pour compiler l'APK — tout est
déjà fonctionnel avec des valeurs de test.

---

## 🔴 PRIORITÉ 1 — Pour gagner de l'argent (AdMob)

### 1.1 Compte Google AdMob

| Info | Détail |
|---|---|
| À quoi ça sert | Afficher les pubs récompensées et toucher les revenus |
| Où | https://admob.google.com |
| Coût | Gratuit |
| Délai | Validation du compte : 24 h à 2 semaines |
| Prérequis | Un compte Google + une adresse postale réelle + un RIB/IBAN |

### 1.2 Les 2 identifiants à me donner

Une fois l'app déclarée dans AdMob, tu obtiens :

```
ID d'application AdMob   →  ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
ID de bloc « Récompensé » →  ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
```

> Le premier contient un **tilde `~`**, le second une **barre `/`**. C'est le
> moyen le plus simple de ne pas les confondre.

**Où les coller :** crée le fichier `SankaiLife/admob.properties` :

```properties
ADMOB_APP_ID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
ADMOB_REWARDED_UNIT_ID=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
```

Tant que ce fichier n'existe pas, l'app utilise les **identifiants de test
officiels de Google** : les pubs s'affichent réellement, elles ne rapportent
simplement rien. C'est voulu — ça permet de tout tester sans compte.

Guide détaillé : [`guides/BRANCHER-GOOGLE-ADMOB.md`](guides/BRANCHER-GOOGLE-ADMOB.md)

### 1.3 Seuil de paiement

AdMob ne verse rien avant **70 € cumulés**. Prévois plusieurs mois avant le
premier virement, sauf grosse audience.

---

## 🟠 PRIORITÉ 2 — Pour publier sur le Play Store

### 2.1 Compte développeur Google Play

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

### 2.2 Éléments graphiques à fournir

| Élément | Format exact | Statut |
|---|---|---|
| Icône | 512 × 512 px, PNG 32 bits | ⚠️ à faire (une icône provisoire est générée) |
| Bannière | 1024 × 500 px, JPEG ou PNG | ⚠️ à faire |
| Captures téléphone | 2 à 8 images, min 320 px de côté | ⚠️ à faire (après installation) |

Outils gratuits : [Canva](https://canva.com), [Figma](https://figma.com),
[GIMP](https://gimp.org).

### 2.3 Textes à rédiger

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

### 2.4 Politique de confidentialité

Obligatoire dès qu'il y a de la pub. Elle doit être en ligne à une URL stable.

- Générateur gratuit : https://app-privacy-policy-generator.firebaseapp.com
- Hébergement gratuit : GitHub Pages, Notion (page publique), Google Sites

Le texte doit mentionner : Google AdMob, l'identifiant publicitaire, et le fait
qu'aucune donnée personnelle ne quitte l'appareil (c'est le cas ici).

---

## 🟡 PRIORITÉ 3 — Liens de l'app

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

## 📦 Ce qui est DÉJÀ fait — rien à fournir

- ✅ Chaîne de compilation complète (JDK, Android SDK, Gradle) dans `outils/`
- ✅ Projet Android Kotlin + Jetpack Compose fonctionnel
- ✅ Économie, XP, coffres, défis, streak, boutique équilibrés
- ✅ Mémos avec notifications locales et tirage anti-répétition
- ✅ Focus timer, thèmes clair / sombre / auto
- ✅ Mode hors ligne intégral
- ✅ Génération d'APK et d'AAB en un double-clic
- ✅ Intégration AdMob prête, en mode test

---

## 🗺️ Ordre recommandé

1. Compiler l'APK et l'installer sur ton téléphone → double-clic sur
   `COMPILER-APK.bat`, puis `INSTALLER-SUR-TELEPHONE.bat`
2. Utiliser l'app une semaine, noter ce qui te déplaît
3. Créer le compte AdMob (le délai de validation court en parallèle)
4. Faire l'icône et les visuels
5. Payer les 25 $ Play Console
6. Rédiger la politique de confidentialité
7. Publier en test fermé avec 12 testeurs
8. Attendre 14 jours, puis passer en production

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
