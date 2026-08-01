package com.sankailife.ui.screens.life.memo

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.sp
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.components.SectionTitle
import com.sankailife.ui.theme.*

/** Ligne « libellé — valeur — moins/plus », utilisée par tous les réglages d'heure. */
@Composable
private fun ReglageValeur(
    libelle: String,
    valeur: String,
    onMoins: () -> Unit,
    onPlus: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface2)
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(libelle, color = c.textSecondary, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMoins, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Remove, "Diminuer",
                    modifier = Modifier.size(18.dp), tint = c.textPrimary)
            }
            Text(
                valeur, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 68.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = onPlus, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Add, "Augmenter",
                    modifier = Modifier.size(18.dp), tint = c.textPrimary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoEditorScreen(profileId: Long, viewModel: MemoViewModel, onBack: () -> Unit) {
    val c = MaterialTheme.sankaiColors
    LaunchedEffect(profileId) { viewModel.loadProfile(profileId) }

    val name        by viewModel.profileName.collectAsState()
    val lines       by viewModel.currentLines.collectAsState()
    val freq        by viewModel.frequency.collectAsState()
    val hour        by viewModel.hour.collectAsState()
    val minute      by viewModel.minute.collectAsState()
    val newLineText by viewModel.newLineText.collectAsState()
    val jours       by viewModel.activeDays.collectAsState()
    val aleatoire   by viewModel.randomMode.collectAsState()
    val plageDebut  by viewModel.randomStart.collectAsState()
    val plageFin    by viewModel.randomEnd.collectAsState()
    val context     = LocalContext.current

    var showPasteConfirm  by remember { mutableStateOf(false) }
    var clipboardContent  by remember { mutableStateOf("") }
    var pasteLineCount    by remember { mutableIntStateOf(0) }

    if (showPasteConfirm) {
        AlertDialog(
            onDismissRequest = { showPasteConfirm = false },
            title = { Text("Importer $pasteLineCount phrases ?", color = c.textPrimary) },
            text  = { Text("Les doublons seront ignorés automatiquement.", color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.pasteFromClipboard(clipboardContent); showPasteConfirm = false }) {
                    Text("Importer", color = c.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showPasteConfirm = false }) { Text("Annuler") } },
            containerColor = c.surface2
        )
    }

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = { Text("Éditeur mémo", color = c.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.saveProfile(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.textSecondary)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.saveProfile(); onBack() }) {
                        Text("💾 Sauver", color = c.accent, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Nom du profil
            item {
                SectionTitle("Nom du profil")
                OutlinedTextField(
                    value = name,
                    onValueChange = { viewModel.setName(it) },
                    placeholder = { Text("Ex: Motivation, Travail...", color = c.textDisabled) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent, unfocusedBorderColor = c.border,
                        focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                        cursorColor = c.accent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Paramètres notif
            item {
                SectionTitle("Notifications")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Fréquence
                    Column(Modifier.weight(1f)) {
                        Text("Fréquence", color = c.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(c.surface2).border(1.dp, c.border, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("${freq}×/jour", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Row {
                                    IconButton(onClick = { if (freq > 1) viewModel.setFrequency(freq - 1) }, Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Remove, null, modifier = Modifier.size(16.dp), tint = c.textPrimary)
                                    }
                                    IconButton(onClick = { if (freq < 5) viewModel.setFrequency(freq + 1) }, Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp), tint = c.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                    // Heure — désactivée en mode aléatoire, où elle n'a plus de sens
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (aleatoire) "Heure (ignorée)" else "Heure",
                            color = c.textSecondary, fontSize = 12.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(c.surface2).border(1.dp, c.border, RoundedCornerShape(12.dp))
                            .alpha(if (aleatoire) 0.4f else 1f).padding(12.dp)) {
                            Text("%02d:%02d".format(hour, minute), color = c.textPrimary,
                                fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Heures et minutes séparées : un pas de 5 minutes couvre les
                // besoins réels sans imposer 60 appuis pour traverser une heure.
                if (!aleatoire) {
                    Spacer(Modifier.height(12.dp))
                    ReglageValeur(
                        libelle = "Heure",
                        valeur = "%02d h".format(hour),
                        onMoins = { viewModel.setHour(hour - 1) },
                        onPlus = { viewModel.setHour(hour + 1) }
                    )
                    Spacer(Modifier.height(8.dp))
                    ReglageValeur(
                        libelle = "Minutes",
                        valeur = "%02d min".format(minute),
                        onMoins = { viewModel.setMinute(minute - 5) },
                        onPlus = { viewModel.setMinute(minute + 5) }
                    )
                }

                // Jours de la semaine
                Spacer(Modifier.height(16.dp))
                Text("Jours actifs", color = c.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val libelles = listOf("L", "M", "M", "J", "V", "S", "D")
                    libelles.forEachIndexed { index, libelle ->
                        val jour = index + 1
                        val actif = jour in jours
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (actif) c.accent.copy(0.18f) else c.surface2)
                                .border(
                                    1.dp,
                                    if (actif) c.accent else c.border,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { viewModel.toggleDay(jour) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                libelle,
                                color = if (actif) c.accent else c.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Mode aléatoire
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Heure aléatoire", color = c.textPrimary,
                            fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Une heure différente chaque jour, tirée dans la plage choisie",
                            color = c.textSecondary, fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = aleatoire,
                        onCheckedChange = { viewModel.setRandomMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = c.accent,
                            checkedTrackColor = c.accent.copy(0.3f)
                        )
                    )
                }

                if (aleatoire) {
                    Spacer(Modifier.height(10.dp))
                    ReglageValeur(
                        libelle = "Début de plage",
                        valeur = "%02dh%02d".format(plageDebut / 60, plageDebut % 60),
                        onMoins = { viewModel.setRandomStart(plageDebut - 30) },
                        onPlus = { viewModel.setRandomStart(plageDebut + 30) }
                    )
                    Spacer(Modifier.height(8.dp))
                    ReglageValeur(
                        libelle = "Fin de plage",
                        valeur = "%02dh%02d".format(plageFin / 60, plageFin % 60),
                        onMoins = { viewModel.setRandomEnd(plageFin - 30) },
                        onPlus = { viewModel.setRandomEnd(plageFin + 30) }
                    )
                    if (plageFin <= plageDebut) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "La fin doit être après le début, sinon la plage est ignorée.",
                            color = WarningAmber, fontSize = 11.sp
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Les heures silencieuses définies dans les paramètres " +
                    "s'appliquent toujours, quel que soit le mode.",
                    color = c.textDisabled, fontSize = 11.sp
                )
            }

            // Import clipboard
            item {
                SectionTitle("Importer")
                SankaiButton(
                    text = "📋 Coller depuis presse-papier",
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        if (text.isNotBlank()) {
                            clipboardContent = text
                            pasteLineCount = text.split("\n").filter { it.isNotBlank() }.size
                            showPasteConfirm = true
                        }
                    },
                    secondary = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
            }

            // Phrases
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("PHRASES".uppercase(), color = c.textSecondary, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text("${lines.size} lignes", color = c.textSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
            }

            // Add new line
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newLineText,
                        onValueChange = { viewModel.setNewLineText(it) },
                        placeholder = { Text("Nouvelle phrase...", color = c.textDisabled, fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.accent, unfocusedBorderColor = c.border,
                            focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                            cursorColor = c.accent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    IconButton(
                        onClick = { viewModel.addLine(newLineText) },
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(c.accent)
                    ) {
                        Icon(Icons.Filled.Add, null, tint = androidx.compose.ui.graphics.Color.Black)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Line list
            items(lines) { line ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
                        .background(c.surface2).border(0.5.dp, c.border, RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${lines.indexOf(line) + 1}.", color = c.textDisabled, fontSize = 12.sp,
                        modifier = Modifier.width(28.dp))
                    Text(line.text, color = c.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.deleteLine(line) }, Modifier.size(32.dp)) {
                        Icon(Icons.Filled.DeleteOutline, null, modifier = Modifier.size(16.dp), tint = DangerRed)
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
