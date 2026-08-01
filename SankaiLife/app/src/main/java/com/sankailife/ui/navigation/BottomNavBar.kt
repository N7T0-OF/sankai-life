package com.sankailife.ui.navigation

import androidx.compose.animation.animateColorAsState
import com.sankailife.R
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.components.LiquidGlassSurface
import com.sankailife.core.domain.engine.DeblocageEngine
import com.sankailife.ui.theme.sankaiColors
import com.sankailife.ui.theme.SankaiGlass
import com.sankailife.ui.theme.SankaiRadius
import com.sankailife.ui.theme.SankaiSpacing

data class NavItem(
    val route: String,
    @StringRes val label: Int,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
    val badgeCount: Int = 0
)

val bottomNavItems = listOf(
    NavItem(Screen.Shop.route,       R.string.nav_shop,       Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
    NavItem(Screen.Life.route,       R.string.nav_life,       Icons.Filled.Bolt,         Icons.Outlined.Bolt),
    NavItem(Screen.Home.route,       R.string.nav_home,       Icons.Filled.Home,         Icons.Outlined.Home),
    NavItem(Screen.Challenges.route, R.string.nav_challenges, Icons.Filled.TrackChanges, Icons.Outlined.TrackChanges),
    NavItem(Screen.Profile.route,    R.string.nav_profile,    Icons.Filled.Person,       Icons.Outlined.Person)
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
    niveau: Int = 1,
    onVerrou: (DeblocageEngine.Verrou) -> Unit = {},
    onNavigate: (String) -> Unit
) {
    val c = MaterialTheme.sankaiColors
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
                    val libelle = stringResource(item.label)
                    val isSelected = currentRoute == item.route

                    // Le verrou de niveau, s'il y en a un pour cet onglet.
                    //
                    // L'onglet reste visible et grisé plutôt que disparaître : une
                    // barre qui change de composition en cours de partie déplace
                    // les autres boutons sous le doigt, et on n'apprend jamais ce
                    // qui existe plus loin.
                    val fonction = when (item.route) {
                        Screen.Challenges.route -> DeblocageEngine.Fonction.DEFIS
                        else -> null
                    }
                    val verrou = fonction?.let { DeblocageEngine.verrou(it, niveau) }

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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .scale(scale)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(SankaiRadius.Medium))
                            .background(c.accent.copy(alpha = pastilleAlpha))
                            .selectable(
                                selected = isSelected,
                                role = Role.Tab
                            ) {
                                if (!isSelected) haptics.click()
                                // Un cadenas ne se contente pas de refuser : il
                                // explique ce qu'on obtient et quand.
                                if (verrou != null) onVerrou(verrou) else onNavigate(item.route)
                            }
                            .semantics {
                                if (verrou != null) {
                                    stateDescription = "Verrouillé, ${verrou.explication}"
                                } else if (isSelected) {
                                    stateDescription = "Sélectionné"
                                }
                            }
                            .padding(
                                horizontal = SankaiSpacing.Xs,
                                vertical = if (showLabels) SankaiSpacing.Xs else SankaiSpacing.Md
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BadgedBox(badge = {
                                if (badge > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.clearAndSetSemantics {
                                            contentDescription = "$badge notification${if (badge > 1) "s" else ""}"
                                        }
                                    ) {
                                        Text(badge.toString(), fontSize = 9.sp)
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = when {
                                        verrou != null -> Icons.Filled.Lock
                                        isSelected -> item.iconSelected
                                        else -> item.iconUnselected
                                    },
                                    contentDescription = if (showLabels) null else libelle,
                                    tint = if (verrou != null) c.textDisabled else iconColor,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                            if (showLabels) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    libelle,
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
