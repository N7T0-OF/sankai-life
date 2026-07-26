package com.sankailife.ui.screens.life.memo

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.components.SectionTitle
import com.sankailife.ui.theme.*

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
                        Icon(Icons.Filled.ArrowBack, null, tint = c.textSecondary)
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
                                        Icon(Icons.Filled.Remove, null, tint = c.textPrimary, Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { if (freq < 5) viewModel.setFrequency(freq + 1) }, Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Add, null, tint = c.textPrimary, Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    // Heure
                    Column(Modifier.weight(1f)) {
                        Text("Heure", color = c.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(c.surface2).border(1.dp, c.border, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("%02d:%02d".format(hour, minute), color = c.textPrimary,
                                    fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Row {
                                    IconButton(onClick = { viewModel.setHour((hour - 1 + 24) % 24) }, Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Remove, null, tint = c.textPrimary, Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { viewModel.setHour((hour + 1) % 24) }, Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Add, null, tint = c.textPrimary, Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
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
                        Icon(Icons.Filled.DeleteOutline, null, tint = DangerRed, Modifier.size(16.dp))
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
