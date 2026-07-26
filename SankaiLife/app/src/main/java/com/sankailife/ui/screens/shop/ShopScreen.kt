package com.sankailife.ui.screens.shop

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.domain.model.ShopItem
import com.sankailife.ui.components.*
import com.sankailife.ui.theme.*

@Composable
fun ShopScreen(viewModel: ShopViewModel) {
    val user by viewModel.user.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val adCd by viewModel.adCooldown.collectAsState()
    val enLigne by viewModel.isOnline.collectAsState()
    val c = MaterialTheme.sankaiColors
    var selectedTab by remember { mutableIntStateOf(0) }

    // AdMob a besoin de l'Activity pour afficher une pub plein écran.
    val activity = LocalContext.current as? Activity

    val chests  = viewModel.shopItems.filter { it.category == "chest" }
    val boosts  = viewModel.shopItems.filter { it.category == "boost" }
    val upgrades= viewModel.shopItems.filter { it.category == "upgrade" }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            ResourceBar(user.level, user.xp, user.xpNext, user.coins, user.gems)
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(16.dp))
                Text("🛒 Boutique", color = c.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                // Tabs
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(4.dp)) {
                    listOf("Coffres", "Boosts", "Amélios").forEachIndexed { i, label ->
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == i) c.surface3 else Color.Transparent)
                            .clickable { selectedTab = i }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center) {
                            Text(label, color = if (selectedTab == i) c.textPrimary else c.textSecondary,
                                fontSize = 13.sp, fontWeight = if (selectedTab == i) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Ad section (in Coffres tab)
                    if (selectedTab == 0) {
                        item {
                            AdSection(
                                adCd = adCd,
                                adCount = user.adCountToday,
                                enLigne = enLigne,
                                onWatch = { activity?.let { viewModel.watchAd(it) } }
                            )
                        }
                    }

                    val list = when (selectedTab) { 0 -> chests; 1 -> boosts; else -> upgrades }
                    items(list) { item ->
                        ShopItemCard(item = item, userCoins = user.coins, userGems = user.gems,
                            onBuy = { viewModel.purchase(item) })
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }

        AnimatedVisibility(
            visible = toast.isNotBlank(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp),
            enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }
        ) {
            Box(Modifier.clip(RoundedCornerShape(24.dp)).background(c.surface2)
                .border(1.dp, c.accent, RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(toast, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AdSection(adCd: Long, adCount: Int, enLigne: Boolean, onWatch: () -> Unit) {
    val c = MaterialTheme.sankaiColors
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(SuccessGreen.copy(0.08f)).border(1.dp, SuccessGreen.copy(0.3f), RoundedCornerShape(16.dp))
        .padding(16.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("🎥 Regarder une pub", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("+5 🪙", color = CoinColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text("Pubs aujourd'hui : $adCount / 50 • 5 pubs → Bonus +10 🪙",
                color = c.textSecondary, fontSize = 12.sp)
            if (adCd > 0) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { adCd / 25f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = SuccessGreen, trackColor = c.surface3
                )
                Text("Cooldown : ${adCd}s", color = c.textSecondary, fontSize = 11.sp)
            }
            if (!enLigne) {
                Spacer(Modifier.height(4.dp))
                Text("Connexion requise — le reste de la boutique fonctionne hors ligne",
                    color = c.textSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            SankaiButton(
                when {
                    !enLigne -> "🔌 HORS LIGNE"
                    adCd > 0 -> "⏳ ${adCd}s"
                    else -> "▶  REGARDER"
                },
                onClick = onWatch,
                enabled = adCd <= 0 && adCount < 50 && enLigne,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ShopItemCard(item: ShopItem, userCoins: Int, userGems: Int, onBuy: () -> Unit) {
    val c = MaterialTheme.sankaiColors
    val canAfford = (item.costCoins == 0 || userCoins >= item.costCoins) &&
                   (item.costGems  == 0 || userGems  >= item.costGems)
    val accentColor = when (item.category) {
        "chest"   -> ChestRare
        "boost"   -> AccentViolet
        "upgrade" -> AccentGold
        else      -> c.accent
    }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface2)
        .border(1.dp, if (canAfford) accentColor.copy(0.3f) else c.border, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(accentColor.copy(0.12f)),
                contentAlignment = Alignment.Center) {
                Text(when(item.id) { "chest_common"->"📦"; "chest_rare"->"🟦"; "chest_epic"->"💜";
                    "boost_2x_coins"->"⚡"; "boost_skip_cd"->"⏩"; "boost_2x_chest"->"🎁";
                    "slot_module"->"🧩"; "rare_chance"->"⭐"; else->"🛒" }, fontSize = 26.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(item.description, color = c.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row {
                    if (item.costCoins > 0) Text("${item.costCoins} 🪙", color = CoinColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (item.costGems  > 0) Text("${item.costGems} 💎", color = GemColor,  fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            SankaiButton("Acheter", onClick = onBuy, enabled = canAfford, small = true)
        }
    }
}
