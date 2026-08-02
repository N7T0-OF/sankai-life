package com.sankailife.ui.screens.life.memo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sankailife.R
import com.sankailife.core.data.db.dao.StatsModule
import com.sankailife.core.data.db.entities.MemoProfileEntity
import com.sankailife.core.domain.engine.ErreursEngine
import com.sankailife.core.domain.engine.FlashcardEngine
import com.sankailife.ui.components.LiquidGlassChip
import com.sankailife.ui.components.LiquidGlassSurface
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.components.SankaiFloatingButton
import com.sankailife.ui.theme.AccentCyan
import com.sankailife.ui.theme.AccentViolet
import com.sankailife.ui.theme.DangerRed
import com.sankailife.ui.theme.GameNavyBottom
import com.sankailife.ui.theme.GameNavyTop
import com.sankailife.ui.theme.RewardGold
import com.sankailife.ui.theme.SankaiRadius
import com.sankailife.ui.theme.SankaiSpacing
import com.sankailife.ui.theme.SuccessGreen
import com.sankailife.ui.theme.sankaiColors
import kotlinx.coroutines.launch
import java.util.Locale

private val memoAccents = listOf(
    Color(0xFF63B8FF),
    Color(0xFF9B8AFB),
    Color(0xFF58C98B),
    Color(0xFFFFB84D),
    Color(0xFFE77DA8)
)

/**
 * Bibliothèque d'apprentissage : chaque Mémo est traité comme un module de
 * progression, avec langue, volume, échéance et maîtrise visibles avant de
 * lancer une session.
 */
@Composable
fun MemoScreen(
    viewModel: MemoViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onReviser: (Long) -> Unit = {},
    onReviserErreurs: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profiles by viewModel.profiles.collectAsState()
    val message by viewModel.message.collectAsState()
    val stats by viewModel.statsParModule.collectAsState()
    val colors = MaterialTheme.sankaiColors
    val snackbar = remember { SnackbarHostState() }
    val copiedMessage = stringResource(R.string.memo_copied)
    val chooserTitle = stringResource(R.string.memo_share_chooser)

    LaunchedEffect(message) {
        if (message.isNotBlank()) {
            snackbar.showSnackbar(message)
            viewModel.messageAffiche()
        }
    }

    val totalCards = stats.values.sumOf { it.total }
    val dueCards = stats.values.sumOf { it.dues }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            MemoTopBar(
                profileCount = profiles.size,
                onBack = onBack,
                onAdd = { viewModel.createNewProfile(onCreated = onEdit) }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = SankaiSpacing.Lg,
                    top = SankaiSpacing.Sm,
                    end = SankaiSpacing.Lg,
                    bottom = SankaiSpacing.Xl
                ),
                verticalArrangement = Arrangement.spacedBy(SankaiSpacing.Md)
            ) {
                item {
                    MemoOverview(
                        profileCount = profiles.size,
                        totalCards = totalCards,
                        dueCards = dueCards
                    )
                }

                if (profiles.isEmpty()) {
                    item {
                        EmptyMemoLibrary {
                            viewModel.createNewProfile(onCreated = onEdit)
                        }
                    }
                } else {
                    items(profiles, key = { it.id }) { profile ->
                        MemoProfileListCard(
                            profile = profile,
                            stats = stats[profile.id],
                            onEdit = { onEdit(profile.id) },
                            onReviser = { onReviser(profile.id) },
                            onToggle = { viewModel.toggleProfile(profile.id, !profile.isActive) },
                            onDelete = { viewModel.deleteProfile(profile.id) },
                            onCopy = {
                                scope.launch {
                                    val text = viewModel.texteAPartager(profile.id)
                                    copyMemoText(context, profile.name, text)
                                    snackbar.showSnackbar(copiedMessage)
                                }
                            },
                            onShare = {
                                scope.launch {
                                    val text = viewModel.texteAPartager(profile.id)
                                    // Le partage copie aussi le contenu : le
                                    // texte reste disponible si la feuille est
                                    // refermée sans choisir d'application.
                                    copyMemoText(context, profile.name, text)
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, profile.name)
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
                                }
                            }
                        )
                    }

                    item {
                        CarteMesErreurs(viewModel, onReviser = onReviserErreurs)
                    }
                }

                item {
                    ImportModuleBouton()
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(SankaiSpacing.Lg)
        )
    }
}

@Composable
private fun MemoTopBar(profileCount: Int, onBack: () -> Unit, onAdd: () -> Unit) {
    val colors = MaterialTheme.sankaiColors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SankaiSpacing.Md, vertical = SankaiSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = colors.textPrimary
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.memo_title),
                color = colors.textPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                stringResource(R.string.memo_profile_count, profileCount),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        SankaiFloatingButton(
            contentDescription = stringResource(R.string.memo_create),
            onClick = onAdd,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Filled.Add, null, tint = RewardGold)
        }
    }
}

@Composable
private fun MemoOverview(profileCount: Int, totalCards: Int, dueCards: Int) {
    val colors = MaterialTheme.sankaiColors
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        forme = RoundedCornerShape(SankaiRadius.Large),
        intensite = 0.92f
    ) {
        Column(Modifier.padding(SankaiSpacing.Lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(SankaiRadius.Medium))
                        .background(AccentViolet.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AutoStories, null, tint = AccentViolet)
                }
                Spacer(Modifier.width(SankaiSpacing.Md))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.memo_library_title),
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.memo_library_hint),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(SankaiSpacing.Md))
            Row(horizontalArrangement = Arrangement.spacedBy(SankaiSpacing.Sm)) {
                MemoSummaryChip(
                    text = stringResource(R.string.memo_summary_modules, profileCount),
                    color = AccentViolet,
                    modifier = Modifier.weight(1f)
                )
                MemoSummaryChip(
                    text = stringResource(R.string.memo_summary_cards, totalCards),
                    color = AccentCyan,
                    modifier = Modifier.weight(1f)
                )
                MemoSummaryChip(
                    text = stringResource(R.string.memo_summary_due, dueCards),
                    color = if (dueCards > 0) RewardGold else SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MemoSummaryChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(SankaiRadius.Medium))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(SankaiRadius.Medium))
            .padding(horizontal = SankaiSpacing.Sm, vertical = SankaiSpacing.Sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyMemoLibrary(onCreate: () -> Unit) {
    val colors = MaterialTheme.sankaiColors
    LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(SankaiSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.AutoStories,
                null,
                tint = colors.textSecondary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(SankaiSpacing.Md))
            Text(
                stringResource(R.string.memo_empty_title),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.memo_empty_hint),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(SankaiSpacing.Lg))
            SankaiButton(
                text = stringResource(R.string.memo_create_first),
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Raccourci d'entraînement, sans aucune récompense économique rejouable. */
@Composable
fun CarteMesErreurs(viewModel: MemoViewModel, onReviser: () -> Unit) {
    val colors = MaterialTheme.sankaiColors
    var count by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { count = viewModel.nombreCartesDifficiles() }
    val summary = ErreursEngine.resume(count) ?: return

    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        forme = RoundedCornerShape(SankaiRadius.Large),
        intensite = 0.92f
    ) {
        Column(Modifier.padding(SankaiSpacing.Lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(SankaiRadius.Medium))
                        .background(DangerRed.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = DangerRed)
                }
                Spacer(Modifier.width(SankaiSpacing.Md))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.memo_errors_title),
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(summary, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
                LiquidGlassChip {
                    Text(
                        count.toString(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = DangerRed,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Spacer(Modifier.height(SankaiSpacing.Md))
            Text(
                stringResource(R.string.memo_errors_hint),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(SankaiSpacing.Md))
            SankaiButton(
                text = stringResource(R.string.memo_review_errors),
                onClick = onReviser,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun MemoProfileListCard(
    profile: MemoProfileEntity,
    stats: StatsModule?,
    onEdit: () -> Unit,
    onReviser: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val colors = MaterialTheme.sankaiColors
    val accent = memoAccents[Math.floorMod(profile.id.toInt(), memoAccents.size)]
    var showDelete by remember(profile.id) { mutableStateOf(false) }
    val total = stats?.total ?: 0
    val due = stats?.dues ?: 0
    val mastery = if (total > 0) {
        (stats?.masteryPoints ?: 0).toFloat() /
            (total * (FlashcardEngine.NOMBRE_BOITES - 1)).coerceAtLeast(1)
    } else {
        0f
    }

    if (showDelete) {
        DeleteMemoDialog(
            name = profile.name.ifBlank { stringResource(R.string.memo_default_name) },
            onConfirm = {
                onDelete()
                showDelete = false
            },
            onDismiss = { showDelete = false }
        )
    }

    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        forme = RoundedCornerShape(SankaiRadius.Large),
        selectionne = profile.isActive,
        intensite = 0.94f
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(5.dp)
                    .heightIn(min = 338.dp)
                    .background(accent)
            )
            Column(Modifier.weight(1f).padding(SankaiSpacing.Lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(SankaiRadius.Medium))
                            .background(accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AutoStories, null, tint = accent)
                    }
                    Spacer(Modifier.width(SankaiSpacing.Md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile.name.ifBlank { stringResource(R.string.memo_default_name) },
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Language,
                                null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(SankaiSpacing.Xs))
                            Text(
                                profile.langue.ifBlank { stringResource(R.string.memo_language_unspecified) }
                                    .uppercase(Locale.getDefault()),
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    StatusBadge(active = profile.isActive)
                }

                Spacer(Modifier.height(SankaiSpacing.Lg))
                Row(horizontalArrangement = Arrangement.spacedBy(SankaiSpacing.Sm)) {
                    MemoMetric(
                        icon = Icons.Filled.AutoStories,
                        label = stringResource(R.string.memo_cards_label),
                        value = total.toString(),
                        color = accent,
                        modifier = Modifier.weight(1f)
                    )
                    MemoMetric(
                        icon = Icons.Filled.MoreTime,
                        label = stringResource(R.string.memo_last_review_label),
                        value = lastReviewLabel(stats),
                        color = AccentViolet,
                        modifier = Modifier.weight(1f)
                    )
                    MemoMetric(
                        icon = Icons.Filled.NotificationsActive,
                        label = stringResource(R.string.memo_next_notification_label),
                        value = nextNotificationLabel(profile),
                        color = AccentCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(SankaiSpacing.Lg))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.memo_mastery),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${(mastery.coerceIn(0f, 1f) * 100).toInt()}%",
                        color = accent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(SankaiSpacing.Xs))
                LinearProgressIndicator(
                    progress = { mastery.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(SankaiRadius.Pill)),
                    color = accent,
                    trackColor = colors.surface3
                )
                Spacer(Modifier.height(SankaiSpacing.Xs))
                Text(
                    when {
                        total == 0 -> stringResource(R.string.memo_no_cards)
                        due > 0 -> stringResource(R.string.memo_due_count, due)
                        else -> stringResource(R.string.memo_up_to_date)
                    },
                    color = if (due > 0) RewardGold else colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(SankaiSpacing.Md))
                SankaiButton(
                    text = when {
                        total == 0 -> stringResource(R.string.memo_add_cards)
                        due > 0 -> stringResource(R.string.memo_review_count, due)
                        else -> stringResource(R.string.memo_up_to_date)
                    },
                    onClick = if (total == 0) onEdit else onReviser,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = total == 0 || due > 0
                )

                Spacer(Modifier.height(SankaiSpacing.Md))
                HorizontalDivider(color = colors.border.copy(alpha = 0.7f))
                Spacer(Modifier.height(SankaiSpacing.Sm))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MemoActionIcon(
                        icon = Icons.Filled.Edit,
                        description = stringResource(R.string.memo_edit),
                        color = colors.textSecondary,
                        onClick = onEdit
                    )
                    MemoActionIcon(
                        icon = Icons.Filled.ContentCopy,
                        description = stringResource(R.string.memo_copy),
                        color = colors.textSecondary,
                        onClick = onCopy
                    )
                    MemoActionIcon(
                        icon = Icons.Filled.Share,
                        description = stringResource(R.string.memo_share_export),
                        color = colors.textSecondary,
                        onClick = onShare
                    )
                    MemoActionIcon(
                        icon = Icons.Filled.Delete,
                        description = stringResource(R.string.memo_delete),
                        color = DangerRed,
                        onClick = { showDelete = true }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(if (profile.isActive) R.string.memo_active else R.string.memo_inactive),
                            color = if (profile.isActive) SuccessGreen else colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Switch(checked = profile.isActive, onCheckedChange = { onToggle() })
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(active: Boolean) {
    val colors = MaterialTheme.sankaiColors
    Box(
        Modifier
            .clip(RoundedCornerShape(SankaiRadius.Pill))
            .background((if (active) SuccessGreen else colors.textDisabled).copy(alpha = 0.15f))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            stringResource(if (active) R.string.memo_active else R.string.memo_inactive),
            color = if (active) SuccessGreen else colors.textSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun MemoMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.sankaiColors
    Column(
        modifier
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(SankaiRadius.Medium))
            .background(color.copy(alpha = 0.09f))
            .padding(SankaiSpacing.Sm)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(SankaiSpacing.Xs))
        Text(
            label,
            color = colors.textSecondary,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MemoActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    Icon(
        icon,
        contentDescription = description,
        tint = color,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(13.dp)
    )
}

@Composable
private fun DeleteMemoDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.sankaiColors
    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            forme = RoundedCornerShape(SankaiRadius.Large),
            intensite = 0.98f
        ) {
            Column(Modifier.padding(SankaiSpacing.Xl)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Delete, null, tint = DangerRed)
                    Spacer(Modifier.width(SankaiSpacing.Sm))
                    Text(
                        stringResource(R.string.memo_delete_title),
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(SankaiSpacing.Md))
                Text(
                    stringResource(R.string.memo_delete_confirmation, name),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(SankaiSpacing.Lg))
                Row(horizontalArrangement = Arrangement.spacedBy(SankaiSpacing.Sm)) {
                    SankaiButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        secondary = true
                    )
                    SankaiButton(
                        text = stringResource(R.string.memo_delete),
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun lastReviewLabel(stats: StatsModule?): String {
    val timestamp = stats?.lastReviewedAtMillis ?: 0L
    if (timestamp <= 0L) return stringResource(R.string.memo_never)
    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}

@Composable
private fun nextNotificationLabel(profile: MemoProfileEntity): String {
    if (!profile.isActive) return stringResource(R.string.memo_paused)
    if (profile.nextTriggerAtMillis > System.currentTimeMillis()) {
        return DateUtils.getRelativeTimeSpanString(
            profile.nextTriggerAtMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }
    return String.format(
        Locale.getDefault(),
        "%02d:%02d",
        profile.scheduledHour,
        profile.scheduledMinute
    )
}

private fun copyMemoText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText(label.ifBlank { "Sankai Life" }, text))
}
