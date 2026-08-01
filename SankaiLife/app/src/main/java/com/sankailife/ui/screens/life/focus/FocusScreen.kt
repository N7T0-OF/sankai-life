package com.sankailife.ui.screens.life.focus

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.ui.components.ChestRewardDialog
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.*

@Composable
fun FocusScreen(viewModel: FocusViewModel, onBack: () -> Unit) {
    val state      by viewModel.timerState.collectAsState()
    val remaining  by viewModel.remaining.collectAsState()
    val hours      by viewModel.hours.collectAsState()
    val minutes    by viewModel.minutes.collectAsState()
    val progress   by viewModel.progress.collectAsState()
    val sessionDone by viewModel.sessionDone.collectAsState()
    val earnedXp   by viewModel.earnedXp.collectAsState()
    val c = MaterialTheme.sankaiColors

    if (sessionDone) {
        ChestRewardDialog(
            title = "Session terminée ! 🎉",
            coins = 10, gems = 0, xp = earnedXp,
            onDismiss = { viewModel.dismissSession() }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(c.background).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = c.textSecondary)
            }
            Text("Focus Timer", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(32.dp))

        // Duration picker (only when IDLE)
        if (state == TimerState.IDLE) {
            Text("Durée", color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberPicker("Heures", 0, 4, hours) { viewModel.setHours(it) }
                Text(":", color = c.textPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                NumberPicker("Minutes", 5, 120, minutes, step = 5) { viewModel.setMinutes(it) }
            }
            Spacer(Modifier.height(32.dp))
        }

        // Circular progress timer
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 10.dp,
                color = c.accentSecondary,
                trackColor = c.surface3,
                strokeCap = StrokeCap.Round
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = viewModel.formatTime(remaining),
                    color = c.textPrimary,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                val statusText = when (state) {
                    TimerState.RUNNING -> "En cours..."
                    TimerState.PAUSED  -> "En pause"
                    TimerState.FINISHED -> "Terminé !"
                    TimerState.IDLE    -> "${hours}h ${minutes}min"
                }
                Text(statusText, color = c.textSecondary, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(40.dp))

        // Control buttons
        when (state) {
            TimerState.IDLE -> {
                SankaiButton("▶  DÉMARRER", onClick = { viewModel.start() },
                    modifier = Modifier.fillMaxWidth().height(54.dp))
            }
            TimerState.RUNNING -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SankaiButton("⏸ PAUSE", onClick = { viewModel.pause() },
                        secondary = true, modifier = Modifier.weight(1f).height(54.dp))
                    SankaiButton("⏹ STOP", onClick = { viewModel.stop() },
                        secondary = true, modifier = Modifier.weight(1f).height(54.dp))
                }
            }
            TimerState.PAUSED -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SankaiButton("▶ REPRENDRE", onClick = { viewModel.resume() },
                        modifier = Modifier.weight(1f).height(54.dp))
                    SankaiButton("⏹ STOP", onClick = { viewModel.stop() },
                        secondary = true, modifier = Modifier.weight(1f).height(54.dp))
                }
            }
            TimerState.FINISHED -> {
                SankaiButton("Nouvelle session", onClick = { viewModel.dismissSession() },
                    modifier = Modifier.fillMaxWidth().height(54.dp))
            }
        }

        Spacer(Modifier.height(32.dp))
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(16.dp)) {
            Column {
                Text("💡 Récompenses Focus", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Session 25 min → +50 XP + 10 🪙 + Coffre", color = c.textSecondary, fontSize = 12.sp)
                Text("Session 45+ min → +80 XP", color = c.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun NumberPicker(label: String, min: Int, max: Int, value: Int, step: Int = 1, onChange: (Int) -> Unit) {
    val c = MaterialTheme.sankaiColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = c.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        IconButton(onClick = { if (value + step <= max) onChange(value + step) }) {
            Icon(Icons.Filled.KeyboardArrowUp, null, tint = c.textPrimary, modifier = Modifier.size(28.dp))
        }
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(c.surface2)
                .border(1.dp, c.border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("%02d".format(value), color = c.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace)
        }
        IconButton(onClick = { if (value - step >= min) onChange(value - step) }) {
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = c.textPrimary, modifier = Modifier.size(28.dp))
        }
    }
}
