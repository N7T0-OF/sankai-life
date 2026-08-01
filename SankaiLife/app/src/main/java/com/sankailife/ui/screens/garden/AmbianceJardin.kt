package com.sankailife.ui.screens.garden

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sankailife.core.garden.domain.CloudShadowState
import com.sankailife.core.garden.domain.GardenWindState
import com.sankailife.core.garden.domain.GraphicsQuality
import com.sankailife.core.garden.domain.LightingEngine
import com.sankailife.core.garden.domain.WeatherLightingState
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
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

/** Filtre diffus du ciel couvert, interpolé sans toucher au HUD. */
@Composable
fun WeatherLightingOverlay(
    state: WeatherLightingState,
    modifier: Modifier = Modifier
) {
    val opacity by animateFloatAsState(
        targetValue = state.opacity,
        animationSpec = tween(12_000, easing = LinearEasing),
        label = "weatherLighting"
    )
    if (opacity <= 0.001f) return
    Canvas(modifier) {
        drawRect(Color(state.tintArgb).copy(alpha = opacity.coerceIn(0f, 1f)))
    }
}

/**
 * Ombres de nuages répétables, générées une seule fois en mémoire.
 *
 * Le motif est ancré au monde via [worldOffset] et [zoom] : déplacer la caméra
 * révèle une autre partie de l'ombre au lieu de coller celle-ci à l'écran.
 */
@Composable
fun CloudShadowOverlay(
    state: CloudShadowState,
    wind: GardenWindState,
    quality: GraphicsQuality,
    reduceMotion: Boolean,
    worldOffset: Offset,
    zoom: Float,
    modifier: Modifier = Modifier
) {
    val targetOpacity = if (state.enabled) state.opacity else 0f
    val opacity by animateFloatAsState(
        targetValue = targetOpacity,
        animationSpec = tween(15_000, easing = LinearEasing),
        label = "cloudOpacity"
    )
    val density by animateFloatAsState(
        targetValue = if (state.enabled) state.density else 0f,
        animationSpec = tween(15_000, easing = LinearEasing),
        label = "cloudDensity"
    )
    if (!state.enabled && opacity <= 0.001f) return

    val layerCount = state.layers.coerceIn(1, 3)
    val tiles = remember(layerCount) {
        List(layerCount) { index -> creerTuileNuage(256, 7_319 + index * 977) }
    }
    val colorFilter = remember(state.tintArgb) {
        ColorFilter.tint(Color(state.tintArgb), BlendMode.SrcIn)
    }

    var elapsedSeconds by remember { mutableFloatStateOf(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val moving = state.enabled && state.speed > 0f && !reduceMotion
    LaunchedEffect(moving, quality, state.speed, lifecycleOwner) {
        if (!moving) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val frameInterval = if (quality == GraphicsQuality.LOW) 33_000_000L else 16_000_000L
            var previous = withFrameNanos { it }
            var lastPublished = previous
            var accumulated = elapsedSeconds
            while (isActive) {
                val now = withFrameNanos { it }
                accumulated += (now - previous).coerceAtMost(100_000_000L) / 1_000_000_000f
                previous = now
                if (now - lastPublished >= frameInterval) {
                    elapsedSeconds = accumulated
                    lastPublished = now
                }
            }
        }
    }

    Canvas(modifier) {
        // Les nuages et la pluie lisent strictement le meme vent partage.
        val direction = wind.directionDegrees * PI.toFloat() / 180f
        val directionX = cos(direction)
        val directionY = sin(direction)
        val weights = floatArrayOf(0.72f, 0.46f, 0.31f)
        val scales = floatArrayOf(1f, 0.72f, 1.38f)
        val speeds = floatArrayOf(1f, 1.13f, 0.86f)
        val filter = if (quality == GraphicsQuality.LOW) FilterQuality.Low else FilterQuality.Medium

        tiles.forEachIndexed { index, image ->
            val tilePixels = (size.minDimension * state.scale * scales[index] * zoom)
                .roundToInt().coerceAtLeast(96)
            val travel = elapsedSeconds * state.speed * speeds[index] * zoom
            val phaseX = index * tilePixels * 0.37f
            val phaseY = index * tilePixels * 0.61f
            val baseX = worldOffset.x + directionX * travel + phaseX
            val baseY = worldOffset.y + directionY * travel + phaseY
            var x = -tilePixels + moduloPositif(baseX, tilePixels.toFloat()).roundToInt()
            val alpha = (opacity * density * weights[index]).coerceIn(0f, 0.32f)

            while (x < size.width + tilePixels) {
                var y = -tilePixels + moduloPositif(baseY, tilePixels.toFloat()).roundToInt()
                while (y < size.height + tilePixels) {
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(x, y),
                        dstSize = IntSize(tilePixels, tilePixels),
                        alpha = alpha,
                        colorFilter = colorFilter,
                        filterQuality = filter
                    )
                    y += tilePixels
                }
                x += tilePixels
            }
        }
    }
}

private fun creerTuileNuage(size: Int, seed: Int): androidx.compose.ui.graphics.ImageBitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val random = Random(seed)

    repeat(8) {
        val x = random.nextFloat() * size
        val y = random.nextFloat() * size
        val radius = size * (0.18f + random.nextFloat() * 0.22f)
        val alpha = 105 + random.nextInt(55)
        for (dx in -size..size step size) {
            for (dy in -size..size step size) {
                val cx = x + dx
                val cy = y + dy
                paint.shader = RadialGradient(
                    cx,
                    cy,
                    radius,
                    intArrayOf(
                        android.graphics.Color.argb(alpha, 0, 0, 0),
                        android.graphics.Color.argb(alpha / 2, 0, 0, 0),
                        android.graphics.Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx, cy, radius, paint)
            }
        }
    }
    paint.shader = null
    return bitmap.asImageBitmap()
}

private fun moduloPositif(value: Float, modulus: Float): Float =
    ((value % modulus) + modulus) % modulus

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
    wind: GardenWindState,
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
                inclinaison = (alea.nextFloat() - 0.5f) * 0.05f
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
        gouttes.forEach { goutte -> dessinerGoutte(goutte, temps, intensite, wind) }
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
    intensite: LightingEngine.IntensitePluie,
    wind: GardenWindState
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
    val direction = wind.directionDegrees * PI.toFloat() / 180f
    val inclinaison = goutte.inclinaison + cos(direction) * wind.strength * 0.20f
    val marge = longueur * 2f
    val brut = goutte.x * size.width + progression * size.width * inclinaison
    val x = moduloPositif(brut + marge, size.width + marge * 2f) - marge

    drawLine(
        color = Color(0xFFBFE0F5).copy(alpha = opacite),
        start = Offset(x, y),
        end = Offset(x - longueur * inclinaison * 3f, y + longueur),
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
