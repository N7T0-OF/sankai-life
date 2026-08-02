# État du système de thèmes

## Deux thèmes, tous deux gratuits

| Thème | Palette | Disponibilité |
|---|---|---|
| **Sankai classique** | Bleu nuit, accents violet et or | Toujours |
| **Couleurs du téléphone** | Palette Material You du système | Android 12 et plus |

Les deux sont visibles dès l'ouverture des Réglages. Celui qui n'est pas
disponible reste affiché, désactivé, **avec sa raison** : le masquer ferait
croire qu'il n'existe pas, et personne ne saurait qu'une mise à jour d'Android
l'ouvrirait.

## Quatre modes d'affichage, indépendants du thème

`Sombre` · `AMOLED` · `Clair` · `Auto`

AMOLED est un mode à part et non une nuance de sombre : il met le fond au noir
**exact**, seul à éteindre réellement les pixels d'une dalle OLED. Les surfaces
gardent leur teinte, sinon les cartes disparaissent dans le fond.

Les deux réglages se combinent librement : *Sankai classique + AMOLED* et
*Couleurs du téléphone + clair* sont l'un et l'autre valides.

## Migration des joueurs existants

Le réglage précédent était un simple interrupteur `couleursSysteme`. Tant que le
joueur n'a pas choisi explicitement une palette, **c'est cet interrupteur qui
fait foi**. Personne ne voit son apparence changer sous ses yeux à la mise à
jour ; écraser une préférence parce qu'on a changé sa forme serait le pire des
accueils.

Les deux réglages restent synchronisés : d'autres endroits lisent encore
l'ancien booléen, et deux sources de vérité finiraient par diverger.

## Ce qui reste stable quelle que soit la palette

L'eau bleue, les erreurs rouges, les récompenses dorées, les verrous gris — et
tout le Jardin, qui garde ses couleurs naturelles. Une palette jaune ne doit pas
transformer l'eau en jaune.

## Défauts corrigés, et ce qu'ils avaient en commun

| Version | Défaut | Cause |
|---|---|---|
| 1.54.0 | Dégradés fixes par-dessus la palette | 15 dégradés codés en dur |
| 1.55.0 | Texte de bouton illisible en clair | `onPrimary` blanc sur orange, 2,39:1 |
| 1.56.0 | Surfaces de verre violettes | rôles `surfaceContainer*` non déclarés |
| 1.57.0 | Fond resté bleu en dynamique | `background` et `surface1` non dérivés |

Les quatre partagent la même origine : **ils ne se voient que dans une
configuration que le développement ne traverse jamais** — palette de repli,
Android ancien, couleurs dynamiques désactivées. D'où les tests de contraste et
de teinte, qui vérifient ce que l'œil ne regarde pas.
