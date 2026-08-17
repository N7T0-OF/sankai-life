package com.sankailife.ui.screens.capsules

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.BuildConfig
import com.sankailife.SankaiApplication
import com.sankailife.core.culture.CulturePackImporter
import com.sankailife.core.culture.CulturePackStore
import com.sankailife.core.culture.CultureSelectionHistory
import com.sankailife.core.culture.DailyCultureEntry
import com.sankailife.core.culture.DailyCultureSelectionRequest
import com.sankailife.core.culture.DailyCultureSelector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class CapsulesUiState(
    val loading: Boolean = true,
    val entry: DailyCultureEntry? = null,
    val detailsVisible: Boolean = false,
    val favorite: Boolean = false,
    val reflectionVisible: Boolean = false,
    val reflection: String = "",
    val savingReflection: Boolean = false,
    val reflectionSaved: Boolean = false,
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
    private val localState = CultureLocalState(app)
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
                        reflection = loaded.reflection,
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
        _state.value = _state.value.copy(detailsVisible = !_state.value.detailsVisible)
    }

    fun toggleReflection() {
        _state.value = _state.value.copy(
            reflectionVisible = !_state.value.reflectionVisible,
            reflectionSaved = false,
            saveError = false
        )
    }

    fun updateReflection(value: String) {
        _state.value = _state.value.copy(
            reflection = value.take(MAX_REFLECTION_LENGTH),
            reflectionSaved = false,
            saveError = false
        )
    }

    fun saveReflection() {
        val entry = _state.value.entry ?: return
        val reflection = _state.value.reflection.trim()
        _state.value = _state.value.copy(savingReflection = true, saveError = false)
        viewModelScope.launch(Dispatchers.IO) {
            val saved = localState.saveReflection(profileId, entry.id, reflection)
            _state.value = _state.value.copy(
                reflection = reflection,
                savingReflection = false,
                reflectionSaved = saved,
                saveError = !saved
            )
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
                    "Impossible de lire ce fichier."
                } else {
                    stream.use { packStore.install(it) }
                    val nombre = packStore.states().filterIsInstance<CulturePackStore.State.Ready>().size
                    if (nombre <= 1) "Pack installé." else "Pack installé ($nombre packs locaux)."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                error.message ?: "Pack refusé."
            }
            _state.value = _state.value.copy(importing = false, importMessage = message)
            load()
        }
    }

    private suspend fun loadToday(): LoadedCapsule? = withContext(Dispatchers.IO) {
        val packs = loadAllPacks()
        if (packs.isEmpty()) return@withContext null
        val userId = app.database.userDao().getUserOnce()?.id ?: 1L
        profileId = "user-$userId"
        val today = LocalDate.now()
        val favorites = localState.favorites(profileId)
        val cachedId = localState.selection(profileId, today)
        val cached = packs.firstOrNull { it.entry.id == cachedId }?.entry
        val selected = cached ?: DailyCultureSelector.select(
            entries = packs.map { it.entry },
            request = DailyCultureSelectionRequest(
                profileId = profileId,
                localDate = today,
                packVersion = packs.joinToString(",") { it.manifest }
                    .let { "v$it" },
                favoriteIds = favorites,
                history = localState.history(profileId)
            )
        ) ?: return@withContext null

        val persisted = if (cached != null) true else {
            localState.saveSelection(profileId, today, selected.id)
        }
        LoadedCapsule(
            entry = selected,
            favorite = selected.id in favorites,
            reflection = localState.reflection(profileId, selected.id),
            persisted = persisted
        )
    }

    /**
     * Toutes les capsules disponibles : le pack embarqué puis les packs
     * locaux importés.
     *
     * Les identifiants d'un pack local sont préfixés par son `id` afin que
     * deux packs ne puissent jamais entrer en collision, ni avec l'embarqué.
     */
    private fun loadAllPacks(): List<PackEntry> {
        val embarqué = runCatching { loadBundledPack() }.getOrNull() ?: return emptyList()
        val liste = mutableListOf<PackEntry>()
        liste += embarqué.entries.map {
            PackEntry(
                entry = it,
                manifest = "${embarqué.manifest.id}@${embarqué.manifest.version}"
            )
        }
        packStore.states().forEach { state ->
            if (state is CulturePackStore.State.Ready) {
                liste += state.pack.entries.map {
                    PackEntry(
                        entry = it.copy(id = "${state.pack.manifest.id}:${it.id}"),
                        manifest = "${state.pack.manifest.id}@${state.pack.manifest.version}"
                    )
                }
            }
        }
        return liste
    }

    private data class PackEntry(
        val entry: DailyCultureEntry,
        val manifest: String
    )

    /**
     * Reconstruit en mémoire l'archive source livrée dans les assets.
     * L'importeur commun contrôle ensuite le format comme pour un fichier
     * externe : l'asset d'exemple ne bénéficie d'aucun passe-droit.
     */
    private fun loadBundledPack() = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { zip ->
            val files = listAssetFiles(ASSET_ROOT)
            files.forEach { relativePath ->
                zip.putNextEntry(ZipEntry(relativePath).apply { time = 0L })
                app.assets.open("$ASSET_ROOT/$relativePath").use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        CulturePackImporter.inspect(bytes.toByteArray(), BuildConfig.VERSION_CODE)
    }

    private fun listAssetFiles(root: String): List<String> {
        fun visit(path: String): List<String> {
            val children = app.assets.list(path).orEmpty().sorted()
            if (children.isEmpty()) return listOf(path.removePrefix("$root/"))
            return children.flatMap { child -> visit("$path/$child") }
        }
        return app.assets.list(root).orEmpty().sorted().flatMap { visit("$root/$it") }
    }

    private data class LoadedCapsule(
        val entry: DailyCultureEntry,
        val favorite: Boolean,
        val reflection: String,
        val persisted: Boolean
    )

    companion object {
        private const val ASSET_ROOT = "culture/classics-fr-v1"
        private const val MAX_REFLECTION_LENGTH = 2_000

        fun factory(app: SankaiApplication) = viewModelFactory {
            initializer { CapsulesViewModel(app) }
        }
    }
}

/** Petit stockage privé, indépendant des réglages globaux de l'application. */
private class CultureLocalState(context: Context) {
    // noBackupFilesDir est volontaire : une réflexion personnelle ne doit
    // pas partir dans une sauvegarde cloud Android sous prétexte qu'elle est
    // stockée dans les préférences de l'application.
    private val stateFile = AtomicFile(
        File(context.noBackupFilesDir, "culture-state/state.properties")
    )

    fun favorites(profileId: String): Set<String> = read()
        .getProperty("favorites.$profileId", "")
        .split(',')
        .filterTo(linkedSetOf()) { it.isNotBlank() }

    fun setFavorite(profileId: String, entryId: String, favorite: Boolean): Boolean {
        val values = favorites(profileId).toMutableSet()
        if (favorite) values += entryId else values -= entryId
        return edit { properties ->
            if (values.isEmpty()) properties.remove("favorites.$profileId")
            else properties.setProperty("favorites.$profileId", values.sorted().joinToString(","))
        }
    }

    fun reflection(profileId: String, entryId: String): String =
        read().getProperty("reflection.$profileId.$entryId", "")

    fun saveReflection(profileId: String, entryId: String, value: String): Boolean {
        return edit { properties ->
            if (value.isBlank()) properties.remove("reflection.$profileId.$entryId")
            else properties.setProperty("reflection.$profileId.$entryId", value)
        }
    }

    fun selection(profileId: String, date: LocalDate): String? {
        val current = read().getProperty("selection.$profileId")
            ?.takeIf { it.startsWith("$date|") }
            ?.substringAfter('|')
        return current ?: history(profileId).firstOrNull { it.selectedOn == date }?.entryId
    }

    fun saveSelection(profileId: String, date: LocalDate, entryId: String): Boolean {
        val updated = (history(profileId).filterNot { it.selectedOn == date } +
            CultureSelectionHistory(entryId, date))
            .filter { !it.selectedOn.isBefore(date.minusDays(HISTORY_DAYS)) }
            .sortedByDescending { it.selectedOn }
        val encoded = updated.joinToString("\n") { "${it.selectedOn}|${it.entryId}" }
        return edit { properties ->
            properties.setProperty("selection.$profileId", "$date|$entryId")
            properties.setProperty("history.$profileId", encoded)
        }
    }

    fun history(profileId: String): List<CultureSelectionHistory> =
        read().getProperty("history.$profileId", "")
            .lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('|')
                if (separator <= 0 || separator == line.lastIndex) return@mapNotNull null
                val date = runCatching { LocalDate.parse(line.substring(0, separator)) }
                    .getOrNull() ?: return@mapNotNull null
                val entryId = line.substring(separator + 1)
                CultureSelectionHistory(entryId, date)
            }
            .toList()

    @Synchronized
    private fun read(): Properties {
        val properties = Properties()
        if (!stateFile.baseFile.isFile) return properties
        return runCatching {
            stateFile.openRead().use { input -> properties.load(input) }
            properties
        }.getOrDefault(Properties())
    }

    @Synchronized
    private fun edit(update: (Properties) -> Unit): Boolean {
        val parent = stateFile.baseFile.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        val properties = read()
        update(properties)
        val output = try {
            stateFile.startWrite()
        } catch (_: Exception) {
            return false
        }
        return try {
            properties.store(output, "Sankai Life culture state - local only")
            stateFile.finishWrite(output)
            true
        } catch (_: Exception) {
            runCatching { stateFile.failWrite(output) }
            false
        }
    }

    private companion object {
        const val HISTORY_DAYS = 90L
    }
}
