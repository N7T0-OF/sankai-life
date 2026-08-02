package com.sankailife.core.domain.engine

import java.util.Locale

/**
 * Décide ce qui doit être prononcé, et dans quelle langue.
 *
 * La synthèse vocale elle-même appartient au système : ce moteur ne parle
 * pas, il choisit. Ce découpage permet de tester les règles — quelle face
 * lire, quelle langue, quand se taire — sans téléphone ni moteur vocal.
 */
object VoixEngine {

    /**
     * Au-delà, on ne prononce pas.
     *
     * Une carte de plusieurs centaines de caractères n'est pas une phrase à
     * mémoriser mais un paragraphe collé par erreur ; l'écouter en entier
     * ferait perdre plus de temps que le lire.
     */
    const val MAX_CARACTERES = 300

    /**
     * Langue à utiliser pour une carte.
     *
     * `null` signifie « ne pas proposer l'écoute ». C'est le cas quand le
     * module ne déclare pas sa langue : faire lire du portugais par une voix
     * française produit un son que personne ne reconnaîtrait, et un apprenant
     * débutant n'aurait aucun moyen de savoir que la prononciation est fausse.
     */
    /**
     * Locales a essayer, de la plus precise a la plus vague.
     *
     * **Le probleme : « pt » n'est pas une prononciation.** La plupart des
     * moteurs Android resolvent le portugais generique en bresilien, qui est le
     * plus repandu. Or le contenu livre avec l'application est du portugais
     * europeen — « comboio », « autocarro », « pequeno-almoco » — et l'entendre
     * avec l'accent bresilien apprend une prononciation qui ne correspond pas
     * aux mots qu'on lit.
     *
     * Un module qui declare « pt-BR » obtient donc du bresilien, et c'est
     * voulu : la regle ne devine que pour le code sans region.
     *
     * L'ordre vaut aussi pour les autres langues : « en » essaie l'anglais
     * britannique avant l'americain seulement s'il est present, sinon la voix
     * du telephone decide, ce qui reste le comportement le plus previsible.
     */
    fun candidats(codeLangue: String): List<Locale> {
        val base = locale(codeLangue) ?: return emptyList()
        if (base.country.isNotBlank()) return listOf(base)

        val prefere = when (base.language.lowercase()) {
            "pt" -> listOf(Locale("pt", "PT"), Locale("pt", "BR"))
            else -> emptyList()
        }
        return prefere + base
    }

    fun locale(codeLangue: String): Locale? {
        val code = codeLangue.trim()
        if (code.isBlank()) return null
        return runCatching { Locale.forLanguageTag(code) }
            .getOrNull()
            ?.takeIf { it.language.isNotBlank() }
    }

    /**
     * Texte réellement prononçable, ou `null`.
     *
     * Les parenthèses sont retirées : « Obrigado (dit par un homme) » doit
     * s'entendre « Obrigado ». La précision s'adresse au lecteur, pas à
     * l'oreille, et la faire lire apprendrait une phrase qui n'existe pas.
     */
    fun aPrononcer(texte: String): String? {
        val sansParentheses = texte.replace(Regex("\\([^)]*\\)"), " ")
        val propre = sansParentheses.replace(Regex("\\s+"), " ").trim()
        if (propre.isBlank()) return null
        return propre.take(MAX_CARACTERES)
    }

    /**
     * L'écoute est-elle proposable pour cette carte ?
     *
     * Les trois conditions sont réunies ici plutôt que dispersées dans
     * l'écran : un bouton qui apparaît et ne produit aucun son est pire que
     * pas de bouton du tout.
     */
    fun peutParler(codeLangue: String, texte: String, moteurPret: Boolean): Boolean =
        moteurPret && locale(codeLangue) != null && aPrononcer(texte) != null

    /**
     * Message affiché quand l'écoute est indisponible malgré une langue connue.
     *
     * Dire *pourquoi* évite de faire passer une voix manquante sur le
     * téléphone pour un défaut de l'application.
     */
    fun raisonIndisponible(codeLangue: String, moteurPret: Boolean): String? = when {
        moteurPret -> null
        locale(codeLangue) == null -> null
        else -> "Voix indisponible sur cet appareil."
    }
}
