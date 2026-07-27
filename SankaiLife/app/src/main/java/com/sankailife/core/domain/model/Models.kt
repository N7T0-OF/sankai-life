package com.sankailife.core.domain.model

data class UserState(
    val pseudo: String = "Joueur",
    val level: Int = 1,
    val xp: Int = 0,
    val xpNext: Int = 200,
    val coins: Int = 0,
    val gems: Int = 0,
    val streakDays: Int = 0,
    val totalFocusMinutes: Int = 0,
    val totalAdsWatched: Int = 0,
    val totalChestsOpened: Int = 0,
    val adCountToday: Int = 0,
    /** Nombre de modules activables simultanément. S'achète en boutique. */
    val moduleSlots: Int = 1,
    val focusSlots: Int = 1
)

data class DayStats(
    val xpGained: Int = 0,
    val coinsGained: Int = 0,
    val focusSessions: Int = 0
)

data class ShopItem(
    val id: String,
    val name: String,
    val description: String,
    val costCoins: Int = 0,
    val costGems: Int = 0,
    val category: String
)

data class Theme(
    val id: String,
    val name: String,
    val emoji: String,
    val accentHex: String,
    val unlockLevel: Int = 0,
    val unlockType: String = "default"
)

val ALL_THEMES = listOf(
    Theme("default", "Default Or",      "🌑", "#F5A623", 0,  "default"),
    Theme("blue",    "Blue Neon",        "🔵", "#22D3EE", 12, "level"),
    Theme("purple",  "Purple",           "🟣", "#A78BFA", 20, "level"),
    Theme("red",     "Red Energy",       "🔴", "#F87171", 25, "level"),
    Theme("green",   "Nature",           "🌿", "#4ADE80", 15, "level"),
    Theme("cyan",    "Cyan Storm",       "⚡", "#67E8F9", 0,  "chest_rare"),
    Theme("gold",    "Legendary Gold",   "👑", "#FCD34D", 0,  "chest_epic"),
    Theme("pink",    "Pink Glitch",      "💗", "#F472B6", 0,  "chest_epic")
)

val ALL_SHOP_ITEMS = listOf(
    ShopItem("boost_2x_coins", "×2 Pièces 30min", "Double tes pièces pendant 30 minutes", 200, 0, "boost"),
    ShopItem("boost_skip_cd",  "Skip Cooldown",    "Retire le cooldown en cours",          0,   1, "boost"),
    ShopItem("boost_2x_chest", "2× Récompense",    "Double les gains des coffres 1h",      0,   2, "boost"),
    ShopItem("chest_common",   "Coffre Commun",    "Pièces et items basiques",             200, 0, "chest"),
    ShopItem("chest_rare",     "Coffre Rare",      "Pièces, gemmes, boosts",               500, 0, "chest"),
    ShopItem("chest_epic",     "Coffre Épique",    "Gemmes, thèmes possibles",             0,   3, "chest"),
    // Prix 0 volontaire : le coût d'un slot augmente à chaque achat et est
    // recalculé à l'affichage via EconomyEngine.slotCost(). Voir ShopScreen.
    ShopItem("slot_module",    "+1 Slot Module",   "Active un module de plus",             0,   0, "upgrade"),
    ShopItem("rare_chance",    "+25% Coffre Rare", "Meilleure chance de drop",             0,   5, "upgrade")
)
