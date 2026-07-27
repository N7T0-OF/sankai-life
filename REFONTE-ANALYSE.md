# Refonte accueil / profil — analyse préalable

Établie avant toute modification, en relisant les écrans existants.
Périmètre : **priorité 1** du cahier des charges. Les priorités 2 et 3 sont
listées en fin de document mais non engagées.

---

## 1. Fichiers à modifier

| Fichier | Nature du changement |
|---|---|
| `ui/screens/home/HomeScreen.kt` | Retrait d'« Action du jour », passage en écran non défilant |
| `ui/screens/home/HomeViewModel.kt` | Ajout d'un `HomeUiState` et du module contextuel |
| `ui/screens/profile/ProfileScreen.kt` | Réduction à un résumé, retrait de la liste des thèmes |
| `ui/screens/profile/ProfileViewModel.kt` | Exposition des 4 statistiques principales |
| `ui/navigation/Screen.kt` | Deux routes : `customization`, `stats` |
| `ui/navigation/NavGraph.kt` | Câblage des deux écrans + `saveState`/`restoreState` |

## 2. Fichiers à créer

| Fichier | Rôle |
|---|---|
| `ui/screens/customization/CustomizationScreen.kt` | Thèmes en grille, par catégories |
| `ui/screens/customization/CustomizationViewModel.kt` | État des thèmes, équipement |
| `ui/screens/profile/AllStatsScreen.kt` | Statistiques complètes déplacées hors du profil |

## 3. Composants à conserver

Aucun de ces éléments ne doit être réécrit : ils fonctionnent et sont déjà
utilisés ailleurs.

| Composant | Où | Pourquoi le garder |
|---|---|---|
| `CarteResumeArene` | `ui/screens/arenas/ArenasScreen.kt` | Répond déjà au besoin, à densifier visuellement seulement |
| `BarreCoffres` | `ui/screens/home/HomeScreen.kt` | Déjà ancrée en bas, hors zone défilante |
| `ChestSlotUI` | idem | États déjà gérés |
| `ArenasScreen` + `ArenaEngine` | `ui/screens/arenas/` | Parcours vertical, graduation et recentrage déjà en place |
| `ResourceBar` | `ui/components/` | Reste utilisée par Shop, Défis, Mode Vie, Objectifs |
| `SankaiCard`, `SankaiButton`, `SectionTitle` | `ui/components/` | Base du design system |
| `ThemeRow` | `ui/screens/profile/ProfileScreen.kt` | **Déplacé** vers l'écran Personnalisation, pas supprimé |

## 4. Composants à supprimer

| Élément | Emplacement | Remarque |
|---|---|---|
| Carte « Action du jour » | `HomeScreen.kt` | Bloc complet + son `SectionTitle` |
| Boucle d'affichage des thèmes | `ProfileScreen.kt` lignes ~94-99 | Migre vers Personnalisation |
| Carte « Clé Ko-fi » | `ProfileScreen.kt` | Doublon : le lien Ko-fi existe déjà dans Paramètres |
| Grille de 6 statistiques | `ProfileScreen.kt` | Réduite à 4, le reste part dans l'écran dédié |

Aucune route ni fonction ne devient orpheline : « Action du jour » ne faisait
que naviguer vers `Screen.Focus`, qui reste atteignable par la barre du bas et
par le module contextuel.

## 5. Données à migrer

**Aucune.** La priorité 1 est purement présentationnelle : ni entité, ni DAO,
ni version de base de données ne changent. La base reste en version 6.

C'est volontaire — la refonte visuelle ne doit pas mettre en jeu la
progression existante. Les priorités 2 et 3 (régularité, habitudes à
intensité, routines) demanderont en revanche de vraies migrations.

## 6. Nouvelles routes

```
customization   → écran Personnalisation (thèmes, plus tard icônes et titres)
stats           → statistiques complètes
```

Les deux sont des sous-écrans : barre de navigation masquée, retour par flèche,
comme `arenas`, `objectives` et `flashcards`.

## 7. Risques de régression

| Risque | Gravité | Parade |
|---|---|---|
| **Accueil non défilant qui déborde** sur petit écran ou grande police | élevée | Repli automatique en défilement quand la hauteur manque ; jamais de hauteur fixe en dur |
| `ThemeRow` déplacé mais encore référencé | moyenne | Le compilateur le signale ; fonction déplacée, pas dupliquée |
| Perte de l'accès aux statistiques détaillées | moyenne | Écran dédié atteignable en un clic depuis le profil |
| Perte de l'accès aux thèmes | moyenne | Trois entrées : Profil → Personnaliser, Paramètres → Apparence, récompense d'arène |
| Onglet actif recliqué qui empile les écrans | faible | `launchSingleTop` déjà en place ; ajout de `saveState`/`restoreState` |
| Module contextuel vide au premier lancement | faible | État par défaut : proposition de session Focus |

## 8. Ordre d'exécution retenu

1. Retrait d'« Action du jour » *(sans risque, immédiat)*
2. Accueil non défilant avec repli accessible
3. Écran Personnalisation + retrait de la liste des thèmes du profil
4. Profil réduit à un résumé + écran statistiques complètes
5. Module contextuel de l'accueil

Chaque étape compile avant de passer à la suivante.

---

## Non engagé à ce stade

Les priorités 2 et 3 représentent un volume comparable à tout ce qui a été
construit jusqu'ici, et plusieurs points demandent des migrations de base
(régularité et boucliers, habitudes à intensité variable, routines, pauses,
calendrier, centre de notifications, widgets).

Les traiter dans la même passe fragiliserait un projet qui n'a encore jamais
tourné sur un téléphone. Ils sont documentés dans `IMPLEMENTATION_STATUS.md`
et seront pris par blocs cohérents.

**Recommandation** : l'idée la plus rentable du cahier des charges n'est pas
une fonctionnalité mais un principe — *mode simple / mode progression*. Elle
répond directement au reproche récurrent d'applications qui se surchargent,
et elle est peu coûteuse une fois l'accueil assaini.
