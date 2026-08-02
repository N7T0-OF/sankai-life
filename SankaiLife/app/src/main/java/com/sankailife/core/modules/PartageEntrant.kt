package com.sankailife.core.modules

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ce qui arrive dans l'application depuis le menu « Partager » d'Android.
 *
 * **Le filtre existait déjà dans le manifeste et personne ne le lisait.**
 * Partager un texte vers Sankai Life ouvrait l'application et ne faisait rien
 * — pas de message, pas d'erreur, rien. C'est le même défaut que l'embauche
 * des Mimos ou les thèmes sans effet : quelque chose de déclaré, qui n'agit
 * pas, et qu'aucun test ne pouvait attraper.
 *
 * Ce porteur est volontairement minuscule : l'activité y dépose ce qu'elle a
 * reçu, l'écran le récupère quand il est prêt. Passer par la navigation
 * obligerait à encoder le contenu dans une route, ce qui n'a aucun sens pour
 * un fichier de plusieurs kilo-octets.
 */
object PartageEntrant {

    /** Ce qu'on a reçu, en attente d'être montré. */
    sealed interface Contenu {
        /** Du texte collé ou partagé : des cartes, peut-être. */
        data class Texte(val valeur: String, val titre: String = "") : Contenu

        /** Un fichier : archive, JSON, texte, tableur. */
        data class Fichier(val uri: android.net.Uri, val nom: String = "") : Contenu
    }

    private val _recu = MutableStateFlow<Contenu?>(null)
    val recu: StateFlow<Contenu?> = _recu.asStateFlow()

    fun deposer(contenu: Contenu) { _recu.value = contenu }

    /**
     * Vide le porteur.
     *
     * Appelé quand l'écran a fini, réussite ou annulation : sans cela, revenir
     * sur l'Académie rouvrirait indéfiniment le même aperçu d'import.
     */
    fun consommer() { _recu.value = null }

    /**
     * Un texte partagé qui n'est qu'une adresse n'est pas une carte.
     *
     * Le cas arrive tout le temps : on partage un lien depuis un navigateur.
     * L'importer comme carte donnerait une carte contenant une URL, ce que
     * personne ne veut. On le dit plutôt que de faire semblant.
     */
    fun estUneAdresse(texte: String): Boolean {
        val net = texte.trim()
        return net.lineSequence().count { it.isNotBlank() } == 1 &&
            (net.startsWith("http://") || net.startsWith("https://")) &&
            !net.contains(' ')
    }
}
