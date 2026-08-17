package com.sankailife.ui.screens.life

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankailife.R
import com.sankailife.SankaiApplication
import com.sankailife.ui.components.SankaiCard
import com.sankailife.ui.navigation.Screen
import com.sankailife.ui.theme.sankaiColors

private data class OutilVie(
    val emoji: String,
    val titre: Int,
    val description: Int,
    val route: String
)

/**
 * Les outils personnels, séparés de l'Académie.
 */
@Composable
fun ModeVieScreen(app: SankaiApplication, onNavigate: (String) -> Unit) {
    val minimal by app.preferences.minimalMode.collectAsStateWithLifecycle(initialValue = false)
    val c = MaterialTheme.sankaiColors

    val essentiels = listOf(
        OutilVie("✦", R.string.mode_life_memos, R.string.mode_life_memos_hint, Screen.Memo.route),
        OutilVie("◷", R.string.mode_life_focus, R.string.mode_life_focus_hint, Screen.Focus.route)
    )
    val facultatifs = listOf(
        OutilVie("◎", R.string.mode_life_objectives, R.string.mode_life_objectives_hint, Screen.Objectives.route)
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
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.mode_life_subtitle),
                color = c.textSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
        }

        items(essentiels, key = { it.route }) { outil ->
            CarteOutilVie(outil, onNavigate)
        }

        if (!minimal) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.mode_life_more),
                    color = c.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            items(facultatifs, key = { it.route }) { outil ->
                CarteOutilVie(outil, onNavigate)
            }
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
