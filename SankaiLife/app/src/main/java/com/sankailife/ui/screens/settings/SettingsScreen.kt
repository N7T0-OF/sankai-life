package com.sankailife.ui.screens.settings

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
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.components.SectionTitle
import com.sankailife.ui.theme.*

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val themeMode   by viewModel.themeMode.collectAsState()
    val showLabels  by viewModel.showNavLabels.collectAsState()
    val vibrations  by viewModel.vibrations.collectAsState()
    val notifs      by viewModel.notifications.collectAsState()
    val battery     by viewModel.batterySaver.collectAsState()
    val streak      by viewModel.streakReminder.collectAsState()
    val c = MaterialTheme.sankaiColors

    var showReset by remember { mutableStateOf(false) }
    var resetCount by remember { mutableIntStateOf(0) }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false; resetCount = 0 },
            title = { Text("Réinitialiser ?", color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text  = { Text("Toute ta progression sera perdue. Cette action est irréversible. Appuie 3× pour confirmer.", color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    resetCount++
                    if (resetCount >= 3) { viewModel.resetProgress(); showReset = false; resetCount = 0 }
                }) { Text("Confirmer (${3 - resetCount}×)", color = DangerRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showReset = false; resetCount = 0 }) { Text("Annuler") } },
            containerColor = c.surface2
        )
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        // TopBar
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = c.textSecondary) }
            Text("Paramètres", color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {

            // Thème
            SectionTitle("Apparence")
            SettingsCard {
                Text("Thème UI", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("dark" to "🌑 Sombre", "light" to "☀️ Clair", "auto" to "⚡ Auto").forEach { (mode, label) ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (themeMode == mode) c.accent.copy(0.15f) else c.surface3)
                                .border(1.dp, if (themeMode == mode) c.accent else c.border, RoundedCornerShape(10.dp))
                                .clickable { viewModel.setThemeMode(mode) }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(label, color = if (themeMode == mode) c.accent else c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SettingToggle("Afficher labels navigation", showLabels) { viewModel.setShowNavLabels(it) }
                SettingToggle("Mode économie batterie", battery) { viewModel.setBatterySaver(it) }
            }

            SectionTitle("Notifications")
            SettingsCard {
                SettingToggle("Notifications actives", notifs)   { viewModel.setNotifications(it) }
                SettingToggle("Rappel streak",         streak)   { viewModel.setStreakReminder(it) }
                SettingToggle("Vibrations interface",  vibrations) { viewModel.setVibrations(it) }
            }

            SectionTitle("Liens")
            SettingsCard {
                SettingLink("🌐 haunt.gg/souanpt",    "Site Souanpt", enabled = true)
                Spacer(Modifier.height(8.dp))
                SettingLink("☕ ko-fi.com/souanpt",   "Ko-fi",         enabled = true)
            }

            SectionTitle("Données")
            SettingsCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Réinitialiser progression", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Efface XP, pièces, niveaux", color = c.textSecondary, fontSize = 11.sp)
                    }
                    SankaiButton("Reset", onClick = { showReset = true; resetCount = 0 }, small = true,
                        secondary = true)
                }
            }

            SectionTitle("À propos")
            SettingsCard {
                Text("Sankai Life", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Version 1.0.0", color = c.textSecondary, fontSize = 12.sp)
                Text("Par Souanpt", color = c.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.sankaiColors
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface2)
        .border(0.5.dp, c.border, RoundedCornerShape(16.dp)).padding(14.dp).padding(bottom = 4.dp)) {
        Column(content = content)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun SettingToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.sankaiColors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.textPrimary, fontSize = 14.sp)
        Switch(checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = c.accent, checkedTrackColor = c.accent.copy(0.3f)))
    }
}

@Composable
fun SettingLink(url: String, label: String, enabled: Boolean) {
    val c = MaterialTheme.sankaiColors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(label, color = if (enabled) c.textPrimary else c.textDisabled, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(url,   color = c.textSecondary, fontSize = 11.sp)
        }
        Icon(Icons.Filled.OpenInNew, null, tint = if (enabled) c.accent else c.textDisabled, modifier = Modifier.size(18.dp))
    }
}
