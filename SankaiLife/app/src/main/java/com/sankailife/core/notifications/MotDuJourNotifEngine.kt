package com.sankailife.core.notifications

import java.time.LocalDateTime

/**
 * Planification de la notification quotidienne du mot du jour.
 *
 * Fonctions pures, sans Android : c'est ce qui rend la logique testable, y
 * compris les cas pénibles (heure déjà passée, minuit, heure limite).
 */
object MotDuJourNotifEngine {

    /**
     * Prochain déclenchement strictement après [maintenant], à [heureMinutes]
     * minutes depuis minuit.
     *
     * Aujourd'hui si l'heure n'est pas encore passée, demain sinon. Un instant
     * égal à maintenant n'est pas un déclenchement : l'alarme qui vient de
     * partir ne doit pas se reprogrammer sur elle-même.
     */
    fun prochaineHeure(
        heureMinutes: Int,
        maintenant: LocalDateTime
    ): LocalDateTime {
        val cible = maintenant.toLocalDate()
            .atStartOfDay()
            .plusMinutes(heureMinutes.coerceIn(0, 24 * 60 - 1).toLong())
        return if (cible.isAfter(maintenant)) cible else cible.plusDays(1)
    }
}
