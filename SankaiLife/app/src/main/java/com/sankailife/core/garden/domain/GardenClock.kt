package com.sankailife.core.garden.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Source de temps du jardin.
 *
 * Isolée derrière une interface pour deux raisons : les tests doivent pouvoir
 * simuler des heures arbitraires, et l'implémentation réelle doit croiser deux
 * horloges différentes pour détecter un changement manuel.
 */
interface GardenClock {
    /** Heure murale, celle que l'utilisateur peut modifier. */
    fun now(): Instant

    /**
     * Millisecondes depuis le démarrage de l'appareil.
     * Impossible à modifier depuis les réglages, mais remise à zéro au
     * redémarrage — d'où le croisement avec l'heure murale.
     */
    fun elapsedRealtimeMillis(): Long

    fun currentDayId(): String = now().atZone(ZoneId.systemDefault()).toLocalDate().toString()

    fun currentWeekId(): String {
        val date = now().atZone(ZoneId.systemDefault()).toLocalDate()
        val champs = WeekFields.of(Locale.FRANCE)
        return "%d-S%02d".format(
            date.get(champs.weekBasedYear()),
            date.get(champs.weekOfWeekBasedYear())
        )
    }
}

/** Dernier repère temporel connu, persisté entre deux ouvertures. */
data class TrustedTimeState(
    val derniereHeureMurale: Long = 0L,
    val dernierElapsedRealtime: Long = 0L
)

/**
 * Détection d'un changement d'heure.
 *
 * Aucune application hors ligne sans serveur ne peut empêcher un utilisateur
 * d'avancer l'horloge de son téléphone. L'objectif n'est donc pas d'interdire,
 * mais de **ne pas récompenser** : on borne les gains hors ligne au lieu de
 * bloquer le joueur ou de l'accuser.
 */
object TrustedTimeEngine {

    /** Gain hors ligne maximal pris en compte, quelle que soit l'absence. */
    const val PLAFOND_HORS_LIGNE_MINUTES = 24L * 60L

    /** Tolérance sur la dérive entre les deux horloges. */
    private const val TOLERANCE_MINUTES = 5L

    enum class Verdict {
        /** Les deux horloges concordent. */
        COHERENT,
        /** Redémarrage détecté : impossible de vérifier, on accepte prudemment. */
        REDEMARRAGE,
        /** L'heure a reculé. */
        RECUL,
        /** L'heure a bondi en avant sans temps réel correspondant. */
        BOND_EN_AVANT
    }

    data class Resultat(
        val verdict: Verdict,
        /** Minutes réellement créditées à la croissance. */
        val minutesRetenues: Long
    )

    /**
     * Compare l'écoulement des deux horloges depuis la dernière ouverture.
     *
     * @param precedent repère enregistré à la fermeture précédente.
     * @param heureMurale heure système actuelle, en millisecondes.
     * @param elapsedRealtime temps depuis démarrage, en millisecondes.
     */
    fun evaluer(
        precedent: TrustedTimeState,
        heureMurale: Long,
        elapsedRealtime: Long
    ): Resultat {
        // Premier lancement : rien à comparer.
        if (precedent.derniereHeureMurale <= 0L) {
            return Resultat(Verdict.COHERENT, 0L)
        }

        val deltaMuralMinutes = (heureMurale - precedent.derniereHeureMurale) / 60_000
        val deltaElapsedMinutes = (elapsedRealtime - precedent.dernierElapsedRealtime) / 60_000

        // elapsedRealtime repart de zéro au redémarrage : on ne peut plus
        // recouper, on fait confiance à l'heure murale mais on la borne.
        if (deltaElapsedMinutes < 0) {
            return Resultat(
                Verdict.REDEMARRAGE,
                deltaMuralMinutes.coerceIn(0L, PLAFOND_HORS_LIGNE_MINUTES)
            )
        }

        if (deltaMuralMinutes < 0) {
            // Horloge reculée : aucune croissance créditée, mais rien n'est
            // perdu — la culture reprendra à la prochaine ouverture cohérente.
            return Resultat(Verdict.RECUL, 0L)
        }

        // L'heure murale a avancé bien plus que le temps réellement écoulé.
        if (deltaMuralMinutes > deltaElapsedMinutes + TOLERANCE_MINUTES) {
            return Resultat(
                Verdict.BOND_EN_AVANT,
                deltaElapsedMinutes.coerceAtMost(PLAFOND_HORS_LIGNE_MINUTES)
            )
        }

        return Resultat(
            Verdict.COHERENT,
            deltaMuralMinutes.coerceIn(0L, PLAFOND_HORS_LIGNE_MINUTES)
        )
    }

    /** Message affiché à l'utilisateur, jamais accusateur. */
    fun message(verdict: Verdict): String? = when (verdict) {
        Verdict.RECUL, Verdict.BOND_EN_AVANT ->
            "L'heure de l'appareil semble avoir changé. La croissance a été " +
            "mise en pause le temps de se resynchroniser."
        else -> null
    }
}
