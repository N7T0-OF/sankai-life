package com.sankailife.ui.screens.academie

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.ui.components.ResourceBar
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.components.SankaiCard
import com.sankailife.ui.components.SectionTitle
import com.sankailife.ui.navigation.Screen
import com.sankailife.ui.theme.sankaiColors

/**
 * L'accueil de l'Académie.
 *
 * **Une seule action passe devant.** L'écran qu'il remplace montrait Focus,
 * objectifs, mémos, slots de module et boutique côte à côte, tous de la même
 * taille : on y lisait ce qu'on *pouvait* faire, jamais ce qu'on *devait*
 * faire, et le premier réflexe devant six portes équivalentes est de n'en
 * ouvrir aucune.
 *
 * Le reste n'est pas supprimé pour autant — il descend simplement en dessous
 * de la recommandation. Retirer des outils que quelqu'un utilise déjà pour
 * « simplifier » serait décider à sa place.
 */
@Composable
fun AcademieScreen(
    viewModel: AcademieViewModel,
    onNavigate: (String) -> Unit
) {
    val etat by viewModel.etat.collectAsState()
    val utilisateur by viewModel.utilisateur.collectAsState()
    val message by viewModel.message.collectAsState()
    val c = MaterialTheme.sankaiColors
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        if (message.isNotBlank()) {
            snackbar.showSnackbar(message)
            viewModel.messageAffiche()
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            ResourceBar(
                utilisateur.level, utilisateur.xp, utilisateur.xpNext,
                utilisateur.coins, utilisateur.gems
            )

            if (etat.chargement) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accent)
                }
                return@Column
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                Text(
                    "Académie", color = c.textPrimary,
                    fontSize = 24.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    "Ce que tu apprends, et quand",
                    color = c.textSecondary, fontSize = 13.sp
                )
                Spacer(Modifier.height(18.dp))

                when {
                    etat.suite != null -> CarteSuite(etat.suite!!, onNavigate)
                    etat.modulesDisponibles.isNotEmpty() -> RienAFaire()
                    else -> PremierPas(onNavigate)
                }

                if (etat.cartesDues > 0) {
                    Spacer(Modifier.height(20.dp))
                    SectionTitle("Révisions")
                    Spacer(Modifier.height(8.dp))
                    SankaiCard(onClick = {
                        onNavigate(
                            Screen.Flashcards.createRoute(
                                com.sankailife.ui.screens.life.flashcards
                                    .FlashcardsViewModel.PROFIL_ERREURS
                            )
                        )
                    }) {
                        Text(
                            "${etat.cartesDues} carte(s) à revoir",
                            color = c.textPrimary, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            // On dit d'où vient la liste : une révision dont on
                            // comprend la raison se fait, une révision
                            // arbitraire s'évite.
                            "Leur date de rappel est passée.",
                            color = c.textSecondary, fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                SectionTitle("Régularité")
                Spacer(Modifier.height(8.dp))
                SankaiCard {
                    Text(
                        "${etat.joursActifs} jour(s) travaillé(s) cette semaine",
                        color = c.textPrimary, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        // Compté en jours, pas en sessions : trois sessions le
                        // même soir ne font pas trois jours de régularité, et
                        // prétendre le contraire serait flatteur et faux.
                        "Compté en jours distincts, pas en sessions.",
                        color = c.textSecondary, fontSize = 12.sp
                    )
                }

                if (etat.modulesDisponibles.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    SectionTitle("Mes modules")
                    Spacer(Modifier.height(8.dp))
                    etat.modulesDisponibles.forEach { (profil, cartes) ->
                        SankaiCard(
                            modifier = Modifier.padding(bottom = 8.dp),
                            onClick = { onNavigate(Screen.Parcours.createRoute(profil.id)) }
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        profil.name.ifBlank { "Module sans nom" },
                                        color = c.textPrimary, fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "$cartes carte(s)" +
                                            if (profil.langue.isNotBlank()) {
                                                " · ${profil.langue}"
                                            } else "",
                                        color = c.textSecondary, fontSize = 12.sp
                                    )
                                }
                                Text("›", color = c.textSecondary, fontSize = 22.sp)
                            }
                        }
                    }
                }

                // Focus et Objectifs.
                //
                // Ils n'existaient nulle part ailleurs que sur l'écran remplacé.
                // Les laisser de côté parce que la nouvelle maquette ne les
                // mentionne pas les aurait rendus inatteignables sans qu'aucun
                // message ne le dise — exactement le défaut trouvé cette
                // semaine sur l'embauche des Mimos. Ils descendent d'un cran,
                // ils ne disparaissent pas.
                Spacer(Modifier.height(20.dp))
                SectionTitle("Outils")
                Spacer(Modifier.height(8.dp))

                val verrouFocus = com.sankailife.core.domain.engine.DeblocageEngine.verrou(
                    com.sankailife.core.domain.engine.DeblocageEngine.Fonction.FOCUS,
                    utilisateur.level
                )
                SankaiCard(
                    modifier = Modifier.padding(bottom = 8.dp),
                    onClick = if (verrouFocus == null) {
                        { onNavigate(Screen.Focus.route) }
                    } else null
                ) {
                    Text(
                        "${if (verrouFocus != null) "🔒" else "⏱️"} Focus",
                        color = if (verrouFocus != null) c.textDisabled else c.textPrimary,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        verrouFocus?.explication ?: "Sessions de concentration",
                        color = c.textSecondary, fontSize = 12.sp
                    )
                }
                SankaiCard(onClick = { onNavigate(Screen.Objectives.route) }) {
                    Text(
                        "🎯 Objectifs", color = c.textPrimary,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text("Ta liste personnelle", color = c.textSecondary, fontSize = 12.sp)
                }

                Spacer(Modifier.height(20.dp))
                SectionTitle("Contenu")
                Spacer(Modifier.height(8.dp))
                SankaiButton(
                    "Créer ou modifier un module",
                    onClick = { onNavigate(Screen.Memo.route) },
                    secondary = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(28.dp))
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

/** La recommandation du jour. Le seul élément mis en avant. */
@Composable
private fun CarteSuite(
    suite: AcademieViewModel.Suite,
    onNavigate: (String) -> Unit
) {
    val c = MaterialTheme.sankaiColors
    SankaiCard {
        Text("CONTINUER", color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            suite.module.nom.ifBlank { "Module" } +
                if (suite.module.niveau.isNotBlank()) " ${suite.module.niveau}" else "",
            color = c.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold
        )
        Text(suite.unite.titre, color = c.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = { suite.progression },
            modifier = Modifier.fillMaxWidth(),
            color = c.accent,
            trackColor = c.surface3
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${(suite.progression * 100).toInt()} % de l'unité",
            color = c.textSecondary, fontSize = 12.sp
        )

        Spacer(Modifier.height(10.dp))
        Text(
            // Annoncer la composition évite le sentiment de tirage au sort et
            // permet de refuser une session dont on n'a pas le temps.
            "≈ ${suite.minutes} min · ${suite.resume}",
            color = c.textSecondary, fontSize = 12.sp
        )
        Spacer(Modifier.height(14.dp))
        SankaiButton(
            "Commencer",
            onClick = {
                onNavigate(Screen.Parcours.createRoute(suite.module.memoProfileId))
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Tout est à jour : on le dit, sans inventer une tâche pour meubler. */
@Composable
private fun RienAFaire() {
    val c = MaterialTheme.sankaiColors
    SankaiCard {
        Text("Tout est à jour", color = c.textPrimary, fontSize = 17.sp,
            fontWeight = FontWeight.Bold)
        Text(
            "Aucune carte n'attend de révision. Reviens plus tard, ou ajoute " +
                "du contenu à un module.",
            color = c.textSecondary, fontSize = 13.sp
        )
    }
}

/** Aucun contenu : on explique quoi faire, une seule chose. */
@Composable
private fun PremierPas(onNavigate: (String) -> Unit) {
    val c = MaterialTheme.sankaiColors
    SankaiCard {
        Text("Par où commencer", color = c.textPrimary, fontSize = 17.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "L'Académie construit un parcours à partir de ton contenu. " +
                "Crée un module — une langue, une matière, une liste de mots — " +
                "et le parcours se découpe tout seul.",
            color = c.textSecondary, fontSize = 13.sp
        )
        Spacer(Modifier.height(14.dp))
        SankaiButton(
            "Créer mon premier module",
            onClick = { onNavigate(Screen.Memo.route) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
