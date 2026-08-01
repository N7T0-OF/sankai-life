package com.sankailife.ui.screens.home

import android.app.Activity
import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.ads.RegarderPubUseCase
import com.sankailife.core.ads.ResultatPub
import com.sankailife.core.data.db.entities.ChestEntity
import com.sankailife.core.data.repository.GameRepository
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.ArenaEngine
import com.sankailife.core.domain.engine.ChestEngine
import com.sankailife.core.domain.engine.EconomyEngine
import com.sankailife.core.domain.engine.XpEngine
import com.sankailife.core.domain.model.UserState
import com.sankailife.ui.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication
    val userRepo  = UserRepository(app.database)
    val gameRepo  = GameRepository(app.database, app)

    val user: StateFlow<UserState> = userRepo.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserState())

    val chests: StateFlow<List<ChestEntity>> = gameRepo.activeChests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayXp    = MutableStateFlow(0)
    val todayCoins = MutableStateFlow(0)

    private val _showLevelUp    = MutableStateFlow(false)
    val showLevelUp: StateFlow<Boolean> = _showLevelUp

    private val _levelUpLevel   = MutableStateFlow(0)
    val levelUpLevel: StateFlow<Int> = _levelUpLevel

    private val _chestReward    = MutableStateFlow<ChestEngine.ChestReward?>(null)
    val chestReward: StateFlow<ChestEngine.ChestReward?> = _chestReward

    private val _toastMessage   = MutableStateFlow("")
    val toastMessage: StateFlow<String> = _toastMessage

    private val _adCooldown     = MutableStateFlow(0L)
    val adCooldown: StateFlow<Long> = _adCooldown

    private val pubUseCase = RegarderPubUseCase(userRepo, gameRepo)

    /** Sert uniquement à griser le bouton pub — le reste de l'écran marche hors ligne. */
    val isOnline: StateFlow<Boolean> = app.connectivity.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), app.connectivity.currentlyOnline())

    /**
     * Carte unique affichée en zone C de l'accueil.
     *
     * Une seule à la fois : montrer Mémo, Focus et Objectifs simultanément
     * remplirait l'écran sans aider à décider quoi faire.
     */
    data class ModuleContextuel(
        val titre: String,
        val ligne1: String,
        val ligne2: String,
        val libelleBouton: String,
        val route: String,
        val emoji: String
    )

    val moduleContextuel: StateFlow<ModuleContextuel> =
        combine(
            app.database.memoDao().getAllProfiles(),
            app.database.objectiveDao().countPending(),
            chests
        ) { memos, objectifsEnCours, coffres ->
            val coffrePret = coffres.firstOrNull { it.isReady }
            val memoActif = memos.firstOrNull { it.isActive && it.nextTriggerAtMillis > 0 }

            when {
                // Un coffre prêt est la seule chose qui se périme : priorité.
                coffrePret != null -> ModuleContextuel(
                    titre = "COFFRE PRÊT",
                    ligne1 = "Coffre ${coffrePret.type.lowercase()}",
                    ligne2 = "Ouvre-le depuis la barre ci-dessous",
                    libelleBouton = "Voir les coffres",
                    route = Screen.Home.route,
                    emoji = "🎁"
                )
                memoActif != null -> ModuleContextuel(
                    titre = "MÉMO ACTIF",
                    ligne1 = memoActif.name.ifBlank { "Mémo" },
                    ligne2 = "Prochaine notification : " + formaterHeure(memoActif.nextTriggerAtMillis),
                    libelleBouton = "Ouvrir",
                    route = Screen.Memo.route,
                    emoji = "📖"
                )
                objectifsEnCours > 0 -> ModuleContextuel(
                    titre = "OBJECTIFS",
                    ligne1 = "$objectifsEnCours en cours",
                    ligne2 = "Valide-en un pour gagner 30 XP",
                    libelleBouton = "Ouvrir",
                    route = Screen.Objectives.route,
                    emoji = "🎯"
                )
                // Repli : toujours proposer quelque chose plutôt qu'un vide.
                else -> ModuleContextuel(
                    titre = "FOCUS",
                    ligne1 = "25 minutes",
                    ligne2 = "Aucune session en cours",
                    libelleBouton = "Commencer",
                    route = Screen.Focus.route,
                    emoji = "⏱️"
                )
            }
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            ModuleContextuel("FOCUS", "25 minutes", "Aucune session en cours",
                "Commencer", Screen.Focus.route, "⏱️")
        )

    private fun formaterHeure(millis: Long): String =
        if (millis <= 0) "non planifiée"
        else java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("HH'h'mm"))

    /** Coffres prêts à ouvrir, pour le badge de l'onglet Accueil. */
    val coffresPrets: StateFlow<Int> = chests
        .map { liste -> liste.count { it.isReady } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Pastille de la carte de progression : récompenses d'arène en attente. */
    val arenesAReclamer: StateFlow<Int> =
        combine(user, app.database.arenaRewardDao().getReclamees()) { u, prises ->
            ArenaEngine.recompensesAReclamer(u.level, prises.toSet()).size
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            userRepo.ensureUser()
            userRepo.checkStreak()
            gameRepo.ensureDailyChallenges()
            gameRepo.ensureWeeklyChallenges()
            gameRepo.addDailyChest()
            // Update today's stats from db
        }
    }

    fun openChest(chestId: Long) = viewModelScope.launch {
        val reward = gameRepo.openChest(chestId) ?: return@launch
        userRepo.addCoins(reward.coins)
        userRepo.addGems(reward.gems)
        val didLevelUp = userRepo.addXp(reward.xp + XpEngine.XP_CHEST_OPEN)
        _chestReward.value = reward
        if (didLevelUp) {
            _levelUpLevel.value = user.value.level
            _showLevelUp.value = true
        }
    }

    fun dismissChestReward() { _chestReward.value = null }
    fun dismissLevelUp()     { _showLevelUp.value = false }

    fun watchAd(activity: Activity) = viewModelScope.launch {
        if (_adCooldown.value > 0) return@launch

        when (val resultat = pubUseCase.executer(activity, isOnline.value)) {
            is ResultatPub.Recompense -> {
                todayCoins.value += EconomyEngine.COINS_PER_AD
                showToast(resultat.message)
                // Le cooldown ne démarre qu'après une pub réellement regardée :
                // un échec réseau ne doit pas pénaliser le joueur.
                _adCooldown.value = EconomyEngine.AD_COOLDOWN_SEC.toLong()
                while (_adCooldown.value > 0) { delay(1000); _adCooldown.value-- }
            }
            is ResultatPub.Impossible -> showToast(resultat.message)
        }
    }

    private fun showToast(msg: String) = viewModelScope.launch {
        _toastMessage.value = msg
        delay(2500)
        _toastMessage.value = ""
    }

    fun formatChestTimer(chest: ChestEntity): String {
        val remaining = chest.unlocksAtMillis - System.currentTimeMillis()
        return ChestEngine.formatTimer(remaining.coerceAtLeast(0))
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { HomeViewModel(app) }
        }
    }
}
