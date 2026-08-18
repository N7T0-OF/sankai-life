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

    const val NOMBRE_BOITES = 6

    /** Nombre de cartes proposées dans une session. Court exprès. */
    const val CARTES_PAR_SESSION = 20

    const val XP_PAR_CARTE = 2
    const val XP_SESSION_TERMINEE = 25
    const val PIECES_SESSION_TERMINEE = 15

    /**
     * Nature d'une session de cartes.
     *
     * Une révision d'échéances fait progresser l'économie. « Mes erreurs » est
     * un entraînement libre : il reste rejouable pour apprendre, mais ne doit
     * jamais devenir une source de récompenses entre deux sessions.
     */
    enum class ModeSession {
        REVISION_ECHEANCES,
        ENTRAINEMENT_ERREURS
    }

    data class RecompenseSession(
        val xpParCarte: Int,
        val xpFin: Int,
        val piecesFin: Int
    )

    /** Politique économique centrale, indépendante de l'écran. */
    fun recompense(mode: ModeSession): RecompenseSession = when (mode) {
        ModeSession.REVISION_ECHEANCES -> RecompenseSession(
            xpParCarte = XP_PAR_CARTE,
            xpFin = XP_SESSION_TERMINEE,
            piecesFin = PIECES_SESSION_TERMINEE
        )
        ModeSession.ENTRAINEMENT_ERREURS -> RecompenseSession(
            xpParCarte = 0,
            xpFin = 0,
            piecesFin = 0
        )
    }

    /** Séparateurs acceptés entre le recto et le verso d'une ligne. */
    private val SEPARATEURS = listOf(" :: ", "::", " — ", " | ", "|")

    /**
     * Intervalle avant la prochaine révision, par boîte.
     * 10 min → 1 j → 3 j → 7 j → 14 j → 30 j : la progression lente est ce
     * qui fait tenir une carte en mémoire, chaque palier allongeant la
     * distance avant le rappel suivant.
     */
    private val INTERVALLES_MINUTES = longArrayOf(
        10,
        TimeUnit.DAYS.toMinutes(1),
        TimeUnit.DAYS.toMinutes(3),
        TimeUnit.DAYS.toMinutes(7),
        TimeUnit.DAYS.toMinutes(14),
        TimeUnit.DAYS.toMinutes(30)
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
        val box: Int,
        /**
         * Langue du recto, au format BCP-47. Vide si le module n'en déclare
         * aucune — une session « Mes erreurs » mélange plusieurs modules, donc
         * la langue se porte par carte et non par session.
         */
        val langue: String = "",
        /** Module source, utilisé pour ne jamais fabriquer de leurres hors sujet. */
        val moduleId: Long = 0L
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
     * Jugement porté sur une carte, du plus dur au plus facile.
     *
     * Le glissement de l'écran s'y traduit directement. Ces quatre valeurs
     * changent réellement l'intervalle, la boîte et la date de révision — un
     * geste qui ne ferait que jouer une animation serait un mensonge poli.
     */
    enum class Jugement(val libelle: String) {
        A_REVOIR("À revoir"),
        DIFFICILE("Difficile"),
        CORRECT("Correct"),
        FACILE("Facile");

        val reussi: Boolean get() = this != A_REVOIR
    }

    /**
     * Nouvelle boîte après une réponse.
     * Une erreur renvoie à zéro : c'est ce qui fait remonter rapidement les
     * cartes mal acquises au lieu de les diluer.
     */
    fun boiteSuivante(boiteActuelle: Int, reussi: Boolean): Int =
        if (reussi) (boiteActuelle + 1).coerceAtMost(NOMBRE_BOITES - 1) else 0

    /**
     * Nouvelle boîte selon le jugement porté.
     *
     * « Difficile » ne renvoie pas à zéro mais recule d'un cran : une carte
     * qu'on retrouve péniblement n'est pas une carte oubliée, et la renvoyer au
     * début effacerait tout le travail déjà fait dessus.
     *
     * « Facile » saute une boîte : revoir dans trois jours ce qu'on connaît
     * déjà est du temps pris aux cartes qui en ont besoin.
     */
    fun boiteSuivante(boiteActuelle: Int, jugement: Jugement): Int = when (jugement) {
        Jugement.A_REVOIR -> 0
        Jugement.DIFFICILE -> (boiteActuelle - 1).coerceAtLeast(0)
        Jugement.CORRECT -> (boiteActuelle + 1).coerceAtMost(NOMBRE_BOITES - 1)
        Jugement.FACILE -> (boiteActuelle + 2).coerceAtMost(NOMBRE_BOITES - 1)
    }

    /**
     * Prochaine révision selon le jugement.
     *
     * « À revoir » revient dans la même session, pas dans dix minutes : c'est
     * la seule façon de retravailler une carte tant qu'elle est fraîche.
     */
    fun prochaineRevision(
        boite: Int,
        jugement: Jugement,
        maintenantMillis: Long = System.currentTimeMillis()
    ): Long = when (jugement) {
        Jugement.A_REVOIR -> maintenantMillis + TimeUnit.MINUTES.toMillis(1)
        else -> prochaineRevision(boite, maintenantMillis)
    }

    /** Instant de la prochaine révision pour une boîte donnée. */
    fun prochaineRevision(boite: Int, maintenantMillis: Long = System.currentTimeMillis()): Long {
        val index = boite.coerceIn(0, INTERVALLES_MINUTES.lastIndex)
        return maintenantMillis + TimeUnit.MINUTES.toMillis(INTERVALLES_MINUTES[index])
    }

    /**
     * Libellé lisible de l'échéance, pour l'écran de révision.
     *
     * La dernière boîte — la maîtrise — espace la révision d'un mois : c'est
     * l'échéance la plus lointaine que Sankai propose, et elle reste
     * révisable si la carte revient échue.
     */
    fun libelleIntervalle(boite: Int): String = when (boite.coerceIn(0, NOMBRE_BOITES - 1)) {
        0 -> "dans 10 min"
        1 -> "demain"
        2 -> "dans 3 jours"
        3 -> "dans 1 semaine"
        4 -> "dans 2 semaines"
        else -> "dans 1 mois"
    }

    /** Progression d'un module : part des cartes en boîte haute. */
    fun tauxMaitrise(boites: List<Int>): Float {
        if (boites.isEmpty()) return 0f
        val total = boites.sumOf { it.coerceIn(0, NOMBRE_BOITES - 1) }
        return total.toFloat() / (boites.size * (NOMBRE_BOITES - 1))
    }
}
