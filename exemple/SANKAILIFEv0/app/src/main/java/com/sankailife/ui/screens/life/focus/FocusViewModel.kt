package com.sankailife.ui.screens.life.focus

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.repository.GameRepository
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.XpEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class TimerState { IDLE, RUNNING, PAUSED, FINISHED }

class FocusViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SankaiApplication
    val userRepo = UserRepository(app.database)
    val gameRepo = GameRepository(app.database)

    private val _state        = MutableStateFlow(TimerState.IDLE)
    val timerState: StateFlow<TimerState> = _state

    private val _totalSecs    = MutableStateFlow(25 * 60L)
    val totalSecs: StateFlow<Long> = _totalSecs

    private val _remaining    = MutableStateFlow(25 * 60L)
    val remaining: StateFlow<Long> = _remaining

    private val _hours        = MutableStateFlow(0)
    val hours: StateFlow<Int> = _hours

    private val _minutes      = MutableStateFlow(25)
    val minutes: StateFlow<Int> = _minutes

    private var timerJob: Job? = null

    private val _sessionDone  = MutableStateFlow(false)
    val sessionDone: StateFlow<Boolean> = _sessionDone

    private val _earnedXp     = MutableStateFlow(0)
    val earnedXp: StateFlow<Int> = _earnedXp

    val progress: StateFlow<Float> = combine(_remaining, _totalSecs) { rem, tot ->
        if (tot > 0) 1f - (rem.toFloat() / tot) else 0f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0f)

    fun setHours(h: Int)   { if (_state.value == TimerState.IDLE) { _hours.value = h;   updateTotal() } }
    fun setMinutes(m: Int) { if (_state.value == TimerState.IDLE) { _minutes.value = m; updateTotal() } }

    private fun updateTotal() {
        val secs = (_hours.value * 3600L + _minutes.value * 60L).coerceAtLeast(60L)
        _totalSecs.value = secs
        _remaining.value = secs
    }

    fun start() {
        if (_state.value != TimerState.IDLE && _state.value != TimerState.PAUSED) return
        _state.value = TimerState.RUNNING
        timerJob = viewModelScope.launch {
            while (_remaining.value > 0 && _state.value == TimerState.RUNNING) {
                delay(1000)
                if (_state.value == TimerState.RUNNING) _remaining.value--
            }
            if (_remaining.value <= 0) finish()
        }
    }

    fun pause()  { timerJob?.cancel(); _state.value = TimerState.PAUSED }
    fun resume() { start() }

    fun stop()   { timerJob?.cancel(); _state.value = TimerState.IDLE; _remaining.value = _totalSecs.value }

    private fun finish() = viewModelScope.launch {
        _state.value = TimerState.FINISHED
        val sessionMin = (_totalSecs.value / 60).toInt()
        val xp = if (sessionMin >= 45) XpEngine.XP_FOCUS_LONG else XpEngine.XP_FOCUS_25MIN
        _earnedXp.value = xp
        userRepo.addXp(xp)
        userRepo.addCoins(10)
        app.database.userDao().addFocusMinutes(sessionMin)
        gameRepo.addChest("COMMON")
        gameRepo.updateChallengeProgress("daily_focus",  1)
        gameRepo.updateChallengeProgress("weekly_focus", 1)
        _sessionDone.value = true
    }

    fun dismissSession() { _sessionDone.value = false; _state.value = TimerState.IDLE; _remaining.value = _totalSecs.value }

    fun formatTime(secs: Long): String {
        val h = secs / 3600; val m = (secs % 3600) / 60; val s = secs % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory { initializer { FocusViewModel(app) } }
    }
}
