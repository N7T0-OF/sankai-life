# 📱 Sankai Life

Application Android de productivité gamifiée, **offline-first**.
Kotlin + Jetpack Compose. Tout est prêt à compiler.

---

## ⚡ Démarrage : un double-clic

```
COMPILER-APK.bat
```

Ce fichier fait tout, tout seul :

1. Télécharge et installe le JDK 17, l'Android SDK et Gradle dans `outils/`
   *(~600 Mo, uniquement la première fois, aucun droit administrateur requis)*
2. Compile l'application
3. Dépose l'APK dans `dist/`

Comptez 5 à 15 minutes au premier lancement, moins d'une minute ensuite.

Puis, pour installer sur ton téléphone :

```
INSTALLER-SUR-TELEPHONE.bat
```

*(ou copie simplement le `.apk` de `dist/` sur le téléphone et ouvre-le)*

---

## 📂 Le répertoire

| Dossier | Contenu |
|---|---|
| `SankaiLife/` | Le projet Android — à ouvrir dans Android Studio |
| `scripts/` | Les scripts d'automatisation PowerShell |
| `outils/` | JDK + Android SDK + Gradle, installés automatiquement |
| `dist/` | Les APK et AAB générés |
| `cles/` | La clé de signature — **à sauvegarder ailleurs** |
| `exemple/` | Documentation, guides, configs, contenus d'exemple |

### Les trois raccourcis

| Fichier | Ce qu'il fait |
|---|---|
| `COMPILER-APK.bat` | Installe les outils si besoin, puis génère l'APK de test |
| `COMPILER-VERSION-PLAY-STORE.bat` | Génère l'APK et l'AAB **signés** pour publier |
| `INSTALLER-SUR-TELEPHONE.bat` | Installe le dernier APK via USB |

---

## 🐙 Ou sans rien installer du tout

Le code est sur un dépôt **privé** : https://github.com/N7T0-OF/sankai-life

À chaque `git push`, GitHub compile l'APK sur ses serveurs. Tu le récupères
depuis n'importe quel appareil dans l'onglet
[**Actions**](https://github.com/N7T0-OF/sankai-life/actions) → dernier run →
section *Artifacts*.

Détails : [`exemple/guides/GITHUB-RECUPERER-APK.md`](exemple/guides/GITHUB-RECUPERER-APK.md)

---

## 📖 Par où continuer

| Je veux… | Fichier |
|---|---|
| Savoir ce que je dois te fournir | [`exemple/RESSOURCES-A-FOURNIR.md`](exemple/RESSOURCES-A-FOURNIR.md) |
| Gagner de l'argent avec les pubs | [`exemple/guides/BRANCHER-GOOGLE-ADMOB.md`](exemple/guides/BRANCHER-GOOGLE-ADMOB.md) |
| Publier sur le Play Store | [`exemple/guides/PUBLIER-SUR-PLAY-STORE.md`](exemple/guides/PUBLIER-SUR-PLAY-STORE.md) |
| Modifier le code | [`exemple/guides/ANDROID-STUDIO-ET-CURSOR.md`](exemple/guides/ANDROID-STUDIO-ET-CURSOR.md) |
| Récupérer l'APK depuis GitHub | [`exemple/guides/GITHUB-RECUPERER-APK.md`](exemple/guides/GITHUB-RECUPERER-APK.md) |
| Régler un problème | [`exemple/guides/DEPANNAGE.md`](exemple/guides/DEPANNAGE.md) |
| Ajuster l'équilibrage | [`exemple/config/equilibrage.json`](exemple/config/equilibrage.json) |
| Relire la spec complète | [`exemple/SANKAI_LIFE_SPEC_COMPLETE.md`](exemple/SANKAI_LIFE_SPEC_COMPLETE.md) |

---

## 🧠 Ce que fait l'app

**Mode Vie** — le cœur

- **Mémo intelligent** : colle n'importe quel texte, chaque ligne devient un
  message. L'app t'en envoie un au hasard aux heures choisies, sans jamais
  répéter les 10 derniers.
- **Focus timer** : sessions de concentration minutées, récompensées en XP.
- **Objectifs** : checklist personnelle.

**Autour**

- **Accueil** : une seule action recommandée, les coffres, la progression.
- **Défis** : quotidiens et hebdomadaires.
- **Boutique** : coffres, boosts, améliorations permanentes.
- **Profil** : niveau, statistiques, badges.

**Économie** — pièces (fréquentes) et gemmes (rares), coffres à timers façon
Clash Royale, 4 emplacements maximum, streak quotidien.

---

## 🔌 Trois règles non négociables

Elles sont appliquées dans le code, pas seulement écrites ici.

**1. L'app fonctionne intégralement hors ligne.**
Mémos, focus, défis, coffres, XP, boutique, statistiques : tout est local.
Seuls les publicités et les liens externes ont besoin du réseau, et leurs
boutons se grisent proprement quand il n'y en a pas.

**2. Aucune fonctionnalité n'est derrière une publicité.**
Regarder une pub rapporte des pièces. Ne jamais en regarder ne bloque rien.

**3. L'XP ne s'achète pas et ne se booste pas.**
Les boosts n'agissent que sur les pièces et les coffres. Le niveau reflète
uniquement ce que tu as réellement fait.

---

## 🔧 Sous le capot

| | |
|---|---|
| Langage | Kotlin 2.0.21 |
| Interface | Jetpack Compose (Material 3) |
| Architecture | MVVM, factories manuelles (pas de Hilt) |
| Base de données | Room |
| Préférences | DataStore |
| Tâches de fond | WorkManager |
| Publicité | Google AdMob — récompensée uniquement |
| Android minimum | 8.0 (API 26) |
| Android cible | 15 (API 35) |
| Build | Gradle 8.9, AGP 8.7.3, JDK 17 |

---

## ⚠️ À ne pas perdre

Après le premier `COMPILER-VERSION-PLAY-STORE.bat`, sauvegarde **hors de cet
ordinateur** :

- `cles/sankai-release.jks`
- `SankaiLife/keystore.properties`

Sans ces deux fichiers, une application publiée ne peut plus jamais être mise à
jour. Ni par toi, ni par Google.

Ces fichiers, comme `SankaiLife/admob.properties`, sont exclus de Git.
