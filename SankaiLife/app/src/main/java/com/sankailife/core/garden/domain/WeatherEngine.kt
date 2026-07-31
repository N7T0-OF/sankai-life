package com.sankailife.core.garden.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * La météo du jardin.
 *
 * Elle est **calculée** à partir de la date, jamais tirée au sort ni stockée.
 * Même raison que pour le cours du marché : hors ligne, un aléatoire enregistré
 * finirait par diverger entre deux installations, et relancer l'application
 * changerait le temps qu'il fait — ce qui ressemblerait à un défaut.
 *
 * Elle ne fait qu'une chose, mais elle la fait pour tout le jardin : modifier
 * l'évaporation, et parfois arroser gratuitement. C'est la seule source d'eau
 * qui ne vient pas de l'apprentissage, et elle est volontairement irrégulière —
 * on ne peut pas compter dessus pour se dispenser de réviser.
 */
object WeatherEngine {

    enum class Meteo(
        val libelle: String,
        val emoji: String,
        /** Multiplicateur appliqué à la vitesse d'évaporation. */
        val facteurEvaporation: Float,
        /** Humidité apportée par heure de pluie, de 0 à 1. */
        val pluieParHeure: Float
    ) {
        CANICULE("Canicule", "🔥", 1.60f, 0f),
        SOLEIL("Grand soleil", "☀️", 1.25f, 0f),
        NUAGEUX("Nuageux", "⛅", 0.90f, 0f),
        PLUIE("Pluie", "🌧️", 0.40f, 0.10f),
        ORAGE("Orage", "⛈️", 0.30f, 0.18f);

        val pleut: Boolean get() = pluieParHeure > 0f
    }

    /**
     * Météo d'un jour donné.
     *
     * La répartition penche vers le beau temps : la pluie doit rester une
     * bonne surprise. Trop fréquente, elle rendrait l'arrosage — donc la
     * révision qui produit l'eau — facultatif.
     */
    fun meteoDuJour(jourIso: String): Meteo {
        val graine = abs(("meteo:$jourIso").hashCode()) % 100
        return when {
            graine < 4 -> Meteo.CANICULE
            graine < 46 -> Meteo.SOLEIL
            graine < 74 -> Meteo.NUAGEUX
            graine < 93 -> Meteo.PLUIE
            else -> Meteo.ORAGE
        }
    }

    fun meteoActuelle(zone: ZoneId = ZoneId.systemDefault()): Meteo =
        meteoDuJour(LocalDate.now(zone).toString())

    /** Prévision des jours suivants, pour décider quand arroser. */
    fun previsions(jours: Int, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, Meteo>> {
        val aujourdhui = LocalDate.now(zone)
        return (0 until jours).map { decalage ->
            val jour = aujourdhui.plusDays(decalage.toLong())
            jour to meteoDuJour(jour.toString())
        }
    }

    /** Une tranche d'absence passée sous une même météo. */
    data class Segment(val meteo: Meteo, val minutes: Long)

    /**
     * Découpe une absence en tranches d'un jour.
     *
     * Une absence de trente heures traverse deux météos. Appliquer celle du
     * retour à toute la période ferait pleuvoir rétroactivement sur une
     * journée qui avait été sèche.
     */
    fun segments(
        debutMillis: Long,
        finMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Segment> {
        if (finMillis <= debutMillis) return emptyList()

        val debut = Instant.ofEpochMilli(debutMillis).atZone(zone).toLocalDateTime()
        val fin = Instant.ofEpochMilli(finMillis).atZone(zone).toLocalDateTime()

        val resultat = mutableListOf<Segment>()
        var jour = debut.toLocalDate()

        while (!jour.isAfter(fin.toLocalDate())) {
            val debutUtile = maxOf(debut, jour.atStartOfDay())
            val finUtile = minOf(fin, jour.plusDays(1).atStartOfDay())
            if (finUtile.isAfter(debutUtile)) {
                val minutes = java.time.Duration.between(debutUtile, finUtile).toMinutes()
                if (minutes > 0) resultat.add(Segment(meteoDuJour(jour.toString()), minutes))
            }
            jour = jour.plusDays(1)
        }
        return resultat
    }

    /** Phrase affichée en tête du jardin. */
    fun message(meteo: Meteo): String = when (meteo) {
        Meteo.CANICULE -> "La terre sèche très vite aujourd'hui."
        Meteo.SOLEIL -> "Beau temps — pense à arroser."
        Meteo.NUAGEUX -> "Ciel couvert, la terre garde son eau."
        Meteo.PLUIE -> "Il pleut : le jardin s'arrose tout seul."
        Meteo.ORAGE -> "Orage — les parcelles se gorgent d'eau."
    }
}
