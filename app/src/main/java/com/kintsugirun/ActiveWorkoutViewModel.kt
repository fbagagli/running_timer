package com.kintsugirun

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ActiveWorkoutViewModel(
    private val repository: WorkoutRepository,
    private val timerManager: TimerManager,
    application: Application,
    private val gpsTracker: GpsTracker? = null
) : AndroidViewModel(application) {

    private val _workout = MutableStateFlow<Workout?>(null)
    val workout: StateFlow<Workout?> = _workout.asStateFlow()

    val timerState: StateFlow<TimerState> = timerManager.timerState

    val lastSavedSessionId: StateFlow<Long?> = timerManager.lastSavedSessionId

    val gpsMetrics: StateFlow<GpsMetrics> = gpsTracker?.metrics ?: MutableStateFlow(GpsMetrics()).asStateFlow()

    fun loadWorkout(id: Long) {
        viewModelScope.launch {
            _workout.value = repository.getWorkoutById(id)
        }
    }

    fun startTimer(announceRepetitions: Boolean = false) {
        val currentWorkout = _workout.value ?: return

        // Start Foreground Service
        val serviceIntent = Intent(getApplication(), TimerService::class.java).apply {
            putExtra(TimerNotificationHelper.EXTRA_WORKOUT_NAME, currentWorkout.workoutName)
            putExtra(TimerNotificationHelper.EXTRA_WORKOUT_ID, currentWorkout.id)
            putExtra(TimerNotificationHelper.EXTRA_ANNOUNCE_REPETITIONS, announceRepetitions)
        }
        ContextCompat.startForegroundService(getApplication(), serviceIntent)

        timerManager.start(currentWorkout, announceRepetitions)
    }

    fun pauseTimer() {
        timerManager.pause()
    }

    fun resumeTimer() {
        timerManager.resume()
    }

    fun nextStep() {
        timerManager.nextStep()
    }

    fun previousStep() {
        timerManager.previousStep()
    }

    fun stopTimer() {
        timerManager.stop()
        val serviceIntent = Intent(getApplication(), TimerService::class.java)
        getApplication<Application>().stopService(serviceIntent)
    }
}
