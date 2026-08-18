package com.sankailife.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.R
import com.sankailife.core.haptics.LocalHaptics

data class NavItem(
    val route: String,
    @StringRes val label: Int,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
    /** L'action principale, au centre : le bouton relevé. */
    val center: Boolean = false
)

/**
 * Cinq destinations, « Accueil » au centre.
 *
 * La section centrale est l'action principale de l'application : elle montre
 * ce qui mérite l'attention aujourd'hui, puis laisse repartir dans la vraie
 * vie. Apprendre et Vie à sa gauche, Culture et Profil à sa droite.
 */
val bottomNavItems = listOf(
    NavItem(Screen.Academy.route, R.string.nav_learn, Icons.Filled.School, Icons.Outlined.School),
    NavItem(
        Screen.Life.route,
        R.string.nav_life,
        Icons.Filled.SelfImprovement,
        Icons.Outlined.SelfImprovement
    ),
    NavItem(
        Screen.Home.route,
        R.string.nav_today,
        Icons.Filled.WbSunny,
        Icons.Outlined.WbSunny,
        center = true
    ),
    NavItem(
        Screen.Capsules.route,
        R.string.nav_culture,
        Icons.Filled.AutoStories,
        Icons.Outlined.AutoStories
    ),
    NavItem(Screen.Profile.route, R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person)
)

/**
 * Barre de navigation basse, Material 3, compatible Dynamic Color.
 *
 * Une seule barre flottante : coins arrondis, légère élévation, rien derrière
 * — pas de calque, pas de séparation avec l'écran. Toutes les couleurs viennent
 * du thème (Dynamic Color quand le téléphone le fournit) : la barre suit le
 * mode clair/sombre du système et la palette du téléphone. Au centre, le
 * bouton rond relevé reste l'action principale, sur `primary`.
 */
@Composable
fun SankaiBottomNavBar(
    currentRoute: String?,
    showLabels: Boolean,
    onNavigate: (String) -> Unit
) {
    val haptics = LocalHaptics.current
    val centerItem = bottomNavItems.first { it.center }
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .height(66.dp)
                .shadow(12.dp, RoundedCornerShape(32.dp), clip = false)
                .background(scheme.surfaceContainer, RoundedCornerShape(32.dp))
                .border(
                    1.dp,
                    scheme.outlineVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(32.dp)
                )
        ) {
            val lateraux = bottomNavItems.filterNot { it.center }
            val gauche = lateraux.take(2)
            val droite = lateraux.drop(2)

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                gauche.forEach { item ->
                    OngletLaterale(
                        item = item,
                        selected = currentRoute == item.route,
                        showLabels = showLabels,
                        onClick = {
                            if (currentRoute != item.route) {
                                haptics.click()
                                onNavigate(item.route)
                            }
                        }
                    )
                }
                // L'emplacement du bouton central, pour que les onglets
                // latéraux ne viennent pas se glisser dessous.
                Spacer(Modifier.width(58.dp))
                droite.forEach { item ->
                    OngletLaterale(
                        item = item,
                        selected = currentRoute == item.route,
                        showLabels = showLabels,
                        onClick = {
                            if (currentRoute != item.route) {
                                haptics.click()
                                onNavigate(item.route)
                            }
                        }
                    )
                }
            }

            BoutonCentral(
                item = centerItem,
                selected = currentRoute == centerItem.route,
                showLabels = showLabels,
                onClick = {
                    if (currentRoute != centerItem.route) {
                        haptics.click()
                        onNavigate(centerItem.route)
                    }
                }
            )

            // Le libellé du centre vit sous le bouton, sur la ligne des
            // autres libellés — jamais par-dessus le bouton lui-même.
            if (showLabels) {
                Text(
                    stringResource(centerItem.label),
                    color = scheme.onSurfaceVariant,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun RowScope.OngletLaterale(
    item: NavItem,
    selected: Boolean,
    showLabels: Boolean,
    onClick: () -> Unit
) {
    val label = stringResource(item.label)
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        label = "navigation_icon_scale"
    )
    val couleur = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clip(RoundedCornerShape(26.dp))
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (selected) item.iconSelected else item.iconUnselected,
                contentDescription = if (showLabels) null else label,
                tint = couleur,
                modifier = Modifier.size(19.dp).scale(scale)
            )
            if (showLabels) {
                Spacer(Modifier.height(3.dp))
                Text(
                    label,
                    color = couleur,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Le bouton rond relevé au centre.
 *
 * Il dépasse du haut de la barre (décalage négatif) et porte un halo de la
 * couleur `primary` qui respire lentement. Sélectionné, il gagne un fin
 * anneau `onPrimary` — rien d'autre ne bouge : le bouton reste le même,
 * c'est l'état qui se lit.
 */
@Composable
private fun BoxScope.BoutonCentral(
    item: NavItem,
    selected: Boolean,
    showLabels: Boolean,
    onClick: () -> Unit
) {
    val label = stringResource(item.label)
    val primary = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "navigation_halo")
    val haloAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "navigation_halo_alpha"
    )

    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = (-24).dp)
            .size(58.dp),
        contentAlignment = Alignment.Center
    ) {
        // Halo de la couleur d'accent, dessiné derrière le bouton.
        Box(
            Modifier
                .fillMaxSize()
                .scale(1.4f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.45f * haloAlpha), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(58.dp)
                .shadow(8.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(primary)
                .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onPrimary, CircleShape)
                    else Modifier
                )
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.iconSelected,
                contentDescription = if (showLabels) null else label,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(27.dp)
            )
        }
    }
}
