# État d'implémentation — Sankai Life

Suivi honnête du cahier des charges. Une ligne n'est marquée **terminé** que si
le code compile et que la fonction est réellement câblée de bout en bout.

Dernière mise à jour : build `assembleDebug` et `assembleRelease` verts.

---

## Vue d'ensemble

| # | Fonction | État | Fichiers | Remarques |
|---|---|---|---|---|
| 1 | Vibrations | ✅ terminé | `core/haptics/HapticManager.kt`, `MainActivity.kt`, `ui/components/CommonComponents.kt`, `ui/navigation/BottomNavBar.kt` | À tester sur appareil |
| 2 | AdMob récompensé | ✅ terminé | `core/ads/AdsManager.kt`, `core/ads/RegarderPubUseCase.kt`, `app/build.gradle.kts` | IDs réels en release, test en debug |
| 3 | Notifications exactes | ✅ terminé | `core/notifications/MemoAlarmScheduler.kt`, `MemoAlarmReceiver.kt`, `SystemEventsReceiver.kt`, `MemoScheduleEngine.kt` | **À tester sur appareil** |
| 4 | Éditeur heure + minutes | 🟠 partiel | `MemoScheduleEngine.kt`, `AllEntities.kt` | Modèle et moteur prêts, **UI éditeur pas encore branchée** |
| 5 | Notification aléatoire | 🟠 partiel | `MemoScheduleEngine.kt` | Moteur prêt, **UI pas encore branchée** |
| 6 | Heures silencieuses | ✅ terminé | `core/notifications/QuietHours.kt`, `AppPreferences.kt`, `SettingsScreen.kt` | Gère les plages traversant minuit |
| 7 | Parcours d'arènes | ❌ à faire | — | Non commencé |
| 8 | Récompenses de paliers | ❌ à faire | — | Dépend du 7 |
| 9 | Refonte accueil + coffres en bas | ❌ à faire | — | Non commencé |
| 10 | Navigation de progression | ❌ à faire | — | Dépend du 7 |
| 11 | Icône d'application | ✅ terminé | `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `mipmap-anydpi-v26/` | Themed icons Android 13 incluses |
| 12 | README visuel | 🟠 partiel | `README.md` | Badges et structure faits, **captures et logo manquants** |
| 13 | Mise à jour depuis l'app | ❌ à faire | — | Non commencé |
| 14 | Sécurité des mises à jour | ❌ à faire | — | Dépend du 13 |
| 15 | Optimisations | 🟠 partiel | `SankaiDatabase.kt` | Migrations explicites faites |
| 16 | Monétisation | 🟠 partiel | `core/ads/` | Pubs récompensées faites, Premium et boutique cosmétique non |
| 17 | Tests automatisés | ❌ à faire | — | Aucun test écrit |

---

## Détail de ce qui est terminé

### 1. Vibrations

`HapticManager` expose cinq intentions (`click`, `success`, `reward`,
`levelUp`, `error`) plutôt que des durées, pour garantir une sensation
cohérente et un seul endroit à régler.

Câblé sur : tous les boutons (via `SankaiButton`), la barre de navigation,
l'ouverture de coffre et la montée de niveau.

Le réglage est relu à chaque appel : couper l'option prend effet
immédiatement, sans relancer l'application.

`VibratorManager` sur Android 12+, `Vibrator` en repli. Chaque appel est
encapsulé : une vibration ne doit jamais faire planter un écran.

### 2. AdMob

| Build | ID application | ID bloc récompensé |
|---|---|---|
| Debug | test Google | test Google |
| Release | `ca-app-pub-9004438844977083~6279544832` | `ca-app-pub-9004438844977083/8842249130` |

Ce n'est pas une commodité mais une protection : cliquer sur ses propres
publicités de production fait bannir le compte AdMob sans recours.

La récompense n'est créditée que depuis le callback officiel
`OnUserEarnedRewardListener`. Fermer la publicité en avance ne rapporte rien.
Préchargement automatique, rechargement après fermeture, dégradation propre
hors connexion.

> **Écart assumé avec le cahier des charges** : la limite est de **50
> publicités par jour**, pas 5. L'équilibrage économique défini plus tôt en
> dépend (un slot de module coûte environ 20 à 40 publicités). Valeur unique à
> changer : `EconomyEngine.MAX_ADS_PER_DAY`.

### 3. Notifications — pourquoi elles arrivaient à 22h08

Cause identifiée : le système reposait sur `WorkManager`, dont l'intervalle
minimum est de 15 minutes et dont les réveils sont regroupés par le système
pour économiser la batterie. Une heure exacte était structurellement
impossible.

Correction : `AlarmManager` avec `setExactAndAllowWhileIdle`, un
`BroadcastReceiver` par déclenchement, et reprogrammation immédiate après
chaque envoi (une alarme Android est à usage unique).

`WorkManager` reste présent mais **ne notifie plus jamais** : il ne fait que
vérifier toutes les 6 heures que les alarmes sont bien posées. Cette
séparation stricte est ce qui garantit l'absence de double notification.

Reprogrammation automatique après : redémarrage du téléphone, mise à jour de
l'app, changement d'heure, changement de fuseau horaire.

Sur Android 12+, l'autorisation `SCHEDULE_EXACT_ALARM` est nécessaire. Sans
elle, l'app retombe sur `setAndAllowWhileIdle` (approximatif) et le signale
dans l'écran de diagnostic, avec un bouton ouvrant le réglage Android.

`USE_EXACT_ALARM` n'est volontairement pas déclaré : réservé aux réveils et
agendas, il ferait rejeter l'application du Play Store.

### 6. Heures silencieuses

Réglables par pas de 15 minutes dans Paramètres. Gèrent les plages traversant
minuit (23h00 → 08h00), qui sont le cas normal et le plus souvent oublié.

Modifier la plage reprogramme immédiatement toutes les alarmes, sinon un mémo
déjà planifié tomberait en pleine plage silencieuse.

> Android ne permet pas à une application ordinaire de s'éteindre puis de se
> relancer seule. Les heures silencieuses suspendent donc les notifications et
> les rappels, elles n'arrêtent pas l'application.

### Migrations Room

`fallbackToDestructiveMigration` a été **retiré**. Migrations explicites 1→2,
2→3, 3→4.

Toute la progression vit uniquement sur l'appareil : il n'existe aucun serveur
pour la reconstituer. Une migration manquante doit planter bruyamment plutôt
qu'effacer les données en silence.

---

## Ce qui reste à faire

Par ordre de valeur décroissante :

1. **UI de l'éditeur mémo** — exposer minutes, jours de la semaine et mode
   aléatoire. Le modèle de données et le moteur de calcul sont déjà en place,
   il ne manque que les contrôles à l'écran.
2. **Parcours d'arènes** — écran résumé + parcours vertical avec graduation,
   paliers et récompenses réclamables. C'est le plus gros morceau restant.
3. **Refonte de l'accueil** — retrait des « Actions rapides » et de
   « Aujourd'hui », barre de coffres en bas.
4. **Mise à jour depuis l'app** — lecture de l'API GitHub Releases,
   comparaison de `versionCode`, téléchargement, vérification SHA-256,
   ouverture de l'installateur Android.
5. **README visuel** — captures d'écran et logo, à produire une fois l'app
   installée sur un téléphone.
6. **Tests automatisés** — en priorité `MemoScheduleEngine`, qui contient
   toute la logique de calcul d'horaires et se teste sans Android.

---

## Limitations Android qui ne seront pas contournées

| Limitation | Conséquence |
|---|---|
| Installation d'un APK | Android exige toujours une confirmation de l'utilisateur. Aucune mise à jour totalement silencieuse n'est possible. |
| Alarmes exactes | Autorisation explicite requise sur Android 12+. Refusée, la précision tombe à quelques minutes. |
| Arrêt de l'application | Une app ordinaire ne peut pas s'éteindre et se relancer seule. Les heures silencieuses suspendent, elles n'éteignent pas. |
| Optimisation batterie | Xiaomi, Huawei, Oppo et Samsung tuent les tâches de fond. L'utilisateur doit passer l'app en « sans restriction ». |
| Artefacts d'un dépôt privé | Le téléchargement exige d'être connecté à GitHub. |

---

## Valeurs à fournir

| Variable | Où | État |
|---|---|---|
| ID application AdMob | `app/build.gradle.kts` | ✅ fourni |
| ID bloc récompensé | `app/build.gradle.kts` | ✅ fourni |
| Secrets de signature GitHub | Settings → Secrets | ❌ non configurés — chaque Release a une signature différente |
| Lien Ko-fi | `SettingsScreen.kt` | 🟠 valeur provisoire |
| Politique de confidentialité | Play Console | ❌ à héberger |
