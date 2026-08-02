package com.sankailife.ui.screens.arenas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankailife.R
import com.sankailife.core.domain.engine.ArenaEngine
import com.sankailife.core.domain.model.Arena
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.components.LiquidGlassSurface
import com.sankailife.ui.components.ResourceBar
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun couleurDepuisHex(hex: String, defaut: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(defaut)

/**
 * Parcours vertical, du point de départ en bas au sommet en haut.
 *
 * Les données restent dans l'ordre du domaine et `reverseLayout` porte la
 * métaphore d'ascension sans réordonner les identifiants ni les index.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArenasScreen(viewModel: ArenasViewModel, onBack: () -> Unit) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val parcours by viewModel.parcours.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val c = MaterialTheme.sankaiColors
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val indexCourant = remember(user.level) { viewModel.indexArenCourante(user.level) }
    val areneActuelle = ArenaEngine.areneActuelle(user.level)
    val nomActuel = areneActuelle.localizedName()
    val toastText = when (val message = toast) {
        is ArenasViewModel.Message.RecompenseRecuperee -> {
            val nom = message.arene.localizedName()
            val recompense = message.arene.recompense.localizedSummary()
            stringResource(
                R.string.arena_reward_claimed_toast,
                message.arene.emoji,
                nom,
                recompense
            )
        }
        ArenasViewModel.Message.CoffresPleins ->
            stringResource(R.string.arena_chest_slots_full)
        null -> ""
    }
    val background = c.background

    var detail by remember { mutableStateOf<ArenasViewModel.LigneArene?>(null) }

    LaunchedEffect(indexCourant, parcours.size) {
        if (parcours.isEmpty()) return@LaunchedEffect
        runCatching {
            snapshotFlow { listState.layoutInfo.viewportSize.height }
                .first { it > 0 }
            val viewport = listState.layoutInfo.viewportSize.height
            listState.animateScrollToItem(indexCourant, -viewport / 3)
        }
    }

    val loinDeSaProgression by remember(listState, indexCourant) {
        derivedStateOf { abs(listState.firstVisibleItemIndex - indexCourant) > 1 }
    }

    detail?.let { ligne ->
        FeuilleDetailArene(
            ligne = ligne,
            niveauJoueur = user.level,
            onReclamer = {
                viewModel.reclamer(ligne.arene)
                detail = null
            },
            onFermer = { detail = null }
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(background)) {
        val compact = maxHeight < 700.dp || maxWidth < 360.dp ||
            LocalDensity.current.fontScale >= 1.35f

        Column(Modifier.fillMaxSize()) {
            ResourceBar(user.level, user.xp, user.xpNext, user.coins, user.gems)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .widthIn(max = 720.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                ArenaHeader(
                    currentArenaName = nomActuel,
                    compact = compact,
                    onBack = onBack
                )

                if (!compact) {
                    ArenaProgressSummary(
                        niveau = user.level,
                        xp = user.xp,
                        xpNext = user.xpNext,
                        nombreAReclamer = parcours.count { it.recompenseDisponible },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SankaiSpacing.Lg)
                    )
                    Spacer(Modifier.height(SankaiSpacing.Sm))
                }

                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(
                        start = SankaiSpacing.Md,
                        top = SankaiSpacing.Xl,
                        end = SankaiSpacing.Lg,
                        bottom = SankaiSpacing.Xl
                    ),
                    verticalArrangement = Arrangement.spacedBy(SankaiSpacing.Sm)
                ) {
                    itemsIndexed(parcours, key = { _, ligne -> ligne.arene.id }) { index, ligne ->
                        ArenaPathItem(
                            ligne = ligne,
                            niveauJoueur = user.level,
                            compact = compact,
                            enBas = index == 0,
                            enHaut = index == parcours.lastIndex,
                            onClic = {
                                haptics.click()
                                detail = ligne
                            },
                            onReclamer = { viewModel.reclamer(ligne.arene) }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = loinDeSaProgression,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = SankaiSpacing.Lg)
        ) {
            LiquidGlassSurface(
                modifier = Modifier
                    .clip(RoundedCornerShape(SankaiRadius.Pill))
                    .clickable(role = Role.Button) {
                        haptics.click()
                        detail = null
                        scope.launch {
                            val viewport = listState.layoutInfo.viewportSize.height
                            listState.animateScrollToItem(
                                indexCourant,
                                if (viewport > 0) -viewport / 3 else 0
                            )
                        }
                    },
                forme = RoundedCornerShape(SankaiRadius.Pill)
            ) {
                Row(
                    Modifier.padding(horizontal = SankaiSpacing.Lg, vertical = SankaiSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(SankaiSpacing.Xs))
                    Text(
                        stringResource(R.string.arena_return_to_progress),
                        color = c.accent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = toastText.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = SankaiSpacing.Xl,
                    end = SankaiSpacing.Xl,
                    bottom = if (loinDeSaProgression) 78.dp else SankaiSpacing.Xl
                )
        ) {
            Text(
                toastText,
                color = Color.Black,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(SankaiRadius.Medium))
                    .background(c.accent.copy(alpha = 0.94f))
                    .padding(horizontal = SankaiSpacing.Lg, vertical = SankaiSpacing.Md)
            )
        }
    }
}

@Composable
private fun ArenaHeader(
    currentArenaName: String,
    compact: Boolean,
    onBack: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SankaiSpacing.Sm, vertical = if (compact) SankaiSpacing.Xs else SankaiSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = c.textPrimary
            )
        }
        Spacer(Modifier.width(SankaiSpacing.Xs))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.arena_path_title),
                color = c.textPrimary,
                style = if (compact) MaterialTheme.typography.titleLarge
                        else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Text(
                stringResource(R.string.arena_path_subtitle, currentArenaName),
                color = c.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ArenaProgressSummary(
    niveau: Int,
    xp: Int,
    xpNext: Int,
    nombreAReclamer: Int,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.sankaiColors
    val prochaine = ArenaEngine.areneSuivante(niveau)
    val prochainNom = prochaine?.localizedName()

    LiquidGlassSurface(
        modifier = modifier,
        forme = RoundedCornerShape(SankaiRadius.Medium)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(SankaiSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryMetric(
                value = stringResource(R.string.arena_level, niveau),
                label = stringResource(R.string.arena_xp_progress, xp, xpNext),
                modifier = Modifier.weight(1f)
            )
            Box(Modifier.width(1.dp).height(34.dp).background(c.border))
            SummaryMetric(
                value = if (prochaine != null && prochainNom != null) {
                    pluralStringResource(
                        R.plurals.arena_levels_remaining,
                        ArenaEngine.niveauxRestants(niveau),
                        ArenaEngine.niveauxRestants(niveau)
                    )
                } else {
                    stringResource(R.string.arena_path_complete)
                },
                label = prochainNom ?: stringResource(R.string.arena_summit_label),
                modifier = Modifier.weight(1f)
            )
            if (nombreAReclamer > 0) {
                Box(Modifier.width(1.dp).height(34.dp).background(c.border))
                SummaryMetric(
                    value = "$nombreAReclamer",
                    label = pluralStringResource(
                        R.plurals.arena_rewards_claimable,
                        nombreAReclamer,
                        nombreAReclamer
                    ),
                    accent = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    val c = MaterialTheme.sankaiColors
    Column(
        modifier = modifier.padding(horizontal = SankaiSpacing.Sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            color = if (accent) c.accent else c.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            label,
            color = c.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArenaPathItem(
    ligne: ArenasViewModel.LigneArene,
    niveauJoueur: Int,
    compact: Boolean,
    enBas: Boolean,
    enHaut: Boolean,
    onClic: () -> Unit,
    onReclamer: () -> Unit
) {
    Box(Modifier.fillMaxWidth()) {
        ArenaPathRail(
            ligne = ligne,
            enBas = enBas,
            enHaut = enHaut,
            modifier = Modifier.matchParentSize()
        )
        ArenaPathCard(
            ligne = ligne,
            niveauJoueur = niveauJoueur,
            compact = compact,
            onClic = onClic,
            onReclamer = onReclamer,
            modifier = Modifier.fillMaxWidth().padding(start = 44.dp)
        )
    }
}

@Composable
private fun ArenaPathRail(
    ligne: ArenasViewModel.LigneArene,
    enBas: Boolean,
    enHaut: Boolean,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.sankaiColors
    val accent = couleurDepuisHex(ligne.arene.accentHex, c.accent)
    val activeBelow = !ligne.estVerrouillee
    val activeAbove = ligne.etat == ArenasViewModel.EtatArene.TERMINEE ||
        ligne.etat == ArenasViewModel.EtatArene.DEBLOQUEE

    Box(modifier) {
        Column(
            modifier = Modifier.align(Alignment.CenterStart).width(36.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (!enHaut) {
                    Box(
                        Modifier.width(3.dp).fillMaxHeight().background(
                            if (activeAbove) accent.copy(alpha = 0.62f) else c.border
                        )
                    )
                }
            }
            ArenaPathNode(ligne = ligne, accent = accent)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (!enBas) {
                    Box(
                        Modifier.width(3.dp).fillMaxHeight().background(
                            if (activeBelow) accent.copy(alpha = 0.62f) else c.border
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ArenaPathNode(
    ligne: ArenasViewModel.LigneArene,
    accent: Color
) {
    val c = MaterialTheme.sankaiColors
    val size = if (ligne.estCourante) 28.dp else 24.dp
    val background = when {
        ligne.estVerrouillee -> c.surface3
        ligne.recompenseReclamee -> accent
        else -> c.surface2
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(
                width = if (ligne.estCourante) 3.dp else 2.dp,
                color = if (ligne.estVerrouillee) c.border else accent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            ligne.estVerrouillee -> Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = c.textDisabled,
                modifier = Modifier.size(12.dp)
            )
            ligne.recompenseReclamee -> Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(14.dp)
            )
            ligne.estCourante -> Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
            else -> Text("!", color = accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ArenaPathCard(
    ligne: ArenasViewModel.LigneArene,
    niveauJoueur: Int,
    compact: Boolean,
    onClic: () -> Unit,
    onReclamer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.sankaiColors
    val verrouillee = ligne.estVerrouillee
    val accent = if (verrouillee) c.textDisabled
        else couleurDepuisHex(ligne.arene.accentHex, c.accent)
    val nom = ligne.arene.localizedName()
    val description = ligne.arene.localizedDescription()
    val reward = ligne.arene.recompense.localizedSummary()
    val status = when (ligne.etat) {
        ArenasViewModel.EtatArene.ACTUELLE -> stringResource(R.string.arena_status_current)
        ArenasViewModel.EtatArene.TERMINEE -> stringResource(R.string.arena_status_completed)
        ArenasViewModel.EtatArene.DEBLOQUEE -> stringResource(R.string.arena_status_unlocked)
        ArenasViewModel.EtatArene.VERROUILLEE -> stringResource(R.string.arena_status_locked)
    }
    val shape = RoundedCornerShape(SankaiRadius.Large)

    LiquidGlassSurface(
        modifier = modifier
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClic),
        forme = shape,
        selectionne = ligne.estCourante
    ) {
        if (ligne.estCourante) {
            Box(
                Modifier.matchParentSize().background(accent.copy(alpha = 0.08f))
            )
            Box(Modifier.matchParentSize().border(1.dp, accent.copy(alpha = 0.55f), shape))
        }

        Column(
            Modifier.fillMaxWidth().padding(if (compact) SankaiSpacing.Md else SankaiSpacing.Lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(if (ligne.estCourante && !compact) 58.dp else 48.dp)
                        .clip(RoundedCornerShape(SankaiRadius.Medium))
                        .background(accent.copy(alpha = if (verrouillee) 0.08f else 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        ligne.arene.emoji,
                        fontSize = if (ligne.estCourante && !compact) 30.sp else 25.sp,
                        modifier = if (verrouillee) Modifier.alpha(0.42f) else Modifier
                    )
                }
                Spacer(Modifier.width(SankaiSpacing.Md))
                Column(Modifier.weight(1f)) {
                    Text(
                        status,
                        color = accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        nom,
                        color = if (verrouillee) c.textSecondary else c.textPrimary,
                        style = if (ligne.estCourante && !compact) MaterialTheme.typography.titleLarge
                                else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.arena_required_level, ligne.arene.niveauRequis),
                        color = c.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                when {
                    verrouillee -> Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = c.textDisabled,
                        modifier = Modifier.size(20.dp)
                    )
                    ligne.recompenseReclamee -> Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(SankaiSpacing.Md))
            Text(
                if (verrouillee) {
                    pluralStringResource(
                        R.plurals.arena_levels_remaining,
                        (ligne.arene.niveauRequis - niveauJoueur).coerceAtLeast(0),
                        (ligne.arene.niveauRequis - niveauJoueur).coerceAtLeast(0)
                    )
                } else {
                    description
                },
                color = c.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis
            )

            if (ligne.estCourante) {
                Spacer(Modifier.height(SankaiSpacing.Md))
                CurrentArenaProgress(niveau = niveauJoueur, accent = accent)
            }

            Spacer(Modifier.height(SankaiSpacing.Md))
            RewardPreview(
                reward = reward,
                accent = accent,
                locked = verrouillee
            )

            if (ligne.recompenseDisponible) {
                Spacer(Modifier.height(SankaiSpacing.Md))
                SankaiButton(
                    text = stringResource(R.string.arena_claim),
                    onClick = onReclamer,
                    small = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CurrentArenaProgress(niveau: Int, accent: Color) {
    val c = MaterialTheme.sankaiColors
    val suivante = ArenaEngine.areneSuivante(niveau)
    val suivanteNom = suivante?.localizedName()
    val progression = ArenaEngine.progressionVersSuivante(niveau).coerceIn(0f, 1f)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            stringResource(R.string.arena_level, niveau),
            color = c.textSecondary,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            if (suivante != null && suivanteNom != null) {
                stringResource(R.string.arena_next_goal, suivante.niveauRequis, suivanteNom)
            } else {
                stringResource(R.string.arena_path_complete)
            },
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    Spacer(Modifier.height(SankaiSpacing.Xs))
    LinearProgressIndicator(
        progress = { progression },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(SankaiRadius.Pill)),
        color = accent,
        trackColor = c.surface3
    )
}

@Composable
private fun RewardPreview(
    reward: String,
    accent: Color,
    locked: Boolean
) {
    val c = MaterialTheme.sankaiColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SankaiRadius.Medium))
            .background(accent.copy(alpha = if (locked) 0.05f else 0.09f))
            .padding(SankaiSpacing.Md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (locked) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = c.textDisabled,
                modifier = Modifier.size(15.dp)
            )
        } else {
            Text("✦", color = accent, fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(SankaiSpacing.Sm))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.arena_reward_title),
                color = if (locked) c.textDisabled else c.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                reward,
                color = if (locked) c.textDisabled else accent,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Carte compacte utilisée par le profil. */
@Composable
fun CarteResumeArene(
    niveau: Int,
    nombreAReclamer: Int,
    onVoirParcours: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val actuelle: Arena = ArenaEngine.areneActuelle(niveau)
    val suivante = ArenaEngine.areneSuivante(niveau)
    val nomActuel = actuelle.localizedName()
    val nomSuivant = suivante?.localizedName()
    val accent = couleurDepuisHex(actuelle.accentHex, c.accent)
    val progression = ArenaEngine.progressionVersSuivante(niveau).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(SankaiRadius.Large)

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(role = Role.Button, onClick = onVoirParcours),
        forme = shape,
        selectionne = true
    ) {
        Box(
            Modifier.matchParentSize().background(accent.copy(alpha = 0.06f))
        )
        Column(Modifier.fillMaxWidth().padding(SankaiSpacing.Lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(actuelle.emoji, fontSize = 32.sp)
                Spacer(Modifier.width(SankaiSpacing.Md))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.arena_current_label),
                        color = accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        nomActuel,
                        color = c.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (nombreAReclamer > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text("$nombreAReclamer", color = MaterialTheme.colorScheme.onError)
                    }
                }
            }

            Spacer(Modifier.height(SankaiSpacing.Md))
            LinearProgressIndicator(
                progress = { progression },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(SankaiRadius.Pill)),
                color = accent,
                trackColor = c.surface3
            )
            Spacer(Modifier.height(SankaiSpacing.Sm))

            if (suivante != null && nomSuivant != null) {
                Text(
                    stringResource(R.string.arena_next_summary, suivante.emoji, nomSuivant),
                    color = c.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    suivante.recompense.localizedSummary(),
                    color = accent,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    stringResource(R.string.arena_path_complete),
                    color = accent,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(SankaiSpacing.Md))
            Text(
                if (nombreAReclamer > 0) {
                    pluralStringResource(
                        R.plurals.arena_view_path_claimable,
                        nombreAReclamer,
                        nombreAReclamer
                    )
                } else {
                    stringResource(R.string.arena_view_path)
                },
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleDetailArene(
    ligne: ArenasViewModel.LigneArene,
    niveauJoueur: Int,
    onReclamer: () -> Unit,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val verrouillee = ligne.estVerrouillee
    val accent = if (verrouillee) c.textDisabled
        else couleurDepuisHex(ligne.arene.accentHex, c.accent)
    val nom = ligne.arene.localizedName()
    val description = ligne.arene.localizedDescription()
    val reward = ligne.arene.recompense.localizedSummary()
    val status = when (ligne.etat) {
        ArenasViewModel.EtatArene.ACTUELLE -> stringResource(R.string.arena_status_current)
        ArenasViewModel.EtatArene.TERMINEE -> stringResource(R.string.arena_status_completed)
        ArenasViewModel.EtatArene.DEBLOQUEE -> stringResource(R.string.arena_status_unlocked)
        ArenasViewModel.EtatArene.VERROUILLEE -> stringResource(R.string.arena_status_locked)
    }

    ModalBottomSheet(
        onDismissRequest = onFermer,
        containerColor = c.surface1,
        contentColor = c.textPrimary
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = SankaiSpacing.Xl).padding(bottom = SankaiSpacing.Xxl)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(SankaiRadius.Medium))
                        .background(accent.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        ligne.arene.emoji,
                        fontSize = 34.sp,
                        modifier = if (verrouillee) Modifier.alpha(0.42f) else Modifier
                    )
                }
                Spacer(Modifier.width(SankaiSpacing.Lg))
                Column(Modifier.weight(1f)) {
                    Text(
                        nom,
                        color = c.textPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        status,
                        color = accent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(SankaiSpacing.Lg))
            Text(description, color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(SankaiSpacing.Lg))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.arena_required_level, ligne.arene.niveauRequis),
                    color = c.textPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.arena_your_level, niveauJoueur),
                    color = c.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(SankaiSpacing.Lg))
            RewardPreview(reward = reward, accent = accent, locked = verrouillee)

            Spacer(Modifier.height(SankaiSpacing.Xl))
            when {
                ligne.recompenseDisponible -> SankaiButton(
                    text = stringResource(R.string.arena_claim_reward),
                    onClick = onReclamer,
                    modifier = Modifier.fillMaxWidth()
                )
                ligne.recompenseReclamee -> Text(
                    stringResource(R.string.arena_reward_collected),
                    color = c.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                verrouillee -> Text(
                    stringResource(R.string.arena_unlock_hint, ligne.arene.niveauRequis),
                    color = c.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
