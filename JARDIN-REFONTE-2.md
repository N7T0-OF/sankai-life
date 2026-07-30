# Jardin — refonte de l'arrosage, de l'expansion et de l'accueil

Analyse préalable, comme convenu. Rien n'est modifié avant cette page.

---

## 1. Architecture existante

Trois couches, déjà séparées proprement :

| Couche | Fichiers | Ce qui change |
|---|---|---|
| Moteurs purs | `CropGrowthEngine`, `DayNightEngine`, `DepotEngine`, `MimoEngine` | +2 moteurs, 1 modifié |
| Données | `GardenEntities`, `GardenDao`, `GardenRepository` | 1 entité fortement modifiée |
| Écrans | `GardenScreen`, `GrilleJardin`, `GardenViewModel`, `HomeScreen` | tous touchés |

**Le point dur est `GardenPlotEntity`.** Sa clé primaire est `id: Int` et vaut
l'index dans une liste — 0 à 15, lus comme 4 colonnes. C'est un tableau
déguisé en base de données. Toute la refonte découle de là : une expansion
libre autour d'un centre n'est pas exprimable avec un index linéaire.

`GardenCropEntity.plotId` pointe sur cet index. Il devra suivre.

---

## 2. Ce qui est conservé, remplacé, ajouté

**Conservé sans changement** — croissance, dépôt, marché, Mimos, défi
souvenir, économie de l'eau, plafonds anti-abus. La refonte est géométrique
et hydrique ; elle ne touche pas la boucle éducative.

**Remplacé**

- `GrilleJardin` : grille dense 4 colonnes → plan cartésien avec brouillard
- `HomeScreen` : la carte contextuelle « Mémo / Focus / Objectifs » disparaît,
  remplacée par le diorama
- déblocage par arène atteinte → déblocage par achat d'une case adjacente

**Ajouté** — humidité par parcelle, évaporation, chantiers, brouillard,
terrains typés, mini-carte.

---

## 3. Nouvelles entités et migration

Pas de nouvelle table : `garden_plot` est étendue.

```
+ x, y            INTEGER   coordonnées sur la grille logique
+ humidite        REAL      0.0 → 1.0
+ dernierCalculHumidite  INTEGER
+ terrain         TEXT      fertile / rocheux / humide / sableux / forestier…
+ deblocage       TEXT      HIDDEN / DISCOVERABLE / UNLOCKING / UNLOCKED
+ chantierFinMillis      INTEGER
+ coutDeblocage   INTEGER
```

**Migration 12 → 13, le passage délicat.** Les seize parcelles existantes
doivent être recentrées sur le plan sans perdre leurs cultures :

```sql
-- ancien index i → (x, y) centré sur (20, 20), nouvelle clé y*40+x
UPDATE garden_plot SET id = (18 + id/4)*40 + (18 + id%4) WHERE id < 16;
UPDATE garden_crop SET plotId = (18 + plotId/4)*40 + (18 + plotId%4) WHERE plotId < 16;
```

Les nouvelles clés valent au minimum 738, les anciennes au maximum 15 : aucune
collision possible pendant la réécriture. C'est ce qui permet de faire la
migration en deux UPDATE au lieu d'une table temporaire.

**Décision : `TileState` n'absorbe pas `PlotState`.** Le cahier des charges
les fusionne, mais ce sont deux axes indépendants — une case peut être
débloquée *et* en friche, débloquée *et* en croissance. Les confondre créerait
des états impossibles à représenter. Deux colonnes, donc.

---

## 4. Boucle de jeu modifiée

L'humidité devient la variable centrale du soin :

```
révision → eau en réserve
eau → humidité de la parcelle
humidité → vitesse de croissance et qualité
temps + soleil → évaporation → humidité redescend
```

L'arrosage cesse d'être un clic qui remet un compteur à zéro. Il verse une
quantité dans un sol qui la perd progressivement, à une vitesse qui dépend du
terrain et de l'heure.

**Aucune plante ne meurt.** Un sol sec ralentit (−40 %), un sol détrempé
ralentit un peu (−15 %). Le pire cas reste une plante lente, jamais perdue.
C'est la règle posée dès la première analyse et elle ne bouge pas.

---

## 5. Économie de l'expansion

Le déblocage passe d'un droit acquis par niveau à un achat choisi :

| Terrain | Coût relatif | Contrepartie |
|---|---|---|
| Fertile | élevé | croissance accélérée |
| Humide | moyen | évaporation lente |
| Sableux | bas | sèche vite, seul sol à cactus |
| Rocheux | bas | à nettoyer d'abord, minéraux |
| Forestier | moyen | ombre, évaporation lente |
| Abandonné | très bas | beaucoup de débris |

Le coût monte avec la distance au centre, sinon rien n'empêcherait de
s'étendre indéfiniment dès la première heure.

---

## 6. Risques

| Risque | Gravité | Traitement |
|---|---|---|
| **La migration 12→13 casse les jardins existants** | élevée | Deux UPDATE arithmétiques, testables ; mais non vérifiables sans appareil |
| Rendu d'une grille 40×40 | moyenne | Seules les cases non cachées sont composées ; le brouillard n'est pas un objet |
| Conflit caméra / outil | moyenne | Déjà résolu dans `GrilleJardin`, à étendre aux deux axes |
| L'humidité rend le jeu punitif | moyenne | Plancher à −40 %, jamais de mort, jamais de perte de récolte |
| Volume | élevée | Le cahier des charges fait 35 sections. Livré par blocs, dans **ton** ordre de priorité |

---

## 7. Plan de tests

- `MoistureEngine` : évaporation par terrain, par phase du jour, bornes 0–1,
  effet sur la croissance à chaque palier, cas du cactus qui préfère le sec
- `ExpansionEngine` : voisinage, passage HIDDEN → DISCOVERABLE, coût selon la
  distance, refus d'une case non adjacente, durée de chantier
- Migration : vérifiable seulement sur appareil — c'est la limite connue

---

## 8. Fichiers à modifier

```
core/garden/domain/MoistureEngine.kt        nouveau
core/garden/domain/ExpansionEngine.kt       nouveau
core/garden/domain/GardenModels.kt          + besoins en eau par espèce
core/garden/data/GardenEntities.kt          GardenPlotEntity étendue
core/garden/data/GardenDao.kt               requêtes par coordonnées
core/garden/data/GardenRepository.kt        arrosage, évaporation, déblocage
core/data/db/SankaiDatabase.kt              version 13 + MIGRATION_12_13
ui/screens/garden/GrilleJardin.kt           plan cartésien, brouillard
ui/screens/garden/GardenScreen.kt           fiche parcelle, mini-carte
ui/screens/garden/GardenViewModel.kt        humidité, chantiers
ui/screens/home/HomeScreen.kt               diorama à la place du raccourci
```

---

## 9. Ce que je livre maintenant, et ce que je ne livre pas

Ton point 35 fixe l'ordre. Je livre **Immédiat** et **Ensuite** en un bloc,
parce que la moitié d'un changement de coordonnées ne compile pas.

Reste explicitement de côté, dans ton ordre : irrigation, bâtiments, chemins,
cultures associées, saisons, météo, événements d'exploration, mini-carte
détaillée, niveaux d'arrosoir.

**Deux choses que je ne peux pas faire**, et qu'il vaut mieux dire maintenant
que découvrir plus tard :

- les visuels demandés — texture poussiéreuse, éclaboussures, flaques,
  silhouettes dans le brouillard — supposent des illustrations que je ne
  produis pas. Je livre l'équivalent géométrique : dégradés de brun calculés
  depuis l'humidité, opacité du brouillard, formes. La logique est séparée du
  rendu, un graphiste pourra substituer ses images sans toucher au reste ;
- le son d'eau demande un fichier audio que je n'ai pas. Le retour haptique,
  lui, existe déjà et sera branché.
