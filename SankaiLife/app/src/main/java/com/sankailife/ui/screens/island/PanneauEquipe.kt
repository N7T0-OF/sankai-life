package com.sankailife.ui.screens.island

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.garden.data.GardenMimoEntity
import com.sankailife.core.garden.domain.MimoEngine
import com.sankailife.core.island.data.IslandBuildingEntity
import com.sankailife.core.island.domain.IslandBuildingEngine
import com.sankailife.core.island.domain.IslandMimoEngine
import com.sankailife.core.island.domain.IslandMimoMondeEngine
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.sankaiColors

/**
 * L'équipe : qui travaille sur l'île, et qui on peut embaucher.
 *
 * Ce panneau existe parce que l'embauche avait disparu. Elle ne vivait que sur
 * l'écran du Jardin, devenu inatteignable quand l'île a pris sa place : le
 * système de Mimos tournait toujours — le travail pendant l'absence, le plafond
 * de l'Atelier, tout — mais plus personne ne pouvait recruter le premier
 * employé. Une mécanique entière était morte sans qu'aucun message ne le dise.
 *
 * Seuls les métiers que l'île sait faire travailler sont proposés. Vendre un
 * transporteur ici serait vendre un objet sans effet : l'île n'a ni caisses ni
 * marchand ambulant, et son moteur de travail ne connaît que l'arrosage et la
 * récolte. Les employés d'un autre métier déjà embauchés restent affichés, et
 * l'écran dit franchement qu'ils n'ont rien à y faire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanneauEquipe(
    mimos: List<GardenMimoEntity>,
    places: List<IslandMimoMondeEngine.Place>,
    batiments: List<IslandBuildingEntity>,
    pieces: Int,
    prixDe: (MimoEngine.Type) -> Int,
    onEmbaucher: (MimoEngine.Type) -> Unit,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val aAtelier = IslandBuildingEngine.Type.ATELIER.id in batiments.map { it.type }.toSet()
    val parId = places.associateBy { it.id }

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Équipe", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            // Ce que les Mimos font vraiment, dit une fois et sans détour. Un
            // joueur qui croit à une simulation en direct trouvera l'île figée
            // et pensera à un bug.
            Text(
                "Tes Mimos travaillent pendant que l'application est fermée : " +
                    "leur travail est rattrapé à ton retour, dans la limite de " +
                    "${IslandMimoEngine.plafond(aAtelier)} actions." +
                    if (!aAtelier) " Un Atelier relèverait cette limite." else "",
                color = c.textSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(16.dp))

            if (mimos.isEmpty()) {
                Text("Personne pour l'instant.", color = c.textSecondary, fontSize = 13.sp)
            } else {
                mimos.forEach { mimo ->
                    val type = MimoEngine.Type.parNom(mimo.type) ?: return@forEach
                    val place = parId[mimo.id]
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${type.emoji}  ", fontSize = 20.sp)
                        Column {
                            Text(
                                "${mimo.nom} — ${type.libelle}",
                                color = c.textPrimary, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                when {
                                    place == null -> type.role
                                    place.sansEmploi ->
                                        "Ce métier n'a rien à faire sur l'île."
                                    place.endormi -> "Dort — reprendra au lever du jour."
                                    place.cible != null ->
                                        "${place.activite.libelle} — parcelle " +
                                            "(${place.cible.first}, ${place.cible.second})"
                                    else -> "Sans tâche : rien à faire pour l'instant."
                                },
                                color = c.textSecondary, fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = c.border)
            Spacer(Modifier.height(14.dp))

            Text("Embaucher", color = c.textPrimary, fontSize = 15.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                "Le prix monte à chaque employé du même métier : dix arroseurs " +
                    "rendraient l'île inutile à regarder.",
                color = c.textSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))

            // Ordre stable : celui du catalogue, pas celui d'un ensemble.
            MimoEngine.Type.entries
                .filter { it in IslandMimoMondeEngine.METIERS_ACTIFS }
                .forEach { type ->
                    val prix = prixDe(type)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.fillMaxWidth(0.6f)) {
                            Text(
                                "${type.emoji} ${type.libelle}",
                                color = c.textPrimary, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(type.role, color = c.textSecondary, fontSize = 12.sp)
                        }
                        SankaiButton(
                            "$prix 🪙",
                            onClick = { onEmbaucher(type) },
                            small = true,
                            enabled = pieces >= prix
                        )
                    }
                }
        }
    }
}
