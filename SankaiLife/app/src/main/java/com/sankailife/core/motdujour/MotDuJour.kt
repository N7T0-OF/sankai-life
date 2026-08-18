package com.sankailife.core.motdujour

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Un mot du jour : mot, langue, définition courte, exemple et origine.
 *
 * Tout est local — aucun réseau — et chaque champ est facultatif sauf le mot
 * et la définition : un mot sans exemple reste utile, un mot sans définition
 * n'est plus un mot du jour.
 */
data class MotDuJour(
    /** Identifiant stable, unique dans le catalogue. */
    val id: String,
    val mot: String,
    /** BCP-47 : « fr », « pt », « pt-BR », « en »… */
    val langue: String,
    val definition: String,
    /** « /sa.u.'da.dʒi/ » par exemple. */
    val prononciation: String? = null,
    val exemple: String? = null,
    /** « Du grec ancien éphēmeros… » */
    val origine: String? = null,
    /** « Adjectif », « Nom », « Verbe »… */
    val categorie: String? = null,
    /** Niveau CECRL approximatif : « A1 » … « C2 ». */
    val niveau: String? = null
) {
    /** Le code court de langue (« pt-BR » devient « pt »), pour l'écoute. */
    val codeLangue: String get() = langue.trim().lowercase().substringBefore('-')
}

/** Drapeau de la langue du mot, pour la pastille de l'écran. */
fun MotDuJour.drapeau(): String = when (codeLangue) {
    "fr" -> "🇫🇷"
    "pt" -> "🇵🇹"
    "en" -> "🇬🇧"
    "es" -> "🇪🇸"
    "it" -> "🇮🇹"
    "de" -> "🇩🇪"
    else -> "🌍"
}

/** Nom lisible de la langue du mot, dans la langue du contenu. */
fun MotDuJour.libelleLangue(): String = when (codeLangue) {
    "pt" -> "Português"
    "es" -> "Español"
    "en" -> "English"
    "fr" -> "Français"
    "de" -> "Deutsch"
    "it" -> "Italiano"
    else -> langue
}

/**
 * Choisit le mot du jour sans réseau et sans état.
 *
 * La même date donne toujours le même mot, sur n'importe quel appareil, et le
 * choix ne dépend ni de l'ordre du catalogue (trié d'abord) ni d'un compteur
 * qui se désynchroniserait. Un mot par jour, pas un fil.
 */
object MotDuJourSelector {

    fun selectionner(mots: Collection<MotDuJour>, date: LocalDate): MotDuJour? {
        val tries = mots.sortedBy { it.id }
        if (tries.isEmpty()) return null
        return tries[indexPour(date.toEpochDay(), tries.size)]
    }

    /** Le mot qui viendra demain, pour la ligne « prochaine découverte ». */
    fun suivant(mots: Collection<MotDuJour>, date: LocalDate): MotDuJour? =
        selectionner(mots, date.plus(1, ChronoUnit.DAYS))

    private fun indexPour(epochDay: Long, taille: Int): Int {
        val graine = MessageDigest.getInstance("SHA-256")
            .digest(epochDay.toString().toByteArray(StandardCharsets.UTF_8))
        // Trois octets suffisent à répartir sur des catalogues de quelques
        // centaines d'entrées sans biais notable.
        val valeur = ((graine[0].toInt() and 0xFF) shl 16) or
            ((graine[1].toInt() and 0xFF) shl 8) or
            (graine[2].toInt() and 0xFF)
        return valeur % taille
    }
}
