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
    val focusSlots: Int = 1,
    /** Record personnel de série, jamais repris à la baisse. */
    val bestStreak: Int = 0,
    /** Boucliers absorbant un jour manqué sans casser la série. */
    val streakShields: Int = 0
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
    Theme("cyan",    "Cyan Storm",       "⚡", "#67E8F9", 10, "level"),
    Theme("gold",    "Legendary Gold",   "👑", "#FCD34D", 20, "level"),
    Theme("pink",    "Pink Glitch",      "💗", "#F472B6", 28, "level")
)

/**
 * Le catalogue de la boutique.
 *
 * **Tout ce qui est vendu ici fait réellement quelque chose.** Quatre articles
 * ont été retirés — deux boosts, un saut de délai, une chance de coffre
 * améliorée : ils étaient encaissés puis annoncés « acheté » sans qu'aucun
 * effet n'existe derrière. C'est la même faute que le slot module gratuit
 * signalé au tout début, et elle mérite un retrait plutôt qu'une rustine.
 *
 * Les catégories rendent la boutique lisible, mais aucune n'a été créée pour
 * remplir un onglet : une catégorie sans article qui marche vaut moins que pas
 * de catégorie du tout.
 */
val ALL_SHOP_ITEMS = listOf(
    // --- Coffres -----------------------------------------------------------
    ShopItem("chest_common",   "Coffre commun",    "Pièces et objets de base",              200, 0, "chest"),
    ShopItem("chest_rare",     "Coffre rare",      "Pièces, gemmes, meilleurs tirages",     500, 0, "chest"),
    ShopItem("chest_epic",     "Coffre épique",    "Les meilleures récompenses",            0,   3, "chest"),

    // --- Jardin ------------------------------------------------------------
    // L'eau s'achète, mais reste bien plus chère que la réviser : le raccourci
    // doit dépanner, pas remplacer l'apprentissage.
    ShopItem("eau_10",         "10 unités d'eau",  "De quoi arroser dix parcelles",         450, 0, "jardin"),
    ShopItem("eau_30",         "30 unités d'eau",  "Réserve pour plusieurs jours",          1200, 0, "jardin"),
    ShopItem("compost_10",     "10 sacs de compost", "Nourrit tes Mimos pendant leur travail", 600, 0, "jardin"),

    // --- Progression -------------------------------------------------------
    // Prix 0 volontaire : le coût d'un slot augmente à chaque achat et est
    // recalculé à l'affichage via EconomyEngine.slotCost(). Voir ShopScreen.
    ShopItem("slot_module",    "+1 emplacement mémo", "Active un module de plus",           0,   0, "progression"),
    ShopItem("bouclier",       "Bouclier de série", "Absorbe un jour manqué sans casser ta série", 800, 0, "progression")
)
