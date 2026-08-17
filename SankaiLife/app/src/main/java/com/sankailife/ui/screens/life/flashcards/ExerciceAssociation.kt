package com.sankailife.ui.screens.life.flashcards

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.sankailife.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.learning.domain.AssociationEngine
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.theme.sankaiColors

/**
 * L'exercice d'association en deux colonnes.
 *
 * Tout se fait au toucher, jamais au glisser-déposer. Faire glisser un mot
 * jusqu'à sa traduction demande de la précision, exclut ceux qui ont du mal à
 * viser, et ne mesure rien de plus qu'un toucher : on touche un mot, puis son
 * équivalent.
 *
 * Une paire trouvée reste affichée, estompée, au lieu de disparaître. La faire
 * disparaître décalerait toutes les lignes du dessous sous le doigt, et le
 * toucher suivant tomberait à côté.
 */
@Composable
fun ExerciceAssociation(
    etat: AssociationEngine.Etat,
    onToucher: (AssociationEngine.Element) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.sankaiColors
    val haptics = LocalHaptics.current

    // Un retour bref sur l'erreur : la vibration dit « non » avant même qu'on
    // ait lu quoi que ce soit.
    LaunchedEffect(etat.derniereErreur) {
        if (etat.derniereErreur != null) haptics.click()
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.exercise_associate_instruction),
            color = c.textSecondary, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(etat.colonneGauche, etat.colonneDroite).forEach { colonne ->
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colonne.forEach { element ->
                        Etiquette(
                            element = element,
                            trouvee = etat.estTrouvee(element),
                            choisie = etat.selection == element,
                            fautive = etat.derniereErreur?.let {
                                element.carteId == it.first || element.carteId == it.second
                            } == true && element.carteId !in etat.trouvees,
                            onClick = { onToucher(element) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "${etat.trouvees.size} / ${etat.colonneGauche.size}",
            color = c.textSecondary, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Etiquette(
    element: AssociationEngine.Element,
    trouvee: Boolean,
    choisie: Boolean,
    fautive: Boolean,
    onClick: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    // La couleur seule ne suffit pas : elle est invisible pour une partie des
    // gens et sur un écran en plein soleil. L'état est donc aussi porté par
    // l'opacité, la bordure et le texte lu par les lecteurs d'écran.
    val fond by animateColorAsState(
        when {
            trouvee -> c.surface1
            fautive -> MaterialTheme.colorScheme.errorContainer
            choisie -> MaterialTheme.colorScheme.primaryContainer
            else -> c.surface2
        },
        animationSpec = tween(160), label = "fondEtiquette"
    )
    val opacite by animateFloatAsState(
        if (trouvee) 0.45f else 1f, animationSpec = tween(160), label = "opaciteEtiquette"
    )

    val etatLu = when {
        trouvee -> stringResource(R.string.exercise_assoc_found)
        choisie -> stringResource(R.string.exercise_assoc_selected)
        fautive -> stringResource(R.string.exercise_assoc_incorrect)
        else -> stringResource(R.string.exercise_assoc_to_match)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .alpha(opacite)
            .clip(RoundedCornerShape(12.dp))
            .background(fond)
            .border(
                width = if (choisie) 2.dp else 0.5.dp,
                color = if (choisie) MaterialTheme.colorScheme.primary else c.border,
                shape = RoundedCornerShape(12.dp)
            )
            // Une paire trouvée ne répond plus, mais garde sa place.
            .then(if (trouvee) Modifier else Modifier.clickable { onClick() })
            .padding(horizontal = 10.dp, vertical = 14.dp)
            .clearAndSetSemantics {
                contentDescription = "${element.texte}, $etatLu"
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            element.texte,
            color = if (trouvee) c.textSecondary else c.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (choisie) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}
