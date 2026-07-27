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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.core.notifications.QuietHours
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
    val quietOn     by viewModel.quietEnabled.collectAsState()
    val quietStart  by viewModel.quietStart.collectAsState()
    val quietEnd    by viewModel.quietEnd.collectAsState()
    val diag        by viewModel.diagnostic.collectAsState()
    val enLigne     by viewModel.isOnline.collectAsState()
    val etatMaj     by viewModel.maj.collectAsState()
    val c = MaterialTheme.sankaiColors
    val contexte = LocalContext.current

    // Les permissions peuvent avoir changé pendant que l'utilisateur était
    // dans les réglages Android : on relit l'état à chaque affichage.
    LaunchedEffect(Unit) { viewModel.rafraichirDiagnostic() }

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

            SectionTitle("Heures silencieuses")
            SettingsCard {
                SettingToggle("Activer les heures silencieuses", quietOn) { viewModel.setQuietEnabled(it) }
                Text(
                    "Aucun mémo ni rappel pendant cette plage. L'application reste " +
                    "utilisable : Android ne permet pas à une app de s'éteindre seule.",
                    color = c.textSecondary, fontSize = 11.sp
                )
                if (quietOn) {
                    Spacer(Modifier.height(12.dp))
                    SelecteurHeure("Début", quietStart) { viewModel.setQuietStart(it) }
                    Spacer(Modifier.height(8.dp))
                    SelecteurHeure("Fin", quietEnd) { viewModel.setQuietEnd(it) }
                    if (quietStart > quietEnd) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "La plage traverse minuit : de ${QuietHours.formater(quietStart)} " +
                            "à ${QuietHours.formater(quietEnd)} le lendemain.",
                            color = c.textSecondary, fontSize = 11.sp
                        )
                    }
                }
            }

            SectionTitle("Diagnostic des notifications")
            SettingsCard {
                LigneDiagnostic("Permission notifications", diag.notificationsAutorisees)
                LigneDiagnostic("Alarmes exactes", diag.alarmesExactes)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (diag.prochaine.isBlank()) "Aucune notification programmée"
                    else "Prochaine : ${diag.prochaine}",
                    color = c.textSecondary, fontSize = 12.sp
                )
                if (!diag.alarmesExactes) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Sans cette autorisation, un mémo prévu à 22h00 peut arriver " +
                        "avec quelques minutes de retard.",
                        color = WarningAmber, fontSize = 11.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    SankaiButton("Autoriser les alarmes exactes",
                        onClick = { viewModel.ouvrirReglageAlarmes(contexte) },
                        small = true, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SankaiButton("Notification test", onClick = { viewModel.envoyerNotificationTest(contexte) },
                        small = true, secondary = true, modifier = Modifier.weight(1f))
                    SankaiButton("Reprogrammer", onClick = { viewModel.reprogrammerTout(contexte) },
                        small = true, secondary = true, modifier = Modifier.weight(1f))
                }
            }

            SectionTitle("Liens")
            SettingsCard {
                SettingLink(
                    url = "https://haunt.gg/souanpt",
                    label = "Site Souanpt", emoji = "🌐", enabled = enLigne
                )
                SettingLink(
                    url = "https://ko-fi.com/souanpt",
                    label = "Soutenir sur Ko-fi", emoji = "☕", enabled = enLigne
                )
                if (!enLigne) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ces liens sont les seules parties de l'app qui ont besoin " +
                        "d'internet. Tout le reste fonctionne hors ligne.",
                        color = c.textSecondary, fontSize = 11.sp
                    )
                }
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

            SectionTitle("Mises à jour")
            SettingsCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Version installée", color = c.textPrimary, fontSize = 14.sp)
                    Text(viewModel.versionInstallee, color = c.textSecondary,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                if (etatMaj.message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        etatMaj.message,
                        color = if (etatMaj.disponible != null) c.accent else c.textSecondary,
                        fontSize = 12.sp
                    )
                }

                etatMaj.disponible?.let { dispo ->
                    if (dispo.nouveautes.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        dispo.nouveautes.take(5).forEach { ligne ->
                            Text("• $ligne", color = c.textSecondary, fontSize = 12.sp)
                        }
                    }
                }

                if (etatMaj.telechargement) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { etatMaj.progression },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = c.accent, trackColor = c.surface3
                    )
                    Text("${(etatMaj.progression * 100).toInt()} %",
                        color = c.textSecondary, fontSize = 11.sp)
                }

                Spacer(Modifier.height(12.dp))
                when {
                    etatMaj.disponible != null && !etatMaj.telechargement ->
                        SankaiButton(
                            "⬇  Télécharger et installer",
                            onClick = { viewModel.telechargerMaj(contexte) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    else ->
                        SankaiButton(
                            if (etatMaj.recherche) "Recherche…" else "🔄  Rechercher une mise à jour",
                            onClick = { viewModel.rechercherMaj() },
                            enabled = !etatMaj.recherche && !etatMaj.telechargement && enLigne,
                            secondary = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    "Android demandera toujours ta confirmation avant d'installer. " +
                    "Aucune mise à jour ne s'installe en silence.",
                    color = c.textDisabled, fontSize = 11.sp
                )
            }

            SectionTitle("À propos")
            SettingsCard {
                Text("Sankai Life", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Version ${viewModel.versionInstallee}", color = c.textSecondary, fontSize = 12.sp)
                Text("Par Souanpt", color = c.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Sélecteur d'heure et de minutes, par pas de 15 minutes. */
@Composable
private fun SelecteurHeure(libelle: String, minutes: Int, onChange: (Int) -> Unit) {
    val c = MaterialTheme.sankaiColors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(libelle, color = c.textPrimary, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SankaiButton("−", onClick = { onChange((minutes - 15 + 1440) % 1440) }, small = true, secondary = true)
            Text(
                QuietHours.formater(minutes),
                color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            SankaiButton("+", onClick = { onChange((minutes + 15) % 1440) }, small = true, secondary = true)
        }
    }
}

@Composable
private fun LigneDiagnostic(libelle: String, ok: Boolean) {
    val c = MaterialTheme.sankaiColors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(libelle, color = c.textPrimary, fontSize = 13.sp)
        Text(
            if (ok) "✓ accordée" else "✗ refusée",
            color = if (ok) SuccessGreen else DangerRed,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
        )
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

/**
 * Lien externe réellement cliquable.
 *
 * [url] doit être une URL complète avec son schéma : sans « https:// »,
 * Android ne trouve aucune application capable d'ouvrir l'intent et le clic
 * ne fait rien.
 */
@Composable
fun SettingLink(url: String, label: String, emoji: String, enabled: Boolean) {
    val c = MaterialTheme.sankaiColors
    val contexte = LocalContext.current
    val haptics = LocalHaptics.current

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (enabled) Modifier.clickable {
                    haptics.click()
                    ouvrirLien(contexte, url)
                } else Modifier
            )
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "$emoji  $label",
                color = if (enabled) c.textPrimary else c.textDisabled,
                fontSize = 14.sp, fontWeight = FontWeight.Medium
            )
            Text(
                if (enabled) url.removePrefix("https://") else "Connexion requise",
                color = c.textSecondary, fontSize = 11.sp
            )
        }
        Icon(
            Icons.Filled.OpenInNew, null,
            tint = if (enabled) c.accent else c.textDisabled,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun ouvrirLien(contexte: android.content.Context, url: String) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse(url)
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    // Aucun navigateur installé : on échoue en silence plutôt que de planter.
    runCatching { contexte.startActivity(intent) }
}
