package com.sankailife.ui.screens.garden

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sankailife.core.domain.engine.ArenaEngine
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.sankailife.core.garden.domain.ArrosoirEngine
import com.sankailife.core.garden.domain.DayNightEngine
import com.sankailife.core.garden.domain.ExpansionEngine
import com.sankailife.core.garden.domain.MoistureEngine
import com.sankailife.core.garden.domain.WeatherEngine
import com.sankailife.core.garden.domain.MemoChallengeEngine
import com.sankailife.core.garden.domain.MimoEngine
import androidx.compose.foundation.verticalScroll
import com.sankailife.core.garden.domain.OutilJardin
import com.sankailife.core.garden.domain.PlotState
import com.sankailife.core.garden.domain.Seed
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.*

/**
 * Le jardin.
 *
 * Écran non défilant : tout tient dans la hauteur, la grille absorbant
 * l'espace disponible via `weight`. Aucune dimension n'est figée en dur, pour
 * que la même mise en page tienne d'un petit téléphone à une tablette.
 *
 * Rendu volontairement géométrique — formes, dégradés et emojis. Les stades,
 * sols et espèces sont des catalogues séparés de l'affichage : substituer de
 * vraies illustrations ne demandera pas de retoucher la logique.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GardenScreen(viewModel: GardenViewModel, onBack: () -> Unit) {
    val c = MaterialTheme.sankaiColors
    val haptics = LocalHaptics.current

    val chargement by viewModel.chargement.collectAsState()
    val parcelles by viewModel.parcelles.collectAsState()
    val etat by viewModel.etat.collectAsState()
    val user by viewModel.user.collectAsState()
    val message by viewModel.message.collectAsState()
    val pretes by viewModel.nombrePretes.collectAsState()

    val caisses by viewModel.caisses.collectAsState()
    val stock by viewModel.stock.collectAsState()
    val valeurStock by viewModel.valeurStock.collectAsState()
    val phase by viewModel.phase.collectAsState()
    val magasinOuvert by viewModel.magasinOuvert.collectAsState()
    val meteo by viewModel.meteo.collectAsState()
    val aSoif by viewModel.parcellesASoif.collectAsState()
    val niveauArrosoir by viewModel.niveauArrosoir.collectAsState()

    val mimos by viewModel.mimos.collectAsState()
    val offres by viewModel.offres.collectAsState()
    val rapport by viewModel.rapportMimos.collectAsState()

    var selection by remember { mutableStateOf<GardenViewModel.ParcelleUi?>(null) }
    var marcheOuvert by remember { mutableStateOf(false) }
    var mimosOuvert by remember { mutableStateOf(false) }
    val defi by viewModel.defi.collectAsState()
    val outil by viewModel.outil.collectAsState()

    // Le rapport passe avant le défi souvenir : il explique un jardin qui a
    // changé tout seul, ce qui est plus déroutant qu'une question de révision.
    rapport?.let { texte ->
        FeuilleRapportMimos(texte = texte, onFermer = { viewModel.fermerRapport() })
    }

    if (mimosOuvert) {
        FeuilleMimos(
            mimos = mimos,
            offres = offres,
            pieces = user.coins,
            compost = etat.compost,
            onEmbaucher = { haptics.click(); viewModel.embaucher(it) },
            onFermer = { mimosOuvert = false }
        )
    }

    if (marcheOuvert) {
        FeuilleMarche(
            stock = stock,
            valeurTotale = valeurStock,
            ouvert = magasinOuvert,
            onVendre = { haptics.reward(); viewModel.vendre(it) },
            onVendreTout = { haptics.reward(); viewModel.vendreTout(); marcheOuvert = false },
            niveauArrosoir = niveauArrosoir,
            pieces = user.coins,
            onAmeliorerArrosoir = { haptics.click(); viewModel.ameliorerArrosoir() },
            onFermer = { marcheOuvert = false }
        )
    }

    defi?.let { d ->
        FeuilleDefiSouvenir(
            defi = d,
            onRepondre = { viewModel.repondreDefi(it) },
            onFermer = { viewModel.ignorerDefi() }
        )
    }

    selection?.let { parcelle ->
        FeuilleParcelle(
            parcelle = parcelle,
            graines = viewModel.grainesDisponibles(user.level),
            pieces = user.coins,
            eau = etat.eau,
            onNettoyer = { viewModel.nettoyer(parcelle.id); selection = null },
            onPlanter = { g -> viewModel.planter(parcelle.id, g); selection = null },
            onArroser = { viewModel.arroser(parcelle.id); selection = null },
            onRecolter = { haptics.reward(); viewModel.recolter(parcelle.id); selection = null },
            onDebloquer = { viewModel.debloquer(parcelle.id); selection = null },
            onFermer = { selection = null }
        )
    }

    if (chargement) {
        Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🌿", fontSize = 40.sp)
                Spacer(Modifier.height(12.dp))
                Text("Préparation du jardin…", color = c.textSecondary, fontSize = 13.sp)
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {

            // Bandeau : retour et ressources du jardin.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quitter le jardin", tint = c.textPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        ArenaEngine.areneActuelle(user.level).nom,
                        color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${phase.emoji} ${phase.libelle} • ${meteo.emoji} ${meteo.libelle}",
                        color = if (meteo.pleut) AccentCyan else c.textSecondary,
                        fontSize = 11.sp
                    )
                }
                Ressource("💧", "${etat.eau}", AccentCyan)
                Spacer(Modifier.width(6.dp))
                Ressource("🪙", "${user.coins}", CoinColor)
                Spacer(Modifier.width(6.dp))
                Ressource("🌱", "${etat.compost}", SuccessGreen)
            }

            // La grille prend tout l'espace restant : c'est ce qui rend
            // l'écran non défilant sans hauteur codée en dur.
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Arbre Sankai : symbole du niveau global, non cultivable.
                    ArbreSankai(niveau = user.level)

                    GrilleJardin(
                        parcelles = parcelles,
                        outil = outil,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onAppliquer = { viewModel.appliquerOutil(it) },
                        onOuvrirDetail = { selection = it }
                    )
                }

                // Voile nocturne, posé sur le terrain seul.
                //
                // Il ne couvre ni le bandeau ni les boutons : assombrir les
                // commandes rendrait l'application pénible à utiliser le soir,
                // qui est justement le moment où beaucoup l'ouvriront. Sans
                // modificateur de saisie, ce voile ne capte aucun geste.
                val nuit = DayNightEngine.intensiteNuit()
                if (nuit > 0f) {
                    Box(
                        Modifier.matchParentSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF060C1F).copy(alpha = nuit))
                    )
                }
            }

            // Barre d'outils : sélectionner puis glisser sur les parcelles.
            BarreOutils(
                outil = outil,
                graines = viewModel.grainesDisponibles(user.level),
                onChoisir = { haptics.click(); viewModel.choisirOutil(it) }
            )

            // Zone d'action unique.
            //
            // Une seule action principale à la fois, choisie dans l'ordre du
            // circuit : ranger avant de récolter, récolter avant de conseiller.
            // C'est ce qui empêche le joueur de laisser le terrain saturer
            // sans jamais comprendre pourquoi la récolte est refusée.
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                when {
                    caisses.isNotEmpty() -> SankaiButton(
                        "📦  Ranger ${caisses.size} caisse(s) au dépôt",
                        onClick = { haptics.click(); viewModel.rangerCaisses() },
                        modifier = Modifier.fillMaxWidth()
                    )

                    pretes > 0 -> SankaiButton(
                        "🧺  Tout récolter ($pretes)",
                        onClick = { haptics.reward(); viewModel.toutRecolter() },
                        modifier = Modifier.fillMaxWidth()
                    )

                    else -> Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(c.surface2)
                            .border(1.dp, c.border, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        // Une seule phrase, la plus utile du moment. Empiler
                        // météo, soif et rappel d'usage remplirait la place
                        // sans aider à décider.
                        Text(
                            when {
                                etat.eau <= 0 ->
                                    "Plus d'eau. Révise des flash cards pour en obtenir."
                                meteo.pleut -> WeatherEngine.message(meteo)
                                aSoif > 0 ->
                                    "$aSoif parcelle(s) auront soif d'ici ce soir."
                                else -> "Touche une parcelle pour planter, arroser ou récolter."
                            },
                            color = c.textSecondary, fontSize = 12.sp
                        )
                    }
                }

                // Accès permanent au dépôt : même vide, il indique où va la
                // récolte, ce qui évite de chercher ses légumes après coup.
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        BandeauDepot(
                            lignes = stock.size,
                            valeur = valeurStock,
                            ouvert = magasinOuvert,
                            onOuvrir = { marcheOuvert = true }
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        BandeauMimos(
                            effectif = mimos.size,
                            compost = etat.compost,
                            onOuvrir = { mimosOuvert = true }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = message.isNotBlank(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp)
        ) {
            Box(
                Modifier.padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surface3)
                    .border(1.dp, c.accent, RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 11.dp)
            ) {
                Text(message, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Bandeau du dépôt central.
 *
 * Affiche la valeur du stock au cours du jour, et si le marchand est là.
 * Reste visible même vide : c'est le repère qui explique où part la récolte.
 */
@Composable
private fun BandeauDepot(
    lignes: Int,
    valeur: Int,
    ouvert: Boolean,
    onOuvrir: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface2)
            .border(1.dp, if (ouvert && valeur > 0) AccentGold.copy(alpha = 0.5f) else c.border,
                RoundedCornerShape(14.dp))
            .clickable { onOuvrir() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (ouvert) "🏪" else "🌙", fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Dépôt central",
                color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                when {
                    lignes == 0 -> "Vide — récolte puis range tes caisses"
                    !ouvert -> "$lignes lot(s) en stock • marchand absent"
                    else -> "$lignes lot(s) • valeur $valeur 🪙"
                },
                color = if (ouvert && lignes > 0) CoinColor else c.textSecondary,
                fontSize = 11.sp
            )
        }
        Text("›", color = c.textSecondary, fontSize = 20.sp)
    }
}

/**
 * Fiche d'une case à acquérir, ou en chantier.
 *
 * Le prix et le temps sont annoncés avant l'achat, pas après : une extension
 * qu'on découvre longue une fois payée serait vécue comme un piège.
 */
@Composable
private fun FicheExtension(
    parcelle: GardenViewModel.ParcelleUi,
    pieces: Int,
    onDebloquer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val enChantier = parcelle.deblocage == ExpansionEngine.Deblocage.EN_CHANTIER

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (enChantier) "🚧" else parcelle.terrain.emoji, fontSize = 30.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                if (enChantier) "Chantier en cours" else parcelle.terrain.libelle,
                color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Text(parcelle.terrain.note, color = c.textSecondary, fontSize = 12.sp)
        }
    }

    Spacer(Modifier.height(16.dp))

    if (enChantier) {
        Text(
            "Encore ${formaterDuree(parcelle.minutesChantier)}. " +
                "Tu peux continuer à jouer ailleurs — le chantier avance même " +
                "application fermée.",
            color = c.textSecondary, fontSize = 13.sp
        )
        return
    }

    val duree = ExpansionEngine.dureeChantierMinutes(parcelle.id, parcelle.terrain)
    Text("Sol : ${parcelle.terrain.sol.emoji} ${parcelle.terrain.sol.libelle}",
        color = c.textSecondary, fontSize = 12.sp)
    Text("Défrichage : ${formaterDuree(duree)}", color = c.textSecondary, fontSize = 12.sp)
    if (parcelle.terrain.aNettoyer) {
        Text("Terrain encombré — il faudra le nettoyer après le chantier.",
            color = c.textDisabled, fontSize = 11.sp)
    }

    Spacer(Modifier.height(18.dp))
    val abordable = pieces >= parcelle.coutDeblocage
    SankaiButton(
        if (abordable) "Défricher • ${parcelle.coutDeblocage} 🪙"
        else "Il faut ${parcelle.coutDeblocage} 🪙",
        onClick = onDebloquer,
        enabled = abordable,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * État du sol.
 *
 * Affiché ici et pas en permanence sur la grille : une jauge sur chaque case
 * rendrait le terrain illisible. La couleur du sol suffit au coup d'œil, le
 * détail vient à la demande.
 */
@Composable
private fun FicheHumidite(parcelle: GardenViewModel.ParcelleUi) {
    val c = MaterialTheme.sankaiColors
    val etat = parcelle.etatHumidite
    val heures = MoistureEngine.heuresAvantSecheresse(parcelle.humidite, parcelle.sol)

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(MoistureEngine.teinteSol(parcelle.humidite)).copy(alpha = 0.35f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(etat.emoji, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${etat.libelle} • ${(parcelle.humidite * 100).toInt()} %",
                color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                if (heures <= 0f) "Le sol a besoin d'eau"
                else "Sec dans environ ${formaterDuree((heures * 60).toLong())}",
                color = c.textSecondary, fontSize = 11.sp
            )
        }
        parcelle.graine?.let {
            Text(it.besoinEau.libelle, color = c.textDisabled, fontSize = 10.sp)
        }
    }
}

/** Accès à la maison des Mimos. Le compost est affiché : c'est leur carburant. */
@Composable
private fun BandeauMimos(
    effectif: Int,
    compost: Int,
    onOuvrir: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val aFaim = effectif > 0 && compost <= 0

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface2)
            .border(1.dp, if (aFaim) AccentCyan.copy(alpha = 0.5f) else c.border,
                RoundedCornerShape(14.dp))
            .clickable { onOuvrir() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🏡", fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Mimos", color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    effectif == 0 -> "Personne — embauche"
                    aFaim -> "$effectif • plus de compost"
                    else -> "$effectif • $compost 🌱"
                },
                color = if (aFaim) AccentCyan else c.textSecondary,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Rapport de retour.
 *
 * Sans lui, l'automatisation serait invisible : le joueur retrouverait un
 * jardin différent sans savoir pourquoi, ce qui ressemble à un bug. Il ne
 * s'affiche que s'il y a quelque chose à dire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleRapportMimos(texte: String, onFermer: () -> Unit) {
    val c = MaterialTheme.sankaiColors

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text("TON JARDIN A CONTINUÉ", color = SuccessGreen, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Rapport des Mimos", color = c.textPrimary,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(texte, color = c.textSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            SankaiButton("Continuer", onClick = onFermer, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * La maison des Mimos.
 *
 * Le prix monte à chaque embauche du même métier : sans cela, la stratégie
 * optimale serait d'aligner dix arroseurs et de ne plus jamais ouvrir le jeu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleMimos(
    mimos: List<com.sankailife.core.garden.data.GardenMimoEntity>,
    offres: List<GardenViewModel.OffreMimo>,
    pieces: Int,
    compost: Int,
    onEmbaucher: (MimoEngine.Type) -> Unit,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("MAISON DES MIMOS", color = AccentCyan, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Ils travaillent en ton absence", color = c.textPrimary,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Chaque action coûte 1 🌱 de compost, produit par tes récoltes. " +
                "Ils dorment la nuit, comme le marchand.",
                color = c.textSecondary, fontSize = 12.sp
            )

            if (mimos.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Ton équipe • compost : $compost 🌱",
                    color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    mimos.forEach { mimo ->
                        val type = MimoEngine.Type.parNom(mimo.type)
                        Column(
                            Modifier.clip(RoundedCornerShape(11.dp))
                                .background(c.surface2)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(type?.emoji ?: "🙂", fontSize = 18.sp)
                            Text(mimo.nom, color = c.textPrimary, fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Embaucher", color = c.textSecondary, fontSize = 11.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            offres.forEach { offre ->
                val abordable = pieces >= offre.prix
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface2)
                        .border(1.dp, c.border, RoundedCornerShape(12.dp))
                        .clickable(enabled = abordable) { onEmbaucher(offre.type) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(offre.type.emoji, fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            offre.type.libelle +
                                if (offre.employes > 0) " ×${offre.employes}" else "",
                            color = if (abordable) c.textPrimary else c.textSecondary,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(offre.type.role, color = c.textSecondary, fontSize = 11.sp)
                    }
                    Text("${offre.prix} 🪙",
                        color = if (abordable) CoinColor else c.textDisabled,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Le marché.
 *
 * Le cours varie d'un jour à l'autre, et il est affiché : vendre au bon moment
 * doit être une décision lisible, pas une loterie invisible. Hors des heures
 * d'ouverture, le stock reste consultable mais la vente est bloquée — on ne
 * cache jamais ce que le joueur possède.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleMarche(
    stock: List<GardenViewModel.LigneStock>,
    valeurTotale: Int,
    ouvert: Boolean,
    onVendre: (GardenViewModel.LigneStock) -> Unit,
    onVendreTout: () -> Unit,
    niveauArrosoir: Int,
    pieces: Int,
    onAmeliorerArrosoir: () -> Unit,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text(
                if (ouvert) "MARCHÉ OUVERT" else "MARCHÉ FERMÉ",
                color = if (ouvert) SuccessGreen else c.textDisabled,
                fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(8.dp))
            Text("Dépôt central", color = c.textPrimary,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                if (ouvert)
                    "Ouvert de ${DayNightEngine.OUVERTURE_MAGASIN} h à ${DayNightEngine.FERMETURE_MAGASIN} h."
                else DayNightEngine.messageMagasinFerme(),
                color = c.textSecondary, fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            if (stock.isEmpty()) {
                Text(
                    "Le dépôt est vide. Récolte tes cultures, puis range les caisses.",
                    color = c.textSecondary, fontSize = 13.sp
                )
            } else {
                stock.forEach { ligne ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.surface2)
                            .border(1.dp, c.border, RoundedCornerShape(12.dp))
                            .clickable(enabled = ouvert) { onVendre(ligne) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(ligne.graine.emoji, fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${ligne.graine.nom} × ${ligne.quantite}",
                                color = c.textPrimary, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${ligne.qualite.libelle} • ${ligne.prixUnitaire} 🪙 pièce",
                                color = c.textSecondary, fontSize = 11.sp
                            )
                        }
                        Text(
                            "${ligne.total} 🪙",
                            color = if (ouvert) CoinColor else c.textDisabled,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                SankaiButton(
                    if (ouvert) "Tout vendre • $valeurTotale 🪙" else "Marchand absent",
                    onClick = onVendreTout,
                    enabled = ouvert,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // L'atelier vit chez le marchand plutôt que dans une barre d'outils
            // de plus : on y vient déjà pour vendre, et c'est là qu'on a les
            // pièces en tête.
            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = c.border)
            Spacer(Modifier.height(16.dp))

            Text("ATELIER", color = AccentGold, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(10.dp))

            val cout = ArrosoirEngine.coutAmelioration(niveauArrosoir)
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surface2)
                    .border(1.dp, c.border, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💧", fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ArrosoirEngine.libelle(niveauArrosoir),
                        color = c.textPrimary, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        if (cout == null) "Niveau maximum atteint"
                        else ArrosoirEngine.description(niveauArrosoir + 1),
                        color = c.textSecondary, fontSize = 11.sp
                    )
                }
            }

            if (cout != null) {
                Spacer(Modifier.height(8.dp))
                SankaiButton(
                    "Améliorer • $cout 🪙",
                    onClick = onAmeliorerArrosoir,
                    enabled = ouvert && pieces >= cout,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Un meilleur arrosoir couvre plus de cases d'un geste. " +
                        "Il ne crée pas d'eau : chaque parcelle arrosée coûte " +
                        "toujours une unité.",
                    color = c.textDisabled, fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Défi souvenir : reconnaître la phrase reçue en notification.
 *
 * Présenté à l'ouverture du jardin, une seule fois par notification. Il dure
 * une dizaine de secondes — c'est une micro-révision, pas un examen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleDefiSouvenir(
    defi: MemoChallengeEngine.Defi,
    onRepondre: (String) -> Unit,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text("SOUVENIR DU JOUR", color = AccentCyan, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Quelle phrase as-tu reçue ?", color = c.textPrimary,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Module : ${defi.nomModule}", color = c.textSecondary, fontSize = 12.sp)

            Spacer(Modifier.height(16.dp))
            defi.options.forEach { option ->
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface2)
                        .border(1.dp, c.border, RoundedCornerShape(12.dp))
                        .clickable { onRepondre(option) }
                        .padding(14.dp)
                ) {
                    Text(option, color = c.textPrimary, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Se tromper ne retire rien. Le défi reviendra à la prochaine notification.",
                color = c.textDisabled, fontSize = 11.sp
            )
        }
    }
}

/**
 * Barre d'outils du jardin.
 *
 * Un seul appui sélectionne, un second repose l'outil. Le glissement sur la
 * grille fait le reste : c'est ce qui permet de semer six cases d'un geste
 * sans jamais confirmer.
 */
@Composable
private fun BarreOutils(
    outil: OutilJardin?,
    graines: List<Seed>,
    onChoisir: (OutilJardin?) -> Unit
) {
    val c = MaterialTheme.sankaiColors

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(OutilJardin.Arrosoir, OutilJardin.Panier, OutilJardin.Pioche).forEach { o ->
                BoutonOutil(o.emoji, o.libelle, outil == o) { onChoisir(o) }
            }
            graines.forEach { graine ->
                val g = OutilJardin.Graine(graine)
                BoutonOutil(
                    graine.emoji,
                    "${graine.prixPieces} 🪙",
                    outil is OutilJardin.Graine && outil.seed.id == graine.id
                ) { onChoisir(g) }
            }
        }

        if (outil == null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Glisse sur le terrain pour te déplacer, ou choisis un outil.",
                color = c.textDisabled, fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun BoutonOutil(
    emoji: String,
    libelle: String,
    actif: Boolean,
    onClic: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    Column(
        Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (actif) c.accent.copy(alpha = 0.18f) else c.surface2)
            .border(
                if (actif) 1.5.dp else 1.dp,
                if (actif) c.accent else c.border,
                RoundedCornerShape(13.dp)
            )
            .clickable { onClic() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 19.sp)
        Text(
            libelle,
            color = if (actif) c.accent else c.textSecondary,
            fontSize = 9.sp,
            fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun Ressource(emoji: String, valeur: String, couleur: Color) {
    val c = MaterialTheme.sankaiColors
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(c.surface2)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 11.sp)
        Spacer(Modifier.width(3.dp))
        Text(valeur, color = couleur, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Arbre central : son apparence suit l'arène atteinte. */
@Composable
private fun ArbreSankai(niveau: Int) {
    val c = MaterialTheme.sankaiColors
    val arene = ArenaEngine.areneActuelle(niveau)
    val taille = (26 + arene.id * 3).sp

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B1A12))
            .border(1.dp, Color(0xFF2B4A34), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(arene.emoji, fontSize = taille)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Arbre Sankai", color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Stade ${arene.id} • niveau $niveau", color = c.textSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun Parcelle(
    parcelle: GardenViewModel.ParcelleUi,
    modifier: Modifier = Modifier,
    onClic: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    // Seule une parcelle prête est animée : si tout bougeait, plus rien
    // n'attirerait le regard.
    val transition = rememberInfiniteTransition(label = "parcelle")
    val pulse by transition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulseParcelle"
    )

    val (fond, bordure) = when (parcelle.etat) {
        PlotState.LOCKED -> Color(0xFF1A1D1B) to c.border
        PlotState.UNCLEARED -> Color(0xFF2A2622) to Color(0xFF4A413A)
        PlotState.READY_TO_HARVEST -> Color(0xFF3B2F16) to AccentGold.copy(alpha = pulse)
        else -> Color(0xFF3A2A1C) to Color(0xFF6B4B30)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(fond)
            .border(if (parcelle.prete) 2.dp else 1.dp, bordure, RoundedCornerShape(14.dp))
            .clickable { onClic() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (parcelle.etat) {
                    PlotState.LOCKED -> "🔒"
                    PlotState.UNCLEARED -> "🪨"
                    PlotState.EMPTY, PlotState.PREPARED -> "＋"
                    else -> parcelle.stage?.emoji ?: "🌱"
                },
                fontSize = 24.sp,
                color = if (parcelle.etat == PlotState.EMPTY) c.textSecondary else Color.Unspecified
            )

            val libelle = when {
                parcelle.etat == PlotState.LOCKED -> "Arène ${parcelle.areneRequise}"
                parcelle.etat == PlotState.UNCLEARED -> "Nettoyer"
                parcelle.etat == PlotState.EMPTY -> "Planter"
                parcelle.prete -> "Prêt"
                parcelle.besoinEau -> "Assoiffée"
                else -> formaterDuree(parcelle.minutesRestantes)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                libelle,
                color = when {
                    parcelle.prete -> AccentGold
                    parcelle.besoinEau -> AccentCyan
                    else -> c.textSecondary
                },
                fontSize = 9.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formaterDuree(minutes: Long): String = when {
    minutes <= 0 -> "Prêt"
    minutes < 60 -> "${minutes} min"
    else -> "${minutes / 60} h ${minutes % 60}"
}

/** Actions possibles sur une parcelle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleParcelle(
    parcelle: GardenViewModel.ParcelleUi,
    graines: List<Seed>,
    pieces: Int,
    eau: Int,
    onNettoyer: () -> Unit,
    onPlanter: (Seed) -> Unit,
    onArroser: () -> Unit,
    onRecolter: () -> Unit,
    onDebloquer: () -> Unit,
    onFermer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors

    ModalBottomSheet(onDismissRequest = onFermer, containerColor = c.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {

            // Une case pas encore acquise ne parle que d'acquisition : ses
            // options de culture n'auraient aucun sens tant qu'elle n'est pas
            // à nous.
            if (!parcelle.cultivable) {
                FicheExtension(parcelle = parcelle, pieces = pieces, onDebloquer = onDebloquer)
                return@Column
            }

            FicheHumidite(parcelle)
            Spacer(Modifier.height(16.dp))

            when (parcelle.etat) {
                PlotState.LOCKED -> {
                    Text("Parcelle verrouillée", color = c.textPrimary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                PlotState.UNCLEARED -> {
                    Text("Terrain encombré", color = c.textPrimary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Des pierres bloquent cette parcelle. " +
                        "Nettoyage : ${com.sankailife.core.garden.data.GardenRepository.COUT_NETTOYAGE} 🪙",
                        color = c.textSecondary, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    SankaiButton(
                        "Nettoyer",
                        onClick = onNettoyer,
                        enabled = pieces >= com.sankailife.core.garden.data.GardenRepository.COUT_NETTOYAGE,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                PlotState.EMPTY, PlotState.PREPARED -> {
                    Text("Planter une graine", color = c.textPrimary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Sol : ${parcelle.sol.emoji} ${parcelle.sol.libelle}",
                        color = c.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))

                    val compatibles = graines.filter { it.solRequis == parcelle.sol }
                    if (compatibles.isEmpty()) {
                        Text(
                            "Aucune graine compatible avec ce sol pour l'instant.",
                            color = c.textSecondary, fontSize = 13.sp
                        )
                    } else {
                        compatibles.forEach { graine ->
                            val abordable = pieces >= graine.prixPieces
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.surface2)
                                    .border(1.dp, c.border, RoundedCornerShape(12.dp))
                                    .clickable(enabled = abordable) { onPlanter(graine) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(graine.emoji, fontSize = 22.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(graine.nom,
                                        color = if (abordable) c.textPrimary else c.textSecondary,
                                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${graine.dureeMinutes / 60} h • rapporte ${graine.rendementPieces} 🪙",
                                        color = c.textSecondary, fontSize = 11.sp
                                    )
                                }
                                Text("${graine.prixPieces} 🪙",
                                    color = if (abordable) CoinColor else c.textDisabled,
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                else -> {
                    val graine = parcelle.graine
                    Text(graine?.nom ?: "Culture", color = c.textPrimary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        parcelle.stage?.libelle.orEmpty() +
                        if (parcelle.enRepos) " • en repos" else "",
                        color = c.textSecondary, fontSize = 12.sp
                    )

                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { parcelle.progression },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (parcelle.prete) AccentGold else SuccessGreen,
                        trackColor = c.surface3
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (parcelle.prete) "Prête à récolter"
                        else "Encore ${formaterDuree(parcelle.minutesRestantes)}",
                        color = c.textSecondary, fontSize = 12.sp
                    )

                    if (parcelle.enRepos) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Cette plante était en repos. Elle n'a rien perdu : " +
                            "arrose-la pour relancer sa croissance.",
                            color = c.textDisabled, fontSize = 11.sp
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    if (parcelle.prete) {
                        SankaiButton("🧺  Récolter", onClick = onRecolter,
                            modifier = Modifier.fillMaxWidth())
                    } else {
                        SankaiButton(
                            if (eau > 0) "💧  Arroser" else "Plus d'eau",
                            onClick = onArroser,
                            enabled = eau > 0,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
