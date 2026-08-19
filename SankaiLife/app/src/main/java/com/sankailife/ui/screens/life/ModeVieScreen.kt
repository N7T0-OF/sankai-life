package com.sankailife.ui.screens.life

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankailife.R
import com.sankailife.SankaiApplication
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.components.SankaiCard
import com.sankailife.ui.navigation.Screen
import com.sankailife.ui.theme.SuccessGreen
import com.sankailife.ui.theme.Drawxsouanpt
import com.sankailife.ui.theme.sankaiColors

private data class OutilVie(
    val emoji: String,
    val titre: Int,
    val description: Int,
    val route: String
)

/**
 * Vie : ce qui accompagne la vraie vie, sans la recréer.
 *
 * Les minuteurs et les listes de tâches sont déjà dans le téléphone ; Sankai
 * ne les recopie pas. Ici : les mémos, et le calendrier Android — lecture
 * seule — dont les événements terminés deviennent une progression symbolique.
 */
@Composable
fun ModeVieScreen(
    app: SankaiApplication,
    viewModel: ModeVieViewModel,
    onNavigate: (String) -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val etat by viewModel.etat.collectAsStateWithLifecycle()

    val demanderPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { accorde ->
        // Qu'il ait accordé ou non, on relit : un refus affiche simplement
        // l'explication au lieu d'une erreur.
        viewModel.rafraichir()
    }

    // Relu à chaque ouverture : les événements terminés depuis la dernière
    // visite méritent d'être crédités.
    LaunchedEffect(Unit) { viewModel.rafraichir() }

    val outils = listOf(
        OutilVie("✦", R.string.mode_life_memos, R.string.mode_life_memos_hint, Screen.Memo.route)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                stringResource(R.string.mode_life_title),
                color = c.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = Drawxsouanpt
            )
            Text(
                stringResource(R.string.mode_life_subtitle),
                color = c.textSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
        }

        items(outils, key = { it.route }) { outil ->
            CarteOutilVie(outil, onNavigate)
        }

        item {
            CarteCalendrier(
                etat = etat,
                onAutoriser = {
                    demanderPermission.launch(android.Manifest.permission.READ_CALENDAR)
                },
                onActualiser = { viewModel.rafraichir() }
            )
        }
    }
}

@Composable
private fun CarteOutilVie(outil: OutilVie, onNavigate: (String) -> Unit) {
    val c = MaterialTheme.sankaiColors
    SankaiCard(onClick = { onNavigate(outil.route) }) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(outil.emoji, color = c.accent, fontSize = 25.sp)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(outil.titre),
                    color = c.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(outil.description),
                    color = c.textSecondary,
                    fontSize = 12.sp
                )
            }
            Text("›", color = c.textSecondary, fontSize = 24.sp)
        }
    }
}

/**
 * Le calendrier : la vraie vie valorisée, jamais recréée.
 *
 * Trois états : pas d'autorisation (explication + bouton), lecture en cours,
 * et le résultat — combien d'événements terminés aujourd'hui, quelle XP
 * symbolique, et la note de confidentialité.
 */
@Composable
private fun CarteCalendrier(
    etat: ModeVieViewModel.Etat,
    onAutoriser: () -> Unit,
    onActualiser: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    SankaiCard(onClick = null) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📅", fontSize = 22.sp)
                Spacer(Modifier.padding(start = 4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.mode_life_calendar_title),
                        color = c.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.mode_life_calendar_privacy),
                        color = c.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            when {
                !etat.permissionAccordee -> {
                    Text(
                        stringResource(R.string.mode_life_calendar_hint),
                        color = c.textSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    SankaiButton(
                        text = stringResource(R.string.mode_life_calendar_allow),
                        onClick = onAutoriser,
                        modifier = Modifier.fillMaxWidth(),
                        small = true
                    )
                }

                etat.chargement -> {
                    Text(
                        stringResource(R.string.mode_life_calendar_loading),
                        color = c.textSecondary,
                        fontSize = 12.sp
                    )
                }

                else -> {
                    Text(
                        pluralStringResource(
                            R.plurals.mode_life_calendar_done,
                            etat.evenementsAujourdhui,
                            etat.evenementsAujourdhui
                        ),
                        color = SuccessGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (etat.xpCalendrier > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.mode_life_calendar_xp, etat.xpCalendrier),
                            color = c.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (etat.dernierGain > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.mode_life_calendar_gained, etat.dernierGain),
                            color = c.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    SankaiButton(
                        text = stringResource(R.string.mode_life_calendar_refresh),
                        onClick = onActualiser,
                        modifier = Modifier.fillMaxWidth(),
                        secondary = true,
                        small = true
                    )
                }
            }
        }
    }
}
