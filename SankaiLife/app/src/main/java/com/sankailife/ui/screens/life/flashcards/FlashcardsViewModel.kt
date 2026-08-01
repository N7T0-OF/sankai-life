package com.sankailife.ui.screens.life.flashcards

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.ExerciceEngine
import com.sankailife.core.domain.engine.ErreursEngine
import com.sankailife.core.domain.engine.FlashcardEngine
import com.sankailife.core.garden.data.GardenRepository
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
    private val gardenRepo = GardenRepository(app.database, userRepo)

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
        val reponseEnCours: Boolean = false
    ) {
        val carteCourante: FlashcardEngine.Carte? get() = cartes.getOrNull(index)
        val total: Int get() = cartes.size
        val progression: Float get() = if (total == 0) 0f else index.toFloat() / total
        val enAttenteDeValidation: Boolean get() = correction == null && !reponseEnCours
    }

    private val _etat = MutableStateFlow(EtatSession())
    val etat: StateFlow<EtatSession> = _etat.asStateFlow()

    private var profileId: Long = -1L
    private val cartesReintroduites = mutableSetOf<Long>()

    fun demarrer(profileId: Long) {
        if (this.profileId == profileId && !_etat.value.chargement) return
        this.profileId = profileId
        cartesReintroduites.clear()

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

            val cartes = lignes.map(::carte)
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
                exercice = cartes.firstOrNull()?.let { exercicePour(it) }
            )
        }
    }

    /** Toutes les cartes du module, pour tirer des leurres crédibles. */
    private var reservoirLeurres: List<FlashcardEngine.Carte> = emptyList()

    private fun exercicePour(carte: FlashcardEngine.Carte): ExerciceEngine.Exercice =
        ExerciceEngine.construire(
            carte = carte,
            autres = reservoirLeurres.filter { it.id != carte.id }
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

                // Une carte ratée revient une fois en fin de session. Une seule
                // reprise empêche de fabriquer une session infinie (et de l'XP
                // infinie) en échouant volontairement.
                val cartes = if (
                    jugement == FlashcardEngine.Jugement.A_REVOIR &&
                    cartesReintroduites.add(carte.id)
                ) {
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
                        exercice = cartes[suivant].let { exercicePour(it) },
                        reussies = etat.reussies + if (reussi) 1 else 0,
                        ratees = etat.ratees + if (reussi) 0 else 1
                    )
                }
            }.onFailure {
                _etat.value = _etat.value.copy(reponseEnCours = false)
            }
        }
    }

    private suspend fun terminerSession(reussies: Int, ratees: Int) {
        val recompense = recompenseSession()
        if (recompense.xpFin > 0) userRepo.addXp(recompense.xpFin)
        if (recompense.piecesFin > 0) userRepo.addCoins(recompense.piecesFin)

        // Boucle éducative : les révisions alimentent réellement le jardin.
        // Le nombre de cartes de la session sert de plafond aux gouttes, ce
        // qui empêche de convertir des révisions anticipées en eau infinie.
        val gain = if (recompense.alimenteJardin) runCatching {
            gardenRepo.crediterRevisions(
                bonnesReponses = reussies,
                cartesDues = _etat.value.total
            )
        }.getOrNull() else null

        val mentionEau = when {
            gain == null -> ""
            gain.eauCreditee > 0 -> " • +${gain.eauCreditee} 💧"
            gain.plafondAtteint -> " • réserve d'eau du jour complète"
            else -> ""
        }

        _etat.value = _etat.value.copy(
            terminee = true,
            reussies = reussies,
            ratees = ratees,
            reponseEnCours = false,
            messageFin = if (profileId == PROFIL_ERREURS) {
                "Entraînement terminé • progression mémorisée"
            } else {
                "+${recompense.xpFin} XP • +${recompense.piecesFin} 🪙$mentionEau"
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
