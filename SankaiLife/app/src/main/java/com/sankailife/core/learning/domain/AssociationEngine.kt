package com.sankailife.core.learning.domain

import kotlin.random.Random

/**
 * L'exercice d'association en deux colonnes.
 *
 * Il diffère de tous les autres sur un point qui a des conséquences partout :
 * **il porte plusieurs cartes à la fois**. Les autres exercices posent une
 * question sur une carte et la corrigent ; celui-ci en montre quatre et
 * n'est fini que lorsque les quatre sont appariées.
 *
 * La conséquence, c'est que chaque paire mérite son propre verdict. Ne juger
 * que la carte « principale » gaspillerait trois quarts de ce que l'exercice
 * vient de mesurer, et une carte appariée du premier coup n'est pas dans le
 * même état qu'une carte trouvée par élimination après trois erreurs.
 *
 * L'état est immuable : chaque geste rend un nouvel état. Une machine à états
 * qui se modifie en place finit toujours par se désynchroniser de l'écran.
 */
object AssociationEngine {

    /**
     * Paires affichées simultanément.
     *
     * Quatre. Trois se résout par élimination dès la deuxième réponse — on
     * apparie sans rien savoir. Au-delà de cinq, les deux colonnes ne tiennent
     * plus sur un téléphone sans faire défiler, et un exercice où il faut
     * chercher la moitié des réponses hors de l'écran teste le défilement.
     */
    const val PAIRES = 4

    /** En dessous, l'exercice ne peut pas être proposé honnêtement. */
    const val PAIRES_MIN = 3

    /** Une paire à retrouver. */
    data class Paire(
        val carteId: Long,
        val gauche: String,
        val droite: String
    )

    /** Un élément de colonne. */
    data class Element(
        val carteId: Long,
        val texte: String,
        val gauche: Boolean
    )

    /**
     * L'état de l'exercice.
     *
     * [selection] est l'élément touché en attente de son pendant. Il n'y en a
     * qu'un : le deuxième toucher valide ou invalide immédiatement, ce qui
     * évite d'avoir à confirmer une paire évidente.
     */
    data class Etat(
        val colonneGauche: List<Element>,
        val colonneDroite: List<Element>,
        val selection: Element? = null,
        /** Cartes déjà appariées, retirées visuellement. */
        val trouvees: Set<Long> = emptySet(),
        /** Cartes ratées au moins une fois : elles ne comptent plus comme sues. */
        val fautives: Set<Long> = emptySet(),
        /** Dernière paire refusée, pour un retour visuel bref. */
        val derniereErreur: Pair<Long, Long>? = null
    ) {
        val termine: Boolean get() = trouvees.size >= colonneGauche.size

        /** Cartes appariées du premier coup, donc réellement sues. */
        val reussies: Set<Long> get() = trouvees - fautives

        fun estTrouvee(element: Element): Boolean = element.carteId in trouvees
    }

    /**
     * Prépare l'exercice.
     *
     * Les deux colonnes sont mélangées **indépendamment** : mélanger une seule
     * fois et afficher l'autre dans le même ordre donnerait les réponses en
     * diagonale.
     *
     * @return `null` si le nombre de paires est insuffisant. Le dire plutôt que
     *   d'afficher un exercice dégradé à deux lignes, qui se résout tout seul.
     */
    fun preparer(paires: List<Paire>, alea: Random = Random.Default): Etat? {
        val utiles = paires
            .filter { it.gauche.isNotBlank() && it.droite.isNotBlank() }
            .distinctBy { it.carteId }
            .take(PAIRES)
        if (utiles.size < PAIRES_MIN) return null

        return Etat(
            colonneGauche = utiles.map { Element(it.carteId, it.gauche, gauche = true) }
                .shuffled(alea),
            colonneDroite = utiles.map { Element(it.carteId, it.droite, gauche = false) }
                .shuffled(alea)
        )
    }

    /**
     * Traite un toucher.
     *
     * Les règles sont volontairement permissives : on peut commencer par la
     * colonne de droite, et retoucher un élément déjà choisi pour l'abandonner.
     * Un exercice qui impose un ordre de lecture ajoute une difficulté qui n'a
     * rien à voir avec ce qu'il prétend mesurer.
     */
    fun toucher(etat: Etat, element: Element): Etat {
        if (etat.termine || element.carteId in etat.trouvees) return etat

        val choisi = etat.selection
        // Retoucher le même élément l'abandonne.
        if (choisi != null && choisi.carteId == element.carteId &&
            choisi.gauche == element.gauche
        ) {
            return etat.copy(selection = null, derniereErreur = null)
        }

        // Deux éléments de la même colonne : on remplace la sélection plutôt
        // que de refuser. Refuser un geste sans le dire donne l'impression que
        // l'écran ne répond pas.
        if (choisi == null || choisi.gauche == element.gauche) {
            return etat.copy(selection = element, derniereErreur = null)
        }

        return if (choisi.carteId == element.carteId) {
            etat.copy(
                selection = null,
                trouvees = etat.trouvees + element.carteId,
                derniereErreur = null
            )
        } else {
            // Les deux cartes concernées sont marquées : celle qu'on cherchait
            // et celle qu'on a prise à sa place. Confondre deux mots salit les
            // deux, et l'une comme l'autre méritent de revenir.
            etat.copy(
                selection = null,
                fautives = etat.fautives + choisi.carteId + element.carteId,
                derniereErreur = choisi.carteId to element.carteId
            )
        }
    }

    /**
     * Verdict par carte, une fois l'exercice fini.
     *
     * `true` signifie « appariée sans jamais s'être trompé dessus ». Une carte
     * trouvée après une confusion reste fausse : elle a fini par sortir par
     * élimination, ce qui ne prouve rien.
     */
    fun verdicts(etat: Etat): Map<Long, Boolean> =
        etat.colonneGauche.associate { it.carteId to (it.carteId in etat.reussies) }

    /** Ce qu'on affiche en fin d'exercice. */
    fun resume(etat: Etat): String {
        val total = etat.colonneGauche.size
        val justes = etat.reussies.size
        return when {
            total == 0 -> ""
            justes == total -> "Toutes les paires du premier coup"
            justes == 0 -> "Ces mots se ressemblent : ils reviendront"
            else -> "$justes paire(s) sur $total du premier coup"
        }
    }
}
