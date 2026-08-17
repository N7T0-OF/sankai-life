package com.sankailife.ui.screens.settings

import android.os.Build
import android.app.Activity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankailife.R
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.core.notifications.QuietHours
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.components.SectionTitle
import com.sankailife.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    /**
     * Conserve pour la navigation, sans bouton dans cet ecran.
     *
     * Les themes se choisissent uniquement dans Profil, Personnalisation. Le
     * parametre reste dans la signature parce que la navigation le fournit
     * deja, et le retirer obligerait a modifier le graphe pour rien.
     */
    @Suppress("UNUSED_PARAMETER") onGererThemes: () -> Unit = {}
) {
    val themeMode   by viewModel.themeMode.collectAsStateWithLifecycle()
    val showLabels  by viewModel.showNavLabels.collectAsStateWithLifecycle()
    val vibrations  by viewModel.vibrations.collectAsStateWithLifecycle()
    val notifs      by viewModel.notifications.collectAsStateWithLifecycle()
    val battery     by viewModel.batterySaver.collectAsStateWithLifecycle()
    val lectureAuto by viewModel.lectureAuto.collectAsStateWithLifecycle()
    val vitesseVoix by viewModel.vitesseVoix.collectAsStateWithLifecycle()
    val repetitions by viewModel.repetitionsVoix.collectAsStateWithLifecycle()
    val quietOn     by viewModel.quietEnabled.collectAsStateWithLifecycle()
    val quietStart  by viewModel.quietStart.collectAsStateWithLifecycle()
    val quietEnd    by viewModel.quietEnd.collectAsStateWithLifecycle()
    val minimalMode by viewModel.minimalMode.collectAsStateWithLifecycle()
    val dailyMinutes by viewModel.dailyMinutes.collectAsStateWithLifecycle()
    val notificationMax by viewModel.notificationDailyMax.collectAsStateWithLifecycle()
    val notificationPauseUntil by viewModel.notificationPauseUntil.collectAsStateWithLifecycle()
    val weekendQuiet by viewModel.weekendQuiet.collectAsStateWithLifecycle()
    val notifyLearning by viewModel.notifyLearning.collectAsStateWithLifecycle()
    val notifyMemo by viewModel.notifyMemo.collectAsStateWithLifecycle()
    val notifyCulture by viewModel.notifyCulture.collectAsStateWithLifecycle()
    val notifyFocus by viewModel.notifyFocus.collectAsStateWithLifecycle()
    val diag        by viewModel.diagnostic.collectAsStateWithLifecycle()
    val enLigne     by viewModel.isOnline.collectAsStateWithLifecycle()
    val etatMaj     by viewModel.maj.collectAsStateWithLifecycle()
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
            title = { Text(stringResource(R.string.settings_reset_game_title), color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text  = { Text(stringResource(R.string.settings_reset_game_body), color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    resetCount++
                    if (resetCount >= 3) { viewModel.resetProgress(); showReset = false; resetCount = 0 }
                }) { Text(stringResource(R.string.settings_reset_confirm, 3 - resetCount), color = DangerRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showReset = false; resetCount = 0 }) { Text(stringResource(R.string.action_cancel)) } },
            containerColor = c.surface2
        )
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        // TopBar
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.textSecondary) }
            Text("Paramètres", color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {

            SectionTitle(stringResource(R.string.wellbeing_title))
            SettingsCard {
                SettingToggle(
                    stringResource(R.string.wellbeing_minimal_mode),
                    minimalMode,
                    viewModel::setMinimalMode
                )
                Text(
                    stringResource(R.string.wellbeing_minimal_hint),
                    color = c.textSecondary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.wellbeing_daily_time),
                    color = c.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(2, 5, 10, 15).forEach { minutes ->
                        FilterChip(
                            selected = dailyMinutes == minutes,
                            onClick = { viewModel.setDailyMinutes(minutes) },
                            label = { Text("$minutes min", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                FilterChip(
                    selected = dailyMinutes == 0,
                    onClick = { viewModel.setDailyMinutes(0) },
                    label = { Text(stringResource(R.string.wellbeing_no_goal)) }
                )
            }

            // Thème
            SectionTitle("Langue")
            SettingsCard { LangueSection() }

            SectionTitle("Apparence")
            val palette by viewModel.palette.collectAsStateWithLifecycle()
            val dynamiqueDispo = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            // Les palettes sont parties dans Profil, Personnalisation.
            //
            // Elles apparaissaient ici **et** dans la collection : deux endroits
            // pour un meme reglage, donc deux endroits a tenir a jour et une
            // hesitation a chaque fois. La collection les garde, parce que c'est
            // la qu'on choisit de quoi l'application a l'air ; les parametres
            // gardent ce qui reste un reglage : clair, sombre, animations.

            // Audio d'apprentissage.
            //
            // Dans les parametres et non dans chaque module : c'est une
            // preference de personne, pas de contenu. Quelqu'un qui revise dans
            // le train coupe la lecture une fois, pas six.
            SettingsCard {
                Text("Audio d'apprentissage", color = c.textPrimary, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Utilise la voix installée sur ton téléphone. Sans voix pour " +
                        "une langue, l'écoute reste simplement indisponible.",
                    color = c.textSecondary, fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))
                SettingToggle("Lire chaque nouveau mot", lectureAuto) {
                    viewModel.setLectureAuto(it)
                }
                Spacer(Modifier.height(10.dp))
                Text("Vitesse de lecture", color = c.textPrimary, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("lente" to "Lente", "normale" to "Normale", "rapide" to "Rapide")
                        .forEach { (cle, libelle) ->
                            ChoixAudio(
                                libelle = libelle,
                                choisi = vitesseVoix == cle,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.setVitesseVoix(cle) }
                            )
                        }
                }
                Spacer(Modifier.height(10.dp))
                Text("Répéter automatiquement", color = c.textPrimary, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Jamais", 1 to "1 fois", 2 to "2 fois").forEach { (n, libelle) ->
                        ChoixAudio(
                            libelle = libelle,
                            choisi = repetitions == n,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setRepetitionsVoix(n) }
                        )
                    }
                }
            }

            SettingsCard {
                // « Mode d'affichage » et non « Theme » : c'est le seul endroit
                // ou l'on choisit clair, sombre ou AMOLED, et l'appeler theme
                // laissait croire a un doublon de la collection alors que les
                // deux reglages ne font pas la meme chose.
                Text("Mode d'affichage", color = c.textPrimary, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // AMOLED est un mode a part, pas une nuance de sombre : il eteint
                    // reellement les pixels d'une dalle OLED.
                    listOf(
                        "dark" to "🌑 Sombre",
                        "amoled" to "⬛ AMOLED",
                        "light" to "☀️ Clair",
                        "auto" to "⚡ Auto"
                    ).forEach { (mode, label) ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (themeMode == mode) c.accent.copy(0.15f) else c.surface3)
                                .border(1.dp, if (themeMode == mode) c.accent else c.border, RoundedCornerShape(10.dp))
                                .clickable { viewModel.setThemeMode(mode) }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(label, color = if (themeMode == mode) c.accent else c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                    }
                }
                // Le bouton vers la collection est parti.
                //
                // Les themes se choisissent dans Profil, Personnalisation, et
                // nulle part ailleurs. Un raccourci ici remettait deux chemins
                // pour une meme chose, ce qu'on venait de corriger.
                Spacer(Modifier.height(12.dp))
                SettingToggle("Afficher labels navigation", showLabels) { viewModel.setShowNavLabels(it) }
                SettingToggle("Mode économie batterie", battery) { viewModel.setBatterySaver(it) }
            }

            SectionTitle("Notifications")
            SettingsCard {
                SettingToggle(stringResource(R.string.settings_notifications_active), notifs) { viewModel.setNotifications(it) }
                SettingToggle(stringResource(R.string.settings_interface_vibrations), vibrations) { viewModel.setVibrations(it) }
                if (notifs) {
                    Spacer(Modifier.height(8.dp))
                    SettingToggle(stringResource(R.string.wellbeing_notify_learning), notifyLearning) {
                        viewModel.setNotifyLearning(it)
                    }
                    SettingToggle(stringResource(R.string.wellbeing_notify_memo), notifyMemo) {
                        viewModel.setNotifyMemo(it)
                    }
                    SettingToggle(stringResource(R.string.wellbeing_notify_culture), notifyCulture) {
                        viewModel.setNotifyCulture(it)
                    }
                    SettingToggle(stringResource(R.string.wellbeing_notify_focus), notifyFocus) {
                        viewModel.setNotifyFocus(it)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.wellbeing_notification_limit),
                        color = c.textPrimary,
                        fontSize = 13.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..3).forEach { maximum ->
                            FilterChip(
                                selected = notificationMax == maximum,
                                onClick = { viewModel.setNotificationDailyMax(maximum) },
                                label = { Text(maximum.toString()) }
                            )
                        }
                    }
                    SettingToggle(
                        stringResource(R.string.wellbeing_weekend_quiet),
                        weekendQuiet,
                        viewModel::setWeekendQuiet
                    )
                    Spacer(Modifier.height(8.dp))
                    SankaiButton(
                        text = if (notificationPauseUntil >= java.time.LocalDate.now().toEpochDay()) {
                            stringResource(R.string.wellbeing_resume)
                        } else {
                            stringResource(R.string.wellbeing_pause_7_days)
                        },
                        onClick = {
                            if (notificationPauseUntil >= java.time.LocalDate.now().toEpochDay()) {
                                viewModel.resumeNotifications()
                            } else {
                                viewModel.pauseNotifications(7)
                            }
                        },
                        secondary = true,
                        small = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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

            // La sauvegarde vient AVANT la réinitialisation, délibérément :
            // quelqu'un qui vient effacer sa progression doit voir d'abord
            // qu'il peut la mettre à l'abri.
            SectionTitle("Données et sauvegarde")
            SettingsCard { SauvegardeSection() }

            SettingsCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.settings_reset_game_label), color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.settings_reset_game_hint), color = c.textSecondary, fontSize = 11.sp)
                    }
                    SankaiButton(stringResource(R.string.settings_reset_action), onClick = { showReset = true; resetCount = 0 }, small = true,
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
            Icons.AutoMirrored.Filled.OpenInNew, null,
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

@Composable
private fun ChoixAudio(
    libelle: String,
    choisi: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (choisi) c.accent.copy(alpha = 0.15f) else c.surface3)
            .border(
                1.dp,
                if (choisi) c.accent else c.border,
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            libelle,
            color = if (choisi) c.accent else c.textSecondary,
            fontSize = 11.sp, fontWeight = FontWeight.Medium
        )
    }
}
