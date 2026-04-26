package com.kintsugirun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
                val loaded = repository.getWorkoutByFileName(fileName)
                _workout.value = loaded
            } else {
                // Initialize default template
                _workout.value = Workout(
                    workoutName = "New Workout",
                    warmup = Warmup(durationSeconds = 0, message = ""),
                    sets = listOf(
                        Set(
                            setName = "New Set",
                            repeats = 1,
                            intervals = listOf(
                                Interval(type = "run", durationSeconds = 60, message = "Run")
                            )
                        )
                    ),
                    cooldown = Cooldown(durationSeconds = 0, message = "")
                )
            }
        }
    }

    fun updateWorkoutName(name: String) {
        _workout.update { current ->
            current?.copy(workoutName = name)
        }
    }

    fun updateWarmup(duration: Int, message: String) {
        _workout.update { current ->
            current?.copy(warmup = current.warmup.copy(durationSeconds = duration, message = message))
        }
    }

    fun updateCooldown(duration: Int, message: String) {
        _workout.update { current ->
            current?.copy(cooldown = current.cooldown.copy(durationSeconds = duration, message = message))
        }
    }

    fun addSet() {
        _workout.update { current ->
            current?.copy(
                sets = current.sets + Set(
                    setName = "New Set",
                    repeats = 1,
                    intervals = listOf()
                )
            )
        }
    }

    fun updateSetName(setIndex: Int, name: String) {
        _workout.update { current ->
            if (current == null) return@update null
            if (setIndex !in current.sets.indices) return@update current

            val updatedSets = current.sets.toMutableList()
            updatedSets[setIndex] = updatedSets[setIndex].copy(setName = name)

            current.copy(sets = updatedSets)
        }
    }

    fun updateSetRepeats(setIndex: Int, repeats: Int) {
        _workout.update { current ->
            if (current == null) return@update null
            if (setIndex !in current.sets.indices) return@update current

            val updatedSets = current.sets.toMutableList()
            updatedSets[setIndex] = updatedSets[setIndex].copy(repeats = repeats)

            current.copy(sets = updatedSets)
        }
    }

    fun addIntervalToSet(setIndex: Int) {
        _workout.update { current ->
            if (current == null) return@update null
            if (setIndex !in current.sets.indices) return@update current

            val updatedSets = current.sets.toMutableList()
            val targetSet = updatedSets[setIndex]

            val updatedIntervals = targetSet.intervals + Interval(
                type = "run",
                durationSeconds = 60,
                message = "Run"
            )

            updatedSets[setIndex] = targetSet.copy(intervals = updatedIntervals)

            current.copy(sets = updatedSets)
        }
    }

    fun updateInterval(setIndex: Int, intervalIndex: Int, name: String, duration: Int, message: String) {
        _workout.update { current ->
            if (current == null) return@update null
            if (setIndex !in current.sets.indices) return@update current

            val targetSet = current.sets[setIndex]
            if (intervalIndex !in targetSet.intervals.indices) return@update current

            val updatedSets = current.sets.toMutableList()
            val updatedIntervals = targetSet.intervals.toMutableList()

            updatedIntervals[intervalIndex] = updatedIntervals[intervalIndex].copy(
                type = name,
                durationSeconds = duration,
                message = message
            )

            updatedSets[setIndex] = targetSet.copy(intervals = updatedIntervals)

            current.copy(sets = updatedSets)
        }
    }

    suspend fun saveWorkout() {
        val currentWorkout = _workout.value ?: return
        repository.saveWorkout(currentWorkout, fileName)
    }

    class Factory(
        private val repository: WorkoutRepository,
        private val fileName: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WorkoutEditorViewModel::class.java)) {
                return WorkoutEditorViewModel(repository, fileName) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
