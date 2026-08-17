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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankailife.R
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
    val etat by viewModel.etat.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val deplies by viewModel.deplies.collectAsStateWithLifecycle()
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
                    stringResource(R.string.academy_title), color = c.textPrimary,
                    fontSize = 24.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.academy_subtitle),
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
                    SectionTitle(stringResource(R.string.academy_revisions))
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
                            pluralStringResource(
                                R.plurals.academy_due_cards, etat.cartesDues, etat.cartesDues
                            ),
                            color = c.textPrimary, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            // On dit d'où vient la liste : une révision dont on
                            // comprend la raison se fait, une révision
                            // arbitraire s'évite.
                            stringResource(R.string.academy_due_reason),
                            color = c.textSecondary, fontSize = 12.sp
                        )
                    }
                }

                if (etat.semaine.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    SectionTitle(stringResource(R.string.academy_regularity))
                    Spacer(Modifier.height(8.dp))
                    SankaiCard {
                        Text(
                            pluralStringResource(
                                R.plurals.academy_days_week, etat.joursActifs, etat.joursActifs
                            ),
                            color = c.textPrimary, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(12.dp))
                        // Une pastille par jour : lire sa semaine en un coup
                        // d'œil, sans compteur à faire grossir. Le jour actif
                        // est rempli, le jour sans session reste sobre.
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            etat.semaine.forEach { jour ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        Modifier
                                            .size(30.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(
                                                when {
                                                    jour.actif -> c.accent
                                                    jour.aujourdHui -> c.surface3
                                                    else -> c.surface2
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            jour.libelle,
                                            color = if (jour.actif) {
                                                if (c.isDark) androidx.compose.ui.graphics.Color.White
                                                else c.background
                                            } else c.textSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = if (jour.actif) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    if (jour.aujourdHui) {
                                        Text(
                                            stringResource(R.string.academy_today),
                                            color = c.textSecondary, fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            // Compté en jours, pas en sessions : trois sessions
                            // le même soir ne font pas trois jours de
                            // régularité, et prétendre le contraire serait
                            // flatteur et faux.
                            stringResource(R.string.academy_days_distinct),
                            color = c.textSecondary, fontSize = 12.sp
                        )
                    }
                }

                if (etat.modulesDisponibles.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    SectionTitle(stringResource(R.string.academy_my_modules))
                    Spacer(Modifier.height(8.dp))
                    // Un dossier par parcours, pas six cartes identiques.
                    //
                    // « Mes modules » affichait A1 a C2 comme six entrees de
                    // meme poids, melangees aux pense-betes : on ne distinguait
                    // plus un parcours complet d'une liste de courses.
                    etat.groupes.forEach { groupe ->
                        val ouvert = groupe.id in deplies
                        if (!groupe.estParcours) {
                            val membre = groupe.modules.first()
                            LigneModule(
                                titre = membre.nom.ifBlank {
                                    stringResource(R.string.academy_module_unnamed)
                                },
                                details = pluralStringResource(
                                    R.plurals.academy_cards_count, membre.cartes, membre.cartes
                                ),
                                onClick = {
                                    onNavigate(Screen.Parcours.createRoute(membre.profileId))
                                }
                            )
                        } else {
                            SankaiCard(
                                modifier = Modifier.padding(bottom = 8.dp),
                                onClick = { viewModel.basculerGroupe(groupe.id) }
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.fillMaxWidth(0.84f)) {
                                        Text(
                                            groupe.titre, color = c.textPrimary,
                                            fontSize = 16.sp, fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            groupe.resume,
                                            color = c.textSecondary, fontSize = 12.sp
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { groupe.progression },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = c.accent,
                                            trackColor = c.surface3
                                        )
                                    }
                                    Text(
                                        if (ouvert) "⌃" else "⌄",
                                        color = c.textSecondary, fontSize = 20.sp
                                    )
                                }
                            }
                            if (ouvert) {
                                groupe.modules.forEach { membre ->
                                    Box(Modifier.padding(start = 14.dp)) {
                                        LigneModule(
                                            titre = membre.nom,
                                            details = buildList {
                                                if (membre.niveau.isNotBlank()) add(membre.niveau)
                                                add(
                                                    pluralStringResource(
                                                        R.plurals.academy_cards_count,
                                                        membre.cartes, membre.cartes
                                                    )
                                                )
                                                add("${(membre.progression * 100).toInt()} %")
                                            }.joinToString(" · "),
                                            onClick = {
                                                onNavigate(
                                                    Screen.Parcours.createRoute(membre.profileId)
                                                )
                                            }
                                        )
                                    }
                                }
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
                SectionTitle(stringResource(R.string.academy_tools))
                Spacer(Modifier.height(8.dp))

                // Focus est un outil de base. Une progression de jeu ne peut
                // plus bloquer une fonctionnalité éducative.
                val verrouFocus: com.sankailife.core.domain.engine.DeblocageEngine.Verrou? = null
                SankaiCard(
                    modifier = Modifier.padding(bottom = 8.dp),
                    onClick = if (verrouFocus == null) {
                        { onNavigate(Screen.Focus.route) }
                    } else null
                ) {
                    Text(
                        "${if (verrouFocus != null) "🔒" else "⏱️"} " +
                            stringResource(R.string.academy_focus),
                        color = if (verrouFocus != null) c.textDisabled else c.textPrimary,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        verrouFocus?.explication ?: stringResource(R.string.academy_focus_desc),
                        color = c.textSecondary, fontSize = 12.sp
                    )
                }
                SankaiCard(onClick = { onNavigate(Screen.Objectives.route) }) {
                    Text(
                        "🎯 ${stringResource(R.string.academy_objectives)}", color = c.textPrimary,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.academy_objectives_desc),
                        color = c.textSecondary, fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(20.dp))
                SectionTitle(stringResource(R.string.academy_content))
                Spacer(Modifier.height(8.dp))
                SankaiButton(
                    stringResource(R.string.academy_create_module),
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
        Text(
            stringResource(R.string.academy_continue_badge),
            color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            suite.module.nom.ifBlank {
                stringResource(R.string.academy_module_unnamed)
            } + if (suite.module.niveau.isNotBlank()) " ${suite.module.niveau}" else "",
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
            stringResource(
                R.string.academy_unit_progress, (suite.progression * 100).toInt()
            ),
            color = c.textSecondary, fontSize = 12.sp
        )

        Spacer(Modifier.height(10.dp))
        Text(
            // Annoncer la composition évite le sentiment de tirage au sort et
            // permet de refuser une session dont on n'a pas le temps.
            stringResource(
                R.string.academy_session_estimate, suite.minutes, suite.resume
            ),
            color = c.textSecondary, fontSize = 12.sp
        )
        Spacer(Modifier.height(14.dp))
        SankaiButton(
            stringResource(R.string.academy_start),
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
        Text(stringResource(R.string.academy_all_caught_up), color = c.textPrimary,
            fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.academy_all_caught_up_desc),
            color = c.textSecondary, fontSize = 13.sp
        )
    }
}

/** Aucun contenu : on explique quoi faire, une seule chose. */
@Composable
private fun PremierPas(onNavigate: (String) -> Unit) {
    val c = MaterialTheme.sankaiColors
    SankaiCard {
        Text(stringResource(R.string.academy_where_to_start), color = c.textPrimary,
            fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.academy_where_to_start_desc),
            color = c.textSecondary, fontSize = 13.sp
        )
        Spacer(Modifier.height(14.dp))
        SankaiButton(
            stringResource(R.string.academy_first_module),
            onClick = { onNavigate(Screen.Memo.route) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Une ligne de module, au premier niveau ou sous son parcours. */
@Composable
private fun LigneModule(
    titre: String,
    details: String,
    onClick: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    SankaiCard(modifier = Modifier.padding(bottom = 8.dp), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(titre, color = c.textPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(details, color = c.textSecondary, fontSize = 12.sp)
            }
            Text("›", color = c.textSecondary, fontSize = 22.sp)
        }
    }
}
