package com.sankailife.ui.screens.island

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.island.domain.IslandMimoMondeEngine
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.sankaiColors

/**
 * La fiche d'un Mimo, ouverte en le touchant sur la carte.
 *
 * Elle existe parce que les Mimos étaient dessinés et muets : on voyait des
 * silhouettes sans savoir laquelle faisait quoi, ni pourquoi l'une dormait. Le
 * panneau d'équipe le disait déjà, mais il fallait savoir qu'il existait et
 * faire le lien entre une ligne de liste et une silhouette à l'écran.
 *
 * Elle répète une chose qu'on ne dira jamais assez : **ce Mimo ne travaille pas
 * sous vos yeux.** Sa position dit où il y a du travail, pas ce qu'il est en
 * train de faire. Laisser croire à une simulation en direct ferait chercher un
 * mouvement qui n'arrivera jamais.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FicheMimo(
    mimo: IslandMimoMondeEngine.Place,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text(
                "${mimo.type.emoji}  ${mimo.nom}",
                color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Text(mimo.type.libelle, color = c.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))

            Text(
                when {
                    mimo.sansEmploi -> "Ce métier n'a rien à faire sur l'île."
                    mimo.endormi -> "Dort — il reprendra au lever du jour."
                    mimo.cible != null ->
                        "${mimo.activite.libelle} — parcelle " +
                            "(${mimo.cible.first}, ${mimo.cible.second})"
                    else -> "Sans tâche : rien ne l'attend pour l'instant."
                },
                color = c.textPrimary, fontSize = 14.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (mimo.sansEmploi) {
                    "L'île ne connaît ni caisses ni marchand ambulant : seuls " +
                        "l'arrosage et la récolte y sont repris."
                } else {
                    "Il travaille pendant que l'application est fermée. Ce que " +
                        "tu vois ici indique où il y a du travail, pas un geste " +
                        "en cours."
                },
                color = c.textSecondary, fontSize = 12.sp
            )

            Spacer(Modifier.height(6.dp))
            Text(mimo.type.role, color = c.textDisabled, fontSize = 11.sp)

            Spacer(Modifier.height(18.dp))
            SankaiButton(
                "Fermer", onClick = onFermer, secondary = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
