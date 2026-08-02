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

    /**
     * Les formes disponibles, nommées.
     *
     * Sert à en **demander** une précise. Sans cela, deux moteurs décideraient
     * de la même chose : celui-ci choisit la forme d'après la maîtrise, et le
     * planificateur de session choisit la sienne d'après la variété. Deux
     * autorités sur une même décision finissent toujours par se contredire —
     * ici, l'écran annoncerait « texte à trous » et afficherait une saisie.
     *
     * La répartition des rôles est donc : le planificateur décide **quelle
     * forme**, ce moteur décide **comment la construire** et refuse quand la
     * carte ne s'y prête pas.
     */
    enum class Forme { RECONNAISSANCE, TEXTE_A_TROUS, ORDRE, SAISIE, MEMOIRE }

    /** Un exercice à choix a besoin de trois leurres crédibles. */
    private const val LEURRES = 3

    /**
     * Construit l'exercice d'une carte.
     *
     * [autres] fournit les leurres. Ils viennent du même module : piocher
     * ailleurs rendrait la bonne réponse reconnaissable au simple sujet, et
     * l'exercice ne testerait plus rien.
     */
    /**
     * @param forme forme demandée. `null` — le comportement d'origine —
     *   laisse la maîtrise décider : on reconnaît avant de produire, et la
     *   difficulté monte toute seule avec la boîte Leitner.
     *
     *   Quand une forme est demandée mais que la carte ne s'y prête pas — pas
     *   assez de leurres, verso trop court — on **redescend** vers une forme
     *   possible plutôt que d'échouer. Perdre une carte parce que son exercice
     *   idéal est impraticable serait le pire des deux.
     */
    fun construire(
        carte: FlashcardEngine.Carte,
        autres: List<FlashcardEngine.Carte>,
        forme: Forme? = null,
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

        val mots = decouperEnMots(verso)

        // Ce que la carte permet réellement, indépendamment de ce qu'on veut.
        val reconnaissancePossible = leurres.size == LEURRES
        val trousPossible = mots.size >= 2
        val ordrePossible = mots.size >= 3

        fun reconnaissance() = Exercice.Reconnaissance(
            carte, "Choisis la bonne réponse", carte.recto,
            options = (leurres + verso).shuffled(aleatoire),
            attendu = verso
        )

        fun trous(): Exercice {
            val index = aleatoire.nextInt(mots.size)
            return Exercice.TexteATrous(
                carte, "Complète le mot manquant",
                avant = mots.take(index).joinToString(" "),
                apres = mots.drop(index + 1).joinToString(" "),
                attendu = mots[index]
            )
        }

        fun ordre() = Exercice.Ordre(
            carte, "Remets la réponse dans l'ordre", carte.recto,
            morceaux = mots.shuffled(aleatoire), attendu = verso
        )

        val saisie = Exercice.Saisie(carte, "Écris la réponse", carte.recto, verso)

        // Forme demandée : on l'honore si la carte le permet, sinon on
        // redescend vers la saisie, qui ne demande rien de la carte.
        if (forme != null) {
            return when (forme) {
                Forme.RECONNAISSANCE -> if (reconnaissancePossible) reconnaissance() else saisie
                Forme.TEXTE_A_TROUS -> if (trousPossible) trous() else saisie
                Forme.ORDRE -> if (ordrePossible) ordre() else saisie
                Forme.SAISIE -> saisie
                Forme.MEMOIRE -> Exercice.Memoire(carte, "Souviens-toi")
            }
        }

        // Sans demande, la maîtrise décide : on reconnaît avant de produire.
        return when {
            carte.box <= 1 && reconnaissancePossible -> reconnaissance()
            // Palier intermédiaire : compléter, ce qui demande de produire
            // sans avoir à tout restituer.
            carte.box == 2 && trousPossible -> trous()
            carte.box == 3 && ordrePossible -> ordre()
            // Carte acquise, ou leurres insuffisants : écrire la réponse.
            else -> saisie
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
