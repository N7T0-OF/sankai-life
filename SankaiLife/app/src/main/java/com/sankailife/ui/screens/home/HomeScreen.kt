package com.sankailife.ui.screens.home

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankailife.R
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.components.SankaiFloatingButton
import com.sankailife.ui.components.SankaiGlassCard
import com.sankailife.ui.navigation.Screen
import com.sankailife.ui.theme.SuccessGreen
import com.sankailife.ui.theme.sankaiColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Accueil court et non déroulant : une décision utile par carte, puis une
 * sortie explicite. Aucun coffre, streak ou compteur de rareté n'y concurrence
 * l'apprentissage.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigate: (String) -> Unit) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val dueCards by viewModel.dueCards.collectAsStateWithLifecycle()
    val nextMemo by viewModel.nextMemo.collectAsStateWithLifecycle()
    val dailyMinutes by viewModel.dailyMinutes.collectAsStateWithLifecycle()
    val minimalMode by viewModel.minimalMode.collectAsStateWithLifecycle()
    val todayCompleted by viewModel.todayCompleted.collectAsStateWithLifecycle()
    val xpDuJour by viewModel.xpDuJour.collectAsStateWithLifecycle()
    val colors = MaterialTheme.sankaiColors
    val activity = LocalContext.current as? Activity
    val nextReminder = nextMemo?.nextTriggerAtMillis?.let { millis ->
        DateTimeFormatter.ofPattern("HH'h'mm")
            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val compact = maxHeight < 650.dp || LocalDensity.current.fontScale >= 1.35f
        val outerPadding = if (compact) 12.dp else 18.dp
        val gap = if (compact) 8.dp else 12.dp

        Column(
            Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .padding(horizontal = outerPadding, vertical = 10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.today_greeting, user.pseudo),
                        color = colors.textPrimary,
                        fontSize = if (compact) 20.sp else 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (minimalMode) stringResource(R.string.today_minimal_active)
                        else stringResource(R.string.today_intentional_subtitle),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    // L'XP d'aujourd'hui : ce que tu as réellement fait, pas
                    // un compteur d'ouverture. Affiché seulement quand il y en
                    // a — un « +0 XP » serait un reproche, pas une donnée.
                    if (xpDuJour > 0) {
                        Text(
                            stringResource(R.string.today_xp_earned, xpDuJour),
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                SankaiFloatingButton(
                    contentDescription = stringResource(R.string.settings_title),
                    onClick = { onNavigate(Screen.Settings.route) }
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = colors.textPrimary)
                }
            }

            Spacer(Modifier.height(gap))

            SankaiGlassCard(
                modifier = Modifier.fillMaxWidth().weight(1.15f),
                onClick = if (todayCompleted) null else ({ onNavigate(Screen.Academy.route) }),
                selectionne = !todayCompleted && dueCards > 0,
                contentPadding = PaddingValues(if (compact) 12.dp else 18.dp)
            ) {
                if (todayCompleted) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen
                        )
                        Text(
                            stringResource(R.string.today_done_title),
                            color = colors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            stringResource(R.string.today_done_hint),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.School, contentDescription = null, tint = colors.accent)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.today_section),
                                color = colors.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                if (dueCards > 0) pluralStringResource(
                                    R.plurals.today_due_cards,
                                    dueCards,
                                    dueCards
                                ) else stringResource(R.string.today_no_due),
                                color = colors.textPrimary,
                                fontSize = if (compact) 16.sp else 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (dailyMinutes == 0) stringResource(R.string.today_no_time_goal)
                                else stringResource(R.string.today_estimated_minutes, dailyMinutes),
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.today_continue),
                            tint = colors.accent
                        )
                    }
                }
            }

            Spacer(Modifier.height(gap))
            SankaiGlassCard(
                modifier = Modifier.fillMaxWidth().weight(0.95f),
                onClick = { onNavigate(Screen.Capsules.route) },
                contentPadding = PaddingValues(if (compact) 12.dp else 16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AutoStories, contentDescription = null, tint = colors.accentSecondary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.today_capsule_title),
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.today_capsule_hint),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        stringResource(R.string.today_one_minute),
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(gap))
            SankaiGlassCard(
                modifier = Modifier.fillMaxWidth().weight(0.82f),
                onClick = { onNavigate(Screen.Memo.route) },
                contentPadding = PaddingValues(if (compact) 12.dp else 16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.NotificationsNone, contentDescription = null, tint = colors.accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.today_memo_title),
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            nextReminder?.let { stringResource(R.string.today_next_memo, it) }
                                ?: stringResource(R.string.today_no_memo),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(gap))
            SankaiButton(
                text = if (todayCompleted) stringResource(R.string.today_close_app)
                else stringResource(R.string.today_finish),
                onClick = {
                    if (todayCompleted) activity?.finishAndRemoveTask()
                    else viewModel.finishToday { activity?.finishAndRemoveTask() }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
