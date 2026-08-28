package com.kintsugirun

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WorkoutRepository(
    private val workoutDao: WorkoutDao
) {
    private val tag = "WorkoutRepository"

    val allWorkouts: Flow<List<Workout>> = workoutDao.getAllWorkouts()

    suspend fun getWorkoutById(id: Long): Workout? = withContext(Dispatchers.IO) {
        workoutDao.getWorkoutById(id)
    }

    suspend fun saveWorkout(workout: Workout): Long = withContext(Dispatchers.IO) {
        workoutDao.insertOrUpdate(workout)
    }

    suspend fun deleteWorkout(id: Long) = withContext(Dispatchers.IO) {
        workoutDao.deleteById(id)
    }

    suspend fun seedDefaultWorkoutsIfEmpty() = withContext(Dispatchers.IO) {
        if (workoutDao.getWorkoutCount() == 0) {
            workoutDao.insertOrUpdate(
                Workout(
                    workoutName = "Couch to 5K — Week 1",
                    warmupSeconds = 300,
                    runSeconds = 60,
                    walkSeconds = 90,
                    repeats = 8,
                    cooldownSeconds = 300
                )
            )
            workoutDao.insertOrUpdate(
                Workout(
                    workoutName = "Easy Intervals",
                    warmupSeconds = 300,
                    runSeconds = 120,
                    walkSeconds = 60,
                    repeats = 5,
                    cooldownSeconds = 300
                )
            )
            Log.d(tag, "Seeded default starter workouts")
        }
    }
}
