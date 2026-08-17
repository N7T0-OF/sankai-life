package com.sankailife.ui.screens.life.flashcards

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.R
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.ExerciceEngine
import com.sankailife.core.domain.engine.ErreursEngine
import com.sankailife.core.domain.engine.FlashcardEngine
import com.sankailife.core.learning.domain.AssociationEngine
import com.sankailife.core.learning.domain.SessionPlanEngine.Type as TypeSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Session de révision d'un module mémo.
 *
 * Les cartes sont chargées une fois au démarrage de la session : ajouter ou
 * retirer des lignes pendant une révision en cours changerait le total sous
 * les yeux de l'utilisateur.
 */
class FlashcardsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication
    private val memoDao = app.database.memoDao()
    private val userRepo = UserRepository(app.database)

    data class EtatSession(
        val chargement: Boolean = true,
        val nomModule: String = "",
        val cartes: List<FlashcardEngine.Carte> = emptyList(),
        val index: Int = 0,
        val versoVisible: Boolean = false,
        val reussies: Int = 0,
        val ratees: Int = 0,
        val terminee: Boolean = false,
        val messageFin: String = "",
        /** Exercice de la carte courante, construit à chaque avancée. */
        val exercice: ExerciceEngine.Exercice? = null,
        /** null tant que rien n'est validé, puis le verdict. */
        val correction: Boolean? = null,
        /** Montrée seulement après une erreur. */
        val reponseAttendue: String? = null,
        /** Verrou anti-double appui pendant l'écriture en base. */
        val reponseEnCours: Boolean = false,
        /**
         * Exercice d'association en cours, quand c'en est un.
         *
         * Il remplace [exercice] plutôt que de coexister avec : les deux
         * affichés en même temps poseraient deux questions à la fois.
         */
        val association: AssociationEngine.Etat? = null
    ) {
        val carteCourante: FlashcardEngine.Carte? get() = cartes.getOrNull(index)
        val total: Int get() = cartes.size
        val progression: Float get() = if (total == 0) 0f else index.toFloat() / total
        val enAttenteDeValidation: Boolean get() = correction == null && !reponseEnCours
    }

    private val _etat = MutableStateFlow(EtatSession())
    val etat: StateFlow<EtatSession> = _etat.asStateFlow()

    private var profileId: Long = -1L
    private var uniteId: String? = null
    private val cartesReintroduites = mutableSetOf<Long>()

    private val learningRepo =
        com.sankailife.core.learning.data.LearningRepository(app.database)

    /**
     * Types demandes par le plan de session, un par exercice.
     *
     * `null` laisse la maitrise decider, comme avant : c'est le comportement
     * d'une revision libre, ou celui d'une carte reintroduite apres un echec.
     *
     * On garde le **type** et non la forme, parce que l'association n'a pas de
     * forme ExerciceEngine : un null serait alors indistinct de « laisse la
     * maitrise decider », et l'exercice ne se lancerait jamais.
     */
    private var typesPlanifies: List<TypeSession?> = emptyList()

    /**
     * Demarre une session.
     *
     * @param uniteId unite du parcours a travailler. Quand elle est fournie,
     *   c'est le planificateur qui choisit les cartes **et** la forme de chaque
     *   exercice ; sinon on retombe sur la revision libre, inchangee.
     */
    fun demarrer(profileId: Long, uniteId: String? = null) {
        if (this.profileId == profileId && this.uniteId == uniteId &&
            !_etat.value.chargement
        ) return
        this.profileId = profileId
        this.uniteId = uniteId
        cartesReintroduites.clear()
        typesPlanifies = emptyList()
        typesJoues.clear()
        sessionId = 0L

        viewModelScope.launch {
            val modeErreurs = profileId == PROFIL_ERREURS

            val lignes = if (modeErreurs) {
                // Les cartes qui résistent, tous modules confondus. La sélection
                // est faite par le moteur : la requête ne fait qu'écarter ce qui
                // est manifestement hors sujet.
                val difficiles = memoDao.cartesDifficiles(ErreursEngine.REVISIONS_MINIMUM)
                val parId = difficiles.associateBy { it.id }
                ErreursEngine.selectionner(
                    difficiles.map {
                        ErreursEngine.Historique(
                            id = it.id, texte = it.text, boite = it.box,
                            revisions = it.reviewCount, reussites = it.successCount
                        )
                    }
                ).mapNotNull { parId[it.id] }
            } else {
                memoDao.getCartesDues(
                    profileId = profileId,
                    maintenant = System.currentTimeMillis(),
                    limite = FlashcardEngine.CARTES_PAR_SESSION
                )
            }

            val profil = if (modeErreurs) null else memoDao.getProfile(profileId)

            // La langue se porte par carte : une session « Mes erreurs »
            // mélange les modules, et lire du portugais avec une voix
            // française apprendrait une prononciation fausse.
            val langues: Map<Long, String> = if (modeErreurs) {
                memoDao.getAllProfilesOnce().associate { it.id to it.langue }
            } else {
                mapOf(profileId to profil?.langue.orEmpty())
            }
            fun carte(ligne: com.sankailife.core.data.db.entities.MemoLineEntity):
                FlashcardEngine.Carte {
                val (recto, verso) = FlashcardEngine.decouper(ligne.text)
                return FlashcardEngine.Carte(
                    ligne.id, recto, verso, ligne.box,
                    langue = langues[ligne.profileId].orEmpty(),
                    moduleId = ligne.profileId
                )
            }

            var cartes = lignes.map(::carte)
            // Les leurres viennent de tout le module, pas seulement des cartes
            // dues : une session courte n'offrirait pas assez de propositions
            // crédibles, et l'exercice se dégraderait en saisie systématique.
            reservoirLeurres = if (modeErreurs) {
                // On charge toutes les cartes des modules concernés pour avoir
                // assez de choix, puis ExerciceEngine garde uniquement celles
                // du même module que la question courante.
                val modules = lignes.map { it.profileId }.distinct()
                if (modules.isEmpty()) emptyList()
                else memoDao.getLinesForProfilesOnce(modules).map(::carte)
            } else {
                memoDao.getLinesOnce(profileId).map(::carte)
            }

            // Session guidee : le plan decide de l'ordre, des repetitions et de
            // la forme de chaque exercice. Il travaille sur toutes les cartes de
            // l'unite, pas seulement celles qui sont dues — une unite qu'on
            // ouvre doit se travailler, meme si rien n'est encore echu.
            var uniteEnCours: Pair<Long, String>? = null
            if (uniteId != null && !modeErreurs) {
                val module = learningRepo.moduleDuProfil(profileId, creer = true)
                val plan = module?.let { learningRepo.preparerSession(it, uniteId) }
                if (plan != null && !plan.vide) {
                    val toutes = memoDao.getLinesOnce(profileId).map(::carte)
                        .associateBy { it.id }
                    val suite = plan.exercices.mapNotNull { ex ->
                        toutes[ex.carteId]?.let { it to ex.type }
                    }
                    cartes = suite.map { it.first }
                    typesPlanifies = suite.map { it.second }
                    uniteEnCours = module.id to uniteId
                }
            }

            _etat.value = EtatSession(
                chargement = false,
                nomModule = if (modeErreurs) "Mes erreurs"
                else profil?.name.orEmpty().ifBlank { "Mémo" },
                cartes = cartes,
                terminee = cartes.isEmpty(),
                messageFin = when {
                    cartes.isNotEmpty() -> ""
                    modeErreurs -> "Aucune carte ne te résiste pour l'instant"
                    else -> "Aucune carte à réviser pour l'instant"
                },
                exercice = null
            ).avecExercice(0)
            uniteEnCours?.let { (moduleId, unite) ->
                sessionId = learningRepo.ouvrirSession(moduleId, unite)
            }
        }
    }

    /** Toutes les cartes du module, pour tirer des leurres crédibles. */
    private var reservoirLeurres: List<FlashcardEngine.Carte> = emptyList()

    /** Identifiant de la session enregistree, 0 quand il n'y en a pas. */
    private var sessionId: Long = 0L


    /**
     * Prepare une association autour d'une carte.
     *
     * Les compagnons viennent des **autres cartes de la session**, pas du
     * module entier : associer un mot de l'unite en cours a trois mots qu'on
     * n'a jamais vus ne teste rien, et l'exercice se resoudrait par
     * elimination.
     *
     * @return `null` quand la session ne fournit pas assez de paires.
     *   L'appelant retombe alors sur un exercice ordinaire plutot que
     *   d'afficher une association degradee.
     */
    private fun associationPour(
        ancreCarte: FlashcardEngine.Carte,
        cartes: List<FlashcardEngine.Carte>
    ): AssociationEngine.Etat? {
        val paire = { c: FlashcardEngine.Carte ->
            c.verso?.let { AssociationEngine.Paire(c.id, c.recto, it) }
        }
        val principale = paire(ancreCarte) ?: return null
        val compagnons = cartes.asSequence()
            .filter { it.id != ancreCarte.id && it.moduleId == ancreCarte.moduleId }
            .distinctBy { it.id }
            .mapNotNull(paire)
            .take(AssociationEngine.PAIRES - 1)
            .toList()

        // Graine tiree de la carte : l'ordre des colonnes ne doit pas changer a
        // chaque recomposition, sinon les mots sautent sous le doigt.
        return AssociationEngine.preparer(
            listOf(principale) + compagnons,
            kotlin.random.Random(ancreCarte.id)
        )
    }

    /**
     * Un toucher dans l'exercice d'association.
     *
     * Quand la derniere paire tombe, chaque carte impliquee recoit **son
     * propre** verdict : appariee du premier coup ou non. C'est tout l'interet
     * de cet exercice, et n'en juger qu'une gaspillerait les trois autres.
     */
    fun toucherAssociation(
        element: AssociationEngine.Element
    ) {
        val etat = _etat.value
        val avant = etat.association ?: return
        if (etat.reponseEnCours) return

        val apres = AssociationEngine.toucher(avant, element)
        if (apres == avant) return
        _etat.value = etat.copy(association = apres)
        if (!apres.termine) return

        _etat.value = _etat.value.copy(reponseEnCours = true)
        viewModelScope.launch {
            runCatching {
                val verdicts = AssociationEngine.verdicts(apres)
                val parId = etat.cartes.associateBy { it.id }
                var justes = 0

                verdicts.forEach { (carteId, juste) ->
                    val carte = parId[carteId]
                        ?: reservoirLeurres.firstOrNull { it.id == carteId }
                        ?: return@forEach
                    if (juste) justes++
                    val jugement = if (juste) FlashcardEngine.Jugement.CORRECT
                    else FlashcardEngine.Jugement.A_REVOIR
                    val boite = FlashcardEngine.boiteSuivante(carte.box, jugement)
                    memoDao.majEtatCarte(
                        id = carte.id, box = boite,
                        prochaine = FlashcardEngine.prochaineRevision(boite, jugement),
                        reussite = if (juste) 1 else 0
                    )
                    if (juste) {
                        learningRepo.oublierErreurs(carte.id)
                    } else {
                        learningRepo.noterErreur(
                            moduleId = carte.moduleId, carteId = carte.id,
                            type = TypeSession.MATCHING
                        )
                    }
                }

                val recompense = recompenseSession()
                if (recompense.xpParCarte > 0) userRepo.addXp(recompense.xpParCarte * justes)
                typesJoues += TypeSession.MATCHING

                // L'exercice compte pour **un** dans l'avancement, meme s'il
                // vient de juger quatre cartes : le compteur affiche des
                // exercices, et le voir sauter de quatre serait incomprehensible.
                avancer(justes == verdicts.size)
            }.onFailure {
                _etat.value = _etat.value.copy(reponseEnCours = false)
            }
        }
    }

    /** Passe a l'exercice suivant, ou termine la session. */
    private suspend fun avancer(reussi: Boolean) {
        val etat = _etat.value
        val suivant = etat.index + 1
        if (suivant >= etat.cartes.size) {
            terminerSession(
                etat.reussies + if (reussi) 1 else 0,
                etat.ratees + if (reussi) 0 else 1
            )
            return
        }
        _etat.value = etat.copy(
            index = suivant,
            versoVisible = false,
            correction = null,
            reponseAttendue = null,
            reponseEnCours = false,
            reussies = etat.reussies + if (reussi) 1 else 0,
            ratees = etat.ratees + if (reussi) 0 else 1
        ).avecExercice(suivant)
    }

    /**
     * Installe l'exercice de l'index donne : association ou question ordinaire.
     *
     * Une association qui ne peut pas se preparer — pas assez de paires —
     * retombe sur un exercice ordinaire. Sauter l'entree laisserait un blanc.
     */
    private fun EtatSession.avecExercice(index: Int): EtatSession {
        val carte = cartes.getOrNull(index) ?: return this
        if (typesPlanifies.getOrNull(index) == TypeSession.MATCHING) {
            associationPour(carte, cartes)?.let {
                return copy(association = it, exercice = null)
            }
        }
        return copy(association = null, exercice = exercicePour(carte, index))
    }

    /** Derniere reponse saisie, conservee pour decrire une faute. */
    private var derniereReponse: String = ""

    /** Types reellement joues, dans l'ordre. Sert a varier la session suivante. */
    private val typesJoues = mutableListOf<TypeSession>()

    /** Type de session correspondant a l'exercice affiche. */
    private fun typeJoue(
        exercice: ExerciceEngine.Exercice?
    ): TypeSession = when (exercice) {
        is ExerciceEngine.Exercice.Reconnaissance -> TypeSession.MULTIPLE_CHOICE
        is ExerciceEngine.Exercice.Saisie -> TypeSession.TYPING
        is ExerciceEngine.Exercice.TexteATrous -> TypeSession.FILL_IN_THE_BLANK
        is ExerciceEngine.Exercice.Ordre -> TypeSession.SENTENCE_ORDER
        else -> TypeSession.FLASHCARD
    }

    private fun exercicePour(
        carte: FlashcardEngine.Carte,
        index: Int
    ): ExerciceEngine.Exercice =
        ExerciceEngine.construire(
            carte = carte,
            autres = reservoirLeurres.filter { it.id != carte.id },
            forme = typesPlanifies.getOrNull(index)
                ?.let { com.sankailife.core.learning.domain.SessionPlanEngine.forme(it) }
        )

    /**
     * Valide une réponse écrite ou choisie.
     *
     * La correction est séparée du passage à la carte suivante : voir sa
     * faute et la bonne réponse est le moment où l'on apprend réellement,
     * enchaîner immédiatement le supprimerait.
     */
    fun valider(reponse: String) {
        val etat = _etat.value
        val exercice = etat.exercice ?: return
        if (etat.correction != null) return

        val juste = ExerciceEngine.corriger(exercice, reponse) ?: return
        derniereReponse = reponse
        _etat.value = etat.copy(
            correction = juste,
            versoVisible = true,
            reponseAttendue = if (juste) null else ExerciceEngine.reponseAttendue(exercice)
        )
    }

    fun revelerVerso() {
        _etat.value = _etat.value.copy(versoVisible = true)
    }

    /** Enregistre la réponse et passe à la carte suivante. */
    fun repondre(reussi: Boolean) = repondre(
        if (reussi) FlashcardEngine.Jugement.CORRECT else FlashcardEngine.Jugement.A_REVOIR
    )

    /**
     * Enregistre un jugement nuancé et passe à la carte suivante.
     *
     * Le glissement de l'écran arrive ici. Il modifie réellement la boîte et la
     * date de prochaine révision : une gestuelle qui ne ferait que jouer une
     * animation donnerait l'illusion d'un réglage sans en avoir l'effet.
     */
    fun repondre(jugement: FlashcardEngine.Jugement) {
        val etat = _etat.value
        val carte = etat.carteCourante ?: return
        if (etat.reponseEnCours || etat.terminee) return
        val reussi = jugement.reussi
        _etat.value = etat.copy(reponseEnCours = true)

        viewModelScope.launch {
            runCatching {
                val nouvelleBoite = FlashcardEngine.boiteSuivante(carte.box, jugement)
                memoDao.majEtatCarte(
                    id = carte.id,
                    box = nouvelleBoite,
                    prochaine = FlashcardEngine.prochaineRevision(nouvelleBoite, jugement),
                    reussite = if (reussi) 1 else 0
                )
                val recompense = recompenseSession()
                if (recompense.xpParCarte > 0) userRepo.addXp(recompense.xpParCarte)

                // Trace datee de la faute. Les boites de Leitner disent qu'une
                // carte resiste ; elles ne disent ni quand ni sur quel exercice.
                // C'est cette difference qui permettra d'expliquer une revision
                // au lieu de l'imposer.
                //
                // La colonne moduleId recoit l'identifiant du profil Memo : un
                // module enveloppe exactement un profil, et le profil existe
                // toujours alors que le module peut ne pas encore avoir ete cree.
                val formeJouee = etat.exercice
                typesJoues += typeJoue(formeJouee)
                if (reussi) {
                    learningRepo.oublierErreurs(carte.id)
                } else if (formeJouee !is ExerciceEngine.Exercice.Memoire) {
                    learningRepo.noterErreur(
                        moduleId = carte.moduleId,
                        carteId = carte.id,
                        type = typeJoue(formeJouee),
                        reponseDonnee = derniereReponse
                    )
                }

                // Une carte ratée revient une fois en fin de session. Une seule
                // reprise empêche de fabriquer une session infinie (et de l'XP
                // infinie) en échouant volontairement.
                val cartes = if (
                    jugement == FlashcardEngine.Jugement.A_REVOIR &&
                    cartesReintroduites.add(carte.id)
                ) {
                    // La carte reintroduite n'herite pas d'une forme imposee :
                    // la reproposer sous le meme exercice qu'on vient de rater
                    // ferait rejouer l'echec a l'identique.
                    typesPlanifies = typesPlanifies + listOf(null)
                    etat.cartes + carte.copy(box = nouvelleBoite)
                } else etat.cartes

                val suivant = etat.index + 1
                if (suivant >= cartes.size) {
                    terminerSession(
                        etat.reussies + if (reussi) 1 else 0,
                        etat.ratees + if (reussi) 0 else 1
                    )
                } else {
                    _etat.value = etat.copy(
                        cartes = cartes,
                        index = suivant,
                        versoVisible = false,
                        correction = null,
                        reponseAttendue = null,
                        reponseEnCours = false,
                        reussies = etat.reussies + if (reussi) 1 else 0,
                        ratees = etat.ratees + if (reussi) 0 else 1
                    ).avecExercice(suivant)
                }
            }.onFailure {
                _etat.value = _etat.value.copy(reponseEnCours = false)
            }
        }
    }

    private suspend fun terminerSession(reussies: Int, ratees: Int) {
        // Cloture de la session enregistree, quand il y en a une.
        //
        // C'est elle qui alimente deux choses visibles : la regularite de la
        // semaine sur l'accueil, et la variete des sessions suivantes — sans
        // savoir ce qui vient d'etre joue, le planificateur reproposerait les
        // memes exercices demain.
        if (sessionId > 0L) {
            runCatching {
                learningRepo.cloturerSession(
                    id = sessionId,
                    faits = reussies + ratees,
                    reussis = reussies,
                    types = typesJoues.toList()
                )
            }
            sessionId = 0L
        }

        val recompense = recompenseSession()
        if (recompense.xpFin > 0) userRepo.addXp(recompense.xpFin)
        if (recompense.piecesFin > 0) userRepo.addCoins(recompense.piecesFin)

        _etat.value = _etat.value.copy(
            terminee = true,
            reussies = reussies,
            ratees = ratees,
            reponseEnCours = false,
            messageFin = if (profileId == PROFIL_ERREURS) {
                app.getString(R.string.flashcards_training_complete)
            } else {
                app.resources.getQuantityString(
                    R.plurals.flashcards_session_complete,
                    reussies + ratees,
                    reussies + ratees
                )
            }
        )
    }

    private fun recompenseSession(): FlashcardEngine.RecompenseSession =
        FlashcardEngine.recompense(
            if (profileId == PROFIL_ERREURS) FlashcardEngine.ModeSession.ENTRAINEMENT_ERREURS
            else FlashcardEngine.ModeSession.REVISION_ECHEANCES
        )

    fun rejouer() {
        val id = profileId
        _etat.value = EtatSession()
        profileId = -1L
        demarrer(id)
    }

    companion object {
        /**
         * Identifiant sentinelle : « toutes mes erreurs », tous modules confondus.
         *
         * La route de navigation ne transporte qu'un identifiant de module. Une
         * valeur négative ne peut jamais entrer en collision avec une clé Room,
         * qui est auto-incrémentée à partir de 1 — c'est ce qui rend la
         * sentinelle sûre plutôt qu'astucieuse.
         */
        const val PROFIL_ERREURS = -2L

        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { FlashcardsViewModel(app) }
        }
    }
}
