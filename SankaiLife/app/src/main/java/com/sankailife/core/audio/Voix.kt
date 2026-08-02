package com.sankailife.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sankailife.core.domain.engine.VoixEngine

/**
 * Accès à la synthèse vocale du système.
 *
 * L'application n'embarque aucune voix : elle emprunte celle du téléphone.
 * C'est ce qui permet de faire prononcer du portugais sans peser un
 * mégaoctet de plus, et c'est aussi pourquoi rien n'est garanti — un appareil
 * peut n'avoir aucun moteur installé, ou aucune voix pour la langue demandée.
 * Tout ici part de ce constat : l'écoute est un bonus, jamais un passage
 * obligé.
 */
class Voix(
    /** null tant que le moteur n'est pas construit, ou après sa libération. */
    private val moteur: TextToSpeech?,
    /** false tant que le moteur n'a pas répondu, ou s'il a échoué. */
    val pret: Boolean
) {

    /**
     * Prononce un texte dans une langue, si les deux sont utilisables.
     *
     * @return true si la lecture a été lancée.
     */
    fun dire(texte: String, codeLangue: String, vitesse: String = "normale"): Boolean {
        val tts = moteur ?: return false
        if (!pret) return false
        val aDire = VoixEngine.aPrononcer(texte) ?: return false

        // On prend la premiere locale reellement installee, pas la premiere
        // demandee : sans voix portugaise du Portugal, le bresilien vaut mieux
        // que le silence, et il faut le dire au moteur explicitement — sinon il
        // choisit seul et on ne sait plus ce qu'on entend.
        val locale = VoixEngine.candidats(codeLangue).firstOrNull { candidat ->
            val etat = runCatching { tts.isLanguageAvailable(candidat) }.getOrNull()
            etat != null && etat >= TextToSpeech.LANG_AVAILABLE
        } ?: return false

        // LANG_MISSING_DATA et LANG_NOT_SUPPORTED sont négatifs ; les codes de
        // succès ne le sont pas. Ignorer ce retour ferait lire du portugais
        // avec la voix par défaut du téléphone, sans que rien ne le signale.
        val etat = runCatching { tts.setLanguage(locale) }.getOrNull()
        if (etat == null || etat < TextToSpeech.LANG_AVAILABLE) return false

        // Une voix lente aide reellement a distinguer les sons d'une langue
        // qu'on ne connait pas encore ; en dessous de 0,6 elle devient trainante
        // et cesse de ressembler a la langue parlee.
        runCatching {
            tts.setSpeechRate(
                when (vitesse) {
                    "lente" -> 0.65f
                    "rapide" -> 1.25f
                    else -> 1.0f
                }
            )
        }

        return runCatching {
            tts.speak(aDire, TextToSpeech.QUEUE_FLUSH, null, aDire.hashCode().toString())
        }.getOrNull() == TextToSpeech.SUCCESS
    }

    /**
     * Une voix existe-t-elle pour cette langue ?
     *
     * Interrogé avant d'afficher le bouton : proposer une écoute qui ne
     * produira aucun son est pire que ne rien proposer.
     */
    fun disponiblePour(codeLangue: String): Boolean {
        val tts = moteur ?: return false
        if (!pret) return false
        return VoixEngine.candidats(codeLangue).any { candidat ->
            val etat = runCatching { tts.isLanguageAvailable(candidat) }.getOrNull()
            etat != null && etat >= TextToSpeech.LANG_AVAILABLE
        }
    }

    fun arreter() {
        runCatching { moteur?.stop() }
    }
}

/**
 * Crée une voix liée au cycle de vie de l'écran.
 *
 * Le moteur est libéré à la sortie : un TextToSpeech non arrêté continue de
 * tenir une connexion au service système bien après la fermeture de l'écran.
 */
@Composable
fun rememberVoix(): Voix {
    val contexte = LocalContext.current
    var pret by remember { mutableStateOf(false) }
    val moteur = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(contexte) {
        val tts = TextToSpeech(contexte.applicationContext) { statut ->
            pret = statut == TextToSpeech.SUCCESS
        }
        moteur.value = tts
        onDispose {
            runCatching { tts.stop() }
            runCatching { tts.shutdown() }
            moteur.value = null
        }
    }

    val instance = moteur.value
    return remember(instance, pret) {
        if (instance == null) VoixMuette else Voix(instance, pret)
    }
}

/**
 * Voix qui ne parle jamais, le temps que le moteur se construise.
 *
 * Un objet inerte évite au reste du code de manipuler un `Voix?` et de tester
 * la nullité à chaque appel — ne rien faire est déjà le comportement d'une
 * voix qui n'est pas prête.
 */
private val VoixMuette = Voix(moteur = null, pret = false)
