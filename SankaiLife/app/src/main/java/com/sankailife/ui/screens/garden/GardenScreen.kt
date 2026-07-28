package com.sankailife.ui.screens.garden

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.domain.engine.ArenaEngine
import com.sankailife.core.garden.domain.MemoChallengeEngine
import com.sankailife.core.garden.domain.PlotState
import com.sankailife.core.garden.domain.Seed
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.*

/**
 * Le jardin.
 *
 * Écran non défilant : tout tient dans la hauteur, la grille absorbant
 * l'espace disponible via `weight`. Aucune dimension n'est figée en dur, pour
 * que la même mise en page tienne d'un petit téléphone à une tablette.
 *
 * Rendu volontairement géométrique — formes, dégradés et emojis. Les stades,
 * sols et espèces sont des catalogues séparés de l'affichage : substituer de
 * vraies illustrations ne demandera pas de retoucher la logique.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GardenScreen(viewModel: GardenViewModel, onBack: () -> Unit) {
    val c = MaterialTheme.sankaiColors
    val haptics = LocalHaptics.current

    val chargement by viewModel.chargement.collectAsState()
    val parcelles by viewModel.parcelles.collectAsState()
    val etat by viewModel.etat.collectAsState()
    val user by viewModel.user.collectAsState()
    val message by viewModel.message.collectAsState()
    val pretes by viewModel.nombrePretes.collectAsState()

    var selection by remember { mutableStateOf<GardenViewModel.ParcelleUi?>(null) }
    val defi by viewModel.defi.collectAsState()

    defi?.let { d ->
        FeuilleDefiSouvenir(
            defi = d,
            onRepondre = { viewModel.repondreDefi(it) },
            onFermer = { viewModel.ignorerDefi() }
        )
    }

    selection?.let { parcelle ->
        FeuilleParcelle(
            parcelle = parcelle,
            graines = viewModel.grainesDisponibles(user.level),
            pieces = user.coins,
            eau = etat.eau,
            onNettoyer = { viewModel.nettoyer(parcelle.id); selection = null },
            onPlanter = { g -> viewModel.planter(parcelle.id, g); selection = null },
            onArroser = { viewModel.arroser(parcelle.id); selection = null },
            onRecolter = { haptics.reward(); viewModel.recolter(parcelle.id); selection = null },
            onFermer = { selection = null }
        )
    }

    if (chargement) {
        Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🌿", fontSize = 40.sp)
                Spacer(Modifier.height(12.dp))
                Text("Préparation du jardin…", color = c.textSecondary, fontSize = 13.sp)
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {

            // Bandeau : retour et ressources du jardin.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quitter le jardin", tint = c.textPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        ArenaEngine.areneActuelle(user.level).nom,
                        color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold
                    )
                    Text("Jardin central", color = c.textSecondary, fontSize = 11.sp)
                }
                Ressource("💧", "${etat.eau}", AccentCyan)
                Spacer(Modifier.width(6.dp))
                Ressource("🪙", "${user.coins}", CoinColor)
                Spacer(Modifier.width(6.dp))
                Ressource("🌱", "${etat.compost}", SuccessGreen)
            }

            // La grille prend tout l'espace restant : c'est ce qui rend
            // l'écran non défilant sans hauteur codée en dur.
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Column(
                    Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF16301F), Color(0xFF0E2116))
                            )
                        )
                        .border(1.dp, Color(0xFF2E5238), RoundedCornerShape(22.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Arbre Sankai : symbole du niveau global, non cultivable.
                    ArbreSankai(niveau = user.level)

                    val lignes = parcelles.chunked(3)
                    lignes.forEach { ligne ->
                        Row(
                            Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ligne.forEach { parcelle ->
                                Parcelle(
                                    parcelle = parcelle,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                ) {
                                    haptics.click()
                                    selection = parcelle
                                }
                            }
                        }
                    }
                }
            }

            // Carte contextuelle unique, comme sur l'accueil.
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                if (pretes > 0) {
                    SankaiButton(
                        "🧺  Tout récolter ($pretes)",
                        onClick = { haptics.reward(); viewModel.toutRecolter() },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(c.surface2)
                            .border(1.dp, c.border, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            if (etat.eau <= 0)
                                "Plus d'eau. Révise des flash cards pour en obtenir."
                            else "Touche une parcelle pour planter, arroser ou récolter.",
                            color = c.textSecondary, fontSize = 12.sp
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = message.isNotBlank(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp)
        ) {
            Box(
                Modifier.padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surface3)
                    .border(1.dp, c.accent, RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 11.dp)
            ) {
                Text(message, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Défi souvenir : reconnaître la phrase reçue en notification.
 *
 * Présenté à l'ouverture du jardin, une seule fois par notification. Il dure
 * une dizaine de secondes — c'est une micro-révision, pas un examen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleDefiSouvenir(
    defi: MemoChallengeEngine.Defi,
    onRepondre: (String) -> Unit,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text("SOUVENIR DU JOUR", color = AccentCyan, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Quelle phrase as-tu reçue ?", color = c.textPrimary,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Module : ${defi.nomModule}", color = c.textSecondary, fontSize = 12.sp)

            Spacer(Modifier.height(16.dp))
            defi.options.forEach { option ->
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface2)
                        .border(1.dp, c.border, RoundedCornerShape(12.dp))
                        .clickable { onRepondre(option) }
                        .padding(14.dp)
                ) {
                    Text(option, color = c.textPrimary, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Se tromper ne retire rien. Le défi reviendra à la prochaine notification.",
                color = c.textDisabled, fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun Ressource(emoji: String, valeur: String, couleur: Color) {
    val c = MaterialTheme.sankaiColors
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(c.surface2)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 11.sp)
        Spacer(Modifier.width(3.dp))
        Text(valeur, color = couleur, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Arbre central : son apparence suit l'arène atteinte. */
@Composable
private fun ArbreSankai(niveau: Int) {
    val c = MaterialTheme.sankaiColors
    val arene = ArenaEngine.areneActuelle(niveau)
    val taille = (26 + arene.id * 3).sp

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B1A12))
            .border(1.dp, Color(0xFF2B4A34), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(arene.emoji, fontSize = taille)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Arbre Sankai", color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Stade ${arene.id} • niveau $niveau", color = c.textSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun Parcelle(
    parcelle: GardenViewModel.ParcelleUi,
    modifier: Modifier = Modifier,
    onClic: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    // Seule une parcelle prête est animée : si tout bougeait, plus rien
    // n'attirerait le regard.
    val transition = rememberInfiniteTransition(label = "parcelle")
    val pulse by transition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulseParcelle"
    )

    val (fond, bordure) = when (parcelle.etat) {
        PlotState.LOCKED -> Color(0xFF1A1D1B) to c.border
        PlotState.UNCLEARED -> Color(0xFF2A2622) to Color(0xFF4A413A)
        PlotState.READY_TO_HARVEST -> Color(0xFF3B2F16) to AccentGold.copy(alpha = pulse)
        else -> Color(0xFF3A2A1C) to Color(0xFF6B4B30)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(fond)
            .border(if (parcelle.prete) 2.dp else 1.dp, bordure, RoundedCornerShape(14.dp))
            .clickable { onClic() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (parcelle.etat) {
                    PlotState.LOCKED -> "🔒"
                    PlotState.UNCLEARED -> "🪨"
                    PlotState.EMPTY, PlotState.PREPARED -> "＋"
                    else -> parcelle.stage?.emoji ?: "🌱"
                },
                fontSize = 24.sp,
                color = if (parcelle.etat == PlotState.EMPTY) c.textSecondary else Color.Unspecified
            )

            val libelle = when {
                parcelle.etat == PlotState.LOCKED -> "Arène ${parcelle.areneRequise}"
                parcelle.etat == PlotState.UNCLEARED -> "Nettoyer"
                parcelle.etat == PlotState.EMPTY -> "Planter"
                parcelle.prete -> "Prêt"
                parcelle.besoinEau -> "Assoiffée"
                else -> formaterDuree(parcelle.minutesRestantes)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                libelle,
                color = when {
                    parcelle.prete -> AccentGold
                    parcelle.besoinEau -> AccentCyan
                    else -> c.textSecondary
                },
                fontSize = 9.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formaterDuree(minutes: Long): String = when {
    minutes <= 0 -> "Prêt"
    minutes < 60 -> "${minutes} min"
    else -> "${minutes / 60} h ${minutes % 60}"
}

/** Actions possibles sur une parcelle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleParcelle(
    parcelle: GardenViewModel.ParcelleUi,
    graines: List<Seed>,
    pieces: Int,
    eau: Int,
    onNettoyer: () -> Unit,
    onPlanter: (Seed) -> Unit,
    onArroser: () -> Unit,
    onRecolter: () -> Unit,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {

            when (parcelle.etat) {
                PlotState.LOCKED -> {
                    Text("Parcelle verrouillée", color = c.textPrimary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Atteins l'arène ${parcelle.areneRequise} pour l'ouvrir.",
                        color = c.textSecondary, fontSize = 13.sp
                    )
                }

                PlotState.UNCLEARED -> {
                    Text("Terrain encombré", color = c.textPrimary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Des pierres bloquent cette parcelle. " +
                        "Nettoyage : ${com.sankailife.core.garden.data.GardenRepository.COUT_NETTOYAGE} 🪙",
                        color = c.textSecondary, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    SankaiButton(
                        "Nettoyer",
                        onClick = onNettoyer,
                        enabled = pieces >= com.sankailife.core.garden.data.GardenRepository.COUT_NETTOYAGE,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                PlotState.EMPTY, PlotState.PREPARED -> {
                    Text("Planter une graine", color = c.textPrimary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Sol : ${parcelle.sol.emoji} ${parcelle.sol.libelle}",
                        color = c.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))

                    val compatibles = graines.filter { it.solRequis == parcelle.sol }
                    if (compatibles.isEmpty()) {
                        Text(
                            "Aucune graine compatible avec ce sol pour l'instant.",
                            color = c.textSecondary, fontSize = 13.sp
                        )
                    } else {
                        compatibles.forEach { graine ->
                            val abordable = pieces >= graine.prixPieces
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.surface2)
                                    .border(1.dp, c.border, RoundedCornerShape(12.dp))
                                    .clickable(enabled = abordable) { onPlanter(graine) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(graine.emoji, fontSize = 22.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(graine.nom,
                                        color = if (abordable) c.textPrimary else c.textSecondary,
                                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${graine.dureeMinutes / 60} h • rapporte ${graine.rendementPieces} 🪙",
                                        color = c.textSecondary, fontSize = 11.sp
                                    )
                                }
                                Text("${graine.prixPieces} 🪙",
                                    color = if (abordable) CoinColor else c.textDisabled,
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                else -> {
                    val graine = parcelle.graine
                    Text(graine?.nom ?: "Culture", color = c.textPrimary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        parcelle.stage?.libelle.orEmpty() +
                        if (parcelle.enRepos) " • en repos" else "",
                        color = c.textSecondary, fontSize = 12.sp
                    )

                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { parcelle.progression },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (parcelle.prete) AccentGold else SuccessGreen,
                        trackColor = c.surface3
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (parcelle.prete) "Prête à récolter"
                        else "Encore ${formaterDuree(parcelle.minutesRestantes)}",
                        color = c.textSecondary, fontSize = 12.sp
                    )

                    if (parcelle.enRepos) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Cette plante était en repos. Elle n'a rien perdu : " +
                            "arrose-la pour relancer sa croissance.",
                            color = c.textDisabled, fontSize = 11.sp
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    if (parcelle.prete) {
                        SankaiButton("🧺  Récolter", onClick = onRecolter,
                            modifier = Modifier.fillMaxWidth())
                    } else {
                        SankaiButton(
                            if (eau > 0) "💧  Arroser" else "Plus d'eau",
                            onClick = onArroser,
                            enabled = eau > 0,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
