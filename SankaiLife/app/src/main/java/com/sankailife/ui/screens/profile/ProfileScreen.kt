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
    val nomThemeEquipe by viewModel.nomThemeEquipe.collectAsState()
    val regularite by viewModel.regularite.collectAsState()
    val c = MaterialTheme.sankaiColors

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

            // Régularité : trois indicateurs plutôt qu'un compteur unique.
            // Un jour manqué casse la série mais laisse la régularité et le
            // record intacts — le sentiment de progression survit à l'accident.
            SectionTitle("Régularité")
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface2)
                .border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(14.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Série actuelle", color = c.textSecondary, fontSize = 13.sp)
                        Text("🔥 ${user.streakDays} jours", color = WarningAmber,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Meilleure série", color = c.textSecondary, fontSize = 13.sp)
                        Text("${user.bestStreak} jours", color = c.textPrimary,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Régularité 30 jours", color = c.textSecondary, fontSize = 13.sp)
                        Text("${regularite.trente} %", color = SuccessGreen,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Boucliers", color = c.textSecondary, fontSize = 13.sp)
                        Text(
                            if (user.streakShields > 0) "🛡️ ".repeat(user.streakShields).trim()
                            else "aucun",
                            color = if (user.streakShields > 0) AccentViolet else c.textDisabled,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Un bouclier absorbe une journée manquée sans casser ta série. " +
                        "Tu en gagnes un tous les 7 jours consécutifs.",
                        color = c.textDisabled, fontSize = 11.sp
                    )
                }
            }

            // Quatre statistiques seulement : au-delà, l'écran devient un
            // tableau de bord et on ne lit plus rien. Le reste est à un clic.
            SectionTitle("Statistiques")
            val principales = listOf(
                Triple("${user.streakDays}j", "Série", WarningAmber),
                Triple("${user.totalFocusMinutes / 60}h${user.totalFocusMinutes % 60}", "Focus", AccentViolet),
                Triple("${user.totalChestsOpened}", "Coffres", CoinColor),
                Triple("${user.totalAdsWatched}", "Pubs vues", SuccessGreen)
            )
            principales.chunked(2).forEach { ligne ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ligne.forEach { (valeur, libelle, couleur) ->
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.surface2)
                            .border(0.5.dp, c.border, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()) {
                                Text(valeur, color = couleur, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(libelle, color = c.textSecondary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            SankaiButton("Voir toutes les statistiques",
                onClick = { onNavigate(Screen.AllStats.route) },
                secondary = true, small = true, modifier = Modifier.fillMaxWidth())

            // La collection complète vit dans son propre écran : l'afficher
            // ici transformait le profil en catalogue, majoritairement
            // composé d'éléments verrouillés.
            SectionTitle("Personnalisation")
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface2)
                .border(1.dp, c.border, RoundedCornerShape(16.dp))
                .clickable { onNavigate(Screen.Customization.route) }
                .padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎨", fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Thème équipé", color = c.textSecondary, fontSize = 11.sp)
                        Text(nomThemeEquipe, color = c.textPrimary,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Personnaliser", color = c.accent,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
