package com.sankailife.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.ui.theme.sankaiColors

/**
 * Carte à état, base commune de tous les écrans.
 *
 * Passer par [state] plutôt que par des couleurs choisies sur place garantit
 * qu'un « verrouillé » a la même tête dans la boutique, les arènes et les
 * thèmes — ce qui n'était pas le cas quand chaque écran décidait seul.
 */
@Composable
fun SankaiStateCard(
    modifier: Modifier = Modifier,
    state: SankaiCardState = SankaiCardState.Default,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val style = styleDeCarte(state)

    // Une récompense en attente respire légèrement. Réservé à cet état :
    // une animation permanente sur chaque carte fatiguerait l'œil et la
    // batterie sans plus rien signaler.
    val transition = rememberInfiniteTransition(label = "carte")
    val pulse by transition.animateFloat(
        initialValue = 0.75f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse"
    )
    val opacite = if (state == SankaiCardState.RewardAvailable) pulse else 1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(style.fond)
            .border(style.epaisseurBordure, style.bordure.copy(alpha = opacite), RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp),
        content = content
    )
}

/**
 * État vide.
 *
 * Un écran blanc laisse croire à une panne. Un état vide explique ce qui
 * manque et propose la première action à faire.
 */
@Composable
fun EmptyStateCard(
    emoji: String,
    titre: String,
    message: String,
    libelleAction: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.sankaiColors
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(titre, color = c.textPrimary, fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, color = c.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (libelleAction != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            SankaiButton(libelleAction, onClick = onAction)
        }
    }
}

/**
 * État d'erreur.
 *
 * [message] doit dire ce qui s'est passé en français, pas afficher un code.
 * Une erreur sans action possible laisse l'utilisateur bloqué : le bouton
 * de reprise est donc la règle, pas l'exception.
 */
@Composable
fun ErrorStateCard(
    message: String,
    libelleAction: String = "Réessayer",
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    SankaiStateCard(modifier = modifier, state = SankaiCardState.Error) {
        val style = styleDeCarte(SankaiCardState.Error)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚠️", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Text(message, color = style.texte, fontSize = 13.sp, modifier = Modifier.weight(1f))
        }
        if (onAction != null) {
            Spacer(Modifier.height(12.dp))
            SankaiButton(libelleAction, onClick = onAction, small = true,
                secondary = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Squelette de chargement.
 *
 * Préféré à un indicateur plein écran : les données sont locales et
 * reviennent en quelques millisecondes, un voile bloquant donnerait
 * l'impression d'une application plus lente qu'elle ne l'est.
 */
@Composable
fun LoadingSkeletonCard(lignes: Int = 3, modifier: Modifier = Modifier) {
    val c = MaterialTheme.sankaiColors
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alphaSkeleton"
    )

    Column(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface2)
            .padding(16.dp)
    ) {
        repeat(lignes) { index ->
            Box(
                Modifier
                    .fillMaxWidth(if (index == 0) 0.55f else 0.9f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(c.surface3)
                    .alpha(alpha)
            )
            if (index != lignes - 1) Spacer(Modifier.height(10.dp))
        }
    }
}
