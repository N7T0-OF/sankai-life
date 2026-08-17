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
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.sankailife.core.culture.CultureEntryType
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.components.SankaiFloatingButton
import com.sankailife.ui.components.SankaiGlassCard
import com.sankailife.ui.navigation.Screen
import com.sankailife.ui.theme.SuccessGreen
import com.sankailife.ui.theme.sankaiColors

/**
 * « Accueil » : le tableau de bord minimal de Sankai, au centre de la
 * navigation.
 *
 * Une découverte à lire, un parcours à continuer, ce qui a réellement été
 * fait — puis une sortie explicite. Aucun compteur de rareté, aucun streak,
 * aucune urgence fabriquée. L'écran tient sur un téléphone sans défilement.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigate: (String) -> Unit) {
    val dueCards by viewModel.dueCards.collectAsStateWithLifecycle()
    val dailyMinutes by viewModel.dailyMinutes.collectAsStateWithLifecycle()
    val todayCompleted by viewModel.todayCompleted.collectAsStateWithLifecycle()
    val xpDuJour by viewModel.xpDuJour.collectAsStateWithLifecycle()
    val suite by viewModel.suite.collectAsStateWithLifecycle()
    val decouverte by viewModel.decouverte.collectAsStateWithLifecycle()
    val colors = MaterialTheme.sankaiColors
    val activity = LocalContext.current as? Activity

    // Relu à chaque ouverture : la progression vient de changer après une
    // session, et la découverte du jour peut avoir été consultée.
    LaunchedEffect(Unit) { viewModel.rafraichir() }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val compact = maxHeight < 680.dp || LocalDensity.current.fontScale >= 1.3f
        val outerPadding = if (compact) 12.dp else 16.dp
        val gap = if (compact) 8.dp else 10.dp

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
                        "Sankai",
                        color = colors.textPrimary,
                        fontSize = if (compact) 20.sp else 23.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.home_intro),
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
                // Les paramètres sont une petite icône, jamais le contenu de
                // l'accueil.
                SankaiFloatingButton(
                    contentDescription = stringResource(R.string.settings_title),
                    onClick = { onNavigate(Screen.Settings.route) }
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = colors.textPrimary)
                }
            }

            Spacer(Modifier.height(gap))

            // Carte principale : la découverte du jour. Une chose à lire,
            // puis à refermer — le « Sankai Moment ».
            SankaiGlassCard(
                modifier = Modifier.fillMaxWidth().weight(1.25f),
                onClick = { onNavigate(Screen.Capsules.route) },
                selectionne = decouverte != null,
                contentPadding = PaddingValues(if (compact) 12.dp else 16.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoStories, contentDescription = null, tint = colors.accentSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            decouverte?.let {
                                "${cultureTypeLabel(it.type)} · " +
                                    stringResource(R.string.home_discover_title)
                            }?.uppercase() ?: stringResource(R.string.home_discover_title).uppercase(),
                            color = colors.accentSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val capsule = decouverte
                    if (capsule != null) {
                        Text(
                            capsule.title,
                            color = colors.textPrimary,
                            fontSize = if (compact) 19.sp else 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            capsule.body?.replace('\n', ' ')?.trim()?.take(110) ?: "",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(10.dp))
                        SankaiButton(
                            text = stringResource(R.string.home_discover_action),
                            onClick = { onNavigate(Screen.Capsules.route) },
                            small = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            stringResource(R.string.home_discover_none),
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(gap))

            // Progression : ce qu'on continue, une seule chose.
            SankaiGlassCard(
                modifier = Modifier.fillMaxWidth().weight(1f),
                onClick = {
                    val s = suite
                    if (s != null) {
                        onNavigate(Screen.Session.createRoute(s.module.memoProfileId, s.unite.id))
                    } else {
                        onNavigate(Screen.Academy.route)
                    }
                },
                contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.home_progress_title).uppercase(),
                        color = colors.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    val s = suite
                    if (s != null) {
                        Text(
                            s.module.nom.ifBlank { stringResource(R.string.memo_default_name) },
                            color = colors.textPrimary,
                            fontSize = if (compact) 15.sp else 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            s.resume,
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { s.progression.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
                            color = colors.accent,
                            trackColor = colors.surface3
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.home_continue),
                                color = colors.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.width(18.dp)
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.home_progress_none),
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        SankaiButton(
                            text = stringResource(R.string.home_progress_cta),
                            onClick = { onNavigate(Screen.Academy.route) },
                            small = true,
                            secondary = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(gap))

            // Activité réelle : ce qui a été fait, pas ce qui reste à faire.
            if (!todayCompleted) {
                SankaiGlassCard(
                    modifier = Modifier.fillMaxWidth().weight(0.9f),
                    onClick = if (dueCards > 0) ({ onNavigate(Screen.Academy.route) }) else null,
                    contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.home_activity_title).uppercase(),
                            color = colors.textPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        if (dueCards > 0) {
                            LigneActivite(
                                icon = Icons.Filled.CheckCircle,
                                couleur = SuccessGreen,
                                texte = pluralStringResource(
                                    R.plurals.today_due_cards,
                                    dueCards,
                                    dueCards
                                )
                            )
                        }
                        if (xpDuJour > 0) {
                            LigneActivite(
                                icon = Icons.Filled.School,
                                couleur = colors.accent,
                                texte = stringResource(R.string.today_xp_earned, xpDuJour)
                            )
                        }
                        if (dueCards == 0 && xpDuJour == 0 && dailyMinutes == 0) {
                            Text(
                                stringResource(R.string.home_activity_nothing),
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                SankaiGlassCard(
                    modifier = Modifier.fillMaxWidth().weight(0.9f),
                    onClick = null,
                    contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.width(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.today_done_title),
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.home_activity_learned),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
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

@Composable
private fun LigneActivite(icon: ImageVector, couleur: androidx.compose.ui.graphics.Color, texte: String) {
    val colors = MaterialTheme.sankaiColors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = couleur, modifier = Modifier.width(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            texte,
            color = colors.textPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Le libellé localisé d'un type de capsule, pour la carte du jour. */
@Composable
fun cultureTypeLabel(type: CultureEntryType): String = when (type) {
    CultureEntryType.POEM -> stringResource(R.string.culture_type_poem)
    CultureEntryType.QUOTE -> stringResource(R.string.culture_type_quote)
    CultureEntryType.PROVERB -> stringResource(R.string.culture_type_proverb)
    CultureEntryType.ARTWORK -> stringResource(R.string.culture_type_artwork)
    CultureEntryType.HISTORY -> stringResource(R.string.culture_type_history)
    CultureEntryType.SCIENCE -> stringResource(R.string.culture_type_science)
    CultureEntryType.WORD -> stringResource(R.string.culture_type_word)
    CultureEntryType.BIOGRAPHY -> stringResource(R.string.culture_type_biography)
}
