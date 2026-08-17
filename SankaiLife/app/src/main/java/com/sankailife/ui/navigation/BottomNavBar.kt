package com.sankailife.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.R
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.components.LiquidGlassSurface
import com.sankailife.ui.theme.SankaiGlass
import com.sankailife.ui.theme.SankaiRadius
import com.sankailife.ui.theme.SankaiSpacing
import com.sankailife.ui.theme.sankaiColors

data class NavItem(
    val route: String,
    @StringRes val label: Int,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector
)

val bottomNavItems = listOf(
    NavItem(Screen.Home.route, R.string.nav_home, Icons.Filled.WbSunny, Icons.Outlined.WbSunny),
    NavItem(Screen.Academy.route, R.string.nav_learn, Icons.Filled.School, Icons.Outlined.School),
    NavItem(
        Screen.Life.route,
        R.string.nav_life,
        Icons.Filled.SelfImprovement,
        Icons.Outlined.SelfImprovement
    ),
    NavItem(Screen.Profile.route, R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person)
)

/** Les cinq destinations du cœur sont toujours accessibles, sans verrou de niveau. */
@Composable
fun SankaiBottomNavBar(
    currentRoute: String?,
    showLabels: Boolean,
    onNavigate: (String) -> Unit
) {
    val colors = MaterialTheme.sankaiColors
    val haptics = LocalHaptics.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = SankaiSpacing.Md, vertical = SankaiSpacing.Sm),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
            forme = RoundedCornerShape(SankaiRadius.Navigation),
            intensite = SankaiGlass.NavigationIntensity
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SankaiSpacing.Xs, vertical = SankaiSpacing.Xs),
                horizontalArrangement = Arrangement.spacedBy(SankaiSpacing.Xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bottomNavItems.forEach { item ->
                    val label = stringResource(item.label)
                    val selected = currentRoute == item.route
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.06f else 1f,
                        label = "navigation_scale"
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (selected) colors.accent else colors.textSecondary,
                        label = "navigation_color"
                    )
                    val backgroundAlpha by animateFloatAsState(
                        targetValue = if (selected) 0.16f else 0f,
                        label = "navigation_background"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .scale(scale)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(SankaiRadius.Medium))
                            .background(colors.accent.copy(alpha = backgroundAlpha))
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = {
                                    if (!selected) {
                                        haptics.click()
                                        onNavigate(item.route)
                                    }
                                }
                            )
                            .padding(
                                horizontal = SankaiSpacing.Xs,
                                vertical = if (showLabels) SankaiSpacing.Xs else SankaiSpacing.Md
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (selected) item.iconSelected else item.iconUnselected,
                                contentDescription = if (showLabels) null else label,
                                tint = iconColor,
                                modifier = Modifier.size(23.dp)
                            )
                            if (showLabels) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    label,
                                    color = iconColor,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold
                                    else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
