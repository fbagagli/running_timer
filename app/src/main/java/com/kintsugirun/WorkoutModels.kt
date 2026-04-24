package com.kintsugirun

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Workout(
    @SerialName("workout_name")
    val workoutName: String,

    @SerialName("warmup")
    val warmup: Warmup,

    @SerialName("sets")
    val sets: List<Set>,

    @SerialName("cooldown")
    val cooldown: Cooldown
)

@Serializable
data class Warmup(
    @SerialName("duration_seconds")
    val durationSeconds: Int,

    @SerialName("message")
    val message: String
)

@Serializable
data class Set(
    @SerialName("set_name")
    val setName: String,

    @SerialName("repeats")
    val repeats: Int,

    @SerialName("intervals")
    val intervals: List<Interval>
)

@Serializable
data class Interval(
    @SerialName("type")
    val type: String,

    @SerialName("duration_seconds")
    val durationSeconds: Int,

    @SerialName("message")
    val message: String
)

@Serializable
data class Cooldown(
    @SerialName("duration_seconds")
    val durationSeconds: Int,

    @SerialName("message")
    val message: String
)
