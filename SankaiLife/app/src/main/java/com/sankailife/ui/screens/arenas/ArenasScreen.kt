package com.sankailife.ui.screens.arenas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import com.sankailife.core.domain.model.Arena
import com.sankailife.ui.components.ResourceBar
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.sankaiColors

/** Parse une couleur « #RRGGBB » du modèle vers une Color Compose. */
private fun couleurDepuisHex(hex: String, defaut: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(defaut)

@Composable
fun ArenasScreen(viewModel: ArenasViewModel, onBack: () -> Unit) {
    val user by viewModel.user.collectAsState()
    val parcours by viewModel.parcours.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val c = MaterialTheme.sankaiColors

    val etatListe = rememberLazyListState()

    // À l'ouverture, on recentre sur l'arène courante plutôt que de laisser
    // l'utilisateur chercher où il en est dans une liste de huit paliers.
    LaunchedEffect(user.level, parcours.size) {
        if (parcours.isNotEmpty()) {
            etatListe.animateScrollToItem(viewModel.indexArenCourante(user.level))
        }
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
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(start = 16.dp, end = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(parcours, key = { _, l -> l.arene.id }) { index, ligne ->
                        CarteArene(
                            ligne = ligne,
                            niveauJoueur = user.level,
                            premier = index == 0,
                            dernier = index == parcours.lastIndex,
                            onReclamer = { viewModel.reclamer(ligne.arene) }
                        )
                    }
                }

                // Graduation des niveaux, à droite comme demandé.
                GraduationNiveaux(
                    niveauJoueur = user.level,
                    modifier = Modifier.width(46.dp).fillMaxHeight()
                )
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
    premier: Boolean,
    dernier: Boolean,
    onReclamer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val accent = couleurDepuisHex(ligne.arene.accentHex, c.accent)
    val verrouillee = ligne.etat == ArenasViewModel.EtatArene.VERROUILLEE

    // Une récompense disponible pulse légèrement : c'est le seul élément animé
    // en continu de l'écran, pour que l'œil y aille sans effort.
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Row(verticalAlignment = Alignment.Top) {

        // Chemin vertical reliant les paliers
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp).height(IntrinsicSize.Min)
        ) {
            Box(
                Modifier.width(2.dp).height(if (premier) 0.dp else 14.dp)
                    .background(if (verrouillee) c.border else accent.copy(alpha = 0.5f))
            )
            Box(
                Modifier.size(14.dp).clip(CircleShape)
                    .background(if (verrouillee) c.surface3 else accent)
            )
            if (!dernier) {
                Box(
                    Modifier.width(2.dp).weight(1f)
                        .background(if (verrouillee) c.border else accent.copy(alpha = 0.5f))
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (ligne.estCourante) accent.copy(alpha = 0.10f) else c.surface2)
                .border(
                    width = if (ligne.estCourante) 1.5.dp else 1.dp,
                    color = when {
                        ligne.estCourante -> accent
                        verrouillee -> c.border
                        else -> accent.copy(alpha = 0.35f)
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                .alpha(if (verrouillee) 0.55f else 1f)
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ligne.arene.emoji, fontSize = 26.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            ligne.arene.nom,
                            color = if (verrouillee) c.textSecondary else c.textPrimary,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Niveau ${ligne.arene.niveauRequis}",
                            color = c.textSecondary, fontSize = 11.sp
                        )
                    }
                    when (ligne.etat) {
                        ArenasViewModel.EtatArene.RECLAMEE ->
                            Icon(Icons.Filled.Check, "Réclamée", tint = accent, modifier = Modifier.size(20.dp))
                        ArenasViewModel.EtatArene.VERROUILLEE ->
                            Icon(Icons.Filled.Lock, "Verrouillée", tint = c.textDisabled, modifier = Modifier.size(18.dp))
                        else -> Unit
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(ligne.arene.description, color = c.textSecondary, fontSize = 12.sp)

                Spacer(Modifier.height(8.dp))
                Text(
                    "Récompense : ${ligne.arene.recompense.resume()}",
                    color = if (verrouillee) c.textDisabled else accent,
                    fontSize = 12.sp, fontWeight = FontWeight.Medium
                )

                when (ligne.etat) {
                    ArenasViewModel.EtatArene.A_RECLAMER -> {
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.alpha(pulse)) {
                            SankaiButton(
                                "Réclamer",
                                onClick = onReclamer,
                                small = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    ArenasViewModel.EtatArene.VERROUILLEE -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Encore ${(ligne.arene.niveauRequis - niveauJoueur).coerceAtLeast(0)} niveaux",
                            color = c.textDisabled, fontSize = 11.sp
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}

/**
 * Graduation verticale des niveaux.
 *
 * Repère fixe : le joueur voit d'un coup d'œil où il se situe entre le départ
 * et le sommet, indépendamment du défilement de la liste.
 */
@Composable
private fun GraduationNiveaux(niveauJoueur: Int, modifier: Modifier = Modifier) {
    val c = MaterialTheme.sankaiColors
    val maximum = ArenaEngine.niveauMaximum
    val ratio = (niveauJoueur.toFloat() / maximum).coerceIn(0f, 1f)

    Column(
        modifier = modifier.padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$maximum", color = c.textDisabled, fontSize = 9.sp)
        Spacer(Modifier.height(4.dp))

        Box(
            Modifier.width(6.dp).weight(1f).clip(RoundedCornerShape(3.dp)).background(c.surface3),
            contentAlignment = Alignment.BottomCenter
        ) {
            // La barre se remplit du bas vers le haut : on monte vers le sommet.
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(ratio)
                    .clip(RoundedCornerShape(3.dp))
                    .background(c.accent)
            )
        }

        Spacer(Modifier.height(4.dp))
        Text("Niv.", color = c.textDisabled, fontSize = 9.sp)
        Text("$niveauJoueur", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Carte de résumé, affichée dans le Profil.
 * Donne l'arène actuelle, la progression et la prochaine récompense sans
 * ouvrir le parcours complet.
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

    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(actuelle.emoji, fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Arène actuelle", color = c.textSecondary, fontSize = 11.sp)
                    Text(actuelle.nom, color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
            Spacer(Modifier.height(6.dp))

            if (suivante != null) {
                Text(
                    "Prochaine : ${suivante.emoji} ${suivante.nom} " +
                    "— encore ${ArenaEngine.niveauxRestants(niveau)} niveaux",
                    color = c.textSecondary, fontSize = 12.sp
                )
                Text(
                    "Récompense : ${suivante.recompense.resume()}",
                    color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium
                )
            } else {
                Text("Sommet atteint. Le parcours n'a plus de plafond.",
                    color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(12.dp))
            SankaiButton(
                if (nombreAReclamer > 0) "Voir le parcours • $nombreAReclamer à réclamer"
                else "Voir le parcours",
                onClick = onVoirParcours,
                small = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
