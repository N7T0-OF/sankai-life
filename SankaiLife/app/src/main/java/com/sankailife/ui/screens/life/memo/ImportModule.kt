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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                .onFailure { avis = Avis(it.message ?: "Fichier illisible.", succes = false) }
        }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        SankaiButton(
            "📦  Importer un module",
            onClick = { choisir.launch(arrayOf("application/zip", "*/*")) },
            secondary = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        // Renvoyer vers « le dépôt GitHub du projet » sans lien laissait
        // l'utilisateur chercher sur son téléphone une adresse qu'on ne lui
        // avait jamais donnée.
        SankaiButton(
            "Voir les modules disponibles",
            onClick = { ouvrirDepotModules(contexte) },
            secondary = true, small = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Des paquets de cartes prêts à l'emploi. Une fois importés, ils " +
                "fonctionnent entièrement hors ligne.",
            color = c.textDisabled, fontSize = 11.sp
        )
        avis?.let {
            Spacer(Modifier.height(6.dp))
            Text(it.texte, color = if (it.succes) SuccessGreen else DangerRed, fontSize = 12.sp)
        }
    }

    verdict?.let { v ->
        ModalBottomSheet(onDismissRequest = { verdict = null }, containerColor = c.surface1) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)
            ) {
                Text("Importer un module", color = c.textPrimary,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))

                when (v) {
                    is ModuleEngine.Verdict.Refuse -> {
                        Text(v.raison, color = DangerRed, fontSize = 13.sp)
                    }

                    is ModuleEngine.Verdict.Utilisable -> {
                        val m = v.apercu.manifeste
                        LigneInfo("Nom", m.nom)
                        LigneInfo("Auteur", m.auteur.ifBlank { "non déclaré" })
                        LigneInfo("Version", m.version.ifBlank { "—" })
                        LigneInfo("Cartes", "${v.apercu.nombreCartes}")
                        LigneInfo("Taille", "${v.apercu.octets / 1024} ko")
                        LigneInfo("Licence", m.licence.ifBlank { "non déclarée" })

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
                            "Un module ne contient que des données : ni script, ni " +
                                "code. Il s'ajoute à tes mémos sans rien remplacer.",
                            color = c.textDisabled, fontSize = 11.sp
                        )

                        Spacer(Modifier.height(16.dp))
                        SankaiButton(
                            "Installer",
                            onClick = {
                                verdict = null
                                portee.launch {
                                    avis = runCatching { depot.installer(m, cartes) }
                                        .fold(
                                            onSuccess = { nom ->
                                                Avis("« $nom » installé, désactivé par défaut.",
                                                    succes = true)
                                            },
                                            onFailure = { e ->
                                                Avis(e.message ?: "Échec de l'installation.",
                                                    succes = false)
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
private const val URL_MODULES = "https://github.com/N7T0-OF/sankai-life/tree/main/modules"

private fun ouvrirDepotModules(contexte: android.content.Context) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse(URL_MODULES)
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    // Aucun navigateur installé : on échoue en silence plutôt que de planter.
    runCatching { contexte.startActivity(intent) }
}

/** Retour d'import : le texte, et s'il annonce une réussite ou un échec. */
private data class Avis(val texte: String, val succes: Boolean)

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
