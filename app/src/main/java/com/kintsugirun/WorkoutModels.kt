package com.kintsugirun

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "workouts")
@Serializable
data class Workout(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @SerialName("workout_name")
    val workoutName: String,

    @SerialName("warmup_seconds")
    val warmupSeconds: Int = 300,

    @SerialName("run_seconds")
    val runSeconds: Int = 120,

    @SerialName("walk_seconds")
    val walkSeconds: Int = 60,

    @SerialName("repeats")
    val repeats: Int = 5,

    @SerialName("cooldown_seconds")
    val cooldownSeconds: Int = 300
)
