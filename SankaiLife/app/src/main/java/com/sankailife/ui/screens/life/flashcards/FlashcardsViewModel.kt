package com.sankailife.ui.screens.life.flashcards

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.ExerciceEngine
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
        val reponseAttendue: String? = null
    ) {
        val carteCourante: FlashcardEngine.Carte? get() = cartes.getOrNull(index)
        val total: Int get() = cartes.size
        val progression: Float get() = if (total == 0) 0f else index.toFloat() / total
        val enAttenteDeValidation: Boolean get() = correction == null
    }

    private val _etat = MutableStateFlow(EtatSession())
    val etat: StateFlow<EtatSession> = _etat.asStateFlow()

    private var profileId: Long = -1L

    fun demarrer(profileId: Long) {
        if (this.profileId == profileId && !_etat.value.chargement) return
        this.profileId = profileId

        viewModelScope.launch {
            val profil = memoDao.getProfile(profileId)
            val lignes = memoDao.getCartesDues(
                profileId = profileId,
                maintenant = System.currentTimeMillis(),
                limite = FlashcardEngine.CARTES_PAR_SESSION
            )
            val cartes = lignes.map { ligne ->
                val (recto, verso) = FlashcardEngine.decouper(ligne.text)
                FlashcardEngine.Carte(ligne.id, recto, verso, ligne.box)
            }
            // Les leurres viennent de tout le module, pas seulement des cartes
            // dues : une session courte n'offrirait pas assez de propositions
            // crédibles, et l'exercice se dégraderait en saisie systématique.
            reservoirLeurres = memoDao.getLinesOnce(profileId).map { ligne ->
                val (recto, verso) = FlashcardEngine.decouper(ligne.text)
                FlashcardEngine.Carte(ligne.id, recto, verso, ligne.box)
            }

            _etat.value = EtatSession(
                chargement = false,
                nomModule = profil?.name.orEmpty().ifBlank { "Mémo" },
                cartes = cartes,
                terminee = cartes.isEmpty(),
                messageFin = if (cartes.isEmpty()) "Aucune carte à réviser pour l'instant" else "",
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
    fun repondre(reussi: Boolean) {
        val etat = _etat.value
        val carte = etat.carteCourante ?: return

        viewModelScope.launch {
            val nouvelleBoite = FlashcardEngine.boiteSuivante(carte.box, reussi)
            memoDao.majEtatCarte(
                id = carte.id,
                box = nouvelleBoite,
                prochaine = FlashcardEngine.prochaineRevision(nouvelleBoite),
                reussite = if (reussi) 1 else 0
            )
            userRepo.addXp(FlashcardEngine.XP_PAR_CARTE)

            val suivant = etat.index + 1
            if (suivant >= etat.cartes.size) {
                terminerSession(etat.reussies + if (reussi) 1 else 0, etat.ratees + if (reussi) 0 else 1)
            } else {
                _etat.value = etat.copy(
                    index = suivant,
                    versoVisible = false,
                    correction = null,
                    reponseAttendue = null,
                    exercice = etat.cartes[suivant].let { exercicePour(it) },
                    reussies = etat.reussies + if (reussi) 1 else 0,
                    ratees = etat.ratees + if (reussi) 0 else 1
                )
            }
        }
    }

    private suspend fun terminerSession(reussies: Int, ratees: Int) {
        userRepo.addXp(FlashcardEngine.XP_SESSION_TERMINEE)
        userRepo.addCoins(FlashcardEngine.PIECES_SESSION_TERMINEE)

        // Boucle éducative : les révisions alimentent réellement le jardin.
        // Le nombre de cartes de la session sert de plafond aux gouttes, ce
        // qui empêche de convertir des révisions anticipées en eau infinie.
        val gain = runCatching {
            gardenRepo.crediterRevisions(
                bonnesReponses = reussies,
                cartesDues = _etat.value.total
            )
        }.getOrNull()

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
            messageFin = "+${FlashcardEngine.XP_SESSION_TERMINEE} XP • " +
                         "+${FlashcardEngine.PIECES_SESSION_TERMINEE} 🪙$mentionEau"
        )
    }

    fun rejouer() {
        val id = profileId
        _etat.value = EtatSession()
        profileId = -1L
        demarrer(id)
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { FlashcardsViewModel(app) }
        }
    }
}
