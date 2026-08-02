package com.sankailife.core.island.domain

import com.sankailife.core.garden.domain.MimoEngine
import com.sankailife.core.garden.domain.MimoMondeEngine
import kotlin.math.abs

/**
 * Où se tiennent les Mimos sur l'île.
 *
 * **Ce n'est pas une simulation, et l'écran ne doit jamais le laisser croire.**
 * Le travail des Mimos est reconstitué à l'ouverture depuis le temps écoulé
 * (voir [IslandMimoEngine]) ; rien ne tourne application fermée. Un Mimo posé
 * près d'une parcelle assoiffée n'est pas en train de l'arroser : il indique
 * qu'il y a là du travail et que c'est son métier.
 *
 * On aurait pu ne rien montrer. Mais des employés qui n'existent que dans une
 * liste, sur une île qu'on regarde, ne se comprennent pas — on ne sait plus ce
 * qu'on a acheté ni s'il sert à quelque chose. Les poser dit la vérité sur
 * l'état de l'île sans mentir sur la mécanique.
 *
 * L'équivalent du Jardin, [MimoMondeEngine], n'est pas réutilisable tel quel :
 * il raisonne sur les clés de l'ancienne grille d'expansion. Le vocabulaire —
 * les activités — l'est, et il l'est.
 */
object IslandMimoMondeEngine {

    /** Un employé, réduit à ce dont le placement a besoin. */
    data class Mimo(
        val id: Long,
        val nom: String,
        val type: MimoEngine.Type
    )

    /** Une parcelle possédée, vue par un Mimo. */
    data class Parcelle(
        val x: Int,
        val y: Int,
        val aSoif: Boolean = false,
        val prete: Boolean = false
    )

    /** Un Mimo posé sur l'île. */
    data class Place(
        val id: Long,
        val nom: String,
        val type: MimoEngine.Type,
        val activite: MimoMondeEngine.Activite,
        val x: Int,
        val y: Int,
        /** La parcelle qui justifie l'activité, ou `null` s'il n'a rien à faire. */
        val cible: Pair<Int, Int>? = null
    ) {
        val endormi: Boolean get() = activite == MimoMondeEngine.Activite.DORT

        /**
         * Ce métier n'a aucun effet sur l'île.
         *
         * L'île ne connaît ni caisses ni marchand ambulant : seuls l'arrosage et
         * la récolte y sont reconstitués. Un transporteur y est décoratif, et
         * l'affichage doit le dire plutôt que de le faire semblant occupé.
         */
        val sansEmploi: Boolean get() = type !in METIERS_ACTIFS
    }

    /**
     * Les métiers que l'île sait réellement faire travailler.
     *
     * Cette liste n'est pas une préférence : c'est le miroir exact de ce que
     * [IslandMimoEngine.planifier] applique. Les deux doivent bouger ensemble,
     * sinon l'écran promet un travail qui n'arrive jamais.
     */
    val METIERS_ACTIFS: Set<MimoEngine.Type> = setOf(
        MimoEngine.Type.ARROSEUR,
        MimoEngine.Type.RECOLTEUR
    )

    /** Jusqu'où on cherche une case libre avant d'abandonner. */
    private const val PORTEE_RECHERCHE = 12

    /**
     * Pose chaque Mimo sur l'île.
     *
     * @param accueillante case où un Mimo peut se tenir : de la terre dégagée,
     *   ni bâtie, ni boisée, ni cultivée. Se tenir sur une culture la
     *   masquerait, et c'est justement ce qu'on est venu regarder.
     * @param repli point de repli quand aucune parcelle n'est possédée — le
     *   ponton, en pratique. Une île sans culture doit quand même montrer ses
     *   employés quelque part.
     *
     * Déterministe : la même île rend toujours le même placement. Un Mimo qui
     * change de place à chaque ouverture paraît cassé.
     */
    fun placer(
        mimos: List<Mimo>,
        parcelles: List<Parcelle> = emptyList(),
        faitJour: Boolean = true,
        repli: Pair<Int, Int>? = null,
        accueillante: (Int, Int) -> Boolean
    ): List<Place> {
        if (mimos.isEmpty()) return emptyList()

        val foyer = foyer(parcelles, repli)
        val occupees = mutableSetOf<Pair<Int, Int>>()
        val cibleesDeja = mutableSetOf<Pair<Int, Int>>()

        // Ordre stable : par identifiant, donc par ordre d'embauche. Sans lui,
        // deux Mimos échangeraient leur place selon l'ordre de la requête.
        return mimos.sortedBy { it.id }.mapIndexed { rang, mimo ->
            val cible = if (faitJour) cible(mimo.type, parcelles, foyer, cibleesDeja) else null
            cible?.let { cibleesDeja += it }

            val activite = activite(mimo.type, cible, faitJour)

            // Un Mimo au travail se tient **à côté** de sa parcelle, jamais
            // dessus. Faute de place autour, il reste à sa station : mieux vaut
            // un Mimo mal placé qu'une culture cachée.
            val position = cible
                ?.let { caseLibre(it, occupees, accueillante, sauter = 0) }
                ?: caseLibre(foyer, occupees, accueillante, sauter = rang)
                ?: foyer

            occupees += position

            Place(
                id = mimo.id,
                nom = mimo.nom,
                type = mimo.type,
                activite = activite,
                x = position.first,
                y = position.second,
                cible = cible
            )
        }.sortedWith(compareBy({ it.y }, { it.x }))
        // Trié par profondeur pour le rendu : un Mimo du bas passe devant un
        // Mimo du haut, comme les arbres.
    }

    /** Centre de gravité du terrain cultivé, ou le point de repli. */
    private fun foyer(parcelles: List<Parcelle>, repli: Pair<Int, Int>?): Pair<Int, Int> = when {
        parcelles.isNotEmpty() ->
            (parcelles.sumOf { it.x } / parcelles.size) to (parcelles.sumOf { it.y } / parcelles.size)
        repli != null -> repli
        else -> 0 to 0
    }

    /** Ce qui justifie qu'un Mimo se déplace, s'il y a quelque chose. */
    private fun cible(
        type: MimoEngine.Type,
        parcelles: List<Parcelle>,
        foyer: Pair<Int, Int>,
        dejaPrises: Set<Pair<Int, Int>>
    ): Pair<Int, Int>? {
        val candidates = when (type) {
            MimoEngine.Type.ARROSEUR -> parcelles.filter { it.aSoif && !it.prete }
            MimoEngine.Type.RECOLTEUR -> parcelles.filter { it.prete }
            // Les autres métiers n'ont rien à faire ici : leur donner une cible
            // les montrerait affairés à une tâche que l'île n'applique pas.
            else -> emptyList()
        }
        return candidates
            .map { it.x to it.y }
            .filter { it !in dejaPrises }
            // Deux Mimos ne visent pas la même parcelle : chacun prend la
            // sienne, sinon ils s'empilent sur le premier travail venu.
            .minByOrNull { distance(it, foyer) }
    }

    private fun activite(
        type: MimoEngine.Type,
        cible: Pair<Int, Int>?,
        faitJour: Boolean
    ): MimoMondeEngine.Activite = when {
        !faitJour -> MimoMondeEngine.Activite.DORT
        cible == null -> MimoMondeEngine.Activite.OISIF
        type == MimoEngine.Type.ARROSEUR -> MimoMondeEngine.Activite.ARROSE
        type == MimoEngine.Type.RECOLTEUR -> MimoMondeEngine.Activite.RECOLTE
        else -> MimoMondeEngine.Activite.OISIF
    }

    private fun distance(a: Pair<Int, Int>, b: Pair<Int, Int>): Int =
        abs(a.first - b.first) + abs(a.second - b.second)

    /**
     * Première case accueillante et libre autour d'un point.
     *
     * Balayage en anneaux croissants, ce qui donne la plus proche disponible.
     * [sauter] laisse passer les premières trouvées : c'est ce qui répartit les
     * stations au lieu de les empiler autour du même carré d'herbe.
     */
    private fun caseLibre(
        centre: Pair<Int, Int>,
        occupees: Set<Pair<Int, Int>>,
        accueillante: (Int, Int) -> Boolean,
        sauter: Int
    ): Pair<Int, Int>? {
        var restant = sauter
        for (rayon in 0..PORTEE_RECHERCHE) {
            for (dy in -rayon..rayon) {
                for (dx in -rayon..rayon) {
                    // Seulement le contour de l'anneau : l'intérieur a déjà été
                    // visité au tour précédent.
                    if (rayon > 0 && abs(dx) != rayon && abs(dy) != rayon) continue
                    val c = (centre.first + dx) to (centre.second + dy)
                    if (c in occupees || !accueillante(c.first, c.second)) continue
                    if (restant > 0) { restant--; continue }
                    return c
                }
            }
        }
        return null
    }
}
