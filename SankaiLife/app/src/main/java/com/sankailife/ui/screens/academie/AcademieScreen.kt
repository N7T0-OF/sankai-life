package com.sankailife.ui.screens.academie

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankailife.R
import com.sankailife.core.data.db.dao.StatsModule
import com.sankailife.core.data.db.entities.MemoProfileEntity
import com.sankailife.core.learning.domain.GroupementEngine
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.components.SankaiCard
import com.sankailife.ui.components.SankaiFloatingButton
import com.sankailife.ui.navigation.Screen
import com.sankailife.ui.screens.life.flashcards.FlashcardsViewModel
import com.sankailife.ui.screens.life.memo.CarteMesErreurs
import com.sankailife.ui.screens.life.memo.ImportModuleBouton
import com.sankailife.ui.screens.life.memo.MemoViewModel
import com.sankailife.ui.theme.Drawxsouanpt
import com.sankailife.ui.theme.SankaiRadius
import com.sankailife.ui.theme.SankaiSpacing
import com.sankailife.ui.theme.sankaiColors

/**
 * L'onglet Apprendre, devenu la bibliothèque d'apprentissage.
 *
 * La maquette rassemblait deux écrans : l'entrée « Académie » (decks) et la
 * bibliothèque « Mémos » (stats, modules, erreurs, import). L'application n'a
 * qu'un onglet — il porte donc les deux : la bibliothèque d'abord, et la
 * révision express comme action la plus courte, juste après les chiffres.
 *
 * Un parcours est une carte-dossier : pourcentage, drapeau, niveaux, barre de
 * maîtrise, « Continuer → » vers là où on s'est arrêté, et les niveaux
 * s'ouvrent à l'intérieur. Un module seul tient dans une carte compacte, en
 * grille de deux, comme la maquette.
 *
 * La suppression ne se déclenche jamais au simple appui : il faut maintenir
 * (« HOLD TO DELETE »), puis confirmer. Rien ne disparaît sans un geste
 * explicite et un décompte de ce qu'on perd.
 */
@Composable
fun AcademieScreen(
    viewModel: AcademieViewModel,
    memoViewModel: MemoViewModel,
    onNavigate: (String) -> Unit,
    onEdit: (Long) -> Unit,
    onReviserErreurs: () -> Unit
) {
    val etat by viewModel.etat.collectAsStateWithLifecycle()
    val messageAcademie by viewModel.message.collectAsStateWithLifecycle()
    val profiles by memoViewModel.profiles.collectAsState()
    val groupes by memoViewModel.groupes.collectAsState()
    val deplies by memoViewModel.deplies.collectAsState()
    val aDesinstaller by memoViewModel.aDesinstaller.collectAsState()
    val message by memoViewModel.message.collectAsState()
    val stats by memoViewModel.statsParModule.collectAsState()
    val c = MaterialTheme.sankaiColors
    val snackbar = remember { SnackbarHostState() }

    // Ouvre le parcours en cours à l'arrivée, une seule fois : « Continuer »
    // doit être visible sans avoir à deviner qu'il faut déplier.
    LaunchedEffect(Unit) { memoViewModel.ouvertureInitiale() }

    LaunchedEffect(message, messageAcademie) {
        val texte = message.ifBlank { messageAcademie }
        if (texte.isNotBlank()) {
            snackbar.showSnackbar(texte)
            if (message.isNotBlank()) memoViewModel.messageAffiche()
            if (messageAcademie.isNotBlank()) viewModel.messageAffiche()
        }
    }

    var aSupprimer by remember { mutableStateOf<MemoProfileEntity?>(null) }

    val totalCards = stats.values.sumOf { it.total }
    val dueCards = stats.values.sumOf { it.dues }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            EnTeteMemos(
                profileCount = profiles.size,
                onAjouter = { memoViewModel.createNewProfile(onCreated = onEdit) }
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
                    CarteBibliotheque(
                        profileCount = profiles.size,
                        totalCards = totalCards,
                        dueCards = dueCards
                    )
                }

                // La révision express : l'action la plus courte passe devant.
                // Elle se compose toute seule et se termine seule — on n'est
                // jamais invité à « continuer pour gagner ».
                item {
                    CarteRevisionExpress(onReviser = {
                        onNavigate(
                            Screen.Flashcards.createRoute(FlashcardsViewModel.PROFIL_EXPRESS)
                        )
                    })
                }

                if (groupes.isEmpty()) {
                    item {
                        CarteVide(onCreer = { memoViewModel.createNewProfile(onCreated = onEdit) })
                    }
                } else {
                    item { TitreSection(stringResource(R.string.academy_my_modules)) }

                    // Un parcours tient dans une carte-dossier pleine largeur :
                    // pas six cartes identiques alignées, mais un dossier qui
                    // s'ouvre sur ses niveaux — les niveaux vivent À l'intérieur
                    // de la carte (CarteParcours), pas dans des items séparés :
                    // les afficher deux fois les aurait dupliqués.
                    groupes.filter { it.estParcours }.forEach { groupe ->
                        item(key = "parcours_${groupe.id}") {
                            CarteParcours(
                                groupe = groupe,
                                ouvert = groupe.id in deplies,
                                suite = etat.suite,
                                emoji = emojiDuGroupe(groupe, profiles),
                                stats = stats,
                                onBasculer = { memoViewModel.basculerGroupe(groupe.id) },
                                onContinuer = { profileId ->
                                    onNavigate(Screen.Parcours.createRoute(profileId))
                                },
                                onDesinstaller = { memoViewModel.demanderDesinstallation(groupe) },
                                onOuvrirNiveau = { profileId ->
                                    onNavigate(Screen.Parcours.createRoute(profileId))
                                }
                            )
                        }
                    }

                    // Les modules seuls tiennent dans des cartes compactes, en
                    // grille de deux, avec « Nouveau dossier » pour compléter —
                    // comme la maquette.
                    val seuls = groupes.filterNot { it.estParcours }
                    val cellules: List<Long> =
                        seuls.map { it.modules.first().profileId } + listOf(NOUVEAU_DOSSIER)
                    cellules.chunked(2).forEach { rangee ->
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(SankaiSpacing.Md)) {
                                rangee.forEach { profileId ->
                                    if (profileId == NOUVEAU_DOSSIER) {
                                        CarteNouveauDossier(
                                            onClick = {
                                                memoViewModel.createNewProfile(onCreated = onEdit)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        val groupe = seuls.first {
                                            it.modules.first().profileId == profileId
                                        }
                                        CarteModuleSeul(
                                            groupe = groupe,
                                            stats = stats[profileId],
                                            emoji = emojiDuGroupe(groupe, profiles),
                                            onOuvrir = {
                                                onNavigate(Screen.Parcours.createRoute(profileId))
                                            },
                                            onSupprimer = {
                                                aSupprimer = profiles.firstOrNull {
                                                    it.id == profileId
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                if (rangee.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    item {
                        CarteMesErreurs(memoViewModel, onReviser = onReviserErreurs)
                    }
                }

                item { ImportModuleBouton() }
            }
        }

        aDesinstaller?.let { cible ->
            AlertDialog(
                onDismissRequest = { memoViewModel.annulerDesinstallation() },
                title = { Text(stringResource(R.string.memo_uninstall_title, cible.titre)) },
                text = {
                    Text(
                        stringResource(
                            R.string.memo_uninstall_body,
                            cible.profileIds.size,
                            cible.cartes
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = { memoViewModel.confirmerDesinstallation() }) {
                        Text(stringResource(R.string.memo_uninstall_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { memoViewModel.annulerDesinstallation() }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        aSupprimer?.let { profil ->
            AlertDialog(
                onDismissRequest = { aSupprimer = null },
                title = { Text(stringResource(R.string.memo_delete_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.memo_delete_confirmation,
                            profil.name.ifBlank {
                                stringResource(R.string.memo_default_name)
                            }
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        memoViewModel.deleteProfile(profil.id)
                        aSupprimer = null
                    }) {
                        Text(stringResource(R.string.memo_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { aSupprimer = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(SankaiSpacing.Lg)
        )
    }
}

/** Identifiant de la cellule « Nouveau dossier » dans la grille de decks. */
private const val NOUVEAU_DOSSIER = -1L

@Composable
private fun EnTeteMemos(profileCount: Int, onAjouter: () -> Unit) {
    val c = MaterialTheme.sankaiColors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SankaiSpacing.Lg, vertical = SankaiSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.memo_title),
                color = c.textPrimary,
                fontSize = 26.sp,
                fontFamily = Drawxsouanpt
            )
            Text(
                stringResource(R.string.memo_profile_count, profileCount),
                color = c.textSecondary, fontSize = 13.sp
            )
        }
        SankaiFloatingButton(
            contentDescription = stringResource(R.string.memo_create),
            onClick = onAjouter,
            // Sans taille fixe, le bouton s'étend à tout l'espace offert par la
            // ligne (sizeIn ne plafonne pas et son contenu est fillMaxSize) :
            // il recouvrait l'écran et masquait le reste de la bibliothèque.
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Filled.Add, null, tint = c.textPrimary)
        }
    }
}

/** « Ta bibliothèque » : les trois chiffres à lire d'un coup d'œil. */
@Composable
private fun CarteBibliotheque(profileCount: Int, totalCards: Int, dueCards: Int) {
    val c = MaterialTheme.sankaiColors
    SankaiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📚", fontSize = 30.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.memo_library_title),
                    color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.memo_library_hint),
                    color = c.textSecondary, fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatMini(
                stringResource(R.string.memo_stat_modules),
                formatCount(profileCount),
                Modifier.weight(1f)
            )
            StatMini(
                stringResource(R.string.memo_stat_cards),
                formatCount(totalCards),
                Modifier.weight(1f)
            )
            StatMini(
                stringResource(R.string.memo_stat_due),
                formatCount(dueCards),
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatMini(label: String, value: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.sankaiColors
    Column(
        modifier
            .clip(RoundedCornerShape(SankaiRadius.Medium))
            .background(c.surface3.copy(alpha = 0.55f))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label.uppercase(),
            color = c.textSecondary, fontSize = 8.sp, letterSpacing = 0.4.sp
        )
        Text(value, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** La révision express : cinq notions choisies par le moteur, en deux minutes. */
@Composable
private fun CarteRevisionExpress(onReviser: () -> Unit) {
    val c = MaterialTheme.sankaiColors
    SankaiCard(onClick = onReviser) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("⚡", fontSize = 24.sp)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.academy_express_title),
                    color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.academy_express_desc),
                    color = c.textSecondary, fontSize = 12.sp
                )
            }
            Text("›", color = c.textSecondary, fontSize = 22.sp)
        }
    }
}

/** La carte-dossier d'un parcours : progression, « Continuer », niveaux. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CarteParcours(
    groupe: GroupementEngine.Groupe,
    ouvert: Boolean,
    suite: AcademieViewModel.Suite?,
    emoji: String,
    stats: Map<Long, StatsModule>,
    onBasculer: () -> Unit,
    onContinuer: (Long) -> Unit,
    onDesinstaller: () -> Unit,
    onOuvrirNiveau: (Long) -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val pct = (groupe.progression.coerceIn(0f, 1f) * 100).toInt()
    val continueIci = suite != null &&
        groupe.modules.any { it.profileId == suite.module.memoProfileId }

    SankaiCard(
        modifier = Modifier.combinedClickable(
            onClick = onBasculer,
            onLongClick = onDesinstaller
        )
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 28.sp)
            Text(
                "$pct%",
                fontFamily = Drawxsouanpt, fontSize = 20.sp, color = c.accent
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            groupe.titre, color = c.textPrimary, fontSize = 16.sp,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            resumeDuGroupe(groupe), color = c.textSecondary, fontSize = 11.5.sp
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { groupe.progression.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = c.accent,
            trackColor = c.surface3
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.memo_mastery_percent, pct),
            color = c.textSecondary, fontSize = 11.sp
        )

        if (continueIci) {
            val suiteLocale = suite
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = c.border.copy(alpha = 0.7f))
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) {
                        onContinuer(suiteLocale.module.memoProfileId)
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.memo_last_session),
                        color = c.textSecondary, fontSize = 10.sp
                    )
                    Text(
                        buildList {
                            suiteLocale.module.niveau.takeIf { it.isNotBlank() }?.let { add(it) }
                            add(suiteLocale.unite.titre)
                        }.joinToString(" · "),
                        color = c.textPrimary, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "${stringResource(R.string.memo_continue)} →",
                    color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.clickable(role = Role.Button, onClick = onDesinstaller),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🗑", fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.memo_hold_to_delete),
                    color = c.textDisabled, fontSize = 8.5.sp, letterSpacing = 0.4.sp
                )
            }
            Text(
                if (ouvert) "⌃" else "⌄",
                color = c.textSecondary, fontSize = 20.sp
            )
        }

        if (ouvert) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = c.border.copy(alpha = 0.7f))
            Spacer(Modifier.height(6.dp))
            groupe.modules.forEach { membre ->
                LigneNiveau(
                    membre = membre,
                    stats = stats[membre.profileId],
                    onClick = { onOuvrirNiveau(membre.profileId) }
                )
            }
        }
    }
}

/** Un module seul, en carte compacte de la grille. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CarteModuleSeul(
    groupe: GroupementEngine.Groupe,
    stats: StatsModule?,
    emoji: String,
    onOuvrir: () -> Unit,
    onSupprimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.sankaiColors
    val membre = groupe.modules.first()
    val total = stats?.total ?: 0
    val dues = stats?.dues ?: 0
    val pct = (membre.progression.coerceIn(0f, 1f) * 100).toInt()

    SankaiCard(
        modifier = modifier.combinedClickable(
            onClick = onOuvrir,
            onLongClick = onSupprimer
        )
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(emoji, fontSize = 26.sp)
                Text("$pct%", fontFamily = Drawxsouanpt, fontSize = 17.sp, color = c.accent)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                membre.nom.ifBlank { stringResource(R.string.academy_module_unnamed) },
                color = c.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                buildList {
                    add("${formatCount(total)} ${stringResource(R.string.memo_cards_label).lowercase()}")
                    add(
                        if (dues > 0) stringResource(R.string.memo_summary_due, dues)
                        else stringResource(R.string.memo_up_to_date)
                    )
                }.joinToString(" · "),
                color = c.textSecondary, fontSize = 10.5.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🗑  ${stringResource(R.string.memo_hold_to_delete)}",
                    color = c.textDisabled, fontSize = 8.5.sp, letterSpacing = 0.4.sp
                )
                Text("›", color = c.textSecondary, fontSize = 18.sp)
            }
        }
    }
}

/** Une ligne de niveau, sous la carte-dossier ouverte. */
@Composable
private fun LigneNiveau(
    membre: GroupementEngine.Module,
    stats: StatsModule?,
    onClick: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val pct = (membre.progression.coerceIn(0f, 1f) * 100).toInt()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SankaiRadius.Small))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                membre.nom, color = c.textPrimary, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                buildList {
                    add("${formatCount(membre.cartes)} cartes")
                    add("$pct%")
                }.joinToString(" · "),
                color = c.textSecondary, fontSize = 11.sp
            )
        }
        Text("›", color = c.textSecondary, fontSize = 18.sp)
    }
}

/** « ＋ Nouveau dossier — Crée un thème », dernière cellule de la grille. */
@Composable
private fun CarteNouveauDossier(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.sankaiColors
    Box(
        modifier
            .clip(RoundedCornerShape(SankaiRadius.Medium))
            .background(c.surface2.copy(alpha = 0.5f))
            .border(1.dp, c.border, RoundedCornerShape(SankaiRadius.Medium))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("＋", color = c.textSecondary, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.memo_new_folder),
                color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.memo_new_folder_hint),
                color = c.textDisabled, fontSize = 10.5.sp, textAlign = TextAlign.Center
            )
        }
    }
}

/** Aucun module : on explique quoi faire, sans chiffres à zéro. */
@Composable
private fun CarteVide(onCreer: () -> Unit) {
    val c = MaterialTheme.sankaiColors
    SankaiCard {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📚", fontSize = 34.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.memo_empty_title),
                color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.memo_empty_hint),
                color = c.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            SankaiButton(
                stringResource(R.string.memo_create_theme),
                onClick = onCreer,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TitreSection(texte: String) {
    val c = MaterialTheme.sankaiColors
    Text(
        texte,
        color = c.textPrimary, fontSize = 20.sp, fontFamily = Drawxsouanpt,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
    )
}

/** « 6 niveaux · 1 240 cartes », sans inventer de libellé. */
private fun resumeDuGroupe(groupe: GroupementEngine.Groupe): String = buildList {
    add("${groupe.modules.size} niveaux")
    add("${formatCount(groupe.cartes)} cartes")
}.joinToString(" · ")

/** Drapeau de la langue du premier module du groupe, sinon une icône neutre. */
private fun emojiDuGroupe(
    groupe: GroupementEngine.Groupe,
    profiles: List<MemoProfileEntity>
): String {
    val langue = groupe.modules.firstNotNullOfOrNull { m ->
        profiles.firstOrNull { it.id == m.profileId }
            ?.langue?.trim()?.takeIf { it.isNotBlank() }
    }
    return when (langue?.lowercase()?.substringBefore('-')) {
        "fr" -> "🇫🇷"
        "pt" -> "🇵🇹"
        "en" -> "🇬🇧"
        "es" -> "🇪🇸"
        "it" -> "🇮🇹"
        "de" -> "🇩🇪"
        else -> "📚"
    }
}

/** 1240 → « 1 240 », sans dépendre de la locale. */
private fun formatCount(n: Int): String {
    val s = n.toString()
    val sb = StringBuilder()
    s.forEachIndexed { i, ch ->
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(' ')
        sb.append(ch)
    }
    return sb.toString()
}
