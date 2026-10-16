package com.kintsugirun

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class TimerServiceActionHandlerTest {

    private lateinit var timerManager: TimerManager
    private var stopCalled = false
    private lateinit var actionHandler: TimerServiceActionHandler

    @Before
    fun setup() {
        timerManager = mockk(relaxed = true)
        stopCalled = false
        actionHandler = TimerServiceActionHandler(
            timerManager = timerManager,
            onStop = { stopCalled = true }
        )
    }

    @Test
    fun `handleAction with ACTION_PAUSE invokes timerManager pause`() {
        val result = actionHandler.handleAction(TimerNotificationHelper.ACTION_PAUSE)

        assertThat(result).isTrue()
        verify(exactly = 1) { timerManager.pause() }
        assertThat(stopCalled).isFalse()
    }

    @Test
    fun `handleAction with ACTION_RESUME invokes timerManager resume`() {
        val result = actionHandler.handleAction(TimerNotificationHelper.ACTION_RESUME)

        assertThat(result).isTrue()
        verify(exactly = 1) { timerManager.resume() }
        assertThat(stopCalled).isFalse()
    }

    @Test
    fun `handleAction with ACTION_NEXT invokes timerManager nextStep`() {
        val result = actionHandler.handleAction(TimerNotificationHelper.ACTION_NEXT)

        assertThat(result).isTrue()
        verify(exactly = 1) { timerManager.nextStep() }
        assertThat(stopCalled).isFalse()
    }

    @Test
    fun `handleAction with ACTION_STOP invokes timerManager stop and onStop callback`() {
        val result = actionHandler.handleAction(TimerNotificationHelper.ACTION_STOP)

        assertThat(result).isTrue()
        verify(exactly = 1) { timerManager.stop() }
        assertThat(stopCalled).isTrue()
    }

    @Test
    fun `handleAction with unknown action or null returns false without invoking actions`() {
        val nullResult = actionHandler.handleAction(null)
        val unknownResult = actionHandler.handleAction("com.unknown.ACTION")

        assertThat(nullResult).isFalse()
        assertThat(unknownResult).isFalse()
        verify(exactly = 0) { timerManager.pause() }
        verify(exactly = 0) { timerManager.resume() }
        verify(exactly = 0) { timerManager.nextStep() }
        verify(exactly = 0) { timerManager.stop() }
        assertThat(stopCalled).isFalse()
    }

    @Test
    fun `handleAction with ACTION_STOP triggers session save on active workout via real TimerManager`() {
        val repository: WorkoutRepository = mockk(relaxed = true)
        val timeProvider = TestTimeProvider(currentTimeMs = 10_000L)
        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher()
        val testScope = kotlinx.coroutines.test.TestScope(testDispatcher)

        val realTimerManager = TimerManager(timeProvider, testScope, repository)
        val realHandler = TimerServiceActionHandler(
            timerManager = realTimerManager,
            onStop = { stopCalled = true }
        )

        val sampleWorkout = Workout(
            id = 55L,
            workoutName = "Test Run",
            warmupSeconds = 60,
            runSeconds = 30,
            walkSeconds = 30,
            repeats = 1,
            cooldownSeconds = 60
        )

        realTimerManager.start(sampleWorkout)
        timeProvider.advanceTimeBy(45_000L)
        realTimerManager.tick()

        val result = realHandler.handleAction(TimerNotificationHelper.ACTION_STOP)
        testScope.testScheduler.advanceUntilIdle()

        assertThat(result).isTrue()
        assertThat(stopCalled).isTrue()
        io.mockk.coVerify(exactly = 1) {
            repository.saveSession(match {
                it.workoutId == 55L && !it.isCompleted && it.totalElapsedSeconds == 45
            })
        }
    }
}
