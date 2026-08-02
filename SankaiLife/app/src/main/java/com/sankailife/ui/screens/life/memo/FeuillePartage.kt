package com.sankailife.ui.screens.life.memo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.SankaiApplication
import com.sankailife.core.modules.FormatImportEngine
import com.sankailife.core.modules.ModuleEngine
import com.sankailife.core.modules.ModuleRepository
import com.sankailife.core.modules.PartageEntrant
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.DangerRed
import com.sankailife.ui.theme.SuccessGreen
import com.sankailife.ui.theme.sankaiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * L'aperçu de ce qui a été partagé vers l'application.
 *
 * **Rien n'est écrit avant confirmation.** Un contenu partagé vient de
 * l'extérieur : on montre ce qu'on a compris — combien de cartes, lesquelles —
 * et on attend un accord. Installer directement reviendrait à accepter
 * n'importe quoi de n'importe qui.
 *
 * Monté à la racine de la navigation : un partage peut arriver pendant qu'on
 * est sur l'Île ou dans les paramètres, et il doit s'afficher là où on est.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuillePartageEntrant() {
    val recu by PartageEntrant.recu.collectAsState()
    val contenu = recu ?: return

    val c = MaterialTheme.sankaiColors
    val contexte = LocalContext.current
    val app = contexte.applicationContext as SankaiApplication
    val portee = rememberCoroutineScope()
    val depot = remember { ModuleRepository(contexte, app.database) }

    var cartes by remember(contenu) { mutableStateOf<List<String>>(emptyList()) }
    var nom by remember(contenu) { mutableStateOf("") }
    var message by remember(contenu) { mutableStateOf("") }
    var estAdresse by remember(contenu) { mutableStateOf(false) }
    var termine by remember(contenu) { mutableStateOf(false) }

    LaunchedEffect(contenu) {
        runCatching {
            when (contenu) {
                is PartageEntrant.Contenu.Texte -> {
                    if (PartageEntrant.estUneAdresse(contenu.valeur)) {
                        estAdresse = true
                        return@runCatching
                    }
                    nom = contenu.titre.ifBlank { "Cartes partagées" }
                    cartes = lire(contenu.valeur.toByteArray(), "")
                }
                is PartageEntrant.Contenu.Fichier -> {
                    val octets = withContext(Dispatchers.IO) {
                        contexte.contentResolver.openInputStream(contenu.uri)
                            ?.use { it.readBytes() }
                    } ?: throw IllegalStateException("Fichier illisible.")

                    if (FormatImportEngine.detecter(octets, contenu.nom) ==
                        FormatImportEngine.Format.ARCHIVE
                    ) {
                        // Une archive passe par le lecteur existant, qui sait
                        // lire un manifeste et refuser un chemin dangereux.
                        val (verdict, lignes) = depot.inspecter(contenu.uri)
                        when (verdict) {
                            is ModuleEngine.Verdict.Refuse ->
                                throw IllegalStateException(verdict.raison)
                            is ModuleEngine.Verdict.Utilisable -> {
                                nom = verdict.apercu.manifeste.nom
                                    .ifBlank { FormatImportEngine.nomPropose(contenu.nom) }
                                cartes = lignes
                            }
                        }
                    } else {
                        nom = FormatImportEngine.nomPropose(contenu.nom)
                        cartes = lire(octets, contenu.nom)
                    }
                }
            }
        }.onFailure { message = it.message ?: "Contenu illisible." }
    }

    ModalBottomSheet(
        onDismissRequest = { PartageEntrant.consommer() },
        containerColor = c.surface1
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Contenu reçu", color = c.textPrimary, fontSize = 18.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                when (contenu) {
                    is PartageEntrant.Contenu.Fichier ->
                        contenu.nom.ifBlank { "Fichier partagé" }
                    is PartageEntrant.Contenu.Texte -> "Texte partagé"
                },
                color = c.textSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(14.dp))

            when {
                termine -> Text(message, color = SuccessGreen, fontSize = 13.sp)

                estAdresse -> Text(
                    // On le dit plutot que de fabriquer une carte contenant une
                    // URL, ce que personne ne veut.
                    "Ce partage ne contient qu'une adresse Internet. Sankai Life " +
                        "fonctionne hors ligne : partage plutôt le fichier " +
                        "lui-même, ou colle directement tes cartes.",
                    color = c.textSecondary, fontSize = 13.sp
                )

                message.isNotBlank() -> Text(message, color = DangerRed, fontSize = 13.sp)

                cartes.isEmpty() -> Text(
                    "Analyse en cours…", color = c.textSecondary, fontSize = 13.sp
                )

                else -> {
                    Text(
                        "${cartes.size} carte(s) reconnue(s)",
                        color = SuccessGreen, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    cartes.take(4).forEach {
                        Text(it, color = c.textSecondary, fontSize = 11.sp)
                    }
                    if (cartes.size > 4) Text("…", color = c.textDisabled, fontSize = 11.sp)

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = nom,
                        onValueChange = { nom = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Nom du module") }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            if (!termine && !estAdresse && cartes.isNotEmpty()) {
                SankaiButton(
                    "Importer",
                    onClick = {
                        portee.launch {
                            runCatching {
                                depot.installer(
                                    ModuleEngine.Manifeste(
                                        schemaVersion = 1,
                                        id = "partage-${System.currentTimeMillis()}",
                                        nom = nom.ifBlank { "Module partagé" },
                                        version = "1.0.0"
                                    ),
                                    cartes
                                )
                            }.onSuccess {
                                message = "« $it » importé — ${cartes.size} cartes."
                                termine = true
                            }.onFailure {
                                message = it.message ?: "Import impossible."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
            SankaiButton(
                if (termine) "Fermer" else "Annuler",
                onClick = { PartageEntrant.consommer() },
                secondary = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Lit des cartes depuis des octets, quel que soit le format non-archive. */
private fun lire(octets: ByteArray, nom: String): List<String> {
    val texte = String(octets)
    return when (FormatImportEngine.detecter(octets, nom)) {
        FormatImportEngine.Format.CSV -> FormatImportEngine.lireCsv(texte)
        FormatImportEngine.Format.INCONNU -> emptyList()
        else -> FormatImportEngine.lireTexte(texte)
    }
}
