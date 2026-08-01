# Audit de HappyIslandDesigner

Lecture du source réel dans
`G:\2_Logiciel\CLAUDE CODE\EXEMPLE\HappyIslandDesigner-master`, le 1er août
2026. Licence MIT, `Copyright (c) 2020 Eugene` — voir
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Le projet est un **éditeur manuel** de cartes, écrit en TypeScript au-dessus de
Paper.js, où l'utilisateur dessine sa carte au pinceau. Sankai Life a besoin de
l'inverse : un **générateur** qui produit une île cohérente, puis un éditeur
restreint. Cette différence de nature commande tout ce qui suit — plusieurs
briques sont excellentes dans leur contexte et inadaptées au nôtre.

Chiffres du dépôt : ~34 500 lignes de TypeScript, dont **29 600 de caches
générés** (`generatedBaseMapCache.ts`, `generatedTilesPathsCache.ts`). La
logique réellement écrite tient donc en ~5 000 lignes.

---

## 1. Historique annuler / rétablir

**Fichiers** — `app/state.ts` (212 lignes)

**Algorithme** — Une pile de commandes sérialisables et un index courant.
Chaque commande porte `type` (`draw` / `object`) et `action` (`create`,
`delete`, `position`, `property`).

Le point remarquable est `applyCommand(command, isApply: boolean)` : **une
seule fonction fait et défait**, selon un booléen. Annuler une création, c'est
appliquer la création à l'envers. Cela supprime par construction la classe de
bugs où `undo()` ne défait pas exactement ce que `do()` a fait, parce qu'il n'y
a pas deux implémentations à garder d'accord.

`addToHistory` tronque la pile de rétablissement dès qu'on édite après avoir
annulé — comportement attendu, et souvent oublié.

**Réutilisable** — Le motif entier. Y compris `maxHistoryIndex = 99`, qui borne
la mémoire.

**À ne pas reprendre** — Les commandes manipulent directement des objets
Paper.js vivants (`position.clone()`, `PathItem`). Une commande Sankai doit ne
contenir que des **données sérialisables**, sinon elle ne survit ni à la mise en
arrière-plan de l'activité ni à la sauvegarde Room.

**Adaptation Android** — `interface IslandEditCommand { suspend fun appliquer(sens: Boolean) }`
plutôt que deux méthodes `execute`/`undo`, pour garder l'avantage ci-dessus.
Chaque commande : un `data class` de valeurs, aucune référence à un objet de
rendu.

**Risque** — Faible. Le seul piège est de laisser fuir une référence UI dans une
commande.

---

## 2. Autosauvegarde

**Fichiers** — `app/save.ts` (370 lignes), `app/state.ts`

**Algorithme** — Deux déclencheurs combinés : toutes les
`autosaveActionsInterval = 20` actions, **et** après
`autosaveInactivityTimer = 10000` ms sans activité. Le second rattrape ce que le
premier laisse passer quand on s'arrête à la 19ᵉ action.

**Réutilisable** — La double condition. C'est une bonne idée, et peu coûteuse.

**À ne pas reprendre** — Le stockage. Tout part dans **une seule clé**
`localStorage.setItem('autosave', …)`, écrasée à chaque fois. Il n'existe
aucune copie de la version précédente. Le README du projet reconnaît lui-même
qu'une sauvegarde corrompue peut faire planter le chargement — et avec ce
schéma, il n'y a rien vers quoi se replier.

**Adaptation Android** — Écriture dans Room, en transaction, avec **trois
générations conservées** : courante, dernière valide, précédente. Somme de
contrôle vérifiée à la lecture ; si elle échoue, on remonte d'une génération au
lieu de refuser de démarrer.

**Risque** — Moyen. C'est le point où une erreur détruit la partie d'un joueur.
À traiter en premier, pas en dernier.

---

## 3. Encodage de la carte dans une image

**Fichiers** — `app/save.ts` (`steg.encode(mapJson, mapRasterData, …)`)

**Algorithme** — Stéganographie : le JSON de la carte est écrit dans les bits de
poids faible des pixels d'un PNG. Un seul fichier sert d'aperçu et de
sauvegarde.

**Réutilisable** — L'idée d'un fichier unique qui montre l'île *et* la contient.
Élégante pour du partage.

**À ne pas reprendre** — Le support. Toute recompression, tout redimensionnement
et le moindre passage par une messagerie qui réencode l'image détruisent les
données, sans que rien ne le signale. Un joueur qui partage son île par WhatsApp
envoie une image morte.

**Adaptation Android** — Un `.sankaiisland`, qui est un ZIP contenant
`island_data.json`, `island_preview.png`, `manifest.json` et `checksum.sha256`.
L'aperçu reste une image, les données restent des données. Le format d'archive
borné existe déjà dans le projet (`BoundedZipReader`), avec ses protections
contre les chemins hostiles.

**Risque** — Faible, et bien moindre que la stéganographie.

---

## 4. Versionnement des sauvegardes

**Fichiers** — `app/save.ts`, `app/save-legacy.ts` (228 lignes)

**Algorithme** — Chaque carte porte `version: 'v2'` ; le décodeur choisit son
implémentation d'après ce champ, et `save-legacy.ts` garde intégralement le
décodeur v1. Une carte ancienne n'est jamais abandonnée.

**Réutilisable** — Le principe et la discipline : un décodeur par version,
conservé, plutôt qu'un décodeur unique bardé de conditions.

**À ne pas reprendre** — Rien de nuisible ici.

**Adaptation Android** — Trois champs distincts, car ils évoluent séparément :
`schemaVersion` (forme des données), `generationVersion` (version du
générateur), `appVersionCode`. Une île déjà générée ne doit **jamais** être
régénérée par une mise à jour du générateur : c'est `generationVersion` qui le
garantit.

**Risque** — Faible si posé dès le départ, élevé si ajouté après coup.

---

## 5. Zoom et déplacement tactiles

**Fichiers** — `app/paper-zoom.ts` (253 lignes)

**Algorithme** — Un unique gestionnaire `twofingermove` fait **le zoom et le
déplacement en même temps** :

```
changeZoomCentered(event.deltaScale * 500, event.center);
view.center = … subtract(event.deltaPosition.divide(2));
```

**À ne pas reprendre — et c'est le constat le plus important de cet audit.**
C'est exactement le schéma qui vient d'être retiré de Sankai Life le 1er août
(v1.44.0) : deux traitements qui modifient l'échelle et la position dans le même
geste font glisser le terrain sous les doigts pendant qu'on essaie de
l'agrandir. Reprendre ce code réintroduirait le défaut que l'utilisateur a
signalé et qui vient d'être corrigé et testé.

Le cahier des charges le pressent d'ailleurs (« ne pas utiliser un déplacement à
deux doigts séparé du zoom si cela réintroduit les bugs actuels »). L'audit le
confirme : c'est bien ce que fait le projet de référence.

**Réutilisable en revanche** — Une idée mobile que Sankai n'a pas : **deux
doigts tapotés = annuler, trois doigts = rétablir**, avec garde-fous
(`tapMaxDuration = 300 ms`, `tapMaxMovement = 20 px`) pour distinguer un
tapotement d'un glissement. Gratuit en surface d'écran, ce qui est rare.

**Adaptation Android** — Conserver `CameraJardinEngine` tel quel (zone morte
1,5 %, bornage dans le même calcul, stabilisation 90 ms). Ajouter uniquement les
tapotements multi-doigts pour l'historique, en mode édition.

**Risque** — Élevé si l'on reprend le zoom ; nul pour les tapotements.

---

## 6. Grille et taille de carte

**Fichiers** — `app/constants.ts`, `app/grid.ts` (227 lignes)

**Algorithme** — `7 × 6` blocs de `16 × 16` divisions, soit **112 × 96 =
10 752 tuiles**. C'est la grille d'Animal Crossing, sur un écran d'ordinateur.

**À ne pas reprendre** — Cette taille. Elle est dix fois celle proposée pour
Sankai (32 × 32 = 1 024) et vise un affichage paysage large.

**Réutilisable** — La séparation entre **blocs** et **divisions**. Elle donne
deux niveaux de repère à l'écran, et se transpose bien en une mini-carte par
blocs et une grille fine par divisions.

**Adaptation Android** — 32 × 32 en une seule granularité, conformément au
cahier des charges. La mini-carte se dessine à partir des types de tuiles, sans
seconde grille.

**Risque** — Faible.

---

## 7. Rendu

**Fichiers** — `app/paint.ts`, `app/layers.ts`, `app/drawer.ts`,
`generatedTilesCache.ts`

**Algorithme** — Rendu **vectoriel** Paper.js : la carte est un ensemble de
chemins fusionnés (`uniteCompoundPath`), avec des caches de tuiles pré-générés.

**À ne pas reprendre** — L'approche entière. Sankai dessine des sprites PNG sur
un `Canvas` Compose ; introduire un moteur vectoriel signifierait une dépendance
lourde et un second système de rendu à faire cohabiter avec l'existant
(parcelles, Mimos, météo, éclairage).

**Réutilisable** — Le principe du **cache d'autotuiles** : calculer une fois la
variante de bordure d'une tuile selon ses voisins, puis la réutiliser. Sankai en
a besoin pour les côtes et les chemins.

**Adaptation Android** — Table de correspondance « masque des 4 ou 8 voisins →
ressource », calculée à la génération et mise en cache, jamais recalculée par
frame.

**Risque** — Moyen : c'est le poste qui décide de la fluidité.

---

## Synthèse

| Mécanisme | Verdict |
|---|---|
| Historique par commandes réversibles | **À reprendre** — motif, pas code |
| Double déclencheur d'autosauvegarde | **À reprendre** |
| Décodeur dédié par version | **À reprendre** |
| Tapotements 2/3 doigts = annuler/rétablir | **À reprendre** |
| Cache d'autotuiles | **À reprendre** (principe) |
| Sauvegarde en clé unique écrasée | **À rejeter** — pas de repli |
| Données cachées dans le PNG | **À rejeter** — détruites par recompression |
| Zoom et pan dans le même geste | **À rejeter** — défaut corrigé en v1.44.0 |
| Grille 112 × 96 | **À rejeter** — dimensionnée pour un écran large |
| Rendu vectoriel Paper.js | **À rejeter** — incompatible avec l'existant |

**Aucune ligne de code n'a été copiée.** Les mécanismes retenus sont des motifs
de programmation courants, à réimplémenter en Kotlin. Si cela devait changer,
les obligations de licence sont listées dans `THIRD_PARTY_NOTICES.md` et
s'appliquent **avant** la reprise, pas après.
