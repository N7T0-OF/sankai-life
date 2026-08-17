package com.sankailife.ui.screens.life

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.calendar.CalendrierIntegration
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.ProgressSourceEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * La section Vie : ce qui accompagne la vraie vie, sans la recréer.
 *
 * Le calendrier Android fait l'action (l'utilisateur vit ses événements),
 * Sankai la transforme en progression symbolique : un événement terminé est
 * crédité une fois par jour, plafonné et dégressif via [ProgressSourceEngine].
 * Lecture seule, traitement local, jamais une modification du calendrier.
 */
class ModeVieViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication
    private val userRepo = UserRepository(app.database)

    data class Etat(
        val permissionAccordee: Boolean = false,
        /** Événements terminés aujourd'hui, tous (déjà crédités ou non). */
        val evenementsAujourdhui: Int = 0,
        /** XP crédité aujourd'hui par la source Calendrier, toutes occurrences. */
        val xpCalendrier: Int = 0,
        /** Gain de la dernière synchronisation, pour une ligne de retour. */
        val dernierGain: Int = 0,
        val chargement: Boolean = false
    )

    private val _etat = MutableStateFlow(Etat())
    val etat: StateFlow<Etat> = _etat.asStateFlow()

    /**
     * Relit le calendrier et crédite les événements terminés non encore
     * crédités aujourd'hui.
     *
     * Appelé à chaque ouverture de l'écran et après l'autorisation de
     * lecture. Un événement ne rapporte qu'une fois par jour ; l'anti-farm
     * plafonne la source quoi qu'il arrive.
     */
    fun rafraichir() {
        val permission = CalendrierIntegration.permissionAccordee(app)
        if (!permission) {
            _etat.value = Etat(permissionAccordee = false)
            return
        }
        _etat.value = _etat.value.copy(permissionAccordee = true, chargement = true)
        viewModelScope.launch {
            runCatching {
                val aujourdhui = LocalDate.now().toString()
                val evenements = CalendrierIntegration.evenementsTerminesAujourdhui(app)
                val deja = app.preferences.calendrierDejaCredites(aujourdhui)
                val aCrediter = CalendrierIntegration.aCrediter(deja, evenements)

                var gain = 0
                aCrediter.forEach {
                    gain += userRepo.addSourceXp(
                        ProgressSourceEngine.Source.CALENDRIER,
                        app.preferences
                    )
                }
                if (aCrediter.isNotEmpty()) {
                    app.preferences.calendrierCrediter(
                        aujourdhui,
                        aCrediter.map { e -> e.id }.toSet()
                    )
                }

                Etat(
                    permissionAccordee = true,
                    evenementsAujourdhui = evenements.size,
                    xpCalendrier = app.preferences.xpAccordeSource(
                        ProgressSourceEngine.Source.CALENDRIER.name,
                        aujourdhui
                    ),
                    dernierGain = gain,
                    chargement = false
                )
            }.onFailure {
                _etat.value = _etat.value.copy(chargement = false)
            }
        }
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { ModeVieViewModel(app) }
        }
    }
}
