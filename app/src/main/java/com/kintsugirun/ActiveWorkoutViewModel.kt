package com.kintsugirun

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ActiveWorkoutViewModel(
    private val repository: WorkoutRepository,
    private val context: Context
) : ViewModel() {

    private val _workout = MutableStateFlow<Workout?>(null)
    val workout: StateFlow<Workout?> = _workout.asStateFlow()

    val timerState: StateFlow<TimerState> = TimerManager.timerState

    fun loadWorkout(fileName: String) {
        viewModelScope.launch {
            _workout.value = repository.getWorkoutByFileName(fileName)
        }
    }

    fun startTimer() {
        val currentWorkout = _workout.value ?: return

        // Start Foreground Service
        val serviceIntent = Intent(context, TimerService::class.java).apply {
            putExtra("WORKOUT_NAME", currentWorkout.workoutName)
        }
        context.startForegroundService(serviceIntent)

        TimerManager.start(currentWorkout)
    }

    fun pauseTimer() {
        TimerManager.pause()
    }

    fun resumeTimer() {
        TimerManager.resume()
    }

    fun stopTimer() {
        TimerManager.stop()
        val serviceIntent = Intent(context, TimerService::class.java)
        context.stopService(serviceIntent)
    }

    override fun onCleared() {
        super.onCleared()
        // If we want to ensure it stops if the viewmodel dies, we could call stopTimer here.
        // But for a foreground service timer, we might want it to survive.
        // Given the requirement to navigate back and stop, stopping it when explicitly requested is better.
    }
}
