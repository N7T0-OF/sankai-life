package com.sankailife.core.domain.engine

/**
 * Lecture honnête de l'état de la mémorisation.
 *
 * Les nombres bruts — cartes, révisions, réussites — ne disent rien tant qu'on
 * ne précise pas ce qu'ils recouvrent. Une réussite sur une révision fait un
 * taux de 100 %, et l'afficher tel quel encouragerait quelqu'un qui n'a encore
 * rien appris. Ce moteur décide de ce qui peut être affirmé.
 */
object MemorisationEngine {

    /**
     * Une carte n'est « maîtrisée » qu'arrivée dans la dernière boîte.
     *
     * Les boîtes sont numérotées de 0 à NOMBRE_BOITES − 1 : la dernière est
     * donc `NOMBRE_BOITES - 1`, pas `NOMBRE_BOITES`.
     */
    val BOITE_MAITRISEE = FlashcardEngine.NOMBRE_BOITES - 1

    /**
     * En dessous de ce nombre de révisions, aucun taux n'est publié.
     *
     * Vingt réponses ne font pas une mesure exacte, mais elles suffisent à ce
     * qu'un pourcentage ne change plus du simple au double à chaque carte.
     */
    const val REVISIONS_POUR_UN_TAUX = 20

    data class Etat(
        val total: Int = 0,
        val maitrisees: Int = 0,
        val dues: Int = 0,
        val revisions: Int = 0,
        val reussites: Int = 0,
        /** Cartes vues au moins une fois, quel que soit le résultat. */
        val entamees: Int = 0
    ) {
        /** Cartes créées mais jamais présentées. */
        val jamaisVues: Int get() = (total - entamees).coerceAtLeast(0)
    }

    /**
     * Part des cartes arrivées en dernière boîte, entre 0 et 1.
     *
     * Renvoie 0 sur une base vide plutôt que de diviser par zéro — et 0 est ici
     * la vérité : personne n'a rien maîtrisé.
     */
    fun partMaitrisee(total: Int, maitrisees: Int): Float =
        if (total <= 0) 0f else (maitrisees.toFloat() / total).coerceIn(0f, 1f)

    /**
     * Taux de bonnes réponses, ou `null` tant qu'il ne veut rien dire.
     *
     * Le `null` est le point important : il vaut mieux ne rien afficher que
     * d'afficher « 100 % » à quelqu'un qui a répondu juste à sa première carte.
     */
    fun tauxReussite(revisions: Int, reussites: Int): Float? {
        if (revisions < REVISIONS_POUR_UN_TAUX) return null
        return (reussites.toFloat() / revisions).coerceIn(0f, 1f)
    }

    /** Formatage en pourcentage entier, sans virgule trompeuse. */
    fun pourcentage(part: Float): String = "${(part.coerceIn(0f, 1f) * 100).toInt()} %"

    /**
     * Phrase qui résume l'état, adaptée au stade où l'on se trouve.
     *
     * Un débutant et quelqu'un qui a tout maîtrisé n'ont pas besoin du même
     * message : le premier a besoin de savoir par où commencer, le second de
     * savoir qu'il n'a rien à faire aujourd'hui.
     */
    fun resume(etat: Etat): String = when {
        etat.total == 0 -> "Aucune phrase enregistrée pour l'instant."
        etat.revisions == 0 -> "${etat.total} phrases prêtes, aucune encore révisée."
        etat.dues > 0 -> "${etat.dues} carte(s) à réviser aujourd'hui."
        etat.maitrisees == etat.total -> "Tout est en dernière boîte. Rien à réviser aujourd'hui."
        else -> "Rien à réviser dans l'immédiat — reviens plus tard."
    }
}
