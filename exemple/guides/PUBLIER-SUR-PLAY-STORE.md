# 🚀 Publier Sankai Life sur le Google Play Store

---

## Vue d'ensemble

| Étape | Coût | Délai |
|---|---|---|
| Compte développeur | 25 $ une fois | 1 à 3 jours de vérification |
| Préparer les visuels | 0 € | 2 à 4 h |
| Test fermé (12 testeurs) | 0 € | **14 jours obligatoires** |
| Examen final par Google | 0 € | 1 à 7 jours |

Compte **personnel** créé après novembre 2023 : le test fermé de 14 jours avec
12 testeurs est obligatoire. Compte **organisation** (avec SIRET) : dispensé.

---

## Étape 1 — Générer le fichier à envoyer

Double-clic sur **`COMPILER-VERSION-PLAY-STORE.bat`**.

Le script crée la clé de signature au premier lancement, puis produit dans `dist/` :

- `SankaiLife-playstore-dernier.aab` → **c'est ce fichier que Google veut**
- `SankaiLife-release-dernier.apk` → pour installer directement, hors Play Store

> ⚠️ Sauvegarde immédiatement `cles/sankai-release.jks` et
> `SankaiLife/keystore.properties` ailleurs qu'ici. Sans eux, plus aucune mise
> à jour de l'app publiée n'est possible — jamais.

---

## Étape 2 — Créer le compte développeur

https://play.google.com/console → « Créer un compte développeur »

- Type : personnel ou organisation
- Paiement : 25 $
- Vérification d'identité : pièce d'identité officielle

---

## Étape 3 — Créer la fiche de l'application

**Créer une application** :

| Champ | Valeur |
|---|---|
| Nom | `Sankai Life` |
| Langue par défaut | Français (France) |
| Type | Application |
| Gratuite ou payante | **Gratuite** (irréversible) |

### Fiche Play Store

**Description courte** (80 car. max) :

```
Mémos motivants, focus et défis. Progresse chaque jour, même hors ligne.
```

**Description longue** (4000 car. max) :

```
Sankai Life transforme ce que tu as déjà écrit en motivation quotidienne.

Colle tes notes, tes citations, tes rappels : chaque ligne devient un message
que l'application t'envoie au fil de la journée. Pas de saisie fastidieuse, pas
de compte à créer, pas de connexion obligatoire.

🧠 MÉMO INTELLIGENT
Crée des modules à partir de n'importe quel texte copié. L'app choisit une
phrase au hasard et te la rappelle à l'heure que tu choisis, en évitant les
répétitions.

⏱️ FOCUS TIMER
Des sessions de concentration minutées. Chaque session terminée rapporte de
l'expérience et des pièces.

🎯 DÉFIS QUOTIDIENS ET HEBDOMADAIRES
Des objectifs courts qui donnent une raison de revenir chaque jour.

🎁 COFFRES ET PROGRESSION
Des coffres à débloquer, des niveaux à monter, une boutique où dépenser tes
pièces. La progression est lente et honnête : l'expérience ne s'achète pas et
ne se booste pas.

🔌 100 % HORS LIGNE
Tout fonctionne en mode avion : mémos, révisions, focus, capsules. Aucune
donnée n'est envoyée sur un serveur. Rien n'est stocké ailleurs que sur ton
téléphone.

🎨 SOBRE ET RAPIDE
Thème sombre AMOLED, thème clair, ou automatique selon ton téléphone. Interface
légère, animations discrètes, mode économie de batterie.

Aucune publicité : l'application est entièrement gratuite et ne contient aucun
SDK publicitaire.
```

---

## Étape 4 — Les questionnaires obligatoires

À remplir dans **Règles et programmes → Contenu de l'application** :

### Politique de confidentialité
URL publique obligatoire. Générateur : https://app-privacy-policy-generator.firebaseapp.com

### Accès à l'application
> « Toutes les fonctionnalités sont disponibles sans identifiants particuliers »

### Publicités
> **Non**, l'application ne contient aucune annonce (SDK AdMob retiré).

### Classification du contenu
Questionnaire à remplir. Réponses attendues pour Sankai Life : pas de violence,
pas de contenu sexuel, pas de jeux d'argent réel. Classification attendue :
**PEGI 3** ou **PEGI 7**.

> ⚠️ La question « éléments interactifs → achats numériques » : répondre **oui**
> si tu ajoutes plus tard la vente de gemmes.

### Public cible
Choisis **18 ans et plus** — viser les mineurs déclenche des obligations
supplémentaires (Families Policy, restrictions publicitaires strictes).

### Sécurité des données

C'est la section la plus scrutée. Pour Sankai Life :

| Question | Réponse |
|---|---|
| Collecte de données ? | **Non** |
| Partage avec des tiers ? | **Non** |
| Type de données | Aucune |
| Finalité | — |
| Chiffrement en transit | Sans objet |
| Suppression possible | Sans objet |

Toutes les données (mémos, révisions, statistiques) restent sur l'appareil et
**ne sont pas** à déclarer comme collectées. Les seules connexions réseau de
l'app sont les liens externes (site, Ko-fi) et la vérification de mises à jour
— elles ne collectent rien.

---

## Étape 5 — Test fermé

**Tests → Test fermé → Créer une version**

1. Envoyer le fichier `.aab`
2. Notes de version :
   ```
   Première version de Sankai Life.
   Mémos, focus timer, défis, coffres et progression. Fonctionne hors ligne.
   ```
3. Créer une liste de testeurs : 12 adresses Gmail minimum
4. Partager le lien d'inscription aux testeurs

Les 12 testeurs doivent **installer l'app et rester inscrits 14 jours
consécutifs**. Si l'un se désinscrit, le compteur repart.

---

## Étape 6 — Production

Après les 14 jours, Google débloque le bouton **« Passer en production »**.
Examen final : de quelques heures à une semaine.

---

## Erreurs de refus fréquentes

| Message | Cause | Correction |
|---|---|---|
| *Privacy policy not accessible* | URL morte ou privée | Vérifier l'URL en navigation privée |
| *Data safety mismatch* | Fiche Play Store et comportement de l'app différents | Relire les questionnaires Étape 4 |
| *Broken functionality* | Crash au lancement | Tester l'APK release, pas seulement le debug |
| *Target API level* | targetSdk trop bas | Déjà en 35 ici, rien à faire |

---

## Mettre à jour l'app plus tard

Dans `SankaiLife/app/build.gradle.kts` :

```kotlin
versionCode = 2          // +1 à CHAQUE envoi, sinon Google refuse
versionName = "1.1.0"    // visible par l'utilisateur
```

Puis relancer `COMPILER-VERSION-PLAY-STORE.bat` et envoyer le nouveau `.aab`.
