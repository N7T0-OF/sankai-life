package com.sankailife.ui.screens.island

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.garden.domain.ALL_SEEDS
import com.sankailife.core.island.data.IslandBuildingEntity
import com.sankailife.core.island.data.IslandStockEntity
import com.sankailife.core.island.domain.IslandBuildingEngine
import com.sankailife.core.island.domain.IslandStockEngine
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.sankaiColors

/**
 * Le stock de récoltes, et la vente.
 *
 * Vendre est possible avec ou sans Boutique : sans elle, un joueur qui
 * commence ne pourrait jamais réunir les pièces nécessaires pour la bâtir, et
 * l'économie se bloquerait au premier jour. La Boutique améliore le prix, elle
 * n'ouvre pas la porte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanneauStock(
    stock: List<IslandStockEntity>,
    batiments: List<IslandBuildingEntity>,
    onFermer: () -> Unit,
    onVendre: (String, Int) -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val types = batiments.map { it.type }.toSet()
    val aBoutique = IslandBuildingEngine.Type.BOUTIQUE.id in types
    val aDepot = IslandBuildingEngine.Type.DEPOT.id in types
    val aPort = IslandBuildingEngine.Type.PORT.id in types
    val capacite = IslandStockEngine.capacite(aDepot)
    val total = stock.sumOf { it.quantite }

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Récoltes", color = c.textPrimary, fontSize = 18.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "$total / $capacite" + if (!aDepot) " — un Dépôt agrandirait le stock." else "",
                color = c.textSecondary, fontSize = 12.sp
            )
            if (!aBoutique || !aPort) {
                Spacer(Modifier.height(4.dp))
                // On dit ce qu'on perd, sans empêcher de vendre.
                Text(
                    when {
                        !aBoutique && !aPort ->
                            "Sans Boutique ni Port, tu vends au prix de base."
                        !aBoutique -> "Une Boutique ameliorerait encore le prix."
                        else -> "Un Port ameliorerait encore le prix."
                    },
                    color = c.textSecondary, fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(14.dp))

            if (stock.isEmpty()) {
                Text("Rien en stock pour l'instant.", color = c.textSecondary, fontSize = 13.sp)
                return@Column
            }

            stock.forEach { ligne ->
                val graine = ALL_SEEDS.firstOrNull { it.id == ligne.graineId } ?: return@forEach
                val unitaire = IslandStockEngine.prixUnitaire(graine, aBoutique, aPort)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.fillMaxWidth(0.45f)) {
                        Text("${graine.emoji} ${graine.nom}", color = c.textPrimary,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${ligne.quantite} en stock · $unitaire 🪙 pièce",
                            color = c.textSecondary, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SankaiButton("Vendre 1", onClick = { onVendre(ligne.graineId, 1) },
                            secondary = true, small = true)
                        SankaiButton("Tout", onClick = { onVendre(ligne.graineId, ligne.quantite) },
                            small = true)
                    }
                }
            }
        }
    }
}
