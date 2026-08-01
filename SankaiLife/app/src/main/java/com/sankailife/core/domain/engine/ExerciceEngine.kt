package com.sankailife.core.domain.engine

import kotlin.random.Random

/**
 * Génère l'exercice adapté à chaque carte.
 *
 * L'ancien système reposait sur l'auto-évaluation : l'utilisateur retournait
 * la carte et déclarait lui-même s'il savait. C'est le maillon faible de tout
 * système de répétition espacée — on se croit toujours plus sûr qu'on ne l'est,
 * et une carte mal jugée reste mal jugée pendant des semaines.
 *
 * Ici, le type d'exercice suit la maîtrise : on reconnaît avant de produire.
 * Une carte fraîche demande de choisir parmi quatre propositions ; une carte
 * bien acquise demande de l'écrire. La difficulté monte donc toute seule, sans
 * réglage, à mesure que la boîte Leitner monte.
 */
object ExerciceEngine {

    /**
     * Les formes d'exercice.
     *
     * Chacune porte de quoi être affichée **et** corrigée : aucun écran n'a à
     * savoir comment on valide une réponse.
     */
    sealed interface Exercice {
        val carte: FlashcardEngine.Carte
        val consigne: String

        /** Choisir la bonne réponse parmi plusieurs. Le plus facile. */
        data class Reconnaissance(
            override val carte: FlashcardEngine.Carte,
            override val consigne: String,
            val question: String,
            val options: List<String>,
            val attendu: String
        ) : Exercice

        /** Écrire la réponse. Le plus exigeant. */
        data class Saisie(
            override val carte: FlashcardEngine.Carte,
            override val consigne: String,
            val question: String,
            val attendu: String
        ) : Exercice

        /** Compléter le mot manquant dans la phrase. */
        data class TexteATrous(
            override val carte: FlashcardEngine.Carte,
            override val consigne: String,
            val avant: String,
            val apres: String,
            val attendu: String
        ) : Exercice

        /** Remettre les mots dans l'ordre. */
        data class Ordre(
            override val carte: FlashcardEngine.Carte,
            override val consigne: String,
            val question: String,
            val morceaux: List<String>,
            val attendu: String
        ) : Exercice

        /**
         * Se remémorer, sans correction possible.
         * Recours pour les cartes d'une seule face trop courtes pour être
         * découpées — mieux vaut une carte imparfaite qu'une carte perdue.
         */
        data class Memoire(
            override val carte: FlashcardEngine.Carte,
            override val consigne: String
        ) : Exercice
    }

    /** Un exercice à choix a besoin de trois leurres crédibles. */
    private const val LEURRES = 3

    /**
     * Construit l'exercice d'une carte.
     *
     * [autres] fournit les leurres. Ils viennent du même module : piocher
     * ailleurs rendrait la bonne réponse reconnaissable au simple sujet, et
     * l'exercice ne testerait plus rien.
     */
    fun construire(
        carte: FlashcardEngine.Carte,
        autres: List<FlashcardEngine.Carte>,
        aleatoire: Random = Random.Default
    ): Exercice {
        val verso = carte.verso

        // Carte d'une seule face : rien à deviner, on remet dans l'ordre.
        if (verso == null) {
            val mots = decouperEnMots(carte.recto)
            return if (mots.size >= 3) {
                Exercice.Ordre(
                    carte, "Remets la phrase dans l'ordre", "",
                    morceaux = mots.shuffled(aleatoire),
                    attendu = carte.recto
                )
            } else {
                Exercice.Memoire(carte, "Souviens-toi")
            }
        }

        // L'appelant peut fournir un réservoir multi-module (notamment dans
        // « Mes erreurs »). L'invariant pédagogique est défendu ici aussi : un
        // leurre d'un autre sujet rendrait souvent la bonne réponse évidente.
        val leurres = autres.asSequence()
            .filter { it.moduleId == carte.moduleId }
            .mapNotNull { it.verso }
            .filter { it.isNotBlank() && !memeReponse(it, verso) }
            .distinct()
            .toList()
            .shuffled(aleatoire)
            .take(LEURRES)

        return when {
            // Cartes fraîches : reconnaître, si les leurres suffisent.
            carte.box <= 1 && leurres.size == LEURRES -> Exercice.Reconnaissance(
                carte, "Choisis la bonne réponse", carte.recto,
                options = (leurres + verso).shuffled(aleatoire),
                attendu = verso
            )

            // Palier intermédiaire : compléter, ce qui demande de produire
            // sans avoir à tout restituer.
            carte.box == 2 && decouperEnMots(verso).size >= 2 -> {
                val mots = decouperEnMots(verso)
                val index = aleatoire.nextInt(mots.size)
                Exercice.TexteATrous(
                    carte, "Complète le mot manquant",
                    avant = mots.take(index).joinToString(" "),
                    apres = mots.drop(index + 1).joinToString(" "),
                    attendu = mots[index]
                )
            }

            carte.box == 3 && decouperEnMots(verso).size >= 3 -> Exercice.Ordre(
                carte, "Remets la réponse dans l'ordre", carte.recto,
                morceaux = decouperEnMots(verso).shuffled(aleatoire),
                attendu = verso
            )

            // Carte acquise, ou leurres insuffisants : écrire la réponse.
            else -> Exercice.Saisie(carte, "Écris la réponse", carte.recto, verso)
        }
    }

    /**
     * Corrige une réponse.
     *
     * L'exercice de mémoire renvoie toujours null : il n'y a rien à corriger,
     * c'est à l'utilisateur de se déclarer. Le distinguer d'un échec évite de
     * compter une carte non corrigeable comme une erreur.
     */
    fun corriger(exercice: Exercice, reponse: String): Boolean? = when (exercice) {
        is Exercice.Reconnaissance -> memeReponse(reponse, exercice.attendu)
        is Exercice.Saisie -> ToleranceOrthographe.estAcceptee(reponse, exercice.attendu)
        is Exercice.TexteATrous -> ToleranceOrthographe.estAcceptee(reponse, exercice.attendu)
        is Exercice.Ordre -> ToleranceOrthographe.estAcceptee(reponse, exercice.attendu)
        is Exercice.Memoire -> null
    }

    /** La réponse attendue, pour l'afficher après une erreur. */
    fun reponseAttendue(exercice: Exercice): String? = when (exercice) {
        is Exercice.Reconnaissance -> exercice.attendu
        is Exercice.Saisie -> exercice.attendu
        is Exercice.TexteATrous -> exercice.attendu
        is Exercice.Ordre -> exercice.attendu
        is Exercice.Memoire -> null
    }

    private fun memeReponse(a: String, b: String): Boolean =
        ToleranceOrthographe.normaliser(a) == ToleranceOrthographe.normaliser(b)

    private fun decouperEnMots(texte: String): List<String> =
        texte.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
}
