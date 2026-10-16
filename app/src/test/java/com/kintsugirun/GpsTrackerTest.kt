package com.kintsugirun

import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class GpsTrackerTest {

    private lateinit var mockContext: Context
    private lateinit var mockLocationManager: LocationManager
    private lateinit var timeProvider: TestTimeProvider
    private lateinit var gpsTracker: GpsTracker

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockLocationManager = mockk(relaxed = true)
        timeProvider = TestTimeProvider(currentTimeMs = 1000L)
        gpsTracker = GpsTracker(
            context = mockContext,
            timeProvider = timeProvider,
            locationManager = mockLocationManager
        )
    }

    private fun createMockLocation(
        lat: Double = 45.0,
        lon: Double = 9.0,
        accuracy: Float = 10f,
        altitude: Double = 120.0,
        time: Long = 1000L
    ): Location {
        val loc = mockk<Location>(relaxed = true)
        every { loc.latitude } returns lat
        every { loc.longitude } returns lon
        every { loc.accuracy } returns accuracy
        every { loc.altitude } returns altitude
        every { loc.time } returns time
        return loc
    }

    @Test
    fun `accumulate distance for Run, Walk, and Total correctly`() {
        gpsTracker.startTracking(initialPhaseName = "Run")

        val loc1 = createMockLocation(lat = 45.0, lon = 9.0)
        gpsTracker.recordLocation(loc1)

        // First point establishes origin; distanceToAdd is 0
        var metrics = gpsTracker.metrics.value
        assertThat(metrics.totalDistanceMeters).isEqualTo(0.0)
        assertThat(metrics.runDistanceMeters).isEqualTo(0.0)
        assertThat(metrics.walkDistanceMeters).isEqualTo(0.0)
        assertThat(metrics.currentIntervalDistanceMeters).isEqualTo(0.0)
        assertThat(metrics.points).hasSize(1)
        assertThat(metrics.points[0].phaseName).isEqualTo("Run")

        // Second point in Run phase (120 meters)
        val loc2 = createMockLocation(lat = 45.001, lon = 9.0)
        every { loc1.distanceTo(loc2) } returns 120f
        gpsTracker.recordLocation(loc2)

        metrics = gpsTracker.metrics.value
        assertThat(metrics.totalDistanceMeters).isEqualTo(120.0)
        assertThat(metrics.runDistanceMeters).isEqualTo(120.0)
        assertThat(metrics.walkDistanceMeters).isEqualTo(0.0)
        assertThat(metrics.currentIntervalDistanceMeters).isEqualTo(120.0)
        assertThat(metrics.points).hasSize(2)

        // Switch to Walk phase and record third point (80 meters from loc2)
        gpsTracker.updatePhase("Walk")
        val loc3 = createMockLocation(lat = 45.002, lon = 9.0)
        every { loc2.distanceTo(loc3) } returns 80f
        gpsTracker.recordLocation(loc3)

        metrics = gpsTracker.metrics.value
        assertThat(metrics.totalDistanceMeters).isEqualTo(200.0)
        assertThat(metrics.runDistanceMeters).isEqualTo(120.0)
        assertThat(metrics.walkDistanceMeters).isEqualTo(80.0)
        assertThat(metrics.currentIntervalDistanceMeters).isEqualTo(80.0)
        assertThat(metrics.points).hasSize(3)
        assertThat(metrics.points[2].phaseName).isEqualTo("Walk")

        // Switch to Warmup (neither Run nor Walk) and record fourth point (50 meters from loc3)
        gpsTracker.updatePhase("Warmup")
        val loc4 = createMockLocation(lat = 45.003, lon = 9.0)
        every { loc3.distanceTo(loc4) } returns 50f
        gpsTracker.recordLocation(loc4)

        metrics = gpsTracker.metrics.value
        assertThat(metrics.totalDistanceMeters).isEqualTo(250.0)
        assertThat(metrics.runDistanceMeters).isEqualTo(120.0)
        assertThat(metrics.walkDistanceMeters).isEqualTo(80.0)
        assertThat(metrics.currentIntervalDistanceMeters).isEqualTo(50.0)
        assertThat(metrics.points).hasSize(4)
        assertThat(metrics.points[3].phaseName).isEqualTo("Warmup")
    }

    @Test
    fun `accumulate distance handles case-insensitive phase names`() {
        gpsTracker.startTracking(initialPhaseName = "Fast RUN 1 of 3")

        val loc1 = createMockLocation()
        gpsTracker.recordLocation(loc1)

        val loc2 = createMockLocation()
        every { loc1.distanceTo(loc2) } returns 100f
        gpsTracker.recordLocation(loc2)

        assertThat(gpsTracker.metrics.value.runDistanceMeters).isEqualTo(100.0)

        gpsTracker.updatePhase("power walk 1 of 3")
        val loc3 = createMockLocation()
        every { loc2.distanceTo(loc3) } returns 60f
        gpsTracker.recordLocation(loc3)

        assertThat(gpsTracker.metrics.value.walkDistanceMeters).isEqualTo(60.0)
        assertThat(gpsTracker.metrics.value.totalDistanceMeters).isEqualTo(160.0)
    }

    @Test
    fun `updatePhase resets currentIntervalDistanceMeters and currentIntervalPaceSecPerKm`() {
        gpsTracker.startTracking(initialPhaseName = "Run")

        val loc1 = createMockLocation()
        gpsTracker.recordLocation(loc1)

        val loc2 = createMockLocation()
        every { loc1.distanceTo(loc2) } returns 200f
        gpsTracker.recordLocation(loc2, elapsedPhaseSeconds = 60)

        assertThat(gpsTracker.metrics.value.currentIntervalDistanceMeters).isEqualTo(200.0)
        assertThat(gpsTracker.metrics.value.currentIntervalPaceSecPerKm).isEqualTo(300)

        // Update phase
        gpsTracker.updatePhase("Walk")

        val metricsAfterUpdate = gpsTracker.metrics.value
        assertThat(metricsAfterUpdate.currentIntervalDistanceMeters).isEqualTo(0.0)
        assertThat(metricsAfterUpdate.currentIntervalPaceSecPerKm).isEqualTo(0)
        assertThat(metricsAfterUpdate.totalDistanceMeters).isEqualTo(200.0)

        // New point in Walk phase
        val loc3 = createMockLocation()
        every { loc2.distanceTo(loc3) } returns 75f
        gpsTracker.recordLocation(loc3)

        assertThat(gpsTracker.metrics.value.currentIntervalDistanceMeters).isEqualTo(75.0)
        assertThat(gpsTracker.metrics.value.totalDistanceMeters).isEqualTo(275.0)
    }

    @Test
    fun `discard points with accuracy greater than 30 meters`() {
        gpsTracker.startTracking(initialPhaseName = "Run")

        val loc1 = createMockLocation(accuracy = 10f)
        gpsTracker.recordLocation(loc1)
        assertThat(gpsTracker.metrics.value.points).hasSize(1)

        // Inaccurate point (> 30m)
        val badLoc = createMockLocation(accuracy = 31f)
        gpsTracker.recordLocation(badLoc)

        // Inaccurate point should be discarded
        assertThat(gpsTracker.metrics.value.points).hasSize(1)
        assertThat(gpsTracker.metrics.value.totalDistanceMeters).isEqualTo(0.0)

        val anotherBadLoc = createMockLocation(accuracy = 50f)
        gpsTracker.recordLocation(anotherBadLoc)
        assertThat(gpsTracker.metrics.value.points).hasSize(1)

        // Valid point arrives (calculated from loc1, bypassing the bad locations)
        val loc2 = createMockLocation(accuracy = 15f)
        every { loc1.distanceTo(loc2) } returns 150f
        gpsTracker.recordLocation(loc2)

        assertThat(gpsTracker.metrics.value.points).hasSize(2)
        assertThat(gpsTracker.metrics.value.totalDistanceMeters).isEqualTo(150.0)
    }

    @Test
    fun `calculate pace correctly based on elapsed seconds and interval distance`() {
        gpsTracker.startTracking(initialPhaseName = "Run")

        val loc1 = createMockLocation()
        gpsTracker.recordLocation(loc1)

        // 200m in 60s -> pace = 60 * 1000 / 200 = 300 sec/km (5:00 min/km)
        val loc2 = createMockLocation()
        every { loc1.distanceTo(loc2) } returns 200f
        gpsTracker.recordLocation(loc2, elapsedPhaseSeconds = 60)
        assertThat(gpsTracker.metrics.value.currentIntervalPaceSecPerKm).isEqualTo(300)

        // Additional 200m (total 400m) at 120s -> pace = 120 * 1000 / 400 = 300 sec/km
        val loc3 = createMockLocation()
        every { loc2.distanceTo(loc3) } returns 200f
        gpsTracker.recordLocation(loc3, elapsedPhaseSeconds = 120)
        assertThat(gpsTracker.metrics.value.currentIntervalPaceSecPerKm).isEqualTo(300)

        // Zero distance or zero seconds yields 0 pace
        gpsTracker.updatePhase("Walk")
        val loc4 = createMockLocation()
        gpsTracker.recordLocation(loc4, elapsedPhaseSeconds = 0)
        assertThat(gpsTracker.metrics.value.currentIntervalPaceSecPerKm).isEqualTo(0)
    }

    @Test
    fun `pace calculation with timeProvider elapsedRealtime`() {
        gpsTracker.startTracking(initialPhaseName = "Run")

        val loc1 = createMockLocation()
        gpsTracker.recordLocation(loc1)

        // Advance time by 60 seconds (60_000 ms)
        timeProvider.advanceTimeBy(60_000L)

        val loc2 = createMockLocation()
        every { loc1.distanceTo(loc2) } returns 200f
        gpsTracker.recordLocation(loc2)

        assertThat(gpsTracker.metrics.value.currentIntervalPaceSecPerKm).isEqualTo(300)
    }

    @Test
    fun `pause and resume tracking behaves properly`() {
        gpsTracker.startTracking(initialPhaseName = "Run")

        val loc1 = createMockLocation()
        gpsTracker.recordLocation(loc1)

        val loc2 = createMockLocation()
        every { loc1.distanceTo(loc2) } returns 100f
        gpsTracker.recordLocation(loc2)

        assertThat(gpsTracker.metrics.value.totalDistanceMeters).isEqualTo(100.0)

        // Pause
        gpsTracker.pauseTracking()
        assertThat(gpsTracker.isPaused()).isTrue()

        // Point during pause should be ignored
        val pausedLoc = createMockLocation()
        gpsTracker.recordLocation(pausedLoc)
        assertThat(gpsTracker.metrics.value.points).hasSize(2)
        assertThat(gpsTracker.metrics.value.totalDistanceMeters).isEqualTo(100.0)

        // Resume
        gpsTracker.resumeTracking()
        assertThat(gpsTracker.isPaused()).isFalse()

        // First point after resume sets reference, doesn't add jump distance
        val loc3 = createMockLocation()
        gpsTracker.recordLocation(loc3)
        assertThat(gpsTracker.metrics.value.points).hasSize(3)
        assertThat(gpsTracker.metrics.value.totalDistanceMeters).isEqualTo(100.0)

        // Next point calculates distance from loc3
        val loc4 = createMockLocation()
        every { loc3.distanceTo(loc4) } returns 50f
        gpsTracker.recordLocation(loc4)
        assertThat(gpsTracker.metrics.value.points).hasSize(4)
        assertThat(gpsTracker.metrics.value.totalDistanceMeters).isEqualTo(150.0)
    }

    @Test
    fun `stopTracking unregisters updates and returns final metrics`() {
        gpsTracker.startTracking(initialPhaseName = "Run")
        val loc1 = createMockLocation()
        gpsTracker.recordLocation(loc1)

        val loc2 = createMockLocation()
        every { loc1.distanceTo(loc2) } returns 80f
        gpsTracker.recordLocation(loc2)

        val finalMetrics = gpsTracker.stopTracking()

        assertThat(gpsTracker.isTracking()).isFalse()
        assertThat(finalMetrics.totalDistanceMeters).isEqualTo(80.0)
        assertThat(finalMetrics.points).hasSize(2)
        verify { mockLocationManager.removeUpdates(any<android.location.LocationListener>()) }

        // Locations recorded after stop are ignored
        val loc3 = createMockLocation()
        gpsTracker.recordLocation(loc3)
        assertThat(gpsTracker.metrics.value.points).hasSize(2)
    }
}
