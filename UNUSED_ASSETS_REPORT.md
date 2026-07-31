# Rapport des assets inutilisés

Audit fait par recherche de chaque nom de fichier dans le code Kotlin, pas par
lecture du dossier. Un fichier présent dans `res/drawable` mais jamais nommé
dans une expression `R.drawable.…` est un fantôme : il pèse dans l'APK et
personne ne le voit.

---

## Supprimés

**Vingt et un fichiers**, en deux familles.

### Remplacés par les nouvelles illustrations (14)

Ils faisaient double emploi avec les PNG importés depuis `EXEMPLE`.

```
art_croissance_graine        art_humidite_sec
art_croissance_germe         art_humidite_legerement
art_croissance_pousse        art_humidite_humide
art_croissance_jeune         art_humidite_bien
art_croissance_mature        art_humidite_detrempe
art_croissance_recoltable    art_parcelle_preparee
art_ressource_piece          art_ressource_bois
```

### Jamais câblés (7)

Générés en v1.22 pour des fonctionnalités qui n'existent pas encore. Les garder
aurait été embarquer du poids pour des écrans qui ne sont pas écrits.

```
art_badge_graine     art_outil_pelle
art_badge_recolte    art_outil_gants
art_badge_maitre     art_outil_binette
art_lieu_serre
```

**Ils sont récupérables.** Leur définition a été retirée du catalogue de
`art/generer-assets.py`, mais l'historique Git la conserve : le jour où les
badges, la serre ou les outils supplémentaires arrivent, il suffit de rétablir
l'entrée et de relancer le script.

Le catalogue a été nettoyé en même temps que les fichiers — sans ça, la
prochaine exécution du générateur aurait recréé les fantômes.

---

## Restant en place : 35 vectoriels, tous référencés

| Famille | Nombre | Point d'entrée |
|---|---|---|
| `art_coffre_*` | 7 | `ArtJardin.coffre()` |
| `art_sol_*` | 6 | `ArtJardin.sol()`, `terrain()` |
| `art_meteo_*` | 5 | `ArtJardin.meteo()` |
| `art_phase_*` | 4 | `ArtJardin.phase()` |
| `art_outil_*` | 3 | `ArtJardin.outil()` |
| `art_ressource_*` | 4 | constantes de `ArtJardin` |
| `art_lieu_*` | 3 | dépôt, magasin, arbre |
| `art_parcelle_*` | 3 | brouillard, friche, terre libre |

Plus les dix PNG importés et les mipmaps de l'icône.

---

## Doublons

Aucun. Chaque élément a **une seule** source graphique, atteignable par un seul
chemin. C'est ce que garantit `ArtJardin` : aucun Composable ne référence un
`R.drawable` directement, tout passe par cette table.

---

## Assets encore manquants

Ils n'ont ni fichier généré ni fichier fourni, et sont donc rendus par des
emojis ou pas rendus du tout.

| Manquant | Conséquence aujourd'hui | Priorité |
|---|---|---|
| Dioramas des 8 arènes | emoji d'arène sur l'accueil | haute |
| Arbre Sankai, 8 états | emoji unique | moyenne |
| Mimos, 5 métiers | emoji par métier | moyenne |
| Icône monochrome Android 13+ | ancien tracé vectoriel, sans rapport avec le nouveau portrait | moyenne |
| Coffres, états ouvert/en attente | un seul dessin par rareté | basse |
| Particules de pluie, flaques, lucioles | dessinées par code, pas par image | aucune — le rendu procédural convient |
| Animaux, décorations, chemins | fonctionnalités non implémentées | aucune |

**L'icône monochrome mérite une note.** Android 13 et suivants l'utilisent pour
le thème sombre du lanceur. Elle doit être une silhouette d'une seule couleur,
ce qu'un portrait peint ne peut pas fournir. L'ancien tracé vectoriel est resté
en place : il fonctionne, mais il ne ressemble plus à l'icône principale.

---

## Comment refaire cet audit

```bash
cd SankaiLife/app/src/main
for f in res/drawable/*.xml res/drawable-nodpi/*.png; do
  n=$(basename "$f"); n="${n%.*}"
  grep -rq "R\.drawable\.$n\b" ../../../app/src/main/java || echo "ORPHELIN $n"
done
```

À relancer après chaque vague d'assets. R8 supprime déjà les ressources
inutilisées de l'APK release, mais il ne nettoie pas le dépôt — et un fichier
qui traîne finit par être réutilisé par erreur.
