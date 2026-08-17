package com.sankailife.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.theme.*

@Composable
fun SankaiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val mod = if (onClick != null) modifier.clickable(role = Role.Button) { onClick() } else modifier
    Box(
        modifier = mod
            .fillMaxWidth()
            .clip(RoundedCornerShape(SankaiRadius.Medium))
            .background(c.surface2)
            .border(0.5.dp, c.border, RoundedCornerShape(SankaiRadius.Medium))
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SankaiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondary: Boolean = false,
    small: Boolean = false
) {
    val c = MaterialTheme.sankaiColors
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.975f else 1f,
        animationSpec = tween(SankaiMotion.Fast),
        label = "sankaiButtonPress"
    )
    // Couleurs unies, tirées du thème.
    //
    // Le bouton principal était un dégradé or codé en dur : il ignorait
    // complètement la palette du téléphone, et se superposait à elle. Un
    // dégradé fixe posé sur une couleur dynamique donne deux identités
    // visuelles qui se contredisent — c'est le défaut signalé.
    val fond = when {
        !enabled -> c.surface3
        secondary -> c.surface2
        else -> MaterialTheme.colorScheme.primary
    }
    val textColor = when {
        !enabled -> c.textDisabled
        secondary -> c.textPrimary
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (enabled && !secondary) {
                    Modifier.shadow(SankaiElevation.Low, RoundedCornerShape(SankaiRadius.Small))
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(SankaiRadius.Small))
            .background(fond)
            .border(
                width = 1.dp,
                color = when {
                    !enabled -> c.border.copy(alpha = 0.55f)
                    secondary -> Color.White.copy(alpha = 0.10f)
                    else -> Color.White.copy(alpha = 0.30f)
                },
                shape = RoundedCornerShape(SankaiRadius.Small)
            )
            // Tous les boutons de l'app passent par ici : c'est le seul endroit
            // à modifier pour changer la sensation d'un appui.
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    role = Role.Button
                ) { haptics.click(); onClick() }
                else Modifier.semantics {
                    role = Role.Button
                    disabled()
                }
            )
            .padding(horizontal = if (small) 14.dp else 20.dp, vertical = if (small) 8.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontWeight = FontWeight.Bold,
            fontSize = if (small) 13.sp else 15.sp)
    }
}

@Composable
fun StatCard(value: String, label: String, valueColor: Color = MaterialTheme.sankaiColors.textPrimary) {
    val c = MaterialTheme.sankaiColors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface2)
            .border(0.5.dp, c.border, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(value, color = valueColor, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(2.dp))
            Text(label, color = c.textSecondary, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun XpBar(xp: Int, xpNext: Int, modifier: Modifier = Modifier) {
    val c = MaterialTheme.sankaiColors
    val progress = if (xpNext > 0) xp.toFloat() / xpNext else 0f
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = c.accentSecondary,
        trackColor = c.surface3
    )
}

@Composable
fun SectionTitle(text: String) {
    val c = MaterialTheme.sankaiColors
    Text(
        text.uppercase(),
        color = c.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}


