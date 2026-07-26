package com.sankailife.ui.screens.life.memo

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.db.entities.MemoLineEntity
import com.sankailife.core.data.db.entities.MemoProfileEntity
import com.sankailife.core.domain.engine.MemoEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MemoViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SankaiApplication
    private val dao = app.database.memoDao()

    val profiles: StateFlow<List<MemoProfileEntity>> =
        dao.getAllProfiles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private val _newLineText    = MutableStateFlow("")
    val newLineText: StateFlow<String> = _newLineText

    fun setNewLineText(t: String) { _newLineText.value = t }

    fun loadProfile(profileId: Long) = viewModelScope.launch {
        _currentProfileId.value = profileId
        if (profileId > 0) {
            val p = dao.getProfile(profileId)
            _profileName.value = p?.name ?: ""
            _frequency.value   = p?.frequencyPerDay ?: 1
            _hour.value        = p?.scheduledHour ?: 18
            _minute.value      = p?.scheduledMinute ?: 0
        } else {
            _profileName.value = ""
            _frequency.value   = 1
            _hour.value        = 18
            _minute.value      = 0
        }
        dao.getLinesOnce(profileId.coerceAtLeast(0)).let { _currentLines.value = it }
    }

    fun setName(n: String)      { _profileName.value = n }
    fun setFrequency(f: Int)    { _frequency.value = f }
    fun setHour(h: Int)         { _hour.value = h }
    fun setMinute(m: Int)       { _minute.value = m }

    fun saveProfile() = viewModelScope.launch {
        val entity = MemoProfileEntity(
            id = if (_currentProfileId.value > 0) _currentProfileId.value else 0L,
            name = _profileName.value.ifBlank { "Mémo" },
            frequencyPerDay = _frequency.value,
            scheduledHour = _hour.value,
            scheduledMinute = _minute.value,
            isActive = false
        )
        val newId = dao.upsertProfile(entity)
        if (_currentProfileId.value <= 0) _currentProfileId.value = newId
    }

    fun createNewProfile() = viewModelScope.launch {
        val newId = dao.upsertProfile(MemoProfileEntity(name = "Nouveau mémo"))
        _currentProfileId.value = newId
        _profileName.value = "Nouveau mémo"
        _currentLines.value = emptyList()
    }

    fun deleteProfile(profileId: Long) = viewModelScope.launch {
        val p = dao.getProfile(profileId) ?: return@launch
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

    fun toggleProfile(profileId: Long, active: Boolean) = viewModelScope.launch {
        dao.setActive(profileId, active)
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory { initializer { MemoViewModel(app) } }
    }
}
