package com.sankailife.ui.screens.garden

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.sankailife.core.garden.domain.LightingEngine
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * Le voile d'ambiance du jardin.
 *
 * Il ne couvre que le terrain, jamais les commandes. Teinter l'interface
 * rendrait les boutons illisibles le soir, qui est précisément le moment où
 * beaucoup ouvriront l'application — et l'ambiance est un plaisir, pas une
 * raison de ne plus savoir sur quoi appuyer.
 *
 * Aucun modificateur de saisie n'est posé ici : le voile laisse passer tous
 * les gestes vers les parcelles qui sont dessous.
 */
@Composable
fun VoileAmbiance(
    ambiance: LightingEngine.Ambiance,
    modifier: Modifier = Modifier
) {
    if (ambiance.opacite <= 0.001f) return

    Canvas(modifier) {
        drawRect(
            color = Color(ambiance.couleur).copy(alpha = ambiance.opacite)
        )
    }
}

/**
 * Les étoiles de la nuit.
 *
 * Positions tirées une seule fois et mémorisées : un ciel qui se redistribue
 * à chaque recomposition scintillerait n'importe comment.
 */
@Composable
fun CielEtoile(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val etoiles = remember {
        val alea = Random(4242)
        List(28) {
            Triple(alea.nextFloat(), alea.nextFloat() * 0.55f, alea.nextFloat())
        }
    }

    val transition = rememberInfiniteTransition(label = "etoiles")
    val scintillement by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing)),
        label = "scintillement"
    )

    Canvas(modifier) {
        etoiles.forEach { (fx, fy, phase) ->
            // Chaque étoile a sa phase : sans ça, tout le ciel clignoterait
            // ensemble, ce qui ressemble à un défaut d'affichage.
            val cycle = ((scintillement + phase) % 1f)
            val eclat = 0.25f + 0.55f * (1f - (cycle * 2f - 1f).absoluteValue)
            drawCircle(
                color = Color.White.copy(alpha = eclat * 0.7f),
                radius = 1.1f + phase * 1.4f,
                center = Offset(fx * size.width, fy * size.height)
            )
        }
    }
}

/**
 * La pluie.
 *
 * Trois profondeurs de gouttes, chacune avec sa vitesse et sa taille : c'est
 * ce qui donne du relief. Une seule couche donnerait un rideau plat, qu'on
 * lirait comme une texture plutôt que comme de la pluie.
 *
 * Les gouttes ne sont pas des objets : leur position est **calculée** à partir
 * du temps et d'une graine par goutte. Rien n'est alloué pendant l'animation,
 * ce qui évite de faire travailler le ramasse-miettes soixante fois par
 * seconde.
 */
@Composable
fun PluieAnimee(
    intensite: LightingEngine.IntensitePluie,
    animationsReduites: Boolean,
    modifier: Modifier = Modifier
) {
    val nombre = LightingEngine.nombreGouttes(intensite, animationsReduites)
    if (nombre == 0) return

    // Une goutte : position horizontale, profondeur, décalage de phase.
    val gouttes = remember(nombre) {
        val alea = Random(1789)
        List(nombre) {
            Goutte(
                x = alea.nextFloat(),
                couche = alea.nextInt(3),
                phase = alea.nextFloat(),
                inclinaison = alea.nextFloat() * 0.12f
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "pluie")
    val temps by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = LinearEasing), RepeatMode.Restart
        ),
        label = "chute"
    )

    Canvas(modifier) {
        gouttes.forEach { goutte -> dessinerGoutte(goutte, temps, intensite) }
    }
}

private data class Goutte(
    val x: Float,
    val couche: Int,
    val phase: Float,
    val inclinaison: Float
)

private fun DrawScope.dessinerGoutte(
    goutte: Goutte,
    temps: Float,
    intensite: LightingEngine.IntensitePluie
) {
    // Les couches lointaines tombent plus lentement et plus pâles : c'est la
    // seule chose qui crée la sensation de profondeur.
    val vitesse = when (goutte.couche) {
        0 -> 0.6f
        1 -> 0.85f
        else -> 1.15f
    } * intensite.vitesse

    val longueur = when (goutte.couche) {
        0 -> 9f
        1 -> 14f
        else -> 20f
    }
    val opacite = when (goutte.couche) {
        0 -> 0.18f
        1 -> 0.28f
        else -> 0.40f
    }

    val progression = ((temps * vitesse + goutte.phase) % 1f)
    val y = progression * (size.height + longueur) - longueur
    val x = goutte.x * size.width + progression * size.width * goutte.inclinaison

    drawLine(
        color = Color(0xFFBFE0F5).copy(alpha = opacite),
        start = Offset(x, y),
        end = Offset(x - longueur * goutte.inclinaison * 3f, y + longueur),
        strokeWidth = 1f + goutte.couche * 0.5f,
        cap = StrokeCap.Round
    )

    // Éclaboussure : un arc bref quand la goutte touche le bas du terrain.
    if (progression > 0.94f && goutte.couche == 2) {
        val impact = (progression - 0.94f) / 0.06f
        drawCircle(
            color = Color(0xFFBFE0F5).copy(alpha = (1f - impact) * 0.25f),
            radius = 2f + impact * 6f,
            center = Offset(x, size.height - 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
        )
    }
}
