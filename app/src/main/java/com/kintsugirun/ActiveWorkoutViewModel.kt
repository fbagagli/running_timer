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
    application: Application
) : AndroidViewModel(application) {

    private val _workout = MutableStateFlow<Workout?>(null)
    val workout: StateFlow<Workout?> = _workout.asStateFlow()

    val timerState: StateFlow<TimerState> = timerManager.timerState

    fun loadWorkout(fileName: String) {
        viewModelScope.launch {
            _workout.value = repository.getWorkoutByFileName(fileName)
        }
    }

    fun startTimer(announceRepetitions: Boolean = false) {
        val currentWorkout = _workout.value ?: return

        // Start Foreground Service
        val serviceIntent = Intent(getApplication(), TimerService::class.java).apply {
            putExtra("WORKOUT_NAME", currentWorkout.workoutName)
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
