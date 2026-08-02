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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import com.sankailife.core.modules.BibliothequeLocale
import com.sankailife.core.modules.FormatImportEngine
import com.sankailife.core.modules.ModuleRepository
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.DangerRed
import com.sankailife.ui.theme.SuccessGreen
import com.sankailife.ui.theme.sankaiColors
import kotlinx.coroutines.launch

/**
 * La bibliothèque locale : ce qui est déjà sur l'appareil.
 *
 * **Elle remplace un catalogue qui téléchargeait depuis GitHub**, et le
 * remplacement corrige une erreur de conception : tout le contenu tient en
 * 55 kilo-octets, soit un centième de l'application. Le télécharger imposait
 * une connexion, une liste d'hôtes autorisés, une vérification d'empreinte et
 * un message d'erreur hors-ligne — pour livrer ce qui aurait pu être déjà là.
 *
 * Rien ici ne touche au réseau. En mode avion, l'écran est identique.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibliothequeModules(
    nomsInstalles: Set<String>,
    onFermer: () -> Unit,
    onInstalle: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val contexte = LocalContext.current
    val app = contexte.applicationContext as SankaiApplication
    val portee = rememberCoroutineScope()
    val bibliotheque = remember { BibliothequeLocale(contexte, app.database) }
    val depotModules = remember { ModuleRepository(contexte, app.database) }

    var contenu by remember { mutableStateOf(BibliothequeLocale.Bibliotheque()) }
    var enCours by remember { mutableStateOf<String?>(null) }
    var avis by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var collageOuvert by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { contenu = bibliotheque.lire() }

    fun installer(fiche: BibliothequeLocale.Fiche) {
        enCours = fiche.id
        avis = null
        portee.launch {
            when (val r = bibliotheque.installer(fiche)) {
                is BibliothequeLocale.Issue.Ok -> { avis = r.message to true; onInstalle() }
                is BibliothequeLocale.Issue.Echec -> avis = r.raison to false
            }
            enCours = null
        }
    }

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Bibliothèque", color = c.textPrimary, fontSize = 18.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Livrée avec l'application. Rien à télécharger, rien à connecter.",
                color = c.textSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(16.dp))

            if (contenu.collections.isNotEmpty()) {
                Text("PARCOURS COMPLETS", color = c.textSecondary,
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                contenu.collections.forEach { fiche ->
                    Ligne(
                        fiche = fiche,
                        installe = false,
                        occupe = enCours != null,
                        enCours = enCours == fiche.id,
                        libelle = "Tout installer",
                        onInstaller = { installer(fiche) }
                    )
                    HorizontalDivider(color = c.border)
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Ou un niveau seul, si tu n'en veux qu'un :",
                    color = c.textSecondary, fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
            }

            contenu.modules.forEach { fiche ->
                Ligne(
                    fiche = fiche,
                    installe = nomsInstalles.any { it.trim().equals(fiche.nom.trim(), true) },
                    occupe = enCours != null,
                    enCours = enCours == fiche.id,
                    libelle = "Installer",
                    onInstaller = { installer(fiche) }
                )
                HorizontalDivider(color = c.border)
            }

            Spacer(Modifier.height(20.dp))
            SankaiButton(
                "📋  Coller des cartes",
                onClick = { collageOuvert = true },
                secondary = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Depuis un tableur, un export Anki, un carnet de notes — colle le " +
                    "texte, l'application reconnaît le format.",
                color = c.textDisabled, fontSize = 11.sp
            )

            avis?.let { (texte, succes) ->
                Spacer(Modifier.height(10.dp))
                Text(texte, fontSize = 12.sp, color = if (succes) SuccessGreen else DangerRed)
            }
        }
    }

    if (collageOuvert) {
        CollageCartes(
            depot = depotModules,
            onFermer = { collageOuvert = false },
            onInstalle = { message ->
                avis = message to true
                collageOuvert = false
                onInstalle()
            }
        )
    }
}

@Composable
private fun Ligne(
    fiche: BibliothequeLocale.Fiche,
    installe: Boolean,
    occupe: Boolean,
    enCours: Boolean,
    libelle: String,
    onInstaller: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.fillMaxWidth(0.62f)) {
            Text(
                fiche.nom, color = c.textPrimary, fontSize = 14.sp,
                fontWeight = if (fiche.estCollection) FontWeight.Bold else FontWeight.SemiBold
            )
            Text(fiche.details, color = c.textSecondary, fontSize = 12.sp)
            if (fiche.description.isNotBlank()) {
                Text(fiche.description, color = c.textDisabled, fontSize = 11.sp)
            }
            if (installe) {
                Text("Déjà installé", color = SuccessGreen, fontSize = 11.sp)
            }
        }
        SankaiButton(
            text = if (enCours) "…" else if (installe) "Réinstaller" else libelle,
            onClick = onInstaller,
            enabled = !occupe,
            secondary = installe,
            small = true
        )
    }
}

/**
 * Import par collage.
 *
 * Le chemin le plus court entre « j'ai mes cartes quelque part » et « elles
 * sont dans l'application ». Pas de fichier à enregistrer, pas de
 * gestionnaire de fichiers à traverser : on colle, on nomme, c'est fait.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollageCartes(
    depot: ModuleRepository,
    onFermer: () -> Unit,
    onInstalle: (String) -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val portee = rememberCoroutineScope()
    var texte by remember { mutableStateOf("") }
    var nom by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf("") }

    // Analyse à la frappe : voir le nombre de cartes bouger pendant qu'on colle
    // dit tout de suite si le format a été compris.
    val format = remember(texte) {
        if (texte.isBlank()) FormatImportEngine.Format.INCONNU
        else FormatImportEngine.detecter(texte.toByteArray(), "")
    }
    val cartes = remember(texte, format) {
        when (format) {
            FormatImportEngine.Format.CSV -> FormatImportEngine.lireCsv(texte)
            FormatImportEngine.Format.INCONNU -> emptyList()
            else -> FormatImportEngine.lireTexte(texte)
        }
    }

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Coller des cartes", color = c.textPrimary, fontSize = 18.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Une carte par ligne. Séparateur : | ou :: ou une tabulation, " +
                    "ou une virgule si ça vient d'un tableur.",
                color = c.textSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = texte,
                onValueChange = { texte = it; erreur = "" },
                modifier = Modifier.fillMaxWidth().height(180.dp),
                placeholder = { Text("Olá | Bonjour\nObrigado | Merci") },
                label = { Text("Contenu") }
            )
            Spacer(Modifier.height(10.dp))

            if (cartes.isNotEmpty()) {
                Text(
                    "${cartes.size} carte(s) reconnue(s)" +
                        if (format == FormatImportEngine.Format.CSV) " — tableur" else "",
                    color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                // Les premières lignes, telles qu'elles seront enregistrées :
                // c'est le seul moyen de voir qu'un séparateur a été mal deviné
                // avant que trois cents cartes soient fausses.
                cartes.take(4).forEach {
                    Text(it, color = c.textSecondary, fontSize = 11.sp)
                }
                if (cartes.size > 4) {
                    Text("…", color = c.textDisabled, fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = nom,
                    onValueChange = { nom = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nom du module") }
                )
            } else if (texte.isNotBlank()) {
                Text(
                    "Aucune carte reconnue. Vérifie qu'il y a bien une carte par ligne.",
                    color = DangerRed, fontSize = 12.sp
                )
            }

            if (erreur.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(erreur, color = DangerRed, fontSize = 12.sp)
            }

            Spacer(Modifier.height(14.dp))
            SankaiButton(
                "Créer le module",
                onClick = {
                    portee.launch {
                        runCatching {
                            depot.installer(
                                com.sankailife.core.modules.ModuleEngine.Manifeste(
                                    schemaVersion = 1,
                                    id = "colle-${System.currentTimeMillis()}",
                                    nom = nom.ifBlank { "Cartes collées" },
                                    version = "1.0.0",
                                    auteur = "",
                                    licence = ""
                                ),
                                cartes
                            )
                        }.onSuccess {
                            onInstalle("« $it » créé — ${cartes.size} cartes.")
                        }.onFailure {
                            erreur = it.message ?: "Création impossible."
                        }
                    }
                },
                enabled = cartes.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
