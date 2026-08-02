package com.sankailife.core.learning.domain

/**
 * Le parcours d'un module : ses unités, et où en est l'apprenant.
 *
 * **Le point de départ de toute la refonte est ici, et il est contraint par
 * une réalité :** l'application ne contient aucun cours. Il n'y a pas de
 * portugais A1 écrit, pas de chapitre rédigé, pas de dialogue enregistré.
 * Créer une hiérarchie vide et l'afficher promettrait un parcours et montrerait
 * zéro leçon.
 *
 * Le seul contenu qui existe vraiment est **celui de l'utilisateur** : ses
 * profils Mémo et leurs lignes. Ce moteur en dérive donc un parcours quand
 * aucune structure n'est déclarée, et respecte la structure quand elle existe.
 * Un module importé plus tard, avec ses vrais chapitres, passera par le même
 * chemin sans que rien ne change ici.
 *
 * Aucune donnée n'est copiée : une unité ne contient pas de cartes, elle
 * désigne des cartes. Modifier une ligne dans l'éditeur Mémo modifie la carte
 * du parcours, parce que c'est la même.
 */
object AcademieEngine {

    /**
     * Cartes par unité, quand la structure est dérivée.
     *
     * Assez pour qu'une unité représente un vrai apprentissage, assez peu pour
     * tenir dans une session courte. Sept est la limite basse de ce qu'on
     * retient d'un bloc, et douze la limite haute de ce qu'on avale sans
     * fatigue ; dix est un compromis, pas une découverte.
     */
    const val CARTES_PAR_UNITE = 10

    /** Unités par chapitre, quand la structure est dérivée. */
    const val UNITES_PAR_CHAPITRE = 3

    /** Une carte du module, réduite à ce dont le parcours a besoin. */
    data class Carte(
        val id: Long,
        /** Rang dans le module, qui donne l'ordre d'apprentissage. */
        val ordre: Int,
        /** Boîte de Leitner, de 0 (fragile) à 4 (acquise). */
        val boite: Int = 0,
        /** Nombre de révisions déjà faites. */
        val revisions: Int = 0,
        /**
         * Unité déclarée par le contenu, ou `null` si le module n'en déclare
         * aucune. C'est ce champ qui distingue un vrai cours d'un deck.
         */
        val uniteDeclaree: String? = null
    )

    /** Une unité du parcours. */
    data class Unite(
        val id: String,
        val titre: String,
        val chapitre: Int,
        /** Rang de l'unité dans le module, à partir de 0. */
        val rang: Int,
        val cartes: List<Long>
    ) {
        val taille: Int get() = cartes.size
    }

    /**
     * État d'un nœud du parcours.
     *
     * `DISPONIBLE` et `ACTUELLE` sont distincts : plusieurs unités peuvent être
     * ouvertes, une seule est celle qu'on propose de continuer. Sans cette
     * distinction, l'écran ne saurait pas quoi mettre derrière « Continuer ».
     */
    enum class Etat { TERMINEE, ACTUELLE, DISPONIBLE, VERROUILLEE, REVISION }

    /** Une unité, avec son état. */
    data class Noeud(
        val unite: Unite,
        val etat: Etat,
        /** Part de cartes maîtrisées, de 0 à 1. */
        val progression: Float
    )

    /**
     * Découpe un module en unités.
     *
     * Deux cas, et un seul code pour les deux :
     *
     * le module **déclare** ses unités — un cours importé, un module créé avec
     * ses chapitres : on les respecte, dans l'ordre d'apparition ;
     *
     * le module ne déclare rien — un deck de vocabulaire, un profil Mémo
     * existant : on découpe par paquets de [CARTES_PAR_UNITE], dans l'ordre du
     * contenu. L'ordre n'est pas arbitraire : c'est celui dans lequel
     * l'utilisateur a écrit ses lignes, donc généralement celui de sa
     * progression.
     *
     * Une unité vide n'est jamais produite : un nœud sans carte est un nœud sur
     * lequel on clique pour rien.
     */
    fun decouper(cartes: List<Carte>, titreModule: String = ""): List<Unite> {
        if (cartes.isEmpty()) return emptyList()
        val ordonnees = cartes.sortedBy { it.ordre }

        val declarees = ordonnees.mapNotNull { it.uniteDeclaree }.distinct()
        return if (declarees.isNotEmpty()) {
            // Structure déclarée. Les cartes sans unité déclarée dans un module
            // qui en déclare sont rassemblées à la fin plutôt qu'ignorées :
            // perdre du contenu parce qu'il est mal étiqueté serait pire que
            // l'afficher en vrac.
            val orphelines = ordonnees.filter { it.uniteDeclaree == null }
            val groupes = declarees.mapIndexed { rang, nom ->
                Unite(
                    id = "u$rang",
                    titre = nom,
                    chapitre = rang / UNITES_PAR_CHAPITRE,
                    rang = rang,
                    cartes = ordonnees.filter { it.uniteDeclaree == nom }.map { it.id }
                )
            }
            if (orphelines.isEmpty()) groupes else groupes + Unite(
                id = "u${declarees.size}",
                titre = "À classer",
                chapitre = declarees.size / UNITES_PAR_CHAPITRE,
                rang = declarees.size,
                cartes = orphelines.map { it.id }
            )
        } else {
            ordonnees.chunked(CARTES_PAR_UNITE).mapIndexed { rang, paquet ->
                Unite(
                    id = "u$rang",
                    titre = titreUnite(titreModule, rang),
                    chapitre = rang / UNITES_PAR_CHAPITRE,
                    rang = rang,
                    cartes = paquet.map { it.id }
                )
            }
        }
    }

    /**
     * Titre d'une unité dérivée.
     *
     * Numéroté, et rien de plus. Inventer « Salutations » à partir de cartes
     * qu'on n'a pas lues produirait un titre faux une fois sur deux, et un
     * titre faux est pire qu'un titre neutre.
     */
    private fun titreUnite(titreModule: String, rang: Int): String =
        if (titreModule.isBlank()) "Unité ${rang + 1}" else "$titreModule ${rang + 1}"

    /**
     * Seuil de maîtrise d'une carte.
     *
     * Boîte 2 = revue avec succès trois fois, prochain rappel à trois jours.
     * En dessous, la carte est encore fragile et l'unité n'est pas acquise.
     */
    const val BOITE_MAITRISEE = 2

    /**
     * Part de l'unité qu'il faut maîtriser pour la considérer terminée.
     *
     * Pas 100 % : exiger la perfection bloquerait le parcours sur une carte
     * récalcitrante, et la carte fragile reviendra de toute façon en révision.
     */
    const val SEUIL_TERMINEE = 0.8f

    /**
     * Construit le parcours : chaque unité avec son état.
     *
     * La règle d'ouverture est volontairement souple. Une unité est disponible
     * dès que la précédente est **commencée**, pas terminée : un parcours qui
     * exige la perfection avant de laisser avancer se transforme en mur, et
     * l'apprenant abandonne devant une carte qu'il aurait fini par retenir en
     * la croisant plus tard.
     *
     * @param maitrisees cartes considérées acquises, par identifiant.
     * @param vues cartes déjà rencontrées au moins une fois.
     */
    fun parcours(
        unites: List<Unite>,
        maitrisees: Set<Long>,
        vues: Set<Long> = maitrisees
    ): List<Noeud> {
        if (unites.isEmpty()) return emptyList()

        val progressions = unites.map { unite ->
            if (unite.taille == 0) 0f
            else unite.cartes.count { it in maitrisees }.toFloat() / unite.taille
        }
        val commencees = unites.map { unite -> unite.cartes.any { it in vues } }

        // La première unité non terminée est « celle qu'on continue ». S'il n'y
        // en a aucune, tout est acquis et il ne reste que de la révision.
        val indexActuelle = progressions.indexOfFirst { it < SEUIL_TERMINEE }

        return unites.mapIndexed { i, unite ->
            val terminee = progressions[i] >= SEUIL_TERMINEE
            val ouverte = i == 0 || commencees[i - 1] || progressions[i - 1] >= SEUIL_TERMINEE
            Noeud(
                unite = unite,
                etat = when {
                    terminee -> Etat.TERMINEE
                    i == indexActuelle && ouverte -> Etat.ACTUELLE
                    ouverte -> Etat.DISPONIBLE
                    else -> Etat.VERROUILLEE
                },
                progression = progressions[i]
            )
        }
    }

    /**
     * L'unité à proposer derrière « Continuer ».
     *
     * Une seule, et c'est tout l'intérêt : dix actions concurrentes sur
     * l'accueil, c'est la situation qu'on corrige.
     */
    fun aContinuer(parcours: List<Noeud>): Unite? =
        parcours.firstOrNull { it.etat == Etat.ACTUELLE }?.unite
            ?: parcours.firstOrNull { it.etat == Etat.DISPONIBLE }?.unite

    /** Progression globale du module, de 0 à 1. */
    fun progression(parcours: List<Noeud>): Float {
        val total = parcours.sumOf { it.unite.taille }
        if (total == 0) return 0f
        return parcours.sumOf { (it.progression * it.unite.taille).toDouble() }
            .toFloat() / total
    }
}
