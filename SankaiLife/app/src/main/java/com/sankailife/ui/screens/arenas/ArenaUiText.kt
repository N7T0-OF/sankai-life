package com.sankailife.ui.screens.arenas

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.sankailife.R
import com.sankailife.core.domain.model.Arena
import com.sankailife.core.domain.model.ArenaReward

@StringRes
private fun arenaNameResource(id: Int): Int? = when (id) {
    1 -> R.string.arena_1_name
    2 -> R.string.arena_2_name
    3 -> R.string.arena_3_name
    4 -> R.string.arena_4_name
    5 -> R.string.arena_5_name
    6 -> R.string.arena_6_name
    7 -> R.string.arena_7_name
    8 -> R.string.arena_8_name
    else -> null
}

@StringRes
private fun arenaDescriptionResource(id: Int): Int? = when (id) {
    1 -> R.string.arena_1_description
    2 -> R.string.arena_2_description
    3 -> R.string.arena_3_description
    4 -> R.string.arena_4_description
    5 -> R.string.arena_5_description
    6 -> R.string.arena_6_description
    7 -> R.string.arena_7_description
    8 -> R.string.arena_8_description
    else -> null
}

@Composable
fun Arena.localizedName(): String {
    val resource = arenaNameResource(id)
    return if (resource != null) stringResource(resource) else nom
}

@Composable
fun Arena.localizedDescription(): String {
    val resource = arenaDescriptionResource(id)
    return if (resource != null) stringResource(resource) else description
}

@StringRes
private fun rewardChestResource(type: String): Int = when (type.uppercase()) {
    "DAILY" -> R.string.chest_daily
    "RARE" -> R.string.chest_rare
    "EPIC" -> R.string.chest_epic
    "LEGENDARY" -> R.string.chest_legendary
    "WEEKLY" -> R.string.chest_weekly
    "ARENA" -> R.string.chest_arena
    else -> R.string.chest_common
}

/** Présentation localisée d'une récompense, sans faire remonter de texte dans le domaine. */
@Composable
fun ArenaReward.localizedSummary(): String {
    val elements = mutableListOf<String>()
    if (coins > 0) elements += stringResource(R.string.arena_reward_coins, coins)
    if (gems > 0) elements += stringResource(R.string.arena_reward_gems, gems)
    if (chestType.isNotBlank()) {
        elements += stringResource(
            R.string.arena_reward_chest,
            stringResource(rewardChestResource(chestType))
        )
    }
    if (themeId.isNotBlank()) elements += stringResource(R.string.arena_reward_theme)
    if (moduleSlots > 0) {
        elements += pluralStringResource(
            R.plurals.arena_reward_module_slots,
            moduleSlots,
            moduleSlots
        )
    }
    return elements.joinToString(" • ").ifBlank { "—" }
}
