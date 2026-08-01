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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.sankailife.core.data.db.entities.ChestEntity
import com.sankailife.core.domain.engine.ArenaEngine
import com.sankailife.ui.art.ArtJardin
import com.sankailife.ui.art.IconeArt
import com.sankailife.ui.components.ChestRewardDialog
import com.sankailife.ui.components.LevelUpDialog
import com.sankailife.ui.components.ResourceBar
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.components.SankaiFloatingButton
import com.sankailife.ui.components.SankaiGlassCard
import com.sankailife.ui.navigation.Screen
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

    if (showLevelUp) {
        LevelUpDialog(
            level = levelUpLevel,
            coins = levelUpLevel * 50,
            onDismiss = viewModel::dismissLevelUp
        )
    }
    chestReward?.let { reward ->
        ChestRewardDialog(
            title = "Coffre ouvert !",
            coins = reward.coins,
            gems = reward.gems,
            xp = reward.xp,
            onDismiss = viewModel::dismissChestReward
        )
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(c.background)
    ) {
        // Le mode compact conserve le hub non défilant : il retire les détails
        // secondaires avant de réduire la zone centrale ou les cibles tactiles.
        val compact = maxHeight < 700.dp || maxWidth < 350.dp || fontScale >= 1.35f

        Column(Modifier.fillMaxSize()) {
            ResourceBar(user.level, user.xp, user.xpNext, user.coins, user.gems)

            HomeHub(
                pseudo = user.pseudo,
                streak = user.streakDays,
                niveau = user.level,
                arenesAReclamer = arenesAReclamer,
                compact = compact,
                modifier = Modifier.weight(1f),
                onSettings = { onNavigate(Screen.Settings.route) },
                onArena = { onNavigate(Screen.Arenas.route) },
                onGarden = { onNavigate(Screen.Garden.route) }
            )

            BarreCoffres(
                chests = chests,
                compact = compact,
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
    modifier: Modifier = Modifier,
    onSettings: () -> Unit,
    onArena: () -> Unit,
    onGarden: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val gap = if (compact) SankaiSpacing.Sm else SankaiSpacing.Md

    Column(
        modifier = modifier.fillMaxWidth().padding(
            start = SankaiSpacing.Lg,
            top = gap,
            end = SankaiSpacing.Lg,
            bottom = SankaiSpacing.Sm
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Bonjour, $pseudo 👋",
                    color = c.textPrimary,
                    style = if (compact) MaterialTheme.typography.headlineMedium
                            else MaterialTheme.typography.headlineLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!compact) {
                    Text(
                        "Reste focus et progresse !",
                        color = c.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.width(SankaiSpacing.Sm))
            StreakCompact(streak)
            Spacer(Modifier.width(SankaiSpacing.Sm))
            SankaiFloatingButton(
                contentDescription = "Paramètres",
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

        Spacer(Modifier.height(gap))
        ArenaHeroCard(
            niveau = niveau,
            nombreAReclamer = arenesAReclamer,
            compact = compact,
            onVoirParcours = onArena,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        Spacer(Modifier.height(gap))
        SankaiButton(
            text = "🌿  Entrer dans le jardin",
            onClick = onGarden,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        )
    }
}

@Composable
private fun StreakCompact(streak: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(SankaiRadius.Pill))
            .background(WarningAmber.copy(alpha = 0.12f))
            .border(1.dp, WarningAmber.copy(alpha = 0.35f), RoundedCornerShape(SankaiRadius.Pill))
            .padding(horizontal = SankaiSpacing.Md, vertical = SankaiSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🔥", fontSize = 13.sp)
        Spacer(Modifier.width(SankaiSpacing.Xs))
        Text("$streak j", color = WarningAmber, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold)
    }
}

/** Élément principal du hub. Il remplit réellement la hauteur que l'accueil lui réserve. */
@Composable
fun ArenaHeroCard(
    niveau: Int,
    nombreAReclamer: Int,
    compact: Boolean,
    onVoirParcours: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.sankaiColors
    val actuelle = ArenaEngine.areneActuelle(niveau)
    val suivante = ArenaEngine.areneSuivante(niveau)
    val accent = remember(actuelle.accentHex, c.accent) {
        runCatching { Color(android.graphics.Color.parseColor(actuelle.accentHex)) }
            .getOrDefault(c.accent)
    }
    val progression = ArenaEngine.progressionVersSuivante(niveau)

    SankaiGlassCard(
        modifier = modifier,
        selectionne = true,
        onClick = onVoirParcours,
        forme = RoundedCornerShape(SankaiRadius.Large),
        contentPadding = PaddingValues(if (compact) SankaiSpacing.Md else SankaiSpacing.Lg)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (!compact) {
                        Text(
                            "ARÈNE ACTUELLE",
                            color = accent,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        )
                    }
                    Text(
                        actuelle.nom,
                        color = c.textPrimary,
                        style = if (compact) MaterialTheme.typography.titleMedium
                                else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (nombreAReclamer > 0) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(SankaiRadius.Pill))
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = SankaiSpacing.Sm, vertical = SankaiSpacing.Xs)
                    ) {
                        Text(
                            if (compact) "$nombreAReclamer" else "$nombreAReclamer à réclamer",
                            color = MaterialTheme.colorScheme.onError,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = if (compact) 38.dp else 72.dp)
                    .padding(vertical = if (compact) SankaiSpacing.Xs else SankaiSpacing.Sm)
                    .clip(RoundedCornerShape(SankaiRadius.Medium))
                    .background(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.24f), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(actuelle.emoji, fontSize = if (compact) 36.sp else 64.sp)
            }

            if (!compact) {
                Text(
                    actuelle.description,
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
                Text("Niveau $niveau", color = c.textSecondary,
                    style = MaterialTheme.typography.labelMedium)
                suivante?.let {
                    Text("Objectif ${it.niveauRequis}", color = accent,
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(SankaiSpacing.Xs))
            LinearProgressIndicator(
                progress = { progression.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp)
                    .clip(RoundedCornerShape(SankaiRadius.Pill)),
                color = accent,
                trackColor = c.surface3
            )
            Spacer(Modifier.height(if (compact) SankaiSpacing.Xs else SankaiSpacing.Sm))
            Text(
                text = if (suivante != null) {
                    if (compact) "${suivante.emoji} ${suivante.nom}"
                    else "${suivante.emoji} ${suivante.nom} • encore ${ArenaEngine.niveauxRestants(niveau)} niveaux"
                } else {
                    "Sommet atteint • le parcours continue"
                },
                color = c.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!compact && suivante != null) {
                Text(
                    "Récompense : ${suivante.recompense.resume()}",
                    color = accent,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!compact) {
                Spacer(Modifier.height(SankaiSpacing.Sm))
                Text(
                    "Voir le parcours  →",
                    color = accent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

/** Dock fixe : les quatre emplacements ne bougent jamais avec le contenu central. */
@Composable
fun BarreCoffres(
    chests: List<ChestEntity>,
    onOpen: (Long) -> Unit,
    formatTimer: (ChestEntity) -> String,
    compact: Boolean = false
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

    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface1)
            .padding(
                horizontal = SankaiSpacing.Md,
                vertical = if (compact) SankaiSpacing.Sm else SankaiSpacing.Md
            )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = if (compact) SankaiSpacing.Xs else SankaiSpacing.Sm),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Coffres  ${chests.size}/4", color = c.textSecondary,
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            if (prets > 0) {
                Text("$prets prêt${if (prets > 1) "s" else ""} !", color = c.accent,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SankaiSpacing.Sm)) {
            repeat(4) { slot ->
                val chest = chests.firstOrNull { it.slotIndex == slot }
                ChestSlotUI(
                    chest = chest,
                    slotNumber = slot + 1,
                    compact = compact,
                    onOpen = { chest?.let { onOpen(it.id) } },
                    // La lecture de tick limite la recomposition au dock, une fois par seconde.
                    timer = chest?.let { if (tick >= 0) formatTimer(it) else "" }.orEmpty(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun libelleRarete(type: String): String = when (type.uppercase()) {
    "DAILY" -> "Quotidien"
    "RARE" -> "Rare"
    "EPIC" -> "Épique"
    "LEGENDARY" -> "Légendaire"
    "WEEKLY" -> "Hebdo"
    "ARENA" -> "Arène"
    else -> "Commun"
}

@Composable
fun ChestSlotUI(
    chest: ChestEntity?,
    onOpen: () -> Unit,
    timer: String,
    modifier: Modifier = Modifier,
    slotNumber: Int? = null,
    compact: Boolean = false
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
            initialValue = 0.5f,
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

    val emplacement = slotNumber?.let { "Emplacement $it, " }.orEmpty()
    val description = when {
        chest == null -> "${emplacement}vide"
        isReady -> "${emplacement}coffre ${libelleRarete(chest.type).lowercase()}, prêt"
        else -> "${emplacement}coffre ${libelleRarete(chest.type).lowercase()}, disponible dans $timer"
    }

    Box(
        modifier = modifier
            .height(if (compact) 82.dp else 104.dp)
            .clip(RoundedCornerShape(SankaiRadius.Medium))
            .background(
                if (chest != null) chestColor.copy(alpha = if (isReady) 0.22f else 0.14f)
                else c.surface2
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
                    chest == null -> "Vide"
                    isReady -> "Prêt"
                    else -> timer
                }
                if (isReady) {
                    role = Role.Button
                    onClick(label = "Ouvrir le coffre") {
                        onOpen()
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (chest == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+", color = c.textDisabled,
                    style = MaterialTheme.typography.headlineMedium)
                if (!compact) {
                    Text("Libre", color = c.textDisabled,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = SankaiSpacing.Xs, vertical = SankaiSpacing.Xs)
            ) {
                Text(
                    if (isReady) "PRÊT" else timer,
                    color = if (isReady) chestColor else c.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isReady) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Spacer(Modifier.height(SankaiSpacing.Xxs))
                IconeArt(
                    ArtJardin.coffre(chest.type),
                    taille = if (compact) 36.dp else 46.dp
                )
                if (!compact) {
                    Spacer(Modifier.height(SankaiSpacing.Xxs))
                    Text(
                        libelleRarete(chest.type),
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
