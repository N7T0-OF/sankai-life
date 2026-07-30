package com.sankailife.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sankailife.ui.theme.sankaiColors

/**
 * Surface « verre liquide ».
 *
 * Note technique honnête : Compose ne sait pas flouter ce qui se trouve
 * *derrière* un composant — `Modifier.blur` ne floute que son propre contenu.
 * Un vrai flou d'arrière-plan demanderait une bibliothèque dédiée et coûterait
 * cher en GPU sur les téléphones d'entrée de gamme.
 *
 * L'effet est donc obtenu par superposition : fond translucide, dégradé
 * interne, liseré clair en haut et bordure fine. C'est ce que livrent la
 * plupart des applications Android qui annoncent ce style.
 *
 * La lisibilité prime sur l'effet : l'opacité du fond reste assez haute pour
 * que le texte tienne sur n'importe quel arrière-plan.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    forme: RoundedCornerShape = RoundedCornerShape(24.dp),
    selectionne: Boolean = false,
    intensite: Float = 0.94f,
    content: @Composable BoxScope.() -> Unit
) {
    val c = MaterialTheme.sankaiColors

    // Deux teintes proches : le dégradé donne l'épaisseur, sans quoi la
    // surface paraît plate et l'effet de verre disparaît.
    val fond = Brush.verticalGradient(
        listOf(
            c.surface2.copy(alpha = intensite),
            c.surface1.copy(alpha = (intensite + 0.04f).coerceAtMost(1f))
        )
    )

    // Liseré : plus clair en haut, presque nul en bas. C'est lui qui simule
    // la lumière rasante sur une tranche de verre.
    val liseré = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (selectionne) 0.22f else 0.12f),
            Color.White.copy(alpha = 0.02f)
        )
    )

    Box(
        modifier = modifier
            .clip(forme)
            .background(fond)
            .border(width = 1.dp, brush = liseré, shape = forme),
        content = content
    )
}

/** Variante compacte pour les puces de ressources et les badges. */
@Composable
fun LiquidGlassChip(
    modifier: Modifier = Modifier,
    rayon: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) = LiquidGlassSurface(
    modifier = modifier,
    forme = RoundedCornerShape(rayon),
    intensite = 0.88f,
    content = content
)
