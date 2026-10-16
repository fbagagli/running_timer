package com.kintsugirun

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutReviewViewModelTest {

    private lateinit var repository: WorkoutRepository
    private val testDispatcher = StandardTestDispatcher()

    private val sampleWorkout = Workout(
        id = 1L,
        workoutName = "5K Intervals",
        warmupSeconds = 300,
        runSeconds = 120,
        walkSeconds = 60,
        repeats = 5,
        cooldownSeconds = 300
    )

    private val sampleSession = WorkoutSession(
        id = 100L,
        workoutId = 1L,
        startedAtTimestamp = 1700000000000L,
        totalElapsedSeconds = 1200,
        isCompleted = true,
        interruptedPhase = null,
        workoutSnapshot = sampleWorkout
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadWorkout loads both workout and latest session from repository`() = runTest {
        coEvery { repository.getWorkoutById(1L) } returns sampleWorkout
        every { repository.getLatestSessionForWorkout(1L) } returns flowOf(sampleSession)

        val viewModel = WorkoutReviewViewModel(repository)

        viewModel.workout.test {
            assertThat(awaitItem()).isNull()

            viewModel.latestSession.test {
                assertThat(awaitItem()).isNull()

                viewModel.loadWorkout(1L)
                testScheduler.advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(sampleSession)
                cancelAndIgnoreRemainingEvents()
            }

            assertThat(awaitItem()).isEqualTo(sampleWorkout)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.getWorkoutById(1L) }
        coVerify(exactly = 1) { repository.getLatestSessionForWorkout(1L) }
    }

    @Test
    fun `loadWorkout handles null latest session when no previous session exists`() = runTest {
        coEvery { repository.getWorkoutById(1L) } returns sampleWorkout
        every { repository.getLatestSessionForWorkout(1L) } returns flowOf(null)

        val viewModel = WorkoutReviewViewModel(repository)

        viewModel.loadWorkout(1L)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.workout.value).isEqualTo(sampleWorkout)
        assertThat(viewModel.latestSession.value).isNull()
    }

    @Test
    fun `loadWorkout cancels previous session subscription and collects new workout session`() = runTest {
        val workout2 = sampleWorkout.copy(id = 2L, workoutName = "10K Tempo")
        val session2 = sampleSession.copy(id = 200L, workoutId = 2L, isCompleted = false, interruptedPhase = "Run")

        val sessionFlow1 = MutableSharedFlow<WorkoutSession?>()
        val sessionFlow2 = MutableSharedFlow<WorkoutSession?>()

        coEvery { repository.getWorkoutById(1L) } returns sampleWorkout
        coEvery { repository.getWorkoutById(2L) } returns workout2
        every { repository.getLatestSessionForWorkout(1L) } returns sessionFlow1
        every { repository.getLatestSessionForWorkout(2L) } returns sessionFlow2

        val viewModel = WorkoutReviewViewModel(repository)

        viewModel.loadWorkout(1L)
        testScheduler.advanceUntilIdle()
        sessionFlow1.emit(sampleSession)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.workout.value).isEqualTo(sampleWorkout)
        assertThat(viewModel.latestSession.value).isEqualTo(sampleSession)

        // Switch to workout 2
        viewModel.loadWorkout(2L)
        testScheduler.advanceUntilIdle()
        sessionFlow2.emit(session2)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.workout.value).isEqualTo(workout2)
        assertThat(viewModel.latestSession.value).isEqualTo(session2)

        // Emit on old flow to verify it was cancelled and not updating state
        sessionFlow1.emit(sampleSession.copy(id = 999L))
        testScheduler.advanceUntilIdle()
        assertThat(viewModel.latestSession.value).isEqualTo(session2)
    }
}
