package com.sankailife.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.domain.engine.OnboardingEngine
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.sankaiColors

/**
 * Le tutoriel de première ouverture.
 *
 * « Passer » est disponible dès la première page, en haut à droite, et non
 * caché derrière trois écrans. Quelqu'un qui sait déjà ce qu'il fait n'a pas à
 * subir six pages pour y arriver — et un tutoriel qu'on ne peut pas fuir est
 * un tutoriel qu'on finit par détester.
 *
 * L'écran se ferme de la même façon dans les deux cas : le tutoriel est marqué
 * comme vu qu'on l'ait lu ou passé. Le reproposer à quelqu'un qui l'a
 * volontairement fermé serait le punir de son choix.
 */
@Composable
fun OnboardingScreen(onTermine: () -> Unit) {
    val c = MaterialTheme.sankaiColors
    var index by remember { mutableStateOf(0) }
    val page = OnboardingEngine.pages[index]

    Box(
        Modifier.fillMaxSize()
            .background(
                c.background
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Toujours accessible, dès la première page.
        if (!OnboardingEngine.estDerniere(index)) {
            Text(
                "Passer",
                color = c.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopEnd)
                    .clickable { onTermine() }
                    .padding(20.dp)
            )
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    val versLaDroite = targetState > initialState
                    (slideInHorizontally(tween(260)) { if (versLaDroite) it else -it } + fadeIn())
                        .togetherWith(
                            slideOutHorizontally(tween(260)) { if (versLaDroite) -it else it } +
                                fadeOut()
                        )
                },
                label = "pageTutoriel"
            ) { i ->
                val p = OnboardingEngine.pages[i]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(p.emoji, fontSize = 64.sp)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        p.titre,
                        color = c.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        p.texte,
                        color = c.textSecondary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OnboardingEngine.pages.indices.forEach { i ->
                    Box(
                        Modifier.size(if (i == index) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (i == index) c.accent else c.surface3)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SankaiButton(
                page.action,
                onClick = {
                    if (OnboardingEngine.estDerniere(index)) onTermine()
                    else index = OnboardingEngine.suivante(index)
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (index > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Retour",
                    color = c.textDisabled,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { index = OnboardingEngine.precedente(index) }
                        .padding(8.dp)
                )
            }
        }
    }
}
