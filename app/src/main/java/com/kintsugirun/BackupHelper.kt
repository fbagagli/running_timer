package com.kintsugirun

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class KintsugiBackup(
    val version: Int = 1,
    val exportedAtTimestamp: Long = System.currentTimeMillis(),
    val workouts: List<Workout> = emptyList(),
    val sessions: List<WorkoutSession> = emptyList()
)

object BackupHelper {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportBackup(workouts: List<Workout>, sessions: List<WorkoutSession>): String {
        val backup = KintsugiBackup(
            version = 1,
            exportedAtTimestamp = System.currentTimeMillis(),
            workouts = workouts,
            sessions = sessions
        )
        return json.encodeToString(backup)
    }

    fun parseBackup(jsonString: String): KintsugiBackup? {
        return try {
            json.decodeFromString<KintsugiBackup>(jsonString)
        } catch (e: Exception) {
            null
        }
    }
}
