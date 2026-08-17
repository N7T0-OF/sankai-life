package com.sankailife.ui.screens.life.objectives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sankailife.R
import androidx.compose.ui.unit.sp
import com.sankailife.core.data.db.entities.ObjectiveEntity
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.components.SectionTitle
import com.sankailife.ui.theme.AccentGold
import com.sankailife.ui.theme.DangerRed
import com.sankailife.ui.theme.SuccessGreen
import com.sankailife.ui.theme.sankaiColors

@Composable
fun ObjectivesScreen(viewModel: ObjectivesViewModel, onBack: () -> Unit) {
    val objectifs by viewModel.objectives.collectAsState()
    val c = MaterialTheme.sankaiColors

    var saisie by remember { mutableStateOf("") }

    val enCours = objectifs.filter { !it.isDone }
    val termines = objectifs.filter { it.isDone }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = c.textPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.objectives_title), color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(
                            R.string.objectives_summary,
                            enCours.size,
                            pluralStringResource(R.plurals.objectives_done, termines.size, termines.size)
                        ),
                        color = c.textSecondary, fontSize = 12.sp
                    )
                }
            }

            // Saisie d'un nouvel objectif
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = saisie,
                    onValueChange = { saisie = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Nouvel objectif…", color = c.textDisabled) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        viewModel.ajouter(saisie)
                        saisie = ""
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                        focusedBorderColor = c.accent,
                        unfocusedBorderColor = c.border,
                        focusedContainerColor = c.surface2,
                        unfocusedContainerColor = c.surface2
                    )
                )
                Spacer(Modifier.width(8.dp))
                SankaiButton(
                    "Ajouter",
                    onClick = {
                        viewModel.ajouter(saisie)
                        saisie = ""
                    },
                    enabled = saisie.isNotBlank(),
                    small = true
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (objectifs.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🎯", fontSize = 40.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("Aucun objectif pour l'instant", color = c.textSecondary, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Ajoute ce que tu veux accomplir, coche quand c'est fait.",
                                color = c.textDisabled, fontSize = 12.sp
                            )
                        }
                    }
                }

                if (enCours.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.objectives_todo)) }
                    items(enCours, key = { it.id }) { objectif ->
                        LigneObjectif(
                            objectif = objectif,
                            onToggle = { viewModel.basculer(objectif) },
                            onDelete = { viewModel.supprimer(objectif) }
                        )
                    }
                }

                if (termines.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.objectives_done_section)) }
                    items(termines, key = { it.id }) { objectif ->
                        LigneObjectif(
                            objectif = objectif,
                            onToggle = { viewModel.basculer(objectif) },
                            onDelete = { viewModel.supprimer(objectif) }
                        )
                    }
                    item {
                        SankaiButton(
                            stringResource(R.string.objectives_clear_done),
                            onClick = { viewModel.effacerTermines() },
                            secondary = true,
                            small = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun LigneObjectif(
    objectif: ObjectiveEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface2)
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (objectif.isDone) Icons.Filled.CheckCircle
                          else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = if (objectif.isDone) stringResource(R.string.objectives_done_cd)
                                 else stringResource(R.string.objectives_todo),
            modifier = Modifier.size(22.dp),
            tint = if (objectif.isDone) SuccessGreen else AccentGold
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = objectif.text,
            color = if (objectif.isDone) c.textDisabled else c.textPrimary,
            fontSize = 14.sp,
            textDecoration = if (objectif.isDone) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.DeleteOutline, "Supprimer",
                modifier = Modifier.size(16.dp), tint = DangerRed
            )
        }
    }
}
