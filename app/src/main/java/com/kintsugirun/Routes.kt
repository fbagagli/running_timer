package com.kintsugirun

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
data class WorkoutReviewRoute(val fileName: String)

@Serializable
data class WorkoutEditorRoute(val fileName: String? = null)

@Serializable
data class ActiveWorkoutRoute(
    val fileName: String,
    val announceRepetitions: Boolean = false
)
