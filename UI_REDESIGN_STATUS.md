# Statut de la refonte UI

Dernière mise à jour : 1er août 2026

| Phase | État | Contenu |
|---|---|---|
| 0 — audit | terminé | inventaire des écrans, top 5, accessibilité, design system |
| 1 — Accueil | terminé — compilé et linté | hub responsive non défilant, Arène centrale, dock coffres, réglages 48 dp |
| 1b — navigation | terminé — compilé et linté | verre unifié, insets système, cibles 48 dp, sémantique d'onglet |
| 1c — Arènes | terminé — compilé et linté | recentrage vers l'arène actuelle |
| 2 — Vie / Mémo / Personnalisation | non démarré | attendre la validation sur appareil de la phase 1 |
| 3 — Boutique / Profil / Défis / Paramètres | non démarré | après phase 2 |
| 4 — accessibilité et assets | partiel | validation appareil et remplacement des assets manquants |

## Critères de sortie phase 1

- aucun scroll sur l'accueil ;
- aucun chevauchement sur format compact ;
- Arène clairement dominante ;
- coffres toujours accessibles ;
- Paramètres et navigation à 48 dp minimum ;
- navigation basse au-dessus de la barre système ;
- ouverture coffre, Arènes, Jardin et Paramètres fonctionnelles ;
- tests et builds debug/release verts.

La chaîne Gradle finale est verte : tests, `lintDebug`, `lintRelease`,
`assembleDebug` et `assembleRelease`. La Phase 1 est donc terminée côté code.

Restent à confirmer sur appareil : l'absence de chevauchement sur petit écran,
le rendu avec une police fortement agrandie et le dégagement de la navigation
basse en mode navigation par gestes.
