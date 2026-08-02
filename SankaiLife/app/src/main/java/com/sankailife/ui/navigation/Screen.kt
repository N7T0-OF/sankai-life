package com.sankailife.ui.navigation

sealed class Screen(val route: String) {
    object Home        : Screen("home")
    object Life        : Screen("life")
    object Focus       : Screen("focus")
    object Memo        : Screen("memo")
    object MemoEditor  : Screen("memo_editor/{profileId}") {
        fun createRoute(profileId: Long) = "memo_editor/$profileId"
    }
    object Objectives  : Screen("objectives")
    object Arenas        : Screen("arenas")
    object Customization : Screen("customization")
    object AllStats      : Screen("stats")
    object Garden        : Screen("garden")
    object Island        : Screen("island")
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
    object Challenges  : Screen("challenges")
    object Shop        : Screen("shop")
    object Profile     : Screen("profile")
    object Settings    : Screen("settings")
}
