package com.sankailife.core.domain.engine

import java.text.Normalizer
import kotlin.math.min

/**
 * Correction tolérante d'une réponse écrite.
 *
 * Une dictée refusée pour un accent oublié est une dictée qui décourage.
 * L'objectif est de valider ce qu'une personne raisonnable appellerait « la
 * bonne réponse », pas de faire de l'orthographe une seconde épreuve cachée
 * par-dessus la première.
 *
 * Trois niveaux, du plus indulgent au plus strict, appliqués dans cet ordre :
 * normalisation (accents, casse, ponctuation), puis distance d'édition bornée.
 */
object ToleranceOrthographe {

    /**
     * Ramène un texte à sa forme comparable.
     *
     * Accents retirés, casse ignorée, ponctuation supprimée, espaces
     * multiples réduits. Un utilisateur qui tape sans accents sur un clavier
     * pressé ne doit pas être pénalisé — c'est la faute la plus fréquente et
     * la moins significative.
     */
    fun normaliser(texte: String): String =
        Normalizer.normalize(texte.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")           // marques d'accentuation
            // Les signes de liaison sont supprimés, pas remplacés par une
            // espace : « l'eau » et « leau » sont le même mot, « week-end » et
            // « weekend » aussi. Les transformer en séparateur créerait une
            // faute là où il n'y en a pas.
            .replace(Regex("[’'’\\-]+"), "")
            .replace(Regex("[\\p{Punct}«»]+"), " ")   // ponctuation séparatrice
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Fautes tolérées pour une réponse de cette longueur.
     *
     * Environ une pour six caractères, plafonnée à trois. Sans plafond, une
     * phrase longue accepterait n'importe quoi ; sans proportionnalité, un mot
     * court n'accepterait rien.
     */
    fun fautesTolerees(longueur: Int): Int = (longueur / 6).coerceIn(0, 3)

    /**
     * Distance de Levenshtein, en n'gardant que deux lignes en mémoire.
     * Les réponses sont courtes, mais la version matricielle n'apporterait
     * rien de plus ici.
     */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var precedente = IntArray(b.length + 1) { it }
        var courante = IntArray(b.length + 1)

        for (i in 1..a.length) {
            courante[0] = i
            for (j in 1..b.length) {
                val substitution = precedente[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                courante[j] = min(min(courante[j - 1] + 1, precedente[j] + 1), substitution)
            }
            val echange = precedente
            precedente = courante
            courante = echange
        }
        return precedente[b.length]
    }

    /** Verdict d'une correction, pour pouvoir nuancer le retour affiché. */
    enum class Verdict { EXACT, ACCEPTE_AVEC_FAUTES, REFUSE }

    fun corriger(reponse: String, attendu: String): Verdict {
        val r = normaliser(reponse)
        val a = normaliser(attendu)

        if (r.isEmpty()) return Verdict.REFUSE
        if (r == a) return Verdict.EXACT

        val ecart = distance(r, a)
        return if (ecart <= fautesTolerees(a.length)) {
            Verdict.ACCEPTE_AVEC_FAUTES
        } else {
            Verdict.REFUSE
        }
    }

    fun estAcceptee(reponse: String, attendu: String): Boolean =
        corriger(reponse, attendu) != Verdict.REFUSE
}
