package com.sankailife.core.notifications

import com.sankailife.core.data.db.entities.MemoProfileEntity
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * Calcule quand un module mémo doit se déclencher.
 *
 * Fonctions pures, sans Android : c'est ce qui rend le comportement testable,
 * notamment les cas pénibles (plage traversant minuit, jours inactifs,
 * changement d'heure).
 */
object MemoScheduleEngine {

    private const val MINUTES_PAR_JOUR = 24 * 60

    /** Jours actifs au format ISO : 1 = lundi … 7 = dimanche. */
    fun joursActifs(profil: MemoProfileEntity): Set<Int> =
        profil.activeDays.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()

    /**
     * Créneaux d'un module à heure fixe, en minutes depuis minuit.
     *
     * Le premier tombe à l'heure choisie, les suivants sont espacés de
     * `12 / fréquence` heures. Un créneau qui déborde sur le lendemain revient
     * au début de la journée ; les heures silencieuses le filtreront ensuite.
     */
    fun creneauxFixes(profil: MemoProfileEntity): List<Int> {
        val base = (profil.scheduledHour.coerceIn(0, 23) * 60) +
                   profil.scheduledMinute.coerceIn(0, 59)
        val frequence = profil.frequencyPerDay.coerceIn(1, 6)
        val pas = (12 * 60) / frequence
        return (0 until frequence)
            .map { (base + it * pas) % MINUTES_PAR_JOUR }
            .distinct()
            .sorted()
    }

    /**
     * Créneaux d'un module en mode aléatoire, tirés dans la plage définie.
     * Un nouveau tirage a lieu à chaque replanification, donc l'heure change
     * d'un jour à l'autre — c'est tout l'intérêt du mode.
     */
    fun creneauxAleatoires(profil: MemoProfileEntity, alea: Random): List<Int> {
        val debut = (profil.randomStartHour.coerceIn(0, 23) * 60) +
                    profil.randomStartMinute.coerceIn(0, 59)
        var fin = (profil.randomEndHour.coerceIn(0, 23) * 60) +
                  profil.randomEndMinute.coerceIn(0, 59)
        // Une plage inversée ou vide n'a pas de sens : on retombe sur la journée.
        if (fin <= debut) fin = (debut + 60).coerceAtMost(MINUTES_PAR_JOUR - 1)

        val frequence = profil.frequencyPerDay.coerceIn(1, 6)
        val ecartMinimal = 30

        val tirages = mutableListOf<Int>()
        var tentatives = 0
        while (tirages.size < frequence && tentatives < 60) {
            tentatives++
            val candidat = alea.nextInt(debut, fin + 1)
            // Deux notifications collées seraient vécues comme un bug.
            if (tirages.none { kotlin.math.abs(it - candidat) < ecartMinimal }) {
                tirages += candidat
            }
        }
        return tirages.sorted()
    }

    /**
     * Prochain déclenchement strictement après [maintenant], ou null si le
     * module ne se déclenchera jamais (aucun jour actif, tout en heures
     * silencieuses).
     *
     * On explore huit jours : assez pour couvrir n'importe quelle combinaison
     * de jours de semaine, et borné pour ne jamais boucler indéfiniment.
     */
    fun prochainDeclenchement(
        profil: MemoProfileEntity,
        maintenant: LocalDateTime,
        heuresSilencieuses: QuietHours,
        alea: Random = Random.Default
    ): LocalDateTime? {
        val jours = joursActifs(profil)
        if (jours.isEmpty()) return null

        for (decalage in 0..7) {
            val jour = maintenant.toLocalDate().plusDays(decalage.toLong())
            if (jour.dayOfWeek.value !in jours) continue

            val creneaux = if (profil.randomMode) {
                creneauxAleatoires(profil, alea)
            } else {
                creneauxFixes(profil)
            }

            for (minute in creneaux) {
                if (heuresSilencieuses.contient(minute)) continue
                val candidat = jour.atStartOfDay().plusMinutes(minute.toLong())
                if (candidat.isAfter(maintenant)) return candidat
            }
        }
        return null
    }

    /** Version pratique renvoyant directement un instant epoch en millisecondes. */
    fun prochainDeclenchementMillis(
        profil: MemoProfileEntity,
        maintenant: LocalDateTime,
        heuresSilencieuses: QuietHours,
        zone: ZoneId = ZoneId.systemDefault(),
        alea: Random = Random.Default
    ): Long? = prochainDeclenchement(profil, maintenant, heuresSilencieuses, alea)
        ?.atZone(zone)
        ?.toInstant()
        ?.toEpochMilli()
}
