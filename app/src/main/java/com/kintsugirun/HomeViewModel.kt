package com.kintsugirun

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: WorkoutRepository) : ViewModel() {

    private val _workouts = MutableStateFlow<List<Pair<String, Workout>>>(emptyList())
    val workouts: StateFlow<List<Pair<String, Workout>>> = _workouts.asStateFlow()

    init {
        loadWorkouts()
    }

    fun loadWorkouts() {
        viewModelScope.launch {
            repository.initializeDefaultWorkoutIfNeeded()
            _workouts.value = repository.getAllWorkouts()
        }
    }

    fun deleteWorkout(fileName: String) {
        viewModelScope.launch {
            if (repository.deleteWorkout(fileName)) {
                loadWorkouts()
            }
        }
    }

    fun importWorkout(uri: Uri) {
        viewModelScope.launch {
            if (repository.importWorkoutFromUri(uri)) {
                loadWorkouts()
            }
        }
    }
}
