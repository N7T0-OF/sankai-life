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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankailife.R
import com.sankailife.ui.components.*
import com.sankailife.ui.navigation.Screen
import com.sankailife.ui.theme.*

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onNavigate: (String) -> Unit) {
    val user    by viewModel.user.collectAsStateWithLifecycle()
    val nomThemeEquipe by viewModel.nomThemeEquipe.collectAsStateWithLifecycle()
    val regularite by viewModel.regularite.collectAsStateWithLifecycle()
    val memorisation by viewModel.memorisation.collectAsStateWithLifecycle()
    val minimalMode by viewModel.minimalMode.collectAsStateWithLifecycle()
    val progressionReelle by viewModel.progressionReelle.collectAsStateWithLifecycle()
    val c = MaterialTheme.sankaiColors

    Column(Modifier.fillMaxSize().background(c.background)) {
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
                    if (minimalMode) {
                        Text(
                            stringResource(R.string.profile_rhythm_summary, regularite.sept),
                            color = c.textSecondary,
                            fontSize = 12.sp
                        )
                    } else {
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(AccentViolet)
                            .padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(stringResource(R.string.profile_level, user.level), color = Color.White, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        XpBar(user.xp, user.xpNext, Modifier.fillMaxWidth())
                    }
                }
            }

            // Progression réelle : cinq dimensions descriptives, aucune
            // obligation. Une barre raconte ce que l'utilisateur fait déjà,
            // elle n'exige rien — l'identité de Sankai, pas une dette.
            SectionTitle(stringResource(R.string.progression_title))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface2)
                .border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(14.dp)) {
                Column {
                    progressionReelle.forEach { dim ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${dim.emoji}  ${stringResource(dim.libelle)}",
                                color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium
                            )
                            Text(dim.valeur, color = c.textSecondary, fontSize = 12.sp)
                        }
                        LinearProgressIndicator(
                            progress = { dim.progression },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                            color = c.accent,
                            trackColor = c.surface3
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        stringResource(R.string.progression_hint),
                        color = c.textDisabled, fontSize = 11.sp
                    )
                }
            }

            // Régularité : trois indicateurs plutôt qu'un compteur unique.
            // Un jour manqué casse la série mais laisse la régularité et le
            // record intacts — le sentiment de progression survit à l'accident.
            SectionTitle(stringResource(R.string.stats_personal_rhythm))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface2)
                .border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(14.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.stats_last_7_days), color = c.textSecondary, fontSize = 13.sp)
                        Text("${regularite.sept} %", color = SuccessGreen,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.stats_last_30_days), color = c.textSecondary, fontSize = 13.sp)
                        Text("${regularite.trente} %", color = c.textPrimary,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.stats_last_90_days), color = c.textSecondary, fontSize = 13.sp)
                        Text("${regularite.quatreVingtDix} %", color = c.textPrimary,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.stats_no_streak),
                        color = c.textDisabled, fontSize = 11.sp
                    )
                }
            }

            // Quatre statistiques seulement : au-delà, l'écran devient un
            // tableau de bord et on ne lit plus rien. Le reste est à un clic.
            SectionTitle(stringResource(R.string.stats_title))
            val principales = listOf(
                Triple("${regularite.sept}%", stringResource(R.string.profile_metric_rhythm), SuccessGreen),
                Triple(
                    "${memorisation.maitrisees}/${memorisation.total}",
                    stringResource(R.string.stats_mastered_cards),
                    c.accent
                ),
                Triple(
                    "${memorisation.revisions}",
                    stringResource(R.string.profile_metric_reviews),
                    c.textPrimary
                )
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
            if (!minimalMode) {
                SankaiButton(stringResource(R.string.profile_all_stats),
                    onClick = { onNavigate(Screen.AllStats.route) },
                    secondary = true, small = true, modifier = Modifier.fillMaxWidth())
            }

            // La collection complète vit dans son propre écran : l'afficher
            // ici transformait le profil en catalogue, majoritairement
            // composé d'éléments verrouillés.
            SectionTitle(stringResource(R.string.profile_customization))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface2)
                .border(1.dp, c.border, RoundedCornerShape(16.dp))
                .clickable { onNavigate(Screen.Customization.route) }
                .padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎨", fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_equipped_theme), color = c.textSecondary, fontSize = 11.sp)
                        Text(nomThemeEquipe, color = c.textPrimary,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(stringResource(R.string.profile_customize), color = c.accent,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(12.dp))
            SankaiButton(stringResource(R.string.settings_title), onClick = { onNavigate(Screen.Settings.route) },
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
                when(theme.unlockType) {
                    "level" -> stringResource(R.string.profile_unlock_level, theme.unlockLevel)
                    else -> ""
                },
                color = c.textDisabled, fontSize = 11.sp
            )
        }
        if (isEquipped) {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(c.accent.copy(0.2f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.profile_equipped), color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else if (isUnlocked) {
            SankaiButton(stringResource(R.string.profile_apply), onClick = onEquip, small = true, secondary = true)
        } else {
            Icon(Icons.Filled.Lock, null, tint = c.textDisabled, modifier = Modifier.size(18.dp))
        }
    }
}
