package com.sankailife.ui.screens.life.memo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.R
import com.sankailife.SankaiApplication
import com.sankailife.core.modules.ModuleEngine
import com.sankailife.core.modules.ModuleRepository
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.DangerRed
import com.sankailife.ui.theme.SuccessGreen
import com.sankailife.ui.theme.WarningAmber
import com.sankailife.ui.theme.sankaiColors
import kotlinx.coroutines.launch

/**
 * Import d'un module d'apprentissage.
 *
 * Rien n'est écrit en base avant confirmation. L'aperçu montre l'auteur, la
 * version, le nombre de cartes et la licence : installer le contenu d'un
 * inconnu sans savoir qui l'a écrit ni sous quelles conditions serait un
 * mauvais réflexe à encourager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportModuleBouton() {
    val c = MaterialTheme.sankaiColors
    val contexte = LocalContext.current
    val app = contexte.applicationContext as SankaiApplication
    val portee = rememberCoroutineScope()
    val depot = remember { ModuleRepository(contexte, app.database) }

    var catalogueOuvert by remember { mutableStateOf(false) }
    var verdict by remember { mutableStateOf<ModuleEngine.Verdict?>(null) }
    var cartes by remember { mutableStateOf<List<String>>(emptyList()) }
    // Le succès et l'échec ne peuvent pas s'afficher de la même couleur :
    // « module installé » en rouge se lit comme une panne.
    var avis by remember { mutableStateOf<Avis?>(null) }

    val choisir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        portee.launch {
            runCatching { depot.inspecter(uri) }
                .onSuccess { (v, lignes) -> verdict = v; cartes = lignes }
                .onFailure { avis = Avis(it.message ?: contexte.getString(R.string.share_file_unreadable), succes = false) }
        }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        SankaiButton(
            stringResource(R.string.import_module_button),
            onClick = { choisir.launch(arrayOf("application/zip", "*/*")) },
            secondary = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        // La bibliotheque est livree avec l'application.
        //
        // Elle remplace un catalogue qui telechargeait depuis GitHub : tout le
        // contenu tient en 55 ko, et le telecharger imposait une connexion, des
        // hotes autorises, une verification d'empreinte et un message d'erreur
        // hors-ligne pour livrer ce qui pouvait deja etre la.
        SankaiButton(
            stringResource(R.string.import_module_library),
            onClick = { catalogueOuvert = true },
            secondary = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.import_module_offline_hint),
            color = c.textDisabled, fontSize = 11.sp
        )
        avis?.let {
            Spacer(Modifier.height(6.dp))
            Text(it.texte, color = if (it.succes) SuccessGreen else DangerRed, fontSize = 12.sp)
        }
    }

    if (catalogueOuvert) {
        var installes by remember { mutableStateOf<Set<String>>(emptySet()) }
        LaunchedEffect(Unit) {
            installes = runCatching {
                app.database.memoDao().getAllProfilesOnce().map { it.name }.toSet()
            }.getOrDefault(emptySet())
        }
        BibliothequeModules(
            nomsInstalles = installes,
            onFermer = { catalogueOuvert = false },
            onInstalle = {
                avis = Avis(contexte.getString(R.string.import_module_installed_library), succes = true)
            }
        )
    }

    verdict?.let { v ->
        ModalBottomSheet(onDismissRequest = { verdict = null }, containerColor = c.surface1) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)
            ) {
                Text(stringResource(R.string.import_module_title), color = c.textPrimary,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))

                when (v) {
                    is ModuleEngine.Verdict.Refuse -> {
                        Text(v.raison, color = DangerRed, fontSize = 13.sp)
                    }

                    is ModuleEngine.Verdict.Utilisable -> {
                        val m = v.apercu.manifeste
                        LigneInfo(stringResource(R.string.import_module_field_name), m.nom)
                        LigneInfo(
                            stringResource(R.string.import_module_field_author),
                            m.auteur.ifBlank { contexte.getString(R.string.import_module_author_unknown) }
                        )
                        LigneInfo(stringResource(R.string.import_module_field_version), m.version.ifBlank { "—" })
                        LigneInfo(stringResource(R.string.import_module_field_cards), "${v.apercu.nombreCartes}")
                        LigneInfo(
                            stringResource(R.string.import_module_field_size),
                            contexte.getString(R.string.import_module_size_kb, v.apercu.octets / 1024)
                        )
                        LigneInfo(
                            stringResource(R.string.import_module_field_license),
                            m.licence.ifBlank { contexte.getString(R.string.import_module_license_unknown) }
                        )

                        if (m.description.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(m.description, color = c.textSecondary, fontSize = 12.sp)
                        }

                        v.reserve?.let { r ->
                            Spacer(Modifier.height(12.dp))
                            Text(r, color = WarningAmber, fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.import_module_safe_hint),
                            color = c.textDisabled, fontSize = 11.sp
                        )

                        Spacer(Modifier.height(16.dp))
                        SankaiButton(
                            stringResource(R.string.action_install),
                            onClick = {
                                verdict = null
                                portee.launch {
                                    avis = runCatching { depot.installer(m, cartes) }
                                        .fold(
                                            onSuccess = { nom ->
                                                Avis(
                                                    contexte.getString(R.string.import_module_installed, nom),
                                                    succes = true
                                                )
                                            },
                                            onFailure = { e ->
                                                Avis(
                                                    e.message ?: contexte.getString(R.string.import_module_install_failed),
                                                    succes = false
                                                )
                                            }
                                        )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/** Adresse du dossier de modules, dans le dépôt du projet. */
@Composable
private fun LigneInfo(cle: String, valeur: String) {
    val c = MaterialTheme.sankaiColors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(cle, color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.width(80.dp))
        Text(valeur, color = c.textPrimary, fontSize = 13.sp)
    }
}

/** Retour d'import : le texte, et s'il annonce une réussite ou un échec. */
private data class Avis(val texte: String, val succes: Boolean)
