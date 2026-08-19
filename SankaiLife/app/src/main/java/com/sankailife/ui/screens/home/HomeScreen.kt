package com.sankailife.ui.screens.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankailife.R
import com.sankailife.core.poesie.TypeTexte
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.components.SankaiGlassCard
import com.sankailife.ui.navigation.Screen
import com.sankailife.ui.theme.Drawxsouanpt
import com.sankailife.ui.theme.sankaiColors

/**
 * « Accueil » : un tableau de bord vivant, jamais un menu.
 *
 * Un bonjour, le niveau réel, le mot du jour, le poème du jour, la
 * progression d'aujourd'hui — et rien d'autre. Chaque carte ouvre son
 * contenu directement ; aucune ne change de section. Les paramètres tiennent
 * dans une icône compacte. Rien n'invite à quitter l'application.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigate: (String) -> Unit) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val xpDuJour by viewModel.xpDuJour.collectAsStateWithLifecycle()
    val motDuJour by viewModel.motDuJour.collectAsStateWithLifecycle()
    val poesieDuJour by viewModel.poesieDuJour.collectAsStateWithLifecycle()
    val colors = MaterialTheme.sankaiColors

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
            // ── En-tête : salut + icône paramètres ────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    // Le salut est le texte d'identité de l'Accueil : la
                    // police manuscrite Sankai y a sa place, le reste de
                    // l'écran reste lisible.
                    Text(
                        stringResource(R.string.home_greeting),
                        color = colors.textPrimary,
                        fontSize = if (compact) 24.sp else 30.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = Drawxsouanpt,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.home_intro),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                // Les paramètres restent à un geste, sans jamais prendre
                // toute la largeur : une icône compacte, en haut à droite.
                IconButton(onClick = { onNavigate(Screen.Settings.route) }) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = colors.textSecondary
                    )
                }
            }

            // ── Niveau réel : une barre, pas un score ─────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.profile_level_xp, user.level, user.xp.toString()),
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (xpDuJour > 0) {
                    Text(
                        stringResource(R.string.today_xp_earned, xpDuJour),
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = {
                    val total = user.xpNext.coerceAtLeast(1)
                    (user.xp.toFloat() / total).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                color = colors.accent,
                trackColor = colors.surface3
            )

            Spacer(Modifier.height(gap))

            // ── Le mot du jour : la découverte principale, lue ici même. ──
            SankaiGlassCard(
                modifier = Modifier.fillMaxWidth().weight(1.25f),
                onClick = { onNavigate(Screen.MotDuJour.route) },
                selectionne = motDuJour != null,
                contentPadding = PaddingValues(if (compact) 12.dp else 16.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoStories, contentDescription = null, tint = colors.accentSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.home_mot_du_jour).uppercase(),
                            color = colors.accentSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val mot = motDuJour
                    if (mot != null) {
                        Text(
                            mot.mot,
                            color = colors.textPrimary,
                            fontSize = if (compact) 26.sp else 32.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = Drawxsouanpt,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            mot.definition,
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(10.dp))
                        SankaiButton(
                            text = stringResource(R.string.home_discover_action),
                            onClick = { onNavigate(Screen.MotDuJour.route) },
                            small = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            stringResource(R.string.mot_du_jour_none),
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(gap))

            // ── Le poème ou le proverbe du jour, en une ligne compacte. ──
            val poesie = poesieDuJour
            if (poesie != null) {
                SankaiGlassCard(
                    modifier = Modifier.fillMaxWidth().weight(0.5f),
                    onClick = { onNavigate(Screen.PoesieDuJour.route) },
                    contentPadding = PaddingValues(if (compact) 10.dp else 12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (poesie.type == TypeTexte.POEME) "📜" else "💭",
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(
                                    if (poesie.type == TypeTexte.POEME) R.string.home_poem
                                    else R.string.home_proverb
                                ),
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                poesie.texte,
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.width(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(gap))
            }

            // ── Ta progression : ce qui a réellement été fait aujourd'hui. ──
            SankaiGlassCard(
                modifier = Modifier.fillMaxWidth().weight(0.55f),
                onClick = { onNavigate(Screen.AllStats.route) },
                contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🌿", fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.home_progression),
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (xpDuJour > 0) {
                                stringResource(R.string.today_xp_earned, xpDuJour)
                            } else {
                                stringResource(R.string.home_progression_none)
                            },
                            color = if (xpDuJour > 0) colors.accent else colors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.width(16.dp)
                    )
                }
            }
        }
    }
}
