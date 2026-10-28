package com.kintsugirun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WorkoutReviewViewModel(
    private val repository: WorkoutRepository,
    val audioPreferences: AudioPreferences? = null
) : ViewModel() {

    private val _workout = MutableStateFlow<Workout?>(null)
    val workout: StateFlow<Workout?> = _workout.asStateFlow()

    fun loadWorkout(id: Long) {
        viewModelScope.launch {
            _workout.value = repository.getWorkoutById(id)
        }
    }
}
