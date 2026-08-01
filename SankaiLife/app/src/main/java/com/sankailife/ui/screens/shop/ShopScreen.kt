package com.sankailife.ui.screens.shop

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.R
import com.sankailife.core.domain.model.ShopItem
import com.sankailife.core.domain.model.UserState
import com.sankailife.ui.art.ArtJardin
import com.sankailife.ui.art.IconeArt
import com.sankailife.ui.components.LiquidGlassChip
import com.sankailife.ui.components.LiquidGlassSurface
import com.sankailife.ui.components.ResourceBar
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.CoinColor
import com.sankailife.ui.theme.GameBlue
import com.sankailife.ui.theme.GameNavyBottom
import com.sankailife.ui.theme.GameNavyTop
import com.sankailife.ui.theme.GardenGreen
import com.sankailife.ui.theme.RewardGold
import com.sankailife.ui.theme.RewardGoldDark
import com.sankailife.ui.theme.SankaiElevation
import com.sankailife.ui.theme.SankaiMotion
import com.sankailife.ui.theme.SankaiRadius
import com.sankailife.ui.theme.SankaiSpacing
import com.sankailife.ui.theme.sankaiColors

private enum class ShopCategory(@StringRes val label: Int, val value: String?) {
    ALL(R.string.shop_category_all, null),
    CHESTS(R.string.shop_category_chests, "chest"),
    GARDEN(R.string.shop_category_garden, "jardin"),
    PROGRESSION(R.string.shop_category_progression, "progression")
}

private enum class ShopFilter(@StringRes val label: Int) {
    ALL(R.string.shop_filter_all),
    AFFORDABLE(R.string.shop_filter_affordable),
    OFFERS(R.string.shop_filter_offers)
}

private data class LocalizedShopItem(
    val item: ShopItem,
    val name: String,
    val description: String
)

/**
 * Boutique pensée comme un écran de jeu : une offre vedette, un catalogue
 * filtrable et un aperçu produit dédié. La logique d'achat reste entièrement
 * portée par [ShopViewModel] ; cet écran ne simule aucun objet ni paiement.
 */
@Composable
fun ShopScreen(viewModel: ShopViewModel) {
    val user by viewModel.user.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val adCooldown by viewModel.adCooldown.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val adsAllowed by viewModel.adsAutorisees.collectAsState()
    val colors = MaterialTheme.sankaiColors
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = context as? Activity

    var category by remember { mutableStateOf(ShopCategory.ALL) }
    var filter by remember { mutableStateOf(ShopFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var previewed by remember { mutableStateOf<ShopItem?>(null) }

    // Les noms du modèle restent les identifiants de secours utilisés par le
    // domaine. L'interface, elle, recherche et affiche la langue active.
    val localizedItems = remember(configuration, viewModel.shopItems) {
        viewModel.shopItems.map { item ->
            val copy = item.copyResources()
            LocalizedShopItem(
                item = item,
                name = context.getString(copy.first),
                description = context.getString(copy.second)
            )
        }
    }

    val visibleItems = localizedItems.filter { localized ->
        val item = localized.item
        val matchesCategory = category.value == null || item.category == category.value
        val matchesQuery = query.isBlank() ||
            localized.name.contains(query.trim(), ignoreCase = true) ||
            localized.description.contains(query.trim(), ignoreCase = true)
        val price = viewModel.coutReel(item, user)
        val affordable = user.coins >= price && user.gems >= item.costGems
        val matchesFilter = when (filter) {
            ShopFilter.ALL -> true
            ShopFilter.AFFORDABLE -> affordable
            ShopFilter.OFFERS -> viewModel.estOffreDuJour(item)
        }
        matchesCategory && matchesQuery && matchesFilter
    }

    BackHandler(enabled = previewed != null) { previewed = null }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GameNavyTop, GameNavyBottom)))
    ) {
        Column(Modifier.fillMaxSize()) {
            ResourceBar(user.level, user.xp, user.xpNext, user.coins, user.gems)

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = SankaiSpacing.Lg,
                    top = SankaiSpacing.Lg,
                    end = SankaiSpacing.Lg,
                    bottom = SankaiSpacing.Xl
                ),
                horizontalArrangement = Arrangement.spacedBy(SankaiSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(SankaiSpacing.Md)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ShopHeading()
                }

                viewModel.offreDuJour?.let { offer ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        DailyOfferBanner(
                            item = offer,
                            name = context.getString(offer.copyResources().first),
                            description = context.getString(offer.copyResources().second),
                            discountedPrice = viewModel.coutReel(offer, user),
                            regularPrice = if (offer.id == "slot_module") {
                                com.sankailife.core.domain.engine.EconomyEngine.slotCost(user.moduleSlots)
                            } else {
                                offer.costCoins
                            },
                            canBuy = user.coins >= viewModel.coutReel(offer, user) &&
                                user.gems >= offer.costGems,
                            onPreview = { previewed = offer },
                            onBuy = { viewModel.purchase(offer) }
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    ShopSearch(
                        query = query,
                        onQueryChange = { query = it },
                        onClear = { query = "" }
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    ChoiceRail {
                        ShopCategory.entries.forEach { choice ->
                            ChoiceChip(
                                text = stringResource(choice.label),
                                selected = category == choice,
                                onClick = { category = choice }
                            )
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    ChoiceRail {
                        ShopFilter.entries.forEach { choice ->
                            ChoiceChip(
                                text = stringResource(choice.label),
                                selected = filter == choice,
                                onClick = { filter = choice }
                            )
                        }
                    }
                }

                if (category == ShopCategory.ALL || category == ShopCategory.CHESTS) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RewardedAdCard(
                            cooldownSeconds = adCooldown,
                            watchedToday = user.adCountToday,
                            isOnline = isOnline,
                            adsAllowed = adsAllowed,
                            onWatch = { activity?.let(viewModel::watchAd) }
                        )
                    }
                }

                if (visibleItems.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyShopResult(onReset = {
                            query = ""
                            category = ShopCategory.ALL
                            filter = ShopFilter.ALL
                        })
                    }
                } else {
                    items(visibleItems, key = { it.item.id }) { localized ->
                        ProductCard(
                            item = localized.item,
                            name = localized.name,
                            description = localized.description,
                            price = viewModel.coutReel(localized.item, user),
                            user = user,
                            isOffer = viewModel.estOffreDuJour(localized.item),
                            onPreview = { previewed = localized.item },
                            onBuy = { viewModel.purchase(localized.item) }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = toast.isNotBlank(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = SankaiSpacing.Lg, vertical = SankaiSpacing.Lg),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            LiquidGlassChip(
                modifier = Modifier.shadow(SankaiElevation.Medium, RoundedCornerShape(SankaiRadius.Pill))
            ) {
                Text(
                    toast,
                    modifier = Modifier.padding(horizontal = SankaiSpacing.Lg, vertical = SankaiSpacing.Md),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        AnimatedContent(
            targetState = previewed,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                (fadeIn(tween(SankaiMotion.Standard)) + scaleIn(initialScale = 0.96f)) togetherWith
                    (fadeOut(tween(SankaiMotion.Fast)) + scaleOut(targetScale = 0.96f))
            },
            label = "shopProductPreview"
        ) { item ->
            if (item != null) {
                val copy = item.copyResources()
                ProductPreviewOverlay(
                    item = item,
                    name = stringResource(copy.first),
                    description = stringResource(copy.second),
                    price = viewModel.coutReel(item, user),
                    user = user,
                    onClose = { previewed = null },
                    onBuy = {
                        viewModel.purchase(item)
                        previewed = null
                    }
                )
            } else {
                Spacer(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ShopHeading() {
    val colors = MaterialTheme.sankaiColors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(SankaiRadius.Medium))
                .background(Brush.linearGradient(listOf(RewardGold, RewardGoldDark))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Storefront, null, tint = Color(0xFF3B2100), modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(SankaiSpacing.Md))
        Column {
            Text(
                stringResource(R.string.shop_title),
                color = colors.textPrimary,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                stringResource(R.string.shop_subtitle),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DailyOfferBanner(
    item: ShopItem,
    name: String,
    description: String,
    discountedPrice: Int,
    regularPrice: Int,
    canBuy: Boolean,
    onPreview: () -> Unit,
    onBuy: () -> Unit
) {
    val colors = MaterialTheme.sankaiColors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SankaiRadius.Large))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF6E3B06), Color(0xFFD98B0B), Color(0xFF76500A))
                )
            )
            .shadow(SankaiElevation.Medium, RoundedCornerShape(SankaiRadius.Large), clip = true)
            .padding(SankaiSpacing.Lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductStage(item = item, size = 86.dp, animated = true, modifier = Modifier.width(104.dp))
            Spacer(Modifier.width(SankaiSpacing.Md))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.shop_daily_offer),
                    color = Color(0xFFFFF1B8),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    description,
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(SankaiSpacing.Sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (regularPrice > discountedPrice) {
                        Text(
                            regularPrice.toString(),
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.labelMedium,
                            textDecoration = TextDecoration.LineThrough
                        )
                        Spacer(Modifier.width(SankaiSpacing.Sm))
                    }
                    CurrencyPrice(coins = discountedPrice, gems = item.costGems, emphasized = true)
                }
                Spacer(Modifier.height(SankaiSpacing.Md))
                Row(horizontalArrangement = Arrangement.spacedBy(SankaiSpacing.Sm)) {
                    PreviewButton(onClick = onPreview)
                    SankaiButton(
                        text = if (canBuy) stringResource(R.string.shop_buy) else stringResource(R.string.shop_insufficient_funds),
                        onClick = onBuy,
                        modifier = Modifier.weight(1f),
                        enabled = canBuy,
                        small = true
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(SankaiRadius.Pill))
                .background(Color(0xFFD52929))
                .padding(horizontal = SankaiSpacing.Sm, vertical = SankaiSpacing.Xs)
        ) {
            Text("-25%", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ShopSearch(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    val colors = MaterialTheme.sankaiColors
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(SankaiRadius.Large),
        placeholder = { Text(stringResource(R.string.shop_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (query.isNotBlank()) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.shop_clear_search),
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(role = Role.Button, onClick = onClear)
                        .padding(12.dp)
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedContainerColor = colors.surface1.copy(alpha = 0.92f),
            unfocusedContainerColor = colors.surface1.copy(alpha = 0.82f),
            focusedBorderColor = GameBlue,
            unfocusedBorderColor = colors.border,
            focusedLeadingIconColor = GameBlue,
            unfocusedLeadingIconColor = colors.textSecondary,
            focusedPlaceholderColor = colors.textSecondary,
            unfocusedPlaceholderColor = colors.textSecondary
        )
    )
}

@Composable
private fun ChoiceRail(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(SankaiSpacing.Sm),
        content = content
    )
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.sankaiColors
    LiquidGlassSurface(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(SankaiRadius.Pill))
            .clickable(role = Role.Tab, onClick = onClick),
        forme = RoundedCornerShape(SankaiRadius.Pill),
        selectionne = selected
    ) {
        Box(
            Modifier
                .background(if (selected) GameBlue.copy(alpha = 0.28f) else Color.Transparent)
                .padding(horizontal = SankaiSpacing.Lg, vertical = SankaiSpacing.Md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (selected) Color.White else colors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ProductCard(
    item: ShopItem,
    name: String,
    description: String,
    price: Int,
    user: UserState,
    isOffer: Boolean,
    onPreview: () -> Unit,
    onBuy: () -> Unit
) {
    val colors = MaterialTheme.sankaiColors
    val canBuy = user.coins >= price && user.gems >= item.costGems
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 252.dp)
            .graphicsLayer {
                scaleX = if (pressed) 0.98f else 1f
                scaleY = if (pressed) 0.98f else 1f
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onPreview
            ),
        forme = RoundedCornerShape(SankaiRadius.Large),
        intensite = 0.92f
    ) {
        Column(Modifier.fillMaxSize().padding(SankaiSpacing.Md)) {
            Box(Modifier.fillMaxWidth()) {
                ProductStage(item = item, size = 74.dp, modifier = Modifier.fillMaxWidth())
                if (isOffer) {
                    Text(
                        "-25%",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(SankaiRadius.Pill))
                            .background(Color(0xFFD52929))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Spacer(Modifier.height(SankaiSpacing.Sm))
            Text(
                name,
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                description,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            CurrencyPrice(coins = price, gems = item.costGems)
            Spacer(Modifier.height(SankaiSpacing.Sm))
            SankaiButton(
                text = if (canBuy) stringResource(R.string.shop_buy) else stringResource(R.string.shop_insufficient_funds),
                onClick = onBuy,
                modifier = Modifier.fillMaxWidth(),
                enabled = canBuy,
                small = true
            )
        }
    }
}

@Composable
private fun ProductStage(
    item: ShopItem,
    size: Dp,
    modifier: Modifier = Modifier,
    animated: Boolean = false
) {
    // Seules la bannière et la fiche d'aperçu s'animent. Lancer deux
    // InfiniteTransition sur chaque carte du catalogue réveillerait le GPU en
    // permanence, même hors de la zone visible.
    val lift: Float
    val turn: Float
    if (animated) {
        val infinite = rememberInfiniteTransition(label = "productStage")
        val animatedLift by infinite.animateFloat(
            initialValue = 0f,
            targetValue = -5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "productLift"
        )
        val animatedTurn by infinite.animateFloat(
            initialValue = -7f,
            targetValue = 7f,
            animationSpec = infiniteRepeatable(
                animation = tween(2_400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "productTurn"
        )
        lift = animatedLift
        turn = animatedTurn
    } else {
        lift = 0f
        turn = 0f
    }

    Box(modifier.height(size + 24.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .offset(y = size * 0.38f)
                .size(width = size * 0.72f, height = size * 0.18f)
                .blur(7.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        )
        Box(
            Modifier
                .offset(y = lift.dp)
                .graphicsLayer {
                    rotationY = turn
                    cameraDistance = 18f * density
                },
            contentAlignment = Alignment.Center
        ) {
            ProductVisual(item = item, size = size)
        }
    }
}

@Composable
private fun ProductVisual(item: ShopItem, size: Dp) {
    when {
        item.category == "chest" -> IconeArt(
            ArtJardin.coffre(item.id.removePrefix("chest_")),
            taille = size
        )
        item.id.startsWith("eau") -> IconeArt(ArtJardin.eau, taille = size)
        item.id.startsWith("compost") -> IconeArt(ArtJardin.compost, taille = size)
        item.id == "bouclier" -> Icon(Icons.Filled.Shield, null, tint = GameBlue, modifier = Modifier.size(size))
        item.id == "slot_module" -> Icon(Icons.Filled.AutoStories, null, tint = GardenGreen, modifier = Modifier.size(size))
        else -> Icon(Icons.Filled.Inventory2, null, tint = RewardGold, modifier = Modifier.size(size))
    }
}

@Composable
private fun CurrencyPrice(coins: Int, gems: Int, emphasized: Boolean = false) {
    val colors = MaterialTheme.sankaiColors
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (coins > 0) {
            IconeArt(ArtJardin.piece, taille = if (emphasized) 23.dp else 19.dp)
            Spacer(Modifier.width(SankaiSpacing.Xs))
            Text(
                coins.toString(),
                color = if (emphasized) Color.White else CoinColor,
                style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black
            )
        }
        if (gems > 0) {
            if (coins > 0) Spacer(Modifier.width(SankaiSpacing.Md))
            Icon(Icons.Filled.Diamond, null, tint = Color(0xFF67D9FF), modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(SankaiSpacing.Xs))
            Text(
                gems.toString(),
                color = if (emphasized) Color.White else Color(0xFF67D9FF),
                style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black
            )
        }
        if (coins <= 0 && gems <= 0) {
            Text(
                stringResource(R.string.shop_free),
                color = colors.textPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun PreviewButton(onClick: () -> Unit) {
    val description = stringResource(R.string.shop_preview)
    LiquidGlassSurface(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(SankaiRadius.Medium))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        forme = RoundedCornerShape(SankaiRadius.Medium)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Visibility, null, tint = Color.White)
        }
    }
}

@Composable
private fun RewardedAdCard(
    cooldownSeconds: Long,
    watchedToday: Int,
    isOnline: Boolean,
    adsAllowed: Boolean,
    onWatch: () -> Unit
) {
    val colors = MaterialTheme.sankaiColors
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        forme = RoundedCornerShape(SankaiRadius.Large),
        intensite = 0.9f
    ) {
        Row(
            Modifier.padding(SankaiSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(SankaiRadius.Medium))
                    .background(GameBlue.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.OndemandVideo, null, tint = GameBlue, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(SankaiSpacing.Md))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.shop_ad_title),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when {
                        !adsAllowed -> stringResource(R.string.shop_ad_consent_unavailable)
                        isOnline -> stringResource(R.string.shop_ad_today, watchedToday, 50)
                        else -> stringResource(R.string.shop_ad_offline_detail)
                    },
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(SankaiSpacing.Sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconeArt(ArtJardin.piece, taille = 20.dp)
                    Text(" +5", color = CoinColor, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(SankaiSpacing.Md))
                    SankaiButton(
                        text = when {
                            !isOnline -> stringResource(R.string.shop_ad_offline)
                            !adsAllowed -> stringResource(R.string.shop_ad_consent_pending)
                            cooldownSeconds > 0 -> stringResource(R.string.shop_ad_cooldown, cooldownSeconds)
                            watchedToday >= 50 -> stringResource(R.string.shop_ad_limit)
                            else -> stringResource(R.string.shop_ad_watch)
                        },
                        onClick = onWatch,
                        modifier = Modifier.weight(1f),
                        enabled = isOnline && adsAllowed && cooldownSeconds <= 0 && watchedToday < 50,
                        small = true
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyShopResult(onReset: () -> Unit) {
    val colors = MaterialTheme.sankaiColors
    LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(SankaiSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Search, null, tint = colors.textSecondary, modifier = Modifier.size(38.dp))
            Spacer(Modifier.height(SankaiSpacing.Sm))
            Text(
                stringResource(R.string.shop_no_results),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.shop_no_results_hint),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(SankaiSpacing.Md))
            SankaiButton(stringResource(R.string.shop_reset_filters), onReset, small = true)
        }
    }
}

@Composable
private fun ProductPreviewOverlay(
    item: ShopItem,
    name: String,
    description: String,
    price: Int,
    user: UserState,
    onClose: () -> Unit,
    onBuy: () -> Unit
) {
    val colors = MaterialTheme.sankaiColors
    val canBuy = user.coins >= price && user.gems >= item.costGems
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            )
            .padding(SankaiSpacing.Xl),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { }
                ),
            forme = RoundedCornerShape(30.dp),
            intensite = 0.98f
        ) {
            Column(
                Modifier.fillMaxWidth().padding(SankaiSpacing.Xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.shop_preview_title),
                        color = GameBlue,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    val closeDescription = stringResource(R.string.shop_close_preview)
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = closeDescription,
                        tint = colors.textPrimary,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onClose)
                            .padding(12.dp)
                    )
                }
                ProductStage(item = item, size = 148.dp, animated = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(SankaiSpacing.Lg))
                Text(
                    name,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(SankaiSpacing.Xs))
                Text(
                    description,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(SankaiSpacing.Lg))
                CurrencyPrice(coins = price, gems = item.costGems)
                Spacer(Modifier.height(SankaiSpacing.Lg))
                SankaiButton(
                    text = if (canBuy) stringResource(R.string.shop_buy) else stringResource(R.string.shop_insufficient_funds),
                    onClick = onBuy,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canBuy
                )
            }
        }
    }
}

private fun ShopItem.copyResources(): Pair<Int, Int> = when (id) {
    "chest_common" -> R.string.shop_item_chest_common_name to R.string.shop_item_chest_common_description
    "chest_rare" -> R.string.shop_item_chest_rare_name to R.string.shop_item_chest_rare_description
    "chest_epic" -> R.string.shop_item_chest_epic_name to R.string.shop_item_chest_epic_description
    "eau_10" -> R.string.shop_item_water_10_name to R.string.shop_item_water_10_description
    "eau_30" -> R.string.shop_item_water_30_name to R.string.shop_item_water_30_description
    "compost_10" -> R.string.shop_item_compost_10_name to R.string.shop_item_compost_10_description
    "slot_module" -> R.string.shop_item_memo_slot_name to R.string.shop_item_memo_slot_description
    "bouclier" -> R.string.shop_item_streak_shield_name to R.string.shop_item_streak_shield_description
    else -> R.string.shop_item_unknown_name to R.string.shop_item_unknown_description
}
