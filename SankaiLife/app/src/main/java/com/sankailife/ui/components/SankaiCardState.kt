package com.sankailife.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sankailife.ui.theme.AccentViolet
import com.sankailife.ui.theme.DangerRed
import com.sankailife.ui.theme.SuccessGreen
import com.sankailife.ui.theme.sankaiColors

/**
 * État visuel d'une carte, partagé par tous les écrans.
 *
 * Sans ce vocabulaire commun, chaque écran réinventait son propre « verrouillé »
 * ou son propre « disponible », et les mêmes notions finissaient par ne pas se
 * ressembler d'un écran à l'autre.
 */
enum class SankaiCardState {
    /** Carte informative ordinaire. */
    Default,
    /** Élément en cours : session lancée, arène actuelle, module actif. */
    Active,
    /** Objectif atteint, défi réclamé, arène franchie. */
    Completed,
    /** Pas encore accessible. */
    Locked,
    /** Quelque chose attend d'être récupéré. */
    RewardAvailable,
    /** Erreur ou action refusée. */
    Error
}

/** Jeu de couleurs résolu pour un état donné. */
@Immutable
data class SankaiCardStyle(
    val fond: Color,
    val bordure: Color,
    val epaisseurBordure: Dp,
    val texte: Color,
    val texteSecondaire: Color,
    val accent: Color
)

/**
 * Résout un état en couleurs concrètes.
 *
 * Le cas verrouillé change de teinte plutôt que de baisser l'opacité :
 * une carte simplement transparentifiée devient illisible sur fond sombre,
 * alors que l'utilisateur doit continuer à lire ce qui l'attend.
 */
@Composable
fun styleDeCarte(state: SankaiCardState): SankaiCardStyle {
    val c = MaterialTheme.sankaiColors
    return when (state) {
        SankaiCardState.Default -> SankaiCardStyle(
            fond = c.surface2, bordure = c.border, epaisseurBordure = 1.dp,
            texte = c.textPrimary, texteSecondaire = c.textSecondary, accent = c.accent
        )
        SankaiCardState.Active -> SankaiCardStyle(
            fond = c.accent.copy(alpha = 0.12f), bordure = c.accent, epaisseurBordure = 2.dp,
            texte = c.textPrimary, texteSecondaire = c.textSecondary, accent = c.accent
        )
        SankaiCardState.Completed -> SankaiCardStyle(
            fond = c.surface2, bordure = SuccessGreen.copy(alpha = 0.45f), epaisseurBordure = 1.dp,
            texte = c.textSecondary, texteSecondaire = c.textSecondary, accent = SuccessGreen
        )
        SankaiCardState.Locked -> SankaiCardStyle(
            fond = c.surface1, bordure = c.border, epaisseurBordure = 1.dp,
            texte = c.textSecondary, texteSecondaire = c.textDisabled, accent = c.textDisabled
        )
        SankaiCardState.RewardAvailable -> SankaiCardStyle(
            fond = AccentViolet.copy(alpha = 0.12f), bordure = AccentViolet, epaisseurBordure = 1.5.dp,
            texte = c.textPrimary, texteSecondaire = c.textSecondary, accent = AccentViolet
        )
        SankaiCardState.Error -> SankaiCardStyle(
            fond = DangerRed.copy(alpha = 0.10f), bordure = DangerRed.copy(alpha = 0.6f),
            epaisseurBordure = 1.dp,
            texte = c.textPrimary, texteSecondaire = c.textSecondary, accent = DangerRed
        )
    }
}
