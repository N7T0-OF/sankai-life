package com.sankailife.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.data.db.entities.ChestEntity
import com.sankailife.ui.components.*
import com.sankailife.ui.navigation.Screen
import com.sankailife.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigate: (String) -> Unit) {
    val user    by viewModel.user.collectAsState()
    val chests  by viewModel.chests.collectAsState()
    val toast   by viewModel.toastMessage.collectAsState()
    val adCd    by viewModel.adCooldown.collectAsState()
    val showLvl by viewModel.showLevelUp.collectAsState()
    val lvlNum  by viewModel.levelUpLevel.collectAsState()
    val chestRw by viewModel.chestReward.collectAsState()
    val c = MaterialTheme.sankaiColors

    // Dialogs
    if (showLvl) LevelUpDialog(level = lvlNum, coins = lvlNum * 50, onDismiss = { viewModel.dismissLevelUp() })
    chestRw?.let { rw ->
        ChestRewardDialog("Coffre ouvert !", rw.coins, rw.gems, rw.xp, onDismiss = { viewModel.dismissChestReward() })
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            // Resource bar
            ResourceBar(user.level, user.xp, user.xpNext, user.coins, user.gems)

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                // Header
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Bonjour, ${user.pseudo} 👋", color = c.textPrimary,
                            fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Reste focus et progresse !", color = c.textSecondary, fontSize = 13.sp)
                    }
                    StreakBadge(user.streakDays)
                }

                Spacer(Modifier.height(20.dp))

                // Action du jour
                SectionTitle("Action du jour")
                SankaiCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(AccentViolet.copy(0.2f)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.TrackChanges, null, tint = AccentViolet, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Session Focus recommandée", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("25 min • +50 XP après", color = c.textSecondary, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    SankaiButton("▶  Commencer", onClick = { onNavigate(Screen.Focus.route) },
                        modifier = Modifier.fillMaxWidth())
                }

                // Coffres
                SectionTitle("Coffres (${chests.size}/4)")
                ChestsRow(chests = chests, onOpen = { viewModel.openChest(it) },
                    formatTimer = { viewModel.formatChestTimer(it) })

                // Actions rapides
                SectionTitle("Actions rapides")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionButton(
                        icon = Icons.Filled.PlayArrow,
                        label = "Pub",
                        sublabel = if (adCd > 0) "${adCd}s" else "+5🪙",
                        color = SuccessGreen,
                        enabled = adCd <= 0,
                        modifier = Modifier.weight(1f)
                    ) { viewModel.watchAd() }

                    QuickActionButton(
                        icon = Icons.Filled.Bolt,
                        label = "Focus",
                        sublabel = "+50 XP",
                        color = AccentViolet,
                        modifier = Modifier.weight(1f)
                    ) { onNavigate(Screen.Focus.route) }

                    QuickActionButton(
                        icon = Icons.Filled.TrackChanges,
                        label = "Défis",
                        sublabel = "Réclamer",
                        color = AccentGold,
                        modifier = Modifier.weight(1f)
                    ) { onNavigate(Screen.Challenges.route) }
                }

                // Stats jour
                SectionTitle("Aujourd'hui")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("+${viewModel.todayXp.collectAsState().value} XP", "Gagné", AccentViolet, Modifier.weight(1f))
                    StatCard("+${viewModel.todayCoins.collectAsState().value}🪙", "Pièces", CoinColor, Modifier.weight(1f))
                    StatCard("${user.streakDays}j", "Streak", WarningAmber, Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // Toast overlay
        AnimatedVisibility(
            visible = toast.isNotBlank(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp),
            enter = fadeIn() + slideInVertically { it },
            exit  = fadeOut() + slideOutVertically { it }
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(24.dp)).background(c.surface2)
                    .border(1.dp, c.accent, RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(toast, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ChestsRow(chests: List<ChestEntity>, onOpen: (Long) -> Unit, formatTimer: (ChestEntity) -> String) {
    val c = MaterialTheme.sankaiColors
    // Refresh timer every second
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(4) { slot ->
            val chest = chests.find { it.slotIndex == slot }
            ChestSlotUI(chest = chest, onOpen = { chest?.let { onOpen(it.id) } },
                timer = chest?.let { if (tick >= 0) formatTimer(it) else "" } ?: "",
                modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun ChestSlotUI(chest: ChestEntity?, onOpen: () -> Unit, timer: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.sankaiColors
    val chestColor = when (chest?.type) {
        "RARE"       -> ChestRare
        "EPIC"       -> ChestEpic
        "LEGENDARY"  -> ChestLegendary
        "DAILY"      -> ChestDaily
        else         -> if (chest != null) ChestCommon else c.surface3
    }
    val isReady = chest?.isReady == true
    Box(
        modifier = modifier.height(100.dp).clip(RoundedCornerShape(12.dp))
            .background(if (chest != null) chestColor.copy(alpha = 0.15f) else c.surface2)
            .border(1.dp, if (isReady) chestColor else c.border, RoundedCornerShape(12.dp))
            .then(if (isReady) Modifier.clickable { onOpen() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (chest == null) {
            Text("—", color = c.textDisabled, fontSize = 20.sp)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
                Text(
                    when(chest.type) { "RARE"->"🟦"; "EPIC"->"💜"; "DAILY"->"🎁"; "LEGENDARY"->"👑"; else->"📦" },
                    fontSize = 26.sp
                )
                Spacer(Modifier.height(4.dp))
                if (isReady) {
                    Text("OUVRIR", color = chestColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text(timer, color = c.textSecondary, fontSize = 10.sp)
                }
                Text(chest.type, color = chestColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sublabel: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    Box(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(if (enabled) color.copy(0.12f) else c.surface2)
            .border(1.dp, if (enabled) color.copy(0.4f) else c.border, RoundedCornerShape(12.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (enabled) color else c.textDisabled, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label,    color = if (enabled) c.textPrimary else c.textDisabled, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(sublabel, color = if (enabled) color else c.textDisabled, fontSize = 10.sp)
        }
    }
}

@Composable
fun StatCard(value: String, label: String, valueColor: Color, modifier: Modifier = Modifier) {
    val c = MaterialTheme.sankaiColors
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(c.surface2).border(0.5.dp, c.border, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(value, color = valueColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(label, color = c.textSecondary, fontSize = 10.sp)
        }
    }
}
