package com.kintsugirun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkoutEditorViewModel(
    private val repository: WorkoutRepository,
    private val fileName: String?
) : ViewModel() {

    private val _workout = MutableStateFlow<Workout?>(null)
    val workout: StateFlow<Workout?> = _workout.asStateFlow()

    init {
        loadWorkout()
    }

    private fun loadWorkout() {
        viewModelScope.launch {
            if (fileName != null) {
                _workout.value = repository.getWorkoutByFileName(fileName)
            } else {
                _workout.value = Workout(
                    workoutName = "New Workout",
                    warmupSeconds = 300,
                    runSeconds = 120,
                    walkSeconds = 60,
                    repeats = 5,
                    cooldownSeconds = 300
                )
            }
        }
    }

    fun updateWorkoutName(name: String) {
        _workout.update { it?.copy(workoutName = name) }
    }

    fun updateWarmupSeconds(seconds: Int) {
        _workout.update { it?.copy(warmupSeconds = seconds) }
    }

    fun updateRunSeconds(seconds: Int) {
        _workout.update { it?.copy(runSeconds = seconds) }
    }

    fun updateWalkSeconds(seconds: Int) {
        _workout.update { it?.copy(walkSeconds = seconds) }
    }

    fun updateRepeats(repeats: Int) {
        _workout.update { it?.copy(repeats = repeats) }
    }

    fun updateCooldownSeconds(seconds: Int) {
        _workout.update { it?.copy(cooldownSeconds = seconds) }
    }

    suspend fun saveWorkout() {
        val currentWorkout = _workout.value ?: return
        repository.saveWorkout(currentWorkout, fileName)
    }
}
