package com.kintsugirun

import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Test
    fun `run interval of 4 minutes announces Halfway at 120s remaining and One minute left at 60s remaining once`() {
        val longRunWorkout = Workout(
            workoutName = "Long Run",
            warmupSeconds = 0,
            runSeconds = 240,
            walkSeconds = 0,
            repeats = 1,
            cooldownSeconds = 0
        )
        timerManager.start(longRunWorkout)
        assertThat(voicePromptManager.spokenMessages).containsExactly("Start running")

        // Advance 100 seconds (140s remaining) -> no intermediate cue yet
        timeProvider.advanceTimeBy(100_000L)
        timerManager.tick()
        assertThat(voicePromptManager.spokenMessages).containsExactly("Start running")

        // Advance to 120 seconds elapsed (120s remaining, halfway)
        timeProvider.advanceTimeBy(20_000L)
        timerManager.tick()
        assertThat(voicePromptManager.spokenMessages).containsExactly("Start running", "Halfway").inOrder()

        // Advance a few more seconds to test that "Halfway" is not spoken again on subsequent ticks
        timeProvider.advanceTimeBy(10_000L)
        timerManager.tick()
        assertThat(voicePromptManager.spokenMessages).containsExactly("Start running", "Halfway").inOrder()

        // Advance to 180 seconds elapsed (60s remaining, one minute left)
        timeProvider.advanceTimeBy(50_000L)
        timerManager.tick()
        assertThat(voicePromptManager.spokenMessages).containsExactly("Start running", "Halfway", "One minute left").inOrder()

        // Advance a few more seconds to test that "One minute left" is not spoken again
        timeProvider.advanceTimeBy(10_000L)
        timerManager.tick()
        assertThat(voicePromptManager.spokenMessages).containsExactly("Start running", "Halfway", "One minute left").inOrder()
    }

    @Test
    fun `run interval of 2 and half minutes announces Halfway at 75s remaining but not One minute left`() {
        val mediumRunWorkout = Workout(
            workoutName = "Medium Run",
            warmupSeconds = 0,
            runSeconds = 150,
            walkSeconds = 0,
            repeats = 1,
            cooldownSeconds = 0
        )
        timerManager.start(mediumRunWorkout)
        assertThat(voicePromptManager.spokenMessages).containsExactly("Start running")

        // Advance to 75 seconds elapsed (75s remaining, halfway of 150s)
        timeProvider.advanceTimeBy(75_000L)
        timerManager.tick()
        assertThat(voicePromptManager.spokenMessages).containsExactly("Start running", "Halfway").inOrder()

        // Advance to 90 seconds elapsed (60s remaining). Since duration < 180s, "One minute left" should NOT trigger
        timeProvider.advanceTimeBy(15_000L)
        timerManager.tick()
        assertThat(voicePromptManager.spokenMessages).containsExactly("Start running", "Halfway").inOrder()

        // Advance to end of phase
        timeProvider.advanceTimeBy(60_000L)
        timerManager.tick()
        assertThat(voicePromptManager.spokenMessages).doesNotContain("One minute left")
    }

    @Test
    fun `run interval of 2 minutes or less does not announce Halfway or One minute left`() {
        val shortRunWorkout = Workout(
            workoutName = "Short Run",
            warmupSeconds = 0,
            runSeconds = 120,
            walkSeconds = 0,
            repeats = 1,
            cooldownSeconds = 0
        )
        timerManager.start(shortRunWorkout)
        assertThat(voicePromptManager.spokenMessages).containsExactly("Start running")

        // Advance through the entire run interval tick by tick or in chunks
        timeProvider.advanceTimeBy(60_000L)
        timerManager.tick()
        timeProvider.advanceTimeBy(30_000L)
        timerManager.tick()
        timeProvider.advanceTimeBy(30_000L)
        timerManager.tick()

        assertThat(voicePromptManager.spokenMessages).doesNotContain("Halfway")
        assertThat(voicePromptManager.spokenMessages).doesNotContain("One minute left")
    }

    @Test
    fun `phases other than Run do not announce Halfway or One minute left`() {
        val workout = Workout(
            workoutName = "Walk and Warmup Long",
            warmupSeconds = 300,
            runSeconds = 0,
            walkSeconds = 300,
            repeats = 1,
            cooldownSeconds = 300
        )
        timerManager.start(workout)
        assertThat(voicePromptManager.spokenMessages).containsExactly("Start warm up")

        // Advance warmup through halfway (150s) and 60s remaining (240s)
        timeProvider.advanceTimeBy(150_000L)
        timerManager.tick()
        timeProvider.advanceTimeBy(90_000L)
        timerManager.tick()

        assertThat(voicePromptManager.spokenMessages).containsExactly("Start warm up")

        // Transition to walk
        timeProvider.advanceTimeBy(60_000L)
        timerManager.tick()
        assertThat(voicePromptManager.spokenMessages).containsExactly("Start warm up", "Start walking").inOrder()

        // Advance walk through halfway (150s) and 60s remaining (240s)
        timeProvider.advanceTimeBy(150_000L)
        timerManager.tick()
        timeProvider.advanceTimeBy(90_000L)
        timerManager.tick()

        assertThat(voicePromptManager.spokenMessages).doesNotContain("Halfway")
        assertThat(voicePromptManager.spokenMessages).doesNotContain("One minute left")
    }

    @Test
    fun `halfway and one minute left cues reset properly across multiple run repetitions`() {
        val repeatWorkout = Workout(
            workoutName = "Repeats",
            warmupSeconds = 0,
            runSeconds = 240,
            walkSeconds = 30,
            repeats = 2,
            cooldownSeconds = 0
        )
        timerManager.start(repeatWorkout)
        assertThat(voicePromptManager.spokenMessages).containsExactly("Start running")

        // Rep 1: advance to 120s remaining
        timeProvider.advanceTimeBy(120_000L)
        timerManager.tick()
        // Rep 1: advance to 60s remaining
        timeProvider.advanceTimeBy(60_000L)
        timerManager.tick()
        // Rep 1: finish run (advance 60s) -> transition to Walk
        timeProvider.advanceTimeBy(60_000L)
        timerManager.tick()

        assertThat(voicePromptManager.spokenMessages).containsExactly(
            "Start running",
            "Halfway",
            "One minute left",
            "Start walking"
        ).inOrder()

        // Walk finishes (30s) -> transition to Run (Rep 2)
        timeProvider.advanceTimeBy(30_000L)
        timerManager.tick()
        assertThat(voicePromptManager.spokenMessages.last()).isEqualTo("Start running")

        // Rep 2: advance to 120s remaining
        timeProvider.advanceTimeBy(120_000L)
        timerManager.tick()
        assertThat(voicePromptManager.spokenMessages.takeLast(2)).containsExactly("Start running", "Halfway").inOrder()

        // Rep 2: advance to 60s remaining
        timeProvider.advanceTimeBy(60_000L)
        timerManager.tick()
        assertThat(voicePromptManager.spokenMessages.takeLast(3)).containsExactly("Start running", "Halfway", "One minute left").inOrder()
    }

    @Test
    fun `run interval of 4 minutes announces Halfway with pace when GPS tracker provides valid pace`() {
        val mockGpsTracker = mockk<GpsTracker>(relaxed = true)
        val metricsFlow = MutableStateFlow(
            GpsMetrics(currentIntervalPaceSecPerKm = 325) // 5:25 min/km
        )
        every { mockGpsTracker.metrics } returns metricsFlow

        val managerWithGps = TimerManager(
            timeProvider = timeProvider,
            scope = testScope,
            repository = repository,
            gpsTracker = mockGpsTracker
        )
        val promptManager = FakeVoicePromptManager()
        managerWithGps.setVoicePromptManager(promptManager)

        val longRunWorkout = Workout(
            workoutName = "Long Run",
            warmupSeconds = 0,
            runSeconds = 240,
            walkSeconds = 0,
            repeats = 1,
            cooldownSeconds = 0
        )
        managerWithGps.start(longRunWorkout)
        assertThat(promptManager.spokenMessages).containsExactly("Start running")

        // Advance to 120 seconds elapsed (120s remaining, halfway)
        timeProvider.advanceTimeBy(120_000L)
        managerWithGps.tick()

        assertThat(promptManager.spokenMessages).containsExactly(
            "Start running",
            "Halfway, pace 5:25"
        ).inOrder()

        // Advance to 180 seconds elapsed (60s remaining, one minute left)
        timeProvider.advanceTimeBy(60_000L)
        managerWithGps.tick()

        assertThat(promptManager.spokenMessages).containsExactly(
            "Start running",
            "Halfway, pace 5:25",
            "One minute left"
        ).inOrder()
    }

    @Test
    fun `run interval announces Halfway without pace when pace is zero or gps is unacquired`() {
        val mockGpsTracker = mockk<GpsTracker>(relaxed = true)
        val metricsFlow = MutableStateFlow(
            GpsMetrics(currentIntervalPaceSecPerKm = 0)
        )
        every { mockGpsTracker.metrics } returns metricsFlow

        val managerWithGps = TimerManager(
            timeProvider = timeProvider,
            scope = testScope,
            repository = repository,
            gpsTracker = mockGpsTracker
        )
        val promptManager = FakeVoicePromptManager()
        managerWithGps.setVoicePromptManager(promptManager)

        val longRunWorkout = Workout(
            workoutName = "Long Run",
            warmupSeconds = 0,
            runSeconds = 240,
            walkSeconds = 0,
            repeats = 1,
            cooldownSeconds = 0
        )
        managerWithGps.start(longRunWorkout)

        // Advance to halfway
        timeProvider.advanceTimeBy(120_000L)
        managerWithGps.tick()

        assertThat(promptManager.spokenMessages).containsExactly(
            "Start running",
            "Halfway"
        ).inOrder()
    }

    @Test
    fun `natural completion with gps tracker populates session with distance pace and route json`() {
        val mockGpsTracker = mockk<GpsTracker>(relaxed = true)
        val gpsPoints = listOf(
            GpsPoint(latitude = 45.0, longitude = 9.0, timestamp = 1000L, phaseName = "Run"),
            GpsPoint(latitude = 45.001, longitude = 9.001, timestamp = 2000L, phaseName = "Walk")
        )
        val metricsFlow = MutableStateFlow(
            GpsMetrics(
                totalDistanceMeters = 3000.0,
                runDistanceMeters = 2000.0,
                walkDistanceMeters = 1000.0,
                points = gpsPoints
            )
        )
        every { mockGpsTracker.metrics } returns metricsFlow

        val managerWithGps = TimerManager(
            timeProvider = timeProvider,
            scope = testScope,
            repository = repository,
            gpsTracker = mockGpsTracker
        )

        val workout = Workout(
            id = 50L,
            workoutName = "GPS Run",
            warmupSeconds = 0,
            runSeconds = 600,
            walkSeconds = 300,
            repeats = 1,
            cooldownSeconds = 0
        )
        managerWithGps.start(workout)

        // Advance 900s
        timeProvider.advanceTimeBy(900_000L)
        managerWithGps.tick()
        testScope.testScheduler.advanceUntilIdle()

        val sessionSlot = slot<WorkoutSession>()
        coVerify(exactly = 1) { repository.saveSession(capture(sessionSlot)) }

        val savedSession = sessionSlot.captured
        assertThat(savedSession.workoutId).isEqualTo(50L)
        assertThat(savedSession.totalElapsedSeconds).isEqualTo(900)
        assertThat(savedSession.isCompleted).isTrue()
        assertThat(savedSession.totalDistanceMeters).isEqualTo(3000.0)
        assertThat(savedSession.runDistanceMeters).isEqualTo(2000.0)
        assertThat(savedSession.walkDistanceMeters).isEqualTo(1000.0)
        // runSec = 600, runDist = 2000m (2.0 km) -> runPace = 600 / 2.0 = 300 s/km (5:00 min/km)
        assertThat(savedSession.runPaceSecondsPerKm).isEqualTo(300)
        // walkSec = 300, walkDist = 1000m (1.0 km) -> walkPace = 300 / 1.0 = 300 s/km
        assertThat(savedSession.walkPaceSecondsPerKm).isEqualTo(300)
        assertThat(savedSession.gpsRouteJson).isNotNull()
        assertThat(savedSession.gpsRouteJson).contains("45.0")
        assertThat(savedSession.gpsRouteJson).contains("45.001")
    }

    @Test
    fun `early stop with gps tracker populates session with accumulated distance and pace`() {
        val mockGpsTracker = mockk<GpsTracker>(relaxed = true)
        val gpsPoints = listOf(
            GpsPoint(latitude = 45.0, longitude = 9.0, timestamp = 1000L, phaseName = "Run")
        )
        val metricsFlow = MutableStateFlow(
            GpsMetrics(
                totalDistanceMeters = 500.0,
                runDistanceMeters = 500.0,
                walkDistanceMeters = 0.0,
                points = gpsPoints
            )
        )
        every { mockGpsTracker.metrics } returns metricsFlow

        val managerWithGps = TimerManager(
            timeProvider = timeProvider,
            scope = testScope,
            repository = repository,
            gpsTracker = mockGpsTracker
        )

        val workout = Workout(
            id = 55L,
            workoutName = "Intervals",
            warmupSeconds = 0,
            runSeconds = 200,
            walkSeconds = 100,
            repeats = 1,
            cooldownSeconds = 0
        )
        managerWithGps.start(workout)

        // Advance 100s into run phase
        timeProvider.advanceTimeBy(100_000L)
        managerWithGps.tick()

        managerWithGps.stop()
        testScope.testScheduler.advanceUntilIdle()

        val sessionSlot = slot<WorkoutSession>()
        coVerify(exactly = 1) { repository.saveSession(capture(sessionSlot)) }

        val savedSession = sessionSlot.captured
        assertThat(savedSession.workoutId).isEqualTo(55L)
        assertThat(savedSession.totalElapsedSeconds).isEqualTo(100)
        assertThat(savedSession.isCompleted).isFalse()
        assertThat(savedSession.totalDistanceMeters).isEqualTo(500.0)
        assertThat(savedSession.runDistanceMeters).isEqualTo(500.0)
        assertThat(savedSession.walkDistanceMeters).isEqualTo(0.0)
        // runSec = 100, runDist = 500m (0.5 km) -> runPace = 100 / 0.5 = 200 s/km
        assertThat(savedSession.runPaceSecondsPerKm).isEqualTo(200)
        assertThat(savedSession.walkPaceSecondsPerKm).isEqualTo(0)
        assertThat(savedSession.gpsRouteJson).isNotNull()
    }

    @Test
    fun `pace calculation returns 0 when distance is 10 meters or less`() {
        val mockGpsTracker = mockk<GpsTracker>(relaxed = true)
        val metricsFlow = MutableStateFlow(
            GpsMetrics(
                totalDistanceMeters = 10.0,
                runDistanceMeters = 10.0,
                walkDistanceMeters = 0.0,
                points = emptyList()
            )
        )
        every { mockGpsTracker.metrics } returns metricsFlow

        val managerWithGps = TimerManager(
            timeProvider = timeProvider,
            scope = testScope,
            repository = repository,
            gpsTracker = mockGpsTracker
        )

        val workout = Workout(
            id = 60L,
            workoutName = "Short",
            warmupSeconds = 0,
            runSeconds = 60,
            walkSeconds = 0,
            repeats = 1,
            cooldownSeconds = 0
        )
        managerWithGps.start(workout)

        timeProvider.advanceTimeBy(60_000L)
        managerWithGps.tick()
        testScope.testScheduler.advanceUntilIdle()

        val sessionSlot = slot<WorkoutSession>()
        coVerify(exactly = 1) { repository.saveSession(capture(sessionSlot)) }

        val savedSession = sessionSlot.captured
        assertThat(savedSession.runPaceSecondsPerKm).isEqualTo(0)
        assertThat(savedSession.gpsRouteJson).isNull()
    }
}
