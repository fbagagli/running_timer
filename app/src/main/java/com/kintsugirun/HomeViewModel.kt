package com.kintsugirun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: WorkoutRepository) : ViewModel() {

    val workouts: StateFlow<List<Workout>> = repository.allWorkouts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            repository.seedDefaultWorkoutsIfEmpty()
        }
    }

    fun deleteWorkout(id: Long) {
        viewModelScope.launch {
            repository.deleteWorkout(id)
        }
    }

    fun saveWorkout(workout: Workout) {
        viewModelScope.launch {
            repository.saveWorkout(workout)
        }
    }
}
