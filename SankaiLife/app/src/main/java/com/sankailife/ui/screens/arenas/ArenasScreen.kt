package com.sankailife.ui.screens.arenas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.domain.engine.ArenaEngine
import com.sankailife.core.domain.model.ALL_ARENAS
import com.sankailife.core.domain.model.Arena
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.components.ResourceBar
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.sankaiColors
import kotlinx.coroutines.flow.first
import kotlin.math.abs

private fun couleurDepuisHex(hex: String, defaut: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(defaut)

/**
 * Parcours de progression, du bas vers le haut.
 *
 * `reverseLayout = true` place l'index 0 en bas : la première arène est donc
 * au sol et l'on monte vers le sommet en faisant défiler. Descendre pour
 * progresser aurait inversé la métaphore d'ascension que porte le nom même
 * du projet.
 *
 * Les données restent dans leur ordre naturel (arène 1 en premier), ce qui
 * garde les index cohérents entre le moteur, les tests et l'affichage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArenasScreen(viewModel: ArenasViewModel, onBack: () -> Unit) {
    val user by viewModel.user.collectAsState()
    val parcours by viewModel.parcours.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val c = MaterialTheme.sankaiColors
    val haptics = LocalHaptics.current

    val etatListe = rememberLazyListState()
    val indexCourant = remember(user.level) { viewModel.indexArenCourante(user.level) }

    var detail by remember { mutableStateOf<ArenasViewModel.LigneArene?>(null) }

    // Recentrage sur l'arène actuelle : on attend que la liste soit mesurée,
    // sinon le décalage est calculé sur une hauteur nulle et n'a aucun effet.
    LaunchedEffect(indexCourant, parcours.size) {
        if (parcours.isEmpty()) return@LaunchedEffect
        runCatching {
            snapshotFlow { etatListe.layoutInfo.viewportSize.height }
                .first { it > 0 }
            val hauteur = etatListe.layoutInfo.viewportSize.height
            // Décalage négatif : l'arène remonte depuis le bord bas, ce qui
            // laisse voir les paliers déjà franchis en dessous.
            etatListe.animateScrollToItem(indexCourant, -hauteur / 3)
        }
    }

    val loinDeSaProgression by remember {
        derivedStateOf { abs(etatListe.firstVisibleItemIndex - indexCourant) > 1 }
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

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            ResourceBar(user.level, user.xp, user.xpNext, user.coins, user.gems)

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = c.textPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text("Parcours", color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Arène ${ArenaEngine.areneActuelle(user.level).nom}",
                        color = c.textSecondary, fontSize = 12.sp
                    )
                }
            }

            Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = etatListe,
                    reverseLayout = true,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(parcours, key = { _, l -> l.arene.id }) { index, ligne ->
                        CarteArene(
                            ligne = ligne,
                            niveauJoueur = user.level,
                            // « premier » et « dernier » sont exprimés dans le
                            // sens visuel : le bas de l'écran est l'index 0.
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

                GraduationNiveaux(
                    niveauJoueur = user.level,
                    modifier = Modifier.width(58.dp).fillMaxHeight()
                )
            }
        }

        // Repère de retour, visible seulement quand on s'est éloigné.
        AnimatedVisibility(
            visible = loinDeSaProgression,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(c.surface3)
                    .border(1.dp, c.accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .clickable {
                        haptics.click()
                        detail = null
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, null,
                    tint = c.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Revenir à ma progression", color = c.accent,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        AnimatedVisibility(
            visible = toast.isNotBlank(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
        ) {
            Box(
                Modifier.padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.accent.copy(alpha = 0.92f))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(toast, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CarteArene(
    ligne: ArenasViewModel.LigneArene,
    niveauJoueur: Int,
    enBas: Boolean,
    enHaut: Boolean,
    onClic: () -> Unit,
    onReclamer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val verrouillee = ligne.estVerrouillee

    // Une arène verrouillée perd sa couleur d'accent au profit d'un gris :
    // baisser seulement l'opacité rendrait le texte illisible sur fond sombre.
    val accent = if (verrouillee) c.textDisabled
                 else couleurDepuisHex(ligne.arene.accentHex, c.accent)

    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Row(verticalAlignment = Alignment.Top) {

        // Chemin vertical. Le segment vers le haut mène à l'arène suivante :
        // il reste éteint tant qu'elle n'est pas atteinte.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp).height(IntrinsicSize.Min)
        ) {
            SegmentChemin(
                hauteur = if (enHaut) 0.dp else 14.dp,
                actif = !verrouillee && ligne.etat == ArenasViewModel.EtatArene.TERMINEE
            )
            Box(
                Modifier.size(if (ligne.estCourante) 18.dp else 14.dp)
                    .clip(CircleShape)
                    .background(if (verrouillee) c.surface3 else accent)
                    .then(
                        if (ligne.estCourante)
                            Modifier.border(2.dp, accent.copy(alpha = 0.4f), CircleShape)
                        else Modifier
                    )
            )
            if (!enBas) {
                SegmentChemin(hauteur = null, actif = !verrouillee)
            }
        }

        Spacer(Modifier.width(8.dp))

        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when {
                        ligne.estCourante -> accent.copy(alpha = 0.12f)
                        verrouillee -> c.surface1
                        else -> c.surface2
                    }
                )
                .border(
                    width = if (ligne.estCourante) 2.dp else 1.dp,
                    color = when {
                        ligne.estCourante -> accent
                        verrouillee -> c.border
                        else -> accent.copy(alpha = 0.3f)
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { onClic() }
                .padding(14.dp)
        ) {
            Column {
                if (ligne.estCourante) {
                    Text(
                        "VOUS ÊTES ICI", color = accent, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(6.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ligne.arene.emoji, fontSize = 26.sp,
                        // L'emoji d'une arène verrouillée reste visible mais
                        // atténué : la masquer supprimerait l'envie d'y aller.
                        modifier = if (verrouillee) Modifier.alpha(0.45f) else Modifier
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            ligne.arene.nom,
                            color = if (verrouillee) c.textSecondary else c.textPrimary,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold
                        )
                        Text("Niveau ${ligne.arene.niveauRequis}",
                            color = c.textSecondary, fontSize = 11.sp)
                    }
                    when {
                        verrouillee -> Icon(Icons.Filled.Lock, "Verrouillée",
                            tint = c.textDisabled, modifier = Modifier.size(18.dp))
                        ligne.recompenseReclamee -> Icon(Icons.Filled.Check, "Terminée",
                            tint = accent, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    if (verrouillee)
                        "Encore ${(ligne.arene.niveauRequis - niveauJoueur).coerceAtLeast(0)} niveaux"
                    else ligne.arene.description,
                    color = c.textSecondary, fontSize = 12.sp
                )

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (verrouillee) {
                        Icon(Icons.Filled.Lock, null,
                            tint = c.textDisabled, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        ligne.arene.recompense.resume(),
                        color = if (verrouillee) c.textDisabled else accent,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium
                    )
                }

                if (ligne.recompenseDisponible) {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.alpha(pulse)) {
                        SankaiButton("Réclamer", onClick = onReclamer,
                            small = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Segment du chemin. [hauteur] null signifie « occupe l'espace restant ». */
@Composable
private fun ColumnScope.SegmentChemin(hauteur: androidx.compose.ui.unit.Dp?, actif: Boolean) {
    val c = MaterialTheme.sankaiColors
    val couleur = if (actif) c.accent.copy(alpha = 0.55f) else c.border
    if (hauteur != null) {
        Box(Modifier.width(2.dp).height(hauteur).background(couleur))
    } else {
        Box(Modifier.width(2.dp).weight(1f).background(couleur))
    }
}

/**
 * Graduation des niveaux : le plus bas en bas, le sommet en haut, comme le
 * parcours qu'elle accompagne.
 */
@Composable
private fun GraduationNiveaux(niveauJoueur: Int, modifier: Modifier = Modifier) {
    val c = MaterialTheme.sankaiColors
    val maximum = ArenaEngine.niveauMaximum
    val ratio = (niveauJoueur.toFloat() / maximum).coerceIn(0f, 1f)

    Box(modifier.padding(vertical = 24.dp, horizontal = 6.dp)) {
        // Repères d'arènes, positionnés proportionnellement à leur niveau.
        Column(
            Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            ALL_ARENAS.reversed().forEach { arene ->
                Text(
                    "${arene.niveauRequis}",
                    color = if (niveauJoueur >= arene.niveauRequis) c.accent else c.textDisabled,
                    fontSize = 9.sp,
                    fontWeight = if (niveauJoueur >= arene.niveauRequis)
                        FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        // Barre de progression, remplie depuis le bas.
        Box(
            Modifier.align(Alignment.CenterStart)
                .width(5.dp).fillMaxHeight()
                .clip(RoundedCornerShape(3.dp)).background(c.surface3),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(ratio)
                    .clip(RoundedCornerShape(3.dp)).background(c.accent)
            )
        }
    }
}

/**
 * Carte de résumé, utilisée par l'Accueil et le Profil.
 * Donne l'arène actuelle et la prochaine récompense sans ouvrir le parcours.
 */
@Composable
fun CarteResumeArene(
    niveau: Int,
    nombreAReclamer: Int,
    onVoirParcours: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val actuelle: Arena = ArenaEngine.areneActuelle(niveau)
    val suivante = ArenaEngine.areneSuivante(niveau)
    val accent = couleurDepuisHex(actuelle.accentHex, c.accent)
    val progression = ArenaEngine.progressionVersSuivante(niveau)

    // Toute la carte est cliquable, pas seulement le bouton : c'est la cible
    // principale de l'accueil, elle doit être facile à atteindre au pouce.
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable { onVoirParcours() }
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(actuelle.emoji, fontSize = 32.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("ARÈNE ACTUELLE", color = c.textSecondary, fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Text(actuelle.nom, color = c.textPrimary,
                        fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Niveau $niveau", color = c.textSecondary, fontSize = 12.sp)
                }
                if (nombreAReclamer > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text("$nombreAReclamer", fontSize = 10.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progression },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = c.surface3
            )
            Spacer(Modifier.height(8.dp))

            if (suivante != null) {
                Text(
                    "Prochaine : ${suivante.emoji} ${suivante.nom} " +
                    "— encore ${ArenaEngine.niveauxRestants(niveau)} niveaux",
                    color = c.textSecondary, fontSize = 12.sp
                )
                Text("Récompense : ${suivante.recompense.resume()}",
                    color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            } else {
                Text("Sommet atteint. Le parcours n'a plus de plafond.",
                    color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                if (nombreAReclamer > 0) "Voir le parcours • $nombreAReclamer à réclamer"
                else "Voir le parcours",
                color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Détail d'une arène, ouvert au clic. */
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

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ligne.arene.emoji, fontSize = 40.sp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(ligne.arene.nom, color = c.textPrimary,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when (ligne.etat) {
                            ArenasViewModel.EtatArene.ACTUELLE -> "Arène actuelle"
                            ArenasViewModel.EtatArene.TERMINEE -> "Terminée"
                            ArenasViewModel.EtatArene.DEBLOQUEE -> "Débloquée"
                            ArenasViewModel.EtatArene.VERROUILLEE -> "Verrouillée"
                        },
                        color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(ligne.arene.description, color = c.textSecondary, fontSize = 14.sp)

            Spacer(Modifier.height(16.dp))
            Text("Niveau requis : ${ligne.arene.niveauRequis}",
                color = c.textPrimary, fontSize = 13.sp)
            if (verrouillee) {
                Text(
                    "Niveau actuel : $niveauJoueur — encore " +
                    "${(ligne.arene.niveauRequis - niveauJoueur).coerceAtLeast(0)} niveaux",
                    color = c.textSecondary, fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("Récompenses", color = c.textSecondary, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (verrouillee) {
                    Icon(Icons.Filled.Lock, null,
                        tint = c.textDisabled, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    ligne.arene.recompense.resume(),
                    color = if (verrouillee) c.textDisabled else accent,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(20.dp))
            when {
                ligne.recompenseDisponible ->
                    SankaiButton("Réclamer la récompense", onClick = onReclamer,
                        modifier = Modifier.fillMaxWidth())
                ligne.recompenseReclamee ->
                    Text("Récompense déjà récupérée", color = c.textSecondary, fontSize = 13.sp)
                // Aucun bouton pour une arène verrouillée : proposer une action
                // impossible est plus frustrant que de ne rien proposer.
                else ->
                    Text("Atteins le niveau ${ligne.arene.niveauRequis} pour débloquer cette arène.",
                        color = c.textSecondary, fontSize = 13.sp)
            }
        }
    }
}
