package com.sankailife.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Retours haptiques de l'application.
 *
 * Volontairement réduit à quelques intentions plutôt qu'à des durées : les
 * écrans expriment « une récompense vient de tomber », pas « vibre 40 ms ».
 * Ça garantit une sensation cohérente partout et un seul endroit à régler.
 */
interface HapticManager {
    /** Appui sur un bouton, changement d'onglet. Très court. */
    fun click()

    /** Action validée : achat, objectif coché, défi réclamé. */
    fun success()

    /** Coffre ouvert, récompense obtenue. Double impulsion. */
    fun reward()

    /** Montée de niveau. Le plus marqué de tous, et le plus rare. */
    fun levelUp()

    /** Action refusée : fonds insuffisants, hors ligne. */
    fun error()
}

/** Implémentation vide : sert de valeur par défaut et dans les Previews. */
object NoOpHapticManager : HapticManager {
    override fun click() = Unit
    override fun success() = Unit
    override fun reward() = Unit
    override fun levelUp() = Unit
    override fun error() = Unit
}

/**
 * Implémentation Android.
 *
 * [enabled] est piloté par le réglage « Vibrations » et lu à chaque appel :
 * couper l'option prend effet immédiatement, sans redémarrer l'application.
 */
class AndroidHapticManager(context: Context) : HapticManager {

    @Volatile
    var enabled: Boolean = true

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    private val disponible: Boolean = vibrator?.hasVibrator() == true

    override fun click() = jouer(VibrationEffect.createOneShot(12, 70))

    override fun success() = jouer(VibrationEffect.createOneShot(25, 120))

    override fun reward() = jouer(
        VibrationEffect.createWaveform(longArrayOf(0, 18, 55, 30), intArrayOf(0, 110, 0, 160), -1)
    )

    override fun levelUp() = jouer(
        VibrationEffect.createWaveform(
            longArrayOf(0, 25, 45, 25, 45, 55),
            intArrayOf(0, 120, 0, 160, 0, 220),
            -1
        )
    )

    override fun error() = jouer(
        VibrationEffect.createWaveform(longArrayOf(0, 20, 70, 20), intArrayOf(0, 90, 0, 90), -1)
    )

    private fun jouer(effet: VibrationEffect) {
        if (!enabled || !disponible) return
        val v = vibrator ?: return
        // Une vibration ne doit jamais faire planter un écran : sur certains
        // appareils le service répond présent mais refuse l'effet demandé.
        runCatching {
            @Suppress("DEPRECATION")
            v.vibrate(effet)
        }
    }
}

/** Accès aux vibrations depuis n'importe quel composable. */
val LocalHaptics = staticCompositionLocalOf<HapticManager> { NoOpHapticManager }
