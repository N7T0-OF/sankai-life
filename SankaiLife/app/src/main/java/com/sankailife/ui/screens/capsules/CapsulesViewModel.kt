package com.sankailife.ui.screens.capsules

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.BuildConfig
import com.sankailife.R
import com.sankailife.SankaiApplication
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.ProgressSourceEngine
import com.sankailife.core.learning.AvailableLearningLanguages
import com.sankailife.core.culture.CulturePackStore
import com.sankailife.core.culture.CultureSelectionHistory
import com.sankailife.core.culture.DailyCultureEntry
import com.sankailife.core.culture.DailyDiscovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.LocalDate

data class CapsulesUiState(
    val loading: Boolean = true,
    val entry: DailyCultureEntry? = null,
    val detailsVisible: Boolean = false,
    val favorite: Boolean = false,
    val saveError: Boolean = false,
    val empty: Boolean = false,
    val loadError: Boolean = false,
    val importing: Boolean = false,
    val importMessage: String? = null
)

/**
 * Une seule capsule locale pour la journée.
 *
 * Le ViewModel ne possède aucune action « suivante » : changer de face,
 * ajouter un favori ou écrire une note ne peut jamais ouvrir un flux. Le
 * choix journalier est mis en cache par profil et date pour rester identique
 * même si les favoris changent après l'ouverture.
 */
class CapsulesViewModel(private val app: SankaiApplication) : ViewModel() {
    private val localState = com.sankailife.core.culture.CultureLocalState(app)
    private val localWrites = Mutex()

    /** Packs culturels locaux, importés depuis un fichier `.culturepack`. */
    private val packStore = CulturePackStore(
        root = File(app.filesDir, "culture"),
        appVersionCode = BuildConfig.VERSION_CODE
    )

    private val _state = MutableStateFlow(CapsulesUiState())
    val state: StateFlow<CapsulesUiState> = _state.asStateFlow()

    private var profileId: String = "user-1"

    init {
        load()
    }

    fun load() {
        if (_state.value.loading && _state.value.entry != null) return
        _state.value = CapsulesUiState(loading = true)
        viewModelScope.launch {
            try {
                val loaded = loadToday()
                _state.value = if (loaded == null) {
                    CapsulesUiState(loading = false, empty = true)
                } else {
                    CapsulesUiState(
                        loading = false,
                        entry = loaded.entry,
                        favorite = loaded.favorite,
                        saveError = !loaded.persisted
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = CapsulesUiState(loading = false, loadError = true)
            }
        }
    }

    fun toggleDetails() {
        val vaAfficher = !_state.value.detailsVisible
        _state.value = _state.value.copy(detailsVisible = vaAfficher)
        if (vaAfficher && _state.value.entry != null) {
            // La découverte est consommée quand elle est retournée : c'est le
            // moment où l'on a réellement lu la fiche. Le plafond de la source
            // (5 XP, une seule occurrence) garantit qu'elle ne rapporte qu'une
            // fois par jour, quel que soit le nombre d'allers-retours.
            viewModelScope.launch(Dispatchers.IO) {
                UserRepository(app.database).addSourceXp(
                    ProgressSourceEngine.Source.DECOUVERTE,
                    app.preferences
                )
            }
        }
    }

    fun toggleFavorite() {
        val entry = _state.value.entry ?: return
        val wanted = !_state.value.favorite
        _state.value = _state.value.copy(favorite = wanted, saveError = false)
        viewModelScope.launch(Dispatchers.IO) {
            val (persistedValue, saved) = localWrites.withLock {
                val latestValue = _state.value.favorite
                latestValue to localState.setFavorite(profileId, entry.id, latestValue)
            }
            if (!saved && _state.value.favorite == persistedValue) {
                _state.value = _state.value.copy(
                    favorite = !persistedValue,
                    saveError = true
                )
            }
        }
    }

    fun dismissSaveError() {
        _state.value = _state.value.copy(saveError = false)
    }

    fun dismissImportMessage() {
        _state.value = _state.value.copy(importMessage = null)
    }

    /**
     * Importe un pack culturel depuis le sélecteur de fichiers.
     *
     * Le pack est entièrement validé (schéma, droits, empreintes, limites)
     * avant d'être écrit : un fichier corrompu ou malveillant n'atteint jamais
     * le stockage. En cas de succès, la capsule du jour peut venir du nouveau
     * pack dès le prochain chargement.
     */
    fun importPack(uri: Uri) {
        _state.value = _state.value.copy(importing = true, importMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            val message = try {
                val stream: InputStream? = app.contentResolver.openInputStream(uri)
                if (stream == null) {
                    app.getString(R.string.pack_unreadable)
                } else {
                    stream.use { packStore.install(it) }
                    val nombre = packStore.states().filterIsInstance<CulturePackStore.State.Ready>().size
                    if (nombre <= 1) app.getString(R.string.pack_installed)
                    else app.getString(R.string.pack_installed_many, nombre)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                error.message ?: app.getString(R.string.pack_refused)
            }
            _state.value = _state.value.copy(importing = false, importMessage = message)
            load()
        }
    }

    private suspend fun loadToday(): LoadedCapsule? = withContext(Dispatchers.IO) {
        // Le catalogue partagé : exactement celui que la notification annonce.
        // Une seule source de vérité pour « quelle capsule aujourd'hui ? ».
        // Les langues disponibles chez l'utilisateur filtrent le catalogue :
        // jamais une capsule dans une langue non installée.
        val langues = AvailableLearningLanguages.pour(app.database)
        val catalogue = DailyDiscovery.catalogue(app).let { cat ->
            cat.copy(
                entries = cat.entries.filter {
                    it.languageCode in langues ||
                        it.languageCode.substringBefore('-') in langues
                }
            )
        }
        if (catalogue.entries.isEmpty()) return@withContext null
        val userId = app.database.userDao().getUserOnce()?.id ?: 1L
        profileId = "user-$userId"
        val today = LocalDate.now()
        val favorites = localState.favorites(profileId)
        val cachedId = localState.selection(profileId, today)
        val cached = catalogue.entries.firstOrNull { it.id == cachedId }
        val selected = cached ?: DailyDiscovery.duJour(
            context = app,
            profileId = profileId,
            history = localState.history(profileId),
            enabledLanguages = langues
        ) ?: return@withContext null

        val persisted = if (cached != null) true else {
            localState.saveSelection(profileId, today, selected.id)
        }
        LoadedCapsule(
            entry = selected,
            favorite = selected.id in favorites,
            persisted = persisted
        )
    }

    private data class LoadedCapsule(
        val entry: DailyCultureEntry,
        val favorite: Boolean,
        val persisted: Boolean
    )

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { CapsulesViewModel(app) }
        }
    }
}
