package com.sankailife.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.art.ArtJardin
import com.sankailife.ui.art.IconeArt
import com.sankailife.ui.theme.*

@Composable
fun ResourceBar(level: Int, xp: Int, xpNext: Int, coins: Int, gems: Int) {
    val c = MaterialTheme.sankaiColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface1)
            .padding(horizontal = SankaiSpacing.Lg, vertical = SankaiSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Level badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(c.accentSecondary)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text("LVL $level", color = Color.White, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        // XP bar
        Column(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { if (xpNext > 0) xp.toFloat() / xpNext else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = c.accentSecondary,
                trackColor = c.surface3
            )
            Text(
                "$xp / $xpNext XP",
                color = c.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(top = SankaiSpacing.Xxs)
            )
        }
        Spacer(Modifier.width(12.dp))

        // Les icônes sont plus grandes que leur texte, et non l'inverse.
        //
        // La pièce était un rond de couleur de 10 dp à côté d'un chiffre de
        // 13 sp : on ne voyait pas le dessin. C'est l'illustration qui doit
        // porter la reconnaissance, le chiffre ne fait que préciser.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "${formatNumber(coins)} pièces"
            }
        ) {
            IconeArt(ArtJardin.piece, taille = 24.dp)
            Spacer(Modifier.width(4.dp))
            Text(formatNumber(coins), color = c.textPrimary, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "${formatNumber(gems)} gemmes"
            }
        ) {
            Icon(Icons.Filled.Diamond, null, tint = GemColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text(formatNumber(gems), color = c.textPrimary,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

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
    val bg = when {
        !enabled -> Brush.verticalGradient(listOf(c.surface3, c.surface2))
        secondary -> Brush.verticalGradient(listOf(c.surface3, c.surface2))
        else -> Brush.verticalGradient(listOf(RewardGold, RewardGoldDark))
    }
    val textColor = when {
        !enabled -> c.textDisabled
        secondary -> c.textPrimary
        else -> Color(0xFF2B1800)
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
            .background(bg)
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
fun StreakBadge(streak: Int) {
    val c = MaterialTheme.sankaiColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface2)
            .border(1.dp, WarningAmber.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text("🔥", fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
        Text("$streak j", color = WarningAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
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

@Composable
fun LevelUpDialog(level: Int, coins: Int, onDismiss: () -> Unit) {
    val haptics = LocalHaptics.current
    // La vibration part à l'apparition du dialogue, pas au clic : c'est le
    // moment où le joueur apprend la nouvelle.
    LaunchedEffect(level) { haptics.levelUp() }
    Dialog(onDismissRequest = onDismiss) {
        val c = MaterialTheme.sankaiColors
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(c.surface2)
                .border(1.dp, c.accentSecondary, RoundedCornerShape(24.dp))
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✨", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text("NIVEAU $level !", color = c.accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("Récompenses", color = c.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text("+$coins 🪙", color = CoinColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                SankaiButton("OK !", onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun ChestRewardDialog(title: String, coins: Int, gems: Int, xp: Int, onDismiss: () -> Unit) {
    val haptics = LocalHaptics.current
    LaunchedEffect(Unit) { haptics.reward() }
    Dialog(onDismissRequest = onDismiss) {
        val c = MaterialTheme.sankaiColors
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(c.surface2)
                .border(1.dp, ChestEpic, RoundedCornerShape(24.dp))
                .padding(28.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("🎁", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text(title, color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                if (coins > 0) Text("+$coins 🪙", color = CoinColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (gems > 0)  { Spacer(Modifier.height(4.dp)); Text("+$gems 💎", color = GemColor, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                if (xp > 0)    { Spacer(Modifier.height(4.dp)); Text("+$xp XP", color = AccentViolet, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(24.dp))
                SankaiButton("Super !", onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

fun formatNumber(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000     -> "${n / 1_000}k"
    else           -> n.toString()
}
