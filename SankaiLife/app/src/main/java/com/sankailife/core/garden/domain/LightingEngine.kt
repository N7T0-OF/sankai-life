package com.sankailife.core.garden.domain

import java.time.LocalTime

/**
 * L'ambiance lumineuse du jardin, heure par heure.
 *
 * Le voile nocturne précédent était binaire : une opacité par phase, qui
 * changeait d'un coup à heure fixe. On voyait le jardin sauter.
 *
 * Ici la couleur et l'opacité sont **interpolées en continu** à partir de
 * l'heure. À 19 h 30 l'ambiance est exactement à mi-chemin entre le coucher de
 * soleil et le crépuscule, sans qu'aucune règle ne le dise — c'est le calcul
 * qui le donne.
 *
 * Rien de tout cela n'a d'effet sur le jeu. Aucune mécanique ne dépend de la
 * lumière : deux joueurs qui ouvrent l'application à des heures différentes
 * doivent avoir exactement les mêmes chances.
 */
object LightingEngine {

    /**
     * Une teinte d'ambiance : couleur ARGB et opacité.
     * Les couleurs sont des entiers plutôt que des Color de Compose pour que
     * ce moteur reste testable sans dépendance Android.
     */
    data class Ambiance(
        val couleur: Long,
        val opacite: Float,
        /** Les lanternes du jardin s'allument-elles ? */
        val lanternes: Boolean,
        /** Les étoiles sont-elles visibles ? */
        val etoiles: Boolean
    )

    /**
     * Points de repère de la journée, en heures décimales.
     *
     * Entre deux repères, tout est interpolé. Les valeurs sont volontairement
     * faibles : au-delà de 45 % d'opacité, le terrain devient illisible, et
     * une application qu'on ouvre surtout le soir ne peut pas se permettre
     * d'être pénible à regarder la nuit.
     */
    private val REPERES: List<Pair<Float, Ambiance>> = listOf(
        0f to Ambiance(0xFF081B3A, 0.38f, lanternes = true, etoiles = true),   // nuit
        5f to Ambiance(0xFF081B3A, 0.38f, lanternes = true, etoiles = true),
        6.5f to Ambiance(0xFF3A2A4A, 0.24f, lanternes = true, etoiles = false), // fin de nuit
        7.5f to Ambiance(0xFFFFB27A, 0.16f, lanternes = false, etoiles = false),// aube
        9f to Ambiance(0xFFFFD9A0, 0.06f, lanternes = false, etoiles = false),
        11f to Ambiance(0xFFFFFFFF, 0.00f, lanternes = false, etoiles = false), // plein jour
        16f to Ambiance(0xFFFFFFFF, 0.00f, lanternes = false, etoiles = false),
        18f to Ambiance(0xFFFFB067, 0.12f, lanternes = false, etoiles = false), // dorée
        19.5f to Ambiance(0xFFE2743C, 0.20f, lanternes = true, etoiles = false),// coucher
        20.5f to Ambiance(0xFF7A4A86, 0.26f, lanternes = true, etoiles = false),// crépuscule
        21.5f to Ambiance(0xFF2B2F63, 0.33f, lanternes = true, etoiles = true),
        23f to Ambiance(0xFF081B3A, 0.38f, lanternes = true, etoiles = true),   // nuit
        24f to Ambiance(0xFF081B3A, 0.38f, lanternes = true, etoiles = true)
    )

    /** Ambiance à une heure donnée, interpolée entre les deux repères voisins. */
    fun ambiance(heure: LocalTime = LocalTime.now()): Ambiance {
        val h = heure.hour + heure.minute / 60f

        var avant = REPERES.first()
        var apres = REPERES.last()
        for (i in 0 until REPERES.size - 1) {
            if (h >= REPERES[i].first && h <= REPERES[i + 1].first) {
                avant = REPERES[i]
                apres = REPERES[i + 1]
                break
            }
        }

        val plage = apres.first - avant.first
        val t = if (plage <= 0f) 0f else ((h - avant.first) / plage).coerceIn(0f, 1f)

        return Ambiance(
            couleur = melanger(avant.second.couleur, apres.second.couleur, t),
            opacite = avant.second.opacite + (apres.second.opacite - avant.second.opacite) * t,
            // Les bascules booléennes suivent le repère le plus proche : une
            // lanterne ne s'allume pas à moitié.
            lanternes = if (t < 0.5f) avant.second.lanternes else apres.second.lanternes,
            etoiles = if (t < 0.5f) avant.second.etoiles else apres.second.etoiles
        )
    }

    /** Interpolation composante par composante, en conservant l'alpha. */
    private fun melanger(a: Long, b: Long, t: Float): Long {
        fun canal(v: Long, decalage: Int) = ((v shr decalage) and 0xFF).toInt()
        fun mix(ca: Int, cb: Int) = (ca + (cb - ca) * t).toInt().coerceIn(0, 255).toLong()

        val alpha = mix(canal(a, 24), canal(b, 24))
        val rouge = mix(canal(a, 16), canal(b, 16))
        val vert = mix(canal(a, 8), canal(b, 8))
        val bleu = mix(canal(a, 0), canal(b, 0))
        return (alpha shl 24) or (rouge shl 16) or (vert shl 8) or bleu
    }

    /**
     * Intensité de pluie visible, dérivée de la météo.
     *
     * Séparée du moteur météo parce qu'elle ne sert qu'au rendu : la
     * mécanique, elle, n'utilise que `pluieParHeure`.
     */
    enum class IntensitePluie(val gouttes: Int, val vitesse: Float) {
        AUCUNE(0, 0f),
        LEGERE(40, 1.0f),
        NORMALE(90, 1.4f),
        FORTE(160, 1.9f)
    }

    fun intensitePluie(meteo: WeatherEngine.Meteo): IntensitePluie = when (meteo) {
        WeatherEngine.Meteo.PLUIE -> IntensitePluie.NORMALE
        WeatherEngine.Meteo.ORAGE -> IntensitePluie.FORTE
        else -> IntensitePluie.AUCUNE
    }

    /**
     * Nombre de gouttes réellement dessinées.
     *
     * Le mode « animations réduites » du système coupe tout : c'est un réglage
     * d'accessibilité, pas une préférence esthétique. Certaines personnes ont
     * des nausées devant une pluie animée.
     */
    fun nombreGouttes(intensite: IntensitePluie, animationsReduites: Boolean): Int =
        if (animationsReduites) 0 else intensite.gouttes
}
