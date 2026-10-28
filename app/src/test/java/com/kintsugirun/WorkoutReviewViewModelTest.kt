package com.kintsugirun

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun `loadWorkout loads workout from repository`() = runTest {
        coEvery { repository.getWorkoutById(1L) } returns sampleWorkout

        val viewModel = WorkoutReviewViewModel(repository)

        viewModel.workout.test {
            assertThat(awaitItem()).isNull()

            viewModel.loadWorkout(1L)
            testScheduler.advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(sampleWorkout)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.getWorkoutById(1L) }
    }

    @Test
    fun `loadWorkout handles not found workout`() = runTest {
        coEvery { repository.getWorkoutById(999L) } returns null

        val viewModel = WorkoutReviewViewModel(repository)
        viewModel.loadWorkout(999L)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.workout.value).isNull()
    }
}
