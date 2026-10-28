package com.kintsugirun

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
class SessionDetailViewModelTest {

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
        workoutSnapshot = sampleWorkout,
        totalDistanceMeters = 3000.0,
        runDistanceMeters = 2000.0,
        walkDistanceMeters = 1000.0,
        runPaceSecondsPerKm = 320,
        walkPaceSecondsPerKm = 580
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
    fun `loadSession loads session from repository`() = runTest {
        every { repository.getSessionById(100L) } returns flowOf(sampleSession)

        val viewModel = SessionDetailViewModel(repository)

        viewModel.session.test {
            assertThat(awaitItem()).isNull()

            viewModel.loadSession(100L)
            testScheduler.advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(sampleSession)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.getSessionById(100L) }
    }

    @Test
    fun `initialSessionId triggers loadSession upon creation`() = runTest {
        every { repository.getSessionById(100L) } returns flowOf(sampleSession)

        val viewModel = SessionDetailViewModel(repository, initialSessionId = 100L)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.session.value).isEqualTo(sampleSession)
        coVerify(exactly = 1) { repository.getSessionById(100L) }
    }

    @Test
    fun `deleteSession calls repository deleteSession and invokes callback`() = runTest {
        coEvery { repository.deleteSession(100L) } returns Unit

        val viewModel = SessionDetailViewModel(repository)
        var callbackCalled = false

        viewModel.deleteSession(100L) {
            callbackCalled = true
        }
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteSession(100L) }
        assertThat(callbackCalled).isTrue()
    }
}
