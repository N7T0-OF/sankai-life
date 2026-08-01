package com.sankailife.core.domain.engine

/**
 * Les cartes qui résistent.
 *
 * Une carte ratée revient plus vite grâce à la répétition espacée, mais elle
 * revient **noyée** parmi les autres. Celle qu'on rate depuis trois semaines
 * est mélangée à celles qu'on connaît, et la session ne dit jamais où se
 * trouve le vrai problème.
 *
 * Ce moteur les rassemble. Il ne remplace pas la répétition espacée — il en
 * extrait ce qui mérite une attention délibérée.
 */
object ErreursEngine {

    /**
     * Nombre minimum de révisions avant de juger une carte difficile.
     *
     * Rater une carte vue une seule fois n'apprend rien : c'est le cas normal
     * d'une carte neuve. Sans ce seuil, « Mes erreurs » se remplirait de
     * cartes qu'on vient de créer et n'aurait plus aucun sens.
     */
    const val REVISIONS_MINIMUM = 3

    /** Au-delà de ce taux d'échec, une carte est considérée comme difficile. */
    const val SEUIL_ECHEC = 0.34f

    /** Taille d'une session ciblée. Courte : c'est un rattrapage, pas un examen. */
    const val CARTES_PAR_SESSION = 12

    /** Ce qu'on sait d'une carte pour la juger. */
    data class Historique(
        val id: Long,
        val texte: String,
        val boite: Int,
        val revisions: Int,
        val reussites: Int
    ) {
        val echecs: Int get() = (revisions - reussites).coerceAtLeast(0)

        val tauxEchec: Float
            get() = if (revisions <= 0) 0f else echecs.toFloat() / revisions
    }

    /**
     * Cette carte pose-t-elle vraiment problème ?
     *
     * Deux conditions, et les deux comptent : assez de tentatives pour que le
     * taux veuille dire quelque chose, et un taux réellement mauvais.
     */
    fun estDifficile(h: Historique): Boolean =
        h.revisions >= REVISIONS_MINIMUM && h.tauxEchec >= SEUIL_ECHEC

    /**
     * Score de priorité. Plus il est haut, plus la carte mérite du travail.
     *
     * Le taux d'échec domine, mais le nombre absolu d'échecs départage : entre
     * deux cartes ratées une fois sur deux, celle qu'on a ratée dix fois pèse
     * plus lourd que celle ratée deux fois.
     *
     * La boîte basse ajoute un peu : une carte qui n'est jamais montée est
     * plus inquiétante qu'une carte redescendue une fois.
     */
    fun priorite(h: Historique): Float {
        if (!estDifficile(h)) return 0f
        val volume = minOf(h.echecs, 10) / 10f
        val boitesUtiles = (FlashcardEngine.NOMBRE_BOITES - 1).coerceAtLeast(1)
        val fraicheur =
            (boitesUtiles - h.boite).coerceIn(0, boitesUtiles).toFloat() / boitesUtiles
        return h.tauxEchec * 2f + volume + fraicheur * 0.5f
    }

    /**
     * Les cartes à retravailler, les plus problématiques d'abord.
     *
     * La liste est bornée : proposer cent cartes ratées d'un coup ne serait pas
     * une aide, ce serait un constat d'échec.
     */
    fun selectionner(
        historiques: List<Historique>,
        limite: Int = CARTES_PAR_SESSION
    ): List<Historique> = historiques
        .filter { estDifficile(it) }
        .sortedByDescending { priorite(it) }
        .take(limite)

    /**
     * Phrase résumant l'état, ou null s'il n'y a rien à signaler.
     *
     * Le silence est volontaire : afficher « 0 erreur » à quelqu'un qui débute
     * lui ferait croire qu'il a déjà tout révisé.
     */
    fun resume(nombre: Int): String? = when {
        nombre <= 0 -> null
        nombre == 1 -> "1 carte te résiste."
        else -> "$nombre cartes te résistent."
    }
}
