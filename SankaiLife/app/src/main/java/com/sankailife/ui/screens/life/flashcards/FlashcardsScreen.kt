package com.sankailife.ui.screens.life.flashcards

import androidx.compose.animation.AnimatedVisibility
import com.sankailife.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.sankailife.SankaiApplication
import com.sankailife.core.audio.rememberVoix
import com.sankailife.core.domain.engine.ExerciceEngine
import com.sankailife.core.domain.engine.FlashcardEngine
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import com.sankailife.core.haptics.LocalHaptics
import com.sankailife.ui.components.SankaiButton
import com.sankailife.ui.theme.AccentCyan
import com.sankailife.ui.theme.AccentGold
import com.sankailife.ui.theme.AccentViolet
import com.sankailife.ui.theme.DangerRed
import com.sankailife.ui.theme.SuccessGreen
import com.sankailife.ui.theme.sankaiColors

@Composable
fun FlashcardsScreen(
    profileId: Long,
    viewModel: FlashcardsViewModel,
    onBack: () -> Unit,
    /**
     * Unite du parcours, quand la session est guidee.
     *
     * Vide pour une revision libre : c'est alors la liste des cartes dues qui
     * fait la session, comme avant.
     */
    uniteId: String = ""
) {
    val etat by viewModel.etat.collectAsState()
    val c = MaterialTheme.sankaiColors
    val haptics = LocalHaptics.current
    // Voix du système, libérée automatiquement à la sortie de l'écran.
    val voix = rememberVoix()

    // Reglages d'ecoute, relus en continu : couper la lecture automatique doit
    // prendre effet a la carte suivante, pas au prochain lancement.
    val prefs = (LocalContext.current.applicationContext as SankaiApplication).preferences
    val lectureAuto by prefs.lectureAuto.collectAsState(initial = true)
    val vitesseVoix by prefs.vitesseVoix.collectAsState(initial = "normale")
    val repetitions by prefs.repetitionsVoix.collectAsState(initial = 0)

    LaunchedEffect(profileId, uniteId) {
        viewModel.demarrer(profileId, uniteId.ifBlank { null })
    }

    /**
     * Ce qu'on prononce : la question, jamais la reponse attendue.
     *
     * Lire le verso avant que l'apprenant reponde donnerait la solution. Le
     * recto est le mot etranger dans le sens habituel des modules, donc
     * l'entendre est exactement ce qui manque — et ne revele rien.
     */
    val aPrononcer = etat.carteCourante?.recto.orEmpty()
    val langueCarte = etat.carteCourante?.langue.orEmpty()
    val peutEcouter = remember(langueCarte, voix.pret) {
        langueCarte.isNotBlank() && voix.disponiblePour(langueCarte)
    }

    // Lecture automatique du nouveau mot.
    //
    // La cle est l'index de la session, pas le texte : deux cartes identiques
    // dans une meme session doivent etre lues chacune leur tour, et une simple
    // recomposition ne doit rien relancer. C'est le defaut classique de ce
    // genre d'effet — l'audio qui repart a chaque frappe au clavier.
    LaunchedEffect(etat.index, lectureAuto, peutEcouter, aPrononcer) {
        if (!lectureAuto || !peutEcouter || aPrononcer.isBlank()) return@LaunchedEffect
        voix.dire(aPrononcer, langueCarte, vitesseVoix)
        repeat(repetitions) {
            kotlinx.coroutines.delay(1_400)
            voix.dire(aPrononcer, langueCarte, vitesseVoix)
        }
    }

    val progression by animateFloatAsState(etat.progression, label = "progression")

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
    ) {
        // En-tête
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = c.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("🃏 Révision", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(etat.nomModule, color = c.textSecondary, fontSize = 12.sp)
            }
            if (!etat.terminee && etat.total > 0) {
                Text(
                    "${etat.index + 1} / ${etat.total}",
                    color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(12.dp))
            }
        }

        LinearProgressIndicator(
            progress = { progression },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = AccentViolet,
            trackColor = c.surface3
        )

        when {
            etat.chargement -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent)
            }

            etat.terminee -> EcranFin(
                reussies = etat.reussies,
                ratees = etat.ratees,
                message = etat.messageFin,
                onRejouer = { viewModel.rejouer() },
                onBack = onBack
            )

            else -> {
                val carte = etat.carteCourante
                if (carte == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Rien à réviser", color = c.textSecondary)
                    }
                } else if (etat.association != null) {
                    // L'association occupe l'ecran entier : elle porte quatre
                    // cartes et ne se corrige pas comme les autres, donc rien de
                    // la barre de reponse habituelle ne s'y applique.
                    Column(
                        Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(12.dp))
                        ExerciceAssociation(
                            etat = etat.association!!,
                            onToucher = { viewModel.toucherAssociation(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(12.dp))

                        val exercice = etat.exercice

                        // La saisie et l'ordre des mots sont remis à zéro à
                        // chaque carte : la clé de `remember` porte l'index,
                        // sinon la réponse précédente resterait affichée.
                        var saisie by remember(etat.index) { mutableStateOf("") }
                        var assemblage by remember(etat.index) {
                            mutableStateOf(listOf<String>())
                        }

                        // Le glissement n'est proposé que sur la carte mémoire,
                        // la seule que la machine ne sait pas corriger. Sur un
                        // QCM ou une saisie, laisser glisser reviendrait à
                        // proposer de contredire une correction objective.
                        CarteGlissable(
                            actif = exercice is ExerciceEngine.Exercice.Memoire &&
                                etat.correction == null,
                            onJuger = { viewModel.repondre(it) },
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp))
                                .background(c.surface2)
                                .border(
                                    1.dp,
                                    when (etat.correction) {
                                        true -> SuccessGreen.copy(alpha = 0.6f)
                                        false -> DangerRed.copy(alpha = 0.6f)
                                        null -> c.border
                                    },
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(20.dp)
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (exercice != null) {
                                    Text(
                                        exercice.consigne.uppercase(),
                                        color = AccentCyan, fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp
                                    )
                                    Spacer(Modifier.height(14.dp))
                                }

                                when (exercice) {
                                    is ExerciceEngine.Exercice.Reconnaissance -> {
                                        Text(
                                            exercice.question, color = c.textPrimary,
                                            fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(18.dp))
                                        exercice.options.forEach { option ->
                                            OptionExercice(
                                                texte = option,
                                                actif = etat.enAttenteDeValidation,
                                                juste = etat.correction != null &&
                                                        option == exercice.attendu
                                            ) {
                                                haptics.click(); viewModel.valider(option)
                                            }
                                        }
                                    }

                                    is ExerciceEngine.Exercice.TexteATrous -> {
                                        Text(
                                            listOf(exercice.avant, "____", exercice.apres)
                                                .filter { it.isNotBlank() }.joinToString(" "),
                                            color = c.textPrimary, fontSize = 18.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(18.dp))
                                        ChampReponse(
                                            valeur = saisie,
                                            actif = etat.enAttenteDeValidation,
                                            onChange = { saisie = it },
                                            onValider = { viewModel.valider(saisie) }
                                        )
                                    }

                                    is ExerciceEngine.Exercice.Saisie -> {
                                        Text(
                                            exercice.question, color = c.textPrimary,
                                            fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(18.dp))
                                        ChampReponse(
                                            valeur = saisie,
                                            actif = etat.enAttenteDeValidation,
                                            onChange = { saisie = it },
                                            onValider = { viewModel.valider(saisie) }
                                        )
                                    }

                                    is ExerciceEngine.Exercice.Ordre -> {
                                        if (exercice.question.isNotBlank()) {
                                            Text(
                                                exercice.question, color = c.textPrimary,
                                                fontSize = 17.sp, textAlign = TextAlign.Center
                                            )
                                            Spacer(Modifier.height(14.dp))
                                        }
                                        Text(
                                            assemblage.joinToString(" ").ifBlank { "…" },
                                            color = AccentGold, fontSize = 17.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(14.dp))
                                        FlowMorceaux(
                                            morceaux = exercice.morceaux,
                                            utilises = assemblage,
                                            actif = etat.enAttenteDeValidation,
                                            onChoisir = { mot -> assemblage = assemblage + mot },
                                            onEffacer = { assemblage = assemblage.dropLast(1) }
                                        )
                                    }

                                    else -> {
                                        Text(
                                            carte.recto, color = c.textPrimary,
                                            fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                // Écouter, une fois la réponse donnée.
                                //
                                // Pas avant : un exercice à trous masque une
                                // partie de la phrase, et la faire prononcer
                                // livrerait la réponse. Après correction, il
                                // n'y a plus rien à protéger, et c'est le
                                // moment où entendre la prononciation sert.
                                //
                                // Le bouton n'apparaît que si le téléphone a
                                // vraiment une voix pour cette langue : un
                                // bouton muet passerait pour une panne.
                                // Disponible **avant** de repondre, et c'est le
                                // correctif.
                                //
                                // Le bouton n'apparaissait qu'apres correction,
                                // par crainte de donner la reponse. Mais on
                                // prononce le recto — le mot etranger, c'est-a-dire
                                // la question. L'entendre avant de repondre est
                                // precisement ce qu'on vient chercher dans un
                                // module de langue.
                                if (peutEcouter) {
                                    Spacer(Modifier.height(14.dp))
                                    Row {
                                        TextButton(onClick = {
                                            haptics.click()
                                            voix.dire(carte.recto, carte.langue, vitesseVoix)
                                        }) {
                                            Text("🔊  Écouter", color = AccentCyan, fontSize = 13.sp)
                                        }
                                        TextButton(onClick = {
                                            haptics.click()
                                            voix.dire(carte.recto, carte.langue, "lente")
                                        }) {
                                            Text("🐢  Lentement", color = AccentCyan, fontSize = 13.sp)
                                        }
                                    }
                                }

                                // Après une erreur, la bonne réponse est
                                // montrée : c'est le seul moment où l'on
                                // apprend vraiment quelque chose.
                                etat.reponseAttendue?.let { attendu ->
                                    Spacer(Modifier.height(16.dp))
                                    HorizontalDivider(color = c.border)
                                    Spacer(Modifier.height(12.dp))
                                    Text("La réponse était", color = c.textSecondary, fontSize = 11.sp)
                                    Text(
                                        attendu, color = AccentGold, fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        }

                        Spacer(Modifier.height(14.dp))

                        when {
                            // Exercice corrigé : on avance, la boîte suit le
                            // verdict de la machine et non l'avis du joueur.
                            // Une carte corrigée par la machine n'a plus besoin
                            // d'être jugée : le verdict est tombé. Le geste
                            // n'est proposé que sur la carte mémoire, la seule
                            // que personne ne peut corriger à ta place.
                            etat.correction != null -> {
                                val juste = etat.correction == true
                                Text(
                                    if (juste)
                                        "Juste • prochaine révision " +
                                            FlashcardEngine.libelleIntervalle(
                                                FlashcardEngine.boiteSuivante(carte.box, true)
                                            )
                                    else "Cette carte reviendra bientôt",
                                    color = if (juste) SuccessGreen else c.textSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                SankaiButton(
                                    "Continuer",
                                    onClick = {
                                        if (juste) haptics.success() else haptics.error()
                                        viewModel.repondre(juste)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // La reconnaissance se valide en touchant une
                            // option : pas de bouton supplémentaire.
                            exercice is ExerciceEngine.Exercice.Reconnaissance -> Unit

                            exercice is ExerciceEngine.Exercice.Ordre -> SankaiButton(
                                "Valider",
                                onClick = { viewModel.valider(assemblage.joinToString(" ")) },
                                enabled = assemblage.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            exercice is ExerciceEngine.Exercice.Memoire -> {
                                // Rien à corriger : c'est à l'utilisateur de se
                                // juger. Le glissement porte une nuance que deux
                                // boutons ne peuvent pas exprimer — entre
                                // « péniblement » et « les yeux fermés », il y a
                                // deux intervalles très différents.
                                //
                                // Les boutons restent là : un système qui
                                // n'obéirait qu'au geste exclurait qui ne peut
                                // pas glisser précisément.
                                Column {
                                    AideGestes()
                                    Spacer(Modifier.height(10.dp))
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        BoutonReponse(stringResource(R.string.cards_judge_again), DangerRed, Modifier.weight(1f)) {
                                            haptics.error()
                                            viewModel.repondre(FlashcardEngine.Jugement.A_REVOIR)
                                        }
                                        BoutonReponse(stringResource(R.string.cards_knew_it), SuccessGreen, Modifier.weight(1f)) {
                                            haptics.success()
                                            viewModel.repondre(FlashcardEngine.Jugement.CORRECT)
                                        }
                                    }
                                }
                            }

                            else -> SankaiButton(
                                "Valider",
                                onClick = { viewModel.valider(saisie) },
                                enabled = saisie.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/** Une proposition de QCM. Après validation, la bonne réponse s'éclaire. */
@Composable
private fun OptionExercice(
    texte: String,
    actif: Boolean,
    juste: Boolean,
    onClic: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    Box(
        Modifier.fillMaxWidth().padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (juste) SuccessGreen.copy(alpha = 0.18f) else c.surface3)
            .border(
                1.dp,
                if (juste) SuccessGreen.copy(alpha = 0.6f) else c.border,
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = actif) { onClic() }
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texte,
            color = if (juste) SuccessGreen else c.textPrimary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

/** Champ de réponse écrite. La touche entrée valide, comme au clavier. */
@Composable
private fun ChampReponse(
    valeur: String,
    actif: Boolean,
    onChange: (String) -> Unit,
    onValider: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    OutlinedTextField(
        value = valeur,
        onValueChange = onChange,
        enabled = actif,
        singleLine = true,
        placeholder = { Text("Ta réponse", color = c.textDisabled, fontSize = 14.sp) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onValider() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.textPrimary,
            unfocusedTextColor = c.textPrimary,
            disabledTextColor = c.textSecondary,
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.border,
            cursorColor = c.accent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Les morceaux à remettre dans l'ordre.
 *
 * Un mot déjà placé reste visible mais grisé plutôt que de disparaître : voir
 * la liste se vider ferait perdre le repère de ce qui reste à placer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowMorceaux(
    morceaux: List<String>,
    utilises: List<String>,
    actif: Boolean,
    onChoisir: (String) -> Unit,
    onEffacer: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    val restants = utilises.toMutableList()

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        morceaux.forEach { mot ->
            // Un même mot peut apparaître deux fois : on ne grise que la
            // première occurrence encore non consommée.
            val consomme = restants.remove(mot)
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (consomme) c.surface1 else c.surface3)
                    .border(1.dp, c.border, RoundedCornerShape(10.dp))
                    .clickable(enabled = actif && !consomme) { onChoisir(mot) }
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Text(
                    mot,
                    color = if (consomme) c.textDisabled else c.textPrimary,
                    fontSize = 13.sp
                )
            }
        }
        if (utilises.isNotEmpty() && actif) {
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(c.surface1)
                    .clickable { onEffacer() }
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Text("⌫", color = c.textSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun BoutonReponse(
    texte: String,
    couleur: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(couleur.copy(alpha = 0.16f))
            .border(1.dp, couleur.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(texte, color = couleur, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EcranFin(
    reussies: Int,
    ratees: Int,
    message: String,
    onRejouer: () -> Unit,
    onBack: () -> Unit
) {
    val c = MaterialTheme.sankaiColors
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (reussies + ratees > 0) "🎉" else "🃏", fontSize = 52.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            if (reussies + ratees > 0) "Session terminée" else "Rien à réviser",
            color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        if (reussies + ratees > 0) {
            Text("$reussies acquises • $ratees à revoir", color = c.textSecondary, fontSize = 14.sp)
        } else {
            Text(
                "Toutes tes cartes sont à jour. Reviens plus tard : " +
                "elles reviendront d'elles-mêmes selon leur échéance.",
                color = c.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center
            )
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(message, color = AccentGold, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(28.dp))
        if (reussies + ratees > 0) {
            SankaiButton("Continuer à réviser", onClick = onRejouer, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
        SankaiButton("Retour", onClick = onBack, secondary = true, modifier = Modifier.fillMaxWidth())
    }
}
