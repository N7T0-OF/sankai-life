# Notices tierces — Sankai Life

Ce fichier recense les travaux tiers étudiés ou adaptés dans Sankai Life.

---

## HappyIslandDesigner

| | |
|---|---|
| **Auteur** | eugeneration (Eugene) |
| **Licence** | MIT — `Copyright (c) 2020 Eugene` |
| **Source** | https://github.com/eugeneration/HappyIslandDesigner |
| **Copie étudiée** | `G:\2_Logiciel\CLAUDE CODE\EXEMPLE\HappyIslandDesigner-master` |
| **Usage** | Référence d'architecture et d'algorithmes pour l'éditeur d'île |

### État actuel de l'usage

**Aucun code n'a été copié, traduit ou adapté à ce jour.** Le projet a été lu
pour en tirer des principes de conception ; les mécanismes retenus
(historique par commandes, versionnement des sauvegardes, autosauvegarde par
seuil d'actions et d'inactivité) sont des motifs de programmation courants,
réimplémentés en Kotlin sans dérivation du source.

Cette entrée existe malgré tout : elle date l'étude et fixe les obligations
avant qu'une ligne soit reprise. Si du code venait à être réellement adapté,
il faudra, **avant la reprise** :

1. copier le texte intégral de la licence MIT dans `licenses/HappyIslandDesigner-MIT.txt` ;
2. conserver la ligne `Copyright (c) 2020 Eugene` dans les fichiers dérivés ;
3. citer le fichier d'origine en en-tête du fichier Kotlin concerné ;
4. décrire les modifications apportées ;
5. mettre à jour le tableau ci-dessous.

### Fichiers dérivés

| Fichier Sankai | Fichier d'origine | Nature de l'adaptation |
|---|---|---|
| *(aucun)* | — | — |

### Ce qui ne sera jamais repris

Le projet d'origine est un éditeur de cartes pour *Animal Crossing: New
Horizons*. Sa licence MIT couvre **son propre code**, pas les éléments
appartenant à Nintendo qu'il manipule. Sont donc exclus sans exception :

- les sprites, icônes et rendus de bâtiments issus du jeu ;
- les noms de bâtiments, de personnages et de lieux d'Animal Crossing ;
- les logos Nintendo ;
- les modèles de cartes officiels ;
- les captures d'écran du jeu.

Sankai Life ne doit à aucun moment se présenter comme lié à Nintendo ni à
Animal Crossing.
