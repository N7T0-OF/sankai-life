# Rapport de migration des assets

Source analysée : `G:\2_Logiciel\CLAUDE CODE\EXEMPLE`
Originaux conservés dans : `assets_source/original_png/`

---

## Inventaire réel

**Onze fichiers PNG**, pas les cinquante que le cahier des charges anticipait.
Chacun a été ouvert et regardé avant d'être associé à une fonction — le nom
seul ne suffisait pas à décider, et deux d'entre eux ne correspondaient pas à
ce que leur nom laissait supposer.

| Fichier d'origine | Renommé en | Rôle constaté | Format |
|---|---|---|---|
| `Icône.png` | `app_icon.png` | portrait plein cadre, fond opaque | 2048², sans transparence utile |
| `Pièce .png` | `currency_coin.png` | pièce d'or détourée | 2048², transparent |
| `Graine.png` | `plant_stage_0_seed.png` | graines éparses, détourées | 2048², transparent |
| `Pousse1.png` | `plant_stage_1.png` | germe | 2048², transparent |
| `Pousse 2.png` | `plant_stage_2.png` | pousse | 2048², transparent |
| `Pousse 3.png` | `plant_stage_3.png` | jeune plante | 2048², transparent |
| `Pousse 5.png` | `plant_stage_4.png` | plante mature | 2048², transparent |
| `Pousse 6.png` | `plant_stage_5_ready.png` | tournesol en fleur | 2048², transparent |
| `Terre 1vide.png` | `plot_empty.png` | case de terre claire | 2048², bords irréguliers |
| `Terre 2prepare.png` | `plot_prepared.png` | case de terre moyenne | 2048², bords irréguliers |
| `Terre 3arrosee.png` | `plot_watered.png` | case de terre sombre | 2048², bords irréguliers |

**Il n'y avait pas de « Pousse 4 ».** La numérotation des fichiers saute de 3 à
5. Les six images forment tout de même une progression continue à l'œil ; elles
ont été renumérotées de 0 à 5 pour correspondre aux stades du code.

---

## Ce que l'observation a changé dans le plan

Deux constats ont modifié l'intégration prévue.

**Les plantes sont détourées, les terres sont pleines.** Ce ne sont pas des
images concurrentes mais **deux couches à composer** : la case porte le sol, la
plante se pose dessus. Une même plante pousse donc indifféremment sur une terre
sèche ou détrempée, sans qu'il faille dessiner six variantes de chaque stade.

**L'icône n'a pas de sujet détourable.** C'est un portrait plein cadre. Elle
sert donc de *couche de fond* de l'icône adaptative, la couche avant restant
vide — l'inverse de l'usage habituel, mais le seul montage possible pour une
image sans détourage.

---

## Remplacements effectués

| Ancien asset | Nouveau | Écran | État | Ancien supprimé |
|---|---|---|---|---|
| `art_croissance_graine.xml` | `plant_stage_0_seed.png` | jardin, accueil | remplacé | oui |
| `art_croissance_germe.xml` | `plant_stage_1.png` | jardin, accueil | remplacé | oui |
| `art_croissance_pousse.xml` | `plant_stage_2.png` | jardin, accueil | remplacé | oui |
| `art_croissance_jeune.xml` | `plant_stage_3.png` | jardin, accueil | remplacé | oui |
| `art_croissance_mature.xml` | `plant_stage_4.png` | jardin, accueil | remplacé | oui |
| `art_croissance_recoltable.xml` | `plant_stage_5_ready.png` | jardin, accueil | remplacé | oui |
| `art_humidite_sec.xml` | `plot_empty.png` | cases du jardin | remplacé | oui |
| `art_humidite_legerement.xml` | `plot_empty.png` | cases du jardin | fusionné | oui |
| `art_humidite_humide.xml` | `plot_prepared.png` | cases du jardin | remplacé | oui |
| `art_humidite_bien.xml` | `plot_watered.png` | cases du jardin | remplacé | oui |
| `art_humidite_detrempe.xml` | `plot_watered.png` | cases du jardin | fusionné | oui |
| `art_ressource_piece.xml` | `currency_coin.png` | bandeau du jardin | remplacé | oui |
| `@color/ic_launcher_background` | `mipmap-*/ic_launcher_background.png` | icône adaptative | remplacé | non (couleur conservée ailleurs) |

**Deux fusions assumées.** Cinq paliers d'humidité pour trois illustrations :
la nuance entre « sec » et « un peu sec » ne se lit pas sur une case de 76 dp,
et fabriquer deux variantes intermédiaires par retouche numérique aurait juré
avec le reste.

**La case est remplacée entièrement**, pas recouverte. L'illustration porte sa
propre forme, ses bords irréguliers et sa texture ; aucun fond ni arrondi n'est
dessiné par l'interface derrière elle. Le cadre de surbrillance est posé
*par-dessus* et reste une indication d'interface, pas une partie du jardin.

---

## Tailles produites

Les originaux font 2048² et jusqu'à 6 Mo pièce. Les embarquer tels quels aurait
ajouté plus de 30 Mo à un APK qui en faisait 3,5.

| Destination | Taille | Poids |
|---|---|---|
| `drawable-nodpi/plot_*.png` | 256² | 63 à 92 ko |
| `drawable-nodpi/plant_stage_*.png` | 224² | 4 à 22 ko |
| `drawable-nodpi/currency_coin.png` | 128² | 26 ko |
| `mipmap-*/ic_launcher.png` | 48 à 192 | 7 à 71 ko |
| `mipmap-*/ic_launcher_background.png` | 108 à 432 | 27 à 298 ko |
| `assets_source/ic_launcher-playstore.png` | 512² | 410 ko, hors APK |

`drawable-nodpi` est délibéré : ces images ont une taille unique et Android ne
doit pas les redimensionner par densité.

**APK : 3,53 Mo → 4,61 Mo.** L'essentiel du gain de poids vient des mipmaps de
l'icône, qui sont toutes présentes dans l'APK. Le bundle Play Store n'en livre
qu'une par appareil, donc le téléchargement réel augmente beaucoup moins.

Le script `assets_source/importer-png.ps1` régénère tout depuis les originaux.

---

## Ce qui n'a pas pu être remplacé, faute d'image

Le cahier des charges demandait des coffres, des outils, de l'eau, des
cristaux, des particules de pluie, du brouillard, des lucioles. **Aucune de ces
images n'existe dans le dossier source.**

Ces éléments gardent donc les vectoriels générés en v1.22 :

- coffres (7), outils (3), sols (6), météo (5), phases (4), lieux (3),
  ressources hors pièce (4), brouillard, friche.

Ils sont dans un style volontairement proche mais ce n'est pas la même main.
La liste complète, avec tailles et priorités, est dans
[art/MANIFESTE-ASSETS.md](art/MANIFESTE-ASSETS.md).

---

## Vérifications faites

- chaque PNG source ouvert et regardé avant association ;
- aucune superposition involontaire : la case est une seule image ;
- point d'ancrage identique sur les six stades de plante (pied centré) ;
- transparence conservée (`Format32bppArgb`, interpolation bicubique) ;
- toutes les références de code vérifiées, aucun `R.drawable` cassé ;
- 145 tests unitaires verts ;
- compilation release réussie.

## Vérification impossible sans appareil

Le rendu réel. Netteté à la taille d'affichage, halo blanc éventuel autour des
transparences, contraste du portrait recadré en cercle par le lanceur, lisibilité
du texte de case par-dessus une terre sombre. Rien de tout cela ne se voit dans
un compilateur.


---

# Deuxième vague — textures de terrain (v1.29.0)

Quatre PNG ajoutés au dossier source le 1er août.

| Fichier d'origine | Renommé en | Rôle | Format constaté |
|---|---|---|---|
| `Terre herbe.png` | `plot_grass.png` | terrain non cultivé | 2048², **opaque, carré** |
| `Terre sec.png` | `plot_dry.png` | terre sèche, ou non labourée | 2048², opaque, carré |
| `Terre labourer .png` | `plot_tilled.png` | terre labourée, prête à semer | 2048², opaque, carré |
| `Terre mouillée .png` | `plot_wet.png` | terre arrosée | 2048², opaque, carré |

**Le format change tout.** Les trois premières textures, livrées en juillet,
avaient des bords rongés et de la transparence : il fallait les espacer, et on
voyait le fond du cadre entre les cases. Celles-ci sont des carrés pleins —
elles se joignent bord à bord et forment un sol continu.

## Remplacements

| Ancien asset | Nouveau | Écran | État | Ancien supprimé |
|---|---|---|---|---|
| `plot_empty.png` | `plot_dry.png` | cases du jardin | remplacé | oui |
| `plot_prepared.png` | `plot_tilled.png` | cases du jardin | remplacé | oui |
| `plot_watered.png` | `plot_wet.png` | cases du jardin | remplacé | oui |
| *(rectangle gris)* | `plot_grass.png` | cases non acquises | ajouté | — |
| `art_parcelle_encombree` | `plot_dry.png` | cases à labourer | remplacé | non, encore utilisé pour les terrains rocheux |

## Ce qui a changé dans le rendu

- **écart entre cases : −2 dp → 0**, et la taille est arrondie au pixel entier
  avant de servir de pas. Sans cet arrondi, une taille fractionnaire décale
  chaque case d'un sous-pixel de plus que la précédente, et une ligne claire
  finit par apparaître entre les colonnes lointaines ;
- **`RoundedCornerShape` → `RectangleShape`** sur les cases et leur liseré. Des
  coins arrondis font ressembler un champ à une grille de boutons ;
- **`ContentScale.Fit` → `Crop`** : `Fit` laissait une marge quand la case
  n'était pas exactement carrée, ce qui rouvrait les jointures ;
- **une case verrouillée garde la texture du monde** — l'herbe — et reçoit un
  voile sombre plus un cadenas en couche indépendante. Le cadenas disparaît
  seul au déblocage, sans qu'il faille changer le sol.
