# Audit Codex — Sankai Life

Date : 1er août 2026
Périmètre : application Android `SankaiLife`, données locales, économie,
apprentissage, Jardin, notifications, sauvegardes, livraison et UI Compose.

## Verdict exécutif

La base est saine pour une application hors ligne : Room est la source de
vérité, les moteurs métier sont généralement séparés de Compose et le projet
possède déjà une couverture unitaire substantielle. Les risques principaux ne
venaient pas de l'architecture générale, mais de plusieurs opérations en deux
temps qui pouvaient perdre une récompense ou un achat, de promesses de
sauvegarde plus larges que le contenu réel, et de sous-systèmes configurés sans
respecter complètement leur état (slots mémo, consentement pub, alarmes de
coffres, Jardin vivant).

La première vague corrige les défauts critiques et s'arrête volontairement
avant une refonte globale de tous les écrans.

## Sources examinées

- `README.md`, `LISEZ-MOI.md`, `IMPLEMENTATION_STATUS.md` ;
- `ASSET_MIGRATION_REPORT.md`, `UNUSED_ASSETS_REPORT.md` ;
- `JARDIN-ANALYSE.md`, `JARDIN-REFONTE-2.md`, `REFONTE-ANALYSE.md` ;
- modèle Room, DAO, dépôts, moteurs métier, notifications et préférences ;
- tous les écrans Compose et la navigation ;
- configuration Gradle et workflow GitHub Actions ;
- catalogue et appels des assets de coffres, outils, plantes et terrains.

## Top 10 priorisé

| Priorité | Problème | Risque | État après cette vague |
|---|---|---|---|
| P0 | Récompenses et soldes modifiés en plusieurs écritures | double crédit ou récompense perdue après interruption | corrigé pour coffres, défis, arènes, achats de slots et opérations courantes ; les livraisons Boutique multi-dépôts restent à unifier |
| P0 | Archives ZIP lues sans plafond pendant la décompression | épuisement mémoire par ZIP bomb | corrigé avec limites globales, par entrée et en nombre d'entrées, plus tests |
| P0 | Restauration annonçant une sauvegarde de sécurité sans en créer | perte irréversible du profil courant | corrigé : destination obligatoire et échec bloquant avant toute mutation |
| P0 | AdMob initialisé avant décision de confidentialité | collecte/chargement publicitaire prématuré | corrigé par UMP et garde `canRequestAds()` ; configuration du message à vérifier dans la console AdMob |
| P0 | Publication possible sans tests ni vraie signature | artefact public non testé ou signé avec une clé debug | corrigé : publication dépend du job de tests, secrets obligatoires, aucun repli debug, lint release bloquant |
| P1 | Slots mémo achetés mais non imposés | monnaie dépensée sans effet réel | corrigé avec activation transactionnelle, message de limite et replanification des alarmes |
| P1 | Horaires aléatoires retirés à chaque replanification | notifications instables et potentiellement répétées | corrigé par graine stable profil/réglages/jour, avec tests |
| P1 | Jardin figé pendant qu'il reste ouvert | croissance, humidité et chantiers visuellement faux | corrigé par synchronisation idempotente liée au cycle de vie et tick partagé |
| P1 | Boucle flash cards non protégée | double réponse, XP répétée, carte « à revoir » absente de la session | corrigé : verrou d'écriture et une reprise maximale par carte |
| P1 | Modèle Room et sauvegarde peu vérifiés dans le temps | régression de migration ou restauration partielle | couverture de sauvegarde renforcée dans cette vague ; restent `exportSchema`, tests de migrations et index à traiter |

## Économie et intégrité

Les crédits et débits simples utilisent désormais des mises à jour SQL
atomiques. Les statistiques journalières sont créées avant incrément, les
remboursements ne gonflent plus les gains, et l'achat d'un slot recalcule le
palier dans la transaction. L'ouverture d'un coffre génère puis livre pièces,
gemmes, XP, bonus de niveau, statistiques et progression du défi dans la même
transaction. Les défis et arènes refusent la réclamation si un coffre promis
ne peut pas être livré.

Reste à faire : créer une seule transaction d'achat pour les articles Boutique
qui touchent simultanément `user`, `chest` ou les tables du Jardin. Le mécanisme
actuel rembourse correctement les échecs applicatifs, mais une interruption du
processus entre débit et livraison reste un cas limite.

## Sauvegardes et import de modules

Le lecteur commun `BoundedZipReader` compte aussi les entrées refusées afin
qu'un chemin dangereux ne contourne pas le plafond. L'installation d'un module
est transactionnelle et conserve l'ordre des cartes. Une restauration ne peut
plus démarrer si la sauvegarde de sécurité n'a pas été écrite.

Le format de sauvegarde `v2` contient désormais les quatre sections réellement
prises en charge : profil et progression (tous les champs utilisateur,
journées, statistiques, récompenses d'arène et objectifs), mémos et cartes,
Jardin complet, puis coffres et défis. Les réglages DataStore ne sont pas
annoncés puisqu'ils ne sont pas encore exportés. Profil, Jardin et coffres sont
restaurés comme des instantanés transactionnels ; les mémos sont ajoutés comme
copies inactives avec de nouveaux identifiants. Les alarmes de coffres sont
replanifiées après validation de la transaction. Le lecteur `v2` accepte les
sauvegardes `v1`, tandis qu'un ancien lecteur refuse proprement le nouveau
format au lieu d'en restaurer seulement une fraction.

Le format de module reste volontairement limité à des données. Il ne persiste
cependant pas encore l'identité et la version du module installé : une future
mise à jour ne peut donc pas être distinguée proprement d'un homonyme. Ajouter
une table `installed_module` est la suite recommandée.

## Apprentissage

Points solides : répétition espacée, exercices variés, état de révision stocké,
récompenses reliées au Jardin, limites d'import. Cette vague stabilise les
horaires aléatoires, bloque les doubles réponses et réintroduit une fois les
cartes ratées.

À poursuivre : afficher le taux de maîtrise déjà calculable, tester les
interruptions entre mise à jour de carte et récompense, et définir une vraie
stratégie de mise à jour/désinstallation des modules externes.

## Jardin et performances

Le rendu est maintenant séparé en état mécanique et état visuel. Une horloge
partagée actualise le Jardin visible ; les cultures sont indexées par parcelle.
La caméra conserve une origine monde stable, recentre réellement et centre les
petits terrains. Le moteur météo fournit un vent commun, un éclairage couvert,
des ombres de nuages procédurales et trois niveaux de qualité. Le mode économie
batterie force la qualité faible.

Reste à mesurer sur appareil : temps de frame sur grand terrain, mémoire des
bitmaps procéduraux et bénéfice d'un culling explicite des parcelles hors
écran. Les effets sont suspendus hors cycle de vie actif, mais aucun benchmark
Macrobenchmark n'existe encore.

## Coffres, outils et assets

- sept vectoriels de coffres sont présents et passent par `ArtJardin.coffre()` ;
- les raretés diffèrent surtout par couleur et partagent une silhouette très
  proche ; l'état ouvert n'est pas utilisé pour les types connus ;
- trois outils réels sont câblés : arrosoir, panier et pioche ;
- les anciens outils non fonctionnels ont été retirés du dépôt ;
- le manque graphique le plus visible reste le diorama des huit arènes ;
- pluie, nuages et particules peuvent rester procéduraux : un bitmap externe
  n'améliorerait pas leur continuité ni leur coût.

Priorité asset recommandée : dioramas d'arènes, états distincts des coffres,
Mimos, arbre Sankai multi-états, puis cohérence de l'icône monochrome.

## Notifications et hors-ligne

Les mémos et révisions respectaient déjà le réglage global et les heures
silencieuses. Les coffres le respectent désormais et leurs alarmes sont
recréées au lancement, au reboot, après mise à jour et changement d'heure. Le
calcul aléatoire quotidien est reproductible.

L'application reste fonctionnelle hors ligne. Les seuls chemins réseau sont
les pubs facultatives, les liens externes et la mise à jour GitHub. Il reste à
câbler la destination de navigation portée par une notification mémo ; les
extras existent mais `MainActivity` ne les consomme pas encore.

## Livraison et qualité

Le workflow de tag ne publie plus si la signature ou les identifiants AdMob de
production manquent. Il attend le job de tests. La release locale peut rester
non signée lorsque les secrets sont absents, mais n'utilise jamais la clé debug
comme substitut. Pour cette vérification finale, les secrets étaient
disponibles : `app-release.apk` est signé par le certificat Sankai Life et sa
signature APK v2 a été vérifiée avec `apksigner`.

## Vérification finale

### Vérification automatisée réussie

La chaîne locale du 1er août 2026 a terminé sans échec : `test`, `lintDebug`,
`lintRelease`, `assembleDebug`, `assembleRelease` et `bundleRelease`.

| Vérification | Résultat exact |
|---|---|
| Tests unitaires debug | 239 tests dans 24 suites ; 0 échec, 0 erreur, 0 ignoré |
| Tests unitaires release | 239 tests dans 24 suites ; 0 échec, 0 erreur, 0 ignoré |
| Lint debug | 0 erreur, 77 avertissements, 3 informations |
| Lint release | 0 erreur, 77 avertissements, 3 informations |
| Assemblage debug | réussi ; APK `1.38.0-debug` (`versionCode` 40) |
| Assemblage release | réussi ; APK `1.38.0` (`versionCode` 40), signature APK v2 vérifiée |
| Bundle release | réussi ; AAB `1.38.0` (`versionCode` 40), signature JAR vérifiée |

Les 77 avertissements lint sont identiques sur les deux variantes : 44
ressources inutilisées, 15 dépendances plus récentes disponibles, 5 formes
d'icône launcher, 4 paramètres `Modifier`, 4 tests de SDK obsolètes, 2 candidats
au pluriel et trois avertissements isolés. Les trois informations concernent
`AutoboxingStateCreation`. Ils ne masquent aucune erreur lint, mais constituent
une dette mesurée et non une validation « zéro avertissement ».

### QA sur appareil encore requise

Cette réussite automatise compilation, tests JVM, lint et empaquetage ; elle ne
remplace pas une exécution Android réelle. Restent à vérifier sur téléphone ou
émulateur : installation et démarrage des deux APK, restauration complète avec
sauvegarde de sécurité, alarmes après reboot/Doze/changement d'heure, parcours
de consentement publicitaire, rendu responsive et accessibilité, ainsi que
fluidité et mémoire du Jardin avec nuages sur un grand terrain. Aucun test
instrumenté, test de migration Room, Macrobenchmark ni test de capture Compose
n'a été exécuté dans cette chaîne.
