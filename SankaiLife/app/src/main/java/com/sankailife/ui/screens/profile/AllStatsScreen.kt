package com.sankailife.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.ui.components.SectionTitle
import com.sankailife.ui.theme.sankaiColors

/**
 * Statistiques complètes, sorties du profil.
 *
 * Le profil doit répondre à « qui suis-je et où j'en suis » ; une dizaine de
 * compteurs empilés répondent à une autre question et noyaient la première.
 */
@Composable
fun AllStatsScreen(viewModel: ProfileViewModel, onBack: () -> Unit) {
    val user by viewModel.user.collectAsState()
    val brut by viewModel.rawUser.collectAsState()
    val c = MaterialTheme.sankaiColors

    Column(Modifier.fillMaxSize().background(c.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = c.textPrimary)
            }
            Text("Statistiques", color = c.textPrimary,
                fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionTitle("Progression")
            LigneStat("Niveau actuel", "${user.level}")
            LigneStat("XP dans le niveau", "${user.xp} / ${user.xpNext}")
            LigneStat("Série actuelle", "${user.streakDays} jours")

            SectionTitle("Activité")
            LigneStat("Temps de focus total",
                "${user.totalFocusMinutes / 60} h ${user.totalFocusMinutes % 60} min")
            LigneStat("Coffres ouverts", "${user.totalChestsOpened}")
            LigneStat("Publicités vues", "${user.totalAdsWatched}")

            SectionTitle("Économie")
            LigneStat("Pièces actuelles", "${user.coins} 🪙")
            LigneStat("Gemmes actuelles", "${user.gems} 💎")
            LigneStat("Pièces gagnées au total", "${brut?.totalCoinsEarned ?: 0} 🪙")
            // Indicateur d'engagement le plus parlant : ce qui a été dépensé
            // dit mieux que le solde si l'économie du jeu tourne vraiment.
            LigneStat("Pièces dépensées au total", "${brut?.totalCoinsSpent ?: 0} 🪙")

            SectionTitle("Capacités")
            LigneStat("Slots de modules", "${user.moduleSlots}")
            LigneStat("Slots de focus", "${user.focusSlots}")

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LigneStat(libelle: String, valeur: String) {
    val c = MaterialTheme.sankaiColors
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp)).background(c.surface2)
            .border(0.5.dp, c.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(libelle, color = c.textSecondary, fontSize = 13.sp)
        Text(valeur, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
