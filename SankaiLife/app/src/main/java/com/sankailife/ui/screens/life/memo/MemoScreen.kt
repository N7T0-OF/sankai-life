package com.sankailife.ui.screens.life.memo

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.components.SectionTitle
import com.sankailife.ui.theme.*

@Composable
fun MemoScreen(viewModel: MemoViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    val profiles by viewModel.profiles.collectAsState()
    val message by viewModel.message.collectAsState()
    val c = MaterialTheme.sankaiColors
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        if (message.isNotBlank()) {
            snackbar.showSnackbar(message)
            viewModel.messageAffiche()
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
      Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, null, tint = c.textSecondary)
                }
                Text("Mémo Intelligent", color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = {
                viewModel.createNewProfile()
                // navigate to editor after creation
            }) {
                Icon(Icons.Filled.Add, null, tint = c.accent)
            }
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                // Import clipboard info card
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(AccentViolet.copy(0.1f)).border(1.dp, AccentViolet.copy(0.3f), RoundedCornerShape(14.dp))
                    .padding(14.dp)) {
                    Column {
                        Text("📋 Astuce presse-papier", color = AccentViolet, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Copie un texte multi-lignes depuis n'importe quelle app, puis colle-le dans un profil — chaque ligne devient une phrase.",
                            color = c.textSecondary, fontSize = 12.sp)
                    }
                }
                SectionTitle("Tes profils mémo (${profiles.size})")
            }

            if (profiles.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(c.surface2).border(1.dp, c.border, RoundedCornerShape(16.dp))
                        .padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📭", fontSize = 40.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Aucun profil mémo", color = c.textSecondary, fontSize = 15.sp)
                            Spacer(Modifier.height(12.dp))
                            SankaiButton("+ Créer mon premier mémo", onClick = {
                                viewModel.createNewProfile()
                            }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            } else {
                items(profiles) { profile ->
                    MemoProfileListCard(
                        profile = profile,
                        onEdit = { onEdit(profile.id) },
                        onToggle = { viewModel.toggleProfile(profile.id, !profile.isActive) },
                        onDelete = { viewModel.deleteProfile(profile.id) },
                        onPartager = {
                            portee.launch {
                                val texte = viewModel.texteAPartager(profile.id)
                                val envoi = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, profile.name)
                                    putExtra(Intent.EXTRA_TEXT, texte)
                                }
                                contexte.startActivity(
                                    Intent.createChooser(envoi, "Partager ${profile.name}")
                                )
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    SankaiButton("+ Nouveau profil", onClick = {
                        viewModel.createNewProfile()
                    }, secondary = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                }
                item { ImportModuleBouton() }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
      }
      SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
fun MemoProfileListCard(
    profile: com.sankailife.core.data.db.entities.MemoProfileEntity,
    onEdit: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit,
    onPartager: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    var showDelete by remember { mutableStateOf(false) }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Supprimer ?", color = c.textPrimary) },
            text  = { Text("Supprimer \"${profile.name}\" et toutes ses phrases ?", color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { onDelete(); showDelete = false }) { Text("Supprimer", color = DangerRed) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Annuler") } },
            containerColor = c.surface2
        )
    }

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface2)
        .border(1.dp, if (profile.isActive) AccentGold.copy(0.5f) else c.border, RoundedCornerShape(16.dp))
        .padding(14.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📖", fontSize = 24.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.name.ifBlank { "Mémo" }, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("${profile.frequencyPerDay}×/jour • ${"%02d".format(profile.scheduledHour)}:${"%02d".format(profile.scheduledMinute)}",
                        color = c.textSecondary, fontSize = 12.sp)
                }
                if (profile.isActive) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(SuccessGreen.copy(0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("ACTIF", color = SuccessGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SankaiButton("✏️ Modifier", onClick = onEdit, secondary = true, small = true, modifier = Modifier.weight(1f))
                SankaiButton(if (profile.isActive) "Désactiver" else "Activer", onClick = onToggle,
                    small = true, modifier = Modifier.weight(1f),
                    secondary = profile.isActive)
                // Partage : ouvre la feuille Android, qui laisse choisir entre
                // copier, envoyer ou enregistrer. Reproduire ce choix dans
                // l'application dupliquerait un menu que le système fait mieux.
                IconButton(onClick = onPartager, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Share, null, tint = c.textSecondary,
                        modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showDelete = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Delete, null, tint = DangerRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
