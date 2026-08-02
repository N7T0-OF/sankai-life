package com.sankailife.ui.screens.customization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.theme.sankaiColors

private fun couleurDepuisHex(hex: String, defaut: Color): Color =
    com.sankailife.ui.theme.Contraste.depuisHex(hex) ?: defaut

@Composable
fun CustomizationScreen(viewModel: CustomizationViewModel, onBack: () -> Unit) {
    val c = MaterialTheme.sankaiColors
    val haptics = LocalHaptics.current
    val couleursSysteme by viewModel.couleursSysteme.collectAsState()

    val affiches by viewModel.themesAffiches.collectAsState()
    val categorie by viewModel.categorie.collectAsState()
    val obtenus by viewModel.nombreObtenus.collectAsState()

    Column(Modifier.fillMaxSize().background(c.background)) {

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = c.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("Personnalisation", color = c.textPrimary,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("$obtenus thèmes obtenus", color = c.textSecondary, fontSize = 12.sp)
            }
        }

        // L'avertissement n'apparait que quand le conflit existe reellement.
        if (couleursSysteme) {
            Text(
                "Les couleurs de ton téléphone sont actives : elles remplacent " +
                    "l'accent du thème équipé. Choisis « Sankai classique » dans " +
                    "les paramètres d'apparence pour voir tes thèmes.",
                color = c.textSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Onglets : les thèmes utilisables en premier. Placer les verrouillés
        // devant donnerait l'impression d'une collection surtout inaccessible.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(4.dp)
        ) {
            CustomizationViewModel.Categorie.entries.forEach { cat ->
                val actif = cat == categorie
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (actif) c.surface3 else Color.Transparent)
                        .clickable { viewModel.choisirCategorie(cat) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        cat.libelle,
                        color = if (actif) c.textPrimary else c.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (actif) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        if (affiches.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🎨", fontSize = 40.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (categorie == CustomizationViewModel.Categorie.OBTENUS)
                        "Aucun thème obtenu pour l'instant"
                    else "Tous les thèmes sont débloqués",
                    color = c.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(affiches, key = { it.theme.id }) { ui ->
                    CarteTheme(ui) {
                        if (ui.debloque) {
                            haptics.success()
                            viewModel.equiper(ui)
                        } else {
                            haptics.error()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarteTheme(ui: CustomizationViewModel.ThemeUi, onClick: () -> Unit) {
    val c = MaterialTheme.sankaiColors
    val accent = couleurDepuisHex(ui.theme.accentHex, c.accent)

    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (ui.equipe) accent.copy(alpha = 0.12f) else c.surface2)
            .border(
                width = if (ui.equipe) 1.5.dp else 1.dp,
                color = if (ui.equipe) accent else c.border,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Aperçu de la couleur d'accent : plus parlant qu'un nom seul.
                Box(
                    Modifier.size(28.dp).clip(CircleShape)
                        .background(if (ui.debloque) accent else c.surface3)
                        .border(1.dp, c.border, CircleShape)
                )
                Spacer(Modifier.weight(1f))
                when {
                    ui.equipe -> Icon(Icons.Filled.Check, "Équipé",
                        tint = accent, modifier = Modifier.size(18.dp))
                    !ui.debloque -> Icon(Icons.Filled.Lock, "Verrouillé",
                        tint = c.textDisabled, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                ui.theme.name,
                color = if (ui.debloque) c.textPrimary else c.textSecondary,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                when {
                    ui.equipe -> "Équipé"
                    ui.debloque -> "Appuie pour équiper"
                    else -> ui.conditionDeblocage
                },
                color = if (ui.equipe) accent else c.textSecondary,
                fontSize = 11.sp
            )
        }
    }
}
