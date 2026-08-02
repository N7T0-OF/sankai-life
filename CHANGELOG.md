# Changelog Sankai Life

## 1.51.0 - 2 aout 2026

- **L'ancienne Boutique est de retour** : onglets Coffres / Jardin /
  Progression et grille de cartes. La recherche et les illustrations PNG de la
  version precedente sont conservees.
- **Sankai Life reprend les couleurs de ton telephone** (Android 12 et plus).
  Si ton theme est jaune, boutons et accents s'y accordent. L'eau reste bleue,
  les erreurs rouges, les recompenses dorees, et le Jardin garde ses couleurs
  naturelles. Reglable dans Reglages -> Apparence.

## 1.50.0 - 2 aout 2026

- **Deux nouveaux batiments.** Le Port (3x2) doit toucher l'eau et ajoute 25 %
  au prix de vente, en plus du bonus de la Boutique. L'Atelier Mimo (2x2) fait
  travailler tes Mimos deux fois plus longtemps pendant ton absence.
- Le panneau de vente dit lequel des deux te manque encore.

## 1.49.0 - 2 aout 2026

- **Toucher un batiment ouvre sa fiche**, plus celle de l'herbe restee dessous.
  La Boutique propose de vendre les recoltes, le Depot montre sa capacite.
- **Une parcelle achetee sous un batiment est reprise et remboursee** au lieu
  de rester la, dessinee sous la construction.
- On ne peut plus batir sur une case deja batie.

## 1.48.0 - 2 aout 2026

- Une case boisee s'appelle desormais « Arbre » et non « Bois » : depuis que
  les arbres sont dessines, la fiche nomme ce qu'on voit plutot que le type de
  terrain.

## 1.47.0 - 2 aout 2026

- **Les arbres sont visibles sur l'ile.** Le bois n'est plus une case verte
  plus sombre : les arbres sont dessines, de trois tailles differentes selon
  qu'ils sont isoles ou en bosquet, avec une legere variation de taille pour
  qu'aucun deux ne soient identiques.

## 1.46.0 - 1er aout 2026

Correctifs de l'Ile, issus d'une utilisation reelle sur telephone.

- **La case touchee est enfin la bonne.** Toucher l'herbe ouvrait la fiche
  « Ocean », le sable donnait « Rocher » : le calcul utilisait l'ancienne taille
  de case apres chaque zoom. Plus on zoomait, plus la case designee etait
  fausse.
- **Le zoom fonctionne.** Deux doigts poses continuaient de deplacer la camera
  et le pincement n'etait presque jamais pris en compte. C'est desormais le
  nombre de doigts qui decide : deux doigts zooment et rien d'autre, un doigt
  deplace, un doigt immobile selectionne.
- **La barre du haut ne passe plus derriere l'encoche** ni derriere la camera.
  Les marges viennent du systeme et s'adaptent a chaque telephone.
- **L'ocean ne se coupe plus** quand on dezoome.
- **L'ile est deux fois plus grande** : 64 cases de cote au lieu de 32, avec des
  silhouettes nettement plus variees.

## 1.45.0 - 1er aout 2026

**Nouveau : l'Ile generative, en construction.**

Reglages -> En construction -> Voir mon ile.

C'est un chantier, pas encore un mode de jeu, et c'est pour cela qu'elle est
rangee la. Le Jardin reste le mode de jeu et n'a pas change.

- chaque profil recoit une ile unique, tiree d'une graine : cote irreguliere,
  plage, rivieres, bois, rochers, ponton et ferme de depart ;
- au premier lancement, trois iles sont proposees au choix, avec apercu et
  chiffres. Le choix est definitif, mais on peut demander trois autres
  propositions avant de trancher ;
- on achete ses parcelles ou l'on veut, dans la limite de son niveau ;
- cycle complet : degager, preparer, semer, arroser, recolter ;
- les recoltes vont au stock et se vendent. Une Boutique ameliore le prix de
  35 %, un Depot triple la capacite. Vendre reste possible sans eux ;
- **arroser coute de l'eau, et l'eau se gagne en revisant.** C'est la meme
  reserve que le Jardin : c'est l'eau du joueur, pas celle d'un lieu ;
- les Mimos deja embauches travaillent aussi sur l'ile pendant l'absence, et
  consomment l'eau comme le joueur ;
- mini-carte pour se reperer et se deplacer.

Ce qui manque encore, et se voit : l'eau, le sable, le bois et le rocher sont
des aplats de couleur faute d'illustrations. Les chemins, les ponts et le
catalogue de la Boutique n'existent pas.

**Corrections du Jardin**

- le zoom ne tremble plus et la vue ne saute plus en fin de geste ;
- les ombres de nuages sont visibles, et l'orage assombrit enfin plus qu'un
  ciel simplement nuageux ;
- le nouvel Arbre Sankai remplace l'ancien dessin genere.

## 1.44.0 - 1er aout 2026

- **Refonte UI/UX** du Hub, des Arenes, du Jardin, de la Boutique et des Memos
  (lot realise par Codex).
- **Zoom du Jardin corrige.** Le detecteur de deplacement etait recree a chaque
  frame du pincement : c'est de la que venait l'essentiel du tremblement. La vue
  ne saute plus en fin de geste, un tremblement de doigt ne declenche plus de
  zoom, et le point situe entre les doigts reste sous les doigts.
- **Ombres de nuages visibles.** L'orage assombrissait moins qu'un ciel
  nuageux : l'ordre etait inverse et les trois valeurs trop basses.
- **Nouvel Arbre Sankai.** L'illustration remplace pour de bon l'ancien dessin
  genere, qui a ete supprime. Son tronc est pose au sol au pixel pres.

## 1.43.0 — 1er août 2026

- **Écouter les cartes.** Après avoir répondu, un bouton « Écouter » prononce
  la phrase dans sa langue. La prononciation est la moitié d'une langue, et un
  mot portugais lu avec les règles du français ne s'entend dans aucune
  conversation réelle.
- L'écoute n'apparaît qu'après la réponse : sur un exercice à trous, faire
  prononcer la phrase donnerait la solution.
- Un module déclare maintenant sa langue. Les modules importés la reprennent de
  leur manifeste ; pour un module créé à la main, un sélecteur a été ajouté à
  l'éditeur. Sans langue déclarée, aucune écoute n'est proposée — faire lire du
  portugais par une voix française apprendrait une prononciation fausse à
  quelqu'un qui ne peut pas s'en apercevoir.
- Le bouton reste absent si le téléphone n'a pas de voix pour cette langue.
  L'application n'embarque aucune voix : elle emprunte celle du système.
- La langue est conservée dans les sauvegardes de profil.

## 1.42.0 — 1er août 2026

- Les modules d'apprentissage sont enfin installables depuis un téléphone :
  deux paquets prêts à l'emploi (Portugais débutant, Raccourcis Blender) sont
  publiés en `.zip`, et un bouton « Voir les modules disponibles » y mène
  directement. Jusqu'ici l'application renvoyait vers « le dépôt GitHub » sans
  donner d'adresse, et les exemples n'existaient que sous forme de dossiers,
  que GitHub ne sait pas télécharger.
- Le bouton d'import apparaît même quand on n'a encore aucun module — c'est
  précisément le moment où il sert.
- Un module installé s'annonce en vert, plus en rouge. La réussite ne
  s'affichait pas autrement qu'une panne.

## 1.41.0 — 1er août 2026

- L'écran Mémo Intelligent devient utilisable pour réviser : chaque module
  annonce son nombre de phrases, combien sont dues, et porte un bouton
  « Réviser ». Il fallait jusqu'ici repasser par Mode Vie.
- Le bouton « + » ouvre enfin l'éditeur du module qu'il vient de créer, au lieu
  d'ajouter une ligne vide dans la liste sans rien dire.
- Nouvelle section « Mémorisation » dans les Statistiques : phrases
  enregistrées, cartes en dernière boîte, réponses données, taux de bonnes
  réponses. Le taux reste masqué tant qu'il ne repose pas sur assez de réponses
  pour vouloir dire quelque chose.
- Les flèches de retour s'inversent d'elles-mêmes dans les langues écrites de
  droite à gauche.

## 1.40.0 — 1er août 2026

- Nouvelle session « Mes erreurs » : rassemble, tous modules confondus, les
  cartes que tu rates le plus souvent. Elle apparaît dans Mémo Intelligent
  seulement quand des cartes résistent vraiment.
- Une carte n'entre dans cette liste qu'après au moins trois révisions : rater
  une carte qu'on vient de découvrir est normal, pas une difficulté.
- Les propositions à choix multiple de cette session sont tirées des mêmes
  cartes, pour qu'aucune réponse ne se repère parce qu'elle vient d'un autre
  sujet.

## 1.39.0 — 1er août 2026

- Trois langues : français, anglais, portugais. L'application suit la langue du
  téléphone et se change depuis les Réglages (Android 13 et plus).
- Publication débloquée : les identifiants publicitaires de production sont de
  nouveau pris en compte sans configuration supplémentaire.

## 1.38.0 — 1er août 2026

- Nouvel accueil plus lisible : Arène au centre, coffres toujours accessibles
  et mise en page adaptée aux petits écrans.
- Le Jardin continue réellement d'évoluer lorsqu'il reste ouvert.
- Météo nuageuse dynamique : ombres lentes, vent commun avec la pluie et rendu
  allégé en mode économie batterie.
- Les slots de mémos limitent maintenant correctement les profils actifs.
- Les horaires aléatoires des mémos restent stables pendant la journée.
- Les cartes ratées reviennent dans la session sans permettre une boucle
  infinie de récompenses.
- Sauvegardes et imports renforcés contre les fichiers trop volumineux ; une
  copie de sécurité est exigée avant restauration.
- Récompenses, coffres et achats mieux protégés contre les doubles appuis et
  interruptions.
- Navigation basse et boutons principaux plus accessibles.
- Les publicités facultatives ne démarrent qu'après le choix de
  confidentialité approprié.
