package com.sankailife.ui.screens.garden

import androidx.compose.animation.AnimatedVisibility
import com.sankailife.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.domain.engine.DeblocageEngine
import com.sankailife.core.garden.domain.ConseilEngine
import com.sankailife.core.garden.domain.MimoMondeEngine
import com.sankailife.core.garden.domain.OutilJardin
import com.sankailife.core.garden.domain.Seed
import com.sankailife.ui.art.ArtJardin
import com.sankailife.ui.art.IconeArt
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.*

/**
 * Les commandes du jardin, en surimpression.
 *
 * Tout ce qui était une barre fixe est devenu un bouton posé sur le terrain.
 * La différence n'est pas cosmétique : une barre coûte sa hauteur en
 * permanence, un bouton flottant ne coûte rien. Le jardin est passé d'environ
 * 55 % à 85 % de la hauteur utile sans qu'aucune fonction ne disparaisse.
 *
 * Les boutons sont volontairement peu nombreux. Chaque icône ajoutée ici
 * reprend au jardin la place qu'on vient de lui rendre.
 */
@Composable
fun BoutonsFlottants(
    conseil: ConseilEngine.Conseil?,
    cartesDues: Int,
    caisses: Int,
    pretes: Int,
    outilTenu: OutilJardin?,
    modifier: Modifier = Modifier,
    onSac: () -> Unit,
    onConseil: () -> Unit,
    onApprendre: () -> Unit,
    onRecentrer: () -> Unit,
    onAnnulerOutil: () -> Unit,
    onActionPrincipale: () -> Unit
) {
    Box(modifier.padding(10.dp)) {

        // Outil tenu : bandeau d'annulation en haut.
        // Sans lui, reposer un outil demanderait de rouvrir le sac.
        AnimatedVisibility(
            visible = outilTenu != null,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 54.dp)
        ) {
            outilTenu?.let { o ->
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF101A14).copy(alpha = 0.86f))
                        .border(1.dp, AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .clickable { onAnnulerOutil() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${o.emoji}  ${nomOutil(o)}", color = AccentCyan, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "✕ ${stringResource(R.string.garden_tool_put_down)}",
                        color = c_textDoux(),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Capsule d'apprentissage, centrée en bas.
        //
        // Elle est au centre parce que c'est la position qu'on regarde en
        // premier. Le jardin peut occuper l'écran entier, mais ce n'est pas
        // pour ça que l'application existe.
        if (cartesDues > 0) {
            CapsuleApprentissage(
                cartes = cartesDues,
                onClic = onApprendre,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp)
            )
        }

        Column(
            Modifier.align(Alignment.BottomStart),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (conseil != null) {
                BulleFlottante(
                    "💡",
                    AccentGold,
                    stringResource(R.string.garden_action_advice),
                    onConseil
                )
            }
            BulleFlottante(
                "⌖",
                null,
                stringResource(R.string.garden_action_recenter),
                onRecentrer
            )
        }

        Column(
            Modifier.align(Alignment.BottomEnd),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // L'action principale n'apparaît que s'il y a vraiment quelque
            // chose à faire : ranger avant de récolter, dans l'ordre du
            // circuit du dépôt.
            if (caisses > 0 || pretes > 0) {
                BulleFlottante(
                    if (caisses > 0) "📦" else "🧺",
                    SuccessGreen,
                    stringResource(
                        if (caisses > 0) R.string.garden_action_store_crates
                        else R.string.garden_action_harvest_all
                    ),
                    onActionPrincipale,
                    badge = if (caisses > 0) caisses else pretes
                )
            }
            BulleFlottante(
                "🎒",
                null,
                stringResource(R.string.garden_action_open_bag),
                onSac
            )
        }
    }
}

@Composable
private fun c_textDoux() = MaterialTheme.sankaiColors.textSecondary

/**
 * Un bouton rond translucide.
 *
 * Le fond est sombre et légèrement transparent : le jardin transparaît
 * dessous, ce qui rappelle qu'il continue derrière l'interface. Compose ne
 * sait pas flouter ce qui est *sous* un composant, donc l'effet de verre est
 * approché par un dégradé et une bordure claire — la limite est connue et
 * documentée depuis la barre de navigation.
 */
@Composable
private fun BulleFlottante(
    symbole: String,
    teinte: Color?,
    description: String,
    onClic: () -> Unit,
    badge: Int = 0
) {
    val c = MaterialTheme.sankaiColors
    Box {
        Box(
            Modifier.size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF16241C).copy(alpha = 0.92f),
                            Color(0xFF0C1611).copy(alpha = 0.88f)
                        )
                    )
                )
                .border(
                    1.dp,
                    (teinte ?: c.border).copy(alpha = if (teinte != null) 0.55f else 0.35f),
                    CircleShape
                )
                .semantics { contentDescription = description }
                .clickable { onClic() },
            contentAlignment = Alignment.Center
        ) {
            Text(symbole, fontSize = 20.sp, color = teinte ?: c.textPrimary)
        }

        if (badge > 0) {
            Box(
                Modifier.align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .clip(CircleShape)
                    .background(DangerRed)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text("$badge", color = Color.White, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Le rappel d'apprentissage.
 *
 * Le libellé dit « réviser », pas « gagner de l'eau ». La récompense est
 * réelle mais elle reste secondaire : mettre le gain en avant apprendrait à
 * réviser pour la ressource, ce qui est exactement l'habitude à ne pas créer.
 */
@Composable
private fun CapsuleApprentissage(
    cartes: Int,
    onClic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "capsule")
    val halo by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "haloCapsule"
    )

    Row(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF101A14).copy(alpha = 0.9f))
            .border(1.5.dp, AccentCyan.copy(alpha = halo), RoundedCornerShape(22.dp))
            .clickable { onClic() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("📚", fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.garden_review),
            color = AccentCyan,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.garden_cards_count, cartes),
            color = MaterialTheme.sankaiColors.textSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun nomOutil(outil: OutilJardin): String = when (outil) {
    OutilJardin.Arrosoir -> stringResource(R.string.garden_tool_watering_can)
    OutilJardin.Panier -> stringResource(R.string.garden_tool_basket)
    OutilJardin.Pioche -> stringResource(R.string.garden_tool_pickaxe)
    is OutilJardin.Graine -> outil.seed.nom
}

/** Le conseil du moment, déplié sans masquer le terrain. */
@Composable
fun MiniConseil(
    conseil: ConseilEngine.Conseil,
    cartesDues: Int,
    pretes: Int,
    parcellesSeches: Int,
    valeurStock: Int,
    modifier: Modifier = Modifier,
    onAgir: () -> Unit,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val fermerDescription = stringResource(R.string.garden_advice_close)
    val texte = when (conseil.type) {
        ConseilEngine.Type.DEPOT_PLEIN ->
            stringResource(R.string.garden_advice_depot_full)
        ConseilEngine.Type.CARTES_DUES ->
            stringResource(R.string.garden_advice_cards_due, cartesDues)
        ConseilEngine.Type.PLUS_D_EAU ->
            stringResource(R.string.garden_advice_no_water)
        ConseilEngine.Type.RECOLTE_PRETE ->
            stringResource(R.string.garden_advice_harvest_ready, pretes)
        ConseilEngine.Type.PARCELLES_SECHES ->
            stringResource(R.string.garden_advice_dry_plots, parcellesSeches)
        ConseilEngine.Type.STOCK_VENDABLE ->
            stringResource(R.string.garden_advice_stock_value, valeurStock)
        ConseilEngine.Type.PLUIE_ATTENDUE ->
            stringResource(R.string.garden_advice_rain)
        ConseilEngine.Type.MIMOS_AFFAMES ->
            stringResource(R.string.garden_advice_mimos_hungry)
    }
    val action = when (conseil.type) {
        ConseilEngine.Type.DEPOT_PLEIN -> R.string.garden_advice_action_store
        ConseilEngine.Type.CARTES_DUES,
        ConseilEngine.Type.PLUS_D_EAU -> R.string.garden_review
        ConseilEngine.Type.RECOLTE_PRETE -> R.string.garden_action_harvest
        ConseilEngine.Type.STOCK_VENDABLE -> R.string.garden_advice_action_sell
        else -> null
    }

    Column(
        modifier
            .widthIn(max = 310.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101A14).copy(alpha = 0.94f))
            .border(1.dp, AccentGold.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💡", fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.garden_advice_title),
                color = c.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "✕",
                color = c.textSecondary,
                fontSize = 16.sp,
                modifier = Modifier
                    .size(36.dp)
                    .semantics { contentDescription = fermerDescription }
                    .clickable { onFermer() }
                    .wrapContentSize(Alignment.Center)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(conseil.type.emoji, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(texte, color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        }

        action?.let { libelle ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(AccentGold.copy(alpha = 0.18f))
                        .clickable { onAgir() }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        stringResource(libelle),
                        color = AccentGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Le sac.
 *
 * N'affiche que ce que le joueur possède réellement. Une graine verrouillée,
 * un outil non débloqué, une ressource à zéro : rien de tout ça n'apparaît.
 * Un inventaire qui liste ce qu'on n'a pas est un catalogue, pas un sac — et
 * le catalogue, c'est la boutique.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleSac(
    graines: List<Seed>,
    stock: List<GardenViewModel.LigneStock>,
    eau: Int,
    compost: Int,
    pieces: Int,
    niveauArrosoir: Int,
    niveau: Int,
    outilTenu: OutilJardin?,
    onChoisir: (OutilJardin?) -> Unit,
    onOuvrirDepot: () -> Unit,
    onOuvrirMimos: () -> Unit,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    var onglet by remember { mutableStateOf(0) }
    val onglets = listOf(
        stringResource(R.string.garden_bag_tools),
        stringResource(R.string.garden_bag_seeds),
        stringResource(R.string.garden_bag_harvests),
        stringResource(R.string.garden_bag_resources)
    )

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)
                .heightIn(max = 460.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎒", fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.garden_bag), color = c.textPrimary, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                onglets.forEachIndexed { index, titre ->
                    val actif = onglet == index
                    Box(
                        Modifier.clip(RoundedCornerShape(11.dp))
                            .background(if (actif) c.accent.copy(alpha = 0.18f) else c.surface2)
                            .border(
                                if (actif) 1.5.dp else 1.dp,
                                if (actif) c.accent else c.border,
                                RoundedCornerShape(11.dp)
                            )
                            .clickable { onglet = index }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            titre,
                            color = if (actif) c.accent else c.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (onglet) {
                    0 -> {
                        LigneSac(
                            art = ArtJardin.outil(OutilJardin.Arrosoir),
                            titre = stringResource(
                                R.string.garden_tool_watering_can_level,
                                niveauArrosoir
                            ),
                            detail = stringResource(R.string.garden_bag_water_available, eau),
                            actif = outilTenu == OutilJardin.Arrosoir
                        ) { onChoisir(OutilJardin.Arrosoir) }

                        LigneSac(
                            art = ArtJardin.outil(OutilJardin.Panier),
                            titre = stringResource(R.string.garden_tool_basket),
                            detail = stringResource(R.string.garden_tool_basket_detail),
                            actif = outilTenu == OutilJardin.Panier
                        ) { onChoisir(OutilJardin.Panier) }

                        LigneSac(
                            art = ArtJardin.outil(OutilJardin.Pioche),
                            titre = stringResource(R.string.garden_tool_pickaxe),
                            detail = stringResource(R.string.garden_tool_pickaxe_detail),
                            actif = outilTenu == OutilJardin.Pioche
                        ) { onChoisir(OutilJardin.Pioche) }
                    }

                    1 -> {
                        // Les graines se paient à la plantation : « posséder »
                        // signifie ici « débloquée et abordable ». Afficher
                        // celles qu'on ne peut pas semer serait afficher la
                        // boutique.
                        val semables = graines.filter { pieces >= it.prixPieces }
                        if (semables.isEmpty()) {
                            TexteVide(stringResource(R.string.garden_bag_no_affordable_seed))
                        } else {
                            semables.forEach { graine ->
                                val g = OutilJardin.Graine(graine)
                                LigneSac(
                                    emoji = graine.emoji,
                                    titre = graine.nom,
                                    detail = stringResource(
                                        R.string.garden_bag_seed_cost,
                                        graine.prixPieces
                                    ),
                                    actif = outilTenu is OutilJardin.Graine &&
                                        outilTenu.seed.id == graine.id
                                ) { onChoisir(g) }
                            }
                        }
                    }

                    2 -> {
                        if (stock.isEmpty()) {
                            TexteVide(stringResource(R.string.garden_bag_empty_stock))
                        } else {
                            stock.forEach { ligne ->
                                LigneSac(
                                    emoji = ligne.graine.emoji,
                                    titre = "${ligne.graine.nom} × ${ligne.quantite}",
                                    detail = stringResource(
                                        R.string.garden_bag_stock_value,
                                        ligne.total
                                    ),
                                    actif = false
                                ) { onOuvrirDepot() }
                            }
                        }
                    }

                    else -> {
                        // Une ressource à zéro n'encombre pas la liste.
                        if (eau > 0) {
                            LigneSac(
                                art = ArtJardin.eau,
                                titre = stringResource(R.string.garden_resource_water),
                                detail = stringResource(R.string.garden_bag_units, eau),
                                actif = false
                            ) {}
                        }
                        if (compost > 0) {
                            LigneSac(
                                art = ArtJardin.compost,
                                titre = stringResource(R.string.garden_resource_compost),
                                detail = stringResource(R.string.garden_bag_sacks, compost),
                                actif = false
                            ) { onOuvrirMimos() }
                        }
                        if (pieces > 0) {
                            LigneSac(
                                art = ArtJardin.piece,
                                titre = stringResource(R.string.garden_resource_coins),
                                detail = "$pieces", actif = false
                            ) {}
                        }
                        if (eau == 0 && compost == 0 && pieces == 0) {
                            TexteVide(stringResource(R.string.garden_bag_empty_resources))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = c.border)
            Spacer(Modifier.height(10.dp))

            // Le marché et les Mimos s'ouvrent avec le niveau. Les boutons
            // restent visibles et grisés : savoir qu'ils existent donne un
            // cap, les cacher ferait croire que le jardin s'arrête là.
            val verrouDepot = DeblocageEngine.verrou(
                DeblocageEngine.Fonction.BOUTIQUE_JARDIN, niveau
            )
            val verrouMimos = DeblocageEngine.verrou(
                DeblocageEngine.Fonction.MIMOS, niveau
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    SankaiButton(
                        if (verrouDepot != null) stringResource(
                            R.string.garden_locked_level,
                            verrouDepot.fonction.niveauRequis
                        ) else stringResource(R.string.garden_depot),
                        onClick = onOuvrirDepot,
                        enabled = verrouDepot == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(Modifier.weight(1f)) {
                    SankaiButton(
                        if (verrouMimos != null) stringResource(
                            R.string.garden_locked_level,
                            verrouMimos.fonction.niveauRequis
                        ) else stringResource(R.string.garden_mimos),
                        onClick = onOuvrirMimos,
                        enabled = verrouMimos == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun TexteVide(texte: String) {
    Text(texte, color = MaterialTheme.sankaiColors.textDisabled, fontSize = 12.sp)
}

@Composable
private fun LigneSac(
    art: Int? = null,
    emoji: String? = null,
    titre: String,
    detail: String,
    actif: Boolean,
    onClic: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (actif) c.accent.copy(alpha = 0.15f) else c.surface2)
            .border(
                if (actif) 1.5.dp else 1.dp,
                if (actif) c.accent else c.border,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClic() }
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            art != null -> IconeArt(art, taille = 26.dp)
            emoji != null -> Text(emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(titre, color = if (actif) c.accent else c.textPrimary,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = c.textSecondary, fontSize = 11.sp)
        }
    }
}

/** Fiche d'un Mimo, ouverte en le touchant dans le jardin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleMimo(
    mimo: MimoMondeEngine.MimoUi,
    compost: Int,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mimo.type.emoji, fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(mimo.nom, color = c.textPrimary, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold)
                    Text(mimo.type.libelle, color = c.textSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                if (mimo.endormi) Text("💤", fontSize = 22.sp)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                MimoMondeEngine.resume(mimo, compost),
                color = if (mimo.actif) SuccessGreen else c.textSecondary,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(14.dp))
            // Cette précision compte : le joueur voit un personnage bouger et
            // en déduirait naturellement qu'il travaille en direct. Ce n'est
            // pas le cas, et le lui cacher créerait une incompréhension bien
            // pire qu'une phrase d'explication.
            Text(
                "Les Mimos travaillent pendant ton absence. Ce que tu vois ici " +
                    "est leur état actuel, pas une tâche en cours : leur travail " +
                    "est calculé à ton retour.",
                color = c.textDisabled, fontSize = 11.sp
            )
        }
    }
}
