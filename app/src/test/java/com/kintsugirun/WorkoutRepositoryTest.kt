package com.kintsugirun

import android.util.Log
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class WorkoutRepositoryTest {

    private lateinit var workoutDao: WorkoutDao
    private lateinit var workoutSessionDao: WorkoutSessionDao
    private lateinit var repository: WorkoutRepository

    companion object {
        @JvmStatic
        @BeforeClass
        fun setupClass() {
            mockkStatic(Log::class)
            every { Log.d(any(), any()) } returns 0
        }

        @JvmStatic
        @AfterClass
        fun tearDownClass() {
            unmockkStatic(Log::class)
        }
    }

    private val sampleWorkout = Workout(
        id = 1L,
        workoutName = "Test Run",
        warmupSeconds = 300,
        runSeconds = 120,
        walkSeconds = 60,
        repeats = 5,
        cooldownSeconds = 300
    )

    private val sampleSession = WorkoutSession(
        id = 10L,
        workoutId = 1L,
        startedAtTimestamp = 1700000000000L,
        totalElapsedSeconds = 1200,
        isCompleted = true,
        interruptedPhase = null,
        workoutSnapshot = sampleWorkout
    )

    @Before
    fun setUp() {
        workoutDao = mockk(relaxed = true)
        workoutSessionDao = mockk(relaxed = true)
        repository = WorkoutRepository(workoutDao, workoutSessionDao)
    }

    @Test
    fun `allWorkouts delegates to workoutDao getAllWorkouts`() = runTest {
        val workouts = listOf(sampleWorkout)
        every { workoutDao.getAllWorkouts() } returns flowOf(workouts)

        val newRepo = WorkoutRepository(workoutDao, workoutSessionDao)
        newRepo.allWorkouts.test {
            assertThat(awaitItem()).isEqualTo(workouts)
            awaitComplete()
        }
    }

    @Test
    fun `getWorkoutById delegates to workoutDao getWorkoutById`() = runTest {
        coEvery { workoutDao.getWorkoutById(1L) } returns sampleWorkout

        val result = repository.getWorkoutById(1L)

        assertThat(result).isEqualTo(sampleWorkout)
        coVerify(exactly = 1) { workoutDao.getWorkoutById(1L) }
    }

    @Test
    fun `saveWorkout delegates to workoutDao insertOrUpdate`() = runTest {
        coEvery { workoutDao.insertOrUpdate(sampleWorkout) } returns 1L

        val result = repository.saveWorkout(sampleWorkout)

        assertThat(result).isEqualTo(1L)
        coVerify(exactly = 1) { workoutDao.insertOrUpdate(sampleWorkout) }
    }

    @Test
    fun `deleteWorkout delegates to workoutDao deleteById`() = runTest {
        repository.deleteWorkout(1L)

        coVerify(exactly = 1) { workoutDao.deleteById(1L) }
    }

    @Test
    fun `saveSession delegates to workoutSessionDao insertSession and returns id`() = runTest {
        coEvery { workoutSessionDao.insertSession(sampleSession) } returns 10L

        val result = repository.saveSession(sampleSession)

        assertThat(result).isEqualTo(10L)
        coVerify(exactly = 1) { workoutSessionDao.insertSession(sampleSession) }
    }

    @Test
    fun `saveSession with gps metrics delegates correctly to workoutSessionDao`() = runTest {
        val sessionWithGps = sampleSession.copy(
            id = 20L,
            totalDistanceMeters = 5000.0,
            runDistanceMeters = 3500.0,
            walkDistanceMeters = 1500.0,
            runPaceSecondsPerKm = 300,
            walkPaceSecondsPerKm = 600,
            gpsRouteJson = """[{"latitude":45.0,"longitude":9.0}]"""
        )
        coEvery { workoutSessionDao.insertSession(sessionWithGps) } returns 20L

        val result = repository.saveSession(sessionWithGps)

        assertThat(result).isEqualTo(20L)
        coVerify(exactly = 1) { workoutSessionDao.insertSession(sessionWithGps) }
    }

    @Test
    fun `saveSessions delegates to workoutSessionDao insertAll`() = runTest {
        val sessions = listOf(sampleSession)

        repository.saveSessions(sessions)

        coVerify(exactly = 1) { workoutSessionDao.insertAll(sessions) }
    }

    @Test
    fun `getLatestSessionForWorkout delegates to workoutSessionDao getLatestSessionForWorkout`() = runTest {
        every { workoutSessionDao.getLatestSessionForWorkout(1L) } returns flowOf(sampleSession)

        repository.getLatestSessionForWorkout(1L).test {
            assertThat(awaitItem()).isEqualTo(sampleSession)
            awaitComplete()
        }
        coVerify(exactly = 1) { workoutSessionDao.getLatestSessionForWorkout(1L) }
    }

    @Test
    fun `getAllSessions delegates to workoutSessionDao getAllSessions`() = runTest {
        val sessions = listOf(sampleSession)
        every { workoutSessionDao.getAllSessions() } returns flowOf(sessions)

        repository.getAllSessions().test {
            assertThat(awaitItem()).isEqualTo(sessions)
            awaitComplete()
        }
        coVerify(exactly = 1) { workoutSessionDao.getAllSessions() }
    }

    @Test
    fun `getAllWorkoutsList retrieves first list of workouts from workoutDao`() = runTest {
        val workouts = listOf(sampleWorkout)
        every { workoutDao.getAllWorkouts() } returns flowOf(workouts)

        val result = repository.getAllWorkoutsList()

        assertThat(result).isEqualTo(workouts)
        coVerify(atLeast = 1) { workoutDao.getAllWorkouts() }
    }

    @Test
    fun `getAllSessionsList retrieves first list of sessions from workoutSessionDao`() = runTest {
        val sessions = listOf(sampleSession)
        every { workoutSessionDao.getAllSessions() } returns flowOf(sessions)

        val result = repository.getAllSessionsList()

        assertThat(result).isEqualTo(sessions)
        coVerify(atLeast = 1) { workoutSessionDao.getAllSessions() }
    }

    @Test
    fun `deleteAllSessions delegates to workoutSessionDao deleteAllSessions`() = runTest {
        repository.deleteAllSessions()

        coVerify(exactly = 1) { workoutSessionDao.deleteAllSessions() }
    }

    @Test
    fun `seedDefaultWorkoutsIfEmpty seeds starter workouts when table is empty`() = runTest {
        coEvery { workoutDao.getWorkoutCount() } returns 0

        repository.seedDefaultWorkoutsIfEmpty()

        coVerify(exactly = 2) { workoutDao.insertOrUpdate(any()) }
    }

    @Test
    fun `seedDefaultWorkoutsIfEmpty does not insert workouts when table is not empty`() = runTest {
        coEvery { workoutDao.getWorkoutCount() } returns 2

        repository.seedDefaultWorkoutsIfEmpty()

        coVerify(exactly = 0) { workoutDao.insertOrUpdate(any()) }
    }

    @Test
    fun `triggerAutoBackup exports workouts and sessions and writes to autoBackupManager when enabled`() = runTest {
        val autoBackupManager = mockk<AutoBackupManager>(relaxed = true)
        every { autoBackupManager.isAutoBackupEnabled() } returns true
        coEvery { autoBackupManager.writeBackup(any()) } returns true

        every { workoutDao.getAllWorkouts() } returns flowOf(listOf(sampleWorkout))
        every { workoutSessionDao.getAllSessions() } returns flowOf(listOf(sampleSession))

        val repo = WorkoutRepository(workoutDao, workoutSessionDao, autoBackupManager)
        repo.triggerAutoBackup()

        coVerify(exactly = 1) { autoBackupManager.writeBackup(match { json ->
            json.contains("Test Run") && json.contains("1200")
        }) }
    }

    @Test
    fun `triggerAutoBackup does not write backup when autoBackupManager is disabled`() = runTest {
        val autoBackupManager = mockk<AutoBackupManager>(relaxed = true)
        every { autoBackupManager.isAutoBackupEnabled() } returns false

        val repo = WorkoutRepository(workoutDao, workoutSessionDao, autoBackupManager)
        repo.triggerAutoBackup()

        coVerify(exactly = 0) { autoBackupManager.writeBackup(any()) }
    }

    @Test
    fun `saveWorkout triggers auto-backup in background`() = runTest {
        val autoBackupManager = mockk<AutoBackupManager>(relaxed = true)
        every { autoBackupManager.isAutoBackupEnabled() } returns true
        coEvery { autoBackupManager.writeBackup(any()) } returns true

        coEvery { workoutDao.insertOrUpdate(sampleWorkout) } returns 1L
        every { workoutDao.getAllWorkouts() } returns flowOf(listOf(sampleWorkout))
        every { workoutSessionDao.getAllSessions() } returns flowOf(emptyList())

        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val testScope = kotlinx.coroutines.CoroutineScope(dispatcher)
        val repo = WorkoutRepository(workoutDao, workoutSessionDao, autoBackupManager, testScope, dispatcher)

        val id = repo.saveWorkout(sampleWorkout)
        testScheduler.advanceUntilIdle()

        assertThat(id).isEqualTo(1L)
        coVerify(atLeast = 1) { autoBackupManager.writeBackup(any()) }
    }

    @Test
    fun `deleteWorkout triggers auto-backup in background`() = runTest {
        val autoBackupManager = mockk<AutoBackupManager>(relaxed = true)
        every { autoBackupManager.isAutoBackupEnabled() } returns true
        coEvery { autoBackupManager.writeBackup(any()) } returns true

        every { workoutDao.getAllWorkouts() } returns flowOf(emptyList())
        every { workoutSessionDao.getAllSessions() } returns flowOf(emptyList())

        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val testScope = kotlinx.coroutines.CoroutineScope(dispatcher)
        val repo = WorkoutRepository(workoutDao, workoutSessionDao, autoBackupManager, testScope, dispatcher)

        repo.deleteWorkout(1L)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { workoutDao.deleteById(1L) }
        coVerify(atLeast = 1) { autoBackupManager.writeBackup(any()) }
    }

    @Test
    fun `saveSession triggers auto-backup in background`() = runTest {
        val autoBackupManager = mockk<AutoBackupManager>(relaxed = true)
        every { autoBackupManager.isAutoBackupEnabled() } returns true
        coEvery { autoBackupManager.writeBackup(any()) } returns true

        coEvery { workoutSessionDao.insertSession(sampleSession) } returns 10L
        every { workoutDao.getAllWorkouts() } returns flowOf(emptyList())
        every { workoutSessionDao.getAllSessions() } returns flowOf(listOf(sampleSession))

        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val testScope = kotlinx.coroutines.CoroutineScope(dispatcher)
        val repo = WorkoutRepository(workoutDao, workoutSessionDao, autoBackupManager, testScope, dispatcher)

        val id = repo.saveSession(sampleSession)
        testScheduler.advanceUntilIdle()

        assertThat(id).isEqualTo(10L)
        coVerify(atLeast = 1) { autoBackupManager.writeBackup(any()) }
    }
}
