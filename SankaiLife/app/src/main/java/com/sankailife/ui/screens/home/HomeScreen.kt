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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.sankailife.core.culture.CultureEntryType
import com.sankailife.core.poesie.TypeTexte
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.components.SankaiGlassCard
import com.sankailife.ui.navigation.Screen
import com.sankailife.ui.theme.Drawxsouanpt
import com.sankailife.ui.theme.sankaiColors

/**
 * « Accueil » : une découverte à lire, puis la vraie vie.
 *
 * Rien ne retient : un bonjour, la découverte du jour, une mini-révision si
 * des cartes attendent, et une sortie explicite. L'écran tient sur un
 * téléphone sans défilement.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigate: (String) -> Unit) {
    val dueCards by viewModel.dueCards.collectAsStateWithLifecycle()
    val todayCompleted by viewModel.todayCompleted.collectAsStateWithLifecycle()
    val xpDuJour by viewModel.xpDuJour.collectAsStateWithLifecycle()
    val decouverte by viewModel.decouverte.collectAsStateWithLifecycle()
    val motDuJour by viewModel.motDuJour.collectAsStateWithLifecycle()
    val motDemain by viewModel.motDemain.collectAsStateWithLifecycle()
    val poesieDuJour by viewModel.poesieDuJour.collectAsStateWithLifecycle()
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
                    // L'XP d'aujourd'hui : ce qui a réellement été fait, pas
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
            }

            Spacer(Modifier.height(gap))

            // Carte principale : le mot du jour. Un mot, une définition, une
            // sortie — le « Sankai Moment ».
            SankaiGlassCard(
                modifier = Modifier.fillMaxWidth().weight(1.3f),
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

            // Le proverbe ou le poème du jour, en une ligne compacte : la
            // découverte littéraire reste à un geste, sans alourdir l'écran.
            val poesie = poesieDuJour
            if (poesie != null) {
                SankaiGlassCard(
                    modifier = Modifier.fillMaxWidth().weight(0.4f),
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

            // La découverte culturelle du jour, en second plan : la capsule
            // (poème, proverbe, histoire…) reste à un geste.
            val capsule = decouverte
            if (capsule != null) {
                SankaiGlassCard(
                    modifier = Modifier.fillMaxWidth().weight(0.55f),
                    onClick = { onNavigate(Screen.Capsules.route) },
                    contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📖", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.home_culture_discovery),
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${cultureTypeLabel(capsule.type)} · ${capsule.title}",
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

            // Mini-révision : le plus court des apprentissages, uniquement si
            // des cartes attendent. Rien d'inventé quand tout est à jour.
            if (!todayCompleted && dueCards > 0) {
                SankaiGlassCard(
                    modifier = Modifier.fillMaxWidth().weight(0.6f),
                    onClick = { onNavigate(Screen.Academy.route) },
                    contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.width(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.home_mini_review),
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                pluralStringResource(
                                    R.plurals.today_due_cards,
                                    dueCards,
                                    dueCards
                                ),
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.width(18.dp)
                        )
                    }
                }
            } else if (todayCompleted) {
                // Tout est fait : on le dit simplement, sans proposer de
                // « continuer » pour rien.
                SankaiGlassCard(
                    modifier = Modifier.fillMaxWidth().weight(0.6f),
                    onClick = null,
                    contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.width(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.today_done_title),
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(gap))

            // La prochaine découverte : une ligne calme, pas une file
            // d'attente. Une seule chose à venir.
            Text(
                motDemain?.let {
                    stringResource(R.string.mot_du_jour_tomorrow, it.mot)
                } ?: stringResource(R.string.home_next_discovery),
                color = colors.textSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

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
