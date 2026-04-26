package com.kintsugirun

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

class WorkoutRepository(private val context: Context) {

    companion object {
        private val safeFileNameRegex = Regex("[^a-z0-9]")
    }

    private val tag = "WorkoutRepository"
    // Using ignoreUnknownKeys = true is generally a good practice for robustness
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getAllWorkouts(): List<Pair<String, Workout>> = withContext(Dispatchers.IO) {
        val workouts = mutableListOf<Pair<String, Workout>>()
        val filesDir = context.filesDir
        val jsonFiles = filesDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()

        for (file in jsonFiles) {
            try {
                val text = file.readText()
                val workout = json.decodeFromString<Workout>(text)
                workouts.add(Pair(file.name, workout))
            } catch (e: Exception) {
                Log.e(tag, "Error parsing file: ${file.name}", e)
            }
        }
        workouts
    }

    suspend fun getWorkoutByFileName(fileName: String): Workout? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                val text = file.readText()
                json.decodeFromString<Workout>(text)
            } else {
                Log.w(tag, "Workout file not found: $fileName")
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing file: $fileName", e)
            null
        }
    }

    suspend fun deleteWorkout(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    Log.d(tag, "Successfully deleted $fileName")
                } else {
                    Log.w(tag, "Failed to delete $fileName")
                }
                deleted
            } else {
                Log.w(tag, "File not found: $fileName")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Error deleting file: $fileName", e)
            false
        }
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

            // Attempt to deserialize to validate the format
            val workout = json.decodeFromString<Workout>(text)

            // Generate a safe file name based on workout name
            val safeFileName = workout.workoutName
                .lowercase()
                .replace(safeFileNameRegex, "_") + ".json"

            // Save the JSON string as a new file, overwriting if it exists
            val outFile = File(context.filesDir, safeFileName)
            outFile.writeText(text)

            Log.d(tag, "Successfully imported workout to $safeFileName")
            true
        } catch (e: Exception) {
            Log.e(tag, "Error importing workout from URI: $uri", e)
            false
        }
    }

    suspend fun saveWorkout(workout: Workout, originalFileName: String? = null) = withContext(Dispatchers.IO) {
        try {
            val fileName = originalFileName ?: (workout.workoutName
                .lowercase()
                .replace(safeFileNameRegex, "_") + ".json")

            val outFile = File(context.filesDir, fileName)
            val jsonString = json.encodeToString(workout)
            outFile.writeText(jsonString)
            Log.d(tag, "Successfully saved workout to $fileName")
        } catch (e: Exception) {
            Log.e(tag, "Error saving workout", e)
        }
    }
}
