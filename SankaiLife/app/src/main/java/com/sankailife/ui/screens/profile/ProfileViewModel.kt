package com.sankailife.ui.screens.profile

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.R
import com.sankailife.SankaiApplication
import com.sankailife.core.calendar.CalendrierIntegration
import com.sankailife.core.culture.CultureLocalState
import com.sankailife.core.data.db.entities.UserEntity
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.MemorisationEngine
import com.sankailife.core.domain.engine.RegularityEngine
import com.sankailife.core.domain.model.ALL_THEMES
import com.sankailife.core.domain.model.UserState
import com.sankailife.core.time.observedMinutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SankaiApplication
    private val userRepo = UserRepository(app.database)
    private val observedTime = observedMinutes().shareIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = 5_000,
            replayExpirationMillis = 0
        ),
        replay = 1
    )
    private val observedDay = observedTime
        .map { it.localDate }
        .distinctUntilChanged()

    val user: StateFlow<UserState> = userRepo.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserState())

    val rawUser: StateFlow<UserEntity?> = app.database.userDao().getUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val minimalMode: StateFlow<Boolean> = app.preferences.minimalMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val dailyMinutes: StateFlow<Int> = app.preferences.dailyMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    /**
     * L'état de la mémorisation, tous modules confondus.
     *
     * Les statistiques ne parlaient jusqu'ici que du jeu — XP, pièces, coffres.
     * Dans une application dont le but est d'apprendre, c'est la moitié qui
     * manquait.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val memorisation: StateFlow<MemorisationEngine.Etat> = observedTime
        .flatMapLatest { tick ->
            app.database.memoDao().statsMemorisation(
                tick.epochMillis,
                MemorisationEngine.BOITE_MAITRISEE
            )
        }
        .map {
            MemorisationEngine.Etat(
                total = it.total,
                maitrisees = it.maitrisees,
                dues = it.dues,
                revisions = it.revisions,
                reussites = it.reussites,
                entamees = it.entamees
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MemorisationEngine.Etat())

    data class Regularite(val sept: Int = 0, val trente: Int = 0, val quatreVingtDix: Int = 0)

    /** Progression de la langue principale, pour la carte de tête du profil. */
    data class LangueProgression(val libelle: String, val pourcentage: Int)

    /**
     * La langue principale, telle que déclarée par les modules : la première
     * langue non vide, dans l'ordre des modules. Aucune devinette — un module
     * sans langue déclarée ne compte pas.
     */
    private fun fluxLanguePrincipale(): Flow<String?> =
        app.database.learningDao().observerModules()
            .map { modules -> modules.asSequence().map { it.langue }.firstOrNull { it.isNotBlank() } }
            .distinctUntilChanged()

    /**
     * Progression de la langue principale (ex. « Portugais »), en pourcentage
     * de cartes maîtrisées. `null` tant qu'aucune langue n'est déclarée : la
     * carte n'est alors pas affichée, plutôt que de montrer un 0 % qui ne
     * veut rien dire.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val languePrincipale: StateFlow<LangueProgression?> = fluxLanguePrincipale()
        .flatMapLatest { langue ->
            if (langue == null) {
                flowOf(null)
            } else {
                app.database.memoDao().statsParLangue(
                    prefixe = langue.trim().lowercase().substringBefore('-'),
                    maintenant = System.currentTimeMillis(),
                    boiteMax = MemorisationEngine.BOITE_MAITRISEE
                ).map { stats ->
                    LangueProgression(
                        libelle = libelleLangue(langue),
                        pourcentage = if (stats.total > 0) {
                            (stats.maitrisees * 100 / stats.total).coerceIn(0, 100)
                        } else 0
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Une dimension de la « progression réelle » : une barre descriptive,
     * jamais une obligation. Chaque dimension raconte ce que l'utilisateur
     * fait déjà, à partir de données locales.
     */
    data class DimensionReelle(
        val emoji: String,
        val libelle: Int,
        val valeur: String,
        val progression: Float
    )

    /**
     * Progression réelle : cinq dimensions au lieu d'un seul niveau.
     *
     * Esprit (apprentissage), Culture (découvertes), Langues, Habitudes
     * (événements du calendrier terminés) et Vie (jours actifs). Les barres
     * sont bornées par des paliers doux — elles se remplissent par l'usage,
     * pas par la contrainte.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val progressionReelle: StateFlow<List<DimensionReelle>> = combine(
        memorisation,
        observedDay.flatMapLatest { jour ->
            flow {
                val userId = app.database.userDao().getUserOnce()?.id ?: 1L
                emit(CultureLocalState(app).history("user-$userId").size)
            }
        }.distinctUntilChanged(),
        app.database.learningDao().observerModules(),
        observedDay.flatMapLatest {
            // Les habitudes, c'est la vraie vie : les événements terminés du
            // calendrier (lecture seule). Zéro sans autorisation — rien n'est
            // inventé.
            flow { emit(CalendrierIntegration.evenementsTerminesAujourdhui(app).size) }
        }.distinctUntilChanged(),
        observedDay.flatMapLatest { jour ->
            app.database.dayRecordDao().getDepuis(jour.minusDays(29).toString())
        }
    ) { mem, decouvertes, modules, evenements, jours ->
        val langues = modules.map { it.langue }.filter { it.isNotBlank() }.distinct().size
        val joursActifs = jours.count { it.status == "SUCCESS" || it.status == "PARTIAL" }
        listOf(
            DimensionReelle(
                emoji = "🌱",
                libelle = R.string.progression_esprit,
                valeur = app.resources.getQuantityString(
                    R.plurals.progression_cards, mem.maitrisees, mem.maitrisees
                ),
                progression = if (mem.total > 0) mem.maitrisees.toFloat() / mem.total else 0f
            ),
            DimensionReelle(
                emoji = "📚",
                libelle = R.string.progression_culture,
                valeur = app.resources.getQuantityString(
                    R.plurals.progression_discoveries, decouvertes, decouvertes
                ),
                progression = decouvertes.coerceAtMost(14) / 14f
            ),
            DimensionReelle(
                emoji = "🌍",
                libelle = R.string.progression_languages,
                valeur = app.resources.getQuantityString(
                    R.plurals.progression_langs, langues, langues
                ),
                progression = langues.coerceAtMost(5) / 5f
            ),
            DimensionReelle(
                emoji = "⏱️",
                libelle = R.string.progression_habits,
                valeur = app.resources.getQuantityString(
                    R.plurals.progression_events, evenements, evenements
                ),
                progression = evenements.coerceAtMost(10) / 10f
            ),
            DimensionReelle(
                emoji = "🌿",
                libelle = R.string.progression_life,
                valeur = app.resources.getQuantityString(
                    R.plurals.progression_days, joursActifs, joursActifs
                ),
                progression = joursActifs.coerceAtMost(30) / 30f
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val regularite: StateFlow<Regularite> = observedDay
        .flatMapLatest { today ->
            // Une seule observation Room couvre les trois fenêtres. Elle se
            // réabonne au changement de jour afin de faire glisser la borne,
            // même quand le ViewModel reste vivant pendant plusieurs jours.
            app.database.dayRecordDao()
                .getDepuis(today.minusDays(89).toString())
                .map { records -> today to records }
        }
        .map { (today, records) ->
            fun percentage(days: Int): Int =
                (RegularityEngine.regularite(records, days, today) * 100).toInt()

            Regularite(
                sept = percentage(7),
                trente = percentage(30),
                quatreVingtDix = percentage(90)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Regularite())

    /**
     * Nom du thème équipé, pour la carte résumé.
     * La collection complète et l'équipement vivent dans CustomizationViewModel :
     * le profil n'affiche plus qu'un aperçu.
     */
    val nomThemeEquipe: StateFlow<String> = rawUser
        .map { e ->
            val id = e?.equippedThemeId ?: "default"
            ALL_THEMES.firstOrNull { it.id == id }?.name ?: "Default Or"
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Default Or")

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory { initializer { ProfileViewModel(app) } }
    }
}

/**
 * Nom lisible d'un code BCP-47 (« pt », « pt-BR », « es »…).
 *
 * L'interface vit en français, anglais et portugais ; les noms de langues
 * restent dans la langue du contenu — on apprend « Português », pas une
 * traduction. Un code inconnu s'affiche tel quel plutôt que d'inventer.
 */
private fun libelleLangue(code: String): String = when (code.trim().lowercase().substringBefore('-')) {
    "pt" -> "Português"
    "es" -> "Español"
    "en" -> "English"
    "fr" -> "Français"
    "de" -> "Deutsch"
    "it" -> "Italiano"
    "nl" -> "Nederlands"
    else -> code.trim()
}
