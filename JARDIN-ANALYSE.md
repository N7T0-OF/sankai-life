# Sankai Garden — analyse préalable

Établie avant toute modification de code, comme demandé. Rien n'a été touché.

---

## 0. Nature du changement

Ce n'est pas une refonte d'interface : c'est **un second produit** greffé sur
l'application existante. L'outil de productivité reste, le jardin devient un
mode isolé qui s'ouvre par-dessus.

Ordre de grandeur honnête : la **phase 1 seule** (flashcards structurées,
répétition espacée, grille, croissance, récolte, sauvegarde) représente à peu
près le volume de tout ce qui a été construit jusqu'ici. Les quatre phases
réunies représentent plusieurs fois ce volume.

Ce document sert à décider quoi engager, pas à tout promettre.

---

## 1. Architecture existante

| Couche | État | Verdict |
|---|---|---|
| Room, 7 versions, migrations explicites | solide | **Réutilisable tel quel** |
| `UserRepository`, `GameRepository` | solide | Réutilisables, à étendre |
| Moteurs (`XpEngine`, `EconomyEngine`, `ChestEngine`, `ArenaEngine`, `RegularityEngine`, `FlashcardEngine`) | purs, testables | **Réutilisables** |
| Navigation Compose, factories manuelles | simple | Réutilisable, à étendre d'un sous-graphe |
| Design system (`SankaiCardState`, composants) | récent | Réutilisable |
| AlarmManager, WorkManager, AdMob, mise à jour | fonctionnels | Intacts |

**Point fort inattendu** : `MemoProfileEntity` + `MemoLineEntity` forment déjà
un embryon de deck. Les lignes portent `box`, `nextReviewAtMillis`,
`reviewCount`, `successCount` — la répétition espacée existe et est testée.

**Manque réel** : une ligne de mémo est un texte, pas une carte structurée.
Il n'y a ni recto/verso explicite, ni langue, ni catégorie, ni état de
maîtrise. C'est le principal chantier de données de la phase 1.

---

## 2. Écrans conservés

Aucun écran actuel n'est supprimé. L'application reste utilisable sans jamais
ouvrir le jardin — c'est la condition du mode « masquer le jeu » que tu
demandes toi-même.

| Écran | Devenir |
|---|---|
| Mode Vie, Mémo, Éditeur, Focus, Objectifs | Conservés, sources de ressources du jardin |
| Défis, Boutique, Profil, Personnalisation, Statistiques, Paramètres | Conservés |
| Parcours d'arènes | **Conservé et réutilisé** : les arènes deviennent les biomes |
| Flash cards | Conservé, mais son modèle est étendu |

## 3. Écrans remplacés ou ajoutés

| Écran | Nature |
|---|---|
| **Accueil** | Remplacé : diorama de l'arène + gros bouton d'entrée |
| Chargement du jardin | Nouveau |
| Jardin (grille) | Nouveau, cœur du mode jeu |
| Boutique de graines | Nouveau, distincte de la boutique actuelle |
| Herbier | Nouveau |
| Missions du jardin | Nouveau |
| Détail de parcelle | Nouveau, feuille modale |
| Souvenir de notification | Nouveau, feuille modale |

---

## 4. Nouvelles entités

Quinze tables, toutes **additives** — aucune table existante n'est modifiée
de façon destructive.

```
GardenEntity            état global, zone active, extensions
GardenZoneEntity        secteurs (central, serre, nocturne…)
GardenPlotEntity        parcelle : zone, position, état, sol
SoilTypeEntity          terre, sable, humide, nocturne, cristallin
SeedEntity              espèce, durée, sol requis, arène de déblocage
CropEntity              culture en cours, horodatages, qualité
HarvestEntity           récoltes, pour l'Herbier et les statistiques
InventoryItemEntity     graines, outils, ressources
LearningDeckEntity      deck, langue, plante associée
FlashcardEntity         recto, verso, deck, difficulté
FlashcardReviewEntity   historique des réponses
MemoChallengeEntity     défis souvenir déjà réclamés
MimoEntity              assistants et spécialités
MimoAssignmentEntity    missions en cours, échéances
WeatherStateEntity      météo courante, influence éducative
LearningRewardLimitEntity plafonds journaliers
TrustedTimeStateEntity  dernière heure fiable connue
```

## 5. Migrations Room

**Une seule migration, 7 → 8, purement additive.** Aucun `ALTER` destructif,
aucune table existante recréée.

Deux points sensibles :

**`MemoLineEntity` vers `FlashcardEntity`.** Les lignes de mémo portent déjà
un état de révision. Deux options :

- *Migrer les données* — les lignes deviennent des cartes, les mémos des
  decks. Cohérent, mais irréversible si mal fait.
- *Cohabiter* — les cartes sont une table neuve, les mémos gardent leur rôle
  et peuvent générer des cartes à la demande.

**Je recommande la cohabitation.** Elle ne touche à aucune donnée existante,
elle correspond à ta section 44 (« générer un deck depuis un Mémo »), et elle
laisse le mémo continuer à faire ce qu'il fait bien : envoyer des phrases.

**`ArenaRewardEntity` est déjà pris.** Le parcours d'arènes existe. Les arènes
du jardin ne doivent pas créer une seconde table concurrente : on étend les
récompenses existantes avec des champs jardin plutôt que de dupliquer.

---

## 6. Boucle de jeu

```
notification mémo reçue
        ↓
défi souvenir  →  quelques gouttes d'eau
        ↓
révision de flashcards  →  eau (plafonnée)
        ↓
plantation / arrosage
        ↓
croissance en temps réel (app fermée)
        ↓
récolte  →  pièces, graines, compost
        ↓
achat de graines, nettoyage, extension
        ↓
progression d'arène  →  nouveau biome, nouvelles espèces
```

Une session type dure **moins de deux minutes** : ouvrir, réviser dix cartes,
arroser, fermer. C'est le rythme que visent tes références.

## 7. Économie du prototype

| Ressource | Source | Plafond |
|---|---|---|
| Eau | 5 bonnes réponses = 1 unité | 30/jour |
| Pièces | Récoltes, missions | — |
| Compost | Recyclage de récoltes | — |
| Cristaux | Arènes, maîtrise de deck | rare par construction |

Équilibrage visé pour le premier jour : dix cartes révisées donnent deux
unités d'eau, une graine commune coûte quarante pièces, une première culture
mûrit en six heures. Le joueur plante le premier jour et récolte le lendemain.

**Le plafond d'eau est la pièce maîtresse.** Sans lui, réviser la même carte
facile en boucle produit des ressources infinies, et le jeu récompense la
triche au lieu de l'apprentissage. Le plafond doit tenir compte de la carte
réellement due, pas du nombre de clics.

---

## 8. Risques techniques

| Risque | Gravité | Réalité |
|---|---|---|
| **Aucun visuel disponible** | **élevée** | Le diorama, les biomes, les plantes à cinq stades, les Mimos : je ne sais pas produire d'illustrations. Sans graphiste, le rendu sera géométrique, très loin de tes maquettes. |
| Volume | élevée | Phase 1 ≈ tout le projet actuel. Les quatre phases : plusieurs fois. |
| Manipulation de l'heure | moyenne | Impossible à empêcher hors ligne, tu l'as écrit. Parade : couple horloge murale + `elapsedRealtime`, gains hors ligne bornés, identifiants jour/semaine. |
| Performance Compose | moyenne | Grille animée + météo + Mimos sur entrée de gamme. À contenir : pas d'animation permanente, recompositions ciblées. |
| Grille non défilante en portrait | moyenne | Déjà rencontré sur l'accueil. Même parade : tailles relatives, repli accessible. |
| Double sens des arènes | moyenne | Elles servent déjà à la progression générale. Les faire aussi piloter les biomes crée un couplage à surveiller. |
| Poids de l'APK | faible | 3,4 Mo aujourd'hui. Des assets réels le feront monter à 15-30 Mo. |

## 9. Plan de tests

Testable sans appareil, donc réellement vérifiable par moi :

- croissance : temps écoulé, franchissement de stades, app fermée longtemps ;
- heure incohérente : recul de l'horloge, gains bornés ;
- plafond d'eau : carte due contre carte répétée artificiellement ;
- répétition espacée : intervalles, maîtrise, régression ;
- défi souvenir : non rejouable sur la même notification ;
- économie : pas de double récolte, pas de double récompense publicitaire ;
- migration 7 → 8 : données existantes intactes.

Non testable sans toi : tout le rendu.

## 10. Fichiers à modifier

**Créés** — environ trente fichiers : `core/garden/` (entités, DAOs, moteurs,
cas d'usage), `ui/screens/garden/` (jardin, boutique de graines, herbier,
missions), `ui/screens/learning/` (decks, révision), `core/time/GardenClock`.

**Modifiés** — `SankaiDatabase` (migration 7→8), `Screen.kt` et `NavGraph.kt`
(sous-graphe jeu), `HomeScreen.kt` (accueil hub), `AppPreferences` (options
jardin simplifié et masquer le jeu).

**Supprimés** — aucun.

---

## Recommandation

Ta direction est bonne : jardin à parcelles plus arbre central, mode isolé,
portrait. Elle est plus forte que le village ouvert, et l'isolement du mode
jeu est le bon choix d'architecture.

Deux réserves à trancher avant de commencer.

**Les visuels sont le vrai goulot.** Tes maquettes supposent des illustrations
que je ne peux pas produire. Je peux livrer une version géométrique
fonctionnelle — formes, couleurs, emojis — mais elle ne ressemblera pas à ce
que tu as dessiné. Il faudra un graphiste, ou accepter un style abstrait
assumé.

**L'application n'a toujours jamais été lancée.** Dix-sept versions publiées,
aucun écran vu. Construire un second produit par-dessus un premier non vérifié
double la surface de ce qui peut être faux sans qu'on le sache.

**Ce que je propose** : engager la **phase 1 uniquement**, dans l'ordre de ta
propre liste, en commençant par ce qui est le plus testable sans appareil —
le modèle de cartes, la répétition espacée, le plafond anti-abus, la
croissance temporelle. Le rendu du jardin vient après, quand la mécanique
est juste.
