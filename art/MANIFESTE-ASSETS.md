# Assets Sankai Life — manifeste

Ce dossier contient les dessins de l'application et la façon de les remplacer.

---

## Comment substituer tes vraies illustrations

Chaque élément est un fichier `SankaiLife/app/src/main/res/drawable/art_<nom>`.

**Poser un PNG ou un WebP du même nom à la place du XML suffit.** Aucun code ne
change, aucune constante n'est à mettre à jour — la correspondance vit
entièrement dans [ArtJardin.kt](../SankaiLife/app/src/main/java/com/sankailife/ui/art/ArtJardin.kt).

```
1. supprimer  res/drawable/art_croissance_mature.xml
2. déposer    res/drawable/art_croissance_mature.webp
3. recompiler
```

Format conseillé : **WebP sans perte, 192 × 192**, fond transparent. Le
vectoriel actuel occupe 2 à 4 ko ; un WebP de cette taille en occupe 8 à 15.
Pour 56 éléments, l'APK grossit d'environ 0,5 Mo — acceptable.

Si tu préfères des tailles par densité, la structure Android habituelle
fonctionne aussi (`drawable-mdpi/`, `-hdpi`, `-xhdpi`, `-xxhdpi`, `-xxxhdpi`).

---

## Ce qui existe aujourd'hui

56 vectoriels générés par [generer-assets.py](generer-assets.py), au trait
crayonné : contours tremblés, trait repassé, aplats hachurés.

Ce sont des **brouillons honnêtes**, pas les illustrations de la planche de
référence. Ils remplissent leur rôle — l'application est lisible et cohérente —
mais ils n'ont ni la matière, ni la lumière, ni le détail d'un vrai dessin.

| Famille | Nombre | Utilisé où |
|---|---|---|
| `art_croissance_*` | 6 | cases du jardin, fiche de parcelle |
| `art_sol_*` | 6 | types de terrain |
| `art_humidite_*` | 5 | couleur du sol selon l'eau |
| `art_parcelle_*` | 4 | friche, brouillard, terre libre |
| `art_meteo_*` | 5 | bandeau du jardin |
| `art_phase_*` | 4 | cycle jour / nuit |
| `art_ressource_*` | 6 | barre de ressources |
| `art_outil_*` | 6 | barre d'outils |
| `art_coffre_*` | 7 | barre des coffres de l'accueil |
| `art_lieu_*` | 4 | dépôt, magasin, serre, arbre Sankai |
| `art_badge_*` | 3 | missions et récompenses |

Pour les revoir toutes d'un coup : ouvrir [planche.html](planche.html) dans un
navigateur.

Pour régénérer après modification du script :

```bash
python art/generer-assets.py
```

---

## Ce que le générateur ne sait pas produire

Ces éléments demandent un vrai illustrateur. Ils restent des emojis dans
l'application, et rien ne casse tant qu'ils ne sont pas fournis.

### Icône de l'application — priorité haute

| Fichier | Taille |
|---|---|
| `ic_launcher-playstore.png` | 512 × 512 |
| `mipmap-xxxhdpi/ic_launcher.png` | 192 × 192 |
| `mipmap-xxhdpi/ic_launcher.png` | 144 × 144 |
| `mipmap-xhdpi/ic_launcher.png` | 96 × 96 |
| `mipmap-hdpi/ic_launcher.png` | 72 × 72 |
| `mipmap-mdpi/ic_launcher.png` | 48 × 48 |

Prévoir aussi la version adaptative Android : un `ic_launcher_foreground` sur
fond transparent, avec 25 % de marge de sécurité sur chaque bord — le système
recadre en cercle, en carré arrondi ou en goutte selon le téléphone.

### Les huit arènes — priorité haute

Dioramas isométriques, un par palier. Ils apparaissent sur l'accueil et en tête
du jardin.

`art_arene_1` … `art_arene_8`, 512 × 512.

Progression prévue : parcelle abandonnée, premières pousses, serre des
connaissances, bosquet des habitudes, jardin nocturne, terrasse des mémoires,
sanctuaire botanique, canopée Sankai.

### L'arbre Sankai — priorité moyenne

Huit états, alignés sur les arènes. `art_arbre_1` … `art_arbre_8`, 256 × 256.

### Les Mimos — priorité moyenne

Cinq métiers existent dans le code : arroseur, récolteur, transporteur,
vendeur, planteur. La planche en prévoit d'autres, pas encore implémentés —
inutile de les dessiner avant.

`art_mimo_arroseur`, `art_mimo_recolteur`, `art_mimo_transporteur`,
`art_mimo_vendeur`, `art_mimo_planteur`. 128 × 128.

### Animaux, décorations — priorité basse

Non implémentés dans le jeu. Les dessiner maintenant serait prématuré : leur
comportement n'est pas défini, donc leurs poses non plus.

---

## Palette

Reprise de la planche de référence, en légèrement désaturé — un crayon de
couleur sur papier, pas un aplat numérique.

| Rôle | Code |
|---|---|
| Trait | `#3B3025` |
| Papier | `#F2EADB` |
| Vert feuille | `#6E9B57` |
| Vert profond | `#41703C` |
| Terre claire | `#8A6A45` |
| Terre humide | `#573F28` |
| Sable | `#D8C08A` |
| Eau | `#6FA8C7` |
| Or | `#D9A441` |
| Bois | `#9A6E43` |
| Pierre | `#9AA0A6` |
| Violet mystique | `#8A6BB0` |
| Nuit | `#3A4670` |
| Cristal | `#8FD4D9` |

---

## Note sur les emojis restants

Ils n'ont pas tous vocation à disparaître. Un chantier (`🚧`) ou une flèche sont
des états passagers de l'interface, pas des éléments du jardin. Les remplacer
par des dessins alourdirait l'APK sans rien gagner en cohérence.

Ce qui doit devenir dessin, c'est ce qui **appartient au monde** : plantes,
sols, outils, coffres, lieux, habitants.
