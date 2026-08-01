package com.sankailife.ui.screens.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankailife.R
import com.sankailife.core.data.db.entities.ChestEntity
import com.sankailife.core.domain.engine.ArenaEngine
import com.sankailife.core.domain.model.ArenaReward
import com.sankailife.ui.art.ArtJardin
import com.sankailife.ui.art.IconeArt
import com.sankailife.ui.components.ChestRewardDialog
import com.sankailife.ui.components.LevelUpDialog
import com.sankailife.ui.components.LiquidGlassSurface
import com.sankailife.ui.components.ResourceBar
import com.sankailife.ui.components.SankaiFloatingButton
import com.sankailife.ui.components.SankaiGlassCard
import com.sankailife.ui.navigation.Screen
import com.sankailife.ui.screens.arenas.localizedDescription
import com.sankailife.ui.screens.arenas.localizedName
import com.sankailife.ui.screens.arenas.localizedSummary
import com.sankailife.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigate: (String) -> Unit) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val chests by viewModel.chests.collectAsStateWithLifecycle()
    val showLevelUp by viewModel.showLevelUp.collectAsStateWithLifecycle()
    val levelUpLevel by viewModel.levelUpLevel.collectAsStateWithLifecycle()
    val chestReward by viewModel.chestReward.collectAsStateWithLifecycle()
    val arenesAReclamer by viewModel.arenesAReclamer.collectAsStateWithLifecycle()
    val c = MaterialTheme.sankaiColors
    val fontScale = LocalDensity.current.fontScale
    val background = remember(c.background, c.accentSecondary) {
        Brush.verticalGradient(
            listOf(
                c.background,
                c.accentSecondary.copy(alpha = 0.055f),
                c.background
            )
        )
    }

    if (showLevelUp) {
        LevelUpDialog(
            level = levelUpLevel,
            coins = levelUpLevel * 50,
            onDismiss = viewModel::dismissLevelUp
        )
    }
    chestReward?.let { reward ->
        ChestRewardDialog(
            title = stringResource(R.string.home_chest_opened),
            coins = reward.coins,
            gems = reward.gems,
            xp = reward.xp,
            onDismiss = viewModel::dismissChestReward
        )
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(background)
    ) {
        val compact = maxHeight < 720.dp || maxWidth < 360.dp || fontScale >= 1.30f
        val dense = maxHeight < 610.dp || maxWidth < 330.dp || fontScale >= 1.60f

        Column(Modifier.fillMaxSize()) {
            ResourceBar(user.level, user.xp, user.xpNext, user.coins, user.gems)

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                HomeHub(
                    pseudo = user.pseudo,
                    streak = user.streakDays,
                    niveau = user.level,
                    arenesAReclamer = arenesAReclamer,
                    compact = compact,
                    dense = dense,
                    modifier = Modifier.fillMaxSize().widthIn(max = 680.dp),
                    onSettings = { onNavigate(Screen.Settings.route) },
                    onArena = { onNavigate(Screen.Arenas.route) },
                    onGarden = { onNavigate(Screen.Garden.route) }
                )
            }

            BarreCoffres(
                chests = chests,
                compact = compact,
                dense = dense,
                onOpen = viewModel::openChest,
                formatTimer = viewModel::formatChestTimer
            )
        }
    }
}

@Composable
private fun HomeHub(
    pseudo: String,
    streak: Int,
    niveau: Int,
    arenesAReclamer: Int,
    compact: Boolean,
    dense: Boolean,
    modifier: Modifier = Modifier,
    onSettings: () -> Unit,
    onArena: () -> Unit,
    onGarden: () -> Unit
) {
    val gap = when {
        dense -> SankaiSpacing.Xs
        compact -> SankaiSpacing.Sm
        else -> SankaiSpacing.Md
    }

    Column(
        modifier = modifier.padding(
            start = SankaiSpacing.Lg,
            top = gap,
            end = SankaiSpacing.Lg,
            bottom = SankaiSpacing.Sm
        )
    ) {
        PremiumProfileHeader(
            pseudo = pseudo,
            streak = streak,
            compact = compact,
            dense = dense,
            onSettings = onSettings
        )

        Spacer(Modifier.height(gap))
        ArenaHeroCard(
            niveau = niveau,
            nombreAReclamer = arenesAReclamer,
            compact = compact,
            dense = dense,
            onVoirParcours = onArena,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        Spacer(Modifier.height(gap))
        GardenPrimaryAction(
            compact = compact,
            onClick = onGarden,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PremiumProfileHeader(
    pseudo: String,
    streak: Int,
    compact: Boolean,
    dense: Boolean,
    onSettings: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val greeting = stringResource(R.string.home_greeting, pseudo)
    val initiale = remember(pseudo) {
        pseudo.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S"
    }

    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        forme = RoundedCornerShape(SankaiRadius.Medium),
        intensite = SankaiGlass.CardIntensity
    ) {
        Row(
            Modifier.fillMaxWidth().padding(
                horizontal = if (dense) SankaiSpacing.Sm else SankaiSpacing.Md,
                vertical = SankaiSpacing.Sm
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (dense) 40.dp else 46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(c.accentSecondary, c.accent.copy(alpha = 0.9f))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initiale,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.width(SankaiSpacing.Md))
            Column(Modifier.weight(1f)) {
                Text(
                    greeting,
                    color = c.textPrimary,
                    style = if (compact) MaterialTheme.typography.titleMedium
                            else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!compact) {
                    Text(
                        stringResource(R.string.home_subtitle),
                        color = c.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(SankaiSpacing.Sm))
            StreakChip(streak = streak, dense = dense)
            Spacer(Modifier.width(SankaiSpacing.Sm))
            SankaiFloatingButton(
                contentDescription = stringResource(R.string.home_settings),
                onClick = onSettings,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    tint = c.textPrimary,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@Composable
private fun StreakChip(streak: Int, dense: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(SankaiRadius.Pill))
            .background(WarningAmber.copy(alpha = 0.13f))
            .border(1.dp, WarningAmber.copy(alpha = 0.38f), RoundedCornerShape(SankaiRadius.Pill))
            .padding(
                horizontal = if (dense) SankaiSpacing.Sm else SankaiSpacing.Md,
                vertical = SankaiSpacing.Sm
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🔥", fontSize = 13.sp)
        Spacer(Modifier.width(SankaiSpacing.Xs))
        Text(
            stringResource(R.string.home_streak_days, streak),
            color = WarningAmber,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Carte maîtresse du Hub : une seule destination, le parcours d'arènes. */
@Composable
fun ArenaHeroCard(
    niveau: Int,
    nombreAReclamer: Int,
    compact: Boolean,
    dense: Boolean = false,
    onVoirParcours: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.sankaiColors
    val actuelle = ArenaEngine.areneActuelle(niveau)
    val suivante = ArenaEngine.areneSuivante(niveau)
    val nomActuel = actuelle.localizedName()
    val description = actuelle.localizedDescription()
    val nomSuivant = suivante?.localizedName()
    val progression = ArenaEngine.progressionVersSuivante(niveau)
    val accent = remember(actuelle.accentHex, c.accent) {
        runCatching { Color(android.graphics.Color.parseColor(actuelle.accentHex)) }
            .getOrDefault(c.accent)
    }
    val rewardText = suivante?.recompense?.localizedSummary()
    val claimableLabel = if (nombreAReclamer > 0) {
        pluralStringResource(
            R.plurals.arena_rewards_claimable,
            nombreAReclamer,
            nombreAReclamer
        )
    } else null

    SankaiGlassCard(
        modifier = modifier,
        selectionne = true,
        onClick = onVoirParcours,
        forme = RoundedCornerShape(SankaiRadius.Large),
        contentPadding = PaddingValues(if (dense) SankaiSpacing.Md else SankaiSpacing.Lg)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.home_current_arena),
                        color = accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.15.sp
                    )
                    Text(
                        nomActuel,
                        color = c.textPrimary,
                        style = when {
                            dense -> MaterialTheme.typography.titleMedium
                            compact -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.headlineMedium
                        },
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (claimableLabel != null) {
                    Text(
                        if (dense) "$nombreAReclamer" else claimableLabel,
                        color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(SankaiRadius.Pill))
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = SankaiSpacing.Sm, vertical = SankaiSpacing.Xs)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = if (dense) 62.dp else 92.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .width(if (dense) 110.dp else 176.dp)
                        .height(if (dense) 22.dp else 34.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.11f))
                )
                Box(
                    modifier = Modifier
                        .size(
                            when {
                                dense -> 72.dp
                                compact -> 96.dp
                                else -> 126.dp
                            }
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.06f))
                            )
                        )
                        .border(1.dp, accent.copy(alpha = 0.38f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        actuelle.emoji,
                        fontSize = when {
                            dense -> 38.sp
                            compact -> 52.sp
                            else -> 68.sp
                        }
                    )
                }
            }

            if (!compact) {
                Text(
                    description,
                    color = c.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(SankaiSpacing.Sm))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.arena_level, niveau),
                    color = c.textSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    if (suivante != null) {
                        stringResource(R.string.home_next_goal_level, suivante.niveauRequis)
                    } else {
                        stringResource(R.string.home_summit_reached)
                    },
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(SankaiSpacing.Xs))
            LinearProgressIndicator(
                progress = { progression.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(SankaiRadius.Pill)),
                color = accent,
                trackColor = c.surface3
            )
            Spacer(Modifier.height(if (dense) SankaiSpacing.Xs else SankaiSpacing.Sm))

            if (suivante != null && nomSuivant != null && rewardText != null) {
                NextArenaReward(
                    reward = suivante.recompense,
                    arenaName = nomSuivant,
                    rewardText = rewardText,
                    levelsRemaining = ArenaEngine.niveauxRestants(niveau),
                    accent = accent,
                    dense = dense
                )
            } else {
                Text(
                    stringResource(R.string.home_summit_message),
                    color = accent,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!compact) {
                Spacer(Modifier.height(SankaiSpacing.Sm))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.home_view_arena_path),
                        color = accent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(SankaiSpacing.Xs))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NextArenaReward(
    reward: ArenaReward,
    arenaName: String,
    rewardText: String,
    levelsRemaining: Int,
    accent: Color,
    dense: Boolean
) {
    val c = MaterialTheme.sankaiColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SankaiRadius.Medium))
            .background(accent.copy(alpha = 0.075f))
            .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(SankaiRadius.Medium))
            .padding(if (dense) SankaiSpacing.Sm else SankaiSpacing.Md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RewardArtwork(reward = reward, size = if (dense) 32.dp else 42.dp)
        Spacer(Modifier.width(SankaiSpacing.Md))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.home_next_arena, arenaName),
                color = c.textPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                pluralStringResource(
                    R.plurals.arena_levels_remaining,
                    levelsRemaining,
                    levelsRemaining
                ),
                color = c.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!dense) {
                Text(
                    stringResource(R.string.home_next_reward, rewardText),
                    color = accent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RewardArtwork(reward: ArenaReward, size: androidx.compose.ui.unit.Dp) {
    val art = if (reward.chestType.isNotBlank()) {
        ArtJardin.coffre(reward.chestType)
    } else {
        ArtJardin.piece
    }
    IconeArt(art, taille = size)
}

@Composable
private fun GardenPrimaryAction(
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.sankaiColors
    val label = stringResource(R.string.home_enter_garden)
    val gradient = remember(c.accent, c.accentSecondary) {
        Brush.horizontalGradient(
            listOf(
                SuccessGreen.copy(alpha = 0.82f),
                c.accent.copy(alpha = 0.92f),
                c.accentSecondary.copy(alpha = 0.84f)
            )
        )
    }

    Row(
        modifier = modifier
            .heightIn(min = if (compact) 52.dp else 60.dp)
            .clip(RoundedCornerShape(SankaiRadius.Medium))
            .background(gradient)
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(SankaiRadius.Medium))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = SankaiSpacing.Lg, vertical = SankaiSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconeArt(ArtJardin.arbre, taille = if (compact) 32.dp else 38.dp)
        Spacer(Modifier.width(SankaiSpacing.Md))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!compact) {
                Text(
                    stringResource(R.string.home_enter_garden_hint),
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Dock fixe : quatre emplacements stables, sans faux bouton sur les cases vides. */
@Composable
fun BarreCoffres(
    chests: List<ChestEntity>,
    onOpen: (Long) -> Unit,
    formatTimer: (ChestEntity) -> String,
    compact: Boolean = false,
    dense: Boolean = false
) {
    val c = MaterialTheme.sankaiColors
    var tick by remember { mutableLongStateOf(0L) }
    val minuterieActive = chests.any { !it.isReady }
    LaunchedEffect(minuterieActive) {
        if (!minuterieActive) return@LaunchedEffect
        while (true) {
            delay(1_000)
            tick++
        }
    }
    val prets = chests.count { it.isReady }

    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        forme = RoundedCornerShape(
            topStart = SankaiRadius.Large,
            topEnd = SankaiRadius.Large,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        ),
        intensite = SankaiGlass.NavigationIntensity
    ) {
        Column(
            Modifier.fillMaxWidth().padding(
                start = SankaiSpacing.Md,
                top = if (dense) SankaiSpacing.Xs else SankaiSpacing.Sm,
                end = SankaiSpacing.Md,
                bottom = if (dense) SankaiSpacing.Xs else SankaiSpacing.Sm
            )
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = SankaiSpacing.Xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.home_chests_title, chests.size, 4),
                    color = c.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (prets > 0) {
                    Text(
                        pluralStringResource(R.plurals.home_chests_ready, prets, prets),
                        color = c.accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    if (dense) SankaiSpacing.Xs else SankaiSpacing.Sm
                )
            ) {
                repeat(4) { slot ->
                    val chest = chests.firstOrNull { it.slotIndex == slot }
                    ChestSlotUI(
                        chest = chest,
                        slotNumber = slot + 1,
                        compact = compact,
                        dense = dense,
                        onOpen = { chest?.let { onOpen(it.id) } },
                        timer = chest?.let { if (tick >= 0) formatTimer(it) else "" }.orEmpty(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun chestRarity(type: String): String = stringResource(
    when (type.uppercase()) {
        "DAILY" -> R.string.chest_daily
        "RARE" -> R.string.chest_rare
        "EPIC" -> R.string.chest_epic
        "LEGENDARY" -> R.string.chest_legendary
        "WEEKLY" -> R.string.chest_weekly
        "ARENA" -> R.string.chest_arena
        else -> R.string.chest_common
    }
)

@Composable
fun ChestSlotUI(
    chest: ChestEntity?,
    onOpen: () -> Unit,
    timer: String,
    modifier: Modifier = Modifier,
    slotNumber: Int? = null,
    compact: Boolean = false,
    dense: Boolean = false
) {
    val c = MaterialTheme.sankaiColors
    val chestColor = when (chest?.type) {
        "RARE" -> ChestRare
        "EPIC" -> ChestEpic
        "LEGENDARY" -> ChestLegendary
        "DAILY" -> ChestDaily
        "WEEKLY", "ARENA" -> c.accentSecondary
        else -> if (chest != null) ChestCommon else c.surface3
    }
    val isReady = chest?.isReady == true
    val halo = if (isReady) {
        val transition = rememberInfiniteTransition(label = "coffrePret")
        val value by transition.animateFloat(
            initialValue = 0.52f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(SankaiMotion.RewardPulse),
                RepeatMode.Reverse
            ),
            label = "haloCoffre"
        )
        value
    } else {
        1f
    }

    val slotLabel = if (slotNumber != null) {
        stringResource(R.string.home_chest_slot, slotNumber)
    } else {
        stringResource(R.string.home_chest_slot_generic)
    }
    val rarity = if (chest != null) chestRarity(chest.type) else ""
    val description = when {
        chest == null -> stringResource(R.string.home_chest_empty_description, slotLabel)
        isReady -> stringResource(R.string.home_chest_ready_description, slotLabel, rarity)
        else -> stringResource(R.string.home_chest_waiting_description, slotLabel, rarity, timer)
    }
    val emptyState = stringResource(R.string.home_chest_empty_state)
    val readyState = stringResource(R.string.home_chest_ready_state)
    val openLabel = stringResource(R.string.home_chest_open_action)

    Box(
        modifier = modifier
            .height(
                when {
                    dense -> 68.dp
                    compact -> 78.dp
                    else -> 94.dp
                }
            )
            .clip(RoundedCornerShape(SankaiRadius.Medium))
            .background(
                if (chest != null) chestColor.copy(alpha = if (isReady) 0.22f else 0.13f)
                else c.surface2.copy(alpha = 0.72f)
            )
            .border(
                width = if (isReady) 2.dp else 1.dp,
                color = if (isReady) chestColor.copy(alpha = halo) else c.border,
                shape = RoundedCornerShape(SankaiRadius.Medium)
            )
            .then(
                if (isReady) Modifier.clickable(role = Role.Button, onClick = onOpen)
                else Modifier
            )
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = when {
                    chest == null -> emptyState
                    isReady -> readyState
                    else -> timer
                }
                if (isReady) {
                    role = Role.Button
                    onClick(label = openLabel) {
                        onOpen()
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (chest == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(if (dense) 26.dp else 32.dp)
                        .clip(CircleShape)
                        .border(1.dp, c.border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("—", color = c.textDisabled, style = MaterialTheme.typography.labelLarge)
                }
                if (!dense) {
                    Spacer(Modifier.height(SankaiSpacing.Xs))
                    Text(
                        stringResource(R.string.home_chest_free),
                        color = c.textDisabled,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = SankaiSpacing.Xs, vertical = SankaiSpacing.Xs)
            ) {
                Text(
                    if (isReady) stringResource(R.string.chest_ready) else timer,
                    color = if (isReady) chestColor else c.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isReady) FontWeight.Black else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Spacer(Modifier.height(SankaiSpacing.Xxs))
                IconeArt(
                    ArtJardin.coffre(chest.type),
                    taille = when {
                        dense -> 32.dp
                        compact -> 38.dp
                        else -> 46.dp
                    }
                )
                if (!compact) {
                    Spacer(Modifier.height(SankaiSpacing.Xxs))
                    Text(
                        rarity,
                        color = chestColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
