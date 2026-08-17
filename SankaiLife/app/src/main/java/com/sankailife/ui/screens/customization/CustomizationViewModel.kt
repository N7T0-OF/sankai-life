package com.sankailife.ui.screens.customization

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.model.ALL_THEMES
import com.sankailife.core.domain.model.Theme
import com.sankailife.core.domain.model.UserState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Personnalisation : thèmes aujourd'hui, icônes et titres plus tard.
 *
 * Sortie du profil pour une raison simple : une collection complète affichée
 * en liste verticale transforme l'écran d'identité en catalogue, et les
 * éléments verrouillés y prennent plus de place que ceux réellement
 * utilisables.
 */
class CustomizationViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SankaiApplication
    private val userDao = app.database.userDao()
    private val userRepo = UserRepository(app.database)

    /** Onglets : les thèmes utilisables d'abord, les verrouillés ensuite. */
    enum class Categorie(val libelle: String) {
        OBTENUS("Obtenus"),
        A_DEBLOQUER("À débloquer")
    }

    data class ThemeUi(
        val theme: Theme,
        val debloque: Boolean,
        val equipe: Boolean,
        val conditionDeblocage: String
    )

    val user: StateFlow<UserState> = userRepo.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserState())

    /**
     * Les couleurs du telephone sont-elles actives ?
     *
     * Quand elles le sont, elles l'emportent sur l'accent du theme equipe. Ce
     * n'est pas un oubli mais un arbitrage — deux sources de couleur qui se
     * disputent le meme role donnent une interface sans identite — et l'ecran
     * doit le dire. Le taire recreerait exactement le defaut qu'on vient de
     * corriger : un theme equipe qui ne change rien, sans explication.
     */
    val couleursSysteme: StateFlow<Boolean> = app.preferences.couleursSysteme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _categorie = MutableStateFlow(Categorie.OBTENUS)
    val categorie: StateFlow<Categorie> = _categorie

    private val entite = userDao.getUser()

    val themes: StateFlow<List<ThemeUi>> =
        combine(entite, user) { e, u ->
            val debloques = (e?.unlockedThemeIds ?: "default").split(",").toSet()
            val equipe = e?.equippedThemeId ?: "default"

            ALL_THEMES.map { theme ->
                val ok = theme.unlockType == "default" ||
                         theme.id in debloques ||
                         (theme.unlockType == "level" && u.level >= theme.unlockLevel)
                ThemeUi(
                    theme = theme,
                    debloque = ok,
                    equipe = theme.id == equipe,
                    conditionDeblocage = when {
                        ok -> ""
                        theme.unlockType == "level" -> "Niveau ${theme.unlockLevel}"
                        else -> ""
                    }
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themesAffiches: StateFlow<List<ThemeUi>> =
        combine(themes, categorie) { liste, cat ->
            when (cat) {
                Categorie.OBTENUS -> liste.filter { it.debloque }
                Categorie.A_DEBLOQUER -> liste.filter { !it.debloque }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nombreObtenus: StateFlow<Int> = themes
        .map { liste -> liste.count { it.debloque } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Nom du thème actuellement équipé, pour la carte résumé du profil. */
    val nomThemeEquipe: StateFlow<String> = themes
        .map { liste -> liste.firstOrNull { it.equipe }?.theme?.name ?: "Default Or" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Default Or")

    fun choisirCategorie(c: Categorie) { _categorie.value = c }

    /**
     * Une palette gratuite, toujours disponible.
     *
     * Les palettes et les themes cosmetiques sont **deux mecanismes
     * differents** : une palette repeint toute l'interface, un theme ne change
     * que l'accent. Les afficher ensemble a quand meme du sens, parce que du
     * point de vue de qui regarde, la question est la meme — de quoi mon
     * application a-t-elle l'air. Les etiquettes disent laquelle fait quoi,
     * donc le regroupement n'induit personne en erreur.
     *
     * Elles restent en bas de la liste et ne se verrouillent jamais : ce sont
     * les deux seules garanties de pouvoir revenir a quelque chose de lisible.
     */
    data class PaletteUi(
        val id: String,
        val nom: String,
        val badge: String,
        val active: Boolean,
        val disponible: Boolean
    )

    val palettes: StateFlow<List<PaletteUi>> = couleursSysteme.map { systeme ->
        val dynamiqueDispo = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
        listOf(
            PaletteUi(
                id = "sankai", nom = "Sankai classique",
                badge = "Par défaut • Gratuit",
                active = !systeme || !dynamiqueDispo, disponible = true
            ),
            PaletteUi(
                id = "systeme", nom = "Couleurs du téléphone",
                badge = if (dynamiqueDispo) "Dynamique Android • Gratuit"
                else "Demande Android 12 ou plus récent",
                active = systeme && dynamiqueDispo, disponible = dynamiqueDispo
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun choisirPalette(id: String) = viewModelScope.launch {
        app.preferences.setCouleursSysteme(id == "systeme")
    }

    fun equiper(themeUi: ThemeUi) = viewModelScope.launch {
        // Un thème verrouillé reste verrouillé : la vérification vit ici et
        // pas seulement dans l'interface, pour qu'aucun chemin ne la contourne.
        if (!themeUi.debloque) return@launch
        userDao.updateTheme(themeUi.theme.id)
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { CustomizationViewModel(app) }
        }
    }
}
