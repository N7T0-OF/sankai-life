package com.sankailife.ui.screens.life.flashcards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.domain.engine.FlashcardEngine
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.AccentGold
import com.sankailife.ui.theme.AccentViolet
import com.sankailife.ui.theme.DangerRed
import com.sankailife.ui.theme.SuccessGreen
import com.sankailife.ui.theme.sankaiColors

@Composable
fun FlashcardsScreen(
    profileId: Long,
    viewModel: FlashcardsViewModel,
    onBack: () -> Unit
) {
    val etat by viewModel.etat.collectAsState()
    val c = MaterialTheme.sankaiColors
    val haptics = LocalHaptics.current

    LaunchedEffect(profileId) { viewModel.demarrer(profileId) }

    val progression by animateFloatAsState(etat.progression, label = "progression")

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
    ) {
        // En-tête
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = c.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("🃏 Révision", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(etat.nomModule, color = c.textSecondary, fontSize = 12.sp)
            }
            if (!etat.terminee && etat.total > 0) {
                Text(
                    "${etat.index + 1} / ${etat.total}",
                    color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(12.dp))
            }
        }

        LinearProgressIndicator(
            progress = { progression },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = AccentViolet,
            trackColor = c.surface3
        )

        when {
            etat.chargement -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent)
            }

            etat.terminee -> EcranFin(
                reussies = etat.reussies,
                ratees = etat.ratees,
                message = etat.messageFin,
                onRejouer = { viewModel.rejouer() },
                onBack = onBack
            )

            else -> {
                val carte = etat.carteCourante
                if (carte == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Rien à réviser", color = c.textSecondary)
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(12.dp))

                        // La carte
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(c.surface2)
                                .border(1.dp, c.border, RoundedCornerShape(20.dp))
                                .clickable(enabled = carte.aDeuxFaces && !etat.versoVisible) {
                                    haptics.click()
                                    viewModel.revelerVerso()
                                }
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    carte.recto,
                                    color = c.textPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )

                                if (carte.aDeuxFaces) {
                                    Spacer(Modifier.height(20.dp))
                                    HorizontalDivider(color = c.border)
                                    Spacer(Modifier.height(20.dp))

                                    AnimatedVisibility(
                                        visible = etat.versoVisible,
                                        enter = fadeIn(), exit = fadeOut()
                                    ) {
                                        Text(
                                            carte.verso.orEmpty(),
                                            color = AccentGold,
                                            fontSize = 18.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    if (!etat.versoVisible) {
                                        Text(
                                            "Touche pour révéler",
                                            color = c.textDisabled, fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            "Prochaine révision si tu sais : " +
                            FlashcardEngine.libelleIntervalle(
                                FlashcardEngine.boiteSuivante(carte.box, true)
                            ),
                            color = c.textSecondary, fontSize = 11.sp
                        )

                        Spacer(Modifier.height(12.dp))

                        // Une carte à une seule face n'a rien à révéler : on
                        // propose directement les deux réponses.
                        val peutRepondre = !carte.aDeuxFaces || etat.versoVisible

                        if (peutRepondre) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                BoutonReponse(
                                    "À revoir", DangerRed, Modifier.weight(1f)
                                ) { haptics.error(); viewModel.repondre(false) }
                                BoutonReponse(
                                    "Je savais", SuccessGreen, Modifier.weight(1f)
                                ) { haptics.success(); viewModel.repondre(true) }
                            }
                        } else {
                            SankaiButton(
                                "Révéler la réponse",
                                onClick = { viewModel.revelerVerso() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BoutonReponse(
    texte: String,
    couleur: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(couleur.copy(alpha = 0.16f))
            .border(1.dp, couleur.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(texte, color = couleur, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EcranFin(
    reussies: Int,
    ratees: Int,
    message: String,
    onRejouer: () -> Unit,
    onBack: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (reussies + ratees > 0) "🎉" else "🃏", fontSize = 52.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            if (reussies + ratees > 0) "Session terminée" else "Rien à réviser",
            color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        if (reussies + ratees > 0) {
            Text("$reussies acquises • $ratees à revoir", color = c.textSecondary, fontSize = 14.sp)
        } else {
            Text(
                "Toutes tes cartes sont à jour. Reviens plus tard : " +
                "elles reviendront d'elles-mêmes selon leur échéance.",
                color = c.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center
            )
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(message, color = AccentGold, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(28.dp))
        if (reussies + ratees > 0) {
            SankaiButton("Continuer à réviser", onClick = onRejouer, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
        SankaiButton("Retour", onClick = onBack, secondary = true, modifier = Modifier.fillMaxWidth())
    }
}
