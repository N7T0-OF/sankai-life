package com.sankailife.ui.screens.life

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.data.db.entities.MemoProfileEntity
import com.sankailife.core.domain.engine.EconomyEngine
import com.sankailife.ui.components.*
import com.sankailife.ui.navigation.Screen
import com.sankailife.core.domain.engine.DeblocageEngine
import com.sankailife.ui.navigation.FeuilleVerrou
import com.sankailife.ui.theme.*

@Composable
fun LifeScreen(viewModel: LifeViewModel, onNavigate: (String) -> Unit) {
    val user     by viewModel.user.collectAsState()
    val profiles by viewModel.memoProfiles.collectAsState()
    val objectifsEnCours by viewModel.objectivesPending.collectAsState()
    val c = MaterialTheme.sankaiColors

    // Le verrou qu'on vient de toucher. Même fiche que dans la navigation :
    // un cadenas doit s'expliquer de la même façon partout, sinon on croit
    // avoir affaire à deux systèmes différents.
    var verrouAffiche by remember { mutableStateOf<DeblocageEngine.Verrou?>(null) }
    verrouAffiche?.let { v ->
        FeuilleVerrou(verrou = v, onFermer = { verrouAffiche = null })
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        ResourceBar(user.level, user.xp, user.xpNext, user.coins, user.gems)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Mode Vie", color = c.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Tes modules actifs", color = c.textSecondary, fontSize = 13.sp)

            // Focus module.
            //
            // Verrouillé jusqu'au niveau 2 : c'est la première chose que les
            // niveaux ouvrent, donc le premier moment où monter en niveau veut
            // dire quelque chose.
            SectionTitle("Focus Timer")
            val verrouFocus = DeblocageEngine.verrou(
                DeblocageEngine.Fonction.FOCUS, user.level
            )
            ModuleCard(
                icon = if (verrouFocus != null) "🔒" else "⏱️",
                title = "Focus Timer",
                subtitle = verrouFocus?.explication
                    ?: "Sessions de concentration Pomodoro",
                isActive = verrouFocus == null,
                accentColor = AccentViolet,
                onToggle = {},
                onEdit = {
                    if (verrouFocus != null) verrouAffiche = verrouFocus
                    else onNavigate(Screen.Focus.route)
                }
            )

            // Objectifs module
            SectionTitle("Objectifs")
            ModuleCard(
                icon = "🎯",
                title = "Objectifs",
                subtitle = if (objectifsEnCours > 0) "$objectifsEnCours en cours"
                           else "Ta checklist personnelle",
                isActive = true,
                accentColor = SuccessGreen,
                onToggle = {},
                onEdit = { onNavigate(Screen.Objectives.route) }
            )

            // Memo module
            SectionTitle("Mémo Intelligent (${profiles.size})")
            if (profiles.isEmpty()) {
                EmptyModuleCard(
                    message = "Aucun profil mémo",
                    actionLabel = "+ Créer un mémo",
                    onClick = { onNavigate(Screen.Memo.route) }
                )
            } else {
                profiles.forEach { profile ->
                    MemoProfileCard(
                        profile = profile,
                        onToggle = { viewModel.toggleMemo(profile.id, !profile.isActive) },
                        onEdit = { onNavigate(Screen.Memo.route) },
                        onReviser = { onNavigate(Screen.Flashcards.createRoute(profile.id)) }
                    )
                }
            }

            // Add module button
            SankaiButton(
                text = "+ Gérer les mémos",
                onClick = { onNavigate(Screen.Memo.route) },
                secondary = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            // Slots info
            SectionTitle("Slots modules")
            SankaiCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Slots modules : ${user.moduleSlots}", color = c.textPrimary,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Prochain slot : ${EconomyEngine.slotCost(user.moduleSlots)} 🪙",
                            color = c.textSecondary, fontSize = 11.sp)
                    }
                    SankaiButton(
                        "Acheter",
                        onClick = { viewModel.buyModuleSlot() },
                        enabled = user.coins >= EconomyEngine.slotCost(user.moduleSlots),
                        small = true, secondary = true
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ModuleCard(icon: String, title: String, subtitle: String, isActive: Boolean,
               accentColor: androidx.compose.ui.graphics.Color,
               onToggle: () -> Unit, onEdit: () -> Unit) {
    val c = MaterialTheme.sankaiColors
    SankaiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(0.15f)), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = c.textSecondary, fontSize = 12.sp)
            }
            Switch(checked = isActive, onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedThumbColor = c.accent, checkedTrackColor = c.accent.copy(0.3f)))
        }
        Spacer(Modifier.height(10.dp))
        SankaiButton("✏️ Modifier", onClick = onEdit, secondary = true, small = true,
            modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun MemoProfileCard(
    profile: MemoProfileEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onReviser: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    SankaiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                .background(AccentGold.copy(0.15f)), contentAlignment = Alignment.Center) {
                Text("📖", fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.name.ifBlank { "Mémo sans nom" }, color = c.textPrimary,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("${profile.frequencyPerDay}×/jour • ${"%02d".format(profile.scheduledHour)}:${"%02d".format(profile.scheduledMinute)}",
                    color = c.textSecondary, fontSize = 12.sp)
            }
            Switch(checked = profile.isActive, onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedThumbColor = AccentGold, checkedTrackColor = AccentGold.copy(0.3f)))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SankaiButton("✏️ Modifier", onClick = onEdit, secondary = true, small = true,
                modifier = Modifier.weight(1f))
            SankaiButton("🃏 Réviser", onClick = onReviser, small = true,
                modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun EmptyModuleCard(message: String, actionLabel: String, onClick: () -> Unit) {
    val c = MaterialTheme.sankaiColors
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(c.surface2).border(1.dp, c.border, RoundedCornerShape(16.dp))
        .clickable { onClick() }.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📭", fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text(message, color = c.textSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text(actionLabel, color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
