package com.sankailife.ui.screens.challenges

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.data.db.entities.ChallengeEntity
import com.sankailife.ui.components.*
import com.sankailife.ui.navigation.Screen
import com.sankailife.ui.theme.*

@Composable
fun ChallengesScreen(viewModel: ChallengesViewModel, onNavigate: (String) -> Unit) {
    val user       by viewModel.user.collectAsState()
    val challenges by viewModel.challenges.collectAsState()
    val toast      by viewModel.toast.collectAsState()
    val c = MaterialTheme.sankaiColors

    val daily  = challenges.filter { it.type == "DAILY" }
    val weekly = challenges.filter { it.type == "WEEKLY" }
    var selectedTab by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            ResourceBar(user.level, user.xp, user.xpNext, user.coins, user.gems)
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(16.dp))
                Text("Défis", color = c.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)

                Spacer(Modifier.height(12.dp))
                // Tabs
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(4.dp)) {
                    listOf("Quotidien", "Hebdo").forEachIndexed { i, label ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (selectedTab == i) c.surface3 else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { selectedTab = i }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (selectedTab == i) c.textPrimary else c.textSecondary,
                                fontSize = 14.sp, fontWeight = if (selectedTab == i) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                val list = if (selectedTab == 0) daily else weekly
                val claimed = list.count { it.isClaimed }
                val total   = list.size

                // Global progress
                if (total > 0) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2)
                        .border(0.5.dp, c.border, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Progression", color = c.textSecondary, fontSize = 12.sp)
                                Text("$claimed / $total", color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { if (total > 0) claimed.toFloat() / total else 0f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = SuccessGreen, trackColor = c.surface3
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list) { challenge ->
                        ChallengeCard(challenge = challenge, onClaim = { viewModel.claimChallenge(challenge.id) },
                            onNavigate = onNavigate)
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }

        // Toast
        AnimatedVisibility(
            visible = toast.isNotBlank(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp),
            enter = fadeIn() + slideInVertically { it },
            exit  = fadeOut() + slideOutVertically { it }
        ) {
            Box(Modifier.clip(RoundedCornerShape(24.dp)).background(c.surface2)
                .border(1.dp, SuccessGreen, RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(toast, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ChallengeCard(challenge: ChallengeEntity, onClaim: () -> Unit, onNavigate: (String) -> Unit) {
    val c = MaterialTheme.sankaiColors
    val progress = if (challenge.targetAmount > 0) challenge.currentProgress.toFloat() / challenge.targetAmount else 0f
    val isClaimed = challenge.isClaimed
    val canClaim  = challenge.isComplete && !isClaimed

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(if (isClaimed) c.surface2.copy(alpha = 0.5f) else c.surface2)
        .border(1.dp, if (canClaim) SuccessGreen.copy(0.5f) else c.border, RoundedCornerShape(16.dp))
        .padding(14.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(challenge.title, color = if (isClaimed) c.textSecondary else c.textPrimary,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(challenge.description, color = c.textSecondary, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (challenge.rewardCoins > 0) Text("+${challenge.rewardCoins} 🪙", color = CoinColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (challenge.rewardXp > 0)    Text("+${challenge.rewardXp} XP", color = AccentViolet, fontSize = 12.sp)
                    if (challenge.rewardChestType.isNotBlank()) Text("🎁 Coffre", color = ChestRare, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (isClaimed) SuccessGreen else if (canClaim) SuccessGreen else AccentViolet,
                trackColor = c.surface3
            )

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("${challenge.currentProgress} / ${challenge.targetAmount}",
                    color = c.textSecondary, fontSize = 12.sp)
                when {
                    isClaimed  -> Box(Modifier.clip(RoundedCornerShape(8.dp)).background(SuccessGreen.copy(0.1f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("✅ Réclamé", color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    canClaim   -> SankaiButton("Réclamer 🎁", onClick = onClaim, small = true)
                    else       -> SankaiButton("En cours...", onClick = {}, small = true, secondary = true, enabled = false)
                }
            }
        }
    }
}
