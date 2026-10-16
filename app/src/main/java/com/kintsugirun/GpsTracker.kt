package com.kintsugirun

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class GpsTracker(
    private val context: Context,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val locationManager: LocationManager? = try {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    } catch (_: Exception) {
        null
    }
) : LocationListener {

    private val _metrics = MutableStateFlow(GpsMetrics())
    val metrics: StateFlow<GpsMetrics> = _metrics.asStateFlow()

    private var isTracking = false
    private var isPaused = false
    private var currentPhaseName: String = ""
    private var lastLocation: Location? = null

    private var phaseStartTimeMs: Long = 0L
    private var phasePausedDurationMs: Long = 0L
    private var pauseStartTimeMs: Long = 0L

    fun isTracking(): Boolean = isTracking

    fun isPaused(): Boolean = isPaused

    fun startTracking(initialPhaseName: String = "") {
        isTracking = true
        isPaused = false
        currentPhaseName = initialPhaseName
        lastLocation = null
        phaseStartTimeMs = timeProvider.elapsedRealtime()
        phasePausedDurationMs = 0L
        pauseStartTimeMs = 0L
        _metrics.value = GpsMetrics()
        registerLocationUpdates()
    }

    fun pauseTracking() {
        if (!isTracking || isPaused) return
        isPaused = true
        pauseStartTimeMs = timeProvider.elapsedRealtime()
        lastLocation = null
    }

    fun resumeTracking() {
        if (!isTracking || !isPaused) return
        if (pauseStartTimeMs > 0L) {
            phasePausedDurationMs += timeProvider.elapsedRealtime() - pauseStartTimeMs
        }
        pauseStartTimeMs = 0L
        isPaused = false
        lastLocation = null
    }

    fun stopTracking(): GpsMetrics {
        if (!isTracking) return _metrics.value
        unregisterLocationUpdates()
        isTracking = false
        isPaused = false
        lastLocation = null
        return _metrics.value
    }

    fun updatePhase(newPhaseName: String) {
        currentPhaseName = newPhaseName
        phaseStartTimeMs = timeProvider.elapsedRealtime()
        phasePausedDurationMs = 0L
        pauseStartTimeMs = 0L
        _metrics.value = _metrics.value.copy(
            currentIntervalDistanceMeters = 0.0,
            currentIntervalPaceSecPerKm = 0
        )
    }

    fun recordLocation(location: Location, elapsedPhaseSeconds: Int? = null) {
        if (!isTracking || isPaused) return

        // 1. Filter out points with poor accuracy (> 30m)
        if (location.accuracy > 30f) {
            return
        }

        // 2. Calculate distance from last valid location
        val prevLocation = lastLocation
        var distanceToAdd = 0.0

        if (prevLocation != null) {
            val dist = try {
                prevLocation.distanceTo(location)
            } catch (_: Exception) {
                0f
            }
            if (dist >= 0f) {
                distanceToAdd = dist.toDouble()
            }
        }

        lastLocation = location

        val currentMetrics = _metrics.value
        val newTotalDistance = currentMetrics.totalDistanceMeters + distanceToAdd

        val isRun = currentPhaseName.contains("run", ignoreCase = true)
        val isWalk = currentPhaseName.contains("walk", ignoreCase = true)

        val newRunDistance = if (isRun) {
            currentMetrics.runDistanceMeters + distanceToAdd
        } else {
            currentMetrics.runDistanceMeters
        }

        val newWalkDistance = if (isWalk) {
            currentMetrics.walkDistanceMeters + distanceToAdd
        } else {
            currentMetrics.walkDistanceMeters
        }

        val newIntervalDistance = currentMetrics.currentIntervalDistanceMeters + distanceToAdd

        // 3. Calculate current interval pace in seconds per kilometer
        val phaseSeconds = elapsedPhaseSeconds ?: calculateElapsedPhaseSeconds()
        val newPace = if (newIntervalDistance > 0.0 && phaseSeconds > 0) {
            (phaseSeconds * 1000.0 / newIntervalDistance).roundToInt()
        } else {
            0
        }

        // 4. Create GpsPoint and append to points
        val point = GpsPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            timestamp = if (location.time > 0L) location.time else System.currentTimeMillis(),
            phaseName = currentPhaseName
        )

        _metrics.value = currentMetrics.copy(
            totalDistanceMeters = newTotalDistance,
            runDistanceMeters = newRunDistance,
            walkDistanceMeters = newWalkDistance,
            currentIntervalDistanceMeters = newIntervalDistance,
            currentIntervalPaceSecPerKm = newPace,
            points = currentMetrics.points + point
        )
    }

    private fun calculateElapsedPhaseSeconds(): Int {
        val now = timeProvider.elapsedRealtime()
        val activeMs = now - phaseStartTimeMs - phasePausedDurationMs
        return maxOf(0, (activeMs / 1000L).toInt())
    }

    override fun onLocationChanged(location: Location) {
        recordLocation(location)
    }

    private fun registerLocationUpdates() {
        try {
            val lm = locationManager ?: return
            val hasFine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasFine || hasCoarse) {
                val isGpsEnabled = try {
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                } catch (_: Exception) {
                    false
                }
                val provider = if (isGpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
                val looper = try {
                    Looper.getMainLooper()
                } catch (_: Throwable) {
                    null
                }
                if (looper != null) {
                    lm.requestLocationUpdates(provider, 1000L, 1f, this, looper)
                } else {
                    lm.requestLocationUpdates(provider, 1000L, 1f, this)
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun unregisterLocationUpdates() {
        try {
            locationManager?.removeUpdates(this)
        } catch (_: Exception) {
        }
    }
}
