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
class WorkoutEditorViewModelTest {

    private lateinit var repository: WorkoutRepository
    private val testDispatcher = StandardTestDispatcher()

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
    fun `when initialized with no filename, workout should be default new workout`() = runTest {
        val viewModel = WorkoutEditorViewModel(repository, null)

        viewModel.workout.test {
            // First emission might be null or initial depending on StateFlow, await the valid state
            val workout = awaitItem()

            // Advance coroutines for loadWorkout init logic
            testScheduler.advanceUntilIdle()

            // Need to retrieve item again if first was null, but let's test if we eventually get the right one
            val actualWorkout = if (workout == null) awaitItem() else workout

            assertThat(actualWorkout).isNotNull()
            assertThat(actualWorkout?.workoutName).isEqualTo("New Workout")
            assertThat(actualWorkout?.sets).hasSize(1)
            assertThat(actualWorkout?.sets?.first()?.intervals).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateWorkoutName correctly updates StateFlow`() = runTest {
        val viewModel = WorkoutEditorViewModel(repository, null)
        testScheduler.advanceUntilIdle()

        viewModel.updateWorkoutName("My Custom Run")

        viewModel.workout.test {
            val workout = awaitItem()
            assertThat(workout?.workoutName).isEqualTo("My Custom Run")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateWarmup correctly updates StateFlow`() = runTest {
        val viewModel = WorkoutEditorViewModel(repository, null)
        testScheduler.advanceUntilIdle()

        viewModel.updateWarmup(120, "Let's go")

        viewModel.workout.test {
            val workout = awaitItem()
            assertThat(workout?.warmup?.durationSeconds).isEqualTo(120)
            assertThat(workout?.warmup?.message).isEqualTo("Let's go")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addSet adds a new set to the workout`() = runTest {
        val viewModel = WorkoutEditorViewModel(repository, null)
        testScheduler.advanceUntilIdle()

        viewModel.addSet()

        viewModel.workout.test {
            val workout = awaitItem()
            assertThat(workout?.sets).hasSize(2)
            assertThat(workout?.sets?.last()?.setName).isEqualTo("New Set")
            assertThat(workout?.sets?.last()?.intervals).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addIntervalToSet adds an interval to the correct set`() = runTest {
        val viewModel = WorkoutEditorViewModel(repository, null)
        testScheduler.advanceUntilIdle()

        // Default has 1 set with 1 interval. Let's add another interval to set 0.
        viewModel.addIntervalToSet(0)

        viewModel.workout.test {
            val workout = awaitItem()
            assertThat(workout?.sets?.first()?.intervals).hasSize(2)
            assertThat(workout?.sets?.first()?.intervals?.last()?.type).isEqualTo("run")
            assertThat(workout?.sets?.first()?.intervals?.last()?.durationSeconds).isEqualTo(60)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateInterval modifies correct interval within correct set`() = runTest {
        val viewModel = WorkoutEditorViewModel(repository, null)
        testScheduler.advanceUntilIdle()

        // Modify the first interval of the first set
        viewModel.updateInterval(0, 0, "walk", 30, "Walk time")

        viewModel.workout.test {
            val workout = awaitItem()
            val interval = workout?.sets?.first()?.intervals?.first()
            assertThat(interval?.type).isEqualTo("walk")
            assertThat(interval?.durationSeconds).isEqualTo(30)
            assertThat(interval?.message).isEqualTo("Walk time")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveWorkout calls repository saveWorkout`() = runTest {
        val viewModel = WorkoutEditorViewModel(repository, null)
        testScheduler.advanceUntilIdle()

        // We use an arbitrary modified workout name to verify what's saved
        viewModel.updateWorkoutName("Target Save")

        viewModel.saveWorkout()

        coVerify {
            repository.saveWorkout(match { it.workoutName == "Target Save" }, null)
        }
    }
}
