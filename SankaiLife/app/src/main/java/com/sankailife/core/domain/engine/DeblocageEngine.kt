package com.sankailife.core.domain.engine

/**
 * Ce que le niveau débloque.
 *
 * Les niveaux montaient sans rien ouvrir : gagner de l'expérience changeait un
 * chiffre et rien d'autre. Ici, chaque palier rend une section ou un exercice
 * accessible.
 *
 * Deux règles encadrent ce système, et elles comptent plus que la liste :
 *
 * 1. **rien d'essentiel n'est verrouillé.** Le mémo, les flash cards et le
 *    jardin sont disponibles au niveau 1. Une application d'apprentissage qui
 *    fait attendre avant d'apprendre a raté son objet ;
 * 2. **un verrou explique toujours.** Niveau requis, niveau actuel, ce qu'on
 *    obtient. Un cadenas muet n'est pas une progression, c'est une frustration.
 */
object DeblocageEngine {

    /**
     * Une fonctionnalité soumise au niveau.
     *
     * L'ordre de déclaration suit les paliers : il sert aussi à afficher la
     * liste « à venir » sans la trier à l'exécution.
     */
    enum class Fonction(
        val niveauRequis: Int,
        val libelle: String,
        val emoji: String,
        val description: String
    ) {
        // --- Niveau 1 : tout ce qui sert à apprendre -----------------------
        MEMO(1, "Mémo", "📝", "Tes modules de mémorisation"),
        FLASHCARDS(1, "Flash cards", "🎴", "Révision par répétition espacée"),
        JARDIN(1, "Jardin", "🌿", "Le mode jeu"),
        COFFRE_QUOTIDIEN(1, "Coffre quotidien", "🎁", "Une récompense par jour"),

        // --- Progression ---------------------------------------------------
        FOCUS(2, "Focus", "⏳", "Sessions de concentration minutées"),
        OUTILS_AMELIORABLES(2, "Atelier", "🔧", "Améliorer ton arrosoir"),

        QCM(3, "Choix multiple", "🔤", "Reconnaître avant de produire"),
        BOUTIQUE_JARDIN(3, "Marché", "🏪", "Vendre tes récoltes"),
        HERBIER(3, "Herbier", "📗", "Collection des espèces cultivées"),

        MIMOS(4, "Mimos", "🐾", "Des habitants qui travaillent pour toi"),
        MISSIONS_QUOTIDIENNES(4, "Missions du jour", "📋", "Objectifs renouvelés"),

        DEFIS(5, "Défis", "🏆", "Missions hebdomadaires et récompenses"),
        NOUVELLE_ARENE(5, "Nouvelle arène", "🏟️", "Un jardin qui change d'allure"),

        TEXTE_A_TROUS(6, "Texte à trous", "✍️", "Compléter au lieu de choisir"),
        STATISTIQUES(6, "Statistiques", "📊", "Ta progression en détail"),

        EXTENSIONS(7, "Extensions", "🧭", "Agrandir le terrain dans les 4 directions"),
        DICTEE(7, "Dictée", "🎧", "Écrire ce que tu entends"),

        AUTOMATISATION(8, "Automatisation", "⚙️", "Irrigation et convoyeurs"),
        SERRE(8, "Serre", "🏡", "Cultiver à l'abri de la météo");

        companion object {
            /** Fonctions ouvertes à ce niveau. */
            fun ouvertes(niveau: Int): List<Fonction> =
                entries.filter { it.niveauRequis <= niveau }

            /** La prochaine chose à débloquer, pour donner un cap. */
            fun prochaine(niveau: Int): Fonction? =
                entries.filter { it.niveauRequis > niveau }.minByOrNull { it.niveauRequis }
        }
    }

    fun estDebloquee(fonction: Fonction, niveau: Int): Boolean =
        niveau >= fonction.niveauRequis

    /**
     * Explication d'un verrou.
     *
     * Toujours trois informations : ce qu'on obtient, quand, et combien il
     * reste. Un cadenas qui dit seulement « verrouillé » n'apprend rien et
     * donne l'impression d'un mur, pas d'un objectif.
     */
    data class Verrou(
        val fonction: Fonction,
        val niveauActuel: Int
    ) {
        val niveauxRestants: Int get() = (fonction.niveauRequis - niveauActuel).coerceAtLeast(0)

        val titre: String get() = "${fonction.libelle} verrouillé"

        val explication: String
            get() = buildString {
                append("Déblocage au niveau ${fonction.niveauRequis}.")
                append(" Niveau actuel : $niveauActuel.")
                if (niveauxRestants > 0) {
                    append(
                        if (niveauxRestants == 1) " Il reste 1 niveau."
                        else " Il reste $niveauxRestants niveaux."
                    )
                }
            }
    }

    fun verrou(fonction: Fonction, niveau: Int): Verrou? =
        if (estDebloquee(fonction, niveau)) null else Verrou(fonction, niveau)
}
