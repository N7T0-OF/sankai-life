# Passation UI Codex

Date : 1er août 2026
Base distante au démarrage : `main` / Sankai Life 1.43.0 (`dc7341c`)

## État du lot en cours

Le worktree contient une refonte UI/UX non encore commitée. Ne repartir ni du
Hub 1.43.0 ni des anciennes consignes « attendre avant de toucher Boutique » :
le nouveau lot demandé explicitement remplace ce jalon.

Le dossier non suivi `AA RESOURCES/` appartient à l'utilisateur. Il a été
préservé intégralement et ne doit pas être ajouté, déplacé, optimisé ou supprimé
sans demande explicite.

## Livré

### Direction commune

- palette bleu nuit et couleurs sémantiques de jeu ;
- bouton principal or en relief, pression animée, haptique et cible 48 dp ;
- réemploi des surfaces Liquid Glass existantes.

### Accueil et Arènes

- Hub responsive : profil, ressources, grande Arène actuelle, CTA Jardin,
  prochaine étape et dock fixe de quatre coffres ;
- coffre prêt indiqué uniquement dans son slot (glow/badge) ;
- parcours vertical de huit Arènes avec ancien/actuel/futur, XP, cadenas,
  récompenses et réclamation ;
- noms, états et toasts FR/EN/PT dans `ArenaUiText.kt` et `strings.xml`.

### Jardin

- mode isolé sans barre basse de l'application ;
- masquage/restauration des barres système pendant l'entrée/sortie ;
- HUD compact, sac flottant catégorisé et conseil en mini-overlay ;
- bulle de parcelle contextuelle et actions rapides ;
- aucun contour permanent, halo discret uniquement pour une cible applicable ;
- états d'humidité, connexion visuelle des voisins, caméra et météo conservés ;
- logique testable dans `GardenContextEngine.kt`.

### Boutique

- bannière d'offre quotidienne ;
- catégories, recherche et filtres ;
- cartes illustrées et aperçu en relief animé ;
- labels et catalogue visibles en FR/EN/PT ;
- économie existante inchangée : `ShopViewModel` reste l'autorité d'achat et
  de remboursement.

### Mémo et Flashcards

- bibliothèque Mémo colorée : langue, cartes, dernière révision estimée,
  prochain rappel, maîtrise, statut actif et cartes dues ;
- actions 48 dp Modifier/Copier/Partager-Exporter/Supprimer ;
- le partage copie automatiquement le texte avant la feuille système ;
- import de module conservé ;
- retour éditeur sauvegardé avant navigation ;
- compteurs dus actualisés chaque minute ;
- « Mes erreurs » devient un entraînement sans XP, pièces, eau ni récolte ;
- suppression du biais `LIMIT 200`, ordre pédagogique conservé et distracteurs
  limités au module source.

### Publication

- IDs AdMob release validés par format, éditeur commun et exclusion du compte
  test Google ;
- `preReleaseBuild` dépend de `verifyReleaseAdmob` ;
- CI : `test`, `lintDebug`, `lintRelease` avant compilation.

## Points techniques à connaître

- `StatsModule.lastReviewedAtMillis` est dérivé de l'échéance et de
  l'intervalle Leitner, sans migration. Après « À revoir », l'heure peut être
  sous-estimée de neuf minutes ; persister une colonne serait nécessaire pour
  une précision absolue.
- l'aperçu Boutique utilise `graphicsLayer(rotationY)` et une ombre 2D ; ce
  n'est volontairement pas un moteur 3D.
- `LiquidGlassSurface` simule le verre par dégradé, transparence et liseré. Un
  vrai blur d'arrière-plan exigerait une dépendance et un budget GPU dédiés.
- aucune piste audio n'a été ajoutée : aucun fichier original/licencié n'était
  fourni. Ne générer ni ne redistribuer de musique tierce pour combler ce vide.

## Validations déjà obtenues

- compilation Kotlin consolidée après intégration Hub/Arènes/Jardin/Boutique/
  Mémo : succès ;
- suite unitaire logique avant la dernière passe UI : 274 tests, 0 échec ;
- `GardenContextEngineTest` : succès ;
- vérification des clés Home/Arena/Garden/Shop sur FR/EN/PT : cohérente ;
- `git diff --check` : propre avant documentation.

La validation finale complète (`test lintDebug lintRelease assembleDebug
assembleRelease bundleRelease`) doit rester la dernière autorité avant commit
ou release. Mettre ce paragraphe à jour avec le résultat réel, sans recycler un
ancien rapport de build.

## Validation appareil prioritaire

1. Accueil : 320 × 568 dp, grand écran et police 200 %.
2. Jardin : barres système au retour, geste arrière, sac vide/plein, chaque
   outil sur cible valide/invalide, météo qualité faible.
3. Boutique : recherche après changement de langue, achat/refund, aperçu et
   retour système.
4. Mémo : dates relatives, copie/partage, module vide, suppression et limite
   de slots.
5. TalkBack : coffres, parcours Arènes, bulle de parcelle, actions Mémo et
   gestes alternatifs Flashcards.

## Suite honnête, non livrée

- illustrations originales et ambiances propres à chaque biome d'Arène ;
- pistes sonores et gestion adaptative de volume ;
- déplacements complets/pathfinding des ouvriers ;
- niveaux persistants distincts Jardin/Mémo/Flashcards/Focus ;
- événements, visiteurs, graines rares et contenu quotidien piloté par données ;
- vrai rendu 3D et vrai flou d'arrière-plan.

Le détail « avant/après/pourquoi/impact/fichiers/à vérifier » de chaque lot se
trouve dans `UI_REFACTOR.md`.

---

# Reprise par Claude — 1er août 2026

Le lot Codex ci-dessus a été **commité tel quel avant toute modification**
(`10b818e`), sans rien retoucher : il n'existait qu'en un exemplaire dans le
worktree. Vérifié avant commit — compilation d'accord, 274 tests, 0 échec.

Ce qui suit s'ajoute par-dessus et ne touche qu'aux points que la passation
déclarait « conservés » : la caméra et la météo.

## Caméra du Jardin

### Ancien système

Trois `pointerInput` empilés dans `GrilleJardin.kt`, un énuméré `ModeGeste`, et
la géométrie écrite à même le composable.

### Cause du bug

Quatre défauts distincts, dont un qui expliquait l'essentiel du tremblement :

1. **`.pointerInput(outil, parcelles.size, zoom)`** — le détecteur de
   glissement était **keyé sur `zoom`**. Or `onZoom()` modifie `zoom` à chaque
   frame du pincement : Compose détruisait et recréait le détecteur **au milieu
   du geste**, en boucle. C'est le défaut principal.
2. **Caméra jamais bornée pendant le zoom.** Elle partait libre, puis un
   `LaunchedEffect(pas, …)` la rattrapait une fois le geste fini — d'où le saut
   de fin de geste.
3. **Aucune zone morte** : seul `facteur == 1f` exactement était filtré. Deux
   doigts posés ne sont jamais immobiles.
4. **Aucune stabilisation** après le pincement : les doigts ne se lèvent jamais
   ensemble, et le dernier encore posé partait aussitôt en glissement.

### Nouveau système

`core/garden/domain/CameraJardinEngine.kt` — flottants purs, aucune dépendance
à Compose, donc testable sans téléphone.

- `franchitSeuil()` — zone morte à **1,5 %**, rejette aussi `NaN`, l'infini et
  les facteurs négatifs qu'un doigt apparaissant en cours de geste peut produire ;
- `pincer()` — applique le **rapport réellement obtenu** et non le facteur
  demandé (aux bornes du zoom, le facteur est écrêté), puis **borne dans le même
  calcul**, avec le pas de la *nouvelle* échelle ;
- `peutDeplacer()` — refuse le glissement pendant le pincement et **90 ms**
  après.

Le détecteur de glissement n'a plus `zoom` en clé ; l'échelle est relue par
`rememberUpdatedState`, qui ne recrée rien.

### Valeurs de zoom

`ZOOM_MIN` / `ZOOM_MAX` inchangés (`GardenViewModel`). Seuil de zone morte
`SEUIL_ZOOM = 0.015f`, stabilisation `STABILISATION_MS = 90L`.

## Ombres de nuages

### Cause

L'**ordre de rendu était déjà correct** — nuages au-dessus du terrain, des
plantes et des Mimos, sous la pluie et le HUD. Rien à corriger de ce côté, et le
dire est plus utile que de prétendre l'avoir réparé.

Le vrai défaut était dans les valeurs, et il était inversé : **l'orage (0,13)
assombrissait moins qu'un ciel simplement nuageux (0,14)**. S'ajoutait la
pondération des couches au dessin (0,72 / 0,46 / 0,31), qui ramenait un ciel
nuageux à 0,10 réel — d'où des ombres qu'on devinait à peine.

### Valeurs d'opacité des nuages

| Météo | Avant | Après | Fourchette demandée |
|---|---|---|---|
| Nuageux | 0,14 | **0,18** | 0,14 – 0,22 |
| Pluie | 0,16 | **0,23** | 0,18 – 0,28 |
| Orage | 0,13 | **0,28** | 0,22 – 0,34 |

Teinte déjà conforme (gris bleu `0xFF536878`), atténuation nocturne conservée.
Un test verrouille l'ordre nuageux < pluie < orage.

## Arbre Sankai

Voir `ASSET_MIGRATION_REPORT.md` pour le détail. En résumé : ancien vectoriel
**supprimé**, PNG réduit de 6,2 Mo à 204 ko, ancrage du tronc **mesuré**
(0,519 / 0,934) et non supposé, système d'emprise 1/2/3/4 cases testé.

## Fichiers modifiés

- `core/garden/domain/CameraJardinEngine.kt` *(nouveau)*
- `core/garden/domain/ArbreSankaiEngine.kt` *(nouveau)*
- `core/garden/domain/WeatherVisualEngine.kt`
- `ui/screens/garden/GrilleJardin.kt`
- `ui/art/ArtJardin.kt`
- `res/drawable-nodpi/tree_sankai.png` *(nouveau)*
- `res/drawable/art_lieu_arbre.xml` *(supprimé)*
- trois fichiers de tests

## Tests effectués

**308 tests, 0 échec** (274 hérités + 34 ajoutés), `lintRelease` sans erreur,
`assembleRelease` d'accord — APK 4,99 Mo.

Dont, pour la caméra : point sous les doigts conservé, zoom hors du centre,
priorité du bornage au bord, non-glissement aux bornes du zoom, **vingt
pincements enchaînés sans dérive**, terrain plus petit que l'écran, fenêtre de
stabilisation.

## Travail restant — et pourquoi il n'a pas été fait

**L'Arbre Sankai n'est pas encore posé dans le Jardin.** Le moteur d'emprise est
complet et testé, mais le poser demande une décision qui n'est pas technique :
la grille n'a **aucune couche de décor**, et réserver les cases centrales
**supprimerait des parcelles que le joueur possède déjà**. Faire disparaître des
parcelles cultivées sans prévenir n'est pas un choix à prendre à sa place. Les
options sont : le poser sur des cases neuves offertes avec l'arbre, l'autoriser
uniquement en bordure, ou accepter la perte contre compensation.

L'arbre est en revanche **déjà visible** : il remplace pour de bon l'icône du
Hub, ancienne comprise.

Non livré, et signalé comme tel plutôt que bâclé :

- les pistes sonores — aucun fichier original ou sous licence n'est fourni, et
  il n'est pas question de redistribuer de la musique tierce pour combler le
  vide ;
- les illustrations propres à chaque biome d'Arène ;
- le pathfinding complet des ouvriers ;
- les événements, visiteurs et graines rares pilotés par données ;
- le vrai rendu 3D et le vrai flou d'arrière-plan.

Le brouillard n'a pas été retouché : il avait été **retiré** sur demande
explicite (« retire le fog au final »), et le point 8 du nouveau cahier des
charges demandait de le refondre. Il n'a pas été réintroduit sans confirmation.

---

# Sprint correctifs île — 1er août 2026 (Claude)

Correctifs issus d'une utilisation réelle sur téléphone, pas d'une relecture.

## 1. Sélection de la mauvaise case

**Problème** — Toucher l'herbe ouvrait la fiche « Océan » ; le sable donnait
« Rocher » ; certaines plages semblaient mortes.

**Cause** — Le détecteur de tape était déclaré
`pointerInput(ile.seed, parcelles.size, niveau, pieces)` : **`zoom` n'était pas
une clé**. La lambda capturait `pas`, la taille d'une case, telle qu'elle était
au moment où le détecteur avait été créé. Après un zoom, elle divisait les
coordonnées par une ancienne valeur, et désignait une case d'autant plus
éloignée que le zoom s'était écarté de 1. À l'échelle 1, tout paraissait
correct — ce qui explique que rien ne l'ait révélé avant un vrai téléphone.

**Solution** — Le pas est relu dans le gestionnaire, jamais capturé.

**Fichiers** — `ui/screens/island/IslandScreen.kt`.

**Résultat** — La case touchée est celle affichée, à tout zoom.

## 2. Zoom qui ne se déclenche presque jamais

**Problème** — Deux doigts posés continuaient de déplacer la caméra ; le
pincement n'était pris en compte qu'exceptionnellement.

**Cause** — Deux causes cumulées.

`detectTransformGestures(panZoomLock = true)` **verrouille le zoom** dès que le
geste est lu comme un déplacement. Deux doigts posés en bougeant un peu partent
en pan, et le zoom ne se déclenche plus du tout pour le reste du geste.

Trois détecteurs indépendants — pincement, glissement, tape — se disputaient le
même flux de doigts, chacun consommant des événements que les autres
attendaient. Le garde-fou de mode ne pouvait rien contre ça : il arrivait trop
tard.

**Solution** — Un seul détecteur, avec une boucle écrite à la main. C'est le
**nombre de doigts** qui décide, pas la direction du mouvement :

```
deux doigts ou plus -> zoom, et rien d'autre
un doigt            -> déplacement
un doigt immobile   -> sélection
```

Un geste ayant touché le zoom ne repasse jamais en déplacement avant que tous
les doigts soient levés.

`CameraJardinEngine` est conservé tel quel : sa géométrie n'était pas en cause,
seule la façon de lui transmettre les gestes l'était.

**Fichiers** — `ui/screens/island/IslandScreen.kt`.

## 3. Barre supérieure derrière l'encoche

**Cause** — Marge fixe en `dp`. Il n'existe aucune hauteur de barre système
universelle : une valeur juste sur un téléphone passe derrière la caméra du
suivant.

**Solution** — `WindowInsets.safeDrawing`, qui couvre barre d'état, encoches et
caméras percées, y compris latérales en paysage. Aucune valeur en dur.

## 4. Océan qui se coupe net

**Cause** — Le balayage de rendu s'arrêtait aux bords de l'île. Au-delà,
seul le fond uni de la vue restait, alors que l'eau de la grille porte une
variation par case : on voyait un carré d'eau texturée posé sur un aplat, donc
la limite du monde.

**Solution** — Le balayage déborde de 24 cases. `ile.type()` rendait déjà de
l'eau profonde hors bornes ; il suffisait de le laisser faire. La marge est
bornée pour ne pas peindre un océan sans fin au dézoom maximum.

## 5. Île trop petite

32 × 32 → **64 × 64**, zoom minimum abaissé pour voir l'île entière. Le coût est
contenu par le culling : le nombre de cases peintes dépend de l'écran, pas de la
taille du monde.

## Deux tests qui mesuraient la mauvaise chose

Le passage à 64 × 64 a fait tomber deux tests, et ils avaient raison de tomber.

`deux iles n'ont pas la meme silhouette` rapportait les différences au nombre
**total** de cases. Sur une grille plus grande, l'océan commun dilue le
résultat : deux îles aussi variées qu'avant tombaient à 7 % d'écart. La mesure
porte désormais sur l'**union des terres**, ce qui ne dépend plus de la taille
du monde.

`l'empreinte change des qu'une case change` écrivait `'W'` à une position fixe.
Sur la nouvelle carte, cette case était déjà de l'eau profonde : le test passait
sans rien vérifier. Il choisit maintenant une lettre forcément différente.

## Tests

412 tests, 0 échec. `lintDebug` et `lintRelease` sans erreur.

## Non fait, et pourquoi

Le reste du sprint — mode Construction, Port, Maison, Atelier Mimo, chemins,
construction progressive, Mimos visibles et marchant, arbres posés sur l'île,
horizon en dégradé — n'est pas traité ici. Ce sont des fonctionnalités, pas des
correctifs, et les livrer sans les avoir vues sur un téléphone reproduirait
exactement ce qui a produit les quatre bugs ci-dessus.

---

# Boutique restaurée + couleurs du téléphone — 2 août 2026 (Claude)

## Boutique : ancienne interface restaurée

**Ancienne interface restaurée** — reprise de `c39ac59`, avant la refonte :
barre de ressources, titre, **onglets** de catégories (Coffres / Jardin /
Progression), grille adaptative de 150 dp, offre du jour sur toute la largeur,
bloc publicité réservé à l'onglet Coffres.

**Fonctions modernes conservées** — la recherche, qui rendait service, et les
illustrations PNG des articles, qui valaient mieux que les emojis d'origine.
`ShopViewModel` n'a pas été touché : les transactions Room, le remboursement,
le calcul du coût réel et l'offre du jour restent ceux de la version actuelle.

La recherche filtre **à l'intérieur de l'onglet courant** et non dans tout le
catalogue : chercher « eau » ne doit pas faire disparaître les onglets sous les
pieds de quelqu'un qui regardait les coffres.

**Composants supprimés** — bannière d'offre pleine hauteur, rails de puces,
cartes à étages, aperçu animé en relief. Ce sont eux qui avaient allongé le
fichier de 308 à 948 lignes sans rendre la Boutique plus lisible.

**Sur « Possédé » et « Équipé »** : ces états n'existent pas dans la Boutique et
n'ont pas été inventés. `ShopItem` n'a ni l'un ni l'autre, et pour cause — les
huit articles sont des consommables (coffres, eau, compost, bouclier, slot).
Les thèmes possédés et équipés vivent dans l'écran Personnalisation, qui n'a pas
changé.

**Fichiers** — `ui/screens/shop/ShopScreen.kt` uniquement.

## Couleurs du téléphone (Material You)

**Ce qui a décidé de l'implémentation** : l'application n'utilise presque pas
`MaterialTheme.colorScheme`. Elle passe par `MaterialTheme.sankaiColors`, une
palette maison. Appeler `dynamicDarkColorScheme` sans plus n'aurait donc
quasiment rien changé à l'écran — c'est le piège de cette tâche.

La palette Sankai emprunte désormais au système ses **accents** et ses
**surfaces**, et garde le reste :

| Rôle Sankai | Source dynamique |
|---|---|
| `accent` | `colorScheme.primary` |
| `accentSecondary` | `colorScheme.tertiary` |
| `surface2` | `colorScheme.surfaceVariant` |
| `surface3` | `colorScheme.surfaceContainerHigh` |
| `border` | `colorScheme.outlineVariant` |

**Les textes ne sont pas repris.** Ceux du système sont calculés pour ses
propres fonds ; les appliquer aux nôtres produirait des contrastes que personne
n'a vérifiés.

**Rien de sémantique ne bouge** — l'eau reste bleue, une erreur rouge, une
récompense dorée, un verrou gris. Un thème jaune qui transformerait l'eau en
jaune rendrait l'interface illisible tout en ayant l'air « personnalisée ».

**Les illustrations ne sont jamais teintées** : le Jardin, l'Île, les arbres,
les sols, les pièces et les coffres sont des dessins, pas des composants.

**Repli** — sous Android 12, les couleurs Sankai sont utilisées et le réglage
est grisé avec son motif affiché, plutôt que masqué sans explication.

**Application immédiate** — la préférence est lue en continu depuis DataStore au
niveau racine ; basculer le réglage recompose le thème sans relancer l'activité
ni perdre l'écran en cours.

**Fichiers** — `ui/theme/Theme.kt`, `MainActivity.kt`,
`core/data/preferences/AppPreferences.kt`,
`ui/screens/settings/SettingsViewModel.kt`, `ui/screens/settings/SettingsScreen.kt`.

## Non fait, et pourquoi

Les options « Intensité des couleurs système » (discrète / normale / renforcée)
et le mode AMOLED distinct ne sont pas livrés. La première demanderait de
recalculer une palette à partir de celle du système, ce qui revient à refaire le
travail que Material You fait déjà ; la seconde mérite d'être traitée avec le
mode sombre existant plutôt qu'ajoutée à côté.

`DYNAMIC_COLOR_MIGRATION_REPORT.md` n'a pas été produit : aucune couleur codée
en dur n'a été migrée, puisque la bascule se fait dans la palette maison et non
écran par écran. Un rapport listant zéro migration n'apprendrait rien.
