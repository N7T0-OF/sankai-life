package com.sankailife.ui.screens.island

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.garden.domain.ALL_SEEDS
import com.sankailife.core.garden.domain.CropGrowthEngine
import com.sankailife.core.garden.domain.PlotState
import com.sankailife.core.garden.domain.SoilType
import com.sankailife.core.island.data.IslandSlotEntity
import com.sankailife.core.island.domain.IslandBuildingEngine
import com.sankailife.core.island.domain.IslandCultureEngine
import com.sankailife.core.island.domain.IslandCultureEngine.Action
import com.sankailife.core.island.domain.IslandSlotEngine
import com.sankailife.core.island.domain.IslandStockEngine
import com.sankailife.core.island.domain.IslandTileType
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.sankaiColors

/**
 * Fiche d'une case, ouverte au toucher.
 *
 * Rien de tout ceci n'est affiché sur la carte elle-même. Un prix, un compte à
 * rebours et un état sur chaque case rendraient l'île illisible dès la
 * vingtième parcelle — l'information vient quand on la demande.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulleParcelle(
    x: Int,
    y: Int,
    type: IslandTileType,
    parcelle: IslandSlotEntity?,
    parcellesPossedees: Int,
    batiments: List<com.sankailife.core.island.data.IslandBuildingEntity>,
    niveau: Int,
    onFermer: () -> Unit,
    onAcheter: () -> Unit,
    onDegager: () -> Unit,
    onPreparer: () -> Unit,
    onSemer: (String) -> Unit,
    onArroser: () -> Unit,
    onRecolter: () -> Unit,
    onBatir: (IslandBuildingEngine.Type) -> Unit,
    onOuvrirStock: () -> Unit = {}
) {
    val c = MaterialTheme.sankaiColors

    // Un bâtiment occupe-t-il cette case ?
    //
    // La fiche recevait jusqu'ici le seul type de terrain, et affichait donc
    // l'herbe restée sous la Boutique : on pouvait bâtir, voir le bâtiment, et
    // continuer d'ouvrir la fiche du sol. Ce qu'on touche doit primer sur ce
    // qui se trouve dessous.
    val batimentIci = batiments.firstOrNull { b ->
        IslandBuildingEngine.Type.parId(b.type)?.let { t ->
            (x to y) in IslandBuildingEngine.casesOccupees(t, b.origineX, b.origineY).toSet()
        } == true
    }
    val typeBatiment = batimentIci?.let { IslandBuildingEngine.Type.parId(it.type) }

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text(
                if (typeBatiment != null) "${typeBatiment.emoji} ${typeBatiment.libelle}"
                else PaletteIle.nom(type),
                color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))

            when {
                // Un bâtiment prime sur tout le reste : ni achat, ni culture,
                // ni construction sur une case déjà bâtie.
                typeBatiment != null -> ContenuBatiment(
                    type = typeBatiment,
                    batiment = batimentIci,
                    onOuvrirStock = onOuvrirStock
                )

                // Case naturelle : on dit pourquoi elle ne s'achète pas plutôt
                // que de laisser une fiche vide.
                parcelle == null && !IslandSlotEngine.terrainAchetable(type) -> {
                    Text(
                        IslandSlotEngine.raisonTerrain(type)
                            ?: "Ce terrain ne peut pas être cultivé.",
                        color = c.textSecondary, fontSize = 13.sp
                    )
                }

                parcelle == null -> {
                    val prix = IslandSlotEngine.prix(type, parcellesPossedees)
                    val plafond = IslandSlotEngine.plafond(niveau)
                    Ligne("Prix", if (prix == 0) "offerte" else "$prix 🪙")
                    Ligne("Parcelles", "$parcellesPossedees / $plafond")
                    if (type == IslandTileType.FOREST || type == IslandTileType.ROCK) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Il faudra la dégager avant de cultiver.",
                            color = c.textSecondary, fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    SankaiButton("Acheter", onClick = onAcheter, modifier = Modifier.fillMaxWidth())
                }

                else -> ContenuParcelle(
                    parcelle = parcelle,
                    onDegager = onDegager,
                    onPreparer = onPreparer,
                    onSemer = onSemer,
                    onArroser = onArroser,
                    onRecolter = onRecolter
                )
            }

            // Construction : proposée sur toute case constructible, qu'elle
            // soit achetée ou non. Un bâtiment ne se cultive pas, donc il n'y
            // a aucune raison d'exiger d'avoir acheté la parcelle d'abord.
            val constructibles = IslandBuildingEngine.Type.entries
                .filter { t -> batiments.none { it.type == t.id } }
            if (typeBatiment == null && type.constructible && constructibles.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Construire", color = c.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                constructibles.forEach { t ->
                    SankaiButton(
                        "${t.emoji} ${t.libelle} · ${t.prix}🪙 · ${t.largeur}×${t.hauteur}",
                        onClick = { onBatir(t) },
                        secondary = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

/**
 * Fiche d'un bâtiment.
 *
 * La Boutique ouvre le stock : c'est elle qui améliore le prix de vente, donc
 * c'est là qu'on vend. Le Dépôt montre ce qu'il permet d'entreposer. Aucun des
 * deux n'ouvre un catalogue propre — les graines s'achètent depuis la parcelle
 * — et le dire vaut mieux qu'un bouton qui ne mènerait nulle part.
 */
@Composable
private fun ContenuBatiment(
    type: IslandBuildingEngine.Type,
    batiment: com.sankailife.core.island.data.IslandBuildingEntity?,
    onOuvrirStock: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    Ligne("Taille", "${type.largeur} × ${type.hauteur} cases")
    Ligne("Niveau", "${batiment?.niveau ?: 1}")

    Spacer(Modifier.height(8.dp))
    when (type) {
        IslandBuildingEngine.Type.BOUTIQUE -> {
            Text(
                "Tes récoltes se vendent ${(IslandStockEngine.BONUS_BOUTIQUE * 100).toInt()} % " +
                    "plus cher tant que la Boutique est debout.",
                color = c.textSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            SankaiButton(
                "Vendre mes récoltes", onClick = onOuvrirStock,
                modifier = Modifier.fillMaxWidth()
            )
        }

        IslandBuildingEngine.Type.DEPOT -> {
            Text(
                "Le Dépôt porte la capacité de stockage à " +
                    "${IslandStockEngine.capacite(aDepot = true)} récoltes.",
                color = c.textSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            SankaiButton(
                "Voir le stock", onClick = onOuvrirStock,
                secondary = true, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ContenuParcelle(
    parcelle: IslandSlotEntity,
    onDegager: () -> Unit,
    onPreparer: () -> Unit,
    onSemer: (String) -> Unit,
    onArroser: () -> Unit,
    onRecolter: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val etat = runCatching { PlotState.valueOf(parcelle.etat) }.getOrDefault(PlotState.EMPTY)
    val graine = ALL_SEEDS.firstOrNull { it.id == parcelle.graineId }
    val sol = SoilType.parId(parcelle.solId)

    val croissance = graine?.let {
        CropGrowthEngine.etat(
            seed = it,
            sol = sol,
            minutesCumulees = parcelle.minutesCumulees,
            minutesDepuisArrosage =
                (System.currentTimeMillis() - parcelle.dernierArrosageMillis) / 60_000L
        )
    }

    if (graine != null && croissance != null) {
        Ligne("Culture", "${graine.emoji} ${graine.nom}")
        Ligne("Croissance", "${(croissance.progression * 100).toInt()} %")
        Ligne(
            "Reste",
            if (croissance.prete) "prête" else "${croissance.minutesRestantes} min"
        )
        if (croissance.besoinEau && !croissance.prete) {
            Spacer(Modifier.height(4.dp))
            Text("Cette plante a soif.", color = c.textSecondary, fontSize = 12.sp)
        }
    } else {
        Ligne("État", if (parcelle.aDegager) "à dégager" else "libre")
        Ligne("Sol", sol.libelle)
    }

    val actions = IslandCultureEngine.actionsPossibles(
        etat = etat,
        aDegager = parcelle.aDegager,
        besoinEau = croissance?.besoinEau ?: false,
        prete = croissance?.prete ?: false
    )

    Spacer(Modifier.height(14.dp))

    actions.forEach { action ->
        when (action) {
            Action.DEGAGER ->
                SankaiButton("Dégager", onClick = onDegager, modifier = Modifier.fillMaxWidth())

            Action.PREPARER ->
                SankaiButton("Préparer la terre", onClick = onPreparer,
                    modifier = Modifier.fillMaxWidth())

            Action.ARROSER ->
                SankaiButton("Arroser", onClick = onArroser, modifier = Modifier.fillMaxWidth())

            Action.RECOLTER ->
                SankaiButton("Récolter", onClick = onRecolter, modifier = Modifier.fillMaxWidth())

            Action.SEMER -> {
                Text("Semer", color = c.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                // Seules les graines qui poussent sur ce sol sont proposées.
                // Montrer les autres pour les refuser ensuite ferait chercher
                // l'erreur à l'utilisateur.
                val compatibles = ALL_SEEDS.filter {
                    IslandCultureEngine.grainePlantable(it, sol)
                }
                if (compatibles.isEmpty()) {
                    Text(
                        "Aucune graine connue ne pousse sur ce sol.",
                        color = c.textSecondary, fontSize = 12.sp
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        compatibles.forEach { g ->
                            SankaiButton(
                                "${g.emoji} ${g.nom} · ${g.prixPieces}🪙",
                                onClick = { onSemer(g.id) },
                                secondary = true,
                                small = true
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Ligne(cle: String, valeur: String) {
    val c = MaterialTheme.sankaiColors
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(cle, color = c.textSecondary, fontSize = 13.sp)
        Text(valeur, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
