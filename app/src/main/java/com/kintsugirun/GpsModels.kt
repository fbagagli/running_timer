package com.kintsugirun

import kotlinx.serialization.Serializable

@Serializable
data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val phaseName: String = ""
)

@Serializable
data class GpsMetrics(
    val totalDistanceMeters: Double = 0.0,
    val runDistanceMeters: Double = 0.0,
    val walkDistanceMeters: Double = 0.0,
    val currentIntervalDistanceMeters: Double = 0.0,
    val currentIntervalPaceSecPerKm: Int = 0,
    val points: List<GpsPoint> = emptyList()
)
