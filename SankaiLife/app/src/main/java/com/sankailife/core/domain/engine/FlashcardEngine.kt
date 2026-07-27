package com.sankailife.core.domain.engine

import java.util.concurrent.TimeUnit

/**
 * Répétition espacée des lignes de mémo transformées en cartes.
 *
 * Système de Leitner : chaque carte occupe une boîte. Une bonne réponse la
 * fait monter d'une boîte et espace la révision suivante ; une mauvaise la
 * renvoie en boîte 0. C'est volontairement plus simple que SM-2 — sans note
 * de difficulté à saisir, l'utilisateur ne répond qu'à « je savais » ou
 * « à revoir », ce qui reste utilisable en dix secondes dans les transports.
 */
object FlashcardEngine {

    const val NOMBRE_BOITES = 5

    /** Nombre de cartes proposées dans une session. Court exprès. */
    const val CARTES_PAR_SESSION = 20

    const val XP_PAR_CARTE = 2
    const val XP_SESSION_TERMINEE = 25
    const val PIECES_SESSION_TERMINEE = 15

    /** Séparateurs acceptés entre le recto et le verso d'une ligne. */
    private val SEPARATEURS = listOf(" :: ", "::", " — ", " | ", "|")

    /**
     * Intervalle avant la prochaine révision, par boîte.
     * 10 min → 1 j → 3 j → 7 j → 21 j.
     */
    private val INTERVALLES_MINUTES = longArrayOf(
        10,
        TimeUnit.DAYS.toMinutes(1),
        TimeUnit.DAYS.toMinutes(3),
        TimeUnit.DAYS.toMinutes(7),
        TimeUnit.DAYS.toMinutes(21)
    )

    /**
     * Une carte à afficher.
     *
     * [verso] est null quand la ligne ne contient pas de séparateur : la carte
     * est alors simplement une phrase à se remémorer, sans réponse cachée.
     */
    data class Carte(
        val id: Long,
        val recto: String,
        val verso: String?,
        val box: Int
    ) {
        val aDeuxFaces: Boolean get() = verso != null
    }

    /**
     * Découpe une ligne en recto / verso.
     *
     * « Capitale du Japon :: Tokyo » donne deux faces.
     * « Continue même si c'est lent » n'en donne qu'une.
     */
    fun decouper(texte: String): Pair<String, String?> {
        for (separateur in SEPARATEURS) {
            val index = texte.indexOf(separateur)
            if (index > 0) {
                val recto = texte.substring(0, index).trim()
                val verso = texte.substring(index + separateur.length).trim()
                if (recto.isNotEmpty() && verso.isNotEmpty()) return recto to verso
            }
        }
        return texte.trim() to null
    }

    /**
     * Nouvelle boîte après une réponse.
     * Une erreur renvoie à zéro : c'est ce qui fait remonter rapidement les
     * cartes mal acquises au lieu de les diluer.
     */
    fun boiteSuivante(boiteActuelle: Int, reussi: Boolean): Int =
        if (reussi) (boiteActuelle + 1).coerceAtMost(NOMBRE_BOITES - 1) else 0

    /** Instant de la prochaine révision pour une boîte donnée. */
    fun prochaineRevision(boite: Int, maintenantMillis: Long = System.currentTimeMillis()): Long {
        val index = boite.coerceIn(0, INTERVALLES_MINUTES.lastIndex)
        return maintenantMillis + TimeUnit.MINUTES.toMillis(INTERVALLES_MINUTES[index])
    }

    /** Libellé lisible de l'échéance, pour l'écran de révision. */
    fun libelleIntervalle(boite: Int): String = when (boite.coerceIn(0, NOMBRE_BOITES - 1)) {
        0 -> "dans 10 min"
        1 -> "demain"
        2 -> "dans 3 jours"
        3 -> "dans 1 semaine"
        else -> "dans 3 semaines"
    }

    /** Progression d'un module : part des cartes en boîte haute. */
    fun tauxMaitrise(boites: List<Int>): Float {
        if (boites.isEmpty()) return 0f
        val total = boites.sumOf { it.coerceIn(0, NOMBRE_BOITES - 1) }
        return total.toFloat() / (boites.size * (NOMBRE_BOITES - 1))
    }
}
