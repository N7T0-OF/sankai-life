package com.sankailife.ui.screens.life.focus

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sankailife.R
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.sankaiColors

/**
 * Minuteur volontairement calme : le temps choisi reste le seul objectif.
 * Les récompenses historiques continuent d'être enregistrées en arrière-plan
 * pour préserver les sauvegardes, mais elles ne prolongent plus la session.
 */
@Composable
fun FocusScreen(viewModel: FocusViewModel, onBack: () -> Unit) {
    val state by viewModel.timerState.collectAsStateWithLifecycle()
    val remaining by viewModel.remaining.collectAsStateWithLifecycle()
    val hours by viewModel.hours.collectAsStateWithLifecycle()
    val minutes by viewModel.minutes.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val sessionDone by viewModel.sessionDone.collectAsStateWithLifecycle()
    val colors = MaterialTheme.sankaiColors
    val activity = LocalContext.current as? Activity
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setScreenActive(true)
                Lifecycle.Event.ON_STOP -> viewModel.setScreenActive(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        viewModel.setScreenActive(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.setScreenActive(false)
        }
    }

    if (sessionDone) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSession,
            title = { Text(stringResource(R.string.focus_done_title)) },
            text = { Text(stringResource(R.string.focus_done_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissSession()
                        activity?.finishAndRemoveTask()
                    }
                ) {
                    Text(stringResource(R.string.focus_close_app))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSession) {
                    Text(stringResource(R.string.focus_continue_freely))
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(colors.background)
    ) {
        val compact = maxHeight < 650.dp
        val timerSize = if (compact) 194.dp else 240.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = colors.textSecondary
                    )
                }
                Text(
                    stringResource(R.string.focus_title),
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.height(if (compact) 10.dp else 24.dp))

            if (state == TimerState.IDLE) {
                Text(
                    stringResource(R.string.focus_duration),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberPicker(
                        label = stringResource(R.string.focus_hours),
                        min = 0,
                        max = 4,
                        value = hours,
                        onChange = viewModel::setHours
                    )
                    Text(":", color = colors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    NumberPicker(
                        label = stringResource(R.string.focus_minutes),
                        min = 5,
                        max = 120,
                        value = minutes,
                        step = 5,
                        onChange = viewModel::setMinutes
                    )
                }
                Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
            }

            Box(Modifier.size(timerSize), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 10.dp,
                    color = colors.accentSecondary,
                    trackColor = colors.surface3,
                    strokeCap = StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = viewModel.formatTime(remaining),
                        color = colors.textPrimary,
                        fontSize = if (compact) 39.sp else 48.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = when (state) {
                            TimerState.RUNNING -> stringResource(R.string.focus_running)
                            TimerState.PAUSED -> stringResource(R.string.focus_paused)
                            TimerState.FINISHED -> stringResource(R.string.focus_finished)
                            TimerState.IDLE -> stringResource(R.string.focus_selected_duration, hours, minutes)
                        },
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(if (compact) 18.dp else 32.dp))

            when (state) {
                TimerState.IDLE -> SankaiButton(
                    text = stringResource(R.string.focus_start),
                    onClick = viewModel::start,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                )
                TimerState.RUNNING -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SankaiButton(
                        text = stringResource(R.string.focus_pause),
                        onClick = viewModel::pause,
                        secondary = true,
                        modifier = Modifier.weight(1f).height(54.dp)
                    )
                    SankaiButton(
                        text = stringResource(R.string.focus_stop),
                        onClick = viewModel::stop,
                        secondary = true,
                        modifier = Modifier.weight(1f).height(54.dp)
                    )
                }
                TimerState.PAUSED -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SankaiButton(
                        text = stringResource(R.string.focus_resume),
                        onClick = viewModel::resume,
                        modifier = Modifier.weight(1f).height(54.dp)
                    )
                    SankaiButton(
                        text = stringResource(R.string.focus_stop),
                        onClick = viewModel::stop,
                        secondary = true,
                        modifier = Modifier.weight(1f).height(54.dp)
                    )
                }
                TimerState.FINISHED -> SankaiButton(
                    text = stringResource(R.string.focus_new_session),
                    onClick = viewModel::dismissSession,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.focus_intentional_hint),
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun NumberPicker(
    label: String,
    min: Int,
    max: Int,
    value: Int,
    step: Int = 1,
    onChange: (Int) -> Unit
) {
    val colors = MaterialTheme.sankaiColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = colors.textSecondary, fontSize = 11.sp)
        IconButton(onClick = { if (value + step <= max) onChange(value + step) }) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.focus_increase, label),
                tint = colors.textPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface2)
                .border(1.dp, colors.border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "%02d".format(value),
                color = colors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        IconButton(onClick = { if (value - step >= min) onChange(value - step) }) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.focus_decrease, label),
                tint = colors.textPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
