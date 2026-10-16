package com.kintsugirun

import android.app.Application
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModelTest {

    private lateinit var repository: WorkoutRepository
    private lateinit var timerManager: TimerManager
    private lateinit var mockApplication: Application
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val sampleWorkout = Workout(
        id = 10L,
        workoutName = "Morning Jog",
        warmupSeconds = 60,
        runSeconds = 30,
        walkSeconds = 30,
        repeats = 3,
        cooldownSeconds = 60
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        coEvery { repository.getWorkoutById(10L) } returns sampleWorkout
        mockApplication = mockk(relaxed = true)
        timerManager = TimerManager(scope = testScope, repository = repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadWorkout loads workout from repository`() = runTest {
        val viewModel = ActiveWorkoutViewModel(repository, timerManager, mockApplication)
        viewModel.loadWorkout(10L)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.workout.value).isEqualTo(sampleWorkout)
    }

    @Test
    fun `stopTimer stops timerManager and saves session when elapsed time is 30s or more`() = runTest {
        val timeProvider = TestTimeProvider(currentTimeMs = 10_000L)
        val managedTimer = TimerManager(timeProvider = timeProvider, scope = testScope, repository = repository)
        val viewModel = ActiveWorkoutViewModel(repository, managedTimer, mockApplication)

        viewModel.loadWorkout(10L)
        testScheduler.advanceUntilIdle()

        managedTimer.start(sampleWorkout)
        timeProvider.advanceTimeBy(40_000L)
        managedTimer.tick()

        viewModel.stopTimer()
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.saveSession(match {
                it.workoutId == 10L && !it.isCompleted && it.totalElapsedSeconds == 40
            })
        }
        verify(exactly = 1) { mockApplication.stopService(any<Intent>()) }
    }

    @Test
    fun `gpsMetrics exposes metrics from gpsTracker when provided`() {
        val mockGpsTracker = mockk<GpsTracker>(relaxed = true)
        val metricsFlow = MutableStateFlow(
            GpsMetrics(
                totalDistanceMeters = 1500.0,
                currentIntervalPaceSecPerKm = 315
            )
        )
        every { mockGpsTracker.metrics } returns metricsFlow

        val viewModel = ActiveWorkoutViewModel(repository, timerManager, mockApplication, mockGpsTracker)
        assertThat(viewModel.gpsMetrics.value.totalDistanceMeters).isEqualTo(1500.0)
        assertThat(viewModel.gpsMetrics.value.currentIntervalPaceSecPerKm).isEqualTo(315)
    }

    @Test
    fun `gpsMetrics defaults to empty metrics when gpsTracker is null`() {
        val viewModel = ActiveWorkoutViewModel(repository, timerManager, mockApplication, null)
        assertThat(viewModel.gpsMetrics.value.totalDistanceMeters).isEqualTo(0.0)
        assertThat(viewModel.gpsMetrics.value.currentIntervalPaceSecPerKm).isEqualTo(0)
    }
}
