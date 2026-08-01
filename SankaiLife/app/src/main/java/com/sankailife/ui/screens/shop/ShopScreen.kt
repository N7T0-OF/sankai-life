package com.sankailife.ui.screens.shop

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.domain.model.ShopItem
import com.sankailife.core.domain.model.UserState
import com.sankailife.ui.components.*
import com.sankailife.ui.theme.*

/**
 * Les rayons de la boutique.
 *
 * « Boosts » a disparu avec les articles qu'il contenait : ils étaient
 * encaissés sans effet. Un onglet vide vaut moins que pas d'onglet du tout.
 */
private enum class OngletBoutique(val libelle: String, val categorie: String) {
    COFFRES("Coffres", "chest"),
    JARDIN("Jardin", "jardin"),
    PROGRESSION("Progression", "progression")
}

@Composable
fun ShopScreen(viewModel: ShopViewModel) {
    val user by viewModel.user.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val adCd by viewModel.adCooldown.collectAsState()
    val enLigne by viewModel.isOnline.collectAsState()
    val adsAutorisees by viewModel.adsAutorisees.collectAsState()
    val c = MaterialTheme.sankaiColors
    val activity = LocalContext.current as? Activity

    var onglet by remember { mutableStateOf(OngletBoutique.COFFRES) }
    val articles = viewModel.shopItems.filter { it.category == onglet.categorie }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            ResourceBar(user.level, user.xp, user.xpNext, user.coins, user.gems)

            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(14.dp))
                Text("Boutique", color = c.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(c.surface2).padding(4.dp)
                ) {
                    OngletBoutique.entries.forEach { o ->
                        val actif = o == onglet
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (actif) c.surface3 else Color.Transparent)
                                .clickable { onglet = o }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                o.libelle,
                                color = if (actif) c.textPrimary else c.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (actif) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // L'offre du jour occupe toute la largeur : c'est la seule
                    // carte qui doit accrocher l'œil en arrivant.
                    viewModel.offreDuJour?.let { offre ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            CarteOffreDuJour(
                                item = offre,
                                prixRemise = viewModel.coutReel(offre, user),
                                prixInitial = if (offre.id == "slot_module")
                                    com.sankailife.core.domain.engine.EconomyEngine.slotCost(user.moduleSlots)
                                else offre.costCoins,
                                achetable = user.coins >= viewModel.coutReel(offre, user),
                                onAcheter = { viewModel.purchase(offre) }
                            )
                        }
                    }

                    if (onglet == OngletBoutique.COFFRES) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            BlocPub(
                                adCd = adCd,
                                nombreVues = user.adCountToday,
                                enLigne = enLigne,
                                adsAutorisees = adsAutorisees,
                                onRegarder = { activity?.let { viewModel.watchAd(it) } }
                            )
                        }
                    }

                    items(articles, key = { it.id }) { item ->
                        CarteArticle(
                            item = item,
                            prix = viewModel.coutReel(item, user),
                            user = user,
                            enPromo = viewModel.estOffreDuJour(item),
                            onAcheter = { viewModel.purchase(item) }
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
                }
            }
        }

        AnimatedVisibility(
            visible = toast.isNotBlank(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            Box(
                Modifier.padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(24.dp)).background(c.surface2)
                    .border(1.dp, c.accent, RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(toast, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CarteOffreDuJour(
    item: ShopItem,
    prixRemise: Int,
    prixInitial: Int,
    achetable: Boolean,
    onAcheter: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    SankaiStateCard(state = SankaiCardState.RewardAvailable) {
        Text("OFFRE DU JOUR", color = AccentViolet, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(item.description, color = c.textSecondary, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (prixInitial > prixRemise) {
                    Text(
                        "$prixInitial 🪙", color = c.textDisabled, fontSize = 11.sp,
                        textDecoration = TextDecoration.LineThrough
                    )
                }
                Text("$prixRemise 🪙", color = CoinColor,
                    fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        SankaiButton(
            if (achetable) "Acheter" else "Pièces insuffisantes",
            onClick = onAcheter,
            enabled = achetable,
            small = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CarteArticle(
    item: ShopItem,
    prix: Int,
    user: UserState,
    enPromo: Boolean,
    onAcheter: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val assezPieces = prix == 0 || user.coins >= prix
    val assezGemmes = item.costGems == 0 || user.gems >= item.costGems
    val achetable = assezPieces && assezGemmes

    // Un article inabordable n'est pas « verrouillé » : il le deviendra dès
    // que le joueur aura les fonds. Le distinguer évite de laisser croire à
    // une progression manquante alors qu'il suffit d'épargner.
    val etat = if (achetable) SankaiCardState.Default else SankaiCardState.Locked

    SankaiStateCard(state = etat) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                when {
                    item.category == "chest" -> "🎁"
                    item.id.startsWith("eau") -> "💧"
                    item.id.startsWith("compost") -> "🌱"
                    item.id == "bouclier" -> "🛡️"
                    else -> "🧩"
                },
                fontSize = 22.sp
            )
            Spacer(Modifier.weight(1f))
            if (enPromo) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(AccentViolet.copy(alpha = 0.25f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("-25 %", color = AccentViolet, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(item.name, color = if (achetable) c.textPrimary else c.textSecondary,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(item.description, color = c.textSecondary, fontSize = 11.sp)

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (prix > 0) {
                Text("$prix 🪙", color = if (assezPieces) CoinColor else c.textDisabled,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            if (item.costGems > 0) {
                if (prix > 0) Spacer(Modifier.width(8.dp))
                Text("${item.costGems} 💎", color = if (assezGemmes) GemColor else c.textDisabled,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(10.dp))
        SankaiButton(
            if (achetable) "Acheter" else "Fonds insuffisants",
            onClick = onAcheter,
            enabled = achetable,
            small = true,
            secondary = !achetable,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BlocPub(
    adCd: Long,
    nombreVues: Int,
    enLigne: Boolean,
    adsAutorisees: Boolean,
    onRegarder: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    SankaiStateCard(state = if (enLigne) SankaiCardState.Default else SankaiCardState.Locked) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("🎥 Regarder une pub", color = c.textPrimary,
                fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("+5 🪙", color = CoinColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (!adsAutorisees) "La pub reste désactivée tant que le choix de confidentialité n'est pas disponible"
            else if (enLigne) "Aujourd'hui : $nombreVues / 50 • bonus tous les 5"
            else "Connexion requise — le reste de la boutique fonctionne hors ligne",
            color = c.textSecondary, fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))
        SankaiButton(
            when {
                !enLigne -> "🔌 Hors ligne"
                !adsAutorisees -> "Confidentialité en attente"
                adCd > 0 -> "⏳ ${adCd}s"
                nombreVues >= 50 -> "Limite atteinte"
                else -> "▶  Regarder"
            },
            onClick = onRegarder,
            enabled = enLigne && adsAutorisees && adCd <= 0 && nombreVues < 50,
            small = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
