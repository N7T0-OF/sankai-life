package com.sankailife.ui.screens.life.flashcards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.domain.engine.FlashcardEngine
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.theme.*
import kotlin.math.abs

/**
 * Une carte qu'on juge en la glissant.
 *
 * Quatre directions, quatre jugements. Le geste n'est pas un raccourci vers les
 * boutons : il porte une nuance que deux boutons ne pouvaient pas exprimer —
 * entre « je savais » et « à revoir », il y a « péniblement » et « les yeux
 * fermés », et ces deux-là méritent des intervalles différents.
 *
 * Les boutons restent disponibles. Un système qui n'obéirait qu'au geste
 * exclurait qui ne peut pas glisser précisément.
 */
@Composable
fun CarteGlissable(
    actif: Boolean,
    onJuger: (FlashcardEngine.Jugement) -> Unit,
    modifier: Modifier = Modifier,
    contenu: @Composable BoxScope.() -> Unit
) {
    val densite = LocalDensity.current
    val haptics = LocalHaptics.current

    var decalage by remember { mutableStateOf(Offset.Zero) }
    var relache by remember { mutableStateOf(true) }

    // Seuil de validation. Assez loin pour qu'un frôlement ne juge rien, assez
    // proche pour ne pas demander de traverser l'écran.
    val seuil = with(densite) { 96.dp.toPx() }

    val x by animateFloatAsState(
        if (relache) 0f else decalage.x,
        spring(dampingRatio = 0.7f), label = "glissementX"
    )
    val y by animateFloatAsState(
        if (relache) 0f else decalage.y,
        spring(dampingRatio = 0.7f), label = "glissementY"
    )

    // Le jugement pressenti, d'après la direction dominante du geste.
    val pressenti: FlashcardEngine.Jugement? = remember(decalage, relache) {
        if (relache) null else jugementDe(decalage, seuil)
    }

    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = x
                    translationY = y
                    // Une légère rotation suit le geste : c'est ce qui fait
                    // sentir qu'on manipule un objet et pas un rectangle.
                    rotationZ = (x / 40f).coerceIn(-9f, 9f)
                }
                .pointerInput(actif) {
                    if (!actif) return@pointerInput
                    detectDragGestures(
                        onDragStart = { relache = false; decalage = Offset.Zero },
                        onDragEnd = {
                            val choix = jugementDe(decalage, seuil)
                            relache = true
                            decalage = Offset.Zero
                            if (choix != null) { haptics.success(); onJuger(choix) }
                        },
                        onDragCancel = { relache = true; decalage = Offset.Zero }
                    ) { changement, delta ->
                        changement.consume()
                        decalage += delta
                    }
                },
            content = contenu
        )

        // Étiquette du jugement pressenti, pendant le geste.
        pressenti?.let { j ->
            val (couleur, alignement) = when (j) {
                FlashcardEngine.Jugement.A_REVOIR -> DangerRed to Alignment.CenterStart
                FlashcardEngine.Jugement.CORRECT -> SuccessGreen to Alignment.CenterEnd
                FlashcardEngine.Jugement.FACILE -> AccentViolet to Alignment.TopCenter
                FlashcardEngine.Jugement.DIFFICILE -> WarningAmber to Alignment.BottomCenter
            }
            Box(
                Modifier.align(alignement).padding(14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(couleur.copy(alpha = 0.18f))
                    .border(1.5.dp, couleur, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(j.libelle, color = couleur, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Direction dominante d'un geste, ou null s'il est trop court.
 *
 * La comparaison horizontale/verticale se fait sur la valeur absolue : sans
 * ça, un glissement en diagonale déclencherait le jugement de l'axe qui a
 * simplement le plus grand signe, pas celui que la main a voulu.
 */
private fun jugementDe(decalage: Offset, seuil: Float): FlashcardEngine.Jugement? {
    val horizontal = abs(decalage.x) > abs(decalage.y)
    return when {
        horizontal && decalage.x <= -seuil -> FlashcardEngine.Jugement.A_REVOIR
        horizontal && decalage.x >= seuil -> FlashcardEngine.Jugement.CORRECT
        !horizontal && decalage.y <= -seuil -> FlashcardEngine.Jugement.FACILE
        !horizontal && decalage.y >= seuil -> FlashcardEngine.Jugement.DIFFICILE
        else -> null
    }
}

/** Rappel des gestes, affiché sous la carte. */
@Composable
fun AideGestes(modifier: Modifier = Modifier) {
    val c = MaterialTheme.sankaiColors
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf(
            "←" to FlashcardEngine.Jugement.A_REVOIR,
            "↑" to FlashcardEngine.Jugement.FACILE,
            "↓" to FlashcardEngine.Jugement.DIFFICILE,
            "→" to FlashcardEngine.Jugement.CORRECT
        ).forEach { (fleche, j) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fleche, color = c.textDisabled, fontSize = 12.sp)
                Spacer(Modifier.width(3.dp))
                Text(j.libelle, color = c.textDisabled, fontSize = 10.sp)
            }
        }
    }
}
