package com.sankailife.core.learning.domain

import kotlin.random.Random

/**
 * Compose une session d'apprentissage.
 *
 * C'est la pièce qui fait la différence entre « une liste de cartes » et « un
 * parcours ». Une session n'est pas un tirage au sort : elle mélange
 * reconnaissance et production, fait revenir les erreurs, évite de répéter le
 * même exercice, et se termine par quelque chose de simple — on retient mieux
 * ce qu'on quitte sur une réussite.
 *
 * Déterministe à graine fixée : deux appels identiques donnent la même session.
 * Sans cela, la moindre recomposition de l'écran redistribuerait les exercices
 * sous les doigts de l'apprenant.
 */
object SessionPlanEngine {

    /**
     * Les types d'exercices du vocabulaire.
     *
     * Tous sont déclarés ici, y compris ceux qui ne sont pas encore construits :
     * c'est le vocabulaire commun de la refonte. Ce qui décide de ce qu'on
     * propose réellement, c'est [DISPONIBLES] — et la leçon est récente : offrir
     * un exercice sans effet, c'est vendre un objet qui ne fait rien.
     *
     * @param production l'apprenant **fabrique** la réponse au lieu de la
     *   choisir. Une session sans production donne l'illusion de savoir : on
     *   reconnaît « casa » dans une liste bien avant de savoir l'écrire.
     */
    enum class Type(
        val libelle: String,
        val production: Boolean,
        /** Durée typique, en secondes. Sert à tenir la durée annoncée. */
        val secondes: Int,
        /** Exige une carte à deux faces : sans verso, il n'y a rien à demander. */
        val besoinVerso: Boolean = true,
        val besoinAudio: Boolean = false,
        val besoinMicro: Boolean = false,
        /**
         * Mots minimum au verso.
         *
         * Un texte à trous a besoin d'au moins deux mots pour qu'il en reste un
         * après en avoir caché un ; une phrase à reconstruire, d'au moins
         * trois, sans quoi il n'y a rien à remettre dans l'ordre. Le déclarer
         * ici plutôt que de laisser l'exercice se dégrader silencieusement :
         * annoncer « 2 texte à trous » et afficher deux saisies serait un
         * mensonge sur la session.
         */
        val motsVersoMin: Int = 0,
        /**
         * L'apprenant juge lui-même sa réponse : il n'y a pas d'échec.
         *
         * La distinction sert à choisir sur quoi finir une session. Une
         * flashcard fait produire de mémoire — donc c'est du rappel actif —
         * mais on ne peut pas s'y tromper, puisqu'on annonce soi-même si on
         * savait.
         */
        val autoEvalue: Boolean = false
    ) {
        FLASHCARD("Flashcard", production = true, secondes = 12, besoinVerso = false,
            autoEvalue = true),
        MULTIPLE_CHOICE("QCM", production = false, secondes = 15),
        MATCHING("Association", production = false, secondes = 40),
        TYPING("Écriture", production = true, secondes = 25),
        SENTENCE_ORDER("Phrase à reconstruire", production = true, secondes = 30,
            motsVersoMin = 3),
        FILL_IN_THE_BLANK("Texte à trous", production = true, secondes = 22,
            motsVersoMin = 2),
        DICTATION("Dictée", production = true, secondes = 30, besoinAudio = true),
        LISTENING("Écoute", production = false, secondes = 25, besoinAudio = true),
        PRONUNCIATION("Prononciation", production = true, secondes = 25,
            besoinAudio = true, besoinMicro = true),
        IMAGE_TO_WORD("Image", production = false, secondes = 18),
        TRUE_FALSE("Vrai ou faux", production = false, secondes = 10),
        CATEGORY_SORT("Tri par catégorie", production = false, secondes = 45),
        QUICK_MEMORY("Mémoire rapide", production = false, secondes = 35),
        GUIDED_DIALOGUE("Dialogue guidé", production = false, secondes = 60)
    }

    /**
     * Ce que l'application sait réellement faire aujourd'hui.
     *
     * Miroir exact des exercices construits. Un test verrouille ce couple :
     * annoncer un exercice qui n'existe pas ferait cliquer dans le vide.
     */
    val DISPONIBLES: Set<Type> = setOf(
        Type.FLASHCARD,
        Type.MULTIPLE_CHOICE,
        Type.TYPING,
        Type.FILL_IN_THE_BLANK,
        Type.SENTENCE_ORDER
    )

    /**
     * Correspondance avec les formes que sait construire [ExerciceEngine].
     *
     * C'est **la** définition de « disponible » : un type sans forme n'a rien
     * qui sache l'afficher. Ma première version n'en déclarait que trois alors
     * que cinq fonctionnaient depuis longtemps — le texte à trous et la phrase
     * à reconstruire existaient, marchaient, et n'étaient jamais programmés.
     * Sous-déclarer est moins grave que sur-promettre, mais c'est le même
     * défaut : un écart entre ce qu'on annonce et ce qui est.
     */
    fun forme(type: Type): com.sankailife.core.domain.engine.ExerciceEngine.Forme? = when (type) {
        Type.MULTIPLE_CHOICE ->
            com.sankailife.core.domain.engine.ExerciceEngine.Forme.RECONNAISSANCE
        Type.TYPING -> com.sankailife.core.domain.engine.ExerciceEngine.Forme.SAISIE
        Type.FILL_IN_THE_BLANK ->
            com.sankailife.core.domain.engine.ExerciceEngine.Forme.TEXTE_A_TROUS
        Type.SENTENCE_ORDER -> com.sankailife.core.domain.engine.ExerciceEngine.Forme.ORDRE
        Type.FLASHCARD -> com.sankailife.core.domain.engine.ExerciceEngine.Forme.MEMOIRE
        else -> null
    }

    /** Une carte candidate, avec ce qui décide de sa place dans la session. */
    data class Carte(
        val id: Long,
        val aVerso: Boolean,
        /** Boîte de Leitner : plus elle est basse, plus la carte est fragile. */
        val boite: Int = 0,
        /** Mots au verso : décide des exercices que la carte peut porter. */
        val motsVerso: Int = 0,
        /** Ratée récemment. Ces cartes passent devant tout le reste. */
        val enErreur: Boolean = false,
        /** Dont la date de révision est passée. */
        val due: Boolean = false
    )

    /** Un exercice de la session. */
    data class Exercice(val type: Type, val carteId: Long)

    /** Le plan complet. */
    data class Plan(
        val moduleId: Long,
        val uniteId: String,
        val exercices: List<Exercice>,
        val minutesEstimees: Int
    ) {
        val vide: Boolean get() = exercices.isEmpty()
    }

    /** Durée par défaut d'une session, en minutes. */
    const val MINUTES_DEFAUT = 5

    /** Au-delà, la session devient une corvée quel que soit le temps annoncé. */
    const val EXERCICES_MAX = 24

    /**
     * Combien de fois une même carte peut revenir dans une session.
     *
     * Deux, et pas plus. Une unité d'une seule carte remplissait sinon les cinq
     * minutes annoncées avec la même carte quatre fois de suite : le budget de
     * temps était respecté à la lettre et la session était absurde.
     */
    const val PASSAGES_PAR_CARTE = 2

    /**
     * Compose la session.
     *
     * @param minutes temps que l'apprenant a annoncé vouloir y passer.
     * @param typesRecents types des dernières sessions, du plus récent au plus
     *   ancien. Ils sont dépriorisés — pas interdits : les bannir viderait le
     *   choix quand peu de types sont disponibles.
     * @param disponibles ce que l'appareil et les réglages permettent
     *   réellement : sans voix installée, pas de dictée ; sans micro autorisé,
     *   pas de prononciation.
     */
    fun composer(
        moduleId: Long,
        uniteId: String,
        cartes: List<Carte>,
        minutes: Int = MINUTES_DEFAUT,
        typesRecents: List<Type> = emptyList(),
        disponibles: Set<Type> = DISPONIBLES,
        graine: Long = 0L
    ): Plan {
        val utilisables = disponibles.intersect(DISPONIBLES)
        if (cartes.isEmpty() || utilisables.isEmpty()) {
            return Plan(moduleId, uniteId, emptyList(), 0)
        }

        val alea = Random(graine)
        val budget = minutes.coerceAtLeast(1) * 60

        // Ordre des cartes : les erreurs d'abord, puis les cartes dues, puis les
        // plus fragiles. Une session qui commence par ce qu'on sait déjà donne
        // le sentiment d'avancer sans rien apprendre.
        val ordonnees = cartes.sortedWith(
            compareByDescending<Carte> { it.enErreur }
                .thenByDescending { it.due }
                .thenBy { it.boite }
                .thenBy { it.id }
        )

        val exercices = mutableListOf<Exercice>()
        var secondes = 0
        var dernierType: Type? = null
        var index = 0

        // Chaque carte est parcourue au plus PASSAGES_PAR_CARTE fois : c'est ce
        // qui borne la session par le contenu réel de l'unité, et pas seulement
        // par le temps annoncé.
        val passages = ordonnees.size * PASSAGES_PAR_CARTE
        while (secondes < budget && exercices.size < EXERCICES_MAX && index < passages) {
            val carte = ordonnees[index % ordonnees.size]
            index++

            val type = choisir(
                carte = carte,
                utilisables = utilisables,
                dernierType = dernierType,
                typesRecents = typesRecents,
                // Alterner : si le précédent faisait produire, on relâche.
                preferProduction = dernierType?.production == false,
                alea = alea
            ) ?: break

            // On n'ajoute jamais un exercice qui ferait dépasser le temps
            // annoncé. Annoncer cinq minutes et en livrer cinq et demie est un
            // petit mensonge, et c'est exactement le genre de petit mensonge
            // qui fait qu'on ne croit plus les durées affichées.
            if (secondes + type.secondes > budget && exercices.isNotEmpty()) break

            exercices += Exercice(type, carte.id)
            secondes += type.secondes
            dernierType = type
        }

        if (exercices.isEmpty()) return Plan(moduleId, uniteId, emptyList(), 0)

        val parId = cartes.associateBy { it.id }
        val supporte = { id: Long, type: Type ->
            val c = parId[id]
            c != null && (!type.besoinVerso || c.aVerso) && c.motsVerso >= type.motsVersoMin
        }
        return Plan(
            moduleId = moduleId,
            uniteId = uniteId,
            exercices = finir(
                garantirRappelActif(exercices, utilisables, supporte), utilisables, supporte
            ),
            minutesEstimees = ((secondes + 59) / 60).coerceAtLeast(1)
        )
    }

    /**
     * Choisit un type pour une carte donnée.
     *
     * Trois filtres, dans cet ordre : ce que la carte permet, ce qui ne répète
     * pas l'exercice précédent, ce qui n'a pas été vu récemment.
     */
    private fun choisir(
        carte: Carte,
        utilisables: Set<Type>,
        dernierType: Type?,
        typesRecents: List<Type>,
        preferProduction: Boolean,
        alea: Random
    ): Type? {
        val possibles = utilisables.filter {
            (!it.besoinVerso || carte.aVerso) && carte.motsVerso >= it.motsVersoMin
        }
        if (possibles.isEmpty()) return null

        // Deux fois le même exercice d'affilée se remarque immédiatement et
        // donne l'impression d'un bug.
        val sansRepetition = possibles.filter { it != dernierType }
            .ifEmpty { possibles }

        // Alternance production / reconnaissance, quand le paquet le permet.
        val alternes = sansRepetition.filter { it.production == preferProduction }
            .ifEmpty { sansRepetition }

        // Une carte fragile mérite d'être reconnue avant d'être produite :
        // demander d'écrire un mot vu une fois ne mesure rien.
        val adaptes = if (carte.boite == 0) {
            alternes.filter { !it.production }.ifEmpty { alternes }
        } else {
            alternes
        }

        // Entre les restants, le moins récemment vu. À égalité, au hasard —
        // mais un hasard à graine fixée, donc reproductible.
        val rangRecent = { t: Type ->
            typesRecents.indexOf(t).let { if (it < 0) Int.MAX_VALUE else it }
        }
        val meilleurRang = adaptes.maxOf(rangRecent)
        return adaptes.filter { rangRecent(it) == meilleurRang }.random(alea)
    }

    /**
     * Garantit au moins un rappel actif.
     *
     * Une session entièrement faite de QCM peut se traverser en reconnaissant
     * des formes sans jamais rien retrouver de mémoire. C'est agréable et ça
     * n'apprend rien.
     *
     * Le type de remplacement doit rester compatible avec **sa** carte : une
     * carte sans verso ne peut pas devenir une écriture, il n'y aurait rien à
     * corriger.
     */
    private fun garantirRappelActif(
        exercices: List<Exercice>,
        utilisables: Set<Type>,
        supporte: (Long, Type) -> Boolean
    ): List<Exercice> {
        if (exercices.any { it.type.production }) return exercices
        val premier = exercices.first()
        // Ne pas recréer la répétition qu'on évite ailleurs : le remplaçant ne
        // doit pas être identique à l'exercice suivant.
        val suivant = exercices.getOrNull(1)?.type
        val actif = utilisables
            .filter {
                it.production && it != suivant && supporte(premier.carteId, it)
            }
            .minByOrNull { it.secondes } ?: return exercices
        // On convertit le premier plutôt que d'en ajouter un : la durée annoncée
        // a déjà été promise.
        return listOf(Exercice(actif, premier.carteId)) + exercices.drop(1)
    }

    /**
     * Termine sur une activité qu'on ne peut pas rater.
     *
     * Quitter une session sur un échec laisse le souvenir de l'échec. Le
     * dernier exercice ne doit donc pas être de ceux qui se corrigent — une
     * écriture, une dictée — mais de ceux qu'on choisit ou qu'on s'évalue
     * soi-même.
     *
     * **La garantie est conditionnelle, et il vaut mieux le dire :** elle cède
     * devant la règle de non-répétition. Quand un seul type doux est
     * disponible et qu'il vient déjà d'être joué, la session se termine comme
     * elle peut. Deux exercices identiques d'affilée se remarquent tout de
     * suite ; finir sur une saisie, beaucoup moins.
     */
    private fun finir(
        exercices: List<Exercice>,
        utilisables: Set<Type>,
        supporte: (Long, Type) -> Boolean
    ): List<Exercice> {
        if (exercices.size < 2) return exercices
        val dernier = exercices.last()
        if (!dernier.type.production || dernier.type.autoEvalue) return exercices
        // Même précaution qu'à l'ouverture : remplacer le dernier par le type
        // de l'avant-dernier ferait apparaître la répétition qu'on interdit.
        val avantDernier = exercices[exercices.size - 2].type
        val doux = utilisables
            .filter {
                (!it.production || it.autoEvalue) && it != avantDernier &&
                    supporte(dernier.carteId, it)
            }
            .minByOrNull { it.secondes } ?: return exercices
        return exercices.dropLast(1) + Exercice(doux, dernier.carteId)
    }

    /**
     * Résumé de la session, à afficher avant de la lancer.
     *
     * Annoncer ce qui va se passer évite le sentiment de tirage au sort, et
     * permet de refuser une session dont on n'a pas le temps.
     */
    fun resume(plan: Plan): String {
        if (plan.vide) return "Rien à réviser pour l'instant."
        val parType = plan.exercices.groupingBy { it.type }.eachCount()
        return parType.entries
            .sortedByDescending { it.value }
            .joinToString(" · ") { (type, n) -> "$n ${type.libelle.lowercase()}" }
    }
}
