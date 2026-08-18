package com.sankailife.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankailife.R
import com.sankailife.core.poesie.PoesieDuJour
import com.sankailife.core.poesie.TypeTexte
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.sankaiColors

/**
 * La poésie ou le proverbe du jour : une découverte, sobre, tout local.
 *
 * Face avant : le texte, l'auteur. Face arrière (détails) : l'œuvre, la date,
 * la source. Aucune file d'attente — un seul texte par jour, puis la vraie
 * vie. Le retournement est un affichage, pas une animation permanente.
 */
@Composable
fun PoesieDuJourScreen(
    viewModel: PoesieDuJourViewModel,
    onBack: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val poesie by viewModel.poesieDuJour.collectAsStateWithLifecycle()
    val poesieDemain by viewModel.poesieDemain.collectAsStateWithLifecycle()
    var detailles by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = c.textPrimary
                )
            }
            Text(
                stringResource(R.string.poesie_du_jour_title),
                color = c.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            val texte = poesie
            if (texte == null) {
                Spacer(Modifier.height(80.dp))
                Text(
                    stringResource(R.string.poesie_du_jour_none),
                    color = c.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Pastilles : type (proverbe/poème) · langue.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PastillePoe(
                        "${texte.drapeau}  ${
                            stringResource(
                                if (texte.type == TypeTexte.POEME) R.string.poesie_du_jour_type_poeme
                                else R.string.poesie_du_jour_type_proverbe
                            )
                        }"
                    )
                    PastillePoe(libelleLangue(texte.langue))
                }

                Spacer(Modifier.height(26.dp))

                // Le texte lui-même, en serif — c'est la découverte.
                Text(
                    texte.texte,
                    color = c.textPrimary,
                    fontSize = 24.sp,
                    lineHeight = 34.sp,
                    fontFamily = FontFamily.Serif,
                    fontStyle = if (texte.type == TypeTexte.POEME) FontStyle.Italic else FontStyle.Normal,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    texte.auteur ?: stringResource(R.string.poesie_du_jour_anonymous),
                    color = c.textSecondary,
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(28.dp))

                // Détails : œuvre, date, source. Affichés à la demande.
                SankaiButton(
                    text = stringResource(
                        if (detailles) R.string.poesie_du_jour_hide_details
                        else R.string.poesie_du_jour_details
                    ),
                    onClick = { detailles = !detailles },
                    small = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (detailles) {
                    Spacer(Modifier.height(14.dp))
                    BlocPoe(stringResource(R.string.poesie_du_jour_work)) {
                        Text(
                            texte.oeuvre ?: "—",
                            color = c.textPrimary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            fontFamily = FontFamily.Serif
                        )
                    }
                    if (!texte.annee.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        BlocPoe(stringResource(R.string.poesie_du_jour_year)) {
                            Text(
                                texte.annee,
                                color = c.textPrimary,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                fontFamily = FontFamily.Serif
                            )
                        }
                    }
                    texte.contexte?.let {
                        Spacer(Modifier.height(10.dp))
                        BlocPoe(stringResource(R.string.poesie_du_jour_source)) {
                            Text(
                                it,
                                color = c.textSecondary,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.poesie_du_jour_public_domain),
                        color = c.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = c.border)

                // Demain : une ligne, pas une file d'attente.
                Spacer(Modifier.height(12.dp))
                Text(
                    poesieDemain?.let {
                        stringResource(
                            R.string.poesie_du_jour_tomorrow,
                            stringResource(
                                if (it.type == TypeTexte.POEME) R.string.poesie_du_jour_type_poeme
                                else R.string.poesie_du_jour_type_proverbe
                            ),
                            it.texte
                        )
                    } ?: stringResource(R.string.poesie_du_jour_none),
                    color = c.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))
                SankaiButton(
                    text = stringResource(R.string.mot_du_jour_done),
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PastillePoe(texte: String) {
    val c = MaterialTheme.sankaiColors
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface2)
            .border(0.5.dp, c.border, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(texte, color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BlocPoe(titre: String, contenu: @Composable () -> Unit) {
    val c = MaterialTheme.sankaiColors
    Column(Modifier.fillMaxWidth()) {
        Text(
            titre.uppercase(),
            color = c.accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(4.dp))
        contenu()
    }
}

private fun libelleLangue(code: String): String = when (code.trim().lowercase()) {
    "fr" -> "Français"
    "pt" -> "Português"
    "en" -> "English"
    "es" -> "Español"
    "it" -> "Italiano"
    "de" -> "Deutsch"
    else -> code
}
