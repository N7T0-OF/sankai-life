# Passation UI Codex

Date : 1er août 2026

## Livré et vérifié

- tokens : `ui/theme/DesignTokens.kt` ;
- composants : `SankaiGlassCard`, `SankaiFloatingButton` et consolidation de
  `LiquidGlass` ;
- accueil : responsive, compact, non défilant, Arène centrale, coffres fixes,
  réglages 48 dp ;
- navigation : inset système, cibles 48 dp, rôle et état d'onglet ;
- Arènes : recentrage réel vers la position courante ;
- nettoyage des états/composables Home prouvés inutilisés ;
- validation Gradle réussie : tests, `lintDebug`, `lintRelease`, `assembleDebug`
  et `assembleRelease`.

## Validation sur appareil restante

- vérifier l'accueil sur un petit écran, notamment en 320×568 ;
- vérifier une police à 200 % et le parcours TalkBack principal ;
- vérifier que le dock et la barre basse restent dégagés avec la navigation par
  gestes.

Ne pas lancer la refonte générale de Mode Vie, Boutique ou Profil avant cette
validation visuelle. Les fichiers de localisation appartiennent au travail
parallèle et ne doivent pas être écrasés.

## Prochain lot conseillé

1. terminer la validation sur appareil ci-dessus ;
2. appliquer `equippedThemeId` dans `SankaiTheme` ;
3. remplacer les interrupteurs inertes de Mode Vie par des actions honnêtes ;
4. migrer écran par écran vers les tokens sans réécriture massive.
