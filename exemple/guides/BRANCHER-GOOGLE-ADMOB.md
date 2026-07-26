# 💰 Brancher Google AdMob (gagner de l'argent)

Durée : ~30 min de manipulation, plus le délai de validation du compte.

---

## Comment ça marche aujourd'hui

L'intégration AdMob est **déjà codée et fonctionnelle**. L'app compile avec les
**identifiants de test officiels de Google** : de vraies pubs s'affichent, mais
elles ne génèrent aucun revenu.

Passer en production = créer un compte et remplacer deux identifiants. Aucune
ligne de code à toucher.

Le code concerné :
- `SankaiLife/app/src/main/java/com/sankailife/core/ads/AdsManager.kt` — chargement et affichage
- `SankaiLife/app/src/main/java/com/sankailife/core/ads/RegarderPubUseCase.kt` — récompenses et paliers
- `SankaiLife/app/build.gradle.kts` — lecture de `admob.properties`

---

## Étape 1 — Créer le compte AdMob

1. Va sur https://admob.google.com et connecte-toi avec ton compte Google
2. Renseigne pays, fuseau horaire, devise (⚠️ **la devise est définitive**)
3. Accepte les conditions

Il faudra ensuite fournir une adresse postale et des coordonnées bancaires pour
être payé. Google envoie un code PIN par courrier postal une fois 10 € atteints.

---

## Étape 2 — Déclarer l'application

Dans AdMob : **Applications → Ajouter une application**

- Plateforme : **Android**
- « L'application est-elle publiée sur une plateforme ? » → **Non** (pour l'instant)
- Nom : `Sankai Life`

Tu obtiens l'**ID d'application** :

```
ca-app-pub-1234567890123456~1234567890
                            ↑ tilde
```

---

## Étape 3 — Créer le bloc d'annonces récompensé

**Blocs d'annonces → Ajouter un bloc d'annonces → Avec récompense**

| Champ | Valeur à mettre |
|---|---|
| Nom du bloc | `Sankai Recompense Pieces` |
| Élément de récompense | `Pieces` |
| Montant de la récompense | `5` |

Tu obtiens l'**ID de bloc** :

```
ca-app-pub-1234567890123456/9876543210
                            ↑ barre oblique
```

> Un nouveau bloc met **jusqu'à 1 heure** avant de servir des pubs. Vide au
> début = normal, ce n'est pas un bug.

---

## Étape 4 — Coller les identifiants

Crée le fichier `SankaiLife/admob.properties` :

```properties
ADMOB_APP_ID=ca-app-pub-1234567890123456~1234567890
ADMOB_REWARDED_UNIT_ID=ca-app-pub-1234567890123456/9876543210
```

Puis recompile :

```bash
COMPILER-APK.bat
```

Ce fichier est dans le `.gitignore` : il ne partira jamais sur GitHub.

---

## Étape 5 — Tester sans risquer un bannissement

⚠️ **Cliquer sur ses propres vraies pubs fait bannir le compte AdMob, sans
avertissement et sans recours.**

Déclare ton téléphone comme appareil de test :

1. Lance l'app avec les vrais IDs, ouvre le logcat dans Android Studio
2. Cherche une ligne du type :
   `Use RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList("ABC123..."))`
3. Ajoute cet identifiant dans AdMob : **Paramètres → Appareils de test**

Ou plus simple : garde `admob.properties` absent pendant tes tests, ce qui
laisse les IDs de test actifs.

---

## Les règles à respecter (déjà appliquées dans le code)

| Règle AdMob | Comment c'est géré ici |
|---|---|
| Pas de pub forcée pour utiliser l'app | Aucune fonctionnalité n'est derrière une pub |
| Pas de spam de pubs | Cooldown de 25 s (`EconomyEngine.AD_COOLDOWN_SEC`) |
| Volume raisonnable | Plafond de 50 pubs/jour (`MAX_ADS_PER_DAY`) |
| Récompense uniquement si vue entière | Crédit sur `AdResult.Rewarded` seulement |
| Pas de clic incité | Le texte ne dit jamais « clique sur la pub » |

Modifier ces valeurs : `core/domain/engine/Engines.kt`, objet `EconomyEngine`.

---

## Combien ça rapporte, concrètement

Ordres de grandeur pour une pub récompensée en France :

| Métrique | Valeur typique |
|---|---|
| eCPM (revenu pour 1000 pubs vues) | 8 à 20 € |
| Revenu par pub vue | ~0,01 € |
| 100 utilisateurs actifs × 5 pubs/jour | ~5 €/jour |
| Seuil de versement AdMob | 70 € |

Autrement dit : les revenus viennent du **nombre d'utilisateurs**, pas du
nombre de pubs par utilisateur. Augmenter le plafond de 50 pubs/jour ne
changerait quasiment rien — sinon dégrader l'expérience et attirer l'attention
de la modération AdMob.

---

## Aller plus loin (plus tard)

- **Consentement RGPD** : obligatoire pour les utilisateurs européens. Ajouter
  le SDK *User Messaging Platform* (`com.google.android.ump:user-messaging-platform`).
  À faire **avant** la publication en Europe.
- **Achats intégrés** : vendre des gemmes via Google Play Billing.
- **Médiation** : brancher plusieurs régies pour faire monter l'eCPM. Utile
  seulement au-delà de quelques milliers d'utilisateurs.
