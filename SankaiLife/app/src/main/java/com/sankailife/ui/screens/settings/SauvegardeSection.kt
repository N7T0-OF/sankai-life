package com.sankailife.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.SankaiApplication
import com.sankailife.core.data.sauvegarde.SauvegardeEngine
import com.sankailife.core.data.sauvegarde.SauvegardeRepository
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Sauvegarde et restauration, dans les paramètres.
 *
 * Sans compte, sans serveur, sans connexion : le sélecteur de fichiers Android
 * choisit où écrire et quoi lire. C'est le seul moyen de garantir qu'un profil
 * survit à un téléphone perdu sans demander de créer un compte pour ça.
 *
 * La restauration ne se déclenche jamais directement depuis le choix du
 * fichier. Elle passe par un aperçu, parce qu'écraser un profil est
 * irréversible et qu'un nom de fichier ne prouve rien.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SauvegardeSection() {
    val c = MaterialTheme.sankaiColors
    val contexte = LocalContext.current
    val app = contexte.applicationContext as SankaiApplication
    val portee = rememberCoroutineScope()
    val depot = remember { SauvegardeRepository(contexte, app.database) }

    var message by remember { mutableStateOf<String?>(null) }
    var apercu by remember { mutableStateOf<SauvegardeRepository.Apercu?>(null) }
    var fichierChoisi by remember { mutableStateOf<Uri?>(null) }
    var sections by remember { mutableStateOf(SauvegardeEngine.Section.entries.toSet()) }

    val creerFichier = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        portee.launch {
            message = runCatching { depot.exporter(uri) }
                .fold(
                    onSuccess = { "Sauvegarde écrite (${it / 1024} ko)." },
                    onFailure = { "Échec : ${it.message}" }
                )
        }
    }

    val ouvrirFichier = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        portee.launch {
            fichierChoisi = uri
            apercu = runCatching { depot.inspecter(uri) }.getOrNull()
            if (apercu == null) message = "Fichier illisible."
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "Ta progression, tes mémos et ton jardin dans un seul fichier. " +
                "Aucun compte, aucune connexion : tu choisis où il est rangé.",
            color = c.textSecondary, fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                SankaiButton(
                    "Exporter",
                    onClick = { creerFichier.launch(SauvegardeRepository.nomProposé()) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(Modifier.weight(1f)) {
                SankaiButton(
                    "Restaurer",
                    onClick = { ouvrirFichier.launch(arrayOf("*/*")) },
                    secondary = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        message?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = c.textSecondary, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))
        // Le tutoriel se rejoue à la demande. Il ne réapparaît jamais tout
        // seul après une mise à jour : réexpliquer l'application à quelqu'un
        // qui l'utilise depuis des mois serait le prendre pour un débutant.
        Text(
            "Revoir le tutoriel",
            color = c.accent,
            fontSize = 13.sp,
            modifier = Modifier
                .clickable {
                    portee.launch { app.preferences.setOnboardingDone(false) }
                }
                .padding(vertical = 6.dp)
        )
    }

    // Aperçu avant restauration.
    apercu?.let { a ->
        ModalBottomSheet(
            onDismissRequest = { apercu = null },
            containerColor = c.surface1
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)
            ) {
                Text("Restaurer une sauvegarde", color = c.textPrimary,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                when (val v = a.verdict) {
                    is SauvegardeEngine.Verdict.Refuse -> {
                        Text(v.raison, color = DangerRed, fontSize = 13.sp)
                    }

                    is SauvegardeEngine.Verdict.Utilisable -> {
                        Text("Créée le ${v.manifeste.creeLe.take(10)}",
                            color = c.textSecondary, fontSize = 12.sp)
                        Text("${a.nombreCartes} carte(s) de révision",
                            color = c.textSecondary, fontSize = 12.sp)

                        v.reserve?.let { r ->
                            Spacer(Modifier.height(10.dp))
                            Text(r, color = WarningAmber, fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(16.dp))
                        Text("À RESTAURER", color = c.textSecondary, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                        Spacer(Modifier.height(8.dp))

                        // Restauration partielle : on peut ne reprendre que ses
                        // mémos sans écraser un jardin plus avancé.
                        a.sections.forEach { section ->
                            val choisie = section in sections
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (choisie) c.accent.copy(alpha = 0.15f) else c.surface2
                                    )
                                    .border(
                                        1.dp,
                                        if (choisie) c.accent else c.border,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        sections = if (choisie) sections - section
                                        else sections + section
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (choisie) "☑" else "☐", fontSize = 15.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(section.libelle,
                                    color = if (choisie) c.accent else c.textSecondary,
                                    fontSize = 13.sp)
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Les mémos importés s'ajoutent aux tiens, ils ne les " +
                                "remplacent pas. Une sauvegarde de sécurité de ton " +
                                "profil actuel est écrite avant toute modification.",
                            color = c.textDisabled, fontSize = 11.sp
                        )

                        Spacer(Modifier.height(16.dp))
                        SankaiButton(
                            "Restaurer maintenant",
                            enabled = sections.isNotEmpty(),
                            onClick = {
                                val uri = fichierChoisi ?: return@SankaiButton
                                apercu = null
                                portee.launch {
                                    message = runCatching {
                                        depot.restaurer(uri, sections, null)
                                    }.fold(
                                        onSuccess = { faites ->
                                            "Restauré : " +
                                                faites.joinToString { s -> s.libelle }
                                        },
                                        onFailure = { e -> "Échec : ${e.message}" }
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
