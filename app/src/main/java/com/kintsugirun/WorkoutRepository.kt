package com.kintsugirun

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class WorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val context: Context
) {
    private val tag = "WorkoutRepository"
    private val json = Json { ignoreUnknownKeys = true }

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

    suspend fun importWorkoutFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val text = contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }

            if (text == null) {
                Log.e(tag, "Failed to read content from URI: $uri")
                return@withContext false
            }

            val workout = json.decodeFromString<Workout>(text)
            // Ensure ID is 0 so Room generates a new unique ID
            val insertedId = workoutDao.insertOrUpdate(workout.copy(id = 0))
            Log.d(tag, "Successfully imported workout with id: $insertedId")
            true
        } catch (e: Exception) {
            Log.e(tag, "Error importing workout from URI: $uri", e)
            false
        }
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
