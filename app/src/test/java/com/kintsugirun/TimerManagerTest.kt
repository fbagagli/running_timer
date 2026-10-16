package com.kintsugirun

import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Test

class TestTimeProvider(var currentTimeMs: Long = 1000L) : TimeProvider {
    override fun elapsedRealtime(): Long = currentTimeMs

    fun advanceTimeBy(ms: Long) {
        currentTimeMs += ms
    }
}

class FakeVoicePromptManager : VoicePromptManager {
    val spokenMessages = mutableListOf<String>()

    override fun speak(message: String) {
        spokenMessages.add(message)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TimerManagerTest {

    private lateinit var timeProvider: TestTimeProvider
    private lateinit var voicePromptManager: FakeVoicePromptManager
    private lateinit var repository: WorkoutRepository
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var timerManager: TimerManager

    private val sampleWorkout = Workout(
        workoutName = "Test Run",
        warmupSeconds = 60,
        runSeconds = 30,
        walkSeconds = 30,
        repeats = 2,
        cooldownSeconds = 60
    )

    @Before
    fun setup() {
        timeProvider = TestTimeProvider(currentTimeMs = 10_000L)
        voicePromptManager = FakeVoicePromptManager()
        repository = mockk(relaxed = true)
        timerManager = TimerManager(timeProvider, testScope, repository)
        timerManager.setVoicePromptManager(voicePromptManager)
    }

    @Test
    fun `start initializes first phase with correct state and voice prompt`() {
        timerManager.start(sampleWorkout)

        val state = timerManager.timerState.value
        assertThat(state.currentPhaseName).isEqualTo("Warmup")
        assertThat(state.remainingSecondsInPhase).isEqualTo(60)
        assertThat(state.totalElapsedSeconds).isEqualTo(0)
        assertThat(state.isPlaying).isTrue()
        assertThat(state.isFinished).isFalse()
        assertThat(state.repetitionInfo).isNull()

        assertThat(voicePromptManager.spokenMessages).containsExactly("Start warm up")
    }

    @Test
    fun `start with announceRepetitions true produces numbered interval speech prompts`() {
        timerManager.start(sampleWorkout, announceRepetitions = true)

        // Advance through warmup (60s)
        timeProvider.advanceTimeBy(60_000L)
        timerManager.tick()

        assertThat(voicePromptManager.spokenMessages).containsExactly(
            "Start warm up",
            "Start running, 1 of 2"
        ).inOrder()
    }

    @Test
    fun `tick updates remaining seconds and total elapsed time`() {
        timerManager.start(sampleWorkout)

        // Advance time by 10 seconds
        timeProvider.advanceTimeBy(10_000L)
        timerManager.tick()

        val state = timerManager.timerState.value
        assertThat(state.remainingSecondsInPhase).isEqualTo(50)
        assertThat(state.totalElapsedSeconds).isEqualTo(10)
        assertThat(state.isPlaying).isTrue()
    }

    @Test
    fun `tick transitions to next interval when target time reached`() {
        timerManager.start(sampleWorkout)

        // Advance time past warmup (60s)
        timeProvider.advanceTimeBy(60_000L)
        timerManager.tick()

        val state = timerManager.timerState.value
        assertThat(state.currentPhaseName).isEqualTo("Run")
        assertThat(state.remainingSecondsInPhase).isEqualTo(30)
        assertThat(state.totalElapsedSeconds).isEqualTo(60)
        assertThat(state.repetitionInfo).isEqualTo("1 of 2")

        assertThat(voicePromptManager.spokenMessages).containsExactly(
            "Start warm up",
            "Start running"
        ).inOrder()
    }

    @Test
    fun `tick completes entire workout at last step and announces completion`() {
        timerManager.start(sampleWorkout)

        // Total duration: 60 (warmup) + 2 * (30 + 30) + 60 (cooldown) = 240 seconds
        timeProvider.advanceTimeBy(240_000L)
        timerManager.tick()

        val state = timerManager.timerState.value
        assertThat(state.isFinished).isTrue()
        assertThat(state.isPlaying).isFalse()
        assertThat(state.remainingSecondsInPhase).isEqualTo(0)

        assertThat(voicePromptManager.spokenMessages.last()).isEqualTo("Workout complete")
    }

    @Test
    fun `pause and resume adjusts target time without losing elapsed progress`() {
        timerManager.start(sampleWorkout)

        // Run for 20 seconds
        timeProvider.advanceTimeBy(20_000L)
        timerManager.tick()
        assertThat(timerManager.timerState.value.remainingSecondsInPhase).isEqualTo(40)

        // Pause for 15 seconds
        timerManager.pause()
        assertThat(timerManager.timerState.value.isPlaying).isFalse()

        timeProvider.advanceTimeBy(15_000L)

        // Resume
        timerManager.resume()
        assertThat(timerManager.timerState.value.isPlaying).isTrue()

        // 5 seconds after resume
        timeProvider.advanceTimeBy(5_000L)
        timerManager.tick()

        // Remaining should be 40 - 5 = 35 seconds
        assertThat(timerManager.timerState.value.remainingSecondsInPhase).isEqualTo(35)
        assertThat(timerManager.timerState.value.totalElapsedSeconds).isEqualTo(25)
    }

    @Test
    fun `nextStep skips to next phase immediately and updates elapsed time`() {
        timerManager.start(sampleWorkout)

        // Skip warmup directly to first interval
        timerManager.nextStep()

        val state = timerManager.timerState.value
        assertThat(state.currentPhaseName).isEqualTo("Run")
        assertThat(state.remainingSecondsInPhase).isEqualTo(30)
        assertThat(state.totalElapsedSeconds).isEqualTo(60)
        assertThat(state.repetitionInfo).isEqualTo("1 of 2")

        assertThat(voicePromptManager.spokenMessages).containsExactly("Start warm up", "Start running").inOrder()
    }

    @Test
    fun `previousStep rewinds to previous phase immediately and recalculates elapsed time`() {
        timerManager.start(sampleWorkout)

        // Move to step 1 (Run)
        timerManager.nextStep()
        assertThat(timerManager.timerState.value.currentPhaseName).isEqualTo("Run")

        // Rewind back to step 0 (Warmup)
        timerManager.previousStep()

        val state = timerManager.timerState.value
        assertThat(state.currentPhaseName).isEqualTo("Warmup")
        assertThat(state.remainingSecondsInPhase).isEqualTo(60)
        assertThat(state.totalElapsedSeconds).isEqualTo(0)
    }

    @Test
    fun `stop resets timer state completely`() {
        timerManager.start(sampleWorkout)
        timeProvider.advanceTimeBy(30_000L)
        timerManager.tick()

        timerManager.stop()

        val state = timerManager.timerState.value
        assertThat(state.currentPhaseName).isEmpty()
        assertThat(state.remainingSecondsInPhase).isEqualTo(0)
        assertThat(state.totalElapsedSeconds).isEqualTo(0)
        assertThat(state.isPlaying).isFalse()
        assertThat(state.isFinished).isFalse()
        assertThat(state.repetitionInfo).isNull()
    }

    @Test
    fun `natural completion saves completed workout session`() {
        val workout = sampleWorkout.copy(id = 42L)
        timerManager.start(workout)

        // Advance through whole workout: 60 + 2 * (30 + 30) + 60 = 240 seconds
        timeProvider.advanceTimeBy(240_000L)
        timerManager.tick()
        testScope.testScheduler.advanceUntilIdle()

        val sessionSlot = slot<WorkoutSession>()
        coVerify(exactly = 1) { repository.saveSession(capture(sessionSlot)) }

        val savedSession = sessionSlot.captured
        assertThat(savedSession.workoutId).isEqualTo(42L)
        assertThat(savedSession.totalElapsedSeconds).isEqualTo(240)
        assertThat(savedSession.isCompleted).isTrue()
        assertThat(savedSession.interruptedPhase).isNull()
        assertThat(savedSession.workoutSnapshot).isEqualTo(workout)
        assertThat(savedSession.startedAtTimestamp).isGreaterThan(0L)
    }

    @Test
    fun `early stop with 30 or more elapsed seconds saves incomplete workout session with interrupted phase`() {
        val workout = sampleWorkout.copy(id = 42L)
        timerManager.start(workout)

        // Advance 35 seconds during warmup
        timeProvider.advanceTimeBy(35_000L)
        timerManager.tick()

        timerManager.stop()
        testScope.testScheduler.advanceUntilIdle()

        val sessionSlot = slot<WorkoutSession>()
        coVerify(exactly = 1) { repository.saveSession(capture(sessionSlot)) }

        val savedSession = sessionSlot.captured
        assertThat(savedSession.workoutId).isEqualTo(42L)
        assertThat(savedSession.totalElapsedSeconds).isEqualTo(35)
        assertThat(savedSession.isCompleted).isFalse()
        assertThat(savedSession.interruptedPhase).isEqualTo("Warmup")
        assertThat(savedSession.workoutSnapshot).isEqualTo(workout)
        assertThat(savedSession.startedAtTimestamp).isGreaterThan(0L)
    }

    @Test
    fun `early stop during interval includes repetition info in interrupted phase`() {
        val workout = sampleWorkout.copy(id = 42L)
        timerManager.start(workout)

        // Advance 60s (Warmup) + 15s (Run 1 of 2) = 75 seconds
        timeProvider.advanceTimeBy(75_000L)
        timerManager.tick()

        timerManager.stop()
        testScope.testScheduler.advanceUntilIdle()

        val sessionSlot = slot<WorkoutSession>()
        coVerify(exactly = 1) { repository.saveSession(capture(sessionSlot)) }

        val savedSession = sessionSlot.captured
        assertThat(savedSession.isCompleted).isFalse()
        assertThat(savedSession.interruptedPhase).isEqualTo("Run (1 of 2)")
        assertThat(savedSession.totalElapsedSeconds).isEqualTo(75)
    }

    @Test
    fun `early stop with less than 30 elapsed seconds does not save workout session`() {
        val workout = sampleWorkout.copy(id = 42L)
        timerManager.start(workout)

        // Advance only 20 seconds
        timeProvider.advanceTimeBy(20_000L)
        timerManager.tick()

        timerManager.stop()
        testScope.testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { repository.saveSession(any()) }
    }

    @Test
    fun `stop called after natural completion does not duplicate session save`() {
        val workout = sampleWorkout.copy(id = 42L)
        timerManager.start(workout)

        // Complete whole workout (240s)
        timeProvider.advanceTimeBy(240_000L)
        timerManager.tick()
        testScope.testScheduler.advanceUntilIdle()

        // Stop called afterwards
        timerManager.stop()
        testScope.testScheduler.advanceUntilIdle()

        // saveSession should only have been called once (from natural completion)
        coVerify(exactly = 1) { repository.saveSession(any()) }
    }
}
