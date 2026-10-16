package com.kintsugirun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WorkoutReviewViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {

    private val _workout = MutableStateFlow<Workout?>(null)
    val workout: StateFlow<Workout?> = _workout.asStateFlow()

    private val _latestSession = MutableStateFlow<WorkoutSession?>(null)
    val latestSession: StateFlow<WorkoutSession?> = _latestSession.asStateFlow()

    private var sessionJob: Job? = null

    fun loadWorkout(id: Long) {
        viewModelScope.launch {
            _workout.value = repository.getWorkoutById(id)
        }
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            repository.getLatestSessionForWorkout(id).collect { session ->
                _latestSession.value = session
            }
        }
    }
}
