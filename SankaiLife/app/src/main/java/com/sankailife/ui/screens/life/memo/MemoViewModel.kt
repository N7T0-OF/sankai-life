package com.sankailife.ui.screens.life.memo

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.db.entities.MemoLineEntity
import com.sankailife.core.data.db.entities.MemoProfileEntity
import com.sankailife.core.domain.engine.ErreursEngine
import com.sankailife.core.domain.engine.MemoEngine
import com.sankailife.core.domain.engine.PartageMemoEngine
import com.sankailife.core.data.repository.MemoActivationRepository
import com.sankailife.core.notifications.MemoAlarmScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MemoViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SankaiApplication
    private val dao = app.database.memoDao()
    private val activation = MemoActivationRepository(app.database)

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    val profiles: StateFlow<List<MemoProfileEntity>> =
        dao.getAllProfiles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Combien de cartes chaque module contient, et combien sont dues.
     *
     * L'horloge avance chaque minute tant que l'écran est observé. Une requête
     * Room ne se relance normalement que si la base change ; sans ce tick, une
     * carte arrivée à échéance pendant que l'application reste ouverte pouvait
     * demeurer absente du compteur indéfiniment.
     */
    private val tickDues = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val statsParModule: StateFlow<Map<Long, com.sankailife.core.data.db.dao.StatsModule>> =
        tickDues.flatMapLatest { maintenant -> dao.statsParModule(maintenant) }
            .map { liste -> liste.associateBy { it.profileId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _currentProfileId = MutableStateFlow(-1L)
    val currentProfileId: StateFlow<Long> = _currentProfileId

    private val _currentLines = MutableStateFlow<List<MemoLineEntity>>(emptyList())
    val currentLines: StateFlow<List<MemoLineEntity>> = _currentLines

    private val _profileName    = MutableStateFlow("")
    val profileName: StateFlow<String> = _profileName

    private val _frequency      = MutableStateFlow(1)
    val frequency: StateFlow<Int> = _frequency

    private val _hour           = MutableStateFlow(18)
    val hour: StateFlow<Int> = _hour

    private val _minute         = MutableStateFlow(0)
    val minute: StateFlow<Int> = _minute

    /** Jours actifs au format ISO : 1 = lundi … 7 = dimanche. */
    private val _activeDays     = MutableStateFlow(setOf(1, 2, 3, 4, 5, 6, 7))
    val activeDays: StateFlow<Set<Int>> = _activeDays

    private val _randomMode     = MutableStateFlow(false)
    val randomMode: StateFlow<Boolean> = _randomMode

    /** Plage aléatoire, en minutes depuis minuit. */
    private val _randomStart    = MutableStateFlow(9 * 60)
    val randomStart: StateFlow<Int> = _randomStart

    private val _randomEnd      = MutableStateFlow(21 * 60)
    val randomEnd: StateFlow<Int> = _randomEnd

    /** Langue du contenu, BCP-47. Vide = aucune, donc pas d'écoute proposée. */
    private val _langue         = MutableStateFlow("")
    val langue: StateFlow<String> = _langue

    private val _newLineText    = MutableStateFlow("")
    val newLineText: StateFlow<String> = _newLineText

    private val _sauvegardeEnCours = MutableStateFlow(false)
    val sauvegardeEnCours: StateFlow<Boolean> = _sauvegardeEnCours

    fun setNewLineText(t: String) { _newLineText.value = t }

    fun loadProfile(profileId: Long) = viewModelScope.launch {
        _currentProfileId.value = profileId
        val p = if (profileId > 0) dao.getProfile(profileId) else null

        _profileName.value = p?.name ?: ""
        _frequency.value   = p?.frequencyPerDay ?: 1
        _hour.value        = p?.scheduledHour ?: 18
        _minute.value      = p?.scheduledMinute ?: 0
        _randomMode.value  = p?.randomMode ?: false
        _randomStart.value = ((p?.randomStartHour ?: 9) * 60) + (p?.randomStartMinute ?: 0)
        _randomEnd.value   = ((p?.randomEndHour ?: 21) * 60) + (p?.randomEndMinute ?: 0)
        _langue.value      = p?.langue.orEmpty()
        _activeDays.value  = p?.activeDays
            ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 1..7 }?.toSet()
            ?.ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) }
            ?: setOf(1, 2, 3, 4, 5, 6, 7)

        _currentLines.value = dao.getLinesOnce(profileId.coerceAtLeast(0))
    }

    fun setName(n: String)      { _profileName.value = n }
    fun setFrequency(f: Int)    { _frequency.value = f.coerceIn(1, 6) }
    fun setHour(h: Int)         { _hour.value = ((h % 24) + 24) % 24 }
    fun setMinute(m: Int)       { _minute.value = ((m % 60) + 60) % 60 }
    fun setRandomMode(v: Boolean) { _randomMode.value = v }
    fun setRandomStart(minutes: Int) { _randomStart.value = ((minutes % 1440) + 1440) % 1440 }
    fun setRandomEnd(minutes: Int)   { _randomEnd.value = ((minutes % 1440) + 1440) % 1440 }
    fun setLangue(code: String)      { _langue.value = code.trim() }

    fun toggleDay(jour: Int) {
        val actuels = _activeDays.value.toMutableSet()
        if (!actuels.remove(jour)) actuels.add(jour)
        // Zéro jour actif rendrait le module muet sans l'expliquer : on refuse.
        if (actuels.isNotEmpty()) _activeDays.value = actuels
    }

    /**
     * Enregistre le profil et reprogramme ses alarmes.
     *
     * L'entité existante est relue puis copiée : la reconstruire de zéro
     * remettrait `isActive` à false et effacerait l'historique anti-répétition
     * à chaque modification d'horaire.
     */
    fun saveProfile(onSaved: () -> Unit = {}) {
        // Le verrou est pris avant de lancer la coroutine : deux appuis dans la
        // même frame ne peuvent pas programmer deux écritures ni deux retours.
        if (!_sauvegardeEnCours.compareAndSet(expect = false, update = true)) return

        viewModelScope.launch {
            val resultat = runCatching {
                val id = _currentProfileId.value
                val existant = if (id > 0) dao.getProfile(id) else null

                val entity = (existant ?: MemoProfileEntity()).copy(
                    id = if (id > 0) id else 0L,
                    name = _profileName.value.ifBlank { "Mémo" },
                    frequencyPerDay = _frequency.value,
                    scheduledHour = _hour.value,
                    scheduledMinute = _minute.value,
                    randomMode = _randomMode.value,
                    randomStartHour = _randomStart.value / 60,
                    randomStartMinute = _randomStart.value % 60,
                    randomEndHour = _randomEnd.value / 60,
                    randomEndMinute = _randomEnd.value % 60,
                    activeDays = _activeDays.value.sorted().joinToString(","),
                    langue = _langue.value
                )

                val newId = dao.upsertProfile(entity)
                if (id <= 0) _currentProfileId.value = newId

                // Sans cette reprogrammation, un changement d'horaire ne
                // prendrait effet qu'au prochain lancement de l'application.
                replanifier()
            }

            _sauvegardeEnCours.value = false
            resultat.fold(
                onSuccess = { onSaved() },
                onFailure = { _message.value = "Impossible d'enregistrer ce mémo" }
            )
        }
    }

    /**
     * Crée un profil vide et signale son identifiant.
     *
     * L'appelant enchaîne sur l'éditeur : sans cela, appuyer sur « + » ajoute
     * une ligne « Nouveau mémo » dans la liste et laisse l'utilisateur deviner
     * qu'il doit la rouvrir pour la remplir.
     */
    fun createNewProfile(onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        val newId = dao.upsertProfile(MemoProfileEntity(name = "Nouveau mémo"))
        _currentProfileId.value = newId
        _profileName.value = "Nouveau mémo"
        _currentLines.value = emptyList()
        onCreated(newId)
    }

    fun deleteProfile(profileId: Long) = viewModelScope.launch {
        val p = dao.getProfile(profileId) ?: return@launch
        MemoAlarmScheduler.annuler(app, profileId)
        dao.deleteAllLines(profileId)
        dao.deleteProfile(p)
    }

    fun addLine(text: String) = viewModelScope.launch {
        val pid = _currentProfileId.value.coerceAtLeast(0)
        if (text.isBlank() || pid <= 0) return@launch
        val idx = _currentLines.value.size
        dao.insertLine(MemoLineEntity(profileId = pid, text = text.trim(), orderIndex = idx))
        _currentLines.value = dao.getLinesOnce(pid)
        _newLineText.value = ""
    }

    fun deleteLine(line: MemoLineEntity) = viewModelScope.launch {
        dao.deleteLine(line)
        _currentLines.value = dao.getLinesOnce(_currentProfileId.value)
    }

    fun pasteFromClipboard(raw: String) = viewModelScope.launch {
        val pid = _currentProfileId.value
        if (pid <= 0) return@launch
        val cleaned = MemoEngine.cleanText(raw)
        val existingTexts = _currentLines.value.map { it.text }.toSet()
        var idx = _currentLines.value.size
        cleaned.filter { it !in existingTexts }.forEach { text ->
            dao.insertLine(MemoLineEntity(profileId = pid, text = text, orderIndex = idx++))
        }
        _currentLines.value = dao.getLinesOnce(pid)
    }

    /**
     * Combien de cartes résistent, tous modules confondus.
     *
     * Recalculé à la demande plutôt qu'observé : ce nombre ne bouge qu'après
     * une session de révision, et un flux Room se réveillerait à chaque
     * écriture de carte sans rien changer à l'affichage.
     */
    suspend fun nombreCartesDifficiles(): Int =
        ErreursEngine.selectionner(
            dao.cartesDifficiles(ErreursEngine.REVISIONS_MINIMUM).map {
                ErreursEngine.Historique(
                    id = it.id, texte = it.text, boite = it.box,
                    revisions = it.reviewCount, reussites = it.successCount
                )
            },
            limite = Int.MAX_VALUE
        ).size

    /**
     * Texte partageable d'un module.
     *
     * Passe par [PartageMemoEngine], qui garantit qu'aucune donnée personnelle
     * ne sort : pas d'identifiants, pas d'historique de réponses, pas de boîtes
     * de révision. Uniquement le contenu.
     */
    suspend fun texteAPartager(profileId: Long): String {
        val profil = dao.getProfile(profileId)
        val lignes = dao.getLinesOnce(profileId).map { it.text }
        return PartageMemoEngine.exporter(profil?.name.orEmpty(), lignes)
    }

    fun toggleProfile(profileId: Long, active: Boolean) = viewModelScope.launch {
        when (val resultat = activation.definirActif(profileId, active)) {
            MemoActivationRepository.Resultat.MisAJour -> replanifier()
            is MemoActivationRepository.Resultat.LimiteAtteinte ->
                _message.value = "${resultat.slots} slot(s) actif(s) maximum — achète un slot dans Mode Vie"
            MemoActivationRepository.Resultat.ProfilIntrouvable ->
                _message.value = "Ce profil mémo n'existe plus"
        }
    }

    fun messageAffiche() { _message.value = "" }

    private suspend fun replanifier() {
        runCatching { MemoAlarmScheduler.replanifierTout(app) }
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory { initializer { MemoViewModel(app) } }
    }
}
