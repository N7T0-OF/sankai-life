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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.sankailife.core.audio.rememberVoix
import com.sankailife.core.motdujour.MotDuJour
import com.sankailife.core.motdujour.drapeau
import com.sankailife.core.motdujour.libelleLangue
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.Drawxsouanpt
import com.sankailife.ui.theme.sankaiColors

/**
 * Le mot du jour, en entier : définition, exemple, origine, écoute.
 *
 * Un seul mot par jour, tout local. L'écran se referme aussi vite qu'il
 * s'ouvre — il n'y a rien d'autre à voir après le mot.
 */
@Composable
fun MotDuJourScreen(
    viewModel: MotDuJourViewModel,
    onBack: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val mot by viewModel.motDuJour.collectAsStateWithLifecycle()
    val motDemain by viewModel.motDemain.collectAsStateWithLifecycle()
    val favoris by viewModel.favoris.collectAsStateWithLifecycle()
    val voice = rememberVoix()

    DisposableEffect(mot?.id, voice) {
        onDispose { voice.arreter() }
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        // Barre du haut : retour, titre, favori.
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
                stringResource(R.string.mot_du_jour_title),
                color = c.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (mot != null) {
                val estFavori = mot!!.id in favoris
                IconButton(onClick = { viewModel.basculerFavori(mot!!.id) }) {
                    Icon(
                        imageVector = if (estFavori) Icons.Filled.Favorite
                        else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(
                            if (estFavori) R.string.mot_du_jour_favorite_remove
                            else R.string.mot_du_jour_favorite_add
                        ),
                        tint = if (estFavori) c.accent else c.textSecondary
                    )
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            val mot = mot
            if (mot == null) {
                Spacer(Modifier.height(80.dp))
                Text(
                    stringResource(R.string.mot_du_jour_none),
                    color = c.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Pastilles : langue · catégorie · niveau.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Pastille("${mot.drapeau()}  ${mot.libelleLangue()}")
                    mot.categorie?.let { Pastille(it) }
                    mot.niveau?.let { Pastille(it) }
                }

                Spacer(Modifier.height(22.dp))

                // Le mot lui-même : l'élément d'identité de l'écran, en
                // police Sankai. Les caractères manquants (accents hors
                // couverture) retombent sur la police système, sans casse.
                Text(
                    mot.mot,
                    color = c.textPrimary,
                    fontSize = 42.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = Drawxsouanpt,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                mot.prononciation?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it,
                        color = c.textSecondary,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Écoute, si une voix existe pour cette langue.
                val canRead = voice.disponiblePour(mot.langue)
                if (canRead) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(c.accent.copy(alpha = 0.10f))
                                .border(1.dp, c.accent.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { voice.dire(mot.mot, mot.langue) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Filled.VolumeUp,
                                    contentDescription = stringResource(R.string.mot_du_jour_listen),
                                    tint = c.accent
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Définition.
                BlocMotDuJour(stringResource(R.string.mot_du_jour_definition)) {
                    Text(
                        mot.definition,
                        color = c.textPrimary,
                        fontSize = 17.sp,
                        lineHeight = 26.sp,
                        fontFamily = FontFamily.Serif
                    )
                }
                mot.exemple?.let {
                    Spacer(Modifier.height(14.dp))
                    BlocMotDuJour(stringResource(R.string.mot_du_jour_example)) {
                        Text(
                            "« $it »",
                            color = c.textSecondary,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
                mot.origine?.let {
                    Spacer(Modifier.height(14.dp))
                    BlocMotDuJour(stringResource(R.string.mot_du_jour_origin)) {
                        Text(
                            it,
                            color = c.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = c.border)

                // Demain : un mot, une ligne — pas une file d'attente.
                Spacer(Modifier.height(12.dp))
                Text(
                    motDemain?.let {
                        stringResource(R.string.mot_du_jour_tomorrow, it.mot)
                    } ?: stringResource(R.string.mot_du_jour_none),
                    color = c.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
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
private fun Pastille(texte: String) {
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
private fun BlocMotDuJour(titre: String, contenu: @Composable () -> Unit) {
    val c = MaterialTheme.sankaiColors
    Column(Modifier.fillMaxWidth()) {
        Text(
            titre.uppercase(),
            color = c.accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(5.dp))
        contenu()
    }
}
