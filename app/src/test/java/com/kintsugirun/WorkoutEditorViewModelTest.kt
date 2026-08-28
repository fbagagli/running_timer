package com.kintsugirun

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
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
            val workout = awaitItem()
            testScheduler.advanceUntilIdle()
            val actualWorkout = workout ?: awaitItem()

            assertThat(actualWorkout).isNotNull()
            assertThat(actualWorkout?.workoutName).isEqualTo("New Workout")
            assertThat(actualWorkout?.warmupSeconds).isEqualTo(300)
            assertThat(actualWorkout?.runSeconds).isEqualTo(120)
            assertThat(actualWorkout?.walkSeconds).isEqualTo(60)
            assertThat(actualWorkout?.repeats).isEqualTo(5)
            assertThat(actualWorkout?.cooldownSeconds).isEqualTo(300)
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
    fun `updateWarmupSeconds correctly updates StateFlow`() = runTest {
        val viewModel = WorkoutEditorViewModel(repository, null)
        testScheduler.advanceUntilIdle()

        viewModel.updateWarmupSeconds(180)

        viewModel.workout.test {
            val workout = awaitItem()
            assertThat(workout?.warmupSeconds).isEqualTo(180)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateRunSeconds correctly updates StateFlow`() = runTest {
        val viewModel = WorkoutEditorViewModel(repository, null)
        testScheduler.advanceUntilIdle()

        viewModel.updateRunSeconds(90)

        viewModel.workout.test {
            val workout = awaitItem()
            assertThat(workout?.runSeconds).isEqualTo(90)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateWalkSeconds correctly updates StateFlow`() = runTest {
        val viewModel = WorkoutEditorViewModel(repository, null)
        testScheduler.advanceUntilIdle()

        viewModel.updateWalkSeconds(45)

        viewModel.workout.test {
            val workout = awaitItem()
            assertThat(workout?.walkSeconds).isEqualTo(45)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateRepeats correctly updates StateFlow`() = runTest {
        val viewModel = WorkoutEditorViewModel(repository, null)
        testScheduler.advanceUntilIdle()

        viewModel.updateRepeats(8)

        viewModel.workout.test {
            val workout = awaitItem()
            assertThat(workout?.repeats).isEqualTo(8)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateCooldownSeconds correctly updates StateFlow`() = runTest {
        val viewModel = WorkoutEditorViewModel(repository, null)
        testScheduler.advanceUntilIdle()

        viewModel.updateCooldownSeconds(240)

        viewModel.workout.test {
            val workout = awaitItem()
            assertThat(workout?.cooldownSeconds).isEqualTo(240)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveWorkout calls repository saveWorkout`() = runTest {
        val viewModel = WorkoutEditorViewModel(repository, null)
        testScheduler.advanceUntilIdle()

        viewModel.updateWorkoutName("Target Save")

        viewModel.saveWorkout()

        coVerify {
            repository.saveWorkout(match { it.workoutName == "Target Save" })
        }
    }

    @Test
    fun `when initialized with workoutId, loads existing workout`() = runTest {
        val existingWorkout = Workout(
            id = 42L,
            workoutName = "Loaded Workout",
            warmupSeconds = 100,
            runSeconds = 50,
            walkSeconds = 50,
            repeats = 3,
            cooldownSeconds = 100
        )
        io.mockk.coEvery { repository.getWorkoutById(42L) } returns existingWorkout

        val viewModel = WorkoutEditorViewModel(repository, 42L)

        viewModel.workout.test {
            val workout = awaitItem()
            testScheduler.advanceUntilIdle()
            val actualWorkout = workout ?: awaitItem()

            assertThat(actualWorkout?.id).isEqualTo(42L)
            assertThat(actualWorkout?.workoutName).isEqualTo("Loaded Workout")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
