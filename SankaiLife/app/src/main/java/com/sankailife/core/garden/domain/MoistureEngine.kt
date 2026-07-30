package com.sankailife.core.garden.domain

/**
 * L'humidité du sol.
 *
 * L'arrosage était un clic qui remettait un compteur à zéro. Il verse
 * maintenant une quantité dans un sol qui la reperd progressivement, à une
 * vitesse qui dépend du terrain et de l'heure. C'est ce qui donne un sens au
 * geste : arroser au bon moment vaut mieux qu'arroser souvent.
 *
 * Règle qui ne bouge pas depuis la première version : **aucune plante ne
 * meurt**. Le pire cas est une plante lente. Punir un oubli de trois jours
 * dans une application censée aider à tenir ses habitudes serait une faute de
 * conception, pas une difficulté.
 */
object MoistureEngine {

    /** Les paliers lisibles, dérivés d'une valeur continue 0 → 1. */
    enum class Etat(val libelle: String, val emoji: String) {
        SEC("Sec", "🏜️"),
        LEGEREMENT_SEC("Un peu sec", "🌤️"),
        HUMIDE("Humide", "💧"),
        BIEN_ARROSE("Bien arrosé", "💦"),
        DETREMPE("Détrempé", "🌊")
    }

    fun etat(humidite: Float): Etat = when {
        humidite < 0.15f -> Etat.SEC
        humidite < 0.35f -> Etat.LEGEREMENT_SEC
        humidite < 0.70f -> Etat.HUMIDE
        humidite <= 0.92f -> Etat.BIEN_ARROSE
        else -> Etat.DETREMPE
    }

    /**
     * Multiplicateur de croissance selon l'humidité.
     *
     * Le plancher est à 0,6 : un sol oublié ralentit nettement mais continue.
     * Le sommet est à 1,1 — un bonus modeste, pour que bien arroser soit
     * gratifiant sans rendre l'arrosage obligatoire.
     */
    fun facteurCroissance(humidite: Float, seed: Seed): Float {
        val etat = etat(humidite)
        // Une espèce qui aime le sec inverse les extrêmes : le cactus souffre
        // dans une terre gorgée d'eau, pas dans une terre sèche.
        if (seed.besoinEau == BesoinEau.FAIBLE) {
            return when (etat) {
                Etat.SEC -> 0.95f
                Etat.LEGEREMENT_SEC -> 1.10f
                Etat.HUMIDE -> 1.0f
                Etat.BIEN_ARROSE -> 0.85f
                Etat.DETREMPE -> 0.70f
            }
        }
        return when (etat) {
            Etat.SEC -> 0.60f
            Etat.LEGEREMENT_SEC -> 0.85f
            Etat.HUMIDE -> 1.0f
            Etat.BIEN_ARROSE -> 1.10f
            Etat.DETREMPE -> 0.85f
        }
    }

    /** Quantité versée par une unité d'eau. */
    const val APPORT_PAR_ARROSAGE = 0.45f

    /** Perte d'humidité par heure, sur un sol de référence en plein jour. */
    private const val EVAPORATION_HORAIRE = 0.055f

    /**
     * Humidité après un temps écoulé.
     *
     * L'évaporation dépend du sol — le sable ne retient rien, la terre riche
     * garde l'eau — et de l'heure : la nuit sèche deux fois moins vite. C'est
     * ce qui rend l'arrosage du soir plus rentable que celui de midi, sans
     * qu'on ait besoin de l'expliquer.
     */
    fun apresEcoulement(
        humidite: Float,
        minutes: Long,
        sol: SoilType,
        partNocturne: Float = 0f
    ): Float {
        if (minutes <= 0) return humidite.coerceIn(0f, 1f)

        val facteurNuit = 1f - 0.5f * partNocturne.coerceIn(0f, 1f)
        val perte = EVAPORATION_HORAIRE * (minutes / 60f) * retentionInverse(sol) * facteurNuit
        return (humidite - perte).coerceIn(0f, 1f)
    }

    /** Plus la valeur est haute, plus le sol sèche vite. */
    private fun retentionInverse(sol: SoilType): Float = when (sol) {
        SoilType.SABLE -> 1.60f
        SoilType.TERRE -> 1.00f
        SoilType.RICHE -> 0.70f
        SoilType.HUMIDE -> 0.45f
        SoilType.NOCTURNE -> 0.80f
        SoilType.CRISTALLIN -> 0.85f
    }

    fun apresArrosage(humidite: Float): Float =
        (humidite + APPORT_PAR_ARROSAGE).coerceIn(0f, 1f)

    /**
     * Heures avant que le sol ne devienne sec.
     * Sert à annoncer le prochain besoin plutôt qu'à laisser deviner.
     */
    fun heuresAvantSecheresse(humidite: Float, sol: SoilType): Float {
        val marge = humidite - 0.15f
        if (marge <= 0f) return 0f
        return marge / (EVAPORATION_HORAIRE * retentionInverse(sol))
    }

    /**
     * Faut-il arroser cette parcelle ?
     *
     * Utilisé par l'arrosage assisté et par les Mimos : arroser un sol déjà
     * gorgé gaspille de l'eau et, pour un cactus, nuit à la plante.
     */
    fun aBesoinDEau(humidite: Float, seed: Seed?): Boolean {
        val seuil = when (seed?.besoinEau) {
            BesoinEau.FAIBLE -> 0.20f
            BesoinEau.FORT -> 0.65f
            else -> 0.45f
        }
        return humidite < seuil
    }

    /** Couleur du sol, du brun clair poussiéreux au brun sombre. */
    fun teinteSol(humidite: Float): Long = when (etat(humidite)) {
        Etat.SEC -> 0xFF8A6A45
        Etat.LEGEREMENT_SEC -> 0xFF6F5335
        Etat.HUMIDE -> 0xFF573F28
        Etat.BIEN_ARROSE -> 0xFF3E2C1B
        Etat.DETREMPE -> 0xFF2A2015
    }
}
