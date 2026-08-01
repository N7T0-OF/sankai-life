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
