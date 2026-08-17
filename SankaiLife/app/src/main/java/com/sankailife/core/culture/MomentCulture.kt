package com.sankailife.core.culture

/**
 * Le moment de la journée, pour la découverte contextuelle.
 *
 * L'idée : le contenu du jour suit l'heure — un mot ou un proverbe le matin,
 * une connaissance la journée, une poésie ou une pensée le soir. C'est une
 * **préférence douce, jamais un filtre** : si le moment préféré n'a aucune
 * capsule disponible, la sélection retombe sur tout le catalogue, pour que
 * « une découverte par jour » reste garantie à toute heure.
 */
enum class MomentDuJour { MATIN, JOURNEE, SOIR }

object MomentCulture {

    /** Moment associé à une heure locale (0-23). */
    fun moment(hour: Int): MomentDuJour = when {
        hour in 5..11 -> MomentDuJour.MATIN
        hour in 12..17 -> MomentDuJour.JOURNEE
        else -> MomentDuJour.SOIR
    }

    /** Types préférés pour ce moment, utilisés en préférence douce. */
    fun typesPreferees(moment: MomentDuJour): Set<CultureEntryType> = when (moment) {
        MomentDuJour.MATIN -> setOf(CultureEntryType.WORD, CultureEntryType.PROVERB)
        MomentDuJour.JOURNEE -> setOf(
            CultureEntryType.HISTORY,
            CultureEntryType.SCIENCE,
            CultureEntryType.ARTWORK,
            CultureEntryType.BIOGRAPHY
        )
        MomentDuJour.SOIR -> setOf(CultureEntryType.POEM, CultureEntryType.QUOTE)
    }
}
