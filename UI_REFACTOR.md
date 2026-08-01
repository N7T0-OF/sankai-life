# Refonte UI/UX — hub de jeu premium

Date : 1er août 2026
Base : Sankai Life 1.43.0

Les deux visuels fournis ont servi de références de hiérarchie, de densité et
de navigation. Aucun bitmap, logo, personnage ou asset d'un autre jeu n'a été
copié. Les illustrations affichées viennent des ressources Sankai existantes
ou de composants Compose natifs.

## 1. Direction visuelle et contrôles communs

**Écran**

Tous les écrans Compose utilisant le thème Sankai et `SankaiButton`.

**Avant**

Palette presque noire, surfaces grises et bouton principal plat. Les écrans
fonctionnaient mais ne partageaient pas la profondeur du nouveau hub.

**Après**

Palette bleu nuit commune, intentions de couleur stables (bleu jeu, vert
Jardin, or récompense) et boutons à dégradé, liseré, ombre légère, pression
animée et retour haptique. Les surfaces Liquid Glass existantes restent
translucides sans faux flou coûteux.

**Pourquoi**

Une identité commune évite que chaque écran ressemble à une application
différente et réserve l'or aux actions ou récompenses importantes.

**Impact UX**

Hiérarchie plus immédiate, meilleur ressenti tactile et cohérence visuelle
entre Accueil, Arènes, Jardin, Boutique et Mémo.

**Fichiers modifiés**

- `SankaiLife/app/src/main/java/com/sankailife/ui/theme/Color.kt`
- `SankaiLife/app/src/main/java/com/sankailife/ui/components/CommonComponents.kt`

**À vérifier**

- contraste du thème clair ;
- animation de pression avec « Supprimer les animations » activé ;
- rendu des boutons longs en portugais et avec une police à 200 %.

## 2. Accueil — HUB principal

**Écran**

Accueil.

**Avant**

Disposition compacte d'application : l'Arène, le Jardin, la progression et
les coffres existaient, mais l'objectif immédiat et la prochaine récompense ne
dominaient pas assez la lecture.

**Après**

Hub responsive structuré en profil, ressources, grande Arène actuelle, appel
principal « Entrer dans le Jardin », progression vers l'Arène suivante et dock
fixe de quatre coffres. Un coffre prêt est signalé dans son emplacement par un
glow et un badge ; aucun encart « coffre prêt » ne concurrence l'Arène.

**Pourquoi**

Le joueur doit comprendre sa position, son action principale et sa prochaine
récompense en moins de deux secondes.

**Impact UX**

Le Jardin devient la destination évidente, tandis que progression et coffres
restent consultables sans détour.

**Fichiers modifiés**

- `SankaiLife/app/src/main/java/com/sankailife/ui/screens/home/HomeScreen.kt`
- `SankaiLife/app/src/main/java/com/sankailife/ui/screens/arenas/ArenaUiText.kt`
- `SankaiLife/app/src/main/res/values*/strings.xml`

**À vérifier**

- téléphone 320 × 568 dp et grand téléphone ;
- quatre coffres avec noms et temps longs ;
- ouverture d'un coffre prêt, emplacement vide et coffre en cours ;
- TalkBack et fontScale 2,0.

## 3. Parcours des Arènes

**Écran**

Arènes.

**Avant**

Liste fonctionnelle mais visuellement uniforme, avec peu de différence entre
les paliers franchis, courant et futurs.

**Après**

Parcours vertical de huit Arènes : anciennes colorées, actuelle mise en avant,
futures atténuées et verrouillées. Chaque palier expose niveau, XP, ambiance,
récompense et état de réclamation. Le retour à la progression recentre
réellement la liste.

**Pourquoi**

Les Arènes portent la progression globale ; leur état doit être lisible sans
ouvrir chaque fiche.

**Impact UX**

Objectif suivant plus clair, récompenses prévisibles et aucune action de
réclamation mensongère.

**Fichiers modifiés**

- `SankaiLife/app/src/main/java/com/sankailife/ui/screens/arenas/ArenasScreen.kt`
- `SankaiLife/app/src/main/java/com/sankailife/ui/screens/arenas/ArenasViewModel.kt`
- `SankaiLife/app/src/main/java/com/sankailife/ui/screens/arenas/ArenaUiText.kt`
- `SankaiLife/app/src/main/res/values*/strings.xml`

**À vérifier**

- niveaux juste avant, exactement sur et après un déblocage ;
- récompense déjà prise et récompense disponible ;
- retour/recentrage après ouverture d'une fiche ;
- noms FR, EN et PT sur petit écran.

## 4. Jardin — mode de jeu isolé

**Écran**

Jardin.

**Avant**

HUD dense, barre d'inventaire permanente et informations de parcelles trop
présentes. Les cases pouvaient se lire comme une grille de boutons.

**Après**

Mode plein écran isolé : navigation de l'application masquée, barres système
occultées pendant la présence dans le Jardin puis restaurées à la sortie. Le
retour reste toujours accessible. Le HUD est compact ; le sac et le conseil
sont deux boutons flottants ouvrant de petits overlays.

Le sac ne montre que les catégories et objets réellement possédés. Une case
touchée ouvre une bulle contextuelle avec les seules actions applicables. Les
contours permanents disparaissent ; un halo discret apparaît uniquement sur
une cible valable pour l'outil sélectionné.

Les états sec, humide et très humide utilisent les textures existantes. Les
parcelles adjacentes calculent leurs voisins afin de réduire les séparations
artificielles. La caméra, les gestes, les nuages, la pluie, l'ambiance horaire
et les réglages de qualité restent actifs.

**Pourquoi**

Le terrain doit être perçu comme un lieu vivant avant d'être perçu comme une
grille d'administration.

**Impact UX**

Moins de bruit visuel, actions tactiles contextualisées et immersion plus
forte sans supprimer l'accès aux ressources ou au retour.

**Fichiers modifiés**

- `SankaiLife/app/src/main/java/com/sankailife/ui/navigation/NavGraph.kt`
- `SankaiLife/app/src/main/java/com/sankailife/ui/screens/garden/GardenScreen.kt`
- `SankaiLife/app/src/main/java/com/sankailife/ui/screens/garden/GrilleJardin.kt`
- `SankaiLife/app/src/main/java/com/sankailife/ui/screens/garden/HudJardin.kt`
- `SankaiLife/app/src/main/java/com/sankailife/core/garden/domain/GardenContextEngine.kt`
- `SankaiLife/app/src/test/java/com/sankailife/core/garden/domain/GardenContextEngineTest.kt`
- `SankaiLife/app/src/main/res/values*/strings.xml`

**À vérifier**

- restauration des barres système après retour, rotation interdite et reprise
  après mise en arrière-plan ;
- chaque outil sur cible valide et invalide ;
- sac vide, partiellement rempli et complet ;
- pluie/orage avec qualité faible et téléphone d'entrée de gamme ;
- ordre TalkBack de la bulle, du sac et du conseil.

## 5. Boutique

**Écran**

Boutique.

**Avant**

Trois onglets, cartes génériques, textes français intégrés au code et offre du
jour répétée sans véritable mise en scène.

**Après**

Bannière d'offre quotidienne, recherche localisée, catégories, filtres « tous
les objets / accessibles / promotion », cartes illustrées et aperçu en relief
animé. Les coffres, l'eau et le compost réutilisent l'art Sankai ; les autres
objets utilisent les icônes Material. L'aperçu est une mise en profondeur
Compose légère, pas un faux moteur 3D.

Le ViewModel reste l'unique autorité d'achat : prix progressif, débit,
livraison et remboursements n'ont pas été dupliqués dans l'UI.

**Pourquoi**

La Boutique doit faciliter une décision, pas seulement présenter une grille
de prix.

**Impact UX**

Catalogue explorable, prix plus lisibles, objets identifiables et détail
consultable avant achat.

**Fichiers modifiés**

- `SankaiLife/app/src/main/java/com/sankailife/ui/screens/shop/ShopScreen.kt`
- `SankaiLife/app/src/main/res/values*/strings.xml`

**À vérifier**

- achat pièces, gemmes et slot à coût progressif ;
- remboursement quand les coffres ou la réserve d'eau sont pleins ;
- recherche avec accents et changement de langue ;
- largeur 320 dp, fontScale 2,0 et aperçu fermé par retour système ;
- disponibilité réelle de la publicité récompensée.

## 6. Mémos

**Écran**

Bibliothèque Mémo.

**Avant**

Liste de cartes très utilitaire : langue, dernière révision, prochain rappel
et maîtrise étaient absents. Les actions secondaires faisaient 36 dp et le
partage ne conservait pas automatiquement le texte.

**Après**

Bibliothèque de modules colorés affichant langue BCP-47, nombre de cartes,
dernière révision estimée, prochain rappel, progression de maîtrise et cartes
dues. Les actions Modifier, Copier, Partager/Exporter et Supprimer ont des
cibles de 48 dp. Le partage copie d'abord le texte local dans le presse-papiers
puis ouvre la feuille système ; l'import de module reste disponible. Le bouton
de révision transforme directement le contenu dû en session Flashcards.

La maîtrise vient des points de boîtes Leitner. La dernière révision est
dérivée de l'échéance et de l'intervalle courant sans migration ; après « À
revoir », cette estimation peut être antérieure de neuf minutes au maximum.

**Pourquoi**

Un Mémo doit ressembler à un parcours d'apprentissage identifiable, pas à un
simple profil de notifications.

**Impact UX**

État pédagogique visible avant l'ouverture, actions plus sûres et partage
moins fragile.

**Fichiers modifiés**

- `SankaiLife/app/src/main/java/com/sankailife/ui/screens/life/memo/MemoScreen.kt`
- `SankaiLife/app/src/main/java/com/sankailife/ui/screens/life/memo/MemoEditorScreen.kt`
- `SankaiLife/app/src/main/java/com/sankailife/ui/screens/life/memo/MemoViewModel.kt`
- `SankaiLife/app/src/main/java/com/sankailife/core/data/db/dao/AllDaos.kt`
- `SankaiLife/app/src/main/res/values*/strings.xml`

**À vérifier**

- module vide, langue vide et module de plusieurs centaines de cartes ;
- date relative après chaque type de jugement ;
- copie et partage avec Android 12 puis Android 13+ ;
- activation au maximum de slots ;
- suppression et retour système de l'éditeur pendant une sauvegarde.

## 7. Flashcards et « Mes erreurs »

**Écran**

Flashcards, éditeur Mémo et entraînement « Mes erreurs ».

**Avant**

Le profil « Mes erreurs » pouvait être relancé pour gagner indéfiniment XP,
pièces, eau et récoltes. Une limite SQL pouvait masquer les cartes les plus
problématiques, et des réponses d'autres modules pouvaient servir de leurres.
Le retour système de l'éditeur pouvait précéder la sauvegarde.

**Après**

Le swipe quatre directions, les haptics, la progression et l'écran de fin sont
conservés. « Mes erreurs » reste un entraînement SRS réel mais ne donne plus
aucune récompense économique. La sélection complète est triée par le moteur,
les distracteurs restent dans le module source, les compteurs dus avancent
chaque minute et l'éditeur attend la sauvegarde avant de revenir.

**Pourquoi**

Le polish visuel ne doit jamais masquer une boucle économique exploitable ou
une perte de données.

**Impact UX**

Sessions pédagogiquement cohérentes, économie protégée et retour éditeur
fiable.

**Fichiers modifiés**

- `SankaiLife/app/src/main/java/com/sankailife/core/domain/engine/FlashcardEngine.kt`
- `SankaiLife/app/src/main/java/com/sankailife/core/domain/engine/ExerciceEngine.kt`
- `SankaiLife/app/src/main/java/com/sankailife/ui/screens/life/flashcards/FlashcardsViewModel.kt`
- `SankaiLife/app/src/main/java/com/sankailife/ui/screens/life/memo/MemoEditorScreen.kt`
- `SankaiLife/app/src/main/java/com/sankailife/ui/screens/life/memo/MemoViewModel.kt`
- tests moteurs associés.

**À vérifier**

- quatre directions et boutons alternatifs avec TalkBack ;
- entraînement erreurs répété plusieurs fois : aucun gain ;
- QCM multi-modules ;
- éditeur fermé par bouton, geste système et clavier.

## 8. Publication et validation AdMob

**Écran**

Chaîne de publication, sans changement visible en debug.

**Avant**

Une chaîne AdMob mal formée ou deux identifiants de comptes différents
pouvaient être considérés comme « production » tant qu'ils ne correspondaient
pas exactement aux deux IDs de test connus.

**Après**

La release valide le format des deux IDs, leur éditeur commun et refuse le
compte de démonstration Google. `preReleaseBuild` dépend de cette vérification
et le workflow l'exécute explicitement. La CI exécute aussi tests, lint debug
et lint release avant de produire un artefact.

**Pourquoi**

Une interface récompensée ne doit jamais annoncer une action qu'une release
mal configurée serait incapable d'exécuter.

**Impact UX**

Moins de risque de bouton publicitaire définitivement indisponible en
production.

**Fichiers modifiés**

- `SankaiLife/app/build.gradle.kts`
- `.github/workflows/build-apk.yml`

**À vérifier**

- `verifyReleaseAdmob` avec les IDs du dépôt ;
- échec attendu avec un ID de test, mal formé ou d'un autre éditeur ;
- secrets GitHub avant la prochaine release.

## Éléments volontairement non simulés

Cette passe ne crée pas de contenu fictif pour cocher une case. Restent à
produire avec des assets et/ou un modèle de données dédiés :

- illustrations originales de biome pour chaque Arène ;
- vraies pistes sonores licenciées (jour, nuit, pluie, serre, Boutique) et
  réglage de volume associé ;
- pathfinding et animations complètes des ouvriers ;
- niveaux persistants séparés Jardin, Mémo, Flashcards et Focus ;
- événements temporaires, visiteurs et récompenses rares pilotés par données ;
- vrai rendu 3D et vrai flou d'arrière-plan. Les effets actuels sont des
  alternatives Compose légères et honnêtes.

Le dossier utilisateur non suivi `AA RESOURCES/` n'a été ni déplacé, ni
modifié, ni ajouté au build.
