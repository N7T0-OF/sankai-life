package com.sankailife.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sankailife.ui.theme.SankaiElevation
import com.sankailife.ui.theme.SankaiGlass
import com.sankailife.ui.theme.SankaiRadius
import com.sankailife.ui.theme.SankaiSpacing
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
    forme: RoundedCornerShape = RoundedCornerShape(SankaiRadius.Large),
    selectionne: Boolean = false,
    intensite: Float = SankaiGlass.NavigationIntensity,
    content: @Composable BoxScope.() -> Unit
) {
    val c = MaterialTheme.sankaiColors

    // Surface unie, tirée du thème.
    //
    // C'était un dégradé de deux teintes, censé donner l'épaisseur du verre.
    // Posé sur une palette dynamique, il se superposait à elle et le composant
    // affichait deux identités à la fois. L'épaisseur vient désormais du
    // liseré seul, qui suffit.
    val fond = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = intensite)

    // Liseré uni : plus marqué quand la surface est sélectionnée. Le blanc fixe
    // salissait les teintes chaudes ; il suit maintenant le thème.
    val liseré = MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = if (selectionne) SankaiGlass.SelectedHighlight * 3f else 0.5f
    )

    Box(
        modifier = modifier
            .clip(forme)
            .background(fond)
            .border(width = 1.dp, color = liseré, shape = forme),
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
    intensite = SankaiGlass.ChipIntensity,
    content = content
)

/** Carte de verre partagée, avec une cible tactile et un rôle corrects si elle est cliquable. */
@Composable
fun SankaiGlassCard(
    modifier: Modifier = Modifier,
    selectionne: Boolean = false,
    onClick: (() -> Unit)? = null,
    forme: RoundedCornerShape = RoundedCornerShape(SankaiRadius.Large),
    contentPadding: PaddingValues = PaddingValues(SankaiSpacing.Lg),
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = if (onClick != null) {
        Modifier
            .clip(forme)
            .clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    LiquidGlassSurface(
        modifier = modifier.then(interaction),
        forme = forme,
        selectionne = selectionne,
        intensite = SankaiGlass.CardIntensity
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            content = content
        )
    }
}

/** Bouton flottant en verre, toujours au moins aussi grand que la cible Android de 48 dp. */
@Composable
fun SankaiFloatingButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassSurface(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .shadow(SankaiElevation.Medium, CircleShape, clip = false)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        forme = RoundedCornerShape(SankaiRadius.Pill),
        intensite = SankaiGlass.FloatingIntensity
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    }
}
