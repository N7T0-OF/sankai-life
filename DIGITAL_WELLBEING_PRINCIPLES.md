# Principes de bien-être numérique de Sankai Life

Date de référence : 8 août 2026
Statut : principes produit, état du code et critères de validation.

## Principe directeur

Sankai Life doit optimiser l'utilité d'une session, pas sa durée. Le succès
n'est pas « l'utilisateur est revenu coûte que coûte », mais « l'utilisateur a
fait ce qu'il avait choisi, comprend où il en est et peut fermer l'application
sans dette ni perte ».

Ces principes ne constituent pas une promesse médicale. L'application peut
aider à organiser l'attention ; elle ne diagnostique ni ne traite un trouble.

## Les dix règles produit

1. **Une fin visible.** Chaque visite doit pouvoir se terminer. Aucun flux
   infini, lecture automatique sans borne ou prochain contenu imposé.
2. **Objectif choisi.** Le temps quotidien est réglable à 0, 2, 5, 10 ou 15
   minutes. Zéro signifie « aucun objectif », pas « objectif manqué ».
3. **Aucune punition d'absence.** Pas de série cassée, ressource perdue,
   personnage triste ou notification culpabilisante après une pause.
4. **Travail réel avant engagement.** Une notification d'apprentissage n'existe
   que s'il y a effectivement des cartes dues ou un mémo demandé.
5. **Silence par défaut pour le facultatif.** Culture et Jardin ne sollicitent
   pas spontanément l'utilisateur sans activation explicite.
6. **Budget global.** Une à trois notifications spontanées maximum par jour,
   heures silencieuses, pause de sept jours et week-end silencieux disponibles.
7. **Récompenses bornées.** Les gains peuvent remercier une action utile, mais
   ne doivent pas rendre rentable le « farming » de temps ou les sessions
   artificiellement longues.
8. **Choix réversible.** Mode minimal, catégories, packs et extensions peuvent
   être désactivés sans perdre les données personnelles.
9. **Pas d'urgence artificielle.** Pas de compte à rebours commercial, rareté
   inventée, coffre qui expire ou badge rouge sans action utile immédiate.
10. **Mesure respectueuse.** Préférer des indicateurs locaux et agrégés ; aucune
    collecte d'attention cachée ni profilage sans consentement clair.

## État observé dans le code

### Déjà aligné

- `HomeScreen` affiche les cartes dues, une capsule courte et le prochain mémo,
  puis propose « Terminer pour aujourd'hui ». L'action enregistre la date et
  appelle `finishAndRemoveTask()`.
- L'accueil n'affiche plus les coffres, la série, la progression d'arène ou le
  Jardin ; les anciens gros composables correspondants ont été retirés de
  `HomeScreen`.
- `AppPreferences` expose `minimalMode`, `dailyMinutes`, la date de fin du jour,
  un maximum quotidien, une pause, le silence du week-end et cinq catégories.
- `NotificationPolicy` applique maître, catégorie, heures silencieuses, pause,
  week-end et compteur quotidien avant les notifications Mémo,
  Révision/Culture, Focus et Jardin/coffres.
- `NotificationCoordinator` réconcilie les alarmes au démarrage, après mise à
  jour, reboot et changement d'heure/fuseau, et annule les familles désactivées.
- Le maximum par défaut est 1 et reste borné entre 1 et 3.
- `notifyCulture` et `notifyGarden` sont faux par défaut.
- `FocusRewardEngine` ne récompense rien sous cinq minutes, rend les petites
  sessions proportionnelles et plafonne les XP/pièces historiques. Focus ne
  crée plus de coffre et ne progresse plus de défi ; ces valeurs de
  compatibilité restent en arrière-plan, notamment en mode minimal.
- Une fin Focus en arrière-plan peut produire une notification discrète
  uniquement après contrôle de la catégorie Focus et de la politique globale.
  Son appui ouvre Focus ; les notifications Mémo, Révision et Culture ouvrent
  respectivement Mémo, Académie et Capsules.
- `DailyCultureSelector` évite les répétitions récentes et les lectures
  profondes successives quand une alternative existe. Il ne produit ni score,
  niveau, streak ni récompense.
- `ExtensionsScreen` rend l'installation du Jardin explicite, et `NavGraph`
  redirige les routes Jardin/Île vers ce gestionnaire lorsque le pack manque.
- Désinstaller le Jardin retire ou désactive son payload, annule ses alarmes et
  conserve snapshot et état Room détaillé en sommeil. La réinstallation
  retrouve ainsi parcelles, inventaire et état Île sans purge préalable.

### Encore partiel ou contradictoire

- `HomeViewModel` n'initialise plus coffres ni défis : c'est acquis. Leurs
  moteurs, écrans et données restent cependant dans l'application et peuvent
  encore être atteints depuis le Mode Vie hors mode minimal.
- Boutique et Défis restent accessibles dans la section facultative du Mode
  Vie. Leur contenu n'a pas encore été audité contre les règles ci-dessus.
- Des chaînes, composables et statistiques de streak/coffres subsistent dans le
  code et le profil. Ils ne sont plus tous visibles au premier niveau, mais ne
  sont pas supprimés.
- Le budget de notification est conservé dans un petit `SharedPreferences` et
  protégé par un verrou de processus. Il faut tester les déclenchements
  concurrents, le changement de fuseau, le redémarrage et la restauration.
- Culture partage aujourd'hui le scheduler de Révision ; Focus ne produit
  qu'une notification de fin lorsque la session s'achève hors écran. Ces deux
  parcours existent, mais leurs catégories, quotas et destinations doivent
  encore être couverts par la matrice instrumentée.
- Le temps « estimé » de l'accueil provient du choix utilisateur, pas d'une
  estimation des cartes réellement dues. Le libellé doit rester honnête.
- Le snapshot Jardin reste trop léger pour déplacer physiquement les données
  vers une Dynamic Feature ou un APK compagnon. La réversibilité locale repose
  aujourd'hui sur les tables Room dormantes ; une future extraction exigera un
  export détaillé et vérifié avant toute suppression de ces tables.

## Notifications : contrat précis

Une notification spontanée est autorisée uniquement si toutes les conditions
sont vraies :

```text
autorisation Android
ET maître activé
ET catégorie activée
ET hors heures silencieuses
ET hors pause choisie
ET hors week-end si option activée
ET budget quotidien non épuisé
ET information réellement utile disponible
```

Le compteur doit être débité au dernier moment, juste avant l'envoi. Si la
construction du contenu échoue après acquisition, il faut décider explicitement
si le quota est rendu ; le comportement actuel ne le rend pas. Les
notifications de test peuvent contourner le quota, mais doivent être clairement
étiquetées et ne jamais créer de progression.

Quand l'utilisateur coupe le maître, toutes les alarmes replanifiables doivent
être annulées. Quand il le réactive, seules les alarmes pertinentes sont
recréées. Une alarme silencieuse ou sans contenu peut être reprogrammée pour le
jour suivant, mais ne doit pas notifier.

## Récompenses et économie

La bonne hiérarchie est :

1. résultat réel — cartes apprises, mémo créé, minutes de focus ;
2. retour informatif — progression lisible et non comparative ;
3. décoration facultative — Jardin ou cosmétique ;
4. monnaie/coffre — jamais nécessaire pour accéder au résultat réel.

Le cœur doit enregistrer l'action même si l'extension Jardin est absente. Le
crédit optionnel est un effet secondaire via un port neutre. Une panne du Jardin
ne doit donc jamais annuler une révision ou une session Focus.

Les récompenses temporelles doivent dépendre de minutes effectivement écoulées,
être idempotentes et bornées. Tester reprise de processus, double callback et
modification d'horloge pour éviter qu'une protection contre le farming ne
devienne une perte légitime.

## Mode minimal

Le mode minimal doit :

- conserver Aujourd'hui, Académie, Mémo, Focus, Capsules si souhaitées,
  Paramètres et export/sauvegarde ;
- masquer Boutique, Défis, Jardin, Île, coffres, badges et monnaies sans
  supprimer leurs données ;
- empêcher leur initialisation proactive et leurs notifications ;
- rester réversible instantanément ;
- ne pas remplacer un écran par un vide ou une route morte.

Le code actuel masque les outils facultatifs dans `ModeVieScreen`, ne crée plus
les défis/coffres quotidiens au démarrage de `HomeViewModel`, réconcilie les
alarmes par catégorie et protège Jardin/Île par l'état d'installation du pack.
Il faut encore garantir le mode minimal au niveau des routes directes, widgets
et écrans de profil, puis tester la réversibilité déjà recherchée par la
conservation de l'état Room dormant.

## Capsules culturelles

Une capsule quotidienne est une suggestion, pas une obligation. Elle doit :

- être finie et lisible hors connexion ;
- proposer « Plus tard » ou aucune ouverture sans badge de retard ;
- ne pas récompenser la consommation en série ;
- respecter les filtres de langue/type et la profondeur souhaitée ;
- ne pas exploiter favoris et historique pour fabriquer un flux sans fin ;
- expliquer sa source et sa licence depuis l'écran de détail.

Le sélecteur actuel est déterministe par profil/date/version, évite une entrée
récente tant qu'une autre existe et ne change pas après enregistrement du choix
du jour. Ce sont de bonnes propriétés de stabilité et de sobriété.

## Mesures recommandées

Par défaut, calculer localement :

- sessions volontairement terminées ;
- nombre de jours avec au moins une action utile, sur fenêtre glissante ;
- notifications affichées, ouvertes, mises en pause et désactivées ;
- durée médiane jusqu'au bouton « Terminer » ;
- actions abandonnées à cause d'une route, erreur ou permission ;
- taux d'activation/désactivation d'une extension, sans identifiant distant.

Ne pas optimiser le temps total, le nombre d'ouvertures, la série maximale ou
le revenu publicitaire par utilisateur comme objectifs produit primaires. Si
une télémétrie distante est ajoutée, elle exige finalité, minimisation,
consentement approprié, rétention courte et documentation de confidentialité.

## Matrice de tests

### Unitaires

- toutes les combinaisons maître/catégorie/silence/pause/week-end ;
- bornes 1 et 3 notifications, deux acquisitions simultanées ;
- changement de jour, fuseau et horloge en arrière ;
- récompense Focus à 0, 4, 5, 24, 25, 44, 45, 120 et 121 minutes ;
- fin Focus n'écrit ni coffre ni progression de défi ;
- sélection culturelle stable, filtre vide et catalogue entièrement récent ;
- mode minimal ne déclenche aucun effet de jeu.

### Instrumentés

- refus et révocation de `POST_NOTIFICATIONS` ;
- Doze, reboot, force stop et changement de fuseau ;
- bouton Terminer sur Activity normale, multi-fenêtre et retour depuis une
  notification ;
- appui à froid et à chaud sur les notifications Mémo, Révision, Culture et
  Focus : bonne destination, une seule navigation, pas de rejeu à la rotation ;
- TalkBack, police 200 %, contraste et cible tactile ;
- navigation avec/sans pack Culture et avec/sans extension Jardin ;
- absence de badge ou notification après désactivation.

### Revue qualitative

Faire relire les textes pour retirer culpabilité, urgence, personnification
triste et promesses de santé. Un test d'utilisabilité doit demander : « Peux-tu
terminer et partir ? », pas seulement « As-tu trouvé le bouton suivant ? ».

### Validation technique exécutée

Le rebuild final stable a réussi 694/694 tests via `testDebugUnitTest`.
`lintDebug` et `lintRelease` comptent chacun 180 avertissements et zéro erreur.
La compilation release, `assembleRelease` et `bundleRelease` ont réussi avec R8
et lint vital, `--no-parallel`, `-Pksp.incremental=false` et Kotlin in-process.
Cette campagne ne remplace pas la matrice ci-dessus : aucun test instrumenté ni
essai sur appareil n'a été exécuté.

## Sources

- Android, permission de notification : <https://developer.android.com/develop/ui/views/notifications/notification-permission>
- Android, conception des notifications : <https://developer.android.com/develop/ui/views/notifications>
- Android, qualité adaptative et accessibilité : <https://developer.android.com/docs/quality-guidelines/core-app-quality>
- Android, DataStore : <https://developer.android.com/topic/libraries/architecture/datastore>
- Mathur et al., *Dark Patterns at Scale: Findings from a Crawl of 11K Shopping Websites*, arXiv:1907.07032 : <https://arxiv.org/abs/1907.07032>
- Recherche bibliographique arXiv « digital wellbeing » : <https://arxiv.org/search/?query=digital+wellbeing&searchtype=all>

La littérature informe la revue ; elle ne transforme pas ces choix produit en
preuves cliniques ni en obligations juridiques automatiques.
