package com.sankailife.ui.screens.life.memo

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.sankailife.R
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
                Icon(Icons.Filled.Remove, stringResource(R.string.memo_editor_decrease),
                    modifier = Modifier.size(18.dp), tint = c.textPrimary)
            }
            Text(
                valeur, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 68.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = onPlus, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Add, stringResource(R.string.memo_editor_increase),
                    modifier = Modifier.size(18.dp), tint = c.textPrimary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoEditorScreen(profileId: Long, viewModel: MemoViewModel, onBack: () -> Unit) {
    val c = MaterialTheme.sankaiColors
    LaunchedEffect(profileId) { viewModel.loadProfile(profileId) }

    val name        by viewModel.profileName.collectAsState()
    val langue      by viewModel.langue.collectAsState()
    val lines       by viewModel.currentLines.collectAsState()
    val freq        by viewModel.frequency.collectAsState()
    val hour        by viewModel.hour.collectAsState()
    val minute      by viewModel.minute.collectAsState()
    val newLineText by viewModel.newLineText.collectAsState()
    val jours       by viewModel.activeDays.collectAsState()
    val aleatoire   by viewModel.randomMode.collectAsState()
    val plageDebut  by viewModel.randomStart.collectAsState()
    val plageFin    by viewModel.randomEnd.collectAsState()
    val sauvegardeEnCours by viewModel.sauvegardeEnCours.collectAsState()
    val message     by viewModel.message.collectAsState()
    val context     = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showPasteConfirm  by remember { mutableStateOf(false) }
    var clipboardContent  by remember { mutableStateOf("") }
    var pasteLineCount    by remember { mutableIntStateOf(0) }

    fun sauvegarderEtRevenir() {
        viewModel.saveProfile(onSaved = onBack)
    }

    // Couvre aussi le geste système. Pendant l'écriture, le retour est absorbé
    // afin que la destination et son ViewModel ne disparaissent pas trop tôt.
    BackHandler {
        if (!sauvegardeEnCours) sauvegarderEtRevenir()
    }

    LaunchedEffect(message) {
        if (message.isNotBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.messageAffiche()
        }
    }

    if (showPasteConfirm) {
        AlertDialog(
            onDismissRequest = { showPasteConfirm = false },
            title = { Text(pluralStringResource(R.plurals.memo_editor_import_confirm, pasteLineCount, pasteLineCount), color = c.textPrimary) },
            text  = { Text(stringResource(R.string.memo_editor_duplicates_ignored), color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.pasteFromClipboard(clipboardContent); showPasteConfirm = false }) {
                    Text(stringResource(R.string.memo_editor_import), color = c.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showPasteConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
            containerColor = c.surface2
        )
    }

    Scaffold(
        containerColor = c.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.memo_editor_title), color = c.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = ::sauvegarderEtRevenir,
                        enabled = !sauvegardeEnCours
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.textSecondary)
                    }
                },
                actions = {
                    TextButton(
                        onClick = ::sauvegarderEtRevenir,
                        enabled = !sauvegardeEnCours
                    ) {
                        Text(
                            if (sauvegardeEnCours) stringResource(R.string.memo_editor_saving)
                            else stringResource(R.string.memo_editor_save),
                            color = c.accent,
                            fontWeight = FontWeight.Bold
                        )
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
                SectionTitle(stringResource(R.string.memo_editor_profile_name))
                OutlinedTextField(
                    value = name,
                    onValueChange = { viewModel.setName(it) },
                    placeholder = { Text(stringResource(R.string.memo_editor_name_placeholder), color = c.textDisabled) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent, unfocusedBorderColor = c.border,
                        focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                        cursorColor = c.accent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Langue du contenu
            item {
                SectionTitle(stringResource(R.string.memo_editor_content_language))
                Text(
                    stringResource(R.string.memo_editor_language_hint),
                    color = c.textSecondary, fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val langues = stringArrayResource(R.array.memo_languages)
                    LANGUES.forEachIndexed { index, code ->
                        val libelle = langues[index]
                        val choisie = langue == code
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (choisie) c.accent.copy(0.18f) else c.surface2)
                                .border(
                                    1.dp,
                                    if (choisie) c.accent else c.border,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { viewModel.setLangue(code) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                libelle,
                                color = if (choisie) c.accent else c.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Paramètres notif
            item {
                SectionTitle(stringResource(R.string.memo_editor_notifications))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Fréquence
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.memo_editor_frequency), color = c.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(c.surface2).border(1.dp, c.border, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.memo_editor_freq_format, freq), color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
                            if (aleatoire) stringResource(R.string.memo_editor_time_ignored)
                            else stringResource(R.string.memo_editor_time),
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
                        libelle = stringResource(R.string.memo_editor_time),
                        valeur = "%02d h".format(hour),
                        onMoins = { viewModel.setHour(hour - 1) },
                        onPlus = { viewModel.setHour(hour + 1) }
                    )
                    Spacer(Modifier.height(8.dp))
                    ReglageValeur(
                        libelle = stringResource(R.string.memo_editor_minutes),
                        valeur = "%02d min".format(minute),
                        onMoins = { viewModel.setMinute(minute - 5) },
                        onPlus = { viewModel.setMinute(minute + 5) }
                    )
                }

                // Jours de la semaine
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.memo_editor_active_days), color = c.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val libelles = stringArrayResource(R.array.memo_days_short)
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
                        Text(stringResource(R.string.memo_editor_random_time), color = c.textPrimary,
                            fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.memo_editor_random_hint),
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
                        libelle = stringResource(R.string.memo_editor_range_start),
                        valeur = "%02dh%02d".format(plageDebut / 60, plageDebut % 60),
                        onMoins = { viewModel.setRandomStart(plageDebut - 30) },
                        onPlus = { viewModel.setRandomStart(plageDebut + 30) }
                    )
                    Spacer(Modifier.height(8.dp))
                    ReglageValeur(
                        libelle = stringResource(R.string.memo_editor_range_end),
                        valeur = "%02dh%02d".format(plageFin / 60, plageFin % 60),
                        onMoins = { viewModel.setRandomEnd(plageFin - 30) },
                        onPlus = { viewModel.setRandomEnd(plageFin + 30) }
                    )
                    if (plageFin <= plageDebut) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.memo_editor_range_invalid),
                            color = WarningAmber, fontSize = 11.sp
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.memo_editor_quiet_hint),
                    color = c.textDisabled, fontSize = 11.sp
                )
            }

            // Import clipboard
            item {
                SectionTitle(stringResource(R.string.memo_editor_import_section))
                SankaiButton(
                    text = stringResource(R.string.memo_editor_paste),
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
                    Text(stringResource(R.string.memo_editor_phrases_header), color = c.textSecondary, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        pluralStringResource(R.plurals.memo_editor_lines, lines.size, lines.size),
                        color = c.textSecondary, fontSize = 11.sp
                    )
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
                        placeholder = { Text(stringResource(R.string.memo_editor_new_line), color = c.textDisabled, fontSize = 13.sp) },
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

/**
 * Langues proposees pour la prononciation.
 *
 * Liste courte et volontairement fermee : le champ sert a choisir une voix,
 * pas a decrire un contenu. Une saisie libre en BCP-47 laisserait taper
 * « portugais » ou « PT-br », qui ne correspondraient a aucune voix installee
 * et donneraient un bouton muet.
 */
private val LANGUES = listOf("", "fr", "en", "es", "pt", "de", "it", "ja")
