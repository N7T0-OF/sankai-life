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
     * Le temps de fond d'une journée.
     *
     * **Il ne pleut jamais ici.** C'était le défaut de la première version :
     * tirer la météo par jour signifiait qu'un jour pluvieux pleuvait vingt-
     * quatre heures d'affilée. La pluie est devenue une averse ponctuelle,
     * traitée séparément par [averse].
     */
    fun cielDuJour(jourIso: String): Meteo {
        val graine = abs(("ciel:$jourIso").hashCode()) % 100
        return when {
            graine < 8 -> Meteo.CANICULE
            graine < 58 -> Meteo.SOLEIL
            else -> Meteo.NUAGEUX
        }
    }

    /** Une averse : quand elle commence, combien de temps elle dure. */
    data class Averse(val debutMinutes: Int, val dureeMinutes: Int, val meteo: Meteo)

    /** Minutes minimales entre deux averses. Une par jour au plus. */
    const val DUREE_MAX_MINUTES = 25

    /**
     * L'averse du jour, s'il y en a une.
     *
     * Environ un jour sur trois, pendant vingt minutes au plus. Calculée depuis
     * la date comme le reste : hors ligne, un tirage stocké finirait par
     * diverger, et relancer l'application changerait le temps qu'il fait.
     *
     * La rareté est délibérée. La pluie arrose gratuitement ; permanente, elle
     * rendrait l'arrosage facultatif, donc la révision qui produit l'eau aussi.
     */
    fun averse(jourIso: String): Averse? {
        val graine = abs(("averse:$jourIso").hashCode())
        if (graine % 100 >= 34) return null

        val intensite = when ((graine / 100) % 100) {
            in 0..64 -> Meteo.PLUIE
            else -> Meteo.ORAGE
        }
        // Les averses tombent entre 6 h et 21 h : une pluie nocturne que
        // personne ne voit ne serait qu'un cadeau invisible.
        val debut = 6 * 60 + (graine / 10_000) % (15 * 60)
        val duree = when (intensite) {
            Meteo.ORAGE -> 6 + (graine / 7) % 7
            else -> 10 + (graine / 13) % 16
        }
        return Averse(debut, duree.coerceAtMost(DUREE_MAX_MINUTES), intensite)
    }

    /** La météo à un instant précis : averse en cours, sinon ciel du jour. */
    fun meteoA(jourIso: String, minutesDepuisMinuit: Int): Meteo {
        val a = averse(jourIso) ?: return cielDuJour(jourIso)
        val dedans = minutesDepuisMinuit >= a.debutMinutes &&
            minutesDepuisMinuit < a.debutMinutes + a.dureeMinutes
        return if (dedans) a.meteo else cielDuJour(jourIso)
    }

    fun meteoActuelle(zone: ZoneId = ZoneId.systemDefault()): Meteo {
        val maintenant = java.time.LocalDateTime.now(zone)
        return meteoA(
            maintenant.toLocalDate().toString(),
            maintenant.hour * 60 + maintenant.minute
        )
    }

    /** Conservé pour les tests et l'affichage : le temps dominant du jour. */
    fun meteoDuJour(jourIso: String): Meteo = cielDuJour(jourIso)

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
            val debutJour = maxOf(debut, jour.atStartOfDay())
            val finJour = minOf(fin, jour.plusDays(1).atStartOfDay())

            if (finJour.isAfter(debutJour)) {
                val ciel = cielDuJour(jour.toString())
                val a = averse(jour.toString())

                if (a == null) {
                    resultat.add(
                        Segment(ciel, java.time.Duration.between(debutJour, finJour).toMinutes())
                    )
                } else {
                    // La journée se découpe en trois : avant l'averse, pendant,
                    // après. Compter l'averse sur toute la journée reviendrait
                    // à arroser le jardin gratuitement pendant vingt heures.
                    val debutAverse = jour.atStartOfDay().plusMinutes(a.debutMinutes.toLong())
                    val finAverse = debutAverse.plusMinutes(a.dureeMinutes.toLong())

                    fun ajouter(d: java.time.LocalDateTime, f: java.time.LocalDateTime, m: Meteo) {
                        val d2 = maxOf(d, debutJour)
                        val f2 = minOf(f, finJour)
                        if (f2.isAfter(d2)) {
                            val minutes = java.time.Duration.between(d2, f2).toMinutes()
                            if (minutes > 0) resultat.add(Segment(m, minutes))
                        }
                    }

                    ajouter(debutJour, debutAverse, ciel)
                    ajouter(debutAverse, finAverse, a.meteo)
                    ajouter(finAverse, finJour, ciel)
                }
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
