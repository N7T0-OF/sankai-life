package com.sankailife.core.garden.domain

import java.time.LocalTime

/**
 * Cycle jour / nuit du jardin.
 *
 * Le jardin suit l'heure réelle du téléphone, sans accélération. Un cycle
 * compressé façon jeu de ferme classique entrerait en conflit avec la
 * croissance, qui est déjà indexée sur le temps réel : on aurait un ciel qui
 * change toutes les dix minutes pendant qu'une plante met six heures à pousser.
 *
 * Conséquence assumée : quelqu'un qui n'ouvre l'application que le soir ne
 * verra jamais le jour. Toutes les activités essentielles restent donc
 * disponibles la nuit — seul le marchand dort.
 */
object DayNightEngine {

    enum class Phase(val libelle: String, val emoji: String) {
        AUBE("Lever du jour", "🌅"),
        JOUR("Journée", "☀️"),
        CREPUSCULE("Coucher du soleil", "🌇"),
        NUIT("Nuit", "🌙")
    }

    const val OUVERTURE_MAGASIN = 8
    const val FERMETURE_MAGASIN = 20

    fun phase(heure: LocalTime = LocalTime.now()): Phase = when (heure.hour) {
        in 6..7 -> Phase.AUBE
        in 8..17 -> Phase.JOUR
        in 18..20 -> Phase.CREPUSCULE
        else -> Phase.NUIT
    }

    /** Le marchand n'est présent qu'aux heures d'ouverture. */
    fun magasinOuvert(heure: LocalTime = LocalTime.now()): Boolean =
        heure.hour in OUVERTURE_MAGASIN until FERMETURE_MAGASIN

    /** Message affiché sur la devanture quand le magasin dort. */
    fun messageMagasinFerme(heure: LocalTime = LocalTime.now()): String {
        val heures = if (heure.hour >= FERMETURE_MAGASIN) {
            24 - heure.hour + OUVERTURE_MAGASIN
        } else {
            OUVERTURE_MAGASIN - heure.hour
        }
        return "Le marchand dort. Réouverture dans environ ${heures} h."
    }

    /**
     * Teinte appliquée au terrain, de 0 (plein jour) à 1 (nuit noire).
     * Sert uniquement au rendu : aucune mécanique n'en dépend, pour que le
     * jeu reste identique quelle que soit l'heure d'ouverture.
     */
    fun intensiteNuit(heure: LocalTime = LocalTime.now()): Float = when (phase(heure)) {
        Phase.JOUR -> 0f
        Phase.AUBE -> 0.25f
        Phase.CREPUSCULE -> 0.35f
        Phase.NUIT -> 0.55f
    }

    /** Certaines espèces ne se plantent que la nuit. */
    fun favoriseLesNocturnes(heure: LocalTime = LocalTime.now()): Boolean =
        phase(heure) == Phase.NUIT
}
