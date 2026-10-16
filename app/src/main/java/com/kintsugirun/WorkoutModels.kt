package com.kintsugirun

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workoutName: String,
    val warmupSeconds: Int = 300,
    val runSeconds: Int = 120,
    val walkSeconds: Int = 60,
    val repeats: Int = 5,
    val cooldownSeconds: Int = 300
)

@Serializable
@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workoutId: Long,
    val startedAtTimestamp: Long,
    val totalElapsedSeconds: Int,
    val isCompleted: Boolean,
    val interruptedPhase: String? = null,
    @Embedded(prefix = "workout_")
    val workoutSnapshot: Workout,
    val totalDistanceMeters: Double = 0.0,
    val runDistanceMeters: Double = 0.0,
    val walkDistanceMeters: Double = 0.0,
    val runPaceSecondsPerKm: Int = 0,
    val walkPaceSecondsPerKm: Int = 0,
    val gpsRouteJson: String? = null
)
