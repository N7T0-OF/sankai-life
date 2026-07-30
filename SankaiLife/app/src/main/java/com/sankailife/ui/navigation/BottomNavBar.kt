package com.sankailife.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.components.LiquidGlassSurface
import com.sankailife.ui.theme.sankaiColors

data class NavItem(
    val route: String,
    val label: String,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
    val badgeCount: Int = 0
)

val bottomNavItems = listOf(
    NavItem(Screen.Shop.route,       "Shop",    Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
    NavItem(Screen.Life.route,       "Vie",     Icons.Filled.Bolt,         Icons.Outlined.Bolt),
    NavItem(Screen.Home.route,       "Accueil", Icons.Filled.Home,         Icons.Outlined.Home),
    NavItem(Screen.Challenges.route, "Défis",   Icons.Filled.TrackChanges, Icons.Outlined.TrackChanges),
    NavItem(Screen.Profile.route,    "Profil",  Icons.Filled.Person,       Icons.Outlined.Person)
)

/**
 * Barre de navigation flottante, style « verre ».
 *
 * Note technique : Compose ne sait pas flouter ce qui se trouve *derrière* un
 * composant — `Modifier.blur` ne floute que le contenu du composant lui-même.
 * L'effet de verre est donc obtenu par translucidité, dégradé et liseré clair,
 * ce qui est exactement ce que font la plupart des applications. Un vrai flou
 * d'arrière-plan demanderait une bibliothèque dédiée et coûterait cher en GPU
 * sur les téléphones d'entrée de gamme.
 */
@Composable
fun SankaiBottomNavBar(
    currentRoute: String?,
    showLabels: Boolean,
    challengeBadge: Int,
    homeBadge: Int = 0,
    onNavigate: (String) -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val haptics = LocalHaptics.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            forme = RoundedCornerShape(28.dp)
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.route
                val badge = when (item.route) {
                    Screen.Challenges.route -> challengeBadge
                    // Un coffre prêt se signale ici plutôt que par une carte
                    // supplémentaire dans le contenu de l'accueil.
                    Screen.Home.route -> homeBadge
                    else -> item.badgeCount
                }

                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = 0.55f),
                    label = "scale"
                )
                val iconColor by animateColorAsState(
                    if (isSelected) c.accent else c.textSecondary, label = "iconColor"
                )
                val pastilleAlpha by animateFloatAsState(
                    if (isSelected) 0.16f else 0f, label = "pastille"
                )
                val padH by animateDpAsState(
                    if (isSelected) 16.dp else 12.dp, label = "padH"
                )

                Box(
                    modifier = Modifier
                        .scale(scale)
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.accent.copy(alpha = pastilleAlpha))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!isSelected) haptics.click()
                            onNavigate(item.route)
                        }
                        .padding(horizontal = padH, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BadgedBox(badge = {
                            if (badge > 0) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text(badge.toString(), fontSize = 9.sp)
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (isSelected) item.iconSelected else item.iconUnselected,
                                contentDescription = item.label,
                                tint = iconColor,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                        if (showLabels) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                item.label,
                                color = iconColor,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
        }
    }
}
