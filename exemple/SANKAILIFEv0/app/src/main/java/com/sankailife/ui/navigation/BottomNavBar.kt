package com.sankailife.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.ui.theme.sankaiColors

data class NavItem(
    val route: String,
    val label: String,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
    val badgeCount: Int = 0
)

val bottomNavItems = listOf(
    NavItem(Screen.Shop.route,       "Shop",   Icons.Filled.ShoppingCart,  Icons.Outlined.ShoppingCart),
    NavItem(Screen.Life.route,       "Vie",    Icons.Filled.Bolt,           Icons.Outlined.Bolt),
    NavItem(Screen.Home.route,       "Accueil",Icons.Filled.Home,           Icons.Outlined.Home),
    NavItem(Screen.Challenges.route, "Défis",  Icons.Filled.TrackChanges,   Icons.Outlined.TrackChanges),
    NavItem(Screen.Profile.route,    "Profil", Icons.Filled.Person,         Icons.Outlined.Person)
)

@Composable
fun SankaiBottomNavBar(
    currentRoute: String?,
    showLabels: Boolean,
    challengeBadge: Int,
    onNavigate: (String) -> Unit
) {
    val c = MaterialTheme.sankaiColors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface1)
    ) {
        Divider(color = c.border, thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.route
                val scale by animateFloatAsState(if (isSelected) 1.1f else 1f, label = "scale")
                val iconColor by animateColorAsState(
                    if (isSelected) c.accent else c.textSecondary, label = "color"
                )
                val badge = if (item.route == Screen.Challenges.route) challengeBadge else item.badgeCount

                Box(
                    modifier = Modifier
                        .scale(scale)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onNavigate(item.route) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BadgedBox(badge = {
                            if (badge > 0) Badge(containerColor = MaterialTheme.colorScheme.error) {
                                Text(badge.toString(), fontSize = 9.sp)
                            }
                        }) {
                            Icon(
                                imageVector = if (isSelected) item.iconSelected else item.iconUnselected,
                                contentDescription = item.label,
                                tint = iconColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (showLabels) {
                            Spacer(Modifier.height(2.dp))
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
