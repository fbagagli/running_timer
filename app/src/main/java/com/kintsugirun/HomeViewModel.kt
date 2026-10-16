package com.kintsugirun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: WorkoutRepository) : ViewModel() {

    data class ImportResult(val workoutsCount: Int, val sessionsCount: Int)

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

    suspend fun exportBackupJson(): String {
        val workouts = repository.getAllWorkoutsList()
        val sessions = repository.getAllSessionsList()
        return BackupHelper.exportBackup(workouts, sessions)
    }

    suspend fun importBackupJson(jsonString: String): Result<ImportResult> = runCatching {
        val backup = BackupHelper.parseBackup(jsonString)
            ?: throw IllegalArgumentException("Invalid backup JSON")

        val oldToNewIdMap = mutableMapOf<Long, Long>()
        backup.workouts.forEach { w ->
            val newId = repository.saveWorkout(w.copy(id = 0))
            oldToNewIdMap[w.id] = newId
        }
        backup.sessions.forEach { s ->
            val mappedWorkoutId = oldToNewIdMap[s.workoutId] ?: s.workoutId
            repository.saveSession(s.copy(id = 0, workoutId = mappedWorkoutId))
        }
        ImportResult(backup.workouts.size, backup.sessions.size)
    }
}

typealias ImportResult = HomeViewModel.ImportResult
