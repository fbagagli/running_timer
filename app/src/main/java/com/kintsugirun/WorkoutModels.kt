package com.kintsugirun

import androidx.room.Entity
import androidx.room.PrimaryKey

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
