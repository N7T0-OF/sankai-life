package com.sankailife.ui.screens.profile

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.sankailife.ui.components.*
import com.sankailife.ui.navigation.Screen
import com.sankailife.ui.screens.arenas.CarteResumeArene
import com.sankailife.ui.theme.*

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onNavigate: (String) -> Unit) {
    val user    by viewModel.user.collectAsState()
    val rawUser by viewModel.rawUser.collectAsState()
    val arenesAReclamer by viewModel.arenesAReclamer.collectAsState()
    val c = MaterialTheme.sankaiColors

    val themes = viewModel.getThemes(rawUser?.unlockedThemeIds ?: "default", user.level)
    val equippedTheme = rawUser?.equippedThemeId ?: "default"

    Column(Modifier.fillMaxSize().background(c.background)) {
        ResourceBar(user.level, user.xp, user.xpNext, user.coins, user.gems)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {

            // Avatar + header
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface2)
                .border(1.dp, c.border, RoundedCornerShape(20.dp)).padding(20.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(80.dp).clip(CircleShape).background(AccentViolet),
                        contentAlignment = Alignment.Center) {
                        Text(user.pseudo.take(1).uppercase(), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(user.pseudo, color = c.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(AccentViolet).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text("LVL ${user.level}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        StreakBadge(user.streakDays)
                    }
                    Spacer(Modifier.height(12.dp))
                    XpBar(user.xp, user.xpNext, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text("${user.xp} / ${user.xpNext} XP — Level ${user.level + 1} dans ${user.xpNext - user.xp} XP",
                        color = c.textSecondary, fontSize = 11.sp)
                }
            }

            SectionTitle("Progression")
            CarteResumeArene(
                niveau = user.level,
                nombreAReclamer = arenesAReclamer,
                onVoirParcours = { onNavigate(Screen.Arenas.route) }
            )

            SectionTitle("Statistiques")
            val stats = listOf(
                Triple("${user.totalAdsWatched}", "Pubs vues", c.textPrimary),
                Triple("${user.totalChestsOpened}", "Coffres", CoinColor),
                Triple("${user.totalFocusMinutes / 60}h${user.totalFocusMinutes % 60}", "Focus", AccentViolet),
                Triple("${user.streakDays}j", "Streak", WarningAmber),
                Triple("${rawUser?.totalCoinsEarned ?: 0}🪙", "Gagnées", SuccessGreen),
                Triple("${rawUser?.totalCoinsSpent ?: 0}🪙", "Dépensées", DangerRed)
            )
            @Suppress("UNCHECKED_CAST")
            val chunks = stats.chunked(3)
            chunks.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (value, label, color) ->
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.surface2)
                            .border(0.5.dp, c.border, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(label, color = c.textSecondary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            SectionTitle("Thèmes (${themes.count { it.second }} / ${themes.size})")
            themes.forEach { (theme, isUnlocked) ->
                ThemeRow(theme = theme, isUnlocked = isUnlocked, isEquipped = theme.id == equippedTheme,
                    onEquip = { viewModel.equipTheme(theme.id) })
                Spacer(Modifier.height(6.dp))
            }

            SectionTitle("Ko-fi")
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface2)
                .border(1.dp, if (user.level >= 20) AccentGold.copy(0.5f) else c.border, RoundedCornerShape(16.dp))
                .padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("☕", fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Clé Ko-fi", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Accès produit exclusif Souanpt", color = c.textSecondary, fontSize = 12.sp)
                        Text(if (user.level >= 20) "Débloqué ✅" else "Niveau 20 requis (${20 - user.level} niveaux restants)",
                            color = if (user.level >= 20) SuccessGreen else c.textSecondary, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SankaiButton("⚙️ Paramètres", onClick = { onNavigate(Screen.Settings.route) },
                secondary = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ThemeRow(theme: com.sankailife.core.domain.model.Theme, isUnlocked: Boolean, isEquipped: Boolean, onEquip: () -> Unit) {
    val c = MaterialTheme.sankaiColors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(c.surface2).border(1.dp, if (isEquipped) c.accent else c.border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(theme.emoji, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(theme.name, color = if (isUnlocked) c.textPrimary else c.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (!isUnlocked) Text(
                when(theme.unlockType) { "level" -> "Niveau ${theme.unlockLevel}"; "chest_rare" -> "Drop coffre rare"; else -> "Drop coffre épique" },
                color = c.textDisabled, fontSize = 11.sp
            )
        }
        if (isEquipped) {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(c.accent.copy(0.2f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("Équipé", color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else if (isUnlocked) {
            SankaiButton("Appliquer", onClick = onEquip, small = true, secondary = true)
        } else {
            Icon(Icons.Filled.Lock, null, tint = c.textDisabled, modifier = Modifier.size(18.dp))
        }
    }
}
