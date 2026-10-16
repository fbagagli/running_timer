package com.kintsugirun

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var repository: WorkoutRepository
    private val testDispatcher = StandardTestDispatcher()

    private val sampleWorkout1 = Workout(
        id = 10L,
        workoutName = "Morning Run",
        warmupSeconds = 300,
        runSeconds = 120,
        walkSeconds = 60,
        repeats = 5,
        cooldownSeconds = 300
    )

    private val sampleWorkout2 = Workout(
        id = 20L,
        workoutName = "Evening Jog",
        warmupSeconds = 200,
        runSeconds = 90,
        walkSeconds = 60,
        repeats = 4,
        cooldownSeconds = 200
    )

    private val sampleSession1 = WorkoutSession(
        id = 100L,
        workoutId = 10L,
        startedAtTimestamp = 1700000000000L,
        totalElapsedSeconds = 1200,
        isCompleted = true,
        interruptedPhase = null,
        workoutSnapshot = sampleWorkout1
    )

    private val sampleSession2 = WorkoutSession(
        id = 101L,
        workoutId = 20L,
        startedAtTimestamp = 1700005000000L,
        totalElapsedSeconds = 900,
        isCompleted = false,
        interruptedPhase = "Walk 2",
        workoutSnapshot = sampleWorkout2
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        coEvery { repository.allWorkouts } returns flowOf(listOf(sampleWorkout1))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init seeds default starter workouts`() = runTest {
        HomeViewModel(repository)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.seedDefaultWorkoutsIfEmpty() }
    }

    @Test
    fun `deleteWorkout delegates to repository deleteWorkout`() = runTest {
        val viewModel = HomeViewModel(repository)
        testScheduler.advanceUntilIdle()

        viewModel.deleteWorkout(10L)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteWorkout(10L) }
    }

    @Test
    fun `saveWorkout delegates to repository saveWorkout`() = runTest {
        val viewModel = HomeViewModel(repository)
        testScheduler.advanceUntilIdle()

        viewModel.saveWorkout(sampleWorkout1)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.saveWorkout(sampleWorkout1) }
    }

    @Test
    fun `exportBackupJson fetches workouts and sessions and produces valid backup JSON`() = runTest {
        coEvery { repository.getAllWorkoutsList() } returns listOf(sampleWorkout1, sampleWorkout2)
        coEvery { repository.getAllSessionsList() } returns listOf(sampleSession1)

        val viewModel = HomeViewModel(repository)
        testScheduler.advanceUntilIdle()

        val json = viewModel.exportBackupJson()

        val parsed = BackupHelper.parseBackup(json)
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.workouts).containsExactly(sampleWorkout1, sampleWorkout2).inOrder()
        assertThat(parsed.sessions).containsExactly(sampleSession1)
    }

    @Test
    fun `importBackupJson parses json, remaps workout IDs, saves entities with id 0, and returns success`() = runTest {
        val backupJson = BackupHelper.exportBackup(
            workouts = listOf(sampleWorkout1, sampleWorkout2),
            sessions = listOf(sampleSession1, sampleSession2)
        )

        coEvery { repository.saveWorkout(match { it.workoutName == sampleWorkout1.workoutName }) } returns 1001L
        coEvery { repository.saveWorkout(match { it.workoutName == sampleWorkout2.workoutName }) } returns 1002L
        coEvery { repository.saveSession(any()) } returns 500L

        val viewModel = HomeViewModel(repository)
        testScheduler.advanceUntilIdle()

        val result = viewModel.importBackupJson(backupJson)

        assertThat(result.isSuccess).isTrue()
        val data = result.getOrThrow()
        assertThat(data.workoutsCount).isEqualTo(2)
        assertThat(data.sessionsCount).isEqualTo(2)

        val savedWorkouts = mutableListOf<Workout>()
        coVerify(exactly = 2) { repository.saveWorkout(capture(savedWorkouts)) }
        assertThat(savedWorkouts[0].id).isEqualTo(0L)
        assertThat(savedWorkouts[0].workoutName).isEqualTo("Morning Run")
        assertThat(savedWorkouts[1].id).isEqualTo(0L)
        assertThat(savedWorkouts[1].workoutName).isEqualTo("Evening Jog")

        val savedSessions = mutableListOf<WorkoutSession>()
        coVerify(exactly = 2) { repository.saveSession(capture(savedSessions)) }
        assertThat(savedSessions[0].id).isEqualTo(0L)
        assertThat(savedSessions[0].workoutId).isEqualTo(1001L)
        assertThat(savedSessions[1].id).isEqualTo(0L)
        assertThat(savedSessions[1].workoutId).isEqualTo(1002L)
    }

    @Test
    fun `importBackupJson returns failure when json is malformed`() = runTest {
        val viewModel = HomeViewModel(repository)
        testScheduler.advanceUntilIdle()

        val result = viewModel.importBackupJson("invalid json content")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        coVerify(exactly = 0) { repository.saveWorkout(any()) }
        coVerify(exactly = 0) { repository.saveSession(any()) }
    }

    @Test
    fun `importBackupJson returns failure when repository save throws`() = runTest {
        val backupJson = BackupHelper.exportBackup(
            workouts = listOf(sampleWorkout1),
            sessions = emptyList()
        )
        coEvery { repository.saveWorkout(any()) } throws IllegalStateException("Database disk full")

        val viewModel = HomeViewModel(repository)
        testScheduler.advanceUntilIdle()

        val result = viewModel.importBackupJson(backupJson)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `isAutoBackupEnabled reflects autoBackupManager state`() = runTest {
        val autoBackupManager = mockk<AutoBackupManager>(relaxed = true)
        every { autoBackupManager.isAutoBackupEnabled() } returns true

        val viewModel = HomeViewModel(repository, autoBackupManager)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.isAutoBackupEnabled.value).isTrue()
    }

    @Test
    fun `enableAutoBackup sets URI and triggers repository triggerAutoBackup`() = runTest {
        val autoBackupManager = mockk<AutoBackupManager>(relaxed = true)
        every { autoBackupManager.isAutoBackupEnabled() } returns true
        val mockUri = mockk<Uri>()

        val viewModel = HomeViewModel(repository, autoBackupManager)
        testScheduler.advanceUntilIdle()

        viewModel.enableAutoBackup(mockUri)
        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { autoBackupManager.setAutoBackupUri(mockUri) }
        assertThat(viewModel.isAutoBackupEnabled.value).isTrue()
        coVerify(exactly = 1) { repository.triggerAutoBackup() }
    }

    @Test
    fun `disableAutoBackup clears URI on autoBackupManager and updates state to false`() = runTest {
        val autoBackupManager = mockk<AutoBackupManager>(relaxed = true)
        every { autoBackupManager.isAutoBackupEnabled() } returns true

        val viewModel = HomeViewModel(repository, autoBackupManager)
        testScheduler.advanceUntilIdle()
        assertThat(viewModel.isAutoBackupEnabled.value).isTrue()

        viewModel.disableAutoBackup()
        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { autoBackupManager.setAutoBackupUri(null) }
        assertThat(viewModel.isAutoBackupEnabled.value).isFalse()
    }
}
