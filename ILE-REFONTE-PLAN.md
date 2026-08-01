# Refonte du Jardin en île générative — analyse préalable

Document produit avant toute modification, conformément à la consigne
permanente du projet. Audit du projet de référence :
[HAPPY_ISLAND_ANALYSIS.md](HAPPY_ISLAND_ANALYSIS.md).

---

## 1. La question à trancher avant d'écrire une ligne

Le cahier des charges demande de **supprimer** l'expansion par voisinage, le
brouillard, les zones cachées case par case et l'ancienne carte fixe. Ces
éléments ne sont pas de la décoration : ils **sont** la table `garden_plot`, où
vivent les parcelles de chaque joueur avec leur sol, leur humidité, leur culture
en cours et leur chantier.

Autrement dit : **appliquer cette refonte telle quelle détruit le jardin de tout
joueur existant.** Vous en êtes un — la capture que vous m'avez envoyée montre
six parcelles cultivées, 52 pièces et 17 unités d'engrais.

La section 42 du cahier des charges dit « ne jamais régénérer automatiquement
l'île existante lors d'une mise à jour ». La règle est juste, mais elle ne
couvre pas ce cas : il n'existe aucune île à préserver, seulement des jardins.
Il faut donc choisir ce qu'on en fait.

**Trois options, et je ne prendrai pas la décision à votre place.**

| | Ce qui se passe | Coût | Ce que perd le joueur |
|---|---|---|---|
| **A. Table rase** | Les jardins sont effacés, chacun génère son île | Le plus simple | Tout le jardin, cultures comprises |
| **B. Greffe** | L'île est générée, puis les parcelles existantes sont replacées sur ses zones cultivables | Migration Room à écrire et à tester | La disposition, pas le contenu |
| **C. Cohabitation** | Le jardin reste jouable, l'île est un nouveau lieu | Deux systèmes à maintenir | Rien |

Mon avis, sans détour : **B**. A est brutal pour un joueur qui a des cultures en
cours ; C double la charge de maintenance d'un projet qui a déjà deux moteurs de
rendu et vingt-cinq écrans. B demande une migration `garden_plot` →
`island_tile` + `island_slot` qui n'est pas triviale, mais qui est écrite une
fois.

Tant que ce point n'est pas tranché, je m'en tiens à ce qui est vrai quelle que
soit la réponse : le **générateur**, qui est du calcul pur et ne touche à aucune
donnée existante.

---

## 2. Architecture existante concernée

**Conservé sans changement** — Mémo, Flashcards, Focus, Arènes, Coffres,
Boutique (logique d'achat), Profil, notifications, sauvegarde/import,
modules d'apprentissage. La refonte est confinée au Jardin.

**Remplacé** — `ExpansionEngine` (expansion par voisinage), `GrilleJardin`
(rendu cartésien plat), la partie « déblocage » de `GardenPlotEntity`.

**Conservé et réutilisé tel quel** — et c'est important, ces briques viennent
d'être corrigées et testées :

| Brique | Pourquoi elle survit |
|---|---|
| `CameraJardinEngine` | Le zoom d'une île pose exactement le même problème. L'audit montre que le projet de référence a le défaut qu'on vient d'en retirer. |
| `MoistureEngine` | L'humidité d'un sol ne dépend pas de la forme de la carte |
| `WeatherEngine`, `WeatherVisualEngine`, `LightingEngine` | Météo et lumière sont indépendantes du terrain |
| `DayNightEngine` | Idem |
| `ArbreSankaiEngine` | Le système d'emprise est déjà celui dont les bâtiments 2×2 ont besoin |
| `DepotEngine`, `MimoEngine` | Logique métier, pas géographie |

Le système d'emprise écrit pour l'arbre couvre déjà les footprints du Shop
(2×2), du dépôt (2×2) et des ponts. Il n'y a rien à réécrire, seulement à
généraliser le nom.

---

## 3. Nouvelles entités

```
IslandEntity          seed, largeur, hauteur, generationVersion, schemaVersion, cree
IslandTileEntity      x, y, type, hauteur          (généré, jamais édité)
IslandSlotEntity      x, y, etat, prix, solId       (acheté par le joueur)
PlacedBuildingEntity  type, originX, originY, orientation, niveau
IslandPathEntity      x, y, style
BridgeEntity          x, y, longueur, orientation
```

`IslandTileEntity` pose une question de volume : 32 × 32 = **1 024 lignes par
profil**. C'est acceptable en écriture unique, mais il ne faut pas les lire une
par une au rendu. Deux réponses possibles — les stocker en un seul blob
compressé indexé par l'île, ou ne persister que la seed et régénérer le terrain
au démarrage. La seconde est plus élégante et rend `generationVersion`
absolument critique : une évolution du générateur changerait l'île sous les
pieds du joueur. **Je recommande la persistance explicite** malgré son coût,
précisément pour que le terrain ne dépende jamais d'un algorithme qui bouge.

## 4. Migrations Room

Base actuellement en **version 15**. La refonte demande 16.

- `16` : création des six tables ci-dessus, aucune destruction.
- Le devenir de `garden_plot` dépend de la décision A/B/C. En B, la migration
  lit les parcelles existantes et les repose sur l'île ; elle ne peut donc pas
  être écrite avant que le générateur existe.

Rien ne sera supprimé dans la même version que la création. Deux versions
séparées permettent de revenir en arrière si la greffe se passe mal.

## 5. Boucle de jeu

Inchangée dans son principe : réviser → gagner de l'eau et des pièces →
cultiver → vendre → acheter des slots et des bâtiments. L'île change **où** cela
se passe, pas **pourquoi**.

Ajout réel : le joueur choisit *où* acheter, au lieu de subir une expansion
concentrique. C'est le gain principal de la refonte.

## 6. Économie

Le barème proposé (50 → 100 → 200 → 350 → 500) est cohérent avec les gains
actuels d'une session de révision. Point de vigilance : le plafond par niveau
(4 slots au niveau 1, 50 au niveau 20) doit être vérifié contre la courbe d'XP
réelle, sinon il devient soit une formalité, soit un mur. À chiffrer sur les
données du jeu avant de le figer.

## 7. Risques

| Risque | Gravité | Traitement |
|---|---|---|
| Perte des jardins existants | **Élevée** | Décision A/B/C, migration testée |
| Génération produisant une île injouable | Élevée | Validateur + rejet de seed, testé sur 100 seeds |
| Chute de performance (1 024 tuiles + sprites) | Moyenne | Culling, cache d'autotuiles, sprites simplifiés au zoom éloigné |
| Régression du zoom | Moyenne | `CameraJardinEngine` conservé ; ne pas reprendre le geste du projet de référence |
| Collision avec le travail de Codex | Moyenne | Le générateur est en fichiers neufs, aucun écran touché |
| Sauvegarde corrompue | Élevée | Trois générations conservées, somme de contrôle, repli |

## 8. Plan de tests

- 100 seeds : île fermée par l'eau, plage continue, ≥ 40 slots cultivables,
  zone de départ 4×4, ponton accessible, place pour le Shop 2×2 ;
- déterminisme : même seed ⇒ île identique, sur deux exécutions et après
  redémarrage ;
- rivière ne coupant jamais la zone de départ du ponton ;
- rejet et régénération quand le validateur échoue, avec plafond de tentatives ;
- refus d'achat sur eau, rivière, bâtiment, plage protégée ;
- footprint 2×2 refusé s'il chevauche une seule case invalide ;
- historique : 50 actions, annuler, refaire, éditer après annulation ;
- import/export : seed seule, plan, sauvegarde complète ;
- migration depuis un jardin existant (selon la décision retenue).

## 9. Fichiers à créer

```
core/island/domain/IslandSeed.kt
core/island/domain/IslandGenerator.kt        masque, plage, rivières, zones
core/island/domain/IslandValidator.kt        rejet des seeds injouables
core/island/domain/IslandTileType.kt
core/island/domain/IslandSlotEngine.kt       états, prix, règles d'achat
core/island/domain/BuildingFootprint.kt      généralisation d'ArbreSankaiEngine
core/island/domain/IslandEditCommand.kt      historique réversible
core/island/data/IslandEntities.kt
core/island/data/IslandDao.kt
core/island/data/IslandRepository.kt
ui/screens/island/…                          après le moteur, pas avant
```

Plus les tests correspondants. **Rien dans `ui/screens/garden/` tant que la
décision A/B/C n'est pas prise** — c'est là que vivent les données du joueur.

---

## 10. Ce que je fais maintenant

Le générateur et son validateur : du calcul pur, testable sans téléphone, qui
ne touche aucune donnée existante et ne peut donc rien casser. Il sera utile
quelle que soit la réponse à la question de la section 1.

Ce que je ne commence pas avant votre arbitrage : la migration, la suppression
de l'ancienne grille, et l'écran d'île.
