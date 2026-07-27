package com.sankailife.core.domain.model

/**
 * Récompense attribuée en atteignant une arène.
 *
 * Un seul lot par arène, réclamable manuellement : rendre la récompense
 * automatique priverait le joueur du moment où il la reçoit, qui est
 * précisément ce qui donne envie de progresser.
 */
data class ArenaReward(
    val coins: Int = 0,
    val gems: Int = 0,
    /** COMMON / RARE / EPIC / LEGENDARY, ou vide. */
    val chestType: String = "",
    /** Identifiant d'un thème de [ALL_THEMES], ou vide. */
    val themeId: String = "",
    /** Slots de module supplémentaires accordés définitivement. */
    val moduleSlots: Int = 0
) {
    /** Description courte, pour l'aperçu « prochaine récompense ». */
    fun resume(): String = buildList {
        if (coins > 0) add("$coins 🪙")
        if (gems > 0) add("$gems 💎")
        if (chestType.isNotBlank()) add("coffre ${chestType.lowercase()}")
        if (themeId.isNotBlank()) add("thème")
        if (moduleSlots > 0) add("+$moduleSlots slot")
    }.joinToString(" • ").ifBlank { "—" }
}

/**
 * Palier de progression.
 *
 * Les arènes ne sont pas une monnaie de plus : elles rythment la progression
 * en donnant un objectif nommé à moyen terme, là où le niveau seul est trop
 * abstrait.
 */
data class Arena(
    val id: Int,
    val nom: String,
    val description: String,
    val emoji: String,
    /** Niveau à partir duquel l'arène est atteinte. */
    val niveauRequis: Int,
    /** Couleur d'accent au format hexadécimal, parsée côté interface. */
    val accentHex: String,
    val recompense: ArenaReward
)

/**
 * Parcours complet. L'ordre de la liste est l'ordre d'affichage, du départ
 * au sommet, et `niveauRequis` doit rester strictement croissant.
 */
val ALL_ARENAS = listOf(
    Arena(
        id = 1,
        nom = "Point de départ",
        description = "Tout commence par une première action.",
        emoji = "🌱",
        niveauRequis = 1,
        accentHex = "#4ADE80",
        recompense = ArenaReward(coins = 100)
    ),
    Arena(
        id = 2,
        nom = "Jardin calme",
        description = "Les premières habitudes prennent racine.",
        emoji = "🍃",
        niveauRequis = 3,
        accentHex = "#22D3EE",
        recompense = ArenaReward(coins = 250, chestType = "COMMON")
    ),
    Arena(
        id = 3,
        nom = "Atelier du focus",
        description = "La concentration devient un outil.",
        emoji = "⏱️",
        niveauRequis = 6,
        accentHex = "#7B6CF6",
        recompense = ArenaReward(coins = 400, gems = 2, chestType = "RARE")
    ),
    Arena(
        id = 4,
        nom = "Temple des habitudes",
        description = "Ce que tu répètes te façonne.",
        emoji = "🏯",
        niveauRequis = 10,
        accentHex = "#F5A623",
        recompense = ArenaReward(coins = 700, gems = 3, moduleSlots = 1)
    ),
    Arena(
        id = 5,
        nom = "Ville nocturne",
        description = "Travailler quand tout est silencieux.",
        emoji = "🌃",
        niveauRequis = 15,
        accentHex = "#60A5FA",
        recompense = ArenaReward(coins = 1200, gems = 5, chestType = "EPIC", themeId = "blue")
    ),
    Arena(
        id = 6,
        nom = "Nexus créatif",
        description = "Les idées se connectent enfin.",
        emoji = "🔮",
        niveauRequis = 20,
        accentHex = "#A78BFA",
        recompense = ArenaReward(coins = 2000, gems = 8, chestType = "EPIC", themeId = "purple")
    ),
    Arena(
        id = 7,
        nom = "Sanctuaire stellaire",
        description = "La régularité est devenue naturelle.",
        emoji = "✨",
        niveauRequis = 26,
        accentHex = "#67E8F9",
        recompense = ArenaReward(coins = 3500, gems = 12, chestType = "LEGENDARY", themeId = "cyan")
    ),
    Arena(
        id = 8,
        nom = "Sommet Sankai",
        description = "Le parcours n'a plus de plafond.",
        emoji = "👑",
        niveauRequis = 35,
        accentHex = "#FCD34D",
        recompense = ArenaReward(
            coins = 6000, gems = 20, chestType = "LEGENDARY",
            themeId = "gold", moduleSlots = 1
        )
    )
)
