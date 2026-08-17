# Recentrage produit de Sankai Life

Date de référence : 8 août 2026 (mise à jour : 17 août 2026)
État : contrat produit et plan de migration, fondés sur le code non commité présent dans le dépôt.

## Mise à jour du 17 août — la direction est assumée

Le recentrage est acté dans le code. **Sankai Life n'a plus de section
« Jeu » : quatre sections, et une seule promesse — ouvrir, apprendre,
fermer.**

| Section | Rôle |
|---|---|
| 🏠 **Accueil** | Écran statique : continuer, découverte du jour, prochain rappel, terminer. |
| 📚 **Apprendre** | Le cœur : révision express ⚡, recommandation du jour, parcours, modules, découverte. |
| 🌱 **Vie** | Les outils personnels : mémos, focus, objectifs. (Le jardin reviendra comme couche de progression calme, jamais comme jeu séparé.) |
| 👤 **Profil** | Progression, statistiques, personnalisation. |

Principes verrouillés par ce document :

- aucune monnaie nécessaire pour apprendre ;
- aucune notification culpabilisante (pas de « ton streak est en danger ») ;
- la révision express se termine seule, sans invitation à continuer ;
- la découverte du jour est une carte, pas un fil ;
- désinstaller une extension ne supprime aucune connaissance.

## La philosophie « Sankai Companion » (17 août)

**Si Android sait déjà faire quelque chose correctement, Sankai ne le
recrée pas : il s'y connecte et valorise son utilisation.** Le téléphone
fait l'action, Sankai la transforme en progression.

- l'apprentissage, la mémoire, la culture : le cœur natif de Sankai — ce
  que le téléphone ne fait pas déjà bien ;
- le calendrier Android, le minuteur système, les rappels : des sources
  de progression connectées, jamais recréées ;
- le jardin et l'Arbre Sankai : la représentation visuelle de cette
  progression, pas un jeu à part.

Le moteur anti-farm est en place : chaque source (Calendrier,
Concentration, Apprentissage, Découverte) a un plafond quotidien et une
XP dégressive (20, 15, 10, 5…). Rien n'est jamais retiré ni pénalisé.

Étapes suivantes : la source Calendrier (permission par permission, en
ne lisant que l'essentiel), la source Concentration connectée au
minuteur système, et la page « Activités connectées » dans les
Paramètres.

## Décision produit

Sankai Life doit devenir un compagnon local pour apprendre, mémoriser, se
concentrer et découvrir un contenu culturel bref. L'application ne doit plus
présenter son économie, ses coffres, ses séries ou son Jardin comme la raison
principale de revenir. Ces éléments peuvent subsister, mais comme décor ou
extension facultative, jamais comme dette quotidienne.

La promesse cible est simple : **ouvrir, accomplir une action utile, puis
pouvoir partir sans pénalité**.

## Architecture de l'expérience cible

| Espace | Rôle | Statut observé dans le diff |
|---|---|---|
| Aujourd'hui | Une action d'apprentissage, une capsule, le prochain mémo, puis « Terminer » | Écran principal refondu ; fermeture volontaire de l'application après validation |
| Académie | Parcours, modules et révisions | Route et onglet dédiés présents |
| Capsules | Une découverte culturelle locale et finie | Écran, ViewModel et destination du graphe présents |
| Mode Vie | Mémo et Focus en premier ; objectifs et anciens systèmes en second | Nouvel écran présent |
| Profil | Réglages personnels et progression lisible | Recentrage partiel dans le diff |
| Extensions | Installer ou retirer le pack Jardin local | Écran, ViewModel, route, sélecteur de fichier et store présents ; gestion limitée au Jardin |
| Jardin / Île | Extension contemplative facultative | Toujours compilée et persistée dans `:app` ; routes gardées par l'installation du pack |

La barre inférieure reflète déjà cette direction dans
`BottomNavBar.kt` : Aujourd'hui, Académie, Capsules, Mode Vie et Profil
remplacent Boutique, Mode Vie, Accueil, Défis et Profil. Cela change la
hiérarchie, pas encore la composition physique de l'APK.

## Ce qui est réellement fait

- `HomeScreen` met en avant les cartes dues, une capsule, le prochain mémo et
  un bouton de fin de journée. Le bouton enregistre la date puis ferme la tâche
  Android ; il ne crée pas une nouvelle récompense.
- `AppPreferences` ajoute un mode minimal, un objectif choisi de 0, 2, 5, 10
  ou 15 minutes, une pause des notifications, un week-end silencieux et des
  catégories indépendantes.
- `NotificationPolicy` centralise les heures silencieuses, la pause, le
  week-end et un budget quotidien de 1 à 3 notifications. Culture et Jardin
  sont silencieux par défaut. `NotificationCoordinator` réconcilie les alarmes
  au démarrage, après réglage et après les événements système.
- `FocusRewardEngine` rend les petites sessions proportionnelles et plafonne
  les XP/pièces historiques conservés pour compatibilité. Focus ne crée plus
  aucun coffre et ne progresse plus aucun défi ; ces gains restent en arrière-
  plan et ne sont pas exposés dans l'expérience minimale.
- Quand l'écran Focus n'est plus actif, une notification de fin discrète peut
  être envoyée si la catégorie Focus et la politique globale l'autorisent.
  Les notifications Mémo, Révision et Culture transportent désormais leur
  destination vers Mémo, Académie ou Capsules.
- `core/culture` fournit un format de paquet local sans code, un importeur ZIP
  borné, un stockage atomique et une sélection quotidienne déterministe.
- `ui/screens/capsules` fournit maintenant une lecture finie d'une capsule, une
  face provenance, un favori et une réflexion locale. Le ViewModel reconstruit
  et valide le pack embarqué sans passe-droit.
- `core/extensions` fournit aussi un manifeste `.sankaipack`, un inspecteur de
  payload passif, un store local atomique, un petit snapshot Jardin et 17 tests
  unitaires dans les sources.
- `ExtensionsScreen` et `ExtensionsViewModel` permettent de choisir un
  `.sankaipack`, n'acceptent que `sankai.garden` / `garden.home`, affichent son
  état et ouvrent ou désinstallent le Jardin. Les routes Jardin et Île
  redirigent vers ce gestionnaire lorsque le pack est absent.
- Le dépôt ne fournit pas encore d'archive `.sankaipack` Jardin installable :
  l'écran et le store existent, mais le fichier doit encore être produit et
  distribué avec droits et provenance.
- Le pack source `classics-fr-v1` contient trois textes du domaine public,
  leurs sources, une licence et les empreintes des fichiers.
- **Localisation 100 % de l'écran Paramètres** : plus aucun texte en dur
  dans Paramètres, Langue, Apparence, Notifications, Heures silencieuses,
  Diagnostic, Liens, Données et sauvegarde, Mises à jour et À propos
  (~70 nouvelles clés en fr/en/pt, dont les messages de sauvegarde et les
  libellés de sections de restauration, traduits à l'affichage).
- **Localisation 100 % de Personnalisation et des écrans Mémo** : thèmes
  (catégories, états, niveaux de déblocage), éditeur mémo (langues, jours
  de la semaine, fréquences, plages, import) et messages d'action du
  ViewModel (~80 nouvelles clés en fr/en/pt, avec pluriels corrects).
- **Localisation du partage entrant, de l'import de modules et de la
  bibliothèque locale** : feuille de partage, champs d'aperçu, collage de
  cartes et messages du moteur `BibliothequeLocale` (~70 nouvelles clés
  en fr/en/pt). Les détails des fiches sont reconstruits avec la langue
  courante au lieu de textes figés.
- **Vérification offline-first** : le seul réseau est les liens externes et
  la recherche de mise à jour, tous deux à la demande ; aucune fonction
  principale ne dépend d'une connexion, une seule permission demandée.

## Règle de développement (17 août)

Avant d'ajouter une fonctionnalité, vérifier si Android la fournit déjà ; si
oui, l'intégrer plutôt que la recréer. Toute nouveauté doit renforcer
l'apprentissage, la culture, la curiosité, la déconnexion ou la progression
personnelle — jamais une mécanique conçue pour augmenter le temps passé dans
l'application. Tout doit fonctionner hors connexion, l'interface doit suivre
la langue du système, et aucun contenu critique ne doit dépendre d'un compte,
d'un serveur ou d'Internet.

## Ce qui n'est pas encore fait

- Le code de production ne construit encore aucun `CulturePackStore` et ne
  propose pas l'import d'un `.culturepack` local. `CapsulesViewModel` charge le
  seul pack embarqué ; les tests utilisent le store générique.
- `core/extensions` sait inspecter, installer, lister et désinstaller un
  `.sankaipack`. L'intégration actuelle est un gestionnaire mono-extension
  codé pour le Jardin, pas encore un registre générique de contributions.
- Le snapshot Jardin ne contient qu'un niveau d'arrosoir, les identifiants de
  plantes découvertes et une date : il reste insuffisant pour une future
  migration physique autonome. En revanche, la désinstallation transitoire ne
  vide plus Room : elle retire/désactive le payload, annule les alarmes et
  conserve l'état détaillé Jardin/Île et les coffres en sommeil. La
  réinstallation retrouve donc cet état local.
- Le Jardin, l'Île, la Boutique, les Défis, les coffres et leurs données sont
  toujours présents dans le module `:app`. Les anciens gros composables de
  coffres/arène/Jardin ont été retirés de `HomeScreen`, sans retirer leurs
  moteurs ni leurs autres écrans.
- Un pack local v1 peut installer et désinstaller **ses assets et ses données**.
  Il ne peut ni charger ni retirer du bytecode Kotlin. Le moteur Jardin reste
  donc physiquement dans l'APK, tout comme les ressources Jardin/Île aujourd'hui
  embarquées dans `:app`, tant qu'ils ne sont pas déplacés vers une Dynamic
  Feature Play ou une application/APK séparée.

## Fonctions retirées de la première ligne ou déplacées

« Retirée » signifie ici retirée de l'entrée principale, pas supprimée du code
ni de la base.

| Fonction antérieure | Destination cible | Données |
|---|---|---|
| Coffres et récompenses d'arène sur l'accueil | Hors de l'accueil ; éventuellement rubrique Héritage/Extension | Conservées |
| Série quotidienne punitive | Remplacée visuellement par des rythmes 7/30/90 jours | Historique conservé |
| Boutique comme onglet | Mode Vie, section facultative | Achats et monnaies conservés |
| Défis comme onglet | Mode Vie, section facultative | Progression conservée |
| Jardin comme action principale | Gestionnaire d'extensions | Pack géré ; état détaillé Room conservé dormant après désinstallation |
| Focus gamifié | Outil personnel ; aucun coffre ni progrès de défi | Minutes et historique conservés ; XP/pièces de compatibilité en arrière-plan |
| Mémo mélangé à la progression de jeu | Outil essentiel du Mode Vie et de l'Académie | Profils, lignes et planifications conservés |

## Migration sans perte

Le recentrage ne devrait supprimer aucune donnée utilisateur sans consentement
explicite. La base Room v20
contient notamment l'utilisateur, les mémos, l'apprentissage, les objectifs,
les arènes, les coffres, les défis, le Jardin et l'Île. Le mode minimal et les
nouveaux choix de notification sont additifs dans DataStore.

**Protection transitoire actuelle :** la désinstallation du pack retire ou
désactive son payload, annule les alarmes Jardin et conserve snapshot et lignes
Room. Le Jardin/Île détaillé reste dormant jusqu'à réinstallation ou migration
physique. Le snapshot léger n'est pas une exportation autonome des onze tables,
mais aucune reconstruction n'est nécessaire tant que celles-ci restent dans
la base de l'application.

Règles obligatoires :

1. masquer une fonctionnalité ne supprime jamais ses lignes Room ;
2. désinstaller un pack enlève son archive et ses données éditoriales, mais
   conserve par défaut l'état créé par l'utilisateur ;
3. un effacement définitif exige une action séparée, explicite et chiffrée ;
4. toute évolution de schéma exporte le JSON Room et possède un test de
   migration depuis v20 ;
5. sauvegarde et restauration continuent à accepter les anciennes sections,
   même quand l'extension correspondante est absente ;
6. réinstaller la même extension rattache l'état conservé après validation de
   sa version de données.

## Ordre d'exécution contractuel 1–10

Cet ordre est commun aux autres documents ; ne pas sauter directement à la
suppression de fichiers.

1. **Geler la référence** : inventorier routes, tables, préférences, assets,
   tailles et tests, puis conserver une sauvegarde v20 réelle anonymisée.
2. **Valider la coque produit** : vérifier Capsules, Extensions, le mode minimal,
   les gardes Jardin/Île et tous les liens de la barre.
3. **Brancher Culture** : relier le pack embarqué et les packs locaux au store,
   à l'écran Capsules et à un historique local sans score.
4. **Généraliser le gestionnaire d'extensions** : séparer registre, état
   installé/activé/compatible et contributions, au-delà du seul Jardin.
5. **Mettre le Jardin derrière le registre** : aucune route, notification,
   récompense ou initialisation quand l'extension est désactivée.
6. **Découpler les contrats** : remplacer les appels directs depuis Flashcards,
   Mémo, Boutique, sauvegarde et Île par des ports optionnels dans le cœur.
7. **Sécuriser la migration des données** : export, reprise, désactivation,
   réinstallation et restauration sans perte ; ajouter les tests Room.
8. **Valider les deux configurations** : matrice automatisée et appareil avec
   pack/Jardin, sans pack/Jardin, mise à jour et archive corrompue.
9. **Nettoyer après preuve** : retirer uniquement les ressources et doublons
   démontrés inutiles ; actualiser droits, licences et documentation.
10. **Choisir l'extraction physique** : Dynamic Feature pour Google Play ou
    APK compagnon pour distribution indépendante ; mesurer à nouveau avant de
    promettre un gain de taille.

## Mesures de référence

Le rebuild final stable a réussi 694/694 tests via `testDebugUnitTest`.
`lintDebug` et `lintRelease` rapportent chacun 180 avertissements et zéro
erreur. La compilation release, `assembleRelease` et `bundleRelease` ont réussi,
y compris R8 et le lint vital, avec `--no-parallel`,
`-Pksp.incremental=false` et le compilateur Kotlin en mode in-process. Aucun
test instrumenté ni essai sur appareil n'a été exécuté.

Les artefacts issus de cette campagne ont été mesurés directement. Les autres
valeurs restent des **estimations issues d'un audit local** :

| Mesure | Valeur |
|---|---:|
| APK debug final | 26 711 899 octets, environ 25,47 Mio |
| SHA-256 APK debug | `37E37B1FA34B69D620AD08A82DBF0D83B22EC2B974AC508D2AC1B4CB3C7D83BA` |
| APK release final | 5 356 330 octets, environ 5,11 Mio |
| AAB release final | 9 709 094 octets, environ 9,26 Mio |
| Ressources réellement présentes dans l'application | environ 1,29 Mio |
| Dépôt de travail | environ 109,5 Mio |
| Doublons exacts récupérables dans le dépôt | environ 41,05 Mio |

Le principal gain immédiat est donc la clarté du produit et du dépôt. Une
extension de données ne peut pas retrancher les classes Kotlin déjà compilées.
Seule l'étape 10 peut réduire physiquement le code livré à un utilisateur qui
n'installe pas le Jardin. Il n'existe pas de baseline strictement comparable à
ces artefacts finaux : ne pas déduire une évolution de taille avant une mesure
reproductible à configuration identique.

## Critères de succès

- aucune destination de premier niveau ne dépend d'une récompense aléatoire ;
- le bouton « Terminer pour aujourd'hui » fonctionne sans sanction demain ;
- zéro notification spontanée quand le maître est coupé, et jamais plus que le
  budget choisi ;
- l'application reste utile sans Culture, sans Jardin, sans réseau et sans
  publicité ;
- désactiver puis réactiver une extension retrouve exactement l'état utilisateur ;
- tous les contenus distribués ont une source et un statut de droits vérifiable ;
- les métriques éventuelles restent locales, agrégées et désactivables.

## Risques à surveiller

- présenter le snapshot résumé comme une sauvegarde complète suffisante pour
  une future extraction physique ;
- réintroduire ultérieurement une purge des parcelles, cultures, bâtiments,
  inventaire, Mimos, stock ou coffres dans le flux « Désinstaller » ;
- confondre intégrité SHA-256 et authenticité d'un éditeur ;
- supprimer les tables Jardin pour gagner de la place et perdre une partie de
  sauvegarde utilisateur ;
- réintroduire coffres, badges ou compteurs dans Aujourd'hui par facilité ;
- promettre un APK plus petit avant une vraie modularisation de code ;
- publier des textes, images ou traductions sans chaîne de droits complète.

## Références

- Android, guide de modularisation : <https://developer.android.com/topic/modularization>
- Android, App Bundles : <https://developer.android.com/guide/app-bundle>
- Android, migration Room : <https://developer.android.com/training/data-storage/room/migrating-db-versions>
- INPI, droit d'auteur : <https://www.inpi.fr/proteger-vos-creations/le-droit-dauteur>
- INPI, recherche de marques et titres : <https://data.inpi.fr/>
- Mathur et al., *Dark Patterns at Scale*, arXiv:1907.07032 : <https://arxiv.org/abs/1907.07032>
