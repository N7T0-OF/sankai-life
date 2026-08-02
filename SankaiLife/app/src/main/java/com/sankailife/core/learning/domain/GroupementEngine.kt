package com.sankailife.core.learning.domain

/**
 * Regroupe les modules installés par parcours.
 *
 * **Six niveaux de portugais donnaient six lignes plates.** La liste des Mémos
 * mélangeait un parcours de langue complet, un module de raccourcis et une
 * liste de courses, tous de la même taille et sans hiérarchie visible : on ne
 * savait plus ce qui allait avec quoi, ni par où continuer.
 *
 * Ce moteur ne fait que classer. Il ne connaît ni la base ni l'écran, ce qui
 * permet de vérifier le classement — l'ordre des niveaux, le repliage, les cas
 * limites — sans appareil.
 */
object GroupementEngine {

    /** Un module installé, réduit à ce dont le classement a besoin. */
    data class Module(
        val profileId: Long,
        val nom: String,
        /** Identifiant du parcours, vide si le module est seul. */
        val collection: String = "",
        /** A1 … C2, vide pour ce qui n'est pas une langue. */
        val niveau: String = "",
        val cartes: Int = 0,
        /** Part de cartes maîtrisées, de 0 à 1. */
        val progression: Float = 0f,
        val notificationsActives: Boolean = false
    )

    /**
     * Un parcours et ses niveaux, ou un module seul.
     *
     * Le module isolé est représenté comme un groupe d'un seul élément plutôt
     * que par un type à part : l'écran n'a alors qu'une forme à afficher, et
     * un module qui rejoint un parcours plus tard ne change pas de nature.
     */
    data class Groupe(
        val id: String,
        val titre: String,
        val modules: List<Module>
    ) {
        val estParcours: Boolean get() = id.isNotBlank() && modules.size > 1

        val cartes: Int get() = modules.sumOf { it.cartes }

        /**
         * Progression du parcours, pondérée par le nombre de cartes.
         *
         * Une moyenne simple des niveaux donnerait le même poids au C2 qu'au
         * A1, alors que l'un compte 88 cartes et l'autre 189 : terminer le plus
         * petit ferait bondir le pourcentage sans qu'on ait appris davantage.
         */
        val progression: Float
            get() {
                val total = cartes
                if (total == 0) return 0f
                return modules.sumOf { (it.progression * it.cartes).toDouble() }
                    .toFloat() / total
            }

        /**
         * Le niveau où l'on en est : le premier qui n'est pas acquis.
         *
         * Tout terminé, on rend le dernier — « tu es au C2 » est plus juste que
         * « aucun niveau en cours ».
         */
        val niveauActuel: Module?
            get() = modules.firstOrNull { it.progression < SEUIL_ACQUIS }
                ?: modules.lastOrNull()

        val resume: String
            get() = buildList {
                if (estParcours) add("${modules.size} niveaux")
                add("$cartes cartes")
                niveauActuel?.niveau?.takeIf { it.isNotBlank() }?.let { add("en cours : $it") }
            }.joinToString(" · ")
    }

    /** Au-delà, on considère le niveau acquis et on propose le suivant. */
    const val SEUIL_ACQUIS = 0.8f

    /** Ordre des niveaux européens. Il n'existe pas de A3, et rien n'en crée. */
    private val ORDRE_NIVEAUX = listOf("A1", "A2", "B1", "B2", "C1", "C2")

    /**
     * Classe les modules en groupes.
     *
     * Les parcours d'abord, les modules seuls ensuite : un parcours porte
     * plusieurs niveaux et mérite d'être vu en premier, un module isolé se
     * retrouve aussi bien plus bas.
     *
     * À l'intérieur d'un parcours, l'ordre est celui des niveaux européens et
     * non l'ordre d'installation : quelqu'un qui installe le B1 avant le A1
     * doit quand même voir A1 en premier.
     */
    fun grouper(modules: List<Module>): List<Groupe> {
        if (modules.isEmpty()) return emptyList()

        val (enParcours, seuls) = modules.partition { it.collection.isNotBlank() }

        val parcours = enParcours
            .groupBy { it.collection }
            .map { (id, membres) ->
                Groupe(
                    id = id,
                    titre = titreCommun(membres),
                    modules = membres.sortedWith(
                        compareBy({ rangNiveau(it.niveau) }, { it.nom })
                    )
                )
            }
            .sortedBy { it.titre }

        val isoles = seuls
            .sortedBy { it.nom }
            .map { Groupe(id = "", titre = it.nom, modules = listOf(it)) }

        return parcours + isoles
    }

    private fun rangNiveau(niveau: String): Int =
        ORDRE_NIVEAUX.indexOf(niveau.uppercase()).let { if (it < 0) Int.MAX_VALUE else it }

    /**
     * Titre du parcours, déduit des noms de ses niveaux.
     *
     * « Portugais A1 — Premiers pas » et « Portugais A2 — Vie quotidienne »
     * donnent « Portugais ». Le préfixe commun est le seul nom dont on soit
     * sûr : l'identifiant du parcours est technique, et inventer un libellé
     * produirait un titre qui ne ressemble à aucun des modules affichés
     * en dessous.
     */
    internal fun titreCommun(modules: List<Module>): String {
        if (modules.isEmpty()) return ""
        if (modules.size == 1) return modules.first().nom

        val motsDe = modules.map { it.nom.trim().split(Regex("\\s+")) }
        val commun = mutableListOf<String>()
        for (i in 0 until motsDe.minOf { it.size }) {
            val mot = motsDe.first()[i]
            if (motsDe.all { it[i].equals(mot, ignoreCase = true) }) commun += mot else break
        }
        // Un préfixe vide ou réduit à un tiret ne dit rien : on retombe alors
        // sur le nom du premier niveau, qui au moins existe.
        val titre = commun.joinToString(" ").trim().trimEnd('—', '-', ':').trim()
        return titre.ifBlank { modules.first().nom }
    }

    /**
     * Quels groupes ouvrir à l'arrivée.
     *
     * Un seul : celui où l'on travaille. Tout ouvrir redonne la liste plate
     * qu'on vient de corriger ; tout fermer oblige à un geste avant de voir
     * quoi que ce soit.
     */
    fun ouvertsParDefaut(groupes: List<Groupe>): Set<String> {
        val actif = groupes.firstOrNull { g ->
            g.estParcours && g.progression > 0f && g.progression < 1f
        } ?: groupes.firstOrNull { it.estParcours }
        return setOfNotNull(actif?.id)
    }
}
