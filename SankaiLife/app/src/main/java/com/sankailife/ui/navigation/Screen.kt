package com.sankailife.ui.navigation

sealed class Screen(val route: String) {
    object Home        : Screen("home")
    object Academy     : Screen("academy")
    object Capsules    : Screen("capsules")
    object Life        : Screen("life")
    object Memo        : Screen("memo")
    object MemoEditor  : Screen("memo_editor/{profileId}") {
        fun createRoute(profileId: Long) = "memo_editor/$profileId"
    }
    object Customization : Screen("customization")
    object AllStats      : Screen("stats")
    object Flashcards  : Screen("flashcards/{profileId}") {
        fun createRoute(profileId: Long) = "flashcards/$profileId"
    }
    /**
     * Le parcours d'un module.
     *
     * Cle sur l'identifiant du profil Memo et non sur celui du module : le
     * module peut ne pas encore exister en base, puisqu'il n'est cree qu'au
     * moment ou l'on commence vraiment.
     */
    object Parcours    : Screen("parcours/{profileId}") {
        fun createRoute(profileId: Long) = "parcours/$profileId"
    }
    /**
     * Une session guidee sur une unite precise.
     *
     * Distincte de Flashcards, qui reste la revision libre : la premiere fait
     * composer la session par le planificateur, la seconde prend simplement les
     * cartes dues. Les confondre obligerait a deviner l'intention.
     */
    object Session     : Screen("session/{profileId}/{uniteId}") {
        fun createRoute(profileId: Long, uniteId: String) = "session/$profileId/$uniteId"
    }
    object Profile     : Screen("profile")
    object Settings    : Screen("settings")
    /** Le mot du jour : un mot, une définition, puis la vraie vie. */
    object MotDuJour   : Screen("mot_du_jour")
    /** La poésie ou le proverbe du jour : un texte, puis la vraie vie. */
    object PoesieDuJour : Screen("poesie_du_jour")
}
