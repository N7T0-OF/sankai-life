# Passation vers Claude

Date : 1er août 2026

Codex a terminé une première vague critique et la phase 1 UI. Commence par lire
`CODEX_AUDIT.md`, `CODEX_HANDOFF.md`, `UI_UX_AUDIT.md` et
`UI_REDESIGN_STATUS.md`.

## État factuel du rendu Jardin

La pile visuelle est maintenant contenue dans la grille, donc découpée avec le
terrain et placée sous le HUD. Son ordre est : terrain et acteurs, voile du
cycle jour/nuit, étoiles, filtre lumineux météo, ombres de nuages, pluie, puis
indicateurs d'interaction et commandes. Les voiles ne capturent aucun geste.

Les ombres sont procédurales : tuiles de 256 px répétables, générées une fois en
mémoire, ancrées au monde et déplacées avec la caméra et le zoom. Les changements
de densité et d'opacité sont interpolés sur 15 secondes ; le filtre lumineux
météo l'est sur 12 secondes. Les ombres sont nettement atténuées la nuit. Elles
s'arrêtent lorsque le ciel devient clair, lorsque le Jardin quitte l'état
`STARTED`, ou lorsque les animations sont réduites.

La qualité graphique est persistée depuis Paramètres :

- `LOW` : une couche, publication du mouvement à environ 30 FPS et filtrage bas ;
- `NORMAL` : deux couches, environ 60 FPS ;
- `HIGH` : trois couches, environ 60 FPS ;
- le mode économie batterie force temporairement `LOW` sans perdre le choix
  enregistré par l'utilisateur.

Le réglage système de réduction des animations immobilise les nuages et retire
toutes les gouttes de pluie. Le scintillement des étoiles reste actuellement
animé. Le moteur de rendu reste séparé des règles agricoles et ses profils
clair/nuageux/pluie/orage, qualité, nuit, vent déterministe et mouvement réduit
sont couverts par des tests unitaires.

Nuages et pluie consomment le même `GardenWindState` déterministe pour la
direction et l'intensité. La pluie incline et fait boucler ses gouttes selon ce
vent ; il n'existe donc plus deux orientations météo indépendantes.

## Temps et caméra du Jardin

Croissance, humidité et fins de chantier sont synchronisées dans une transaction
idempotente à l'ouverture puis toutes les minutes, uniquement lorsque l'écran
Jardin est composé et `STARTED`. Cette synchronisation périodique ne déclenche
ni travail de Mimo, ni rapport, ni défi souvenir. Les états UI dérivés partagent
un seul tick et les cultures sont indexées par parcelle.

La caméra utilise une origine monde stable : une extension au nord ou à l'ouest
ne change plus la position de toutes les cases. Le bouton recentre réellement,
les petites cartes restent centrées et le déplacement est borné au terrain.

## Validation obtenue

La chaîne finale suivante est verte : tests unitaires, `lintDebug`,
`lintRelease`, `assembleDebug` et `assembleRelease`.

## À valider sur appareil

- lisibilité et naturel des nuages par temps nuageux, pluie et orage, de jour,
  au crépuscule et de nuit ; vérifier notamment que l'empilement ne rend pas les
  plantes ou les Mimos trop sombres ;
- continuité du motif pendant un panoramique, un pincement et un recentrage,
  sans couture visible ni ombre collée à l'écran ;
- cohérence visuelle de la direction commune pluie/nuages sur plusieurs jours ;
- différences réellement perceptibles entre `LOW`, `NORMAL` et `HIGH`, ainsi
  que le passage automatique à `LOW` en économie batterie ;
- comportement avec l'échelle d'animation système à zéro : nuages immobiles,
  pluie absente ; décider si les étoiles doivent elles aussi être figées ;
- arrêt/reprise des animations et de la synchronisation en mettant l'application
  en arrière-plan, sans saut, double progression ni activité persistante ;
- recentrage, limites et absence de saut après extensions nord/ouest, sur petit
  téléphone, tablette et aux zooms minimum/maximum ;
- fluidité et consommation sur appareil modeste et grand terrain, surtout en
  qualité `HIGH` sous pluie ou orage.

## Coordination

Ne remplace pas les changements non committés de localisation dans :

- `SankaiLife/app/src/main/res/values/strings.xml` ;
- `values-en/` ;
- `values-pt/` ;
- `xml/locales_config.xml`.

La suite la plus utile reste la transaction Boutique, les migrations Room et
la validation appareil de l'accueil/Jardin. Aucun commit ni publication n'a été
fait.
