package com.sankailife.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.R
import com.sankailife.core.domain.engine.OnboardingEngine
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.sankaiColors

@Composable
fun OnboardingScreen(onTermine: (dailyMinutes: Int) -> Unit) {
    val colors = MaterialTheme.sankaiColors
    var index by rememberSaveable { mutableIntStateOf(0) }
    var dailyMinutes by rememberSaveable { mutableIntStateOf(5) }
    val indexBorne = OnboardingEngine.borner(index)
    val topic = OnboardingEngine.pages[indexBorne]

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (!OnboardingEngine.estDerniere(indexBorne)) {
            Text(
                stringResource(R.string.onboarding_skip),
                color = colors.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clickable { onTermine(dailyMinutes) }
                    .padding(20.dp)
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp)
                // Les commandes restent accessibles quand la police est
                // agrandie : le contenu central defile entre les deux zones.
                .padding(top = 64.dp, bottom = 184.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = topic,
                // Changement immédiat, sans fondu : la navigation doit rester
                // franche, jamais flottante.
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                label = "onboarding_page"
            ) { page ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = when (page) {
                            OnboardingEngine.Topic.WELCOME -> Icons.Filled.WavingHand
                            OnboardingEngine.Topic.LEARNING -> Icons.Filled.School
                            OnboardingEngine.Topic.CULTURE -> Icons.Filled.AutoStories
                            OnboardingEngine.Topic.INTENTIONAL_USE -> Icons.Filled.SelfImprovement
                            OnboardingEngine.Topic.DAILY_TIME -> Icons.Filled.Schedule
                        },
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(58.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        stringResource(titleFor(page)),
                        color = colors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(bodyFor(page)),
                        color = colors.textSecondary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                    if (page == OnboardingEngine.Topic.DAILY_TIME) {
                        Spacer(Modifier.height(22.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(2, 5, 10, 15, 0).forEach { minutes ->
                                FilterChip(
                                    selected = dailyMinutes == minutes,
                                    onClick = { dailyMinutes = minutes },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = {
                                        Text(
                                            if (minutes == 0) {
                                                stringResource(R.string.onboarding_no_goal)
                                            } else {
                                                stringResource(R.string.onboarding_minutes, minutes)
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OnboardingEngine.pages.indices.forEach { pageIndex ->
                    Box(
                        Modifier
                            .size(if (pageIndex == indexBorne) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (pageIndex == indexBorne) colors.accent else colors.surface3)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            SankaiButton(
                text = if (OnboardingEngine.estDerniere(indexBorne)) {
                    stringResource(R.string.onboarding_finish)
                } else {
                    stringResource(R.string.onboarding_next)
                },
                onClick = {
                    if (OnboardingEngine.estDerniere(indexBorne)) onTermine(dailyMinutes)
                    else index = OnboardingEngine.suivante(indexBorne)
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (indexBorne > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.action_back),
                    color = colors.textDisabled,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { index = OnboardingEngine.precedente(indexBorne) }
                        .padding(8.dp)
                )
            }
        }
    }
}

private fun titleFor(topic: OnboardingEngine.Topic): Int = when (topic) {
    OnboardingEngine.Topic.WELCOME -> R.string.onboarding_welcome_title
    OnboardingEngine.Topic.LEARNING -> R.string.onboarding_learning_title
    OnboardingEngine.Topic.CULTURE -> R.string.onboarding_culture_title
    OnboardingEngine.Topic.INTENTIONAL_USE -> R.string.onboarding_intentional_title
    OnboardingEngine.Topic.DAILY_TIME -> R.string.onboarding_time_title
}

private fun bodyFor(topic: OnboardingEngine.Topic): Int = when (topic) {
    OnboardingEngine.Topic.WELCOME -> R.string.onboarding_welcome_body
    OnboardingEngine.Topic.LEARNING -> R.string.onboarding_learning_body
    OnboardingEngine.Topic.CULTURE -> R.string.onboarding_culture_body
    OnboardingEngine.Topic.INTENTIONAL_USE -> R.string.onboarding_intentional_body
    OnboardingEngine.Topic.DAILY_TIME -> R.string.onboarding_time_body
}
