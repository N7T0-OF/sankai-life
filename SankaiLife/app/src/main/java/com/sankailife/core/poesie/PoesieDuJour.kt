package com.sankailife.core.poesie

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** La nature de la découverte : un proverbe ou un poème (extrait). */
enum class TypeTexte { PROVERBE, POEME }

/**
 * Une découverte littéraire du jour : proverbe ou court extrait de poème.
 *
 * Tout est local — aucun réseau — et les textes sont choisis dans le domaine
 * public (ou des sagesses populaires sans auteur), pour rester utilisables
 * légalement. Seul le texte est obligatoire : un proverbe peut se passer
 * d'auteur, un poème peut se passer de date.
 */
data class PoesieDuJour(
    /** Identifiant stable, unique dans le catalogue. */
    val id: String,
    val type: TypeTexte,
    /** Le proverbe entier, ou l'extrait du poème. */
    val texte: String,
    val auteur: String? = null,
    /** L'œuvre d'où vient l'extrait. */
    val oeuvre: String? = null,
    val annee: String? = null,
    /** « fr », « pt », « en »… — la langue du texte original. */
    val langue: String = "fr",
    /** 🇫🇷 ou 🌍 — filtre France / Monde. */
    val drapeau: String = "🇫🇷",
    /** Source, traduction, contexte d'écriture. */
    val contexte: String? = null
)

/**
 * Choisit la découverte du jour sans réseau et sans état.
 *
 * Même logique que le Mot du jour : la même date donne toujours la même
 * découverte, sur n'importe quel appareil, indépendamment de l'ordre du
 * catalogue. Une découverte par jour, pas un fil.
 */
object PoesieDuJourSelector {

    fun selectionner(textes: Collection<PoesieDuJour>, date: LocalDate): PoesieDuJour? {
        val tries = textes.sortedBy { it.id }
        if (tries.isEmpty()) return null
        return tries[indexPour(date.toEpochDay(), tries.size)]
    }

    /** La découverte qui viendra demain, pour la ligne « prochaine ». */
    fun suivant(textes: Collection<PoesieDuJour>, date: LocalDate): PoesieDuJour? =
        selectionner(textes, date.plus(1, ChronoUnit.DAYS))

    private fun indexPour(epochDay: Long, taille: Int): Int {
        val graine = MessageDigest.getInstance("SHA-256")
            .digest(epochDay.toString().toByteArray(StandardCharsets.UTF_8))
        val valeur = ((graine[0].toInt() and 0xFF) shl 16) or
            ((graine[1].toInt() and 0xFF) shl 8) or
            (graine[2].toInt() and 0xFF)
        return valeur % taille
    }
}
