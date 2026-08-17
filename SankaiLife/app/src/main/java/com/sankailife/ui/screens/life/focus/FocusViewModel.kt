package com.sankailife.ui.screens.life.focus

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sankailife.SankaiApplication
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.domain.engine.FocusRewardEngine
import com.sankailife.core.notifications.NotificationCategory
import com.sankailife.core.notifications.NotificationPolicy
import com.sankailife.core.notifications.SankaiNotifications
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class TimerState { IDLE, RUNNING, PAUSED, FINISHED }

class FocusViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SankaiApplication
    private val userRepo = UserRepository(app.database)

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
    private var deadlineElapsedMillis: Long = 0L
    private var completionInProgress = false
    private var screenActive = false

    private val _sessionDone  = MutableStateFlow(false)
    val sessionDone: StateFlow<Boolean> = _sessionDone

    val progress: StateFlow<Float> = combine(_remaining, _totalSecs) { rem, tot ->
        if (tot > 0) 1f - (rem.toFloat() / tot) else 0f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0f)

    fun setHours(h: Int)   { if (_state.value == TimerState.IDLE && !completionInProgress) { _hours.value = h;   updateTotal() } }
    fun setMinutes(m: Int) { if (_state.value == TimerState.IDLE && !completionInProgress) { _minutes.value = m; updateTotal() } }

    private fun updateTotal() {
        val secs = (_hours.value * 3600L + _minutes.value * 60L).coerceAtLeast(60L)
        _totalSecs.value = secs
        _remaining.value = secs
    }

    fun start() {
        if (completionInProgress || (_state.value != TimerState.IDLE && _state.value != TimerState.PAUSED)) return
        timerJob?.cancel()
        deadlineElapsedMillis = SystemClock.elapsedRealtime() + _remaining.value * 1_000L
        _state.value = TimerState.RUNNING
        timerJob = viewModelScope.launch {
            while (isActive && _state.value == TimerState.RUNNING) {
                val millisRestants = (deadlineElapsedMillis - SystemClock.elapsedRealtime())
                    .coerceAtLeast(0L)
                _remaining.value = (millisRestants + 999L) / 1_000L
                if (millisRestants == 0L) {
                    completeSession()
                    return@launch
                }
                // Une deadline monotone évite la dérive de `delay(1000)` en
                // cas de frame lente ou de mise en veille légère.
                delay(millisRestants.coerceAtMost(250L))
            }
        }
    }

    fun pause()  {
        if (completionInProgress || _state.value != TimerState.RUNNING) return
        val millisRestants = (deadlineElapsedMillis - SystemClock.elapsedRealtime())
            .coerceAtLeast(0L)
        _remaining.value = (millisRestants + 999L) / 1_000L
        timerJob?.cancel()
        _state.value = TimerState.PAUSED
    }
    fun resume() { start() }

    fun stop()   {
        if (completionInProgress) return
        timerJob?.cancel()
        deadlineElapsedMillis = 0L
        _state.value = TimerState.IDLE
        _remaining.value = _totalSecs.value
    }

    fun setScreenActive(active: Boolean) {
        screenActive = active
    }

    private suspend fun completeSession() {
        if (completionInProgress) return
        completionInProgress = true
        try {
            val sessionMin = (_totalSecs.value / 60).toInt()
            val recompense = FocusRewardEngine.pourMinutes(sessionMin)
            if (recompense.xp > 0) userRepo.addXp(recompense.xp)
            if (recompense.pieces > 0) userRepo.addCoins(recompense.pieces)
            app.database.userDao().addFocusMinutes(sessionMin)
            if (!screenActive && NotificationPolicy.tryAcquire(app, NotificationCategory.FOCUS)) {
                SankaiNotifications.afficherFinFocus(app, sessionMin)
            }
        } finally {
            deadlineElapsedMillis = 0L
            _remaining.value = 0L
            // Une nouvelle session n'est offerte qu'après la fin de toutes
            // les écritures de la précédente.
            _state.value = TimerState.FINISHED
            _sessionDone.value = true
            completionInProgress = false
        }
    }

    fun dismissSession() {
        if (completionInProgress) return
        timerJob?.cancel()
        _sessionDone.value = false
        _state.value = TimerState.IDLE
        _remaining.value = _totalSecs.value
    }

    fun formatTime(secs: Long): String {
        val h = secs / 3600; val m = (secs % 3600) / 60; val s = secs % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    companion object {
        fun factory(app: SankaiApplication) = viewModelFactory { initializer { FocusViewModel(app) } }
    }
}
