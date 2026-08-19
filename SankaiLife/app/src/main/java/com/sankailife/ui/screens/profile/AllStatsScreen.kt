package com.sankailife.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankailife.R
import com.sankailife.core.domain.engine.MemorisationEngine
import com.sankailife.ui.components.SectionTitle
import com.sankailife.ui.theme.sankaiColors

/** Statistiques pédagogiques sobres, sans streak ni économie de jeu. */
@Composable
fun AllStatsScreen(viewModel: ProfileViewModel, onBack: () -> Unit) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val memo by viewModel.memorisation.collectAsStateWithLifecycle()
    val rhythm by viewModel.regularite.collectAsStateWithLifecycle()
    val dailyMinutes by viewModel.dailyMinutes.collectAsStateWithLifecycle()
    val progressionReelle by viewModel.progressionReelle.collectAsStateWithLifecycle()
    val colors = MaterialTheme.sankaiColors

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = colors.textPrimary
                )
            }
            Text(
                stringResource(R.string.stats_title),
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Progression réelle : cinq dimensions, aucune obligation. C'est
            // ici qu'elle vit — le profil reste l'identité, pas un tableau de
            // bord.
            SectionTitle(stringResource(R.string.progression_title))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface2)
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                progressionReelle.forEach { dim ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${dim.emoji}  ${stringResource(dim.libelle)}",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(dim.valeur, color = colors.textSecondary, fontSize = 12.sp)
                    }
                    LinearProgressIndicator(
                        progress = { dim.progression },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = colors.accent,
                        trackColor = colors.surface3
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    stringResource(R.string.progression_hint),
                    color = colors.textDisabled,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(8.dp))

            SectionTitle(stringResource(R.string.stats_personal_rhythm))
            StatRow(stringResource(R.string.stats_last_7_days), "${rhythm.sept} %")
            StatRow(stringResource(R.string.stats_last_30_days), "${rhythm.trente} %")
            StatRow(stringResource(R.string.stats_last_90_days), "${rhythm.quatreVingtDix} %")
            Text(
                stringResource(R.string.stats_no_streak),
                color = colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            SectionTitle(stringResource(R.string.stats_learning))
            StatRow(stringResource(R.string.stats_saved_cards), memo.total.toString())
            StatRow(stringResource(R.string.stats_mastered_cards), memo.maitrisees.toString())
            if (memo.jamaisVues > 0) {
                StatRow(stringResource(R.string.stats_unseen_cards), memo.jamaisVues.toString())
            }
            StatRow(stringResource(R.string.stats_answers), memo.revisions.toString())
            val successRate = MemorisationEngine.tauxReussite(memo.revisions, memo.reussites)
            StatRow(
                stringResource(R.string.stats_success_rate),
                successRate?.let(MemorisationEngine::pourcentage)
                    ?: stringResource(
                        R.string.stats_after_answers,
                        MemorisationEngine.REVISIONS_POUR_UN_TAUX
                    )
            )

            SectionTitle(stringResource(R.string.stats_life_time))
            Text(
                if (dailyMinutes == 0) stringResource(R.string.stats_no_time_target)
                else pluralStringResource(
                    R.plurals.stats_chosen_session,
                    dailyMinutes,
                    dailyMinutes
                ),
                color = colors.textSecondary,
                fontSize = 13.sp
            )
            Text(
                stringResource(R.string.stats_life_time_disclaimer),
                color = colors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    val colors = MaterialTheme.sankaiColors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface2)
            .border(0.5.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = colors.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
