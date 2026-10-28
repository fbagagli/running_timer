package com.kintsugirun

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
data class WorkoutReviewRoute(val workoutId: Long)

@Serializable
data class WorkoutEditorRoute(val workoutId: Long? = null)

@Serializable
data class ActiveWorkoutRoute(
    val workoutId: Long,
    val announceRepetitions: Boolean = false
)

@Serializable
data class SessionDetailRoute(val sessionId: Long)
