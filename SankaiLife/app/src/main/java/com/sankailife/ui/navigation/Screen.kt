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
    object Flashcards  : Screen("flashcards/{profileId}") {
        fun createRoute(profileId: Long) = "flashcards/$profileId"
    }
    object Challenges  : Screen("challenges")
    object Shop        : Screen("shop")
    object Profile     : Screen("profile")
    object Settings    : Screen("settings")
}
