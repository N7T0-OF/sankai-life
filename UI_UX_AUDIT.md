# Audit UI/UX — Sankai Life

Date : 1er août 2026
Référence : interface Compose actuelle, sans maquette externe et sans modifier
les changements de localisation déjà présents dans le worktree.

## Diagnostic global

L'identité sombre, violette et dorée est reconnaissable, mais elle était
appliquée par accumulation de valeurs locales : tailles, rayons, surfaces et
interactions variaient d'un écran à l'autre. L'accueil avait la bonne
hiérarchie fonctionnelle sur le papier, sans utiliser correctement la hauteur
disponible. La première phase crée donc les primitives et stabilise le hub
avant de toucher aux écrans secondaires.

## Cinq écrans les plus faibles avant refonte

1. **Accueil** — grand vide sur écrans hauts, risque de coupe sur écrans courts,
   carte Arène trop peu dominante, réglages sous 48 dp, plusieurs états morts
   dans le ViewModel.
2. **Personnalisation** — un thème peut être équipé en base mais n'influence pas
   réellement le thème global ; la promesse visuelle n'est donc pas tenue.
3. **Mode Vie** — liste non paresseuse et interrupteurs Focus/Objectifs sans
   action, ce qui donne l'apparence de réglages fonctionnels.
4. **Parcours Arènes** — bouton « revenir à l'arène actuelle » qui fermait une
   fiche au lieu de recentrer la liste ; rythme visuel uniforme malgré des
   écarts de niveaux différents.
5. **Boutique** — offre du jour également répétée dans la grille, illustrations
   très génériques et distinction insuffisante entre indisponible, coûteux et
   verrouillé.

## Revue des écrans

| Écran | Force | Faiblesse principale | Suite |
|---|---|---|---|
| Accueil | informations essentielles présentes | composition non responsive | phase 1 livrée |
| Jardin | identité la plus forte de l'app | densité HUD et coût des effets | moteur météo/qualité livré, test appareil requis |
| Mode Vie | fonctions utiles regroupées | faux interrupteurs, liste longue | phase 2 |
| Mémo / éditeur | parcours compréhensible | densité, actions secondaires petites | phase 2 |
| Flash cards | boucle pédagogique riche | verrou de réponse auparavant invisible | logique corrigée, polish phase 2 |
| Arènes | progression lisible | recentrage cassé et animations permanentes | recentrage livré, animation à réduire |
| Défis | récompenses explicites | peu d'états de chargement/erreur | phase 3 |
| Boutique | catégories simples | doublon promo, assets génériques | phase 3 |
| Profil | données nombreuses | hiérarchie et personnalisation déconnectées | phase 3 |
| Personnalisation | catalogue existant | thème équipé non appliqué | priorité haute phase 2 |
| Paramètres | couverture fonctionnelle large | sections très longues, réglages factices | phase 2 |
| Onboarding | séquence courte | validation accessibilité limitée | phase 3 |

## Accessibilité

Constats initiaux : nombreuses zones `clickable` brutes, plusieurs cibles de
36–38 dp, sémantique d'onglet absente, barre basse sans inset système et collecte
de flows non liée au cycle de vie sur la majorité des écrans.

Corrections phase 1 : réglages à 48 dp, navigation basse à 48 dp minimum,
`navigationBarsPadding`, rôle d'onglet et état sélectionné, descriptions
d'action sur l'accueil, `collectAsStateWithLifecycle` sur le hub.

À vérifier : TalkBack sur les coffres et la jauge d'Arène, fontScale 1,5–2,0,
contraste en thème clair, ordre de focus dans le Jardin et alternatives aux
animations infinies hors météo.

## Design system proposé

Les nouvelles primitives sont `SankaiSpacing`, `SankaiRadius`, la typographie
Material thémée, `SankaiGlassCard` et `SankaiFloatingButton`. La règle est :

- espacement issu de l'échelle commune ;
- rayon sémantique (`Small`, `Medium`, `Large`, `Pill`) ;
- carte vitrée unique pour les surfaces interactives premium ;
- cible tactile minimale de 48 dp ;
- couleur d'état réservée à un sens stable : succès, avertissement, erreur,
  récompense ;
- texte tronqué ou détail retiré en mode compact, jamais cible tactile réduite.

## Phase 1 — Accueil

Le hub est non défilant et piloté par `BoxWithConstraints`. Un mode compact
s'active sous 700 dp de hauteur, sous 350 dp de largeur ou avec un fontScale
élevé. La carte Arène occupe l'espace central pondéré, le bouton Jardin reste
à 48 dp minimum, la barre de coffres est fixe en bas du contenu et le raccourci
Paramètres fait 48 dp. Les anciens flows et composables Home réellement morts
ont été retirés.

Le contrôle statique est satisfaisant. La validation finale doit encore couvrir
un petit téléphone réel, un grand écran, TalkBack et une police à 200 %.

## Ordre recommandé après validation de l'accueil

1. appliquer réellement le thème équipé et nettoyer les faux interrupteurs ;
2. refondre Mode Vie + Mémo autour de listes paresseuses et états explicites ;
3. dédupliquer l'offre Boutique et distinguer manque de fonds / verrou / plein ;
4. harmoniser Profil, Défis et Paramètres ;
5. terminer l'accessibilité transversale et remplacer progressivement les
   emojis par les assets prioritaires.
