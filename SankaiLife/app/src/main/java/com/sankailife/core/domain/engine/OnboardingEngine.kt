package com.sankailife.core.domain.engine

/**
 * Le tutoriel de première ouverture.
 *
 * Six pages, pas douze. Le cahier des charges en listait une par geste —
 * planter, arroser, récolter, ouvrir un coffre — mais un tutoriel qui explique
 * chaque bouton avant qu'on ait touché à quoi que ce soit se fait passer, et
 * on n'en retient rien.
 *
 * Ce qui est expliqué ici, c'est ce qu'on ne peut pas deviner : le lien entre
 * réviser et jardiner. Le reste — un bouton « Planter » sur une case vide — se
 * découvre en une seconde et n'a pas besoin d'un écran.
 *
 * Le contenu vit dans un moteur pur pour être vérifiable : un tutoriel qui
 * saute une page ou boucle est un tutoriel dont personne ne sort.
 */
object OnboardingEngine {

    data class Page(
        val emoji: String,
        val titre: String,
        val texte: String,
        /** Libellé du bouton principal. */
        val action: String
    )

    val pages: List<Page> = listOf(
        Page(
            emoji = "🌱",
            titre = "Bienvenue dans Sankai Life",
            texte = "Une application pour apprendre, avec un jardin qui pousse " +
                "à mesure que tu progresses.",
            action = "Commencer"
        ),
        Page(
            emoji = "📝",
            titre = "Tes mémos",
            texte = "Écris ce que tu veux retenir, une ligne par idée. " +
                "Sépare la question de la réponse avec « | ».\n\n" +
                "Exemple : Olá | Bonjour",
            action = "Suivant"
        ),
        Page(
            emoji = "🎴",
            titre = "La révision espacée",
            texte = "Sankai te repropose chaque carte au bon moment : juste " +
                "avant que tu l'oublies.\n\n" +
                "Glisse la carte pour dire si tu savais.",
            action = "Suivant"
        ),
        Page(
            emoji = "💧",
            titre = "Réviser produit de l'eau",
            texte = "C'est le cœur de l'application : tes révisions donnent " +
                "l'eau qui fait pousser ton jardin.\n\n" +
                "Sans réviser, rien ne pousse.",
            action = "Suivant"
        ),
        Page(
            emoji = "🌿",
            titre = "Ton jardin",
            texte = "Plante, arrose, récolte, vends. Tes Mimos continuent le " +
                "travail pendant ton absence.\n\n" +
                "Touche une parcelle pour agir dessus.",
            action = "Suivant"
        ),
        Page(
            emoji = "🎁",
            titre = "Tout marche hors ligne",
            texte = "Aucun compte, aucune connexion nécessaire. Pense à " +
                "exporter ton profil de temps en temps, dans les paramètres.",
            action = "C'est parti"
        )
    )

    val derniere: Int get() = pages.lastIndex

    fun estDerniere(index: Int): Boolean = index >= derniere

    /**
     * Page suivante, bornée.
     * Dépasser la dernière ne fait rien : c'est au bouton final de fermer.
     */
    fun suivante(index: Int): Int = (index + 1).coerceAtMost(derniere)

    fun precedente(index: Int): Int = (index - 1).coerceAtLeast(0)
}
