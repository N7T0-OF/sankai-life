package com.sankailife.ui.screens.life.memo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.SankaiApplication
import com.sankailife.core.modules.CatalogueEngine
import com.sankailife.core.modules.CatalogueRepository
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.DangerRed
import com.sankailife.ui.theme.SuccessGreen
import com.sankailife.ui.theme.sankaiColors
import kotlinx.coroutines.launch

/**
 * Le catalogue des contenus téléchargeables.
 *
 * Il remplace un bouton qui ouvrait le dépôt dans un navigateur : il fallait
 * y trouver le bon fichier, le télécharger, revenir dans l'application, puis
 * l'importer depuis le gestionnaire de fichiers. Cinq étapes, dont trois hors
 * de l'application, pour installer deux kilo-octets.
 *
 * **Rien n'est embarqué et rien n'est mis en cache.** L'installation reste
 * légère parce que le contenu n'y est pas ; une fois un module téléchargé, ses
 * cartes sont en base et plus rien ne dépend du réseau.
 *
 * La taille est annoncée avant le téléchargement, à côté du bouton. Quelqu'un
 * en données mobiles doit savoir ce qu'il engage — même si, en pratique, ces
 * modules pèsent moins qu'une photo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogueModules(
    nomsInstalles: Set<String>,
    onFermer: () -> Unit,
    onInstalle: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val contexte = LocalContext.current
    val app = contexte.applicationContext as SankaiApplication
    val portee = rememberCoroutineScope()
    val depot = remember { CatalogueRepository(contexte, app.database) }

    var chargement by remember { mutableStateOf(true) }
    var erreur by remember { mutableStateOf("") }
    var groupes by remember {
        mutableStateOf<List<Pair<CatalogueEngine.Famille, List<CatalogueEngine.Entree>>>>(
            emptyList()
        )
    }
    var enCours by remember { mutableStateOf<String?>(null) }
    var avis by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    LaunchedEffect(Unit) {
        depot.catalogue()
            .onSuccess { groupes = CatalogueEngine.classer(it); erreur = "" }
            .onFailure { erreur = it.message ?: "Catalogue indisponible." }
        chargement = false
    }

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Contenus disponibles", color = c.textPrimary,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Téléchargés à la demande, puis utilisables hors ligne.",
                color = c.textSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(16.dp))

            when {
                chargement -> Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator(color = c.accent) }

                erreur.isNotBlank() -> Column {
                    Text(erreur, color = DangerRed, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        // On distingue « pas de réseau » de « rien à proposer » :
                        // les modules déjà installés continuent de fonctionner.
                        "Tes modules déjà installés ne sont pas concernés : ils " +
                            "fonctionnent sans connexion.",
                        color = c.textSecondary, fontSize = 12.sp
                    )
                }

                groupes.isEmpty() -> Text(
                    "Aucun contenu publié pour l'instant.",
                    color = c.textSecondary, fontSize = 13.sp
                )

                else -> groupes.forEach { (famille, entrees) ->
                    Text(
                        famille.libelle.uppercase(), color = c.textSecondary,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    entrees.forEach { entree ->
                        LigneModule(
                            entree = entree,
                            installe = CatalogueEngine.estInstalle(entree, nomsInstalles),
                            occupe = enCours != null,
                            enCours = enCours == entree.id,
                            onInstaller = {
                                enCours = entree.id
                                avis = null
                                portee.launch {
                                    when (val r = depot.installer(entree)) {
                                        is CatalogueRepository.Issue.Ok -> {
                                            avis = r.message to true
                                            onInstalle()
                                        }
                                        is CatalogueRepository.Issue.Echec ->
                                            avis = r.raison to false
                                    }
                                    enCours = null
                                }
                            }
                        )
                        HorizontalDivider(color = c.border)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            avis?.let { (texte, succes) ->
                Spacer(Modifier.height(8.dp))
                Text(
                    texte, fontSize = 12.sp,
                    color = if (succes) SuccessGreen else DangerRed
                )
            }
        }
    }
}

@Composable
private fun LigneModule(
    entree: CatalogueEngine.Entree,
    installe: Boolean,
    occupe: Boolean,
    enCours: Boolean,
    onInstaller: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.fillMaxWidth(0.62f)) {
            Text(entree.nom, color = c.textPrimary, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold)
            Text(entree.details, color = c.textSecondary, fontSize = 12.sp)
            if (entree.description.isNotBlank()) {
                Text(entree.description, color = c.textDisabled, fontSize = 11.sp)
            }
            if (installe) {
                // On le dit sans empêcher : réinstaller reste possible pour qui
                // a supprimé un module par erreur.
                Text("Déjà installé", color = SuccessGreen, fontSize = 11.sp)
            }
        }
        SankaiButton(
            text = when {
                enCours -> "…"
                installe -> "Réinstaller"
                else -> "Installer"
            },
            onClick = onInstaller,
            enabled = !occupe,
            secondary = installe,
            small = true
        )
    }
}
